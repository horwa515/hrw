#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -n "${JAVA_HOME:-}" ]]; then
  JAVAC="$JAVA_HOME/bin/javac"; JAVA="$JAVA_HOME/bin/java"; JAR="$JAVA_HOME/bin/jar"
else
  JAVAC=javac; JAVA=java; JAR=jar
fi
command -v "$JAVAC" >/dev/null || { echo '需要 JDK 8+，请设置 JAVA_HOME。' >&2; exit 1; }
if [[ "$("$JAVAC" -version 2>&1)" == "javac 1.8."* ]]; then
  COMPILER_TARGET=(-source 8 -target 8)
else
  COMPILER_TARGET=(--release 8 -Xlint:-options)
fi
mkdir -p build/classes build/test-classes dist build/package
find src/main/java -name '*.java' | sort > build/main-sources.txt
"$JAVAC" "${COMPILER_TARGET[@]}" -encoding UTF-8 -d build/classes @build/main-sources.txt
cp -R src/main/resources/. build/classes/
"$JAR" cfe dist/ppt-format-converter.jar com.pptconverter.App -C build/classes .
cp dist/ppt-format-converter.jar build/package/
find src/test/java -name '*.java' | sort > build/test-sources.txt
"$JAVAC" "${COMPILER_TARGET[@]}" -encoding UTF-8 -cp build/classes -d build/test-classes @build/test-sources.txt
"${PPT_TEST_JAVA:-$JAVA}" -ea -cp build/classes:build/test-classes com.pptconverter.ConverterTest
echo '构建与测试完成：dist/ppt-format-converter.jar'
