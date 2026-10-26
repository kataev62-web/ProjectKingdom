# Windows NPC voice recovery and verification

**Goal:** Resume the approved 24 October single-JAR Windows voice design on current main.
**Spec:** docs/superpowers/specs/2026-10-24-windows-npc-voice-bubbles-design.md
**Execution:** ordinary inline implementation, previously approved by user; dedicated clone and branch codex/npc-voice-windows.

- [x] Recover the five local voice commits 24 October, preserving current economy, maps and NPC work.
- [x] Reproduce and fix capture lifecycle: one finalization per press; cancel on GUI, focus loss, logout or dimension change; never deliver recognition into a later connection.
- [x] Bound pending synthesis; invalidate old NPC replies immediately and cancel on world changes. Prove with deterministic Java tests before wiring clients.
- [x] Update spatial volume/pan during playback, respecting Minecraft sound sliders. Prove orientation and mute policy with pure tests.
- [x] Compile full Forge mod, run suite, verify embedded native/model resources and JAR packaging, review whole change.

Review focus: release while microphone opens; 8-second recording finishes before key release; pending synthesis completing after disconnect; many different NPC replies; playback after walking away or muting game.

Ruling: user has already approved this design and ordinary execution; no repeated design approval is needed.
Ruling: local Gradle needs the environment HTTPS proxy; direct Java network failed, curl succeeded. No repository proxy configuration will be added.
Ruling: GitHub push was blocked by automatic approval review as unverified export. Do not retry publication by another tool; finish local verification first.

Verification: full Forge `test build --no-daemon` succeeded with 338 tests, zero failures/errors/skips and reobfJar. Voice/source JDK subset: 53 passed. Embedded model SHA-256 and ZIP integrity passed; actual Linux native model-load/silence smoke passed. Manual Windows client and dedicated-server play tests remain open.

Final review: independent reviewer found pending-synthesis expiry, development Vosk runtime classpath and unbounded pending audio opens. Fixed expiry and bounded audio opens with RED→GREEN behavioral tests; runtimeOnly configuration restored for Forge runClient. No deferred findings.
Ruling: expiry prevents a delayed voice from starting after its bubble lifetime; an already-started sentence may finish unless replaced, muted, out of range or disconnected — avoids cutting words mid-sentence. Cost: text can expire shortly before the current audio ends.
Ruling: pending synthesis and audio-device opens each have a hard cap of 8; newer same-NPC jobs replace older jobs and discarded WAV files are deleted. Cost: excessive simultaneous NPC dialogue drops waiting audio while retaining subtitle bubbles.

Publication follow-up: user explicitly authorized upload to kataev62-web/ProjectKingdom. Git CLI has no write credentials; connected GitHub API limits request bodies to 16 MiB. Source model split into five ordered binary parts; assembleVoskModel reconstructs the same pinned archive at build time. No runtime or installation change. Added reconstruction regression test: RED (missing parts) → GREEN; full suite 339 tests, zero failures.

Integration follow-up: user still receives chat-only NPC replies. Verified current main 4c80e7b NpcDialogueService.speak still sends listener.sendSystemMessage; voice changes were published only to a feature branch. Merge voice with current main, preserve current maps/female-outfit packet mappings, use protocol 6 and visible release 0.1.0-stage0-voice1. Update Windows installer and static version verifier together. Supply actual rebuilt JAR for both client and server; Windows microphone/TTS remains a manual test.
