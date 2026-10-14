#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -n "${JAVA_HOME:-}" ]]; then
  JAVAC="$JAVA_HOME/bin/javac"; JAVA="$JAVA_HOME/bin/java"; JAR="$JAVA_HOME/bin/jar"
else
  JAVAC=javac; JAVA=java; JAR=jar
fi
command -v "$JAVAC" >/dev/null || { echo '需要 JDK 17+，请设置 JAVA_HOME。' >&2; exit 1; }
mkdir -p build/classes build/test-classes dist build/package
find src/main/java -name '*.java' | sort > build/main-sources.txt
"$JAVAC" --release 17 -encoding UTF-8 -d build/classes @build/main-sources.txt
cp -R src/main/resources/. build/classes/
"$JAR" --create --file dist/ppt-format-converter.jar --main-class com.pptconverter.App -C build/classes .
cp dist/ppt-format-converter.jar build/package/
find src/test/java -name '*.java' | sort > build/test-sources.txt
"$JAVAC" --release 17 -encoding UTF-8 -cp build/classes -d build/test-classes @build/test-sources.txt
"$JAVA" -ea -cp build/classes:build/test-classes com.pptconverter.ConverterTest
echo '构建与测试完成：dist/ppt-format-converter.jar'
