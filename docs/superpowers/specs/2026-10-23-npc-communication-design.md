# NPC Communication Design

## Intent

NPC communication must feel like talking to a resident, while every material action remains server-authoritative. A player addresses a nearby NPC by name in Russian, receives a state-aware Russian reply visible to nearby players, and can fall back to a compact dialogue page and the existing management menu.

## Goals

- Understand a bounded set of Russian intents: greeting, identity, needs, work, task/help, trade, hiring, dismissal, relationship, and management.
- Base replies on the real persisted NPC state: profession, needs, housing, contract, workstation, order, and relationship.
- Let a player address a nearby NPC by name in chat or use `/knpc talk <id> <phrase>`.
- Show the NPC reply as a subtitle-like chat message to players within 16 blocks and play a villager voice cue at the NPC.
- Open a dialogue-first NPC menu with buttons for the same common topics and a clear route to the existing management actions.
- Persist per-player conversation memory and reputation. Giving requested bread is a real, inventory-backed help action.

## Non-goals

- No cloud speech recognition, generated dialogue, or mandatory external service in this stage.
- No synthesized Russian audio yet; the voice transport is represented by an audible in-world NPC cue plus full Russian subtitles.
- No free rewards, money, items, remote trade, ownership changes, or direct contract mutation from natural-language parsing.
- No MineColonies dependency or architecture.

## Constraints

- Forge 1.20.1 / Java 17.
- Nearby interaction only: 8 blocks for conversation, 16 blocks for listeners.
- Scripted intent engine must be deterministic, role-limited, and tied to current server state.
- All transactions, hiring/firing, orders, and world changes continue through existing validated menus and services.
- Existing saves load with neutral social memory.

## Approaches

1. Cloud speech/LLM service: closest to unrestricted speech, but introduces credentials, latency, privacy, cost, and non-deterministic authority. Rejected for the core mod.
2. Fully local speech recognition and TTS: offline, but requires large native/model dependencies and would dominate the JAR. Deferred behind an adapter boundary.
3. Deterministic Russian intent engine with addressed chat, menu fallback, subtitles, and in-world sound: selected because it is reliable, testable, low-load, and safe for the existing economy.

## Components

- `NpcDialogueEngine`: pure Russian intent classification and state-aware response selection.
- `NpcAddressing`: pure extraction of `Имя, фраза`, `Имя: фраза`, or `#id фраза` for nearby candidates.
- `NpcSocialMemory`: immutable per-player memory and reputation rules.
- `NpcDialogueService`: proximity validation, persistence, inventory-backed help, nearby subtitle broadcast, and sound cue.
- `KingdomNpcMenu`: dialogue page first; management page preserves current controls.
- `KingdomNpcMod`: `/knpc talk` command and addressed-chat event wiring.

## Acceptance

- The pure engine recognizes every supported intent and gives different replies for hungry, tired, unsafe, unemployed, unpaid, and active workers.
- Duplicate names resolve to the nearest matching NPC; malformed or unaddressed chat remains normal chat.
- Conversation memory survives save/load and never grants authority.
- A bread-help action consumes exactly one real bread and increases hunger/reputation only after successful consumption.
- Players farther than 8 blocks cannot converse; only listeners within 16 blocks receive the subtitle.
- Right-clicking a tracked NPC opens dialogue first and still exposes every existing management action.


