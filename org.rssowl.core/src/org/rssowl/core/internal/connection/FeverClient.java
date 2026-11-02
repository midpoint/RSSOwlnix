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
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.rssowl.core.Owl;
import org.rssowl.core.connection.AuthenticationRequiredException;
import org.rssowl.core.connection.ConnectionException;
import org.rssowl.core.connection.IAbortable;
import org.rssowl.core.connection.IConnectionPropertyConstants;
import org.rssowl.core.connection.IProtocolHandler;
import org.rssowl.core.internal.Activator;
import org.rssowl.core.internal.interpreter.json.JSONArray;
import org.rssowl.core.internal.interpreter.json.JSONException;
import org.rssowl.core.internal.interpreter.json.JSONObject;
import org.rssowl.core.internal.interpreter.json.JSONTokener;
import org.rssowl.core.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small client for the Fever API. The client only depends on the generic
 * connection service so proxies, SSL settings and timeouts are handled by the
 * existing HTTP protocol handler.
 *
 * @author RSSOwl Development Team
 */
public class FeverClient {

  /* Request parameters */
  private static final String PARAM_API_KEY = "api_key"; //$NON-NLS-1$
  private static final String PARAM_API = "api"; //$NON-NLS-1$

  /* Response members */
  private static final String RESPONSE_AUTH = "auth"; //$NON-NLS-1$
  private static final String RESPONSE_API_VERSION = "api_version"; //$NON-NLS-1$
  private static final String RESPONSE_FEEDS = "feeds"; //$NON-NLS-1$
  private static final String RESPONSE_GROUPS = "groups"; //$NON-NLS-1$
  private static final String RESPONSE_FEEDS_GROUPS = "feeds_groups"; //$NON-NLS-1$
  private static final String RESPONSE_ITEMS = "items"; //$NON-NLS-1$
  private static final String RESPONSE_TOTAL_ITEMS = "total_items"; //$NON-NLS-1$
  private static final String RESPONSE_UNREAD_ITEM_IDS = "unread_item_ids"; //$NON-NLS-1$
  private static final String RESPONSE_SAVED_ITEM_IDS = "saved_item_ids"; //$NON-NLS-1$

  /* Item fields */
  private static final String ID = "id"; //$NON-NLS-1$
  private static final String FEED_ID = "feed_id"; //$NON-NLS-1$
  private static final String TITLE = "title"; //$NON-NLS-1$
  private static final String AUTHOR = "author"; //$NON-NLS-1$
  private static final String HTML = "html"; //$NON-NLS-1$
  private static final String URL = "url"; //$NON-NLS-1$
  private static final String IS_SAVED = "is_saved"; //$NON-NLS-1$
  private static final String IS_READ = "is_read"; //$NON-NLS-1$
  private static final String CREATED_ON_TIME = "created_on_time"; //$NON-NLS-1$

  /* Feed fields */
  private static final String FAVICON_ID = "favicon_id"; //$NON-NLS-1$
  private static final String SITE_URL = "site_url"; //$NON-NLS-1$
  private static final String IS_SPARK = "is_spark"; //$NON-NLS-1$
  private static final String LAST_UPDATED_ON_TIME = "last_updated_on_time"; //$NON-NLS-1$

  /* Group fields */
  private static final String GROUP_ID = "group_id"; //$NON-NLS-1$
  private static final String FEED_IDS = "feed_ids"; //$NON-NLS-1$

  /* Connection timeout */
  private static final int DEFAULT_CON_TIMEOUT = 30000;

  /* The endpoint that answered successfully last, if it differs from the input */
  private URI fResolvedEndpoint;
  private String fResolvedEndpointSource;

  /**
   * A feed known to the Fever installation.
   */
  public static final class Feed {
    public final long id;
    public final long faviconId;
    public final String title;
    public final String url;
    public final String siteUrl;
    public final boolean spark;
    public final long lastUpdatedOnTime;

    Feed(long id, long faviconId, String title, String url, String siteUrl, boolean spark, long lastUpdatedOnTime) {
      this.id = id;
      this.faviconId = faviconId;
      this.title = title;
      this.url = url;
      this.siteUrl = siteUrl;
      this.spark = spark;
      this.lastUpdatedOnTime = lastUpdatedOnTime;
    }
  }

  /**
   * A user defined group (folder) of the Fever installation.
   */
  public static final class Group {
    public final long id;
    public final String title;

    Group(long id, String title) {
      this.id = id;
      this.title = title;
    }
  }

  /**
   * A single Fever item.
   */
  public static final class Item {
    public final long id;
    public final long feedId;
    public final String title;
    public final String author;
    public String html;
    public final String url;
    public final boolean saved;
    public final boolean read;
    public final long createdOnTime;

    Item(long id, long feedId, String title, String author, String html, String url, boolean saved, boolean read, long createdOnTime) {
      this.id = id;
      this.feedId = feedId;
      this.title = title;
      this.author = author;
      this.html = html;
      this.url = url;
      this.saved = saved;
      this.read = read;
      this.createdOnTime = createdOnTime;
    }
  }

  /**
   * The subscription structure of a Fever installation.
   */
  public static final class Snapshot {
    public final int apiVersion;
    public final List<Feed> feeds;
    public final List<Group> groups;
    public final Map<Long, List<Long>> groupToFeedIds;

    Snapshot(int apiVersion, List<Feed> feeds, List<Group> groups, Map<Long, List<Long>> groupToFeedIds) {
      this.apiVersion = apiVersion;
      this.feeds = feeds;
      this.groups = groups;
      this.groupToFeedIds = groupToFeedIds;
    }
  }

  /**
   * One page of items as returned by the Fever API (at most 50 entries).
   */
  public static final class ItemsPage {
    public final List<Item> items;
    public final long totalItems;

    ItemsPage(List<Item> items, long totalItems) {
      this.items = items;
      this.totalItems = totalItems;
    }
  }

  /**
   * The unread and saved item ids of an account.
   */
  public static final class ItemIds {
    public final boolean hasUnread;
    public final boolean hasSaved;
    public final Set<Long> unread;
    public final Set<Long> saved;

    ItemIds(boolean hasUnread, boolean hasSaved, Set<Long> unread, Set<Long> saved) {
      this.hasUnread = hasUnread;
      this.hasSaved = hasSaved;
      this.unread = unread;
      this.saved = saved;
    }
  }

  /**
   * Reads feeds and groups from the Fever installation.
   *
   * @param endpoint the HTTP(S) endpoint of the Fever installation.
   * @param apiKey the Fever API key.
   * @param monitor a progress monitor.
   * @return the subscription structure.
   * @throws ConnectionException in case of any connection or protocol error.
   */
  public Snapshot fetchSnapshot(URI endpoint, String apiKey, IProgressMonitor monitor) throws ConnectionException {
    JSONObject response = request(endpoint, apiKey, PARAM_API + "&feeds&groups", null, monitor); //$NON-NLS-1$

    List<Feed> feeds = new ArrayList<>();
    JSONArray feedsArray = response.optJSONArray(RESPONSE_FEEDS);
    if (feedsArray != null) {
      for (int i = 0; i < feedsArray.length(); i++) {
        JSONObject feedObj = feedsArray.optJSONObject(i);
        if (feedObj != null)
          feeds.add(parseFeed(feedObj));
      }
    }

    List<Group> groups = new ArrayList<>();
    JSONArray groupsArray = response.optJSONArray(RESPONSE_GROUPS);
    if (groupsArray != null) {
      for (int i = 0; i < groupsArray.length(); i++) {
        JSONObject groupObj = groupsArray.optJSONObject(i);
        if (groupObj == null)
          continue;

        long id = optLong(groupObj, ID, -1);
        if (id > 0)
          groups.add(new Group(id, optString(groupObj, TITLE)));
      }
    }

    Map<Long, List<Long>> groupToFeedIds = new HashMap<>();
    JSONArray feedsGroupsArray = response.optJSONArray(RESPONSE_FEEDS_GROUPS);
    if (feedsGroupsArray != null) {
      for (int i = 0; i < feedsGroupsArray.length(); i++) {
        JSONObject relation = feedsGroupsArray.optJSONObject(i);
        if (relation == null)
          continue;

        long groupId = optLong(relation, GROUP_ID, -1);
        if (groupId <= 0)
          continue;

        groupToFeedIds.put(groupId, parseIdList(optObject(relation, FEED_IDS)));
      }
    }

    return new Snapshot(optInt(response, RESPONSE_API_VERSION, 0), feeds, groups, groupToFeedIds);
  }

  /**
   * Reads one page of items.
   *
   * @param endpoint the HTTP(S) endpoint of the Fever installation.
   * @param apiKey the Fever API key.
   * @param sinceId if not <code>null</code>, requests items newer than this id.
   * @param maxId if not <code>null</code>, requests items older than this id.
   * @param monitor a progress monitor.
   * @return the requested page.
   * @throws ConnectionException in case of any connection or protocol error.
   */
  public ItemsPage fetchItems(URI endpoint, String apiKey, Long sinceId, Long maxId, IProgressMonitor monitor) throws ConnectionException {
    StringBuilder query = new StringBuilder(PARAM_API).append("&items"); //$NON-NLS-1$
    if (sinceId != null)
      query.append("&since_id=").append(sinceId); //$NON-NLS-1$
    else if (maxId != null)
      query.append("&max_id=").append(maxId); //$NON-NLS-1$

    JSONObject response = request(endpoint, apiKey, query.toString(), null, monitor);

    List<Item> items = new ArrayList<>();
    JSONArray itemsArray = response.optJSONArray(RESPONSE_ITEMS);
    if (itemsArray != null) {
      for (int i = 0; i < itemsArray.length(); i++) {
        JSONObject itemObj = itemsArray.optJSONObject(i);
        if (itemObj != null && optLong(itemObj, ID, -1) > 0)
          items.add(parseItem(itemObj));
      }
    }

    return new ItemsPage(items, optLong(response, RESPONSE_TOTAL_ITEMS, 0));
  }

  /**
   * Reads the unread and saved item ids of the account.
   *
   * @param endpoint the HTTP(S) endpoint of the Fever installation.
   * @param apiKey the Fever API key.
   * @param monitor a progress monitor.
   * @return the item ids.
   * @throws ConnectionException in case of any connection or protocol error.
   */
  public ItemIds fetchItemIds(URI endpoint, String apiKey, IProgressMonitor monitor) throws ConnectionException {
    JSONObject response = request(endpoint, apiKey, PARAM_API + "&unread_item_ids&saved_item_ids", null, monitor); //$NON-NLS-1$
    boolean hasUnread = response.has(RESPONSE_UNREAD_ITEM_IDS);
    boolean hasSaved = response.has(RESPONSE_SAVED_ITEM_IDS);

    return new ItemIds(hasUnread, hasSaved, new HashSet<>(parseIdList(optObject(response, RESPONSE_UNREAD_ITEM_IDS))), new HashSet<>(parseIdList(optObject(response, RESPONSE_SAVED_ITEM_IDS))));
  }

  /**
   * Updates a single item.
   *
   * @param endpoint the HTTP(S) endpoint of the Fever installation.
   * @param apiKey the Fever API key.
   * @param itemId the item id.
   * @param action one of <code>read</code>, <code>unread</code>,
   * <code>saved</code> or <code>unsaved</code>.
   * @param monitor a progress monitor.
   * @throws ConnectionException in case of any connection or protocol error.
   */
  public void markItem(URI endpoint, String apiKey, String itemId, String action, IProgressMonitor monitor) throws ConnectionException {
    Map<String, String[]> parameters = new HashMap<>();
    parameters.put("mark", new String[] { "item" }); //$NON-NLS-1$ //$NON-NLS-2$
    parameters.put("as", new String[] { action }); //$NON-NLS-1$
    parameters.put("id", new String[] { itemId }); //$NON-NLS-1$
    request(endpoint, apiKey, PARAM_API, parameters, monitor);
  }

  /**
   * @return the endpoint that answered successfully last, or <code>null</code>
   * if no request succeeded yet.
   */
  public URI getResolvedEndpoint() {
    return fResolvedEndpoint;
  }

  private JSONObject request(URI endpoint, String apiKey, String query, Map<String, String[]> additionalParameters, IProgressMonitor monitor) throws ConnectionException {
    List<URI> candidates = getEndpointCandidates(endpoint);
    ConnectionException recoveryError = null;

    for (URI candidate : candidates) {
      try {
        JSONObject response = requestOnce(candidate, apiKey, query, additionalParameters, monitor);
        fResolvedEndpoint = candidate;
        fResolvedEndpointSource = endpoint.toASCIIString();
        return response;
      } catch (FeverAuthenticationException e) {
        recoveryError = e;
      } catch (FeverResponseException e) {
        recoveryError = e;
      }
    }

    if (recoveryError != null)
      throw recoveryError;

    throw new ConnectionException(Activator.getDefault().createErrorStatus("Unable to connect to the Fever server at " + endpoint + ".", null)); //$NON-NLS-1$ //$NON-NLS-2$
  }

  private JSONObject requestOnce(URI endpoint, String apiKey, String query, Map<String, String[]> additionalParameters, IProgressMonitor monitor) throws ConnectionException {
    if (monitor != null && monitor.isCanceled())
      throw new ConnectionException(Status.CANCEL_STATUS);

    URI requestUri;
    try {
      requestUri = buildRequestUri(endpoint, query);
    } catch (Exception e) {
      throw new ConnectionException(Activator.getDefault().createErrorStatus(e.getMessage(), e));
    }

    Map<String, String[]> parameters = new HashMap<>();
    parameters.put(PARAM_API_KEY, new String[] { apiKey });
    if (additionalParameters != null)
      parameters.putAll(additionalParameters);

    Map<Object, Object> properties = new HashMap<>();
    properties.put(IConnectionPropertyConstants.POST, Boolean.TRUE);
    properties.put(IConnectionPropertyConstants.PARAMETERS, parameters);
    properties.put(IConnectionPropertyConstants.CON_TIMEOUT, DEFAULT_CON_TIMEOUT);
    if (monitor != null)
      properties.put(IConnectionPropertyConstants.PROGRESS_MONITOR, monitor);

    IProtocolHandler handler = Owl.getConnectionService().getHandler(requestUri);
    InputStream inS = null;
    try {
      inS = handler.openStream(requestUri, monitor, properties);
      if (monitor != null && monitor.isCanceled())
        throw new ConnectionException(Status.CANCEL_STATUS);

      String content = StringUtils.readString(new InputStreamReader(inS, StandardCharsets.UTF_8));
      JSONObject response = new JSONObject(new JSONTokener(content));
      if (optInt(response, RESPONSE_AUTH, 0) != 1)
        throw new FeverAuthenticationException(Activator.getDefault().createErrorStatus("Fever authentication failed for " + endpoint + ". Please check that this is the full Fever API URL (for example https://example.com/fever/) and that the API key is correct.", null)); //$NON-NLS-1$ //$NON-NLS-2$

      return response;
    } catch (AuthenticationRequiredException e) {
      throw new FeverAuthenticationException(Activator.getDefault().createErrorStatus("Fever authentication failed for " + endpoint + " (HTTP 401). Please check that this is the full Fever API URL (for example https://example.com/fever/) and that the API key is correct.", e)); //$NON-NLS-1$ //$NON-NLS-2$
    } catch (JSONException e) {
      throw new FeverResponseException(Activator.getDefault().createErrorStatus("The server at " + endpoint + " did not return a valid Fever JSON response. Please check the Fever API URL.", e)); //$NON-NLS-1$ //$NON-NLS-2$
    } catch (ConnectionException e) {
      throw e;
    } catch (IOException e) {
      throw new ConnectionException(Activator.getDefault().createErrorStatus(e.getMessage(), e));
    } finally {
      if (inS != null) {
        try {
          if (inS instanceof IAbortable && monitor != null && monitor.isCanceled())
            ((IAbortable) inS).abort();
          else
            inS.close();
        } catch (IOException e) {
          /* Ignore */
        }
      }
    }
  }

  /*
   * The connection service automatically follows redirects. A POST body is
   * lost when a server redirects, for example because the Fever API URL was
   * entered without its trailing slash. Since the api key is posted, such a
   * redirect looks like a failed authentication. Try the canonical variants
   * of the URL as well so that the request still succeeds.
   */
  private List<URI> getEndpointCandidates(URI endpoint) {
    List<URI> candidates = new ArrayList<>();
    if (fResolvedEndpoint != null && endpoint.toASCIIString().equals(fResolvedEndpointSource))
      addCandidate(candidates, fResolvedEndpoint);

    addCandidate(candidates, endpoint);

    String path = endpoint.getPath();
    if (!StringUtils.isSet(path) || "/".equals(path)) //$NON-NLS-1$
      addCandidate(candidates, withPath(endpoint, "/fever/")); //$NON-NLS-1$
    else if (!path.endsWith("/") && !hasFileExtension(path)) //$NON-NLS-1$
      addCandidate(candidates, withPath(endpoint, path + '/'));

    return candidates;
  }

  private void addCandidate(List<URI> candidates, URI candidate) {
    if (candidate == null)
      return;

    for (URI existing : candidates) {
      if (existing.equals(candidate))
        return;
    }

    candidates.add(candidate);
  }

  private URI withPath(URI endpoint, String path) {
    try {
      return new URI(endpoint.getScheme(), endpoint.getUserInfo(), endpoint.getHost(), endpoint.getPort(), path, null, null);
    } catch (URISyntaxException e) {
      return null;
    }
  }

  private boolean hasFileExtension(String path) {
    return path.lastIndexOf('.') > path.lastIndexOf('/');
  }

  private static final class FeverAuthenticationException extends ConnectionException {
    FeverAuthenticationException(IStatus status) {
      super(status);
    }
  }

  private static final class FeverResponseException extends ConnectionException {
    FeverResponseException(IStatus status) {
      super(status);
    }
  }

  private URI buildRequestUri(URI endpoint, String query) throws Exception {
    StringBuilder value = new StringBuilder(endpoint.toASCIIString());
    if (endpoint.getQuery() != null)
      value.append('?').append(endpoint.getQuery());
    if (value.indexOf("?") >= 0) //$NON-NLS-1$
      value.append('&');
    else
      value.append('?');

    value.append(query);
    return new URI(value.toString());
  }

  private Feed parseFeed(JSONObject obj) {
    return new Feed(optLong(obj, ID, -1), optLong(obj, FAVICON_ID, -1), optString(obj, TITLE), optString(obj, URL), optString(obj, SITE_URL), optInt(obj, IS_SPARK, 0) == 1, optLong(obj, LAST_UPDATED_ON_TIME, 0));
  }

  private Item parseItem(JSONObject obj) {
    return new Item(optLong(obj, ID, -1), optLong(obj, FEED_ID, -1), optString(obj, TITLE), optString(obj, AUTHOR), optString(obj, HTML), optString(obj, URL), optInt(obj, IS_SAVED, 0) == 1, optInt(obj, IS_READ, 0) == 1, optLong(obj, CREATED_ON_TIME, 0));
  }

  private List<Long> parseIdList(Object value) {
    if (value == null || JSONObject.NULL.equals(value))
      return Collections.emptyList();

    List<Long> ids = new ArrayList<>();
    if (value instanceof JSONArray) {
      JSONArray array = (JSONArray) value;
      for (int i = 0; i < array.length(); i++) {
        long id = parseLong(array.opt(i));
        if (id > 0)
          ids.add(id);
      }
    } else {
      String[] parts = value.toString().split(","); //$NON-NLS-1$
      for (String part : parts) {
        long id = parseLong(part.trim());
        if (id > 0)
          ids.add(id);
      }
    }

    return ids;
  }

  private long parseLong(Object value) {
    if (value == null || JSONObject.NULL.equals(value))
      return -1;

    if (value instanceof Number)
      return ((Number) value).longValue();

    try {
      return Long.parseLong(value.toString().trim());
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private Object optObject(JSONObject obj, String key) {
    return obj.has(key) ? obj.opt(key) : null;
  }

  private String optString(JSONObject obj, String key) {
    Object value = optObject(obj, key);
    if (value == null || JSONObject.NULL.equals(value))
      return null;

    return value.toString();
  }

  private long optLong(JSONObject obj, String key, long defaultValue) {
    Object value = optObject(obj, key);
    if (value == null || JSONObject.NULL.equals(value))
      return defaultValue;

    long result = parseLong(value);
    return result >= 0 ? result : defaultValue;
  }

  private int optInt(JSONObject obj, String key, int defaultValue) {
    Object value = optObject(obj, key);
    if (value == null || JSONObject.NULL.equals(value))
      return defaultValue;

    if (value instanceof Number)
      return ((Number) value).intValue();

    if (value instanceof Boolean)
      return ((Boolean) value).booleanValue() ? 1 : 0;

    try {
      return Integer.parseInt(value.toString().trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
