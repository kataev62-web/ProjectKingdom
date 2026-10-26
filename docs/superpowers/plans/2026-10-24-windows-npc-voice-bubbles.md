# Windows NPC Voice and Speech Bubbles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship one Forge 1.20.1 JAR that accepts Russian push-to-talk NPC questions on Windows, routes them through the existing server-authoritative dialogue engine, speaks responses, and renders them in world-space bubbles instead of chat.

**Architecture:** Client-only capture and synthesis are isolated behind `npc.voice.client`; common packets carry recognized text to the server and validated speech events back to nearby clients. `NpcDialogueService` remains the only material-effect boundary. A versioned Vosk model ZIP is embedded in the built JAR and extracted automatically; Windows Speech produces response WAV files asynchronously.

**Tech Stack:** Java 17, Forge 47.3.0 / Minecraft 1.20.1, SimpleChannel, Java Sound, Vosk Java 0.3.45, Windows PowerShell/System.Speech, JUnit 5, Gradle 8.8.

**Spec:** `docs/superpowers/specs/2026-10-24-windows-npc-voice-bubbles-design.md`

## Global Constraints

- The deliverable is one mod JAR for client and dedicated server; no manual model or helper installation.
- First release supports Windows 10/11 x64 for microphone recognition and synthesized speech.
- Client input never bypasses server range, entity, permission, inventory, economy, or cooldown checks.
- NPC speech is not written to chat; chat errors may remain for actionable failures.
- Existing addressed chat, `/knpc talk`, right-click dialogue, economy, professions, construction, and atlas behavior must remain intact.
- Client classes and native voice initialization must never load on a dedicated server.
- Voice failure degrades to the speech bubble and existing villager cue, never to a broken dialogue.

## Review Focus

- A malicious client sends a blank, over-256-character, stale-entity, wrong-dimension, or rapid request: the server rejects it without mutation.
- Vosk model extraction receives an unexpected ZIP path: extraction cannot write outside the versioned voice cache.
- Microphone, Vosk native DLL, PowerShell, Russian Windows voice, or WAV playback is unavailable: the client reports a short HUD state and preserves chat/menu dialogue.
- Two NPCs speak close together or one NPC replies twice: bounded queues replace stale same-NPC speech without leaking processes or audio lines.
- Dedicated Linux server loads the common mod JAR: no `net.minecraft.client`, Java Sound capture, Vosk initialization, or Windows process starts server-side.

---

### Task 1: Pure voice request and presentation policies

**Files:**
- Create: `src/main/java/ru/projectkingdom/npc/voice/NpcVoiceRequestPolicy.java`
- Create: `src/main/java/ru/projectkingdom/npc/voice/NpcSpeechTiming.java`
- Create: `src/test/java/ru/projectkingdom/npc/voice/NpcVoiceRequestPolicyTest.java`
- Create: `src/test/java/ru/projectkingdom/npc/voice/NpcSpeechTimingTest.java`

**Interfaces:**
- Produces: `NpcVoiceRequestPolicy.validate(String text, long nowTick, long lastAcceptedTick): Validation`, with maximum 256 Unicode characters and minimum 40 ticks between accepted requests.
- Produces: `NpcSpeechTiming.durationTicks(String text): int`, clamped to 100–160 ticks.

- [x] **Step 1: Write failing request-policy tests** for blank text, 256/257-character boundaries, 39/40-tick cooldown boundaries, and valid Russian text.
- [x] **Step 2: Run `./gradlew test --tests '*NpcVoiceRequestPolicyTest' --no-daemon`** and verify the missing types fail compilation.
- [x] **Step 3: Implement immutable validation results** without Forge or Minecraft dependencies.
- [x] **Step 4: Write failing timing tests** for empty, short, long, and null text.
- [x] **Step 5: Implement deterministic 100–160 tick timing** based on normalized text length.
- [x] **Step 6: Run both tests** and verify PASS.
- [x] **Step 7: Commit** with `feat(voice): add bounded NPC voice policies`.

### Task 2: Server-authoritative voice and speech packets

**Files:**
- Create: `src/main/java/ru/projectkingdom/network/NpcVoiceRequestPacket.java`
- Create: `src/main/java/ru/projectkingdom/network/NpcSpeechPacket.java`
- Create: `src/main/java/ru/projectkingdom/npc/voice/NpcVoiceRateLimiter.java`
- Modify: `src/main/java/ru/projectkingdom/network/KingdomNetwork.java`
- Modify: `src/main/java/ru/projectkingdom/npc/NpcDialogueService.java`
- Test: `src/test/java/ru/projectkingdom/network/NpcVoiceNetworkContractTest.java`
- Test: `src/test/java/ru/projectkingdom/network/KingdomNetworkPacketRegistryTest.java`

**Interfaces:**
- Consumes: Task 1 validation and duration policies.
- Produces: C2S `NpcVoiceRequestPacket(Optional<UUID> targetEntity, String text)` and S2C `NpcSpeechPacket(UUID entityId, String name, String text, String gender, int durationTicks)`.
- Produces: `NpcDialogueService.respondVoice(ServerPlayer, UUID targetEntity, String text): boolean` and `KingdomNetwork.sendNpcSpeech(ServerPlayer, NpcSpeechPacket): void`.

- [x] **Step 1: Write failing packet contract tests** for maximum decoded lengths, direction checks, enqueueing, sender-only authority, packet IDs 40/41, unique IDs, and protocol `4`.
- [x] **Step 2: Run the packet tests** and verify RED.
- [x] **Step 3: Implement packet codecs and registration**; reject C2S packets received without a server sender and S2C packets received on the wrong side.
- [x] **Step 4: Implement per-player server tick cooldown** with bounded logout cleanup and no client-supplied timestamps.
- [x] **Step 5: Implement `respondVoice`** by resolving the worker from the loaded entity UUID, or by existing explicit-name addressing when target is absent; reuse `respond` for all effects.
- [x] **Step 6: Replace `listener.sendSystemMessage(subtitle)` in `speak`** with one `NpcSpeechPacket` per same-level listener inside 16 blocks; retain the villager cue.
- [x] **Step 7: Add tests** proving stale/wrong-dimension/out-of-range targets never reach `respond`, and dialogue speech no longer writes to listener chat.
- [x] **Step 8: Run packet and existing NPC communication tests** and verify PASS.
- [x] **Step 9: Commit** with `feat(voice): add validated NPC speech transport`.

### Task 3: Client speech-bubble state and world renderer

**Files:**
- Create: `src/main/java/ru/projectkingdom/npc/voice/client/NpcSpeechBubbleStore.java`
- Create: `src/main/java/ru/projectkingdom/npc/voice/client/NpcSpeechBubbleRenderer.java`
- Create: `src/main/java/ru/projectkingdom/npc/voice/client/NpcVoiceClient.java`
- Create: `src/test/java/ru/projectkingdom/npc/voice/NpcSpeechBubbleContractTest.java`

**Interfaces:**
- Consumes: `NpcSpeechPacket` and Task 1 duration.
- Produces: `NpcVoiceClient.acceptSpeech(NpcSpeechPacket)` as the client packet boundary.
- Produces: one active bubble per entity UUID with replacement, expiry, and final 30-tick alpha fade.

- [x] **Step 1: Write failing store tests** for same-NPC replacement, different-NPC coexistence, expiration, fade alpha, and hard maximum active entries.
- [x] **Step 2: Run the store tests** and verify RED.
- [x] **Step 3: Implement the bounded store** with monotonic client ticks and explicit world-disconnect clearing.
- [x] **Step 4: Write a failing source/contract test** requiring `RenderLivingEvent.Post`, Villager UUID lookup, `Font.split` wrapping near 220 pixels, dark translucent background, gold name, white body, camera-facing pose, and no chat API.
- [x] **Step 5: Implement the renderer** above the normal name tag, with depth-tested billboard positioning and 30-tick fade.
- [x] **Step 6: Register client-only Forge events** without any common/server class directly importing the renderer.
- [x] **Step 7: Run bubble and dedicated-server class-boundary tests** and verify PASS.
- [x] **Step 8: Commit** with `feat(voice): render NPC replies as world bubbles`.

### Task 4: Windows microphone capture and embedded Russian Vosk

**Files:**
- Create: `src/main/java/ru/projectkingdom/npc/voice/client/NpcVoiceKeyHandler.java`
- Create: `src/main/java/ru/projectkingdom/npc/voice/client/WindowsMicrophoneCapture.java`
- Create: `src/main/java/ru/projectkingdom/npc/voice/client/VoskRussianRecognizer.java`
- Create: `src/main/java/ru/projectkingdom/npc/voice/client/VoiceAssetExtractor.java`
- Create: `src/main/java/ru/projectkingdom/npc/voice/client/NpcVoiceTargeting.java`
- Modify: `build.gradle`
- Create: `src/main/resources/assets/kingdomnpc/voice/LICENSE-vosk-model.txt`
- Create: `src/test/java/ru/projectkingdom/npc/voice/VoiceAssetExtractorTest.java`
- Create: `src/test/java/ru/projectkingdom/npc/voice/NpcVoiceInputContractTest.java`

**Interfaces:**
- Produces: remappable `key.kingdomnpc.talk` default `V`.
- Produces: `WindowsMicrophoneCapture.start()` / `stop(): CompletableFuture<byte[]>`, 16 kHz mono signed PCM, maximum 8 seconds.
- Produces: `VoskRussianRecognizer.recognize(byte[] pcm): CompletableFuture<String>`.
- Produces: `NpcVoiceTargeting.target(Minecraft, double maxDistance): Optional<UUID>` with exactly 8-block reach.

- [x] **Step 1: Write failing extraction tests** for idempotent versioned extraction, checksum marker, and ZIP-slip entries (`../`, absolute paths, and drive paths).
- [x] **Step 2: Implement safe ZIP extraction** into `gameDirectory/kingdomnpc/voice-cache/vosk-small-ru-0.22` using temporary directories and atomic completion marker.
- [x] **Step 3: Add Gradle Vosk Jar-in-Jar configuration** and a checksum-pinned build task that embeds the official 46,236,750-byte `vosk-model-small-ru-0.22.zip`; include its Apache-2.0 notice.
- [x] **Step 4: Write failing input contract tests** for Windows gating, client-only registration, default `V`, press/hold/release behavior, eight-second cutoff, direct crosshair targeting, explicit-name fallback, and no raw audio network packet.
- [x] **Step 5: Implement asynchronous model loading and microphone lifecycle**; never record or recognize on the render thread.
- [x] **Step 6: Send only nonblank recognized text** through `NpcVoiceRequestPacket`; expose `Слушаю…`, `Распознаю…`, and concise failure states above the hotbar.
- [x] **Step 7: Run input, extraction, networking, and existing NPC tests** and verify PASS.
- [x] **Step 8: Commit** with `feat(voice): add embedded Russian push to talk`.

### Task 5: Windows speech synthesis, integration, and release verification

**Files:**
- Create: `src/main/java/ru/projectkingdom/npc/voice/client/WindowsSpeechSynthesizer.java`
- Create: `src/main/java/ru/projectkingdom/npc/voice/client/NpcSpeechPlayback.java`
- Create: `src/test/java/ru/projectkingdom/npc/voice/WindowsSpeechCommandTest.java`
- Create: `src/test/java/ru/projectkingdom/npc/voice/NpcVoiceEndToEndContractTest.java`
- Create: `NPC_VOICE_WINDOWS_TEST_RU.md`
- Modify: `NPC_COMMUNICATION_STATUS_RU.md`

**Interfaces:**
- Consumes: Task 3 `NpcVoiceClient.acceptSpeech` and entity position.
- Produces: `WindowsSpeechSynthesizer.synthesize(String text, String gender): CompletableFuture<Path>` using `powershell.exe -NoProfile -NonInteractive -EncodedCommand` and `System.Speech`.
- Produces: `NpcSpeechPlayback.play(UUID entityId, Path wav)` with distance volume, listener-relative pan, bounded queue, cleanup, and same-NPC replacement.

- [ ] **Step 1: Write failing command tests** proving Russian text, quotes, semicolons, newlines, and PowerShell metacharacters are data inside Base64 UTF-16LE, never interpolated shell syntax.
- [ ] **Step 2: Implement asynchronous WAV synthesis** with timeout, Russian installed-voice selection, gender preference, unique cache files, exit-code checks, and cleanup.
- [ ] **Step 3: Write failing playback tests** for distance attenuation, pan bounds, same-NPC replacement, maximum queue size, and closed audio resources.
- [ ] **Step 4: Implement Java Sound playback** off-thread and trigger it from the same accepted speech packet as the bubble; on failure retain bubble and villager cue.
- [ ] **Step 5: Add end-to-end source contracts** covering `V` → microphone → local Russian recognition → C2S text → server validation/dialogue → nearby S2C speech → bubble + TTS, with no NPC response chat write.
- [ ] **Step 6: Run `./gradlew test --no-daemon`** and require zero failures.
- [ ] **Step 7: Run `./gradlew build --no-daemon`** and verify the final JAR contains Vosk classes, the model ZIP, voice classes, packets, license, and all current Project Kingdom modules.
- [ ] **Step 8: Run JAR integrity and dedicated-server smoke checks**; document that real Windows microphone/TTS still requires the supplied manual checklist.
- [ ] **Step 9: Update Russian status/test documentation** with controls, fallback behavior, expected first-load delay, microphone selection, and troubleshooting.
- [ ] **Step 10: Commit** with `feat(voice): complete Windows NPC voice experience`.

