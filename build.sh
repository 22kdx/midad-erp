#!/usr/bin/env bash
# ترجمة وتشغيل اختبارات نواة المحاسبة (لا تحتاج Android SDK)
set -euo pipefail
cd "$(dirname "$0")"
KOTLINC="${KOTLINC:-kotlinc}"
if [ ! -x "$(command -v "$KOTLINC" 2>/dev/null || true)" ] && [ -x /tmp/kotlinc/bin/kotlinc ]; then
  KOTLINC=/tmp/kotlinc/bin/kotlinc
fi
mkdir -p out
echo "▶ ترجمة النواة…"
if ! "$KOTLINC" app/src/main/java/com/example/midad/core -d out/core.jar >out/core-compile.log 2>&1; then
  cat out/core-compile.log
  exit 1
fi
grep -vE "^warning:" out/core-compile.log || true
echo "▶ ترجمة الاختبارات…"
if ! "$KOTLINC" -classpath out/core.jar app/src/test/java/com/example/midad/core -include-runtime -d out/tests.jar >out/tests-compile.log 2>&1; then
  cat out/tests-compile.log
  exit 1
fi
grep -vE "^warning:" out/tests-compile.log || true
echo "▶ تشغيل الاختبارات…"
java -Dfile.encoding=UTF-8 -cp out/core.jar:out/tests.jar com.example.midad.core.TestRunnerKt
