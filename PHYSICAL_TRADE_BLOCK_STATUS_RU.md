# Физическая торговля — незавершённый крупный блок

16.10.2026. Добавлен DemandPhysicalSettlementEngine — эталонная слотная Java-модель сделки между четырьмя инвентарями. Проверены передача товара, точная передача существующих физических номиналов, вместимость, отсутствие подходящего набора монет, дублирование ID и переполнение стоимости. Тест: validation/DemandPhysicalSettlementEngineTest.java.

НЕ ГОТОВО: интеграция с Forge ItemStack/Capability и TFC NBT, реальные блок-сущности кассы/склада/кошелька, durable transaction/receipt, восстановление после падения между четырьмя записями, GUI приёмки, Forge сборка и игровой тест. Java-модель не является игровой реализацией. Доска KingdomDemandBoardBlock пока read-only.

Запуск теста:
javac -d /tmp/kingdom-classes src/main/java/ru/projectkingdom/npc/DemandPhysicalSettlementEngine.java validation/DemandPhysicalSettlementEngineTest.java
java -cp /tmp/kingdom-classes DemandPhysicalSettlementEngineTest
