# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 12.x    | ✅ 积极维护        |
| 11.x    | ⚠️ 仅关键安全修复  |
| < 11.0  | ❌ 不再支持        |

## Reporting a Vulnerability

**请勿在公开 Issue 中报告安全漏洞。**

通过 GitHub Security Advisories 私密报告:
https://github.com/<owner>/MusicFusion/security/advisories/new

请提供:
- 漏洞描述与影响范围
- 复现步骤 (PoC 优先)
- 受影响版本
- 您的联系方式 (可选)

## Response Timeline

- **48 小时内**确认收到
- **7 天内**评估严重性
- **30 天内**发布修复或披露时间表

## Security Design Principles

1. **零外部追踪** — 不集成 Firebase / Crashlytics / Bugly / 任何分析 SDK
2. **零广告** — 不嵌入 AdMob / 穿山甲 / 任何广告 SDK
3. **本地优先** — 所有用户数据仅存本机 `SharedPreferences` + 应用沙盒
4. **崩溃脱敏** — 崩溃报告设备字段使用 SHA256 截断, 不收集真实标识
5. **最小权限** — 仅申请必要权限 (INTERNET / FOREGROUND_SERVICE / ACCESS_NETWORK_STATE)
6. **来源合法** — 音乐源全部为公开合法 API (Audius / Internet Archive / RadioBrowser / SomaFM / Openverse), 不抓取商业曲库

## 已知安全权衡

- `minSdk=24` — 出于兼容考虑, 不支持 API < 24 设备
- `targetSdk=30` — 出于体积考虑, 未适配 Android 11+ 分区存储的强制要求; 下载文件存 `Downloads/MusicFusion/`
- 自写 ID3/VorbisComment 解析器 — 已做长度校验, 但理论上存在畸形文件 DoS 风险
- 网络层使用 `HttpURLConnection` — 不强制 HTTPS, 部分历史电台仅支持 HTTP
