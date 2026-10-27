# voice2 recovery provenance

The working checkout was lost when the execution environment restarted before GitHub publication. Recovery uses the previously delivered and verified `ProjectKingdom_NPC_Recorded_Voice2_Forge_1.20.1.jar`, SHA-256 `85b0ece6c14f1d7a02bce0cf090b41ab07ffb3bd39fc3c536e5f3b951e5c1092`.

All 156 OGG files plus sounds.json and measured-duration metadata are byte-for-byte extracts of that JAR, tracked in recovery-manifest.json. No paid audio regeneration occurred. Three pure catalog/selection classes were recovered from the actual compiled classes with CFR 0.152; the conversation, client, transport and rendering source was reconstructed from the preserved implementation and the unchanged a03d137 base. Gameplay behavior is intended to match the delivered JAR; a new CI build validates recovered source separately. The original packaged JAR previously passed 439 tests. This recovery does not claim to restore lost comments, original generation masters or word-alignment authoring files.
# Voice2.1: replace vanilla citizen audio

Registered Kingdom villagers are marked Silent on creation, enrolment, entity load
and the start of entity tracking. The flag persists in vanilla entity NBT and
syncs to clients. Ordinary, unregistered villagers retain their sounds. There is
no population scan on every game tick and no global Minecraft sound override.

Main-hand interaction now sends the existing GREETING recording through the
same server-validated conversation path as chat and voice input. Custom audio
is played directly by Minecraft's client sound manager, independently of the
villager's Silent flag. All 156 recordings and the six stable voice banks remain
unchanged. No artificial pain/death lines or repeated idle dialogue are added.

Manual in-game checks (not performed in the headless build): create both genders,
enrol an ordinary villager, approach an old saved citizen and reload its chunk;
check that vanilla mumbling/hurt/trade sounds are absent for citizens, the
recorded greeting/replies and their subtitle clouds are audible/visible, and
ordinary villagers still make their normal sounds. Repeat with two clients and
after restarting the server. Install the same voice2.1 JAR on both sides.
