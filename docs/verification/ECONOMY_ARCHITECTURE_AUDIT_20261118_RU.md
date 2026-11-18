# Independent economy architecture audit — 2026-11-18

Repository: `kataev62-web/ProjectKingdom`. Audited `main` at `407f68f330da2c1572a6a3c94a526e9dcb8ccc66`; separately inspected draft PR #59 at `203a7635eab08c8f2a713017b327243db215df9b` and Issue #70.

## Boundaries

- **MiniMax owns Issue #70**: `DemandDeliveryCoordinator`, `SettlementAuthority` integration, delivery journals, crash/recovery, supplier→warehouse and till→wallet. This change deliberately does not edit those files or PR #59.
- This PR touches only the **existing physical cash transfer helper**, one pure denomination selector, unit tests and this audit.
- No main changes, no deployment, no journal cleanup. Keep PR draft until Forge CI and in-game verification.

## Economic invariants

1. Coin value and **the count of each physical denomination** remain conserved across transfers; denomination exchange requires its own explicitly backed mechanism.
2. Player funds, municipal treasury claims, deposited personal claims and enterprise cash remain separate.
3. A transaction must not add/remove item stacks if planning, ownership, capacity, NBT, loaded chunks or persistence checks fail.
4. Persisted receipts never authorize re-executing a partly completed operation.
5. Existing-world NBT and pending journals are preserved or quarantined, never zeroed to make a transaction look successful.

## Source audit findings

| ID | Severity | Evidence | Status / next independent scope |
|---|---|---|---|
| ECO-01 | High | `KingdomPhysicalCash.move` used `planWithdrawal(source,amount)` followed by `planDeposit(target,amount)`: the latter constructs greedy gold/silver/copper items from abstract value. Example: 100 copper removed from source, 1 silver inserted into target. | Narrow denomination-preserving change in this PR; actual MC inventory integration still requires verification. |
| ECO-02 | High | `KingdomCurrency.debit/credit` → `PurseTransaction` → `LegacyPurseMigration.fromCopperValue` and `KingdomPhysicalCash.deposit/withdraw` still convert denominations when interacting with purses/bank. | Follow-up, **not** solved by ECO-01. Define physical change policy; test every deposit/withdraw, payroll, and player wallet transfer. |
| ECO-03 | Critical | `KingdomBankData.deposit/withdraw` updates physical container, reserve and account SavedData separately. No durable single-owner cash/bank receipt ties all changes across crashes. | Separate bank-journal/recovery design, not part of MiniMax delivery authority unless explicitly coordinated. |
| ECO-04 | High | `MoneyPurseNbt.readMigrating` catches count overflow and returns `MoneyPurse.empty()`; a later write risks replacing untrusted old NBT with zero. | Separate fail-closed NBT integrity fix with migration and corrupt-tag tests. |
| ECO-05 | High | `KingdomEnterprisePayrollData.claimNpc` constructs denominations using `coinsWithExisting(..., amount)` after `debitVerifiedNpcWage`. Source denomination identity is not carried to the recipient. | Separate payroll exact-items design; do not touch #56/#50 PRs without coordination. |
| ECO-06 | Medium | `KingdomBankData` does maintain separate `accounts`, bank reserves and town treasury, with `TownBankFunds` preflight and physical coverage checks. | Positive structural separation, but crash consistency and permissions still need in-world proof. |
| ECO-07 | High | `KingdomPhysicalCash` did not distinguish tagged and untagged coins in placement/removal. | This PR excludes tagged stacks from the exact-transfer path and avoids merging plain coins into tagged stacks. Other call sites need further audit. |

## Implemented narrow change (ECO-01)

- `PhysicalCoinSelection.select`: deterministic exact selection from **available coin counts**, rejects a denomination split that would require making change.
- `KingdomPhysicalCash.move/canMove`: destination planning uses the counts **actually removed** from the source rather than a newly recomputed denomination set.
- All additions/removals remain planned on snapshots before applying either side.
- Plain coin insertion does not merge into NBT-tagged stacks.
- The fix is in-memory only. It **does not** make a cross-inventory operation power-loss atomic or prove that separate wrappers do not alias the same physical inventory.

## Test status and acceptance

- Pure Java 17 manual harness: 5 exact-denomination selection assertions passed on 2026-11-18, with an isolated OpenJDK 21 compiler in `--release 17` mode.
- JUnit tests included: 100 copper without silver, available silver, mixed denominations, no virtual change, invalid inputs.
- **Not run here:** full `./gradlew test build`, real Forge/TFC/Create server, modded/double chest, dimension/permission boundaries and crash/restart. Do not merge before these pass.
- Required integration scenarios: verify gold/silver/copper counts before and after, insufficient exact coin combination, full destination, tagged coin refusal, source/destination alias, double-chest access, NBT/perishables, concurrent calls, server restart and persisted balances.

## Next non-overlapping work

1. **Bank crash journal:** isolate bank deposit/withdraw/treasury from delivery authority; define operation IDs and quarantine for partial SavedData/physical writes.
2. **Wallet integrity:** fail-closed corruption handling and an explicit physical change/exchange policy.
3. **NPC payroll:** conserve exact coin stacks and settle debts only after physical write is confirmed.
4. **Integration audit:** eventually reconcile multiple economic journals and owner/authority boundaries with MiniMax PR #59/#70; no cross-branch editing.
