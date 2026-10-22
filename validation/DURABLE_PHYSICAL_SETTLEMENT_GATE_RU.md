# Физическая торговля: долговечный шлюз подтверждения (16.10.2026)

Добавлен DemandDurableSettlementGate: до изменения тестовых инвентарей записывает PREPARED на диск, после успешного обмена — COMMITTED. После перезапуска COMMITTED не выполняется повторно; PREPARED или повреждённая запись переводятся в NEEDS_REVIEW без автоматического повторения. REJECTED фиксируется отдельно. Проверены повторный вызов после перезапуска, аварийный маркер и отказ.

## Ограничения — критично

Это **не завершённая игровая механика**. Используется DemandPhysicalSettlementEngine с модельными слотами, не реальными Minecraft ItemStack/NBT. Не реализованы интеграция с Forge, сериализация/восстановление реальных контейнеров, согласованное подтверждение доски потребностей, корректное физическое разменивание номиналов, многопроцессная блокировка и автоматическое восстановление. PREPARED требует ручной сверки. Этот шлюз нельзя напрямую включать в игровые продажи.

Команды проверки: `javac -d /mnt/data/kingdom_compile src/main/java/ru/projectkingdom/npc/{DemandPhysicalSettlementEngine,DemandDurableSettlementGate}.java validation/{DemandPhysicalSettlementEngineTest,DemandDurableSettlementGateTest}.java`; затем `java -cp /mnt/data/kingdom_compile DemandDurableSettlementGateTest`.
