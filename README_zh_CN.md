# RSSOwlnix 3.0.2

[English](README.md) | 简体中文

RSSOwlnix 是 RSSOwl 的 fork，一个基于 Eclipse RCP 构建的 RSS/Atom 阅读器。

## 3.0.2 更新内容

### 快捷键持久化

- 修复应用非正常退出（强制结束进程、Windows 注销或重启、崩溃、断电）后自定义快捷键恢复默认值的问题。
- 在 **设置 > 按键** 中修改快捷键并应用后立即写入磁盘，不再等待首选项对话框点击“应用并关闭”或工作台正常关闭。

### 打包

- 项目和 bundle 版本升级到 **3.0.2**。
- 重新构建 Windows x86_64 安装程序：`RSSOwlnix-3.0.2-win32.win32.x86_64-Setup.exe`。

## 3.0.1 更新内容

### 多语言支持

- 新增 **设置 > 语言**，可选择界面语言（跟随系统、English 或自动发现的外置语言包）、打开语言包目录，以及导出内置翻译。
- 内置英文和简体中文翻译，覆盖 31 个消息资源包，以及 RSSOwlnix 提供的菜单、视图、透视图和偏好设置页面标签。
- 语言包是外置的 UTF-8 properties 文件，可使用任意文本编辑器修改，重启后生效，无需重新构建程序。
- 语言包使用 `*.messages.properties` 保存消息文本，使用 `*.plugin.properties` 保存菜单、视图、透视图和偏好设置页面标签；用户语言包会覆盖内置翻译。
- 新增菜单、视图、透视图和偏好设置页面标签的早期本地化，外置语言包可在下次启动时覆盖 `plugin.xml` 中的标签。
- 新增 **导出语言包**，可写出可直接编辑的 UTF-8 文件（中文文本已解码），作为创建新语言的起点。
- 可通过 `-Drssowlnix.languagePacks=/path/one:/path/two` 添加语言包目录（Windows 使用 `;` 分隔）。

### 语言包

语言包位于程序 jar 之外，可使用任意文本编辑器修改：

- 用户语言包：`~/.rssowlnix-beta/language-packs/<locale>/`
- 随程序附带：`<installation>/language-packs/<locale>/`
- 可通过 `-Drssowlnix.languagePacks=/path/one:/path/two` 添加语言包目录（Windows 使用 `;` 分隔）。

所有文件都是标准 Java `properties` 文件，保存为 UTF-8。键名不要修改，只翻译 `=` 后面的文本。例如：

```properties
# ~/.rssowlnix-beta/language-packs/fr/org.rssowl.ui.internal.dialogs.messages.properties
MarkAllNewsReadDialog_ALL_NEWS=Toutes les actualités
MarkAllNewsReadDialog_INFO=Les actualités non lues dans la période sélectionnée restent non lues. Les actualités plus anciennes seront marquées comme lues.
```

菜单、视图、透视图和偏好设置页面标签使用以 bundle 命名的文件，例如 `org.rssowl.ui.plugin.properties` 和 `org.rssowl.core.plugin.properties`。使用 **设置 > 语言 > 导出语言包** 复制内置英文或中文文件作为起点，然后将文件夹重命名为目标语言代码（例如 `fr` 或 `pt_BR`）。导出的文件以 UTF-8 保存且文本可读，因此可以直接编辑内置中文翻译。用户语言包优先于随程序安装的语言包，用户目录中的文件优先于内置翻译。

语言选择写入 `~/.rssowlnix-beta/config/config.ini` 中的 `osgi.nl`，下次启动时读取。Eclipse 平台自身提供的标签（例如标准的“文件”和“编辑”菜单）取决于产品中安装的 Eclipse 语言包；RSSOwlnix 语言包只覆盖 RSSOwlnix 自己的界面。

### 打包

- 项目和 bundle 版本升级到 **3.0.1**。
- 重新构建 Windows x86_64 安装程序：`RSSOwlnix-3.0.1-win32.win32.x86_64-Setup.exe`。

## 3.0.0 更新内容

### 远程同步账号

- 新增 Fever API 支持：
  - 新增 **Fever 账号** 向导，包含服务器地址、用户名、Fever API 密钥、账号名称和目标文件夹。
  - 从 Fever 服务器获取频道、分组和文章，包括分页获取文章。
  - 将已读、未读、收藏和取消收藏状态同步回服务器。
  - 缓存账号数据，并在写回后使缓存失效，使更改立即显示。
- 新增 Google Reader API 支持：
  - 新增 **Google Reader 账号** 向导，包含服务器地址、用户名和 API 密码。
  - 使用 ClientLogin 认证并获取账号的订阅。
  - 处理 continuation 续页并批量获取流内容。
  - 通过 edit-tags API 同步已读、未读、星标和标签状态。
  - 获取写操作所需的 action token。
- 在同步频道和文件夹的上下文菜单中新增 **编辑同步账号**。无需删除并重新创建账号即可修改服务器地址、用户名、密码/API 密钥、账号名称和位置。
- Fever API 密钥和 Google Reader 认证 token 存储在平台凭据存储（Equinox Secure Storage）中，绝不会写入频道 URL 或明文数据库。密码仅用于获取 token，不会被持久化。
- 新增适用于 Linux、macOS 和 Windows x86_64 的原生安全存储 fragment。这修复了“Unable to locate secure storage module”错误，并让保存的凭据在所有支持的平台上可用。
- 同步条目现在以频道链接加条目 ID 为键，因此不同账号中相同的文章 ID 不会再互相覆盖。仅含 ID 的旧条目会自动迁移。
- 同步请求按服务和端点分区。认证表单 POST 请求现在使用 UTF-8。
- 注册了 `fever://`、`fevers://`、`greader://` 和 `greaders://` URL 协议；带 `s` 的变体保持 HTTPS 连接。
- Fever 返回的已读/未读标记会在频道合并时应用，与现有 Google Reader 行为一致。

### 文章阅读

- 当文章没有内容，或内容只有日期/时间（例如只有视频的 Infzm 页面）时，自动获取全文。
- 新增本地文章提取（`ArticleExtractor`），当在线全文服务只返回摘要时作为后备。提取的文章使用舒适的阅读排版，包含正确的段落、标题、图片和引用样式。
- 新增嵌入视频提取与播放（`VideoResolver`），适用于视频页面，包括 Infzm 文章中嵌入的腾讯视频。
- 新增本地图片代理，用于防盗链图片。当图片无法直接加载时（例如 365geo 上的阿里云 OSS 图片），文章页面会以文章 URL 作为 Referer 通过本地代理重试。Google/Blogger 图片会先尝试 weserv 图片服务。代理以 32 MB 为上限流式传输图片，并缓存成功响应 24 小时。
- 文章内容中的图片限制在可用宽度内，并保持宽高比，包括全文提取返回的图片。
- 移除频道内容中的内联 `font-size` 声明，使 RSSOwl 的文字大小设置和缩放功能在 365geo、Hecaitou 等网站上生效。
- 文章标题和标签工具栏保持固定，滚动文章时始终可见。
- 新增从嵌入文章浏览器到工作台快捷键绑定的转发，阅读时配置的快捷键也能使用。
- 新增 Windows Edge（Chromium/WebView2）浏览器支持，并自动回退到 XULRunner 和默认浏览器。可通过 `noEdge` 系统属性禁用 Edge。
- 改进 Hecaitou 等频道包含大量内联样式和外部图片的网站的文章排版。

### 用户界面

- **全部标为已读** 现在会打开时间范围对话框。可选择保留最近多少新闻为未读：最近 1 天、最近 3 天、最近 1 周、最近 1 年或自定义天数。超出所选范围的未读新闻会被标为已读，范围内的新闻保持未读；**全部新闻** 会将所有新闻标为已读。对话框会显示将要标记的数量，并在没有更早的新闻时给出提示，而不是静默地不执行任何操作。
- 修复 **自定义工具栏** 导致工具栏显示不完整或为空的问题。现在每个工具栏分组都有唯一 ID，配置变更后会刷新工具栏。
- 修复自定义工具栏问题后，恢复主菜单和工具栏。
- 在“文件 > 新建”菜单和新建工具栏下拉菜单中新增 **Fever 账号** 和 **Google Reader 账号** 条目。
- 更新 3.0.0 的产品“关于”文本、启动画面版本和构建 ID。

### 打包与平台

- 项目和 bundle 版本升级到 **3.0.0**。
- 新增可重复构建的 Windows x86_64 NSIS 安装脚本。
- Windows 安装程序包含新的安全存储 fragment，并生成 `RSSOwlnix-3.0.0-win32.win32.x86_64-Setup.exe`。
- 使用 Eclipse RCP 4.30 构建，目标 Java 17/21。

## 项目链接

发布版本：https://github.com/Xyrio/RSSOwlnix/releases

更新源：https://xyrio.github.io/RSSOwlnix-site/updates.rss

更新日志：https://github.com/Xyrio/RSSOwlnix/blob/master/CHANGELOG.md

问题反馈：https://github.com/Xyrio/RSSOwlnix/issues

其他讨论：https://github.com/Xyrio/RSSOwlnix/discussions

Wiki：https://github.com/Xyrio/RSSOwlnix/wiki

如何构建：https://github.com/Xyrio/RSSOwlnix/wiki/How-To-Build

插件仓库：https://github.com/Xyrio/RSSOwlnix-plugins-addons

翻译仓库：https://github.com/Xyrio/RSSOwlnix-plugins-translation

**其他分发渠道（本人不负责）：**

通过 Chocolatey 安装：https://chocolatey.org/packages/rssowlnix \
Chocolatey 包仓库：https://github.com/eargollo/choco-rssowlnix \
Chocolatey 包问题：https://github.com/Xyrio/RSSOwlnix/issues/28
