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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.osgi.service.url.URLStreamHandlerService;
import org.rssowl.core.Owl;
import org.rssowl.core.connection.ConnectionException;
import org.rssowl.core.connection.IConnectionPropertyConstants;
import org.rssowl.core.internal.Activator;
import org.rssowl.core.persist.IConditionalGet;
import org.rssowl.core.persist.IFeed;
import org.rssowl.core.persist.IModelFactory;
import org.rssowl.core.persist.INews;
import org.rssowl.core.persist.IPerson;
import org.rssowl.core.util.FeverUtils;
import org.rssowl.core.util.StringUtils;
import org.rssowl.core.util.SyncItem;
import org.rssowl.core.util.SyncUtils;
import org.rssowl.core.util.Triple;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Protocol handler for feeds of a Fever API account. A feed link has the form
 * <code>fever://host/path?feed_id=123</code> and is resolved against the
 * account level cache since the Fever API only returns all items of an account
 * at once.
 *
 * @author RSSOwl Development Team
 */
public class FeverProtocolHandler extends DefaultProtocolHandler {

  /* Default number of items to load per feed */
  private static final int DEFAULT_ITEM_LIMIT = 200;

  /*
   * @see
   * org.rssowl.core.internal.connection.DefaultProtocolHandler#reload(java.
   * net.URI, org.eclipse.core.runtime.IProgressMonitor, java.util.Map)
   */
  @Override
  @SuppressWarnings("unchecked")
  public Triple<IFeed, IConditionalGet, URI> reload(URI link, IProgressMonitor monitor, Map<Object, Object> properties) throws CoreException {
    URI endpoint;
    try {
      endpoint = FeverUtils.toEndpoint(link);
    } catch (URISyntaxException e) {
      throw new ConnectionException(Activator.getDefault().createErrorStatus(e.getMessage(), e));
    }

    long feedId = FeverUtils.getFeedId(link);
    if (feedId <= 0)
      throw new ConnectionException(Activator.getDefault().createErrorStatus("The Fever feed link is missing a feed id.", null)); //$NON-NLS-1$

    String apiKey = FeverUtils.getApiKey(endpoint);
    if (!StringUtils.isSet(apiKey)) {
      URI finalEndpoint = endpoint;
      throw new ConnectionException(Activator.getDefault().createErrorStatus("No Fever API key is stored for " + finalEndpoint + ". Please create the Fever account again.", null)); //$NON-NLS-1$ //$NON-NLS-2$
    }

    int itemLimit = DEFAULT_ITEM_LIMIT;
    long dateLimit = 0;
    Map<String, SyncItem> uncommittedItems = null;

    if (properties != null) {
      Object itemLimitObj = properties.get(IConnectionPropertyConstants.ITEM_LIMIT);
      if (itemLimitObj instanceof Integer)
        itemLimit = (Integer) itemLimitObj;

      Object dateLimitObj = properties.get(IConnectionPropertyConstants.DATE_LIMIT);
      if (dateLimitObj instanceof Long)
        dateLimit = (Long) dateLimitObj;

      Object uncommittedObj = properties.get(IConnectionPropertyConstants.UNCOMMITTED_ITEMS);
      if (uncommittedObj instanceof Map)
        uncommittedItems = (Map<String, SyncItem>) uncommittedObj;
    }

    FeverAccountCache cache = FeverAccountCache.get(endpoint, apiKey);
    cache.refresh(itemLimit, dateLimit, monitor);

    if (monitor != null && monitor.isCanceled())
      return null;

    FeverClient.Feed feedInfo = cache.getFeed(feedId);
    List<FeverAccountCache.CachedItem> items = cache.getItems(feedId);

    IModelFactory factory = Owl.getModelFactory();
    IFeed feed = factory.createFeed(null, link);
    feed.setBase(endpoint);
    feed.setTitle(feedInfo != null && StringUtils.isSet(feedInfo.title) ? feedInfo.title : endpoint.getHost());

    if (feedInfo != null) {
      if (StringUtils.isSet(feedInfo.siteUrl)) {
        try {
          feed.setHomepage(new URI(feedInfo.siteUrl));
        } catch (URISyntaxException e) {
          /* Ignore malformed site url */
        }
      }

      if (StringUtils.isSet(feedInfo.url))
        feed.setDescription(feedInfo.url);
    }

    for (FeverAccountCache.CachedItem cachedItem : items) {
      if (monitor != null && monitor.isCanceled())
        return null;

      FeverClient.Item item = cachedItem.item;
      INews news = factory.createNews(null, feed, new Date());
      news.setBase(endpoint);
      news.setGuid(factory.createGuid(news, String.valueOf(item.id), true));

      if (StringUtils.isSet(item.title))
        news.setTitle(item.title);

      if (item.createdOnTime > 0)
        news.setPublishDate(new Date(item.createdOnTime * 1000));

      if (StringUtils.isSet(item.html))
        news.setDescription(item.html);

      if (StringUtils.isSet(item.url)) {
        try {
          news.setLink(new URI(item.url));
        } catch (URISyntaxException e) {
          /* Ignore malformed item url */
        }
      }

      if (StringUtils.isSet(item.author)) {
        IPerson person = factory.createPerson(null, news);
        person.setName(item.author);
      }

      news.setFlagged(cachedItem.saved);

      /* The state is applied during merge of synchronized feeds */
      if (cachedItem.read)
        news.setProperty(SyncUtils.FEVER_MARKED_READ, true);
      else
        news.setProperty(SyncUtils.FEVER_MARKED_UNREAD, true);

      /* Apply pending changes of the user that have not been sent yet */
      if (uncommittedItems != null) {
        String itemId = String.valueOf(item.id);
        SyncItem syncItem = uncommittedItems.get(SyncItem.toKey(link.toString(), itemId));
        if (syncItem == null)
          syncItem = uncommittedItems.get(itemId); //Legacy entries that are keyed by id only
        if (syncItem != null)
          syncItem.applyTo(news);
      }
    }

    /* The descriptions are now referenced by the news, free the cached copies */
    cache.releaseContent(feedId);

    return Triple.create(feed, null, link);
  }

  /*
   * @see
   * org.rssowl.core.internal.connection.DefaultProtocolHandler#getFeedIcon(
   * java.net.URI, org.eclipse.core.runtime.IProgressMonitor)
   */
  @Override
  public byte[] getFeedIcon(URI link, IProgressMonitor monitor) {
    return null;
  }

  /*
   * @see
   * org.rssowl.core.internal.connection.DefaultProtocolHandler#getLabel(java.
   * net.URI, org.eclipse.core.runtime.IProgressMonitor)
   */
  @Override
  public String getLabel(URI link, IProgressMonitor monitor) throws ConnectionException {
    try {
      URI endpoint = FeverUtils.toEndpoint(link);
      String apiKey = FeverUtils.getApiKey(endpoint);
      if (!StringUtils.isSet(apiKey))
        return link.getHost();

      FeverAccountCache cache = FeverAccountCache.get(endpoint, apiKey);
      cache.refresh(DEFAULT_ITEM_LIMIT, 0, monitor);

      FeverClient.Feed feed = cache.getFeed(FeverUtils.getFeedId(link));
      return feed != null ? feed.title : link.getHost();
    } catch (URISyntaxException e) {
      return link.getHost();
    }
  }

  /*
   * @see
   * org.rssowl.core.internal.connection.DefaultProtocolHandler#getFeed(java.
   * net.URI, org.eclipse.core.runtime.IProgressMonitor)
   */
  @Override
  public URI getFeed(URI website, IProgressMonitor monitor) {
    return SyncUtils.isFever(website.toString()) ? website : null;
  }

  /*
   * @see
   * org.rssowl.core.internal.connection.DefaultProtocolHandler#getURLStreamHandler()
   */
  @Override
  public URLStreamHandlerService getURLStreamHandler() {
    return null;
  }

  /**
   * Creates the link of a feed inside a Fever account. This is a convenience
   * method for the UI.
   *
   * @param endpoint the HTTP(S) endpoint of the Fever installation.
   * @param feedId the Fever feed id.
   * @return the link of the feed.
   * @throws URISyntaxException if the link cannot be created.
   */
  public static URI createFeedLink(URI endpoint, long feedId) throws URISyntaxException {
    return FeverUtils.createFeedLink(endpoint, feedId);
  }
}
