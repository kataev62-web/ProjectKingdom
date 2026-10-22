# Crash-safe trading implementation plan

**Goal:** Ensure that a server crash never silently duplicates goods or money during a trade.

**Current finding:** KingdomEnterpriseData.purchase() writes PENDING, mutates two block inventories and enterprise SavedData balances, then writes SETTLED; the three persistence domains do not commit atomically. The existing PENDING -> NEEDS_RECONCILIATION loader only catches some interrupted saves.

## Tasks

- [x] Quarantine both seller and buyer for unresolved legacy transactions, not only seller. Verify existing stage1 harness passes.
- [ ] Add persistent pre/post inventory slot snapshots including full NBT and dimensions/positions, plus pre/post firm balances and a stable transaction UUID.
- [ ] Implement a durable write-ahead record with explicit fsync and atomic rename before mutating inventories. If durability cannot be guaranteed, refuse transaction.
- [ ] Add a recovery state machine that inspects both counterparties and all recorded slots, never blindly replays mutations, and quarantines ambiguous/missing/unloaded containers.
- [ ] Require verified durable commit before removing a transaction's quarantine; implement idempotent duplicate-request handling.
- [ ] Add fault injection tests at every save boundary, partial/torn journal, concurrent purchase, changed NBT, and repeated restart; assert conservation of money and items.
- [ ] Validate with ForgeGradle 1.20.1 Forge 47.3.0 and actual server save/restart once available.

**Important:** SavedData.setDirty() is not an immediate durable disk write. In-memory PENDING and SETTLED states are not a crash-safe transaction protocol.

## 2026-10-15: durable reconciliation receipts (implemented in worktree)

- Journal supports terminal `RECONCILED` records, retains immutable transaction UUID and checksum, and excludes terminal receipts from `unresolved()`.
- `acknowledgeReconciled(UUID)` can now close an audited transaction *only when explicitly invoked after human verification of the persisted inventory and balances*; the terminal receipt is forced to disk before the SavedData trade state is changed.
- On startup, terminal receipts correct stale `NEEDS_RECONCILIATION` SavedData flags. Legacy unresolved records without receipts remain blocked.
- No operator command/UI is wired yet. No automatic inventory repair or multi-file atomicity is claimed. This API is not safe to expose without an authenticated, audited operator verification workflow.
- Validation: `javac ... TradeWriteAheadJournalTest.java` and `java ... TradeWriteAheadJournalTest` passed; Forge compilation and actual crash-injection tests remain outstanding.
