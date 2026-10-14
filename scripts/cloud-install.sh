#!/usr/bin/env bash
# Reproducible cloud JDK installation; does not modify source, fixtures or lockfiles.
set -euo pipefail
JDK_DIR=/workspace/toolchains/ppt-converter-jdk
mkdir -p /workspace/toolchains
if [[ ! -x "$JDK_DIR/bin/javac" ]]; then
  if [[ -e "$JDK_DIR" ]]; then
    echo "Existing incomplete toolchain at $JDK_DIR; preserve it and diagnose before installing." >&2
    exit 1
  fi
  JDK_STAGE=$(mktemp -d /workspace/toolchains/ppt-jdk-download.XXXXXX)
  trap 'rm -rf "$JDK_STAGE"' EXIT
  JDK_URL=https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz
  curl --fail --silent --show-error --location --retry 2 "$JDK_URL" -o "$JDK_STAGE/jdk.tar.gz"
  curl --fail --silent --show-error --location --retry 2 "$JDK_URL.sha256" -o "$JDK_STAGE/checksum.txt"
  EXPECTED_SHA=$(awk '{print $1}' "$JDK_STAGE/checksum.txt")
  printf '%s  %s\n' "$EXPECTED_SHA" "$JDK_STAGE/jdk.tar.gz" | sha256sum --check --strict
  mkdir "$JDK_STAGE/jdk"
  tar -xzf "$JDK_STAGE/jdk.tar.gz" --strip-components=1 -C "$JDK_STAGE/jdk"
  test -x "$JDK_STAGE/jdk/bin/javac"
  mv "$JDK_STAGE/jdk" "$JDK_DIR"
fi
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
cd /workspace/hrw
bash build.sh
