updates feed: https://xyrio.github.io/RSSOwlnix-site/updates.rss

# 3.0.1
- added multi-language support with a Language preference page, built-in English and Simplified Chinese translations, and external UTF-8 language packs under `language-packs/<locale>/` that override messages and plugin.xml labels without rebuilding
- added early localization of menu, view and preference labels so external packs apply on the next start, and export language packs as directly editable UTF-8 files
- added export of the bundled translations as editable language packs and support for additional pack roots through `-Drssowlnix.languagePacks`
- rebuilt the Windows x86_64 installer as RSSOwlnix-3.0.1-win32.win32.x86_64-Setup.exe
- upgraded the version to 3.0.1

# 3.0.0
- added Fever API account support (feeds, groups, articles, read/unread and saved states)
- added Google Reader API account support (subscriptions, paged streams, read/unread, starred and label sync)
- added editing of existing synchronization accounts, including server and credentials
- stored Fever API keys and Google Reader auth tokens in Equinox Secure Storage; added native secure-storage fragments for Linux, macOS and Windows
- made synchronization items account-aware so identical item IDs from different accounts no longer collide
- added automatic full-content retrieval for empty and date/time-only articles
- added local article extraction as a fallback when the online full-content service returns a teaser
- added embedded video extraction and playback for pages such as Infzm/Tencent video articles
- added a local Referer-aware image proxy for hotlink-protected images and image fallbacks for Google/Blogger images
- constrained article images to the page width and improved extracted-article typography
- removed inline font sizes so the user's text size setting works for 365geo, Hecaitou and similar feeds
- made the article title and tag toolbar sticky while scrolling
- forwarded configured keyboard shortcuts from the embedded article browser to the workbench
- added Windows Edge (Chromium/WebView2) support with fallback to XULRunner and IE
- added time-range selection to Mark All Read (keep the last day, 3 days, week, year or custom days unread and mark older news read), with a count of the news that will be marked and a warning when none are older
- fixed Mark All Read time ranges to use the same date as the news table and to no longer silently do nothing when no news match
- fixed Customize Toolbar leaving the toolbar incomplete or empty and restored the main menu and toolbar
- added a reproducible NSIS installer script for the Windows x86_64 product
- updated crash report email
- upgraded the version to 3.0.0

# 2.10.0-beta
- runs with java 17,21 [#116](https://github.com/Xyrio/RSSOwlnix/issues/116)
- updated to eclipse rcp 4.30
- updated httpclient to 5.5
- removed auto reload of feeds without any data and error on viewing feed or the folder(s) feed is in [#118](https://github.com/Xyrio/RSSOwlnix/issues/118)
- added RSSOWLNIX_USER_AGENT system property to change the User-Agent used when requesting feeds to match demands of websites for popular browsers [#164](https://github.com/Xyrio/RSSOwlnix/issues/164)
- changed name reverse sorting to reverse only word positions so that numbers are sorted as usual
- change dead feedvalidator.org to validator.w3.org [#124](https://github.com/Xyrio/RSSOwlnix/issues/124)
- fix BookMark.setLastRecentNewsDate(Date) not allowing null [#178](https://github.com/Xyrio/RSSOwlnix/issues/178)

# 2.9.0-beta
- runs also with java 15, **does not run with java 16 [#116](https://github.com/Xyrio/RSSOwlnix/issues/116)** 
- added more internal information to: Feed / Properties / Status
- added group feeds by: Latest Date (Modified,Published,Received), Last Update date of feed
- added JSON Feed 1.1 support as described by [jsonfeed.org](https://jsonfeed.org) [example](https://jsonfeed.org/feed.json) [#68](https://github.com/Xyrio/RSSOwlnix/issues/68) [PR#98](https://github.com/Xyrio/RSSOwlnix/pull/98)
- added feeds:// for https:// (also feed:https:// is still supported)
- improvement to keep http or https accordingly when reconstructing urls (favico,etc)
- support more media tags from youtube, peertube [#74](https://github.com/Xyrio/RSSOwlnix/issues/74)
- fixed missing description when <content:encode> exists but has no text to not overwrite what was already loaded from <description> [#56](https://github.com/Xyrio/RSSOwlnix/issues/56)
- updated share links: removed Mister Wong, Google+ and added BibSonomy [#95](https://github.com/Xyrio/RSSOwlnix/issues/95)
- fixed progress button at bottom right to show new Download & Activities view (eclipse ProgressView) toggleable from Menu/View/Downloads & Activity. Kept old view at Menu/Tools/Downloads & Activity for now. [#45](https://github.com/Xyrio/RSSOwlnix/issues/45)
- changed page size preference for newspaper/headlines view to be setable to any value [#91](https://github.com/Xyrio/RSSOwlnix/issues/91)
- fixed missing Overview/Network Connections in Preferrences [#67](https://github.com/Xyrio/RSSOwlnix/issues/67)

# 2.8.0-beta
- improved favico search
- updated httpclient to 4.5.12
- added shell:// protocol for feed links to retrieve or transform rss using external scripts or programs. example shell://python html2rss.py https://website/

# 2.7.1-beta
- runs also with java 13, 14
- fixed null news in feed when doing cleanup [#64](https://github.com/Xyrio/RSSOwlnix/issues/64)
- fixed different behaviour when adding new feed from toolbar [#42](https://github.com/Xyrio/RSSOwlnix/issues/42)
- updated rssowl news to use RSSOwlnix's update.rss [PR#59](https://github.com/Xyrio/RSSOwlnix/pull/59)

# 2.7.0-beta
- runs also with java 12
- updated eclipse rcp to 4.9.1 (last rcp supporting 32bit) (no babel localization for 4.9+)
- Added Telegram to options for sharing links [#33](https://github.com/Xyrio/RSSOwlnix/issues/33) [PR#38](https://github.com/Xyrio/RSSOwlnix/pull/38)

# 2.6.1-beta
- fixed wrong sticky news counting when doing a cleanup [#22](https://github.com/Xyrio/RSSOwlnix/issues/22)

# 2.6.0-beta
- fixed some website authentication and authentication secure storage related bugs since updating httpclient to 4.x
- added support for feed links using https like `feed:https://host/rss.xml` ( https://en.wikipedia.org/wiki/Feed_URI_scheme )
- added right to left sorting for search dialog title column too

# 2.5.5-beta
- fixed type check for preferences

# 2.5.4-beta
- updated httpclient to 4.5.6

# 2.5.3-beta
- updated eclipse rcp to 4.7.3a

# 2.5.2-beta
- fixed missing 256x256 program logo icon [#14](https://github.com/Xyrio/RSSOwlnix/issues/14)

# 2.5.1-beta
- added right to left sorting for title column of classic view (appends "<-" to Title column)
- linux: do not force xulrunner [PR#21](https://github.com/Xyrio/RSSOwlnix/pull/21)

# 2.5.0-beta
- main program is now updateable (addons and translation available through Help/Install new Software...) * does not work correctly
- renamed to RSSOwlnix
- updated httpclient to 4.5.5
- updated eclipse rcp to 4.7.2

# 2.4.0-beta
- removed old update manager and added new p2 one (addons work so far)

# 2.3.0-beta
- runs also with java 9
- https websites which needed JCE should work without it when jre 9+ is used. https://sourceforge.net/p/rssowl/discussion/296910/thread/6dc4a203/
- updated eclipse rcp to 4.7
- updated httpclient to 4.5.3
- fixed 2 (maybe) memory leaks

based on RSSOwl 2.2.1
