# 🎵 MusicFusion

一站式聚合音乐播放器 — 整合全球开放音乐源，纯公开 API，无需任何账号。

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![APK](https://img.shields.io/badge/APK-v1.0.0-1db954.svg)](musicfusion.apk)

## ✨ 功能

**v6.0** — 播放进度条拖拽 / 5段均衡器 / 倍速0.75-2x / 睡眠定时 / 单曲循环 / 搜索历史 / 分享与复制链接 / 统计 / ListView复用优化(2899台目录丝滑滚动) / Openverse请求缓存

| 标签 | 内容 |
|---|---|
| 🔥 热门 | Audius 去中心化音乐平台实时热榜（创作者自主上传，全曲播放） |
| 🔍 搜索 | 跨源聚合搜索：Audius 曲目 + Internet Archive 音乐档案（公有领域/CC 授权） |
| 📻 电台 | RadioBrowser 全球电台目录实时搜索 |
| 📡 目录 | **内置 2899 个真实电台离线目录**（断网可浏览，联网播放） |

- 深色 Spotify 风格 UI，600ms 防抖实时搜索
- 后台播放服务（START_STICKY），切出应用不断播
- 纯 Java + 原生 Android API，零第三方依赖，APK 仅 ~120KB

## 🎧 音乐源

全部为**合法开放内容**：

| 源 | 类型 | 授权 |
|---|---|---|
| [Audius](https://audius.co) | 去中心化流媒体，艺术家自主发布 | 平台协议 |
| [Internet Archive](https://archive.org) | 公有领域 / Creative Commons 音乐档案 | CC / PD |
| [RadioBrowser](https://www.radio-browser.info) | 5 万+ 社区维护电台目录 | 公开 API |

> 本项目不聚合也不支持任何付费订阅平台的盗版内容。

## 🏗️ 构建

无需 Gradle/Android Studio，一条命令（需 Termux 或 Linux + JDK8 + Android build-tools）：

```bash
bash build.sh        # 产出 musicfusion.apk
```

## 📱 安装

下载 `musicfusion.apk` 侧载安装（允许未知来源）。

## 📄 License

MIT — 电台目录数据来自 RadioBrowser 社区(CC-BY 4.0)。
