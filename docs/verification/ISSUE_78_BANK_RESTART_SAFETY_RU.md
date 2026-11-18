# Issue #78 — Bank restart safety and recovery (2026-11-18)

Scope: `KingdomBankData`, `KingdomTaxData`, independent `BankOperationLedger`,
`BankOperations`, `BankRecoveryCommands`, unit tests. Base: main `407f68f`.
Does **not** edit MiniMax's `SettlementAuthority`, `DemandDeliveryCoordinator`, delivery journals or Issue #70.

## Contract

- Every protected financial mutation receives a server-generated operation UUID and durable
  append-only **PREPARED** record, with actor, recipient, town, amount, type,
  copper-value snapshots, full wallet/vault item+NBT hashes. File is forced to disk
  before calling world/inventory mutators.
- A **RECEIPT** is forced after all in-memory physical + accounting writes,
  only when the expected wallet, real bank contents, personal/recipient account,
  bank reserve and treasury reserve transitions reconcile.
- If a callback returns false, **ABORTED** is allowed only if the complete before/after
  wallet/vault NBT hashes and all observed ledger values are identical.
  Any mismatch, I/O failure or corrupted journal locks further writes.
- Following **any restart**, unfinished and receipt-bearing operations are in
  quarantine, because Minecraft stores playerdata, chunks, SavedData and our
  journal independently. A receipt on disk does **not** prove the world save survived.
- Nothing is silently re-applied. No extra coins are issued. A manual operator at
  the exact town's bank, with the original player online, may record **RECONCILED**
  after a two-step confirmation *only if* all observed physical and accounting fields
  exactly match either a never-applied PREPARED or completed RECEIPT.
- This is intentionally conservative. Normal banking after a restart that followed
  bank activity can remain unavailable until operator review. A valid but later-
  changed wallet/inventory also prevents automatic reconciliation; restoring from
  an audited backup may be necessary. Never erase the journal to unlock it.

## Covered paths

- Personal bank `deposit` / `withdraw`.
- Municipal `DEPOSIT` / `WITHDRAW`, personal-to-city `DONATE`.
- Explicit assignment of verified unclaimed cash to municipal funds.
- Paid lease, lease debt and land-sale transfer through the physical bank.
- Tax debt paid to the original city's real bank and treasury (not a private account).

`KingdomPhysicalCash` and PR #76 are unchanged; this journal documents the
remaining need for exact-denomination conservation in purses and real modded inventories.

## Recovery

`/kingdom-bank-recovery list` — OP4 only, report unresolved operation UUID,
town, kind, amount and state.

`/kingdom-bank-recovery resolve <uuid>` — twice in 30 seconds from the
**registered bank of that town**, with the original payer online. The second
call does not move items, only writes the recovery marker after comparing
all ledger fields and complete NBT fingerprints. If physical positions or
counts differ, the transaction remains blocked. There is no force-ack command.

Logs live at:
`<world>/data/kingdom_bank_transactions/bank-operations.log`.

## Explicit limits / release blockers

1. **Not multi-file atomicity:** WAL + quarantine avoids blind retries,
   but cannot itself reverse a crash that already persisted only half of an
   operation. Exactly-once automatic recovery requires a durable, versioned
   transaction protocol across playerdata, chunk inventory and all SavedData.
   Don't claim lossless crash-atomic funds until tested with **real world copies**.
2. Multiple receipts in the same bank since its last save may require an
   operator to reconstruct a valid latest checkpoint; current strict witness
   rules intentionally refuse to guess from intermediate snapshots.
3. Archived legacy-bank mass migration and old treasury-vault consolidation,
   plus independent city procurement/payroll and ownership transfers, need a
   separate migration/settlement gate. Do not claim these routes covered here.
4. All new bank code runs exclusively on the server thread; server restarts
   are required between crash-boundary acceptance tests.

## Verification matrix

JUnit `BankOperationLedgerTest` covers eleven **journal/restart boundary models**,
repeat attempts, NBT digest mismatches, corrupt tails, expected reserve movements,
separate town quarantine, donations and third-party receipts.

**Not yet equivalent to Minecraft save/restart acceptance**. Before merging run
`./gradlew test build` and `python3 03_verify_project.py`, then on a disposable
Forge 47.3.0 + TFC + Create world test:
- normal and forced kill before/after each ledger/world/player write (at least 11),
- 2 simultaneous clients, 2 banks, offline payer, full/double/TFC chests,
- personal/municipal rights and treasury balances,
- physical denomination counts and coin NBT before/after each operation,
- operator recovery after full and partial rollback,
- backups and recovery with journals present, including corrupted tail.
Never run destructive tests on the live player's world.
