#!/usr/bin/env bash
# MusicFusion 静态自检 —— 不需要 Android SDK，哪里都能跑。
# 完整构建走 ./build.sh（需要 ANDROID_HOME + JDK 17）。
set -euo pipefail
cd "$(dirname "$0")/.."
fail=0

# 1. 根目录不允许散落 .java（构建只认 src/，见 build.sh 的 find src）
if ls *.java >/dev/null 2>&1; then
  echo "❌ 根目录有散落的 .java（构建不认，还会误导新人）:"
  ls *.java
  fail=1
else
  echo "✅ 根目录干净"
fi

# 2. 不允许 .bak 残留进仓库
if git ls-files '*.bak*' | grep -q .; then
  echo "❌ 仓库里有 .bak 残留:"
  git ls-files '*.bak*'
  fail=1
else
  echo "✅ 无 .bak 残留"
fi

# 3. README 引用的协作文档必须存在
for f in CONTRIBUTING.md docs/add-source.md scripts/test-all.sh; do
  if [ -f "$f" ]; then
    echo "✅ $f 存在"
  else
    echo "❌ 缺少 $f（README 引用了它）"
    fail=1
  fi
done

# 4. 源码规模速览
echo "ℹ️  src 下 $(find src -name '*.java' | wc -l) 个 java 文件"

exit $fail
