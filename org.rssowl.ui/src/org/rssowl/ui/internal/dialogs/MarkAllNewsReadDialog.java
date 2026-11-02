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

package org.rssowl.ui.internal.dialogs;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.rssowl.core.persist.INews;
import org.rssowl.ui.internal.OwlUI;
import org.rssowl.ui.internal.actions.MarkTypesReadAction;
import org.rssowl.ui.internal.util.LayoutUtils;

import java.util.Collection;

/**
 * A dialog to select the time range of news that should be marked as read.
 *
 * @author bpasero
 */
public class MarkAllNewsReadDialog extends TitleAreaDialog {
  private static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;
  private static final int CUSTOM_DAYS_DEFAULT = 30;

  private Button fAllNewsButton;
  private Button fDayButton;
  private Button fThreeDaysButton;
  private Button fWeekButton;
  private Button fYearButton;
  private Button fCustomButton;
  private Spinner fCustomDaysSpinner;
  private Label fMatchCountLabel;
  private final Collection<INews> fNews;
  private long fSelectedRangeInMillis;

  /**
   * @param parentShell The parent shell.
   */
  public MarkAllNewsReadDialog(Shell parentShell) {
    this(parentShell, null);
  }

  /**
   * @param parentShell The parent shell.
   * @param news The unread News currently shown in the feed view, used to
   * display how many older News will be marked read, or <code>null</code> to
   * hide the count.
   */
  public MarkAllNewsReadDialog(Shell parentShell, Collection<INews> news) {
    super(parentShell);
    fNews = news;
  }

  /*
   * @see org.eclipse.jface.dialogs.TitleAreaDialog#createDialogArea(org.eclipse.swt.widgets.Composite)
   */
  @Override
  protected Control createDialogArea(Composite parent) {
    setTitle(Messages.MarkAllNewsReadDialog_MARK_NEWS_READ);
    setMessage(Messages.MarkAllNewsReadDialog_INFO);

    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(LayoutUtils.createGridLayout(1, 10, 10));
    composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    SelectionAdapter selectionListener = new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        updateControls();
      }
    };

    fAllNewsButton = createRadioButton(composite, Messages.MarkAllNewsReadDialog_ALL_NEWS, selectionListener);
    fAllNewsButton.setSelection(true);

    fDayButton = createRadioButton(composite, Messages.MarkAllNewsReadDialog_LAST_DAY, selectionListener);
    fThreeDaysButton = createRadioButton(composite, Messages.MarkAllNewsReadDialog_LAST_3_DAYS, selectionListener);
    fWeekButton = createRadioButton(composite, Messages.MarkAllNewsReadDialog_LAST_WEEK, selectionListener);
    fYearButton = createRadioButton(composite, Messages.MarkAllNewsReadDialog_LAST_YEAR, selectionListener);
    fCustomButton = createRadioButton(composite, Messages.MarkAllNewsReadDialog_CUSTOM, selectionListener);

    Composite customRangeContainer = new Composite(composite, SWT.NONE);
    GridData gridData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
    gridData.horizontalIndent = convertHorizontalDLUsToPixels(15);
    customRangeContainer.setLayoutData(gridData);
    customRangeContainer.setLayout(LayoutUtils.createGridLayout(2, 0, 0));

    fCustomDaysSpinner = new Spinner(customRangeContainer, SWT.BORDER);
    fCustomDaysSpinner.setMinimum(1);
    fCustomDaysSpinner.setMaximum(3650);
    fCustomDaysSpinner.setSelection(CUSTOM_DAYS_DEFAULT);
    fCustomDaysSpinner.setEnabled(false);
    fCustomDaysSpinner.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        updateMatchCount();
      }
    });

    Label daysLabel = new Label(customRangeContainer, SWT.NONE);
    daysLabel.setText(Messages.MarkAllNewsReadDialog_DAYS);

    if (fNews != null) {
      fMatchCountLabel = new Label(composite, SWT.NONE);
      GridData labelData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
      labelData.verticalIndent = convertVerticalDLUsToPixels(5);
      fMatchCountLabel.setLayoutData(labelData);
    }

    applyDialogFont(composite);
    updateControls();

    return composite;
  }

  private void updateControls() {
    fCustomDaysSpinner.setEnabled(fCustomButton.getSelection());
    updateMatchCount();
  }

  private void updateMatchCount() {
    if (fMatchCountLabel == null || fMatchCountLabel.isDisposed())
      return;

    long rangeInMillis = calculateSelectedRangeInMillis();
    fMatchCountLabel.setText(NLS.bind(Messages.MarkAllNewsReadDialog_NEWS_TO_MARK_READ, countNewsToMarkRead(rangeInMillis)));
  }

  private int countNewsToMarkRead(long rangeInMillis) {
    if (fNews == null)
      return 0;

    long now = System.currentTimeMillis();
    int count = 0;
    for (INews news : fNews) {
      if (news != null && MarkTypesReadAction.isNewsOlderThanRange(news, rangeInMillis, now))
        count++;
    }

    return count;
  }

  private Button createRadioButton(Composite parent, String text, SelectionAdapter selectionListener) {
    Button button = new Button(parent, SWT.RADIO);
    button.setText(text);
    button.addSelectionListener(selectionListener);
    return button;
  }

  /**
   * @return The time range in milliseconds to keep unread, or <code>0</code>
   * if all news should be marked read.
   */
  public long getSelectedRangeInMillis() {
    return fSelectedRangeInMillis;
  }

  private long calculateSelectedRangeInMillis() {
    if (fDayButton.getSelection())
      return MILLIS_PER_DAY;
    if (fThreeDaysButton.getSelection())
      return 3 * MILLIS_PER_DAY;
    if (fWeekButton.getSelection())
      return 7 * MILLIS_PER_DAY;
    if (fYearButton.getSelection())
      return 365 * MILLIS_PER_DAY;
    if (fCustomButton.getSelection())
      return Math.max(1, fCustomDaysSpinner.getSelection()) * MILLIS_PER_DAY;

    return 0;
  }

  /*
   * @see org.eclipse.jface.dialogs.Dialog#okPressed()
   */
  @Override
  protected void okPressed() {

    /*
     * The controls are disposed when the dialog closes, so remember the
     * selected range while they are still alive.
     */
    fSelectedRangeInMillis = calculateSelectedRangeInMillis();

    if (fNews != null && countNewsToMarkRead(fSelectedRangeInMillis) == 0) {
      String message = fSelectedRangeInMillis == 0 ? Messages.MarkAllNewsReadDialog_NO_UNREAD_NEWS : Messages.MarkAllNewsReadDialog_NO_OLDER_NEWS;
      MessageDialog.openInformation(getShell(), Messages.MarkAllNewsReadDialog_MARK_NEWS_READ, message);
      return;
    }

    super.okPressed();
  }

  /*
   * @see org.eclipse.jface.dialogs.Dialog#createButtonsForButtonBar(org.eclipse.swt.widgets.Composite)
   */
  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
  }

  /*
   * @see org.eclipse.jface.window.Window#getShellStyle()
   */
  @Override
  protected int getShellStyle() {
    return SWT.TITLE | SWT.BORDER | SWT.CLOSE | getDefaultOrientation();
  }

  /*
   * @see org.eclipse.jface.dialogs.Dialog#initializeBounds()
   */
  @Override
  protected void initializeBounds() {
    super.initializeBounds();

    Shell shell = getShell();

    int maxWidth = convertHorizontalDLUsToPixels(OwlUI.MIN_DIALOG_WIDTH_DLU);
    int maxHeight = 500;

    Point requiredSize = shell.computeSize(SWT.DEFAULT, SWT.DEFAULT);

    shell.setSize(Math.min(maxWidth, requiredSize.x), Math.min(maxHeight, requiredSize.y));
    LayoutUtils.positionShell(shell);
  }
}
