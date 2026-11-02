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
import org.rssowl.core.util.GReaderUtils;
import org.rssowl.core.util.StringUtils;
import org.rssowl.core.util.SyncItem;
import org.rssowl.core.util.SyncUtils;
import org.rssowl.core.util.Triple;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/**
 * Protocol handler for feeds of a Google Reader API account. A feed link has
 * the form <code>greader://host/path?stream=feed/123</code>. The handler
 * resolves the link against the Google Reader API and reuses the existing
 * JSON interpreter for the returned stream contents.
 *
 * @author RSSOwl Development Team
 */
public class GReaderProtocolHandler extends DefaultProtocolHandler {

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
      endpoint = GReaderUtils.toEndpoint(link);
    } catch (URISyntaxException e) {
      throw new ConnectionException(Activator.getDefault().createErrorStatus(e.getMessage(), e));
    }

    String streamId = GReaderUtils.getStreamId(link);
    if (!StringUtils.isSet(streamId))
      throw new ConnectionException(Activator.getDefault().createErrorStatus("The Google Reader feed link is missing a stream id.", null)); //$NON-NLS-1$

    String authToken = GReaderUtils.getAuthToken(endpoint);
    if (!StringUtils.isSet(authToken)) {
      URI finalEndpoint = endpoint;
      throw new ConnectionException(Activator.getDefault().createErrorStatus("No Google Reader API token is stored for " + finalEndpoint + ". Please create the Google Reader account again.", null)); //$NON-NLS-1$ //$NON-NLS-2$
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
        dateLimit = (Long) dateLimitObj / 1000; //Google Reader expects seconds here

      Object uncommittedObj = properties.get(IConnectionPropertyConstants.UNCOMMITTED_ITEMS);
      if (uncommittedObj instanceof Map)
        uncommittedItems = (Map<String, SyncItem>) uncommittedObj;
    }

    GReaderClient client = new GReaderClient();
    org.rssowl.core.internal.interpreter.json.JSONObject json = client.fetchStreamContents(endpoint, authToken, streamId, itemLimit, dateLimit, monitor);

    if (monitor != null && monitor.isCanceled())
      return null;

    IModelFactory factory = Owl.getModelFactory();
    IFeed feed = factory.createFeed(null, link);
    feed.setBase(endpoint);
    Owl.getInterpreter().interpretJSONObject(json, feed);

    if (!StringUtils.isSet(feed.getTitle()))
      feed.setTitle(endpoint.getHost());

    /* Apply changes that have not been sent to the server yet */
    if (uncommittedItems != null && !uncommittedItems.isEmpty()) {
      List<INews> news = feed.getNews();
      for (INews item : news) {
        if (item.getGuid() == null || item.getGuid().getValue() == null)
          continue;

        String guid = item.getGuid().getValue();
        SyncItem syncItem = uncommittedItems.get(SyncItem.toKey(link.toString(), guid));
        if (syncItem == null)
          syncItem = uncommittedItems.get(guid); //Legacy entries that are keyed by id only
        if (syncItem != null)
          syncItem.applyTo(item);
      }
    }

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
    return link.getHost();
  }

  /*
   * @see
   * org.rssowl.core.internal.connection.DefaultProtocolHandler#getFeed(java.
   * net.URI, org.eclipse.core.runtime.IProgressMonitor)
   */
  @Override
  public URI getFeed(URI website, IProgressMonitor monitor) {
    return website != null && SyncUtils.isGReader(website.toString()) ? website : null;
  }

  /*
   * @see
   * org.rssowl.core.internal.connection.DefaultProtocolHandler#getURLStreamHandler()
   */
  @Override
  public URLStreamHandlerService getURLStreamHandler() {
    return null;
  }
}
