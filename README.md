# RSSOwlnix 3.0.1

[简体中文](README_zh_CN.md)

RSSOwlnix is a fork of RSSOwl, an RSS/Atom feed reader built on Eclipse RCP.

## What's new in 3.0.1

### Multi-language support

- Added **Preferences > Language** to select the interface language (system default, English, or any discovered external language pack), open the language pack folder, and export the bundled translations.
- Added built-in English and Simplified Chinese translations covering 31 message bundles plus the menu, view, perspective and preference-page labels contributed by RSSOwlnix.
- Language packs are external UTF-8 properties files, so translations can be edited with any text editor and take effect after a restart without rebuilding the application.
- Packs use `*.messages.properties` for message texts and `*.plugin.properties` for menu, view, perspective and preference-page labels; user packs override built-in translations.
- Added early localization of menu, view, perspective and preference-page labels, so external packs override `plugin.xml` labels on the next start.
- Added **Export Language Pack**, which writes directly editable UTF-8 files, including decoded Chinese text, as a starting point for new languages.
- Additional language pack roots can be supplied with `-Drssowlnix.languagePacks=/path/one:/path/two` (`;` on Windows).

### Language packs

Language packs live outside the application jars so they can be edited with any text editor:

- User packs: `~/.rssowlnix-beta/language-packs/<locale>/`
- Packs shipped with the application: `<installation>/language-packs/<locale>/`
- Additional pack roots can be supplied with `-Drssowlnix.languagePacks=/path/one:/path/two` (use `;` on Windows).

Every file is a standard Java `properties` file saved as UTF-8. Keys must stay unchanged; translate the text after `=`. For example:

```properties
# ~/.rssowlnix-beta/language-packs/fr/org.rssowl.ui.internal.dialogs.messages.properties
MarkAllNewsReadDialog_ALL_NEWS=Toutes les actualités
MarkAllNewsReadDialog_INFO=Les actualités non lues dans la période sélectionnée restent non lues. Les actualités plus anciennes seront marquées comme lues.
```

Menu, view, perspective and preference-page labels use files named after the bundle, such as `org.rssowl.ui.plugin.properties` and `org.rssowl.core.plugin.properties`. Use **Preferences > Language > Export Language Pack** to copy the bundled English or Chinese files as a starting point, then rename the folder to the desired locale (for example `fr` or `pt_BR`). The exported files are written as UTF-8 with readable text, so the built-in Chinese translations can be edited directly. User packs take precedence over packs shipped with the application, and the files in the user folder take precedence over built-in translations.

The language selection is written to `osgi.nl` in `~/.rssowlnix-beta/config/config.ini` and is read on the next start. Labels contributed by the Eclipse platform itself (for example the standard File and Edit menus) follow the Eclipse language fragments installed in the product; the language pack covers RSSOwlnix's own interface.

### Packaging

- Project and bundle versions upgraded to **3.0.1**.
- Rebuilt the Windows x86_64 installer as `RSSOwlnix-3.0.1-win32.win32.x86_64-Setup.exe`.

## What's new in 3.0.0

### Remote synchronization accounts

- Added Fever API support:
  - New **Fever Account** wizard with server URL, username, Fever API key, account name and target folder.
  - Retrieves feeds, groups and articles from Fever servers, including paginated article retrieval.
  - Synchronizes read, unread, saved and unsaved states back to the server.
  - Caches account data and invalidates the cache after write-back so changes show up immediately.
- Added Google Reader API support:
  - New **Google Reader Account** wizard with server URL, username and API password.
  - Authenticates with ClientLogin and retrieves the account's subscriptions.
  - Retrieves stream contents with continuation handling and batched item requests.
  - Synchronizes read, unread, starred and labeled states through the edit-tags API.
  - Fetches the action token required for write operations.
- Added **Edit Synchronization Account** to the context menu of synchronized feeds and folders. The server URL, username, password/API key, account name and location can be changed without deleting and recreating the account.
- Fever API keys and Google Reader authentication tokens are stored in the platform credential store (Equinox Secure Storage), never in feed URLs or the plain database. Passwords are only used to obtain a token and are not persisted.
- Added native secure-storage fragments for Linux, macOS and Windows x86_64. This fixes the "Unable to locate secure storage module" error and makes saved credentials work on all supported platforms.
- Synchronization items are now keyed by feed link plus item ID, so identical article IDs from different accounts no longer overwrite each other. Existing ID-only entries are migrated automatically.
- Sync requests are partitioned by service and endpoint. Authentication form posts now use UTF-8.
- Registered the `fever://`, `fevers://`, `greader://` and `greaders://` URL schemes; the `*s` variants keep HTTPS connections.
- Read/unread flags returned by Fever are applied during feed merging, matching the existing Google Reader behavior.

### Article reading

- Added automatic full-content retrieval when an article has no content or its content only contains a date/time, such as video-only Infzm pages.
- Added local article extraction (`ArticleExtractor`) as a fallback when the online full-content service only returns a teaser. Extracted articles use a comfortable reading layout with proper paragraph, heading, image and blockquote styling.
- Added embedded video extraction and playback (`VideoResolver`) for video pages, including Tencent videos embedded in Infzm articles.
- Added a local image proxy for hotlink-protected images. When an image cannot be loaded directly (for example Aliyun OSS images on 365geo), the article page retries it through the local proxy with the article URL as the Referer. Google/Blogger images first try the weserv image service. The proxy streams images with a 32 MB limit and caches successful responses for 24 hours.
- Images in article content are limited to the available width and keep their aspect ratio, including images returned by full-content extraction.
- Removed inline `font-size` declarations from feed content so the RSSOwl text-size setting and Zoom In/Out work on sites such as 365geo and Hecaitou.
- Made the article title and tag toolbar sticky so they stay visible while the article scrolls.
- Added forwarding of keyboard shortcuts from the embedded article browser to the workbench key bindings, so configured shortcuts work while reading.
- Added Windows Edge (Chromium/WebView2) browser support with automatic fallback to XULRunner and the default browser. Edge can be disabled with the `noEdge` system property.
- Improved article layout for Hecaitou and other sites whose feeds contain large inline styles and external images.

### User interface

- **Mark All Read** now opens a time-range dialog. Choose how much recent news to keep unread: the last day, the last 3 days, the last week, the last year, or a custom number of days. Unread news older than the selected range are marked read, while news inside the range stay unread; **All News** marks everything read. The dialog shows how many news will be marked and warns instead of silently doing nothing when none are older.
- Fixed **Customize Toolbar** leaving the toolbar incomplete or empty. Each toolbar group now receives a unique ID, and the toolbar is refreshed after configuration changes.
- Restored the main menu and toolbar after the Customize Toolbar fix.
- Added **Fever Account** and **Google Reader Account** entries to the File > New menu and the New toolbar dropdown.
- Updated the product About text, splash version and build ID for 3.0.0.

### Packaging and platform

- Project and bundle versions upgraded to **3.0.0**.
- Added a reproducible NSIS installer script for the Windows x86_64 product.
- Windows installer includes the new secure-storage fragment and produces `RSSOwlnix-3.0.0-win32.win32.x86_64-Setup.exe`.
- Built with Eclipse RCP 4.30 and targeting Java 17/21.

## Project links

releases: https://github.com/Xyrio/RSSOwlnix/releases

updates feed: https://xyrio.github.io/RSSOwlnix-site/updates.rss

changelog: https://github.com/Xyrio/RSSOwlnix/blob/master/CHANGELOG.md

bug reports: https://github.com/Xyrio/RSSOwlnix/issues

other discussions: https://github.com/Xyrio/RSSOwlnix/discussions

wiki: https://github.com/Xyrio/RSSOwlnix/wiki

how to build: https://github.com/Xyrio/RSSOwlnix/wiki/How-To-Build

addons repository: https://github.com/Xyrio/RSSOwlnix-plugins-addons

translations repository: https://github.com/Xyrio/RSSOwlnix-plugins-translation

**other distributions i am not responsible for:**

install through chocolatey: https://chocolatey.org/packages/rssowlnix \
coocolatey package repository: https://github.com/eargollo/choco-rssowlnix \
chocolatey package issue: https://github.com/Xyrio/RSSOwlnix/issues/28
