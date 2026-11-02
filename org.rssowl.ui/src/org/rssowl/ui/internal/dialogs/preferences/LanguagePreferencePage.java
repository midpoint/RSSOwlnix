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

package org.rssowl.ui.internal.dialogs.preferences;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.rssowl.core.util.LanguageManager;
import org.rssowl.ui.internal.OwlUI;
import org.rssowl.ui.internal.util.LayoutUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link PreferencePage} to select the user interface language and to export
 * editable language packs.
 *
 * @author RSSOwl Development Team
 */
public class LanguagePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

  /** ID of the Page */
  public static final String ID = "org.rssowl.ui.LanguagePreferences"; //$NON-NLS-1$

  private static final String[] MESSAGE_BUNDLES = {
      "org.rssowl.core.connection.messages", //$NON-NLS-1$
      "org.rssowl.core.internal.connection.messages", //$NON-NLS-1$
      "org.rssowl.core.internal.interpreter.messages", //$NON-NLS-1$
      "org.rssowl.core.internal.messages", //$NON-NLS-1$
      "org.rssowl.core.internal.newsaction.messages", //$NON-NLS-1$
      "org.rssowl.core.internal.persist.messages", //$NON-NLS-1$
      "org.rssowl.core.internal.persist.migration.messages", //$NON-NLS-1$
      "org.rssowl.core.internal.persist.search.messages", //$NON-NLS-1$
      "org.rssowl.core.internal.persist.service.messages", //$NON-NLS-1$
      "org.rssowl.core.persist.messages", //$NON-NLS-1$
      "org.rssowl.core.util.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.actions.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.dialogs.bookmark.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.dialogs.cleanup.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.dialogs.exporter.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.dialogs.fatal.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.dialogs.importer.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.dialogs.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.dialogs.preferences.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.dialogs.properties.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.dialogs.welcome.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.editors.browser.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.editors.feed.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.filter.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.notifier.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.search.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.services.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.undo.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.util.messages", //$NON-NLS-1$
      "org.rssowl.ui.internal.views.explorer.messages" //$NON-NLS-1$
  };

  private static final String[] PLUGIN_BUNDLES = {
      "org.rssowl.ui", //$NON-NLS-1$
      "org.rssowl.core" //$NON-NLS-1$
  };

  private Combo fLanguageCombo;
  private Button fExportButton;
  private final List<String> fTags = new ArrayList<>();
  private String fInitialTag;

  /** Leave for reflection */
  public LanguagePreferencePage() {
    setImageDescriptor(OwlUI.getImageDescriptor("icons/elcl16/languages.gif")); //$NON-NLS-1$
  }

  /*
   * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
   */
  @Override
  public void init(IWorkbench workbench) {
    fInitialTag = LanguageManager.getConfiguredLanguageTag();
  }

  /*
   * @see org.eclipse.jface.preference.PreferencePage#createContents(org.eclipse.swt.widgets.Composite)
   */
  @Override
  protected Control createContents(Composite parent) {
    Composite container = new Composite(parent, SWT.NONE);
    container.setLayout(LayoutUtils.createGridLayout(2, 0, 0));
    container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    container.setFont(parent.getFont());

    Label description = new Label(container, SWT.WRAP);
    GridData descriptionData = new GridData(SWT.FILL, SWT.BEGINNING, true, false, 2, 1);
    descriptionData.widthHint = 420;
    description.setLayoutData(descriptionData);
    description.setText(Messages.LanguagePreferencePage_DESCRIPTION);

    Label languageLabel = new Label(container, SWT.NONE);
    languageLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
    languageLabel.setText(Messages.LanguagePreferencePage_LANGUAGE);

    fLanguageCombo = new Combo(container, SWT.READ_ONLY | SWT.DROP_DOWN);
    fLanguageCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    fLanguageCombo.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        updateApplyEnablement(true);
      }
    });

    Label folderLabel = new Label(container, SWT.NONE);
    folderLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
    folderLabel.setText(Messages.LanguagePreferencePage_PACK_FOLDER);

    Composite folderContainer = new Composite(container, SWT.NONE);
    folderContainer.setLayout(LayoutUtils.createGridLayout(2, 0, 0));
    folderContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    Label folderPath = new Label(folderContainer, SWT.WRAP);
    GridData folderPathData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    folderPathData.widthHint = 300;
    folderPath.setLayoutData(folderPathData);
    folderPath.setText(LanguageManager.getWritableLanguagePackRoot().getAbsolutePath());

    Button openFolderButton = new Button(folderContainer, SWT.PUSH);
    openFolderButton.setText(Messages.LanguagePreferencePage_OPEN_FOLDER);
    openFolderButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        openLanguagePackFolder();
      }
    });

    Composite buttonContainer = new Composite(container, SWT.NONE);
    buttonContainer.setLayout(LayoutUtils.createGridLayout(1, 0, 0));
    buttonContainer.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 2, 1));

    fExportButton = new Button(buttonContainer, SWT.PUSH);
    fExportButton.setText(Messages.LanguagePreferencePage_EXPORT);
    fExportButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        exportLanguagePack();
      }
    });

    populateLanguages();
    applyDialogFont(container);
    updateApplyEnablement(false);

    return container;
  }

  private void populateLanguages() {
    fTags.clear();
    fLanguageCombo.removeAll();

    addLanguage("", Messages.LanguagePreferencePage_SYSTEM_DEFAULT); //$NON-NLS-1$
    addLanguage("en", Messages.LanguagePreferencePage_ENGLISH); //$NON-NLS-1$

    for (String tag : LanguageManager.getAvailableLanguageTags()) {
      if ("en".equals(tag) || tag.length() == 0) //$NON-NLS-1$
        continue;

      addLanguage(tag, LanguageManager.getDisplayName(tag) + " (" + tag + ")"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    String configuredTag = LanguageManager.getConfiguredLanguageTag();
    if (configuredTag.length() > 0 && !fTags.contains(configuredTag))
      addLanguage(configuredTag, configuredTag);

    int selection = fTags.indexOf(configuredTag);
    fLanguageCombo.select(Math.max(0, selection));
  }

  private void addLanguage(String tag, String label) {
    fTags.add(tag);
    fLanguageCombo.add(label);
  }

  private String getSelectedTag() {
    int selection = fLanguageCombo.getSelectionIndex();
    if (selection < 0 || selection >= fTags.size())
      return ""; //$NON-NLS-1$

    return fTags.get(selection);
  }

  private String getExportTag() {
    String tag = getSelectedTag();
    return tag.length() > 0 ? tag : LanguageManager.getLanguageTag();
  }

  private void openLanguagePackFolder() {
    File folder = LanguageManager.getWritableLanguagePackRoot();
    try {
      Files.createDirectories(folder.toPath());
    } catch (Exception e) {
      MessageDialog.openError(getShell(), Messages.LanguagePreferencePage_EXPORT, NLS.bind(Messages.LanguagePreferencePage_OPEN_FAILED, folder.getAbsolutePath()));
      return;
    }

    try {
      if (!Program.launch(folder.getAbsolutePath()))
        MessageDialog.openError(getShell(), Messages.LanguagePreferencePage_EXPORT, NLS.bind(Messages.LanguagePreferencePage_OPEN_FAILED, folder.getAbsolutePath()));
    } catch (Exception e) {
      MessageDialog.openError(getShell(), Messages.LanguagePreferencePage_EXPORT, NLS.bind(Messages.LanguagePreferencePage_OPEN_FAILED, e.getMessage()));
    }
  }

  private void exportLanguagePack() {
    String tag = getExportTag();
    String builtInTag = getBuiltInTag(tag);
    if (builtInTag == null) {
      MessageDialog.openInformation(getShell(), Messages.LanguagePreferencePage_EXPORT, NLS.bind(Messages.LanguagePreferencePage_EXPORT_NO_BUILT_IN, tag));
      return;
    }

    File target = LanguageManager.getWritableLanguagePackDirectory(tag);
    int written = 0;
    int kept = 0;

    try {
      Files.createDirectories(target.toPath());

      String suffix = "en".equals(builtInTag) ? "" : "_" + builtInTag; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      for (String bundle : MESSAGE_BUNDLES) {
        File output = new File(target, bundle + ".properties"); //$NON-NLS-1$
        if (output.exists()) {
          kept++;
          continue;
        }

        String resource = bundle.replace('.', '/') + suffix + ".properties"; //$NON-NLS-1$
        try (InputStream in = openResource(resource)) {
          if (in == null)
            continue;

          copyResource(in, output);
          written++;
        }
      }

      String pluginResource;
      if ("en".equals(builtInTag)) //$NON-NLS-1$
        pluginResource = "plugin.properties"; //$NON-NLS-1$
      else
        pluginResource = "plugin_" + builtInTag + ".properties"; //$NON-NLS-1$ //$NON-NLS-2$
      for (String pluginBundle : PLUGIN_BUNDLES) {
        File output = new File(target, pluginBundle + ".plugin.properties"); //$NON-NLS-1$
        if (output.exists()) {
          kept++;
          continue;
        }

        ClassLoader classLoader = "org.rssowl.ui".equals(pluginBundle) ? LanguagePreferencePage.class.getClassLoader() : LanguageManager.class.getClassLoader(); //$NON-NLS-1$
        try (InputStream in = classLoader.getResourceAsStream(pluginResource)) {
          if (in == null)
            continue;

          copyResource(in, output);
          written++;
        }
      }

      File readme = new File(target, "README.txt"); //$NON-NLS-1$
      if (!readme.exists())
        Files.write(readme.toPath(), createReadme(tag).getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      MessageDialog.openError(getShell(), Messages.LanguagePreferencePage_EXPORT, NLS.bind(Messages.LanguagePreferencePage_EXPORT_FAILED, e.getMessage()));
      return;
    }

    if (written > 0)
      MessageDialog.openInformation(getShell(), Messages.LanguagePreferencePage_EXPORT, NLS.bind(Messages.LanguagePreferencePage_EXPORT_DONE, target.getAbsolutePath(), written));
    else if (kept > 0)
      MessageDialog.openInformation(getShell(), Messages.LanguagePreferencePage_EXPORT, NLS.bind(Messages.LanguagePreferencePage_EXPORT_EXISTS, target.getAbsolutePath()));
    else
      MessageDialog.openInformation(getShell(), Messages.LanguagePreferencePage_EXPORT, NLS.bind(Messages.LanguagePreferencePage_EXPORT_NO_BUILT_IN, tag));
  }

  private InputStream openResource(String resource) {
    ClassLoader classLoader = LanguagePreferencePage.class.getClassLoader();
    InputStream in = classLoader.getResourceAsStream(resource);
    if (in != null)
      return in;

    return LanguageManager.class.getClassLoader().getResourceAsStream(resource);
  }

  /*
   * Properties files inside the application use ISO-8859-1 with unicode escapes.
   * Language packs are read as UTF-8, so decode the escapes to make the exported
   * files directly editable.
   */
  private void copyResource(InputStream in, File output) throws Exception {
    String contents = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
    Files.writeString(output.toPath(), decodeUnicodeEscapes(contents), StandardCharsets.UTF_8);
  }

  private String decodeUnicodeEscapes(String value) {
    if (value.indexOf("\\u") < 0) //$NON-NLS-1$
      return value;

    StringBuilder result = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\\' && i + 5 < value.length() && value.charAt(i + 1) == 'u') {
        int code = 0;
        boolean valid = true;
        for (int j = 0; j < 4; j++) {
          int digit = Character.digit(value.charAt(i + 2 + j), 16);
          if (digit < 0) {
            valid = false;
            break;
          }
          code = (code << 4) | digit;
        }

        if (valid) {
          result.append((char) code);
          i += 5;
          continue;
        }
      }

      result.append(c);
    }

    return result.toString();
  }

  private String getBuiltInTag(String tag) {
    if ("en".equalsIgnoreCase(tag) || tag.regionMatches(true, 0, "en_", 0, 3) || tag.regionMatches(true, 0, "en-", 0, 3)) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      return "en"; //$NON-NLS-1$

    if ("zh_CN".equalsIgnoreCase(tag) || "zh-CN".equalsIgnoreCase(tag)) //$NON-NLS-1$ //$NON-NLS-2$
      return "zh_CN"; //$NON-NLS-1$

    return null;
  }

  private String createReadme(String tag) {
    return "RSSOwlnix language pack: " + tag + "\r\n" + //$NON-NLS-1$ //$NON-NLS-2$
        "\r\n" + //$NON-NLS-1$
        "Every .properties file in this folder contains the messages of one part of RSSOwlnix.\r\n" + //$NON-NLS-1$
        "Edit the text after '=' and keep the keys unchanged. Save files as UTF-8.\r\n" + //$NON-NLS-1$
        "Files ending in .plugin.properties contain menu, view and preference labels.\r\n" + //$NON-NLS-1$
        "Restart RSSOwlnix to reload the language pack. Files here override the built-in translations.\r\n" + //$NON-NLS-1$
        "\r\n" + //$NON-NLS-1$
        "To create another language, copy this folder, rename it to a language tag such as fr or pt_BR,\r\n" + //$NON-NLS-1$
        "and select it in Preferences > Language. The folder name can also be specified with the\r\n" + //$NON-NLS-1$
        "system property rssowlnix.languagePacks if the language pack is stored outside of the user area.\r\n" + //$NON-NLS-1$
        "\r\n" + //$NON-NLS-1$
        "中文说明：\r\n" + //$NON-NLS-1$
        "此文件夹中的每个 .properties 文件对应 RSSOwlnix 的一部分界面文字。\r\n" + //$NON-NLS-1$
        "以 .plugin.properties 结尾的文件包含菜单、视图和设置页面的标签。\r\n" + //$NON-NLS-1$
        "只需修改等号右侧的文字，不要修改等号左侧的键名；文件请保存为 UTF-8 编码。\r\n" + //$NON-NLS-1$
        "重启 RSSOwlnix 后生效，此处的文件会覆盖程序内置的翻译。\r\n" + //$NON-NLS-1$
        "如需创建其他语言，请复制此文件夹并以语言代码命名（例如 fr 或 pt_BR），\r\n" + //$NON-NLS-1$
        "然后在“设置 > 语言”中选择该语言。\r\n"; //$NON-NLS-1$
  }

  private boolean saveLanguage() {
    String tag = getSelectedTag();
    if (tag.equals(fInitialTag))
      return true;

    try {
      LanguageManager.setLanguageTag(tag);
      fInitialTag = tag;
      updateApplyEnablement(false);
      MessageDialog.openInformation(getShell(), Messages.LanguagePreferencePage_RESTART_TITLE, Messages.LanguagePreferencePage_RESTART);
      return true;
    } catch (Exception e) {
      MessageDialog.openError(getShell(), Messages.LanguagePreferencePage_RESTART_TITLE, NLS.bind(Messages.LanguagePreferencePage_SAVE_FAILED, e.getMessage()));
      return false;
    }
  }

  /*
   * @see org.eclipse.jface.preference.PreferencePage#performOk()
   */
  @Override
  public boolean performOk() {
    if (!saveLanguage())
      return false;

    return super.performOk();
  }

  /*
   * @see org.eclipse.jface.preference.PreferencePage#performApply()
   */
  @Override
  protected void performApply() {
    if (saveLanguage())
      super.performApply();
  }

  /*
   * @see org.eclipse.jface.preference.PreferencePage#performDefaults()
   */
  @Override
  protected void performDefaults() {
    fLanguageCombo.select(0);
    updateApplyEnablement(true);
  }

  private void updateApplyEnablement(boolean enable) {
    Button applyButton = getApplyButton();
    if (applyButton != null && !applyButton.isDisposed() && applyButton.isEnabled() != enable)
      applyButton.setEnabled(enable);
  }
}
