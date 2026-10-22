# Administrative City Blocks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add nine secure city-owned administrative blocks with physical money and storage, role-based access, migration-safe persistence, and MineColonies-style Russian book menus.

**Architecture:** A new `administration` module provides one shared lifecycle, permission, persistence, menu, and binding foundation parameterized by `AdministrativeKind`. Existing civics, bank, warehouse, market, land, and economy classes remain authoritative and are accessed through small adapters rather than duplicated. Justice, prison, and guard registries are durable foundations only; their NPC automation remains deferred to Stage 9.

**Tech Stack:** Java 17, Minecraft 1.20.1, Forge 47.3.0, Forge `SavedData`, menus/screens/network packets, JUnit 5, Gradle, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-22-administrative-blocks-design.md`

## Global Constraints

- Exactly nine administrative kinds: town hall, treasury, bank, central warehouse, city market, land office, court, prison, and guard administration.
- Exactly one active core of each kind per town; future branches and outposts are outside this plan.
- Administrative cores must be placed on the exact town's claimed land and all mutations are server-authoritative.
- Founder keeps permanent highest rights; migration assigns the current mayor as founder.
- Filled purses and physical coins remain the only cash source; bank accounts never become an automatic payment rail.
- Bank reserve and town treasury reserve are independent; no unbacked withdrawal is allowed.
- Storage and market inventory reads are limited to loaded physical containers; no chunk tickets or forced loads.
- Court, prison, and guard automation is not simulated before Stage 9.
- All user-facing text is Russian and all new book-style screens use the scoped `kingdomnpc:readable` font.
- Existing world data must migrate idempotently without deleting balances, plots, orders, stalls, debts, or registrations.

## Review Focus

- Replacing a lost bound core must fail while the old core is loaded and active, but succeed after the server proves the old location is absent.
- A removed mayor who is still founder must retain founder rights, while a replacement mayor must not gain founder-only operations.
- A withdrawal with sufficient account balance but insufficient physical reserve must leave both values unchanged.
- A container that unloads between menu display and confirmation must make the operation fail atomically without loading its chunk.
- Replaying, spoofing, or sending an action packet for a different core than the currently open menu must be rejected.

---

## File Structure

New production files live in `src/main/java/ru/projectkingdom/administration/`:

- `AdministrativeKind.java` — nine stable identifiers and Russian labels.
- `MunicipalRole.java` — founder-independent appointed roles and permission mapping.
- `AdministrativeCoreBinding.java` — immutable town/type/dimension/position binding.
- `AdministrativePermissionService.java` — pure authorization decisions.
- `KingdomAdministrationData.java` — SavedData for founders, appointments, core bindings, logs, reserves, container bindings, and Stage 9 registries.
- `AdministrativeCoreItemData.java` — bound item NBT encode/decode.
- `AdministrativeCoreBlock.java` — common placement, use, and removal behavior.
- `AdministrativeCoreBlockEntity.java` — menu provider and core identity.
- `KingdomAdministrationRegistry.java` — blocks, items, block entity, and menu registration.
- `AdministrativeMenuLayout.java` — pure tab definitions by kind.
- `AdministrativeCoreMenu.java` — server menu context and validity.
- `AdministrativeCoreScreen.java` — shared book renderer.
- `AdministrativeBindingRole.java` and `AdministrativeBindingSession.java` — physical container selection.
- `AdministrativeAccessService.java` — loaded-only core/container checks and binding completion.
- `AdministrativeActionPacket.java` — validated actions for roles, deposits, withdrawals, and bindings.
- `AdministrativeTextPacket.java` — validated bounded text changes.
- `MunicipalLedger.java` — atomic physical reserve/account transitions with no Minecraft dependencies.

Existing files changed only through focused integration:

- `npc/KingdomNpcMod.java` — register the module and route binding interactions.
- `npc/KingdomCivicsData.java` — expose stable town/plot queries and backed treasury mutations.
- `npc/KingdomBankData.java` — open accounts through an active city bank and enforce local physical reserve.
- `npc/KingdomWarehouseMarket.java` — require active city market/warehouse cores for administrative counters.
- `network/KingdomNetwork.java` — register two administration packet types.
- `core/KingdomFonts.java` — reused without global font replacement.

Tests live in `src/test/java/ru/projectkingdom/administration/`, plus resource checks. Generated assets live under the existing `src/main/resources/assets/kingdomnpc/` and `data/kingdomnpc/recipes/` trees.

### Task 1: Administrative Domain and Permission Matrix

**Files:**
- Create: `src/main/java/ru/projectkingdom/administration/AdministrativeKind.java`
- Create: `src/main/java/ru/projectkingdom/administration/MunicipalRole.java`
- Create: `src/main/java/ru/projectkingdom/administration/AdministrativeCoreBinding.java`
- Create: `src/main/java/ru/projectkingdom/administration/AdministrativePermissionService.java`
- Test: `src/test/java/ru/projectkingdom/administration/AdministrativeDomainTest.java`

**Interfaces:**
- Produces: `AdministrativeKind` with IDs `town_hall`, `treasury`, `bank`, `central_warehouse`, `city_market`, `land_office`, `court`, `prison`, `guard_administration`.
- Produces: `MunicipalRole` values `MAYOR`, `TREASURER`, `LAND_MANAGER`, `JUDGE`, `GUARD_CHIEF`, `WAREHOUSE_MANAGER`.
- Produces: `AdministrativePermissionService.mayManage(UUID actor, UUID founder, UUID mayor, Set<MunicipalRole> roles, AdministrativeKind kind)`.

- [ ] **Step 1: Write failing domain tests** asserting the exact nine IDs, founder access to all kinds, mayor access without founder-only authority, each specialist mapping, and no ordinary-citizen management access.
- [ ] **Step 2: Run** `./gradlew test --tests ru.projectkingdom.administration.AdministrativeDomainTest` and expect failure because the domain types do not exist.
- [ ] **Step 3: Implement the four pure domain files** with immutable values and no Forge dependencies.
- [ ] **Step 4: Re-run the focused test** and expect all assertions to pass.
- [ ] **Step 5: Commit** `feat: add administrative domain and permissions`.

### Task 2: Persistent City Administration, Migration, and Logs

**Files:**
- Create: `src/main/java/ru/projectkingdom/administration/KingdomAdministrationData.java`
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomCivicsData.java`
- Test: `src/test/java/ru/projectkingdom/administration/AdministrationPersistenceTest.java`

**Interfaces:**
- Consumes: Task 1 enums and binding.
- Produces: `founder(UUID townId)`, `roles(UUID townId, UUID playerId)`, `appoint(...)`, `revoke(...)`, `core(UUID townId, AdministrativeKind kind)`, `activateCore(...)`, `suspendCore(...)`, `record(...)`, and `entries(UUID townId, int limit)`.
- Produces: migration version `2`; absent founder becomes the town's current mayor exactly once.

- [ ] **Step 1: Write failing persistence tests** for nine unique bindings, duplicate rejection, founder migration, role persistence, bounded log retention, and repeated-load idempotence.
- [ ] **Step 2: Run** `./gradlew test --tests ru.projectkingdom.administration.AdministrationPersistenceTest` and expect failure.
- [ ] **Step 3: Implement SavedData serialization** with explicit version, deterministic load validation, maximum 500 log entries per town, and no data deletion on core suspension.
- [ ] **Step 4: Add the minimal civics queries** `townAt(ServerLevel, BlockPos)` and `isMember(UUID townId, UUID playerId)` without changing plot ownership semantics.
- [ ] **Step 5: Run the focused tests** and expect pass.
- [ ] **Step 6: Commit** `feat: persist city administration and migrate founders`.

### Task 3: Physical Core Blocks and Bound Items

**Files:**
- Create: `src/main/java/ru/projectkingdom/administration/AdministrativeCoreItemData.java`
- Create: `src/main/java/ru/projectkingdom/administration/AdministrativeCoreBlock.java`
- Create: `src/main/java/ru/projectkingdom/administration/AdministrativeCoreBlockEntity.java`
- Create: `src/main/java/ru/projectkingdom/administration/KingdomAdministrationRegistry.java`
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java`
- Test: `src/test/java/ru/projectkingdom/administration/AdministrationRegistryContractTest.java`
- Test: `src/test/java/ru/projectkingdom/administration/AdministrativeCoreLifecycleTest.java`

**Interfaces:**
- Consumes: Task 2 core lifecycle API.
- Produces: nine block/item registry IDs matching Task 1 plus one `administrative_core` block entity type.
- Produces: item keys `KingdomAdministrativeTown`, `KingdomAdministrativeKind`, `KingdomAdministrativeOwner`.

- [ ] **Step 1: Write failing registry and lifecycle tests** for all IDs, town-land placement, town-hall-first ordering, duplicate active core denial, foreign bound item denial, owner-preserving drops, and safe replacement of a proven-missing core.
- [ ] **Step 2: Run both focused test classes** and expect failure.
- [ ] **Step 3: Implement bound item NBT and common block lifecycle** using `level.hasChunkAt` before inspecting the previous location; never force-load it.
- [ ] **Step 4: Register blocks, items, block entity, and module startup** in `KingdomNpcMod`.
- [ ] **Step 5: Run the two focused tests** and expect pass.
- [ ] **Step 6: Commit** `feat: add nine administrative core blocks`.

### Task 4: Menu Layout, Shared Container, and Book Screen

**Files:**
- Create: `src/main/java/ru/projectkingdom/administration/AdministrativeMenuLayout.java`
- Create: `src/main/java/ru/projectkingdom/administration/AdministrativeCoreMenu.java`
- Create: `src/main/java/ru/projectkingdom/administration/AdministrativeCoreScreen.java`
- Modify: `src/main/java/ru/projectkingdom/administration/KingdomAdministrationRegistry.java`
- Test: `src/test/java/ru/projectkingdom/administration/AdministrativeMenuLayoutTest.java`
- Test: `src/test/java/ru/projectkingdom/administration/AdministrativeMenuContractTest.java`

**Interfaces:**
- Consumes: Tasks 1-3 kind, permission, and active-core lookup.
- Produces: `tabs(AdministrativeKind kind)` and stable action IDs grouped by service.
- Produces: menu context `(townId, kind, dimension, pos)` and `stillValid(Player)` requiring the exact active loaded core within interaction distance.

- [ ] **Step 1: Write failing menu tests** for each kind's tabs, public versus management visibility, mismatched-core rejection, unloaded-core rejection, and scoped font resource use.
- [ ] **Step 2: Run the focused tests** and expect failure.
- [ ] **Step 3: Implement the common menu and parchment screen** reusing `KingdomFonts.readable`; do not install a global font provider.
- [ ] **Step 4: Register the menu and client screen binding** through the existing Forge client setup pattern.
- [ ] **Step 5: Run the focused tests** and expect pass.
- [ ] **Step 6: Commit** `feat: add administrative book menus`.

### Task 5: Secure Network Actions and Physical Binding Sessions

**Files:**
- Create: `src/main/java/ru/projectkingdom/administration/AdministrativeBindingRole.java`
- Create: `src/main/java/ru/projectkingdom/administration/AdministrativeBindingSession.java`
- Create: `src/main/java/ru/projectkingdom/administration/AdministrativeAccessService.java`
- Create: `src/main/java/ru/projectkingdom/administration/AdministrativeActionPacket.java`
- Create: `src/main/java/ru/projectkingdom/administration/AdministrativeTextPacket.java`
- Modify: `src/main/java/ru/projectkingdom/network/KingdomNetwork.java`
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java`
- Test: `src/test/java/ru/projectkingdom/administration/AdministrativeNetworkSecurityTest.java`

**Interfaces:**
- Consumes: exact menu context and Task 2 permissions.
- Produces: binding roles `TREASURY_VAULT`, `BANK_VAULT`, `WAREHOUSE_STORAGE`, `MARKET_STORAGE`, `PRISON_CELL`.
- Produces: `begin(ServerPlayer, UUID townId, AdministrativeKind kind, AdministrativeBindingRole role)`, `complete(...)`, and `cancel(UUID playerId)`.

- [ ] **Step 1: Write failing security tests** for packet replay, mismatched open menu, remote core, changed role, oversized text, formatting/control characters, foreign land, and chunk unloading between begin and complete.
- [ ] **Step 2: Run** `./gradlew test --tests ru.projectkingdom.administration.AdministrativeNetworkSecurityTest` and expect failure.
- [ ] **Step 3: Implement session and packet validation** with maximum text length 48 and server-side permission recheck immediately before mutation.
- [ ] **Step 4: Route right-click binding and logout cancellation** in `KingdomNpcMod` without interfering with enterprise binding sessions.
- [ ] **Step 5: Run the focused test** and expect pass.
- [ ] **Step 6: Commit** `feat: secure administrative actions and bindings`.

### Task 6: Physical Treasury and City Bank Reserves

**Files:**
- Create: `src/main/java/ru/projectkingdom/administration/MunicipalLedger.java`
- Modify: `src/main/java/ru/projectkingdom/administration/KingdomAdministrationData.java`
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomCivicsData.java`
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomBankData.java`
- Test: `src/test/java/ru/projectkingdom/administration/MunicipalLedgerTest.java`
- Test: `src/test/java/ru/projectkingdom/administration/AdministrativeFinanceIntegrationTest.java`

**Interfaces:**
- Produces: pure atomic transitions `deposit`, `withdraw`, `transfer`, and `settleDebt` returning an immutable result without partial mutation.
- Produces: per-town `treasuryReserve`, `bankReserve`, and `historicalTreasuryLiability`.
- Preserves: global player bank account receipts; withdrawal at a city bank requires both player balance and that city's physical bank reserve.

- [ ] **Step 1: Write failing ledger tests** for independent reserves, overflow, negative/zero amounts, insufficient account, insufficient local reserve, rollback, and bank-to-treasury transfer authorization.
- [ ] **Step 2: Run the two focused test classes** and expect failure.
- [ ] **Step 3: Implement `MunicipalLedger`** as a pure value transition layer.
- [ ] **Step 4: Integrate wallet debit/credit and town reserves** so successful deposit changes wallet, account, and exactly one reserve once.
- [ ] **Step 5: Migrate the old numeric town treasury** to `historicalTreasuryLiability` with zero physical backing; preserve existing global bank accounts and vault as a legacy network reserve that cannot be duplicated into town reserves.
- [ ] **Step 6: Add tests for the Review Focus withdrawal case and repeated migration**, then run the focused suite and expect pass.
- [ ] **Step 7: Commit** `feat: back municipal finance with physical reserves`.

### Task 7: Central Warehouse and City Market Adapters

**Files:**
- Modify: `src/main/java/ru/projectkingdom/administration/KingdomAdministrationData.java`
- Modify: `src/main/java/ru/projectkingdom/administration/AdministrativeAccessService.java`
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomWarehouseMarket.java`
- Test: `src/test/java/ru/projectkingdom/administration/AdministrativeStorageMarketTest.java`

**Interfaces:**
- Consumes: Task 5 bindings and active-core checks.
- Produces: `boundContainers(UUID townId, AdministrativeBindingRole role)` and loaded-only inventory snapshots.
- Preserves: existing stalls, orders, escrow, and seller ownership.

- [ ] **Step 1: Write failing tests** for multiple warehouse containers, duplicate binding denial, foreign-town container denial, unload-before-confirmation, inactive core, one city market per town, and unchanged order escrow semantics.
- [ ] **Step 2: Run the focused test** and expect failure.
- [ ] **Step 3: Implement loaded-only container adapters** that call `hasChunkAt` before `getBlockEntity` and never retain a live `Container` across ticks.
- [ ] **Step 4: Require an active warehouse core for municipal stock actions and active market core for city market actions**, while preserving private enterprise stalls.
- [ ] **Step 5: Run the focused test** and expect pass.
- [ ] **Step 6: Commit** `feat: connect city warehouse and market cores`.

### Task 8: Land Office and Role Appointments

**Files:**
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomCivicsData.java`
- Modify: `src/main/java/ru/projectkingdom/administration/AdministrativeActionPacket.java`
- Modify: `src/main/java/ru/projectkingdom/administration/AdministrativeCoreScreen.java`
- Test: `src/test/java/ru/projectkingdom/administration/LandOfficeIntegrationTest.java`

**Interfaces:**
- Consumes: existing sale, lease, debt, plot, and payment APIs.
- Produces: land-office-only administrative mutation routes and town-hall role appointment routes.

- [ ] **Step 1: Write failing tests** for founder/mayor appointments, permanent founder rights after mayor replacement, land-manager authorization, outsider denial, foreign-city ownership denial, and payment-without-contract rollback.
- [ ] **Step 2: Run the focused test** and expect failure.
- [ ] **Step 3: Expose bounded land-office actions** without duplicating offers, contracts, debts, or plot maps.
- [ ] **Step 4: Add town-hall role controls and land-office views** with server-side pagination.
- [ ] **Step 5: Run the focused test** and expect pass.
- [ ] **Step 6: Commit** `feat: connect town roles and land office`.

### Task 9: Court, Prison, and Guard Durable Foundations

**Files:**
- Create: `src/main/java/ru/projectkingdom/administration/JusticeCase.java`
- Create: `src/main/java/ru/projectkingdom/administration/PrisonRecord.java`
- Create: `src/main/java/ru/projectkingdom/administration/GuardRecord.java`
- Modify: `src/main/java/ru/projectkingdom/administration/KingdomAdministrationData.java`
- Modify: `src/main/java/ru/projectkingdom/administration/AdministrativeCoreScreen.java`
- Test: `src/test/java/ru/projectkingdom/administration/JusticeFoundationTest.java`

**Interfaces:**
- Produces: create/read/update operations for cases, cells, sentences, guards, patrol zones, warrants, and wanted entries.
- Enforces: judge writes court decisions; guard chief manages guard/prison registries; no tick-driven AI or automatic arrest.

- [ ] **Step 1: Write failing tests** for role authorization, bounded text, immutable identifiers, persistence, sentence time bounds, duplicate active warrant denial, and explicit absence of automatic player/NPC mutation.
- [ ] **Step 2: Run the focused test** and expect failure.
- [ ] **Step 3: Implement compact immutable records and versioned NBT lists** with safe load validation and pagination.
- [ ] **Step 4: Add read/write menu sections** clearly labelled as administrative records, with Stage 9 automation marked inactive.
- [ ] **Step 5: Run the focused test** and expect pass.
- [ ] **Step 6: Commit** `feat: add justice prison and guard registries`.

### Task 10: Models, Recipes, Textures, and Russian Localization

**Files:**
- Create: `design/AdministrationTextureGenerator.java`
- Create: nine blockstates under `src/main/resources/assets/kingdomnpc/blockstates/`
- Create: nine block models and nine item models under `src/main/resources/assets/kingdomnpc/models/`
- Create: nine PNG textures under `src/main/resources/assets/kingdomnpc/textures/block/`
- Create: nine recipes under `src/main/resources/data/kingdomnpc/recipes/`
- Modify: `src/main/resources/assets/kingdomnpc/lang/ru_ru.json`
- Test: `src/test/java/ru/projectkingdom/administration/AdministrationResourceTest.java`

**Interfaces:**
- Consumes: Task 3 exact registry IDs.
- Produces: one complete resource set per kind and original 16x16 medieval textures.

- [ ] **Step 1: Write a failing resource contract test** requiring all 45 resource files, valid JSON references, exact translation keys, non-identical PNG hashes, and no enterprise IDs reused.
- [ ] **Step 2: Run the focused test** and expect failure.
- [ ] **Step 3: Implement the deterministic texture generator and generate the nine textures** with distinct symbols for each civic service.
- [ ] **Step 4: Add blockstates, models, recipes, and Russian translations** using TFC-compatible ingredients where the dependency permits tags; otherwise use vanilla placeholders documented for later TFC replacement.
- [ ] **Step 5: Render a contact sheet and inspect it visually** for legibility and distinction at Minecraft scale.
- [ ] **Step 6: Run the focused test** and expect pass.
- [ ] **Step 7: Commit** `feat: add administrative block resources`.

### Task 11: Legacy Entry Points and End-to-End Acceptance

**Files:**
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java`
- Modify: relevant bank, warehouse, market, land, and town-hall open paths in `src/main/java/ru/projectkingdom/npc/KingdomMainMenu.java`
- Test: `src/test/java/ru/projectkingdom/administration/AdministrationAcceptanceTest.java`
- Create: `validation/ADMINISTRATIVE_BLOCKS_STATUS_RU.md`

**Interfaces:**
- Consumes: Tasks 1-10.
- Produces: all public administrative UI entry points through the correct physical core; old Lodestone/chest counters remain migration-only and cannot create a second active service.

- [ ] **Step 1: Write failing acceptance tests** covering one town's complete nine-core lifecycle, another town's isolation, public bank/market access, role-specific management, physical finance, loaded-only storage, and deferred Stage 9 behavior.
- [ ] **Step 2: Run** `./gradlew test --tests ru.projectkingdom.administration.AdministrationAcceptanceTest` and expect failure.
- [ ] **Step 3: Route legacy menu openings to nearest matching active core** and display an explicit migration message when only an old counter exists.
- [ ] **Step 4: Complete the Russian validation status document** with exact IDs, migration behavior, deferred functions, and security guarantees.
- [ ] **Step 5: Run** `./gradlew test` and expect zero failures.
- [ ] **Step 6: Commit** `feat: complete administrative block integration`.

### Task 12: Forge Build, Artifact Verification, and Publication

**Files:**
- Modify: `dist/kingdomnpc-0.1.0-stage0-dev.jar`
- Modify: persistent source archive after all source commits are green.

**Interfaces:**
- Consumes: final source tree from Tasks 1-11.
- Produces: one verified JAR and one source ZIP; the JAR replaces the previous mod file.

- [ ] **Step 1: Run full verification** with `./gradlew clean test build` under Java 17 / Forge 47.3.0 and expect exit code 0.
- [ ] **Step 2: Inspect the built JAR** with `unzip -t`, count nine blockstates/textures/recipes, and confirm key administration classes.
- [ ] **Step 3: Compute SHA-256 and Git blob SHA** for the exact JAR selected for publication.
- [ ] **Step 4: Publish the verified JAR to `dist/kingdomnpc-0.1.0-stage0-dev.jar`** and push the source commits to `main` only after a successful GitHub Actions run.
- [ ] **Step 5: Build and test a source ZIP** excluding build caches, transient artifacts, and private progress ledgers.
- [ ] **Step 6: Replace the existing persistent JAR and source ZIP versions** without creating duplicate filenames.
- [ ] **Step 7: Re-check the final `main` SHA and final GitHub Actions conclusion**; report both links and instruct the user to remove the old JAR before installing the new one.
