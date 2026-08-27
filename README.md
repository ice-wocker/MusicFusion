# MusicFusion v12 "Nebula" 🎵

一站式聚合音乐播放器 — 28 个源文件 / 6600+ 行 / 201KB / 纯 Java / MIT / 全部合法音源

> 上游 v11 基础上极全面升级：架构现代化 · 播放核心质变 · 音乐发现深挖 · 独家功能 · 系统级集成

## ✨ v12 升级清单 (22+ 项)

### 🛡️ P0 稳定基石
1. **全链路 try-catch 铁壁** — 所有 public 入口、Service 回调、UI 点击、网络解析、文件 IO 零闪退
2. **崩溃上报 JSON v2** — `CrashReporter.java` 自动写入 `filesDir/crashes/`, 启动时聚合展示, 含设备/内存/线程/堆栈全维度元数据

### 🎚️ P1 播放核心质变
3. **Gapless 无缝播放** — 双 `MediaPlayer` 交替, `setNextMediaPlayer` (API 23+) 预加载下一曲
4. **Crossfade 交叉淡入淡出** — 200ms 主音量淡出, 下一曲从 0 渐入
5. **ReplayGain 响度归一化** — `ReplayGainParser.java` 自写 MP3(ID3v2) / FLAC(VorbisComment) / OGG / OPUS 标签解析器 (<300行), 自动调音量防爆音
6. **队列持久化 + 恢复** — 进程被杀后启动自动恢复队列 + 位置, 透明无感
7. **MediaSession + 锁屏控制** — 反射初始化 `android.media.session.MediaSession` (无 androidx 依赖), 锁屏/通知/耳机线控/Assistant 统一入口
8. **三模式循环** — 关 / 列表循环 / 单曲循环, 通知图标 + 状态栏实时同步
9. **睡眠淡出模式** — 30s 线性淡出, 优雅结束不突兀

### 🔍 P2 音乐发现深挖
10. **Audius GraphQL 深度挖掘** — `Audius.java` 新增 `playlists()` / `trendingUnderground()` / `remixes()` / `playlist(id)`
11. **Internet Archive 高级检索** — `Archive.java` collection/date/subject/venue/source 多字段组合, 现场(etree) / 古董(georgeblood) / 公共音频, 含 `firstAudio()` 提取真实流地址
12. **RadioBrowser 多维筛选** — `RadioBrowser.java` byTag / byLanguage / byCountry / byCodec / advanced(组合条件), 25 流派标签快速入口
13. **Openverse 许可证/来源/扩展名** — `Openverse.java` searchCC0 / searchBySource(Wikimedia/Jamendo/Freesound) / searchByExt(MP3/OGG/FLAC)

### 🧠 P3 独家差异化
14. **智能歌单生成** — `SmartPlaylist.java` 按流派/年代/心情/BPM 规则, 80% 历史偏好 + 20% 探索, 智能洗牌避免重复
15. **多源歌词聚合** — `LyricsEngine.java` LRCLIB(优先) / 网易云 / QQ / Musixmatch, LRC 解析, 时间轴同步
16. **音频可视化** — `VisualizerView.java` 4 模式: 波形/频谱柱/圆环/粒子, FFT 实时频谱, 渐变着色, 30fps
17. **完整备份/恢复** — `BackupManager.java` 设置+歌单+收藏+历史+EQ+可视化+统计, Download/MusicFusion/ JSON 导出, 合并去重
18. **图片缓存** — `ImageCache.java` 内存 LRU (8MB) + 磁盘 (50MB) 双层缓存, 自动 LRU 淘汰
19. **搜索建议+纠错+热词** — `SearchSuggest.java` 历史+热词+远程 Gist 12h 缓存, Damerau-Levenshtein 距离纠错
20. **Material You 动态色** — `MaterialColor.java` API 31+ 提取壁纸主色调, HSL 调色板生成, 浅/深双模式, WCAG AA 对比度修正

### 📱 P4 系统级集成
21. **通知 MediaStyle + 4 动作按钮** — 上一曲/播放暂停/下一曲/停止, 锁屏可见
22. **设置面板 17 项** — 新增 可视化/ReplayGain/备份恢复/智能歌单/崩溃报告
23. **设置面板分类导航** — 播放/视觉/数据/系统/关于五大类

## 🏗️ 架构 (28 源文件 / 6600+ 行)

```
src/com/musicfusion/app/
├── MainActivity.java          (2000+ 行 · UI/标签/搜索/播放控制)
├── PlayerService.java         (600+ 行 · 播放引擎)
├── Audius.java                (210 行 · 去中心化音乐)
├── Archive.java               (200 行 · 公有领域/CC)
├── RadioBrowser.java          (180 行 · 5万+ 电台)
├── SomaFM.java                (非营利电台)
├── Openverse.java             (CC 音频聚合)
├── EqPresets.java             (5 种 EQ 风格)
├── WhiteNoise.java            (雨/火/棕三层混音)
├── IcyMetadata.java           (直播流元数据)
├── LyricsEngine.java          (多源歌词聚合)      ★ v12
├── Lyrics.java                (本地 LRC 解析)
├── L10n.java                  (中/英双语)
├── MusicWidget.java           (桌面小组件)
├── CrashReporter.java         (JSON 崩溃上报)     ★ v12
├── ImageCache.java            (图片缓存)          ★ v12
├── BackupManager.java         (备份/恢复)         ★ v12
├── SmartPlaylist.java         (智能歌单)          ★ v12
├── SearchSuggest.java         (搜索建议纠错)      ★ v12
├── ReplayGainParser.java      (响度归一化)        ★ v12
├── MaterialColor.java         (动态色)            ★ v12
├── VisualizerView.java        (音频可视化)        ★ v12
└── Jamendo.java               (Openverse 来源)
```

## 🎵 音乐源 (全部合法开放 · 0 商业曲库)

| 源 | 类型 | 数量 | 合法依据 |
|---|---|---|---|
| **Audius** | 去中心化创作者平台 | 100万+ | 创作者自主上传, CC 协议 |
| **Internet Archive** | 公有领域 + CC | 900万+ | archive.org 公有领域/CC 许可 |
| **RadioBrowser** | 全球电台目录 | 5万+ | 社区维护, 公开 API |
| **SomaFM** | 非营利独立电台 | 24 频道 | soma.fm 商业赞助非营利 |
| **Openverse** | CC 音频聚合 | 393万+ | WordPress 基金会, API.openverse.org |

## 🔧 编译/运行

**无 Gradle, 纯 Java + aapt/dx/apksigner, Termux/Android SDK 即可**

```bash
cd ~/musicfusion
bash build.sh
# 产物: musicfusion.apk (201KB)
```

## 📦 安装 (vivo V2283A 真机测试通过)

```bash
scp -P 8022 musicfusion.apk u0_a260@192.168.101.26:~/mf.apk
ssh -p 8022 "cp mf.apk /sdcard/Download/"
# shizuku 安装
adb-equivalent: cp /sdcard/Download/mf.apk /data/local/tmp/ && pm install -r /data/local/tmp/mf.apk
```

## 🧪 真机回归 (vivo V2283A Android 15)

✅ 启动正常, 无 FATAL, 进程存活  
✅ Audius 热门榜加载成功 (8首)  
✅ 5 个 tab 切换正常  
✅ UI 渲染正常, 主题色正常  

## 📊 版本

| 字段 | 值 |
|---|---|
| versionCode | 15 |
| versionName | 12.0.0 |
| minSdk | 24 |
| targetSdk | 30 |
| 大小 | 201KB |
| SHA256 | b490cd878c4b3f6674031d9b8e1b45caec73ab62f48d15b7f4576d514523869f |
| License | MIT |

## 📜 License

MIT — 完全开源, 自由使用/修改/分发

---

**Made with ❤️ by ice-wocker · 一站式合法音乐聚合, 让 900万+ 曲目触手可及**
