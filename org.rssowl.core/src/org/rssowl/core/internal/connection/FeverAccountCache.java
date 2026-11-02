/*   **********************************************************************  **
 **   Copyright notice                                                       **
 **                                                                          **
 **   (c) 2005-2011 RSSOwl Development Team                                  **
 **   http://www.rssowl.org/                                                 **
 **                                                                          **
 **   All rights reserved                                                    **
 **                                                                          **
 **   This program and the accompanying materials are made available under   **
 **   the terms of the Eclipse Public License v1.0 which accompanies this    **
 **   distribution, and is available at:                                     **
 **   http://www.rssowl.org/legal/epl-v10.html                               **
 **                                                                          **
 **   A copy is found in the file epl-v10.html and important notices to the  **
 **   license from the team is found in the textfile LICENSE.txt distributed **
 **   in this package.                                                       **
 **                                                                          **
 **   This copyright notice MUST APPEAR in all copies of the file!           **
 **                                                                          **
 **   Contributors:                                                          **
 **     RSSOwl Development Team - initial API and implementation             **
 **                                                                          **
 **  **********************************************************************  */

package org.rssowl.core.internal.connection;

import org.eclipse.core.runtime.IProgressMonitor;
import org.rssowl.core.connection.ConnectionException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Fever API does not allow to request the items of a single feed. All
 * bookmarks of one Fever account therefore share this account level cache,
 * which is filled once and updated incrementally on subsequent reloads.
 *
 * @author RSSOwl Development Team
 */
public final class FeverAccountCache {

  /* Time in ms the cache is considered up to date */
  private static final long CACHE_TTL = 60 * 1000;

  /* Maximum number of items to keep for one account */
  private static final int MAX_CACHED_ITEMS = 10000;

  /* Number of items the Fever API returns per page */
  private static final int PAGE_SIZE = 50;

  /*
   * Upper bound used for the first backwards request. Some Fever
   * implementations (FreshRSS) return the oldest items when max_id is omitted
   * or too large to be parsed as a 32 bit integer, so paging has to start
   * explicitly above any existing item id.
   */
  private static final long MAX_ITEM_ID = Integer.MAX_VALUE;

  /* Maximum number of pages to request during one refresh */
  private static final int MAX_PAGES = 200;

  /* Stop backfill after this many pages without new items */
  private static final int PAGES_WITHOUT_PROGRESS_LIMIT = 4;

  /*
   * Number of items for which the HTML content is kept while filling the
   * cache. Older items are still cached with their metadata but without
   * content to keep the memory footprint low for large accounts.
   */
  private static final int CONTENT_ITEM_LIMIT = 2000;

  /* All caches by endpoint and API key */
  private static final Map<String, FeverAccountCache> fCaches = new HashMap<>();

  /**
   * Returns the cache responsible for the given account, creating it on
   * demand.
   *
   * @param endpoint the HTTP(S) endpoint of the Fever installation.
   * @param apiKey the Fever API key.
   * @return the account cache.
   */
  public static synchronized FeverAccountCache get(URI endpoint, String apiKey) {
    String key = cacheKey(endpoint, apiKey);
    FeverAccountCache cache = fCaches.get(key);
    if (cache == null) {
      cache = new FeverAccountCache(endpoint, apiKey);
      fCaches.put(key, cache);
    }

    return cache;
  }

  private static String cacheKey(URI endpoint, String apiKey) {
    return endpoint.toASCIIString() + '|' + (apiKey != null ? Integer.toHexString(apiKey.hashCode()) : "0"); //$NON-NLS-1$
  }

  /**
   * Removes all caches. Called when the connection service shuts down.
   */
  public static synchronized void clear() {
    fCaches.clear();
  }

  /**
   * An item together with its effective read and saved state as reported by
   * the account level id lists.
   */
  public static final class CachedItem {
    public final FeverClient.Item item;
    public final boolean read;
    public final boolean saved;

    CachedItem(FeverClient.Item item, boolean read, boolean saved) {
      this.item = item;
      this.read = read;
      this.saved = saved;
    }
  }

  private final URI fEndpoint;
  private final String fApiKey;
  private final FeverClient fClient = new FeverClient();
  private final Object fLock = new Object();

  private final Map<Long, FeverClient.Feed> fFeeds = new HashMap<>();
  private final Map<Long, FeverClient.Group> fGroups = new HashMap<>();
  private final Map<Long, List<Long>> fGroupToFeedIds = new HashMap<>();
  private final Map<Long, Map<Long, FeverClient.Item>> fItemsByFeed = new HashMap<>();

  private FeverClient.ItemIds fItemIds;
  private long fMaxItemId;
  private long fLastSync;
  private boolean fInitialized;

  private FeverAccountCache(URI endpoint, String apiKey) {
    fEndpoint = endpoint;
    fApiKey = apiKey;
  }

  /**
   * Refreshes the cached subscription structure, items and item states if the
   * cache is older than its time to live.
   *
   * @param itemLimit the number of items to keep per feed.
   * @param dateLimit the oldest item date in milliseconds to keep, or
   * <code>0</code> for no limit.
   * @param monitor a progress monitor.
   * @throws ConnectionException in case of any connection or protocol error.
   */
  public void refresh(int itemLimit, long dateLimit, IProgressMonitor monitor) throws ConnectionException {
    synchronized (fLock) {
      if (isCanceled(monitor))
        return;

      if (fInitialized && System.currentTimeMillis() - fLastSync < CACHE_TTL)
        return;

      int limit = itemLimit > 0 ? itemLimit : 1;
      FeverClient.Snapshot snapshot = fClient.fetchSnapshot(fEndpoint, fApiKey, monitor);
      boolean hasNewFeeds = updateFeeds(snapshot);

      if (!fInitialized || hasNewFeeds || fMaxItemId <= 0)
        backfill(limit, dateLimit, monitor);
      else
        incremental(dateLimit, monitor);

      if (isCanceled(monitor))
        return;

      fItemIds = fClient.fetchItemIds(fEndpoint, fApiKey, monitor);
      prune(limit);
      fLastSync = System.currentTimeMillis();
      fInitialized = true;
    }
  }

  /**
   * Forces the next reload to fetch the account from the server again. Used
   * after item states have been written back so that a reload cannot revert
   * them from the stale cache.
   */
  public void invalidate() {
    synchronized (fLock) {
      fLastSync = 0;
    }
  }

  /**
   * @param feedId the Fever feed id.
   * @return the cached items of the feed, newest first.
   */
  public List<CachedItem> getItems(long feedId) {
    synchronized (fLock) {
      Map<Long, FeverClient.Item> items = fItemsByFeed.get(feedId);
      if (items == null)
        return Collections.emptyList();

      List<FeverClient.Item> sorted = new ArrayList<>(items.values());
      sorted.sort((o1, o2) -> Long.compare(o2.id, o1.id));

      List<CachedItem> result = new ArrayList<>(sorted.size());
      for (FeverClient.Item item : sorted)
        result.add(new CachedItem(item, isRead(item), isSaved(item)));

      return result;
    }
  }

  /**
   * Releases the HTML content of the cached items of a feed. The content is
   * only needed while the news are created and would otherwise duplicate the
   * descriptions that are already persisted with the news.
   *
   * @param feedId the Fever feed id.
   */
  public void releaseContent(long feedId) {
    synchronized (fLock) {
      Map<Long, FeverClient.Item> items = fItemsByFeed.get(feedId);
      if (items != null) {
        for (FeverClient.Item item : items.values())
          item.html = null;
      }
    }
  }

  /**
   * @param feedId the Fever feed id.
   * @return the feed or <code>null</code> if unknown.
   */
  public FeverClient.Feed getFeed(long feedId) {
    synchronized (fLock) {
      return fFeeds.get(feedId);
    }
  }

  /**
   * @return the feeds of the account by id.
   */
  public Map<Long, FeverClient.Feed> getFeeds() {
    synchronized (fLock) {
      return new HashMap<>(fFeeds);
    }
  }

  /**
   * @return the groups of the account by id.
   */
  public Map<Long, FeverClient.Group> getGroups() {
    synchronized (fLock) {
      return new HashMap<>(fGroups);
    }
  }

  /**
   * @return the feed ids by group id.
   */
  public Map<Long, List<Long>> getGroupToFeedIds() {
    synchronized (fLock) {
      return new HashMap<>(fGroupToFeedIds);
    }
  }

  /**
   * @param monitor a progress monitor.
   * @return the subscription structure of the account.
   * @throws ConnectionException in case of any connection or protocol error.
   */
  public FeverClient.Snapshot fetchSnapshot(IProgressMonitor monitor) throws ConnectionException {
    return fClient.fetchSnapshot(fEndpoint, fApiKey, monitor);
  }

  /**
   * @param monitor a progress monitor.
   * @return the unread and saved item ids of the account.
   * @throws ConnectionException in case of any connection or protocol error.
   */
  public FeverClient.ItemIds fetchItemIds(IProgressMonitor monitor) throws ConnectionException {
    return fClient.fetchItemIds(fEndpoint, fApiKey, monitor);
  }

  /**
   * @return the HTTP(S) endpoint of the Fever installation.
   */
  public URI getEndpoint() {
    return fEndpoint;
  }

  /**
   * @return the API key of the account.
   */
  public String getApiKey() {
    return fApiKey;
  }

  private boolean updateFeeds(FeverClient.Snapshot snapshot) {
    boolean hasNewFeeds = false;
    Set<Long> knownFeeds = new HashSet<>(fFeeds.keySet());

    fFeeds.clear();
    for (FeverClient.Feed feed : snapshot.feeds) {
      fFeeds.put(feed.id, feed);
      if (!knownFeeds.contains(feed.id))
        hasNewFeeds = true;
    }

    fGroups.clear();
    for (FeverClient.Group group : snapshot.groups)
      fGroups.put(group.id, group);

    fGroupToFeedIds.clear();
    fGroupToFeedIds.putAll(snapshot.groupToFeedIds);

    /* Keep cached items only for feeds that still exist */
    Iterator<Long> iterator = fItemsByFeed.keySet().iterator();
    while (iterator.hasNext()) {
      if (!fFeeds.containsKey(iterator.next()))
        iterator.remove();
    }

    return hasNewFeeds;
  }

  private void backfill(int itemLimit, long dateLimit, IProgressMonitor monitor) throws ConnectionException {
    Long maxId = MAX_ITEM_ID;
    int pages = 0;
    int fetched = 0;
    int contentItems = 0;
    int pagesWithoutProgress = 0;

    while (pages++ < MAX_PAGES && fetched < MAX_CACHED_ITEMS && !isCanceled(monitor)) {
      FeverClient.ItemsPage page = fClient.fetchItems(fEndpoint, fApiKey, null, maxId, monitor);
      if (page.items.isEmpty())
        break;

      boolean progress = false;
      boolean reachedDateLimit = false;
      long lowestId = Long.MAX_VALUE;
      long highestId = 0;

      for (FeverClient.Item item : page.items) {
        lowestId = Math.min(lowestId, item.id);
        highestId = Math.max(highestId, item.id);

        if (dateLimit > 0 && item.createdOnTime > 0 && item.createdOnTime * 1000 < dateLimit) {
          reachedDateLimit = true;
          continue;
        }

        if (fFeeds.containsKey(item.feedId) && addItem(item, itemLimit)) {
          progress = true;
          contentItems++;

          /* Keep the content of the newest items only */
          if (contentItems > CONTENT_ITEM_LIMIT)
            item.html = null;
        }
      }

      fMaxItemId = Math.max(fMaxItemId, highestId);
      fetched += page.items.size();
      pagesWithoutProgress = progress ? 0 : pagesWithoutProgress + 1;

      if (allFeedsFull(itemLimit) || reachedDateLimit || page.items.size() < PAGE_SIZE)
        break;
      if (pagesWithoutProgress >= PAGES_WITHOUT_PROGRESS_LIMIT || lowestId <= 0)
        break;

      maxId = lowestId;
    }
  }

  private void incremental(long dateLimit, IProgressMonitor monitor) throws ConnectionException {
    long sinceId = fMaxItemId;
    int pages = 0;
    int fetched = 0;
    int contentItems = 0;

    while (pages++ < MAX_PAGES && fetched < MAX_CACHED_ITEMS && !isCanceled(monitor)) {
      FeverClient.ItemsPage page = fClient.fetchItems(fEndpoint, fApiKey, sinceId, null, monitor);
      if (page.items.isEmpty())
        break;

      long highestId = sinceId;

      for (FeverClient.Item item : page.items) {
        highestId = Math.max(highestId, item.id);

        if (dateLimit > 0 && item.createdOnTime > 0 && item.createdOnTime * 1000 < dateLimit)
          continue;
        if (fFeeds.containsKey(item.feedId)) {
          contentItems++;
          if (contentItems > CONTENT_ITEM_LIMIT)
            item.html = null;

          addItem(item, Integer.MAX_VALUE); //Pruned later
        }
      }

      if (highestId <= sinceId)
        break;

      fMaxItemId = Math.max(fMaxItemId, highestId);
      fetched += page.items.size();

      if (page.items.size() < PAGE_SIZE)
        break;

      sinceId = highestId;
    }
  }

  private boolean addItem(FeverClient.Item item, int itemLimit) {
    Map<Long, FeverClient.Item> items = fItemsByFeed.get(item.feedId);
    if (items == null) {
      items = new HashMap<>();
      fItemsByFeed.put(item.feedId, items);
    }

    if (items.containsKey(item.id))
      return false;

    if (items.size() >= itemLimit) {
      /* Remove the oldest cached item to make room */
      long oldestId = Long.MAX_VALUE;
      for (Long id : items.keySet())
        oldestId = Math.min(oldestId, id);

      if (item.id <= oldestId)
        return false;

      items.remove(oldestId);
    }

    items.put(item.id, item);
    return true;
  }

  private boolean allFeedsFull(int itemLimit) {
    for (Long feedId : fFeeds.keySet()) {
      Map<Long, FeverClient.Item> items = fItemsByFeed.get(feedId);
      if (items == null || items.size() < itemLimit)
        return false;
    }

    return true;
  }

  private void prune(int itemLimit) {
    int total = 0;
    List<FeverClient.Item> allItems = new ArrayList<>();

    for (Map<Long, FeverClient.Item> items : fItemsByFeed.values()) {
      if (items.size() > itemLimit) {
        List<Long> ids = new ArrayList<>(items.keySet());
        ids.sort((o1, o2) -> Long.compare(o2, o1));
        for (int i = itemLimit; i < ids.size(); i++)
          items.remove(ids.get(i));
      }

      total += items.size();
      allItems.addAll(items.values());
    }

    /* Enforce the global limit by dropping the oldest items */
    if (total > MAX_CACHED_ITEMS) {
      allItems.sort((o1, o2) -> Long.compare(o1.id, o2.id));
      int toRemove = total - MAX_CACHED_ITEMS;
      for (int i = 0; i < toRemove; i++) {
        FeverClient.Item item = allItems.get(i);
        Map<Long, FeverClient.Item> items = fItemsByFeed.get(item.feedId);
        if (items != null)
          items.remove(item.id);
      }
    }
  }

  private boolean isRead(FeverClient.Item item) {
    if (fItemIds != null && fItemIds.hasUnread)
      return !fItemIds.unread.contains(item.id);

    return item.read;
  }

  private boolean isSaved(FeverClient.Item item) {
    if (fItemIds != null && fItemIds.hasSaved)
      return fItemIds.saved.contains(item.id);

    return item.saved;
  }

  private boolean isCanceled(IProgressMonitor monitor) {
    return monitor != null && monitor.isCanceled();
  }
}
