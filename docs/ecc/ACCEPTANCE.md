# Integration acceptance update — 2026-11-04

`STATUS.md` records the initial ECC-only snapshot before the separately requested source fixes. The combined integration branch now deliberately includes these additional changes:

- Source commit f36e111d25285c1bf91d0d40196d33b1dae44b3d: import BlockPos, disambiguate the gallery approach local, and omit a repeated gallery exit when already excavated as a stair landing. Excavation coordinates, depths, galleries and connectivity are preserved.
- Five focused JUnit tests pass locally on Java 17 with temporary Minecraft test doubles; these are partial evidence, not a Forge build.
- Full real Forge Java 17 CI on 0be73241ca5fb91793aa4098e88e4d5bff66fe79 succeeded: 557 tests, zero failures/errors/skips, JAR and JUnit reports uploaded: https://github.com/kataev62-web/ProjectKingdom/actions/runs/41811392213
- Final review then required explicit Bash/pipefail for diagnostic tee. Latest-head CI must also pass; do not infer its status from the earlier run.
- Per-coordinate coverage replaces an invalid scalar minimum that double-counted a shared stair/gallery cell. Existing uniqueness, bounds, connectivity and ten-drop assertions remain. No test is disabled.
- CI branch trigger/reporting changes are intentional. Vosk checksums, dependencies and game content outside the two requested mine classes are unchanged.
- Legacy stage1 javac harness still has unrelated missing source/stub dependencies. It is not part of the Gradle/JUnit pass and has not been removed or relaxed.
- Real Minecraft gameplay/manual acceptance remains unverified. Existing Actions dependency-version deprecation warnings remain; upgrades are outside this targeted fix.
