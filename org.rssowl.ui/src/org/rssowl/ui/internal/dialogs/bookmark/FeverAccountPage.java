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

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.rssowl.core.persist.IFolder;
import org.rssowl.ui.internal.OwlUI;
import org.rssowl.ui.internal.util.FolderChooser;
import org.rssowl.ui.internal.util.LayoutUtils;

import java.net.URI;

/**
 * The single page of the {@link FeverAccountWizard}.
 *
 * @author RSSOwl Development Team
 */
class FeverAccountPage extends WizardPage {

  private final IFolder fInitialFolder;
  private Text fEndpointInput;
  private Text fUsernameInput;
  private Text fApiKeyInput;
  private Text fNameInput;
  private FolderChooser fFolderChooser;
  private Label fStatusLabel;
  private boolean fNameEditedByUser;
  private boolean fUpdatingName;

  FeverAccountPage(IFolder initialFolder) {
    super(Messages.FeverAccountWizard_TITLE, Messages.FeverAccountWizard_TITLE, OwlUI.getImageDescriptor("icons/wizban/bkmrk_wiz.gif")); //$NON-NLS-1$
    fInitialFolder = initialFolder != null ? initialFolder : OwlUI.getSelectedBookMarkSet();
    setMessage(Messages.FeverAccountWizard_DESCRIPTION);
  }

  String getEndpoint() {
    return fEndpointInput.getText().trim();
  }

  String getUsername() {
    return fUsernameInput.getText().trim();
  }

  String getApiKey() {
    return fApiKeyInput.getText().trim();
  }

  String getAccountName() {
    return fNameInput.getText().trim();
  }

  IFolder getFolder() {
    return fFolderChooser.getFolder();
  }

  void setTestResult(boolean success, String message) {
    fStatusLabel.setText(message);
    fStatusLabel.setForeground(getShell().getDisplay().getSystemColor(success ? SWT.COLOR_DARK_GREEN : SWT.COLOR_DARK_RED));
    fStatusLabel.getParent().layout();
  }

  /*
   * @see org.eclipse.jface.wizard.WizardPage#isPageComplete()
   */
  @Override
  public boolean isPageComplete() {
    return getEndpoint().length() > 0 && getApiKey().length() > 0 && getAccountName().length() > 0 && getFolder() != null;
  }

  /*
   * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
   */
  @Override
  public void createControl(Composite parent) {
    Composite container = new Composite(parent, SWT.NONE);
    container.setLayout(new GridLayout(2, false));

    /* Server URL */
    Label endpointLabel = new Label(container, SWT.None);
    endpointLabel.setText(Messages.FeverAccountWizard_SERVER);

    fEndpointInput = new Text(container, SWT.BORDER);
    OwlUI.makeAccessible(fEndpointInput, endpointLabel);
    fEndpointInput.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
    fEndpointInput.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        if (fNameInput != null && !fNameEditedByUser && !fUpdatingName) {
          fUpdatingName = true;
          try {
            fNameInput.setText(guessAccountName(getEndpoint()));
          } finally {
            fUpdatingName = false;
          }
        }

        getContainer().updateButtons();
      }
    });

    /* Username */
    Label usernameLabel = new Label(container, SWT.None);
    usernameLabel.setText(Messages.FeverAccountWizard_USERNAME);

    fUsernameInput = new Text(container, SWT.BORDER);
    OwlUI.makeAccessible(fUsernameInput, usernameLabel);
    fUsernameInput.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
    fUsernameInput.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        getContainer().updateButtons();
      }
    });

    /* API Key */
    Label apiKeyLabel = new Label(container, SWT.None);
    apiKeyLabel.setText(Messages.FeverAccountWizard_API_KEY);

    fApiKeyInput = new Text(container, SWT.BORDER | SWT.PASSWORD);
    OwlUI.makeAccessible(fApiKeyInput, apiKeyLabel);
    fApiKeyInput.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
    fApiKeyInput.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        getContainer().updateButtons();
      }
    });

    /* Test Connection */
    new Label(container, SWT.None);

    Button testButton = new Button(container, SWT.PUSH);
    testButton.setText(Messages.FeverAccountWizard_TEST);
    testButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
    testButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        setTestResult(true, Messages.FeverAccountWizard_CONNECTING);
        ((FeverAccountWizard) getWizard()).testConnection();
      }
    });

    /* Account Name */
    Label nameLabel = new Label(container, SWT.None);
    nameLabel.setText(Messages.FeverAccountWizard_ACCOUNT_NAME);

    fNameInput = new Text(container, SWT.BORDER);
    OwlUI.makeAccessible(fNameInput, nameLabel);
    fNameInput.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
    fNameInput.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        if (!fUpdatingName)
          fNameEditedByUser = true;

        getContainer().updateButtons();
      }
    });

    /* Location */
    Composite labelContainer = new Composite(container, SWT.None);
    labelContainer.setLayout(LayoutUtils.createGridLayout(1, 0, 3));
    labelContainer.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));

    Label locationLabel = new Label(labelContainer, SWT.None);
    locationLabel.setText(Messages.FeverAccountWizard_LOCATION);

    fFolderChooser = new FolderChooser(container, fInitialFolder, null, SWT.BORDER, false, 5);
    fFolderChooser.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    fFolderChooser.setLayout(LayoutUtils.createGridLayout(1, 0, 0, 2, 5, false));
    fFolderChooser.setBackground(container.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND));

    /* Status */
    fStatusLabel = new Label(container, SWT.NONE);
    GridData statusData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
    statusData.horizontalSpan = 2;
    fStatusLabel.setLayoutData(statusData);

    Dialog.applyDialogFont(container);
    setControl(container);
  }

  private String guessAccountName(String endpoint) {
    try {
      URI uri = new URI(endpoint.startsWith("http") ? endpoint : "https://" + endpoint); //$NON-NLS-1$ //$NON-NLS-2$
      return uri.getHost() != null ? uri.getHost() : ""; //$NON-NLS-1$
    } catch (Exception e) {
      return ""; //$NON-NLS-1$
    }
  }
}
