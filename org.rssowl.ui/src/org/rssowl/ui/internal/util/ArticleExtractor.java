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

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the main article body from an HTML page. This is used as a local
 * fallback when the online full-content service only returns a short teaser.
 *
 * @author RSSOwl Development Team
 */
public final class ArticleExtractor {

  private static final int MIN_ARTICLE_TEXT_LENGTH = 200;

  private static final Set<String> VOID_TAGS = set("area base br col embed hr img input link meta param source track wbr"); //$NON-NLS-1$

  private static final Set<String> SKIP_TAGS = set("applet audio base button canvas embed form head iframe input link meta noscript object script select source style svg textarea video"); //$NON-NLS-1$

  private static final Set<String> BLOCK_TAGS = set("blockquote caption dd div dl dt figcaption figure h1 h2 h3 h4 h5 h6 hr li main ol p pre section table tbody td tfoot th thead tr ul"); //$NON-NLS-1$

  private static final Set<String> KEEP_TAGS = set("a b big blockquote br caption cite code dd del div dl dt em figcaption figure h1 h2 h3 h4 h5 h6 hr i img ins kbd li mark ol p pre q s samp small strike strong sub sup table tbody td tfoot th thead tr u ul var"); //$NON-NLS-1$

  private static final Set<String> INLINE_TAGS = set("font label span"); //$NON-NLS-1$

  private static final Set<String> CELL_TAGS = set("td th"); //$NON-NLS-1$

  private static final Set<String> SEMANTIC_SKIP_TAGS = set("aside footer header nav"); //$NON-NLS-1$

  private static final Pattern HTML5_START_TAG = Pattern.compile("(?i)<(article|main|section|figure|figcaption|header|footer|nav|aside)(\\s[^>]*)?>"); //$NON-NLS-1$

  private static final Pattern HTML5_END_TAG = Pattern.compile("(?i)</(article|main|section|figure|figcaption|header|footer|nav|aside)\\s*>"); //$NON-NLS-1$

  private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)(?:<meta[^>]+charset\\s*=\\s*[\"']?([a-z0-9._-]+)|charset\\s*=\\s*[\"']?([a-z0-9._-]+))"); //$NON-NLS-1$

  private static final Pattern VIDEO_PATTERN = Pattern.compile("(?is)<video\\b([^>]*?)(?:/\\s*>|>(.*?)</video\\s*>)"); //$NON-NLS-1$

  private static final Pattern AUDIO_PATTERN = Pattern.compile("(?is)<audio\\b([^>]*?)(?:/\\s*>|>(.*?)</audio\\s*>)"); //$NON-NLS-1$

  private static final Pattern IFRAME_PATTERN = Pattern.compile("(?is)<iframe\\b([^>]*?)(?:/\\s*>|>(.*?)</iframe\\s*>)"); //$NON-NLS-1$

  private static final Pattern SOURCE_PATTERN = Pattern.compile("(?is)<source\\b([^>]*)>"); //$NON-NLS-1$

  private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))"); //$NON-NLS-1$

  private static final String TENCENT_VIDEO_APP_ID = "1251434507"; //$NON-NLS-1$

  private static final Pattern BLOGGER_IMAGE_SIZE_PATTERN = Pattern.compile("(/img/b/[^/]+/[^/]+/)(?:s\\d+|w\\d+(?:-h\\d+)?)(?:-[a-z0-9-]+)?/"); //$NON-NLS-1$

  /**
   * The result of an article extraction.
   */
  public static final class Result {
    private final String fHtml;
    private final int fTextLength;
    private final boolean fHasMedia;

    Result(String html, int textLength, boolean hasMedia) {
      fHtml = html;
      fTextLength = textLength;
      fHasMedia = hasMedia;
    }

    /**
     * @return the cleaned article HTML.
     */
    public String getHtml() {
      return fHtml;
    }

    /**
     * @return the number of non-whitespace characters in the article.
     */
    public int getTextLength() {
      return fTextLength;
    }

    /**
     * @return <code>true</code> if the article contains playable media even
     * without any text (for example a video-only article).
     */
    public boolean hasMedia() {
      return fHasMedia;
    }
  }

  private static final class Node {
    final String tag;
    String semanticTag;
    final List<Object> children = new ArrayList<Object>();
    final Map<String, String> attributes = new HashMap<String, String>();

    Node(String tag) {
      this(tag, null);
    }

    Node(String tag, String semanticTag) {
      this.tag = tag;
      this.semanticTag = semanticTag;
    }

    String getAttribute(String name) {
      return attributes.get(name);
    }
  }

  private ArticleExtractor() {
    // Do not instantiate
  }

  /**
   * Extracts the main article content from the given HTML.
   *
   * @param html the HTML page.
   * @param base the base URI used to resolve relative links.
   * @return the extraction result or <code>null</code> if no suitable article
   * container was found.
   */
  public static Result extract(String html, URI base) {
    Node root = parse(html);
    if (root == null)
      return null;

    Map<Node, Node> parents = new HashMap<Node, Node>();
    Node best = findBestCandidate(root, parents);
    if (best == null)
      best = findVideoCandidate(root);
    if (best == null)
      return null;

    int textLength = textLength(best);
    boolean hasMedia = containsVideo(best);
    if (textLength < MIN_ARTICLE_TEXT_LENGTH && !hasMedia)
      return null;

    /* If the text lives next to a video player, include the player as well */
    Node expanded = expandToVideo(best, parents, textLength);
    if (expanded != null) {
      best = expanded;
      textLength = textLength(best);
      hasMedia = containsVideo(best);
    }

    StringBuilder result = new StringBuilder(Math.max(256, textLength * 2));
    if (isVideoNode(best))
      renderNode(best, result, base);
    else
      renderChildren(best, result, base);
    String cleaned = result.toString().trim();
    if (cleaned.length() == 0)
      return null;

    return new Result("<div class=\"rssowl-article\">" + cleaned + "</div>", textLength, hasMedia); //$NON-NLS-1$ //$NON-NLS-2$
  }

  /**
   * @param html an HTML fragment.
   * @return the number of non-whitespace characters in the fragment.
   */
  public static int textLength(String html) {
    Node root = parse(html);
    return root != null ? textLength(root) : 0;
  }

  /**
   * Decodes an HTML byte stream using its byte-order mark or declared charset.
   *
   * @param data the raw HTML bytes.
   * @return the decoded HTML.
   */
  public static String decode(byte[] data) {
    if (data == null || data.length == 0)
      return ""; //$NON-NLS-1$

    if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF)
      return new String(data, 3, data.length - 3, StandardCharsets.UTF_8);

    if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE)
      return new String(data, 2, data.length - 2, StandardCharsets.UTF_16LE);

    if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF)
      return new String(data, 2, data.length - 2, StandardCharsets.UTF_16BE);

    String prefix = new String(data, 0, Math.min(data.length, 8192), StandardCharsets.ISO_8859_1);
    Matcher matcher = CHARSET_PATTERN.matcher(prefix);
    if (matcher.find()) {
      String charsetName = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
      try {
        return new String(data, Charset.forName(charsetName));
      } catch (Exception e) {
        // Fall through to UTF-8
      }
    }

    return new String(data, StandardCharsets.UTF_8);
  }

  private static Node parse(String html) {
    if (html == null || html.length() == 0)
      return null;

    html = normalizeMediaTags(html);
    html = normalizeHtml5Tags(html);

    final Node root = new Node("#root"); //$NON-NLS-1$
    final Deque<Node> stack = new ArrayDeque<Node>();
    stack.push(root);

    try {
      ParserDelegator parser = new ParserDelegator();
      parser.parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
          Node node = createNode(tag, attributes);
          stack.peek().children.add(node);
          if (!VOID_TAGS.contains(node.tag))
            stack.push(node);
        }

        @Override
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
          stack.peek().children.add(createNode(tag, attributes));
        }

        @Override
        public void handleEndTag(HTML.Tag tag, int position) {
          String name = tag.toString().toLowerCase(Locale.ENGLISH);
          boolean present = false;
          for (Node node : stack) {
            if (node.tag.equals(name)) {
              present = true;
              break;
            }
          }

          if (present) {
            while (stack.size() > 1) {
              Node node = stack.pop();
              if (node.tag.equals(name))
                break;
            }
          }
        }

        @Override
        public void handleText(char[] data, int position) {
          stack.peek().children.add(new String(data));
        }

        @Override
        public void handleComment(char[] data, int position) {
          // Ignore comments
        }

        @Override
        public void handleError(String errorMsg, int position) {
          // Be tolerant of malformed HTML
        }
      }, true);
    } catch (Exception e) {
      return null;
    }

    return root;
  }

  private static String normalizeMediaTags(String html) {
    StringBuffer result = new StringBuffer(html.length() + 256);

    Matcher matcher = VIDEO_PATTERN.matcher(html);
    while (matcher.find()) {
      String replacement = createMediaPlaceholder("video", matcher.group(1), matcher.group(2)); //$NON-NLS-1$
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);

    String normalized = result.toString();
    result.setLength(0);
    matcher = AUDIO_PATTERN.matcher(normalized);
    while (matcher.find()) {
      String replacement = createMediaPlaceholder("audio", matcher.group(1), matcher.group(2)); //$NON-NLS-1$
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);

    normalized = result.toString();
    result.setLength(0);
    matcher = IFRAME_PATTERN.matcher(normalized);
    while (matcher.find()) {
      String attributes = matcher.group(1);
      String src = parseAttributes(attributes).get("src"); //$NON-NLS-1$
      String replacement = isVideoIframe(src) ? createMediaPlaceholder("iframe", attributes, null) : ""; //$NON-NLS-1$ //$NON-NLS-2$
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);

    return result.toString();
  }

  private static String createMediaPlaceholder(String media, String attributes, String inner) {
    Map<String, String> values = parseAttributes(attributes);
    String src = values.get("src"); //$NON-NLS-1$
    String type = values.get("type"); //$NON-NLS-1$
    String poster = values.get("poster"); //$NON-NLS-1$

    if (!isSet(src) && inner != null) {
      Matcher sources = SOURCE_PATTERN.matcher(inner);
      while (sources.find()) {
        Map<String, String> sourceValues = parseAttributes(sources.group(1));
        String sourceSrc = sourceValues.get("src"); //$NON-NLS-1$
        if (!isSet(sourceSrc))
          continue;

        if (!isSet(src) || (isMp4(sourceSrc) && !isMp4(src))) {
          src = sourceSrc;
          type = sourceValues.get("type"); //$NON-NLS-1$
        }
      }
    }

    if (!isSet(src))
      return ""; //$NON-NLS-1$

    StringBuilder placeholder = new StringBuilder();
    placeholder.append("<div data-rssowl-media=\"").append(escapeAttribute(media)).append("\" data-src=\"").append(escapeAttribute(src)).append('"'); //$NON-NLS-1$ //$NON-NLS-2$
    if (isSet(type))
      placeholder.append(" data-type=\"").append(escapeAttribute(type)).append('"'); //$NON-NLS-1$
    if (isSet(poster))
      placeholder.append(" data-poster=\"").append(escapeAttribute(poster)).append('"'); //$NON-NLS-1$
    placeholder.append("></div>"); //$NON-NLS-1$
    return placeholder.toString();
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

  private static boolean isVideoIframe(String src) {
    if (!isSet(src))
      return false;

    String value = src.toLowerCase(Locale.ENGLISH);
    return value.contains("youtube.com/embed/") || value.contains("youtube-nocookie.com/embed/") //$NON-NLS-1$ //$NON-NLS-2$
        || value.contains("player.vimeo.com/") || value.contains("player.youku.com/") //$NON-NLS-1$ //$NON-NLS-2$
        || value.contains("player.bilibili.com/") || value.contains("v.qq.com/iframe/") //$NON-NLS-1$ //$NON-NLS-2$
        || value.contains("v.qq.com/txp/iframe/") || value.contains("open.iqiyi.com/") //$NON-NLS-1$ //$NON-NLS-2$
        || value.contains("ixigua.com/iframe/") || value.contains("douyin.com/player/") //$NON-NLS-1$ //$NON-NLS-2$
        || value.contains("tv.sohu.com/upload/"); //$NON-NLS-1$
  }

  private static boolean isMp4(String src) {
    String value = src.toLowerCase(Locale.ENGLISH);
    int index = value.indexOf(".mp4"); //$NON-NLS-1$
    return index != -1 && (index + 4 == value.length() || !Character.isLetterOrDigit(value.charAt(index + 4)));
  }

  private static boolean isSet(String value) {
    return value != null && value.trim().length() > 0;
  }

  private static Node findVideoCandidate(Node node) {
    for (Object child : node.children) {
      if (child instanceof Node) {
        Node childNode = (Node) child;
        if (isVideoNode(childNode))
          return childNode;

        Node result = findVideoCandidate(childNode);
        if (result != null)
          return result;
      }
    }
    return null;
  }

  private static Node expandToVideo(Node best, Map<Node, Node> parents, int textLength) {
    if (containsVideo(best))
      return null;

    Node parent = parents.get(best);
    while (parent != null) {
      if (containsVideo(parent)) {
        int parentLength = textLength(parent);
        if (parentLength > 0 && parentLength <= textLength + 1500)
          return parent;
        return null;
      }
      parent = parents.get(parent);
    }

    return null;
  }

  private static boolean containsVideo(Node node) {
    if (isVideoNode(node))
      return true;

    for (Object child : node.children) {
      if (child instanceof Node && containsVideo((Node) child))
        return true;
    }
    return false;
  }

  private static boolean isVideoNode(Node node) {
    if ("video".equals(node.tag) || "video".equals(node.semanticTag)) //$NON-NLS-1$ //$NON-NLS-2$
      return true;

    String media = lower(node.getAttribute("data-rssowl-media")); //$NON-NLS-1$
    if ("video".equals(media) || "iframe".equals(media)) //$NON-NLS-1$ //$NON-NLS-2$
      return true;

    String fileId = node.getAttribute("file-id"); //$NON-NLS-1$
    return isSet(fileId) && lower(node.getAttribute("is")).contains("videoplayer"); //$NON-NLS-1$ //$NON-NLS-2$
  }

  private static boolean isTencentVideo(Node node) {
    String fileId = node.getAttribute("file-id"); //$NON-NLS-1$
    return isSet(fileId) && lower(node.getAttribute("is")).contains("videoplayer"); //$NON-NLS-1$ //$NON-NLS-2$
  }

  private static String normalizeHtml5Tags(String html) {
    StringBuffer normalized = new StringBuffer(html.length() + 1024);
    Matcher startMatcher = HTML5_START_TAG.matcher(html);
    while (startMatcher.find()) {
      String tag = startMatcher.group(1).toLowerCase(Locale.ENGLISH);
      String attributes = startMatcher.group(2) != null ? startMatcher.group(2) : ""; //$NON-NLS-1$
      startMatcher.appendReplacement(normalized, Matcher.quoteReplacement("<div data-rssowl-tag=\"" + tag + "\"" + attributes + ">")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }
    startMatcher.appendTail(normalized);

    StringBuffer result = new StringBuffer(normalized.length());
    Matcher endMatcher = HTML5_END_TAG.matcher(normalized);
    while (endMatcher.find())
      endMatcher.appendReplacement(result, Matcher.quoteReplacement("</div>")); //$NON-NLS-1$
    endMatcher.appendTail(result);

    return result.toString();
  }

  private static Node createNode(HTML.Tag tag, MutableAttributeSet attributes) {
    String name = tag.toString().toLowerCase(Locale.ENGLISH);
    Node node = new Node(name);

    Enumeration<?> attributeNames = attributes.getAttributeNames();
    while (attributeNames.hasMoreElements()) {
      Object key = attributeNames.nextElement();
      Object value = attributes.getAttribute(key);
      if (key != null && value != null) {
        String attributeName = key.toString().toLowerCase(Locale.ENGLISH);
        String attributeValue = value.toString();
        if ("data-rssowl-tag".equals(attributeName) && "div".equals(name)) //$NON-NLS-1$ //$NON-NLS-2$
          node.semanticTag = attributeValue.toLowerCase(Locale.ENGLISH);
        else
          node.attributes.put(attributeName, attributeValue);
      }
    }

    return node;
  }

  private static Node findBestCandidate(Node root, Map<Node, Node> parents) {
    List<Node> nodes = new ArrayList<Node>();
    collectNodes(root, nodes, parents);

    Node best = null;
    int bestScore = 0;
    int bestTextLength = 0;

    for (Node node : nodes) {
      int score = semanticScore(node);
      if (score == 0)
        continue;

      int textLength = textLength(node);
      if (textLength < MIN_ARTICLE_TEXT_LENGTH)
        continue;

      if (score > bestScore || (score == bestScore && textLength > bestTextLength)) {
        best = node;
        bestScore = score;
        bestTextLength = textLength;
      }
    }

    return best;
  }

  private static int semanticScore(Node node) {
    String tag = node.semanticTag != null ? node.semanticTag : node.tag;
    String cssClass = lower(node.getAttribute("class")); //$NON-NLS-1$
    String id = lower(node.getAttribute("id")); //$NON-NLS-1$
    String itemProp = lower(node.getAttribute("itemprop")); //$NON-NLS-1$
    String role = lower(node.getAttribute("role")); //$NON-NLS-1$

    if (itemProp.contains("articlebody")) //$NON-NLS-1$
      return 1000;

    if (cssClass.contains("post-body") && cssClass.contains("entry-content")) //$NON-NLS-1$ //$NON-NLS-2$
      return 950;

    if (cssClass.contains("entry-content") || cssClass.contains("post-content") || cssClass.contains("article-content") || cssClass.contains("article-body") || cssClass.contains("post-body")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
      return 900;

    if ("article".equals(tag)) //$NON-NLS-1$
      return 800;

    if ("main".equals(tag) || "main".equals(role)) //$NON-NLS-1$ //$NON-NLS-2$
      return 500;

    if (id.contains("article-body") || id.contains("entry-content") || id.contains("post-content")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
      return 850;

    return 0;
  }

  private static void collectNodes(Node node, List<Node> result, Map<Node, Node> parents) {
    for (Object child : node.children) {
      if (child instanceof Node) {
        Node childNode = (Node) child;
        result.add(childNode);
        parents.put(childNode, node);
        collectNodes(childNode, result, parents);
      }
    }
  }

  private static int textLength(Node node) {
    int length = 0;
    for (Object child : node.children) {
      if (child instanceof String) {
        String text = (String) child;
        for (int i = 0; i < text.length(); i++) {
          if (!Character.isWhitespace(text.charAt(i)))
            length++;
        }
      } else if (child instanceof Node) {
        Node childNode = (Node) child;
        if (!SKIP_TAGS.contains(childNode.tag) && (childNode.semanticTag == null || !SEMANTIC_SKIP_TAGS.contains(childNode.semanticTag)))
          length += textLength(childNode);
      }
    }
    return length;
  }

  private static void renderChildren(Node node, StringBuilder result, URI base) {
    for (Object child : node.children) {
      if (child instanceof String)
        appendText(result, (String) child);
      else if (child instanceof Node)
        renderNode((Node) child, result, base);
    }
  }

  private static void renderNode(Node node, StringBuilder result, URI base) {
    String tag = node.tag;

    if ("video".equals(tag) || "video".equals(node.semanticTag)) { //$NON-NLS-1$ //$NON-NLS-2$
      renderVideo(node, result, base);
      return;
    }

    if ("audio".equals(tag) || "audio".equals(node.semanticTag)) { //$NON-NLS-1$ //$NON-NLS-2$
      renderAudio(node, result, base);
      return;
    }

    if (node.semanticTag != null && SEMANTIC_SKIP_TAGS.contains(node.semanticTag)) {
      if (containsVideo(node))
        renderChildren(node, result, base);
      return;
    }

    if (SKIP_TAGS.contains(tag))
      return;

    if ("img".equals(tag)) { //$NON-NLS-1$
      renderImage(node, result, base);
      return;
    }

    if ("br".equals(tag)) { //$NON-NLS-1$
      result.append("<br/>"); //$NON-NLS-1$
      return;
    }

    if ("hr".equals(tag)) { //$NON-NLS-1$
      result.append("<hr/>"); //$NON-NLS-1$
      return;
    }

    if (INLINE_TAGS.contains(tag)) {
      renderChildren(node, result, base);
      return;
    }

    if ("section".equals(tag) || "div".equals(tag)) { //$NON-NLS-1$ //$NON-NLS-2$
      renderBlock(node, result, base);
      return;
    }

    if ("a".equals(tag)) { //$NON-NLS-1$
      if (!hasMeaningfulContent(node))
        return;

      String href = resolve(base, node.getAttribute("href")); //$NON-NLS-1$
      if (href != null) {
        result.append("<a href=\"").append(escapeAttribute(href)).append("\">"); //$NON-NLS-1$ //$NON-NLS-2$
        renderChildren(node, result, base);
        result.append("</a>"); //$NON-NLS-1$
      } else {
        renderChildren(node, result, base);
      }
      return;
    }

    if (KEEP_TAGS.contains(tag)) {
      if (!hasMeaningfulContent(node))
        return;

      result.append('<').append(tag);

      if (CELL_TAGS.contains(tag)) {
        appendAttribute(result, node, "colspan"); //$NON-NLS-1$
        appendAttribute(result, node, "rowspan"); //$NON-NLS-1$
      } else if ("ol".equals(tag)) { //$NON-NLS-1$
        appendAttribute(result, node, "start"); //$NON-NLS-1$
      }

      result.append('>');
      renderChildren(node, result, base);
      result.append("</").append(tag).append('>'); //$NON-NLS-1$
      return;
    }

    // Unknown element: keep its contents only
    renderChildren(node, result, base);
  }

  private static void renderBlock(Node node, StringBuilder result, URI base) {
    if (isTencentVideo(node)) {
      renderTencentVideo(node, result, base);
      return;
    }

    String media = lower(node.getAttribute("data-rssowl-media")); //$NON-NLS-1$
    if ("video".equals(media)) { //$NON-NLS-1$
      renderVideo(node, result, base);
      return;
    }
    if ("audio".equals(media)) { //$NON-NLS-1$
      renderAudio(node, result, base);
      return;
    }
    if ("iframe".equals(media)) { //$NON-NLS-1$
      renderIframe(node, result, base);
      return;
    }

    if (!hasMeaningfulContent(node))
      return;

    String cssClass = lower(node.getAttribute("class")); //$NON-NLS-1$

    if (cssClass.contains("separator") && containsImage(node)) { //$NON-NLS-1$
      result.append("<div class=\"separator\" style=\"text-align: center;\">"); //$NON-NLS-1$
      renderChildren(node, result, base);
      result.append("</div>"); //$NON-NLS-1$
      return;
    }

    boolean hasBlockChild = hasBlockChild(node);
    if (hasBlockChild && textLength(node) == 0) {
      renderChildren(node, result, base);
      return;
    }

    String container = hasBlockChild ? "div" : "p"; //$NON-NLS-1$ //$NON-NLS-2$
    result.append('<').append(container).append('>');
    renderChildren(node, result, base);
    result.append("</").append(container).append('>'); //$NON-NLS-1$
  }

  private static void renderImage(Node node, StringBuilder result, URI base) {
    String src = node.getAttribute("src"); //$NON-NLS-1$
    if (src == null || src.trim().length() == 0) {
      src = node.getAttribute("data-src"); //$NON-NLS-1$
      if (src == null)
        src = node.getAttribute("data-original"); //$NON-NLS-1$
    }

    src = resolve(base, src);
    if (src == null || src.startsWith("data:image/svg")) //$NON-NLS-1$
      return;

    src = upgradeBloggerImage(src);

    result.append("<img src=\"").append(escapeAttribute(src)).append('"'); //$NON-NLS-1$
    String alt = node.getAttribute("alt"); //$NON-NLS-1$
    if (alt != null)
      result.append(" alt=\"").append(escapeAttribute(alt)).append('"'); //$NON-NLS-1$
    result.append(" />"); //$NON-NLS-1$
  }

  private static String upgradeBloggerImage(String src) {
    String lowerSrc = src.toLowerCase(Locale.ENGLISH);
    if (!lowerSrc.contains("blogger.googleusercontent.com") && !lowerSrc.contains(".bp.blogspot.com")) //$NON-NLS-1$ //$NON-NLS-2$
      return src;

    if (lowerSrc.startsWith("http://")) //$NON-NLS-1$
      src = "https://" + src.substring(7); //$NON-NLS-1$

    Matcher matcher = BLOGGER_IMAGE_SIZE_PATTERN.matcher(src);
    if (matcher.find())
      return src.substring(0, matcher.start()) + matcher.group(1) + "s1600/" + src.substring(matcher.end()); //$NON-NLS-1$

    return src;
  }

  private static void renderVideo(Node node, StringBuilder result, URI base) {
    String src = node.getAttribute("data-src"); //$NON-NLS-1$
    String type = node.getAttribute("data-type"); //$NON-NLS-1$
    String poster = node.getAttribute("data-poster"); //$NON-NLS-1$

    if (!isSet(src)) {
      src = node.getAttribute("src"); //$NON-NLS-1$
      type = node.getAttribute("type"); //$NON-NLS-1$
      poster = node.getAttribute("poster"); //$NON-NLS-1$
    }

    if (!isSet(src)) {
      Node source = findSource(node);
      if (source != null) {
        src = source.getAttribute("src"); //$NON-NLS-1$
        type = source.getAttribute("type"); //$NON-NLS-1$
      }
    }

    src = resolve(base, src);
    if (src == null)
      return;

    poster = resolve(base, poster);
    result.append("<div class=\"rssowl-video\"><video controls=\"controls\" playsinline=\"playsinline\" preload=\"metadata\""); //$NON-NLS-1$
    if (isSet(poster))
      result.append(" poster=\"").append(escapeAttribute(poster)).append('"'); //$NON-NLS-1$
    result.append(" style=\"max-width:100%;max-height:80vh;display:block;margin:1em auto;\"><source src=\"").append(escapeAttribute(src)).append('"'); //$NON-NLS-1$
    if (isSet(type))
      result.append(" type=\"").append(escapeAttribute(type)).append('"'); //$NON-NLS-1$
    result.append("/></video></div>"); //$NON-NLS-1$
  }

  private static void renderAudio(Node node, StringBuilder result, URI base) {
    String src = node.getAttribute("data-src"); //$NON-NLS-1$
    String type = node.getAttribute("data-type"); //$NON-NLS-1$
    if (!isSet(src)) {
      src = node.getAttribute("src"); //$NON-NLS-1$
      type = node.getAttribute("type"); //$NON-NLS-1$
    }
    if (!isSet(src)) {
      Node source = findSource(node);
      if (source != null) {
        src = source.getAttribute("src"); //$NON-NLS-1$
        type = source.getAttribute("type"); //$NON-NLS-1$
      }
    }
    src = resolve(base, src);
    if (src == null)
      return;

    result.append("<div class=\"rssowl-video\"><audio controls=\"controls\" preload=\"metadata\" style=\"width:100%;\"><source src=\"").append(escapeAttribute(src)).append('"'); //$NON-NLS-1$
    if (isSet(type))
      result.append(" type=\"").append(escapeAttribute(type)).append('"'); //$NON-NLS-1$
    result.append("/></audio></div>"); //$NON-NLS-1$
  }

  private static void renderIframe(Node node, StringBuilder result, URI base) {
    String src = resolve(base, node.getAttribute("data-src")); //$NON-NLS-1$
    if (src == null)
      return;

    result.append("<div class=\"rssowl-video\"><iframe src=\"").append(escapeAttribute(src)).append("\" allowfullscreen=\"true\" frameborder=\"0\" scrolling=\"no\" style=\"width:100%;height:420px;border:0;\"></iframe></div>"); //$NON-NLS-1$ //$NON-NLS-2$
  }

  private static void renderTencentVideo(Node node, StringBuilder result, URI base) {
    String fileId = node.getAttribute("file-id"); //$NON-NLS-1$
    if (!isSet(fileId))
      return;

    String appId = node.getAttribute("app-id"); //$NON-NLS-1$
    if (!isSet(appId))
      appId = node.getAttribute("appid"); //$NON-NLS-1$
    if (!isSet(appId))
      appId = TENCENT_VIDEO_APP_ID;

    String poster = resolve(base, node.getAttribute("cover-url")); //$NON-NLS-1$
    result.append("<div class=\"rssowl-video\" data-rssowl-video=\"tencent\" data-app-id=\"").append(escapeAttribute(appId)).append("\" data-file-id=\"").append(escapeAttribute(fileId)).append('"'); //$NON-NLS-1$ //$NON-NLS-2$
    if (isSet(poster))
      result.append(" data-poster=\"").append(escapeAttribute(poster)).append('"'); //$NON-NLS-1$
    result.append("></div>"); //$NON-NLS-1$
  }

  private static Node findSource(Node node) {
    for (Object child : node.children) {
      if (child instanceof Node) {
        Node childNode = (Node) child;
        if ("source".equals(childNode.tag) && isSet(childNode.getAttribute("src"))) //$NON-NLS-1$ //$NON-NLS-2$
          return childNode;
      }
    }
    return null;
  }

  private static void appendAttribute(StringBuilder result, Node node, String name) {
    String value = node.getAttribute(name);
    if (value != null && value.trim().length() > 0)
      result.append(' ').append(name).append("=\"").append(escapeAttribute(value)).append('"'); //$NON-NLS-1$
  }

  private static boolean hasBlockChild(Node node) {
    for (Object child : node.children) {
      if (child instanceof Node) {
        Node childNode = (Node) child;
        if (BLOCK_TAGS.contains(childNode.tag))
          return true;
        if (hasBlockChild(childNode))
          return true;
      }
    }
    return false;
  }

  private static boolean containsImage(Node node) {
    if ("img".equals(node.tag)) //$NON-NLS-1$
      return true;

    for (Object child : node.children) {
      if (child instanceof Node && containsImage((Node) child))
        return true;
    }
    return false;
  }

  private static boolean hasMeaningfulContent(Node node) {
    if (textLength(node) > 0)
      return true;

    for (Object child : node.children) {
      if (child instanceof Node) {
        Node childNode = (Node) child;
        if (childNode.semanticTag != null && SEMANTIC_SKIP_TAGS.contains(childNode.semanticTag))
          continue;

        String tag = childNode.tag;
        if ("img".equals(tag) || "hr".equals(tag) || "table".equals(tag) || "pre".equals(tag) || "ul".equals(tag) || "ol".equals(tag) || "blockquote".equals(tag) || "figure".equals(tag) || isVideoNode(childNode)) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
          return true;
        if (hasMeaningfulContent(childNode))
          return true;
      }
    }

    return false;
  }

  private static String resolve(URI base, String link) {
    if (link == null)
      return null;

    link = link.trim();
    if (link.length() == 0)
      return null;

    String lowerLink = link.toLowerCase(Locale.ENGLISH);
    if (lowerLink.startsWith("javascript:") || lowerLink.startsWith("vbscript:")) //$NON-NLS-1$ //$NON-NLS-2$
      return null;

    if (lowerLink.startsWith("data:")) //$NON-NLS-1$
      return link;

    if (base == null)
      return link;

    try {
      URI resolved = base.resolve(new URI(link.replace(" ", "%20"))); //$NON-NLS-1$ //$NON-NLS-2$
      return resolved.toString();
    } catch (URISyntaxException e) {
      return link;
    }
  }

  private static void appendText(StringBuilder result, String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
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
        default:
          result.append(c);
      }
    }
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

  private static String lower(String value) {
    return value != null ? value.toLowerCase(Locale.ENGLISH) : ""; //$NON-NLS-1$
  }

  private static Set<String> set(String values) {
    Set<String> result = new HashSet<String>();
    for (String value : values.split(" ")) //$NON-NLS-1$
      result.add(value);
    return result;
  }
}
