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

package org.rssowl.core.util;

import org.rssowl.core.Owl;
import org.rssowl.core.connection.CredentialsException;
import org.rssowl.core.connection.ICredentials;
import org.rssowl.core.connection.ICredentialsProvider;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Utilities shared by the Google Reader API client, protocol handler and UI.
 *
 * @author RSSOwl Development Team
 */
public final class GReaderUtils {

  /* Parameter used inside Google Reader feed links */
  private static final String STREAM_PARAM = "stream"; //$NON-NLS-1$

  /* Realm used to store the authentication token in the credentials provider */
  private static final String GREADER_REALM = "greader"; //$NON-NLS-1$

  /* Username stored when the account has no user name */
  private static final String DEFAULT_USERNAME = "greader"; //$NON-NLS-1$

  private GReaderUtils() {}

  /**
   * Normalizes a user provided Google Reader API endpoint. A missing protocol
   * is completed with HTTPS. Query and fragment are removed because RSSOwl
   * manages the API request parameters itself.
   *
   * @param link the endpoint as entered by the user.
   * @return the normalized endpoint.
   * @throws URISyntaxException if the link is not a valid HTTP(S) URL.
   */
  public static URI normalizeEndpoint(String link) throws URISyntaxException {
    if (!StringUtils.isSet(link))
      throw new URISyntaxException(String.valueOf(link), "Missing Google Reader API endpoint"); //$NON-NLS-1$

    String value = link.trim();
    if (!value.contains("://")) //$NON-NLS-1$
      value = URIUtils.HTTPS + value;

    URI uri = new URI(URIUtils.fastEncode(value));
    if (!URIUtils.HTTP_SCHEME.equalsIgnoreCase(uri.getScheme()) && !URIUtils.HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme()))
      throw new URISyntaxException(link, "Google Reader API endpoint must use HTTP or HTTPS"); //$NON-NLS-1$
    if (!StringUtils.isSet(uri.getHost()))
      throw new URISyntaxException(link, "Google Reader API endpoint is missing a host"); //$NON-NLS-1$

    String path = uri.getRawPath();
    if (!StringUtils.isSet(path) || "/".equals(path)) //$NON-NLS-1$
      path = ""; //$NON-NLS-1$

    /* Remove a trailing slash so that API paths can simply be appended */
    while (path.endsWith("/") && path.length() > 1) //$NON-NLS-1$
      path = path.substring(0, path.length() - 1);

    StringBuilder result = new StringBuilder();
    result.append(uri.getScheme().toLowerCase(Locale.ROOT));
    result.append("://"); //$NON-NLS-1$
    result.append(formatHost(uri.getHost()));
    if (uri.getPort() >= 0)
      result.append(':').append(uri.getPort());
    result.append(path);

    return new URI(result.toString());
  }

  /**
   * @param link a Google Reader feed link.
   * @return the HTTP(S) endpoint of the Google Reader API installation.
   * @throws URISyntaxException if the link is not a valid Google Reader feed
   * link.
   */
  public static URI toEndpoint(URI link) throws URISyntaxException {
    if (!isGReaderLink(link))
      throw new URISyntaxException(String.valueOf(link), "Not a Google Reader API feed link"); //$NON-NLS-1$

    String httpScheme = SyncUtils.GREADER_HTTPS_SCHEME.equalsIgnoreCase(link.getScheme()) ? URIUtils.HTTPS_SCHEME : URIUtils.HTTP_SCHEME;
    StringBuilder result = new StringBuilder();
    result.append(httpScheme).append("://").append(formatHost(link.getHost())); //$NON-NLS-1$
    if (link.getPort() >= 0)
      result.append(':').append(link.getPort());

    String path = link.getRawPath();
    if (StringUtils.isSet(path))
      result.append(path);

    return new URI(result.toString());
  }

  /**
   * @param link a link to check.
   * @return <code>true</code> if the link uses one of the Google Reader API
   * schemes.
   */
  public static boolean isGReaderLink(URI link) {
    if (link == null)
      return false;

    String scheme = link.getScheme();
    return SyncUtils.GREADER_HTTP_SCHEME.equalsIgnoreCase(scheme) || SyncUtils.GREADER_HTTPS_SCHEME.equalsIgnoreCase(scheme);
  }

  /**
   * Creates the link of a single feed inside a Google Reader API account.
   *
   * @param endpoint the normalized Google Reader API endpoint.
   * @param streamId the stream id as returned by the subscription list.
   * @return the link to store in the {@link org.rssowl.core.persist.IFeed}.
   * @throws URISyntaxException if the link cannot be created.
   */
  public static URI createFeedLink(URI endpoint, String streamId) throws URISyntaxException {
    if (!StringUtils.isSet(streamId))
      throw new URISyntaxException(String.valueOf(streamId), "Missing Google Reader stream id"); //$NON-NLS-1$

    String scheme = URIUtils.HTTPS_SCHEME.equalsIgnoreCase(endpoint.getScheme()) ? SyncUtils.GREADER_HTTPS_SCHEME : SyncUtils.GREADER_HTTP_SCHEME;
    String encodedStreamId = URLEncoder.encode(streamId, StandardCharsets.UTF_8).replace("+", "%20"); //$NON-NLS-1$ //$NON-NLS-2$

    StringBuilder result = new StringBuilder();
    result.append(scheme).append("://").append(formatHost(endpoint.getHost())); //$NON-NLS-1$
    if (endpoint.getPort() >= 0)
      result.append(':').append(endpoint.getPort());

    String path = endpoint.getRawPath();
    if (StringUtils.isSet(path))
      result.append(path);

    result.append('?').append(STREAM_PARAM).append('=').append(encodedStreamId);
    return new URI(result.toString());
  }

  /**
   * @param link a Google Reader feed link.
   * @return the stream id encoded in the link or <code>null</code> if none.
   */
  public static String getStreamId(URI link) {
    String query = link.getRawQuery();
    if (!StringUtils.isSet(query))
      return null;

    String[] parameters = query.split("&"); //$NON-NLS-1$
    for (String parameter : parameters) {
      int separator = parameter.indexOf('=');
      String name = separator >= 0 ? parameter.substring(0, separator) : parameter;
      if (!STREAM_PARAM.equals(name))
        continue;

      String value = separator >= 0 ? parameter.substring(separator + 1) : ""; //$NON-NLS-1$
      if (!StringUtils.isSet(value))
        return null;

      try {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
      } catch (Exception e) {
        return null;
      }
    }

    return null;
  }

  /**
   * Returns the authentication token that was stored for the given Google
   * Reader API endpoint.
   *
   * @param endpoint the HTTP(S) endpoint of the Google Reader API.
   * @return the authentication token or <code>null</code> if none is stored.
   */
  public static String getAuthToken(URI endpoint) {
    ICredentials credentials = getCredentials(endpoint);
    return credentials != null ? credentials.getPassword() : null;
  }

  /**
   * Returns the user name that was stored for the given Google Reader API
   * endpoint.
   *
   * @param endpoint the HTTP(S) endpoint of the Google Reader API.
   * @return the user name or <code>null</code> if none is stored.
   */
  public static String getUsername(URI endpoint) {
    ICredentials credentials = getCredentials(endpoint);
    return credentials != null ? credentials.getUsername() : null;
  }

  /**
   * Stores the authentication token for the given Google Reader API endpoint
   * in the credentials provider. The password used to obtain the token is
   * intentionally not stored.
   *
   * @param endpoint the HTTP(S) endpoint of the Google Reader API.
   * @param username the user name of the account.
   * @param authToken the authentication token returned by ClientLogin.
   * @throws CredentialsException if storing the token fails.
   */
  public static void setAuthToken(URI endpoint, final String username, final String authToken) throws CredentialsException {
    ICredentialsProvider provider = Owl.getConnectionService().getCredentialsProvider(endpoint);
    if (provider == null)
      throw new CredentialsException(org.rssowl.core.internal.Activator.getDefault().createErrorStatus("No credentials provider available for " + endpoint, null)); //$NON-NLS-1$

    provider.setAuthCredentials(new ICredentials() {
      @Override
      public String getUsername() {
        return StringUtils.isSet(username) ? username : DEFAULT_USERNAME;
      }

      @Override
      public String getPassword() {
        return authToken;
      }

      @Override
      public String getDomain() {
        return null;
      }
    }, endpoint, GREADER_REALM);
  }

  /**
   * Removes a stored authentication token.
   *
   * @param endpoint the HTTP(S) endpoint of the Google Reader API.
   */
  public static void deleteAuthToken(URI endpoint) {
    ICredentialsProvider provider = Owl.getConnectionService().getCredentialsProvider(endpoint);
    if (provider != null) {
      try {
        provider.deleteAuthCredentials(endpoint, GREADER_REALM);
      } catch (CredentialsException e) {
        /* Ignore */
      }
    }
  }

  private static ICredentials getCredentials(URI endpoint) {
    ICredentialsProvider provider = Owl.getConnectionService().getCredentialsProvider(endpoint);
    if (provider == null)
      return null;

    try {
      return provider.getAuthCredentials(endpoint, GREADER_REALM);
    } catch (CredentialsException e) {
      return null;
    }
  }

  private static String formatHost(String host) {
    if (!StringUtils.isSet(host))
      return ""; //$NON-NLS-1$

    if (host.startsWith("[") && host.endsWith("]")) //$NON-NLS-1$ //$NON-NLS-2$
      return host;

    if (host.indexOf(':') >= 0)
      return '[' + host + ']';

    return host.toLowerCase(Locale.ROOT);
  }
}
