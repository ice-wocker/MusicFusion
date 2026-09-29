#!/bin/bash
# MusicFusion 构建脚本 —— 任意 Linux + Android SDK 环境可用
#
# 原脚本是 Termux 专用的：硬编码 /data/data/com.termux/... 的 aapt/dx/keytool，
# 还依赖 Termux 的 dalvikvm 跑 ecj，导致 CI 和普通开发者都跑不起来。
# 现在改为全部走 ANDROID_HOME 里的官方工具，并保留 Termux 兼容路径。
#
# 环境变量：
#   ANDROID_HOME / ANDROID_SDK_ROOT   Android SDK 根目录
#   KEYSTORE / KS_PASS / KEY_PASS     可选，默认用仓库内 debug.keystore(mf123456)
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
  for c in "$HOME/Android/Sdk" "$HOME/apkbuild" /usr/local/lib/android/sdk /opt/android-sdk; do
    [ -d "$c" ] && SDK="$c" && break
  done
fi
[ -n "$SDK" ] && [ -d "$SDK" ] || { echo "❌ 未找到 Android SDK，请设置 ANDROID_HOME"; exit 1; }

BT=$(ls -1d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)
PLATFORM=""
for v in 35 34 33 31 30 29 28 27 26 25 24 23 22 21; do
  [ -f "$SDK/platforms/android-$v/android.jar" ] && PLATFORM="$v" && break
done
[ -n "$PLATFORM" ] || { echo "❌ $SDK/platforms 下没有可用 platform"; exit 1; }
ANDROID_JAR="$SDK/platforms/android-$PLATFORM/android.jar"
command -v javac >/dev/null || { echo "❌ 需要 javac（JDK 17）"; exit 1; }

echo "SDK: $SDK | build-tools: $(basename "$BT") | platform: android-$PLATFORM"

WORK=build
rm -rf "$WORK"
mkdir -p "$WORK"/{res,gen,classes,dex}

echo "=== 1/5 编译资源 (aapt2) ==="
"$BT/aapt2" compile --dir res -o "$WORK/res.zip"

echo "=== 2/5 链接资源 + R.java ==="
LINK_ARGS=(
  link -o "$WORK/base.apk" -I "$ANDROID_JAR"
  --manifest AndroidManifest.xml
  -R "$WORK/res.zip" --java "$WORK/gen"
  --min-sdk-version 24 --target-sdk-version 30 --auto-add-overlay
)
[ -d assets ] && LINK_ARGS+=(-A assets)
"$BT/aapt2" "${LINK_ARGS[@]}"

echo "=== 3/5 编译 Java ==="
find src -name '*.java' > "$WORK/sources.txt"
find "$WORK/gen" -name '*.java' >> "$WORK/sources.txt" 2>/dev/null || true
javac -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
  -nowarn -d "$WORK/classes" @"$WORK/sources.txt" 2>&1 | grep -v 'bootstrap class path' || true

COUNT=$(find "$WORK/classes" -name '*.class' | wc -l)
[ "$COUNT" -gt 0 ] || { echo "❌ 没有生成 class 文件"; exit 1; }
echo "生成 class 文件: $COUNT"

echo "=== 4/5 转 DEX + 打包 + 对齐 ==="
"$BT/d8" --min-api 24 --lib "$ANDROID_JAR" --output "$WORK/dex" \
  $(find "$WORK/classes" -name '*.class') > /dev/null
cp "$WORK/base.apk" "$WORK/unsigned.apk"
(cd "$WORK/dex" && zip -q -j "../unsigned.apk" classes.dex)
"$BT/zipalign" -f 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"

echo "=== 5/5 签名 ==="
KS="${KEYSTORE:-debug.keystore}"
KS_PASS="${KS_PASS:-mf123456}"
KEY_PASS="${KEY_PASS:-mf123456}"
ALIAS="${KEY_ALIAS:-mf}"
[ -f "$KS" ] || { echo "❌ 找不到 keystore: $KS"; exit 1; }
"$BT/apksigner" sign --ks "$KS" --ks-key-alias "$ALIAS" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" \
  --out musicfusion.apk "$WORK/aligned.apk"
"$BT/apksigner" verify musicfusion.apk > /dev/null && echo "✅ 签名验证通过"

SIZE=$(stat -c%s musicfusion.apk 2>/dev/null || stat -f%z musicfusion.apk)
echo "==== ✅ 成品: musicfusion.apk ($SIZE bytes / $((SIZE/1024)) KB) ===="
