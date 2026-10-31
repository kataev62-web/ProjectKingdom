#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mapfile -t SOURCES < <(find "$ROOT/validation/identity_names_harness/src" -name '*.java')
java com.sun.tools.javac.Main --release 17 -d "$TMP" "${SOURCES[@]}" \
  "$ROOT/src/main/java/ru/projectkingdom/npc/KingdomIdentityNames.java"
java -cp "$TMP" ru.projectkingdom.npc.IdentityNamesHarness
