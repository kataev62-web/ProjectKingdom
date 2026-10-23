# GDMC dock-middle — conversion and QA report

**Status:** PREVIEW ONLY / NEVER AUTO-PASTE INTO AVALON

## Provenance

- Source: https://github.com/williamcwi/GDMC/blob/master/schematics/dock/dock_middle.schematic
- Source license: Apache License 2.0, preserved in `GDMC_APACHE-2.0_LICENSE.txt`.
- Unmodified original: `gdmc_dock_middle_legacy.schematic` — SHA256 `ac570af9955a7ca8ebb5a056d8e8a7b74ce368e821a8b14477246787c83b68ca`.
- Converted derivative: `gdmc_dock_middle_converted_1_20_1.schem` — SHA256 `0bdf041242ad4fe712d4e1fdee3e8724e08835d725d81569228c913c6f815cbe`.
- Conversion is a modification of the upstream work; original attribution and Apache 2.0 conditions retained.

## Confirmed technical facts

| Field | Original | Converted |
|---|---|---|
| Type | MCEdit legacy NBT | Sponge schematic v2 |
| Width / Height / Length | 20 / 8 / 9 | 20 / 8 / 9 |
| Volume | 1,440 | 1,440 |
| Non-air blocks | 333 | 333 |
| Block representation | numeric ID + numeric meta | namespaced Minecraft blockstates |
| Palette | — | 30 distinct blockstates |
| NBT DataVersion | — | 3465 (MC 1.20.1) |
| Compression | gzip | gzip |
| TileEntities / Entities | 0 / 0 | none imported |
| Runtime test | NO | NO |

Block ID conversions used: 0 air; 4 cobblestone; 5 wood planks with species metadata; 17 oak log; 50 torch/wall torch; 53 oak stairs; 85 oak fence; 126 wood slab with species/half metadata; 135 spruce stairs; 160 colored glass panes; 164 dark oak stairs; 188 spruce fence. Stairs receive facing/half properties, slabs their half, fences/panes initial adjacency estimates based on neighbors.

## Known risks

- Converted `.schem` has been structurally read, but **has not been pasted in running Minecraft/Forge/WorldEdit**. Some directional states may require edits.
- This is **one modular section of a pier**, not a complete Avalon port. Its original visual quality is unknown until first-person screenshots exist.
- The source represents an older vanilla block palette, not the TFC/Chisel/DOM palette. Adaptation is separate work.
- No terrain preparation, foundations, bridges or auto-placement are included.
- Inspector `inspect_schematic.py` validates format and metadata, not architectonic merit.

## Test procedure

1. Use a **separate blank 1.20.1 Forge staging world** (not the Avalon save).
2. Copy the converted file into the WorldEdit Forge schematic directory.
3. Load with `//schem load gdmc_dock_middle_converted_1_20_1` then `//paste -a` from an appropriate water-level anchor.
4. Inspect fence/pane connections, stairs, underwater supports, walking access and lighting.
5. Take screenshots from player height and bird's-eye.
6. If structurally/visually unsuitable, **reject**. Do not promote to approved library merely because NBT loaded.

## Tooling

Run `python inspect_schematic.py schematics/review/gdmc_dock_middle_converted_1_20_1.schem` from the library root to inspect the structural metadata.
