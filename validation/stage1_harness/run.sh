#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# The old isolated javac stubs model virtual wallets and predate enterprise
# cores/physical cash. Compile the real production classes and run the current
# economy/workshop regression suite, using the same Forge classpath as CI.
exec "$ROOT/gradlew" -p "$ROOT" test --no-daemon \
  --tests 'ru.projectkingdom.npc.Trade*Test' \
  --tests 'ru.projectkingdom.npc.KingdomEnterprise*Test' \
  --tests 'ru.projectkingdom.npc.KingdomNpcWorkshop*Test' \
  --tests 'ru.projectkingdom.npc.KingdomPayroll*Test' \
  --tests 'ru.projectkingdom.npc.KingdomTax*Test' \
  --tests 'ru.projectkingdom.npc.ResidentPhysicalPurchaseTest'
