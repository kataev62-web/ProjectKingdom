# Development worklog — NPC builder (unreleased)

Implemented in source tree:
- `KingdomNpcBuilder`: a real villager with employer=project owner, job=builder, order=work, within 48 blocks navigates to lectern and places one blueprint block every 10 server ticks, only after removing the corresponding real item from a chest/barrel immediately adjacent to the lectern.
- Server-tick integration in `KingdomNpcMod`; builder job available in NPC menu and spawn command.
- Site menu now starts/pauses an NPC construction order instead of instantly building 32 blocks from player's inventory.
- `KingdomConstructionData` persists project status/progress and owner-validated start/pause.

Verified: plain-Java `BuildingPlanSmokeTest` passes for all five building types.
Not verified: Forge compilation, Minecraft runtime, NPC pathing, material conservation under chunk unload/restart.
Not yet implemented: ghost preview/rotation, MineColonies-equivalent building controller, upgrades, completed-building management, TFC block recipes, linked functional production.

This is NOT a release; don't describe it as a completed MineColonies replacement.
