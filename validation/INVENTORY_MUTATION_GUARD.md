# Inventory mutation guard — integration contract

- Call `TradePhysicalProjectionCoordinator.projectGuarded(...)` for physical projections.
- Use the same `TradeInventoryMutationGuard` instance for each server level / economic authority.
- All enterprise container mutation paths (menu clicks, shift-click, hoppers, item pipes, NPC withdrawals/deposits, block break, explosions, and unload paths) MUST consult `canMutate(accountId)` and reject changes while locked. This integration is NOT implemented by the standalone guard.
- The guard must be held for the entire preflight + both container writes; `Lease.close()` releases it on exception.
- Runtime locks do not survive crashes. Journal-based reconciliation remains mandatory.
- The current adapter has no direct Forge event coverage, and chunk unload behavior is not yet verified. Do not enable live player trades on this basis alone.
