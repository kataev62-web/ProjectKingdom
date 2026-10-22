# Project Kingdom: crash-safe economy recovery (work in progress)

## Existing persistence boundary

Enterprise balances and trade metadata are Minecraft SavedData; the physical
stock is in block-entity inventories in independently saved chunks. A durable
intent file and a checkpoint receipt cannot turn those independent writes into
one atomic commit. A receipt created before chunk durability is not proof of
success, and a `SavedData#setDirty()` call does not imply disk persistence.

## Implemented in this worktree

- `TradeRecoveryPolicy` is a pure-Java, fail-closed decision table.
- `KingdomEnterpriseData.recoveryRecommendation` exposes a read-only policy
  decision with `durableSaveVerified=false` until a trusted barrier exists.
- Nine Java test scenarios verify no premature recovery eligibility.
- The existing journal continues to quarantine parties on ambiguous trades.

## Required before automated recovery

1. Choose a single authoritative durable record for BOTH currency and goods
   or implement a recoverable escrow with physical inventory reservation.
2. Serialize all changes in one authority, fsync the authority, then project
   the state into Minecraft block entities idempotently using transaction IDs.
3. Persist completion receipts after the projection is verifiably durable;
   crash-inject at every fsync, rename, SavedData and chunk write boundary.
4. Make restart replay idempotent, and prove zero currency/item creation or
   loss across repeated restarts and parallel buyers.
5. Run a real Forge 1.20.1 server build and shutdown/crash integration tests.

Do not infer automatic unlock from a receipt or from `AFTER` alone.
