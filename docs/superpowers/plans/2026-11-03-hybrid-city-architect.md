# Standalone Hybrid City Architect Add-on Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Выпустить градостроителя отдельным Forge-модом `kingdom_city_architect`, который зависит от Project Kingdom, но собирается и устанавливается отдельным JAR.

**Architecture:** Изолированный source set в `city-architect-addon/` компилируется против публичных классов основного мода, но не попадает в `kingdomnpc-*.jar`. Дополнение самостоятельно реализует чат-команды, планирование, SavedData, шерстяную разметку, подтверждение, сетевой экран и постепенную стройку. Гибридный слой выбирает только серверный candidate id; при отсутствии модели используется детерминированный рейтинг.

**Tech Stack:** Java 17, Minecraft 1.20.1, Forge 47.3.0, SavedData/NBT, SimpleChannel, JUnit 5, Gradle 8.8.

**Spec:** `docs/superpowers/specs/2026-11-03-hybrid-city-architect-design.md`

## Global Constraints

- Результат — отдельный `kingdom-city-architect-0.1.0-ai1.jar`; основной `kingdomnpc-*.jar` не содержит классов дополнения.
- Mod id: `kingdom_city_architect`; обязательная зависимость `kingdomnpc`; side `BOTH`.
- Не изменять production-файлы под `src/main/java`; допустимы только `build.gradle`, workflow/verification и публичное чтение уже существующих API.
- Режим доступен владельцу помощника с permission level 4 и строит во всех загруженных чанках только внутри подтверждённого плана начального города.
- Не заменять контейнеры, block entity, неразрушаемые блоки и блоки в незагруженных чанках.
- Модель не создаёт координаты, команды или блоки; она выбирает только один id из серверного allow-list.
- Максимумы: радиус 64/96, 6 этапов, 16 зданий, 8 192 маркера, 65 536 дорожных узлов, 16 изменений мира за тик.
- Каждый этап: расчёт → шерсть → подтверждение → стройка; следующий этап требует отдельного подтверждения.
- Все новые поведения разрабатываются RED→GREEN и проверяются отдельно от тестов основного мода.

## Review Focus

- JAR isolation: addon-классы отсутствуют в основном JAR, а `mods.toml` дополнения требует `kingdomnpc` — Task 1.
- Missing/unloaded surface cell закрывает маршрут, а не становится ровной землёй — Task 2.
- Неизвестный candidate id от модели включает безопасный fallback — Task 3.
- Перезапуск и изменённая игроком шерсть не теряют before-image и не перезаписывают конфликт — Tasks 4–5.
- Отсутствующая/неоднозначная ратуша или кузница не изменяет мир — Task 6.

---

## File Map

### Build boundary

- Modify: `build.gradle` — isolated `cityArchitect` source set, test source set and standalone JAR task.
- Create: `city-architect-addon/src/main/resources/META-INF/mods.toml`
- Create: `city-architect-addon/src/main/resources/pack.mcmeta`
- Create: `city-architect-addon/src/main/java/ru/projectkingdom/cityarchitect/KingdomCityArchitectMod.java`

### Add-on production packages

- `.../command/CityArchitectCommandParser.java`
- `.../plan/CityArchitectRoadPlanner.java`
- `.../plan/CityArchitectPlanner.java`
- `.../plan/CityArchitectReasoner.java`
- `.../data/CityArchitectPlanData.java`
- `.../world/CityArchitectMarkerService.java`
- `.../world/CityArchitectBuildService.java`
- `.../world/CityArchitectRoadDesignFactory.java`
- `.../world/CityArchitectLandmarks.java`
- `.../CityArchitectCoordinator.java`
- `.../network/CityArchitectNetwork.java`
- `.../network/CityArchitectPreviewPacket.java`
- `.../network/CityArchitectDecisionPacket.java`
- `.../client/CityArchitectPreviewScreen.java`

### Add-on tests

- All tests live under `city-architect-addon/src/test/java/ru/projectkingdom/cityarchitect/`.
- Existing `src/test/java` remains unchanged except verification that the base JAR contains no add-on classes.

---

### Task 1: Отдельный Forge JAR и граница зависимости

**Files:**
- Modify: `build.gradle`
- Create: `city-architect-addon/src/main/resources/META-INF/mods.toml`
- Create: `city-architect-addon/src/main/resources/pack.mcmeta`
- Create: `city-architect-addon/src/main/java/ru/projectkingdom/cityarchitect/KingdomCityArchitectMod.java`
- Create: `city-architect-addon/src/test/java/ru/projectkingdom/cityarchitect/AddonPackagingTest.java`

**Interfaces:**
- Gradle tasks: `compileCityArchitectJava`, `cityArchitectTest`, `cityArchitectJar`.
- Artifact: `build/libs/kingdom-city-architect-0.1.0-ai1.jar`.
- `KingdomCityArchitectMod.MOD_ID = "kingdom_city_architect"`.

- [ ] Write packaging tests asserting distinct mod id, mandatory `kingdomnpc` dependency, BOTH side and no `ru/projectkingdom/cityarchitect/` entries in the base JAR.
- [ ] Run packaging test and verify RED because the source set/JAR does not exist.
- [ ] Add isolated source/test sets whose compile classpaths include `sourceSets.main.output` and main Forge dependencies; base `jar` keeps only main output.
- [ ] Register the add-on `@Mod` entrypoint and resource metadata.
- [ ] Run `cityArchitectTest`, `jar`, and `cityArchitectJar`; verify both JARs exist and packaging assertions pass.
- [ ] Commit: `git commit -m "build(city-architect): add standalone addon jar"`.

---

### Task 2: Команды и ограниченный дорожный планировщик

**Files:**
- Create: `.../command/CityArchitectIntent.java`
- Create: `.../command/CityArchitectCommand.java`
- Create: `.../command/CityArchitectCommandParser.java`
- Create: `.../plan/CityArchitectRoadPlanner.java`
- Create tests: `CityArchitectCommandParserTest.java`, `CityArchitectRoadPlannerTest.java`

**Interfaces:**
- Intents: `ROAD`, `CITY_PLAN`, `NEXT_STAGE`, `REPLAN`, `CONFIRM`, `CANCEL_MARKERS`, `STATUS`.
- `RoadRequest(start, goal, cells, maxNodes)` → at most three stable `RoadCandidate` values.
- `SurfaceCell(position, water, blocked, clearCost)`; missing cells are impassable.

- [ ] Write parser tests for all approved Russian phrases and rejection of ordinary chat.
- [ ] Write road tests for flat path, water detour, slope >1, blocked/missing cells, stable ordering and `maxNodes=32` failure.
- [ ] Run both tests and verify RED.
- [ ] Implement strict parser plus bounded four-neighbor A* using Manhattan heuristic and penalties for elevation, water, clearing and turns.
- [ ] Re-run focused tests and full `cityArchitectTest`; verify GREEN.
- [ ] Commit: `git commit -m "feat(city-architect): parse commands and plan roads"`.

---

### Task 3: Гибридное рассуждение с allow-list

**Files:**
- Create: `.../plan/CityArchitectModelClient.java`
- Create: `.../plan/DisabledCityArchitectModelClient.java`
- Create: `.../plan/CityArchitectReasoner.java`
- Create test: `CityArchitectReasonerTest.java`

**Interfaces:**
- `CandidateSummary(id, kind, score, facts)`.
- Model response may contain only `candidateId` and `explanation`.
- `Choice(candidateId, explanation, modelUsed)`.

- [ ] Write tests for accepted known id, rejected unknown id/raw coordinates/commands, disabled client, exception, timeout/malformed output and deterministic tie by id.
- [ ] Run focused test and verify RED.
- [ ] Implement asynchronous optional client seam and validator; fallback selects lowest score and produces metric-based Russian explanation.
- [ ] Run focused and full add-on tests; verify GREEN.
- [ ] Commit: `git commit -m "feat(city-architect): add allow-listed hybrid reasoning"`.

---

### Task 4: SavedData городского плана

**Files:**
- Create: `.../data/CityArchitectPlanData.java`
- Create test: `CityArchitectPlanDataTest.java`

**Interfaces:**
- States: `PlanStatus`, `StageStatus`, `StageKind`, `MarkerKind`.
- Records: `BuildingPlacement`, `RoadPlacement`, `CompletedPlacement`, `MarkerCell`, `MarkerChange`, `StageView`, `PlanView`.
- Methods: `create`, `ownedBy`, `marking`, `approve`, `building`, `completeStage`, `failStage`, `cancel`, `recordMarkerChange`, `recordCompletedPlacement`.
- Data key: `kingdom_city_architect_plans_v1`; one active plan per owner.

- [ ] Write legal/illegal transition, one-active-plan, complete NBT round-trip, corrupt-row quarantine, limits and partial-marker restart tests.
- [ ] Run focused test and verify RED.
- [ ] Implement bounded defensive SavedData with candidate explanations, stage cursors, placements and before-images.
- [ ] Run focused and full tests; verify GREEN.
- [ ] Commit: `git commit -m "feat(city-architect): persist staged plans"`.

---

### Task 5: Восстанавливаемая шерстяная разметка

**Files:**
- Create: `.../world/CityArchitectMarkerService.java`
- Create test: `CityArchitectMarkerServiceTest.java`

**Interfaces:**
- `WorldAccess` provides loaded, inspect, safeReplaceable and exact set.
- Methods: `queue(planId, cells)`, `tick(budget)`, `restore(planId)`, `resume(planId)`.
- Fixed palette: yellow road, red administration, orange production, green commerce/storage, light blue housing, blue water/port.

- [ ] Write tests for 16 changes/tick, air-above-surface, unsafe target refusal, unloaded pause, exact restore, player edit conflict, idempotent restart and 8 192 cap.
- [ ] Run focused test and verify RED.
- [ ] Implement before-image journaling before every write and conflict-safe restore.
- [ ] Run focused and full tests; verify GREEN.
- [ ] Commit: `git commit -m "feat(city-architect): add recoverable wool markers"`.

---

### Task 6: Якоря Project Kingdom и дорожный сценарий

**Files:**
- Create: `.../world/CityArchitectLandmarks.java`
- Create: `.../CityArchitectCoordinator.java`
- Create tests: `CityArchitectLandmarksTest.java`, `CityArchitectCoordinatorTest.java`

**Interfaces:**
- Read-only adapters consume public `KingdomAssistantData`, `KingdomAdministrationData`, `KingdomEnterpriseData`, `KingdomConstructionData`, `KingdomConstructionPlans`.
- Landmark result: `FOUND`, `MISSING`, `AMBIGUOUS` with bounded matches.
- `requestRoad(ServerPlayer, String)` mutates plan/markers only after unique anchors and permission level 4.

- [ ] Write tests for unique rat hall/smithy, missing/ambiguous/wrong dimension, no mutation on failure and marker start after accepted candidate.
- [ ] Run focused tests and verify RED.
- [ ] Implement normalized Russian landmark lookup and immutable surface snapshot on the server thread.
- [ ] Implement road request orchestration and readable Russian explanations.
- [ ] Run focused and full tests; verify GREEN.
- [ ] Commit: `git commit -m "feat(city-architect): stage roads between kingdom landmarks"`.

---

### Task 7: Собственное подтверждение и постепенная стройка

**Files:**
- Create: `.../network/CityArchitectNetwork.java`
- Create: `.../network/CityArchitectPreviewPacket.java`
- Create: `.../network/CityArchitectDecisionPacket.java`
- Create: `.../client/CityArchitectPreviewScreen.java`
- Create: `.../world/CityArchitectRoadDesignFactory.java`
- Create: `.../world/CityArchitectBuildService.java`
- Create tests: `CityArchitectProtocolTest.java`, `CityArchitectRoadDesignFactoryTest.java`, `CityArchitectBuildServiceTest.java`

**Interfaces:**
- Server session binds token, owner, assistant, dimension, plan id, stage id and expiry.
- Decision packet contains token plus confirm/cancel only; no blocks or coordinates.
- Build queue persists embedded cells and before-images inside add-on data; maximum 16 changes/tick.
- Road factory produces a three-block-wide varying-Y design capped at 8 192 cells.

- [ ] Write packet bound/round-trip/forgery tests, road width/corner/height tests and build safety/rollback/restart tests.
- [ ] Run focused tests and verify RED.
- [ ] Implement separate SimpleChannel and green-check/red-cross screen.
- [ ] Implement marker restore followed by confirmed road queue; allow all chunks only for the bound admin plan while retaining world safety checks.
- [ ] Run focused and full tests; verify GREEN.
- [ ] Commit: `git commit -m "feat(city-architect): confirm and build approved roads"`.

---

### Task 8: Поэтапный генеральный план города

**Files:**
- Create: `.../plan/CityArchitectPlanner.java`
- Extend: `.../CityArchitectCoordinator.java`
- Extend tests: `CityArchitectPlannerTest.java`, `CityArchitectCoordinatorTest.java`

**Interfaces:**
- `CityPlanningRequest` carries rat hall, immutable surface, existing outlines and resolved blueprint footprints.
- `CityCandidate` contains up to six `CityStage` values with building/road placements and metrics.
- Commands: request plan, mark/replan/confirm/cancel/status/next stage.

- [ ] Write tests for fixed rat hall anchor, no overlaps, three-block circulation, commerce near square, production away from housing, surveyed bounds, missing blueprint finding and stable result.
- [ ] Write state-flow test: plan → wool → confirm → sequential object builds → COMPLETE → explicit next stage.
- [ ] Run focused tests and verify RED.
- [ ] Implement axis/square generation then bounded placement of administration, production, tavern/housing and optional expansions using only real server schematics.
- [ ] Project completed placements into the add-on's own landmark registry for later stages and roads.
- [ ] Run focused and full tests; verify GREEN.
- [ ] Commit: `git commit -m "feat(city-architect): execute staged city layouts"`.

---

### Task 9: Forge events, complete verification and delivery

**Files:**
- Extend: `KingdomCityArchitectMod.java`, `CityArchitectCoordinator.java`
- Modify: `build.gradle`, `.github/workflows/build.yml`, `03_verify_project.py` only as required for add-on build verification.
- Update: approved spec with implemented-status notes.

**Interfaces:**
- Add-on handles addressed city-architect chat commands, server END tick, player disconnect/session expiry and client setup without modifying base handlers.
- Build outputs both base JAR and separate add-on JAR.

- [ ] Add integration tests proving ordinary assistant commands still reach base behavior and handled add-on commands are not executed twice.
- [ ] Wire Forge chat/tick/client lifecycle events in the add-on entrypoint.
- [ ] Run `./gradlew test cityArchitectTest build cityArchitectJar --no-daemon` and `python3 03_verify_project.py`; require zero failures.
- [ ] Inspect both JARs: correct separate `mods.toml`, no add-on classes in base JAR, no base classes bundled into add-on JAR.
- [ ] Manual smoke: request/cancel/re-request/confirm road; request plan; confirm stage 1; verify stage 2 waits; restart during markers and build.
- [ ] Commit: `git commit -m "test(city-architect): verify standalone addon"`.
