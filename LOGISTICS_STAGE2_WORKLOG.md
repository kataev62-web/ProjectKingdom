# Project Kingdom — Stage 2 logistics worktree (unreleased)

Implemented: persistent supply-order ledger, separate self-pickup and courier modes, seller/buyer IDs, physical source and destination coordinates, item ID, quantity, prices, courier assignment, cancellation, state transitions, payment flags, pickup/delivery counts, NBT save/load. Existing NpcLogistics already moves real stacks chest-to-chest for same-employer territory.

NOT implemented or verified: order creation UI, purchase payment integration, item-specific stock reservation, NPC courier assignment/navigation connected to supply orders, fee transfer, player delivery, automatic source selection, Forge compilation, Minecraft integration test. Do not expose markPaid/recordPickup/recordDelivery to clients: these are trusted server-side primitives and must only be called after validated real money/item movement. Existing NpcLogistics remains limited to same-employer transfers.

Important: do not claim that this is a complete economy/logistics loop or a working JAR. This archive is a private development checkpoint.
