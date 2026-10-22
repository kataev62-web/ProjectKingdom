# Money Purses, Font, and Currency Assets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver readable Project Kingdom economy screens, original coin and purse visuals, and a conserved multi-purse currency system with 1000 coins per filled purse.

**Architecture:** Keep Minecraft-facing NBT, inventory, menu, and screen code at the edges. Put denominations, purse capacity, migration, and atomic multi-purse payment rules in small pure-Java classes so they can be regression-tested without launching Minecraft.

**Tech Stack:** Java 17, Minecraft 1.20.1, Forge 47.3.0, JUnit 5, Minecraft resource JSON, PNG/TTF assets.

**Spec:** `docs/superpowers/specs/2026-10-22-economy-wallet-enterprise-blocks-design.md`

## Global Constraints

- Preserve the registry id `kingdomnpc:wallet`; rename only its Russian display name to «Денежный мешок».
- Denominations are exactly copper `1`, silver `100`, and gold `10_000` copper units.
- One filled purse holds at most `1000` physical coins; empty purses stack to `16`; filled purses stack to `1`.
- A player may carry several independent purses; payment uses them in inventory-slot order.
- Bank accounts store money but never pay purchases directly.
- No operation may mint or lose copper value; failed operations leave every input unchanged.
- Preserve old `KingdomCopperBalance` NBT values through migration.
- The custom Cyrillic font applies only to Project Kingdom screens.
- Do not require TFC Java classes at compile time; use compatible item/tag data resources.

## Review Focus

- A purse stack of 16 with no free inventory slot: right-click must refuse cleanly instead of deleting or filling the whole stack; pinned in Task 3.
- Payment that needs change and would exceed 1000 coins: reject atomically; pinned in Task 2.
- Old balance above one purse's representable capacity: preserve value in overflow migration mode; pinned in Task 1.
- Two purses with identical NBT in different slots: operate by slot, never by item equality; pinned in Task 3.
- Long Russian labels or a missing custom font resource: remain clipped/wrapped inside the screen and fall back safely; pinned in Task 4.

---

### Task 1: Pure denomination, purse state, and legacy migration

**Files:**
- Create: `src/main/java/ru/projectkingdom/economy/MoneyDenomination.java`
- Create: `src/main/java/ru/projectkingdom/economy/MoneyPurse.java`
- Create: `src/main/java/ru/projectkingdom/economy/LegacyPurseMigration.java`
- Create: `src/test/java/ru/projectkingdom/economy/MoneyPurseTest.java`
- Create: `validation/MoneyPurseContractTest.java`

**Interfaces:**
- Produces: `MoneyDenomination.copperValue(): long` and `MoneyPurse(copper, silver, gold, legacyOverflow)` with `MAX_COINS = 1000`, `coinCount()`, `valueCopper()`, `canAccept(...)`, and immutable add/remove methods.
- Produces: `LegacyPurseMigration.fromCopperValue(long): MoneyPurse`, decomposing gold, then silver, then copper and marking overflow instead of truncating.

- [ ] **Step 1: Write failing capacity and value tests**

Add tests named `countsThreeDenominationsWithoutLosingValue`, `rejectsTheThousandAndFirstCoin`, `allowsExactlyOneThousandCoins`, and `preservesOversizedLegacyValueAsOverflow`. Assert `1/100/10_000`, exact coin counts, and unchanged input after rejected additions.

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `./gradlew test --tests 'ru.projectkingdom.economy.MoneyPurseTest' --no-daemon`  
Expected: FAIL because the three production types do not exist.

- [ ] **Step 3: Implement the immutable pure-Java model**

Validate non-negative counts, use overflow-safe arithmetic (`Math.addExact`/`Math.multiplyExact` caught as invalid), and never mutate a purse on a rejected operation. `legacyOverflow` permits spending but forbids credit until `coinCount() <= 1000`.

- [ ] **Step 4: Add and run the standalone contract**

Run the Java compiler module against the three production files and `validation/MoneyPurseContractTest.java`, then run `ru.projectkingdom.economy.MoneyPurseContractTest`.  
Expected: `PASS purse value, capacity, and migration` with exit code 0.

- [ ] **Step 5: Commit the pure model**

Commit message: `feat: add conserved money purse model`

### Task 2: Atomic transactions across multiple purses

**Files:**
- Create: `src/main/java/ru/projectkingdom/economy/PurseTransaction.java`
- Create: `src/test/java/ru/projectkingdom/economy/PurseTransactionTest.java`
- Create: `validation/PurseTransactionContractTest.java`

**Interfaces:**
- Consumes: `MoneyPurse` and `MoneyDenomination` from Task 1.
- Produces: `PurseTransaction.debit(List<MoneyPurse>, long): Optional<List<MoneyPurse>>` and `credit(List<MoneyPurse>, long): Optional<List<MoneyPurse>>`.
- Guarantee: returned lists are new snapshots; empty means the caller must retain every original purse unchanged.

- [ ] **Step 1: Write failing transaction tests**

Cover `debitsInInventoryOrder`, `usesTwoPursesForOnePayment`, `makesExactChangeWithoutChangingTotalValue`, `rejectsWhenChangeExceedsCapacity`, `creditsTheFirstPurseWithSpaceThenTheNext`, and `failureDoesNotMutateAnyInput`.

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `./gradlew test --tests 'ru.projectkingdom.economy.PurseTransactionTest' --no-daemon`  
Expected: FAIL because `PurseTransaction` does not exist.

- [ ] **Step 3: Implement debit and credit planning**

Use deterministic slot order. Recompose only a purse touched by the transaction, preserve copper value exactly, and validate every resulting purse before returning a snapshot.

- [ ] **Step 4: Run JUnit and the standalone contract**

Expected: all transaction cases pass and the contract prints `PASS multi-purse atomic transactions`.

- [ ] **Step 5: Commit transaction planning**

Commit message: `feat: add atomic multi-purse transactions`

### Task 3: Minecraft NBT adapter, dynamic stacking, and purse menu

**Files:**
- Create: `src/main/java/ru/projectkingdom/economy/MoneyPurseNbt.java`
- Create: `src/main/java/ru/projectkingdom/economy/MoneyPurseInventory.java`
- Create: `src/main/java/ru/projectkingdom/economy/MoneyPurseMenu.java`
- Create: `src/main/java/ru/projectkingdom/economy/KingdomEconomyRegistry.java`
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomCurrency.java`
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomWalletItem.java`
- Modify: `src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java`
- Modify: `src/main/java/ru/projectkingdom/core/Stage1FinanceAdapter.java`
- Test: `src/test/java/ru/projectkingdom/economy/MoneyPurseNbtTest.java`

**Interfaces:**
- Consumes: Task 1 and 2 pure models.
- Produces: NBT keys `KingdomPurseVersion`, `KingdomCopperCoins`, `KingdomSilverCoins`, `KingdomGoldCoins`, and `KingdomLegacyOverflow`.
- Produces: `MoneyPurseInventory.snapshot(ServerPlayer)`, `debit(ServerPlayer,long)`, `credit(ServerPlayer,long)`, `collectLooseCoins(ServerPlayer,int purseSlot)`, and `withdraw(ServerPlayer,int purseSlot,MoneyDenomination,int)`.
- Produces: menu button ids `0=collect`, `1=copper`, `2=silver`, `3=gold`.

- [ ] **Step 1: Write failing NBT and slot-identity tests**

Assert old balance migration, round-trip of each count, no rewrite on read failure, operation on the requested inventory slot, and separate handling of identical purses in two slots.

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `./gradlew test --tests 'ru.projectkingdom.economy.MoneyPurseNbtTest' --no-daemon`  
Expected: FAIL because the adapter is absent.

- [ ] **Step 3: Implement NBT and player-inventory transactions**

Apply the complete transaction snapshot only after every target slot still contains the expected purse. Mark the inventory changed once after commit.

- [ ] **Step 4: Implement empty-stack separation and dynamic maximum stack size**

Construct wallet items with `stacksTo(16)`. Override Forge's stack-aware maximum so a stack with money returns `1`. On right-click of an empty stack larger than one, move one empty purse to a free slot and open that exact slot; with no free slot, show a message and change nothing.

- [ ] **Step 5: Implement and register the server menu**

Open through `NetworkHooks.openScreen`, send the exact slot index, expose synchronized counts/value/capacity, and process only ids `0..3` after validating player, slot, and stack.

- [ ] **Step 6: Replace single-wallet callers with multi-purse operations**

Update finance display, payroll, enterprise cash, bank, supply orders, warehouse checkout, and transfers to call `MoneyPurseInventory` without changing the rule that bank money cannot pay directly.

- [ ] **Step 7: Run economy and existing Stage 1 regression tests**

Run: `./gradlew test --no-daemon` and `bash validation/stage1_harness/run.sh`.  
Expected: all tests pass; no legacy caller reads or writes `KingdomCopperBalance` except the migration adapter.

- [ ] **Step 8: Commit Minecraft integration**

Commit message: `feat: integrate stack-aware money purses`

### Task 4: Original item visuals and readable Project Kingdom font

**Files:**
- Create: `design/EconomyTextureGenerator.java`
- Create: `src/main/resources/assets/kingdomnpc/textures/item/copper_coin.png`
- Create: `src/main/resources/assets/kingdomnpc/textures/item/silver_coin.png`
- Create: `src/main/resources/assets/kingdomnpc/textures/item/gold_coin.png`
- Create: `src/main/resources/assets/kingdomnpc/textures/item/wallet.png`
- Create: `src/main/resources/assets/kingdomnpc/font/readable.json`
- Create: `src/main/resources/assets/kingdomnpc/font/dejavu_sans.ttf`
- Create: `licenses/DejaVu-Fonts-LICENSE.txt`
- Create: `src/main/java/ru/projectkingdom/core/KingdomFonts.java`
- Create: `src/main/java/ru/projectkingdom/economy/MoneyPurseScreen.java`
- Modify: `src/main/resources/assets/kingdomnpc/models/item/copper_coin.json`
- Modify: `src/main/resources/assets/kingdomnpc/models/item/silver_coin.json`
- Modify: `src/main/resources/assets/kingdomnpc/models/item/gold_coin.json`
- Modify: `src/main/resources/assets/kingdomnpc/models/item/wallet.json`
- Modify: `src/main/resources/assets/kingdomnpc/lang/ru_ru.json`
- Modify: `src/main/java/ru/projectkingdom/core/KingdomBookScreen.java`
- Modify: `src/main/java/ru/projectkingdom/core/KingdomBookClient.java`
- Test: `src/test/java/ru/projectkingdom/economy/EconomyResourceTest.java`

**Interfaces:**
- Consumes: `MoneyPurseMenu` from Task 3.
- Produces: `KingdomFonts.readable(String): Component` using `kingdomnpc:readable` and a Minecraft-default fallback provider.
- Produces: four original 16×16 transparent PNG item textures and a client screen that never changes global fonts.

- [ ] **Step 1: Write failing resource and typography tests**

Assert all four PNG resources exist at 16×16 with alpha, all four models reference `kingdomnpc:item/...`, the font JSON contains a fallback provider, the TTF license exists, and representative Russian labels fit or wrap within their declared widths.

- [ ] **Step 2: Run the resource test and confirm RED**

Run: `./gradlew test --tests 'ru.projectkingdom.economy.EconomyResourceTest' --no-daemon`  
Expected: FAIL because the new resources and font do not exist.

- [ ] **Step 3: Generate and visually inspect the four pixel-art textures**

Use the checked-in Java generator for deterministic 16×16 images: copper with warm rim, silver with pale rim, gold with bright rim, and a brown tied leather purse. Inspect the output at original size and nearest-neighbour enlargement.

- [ ] **Step 4: Add the scoped font and license**

Bundle the redistribution-permitted DejaVu Sans file, define `kingdomnpc:readable`, and keep Minecraft's default font as fallback for missing glyphs.

- [ ] **Step 5: Implement the purse screen and update book typography**

Use `Component` values styled by `KingdomFonts`; centralize line heights; wrap descriptions; clip dynamic player names and numeric values to page bounds. Register `MoneyPurseScreen` in client setup.

- [ ] **Step 6: Run resource, book-layout, and full JUnit tests**

Expected: all resources load, the existing book texture/layout tests remain green, and no global `minecraft:default` font resource is overridden.

- [ ] **Step 7: Commit visuals and font**

Commit message: `feat: add readable economy visuals and purse screen`

### Task 5: Currency subsystem integration checkpoint

**Files:**
- Modify if needed: `.github/workflows/build.yml`
- Create: `validation/MONEY_PURSE_STATUS_RU.md`

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces: a Forge-compiled checkpoint JAR for use by the enterprise plan; this is not delivered as an interim user archive.

- [ ] **Step 1: Run every local standalone and Stage 1 contract**

Expected: every command exits 0 and prints its PASS marker.

- [ ] **Step 2: Push the checkpoint and require GitHub Actions success**

Expected: `./gradlew test build --no-daemon` succeeds on Java 17 and uploads `kingdomnpc-stage0`.

- [ ] **Step 3: Inspect the built JAR**

Confirm the purse classes, four item textures, font JSON, TTF, license, recipe, translations, and unchanged open-book texture are present; run `unzip -t` with zero errors.

- [ ] **Step 4: Record the verified behavior**

Document limits, NBT migration, test commands, artifact SHA-256, and known scope boundaries in `validation/MONEY_PURSE_STATUS_RU.md`.

- [ ] **Step 5: Commit the verified checkpoint metadata**

Commit message: `test: verify money purse subsystem`
