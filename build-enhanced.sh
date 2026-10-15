#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
JDK_BIN="${JAVA_HOME:?Set JAVA_HOME to a JDK 8+}/bin"
[[ -f build/office-libs/dependencies.json ]] || { echo 'Run python3 scripts/fetch_office_dependencies.py first.' >&2; exit 1; }
mkdir -p build/enhanced-classes build/enhanced-tests
if [[ "$("$JDK_BIN/javac" -version 2>&1)" == "javac 1.8."* ]]; then TARGET=(-source 8 -target 8); else TARGET=(--release 8 -Xlint:-options); fi
"$JDK_BIN/javac" "${TARGET[@]}" -encoding UTF-8 -cp 'build/classes:build/office-libs/*' -d build/enhanced-classes src/enhanced/java/com/pptconverter/*.java
"$JDK_BIN/jar" cf dist/ppt-office-encryption.jar -C build/enhanced-classes .
"$JDK_BIN/javac" "${TARGET[@]}" -encoding UTF-8 -cp 'build/classes:build/enhanced-classes:build/office-libs/*' -d build/enhanced-tests src/enhanced/test/com/pptconverter/*.java
VALIDATION_OUTPUT="${1:-build/encrypted-validation.pptx}"
if [[ $# == 0 ]]; then rm -f "$VALIDATION_OUTPUT"; fi
"${PPT_TEST_JAVA:-$JDK_BIN/java}" -ea -cp 'build/classes:build/enhanced-classes:build/enhanced-tests:build/office-libs/*' com.pptconverter.OfficeEncryptionTest "$VALIDATION_OUTPUT"
