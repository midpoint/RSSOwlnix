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
import org.eclipse.core.runtime.NullProgressMonitor;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small client for the Google Reader API as implemented by FreshRSS, Miniflux
 * and other self-hosted feed readers. The client only depends on the generic
 * connection service so proxies, SSL settings and timeouts are handled by the
 * existing HTTP protocol handler.
 *
 * @author RSSOwl Development Team
 */
public class GReaderClient {

  /* Google Reader API paths */
  private static final String CLIENT_LOGIN_PATH = "/accounts/ClientLogin"; //$NON-NLS-1$
  private static final String API_PATH = "/reader/api/0"; //$NON-NLS-1$
  private static final String SUBSCRIPTION_LIST_PATH = API_PATH + "/subscription/list"; //$NON-NLS-1$
  private static final String STREAM_CONTENTS_PATH = API_PATH + "/stream/contents/"; //$NON-NLS-1$
  private static final String STREAM_ITEM_IDS_PATH = API_PATH + "/stream/items/ids"; //$NON-NLS-1$
  private static final String STREAM_ITEM_CONTENTS_PATH = API_PATH + "/stream/items/contents"; //$NON-NLS-1$
  private static final String TOKEN_PATH = API_PATH + "/token"; //$NON-NLS-1$
  private static final String EDIT_TAG_PATH = API_PATH + "/edit-tag"; //$NON-NLS-1$

  /* Request and response constants */
  private static final String HEADER_AUTHORIZATION = "Authorization"; //$NON-NLS-1$
  private static final String HEADER_ACCEPT_CHARSET = "Accept-Charset"; //$NON-NLS-1$
  private static final String AUTH_HEADER_PREFIX = "GoogleLogin auth="; //$NON-NLS-1$
  private static final String AUTH_RESPONSE_PREFIX = "Auth="; //$NON-NLS-1$
  private static final String ERROR_RESPONSE_PREFIX = "Error="; //$NON-NLS-1$
  private static final String UTF_8 = "UTF-8"; //$NON-NLS-1$

  /* JSON members */
  private static final String RESPONSE_SUBSCRIPTIONS = "subscriptions"; //$NON-NLS-1$
  private static final String RESPONSE_CATEGORIES = "categories"; //$NON-NLS-1$
  private static final String SUBSCRIPTION_ID = "id"; //$NON-NLS-1$
  private static final String SUBSCRIPTION_TITLE = "title"; //$NON-NLS-1$
  private static final String SUBSCRIPTION_URL = "url"; //$NON-NLS-1$
  private static final String SUBSCRIPTION_HTML_URL = "htmlUrl"; //$NON-NLS-1$
  private static final String CATEGORY_LABEL = "label"; //$NON-NLS-1$
  private static final String CATEGORY_ID = "id"; //$NON-NLS-1$
  private static final String LABEL_PATH = "/label/"; //$NON-NLS-1$

  /* JSON members used for the stream/items fallback */
  private static final String RESPONSE_ITEMS = "items"; //$NON-NLS-1$
  private static final String RESPONSE_ITEM_REFS = "itemRefs"; //$NON-NLS-1$
  private static final String RESPONSE_CONTINUATION = "continuation"; //$NON-NLS-1$
  private static final String ITEM_REF_ID = "id"; //$NON-NLS-1$
  private static final String JSON = "json"; //$NON-NLS-1$

  /* Connection timeout */
  private static final int DEFAULT_CON_TIMEOUT = 30000;

  /* Maximum number of items to request from stream/contents */
  private static final int MAX_ITEM_LIMIT = 1000;

  /* Number of item ids to request per stream/items/contents call */
  private static final int ITEM_CONTENTS_BATCH_SIZE = 100;

  /**
   * A feed subscription as returned by the Google Reader API.
   */
  public static final class Subscription {
    public final String streamId;
    public final String title;
    public final String url;
    public final String htmlUrl;
    public final List<String> categories;

    Subscription(String streamId, String title, String url, String htmlUrl, List<String> categories) {
      this.streamId = streamId;
      this.title = title;
      this.url = url;
      this.htmlUrl = htmlUrl;
      this.categories = categories;
    }
  }

  /**
   * The result of connecting to a Google Reader API account.
   */
  public static final class Snapshot {
    public final URI endpoint;
    public final String authToken;
    public final List<Subscription> subscriptions;

    Snapshot(URI endpoint, String authToken, List<Subscription> subscriptions) {
      this.endpoint = endpoint;
      this.authToken = authToken;
      this.subscriptions = subscriptions;
    }
  }

  /**
   * Connects to a Google Reader API server. The entered endpoint is probed
   * with the common endpoint variants and the first one that accepts the
   * credentials is used.
   *
   * @param inputEndpoint the endpoint entered by the user.
   * @param username the Google Reader API user name.
   * @param password the Google Reader API password.
   * @param monitor a progress monitor.
   * @return the resolved endpoint, the authentication token and the
   * subscriptions of the account.
   * @throws ConnectionException in case of any connection or protocol error.
   */
  public Snapshot connect(URI inputEndpoint, String username, String password, IProgressMonitor monitor) throws ConnectionException {
    if (!StringUtils.isSet(username) || !StringUtils.isSet(password))
      throw new ConnectionException(Activator.getDefault().createErrorStatus("Please enter a username and API password for the Google Reader API account.", null)); //$NON-NLS-1$

    ConnectionException lastError = null;
    GReaderAuthenticationException authenticationError = null;

    for (URI candidate : getEndpointCandidates(inputEndpoint)) {
      if (isCanceled(monitor))
        throw new ConnectionException(Status.CANCEL_STATUS);

      try {
        String authToken = login(candidate, username, password, monitor);
        List<Subscription> subscriptions = fetchSubscriptions(candidate, authToken, monitor);
        return new Snapshot(candidate, authToken, subscriptions);
      } catch (GReaderAuthenticationException e) {
        authenticationError = e;
      } catch (GReaderNotFoundException e) {
        lastError = e;
      } catch (ConnectionException e) {
        lastError = e;
      }
    }

    if (authenticationError != null)
      throw authenticationError;

    if (lastError != null)
      throw lastError;

    throw new GReaderNotFoundException(Activator.getDefault().createErrorStatus("Unable to connect to the Google Reader API at " + inputEndpoint + ".", null)); //$NON-NLS-1$ //$NON-NLS-2$
  }

  /**
   * Reads the subscriptions of an account.
   *
   * @param endpoint the resolved Google Reader API endpoint.
   * @param authToken the authentication token.
   * @param monitor a progress monitor.
   * @return the subscriptions of the account.
   * @throws ConnectionException in case of any connection or protocol error.
   */
  public List<Subscription> fetchSubscriptions(URI endpoint, String authToken, IProgressMonitor monitor) throws ConnectionException {
    String content = request(buildEndpointUri(endpoint, SUBSCRIPTION_LIST_PATH + "?output=json&client=RSSOwl"), authToken, false, null, monitor); //$NON-NLS-1$

    JSONObject response = parseJson(content, endpoint);
    JSONArray subscriptionsArray = response.optJSONArray(RESPONSE_SUBSCRIPTIONS);
    if (subscriptionsArray == null)
      throw new GReaderResponseException(Activator.getDefault().createErrorStatus("The server at " + endpoint + " did not return a subscription list. Please check that the Google Reader API is enabled.", null)); //$NON-NLS-1$ //$NON-NLS-2$

    List<Subscription> subscriptions = new ArrayList<>();
    for (int i = 0; i < subscriptionsArray.length(); i++) {
      JSONObject subscriptionObj = subscriptionsArray.optJSONObject(i);
      if (subscriptionObj == null)
        continue;

      String streamId = optString(subscriptionObj, SUBSCRIPTION_ID);
      String url = optString(subscriptionObj, SUBSCRIPTION_URL);
      if (!StringUtils.isSet(streamId) && StringUtils.isSet(url))
        streamId = "feed/" + url; //$NON-NLS-1$
      if (!StringUtils.isSet(streamId))
        continue;

      List<String> categories = new ArrayList<>();
      JSONArray categoriesArray = subscriptionObj.optJSONArray(RESPONSE_CATEGORIES);
      if (categoriesArray != null) {
        for (int j = 0; j < categoriesArray.length(); j++) {
          JSONObject categoryObj = categoriesArray.optJSONObject(j);
          String label = categoryObj != null ? optString(categoryObj, CATEGORY_LABEL) : null;
          if (!StringUtils.isSet(label) && categoryObj != null) {
            String categoryId = optString(categoryObj, CATEGORY_ID);
            if (StringUtils.isSet(categoryId) && categoryId.contains(LABEL_PATH))
              label = categoryId.substring(categoryId.indexOf(LABEL_PATH) + LABEL_PATH.length());
          }

          if (StringUtils.isSet(label) && !categories.contains(label))
            categories.add(label);
        }
      }

      subscriptions.add(new Subscription(streamId, optString(subscriptionObj, SUBSCRIPTION_TITLE), url, optString(subscriptionObj, SUBSCRIPTION_HTML_URL), categories));
    }

    return subscriptions;
  }

  /**
   * Reads the items of a stream.
   *
   * @param endpoint the resolved Google Reader API endpoint.
   * @param authToken the authentication token.
   * @param streamId the stream id of the feed.
   * @param itemLimit the maximum number of items to return.
   * @param dateLimit the oldest item date in seconds, or <code>0</code> for
   * no limit.
   * @param monitor a progress monitor.
   * @return the JSON response of the stream contents request.
   * @throws ConnectionException in case of any connection or protocol error.
   */
  public JSONObject fetchStreamContents(URI endpoint, String authToken, String streamId, int itemLimit, long dateLimit, IProgressMonitor monitor) throws ConnectionException {
    int limit = itemLimit > 0 ? Math.min(itemLimit, MAX_ITEM_LIMIT) : MAX_ITEM_LIMIT;

    StringBuilder query = new StringBuilder();
    query.append("?n=").append(limit); //$NON-NLS-1$
    query.append("&r=n&output=json&client=RSSOwl&ck=").append(System.currentTimeMillis()); //$NON-NLS-1$
    if (dateLimit > 0)
      query.append("&ot=").append(dateLimit); //$NON-NLS-1$

    String path = STREAM_CONTENTS_PATH + encodeStreamPath(streamId) + query;
    try {
      String content = request(buildEndpointUri(endpoint, path), authToken, false, null, monitor);
      JSONObject streamContents = parseStreamContents(content);
      if (streamContents != null)
        return streamContents;
    } catch (GReaderNotFoundException e) {
      /* Some servers, e.g. Miniflux, do not implement stream/contents */
    } catch (GReaderResponseException e) {
      /* Some servers, e.g. Miniflux, answer with an empty JSON array */
    }

    return fetchStreamContentsByItemIds(endpoint, authToken, streamId, limit, dateLimit, monitor);
  }

  /**
   * Fallback for servers that do not implement <code>stream/contents</code>,
   * most notably Miniflux. The item ids are requested first and the item
   * contents are then fetched in batches.
   */
  private JSONObject fetchStreamContentsByItemIds(URI endpoint, String authToken, String streamId, int limit, long dateLimit, IProgressMonitor monitor) throws ConnectionException {
    List<String> itemIds = new ArrayList<>();
    Set<String> seenIds = new HashSet<>();
    String continuation = null;

    while (itemIds.size() < limit) {
      StringBuilder query = new StringBuilder();
      query.append("?s=").append(encodeQueryValue(streamId)); //$NON-NLS-1$
      query.append("&n=").append(limit - itemIds.size()); //$NON-NLS-1$
      query.append("&r=n&output=json&client=RSSOwl"); //$NON-NLS-1$
      if (dateLimit > 0)
        query.append("&ot=").append(dateLimit); //$NON-NLS-1$
      if (StringUtils.isSet(continuation))
        query.append("&c=").append(encodeQueryValue(continuation)); //$NON-NLS-1$

      String content = request(buildEndpointUri(endpoint, STREAM_ITEM_IDS_PATH + query), authToken, false, null, monitor);
      JSONObject response = parseJson(content, endpoint);
      JSONArray itemRefs = response.optJSONArray(RESPONSE_ITEM_REFS);
      if (itemRefs == null)
        throw new GReaderResponseException(Activator.getDefault().createErrorStatus("The server at " + endpoint + " did not return any item ids for the requested feed.", null)); //$NON-NLS-1$ //$NON-NLS-2$

      int added = 0;
      for (int i = 0; i < itemRefs.length() && itemIds.size() < limit; i++) {
        JSONObject itemRef = itemRefs.optJSONObject(i);
        String itemId = itemRef != null ? optString(itemRef, ITEM_REF_ID) : null;
        if (StringUtils.isSet(itemId) && seenIds.add(itemId)) {
          itemIds.add(itemId);
          added++;
        }
      }

      String nextContinuation = optString(response, RESPONSE_CONTINUATION);
      if (added == 0 || !StringUtils.isSet(nextContinuation) || nextContinuation.equals(continuation) || isCanceled(monitor))
        break;

      continuation = nextContinuation;
    }

    JSONArray items = new JSONArray();
    for (int start = 0; start < itemIds.size(); start += ITEM_CONTENTS_BATCH_SIZE) {
      if (isCanceled(monitor))
        break;

      List<String> batch = itemIds.subList(start, Math.min(start + ITEM_CONTENTS_BATCH_SIZE, itemIds.size()));
      Map<String, String[]> parameters = new HashMap<>();
      parameters.put("output", new String[] { JSON }); //$NON-NLS-1$
      parameters.put("i", batch.toArray(new String[batch.size()])); //$NON-NLS-1$

      String content = request(buildEndpointUri(endpoint, STREAM_ITEM_CONTENTS_PATH), authToken, true, parameters, monitor);
      JSONObject response = parseJson(content, endpoint);
      JSONArray batchItems = response.optJSONArray(RESPONSE_ITEMS);
      if (batchItems != null) {
        for (int i = 0; i < batchItems.length(); i++)
          items.put(batchItems.opt(i));
      }
    }

    JSONObject result = new JSONObject();
    try {
      result.put(RESPONSE_ITEMS, items);
    } catch (JSONException e) {
      throw new GReaderResponseException(Activator.getDefault().createErrorStatus(e.getMessage(), e));
    }

    return result;
  }

  private JSONObject parseStreamContents(String content) {
    if (!StringUtils.isSet(content))
      return null;

    try {
      JSONObject json = new JSONObject(new JSONTokener(content));
      return json.optJSONArray(RESPONSE_ITEMS) != null ? json : null;
    } catch (JSONException e) {
      return null;
    }
  }

  private String encodeQueryValue(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); //$NON-NLS-1$ //$NON-NLS-2$
  }

  /**
   * Obtains the CSRF token required for write requests.
   *
   * @param endpoint the resolved Google Reader API endpoint.
   * @param authToken the authentication token.
   * @param monitor a progress monitor.
   * @return the CSRF token.
   * @throws ConnectionException in case of any connection or protocol error.
   */
  public String fetchToken(URI endpoint, String authToken, IProgressMonitor monitor) throws ConnectionException {
    String token = request(buildEndpointUri(endpoint, TOKEN_PATH), authToken, false, null, monitor);
    token = token != null ? token.trim() : null;
    if (!StringUtils.isSet(token))
      throw new GReaderResponseException(Activator.getDefault().createErrorStatus("The server at " + endpoint + " did not return a Google Reader API action token. Please add the account again.", null)); //$NON-NLS-1$ //$NON-NLS-2$

    return token;
  }

  /**
   * Updates the read, unread, starred and label state of items.
   *
   * @param endpoint the resolved Google Reader API endpoint.
   * @param authToken the authentication token.
   * @param actionToken the CSRF token returned by {@link #fetchToken(URI,
   * String, IProgressMonitor)}.
   * @param itemIds the item ids to update.
   * @param tagsToAdd the tags to add.
   * @param tagsToRemove the tags to remove.
   * @param monitor a progress monitor.
   * @throws ConnectionException in case of any connection or protocol error.
   */
  public void editTags(URI endpoint, String authToken, String actionToken, List<String> itemIds, Set<String> tagsToAdd, Set<String> tagsToRemove, IProgressMonitor monitor) throws ConnectionException {
    if (itemIds == null || itemIds.isEmpty())
      return;

    Map<String, String[]> parameters = new HashMap<>();
    parameters.put("T", new String[] { actionToken }); //$NON-NLS-1$
    parameters.put("i", itemIds.toArray(new String[itemIds.size()])); //$NON-NLS-1$

    if (tagsToAdd != null && !tagsToAdd.isEmpty())
      parameters.put("a", tagsToAdd.toArray(new String[tagsToAdd.size()])); //$NON-NLS-1$

    if (tagsToRemove != null && !tagsToRemove.isEmpty())
      parameters.put("r", tagsToRemove.toArray(new String[tagsToRemove.size()])); //$NON-NLS-1$

    request(buildEndpointUri(endpoint, EDIT_TAG_PATH), authToken, true, parameters, monitor);
  }

  private String login(URI endpoint, String username, String password, IProgressMonitor monitor) throws ConnectionException {
    Map<String, String[]> parameters = new HashMap<>();
    parameters.put("accountType", new String[] { "GOOGLE" }); //$NON-NLS-1$ //$NON-NLS-2$
    parameters.put("Email", new String[] { username }); //$NON-NLS-1$
    parameters.put("Passwd", new String[] { password }); //$NON-NLS-1$
    parameters.put("service", new String[] { "reader" }); //$NON-NLS-1$ //$NON-NLS-2$
    parameters.put("source", new String[] { "RSSOwl" }); //$NON-NLS-1$ //$NON-NLS-2$

    String content = request(buildEndpointUri(endpoint, CLIENT_LOGIN_PATH), null, true, parameters, monitor);
    if (content == null)
      throw new GReaderNotFoundException(Activator.getDefault().createErrorStatus("The server at " + endpoint + " did not return a Google Reader API login response.", null)); //$NON-NLS-1$ //$NON-NLS-2$

    String error = null;
    String[] lines = content.split("\\r?\\n"); //$NON-NLS-1$
    for (String line : lines) {
      String trimmed = line.trim();
      if (trimmed.startsWith(AUTH_RESPONSE_PREFIX)) {
        String authToken = trimmed.substring(AUTH_RESPONSE_PREFIX.length()).trim();
        if (StringUtils.isSet(authToken))
          return authToken;
      } else if (trimmed.startsWith(ERROR_RESPONSE_PREFIX)) {
        error = trimmed.substring(ERROR_RESPONSE_PREFIX.length()).trim();
      }
    }

    if (StringUtils.isSet(error))
      throw new GReaderAuthenticationException(Activator.getDefault().createErrorStatus("Google Reader authentication failed for " + endpoint + ": " + error + ". Please check the username and API password.", null)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    throw new GReaderNotFoundException(Activator.getDefault().createErrorStatus("The server at " + endpoint + " did not return a Google Reader API login response. Please check the server URL and that the Google Reader API is enabled.", null)); //$NON-NLS-1$ //$NON-NLS-2$
  }

  private String request(URI uri, String authToken, boolean post, Map<String, String[]> parameters, IProgressMonitor monitor) throws ConnectionException {
    if (isCanceled(monitor))
      throw new ConnectionException(Status.CANCEL_STATUS);

    IProtocolHandler handler = Owl.getConnectionService().getHandler(uri);
    if (handler == null)
      throw new ConnectionException(Activator.getDefault().createErrorStatus("No protocol handler available for " + uri + ".", null)); //$NON-NLS-1$ //$NON-NLS-2$

    Map<String, String> headers = new HashMap<>();
    headers.put(HEADER_ACCEPT_CHARSET, UTF_8.toLowerCase());
    if (StringUtils.isSet(authToken))
      headers.put(HEADER_AUTHORIZATION, AUTH_HEADER_PREFIX + authToken);

    Map<Object, Object> properties = new HashMap<>();
    properties.put(IConnectionPropertyConstants.HEADERS, headers);
    properties.put(IConnectionPropertyConstants.CON_TIMEOUT, DEFAULT_CON_TIMEOUT);
    if (post) {
      properties.put(IConnectionPropertyConstants.POST, Boolean.TRUE);
      if (parameters != null)
        properties.put(IConnectionPropertyConstants.PARAMETERS, parameters);
    }
    if (monitor != null)
      properties.put(IConnectionPropertyConstants.PROGRESS_MONITOR, monitor);

    IProgressMonitor effectiveMonitor = monitor != null ? monitor : new NullProgressMonitor();
    InputStream inS = null;
    try {
      inS = handler.openStream(uri, effectiveMonitor, properties);
      if (inS == null)
        throw new GReaderResponseException(Activator.getDefault().createErrorStatus("The server at " + uri + " returned no response.", null)); //$NON-NLS-1$ //$NON-NLS-2$

      if (isCanceled(monitor))
        throw new ConnectionException(Status.CANCEL_STATUS);

      return StringUtils.readString(new InputStreamReader(inS, StandardCharsets.UTF_8));
    } catch (AuthenticationRequiredException e) {
      throw new GReaderAuthenticationException(Activator.getDefault().createErrorStatus("Google Reader authentication failed for " + uri + ". Please check the username and API password (FreshRSS requires the API password, not the web login password).", e)); //$NON-NLS-1$ //$NON-NLS-2$
    } catch (GReaderAuthenticationException e) {
      throw e;
    } catch (GReaderNotFoundException e) {
      throw e;
    } catch (ConnectionException e) {
      throw classify(e, uri);
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

  private ConnectionException classify(ConnectionException e, URI uri) {
    String message = e.getStatus() != null ? e.getStatus().getMessage() : e.getMessage();
    if (StringUtils.isSet(message)) {
      if (message.contains("404") || message.contains("Not Found")) //$NON-NLS-1$ //$NON-NLS-2$
        return new GReaderNotFoundException(Activator.getDefault().createErrorStatus("The server at " + uri + " does not expose the Google Reader API. Please check the server URL.", e)); //$NON-NLS-1$ //$NON-NLS-2$
      if (message.contains("401") || message.contains("403") || message.contains("Unauthorized") || message.contains("Forbidden")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        return new GReaderAuthenticationException(Activator.getDefault().createErrorStatus("Google Reader authentication failed for " + uri + ". Please check the username and API password.", e)); //$NON-NLS-1$ //$NON-NLS-2$
    }

    return e;
  }

  private JSONObject parseJson(String content, URI endpoint) throws GReaderResponseException {
    if (!StringUtils.isSet(content))
      throw new GReaderResponseException(Activator.getDefault().createErrorStatus("The server at " + endpoint + " returned an empty Google Reader API response.", null)); //$NON-NLS-1$ //$NON-NLS-2$

    try {
      return new JSONObject(new JSONTokener(content));
    } catch (JSONException e) {
      throw new GReaderResponseException(Activator.getDefault().createErrorStatus("The server at " + endpoint + " did not return a valid Google Reader API JSON response. Please check the server URL.", e)); //$NON-NLS-1$ //$NON-NLS-2$
    }
  }

  private List<URI> getEndpointCandidates(URI endpoint) {
    List<URI> candidates = new ArrayList<>();
    addCandidate(candidates, endpoint);

    String path = endpoint.getRawPath();
    if (!StringUtils.isSet(path) || "/".equals(path)) { //$NON-NLS-1$
      addCandidate(candidates, withPath(endpoint, "/api/greader.php")); //$NON-NLS-1$
    } else if (!path.endsWith(".php")) { //$NON-NLS-1$
      if (path.endsWith("/api")) //$NON-NLS-1$
        addCandidate(candidates, withPath(endpoint, path + "/greader.php")); //$NON-NLS-1$
      else
        addCandidate(candidates, withPath(endpoint, path + "/api/greader.php")); //$NON-NLS-1$
    }

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
      return new URI(endpoint.getScheme() + "://" + endpoint.getRawAuthority() + path); //$NON-NLS-1$
    } catch (URISyntaxException e) {
      return null;
    }
  }

  private URI buildEndpointUri(URI endpoint, String pathAndQuery) throws ConnectionException {
    try {
      return new URI(endpoint.toASCIIString() + pathAndQuery);
    } catch (URISyntaxException e) {
      throw new ConnectionException(Activator.getDefault().createErrorStatus(e.getMessage(), e));
    }
  }

  private String encodeStreamPath(String streamId) {
    if (!StringUtils.isSet(streamId))
      return ""; //$NON-NLS-1$

    if (streamId.startsWith("feed/")) { //$NON-NLS-1$
      return "feed/" + encodePathValue(streamId.substring(5), false); //$NON-NLS-1$
    }

    return encodePathValue(streamId, true);
  }

  private String encodePathValue(String value, boolean keepSlashes) {
    String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); //$NON-NLS-1$ //$NON-NLS-2$
    if (keepSlashes)
      encoded = encoded.replace("%2F", "/"); //$NON-NLS-1$ //$NON-NLS-2$
    else
      encoded = encoded.replace("%2f", "%2F"); //$NON-NLS-1$ //$NON-NLS-2$

    return encoded;
  }

  private String optString(JSONObject obj, String key) {
    Object value = obj.has(key) ? obj.opt(key) : null;
    if (value == null || JSONObject.NULL.equals(value))
      return null;

    String result = value.toString();
    return StringUtils.isSet(result) ? result : null;
  }

  private boolean isCanceled(IProgressMonitor monitor) {
    return monitor != null && monitor.isCanceled();
  }

  private static final class GReaderAuthenticationException extends ConnectionException {
    GReaderAuthenticationException(IStatus status) {
      super(status);
    }
  }

  private static final class GReaderNotFoundException extends ConnectionException {
    GReaderNotFoundException(IStatus status) {
      super(status);
    }
  }

  private static final class GReaderResponseException extends ConnectionException {
    GReaderResponseException(IStatus status) {
      super(status);
    }
  }
}
