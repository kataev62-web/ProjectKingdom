# NPC approach dialogue implementation plan

> **For agentic workers:** Use superpowers:executing-plans for inline implementation.

**Goal:** Citizens initiate varied prerecorded dialogue on player approach.
**Architecture:** Pure encounter gate and topic policy, one common Forge server
adapter, existing speech transport/client playback, six additional Ogg clips.
**Tech Stack:** Java 17, Forge 47.3.0 / Minecraft 1.20.1, JUnit 5, Ogg Vorbis.
**Spec:** `docs/superpowers/specs/2026-10-27-npc-approach-design.md`

## Global Constraints
- One JAR, six stable voice banks, prepared Russian clips, no runtime synthesis.
- Range 5 blocks, rearm beyond 8; NPC pause 1200 ticks, listener pause 240 ticks.
- Check loaded citizens every 20 ticks; listener radius 16; no chunk loads.
- No automatic economic actions or social-memory mutations.

## Review Focus
- Crowd and two-player approaches must not cause overlapping automatic voices.
- Standing nearby, range jitter and blocked encounters must not build a queue.
- Manual dialogue must suppress automatic interruptions and repeated click hello.
- Disconnect/restart and unloaded citizens must not retain unbounded encounter state.
- Free citizens and self-employed workers need accurate, distinct job claims.

### Task 1: Pure encounter and topic policy
**Files:** `npc/voice/NpcApproachGate.java`, `NpcApproachLines.java`, corresponding tests.
**Interfaces:** Encounter(npcId,playerId,distanceSquared,visible,listeners);
select(long,List<Encounter>) -> List<Encounter>; spoken(long,UUID,List<UUID>);
recentlySpoke(long,UUID,int); clear(); choose(Snapshot,int) -> NpcVoiceLine.
- [x] Test first entry, standing, hysteresis, cooldown, two clients, crowd,
  shared listeners, walls, invalid distance, manual priority and lifecycle reset.
- [x] Test emergencies, day question, jobless state and self-employed state.
- [x] Run tests RED, implement, run GREEN with Java/JUnit local runner.

### Task 2: Forge integration and recordings
**Files:** `NpcApproachSpeech.java`, `NpcDialogueService.java`, `KingdomNpcMod.java`,
`NpcVoiceLine.java`, sound assets/manifests, build/protocol and existing catalog tests.
**Interfaces:** call the Task 1 policy from server END tick and call spoken from
the existing speech boundary. Keep original chat/microphone behavior.
- [x] Add six matching day-question recordings; normalize mono 24 kHz like voice2.
- [x] Integrate loaded-entity checks, line of sight, audience-wide quiet periods;
  skip sleeping NPCs and players in menus. Suppress duplicate click greetings.
- [x] Set voice3 release/protocol and validate all 162 sound events/durations.
- [x] Run full Gradle test/build in existing PR CI, review complete feature diff,
  inspect resulting JAR and save final downloadable release.

## Verification ledger

Task 1: pure gate 9/9 RED, then GREEN; topic policy 3/3 RED, then GREEN.
Fresh review found menu/sleep proximity reset and manual/automatic overlap.
Both findings reproduced by new tests and fixed in one pass: 19/19 targeted
checks GREEN. Added wire-origin roundtrip coverage for full Forge CI.
Upgraded legacy invalid-name crash risk to a required fix: server presentation
text now has safe UTF-16 bounds without cutting surrogate pairs; wire decoder
validation stays strict. Unicode/blank-name tests observed RED then GREEN.
Combined local recorded-assets/pure tests: 29 passed, zero failed.
Runtime audio/render and actual multiplayer acceptance remain manual.
Full Gradle test/build and release inspection are recorded in the PR checks.
