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

package org.rssowl.ui.internal.util;

import org.eclipse.core.runtime.IProgressMonitor;
import org.rssowl.core.Owl;
import org.rssowl.core.connection.IProtocolHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves video placeholders created by {@link ArticleExtractor} into
 * playable HTML5 video elements.
 *
 * @author RSSOwl Development Team
 */
public final class VideoResolver {

  private static final String API_URL = "https://playvideo.qcloud.com/getplayinfo/v2/"; //$NON-NLS-1$

  private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("(?is)<div\\b([^>]*\\bdata-rssowl-video\\s*=\\s*[\"']tencent[\"'][^>]*)></div>"); //$NON-NLS-1$

  private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))"); //$NON-NLS-1$

  private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\""); //$NON-NLS-1$

  private static final Pattern DEFINITION_PATTERN = Pattern.compile("\"definition\"\\s*:\\s*(\\d+)"); //$NON-NLS-1$

  private static final Pattern CODE_PATTERN = Pattern.compile("\"code\"\\s*:\\s*(\\d+)"); //$NON-NLS-1$

  private static final class VideoEntry {
    final String url;
    final int definition;

    VideoEntry(String url, int definition) {
      this.url = url;
      this.definition = definition;
    }
  }

  private VideoResolver() {
    // Do not instantiate
  }

  /**
   * Resolves all supported video placeholders in the given HTML.
   *
   * @param html the article HTML.
   * @param monitor the progress monitor or <code>null</code>.
   * @return the HTML with playable video elements.
   */
  public static String resolve(String html, IProgressMonitor monitor) {
    if (html == null || html.indexOf("data-rssowl-video") == -1) //$NON-NLS-1$
      return html;

    Matcher matcher = PLACEHOLDER_PATTERN.matcher(html);
    StringBuffer result = new StringBuffer(html.length() + 512);
    while (matcher.find()) {
      if (isCanceled(monitor))
        return html;

      Map<String, String> attributes = parseAttributes(matcher.group(1));
      String appId = attributes.get("data-app-id"); //$NON-NLS-1$
      String fileId = attributes.get("data-file-id"); //$NON-NLS-1$
      String poster = attributes.get("data-poster"); //$NON-NLS-1$

      String replacement = resolveTencentVideo(appId, fileId, poster, monitor);
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);

    return result.toString();
  }

  private static String resolveTencentVideo(String appId, String fileId, String poster, IProgressMonitor monitor) {
    String videoUrl = null;
    if (isSet(appId) && isSet(fileId))
      videoUrl = fetchVideoUrl(appId, fileId, monitor);

    if (isSet(videoUrl))
      return createVideoTag(videoUrl, poster);

    if (isSet(poster)) {
      return "<div class=\"rssowl-video\"><img src=\"" + escapeAttribute(toHttps(poster)) + "\" alt=\"\"/></div>"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    return ""; //$NON-NLS-1$
  }

  private static String fetchVideoUrl(String appId, String fileId, IProgressMonitor monitor) {
    InputStream input = null;
    try {
      URI uri = new URI(API_URL + appId + "/" + fileId); //$NON-NLS-1$
      IProtocolHandler handler = Owl.getConnectionService().getHandler(uri);
      if (handler == null)
        return null;

      input = handler.openStream(uri, monitor, null);
      ByteArrayOutputStream output = new ByteArrayOutputStream(16 * 1024);
      byte[] buffer = new byte[8192];
      int total = 0;
      int read;
      while (!isCanceled(monitor) && (read = input.read(buffer)) != -1) {
        int bytesToWrite = Math.min(read, MAX_RESPONSE_BYTES - total);
        if (bytesToWrite <= 0)
          break;

        output.write(buffer, 0, bytesToWrite);
        total += bytesToWrite;
      }

      if (isCanceled(monitor) || total == 0)
        return null;

      String json = new String(output.toByteArray(), StandardCharsets.UTF_8);
      Matcher code = CODE_PATTERN.matcher(json);
      if (code.find() && !"0".equals(code.group(1))) //$NON-NLS-1$
        return null;

      return selectVideoUrl(json);
    } catch (Exception e) {
      return null;
    } finally {
      if (input != null) {
        try {
          input.close();
        } catch (IOException e) {
          // Ignore
        }
      }
    }
  }

  private static String selectVideoUrl(String json) {
    List<VideoEntry> entries = new ArrayList<VideoEntry>();
    Matcher matcher = URL_PATTERN.matcher(json);
    while (matcher.find()) {
      String url = matcher.group(1).replace("\\/", "/"); //$NON-NLS-1$ //$NON-NLS-2$
      if (!url.toLowerCase(Locale.ENGLISH).contains(".mp4")) //$NON-NLS-1$
        continue;

      int start = matcher.end();
      int end = json.indexOf("\"url\"", start); //$NON-NLS-1$
      String segment = end != -1 ? json.substring(start, end) : json.substring(start);
      Matcher definition = DEFINITION_PATTERN.matcher(segment);
      int definitionValue = definition.find() ? Integer.parseInt(definition.group(1)) : 0;
      entries.add(new VideoEntry(url, definitionValue));
    }

    VideoEntry best = null;
    for (VideoEntry entry : entries) {
      if (entry.definition == 30) {
        best = entry;
        break;
      }

      if (entry.definition > 0 && entry.definition <= 40 && (best == null || entry.definition > best.definition))
        best = entry;
    }

    if (best == null) {
      for (VideoEntry entry : entries) {
        if (entry.definition <= 40 && (best == null || entry.definition > best.definition))
          best = entry;
      }
    }

    if (best == null && !entries.isEmpty())
      best = entries.get(entries.size() - 1);

    return best != null ? best.url : null;
  }

  private static String createVideoTag(String url, String poster) {
    StringBuilder result = new StringBuilder();
    result.append("<div class=\"rssowl-video\"><video controls=\"controls\" playsinline=\"playsinline\" preload=\"metadata\""); //$NON-NLS-1$
    if (isSet(poster))
      result.append(" poster=\"").append(escapeAttribute(toHttps(poster))).append('"'); //$NON-NLS-1$
    result.append(" style=\"max-width:100%;max-height:80vh;display:block;margin:1em auto;\"><source src=\"").append(escapeAttribute(toHttps(url))).append("\" type=\"video/mp4\"/></video></div>"); //$NON-NLS-1$ //$NON-NLS-2$
    return result.toString();
  }

  private static Map<String, String> parseAttributes(String attributes) {
    Map<String, String> result = new HashMap<String, String>();
    if (attributes == null)
      return result;

    Matcher matcher = ATTRIBUTE_PATTERN.matcher(attributes);
    while (matcher.find()) {
      String name = matcher.group(1).toLowerCase(Locale.ENGLISH);
      String value = matcher.group(2) != null ? matcher.group(2) : (matcher.group(3) != null ? matcher.group(3) : matcher.group(4));
      result.put(name, unescapeHtml(value != null ? value : "")); //$NON-NLS-1$
    }

    return result;
  }

  private static String unescapeHtml(String value) {
    return value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
  }

  private static String toHttps(String url) {
    if (url == null)
      return null;

    if (url.startsWith("http://")) //$NON-NLS-1$
      return "https://" + url.substring(7); //$NON-NLS-1$

    return url;
  }

  private static String escapeAttribute(String value) {
    StringBuilder result = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '&':
          result.append("&amp;"); //$NON-NLS-1$
          break;
        case '<':
          result.append("&lt;"); //$NON-NLS-1$
          break;
        case '>':
          result.append("&gt;"); //$NON-NLS-1$
          break;
        case '"':
          result.append("&quot;"); //$NON-NLS-1$
          break;
        default:
          result.append(c);
      }
    }
    return result.toString();
  }

  private static boolean isCanceled(IProgressMonitor monitor) {
    return monitor != null && monitor.isCanceled();
  }

  private static boolean isSet(String value) {
    return value != null && value.trim().length() > 0;
  }
}
