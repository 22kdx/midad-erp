#!/usr/bin/env bash
# ترجمة وتشغيل اختبارات نواة المحاسبة (لا تحتاج Android SDK)
set -e
cd "$(dirname "$0")"
KOTLINC="${KOTLINC:-kotlinc}"
if [ ! -x "$(command -v "$KOTLINC" 2>/dev/null || true)" ] && [ -x /tmp/kotlinc/bin/kotlinc ]; then
  KOTLINC=/tmp/kotlinc/bin/kotlinc
fi
mkdir -p out
echo "▶ ترجمة النواة…"
"$KOTLINC" app/src/main/java/com/example/midad/core -d out/core.jar 2>&1 | grep -vE "^warning:" || true
echo "▶ ترجمة الاختبارات…"
"$KOTLINC" -classpath out/core.jar app/src/test/java/com/example/midad/core -include-runtime -d out/tests.jar 2>&1 | grep -vE "^warning:" || true
echo "▶ تشغيل الاختبارات…"
java -Dfile.encoding=UTF-8 -cp out/core.jar:out/tests.jar com.example.midad.core.TestRunnerKt
