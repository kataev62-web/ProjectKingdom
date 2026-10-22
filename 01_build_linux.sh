#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v java >/dev/null; then
  echo 'ERROR: JDK 17 is required.' >&2; exit 1
fi
if ! java -version 2>&1 | head -n 1 | grep -q 'version "17\.'; then
  echo 'ERROR: Use JDK 17 (set JAVA_HOME/PATH) for Forge 1.20.1.' >&2
  java -version 2>&1 | head -n 1 >&2
  exit 1
fi
if [[ -x ./gradlew ]]; then
  ./gradlew clean build --no-daemon
elif command -v gradle >/dev/null; then
  gradle clean build --no-daemon
else
  echo 'ERROR: Gradle is not installed and Gradle Wrapper is missing.' >&2
  echo 'Install Gradle 8.8 or restore the official Forge MDK wrapper.' >&2
  exit 1
fi
printf 'Build complete: see build/libs/\n'
