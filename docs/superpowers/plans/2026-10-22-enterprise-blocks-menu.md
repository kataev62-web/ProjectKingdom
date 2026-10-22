# Enterprise Blocks and MineColonies-Style Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace generic enterprise creation with eight dedicated, secure, relocatable enterprise blocks and a readable MineColonies-style management screen connected to existing physical economy systems.

**Architecture:** Register eight distinct blocks and items that share one block class, block entity, menu, screen, and lifecycle service parameterized by `EnterpriseKind`. Extend `KingdomEnterpriseData` through migration-safe maps and adapter methods rather than rewriting existing trade, payroll, storage, and workshop records.

**Tech Stack:** Java 17, Minecraft 1.20.1, Forge 47.3.0, Forge registries/menu/network APIs, NBT SavedData, JUnit 5, Minecraft blockstate/model/recipe resources.

**Spec:** `docs/superpowers/specs/2026-10-22-economy-wallet-enterprise-blocks-design.md`

**Prerequisite:** `docs/superpowers/plans/2026-10-22-money-purses-font-assets.md` is complete and its GitHub Actions checkpoint is green.

## Global Constraints

- Register exactly eight private types: farm, mine, lumber, smithy, bakery, pottery, construction, courier.
- Warehouse and market are administrative city buildings, never private enterprise types.
- Every type has a distinct registered block, item, model, texture, recipe, translation, and menu identity.
- One shared implementation must serve the eight blocks; do not copy block entity or screen classes per type.
- Goods remain in physical linked containers and enterprise funds remain backed by existing physical money flows.
- Only the server creates, activates, suspends, relinks, renames, or mutates enterprise records.
- Breaking the core must preserve its enterprise UUID; one UUID may have only one active core.
- Do not load chunks to inspect or operate enterprise storage.
- Specialized NPC production remains staged except for the existing smith workshop integration.

## Review Focus

- Creative-mode copied bound block item: must not activate the same enterprise UUID twice; pinned in Task 2.
- Core broken during an open menu: later button presses must fail without mutating data; pinned in Task 4.
- Legacy enterprise without kind/core fields: remains visible and can be adopted once without losing cash/contracts; pinned in Task 1.
- Enterprise core chunk unloaded while storage chunk is loaded, or vice versa: show unavailable and never force-load; pinned in Task 3.
- Rename packet with a stale menu, foreign UUID, formatting codes, or more than 48 characters: reject server-side; pinned in Task 4.

---

### Task 1: Enterprise type catalog and migration-safe SavedData

**Files:**
- Create: `src/main/java/ru/projectkingdom/enterprise/EnterpriseKind.java`
- Create: `src/main/java/ru/projectkingdom/enterprise/EnterpriseCoreBinding.java`
- Create: `src/main/java/ru/projectkingdom/enterprise/EnterpriseActivation.java`
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomEnterpriseData.java`
- Create: `src/test/java/ru/projectkingdom/enterprise/EnterpriseKindTest.java`
- Create: `validation/EnterpriseLifecycleContractTest.java`

**Interfaces:**
- Produces: `EnterpriseKind` constants `FARM`, `MINE`, `LUMBER`, `SMITHY`, `BAKERY`, `POTTERY`, `CONSTRUCTION`, `COURIER`, plus non-placeable `LEGACY`.
- Produces: `EnterpriseCoreBinding(UUID enterpriseId, UUID owner, EnterpriseKind kind, String dimension, long packedPos, boolean active)`.
- Extends `KingdomEnterpriseData` with `create(UUID,String,EnterpriseKind)`, `kind(UUID)`, `core(UUID)`, `activateCore(...)`, `suspendCore(...)`, `adoptLegacy(...)`, `rename(...)`, and `mayManage(...)`.

- [ ] **Step 1: Write failing catalog and lifecycle tests**

Assert eight placeable ids, no `warehouse`/`market`, unique translation/model ids, only one active binding per enterprise UUID, owner-only activation, exact suspend-position matching, and legacy adoption preserving enterprise id, name, cash, offers, and contracts.

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `./gradlew test --tests 'ru.projectkingdom.enterprise.EnterpriseKindTest' --no-daemon`  
Expected: FAIL because the enterprise package does not exist.

- [ ] **Step 3: Implement pure type and lifecycle decisions**

Keep id/title/default prefix in the enum; keep activation result values explicit (`ACTIVATED`, `ALREADY_ACTIVE`, `FOREIGN_OWNER`, `TYPE_MISMATCH`, `INVALID_LOCATION`).

- [ ] **Step 4: Extend SavedData with backward-compatible NBT maps**

Old saves lacking kind/core fields load as `LEGACY` and inactive. Preserve the existing save key `kingdom_enterprises_v1`; add optional compounds instead of changing the record key or deleting old fields.

- [ ] **Step 5: Run JUnit plus the standalone lifecycle contract**

Expected: all catalog, legacy, duplicate, and ownership cases pass.

- [ ] **Step 6: Commit the enterprise domain model**

Commit message: `feat: add typed enterprise lifecycle model`

### Task 2: Eight registered blocks and secure bound-block lifecycle

**Files:**
- Create: `src/main/java/ru/projectkingdom/enterprise/KingdomEnterpriseRegistry.java`
- Create: `src/main/java/ru/projectkingdom/enterprise/EnterpriseCoreBlock.java`
- Create: `src/main/java/ru/projectkingdom/enterprise/EnterpriseCoreBlockEntity.java`
- Create: `src/main/java/ru/projectkingdom/enterprise/EnterpriseCoreItemData.java`
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java`
- Create: `src/test/java/ru/projectkingdom/enterprise/EnterpriseRegistryContractTest.java`

**Interfaces:**
- Consumes: enterprise domain interfaces from Task 1.
- Produces: registry ids `farm_core`, `mine_core`, `lumber_core`, `smithy_core`, `bakery_core`, `pottery_core`, `construction_core`, `courier_core`.
- Produces: item NBT fields `KingdomEnterpriseId`, `KingdomEnterpriseOwner`, and `KingdomEnterpriseKind`.

- [ ] **Step 1: Write a failing registry contract**

Assert exactly eight distinct block/item registry objects, one block entity type accepting all eight blocks, and one-to-one kind mapping with no administrative ids.

- [ ] **Step 2: Run the contract and confirm RED**

Expected: FAIL because no enterprise registry exists.

- [ ] **Step 3: Register shared-class blocks, block items, and block entity type**

Use `EnterpriseCoreBlock(EnterpriseKind)` and `EnterpriseCoreBlockEntity`; register all DeferredRegisters on the mod event bus from `KingdomNpcMod`.

- [ ] **Step 4: Implement server-side placement**

For an unbound item, validate territory then create and activate a typed enterprise. For a bound item, validate owner, type, inactive state, and exact UUID before activation. Failed placement must not create a record.

- [ ] **Step 5: Implement secure break/drop behavior**

Owner break suspends the exact active binding and drops one block item carrying the three NBT fields. Non-owner break follows territory protection and never receives a management-capable bound item.

- [ ] **Step 6: Test copy and relocation attacks**

Cover copied bound item, stale position, mismatched kind, foreign owner, second active core, repeated break, and successful owner relocation.

- [ ] **Step 7: Commit the block lifecycle**

Commit message: `feat: add eight secure enterprise core blocks`

### Task 3: Physical cash and storage bindings

**Files:**
- Create: `src/main/java/ru/projectkingdom/enterprise/EnterpriseBindingRole.java`
- Create: `src/main/java/ru/projectkingdom/enterprise/EnterpriseBindingSession.java`
- Create: `src/main/java/ru/projectkingdom/enterprise/EnterpriseAccessService.java`
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomEnterpriseData.java`
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java`
- Test: `src/test/java/ru/projectkingdom/enterprise/EnterpriseAccessServiceTest.java`

**Interfaces:**
- Produces roles `CASHBOX`, `RAW_STORAGE`, and `FINISHED_STORAGE`.
- Produces `begin(ServerPlayer,UUID,EnterpriseBindingRole)`, `complete(ServerPlayer,ServerLevel,BlockPos)`, and `cancel(UUID playerId)`.
- Produces access snapshots that report `BOUND_AVAILABLE`, `BOUND_UNLOADED`, `MISSING`, `FOREIGN_TERRITORY`, or `INVALID_CONTAINER` without force-loading chunks.

- [ ] **Step 1: Write failing binding and unloaded-chunk tests**

Cover owner proximity, rights, real-container requirement, one container not serving conflicting roles, logout cancellation, unloaded target, replaced block entity, and foreign territory.

- [ ] **Step 2: Run the focused test and confirm RED**

Expected: FAIL because roles and service do not exist.

- [ ] **Step 3: Implement explicit one-click binding sessions**

Sessions are server-memory intent only; final bindings persist in `KingdomEnterpriseData`. Reuse the existing interaction-event pattern but keep enterprise sessions separate from NPC workstation sessions.

- [ ] **Step 4: Add enterprise-id overloads for cash operations**

Deposit/withdraw must validate the opened core, management rights, loaded physical cashbox, and multi-purse transaction result before changing enterprise cash.

- [ ] **Step 5: Run access, territory, trade, and Stage 1 regression suites**

Expected: all existing physical trade protections remain green and no new code calls `getChunk`/ticket APIs.

- [ ] **Step 6: Commit physical bindings**

Commit message: `feat: bind enterprise cores to physical storage`

### Task 4: Container menu, rename packet, and MineColonies-style screen

**Files:**
- Create: `src/main/java/ru/projectkingdom/enterprise/EnterpriseCoreMenu.java`
- Create: `src/main/java/ru/projectkingdom/enterprise/EnterpriseCoreScreen.java`
- Create: `src/main/java/ru/projectkingdom/enterprise/EnterpriseMenuLayout.java`
- Create: `src/main/java/ru/projectkingdom/network/KingdomNetwork.java`
- Create: `src/main/java/ru/projectkingdom/network/RenameEnterprisePacket.java`
- Modify: `src/main/java/ru/projectkingdom/enterprise/KingdomEnterpriseRegistry.java`
- Modify: `src/main/java/ru/projectkingdom/core/KingdomBookClient.java`
- Create: `src/test/java/ru/projectkingdom/enterprise/EnterpriseMenuLayoutTest.java`
- Create: `src/test/java/ru/projectkingdom/network/RenameEnterprisePacketTest.java`

**Interfaces:**
- Consumes: Task 1 lifecycle, Task 3 access snapshots, and `KingdomFonts.readable` from the currency plan.
- Produces tabs `INFO`, `WORKERS`, `PRODUCTION`, `STORAGE`, `CASH`, `ORDERS`, `SETTINGS`.
- Produces menu action ids `100=deposit100`, `101=withdraw100`, `110=bindCashbox`, `111=bindRaw`, `112=bindFinished`.
- Produces `RenameEnterprisePacket(UUID enterpriseId,String name)` with UTF-8 length `1..48` after trimming and formatting-code rejection.

- [ ] **Step 1: Write failing layout and packet-validation tests**

Assert seven non-overlapping side tabs, parchment content bounds, long Russian wrapping, stale-menu rejection, proximity, ownership, active-core identity, control-code rejection, and 48-character limit.

- [ ] **Step 2: Run focused tests and confirm RED**

Expected: FAIL because menu, layout, and packet do not exist.

- [ ] **Step 3: Implement and register the menu**

Open from the core block with its position and UUID. Synchronize kind, status, cash, worker count, debt, storage states, and production state; `stillValid` requires the same loaded block entity within normal reach.

- [ ] **Step 4: Implement the scoped network channel and rename packet**

Accept only while the sender has the matching `EnterpriseCoreMenu` open. Enqueue on the server thread, re-read SavedData and block entity, then call `rename`; never trust owner or position fields from the client.

- [ ] **Step 5: Implement the single-page parchment screen**

Match the approved MineColonies direction: one parchment page, left icon tabs, large readable Russian labels, no overlapping lines, type-specific heading/icon, and honest `Не настроено`/`Скоро` states.

- [ ] **Step 6: Wire functional buttons**

Cash and binding actions use menu button ids and server revalidation. Settings owns the rename text field. Tabs that lack a profession scenario display real saved data plus the staged status, never simulated output.

- [ ] **Step 7: Run layout, security, and full JUnit suites**

Expected: all tests pass; closing/breaking/unloading the core invalidates further mutations.

- [ ] **Step 8: Commit menu and screen**

Commit message: `feat: add enterprise management screen`

### Task 5: Block visuals, recipes, translations, and existing-system adapters

**Files:**
- Create: `design/EnterpriseTextureGenerator.java`
- Create: `src/main/resources/assets/kingdomnpc/blockstates/{farm,mine,lumber,smithy,bakery,pottery,construction,courier}_core.json`
- Create: `src/main/resources/assets/kingdomnpc/models/block/{farm,mine,lumber,smithy,bakery,pottery,construction,courier}_core.json`
- Create: `src/main/resources/assets/kingdomnpc/models/item/{farm,mine,lumber,smithy,bakery,pottery,construction,courier}_core.json`
- Create: `src/main/resources/assets/kingdomnpc/textures/block/{farm,mine,lumber,smithy,bakery,pottery,construction,courier}_core.png`
- Create: `src/main/resources/data/kingdomnpc/recipes/{farm,mine,lumber,smithy,bakery,pottery,construction,courier}_core.json`
- Modify: `src/main/resources/assets/kingdomnpc/lang/ru_ru.json`
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomNpcWorkshop.java`
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomMainMenu.java`
- Create: `src/test/java/ru/projectkingdom/enterprise/EnterpriseResourceTest.java`

**Interfaces:**
- Consumes: all prior enterprise tasks.
- Produces: eight original 16×16 block textures and survival recipes; the smith workshop accepts only a live `SMITHY` core for its enterprise.

- [ ] **Step 1: Write the failing resource matrix test**

For every placeable `EnterpriseKind`, assert blockstate, block model, item model, texture, recipe, Russian block/item translation, and distinct registry id. Assert no warehouse/market enterprise resource exists.

- [ ] **Step 2: Run the resource test and confirm RED**

Expected: FAIL listing all missing resources.

- [ ] **Step 3: Generate and inspect eight original block textures**

Use one checked-in deterministic Java generator with a shared carved-wood frame and a unique profession emblem/color for each type. Inspect a nearest-neighbour contact sheet before accepting assets.

- [ ] **Step 4: Add blockstates, models, recipes, and translations**

Recipes use broadly compatible Minecraft/Forge ingredients and avoid a hard TFC Java dependency. Every recipe produces exactly one unbound core block.

- [ ] **Step 5: Connect existing systems**

Route old generic enterprise entry points to the placed-core flow; preserve legacy records for adoption. Require `SMITHY` for existing smith production. Keep warehouse/market access on their administrative paths.

- [ ] **Step 6: Run the resource matrix and Stage 1 harness**

Expected: all eight matrices pass and existing workshop/trade/payroll contracts remain green.

- [ ] **Step 7: Commit resources and adapters**

Commit message: `feat: connect typed enterprises to stage one economy`

### Task 6: Integrated release verification and publication

**Files:**
- Create: `validation/ENTERPRISE_BLOCKS_STATUS_RU.md`
- Update: `dist/kingdomnpc-0.1.0-stage0-dev.jar`
- Replace after verification: `ProjectKingdom_Stage0_GameCore_0.1.0_Source.zip`

**Interfaces:**
- Consumes: the complete currency plan and Tasks 1–5 here.
- Produces: one user-facing verified JAR and one matching source archive; no interim archive is delivered.

- [ ] **Step 1: Run all standalone contracts and the Stage 1 harness**

Expected: every test exits 0; record the count and PASS markers.

- [ ] **Step 2: Push source and require GitHub Actions success**

Expected: Java 17 `./gradlew test build --no-daemon` succeeds and uploads the Forge artifact.

- [ ] **Step 3: Inspect the CI-built JAR**

Run `unzip -t`; verify all money, font, eight-block, menu, model, recipe, translation, and texture resources. Compare the local Git blob/SHA-256 with the repository `dist` file after publication.

- [ ] **Step 4: Publish the verified JAR to `dist/` and require the final CI run to pass**

Expected: `main` points to the dist commit, final workflow conclusion is `success`, and remote dist blob matches the verified artifact exactly.

- [ ] **Step 5: Package and verify matching sources**

Create one source ZIP excluding build caches, run `unzip -t`, and confirm it contains both approved spec and plans plus every source/resource used by the JAR.

- [ ] **Step 6: Replace the existing persistent JAR and source archive identities**

Preserve their version histories and paths; record the returned version numbers and apply local file identity metadata.

- [ ] **Step 7: Write the release validation note**

Record the final commit, workflow run, JAR SHA-256, included enterprise ids, purse rules, migrations, and staged production limitations in `validation/ENTERPRISE_BLOCKS_STATUS_RU.md`.

- [ ] **Step 8: Final verification gate**

Re-run critical standalone tests, inspect the final JAR and source ZIP, confirm GitHub `main` and CI, then provide the user only the final JAR, source archive, commit link, and installation instruction to replace the old mod.
