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

package org.rssowl.ui.internal.dialogs.bookmark;

import org.rssowl.core.persist.IBookMark;
import org.rssowl.core.persist.IFolder;
import org.rssowl.core.persist.IMark;
import org.rssowl.core.util.FeverUtils;
import org.rssowl.core.util.GReaderUtils;
import org.rssowl.core.util.SyncUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Identifies the synchronization account a selected folder or bookmark belongs
 * to.
 *
 * @author RSSOwl Development Team
 */
public final class SyncAccountTarget {

  /** The Fever protocol identifier. */
  public static final String PROTOCOL_FEVER = "fever"; //$NON-NLS-1$

  /** The Google Reader protocol identifier. */
  public static final String PROTOCOL_GREADER = "greader"; //$NON-NLS-1$

  /** The protocol of the account. */
  public final String protocol;

  /** The HTTP(S) endpoint of the account. */
  public final URI endpoint;

  private SyncAccountTarget(String protocol, URI endpoint) {
    this.protocol = protocol;
    this.endpoint = endpoint;
  }

  /**
   * @param element the selected entity.
   * @return the synchronization account or <code>null</code> if the selection
   * is not part of a Fever or Google Reader account.
   */
  public static SyncAccountTarget fromSelection(Object element) {
    if (element instanceof IBookMark)
      return fromBookMark((IBookMark) element);

    if (element instanceof IFolder)
      return fromFolder((IFolder) element);

    return null;
  }

  /**
   * @param link a feed link.
   * @return the synchronization account or <code>null</code> if the link does
   * not belong to a Fever or Google Reader account.
   */
  public static SyncAccountTarget fromLink(URI link) {
    if (link == null)
      return null;

    String linkText = link.toString();
    try {
      if (SyncUtils.isFever(linkText))
        return new SyncAccountTarget(PROTOCOL_FEVER, FeverUtils.toEndpoint(link));

      if (SyncUtils.isGReader(linkText))
        return new SyncAccountTarget(PROTOCOL_GREADER, GReaderUtils.toEndpoint(link));
    } catch (URISyntaxException e) {
      return null;
    }

    return null;
  }

  private static SyncAccountTarget fromBookMark(IBookMark bookMark) {
    return fromLink(bookMark.getFeedLinkReference().getLink());
  }

  private static SyncAccountTarget fromFolder(IFolder folder) {
    Set<SyncAccountTarget> accounts = new LinkedHashSet<>();
    collectAccounts(folder, accounts);

    if (accounts.size() == 1)
      return accounts.iterator().next();

    return null;
  }

  private static void collectAccounts(IFolder folder, Set<SyncAccountTarget> accounts) {
    for (IMark mark : folder.getMarks()) {
      if (mark instanceof IBookMark) {
        SyncAccountTarget account = fromBookMark((IBookMark) mark);
        if (account != null)
          accounts.add(account);
      }
    }

    for (IFolder child : folder.getFolders())
      collectAccounts(child, accounts);
  }

  /**
   * @return <code>true</code> if this is a Fever account.
   */
  public boolean isFever() {
    return PROTOCOL_FEVER.equals(protocol);
  }

  /**
   * @return a human readable name of the protocol.
   */
  public String getProtocolName() {
    return isFever() ? "Fever" : "Google Reader"; //$NON-NLS-1$ //$NON-NLS-2$
  }

  /*
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;

    if (!(obj instanceof SyncAccountTarget))
      return false;

    SyncAccountTarget other = (SyncAccountTarget) obj;
    return protocol.equals(other.protocol) && endpoint.equals(other.endpoint);
  }

  /*
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    return 31 * protocol.hashCode() + endpoint.hashCode();
  }
}
