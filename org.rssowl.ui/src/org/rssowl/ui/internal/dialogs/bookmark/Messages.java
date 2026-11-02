/*   **********************************************************************  **
 **   Copyright notice                                                       **
 **                                                                          **
 **   (c) 2005-2009 RSSOwl Development Team                                  **
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

import org.eclipse.osgi.util.NLS;
import org.rssowl.core.util.ExternalMessages;

public class Messages extends NLS {
  private static final String BUNDLE_NAME = "org.rssowl.ui.internal.dialogs.bookmark.messages"; //$NON-NLS-1$
  public static String BookmarkDefinitionPage_CREATE_BOOKMARK;
  public static String BookmarkDefinitionPage_LOCATION;
  public static String BookmarkDefinitionPage_NAME;
  public static String BookmarkDefinitionPage_UNABLE_LOAD_TITLE;
  public static String CreateBookmarkWizard_BOOKMARK;
  public static String CreateBookmarkWizard_ENTER_VALID_LINK;
  public static String CreateBookmarkWizard_LOADING_TITLE;
  public static String CreateBookmarkWizard_NEW_BOOKMARK;
  public static String FeedDefinitionPage_BOOKMARK_EXISTS;
  public static String FeedDefinitionPage_CREATE_BOOKMARK;
  public static String FeedDefinitionPage_CREATE_FEED;
  public static String FeedDefinitionPage_CREATE_FEED_DIRECT;
  public static String FeedDefinitionPage_CREATE_KEYWORD_FEED;
  public static String FeedDefinitionPage_IMPORT_WIZARD_TIP;
  public static String FeedDefinitionPage_USE_TITLE_OF_FEED;
  public static String FeverAccountWizard_ACCOUNT_NAME;
  public static String FeverAccountWizard_API_KEY;
  public static String FeverAccountWizard_CONNECTED;
  public static String FeverAccountWizard_CONNECTING;
  public static String FeverAccountWizard_DESCRIPTION;
  public static String FeverAccountWizard_FAILED;
  public static String FeverAccountWizard_FEED;
  public static String FeverAccountWizard_GROUP;
  public static String FeverAccountWizard_INVALID_SERVER;
  public static String FeverAccountWizard_LOCATION;
  public static String FeverAccountWizard_NO_FEEDS;
  public static String FeverAccountWizard_NO_LOCATION;
  public static String FeverAccountWizard_SERVER;
  public static String FeverAccountWizard_TEST;
  public static String FeverAccountWizard_TITLE;
  public static String FeverAccountWizard_UNKNOWN_ERROR;
  public static String FeverAccountWizard_USERNAME;
  public static String GReaderAccountWizard_ACCOUNT_NAME;
  public static String GReaderAccountWizard_CONNECTED;
  public static String GReaderAccountWizard_CONNECTING;
  public static String GReaderAccountWizard_DESCRIPTION;
  public static String GReaderAccountWizard_FAILED;
  public static String GReaderAccountWizard_FEED;
  public static String GReaderAccountWizard_GROUP;
  public static String GReaderAccountWizard_INVALID_SERVER;
  public static String GReaderAccountWizard_LOCATION;
  public static String GReaderAccountWizard_NO_FEEDS;
  public static String GReaderAccountWizard_NO_LOCATION;
  public static String GReaderAccountWizard_PASSWORD;
  public static String GReaderAccountWizard_SERVER;
  public static String GReaderAccountWizard_TEST;
  public static String GReaderAccountWizard_TITLE;
  public static String GReaderAccountWizard_UNKNOWN_ERROR;
  public static String GReaderAccountWizard_USERNAME;
  public static String SyncAccountDialog_API_KEY;
  public static String SyncAccountDialog_CONNECTED;
  public static String SyncAccountDialog_CONNECTING;
  public static String SyncAccountDialog_DESCRIPTION;
  public static String SyncAccountDialog_ENDPOINT_CHANGED;
  public static String SyncAccountDialog_ENDPOINT_CHANGED_TITLE;
  public static String SyncAccountDialog_ENTER_FEVER_KEY;
  public static String SyncAccountDialog_ENTER_GREADER_PASSWORD;
  public static String SyncAccountDialog_ENTER_USERNAME;
  public static String SyncAccountDialog_FAILED;
  public static String SyncAccountDialog_INVALID_SERVER;
  public static String SyncAccountDialog_KEEP_FEVER;
  public static String SyncAccountDialog_KEEP_GREADER;
  public static String SyncAccountDialog_PASSWORD;
  public static String SyncAccountDialog_SERVER;
  public static String SyncAccountDialog_TEST;
  public static String SyncAccountDialog_TITLE;
  public static String SyncAccountDialog_UNKNOWN_ERROR;
  public static String SyncAccountDialog_UPDATE_FAILED;
  public static String SyncAccountDialog_USERNAME;
  public static String SyncAccountDialog_USERNAME_NEEDS_PASSWORD;
  public static String SyncAccountDialog_USERNAME_OPTIONAL;
  public static String KeywordSubscriptionPage_CREATE_BOOKMARK;
  public static String KeywordSubscriptionPage_N_ON_M;
  public static String KeywordSubscriptionPage_SELECT_SEARCH_ENGINE;

  private Messages() {}

  static {
    // initialize resource bundle
    ExternalMessages.initializeMessages(BUNDLE_NAME, Messages.class);
  }
}
