# NPC approach dialogue

The user requests citizens to initiate a conversation on approach, asking about
the player's day or mentioning real needs such as finding work. Use the existing
six prerecorded Russian voices, subtitle clouds and one client/server JAR.

Implementation decisions: check loaded registered citizens once per second;
enter within 5 blocks with line of sight, rearm only beyond 8 blocks. Consume an
encounter even when another nearby speaker wins: no queue of delayed greetings.
Prefer the nearest citizen deterministically. Keep 60 seconds between a citizen's
automatic addresses and 12 seconds of quiet for every listener within 16 blocks.
Direct dialogue takes priority and extends these pauses. Ignore sleeping/dead
citizens and dead/spectating/sleeping players or players using menus. Automatic
speech changes no contracts, inventory or social reputation.

Choose emergencies from real safety/hunger/energy; otherwise vary friendly lines
and actual needs. Only citizens whose job is `free` and who have no employer say
they seek work: a self-employed farmer must not claim unemployment. Add the day
question to all six voice banks. Keep old recordings byte-for-byte unchanged.
Use a new protocol because an older client cannot decode the new line ID.

Approaches considered: replacing global villager sound events cannot target
registered citizens or detect an approaching player; a client-only trigger can
duplicate speech for multiplayer observers. The server owns encounter selection
and broadcasts one finite recording event through the existing transport.

Manual acceptance: two clients; crowded tavern; wall between player/NPC; standing
nearby; boundary jitter; walking away/back; direct speech during a greeting;
sleep, logout, chunk reload and server restart. No Minecraft runtime is available
in the build host; these game checks remain explicitly unverified.

Suppressed menu/sleep/spectator encounters remain in proximity observations and
consume entry, so closing a menu or waking does not produce a delayed greeting.
Packet origin distinguishes automatic speech; explicit replies stop active
automatic audio and its subtitle while preserving other explicit conversations.
Server-side text bounds protect automatic ticks from invalid legacy names.
