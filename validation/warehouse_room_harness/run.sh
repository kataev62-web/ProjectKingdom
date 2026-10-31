#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mapfile -t SOURCES < <(find "$ROOT/validation/warehouse_room_harness/src" -name '*.java')
java com.sun.tools.javac.Main --release 17 -d "$TMP" "${SOURCES[@]}" \
  "$ROOT/src/main/java/ru/projectkingdom/administration/KingdomCentralWarehouseRooms.java"
RESULT=0
for CASE in floor diagonal retry height no_storage rights dimension upper_limit; do
  java -cp "$TMP" ru.projectkingdom.administration.WarehouseRoomHarness "$CASE" || RESULT=1
done
exit "$RESULT"
