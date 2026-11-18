#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BASE="$ROOT/src/main/java/ru/projectkingdom/npc"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# The authority is pure Java: no Minecraft classes, no Forge, no network.
javac --release 17 -encoding UTF-8 -d "$TMP" \
 "$BASE/SettlementJournal.java" \
 "$BASE/SettlementAuthority.java" \
 "$BASE/DemandPhysicalSettlementEngine.java" \
 "$BASE/EnterpriseStockPolicy.java" \
 "$BASE/EnterpriseDemandBoard.java" \
 "$BASE/DurableDemandBoardCatalog.java" \
 "$BASE/DemandDeliveryCoordinator.java" \
 "$BASE/DemandSettlementDeliveryAdapter.java" \
 $(find "$ROOT/validation/server_authority_harness/src" -name '*.java')

java -cp "$TMP" ru.projectkingdom.npc.SettlementAuthorityHarness
