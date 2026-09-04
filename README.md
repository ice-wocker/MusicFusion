<p align="center">
  <img src="https://img.shields.io/badge/license-MIT-blue">
  <img src="https://img.shields.io/badge/Android-21%2B-green">
  <img src="https://img.shields.io/badge/Java-8-orange">
  <img src="https://img.shields.io/badge/tracks-9M%2B-brightgreen">
  <img src="https://img.shields.io/badge/radio-2945-blue">
  <img src="https://img.shields.io/badge/sources-4-yellow">
  <img src="https://img.shields.io/github/stars/ice-wocker/MusicFusion?style=social">
</p>


一站式聚合音乐播放器 — 36 源文件 / 9000+ 行 / <250KB / 纯 Java / MIT / 全部合法音源

> v12 基础上极全面升级：离线下载 · 10段均衡器 · 播客引擎 · Last.fm Scrobble · MusicBrainz 元数据补全 · 统计图表 · 自定义主题 · 搜索筛选 · 多尺寸小组件 · BigPicture 通知 · Android Auto · 快捷设置磁贴 · App Shortcuts

## ✨ v13 升级清单 (13 大新功能)

### 🛡️ 核心新增 (P0)
1. **DownloadManager.java** — 离线下载管理器 (MediaStore API 29+, 分区存储兼容, 支持暂停/恢复/队列)
2. **GraphicEqualizer.java** — 10段图形均衡器 UI + 自定义曲线保存/预设切换
3. **PodcastEngine.java** — RSS/OPML 解析, 播客订阅/播放/更新/自动刷新
4. **LastFmScrobbler.java** — Last.fm Scrobble (可选开启, 无Key即用, 官方授权流程)
5. **MusicBrainz.java** — 元数据补全 (MusicBrainz/OpenMusicBrainz API, 标题/艺术家/专辑/年份/流派)

### 🎨 UI/体验升级 (P1)
6. **ThemeManager.java** — 用户自定义主题 (AMOLED/高对比/护眼/浅色/自定义色板导入导出)
7. **StatsEngine.java** — 听歌统计图表 (周/月/年趋势, 流派饼图, 时段热力图, 艺术家排行)
8. **SearchFilters.java** — 搜索筛选面板 (源/画质/时长/年份/流派 多维筛选)
9. **Widget2x.java** — 2x1/4x1/4x2 小组件 (专辑图+进度+歌词行+播放控制)
10. **NotificationBig.java** — BigPictureStyle 通知 (封面大图+歌词滚动+进度条+4动作)

### 📱 系统集成 (P2)
11. **AutoManifest.java** — Android Auto 最小清单声明 (MediaBrowserService 接口预留)
12. **QuickTile.java** — 系统快捷设置磁贴 (播/暂/上/下循环切换, 显示当前曲目)
13. **Shortcuts.java** — App Shortcuts (长按图标: 继续/搜索/电台/下载/智能歌单)

### 🏗️ 架构增强
- **MainActivity**: 新增"下载"标签、播客标签、统计标签、下载管理、主题编辑器、播客管理、Last.fm/MusicBrainz 设置、搜索筛选、小组件配置
- **PlayerService**: 集成 DownloadManager 回调、Last.fm scrobble、播客流处理、10段均衡器应用
- **build.sh**: 版本号 16/13.0.0, 新文件编译

## 🏗️ 架构 (36 源文件 / 9000+ 行)

```
src/com/musicfusion/app/
├── MainActivity.java       # UI / 8标签 / 搜索 / 播放控制 / 设置
├── PlayerService.java      # 播放引擎 (Gapless / RG / MediaSession / 均衡器)
├── Audius.java             # 去中心化音乐
├── Archive.java            # 公有领域/CC
├── RadioBrowser.java       # 全球电台
├── SomaFM.java             # 非营利电台
├── Openverse.java          # CC 音频聚合
├── Jamendo.java            # Openverse 来源
├── EqPresets.java          # 5 种 EQ 风格
├── WhiteNoise.java         # 雨/火/棕混音
├── IcyMetadata.java        # 直播流元数据
├── LyricsEngine.java       # 多源歌词聚合
├── Lyrics.java             # 本地 LRC 解析
├── L10n.java               # 中/英双语
├── MusicWidget.java        # 桌面小组件 (原版)
├── CrashReporter.java      # JSON 崩溃上报 (脱敏)
├── ImageCache.java         # 图片缓存
├── BackupManager.java      # 备份/恢复
├── SmartPlaylist.java      # 智能歌单
├── SearchSuggest.java      # 搜索建议
├── ReplayGainParser.java   # 响度归一化
├── MaterialColor.java      # 动态色
├── VisualizerView.java     # 音频可视化
├── DownloadManager.java    # 🆕 离线下载管理
├── GraphicEqualizer.java   # 🆕 10段图形均衡器
├── PodcastEngine.java      # 🆕 播客引擎
├── LastFmScrobbler.java    # 🆕 Last.fm Scrobble
├── MusicBrainz.java        # 🆕 元数据补全
├── ThemeManager.java       # 🆕 自定义主题
├── StatsEngine.java        # 🆕 统计图表
├── SearchFilters.java      # 🆕 搜索筛选
├── Widget2x.java           # 🆕 多尺寸小组件
├── NotificationBig.java    # 🆕 BigPicture 通知
├── AutoManifest.java       # 🆕 Android Auto 声明
├── QuickTile.java          # 🆕 快捷设置磁贴
└── Shortcuts.java          # 🆕 App Shortcuts
```

## 🎵 音乐源 (全部合法开放 · 0 商业曲库)

| 源 | 类型 | 合法依据 |
|---|---|---|
| **Audius** | 去中心化创作者 | 创作者自主上传 CC |
| **Internet Archive** | 公有领域+CC | archive.org 公有许可 |
| **RadioBrowser** | 全球电台目录 | 社区维护, 公开 API |
| **SomaFM** | 非营利独立电台 | 商业赞助非营利 |
| **Openverse** | CC 音频聚合 | WordPress 基金会 |
| **Jamendo** | CC 音乐 | Jamendo 许可 |
| **Podcast (RSS)** | 播客 | 开放 RSS/OPML 标准 |
| **Last.fm** | 元数据/统计 | 官方 API 免费 |
| **MusicBrainz** | 元数据 | 开放音乐百科 |

## 🔒 安全与隐私

详见 [SECURITY.md](SECURITY.md) 与 [PRIVACY.md](PRIVACY.md)

- 崩溃报告**指纹脱敏** (设备型号/序列号 SHA256 截断)
- 无网络追踪/分析 SDK
- 无广告/付费墙/登录墙
- 所有数据仅存本机 `SharedPreferences` + 应用沙盒
- 备份文件仅在用户主动导出时写入 `Downloads/MusicFusion/`
- 下载仅限明确允许下载的源 (IA/Archive/Openverse CC0)

## 🔧 编译

```bash
# 需要: aapt / dx / apksigner / android.jar (API 30)
bash build.sh
# 产物: musicfusion.apk (<250KB)
```

## 📦 安装

```bash
# 通过 ADB / Shizuku / 任何已 root 通道
pm install -r musicfusion.apk
```

## 📊 版本

| 字段 | 值 |
|---|---|
| versionCode | 16 |
| versionName | 13.0.0 |
| minSdk | 24 |
| targetSdk | 30 |
| 大小 | <250KB |
| License | MIT |

## 📜 License

MIT — 完全开源, 自由使用/修改/分发