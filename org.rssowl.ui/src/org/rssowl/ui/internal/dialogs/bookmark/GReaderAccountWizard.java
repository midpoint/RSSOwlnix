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
import org.rssowl.core.internal.connection.GReaderClient;
import org.rssowl.core.persist.IBookMark;
import org.rssowl.core.persist.IFolder;
import org.rssowl.core.persist.IMark;
import org.rssowl.core.persist.IModelFactory;
import org.rssowl.core.persist.dao.IBookMarkDAO;
import org.rssowl.core.persist.dao.IFeedDAO;
import org.rssowl.core.persist.dao.OwlDAO;
import org.rssowl.core.persist.reference.FeedLinkReference;
import org.rssowl.core.util.GReaderUtils;
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
 * A wizard that connects to a site implementing the Google Reader API and
 * creates a bookmark for every feed of the account inside folders that mirror
 * the categories of the account.
 *
 * @author RSSOwl Development Team
 */
public class GReaderAccountWizard extends Wizard {

  private final IFolder fParent;
  private GReaderAccountPage fPage;

  private URI fTestedInputEndpoint;
  private String fTestedUsername;
  private String fTestedPassword;
  private GReaderClient.Snapshot fTestedSnapshot;

  /**
   * @param parent the folder to create the account folder in.
   */
  public GReaderAccountWizard(IFolder parent) {
    fParent = parent;
    setWindowTitle(Messages.GReaderAccountWizard_TITLE);
    setHelpAvailable(false);
  }

  /*
   * @see org.eclipse.jface.wizard.Wizard#addPages()
   */
  @Override
  public void addPages() {
    fPage = new GReaderAccountPage(fParent);
    addPage(fPage);
  }

  /*
   * @see org.eclipse.jface.wizard.Wizard#performFinish()
   */
  @Override
  public boolean performFinish() {
    final String endpointText = fPage.getEndpoint();
    final String username = fPage.getUsername();
    final String password = fPage.getPassword();
    final String accountName = fPage.getAccountName();

    URI endpoint;
    try {
      endpoint = GReaderUtils.normalizeEndpoint(endpointText);
    } catch (Exception e) {
      fPage.setErrorMessage(Messages.GReaderAccountWizard_INVALID_SERVER);
      return false;
    }

    if (fPage.getFolder() == null) {
      fPage.setErrorMessage(Messages.GReaderAccountWizard_NO_LOCATION);
      return false;
    }

    final GReaderClient.Snapshot[] snapshot = new GReaderClient.Snapshot[1];

    /* Fetch the account structure unless the exact values were tested already */
    if (fTestedSnapshot == null || !endpoint.equals(fTestedInputEndpoint) || !username.equals(fTestedUsername) || !password.equals(fTestedPassword)) {
      final URI finalEndpoint = endpoint;
      try {
        getContainer().run(true, true, new org.eclipse.jface.operation.IRunnableWithProgress() {
          @Override
          public void run(IProgressMonitor monitor) {
            monitor.beginTask(Messages.GReaderAccountWizard_CONNECTING, IProgressMonitor.UNKNOWN);
            try {
              GReaderClient client = new GReaderClient();
              snapshot[0] = client.connect(finalEndpoint, username, password, monitor);
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
    }

    if (snapshot[0] == null || snapshot[0].subscriptions.isEmpty()) {
      fPage.setErrorMessage(Messages.GReaderAccountWizard_NO_FEEDS);
      return false;
    }

    URI accountEndpoint = snapshot[0].endpoint;

    /* Store only the token derived from the password in the secure credentials store */
    try {
      GReaderUtils.setAuthToken(accountEndpoint, username, snapshot[0].authToken);
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
    final String username = fPage.getUsername();
    final String password = fPage.getPassword();
    final Exception[] error = new Exception[1];
    final GReaderClient.Snapshot[] snapshot = new GReaderClient.Snapshot[1];
    final URI[] inputEndpoint = new URI[1];

    try {
      getContainer().run(true, true, new org.eclipse.jface.operation.IRunnableWithProgress() {
        @Override
        public void run(IProgressMonitor monitor) {
          monitor.beginTask(Messages.GReaderAccountWizard_CONNECTING, IProgressMonitor.UNKNOWN);
          try {
            inputEndpoint[0] = GReaderUtils.normalizeEndpoint(endpointText);
            GReaderClient client = new GReaderClient();
            snapshot[0] = client.connect(inputEndpoint[0], username, password, monitor);
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
      fTestedSnapshot = null;
      fPage.setTestResult(false, NLS.bind(Messages.GReaderAccountWizard_FAILED, getErrorMessage(error[0])));
    } else {
      fTestedInputEndpoint = inputEndpoint[0];
      fTestedUsername = username;
      fTestedPassword = password;
      fTestedSnapshot = snapshot[0];
      fPage.setTestResult(true, NLS.bind(Messages.GReaderAccountWizard_CONNECTED, snapshot[0].subscriptions.size()));
    }
  }

  @Override
  public boolean needsProgressMonitor() {
    return true;
  }

  private void createBookMarks(URI endpoint, String accountName, GReaderClient.Snapshot snapshot) {
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

    /* Create one folder per category */
    List<String> categoryNames = new ArrayList<>();
    for (GReaderClient.Subscription subscription : snapshot.subscriptions) {
      if (!subscription.categories.isEmpty()) {
        String category = subscription.categories.get(0);
        if (StringUtils.isSet(category) && !categoryNames.contains(category))
          categoryNames.add(category);
      }
    }

    categoryNames.sort(Comparator.naturalOrder());
    Map<String, IFolder> categoryFolders = new HashMap<>();
    for (String category : categoryNames)
      categoryFolders.put(category, findOrCreateFolder(accountFolder, category));

    List<GReaderClient.Subscription> sortedSubscriptions = new ArrayList<>(snapshot.subscriptions);
    sortedSubscriptions.sort(Comparator.comparing(subscription -> subscription.title != null ? subscription.title : "")); //$NON-NLS-1$

    IBookMarkDAO bookMarkDAO = OwlDAO.getDAO(IBookMarkDAO.class);
    IFeedDAO feedDAO = OwlDAO.getDAO(IFeedDAO.class);
    List<IMark> createdMarks = new ArrayList<>();

    for (GReaderClient.Subscription subscription : sortedSubscriptions) {
      try {
        URI feedLink = GReaderUtils.createFeedLink(endpoint, subscription.streamId);
        FeedLinkReference reference = new FeedLinkReference(feedLink);

        if (bookMarkDAO.exists(reference))
          continue;

        if (!feedDAO.exists(feedLink))
          feedDAO.save(factory.createFeed(null, feedLink));

        IFolder folder = accountFolder;
        if (!subscription.categories.isEmpty()) {
          IFolder categoryFolder = categoryFolders.get(subscription.categories.get(0));
          if (categoryFolder != null)
            folder = categoryFolder;
        }

        String title = subscription.title;
        if (!StringUtils.isSet(title))
          title = StringUtils.isSet(subscription.url) ? subscription.url : NLS.bind(Messages.GReaderAccountWizard_FEED, subscription.streamId);

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
      return Messages.GReaderAccountWizard_UNKNOWN_ERROR;

    if (StringUtils.isSet(error.getMessage()))
      return error.getMessage();

    Throwable cause = error;
    while (cause.getCause() != null && cause.getCause() != cause)
      cause = cause.getCause();

    return StringUtils.isSet(cause.getMessage()) ? cause.getMessage() : cause.toString();
  }
}
