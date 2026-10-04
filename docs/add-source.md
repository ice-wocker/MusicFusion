# 加一个新音源

以 `src/com/musicfusion/app/SomaFM.java` 为模板（最短、最典型），四步：

## 1. 新建 `src/com/musicfusion/app/Xxx.java`

- `package com.musicfusion.app;`，只用 `org.json` + JDK 标准库（零依赖铁律）。
- 提供两个静态方法：
  - `public static String search(String q)` —— 返回原始 JSON/文本（网络请求自己写，参考 `Audius.search` 的 fallback host 写法）。
  - 解析方法（如 `parse`）—— 返回 `String[]`，每行字段用 `\u0001` 分隔，参考 `Audius.parseWithIds`：`曲名\1描述\1来源\1url`。
- 所有网络异常直接抛出，不要吞（调用方统一处理）。

## 2. 接入 `MainActivity.doSearch`

在 `else` 分支（Audius / Archive / Openverse 那几段）仿写一段：

```java
try {
    for (String _l : Xxx.parse(Xxx.search(q))) {
        String[] s = _l.split("\u0001");
        out.add(new Object[]{s[0], s[1] + " · " + s[2], s[3], "曲"});
    }
} catch (Exception e) { addErr("Xxx", e); }
```

失败必须调 `addErr`，这样顶部错误条会告诉用户是哪个源挂了，而不是静默少结果。

## 3. 注册到来源筛选

- `MainActivity.java` 的 `sourceLabels` 数组（约 2458 行）追加标签。
- `SearchFilters.java` 的 `sources` 数组长度同步 +1，并更新 `0=…` 注释。

## 4. 验证

```bash
./scripts/test-all.sh   # 先过静态自检
./build.sh              # 再完整构建（需 ANDROID_HOME + JDK 17）
```

只接受合法公开 API / 免费流媒体源。
