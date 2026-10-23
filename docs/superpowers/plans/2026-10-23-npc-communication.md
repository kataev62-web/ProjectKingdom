# NPC Communication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add deterministic, state-aware Russian NPC conversations with addressed chat, nearby subtitles/sound, saved relationships, and a dialogue-first fallback menu.

**Architecture:** Pure Java classes classify speech, resolve addressed NPCs, and update social memory. A Forge-facing service validates range and performs server-side effects. Existing NPC menus and command/event wiring expose the subsystem without allowing dialogue to mutate protected economy state.

**Tech Stack:** Java 17, Minecraft Forge 1.20.1, JUnit 5, Forge SavedData/NBT.

**Spec:** `docs/superpowers/specs/2026-10-23-npc-communication-design.md`

## Global Constraints

- No external speech, AI, or MineColonies dependency.
- Conversation range is 8 blocks; listener range is 16 blocks.
- Dialogue cannot create items/money or bypass existing authorization.
- Existing saves receive neutral social memory.
- Russian text must remain mixed-case and readable.

## Review Focus

- Empty and punctuation-only phrases return a safe fallback without throwing.
- Duplicate names resolve to the nearest live NPC in the same dimension.
- A remote player cannot trigger conversation or material actions.
- Bread is consumed exactly once and only then changes need/reputation.
- Old NBT without social memory loads unchanged.

---

### Task 1: Pure dialogue domain

**Files:**
- Create: `src/main/java/ru/projectkingdom/npc/NpcDialogueEngine.java`
- Create: `src/main/java/ru/projectkingdom/npc/NpcAddressing.java`
- Create: `src/main/java/ru/projectkingdom/npc/NpcSocialMemory.java`
- Test: `src/test/java/ru/projectkingdom/npc/NpcDialogueEngineTest.java`
- Test: `src/test/java/ru/projectkingdom/npc/NpcAddressingTest.java`
- Test: `src/test/java/ru/projectkingdom/npc/NpcSocialMemoryTest.java`

**Interfaces:**
- Produces: `NpcDialogueEngine.reply(String, Snapshot)`, `NpcAddressing.match(String, List<Candidate>)`, and immutable `NpcSocialMemory` transitions.

- [x] Write behavior tests for Russian intent priority, state-aware replies, safe unknown fallback, nearest-name addressing, numeric addressing, and bounded reputation.
- [x] Run focused tests and confirm they fail because the classes do not exist.
- [x] Implement the minimal pure classes.
- [x] Run focused tests; the complete Gradle suite is reserved for the final CI gate because the local runtime cannot download Gradle.

### Task 2: Persistent relationship memory

**Files:**
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomNpcData.java`
- Test: `src/test/java/ru/projectkingdom/npc/NpcCommunicationPersistenceContractTest.java`

**Interfaces:**
- Consumes: `NpcSocialMemory` from Task 1.
- Produces: `Worker.social(UUID)`, `Worker.remember(UUID, NpcSocialMemory)`, and backward-compatible NBT list `SocialMemory`.

- [x] Write the persistence contract test first and watch it fail.
- [x] Add the relation map and bounded load/save logic without changing old defaults.
- [x] Run focused tests; complete Forge suite remains reserved for final CI.

### Task 3: Server dialogue service and entry points

**Files:**
- Create: `src/main/java/ru/projectkingdom/npc/NpcDialogueService.java`
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java`
- Test: `src/test/java/ru/projectkingdom/npc/NpcCommunicationWiringTest.java`

**Interfaces:**
- Consumes: Tasks 1-2 and existing `KingdomNpcData`, `KingdomWarehouseMarket`, and inventory APIs.
- Produces: validated `respond`, addressed-chat handling, `/knpc talk`, nearby subtitle/sound broadcast, and inventory-backed bread help.

- [x] Write source/wiring and pure action-policy tests first and watch them fail.
- [x] Implement range checks, lookup, response broadcast, command, and addressed-chat interception.
- [x] Run focused tests; complete Forge suite remains reserved for final CI.

### Task 4: Dialogue-first NPC menu

**Files:**
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomNpcMenu.java`
- Test: `src/test/java/ru/projectkingdom/npc/NpcDialogueMenuContractTest.java`

**Interfaces:**
- Consumes: `NpcDialogueService` from Task 3.
- Produces: default dialogue page, topic buttons, real help action, relationship view, and route to unchanged management page.

- [x] Write menu contract test first and watch it fail.
- [x] Add page switching and topic actions while preserving all management controls.
- [x] Run focused tests; complete Forge suite remains reserved for final CI.

### Task 5: Build, review, and distributable

**Files:**
- Modify: `src/main/resources/assets/kingdomnpc/lang/ru_ru.json` only if new translation keys are introduced.
- Create: `NPC_COMMUNICATION_STATUS_RU.md`
- Update: `dist/kingdomnpc-0.1.0-stage0-dev.jar`

**Interfaces:**
- Consumes: completed Tasks 1-4.
- Produces: verified Forge JAR and Russian test instructions.

- [ ] Run `./gradlew test build --no-daemon` in CI-capable environment.
- [ ] Inspect JAR contents and hashes.
- [ ] Perform a fresh whole-change review and one TDD fix pass for Critical/Important findings.
- [ ] Publish the single integrated JAR only after green verification.


