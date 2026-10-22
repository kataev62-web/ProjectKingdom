# Physical writer integration boundary

`TradeMinecraftPhysicalWriter` is intentionally NOT activated from any game event.

- Run on the server thread, with both account inventories exclusively owned by the trade coordinator.
- Never force-load chunks; unloaded or non-container blocks fail closed.
- `CashAccess` must reference the physical cash authority, not a second independently saved ledger.
- Do not assume `Container#setChanged` is durable; a crash can leave one side changed.
- Projection requires a durable receipt, a complete pre/post audit, and manual reconciliation for mixed states.
- Do not enable this adapter until the Forge build, inventory ownership rules, container save semantics and crash tests are verified.
- External inventory handlers may bypass `Container` and must be excluded or wrapped.
