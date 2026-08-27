# 🎵 MusicFusion

一站式聚合音乐播放器 — 整合全球开放音乐源，纯公开 API，无需任何账号。

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![APK](https://img.shields.io/badge/APK-v11.0--Aurora-1db954.svg)](musicfusion.apk)

## ✨ v11.0 "Aurora" 全新特性

| 特性 | 描述 |
|---|---|
| 🎵 **Mini Player 持久化** | 全 App 任何 tab 顶部都能看到迷你播放条，背景播放状态随时可见 |
| 📜 **歌词抽屉** | 半屏可滚动歌词面板（替代原弹窗），字号/排版更舒服 |
| 🎤 **Audius 作者/专辑下钻** | 点击曲目"作者"展开该创作者全部作品 |
| 📡 **电台 ICY 元数据** | 直播流 StreamTitle 解析，电台上方显示当前播放曲目 |
| 🎛 **EQ 预设** | 流行/摇滚/古典/爵士/电子 5 种风格一键切换 |
| 🌧 **白噪声生成器** | 雨声/柴火/棕噪声 3 层独立音量混音，离线生成 .wav 播放 |
| 🦽 **A11y 注释** | 所有可点击控件加 contentDescription |
| ⚠️ **错误横幅** | Audius/Archive/Openverse 多源失败时统一横幅显示 |
| 🔄 **刷新离线目录** | 设置里一键从 RadioBrowser API 拉取最新 Top100 热门覆盖本地 |

**继承 v10 全部能力**：多语言（中/英）/均衡器/倍速/睡眠定时/桌面小部件/本地下载/歌单/排行/省流量模式/浅色主题。

## 📱 标签页

| 标签 | 内容 |
|---|---|
| 🔥 热门 | Audius 去中心化音乐平台实时热榜（创作者自主上传） |
| 🔍 搜索 | 跨源聚合搜索：Audius 曲目 + Internet Archive 音乐档案 + Openverse CC 音频 |
| 📻 电台 | 公共电台直链 + SomaFM 24 频道 + RadioBrowser 全球社区电台 |
| 📡 目录 | **内置 2945 个真实电台离线目录**（断网可浏览，联网播放） |
| ❤️ 我的 | 最近播放 / 收藏 / 歌单 / 排行榜 / 已下载 |

## 🎧 音乐源（全部合法开放）

| 源 | 类型 | 授权 |
|---|---|---|
| [Audius](https://audius.co) | 去中心化流媒体 | 平台协议 |
| [Internet Archive](https://archive.org) | 公有领域 / CC 音乐档案 | CC / PD |
| [RadioBrowser](https://www.radio-browser.info) | 5 万+ 社区维护电台 | 公开 API |
| [SomaFM](https://somafm.com) | 非营利独立电台 | 听众赞助 |
| [Openverse](https://openverse.org) | CC 音频聚合 | CC |

> 本项目不聚合也不支持任何付费订阅平台的盗版内容。

## 🏗️ 构建

无需 Gradle / Android Studio，纯 Java + 原生 Android API：

```bash
bash build.sh   # 产出 musicfusion.apk (~160KB)
```

## 📄 License

MIT — 电台目录数据来自 RadioBrowser 社区 (CC-BY 4.0)。
