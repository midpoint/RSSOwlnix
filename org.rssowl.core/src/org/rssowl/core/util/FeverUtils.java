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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utilities shared by the Fever API client, protocol handler and UI.
 *
 * @author RSSOwl Development Team
 */
public final class FeverUtils {

  /* Parameter used inside Fever feed links */
  private static final String FEED_ID_PARAM = "feed_id"; //$NON-NLS-1$

  /* Realm used to store the API key in the credentials provider */
  private static final String FEVER_REALM = "fever"; //$NON-NLS-1$

  /* Username stored alongside the API key */
  private static final String FEVER_USERNAME = "fever"; //$NON-NLS-1$

  private FeverUtils() {}

  /**
   * Normalizes a user provided Fever endpoint. A missing protocol is completed
   * with HTTPS, query and fragment are removed since RSSOwl manages the Fever
   * request parameters itself.
   *
   * @param link the endpoint as entered by the user.
   * @return the normalized endpoint.
   * @throws URISyntaxException if the link is not a valid HTTP(S) URL.
   */
  public static URI normalizeEndpoint(String link) throws URISyntaxException {
    if (!StringUtils.isSet(link))
      throw new URISyntaxException(String.valueOf(link), "Missing Fever endpoint"); //$NON-NLS-1$

    String value = link.trim();
    if (!value.contains("://")) //$NON-NLS-1$
      value = URIUtils.HTTPS + value;

    URI uri = new URI(URIUtils.fastEncode(value));
    if (!URIUtils.HTTP_SCHEME.equalsIgnoreCase(uri.getScheme()) && !URIUtils.HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme()))
      throw new URISyntaxException(link, "Fever endpoint must use HTTP or HTTPS"); //$NON-NLS-1$
    if (!StringUtils.isSet(uri.getHost()))
      throw new URISyntaxException(link, "Fever endpoint is missing a host"); //$NON-NLS-1$

    return new URI(uri.getScheme().toLowerCase(), uri.getUserInfo(), uri.getHost().toLowerCase(), uri.getPort(), uri.getPath(), null, null);
  }

  /**
   * Computes the Fever API key from a user name and password. FreshRSS expects
   * the MD5 hash of <code>username:password</code> as the api key. Most other
   * Fever implementations hand out the API key directly, in which case the
   * user name has to be left empty.
   *
   * @param username the user name, or <code>null</code>/empty if the given
   * value already is an API key.
   * @param passwordOrKey the password for the user name, or the API key itself.
   * @return the API key to send to the server.
   */
  public static String toApiKey(String username, String passwordOrKey) {
    if (!StringUtils.isSet(username) || !StringUtils.isSet(passwordOrKey))
      return passwordOrKey;

    try {
      MessageDigest digest = MessageDigest.getInstance("MD5"); //$NON-NLS-1$
      byte[] hash = digest.digest((username + ':' + passwordOrKey).getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(hash.length * 2);
      for (byte value : hash) {
        int unsigned = value & 0xff;
        if (unsigned < 0x10)
          result.append('0');
        result.append(Integer.toHexString(unsigned));
      }

      return result.toString();
    } catch (NoSuchAlgorithmException e) {
      return passwordOrKey;
    }
  }

  /**
   * Creates the link of a single feed inside a Fever account.
   *
   * @param endpoint the normalized Fever endpoint.
   * @param feedId the Fever feed id.
   * @return the link to store in the {@link org.rssowl.core.persist.IFeed}.
   * @throws URISyntaxException if the link cannot be created.
   */
  public static URI createFeedLink(URI endpoint, long feedId) throws URISyntaxException {
    String scheme = URIUtils.HTTPS_SCHEME.equalsIgnoreCase(endpoint.getScheme()) ? SyncUtils.FEVER_HTTPS_SCHEME : SyncUtils.FEVER_HTTP_SCHEME;
    return new URI(scheme, endpoint.getUserInfo(), endpoint.getHost(), endpoint.getPort(), endpoint.getPath(), FEED_ID_PARAM + "=" + feedId, null); //$NON-NLS-1$
  }

  /**
   * @param link a Fever feed link.
   * @return the HTTP(S) endpoint of the Fever installation.
   * @throws URISyntaxException if the link is not a valid Fever feed link.
   */
  public static URI toEndpoint(URI link) throws URISyntaxException {
    String scheme = link.getScheme();
    if (!SyncUtils.FEVER_HTTP_SCHEME.equalsIgnoreCase(scheme) && !SyncUtils.FEVER_HTTPS_SCHEME.equalsIgnoreCase(scheme))
      throw new URISyntaxException(String.valueOf(link), "Not a Fever feed link"); //$NON-NLS-1$

    String httpScheme = SyncUtils.FEVER_HTTPS_SCHEME.equalsIgnoreCase(scheme) ? URIUtils.HTTPS_SCHEME : URIUtils.HTTP_SCHEME;
    return new URI(httpScheme, link.getUserInfo(), link.getHost(), link.getPort(), link.getPath(), null, null);
  }

  /**
   * @param link a Fever feed link.
   * @return the feed id encoded in the link or <code>-1</code> if none.
   */
  public static long getFeedId(URI link) {
    String query = link.getQuery();
    if (!StringUtils.isSet(query))
      return -1;

    String[] parameters = query.split("&"); //$NON-NLS-1$
    for (String parameter : parameters) {
      int separator = parameter.indexOf('=');
      String name = separator >= 0 ? parameter.substring(0, separator) : parameter;
      if (!FEED_ID_PARAM.equals(name))
        continue;

      String value = separator >= 0 ? parameter.substring(separator + 1) : ""; //$NON-NLS-1$
      try {
        return Long.parseLong(URLDecoder.decode(value, StandardCharsets.UTF_8.name()));
      } catch (Exception e) {
        return -1;
      }
    }

    return -1;
  }

  /**
   * Returns the API key that was stored for the given Fever endpoint.
   *
   * @param endpoint the HTTP(S) endpoint of the Fever installation.
   * @return the API key or <code>null</code> if none is stored.
   */
  public static String getApiKey(URI endpoint) {
    ICredentialsProvider provider = Owl.getConnectionService().getCredentialsProvider(endpoint);
    if (provider == null)
      return null;

    try {
      ICredentials credentials = provider.getAuthCredentials(endpoint, FEVER_REALM);
      return credentials != null ? credentials.getPassword() : null;
    } catch (CredentialsException e) {
      return null;
    }
  }

  /**
   * Stores the API key for the given Fever endpoint in the credentials
   * provider.
   *
   * @param endpoint the HTTP(S) endpoint of the Fever installation.
   * @param apiKey the API key to store.
   * @throws CredentialsException if storing the key fails.
   */
  public static void setApiKey(URI endpoint, final String apiKey) throws CredentialsException {
    ICredentialsProvider provider = Owl.getConnectionService().getCredentialsProvider(endpoint);
    if (provider == null)
      throw new CredentialsException(org.rssowl.core.internal.Activator.getDefault().createErrorStatus("No credentials provider available for " + endpoint, null)); //$NON-NLS-1$

    provider.setAuthCredentials(new ICredentials() {
      @Override
      public String getUsername() {
        return FEVER_USERNAME;
      }

      @Override
      public String getPassword() {
        return apiKey;
      }

      @Override
      public String getDomain() {
        return null;
      }
    }, endpoint, FEVER_REALM);
  }

  /**
   * Removes a stored API key.
   *
   * @param endpoint the HTTP(S) endpoint of the Fever installation.
   */
  public static void deleteApiKey(URI endpoint) {
    ICredentialsProvider provider = Owl.getConnectionService().getCredentialsProvider(endpoint);
    if (provider != null) {
      try {
        provider.deleteAuthCredentials(endpoint, FEVER_REALM);
      } catch (CredentialsException e) {
        /* Ignore */
      }
    }
  }
}
