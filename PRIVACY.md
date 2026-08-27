# Privacy Policy

**最后更新: 2026-08-27**

## 概述

MusicFusion 是**完全离线优先**的音乐播放器, 不收集任何个人数据。

## 数据收集: 零

本应用**不收集**以下任何信息:
- ❌ 设备标识 (IMEI / Android ID / MAC / 序列号)
- ❌ 位置信息
- ❌ 通讯录 / 短信 / 通话记录
- ❌ 摄像头 / 麦克风
- ❌ 已安装应用列表
- ❌ 崩溃时的真实设备指纹 (已脱敏, 见下)
- ❌ 任何形式的用户行为分析

## 网络请求: 仅限音乐源

应用**仅向以下合法公开 API 发起 HTTPS/HTTP 请求**:

| 域名 | 用途 |
|---|---|
| `*.audius.co` | Audius 去中心化音乐 |
| `*.radio-browser.info` | RadioBrowser 全球电台 |
| `archive.org` | Internet Archive 公有领域音频 |
| `somafm.com` | SomaFM 非营利电台 |
| `api.openverse.org` | Openverse CC 音频 |
| `lrclib.net` | LRCLIB 歌词 |
| 流媒体服务器 (用户播放的电台) | 仅用于音频流 |

**不向任何第三方追踪/广告/分析服务器发送数据。**

## 崩溃报告 (本地)

应用启用本地崩溃捕获 (用于改进稳定性):
- 崩溃记录**仅存应用沙盒** `filesDir/crashes/`
- 设备字段使用 **SHA256 截断**脱敏:
  - `Build.FINGERPRINT` → 取前 16 字符的 SHA256
  - `Build.SERIAL` → 完全不记录
- **不上传任何服务器**, 仅供用户在应用内查看
- 可在 设置 → 崩溃报告 → 清除 中随时删除

## 用户数据存储

| 数据 | 位置 | 用途 |
|---|---|---|
| 设置偏好 | `SharedPreferences` (`mf`) | 主题/语言/睡眠定时等 |
| 歌单/收藏/历史 | `SharedPreferences` (`mf`) | 列表显示 |
| 队列状态 | `SharedPreferences` (`mf`) | 进程恢复 |
| 崩溃报告 | `filesDir/crashes/*.json` | 稳定性改进 |
| 图片缓存 | `filesDir/image_cache/` | 专辑封面缓存 |
| 备份文件 | `Downloads/MusicFusion/` | 用户主动导出 |
| 下载音频 | `Downloads/MusicFusion/` | 用户主动下载 |

**所有数据均在本机, 卸载应用即彻底删除。**

## 权限使用

| 权限 | 用途 | 必需 |
|---|---|---|
| `INTERNET` | 音乐流 + API 请求 | ✅ |
| `ACCESS_NETWORK_STATE` | 网络状态检测 | ✅ |
| `FOREGROUND_SERVICE` | 后台播放 | ✅ |

应用**不申请**以下权限 (无相关功能):
- 定位 / 通讯录 / 短信 / 通话 / 存储 / 相机 / 麦克风

## 第三方组件

- **MediaPlayer / AudioFocusRequest** (Android Framework) — 系统 API
- **Visualizer** (Android Framework) — 系统 API, 仅当启用可视化时采集当前播放会话的频谱

## 您的权利

- **查看**: 应用内 设置 → 关于/统计 可见本地数据
- **导出**: 设置 → 备份/恢复 可生成 JSON
- **删除**: 设置 → 崩溃报告 → 清除 可删除崩溃记录
- **完全清除**: 卸载应用, 卸载时所有数据一并删除

## 联系方式

如对本政策有疑问, 请通过 GitHub Issues 提交:
https://github.com/<owner>/MusicFusion/issues

---

**本应用遵守 GDPR / CCPA / 中国《个人信息保护法》之最小必要原则。**
