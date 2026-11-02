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

package org.rssowl.core.util;

import org.eclipse.core.runtime.Platform;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Manages the user interface language and external language packs.
 * <p>
 * The selected language is stored as <code>osgi.nl</code> in the user
 * configuration area and is read by Equinox on the next start. Language packs
 * are plain properties files below a <code>language-packs</code> directory and
 * can be edited without rebuilding the application.
 * </p>
 *
 * @author RSSOwl Development Team
 */
public final class LanguageManager {
  private static final String NL_PROPERTY = "osgi.nl"; //$NON-NLS-1$
  private static final String LANGUAGE_PACK_DIR = "language-packs"; //$NON-NLS-1$
  private static final String LANGUAGE_PACK_ROOTS_PROPERTY = "rssowlnix.languagePacks"; //$NON-NLS-1$
  private static final String LANGUAGE_OVERRIDE_PROPERTY = "rssowlnix.language"; //$NON-NLS-1$
  private static final String USER_HOME = "@user.home"; //$NON-NLS-1$
  private static final String BUILT_IN_LANGUAGES[] = { "en", "zh_CN" }; //$NON-NLS-1$ //$NON-NLS-2$

  private LanguageManager() {}

  /**
   * @return The currently effective language tag, for example
   * <code>zh_CN</code>. Never <code>null</code>.
   */
  public static String getLanguageTag() {
    String tag = System.getProperty(LANGUAGE_OVERRIDE_PROPERTY);
    if (!isSet(tag))
      tag = System.getProperty(NL_PROPERTY);
    if (!isSet(tag))
      tag = getConfiguredLanguageTag();
    if (!isSet(tag)) {
      try {
        tag = Platform.getNL();
      } catch (Throwable t) {
        /* Platform is not available outside of an Eclipse application */
      }
    }
    if (!isSet(tag))
      tag = Locale.getDefault().toString();

    return tag.trim();
  }

  /**
   * @return The language selected in the settings, or an empty string if the
   * system default language should be used.
   */
  public static String getConfiguredLanguageTag() {
    Properties properties = loadConfig();
    String tag = properties.getProperty(NL_PROPERTY);
    return tag != null ? tag.trim() : ""; //$NON-NLS-1$
  }

  /**
   * Stores the language that should be used after the next restart.
   *
   * @param tag The language tag, or an empty string to use the system default.
   * @throws Exception If the configuration could not be written.
   */
  public static void setLanguageTag(String tag) throws Exception {
    File configFile = getConfigFile();
    Properties properties = loadConfig();

    if (isSet(tag))
      properties.setProperty(NL_PROPERTY, tag.trim());
    else
      properties.remove(NL_PROPERTY);

    File parent = configFile.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs())
      throw new Exception("Could not create the configuration directory."); //$NON-NLS-1$

    File tempFile = new File(configFile.getPath() + ".tmp"); //$NON-NLS-1$
    try (OutputStream out = Files.newOutputStream(tempFile.toPath())) {
      properties.store(out, "RSSOwlnix language configuration"); //$NON-NLS-1$
    }

    try {
      Files.move(tempFile.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (Exception e) {
      Files.move(tempFile.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /**
   * @return The locale for the selected language.
   */
  public static Locale getLocale() {
    return toLocale(getLanguageTag());
  }

  /**
   * @param tag A language tag such as <code>zh_CN</code>.
   * @return The locale for the tag.
   */
  public static Locale toLocale(String tag) {
    if (!isSet(tag))
      return Locale.getDefault();

    String normalized = tag.trim().replace('-', '_');
    String parts[] = normalized.split("_"); //$NON-NLS-1$
    if (parts.length == 1)
      return new Locale(parts[0]);

    if (parts.length == 2)
      return new Locale(parts[0], parts[1]);

    return new Locale(parts[0], parts[1], parts[2]);
  }

  /**
   * @param tag A language tag such as <code>zh_CN</code>.
   * @return The name of the language in the current language.
   */
  public static String getDisplayName(String tag) {
    Locale locale = toLocale(tag);
    String name = locale.getDisplayName(getLocale());
    return isSet(name) ? name : tag;
  }

  /**
   * @return All languages that are either built in or available as an external
   * language pack.
   */
  public static List<String> getAvailableLanguageTags() {
    Set<String> tags = new LinkedHashSet<>();

    for (String tag : BUILT_IN_LANGUAGES)
      tags.add(tag);

    for (File root : getLanguagePackRoots()) {
      File children[] = root.listFiles();
      if (children == null)
        continue;

      for (File child : children) {
        if (child.isDirectory() && isLanguageTag(child.getName()) && containsProperties(child))
          tags.add(child.getName());
      }
    }

    return new ArrayList<>(tags);
  }

  /**
   * @return The directories that are searched for external language packs.
   * Directories later in the list take precedence.
   */
  public static List<File> getLanguagePackRoots() {
    List<File> roots = new ArrayList<>();

    String configuredRoots = System.getProperty(LANGUAGE_PACK_ROOTS_PROPERTY);
    if (isSet(configuredRoots)) {
      for (String path : configuredRoots.split(File.pathSeparator)) {
        if (isSet(path))
          addRoot(roots, new File(path.trim()));
      }
      return roots;
    }

    addRoot(roots, new File(getInstallDirectory(), LANGUAGE_PACK_DIR));
    addRoot(roots, new File(getInstanceDirectory(), LANGUAGE_PACK_DIR));

    return roots;
  }

  /**
   * @return The language pack directory that can be edited by the user.
   */
  public static File getWritableLanguagePackRoot() {
    return new File(getInstanceDirectory(), LANGUAGE_PACK_DIR);
  }

  /**
   * @param tag A language tag.
   * @return The writable directory for the language pack.
   */
  public static File getWritableLanguagePackDirectory(String tag) {
    return new File(getWritableLanguagePackRoot(), tag);
  }

  private static void addRoot(List<File> roots, File root) {
    if (root == null || !root.isDirectory())
      return;

    for (File existing : roots) {
      if (existing.equals(root))
        return;
    }

    roots.add(root);
  }

  private static boolean containsProperties(File directory) {
    File children[] = directory.listFiles();
    if (children == null)
      return false;

    for (File child : children) {
      if (child.isFile() && child.getName().endsWith(".properties")) //$NON-NLS-1$
        return true;
      if (child.isDirectory() && containsProperties(child))
        return true;
    }

    return false;
  }

  private static boolean isLanguageTag(String name) {
    return name.matches("[a-z]{2,3}([_-][A-Za-z]{2,4})?"); //$NON-NLS-1$
  }

  private static File getConfigFile() {
    File directory = getConfigurationDirectory();
    return new File(directory, "config.ini"); //$NON-NLS-1$
  }

  private static Properties loadConfig() {
    Properties properties = new Properties();
    File configFile = getConfigFile();
    if (!configFile.isFile())
      return properties;

    try (InputStream in = Files.newInputStream(configFile.toPath())) {
      properties.load(in);
    } catch (Exception e) {
      /* Ignore a broken configuration and fall back to the system language */
    }

    return properties;
  }

  private static File getConfigurationDirectory() {
    String configured = System.getProperty("osgi.configuration.area"); //$NON-NLS-1$
    File directory = toFile(configured);
    if (directory != null)
      return directory;

    try {
      URL url = Platform.getConfigurationLocation().getURL();
      if (url != null)
        return new File(url.toURI());
    } catch (Throwable t) {
      /* Platform is not available outside of an Eclipse application */
    }

    return new File(new File(System.getProperty("user.home"), ".rssowlnix-beta"), "config"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
  }

  private static File getInstanceDirectory() {
    String configured = System.getProperty("osgi.instance.area"); //$NON-NLS-1$
    File directory = toFile(configured);
    if (directory != null)
      return directory;

    try {
      URL url = Platform.getInstanceLocation().getURL();
      if (url != null)
        return new File(url.toURI());
    } catch (Throwable t) {
      /* Platform is not available outside of an Eclipse application */
    }

    return new File(System.getProperty("user.home"), ".rssowlnix-beta"); //$NON-NLS-1$ //$NON-NLS-2$
  }

  private static File getInstallDirectory() {
    try {
      URL url = Platform.getInstallLocation().getURL();
      if (url != null)
        return new File(url.toURI());
    } catch (Throwable t) {
      /* Platform is not available outside of an Eclipse application */
    }

    return new File(System.getProperty("user.dir")); //$NON-NLS-1$
  }

  private static File toFile(String path) {
    if (!isSet(path))
      return null;

    String expanded = path.replace(USER_HOME, System.getProperty("user.home")); //$NON-NLS-1$
    if (expanded.startsWith("file:")) { //$NON-NLS-1$
      try {
        return new File(new URI(expanded));
      } catch (Exception e) {
        return null;
      }
    }

    return new File(expanded);
  }

  private static boolean isSet(String value) {
    return value != null && value.trim().length() > 0;
  }
}
