# Forge event guard — partial integration

`TradeProtectedLocations` is a runtime-only index. The server must rebuild it from
persisted enterprise-to-container bindings before enabling guarded projections.
`TradeForgeInteractionGuard.install(index)` is intentionally NOT called yet:
there is no verified durable container binding source covering all trade accounts.

Events implemented: right-click block, break block, explosion affected blocks.
NOT covered: already-open inventory menus, hoppers, Create transport, modded item
handlers/capabilities, pistons, chunk unload, external code mutating containers.
Do not enable real physical projections until all mutation paths are isolated,
preferably through a dedicated managed inventory with a single mutation API.
