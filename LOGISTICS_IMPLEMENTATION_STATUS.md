# Stage 2 status (unreleased)

Implemented in source: persistent order ledger, courier villager pathing between real source and destination containers, physical one-stack-at-a-time pickup and delivery, loaded-chunk guard, payment flags required before courier proceeds, NBT worker cargo persistence.

Not implemented: warehouse ordering GUI, cash escrow transfer, seller discovery, cart inventory adapter, AstikorCarts cart-follow visual, animal navigation, Forge compile verification. No claims of functional AstikorCarts integration.

Known limitations: pickup ledger is updated before inventory extraction; exotic container implementations that violate Container.removeItem contract may cause reconciliation discrepancy. Payment flags must only be set by a real cash transaction handler. The courier does not currently automatically set worker.order or assign itself to a new order.
