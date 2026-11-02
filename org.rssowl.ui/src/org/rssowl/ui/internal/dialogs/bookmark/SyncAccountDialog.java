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
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.rssowl.core.Owl;
import org.rssowl.core.internal.connection.FeverClient;
import org.rssowl.core.internal.connection.GReaderClient;
import org.rssowl.core.persist.IBookMark;
import org.rssowl.core.persist.IFeed;
import org.rssowl.core.persist.dao.IFeedDAO;
import org.rssowl.core.persist.dao.IBookMarkDAO;
import org.rssowl.core.persist.dao.OwlDAO;
import org.rssowl.core.persist.reference.FeedLinkReference;
import org.rssowl.core.util.FeverUtils;
import org.rssowl.core.util.GReaderUtils;
import org.rssowl.core.util.StringUtils;
import org.rssowl.ui.internal.OwlUI;
import org.rssowl.ui.internal.util.LayoutUtils;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog to change the server and credentials of an existing Fever or Google
 * Reader account.
 *
 * @author RSSOwl Development Team
 */
public class SyncAccountDialog extends TitleAreaDialog {

  private final SyncAccountTarget fAccount;
  private final LocalResourceManager fResources;

  private Text fEndpointInput;
  private Text fUsernameInput;
  private Text fSecretInput;
  private Label fStatusLabel;
  private Button fTestButton;

  private String fTestedEndpointText;
  private String fTestedUsername;
  private String fTestedSecret;
  private ConnectionResult fTestedResult;

  /**
   * @param parentShell the parent shell.
   * @param account the account to edit.
   */
  public SyncAccountDialog(Shell parentShell, SyncAccountTarget account) {
    super(parentShell);
    fAccount = account;
    fResources = new LocalResourceManager(JFaceResources.getResources());
    setHelpAvailable(false);
  }

  /*
   * @see org.eclipse.jface.dialogs.Dialog#close()
   */
  @Override
  public boolean close() {
    fResources.dispose();
    return super.close();
  }

  /*
   * @see org.eclipse.jface.window.Window#configureShell(org.eclipse.swt.widgets.Shell)
   */
  @Override
  protected void configureShell(Shell newShell) {
    super.configureShell(newShell);
    newShell.setText(NLS.bind(Messages.SyncAccountDialog_TITLE, fAccount.getProtocolName()));
  }

  /*
   * @see org.eclipse.jface.dialogs.TitleAreaDialog#createDialogArea(org.eclipse.swt.widgets.Composite)
   */
  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = (Composite) super.createDialogArea(parent);

    setTitle(NLS.bind(Messages.SyncAccountDialog_TITLE, fAccount.getProtocolName()));
    setTitleImage(OwlUI.getImage(fResources, "icons/wizban/bkmrk_wiz.gif")); //$NON-NLS-1$
    setMessage(Messages.SyncAccountDialog_DESCRIPTION);

    Composite container = new Composite(area, SWT.NONE);
    container.setLayout(LayoutUtils.createGridLayout(2, 10, 10));
    container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    /* Server URL */
    Label endpointLabel = new Label(container, SWT.None);
    endpointLabel.setText(Messages.SyncAccountDialog_SERVER);

    fEndpointInput = new Text(container, SWT.BORDER);
    OwlUI.makeAccessible(fEndpointInput, endpointLabel);
    fEndpointInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    fEndpointInput.setText(fAccount.endpoint.toString());
    fEndpointInput.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        updateButtons();
      }
    });

    /* Username */
    Label usernameLabel = new Label(container, SWT.None);
    usernameLabel.setText(fAccount.isFever() ? Messages.SyncAccountDialog_USERNAME_OPTIONAL : Messages.SyncAccountDialog_USERNAME);

    fUsernameInput = new Text(container, SWT.BORDER);
    OwlUI.makeAccessible(fUsernameInput, usernameLabel);
    fUsernameInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    if (!fAccount.isFever()) {
      String username = GReaderUtils.getUsername(fAccount.endpoint);
      if (StringUtils.isSet(username))
        fUsernameInput.setText(username);
    }
    fUsernameInput.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        updateButtons();
      }
    });

    /* Password or API key */
    Label secretLabel = new Label(container, SWT.None);
    secretLabel.setText(fAccount.isFever() ? Messages.SyncAccountDialog_API_KEY : Messages.SyncAccountDialog_PASSWORD);

    fSecretInput = new Text(container, SWT.BORDER | SWT.PASSWORD);
    OwlUI.makeAccessible(fSecretInput, secretLabel);
    fSecretInput.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    fSecretInput.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        updateButtons();
      }
    });

    /* Note about stored credentials */
    Label noteLabel = new Label(container, SWT.WRAP);
    noteLabel.setText(fAccount.isFever() ? Messages.SyncAccountDialog_KEEP_FEVER : Messages.SyncAccountDialog_KEEP_GREADER);
    GridData noteData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
    noteData.horizontalSpan = 2;
    noteLabel.setLayoutData(noteData);

    /* Test connection */
    new Label(container, SWT.None);

    fTestButton = new Button(container, SWT.PUSH);
    fTestButton.setText(Messages.SyncAccountDialog_TEST);
    fTestButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
    fTestButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        runConnection(true);
      }
    });

    /* Status */
    fStatusLabel = new Label(container, SWT.WRAP);
    GridData statusData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
    statusData.horizontalSpan = 2;
    fStatusLabel.setLayoutData(statusData);

    applyDialogFont(container);
    return area;
  }

  /*
   * @see org.eclipse.jface.dialogs.Dialog#createButtonsForButtonBar(org.eclipse.swt.widgets.Composite)
   */
  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    super.createButtonsForButtonBar(parent);
    updateButtons();
  }

  /*
   * @see org.eclipse.jface.dialogs.Dialog#okPressed()
   */
  @Override
  protected void okPressed() {
    ConnectionResult result = runConnection(false);
    if (result == null)
      return;

    if (!result.endpoint.equals(fAccount.endpoint)) {
      String message = NLS.bind(Messages.SyncAccountDialog_ENDPOINT_CHANGED, result.endpoint.toString());
      if (!MessageDialog.openConfirm(getShell(), Messages.SyncAccountDialog_ENDPOINT_CHANGED_TITLE, message))
        return;
    }

    try {
      applyAccountChanges(result);
    } catch (Exception e) {
      setStatus(NLS.bind(Messages.SyncAccountDialog_UPDATE_FAILED, getErrorMessage(e)), SWT.COLOR_DARK_RED);
      return;
    }

    super.okPressed();
  }

  private void applyAccountChanges(ConnectionResult result) throws Exception {
    boolean endpointChanged = !result.endpoint.equals(fAccount.endpoint);

    if (endpointChanged)
      rewireBookmarks(result.endpoint);

    if (fAccount.isFever()) {
      FeverUtils.setApiKey(result.endpoint, result.secret);
      if (endpointChanged)
        FeverUtils.deleteApiKey(fAccount.endpoint);
    } else {
      GReaderUtils.setAuthToken(result.endpoint, result.username, result.secret);
      if (endpointChanged)
        GReaderUtils.deleteAuthToken(fAccount.endpoint);
    }
  }

  /**
   * Points every bookmark of the account to the new endpoint while preserving
   * the Fever feed id or Google Reader stream id.
   */
  private void rewireBookmarks(URI newEndpoint) throws Exception {
    List<IBookMark> bookMarks = new ArrayList<>(OwlDAO.getDAO(IBookMarkDAO.class).loadAll());
    IFeedDAO feedDAO = OwlDAO.getDAO(IFeedDAO.class);

    for (IBookMark bookMark : bookMarks) {
      URI oldLink = bookMark.getFeedLinkReference().getLink();
      SyncAccountTarget linkAccount = SyncAccountTarget.fromLink(oldLink);
      if (linkAccount == null || !linkAccount.protocol.equals(fAccount.protocol) || !linkAccount.endpoint.equals(fAccount.endpoint))
        continue;

      URI newLink;
      if (fAccount.isFever()) {
        long feedId = FeverUtils.getFeedId(oldLink);
        if (feedId < 0)
          continue;
        newLink = FeverUtils.createFeedLink(newEndpoint, feedId);
      } else {
        String streamId = GReaderUtils.getStreamId(oldLink);
        if (!StringUtils.isSet(streamId))
          continue;
        newLink = GReaderUtils.createFeedLink(newEndpoint, streamId);
      }

      if (!feedDAO.exists(newLink)) {
        IFeed feed = Owl.getModelFactory().createFeed(null, newLink);
        feedDAO.save(feed);
      }

      bookMark.setFeedLinkReference(new FeedLinkReference(newLink));
      OwlDAO.save(bookMark);
    }
  }

  private ConnectionResult runConnection(boolean force) {
    if (!force && isTestedResultCurrent())
      return fTestedResult;

    final String endpointText = fEndpointInput.getText().trim();
    final String username = fUsernameInput.getText().trim();
    final String secret = fSecretInput.getText();
    final ConnectionResult[] result = new ConnectionResult[1];
    final Throwable[] error = new Throwable[1];

    setStatus(Messages.SyncAccountDialog_CONNECTING, SWT.COLOR_WIDGET_FOREGROUND);
    fTestButton.setEnabled(false);

    ProgressMonitorDialog dialog = new ProgressMonitorDialog(getShell());
    try {
      dialog.run(true, true, new org.eclipse.jface.operation.IRunnableWithProgress() {
        @Override
        public void run(IProgressMonitor monitor) {
          monitor.beginTask(Messages.SyncAccountDialog_CONNECTING, IProgressMonitor.UNKNOWN);
          try {
            result[0] = connect(endpointText, username, secret, monitor);
          } catch (Exception e) {
            error[0] = e;
          } finally {
            monitor.done();
          }
        }
      });
    } catch (InvocationTargetException e) {
      error[0] = e.getCause() != null ? e.getCause() : e;
    } catch (InterruptedException e) {
      return null;
    } finally {
      updateButtons();
    }

    if (error[0] != null) {
      clearTestedResult();
      setStatus(NLS.bind(Messages.SyncAccountDialog_FAILED, getErrorMessage(error[0])), SWT.COLOR_DARK_RED);
      return null;
    }

    if (result[0] == null) {
      clearTestedResult();
      setStatus("", SWT.COLOR_WIDGET_FOREGROUND); //$NON-NLS-1$
      return null;
    }

    fTestedEndpointText = endpointText;
    fTestedUsername = username;
    fTestedSecret = secret;
    fTestedResult = result[0];

    setStatus(NLS.bind(Messages.SyncAccountDialog_CONNECTED, result[0].feedCount), SWT.COLOR_DARK_GREEN);
    return result[0];
  }

  private ConnectionResult connect(String endpointText, String username, String secret, IProgressMonitor monitor) throws Exception {
    if (!StringUtils.isSet(endpointText))
      throw new IllegalArgumentException(Messages.SyncAccountDialog_INVALID_SERVER);

    URI inputEndpoint;
    try {
      inputEndpoint = fAccount.isFever() ? FeverUtils.normalizeEndpoint(endpointText) : GReaderUtils.normalizeEndpoint(endpointText);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(Messages.SyncAccountDialog_INVALID_SERVER, e);
    }

    if (fAccount.isFever())
      return connectFever(inputEndpoint, username, secret, monitor);

    return connectGReader(inputEndpoint, username, secret, monitor);
  }

  private ConnectionResult connectGReader(URI endpoint, String username, String secret, IProgressMonitor monitor) throws Exception {
    if (!StringUtils.isSet(username))
      throw new IllegalArgumentException(Messages.SyncAccountDialog_ENTER_USERNAME);

    GReaderClient client = new GReaderClient();

    if (StringUtils.isSet(secret)) {
      GReaderClient.Snapshot snapshot = client.connect(endpoint, username, secret, monitor);
      return new ConnectionResult(snapshot.endpoint, username, snapshot.authToken, snapshot.subscriptions.size());
    }

    String authToken = GReaderUtils.getAuthToken(endpoint);
    if (!StringUtils.isSet(authToken))
      throw new IllegalArgumentException(Messages.SyncAccountDialog_ENTER_GREADER_PASSWORD);

    String storedUsername = GReaderUtils.getUsername(endpoint);
    if (StringUtils.isSet(storedUsername) && !storedUsername.equals(username))
      throw new IllegalArgumentException(Messages.SyncAccountDialog_USERNAME_NEEDS_PASSWORD);

    List<GReaderClient.Subscription> subscriptions = client.fetchSubscriptions(endpoint, authToken, monitor);
    return new ConnectionResult(endpoint, username, authToken, subscriptions.size());
  }

  private ConnectionResult connectFever(URI endpoint, String username, String secret, IProgressMonitor monitor) throws Exception {
    String apiKey;
    if (StringUtils.isSet(secret))
      apiKey = FeverUtils.toApiKey(username, secret);
    else
      apiKey = FeverUtils.getApiKey(endpoint);

    if (!StringUtils.isSet(apiKey))
      throw new IllegalArgumentException(Messages.SyncAccountDialog_ENTER_FEVER_KEY);

    FeverClient client = new FeverClient();
    FeverClient.Snapshot snapshot = client.fetchSnapshot(endpoint, apiKey, monitor);
    URI resolvedEndpoint = client.getResolvedEndpoint() != null ? client.getResolvedEndpoint() : endpoint;

    return new ConnectionResult(resolvedEndpoint, username, apiKey, snapshot.feeds.size());
  }

  private boolean isTestedResultCurrent() {
    if (fTestedResult == null)
      return false;

    return fEndpointInput.getText().trim().equals(fTestedEndpointText) && fUsernameInput.getText().trim().equals(fTestedUsername) && fSecretInput.getText().equals(fTestedSecret);
  }

  private void clearTestedResult() {
    fTestedEndpointText = null;
    fTestedUsername = null;
    fTestedSecret = null;
    fTestedResult = null;
  }

  private void updateButtons() {
    if (fEndpointInput == null || fEndpointInput.isDisposed())
      return;

    boolean valid = StringUtils.isSet(fEndpointInput.getText().trim());
    if (valid && !fAccount.isFever())
      valid = StringUtils.isSet(fUsernameInput.getText().trim());

    Button okButton = getButton(IDialogConstants.OK_ID);
    if (okButton != null)
      okButton.setEnabled(valid);

    if (fTestButton != null)
      fTestButton.setEnabled(valid);
  }

  private void setStatus(String message, int color) {
    if (fStatusLabel == null || fStatusLabel.isDisposed())
      return;

    fStatusLabel.setText(message);
    fStatusLabel.setForeground(getShell().getDisplay().getSystemColor(color));
    fStatusLabel.getParent().layout();
  }

  private String getErrorMessage(Throwable error) {
    if (error == null)
      return Messages.SyncAccountDialog_UNKNOWN_ERROR;

    if (error instanceof InvocationTargetException && ((InvocationTargetException) error).getCause() != null)
      error = ((InvocationTargetException) error).getCause();

    if (StringUtils.isSet(error.getMessage()))
      return error.getMessage();

    Throwable cause = error;
    while (cause.getCause() != null && cause.getCause() != cause)
      cause = cause.getCause();

    return StringUtils.isSet(cause.getMessage()) ? cause.getMessage() : cause.toString();
  }

  /*
   * @see org.eclipse.jface.dialogs.Dialog#getInitialSize()
   */
  @Override
  protected Point getInitialSize() {
    Point size = super.getInitialSize();
    return new Point(Math.max(size.x, 520), size.y);
  }

  private static final class ConnectionResult {
    private final URI endpoint;
    private final String username;
    private final String secret;
    private final int feedCount;

    ConnectionResult(URI endpoint, String username, String secret, int feedCount) {
      this.endpoint = endpoint;
      this.username = username;
      this.secret = secret;
      this.feedCount = feedCount;
    }
  }
}
