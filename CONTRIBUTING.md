# Contributing to MusicFusion

## 铁律

- **零依赖**：只用 Android SDK + JDK 标准能力 + `org.json`（系统自带），不要加 Gradle 依赖。
- 构建脚本是 `build.sh`（手写 aapt2/d8 链），改构建逻辑务必保证 CI（`build.yml` 跑的就是它）能过。

## 本地验证

```bash
./scripts/test-all.sh   # 静态自检，不需要 Android SDK
./build.sh              # 完整构建，需要 ANDROID_HOME + JDK 17
```

## 加新音源

看 [docs/add-source.md](docs/add-source.md)，按 SomaFM 的样子抄一遍就行。

中文 / English 都欢迎。
