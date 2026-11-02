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

package org.rssowl.i18n;

import org.eclipse.osgi.service.localization.BundleLocalization;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Merges the built-in <code>plugin.properties</code> files with external
 * language pack overrides so that menu, view and preference labels can be
 * translated without rebuilding the application.
 *
 * @author RSSOwl Development Team
 */
public class ExternalBundleLocalization implements BundleLocalization {
  private static final String LANGUAGE_PACK_DIR = "language-packs"; //$NON-NLS-1$
  private static final String LANGUAGE_PACK_ROOTS_PROPERTY = "rssowlnix.languagePacks"; //$NON-NLS-1$
  private static final String LANGUAGE_OVERRIDE_PROPERTY = "rssowlnix.language"; //$NON-NLS-1$
  private static final String NL_PROPERTY = "osgi.nl"; //$NON-NLS-1$
  private static final String USER_HOME = "@user.home"; //$NON-NLS-1$
  private static final ConcurrentHashMap<String, ResourceBundle> CACHE = new ConcurrentHashMap<>();
  private static List<File> fgRoots;

  /*
   * @see org.eclipse.osgi.service.localization.BundleLocalization#getLocalization(org.osgi.framework.Bundle, java.lang.String)
   */
  @Override
  public ResourceBundle getLocalization(Bundle bundle, String locale) {
    String baseName = getBaseName(bundle);
    if (baseName == null)
      return null;

    String tag = getLanguageTag();
    if (System.getProperty("rssowlnix.debugLanguage") != null) //$NON-NLS-1$
      System.err.println("RSSOwlnix i18n: getLocalization " + bundle.getSymbolicName() + " base=" + baseName + " locale=" + locale + " tag=" + tag); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    String cacheKey = bundle.getBundleId() + "|" + baseName + "|" + locale + "|" + tag; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    ResourceBundle cached = CACHE.get(cacheKey);
    if (cached != null)
      return cached;

    ResourceBundle result = loadLocalization(bundle, baseName, locale, tag);
    if (result != null) {
      ResourceBundle existing = CACHE.putIfAbsent(cacheKey, result);
      if (existing != null)
        return existing;
      if (System.getProperty("rssowlnix.debugLanguage") != null) //$NON-NLS-1$
        System.err.println("RSSOwlnix i18n: localization for " + bundle.getSymbolicName() + " [" + tag + "]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    return result;
  }

  private String getBaseName(Bundle bundle) {
    String header = bundle.getHeaders().get(Constants.BUNDLE_LOCALIZATION);
    if (header == null)
      return "plugin"; //$NON-NLS-1$

    int semicolon = header.indexOf(';');
    if (semicolon >= 0)
      header = header.substring(0, semicolon);

    header = header.trim();
    return header.length() > 0 ? header : "plugin"; //$NON-NLS-1$
  }

  private ResourceBundle loadLocalization(Bundle bundle, String baseName, String locale, String selectedTag) {
    Properties properties = new Properties();

    loadBuiltIn(bundle, baseName + ".properties", properties); //$NON-NLS-1$

    Set<String> locales = new LinkedHashSet<>();
    addLocale(locales, selectedTag);
    addLocale(locales, locale);

    for (String candidate : locales)
      loadBuiltIn(bundle, baseName + "_" + candidate + ".properties", properties); //$NON-NLS-1$ //$NON-NLS-2$

    loadExternal(bundle, baseName, selectedTag, properties);

    if (properties.isEmpty())
      return null;

    return new PropertiesResourceBundle(properties);
  }

  private void addLocale(Set<String> locales, String locale) {
    if (locale == null || locale.trim().length() == 0)
      return;

    String normalized = locale.trim().replace('-', '_');
    int separator = normalized.indexOf('_');
    if (separator > 0)
      locales.add(normalized.substring(0, separator));
    locales.add(normalized);
  }

  private void loadBuiltIn(Bundle bundle, String path, Properties properties) {
    try (InputStream in = openStream(bundle, path)) {
      if (in != null)
        properties.load(in);
    } catch (Exception e) {
      /* Ignore a broken localization file and keep the values loaded so far */
    }
  }

  private InputStream openStream(Bundle bundle, String path) throws Exception {
    URL url = bundle.getEntry(path);
    if (url == null)
      url = bundle.getResource(path);
    return url != null ? url.openStream() : null;
  }

  private void loadExternal(Bundle bundle, String baseName, String tag, Properties properties) {
    String symbolicName = bundle.getSymbolicName();
    if (symbolicName == null)
      return;

    String flatName = symbolicName + "." + baseName.replace('/', '.') + ".properties"; //$NON-NLS-1$ //$NON-NLS-2$
    String nestedName = baseName + ".properties"; //$NON-NLS-1$

    for (File root : getLanguagePackRoots()) {
      for (String candidate : getCandidateTags(tag)) {
        File tagDirectory = new File(root, candidate);
        if (!tagDirectory.isDirectory())
          continue;

        File nested = new File(new File(tagDirectory, symbolicName), nestedName);
        if (nested.isFile())
          loadFile(nested, properties);

        File flat = new File(tagDirectory, flatName);
        if (flat.isFile())
          loadFile(flat, properties);
      }
    }
  }

  private Set<String> getCandidateTags(String tag) {
    Set<String> tags = new LinkedHashSet<>();
    String normalized = tag.trim().replace('-', '_');
    int separator = normalized.indexOf('_');
    if (separator > 0)
      tags.add(normalized.substring(0, separator));
    tags.add(normalized);
    return tags;
  }

  private void loadFile(File file, Properties properties) {
    try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
      properties.load(reader);
      if (System.getProperty("rssowlnix.debugLanguage") != null) //$NON-NLS-1$
        System.err.println("RSSOwlnix i18n: loaded " + file); //$NON-NLS-1$
    } catch (Exception e) {
      System.err.println("RSSOwlnix i18n: could not read " + file + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
    }
  }

  private static synchronized List<File> getLanguagePackRoots() {
    if (fgRoots != null)
      return fgRoots;

    List<File> roots = new ArrayList<>();
    String configuredRoots = System.getProperty(LANGUAGE_PACK_ROOTS_PROPERTY);
    if (configuredRoots != null && configuredRoots.trim().length() > 0) {
      for (String path : configuredRoots.split(File.pathSeparator)) {
        if (path.trim().length() > 0)
          addRoot(roots, new File(path.trim()));
      }
      fgRoots = roots;
      return fgRoots;
    }

    addRoot(roots, new File(getInstallDirectory(), LANGUAGE_PACK_DIR));
    addRoot(roots, new File(getInstanceDirectory(), LANGUAGE_PACK_DIR));

    fgRoots = roots;
    return fgRoots;
  }

  private static void addRoot(List<File> roots, File root) {
    if (root == null || !root.isDirectory())
      return;

    for (File existing : roots) {
      try {
        if (existing.getCanonicalFile().equals(root.getCanonicalFile()))
          return;
      } catch (Exception e) {
        if (existing.equals(root))
          return;
      }
    }

    roots.add(root);
  }

  private static File getInstallDirectory() {
    File directory = toFile(System.getProperty("osgi.install.area")); //$NON-NLS-1$
    return directory != null ? directory : new File(System.getProperty("user.dir")); //$NON-NLS-1$
  }

  private static File getInstanceDirectory() {
    File directory = toFile(System.getProperty("osgi.instance.area")); //$NON-NLS-1$
    if (directory == null)
      directory = toFile(System.getProperty("osgi.instance.area.default")); //$NON-NLS-1$
    if (directory == null)
      directory = new File(System.getProperty("user.home"), ".rssowlnix-beta"); //$NON-NLS-1$ //$NON-NLS-2$
    return directory;
  }

  private static File toFile(String path) {
    if (path == null || path.trim().length() == 0)
      return null;

    String expanded = path.trim().replace(USER_HOME, System.getProperty("user.home")); //$NON-NLS-1$
    if (expanded.startsWith("file:")) { //$NON-NLS-1$
      try {
        return new File(new URI(expanded));
      } catch (Exception e) {
        return null;
      }
    }

    return new File(expanded);
  }

  private static String getLanguageTag() {
    String tag = System.getProperty(LANGUAGE_OVERRIDE_PROPERTY);
    if (tag == null || tag.trim().length() == 0)
      tag = System.getProperty(NL_PROPERTY);
    if (tag == null || tag.trim().length() == 0)
      tag = Locale.getDefault().toString();
    return tag.trim();
  }

  private static final class PropertiesResourceBundle extends ResourceBundle {
    private final Properties fProperties;

    PropertiesResourceBundle(Properties properties) {
      fProperties = properties;
    }

    /*
     * @see java.util.ResourceBundle#handleGetObject(java.lang.String)
     */
    @Override
    protected Object handleGetObject(String key) {
      return fProperties.getProperty(key);
    }

    /*
     * @see java.util.ResourceBundle#getKeys()
     */
    @Override
    public Enumeration<String> getKeys() {
      return Collections.enumeration(fProperties.stringPropertyNames());
    }
  }
}
