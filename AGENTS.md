# Project Kingdom — engineering instructions

Read `README_START_HERE_RU.md`, `SCOPE_AND_ACCEPTANCE_RU.md`, this file, and any more specific instructions before editing. Existing task requirements and subsystem rules remain authoritative. This file supplements the repository; it does not replace game design.

## Scope and compatibility

- Java **17**, Gradle **8.8** through the checked-in wrapper, Minecraft **1.20.1**, Forge **47.3.0**, ForgeGradle existing `[6.0,6.2)` range, JUnit 5.10.3 BOM. Preserve these unless the user explicitly requests a dependency/compatibility change.
- For tooling/ECC tasks, leave game mechanics, recipes, registries, network protocol, world data, economy, NPC logic, auth and assets unchanged. Runtime changes require a separate user request.
- Do not apply Spring Boot, Quarkus, JPA, web-service layouts, Mockito/Testcontainers or TypeScript commands merely because an ECC example uses them. Reuse the actual Forge/JUnit setup.
- Keep client-only classes out of dedicated-server/common initialization. Check packet sender, ownership/access, loaded chunks and server-thread enqueueing. Preserve identity keys, SavedData schemas/migrations, registries and network compatibility.
- Do not start a Minecraft client/server, accept an EULA, modify a real world, publish a release, download models or change credentials merely to validate an engineering-doc change. Use a disposable test world for explicitly authorized gameplay checks.

## Evidence-driven workflow

Use the curated [ECC adaptation](docs/ecc/WORKFLOW.md) for TDD, verification and review. It supplements the separately installed official ECC plugin; it is not a copied plugin or an installer. Prefer a small change and a meaningful failing regression test for behavior changes, then the minimum fix and relevant/full checks. Do not manufacture tests for human prose or claim an unmeasured coverage percentage.

From the repository root, with `JAVA_HOME`/`PATH` selecting an actual JDK 17:

```bash
java -version
javac -version
python3 03_verify_project.py
bash validation/stage1_harness/run.sh
bash validation/identity_names_harness/run.sh
bash validation/warehouse_room_harness/run.sh
./gradlew test build --no-daemon --max-workers=2 --stacktrace --console=plain
```

- Static verification and the javac harnesses are useful **partial** checks. The harnesses use test doubles; they do not prove Forge compilation or Minecraft behavior.
- `src/test/java` is the Gradle/JUnit suite. `tests/` and `validation/` contain additional legacy/manual/harness material; do not silently treat them all as Gradle-discovered tests.
- Preserve the Vosk model reconstruction, size and SHA256 checks in `build.gradle`; preserve TLS/checksum verification. Do not weaken validation to download a dependency.
- Do not run `clean` by default: retain useful build outputs. Use the existing wrapper, not a globally installed Gradle or a generated replacement wrapper.
- On Windows use `gradlew.bat` with Java 17; the bash harnesses require Git Bash/WSL. Do not describe a Linux harness pass as a Windows/gameplay pass.

## Debugging and review

Reproduce the smallest failing command and read the first relevant `Caused by`, stack trace, Gradle test XML/HTML or Forge `run/logs/latest.log`. Separate JDK/toolchain, network/CA/dependency failures from Java compilation, contract tests and gameplay defects. Never repair a setup failure by changing game rules.

Before completion inspect `git status`, `git diff --check`, the changed-file list and existing CI. Review behavior, client/server separation, persistence/migrations, auth/packets, races and tests. For configuration-only tasks confirm a zero diff under `src/`, `tests/`, `validation/`, `build.gradle`, wrapper files and assets. Preserve user edits and report unrun checks and external blockers explicitly.

## Safety and ECC boundaries

No secret values in output, commits, prompts or reports. Inspect credential names/presence only. Secret scanning reports filenames/categories, not matching lines. No default auto-push, auto-merge, destructive git, shell hooks, external MCP, background observers, learned-memory import or broad filesystem/network grants.

Official `ecc@ecc` 2.2.3 is installed in this cloud CLI; a fresh Codex app-server discovers its skills. Select only `ecc:verification-loop`, `ecc:security-review`, `ecc:tdd-workflow`, and the shared parts of `ecc:java-coding-standards` for relevant tasks. User-level lifecycle hooks and ECC Chrome DevTools MCP are disabled; project `.codex/config.toml` repeats these guards without forcing plugin enablement, models or permissions. Project config only applies after normal project trust; do not auto-trust it. Do not use a legacy ECC sync to work around a rejected native install. Do not bypass hook trust or sandbox/approval controls.

Completion report: changed scope; exact commands and outcomes; passed/failed/skipped/unrun counts where available; Java/Forge target; review findings; artifact/CI provenance; remaining manual game checks. The game is compatible only after the relevant Forge build and target tests succeed.
