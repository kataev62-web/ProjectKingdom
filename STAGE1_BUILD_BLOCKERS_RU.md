# Этап №1 — контроль сборки (не релиз)

Дата: 2026-10-16.

- Исправлен локальный `validation/stage1_harness/run.sh`: добавлены реально используемые Java-зависимости `TradeAtomicStore`, `TradeAtomicEconomy`, `TradePhysicalReconciliation`, `TradeRecoveryPolicy`; тестовый `TradeMinecraftContainerView` изолирован заглушкой **только внутри harness** (не входит в исходники мода).
- `bash validation/stage1_harness/run.sh`: PASS для экономики/зарплат/налогов и NPC-производства.
- `python3 03_verify_project.py`: статическая проверка структуры PASS.
- Блокер итоговой сборки: в проекте отсутствует Gradle Wrapper (`gradlew`, `gradle/wrapper/gradle-wrapper.jar`), системный Gradle также не установлен. Требуется Gradle и зависимости ForgeGradle/Forge 1.20.1 для реальной компиляции.
- Это не подтверждает сборку Forge или работоспособность GUI в Minecraft. Этап №1 пока не завершён.

## Следующая попытка сборки
- Добавлены `01_build_linux.sh` и обновлён `01_build_windows.bat`: проверка JDK 17, использование существующего wrapper или установленного Gradle, остановка без ложного успеха.
- Здесь доступна только Java 21, Gradle и Gradle Wrapper отсутствуют; внешние DNS-запросы не работают. Forge-сборка объективно не выполнена.
- Для компиляции требуется JDK 17 + Gradle 8.8 (или официальный wrapper Forge MDK) + сетевой доступ к Maven Forge/Gradle.
- Тестовые заглушки в `validation/stage1_harness` не являются доказательством корректности Forge API.
