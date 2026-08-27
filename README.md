# MusicFusion v12 "Nebula" 🎵

一站式聚合音乐播放器 — 28 源文件 / 6600+ 行 / 201KB / 纯 Java / MIT / 全部合法音源

> v11 基础上极全面升级：架构现代化 · 播放核心质变 · 音乐发现深挖 · 独家功能 · 系统级集成

## ✨ v12 升级清单 (22+ 项)

### 🛡️ 稳定基石
- **全链路 try-catch 铁壁** — 所有入口、回调、点击、网络 IO 零闪退
- **崩溃上报 JSON v2** — 自动写入应用沙盒, 启动时聚合展示, 设备指纹**脱敏**

### 🎚️ 播放核心
- **Gapless 无缝播放** — 双 `MediaPlayer` 交替, `setNextMediaPlayer` (API 23+)
- **Crossfade 200ms 淡入淡出**
- **ReplayGain 响度归一化** — 自写 ID3v2 / VorbisComment / FLAC 解析器
- **队列持久化 + 恢复** — 进程被杀自动复原
- **MediaSession 锁屏控制** — 通知/线控/Assistant 统一入口
- **三模式循环** (关/列表/单曲)
- **睡眠淡出** 30s 线性淡出

### 🔍 音乐发现
- **Audius GraphQL 深度挖掘** — playlist / underground / remixes
- **Internet Archive 高级检索** — collection/date/subject/venue 多字段
- **RadioBrowser 多维筛选** — byTag / byLanguage / byCountry / byCodec
- **Openverse 许可证/来源/扩展名** — searchCC0 / bySource / byExt

### 🧠 独家功能
- **智能歌单** — 流派/年代/心情/BPM 规则
- **多源歌词** — LRCLIB(优先) / 网易云 / QQ
- **音频可视化** — 4 模式 (波形/频谱柱/圆环/粒子)
- **完整备份/恢复** — JSON 含设置/歌单/收藏/历史/EQ
- **图片缓存** — 内存+磁盘双层 LRU
- **搜索建议+纠错** — 历史+热词+ Damerau-Levenshtein
- **Material You 动态色** (API 31+)

### 📱 系统集成
- **通知 MediaStyle + 4 动作** (上一曲/暂停/下一曲/停止)
- **17 项设置面板** (新增 可视化/ReplayGain/备份/智能歌单/崩溃)

## 🏗️ 架构 (28 源文件 / 6600+ 行)

```
src/com/musicfusion/app/
├── MainActivity.java     # UI / 标签 / 搜索 / 播放控制
├── PlayerService.java    # 播放引擎 (Gapless / RG / MediaSession)
├── Audius.java           # 去中心化音乐
├── Archive.java          # 公有领域/CC
├── RadioBrowser.java     # 全球电台
├── SomaFM.java           # 非营利电台
├── Openverse.java        # CC 音频聚合
├── EqPresets.java        # 5 种 EQ 风格
├── WhiteNoise.java       # 雨/火/棕混音
├── IcyMetadata.java      # 直播流元数据
├── LyricsEngine.java     # 多源歌词聚合
├── Lyrics.java           # 本地 LRC 解析
├── L10n.java             # 中/英双语
├── MusicWidget.java      # 桌面小组件
├── CrashReporter.java    # JSON 崩溃上报 (脱敏)
├── ImageCache.java       # 图片缓存
├── BackupManager.java    # 备份/恢复
├── SmartPlaylist.java    # 智能歌单
├── SearchSuggest.java    # 搜索建议
├── ReplayGainParser.java # 响度归一化
├── MaterialColor.java    # 动态色
├── VisualizerView.java   # 音频可视化
└── Jamendo.java          # Openverse 来源
```

## 🎵 音乐源 (全部合法开放 · 0 商业曲库)

| 源 | 类型 | 合法依据 |
|---|---|---|
| **Audius** | 去中心化创作者 | 创作者自主上传 CC |
| **Internet Archive** | 公有领域+CC | archive.org 公有许可 |
| **RadioBrowser** | 全球电台目录 | 社区维护, 公开 API |
| **SomaFM** | 非营利独立电台 | 商业赞助非营利 |
| **Openverse** | CC 音频聚合 | WordPress 基金会 |

## 🔒 安全与隐私

详见 [SECURITY.md](SECURITY.md) 与 [PRIVACY.md](PRIVACY.md)

- 崩溃报告**指纹脱敏** (设备型号/序列号 SHA256 截断)
- 无网络追踪/分析 SDK
- 无广告/付费墙
- 所有数据仅存本机 `SharedPreferences` + 应用沙盒
- 备份文件仅在用户主动导出时写入 `Downloads/MusicFusion/`

## 🔧 编译

```bash
# 需要: aapt / dx / apksigner / android.jar (API 30)
bash build.sh
# 产物: musicfusion.apk (201KB)
```

## 📦 安装

```bash
# 通过 ADB / Shizuku / 任何已 root 通道
pm install -r musicfusion.apk
```

## 📊 版本

| 字段 | 值 |
|---|---|
| versionCode | 15 |
| versionName | 12.0.0 |
| minSdk | 24 |
| targetSdk | 30 |
| 大小 | 201KB |
| License | MIT |

## 📜 License

MIT — 完全开源, 自由使用/修改/分发
