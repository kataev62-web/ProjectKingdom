#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BASE="$ROOT/src/main/java/ru/projectkingdom/npc"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac --release 17 -d "$TMP" $(find "$ROOT/validation/stage1_harness/src" -name '*.java') \
 "$BASE/TradeAtomicStore.java" \
 "$BASE/TradeAtomicEconomy.java" \
 "$BASE/TradePhysicalReconciliation.java" \
 "$BASE/TradeRecoveryPolicy.java" \
 "$BASE/KingdomEnterpriseData.java" \
 "$BASE/TradeWriteAheadJournal.java" \
 "$BASE/TradeCheckpointLedger.java" \
 "$BASE/TradeCheckpointGate.java" \
 "$BASE/KingdomEnterprisePayrollData.java" \
 "$BASE/KingdomNpcWorkshopData.java" \
 "$BASE/KingdomNpcWorkshop.java" \
 "$BASE/KingdomPayrollData.java" \
 "$BASE/KingdomTaxData.java"
java -cp "$TMP" ru.projectkingdom.npc.Stage1LogicHarness

java -cp "$TMP" ru.projectkingdom.npc.Stage1WorkshopHarness
