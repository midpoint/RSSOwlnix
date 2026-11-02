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

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.osgi.util.NLS;
import org.rssowl.core.Owl;
import org.rssowl.core.connection.CredentialsException;
import org.rssowl.core.internal.connection.FeverClient;
import org.rssowl.core.persist.IBookMark;
import org.rssowl.core.persist.IFolder;
import org.rssowl.core.persist.IMark;
import org.rssowl.core.persist.IModelFactory;
import org.rssowl.core.persist.dao.IBookMarkDAO;
import org.rssowl.core.persist.dao.IFeedDAO;
import org.rssowl.core.persist.dao.OwlDAO;
import org.rssowl.core.persist.reference.FeedLinkReference;
import org.rssowl.core.util.FeverUtils;
import org.rssowl.core.util.StringUtils;
import org.rssowl.ui.internal.Activator;
import org.rssowl.ui.internal.actions.ReloadTypesAction;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A wizard that connects to a server implementing the Fever API and creates a
 * bookmark for every feed of the account inside folders that mirror the Fever
 * groups.
 *
 * @author RSSOwl Development Team
 */
public class FeverAccountWizard extends Wizard {

  private final IFolder fParent;
  private FeverAccountPage fPage;

  private URI fTestedInputEndpoint;
  private URI fTestedEndpoint;
  private String fTestedApiKey;
  private FeverClient.Snapshot fTestedSnapshot;

  /**
   * @param parent the folder to create the account folder in.
   */
  public FeverAccountWizard(IFolder parent) {
    fParent = parent;
    setWindowTitle(Messages.FeverAccountWizard_TITLE);
    setHelpAvailable(false);
  }

  /*
   * @see org.eclipse.jface.wizard.Wizard#addPages()
   */
  @Override
  public void addPages() {
    fPage = new FeverAccountPage(fParent);
    addPage(fPage);
  }

  /*
   * @see org.eclipse.jface.wizard.Wizard#performFinish()
   */
  @Override
  public boolean performFinish() {
    final String endpointText = fPage.getEndpoint();
    final String username = fPage.getUsername();
    final String apiKey = FeverUtils.toApiKey(username, fPage.getApiKey());
    final String accountName = fPage.getAccountName();

    URI endpoint;
    try {
      endpoint = FeverUtils.normalizeEndpoint(endpointText);
    } catch (Exception e) {
      fPage.setErrorMessage(Messages.FeverAccountWizard_INVALID_SERVER);
      return false;
    }

    if (fPage.getFolder() == null) {
      fPage.setErrorMessage(Messages.FeverAccountWizard_NO_LOCATION);
      return false;
    }

    final URI finalEndpoint = endpoint;
    final FeverClient.Snapshot[] snapshot = new FeverClient.Snapshot[1];
    final URI[] resolvedEndpoint = new URI[1];

    /* Fetch the account structure unless it was already loaded by a test */
    if (fTestedSnapshot == null || !finalEndpoint.equals(fTestedInputEndpoint) || !apiKey.equals(fTestedApiKey)) {
      try {
        getContainer().run(true, true, new org.eclipse.jface.operation.IRunnableWithProgress() {
          @Override
          public void run(IProgressMonitor monitor) {
            monitor.beginTask(Messages.FeverAccountWizard_CONNECTING, IProgressMonitor.UNKNOWN);
            try {
              FeverClient client = new FeverClient();
              snapshot[0] = client.fetchSnapshot(finalEndpoint, apiKey, monitor);
              resolvedEndpoint[0] = client.getResolvedEndpoint();
            } catch (Exception e) {
              throw new RuntimeException(e);
            } finally {
              monitor.done();
            }
          }
        });
      } catch (InvocationTargetException e) {
        fPage.setErrorMessage(getErrorMessage(e));
        return false;
      } catch (InterruptedException e) {
        return false;
      }
    } else {
      snapshot[0] = fTestedSnapshot;
      resolvedEndpoint[0] = fTestedEndpoint;
    }

    if (snapshot[0] == null || snapshot[0].feeds.isEmpty()) {
      fPage.setErrorMessage(Messages.FeverAccountWizard_NO_FEEDS);
      return false;
    }

    URI accountEndpoint = resolvedEndpoint[0] != null ? resolvedEndpoint[0] : finalEndpoint;

    /* Store the API key in the secure credentials store */
    try {
      FeverUtils.setApiKey(accountEndpoint, apiKey);
    } catch (CredentialsException e) {
      fPage.setErrorMessage(e.getMessage());
      return false;
    }

    createBookMarks(accountEndpoint, accountName, snapshot[0]);
    return true;
  }

  /**
   * Tests the connection with the currently entered values.
   */
  void testConnection() {
    final String endpointText = fPage.getEndpoint();
    final String apiKey = FeverUtils.toApiKey(fPage.getUsername(), fPage.getApiKey());
    final Exception[] error = new Exception[1];
    final FeverClient.Snapshot[] snapshot = new FeverClient.Snapshot[1];
    final URI[] inputEndpoint = new URI[1];
    final URI[] endpoint = new URI[1];

    try {
      getContainer().run(true, true, new org.eclipse.jface.operation.IRunnableWithProgress() {
        @Override
        public void run(IProgressMonitor monitor) {
          monitor.beginTask(Messages.FeverAccountWizard_CONNECTING, IProgressMonitor.UNKNOWN);
          try {
            inputEndpoint[0] = FeverUtils.normalizeEndpoint(endpointText);
            FeverClient client = new FeverClient();
            snapshot[0] = client.fetchSnapshot(inputEndpoint[0], apiKey, monitor);
            endpoint[0] = client.getResolvedEndpoint() != null ? client.getResolvedEndpoint() : inputEndpoint[0];
          } catch (Exception e) {
            error[0] = e;
          } finally {
            monitor.done();
          }
        }
      });
    } catch (InvocationTargetException e) {
      error[0] = e;
    } catch (InterruptedException e) {
      return;
    }

    if (error[0] != null || snapshot[0] == null) {
      fTestedInputEndpoint = null;
      fTestedSnapshot = null;
      fPage.setTestResult(false, NLS.bind(Messages.FeverAccountWizard_FAILED, getErrorMessage(error[0])));
    } else {
      fTestedInputEndpoint = inputEndpoint[0];
      fTestedEndpoint = endpoint[0];
      fTestedApiKey = apiKey;
      fTestedSnapshot = snapshot[0];
      fPage.setTestResult(true, NLS.bind(Messages.FeverAccountWizard_CONNECTED, snapshot[0].feeds.size(), snapshot[0].groups.size()));
    }
  }

  @Override
  public boolean needsProgressMonitor() {
    return true;
  }

  private void createBookMarks(URI endpoint, String accountName, FeverClient.Snapshot snapshot) {
    IModelFactory factory = Owl.getModelFactory();
    IFolder parent = fPage.getFolder();
    IFolder accountFolder = findFolder(parent, accountName);
    boolean isNewAccountFolder = accountFolder == null;
    if (isNewAccountFolder)
      accountFolder = factory.createFolder(null, parent, accountName);

    /* Copy all properties from the parent into a new account folder */
    if (isNewAccountFolder && parent != null) {
      Map<String, Serializable> properties = parent.getProperties();
      for (Map.Entry<String, Serializable> property : properties.entrySet())
        accountFolder.setProperty(property.getKey(), property.getValue());
    }

    /* Create one folder per Fever group */
    List<FeverClient.Group> sortedGroups = new ArrayList<>(snapshot.groups);
    sortedGroups.sort(Comparator.comparing(group -> group.title != null ? group.title : "")); //$NON-NLS-1$

    Map<Long, IFolder> groupFolders = new HashMap<>();
    for (FeverClient.Group group : sortedGroups) {
      String groupName = StringUtils.isSet(group.title) ? group.title : Messages.FeverAccountWizard_GROUP;
      groupFolders.put(group.id, findOrCreateFolder(accountFolder, groupName));
    }

    /* Determine the folder of every feed (first group wins) */
    Map<Long, IFolder> feedFolders = new HashMap<>();
    for (FeverClient.Group group : sortedGroups) {
      List<Long> feedIds = snapshot.groupToFeedIds.get(group.id);
      if (feedIds == null)
        continue;

      IFolder folder = groupFolders.get(group.id);
      for (Long feedId : feedIds) {
        if (!feedFolders.containsKey(feedId))
          feedFolders.put(feedId, folder);
      }
    }

    List<FeverClient.Feed> sortedFeeds = new ArrayList<>(snapshot.feeds);
    sortedFeeds.sort(Comparator.comparing(feed -> feed.title != null ? feed.title : "")); //$NON-NLS-1$

    IBookMarkDAO bookMarkDAO = OwlDAO.getDAO(IBookMarkDAO.class);
    IFeedDAO feedDAO = OwlDAO.getDAO(IFeedDAO.class);
    List<IMark> createdMarks = new ArrayList<>();

    for (FeverClient.Feed feed : sortedFeeds) {
      try {
        URI feedLink = FeverUtils.createFeedLink(endpoint, feed.id);
        FeedLinkReference reference = new FeedLinkReference(feedLink);

        if (bookMarkDAO.exists(reference))
          continue;

        if (!feedDAO.exists(feedLink))
          feedDAO.save(factory.createFeed(null, feedLink));

        IFolder folder = feedFolders.get(feed.id);
        if (folder == null)
          folder = accountFolder;

        String title = feed.title;
        if (!StringUtils.isSet(title))
          title = StringUtils.isSet(feed.url) ? feed.url : NLS.bind(Messages.FeverAccountWizard_FEED, feed.id);

        IBookMark bookMark = factory.createBookMark(null, folder, reference, title);
        createdMarks.add(bookMark);
      } catch (Exception e) {
        /* Ignore a single broken feed and continue with the others */
        Activator.getDefault().logError(e.getMessage(), e);
      }
    }

    OwlDAO.save(parent);

    /* Load the new bookmarks */
    if (!createdMarks.isEmpty())
      new ReloadTypesAction(new StructuredSelection(createdMarks.toArray()), getShell()).run();
  }

  private IFolder findFolder(IFolder parent, String name) {
    if (parent != null) {
      for (IFolder folder : parent.getFolders()) {
        if (name.equals(folder.getName()))
          return folder;
      }
    }

    return null;
  }

  private IFolder findOrCreateFolder(IFolder parent, String name) {
    IFolder folder = findFolder(parent, name);
    if (folder != null)
      return folder;

    return Owl.getModelFactory().createFolder(null, parent, name);
  }

  private String getErrorMessage(Throwable error) {
    if (error == null)
      return Messages.FeverAccountWizard_UNKNOWN_ERROR;

    if (StringUtils.isSet(error.getMessage()))
      return error.getMessage();

    Throwable cause = error;
    while (cause.getCause() != null && cause.getCause() != cause)
      cause = cause.getCause();

    return StringUtils.isSet(cause.getMessage()) ? cause.getMessage() : cause.toString();
  }
}
