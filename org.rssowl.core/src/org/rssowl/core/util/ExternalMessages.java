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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Initializes NLS message classes from the built-in resource bundle and then
 * applies properties from external language packs.
 * <p>
 * This behaves like {@link org.eclipse.osgi.util.NLS#initializeMessages(String, Class)}
 * but allows individual messages to be overridden without rebuilding the
 * application. External language packs are looked up by
 * {@link LanguageManager} and use the same bundle name and keys, for example
 * <code>language-packs/zh_CN/org.rssowl.ui.internal.dialogs.messages.properties</code>
 * or <code>language-packs/zh_CN/org.rssowl.ui.internal.dialogs/messages.properties</code>.
 * </p>
 *
 * @author RSSOwl Development Team
 */
public final class ExternalMessages {
  private static final ConcurrentHashMap<String, Properties> EXTERNAL_CACHE = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, Boolean> WARNED_BUNDLES = new ConcurrentHashMap<>();

  private ExternalMessages() {}

  /**
   * Initializes all public static String fields of the given class from the
   * resource bundle with the same name and from the external language pack.
   *
   * @param bundleName The fully qualified resource bundle name.
   * @param clazz The class whose fields are initialized.
   */
  public static void initializeMessages(String bundleName, Class<?> clazz) {
    String tag = LanguageManager.getLanguageTag();
    Locale locale = LanguageManager.toLocale(tag);
    ResourceBundle bundle = loadBundle(bundleName, locale, clazz.getClassLoader());

    Properties external = getExternalProperties(bundleName, tag);

    for (Field field : clazz.getFields()) {
      if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class)
        continue;

      String key = field.getName();
      String value = null;

      if (bundle != null && bundle.containsKey(key)) {
        try {
          value = bundle.getString(key);
        } catch (MissingResourceException e) {
          /* Fall through to the external pack, if any */
        }
      }

      if (external.containsKey(key))
        value = external.getProperty(key);

      if (value == null)
        continue;

      try {
        field.set(null, value);
      } catch (IllegalAccessException e) {
        /* The class does not expose the message field as expected */
      }
    }
  }

  /**
   * @param bundleName The fully qualified resource bundle name.
   * @param tag The language tag such as <code>zh_CN</code>.
   * @return The external overrides for the bundle. Never <code>null</code>.
   */
  public static Properties getExternalProperties(String bundleName, String tag) {
    String cacheKey = bundleName + '\0' + tag;
    Properties cached = EXTERNAL_CACHE.get(cacheKey);
    if (cached != null)
      return cached;

    Properties properties = loadExternalProperties(bundleName, tag);
    Properties existing = EXTERNAL_CACHE.putIfAbsent(cacheKey, properties);
    return existing != null ? existing : properties;
  }

  /**
   * @param bundleName The fully qualified resource bundle name.
   * @param tag The language tag such as <code>zh_CN</code>.
   * @return <code>true</code> if an external language pack contains a file for
   * the bundle.
   */
  public static boolean hasExternalProperties(String bundleName, String tag) {
    return !getExternalProperties(bundleName, tag).isEmpty();
  }

  private static ResourceBundle loadBundle(String bundleName, Locale locale, ClassLoader classLoader) {
    try {
      ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
      return ResourceBundle.getBundle(bundleName, locale, classLoader, control);
    } catch (MissingResourceException e) {
      if (WARNED_BUNDLES.putIfAbsent(bundleName, Boolean.TRUE) == null)
        System.err.println("RSSOwlnix: missing message bundle " + bundleName); //$NON-NLS-1$
      return null;
    }
  }

  private static Properties loadExternalProperties(String bundleName, String tag) {
    Properties properties = new Properties();
    if (tag == null || tag.trim().length() == 0)
      return properties;

    String relativePath = bundleName.replace('.', '/') + ".properties"; //$NON-NLS-1$
    String flatName = bundleName + ".properties"; //$NON-NLS-1$

    for (File root : LanguageManager.getLanguagePackRoots()) {
      for (String candidate : getCandidateTags(tag)) {
        File directory = new File(root, candidate);

        File nested = new File(directory, relativePath);
        if (nested.isFile())
          loadFile(properties, nested);

        File flat = new File(directory, flatName);
        if (flat.isFile())
          loadFile(properties, flat);
      }
    }

    return properties;
  }

  private static Set<String> getCandidateTags(String tag) {
    Set<String> tags = new LinkedHashSet<>();
    String normalized = tag.trim().replace('-', '_');
    int separator = normalized.indexOf('_');
    if (separator > 0)
      tags.add(normalized.substring(0, separator));
    tags.add(normalized);
    return tags;
  }

  private static void loadFile(Properties properties, File file) {
    try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
      properties.load(reader);
    } catch (Exception e) {
      System.err.println("RSSOwlnix: could not read language pack " + file + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
    }
  }
}
