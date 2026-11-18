#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BASE="$ROOT/src/main/java/ru/projectkingdom/npc"
TOOL="$ROOT/validation/settlement_delivery_crash/src"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# The delivery path is pure Java: no Minecraft classes, no Forge, no network.
javac --release 17 -encoding UTF-8 -d "$TMP" \
 "$BASE/SettlementJournal.java" \
 "$BASE/SettlementAuthority.java" \
 "$BASE/DemandPhysicalSettlementEngine.java" \
 "$BASE/EnterpriseStockPolicy.java" \
 "$BASE/EnterpriseDemandBoard.java" \
 "$BASE/DurableDemandBoardCatalog.java" \
 "$BASE/DemandDeliveryCoordinator.java" \
 "$BASE/DemandSettlementDeliveryAdapter.java" \
 $(find "$TOOL" -name '*.java')

# Every boundary of the durable log, plus the point where the world itself was
# changed but the process died before the next step.
POINTS=(
  "prepare.before"
  "journal.before_write:1"
  "journal.after_write:1"
  "journal.after_force:1"
  "commit.before"
  "journal.before_write:2"
  "journal.after_write:2"
  "journal.after_force:2"
  "project.after_goods"
  "project.after_coins"
  "receipt.before"
  "world.after_write"
)

COUNT=0
for entry in "${POINTS[@]}"; do
  POINT="${entry%%:*}"
  OCCURRENCE="1"
  if [[ "$entry" == *:* ]]; then OCCURRENCE="${entry##*:}"; fi
  DIR="$TMP/crash-$COUNT-$POINT"
  mkdir -p "$DIR"

  # The child dies at the boundary: no shutdown hook, no flush.
  set +e
  java -cp "$TMP" ru.projectkingdom.npc.SettlementCrashTool child "$DIR" "$POINT:$OCCURRENCE" >/dev/null 2>&1
  CODE=$?
  set -e
  if [[ "$CODE" != "137" ]]; then
    echo "FAIL $POINT: the process must die at the boundary, exit was $CODE" >&2
    exit 1
  fi

  # The restart: a second process recovers the log and checks the invariants.
  java -cp "$TMP" ru.projectkingdom.npc.SettlementCrashTool parent "$DIR" "$POINT"
  COUNT=$((COUNT+1))
done

echo "PASS settlement delivery across a real crash: $COUNT boundaries, no creation, no loss, no double debit"
