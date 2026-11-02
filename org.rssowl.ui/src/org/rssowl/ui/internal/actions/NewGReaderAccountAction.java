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

package org.rssowl.ui.internal.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.rssowl.core.persist.IFolder;
import org.rssowl.ui.internal.OwlUI;
import org.rssowl.ui.internal.dialogs.bookmark.GReaderAccountWizard;

/**
 * Creates a new Google Reader API account and a bookmark for every feed of the
 * account.
 *
 * @author RSSOwl Development Team
 */
public class NewGReaderAccountAction extends AbstractSelectionAwareBookMarkAction<NewGReaderAccountAction> implements IWorkbenchWindowActionDelegate, IObjectActionDelegate {

  /* Section for Dialogs Settings */
  private static final String SETTINGS_SECTION = "org.rssowl.ui.internal.dialogs.bookmark.GReaderAccountWizard"; //$NON-NLS-1$

  /** Keep for Reflection */
  public NewGReaderAccountAction() {
  }

  /*
   * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
   */
  @Override
  public void run(IAction action) {
    IFolder parent = OwlUI.getSelectedParent(fParent);
    GReaderAccountWizard wizard = new GReaderAccountWizard(parent);
    OwlUI.openWizard(fShell, wizard, true, true, SETTINGS_SECTION);
  }
}
