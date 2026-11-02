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

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.rssowl.ui.internal.dialogs.bookmark.SyncAccountDialog;
import org.rssowl.ui.internal.dialogs.bookmark.SyncAccountTarget;

/**
 * Opens the settings dialog for the Fever or Google Reader account a selected
 * folder or bookmark belongs to.
 *
 * @author RSSOwl Development Team
 */
public class EditSyncAccountAction extends Action implements IObjectActionDelegate {

  private Shell fShell;
  private SyncAccountTarget fTarget;

  /** Keep for Reflection */
  public EditSyncAccountAction() {}

  /*
   * @see org.eclipse.jface.action.Action#run()
   */
  @Override
  public void run() {
    if (fTarget == null)
      return;

    Shell shell = fShell;
    if (shell == null) {
      IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
      if (window != null)
        shell = window.getShell();
    }

    if (shell == null)
      return;

    SyncAccountDialog dialog = new SyncAccountDialog(shell, fTarget);
    dialog.open();
  }

  /*
   * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
   */
  @Override
  public void run(org.eclipse.jface.action.IAction action) {
    run();
  }

  /*
   * @see org.eclipse.ui.IActionDelegate#selectionChanged(org.eclipse.jface.action.IAction,
   * org.eclipse.jface.viewers.ISelection)
   */
  @Override
  public void selectionChanged(org.eclipse.jface.action.IAction action, ISelection selection) {
    fTarget = null;

    if (selection instanceof IStructuredSelection) {
      IStructuredSelection structuredSelection = (IStructuredSelection) selection;
      if (structuredSelection.size() == 1)
        fTarget = SyncAccountTarget.fromSelection(structuredSelection.getFirstElement());
    }

    action.setEnabled(fTarget != null);
  }

  /*
   * @see org.eclipse.ui.IObjectActionDelegate#setActivePart(org.eclipse.jface.action.IAction,
   * org.eclipse.ui.IWorkbenchPart)
   */
  @Override
  public void setActivePart(org.eclipse.jface.action.IAction action, IWorkbenchPart targetPart) {
    fShell = targetPart.getSite().getShell();
  }
}
