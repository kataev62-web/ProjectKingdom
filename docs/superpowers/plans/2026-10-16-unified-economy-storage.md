# Unified Economy Storage — implementation plan

Goal: Make the authoritative balances and enterprise inventory one atomic persistence domain instead of mixing chunk inventories, SavedData and trade journal files.

## Delivered in this block
- `TradeAtomicStore`: world-scoped versioned, checksummed, atomic snapshot file with CAS generation and cross-process write lock.
- `validation/TradeAtomicStoreTest.java`: isolated file roundtrip, stale write, restart, foreign world, corrupt bytes and orphan temp.

## Remaining integration (NOT delivered)
1. Inventory ownership: identify all real item/coin mutations in `KingdomEnterpriseData`, cash containers, NPC workshops and player interactions. Decide whether physical inventories are projections or authoritative; prohibit dual authority.
2. Serialize the *entire* authoritative economy (wallets, cash, stock, trade state and reservations) into a deterministic single snapshot. Add migration/versioning and size limits.
3. Route trade mutation through a single-threaded coordinator: validate both sides, create candidate snapshot, commit once, then update in-world projections. Never acknowledge a trade before durable commit.
4. Add recovery on server startup: load snapshot before NPC ticks, reconcile all physical projections, block interaction while reconciling; reject incompatible worlds or corrupted data without minting or replaying assets.
5. Test kill points before/after snapshot write and rename, stale inventory projection, concurrent trade requests, duplicate order IDs, and container chunk unload. Verify no net coin/item change across process restarts.
6. Run ForgeGradle compile and real Forge 1.20.1 + TFC integration tests on an accessible runtime; keep release blocked until then.

Safety: `TradeAtomicStore` by itself does NOT make existing Minecraft inventories atomic. Do not wire checkpoint auto-unlock to this primitive until all authoritative balances and goods are moved under one commit boundary.
