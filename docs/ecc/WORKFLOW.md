# ECC: curated Codex engineering workflow

Audited upstream: [affaan-m/ECC](https://github.com/affaan-m/ECC), version **2.2.3**, commit [`b38be6a7f6350964d59328b52f0624e1d1d552d1`](https://github.com/affaan-m/ECC/tree/b38be6a7f6350964d59328b52f0624e1d1d552d1), reviewed 2026-11-04. This is an explicitly adapted project workflow, not a native ECC plugin, copied skill install, legacy sync, or automatic hook runner.

## What is selected

| ECC reference | Applied here | Deliberate limit |
|---|---|---|
| `skills/verification-loop/SKILL.md` | Build/check/test/security/diff evidence and target acceptance | Use this project's commands; no npm/TypeScript assumptions |
| `skills/security-review/SKILL.md` | Input trust, credentials, permissions, privacy and review | Never print matching secrets; do not replace Windows Credential Manager with plaintext env/config |
| `skills/python-testing/SKILL.md` | Real pytest/JUnit fixtures, regression evidence, red/green/refactor | No invented coverage percentage or mandatory coverage dependency |
| `skills/java-coding-standards/SKILL.md` | Shared Java 17 naming, invariants and exception handling | No Spring/Quarkus/CDI/JPA conventions in Forge; no Java requirements for BENDER AI |

Only these ideas are adapted. Canonical source and SHA256 inventory are in `upstream.json`; upstream license is retained. The whole skills collection, hooks, MCP definitions, models, agents and installers are not copied into this project.

## Common execution contract

1. Inspect task scope, current status, instructions, manifests and existing CI. Preserve all user changes.
2. For a behavior change, first reproduce the failure using a relevant real test. Apply the minimum fix; refactor with tests green. For docs/config-only work, use the native parser and existing meaningful checks; do not add prose-grep tests.
3. Run the project's selected verification commands below, preserve each command's real exit code, and inspect its current results. A build of a different SHA or a zero-test run is not proof.
4. Review the diff: correctness; tests and edge cases; security/privacy; dependency/build/config impact; target-platform acceptance. Use a reviewer when available and authorized; never force extra agents, a model or an external provider.
5. Report build/tests/types/lint/security/diff as PASS/FAIL/NOT CONFIGURED/NOT RUN, with concrete evidence. Record exact failed/skipped/deselected counts and blockers. Do not claim 80% coverage from ECC examples without measuring it.
6. State the next permitted action and separately distinguish portable checks, CI target checks and manual acceptance. Do not commit/push/merge or widen access merely because an ECC document suggests it; use the user's task authorization.

ECC grep examples that print matching credential lines must be replaced with filename/category-only scanning. A failing baseline is reported, not hidden by disabling assertions, broadening permissions, replacing expected checksums or altering unrelated product behavior.

## Official install state and local Codex setup

**Official ECC 2.2.3 is installed and enabled** in this cloud Codex CLI 0.166.0-alpha.1 through `codex plugin marketplace add affaan-m/ECC --ref b38be6a7f6350964d59328b52f0624e1d1d552d1` and `codex plugin add ecc@ecc --json`. The native cache checker passes and a fresh app-server `skills/list` discovers **350 ECC skills** with no errors, including `ecc:verification-loop`, `ecc:security-review`, `ecc:tdd-workflow`, `ecc:python-testing` and `ecc:java-coding-standards`. The upstream manifest/docs advertise 281; 350 is the observed native discovery count, not a claim that every skill was executed. The other 9 discovered skills are existing built-ins.

The initial unsafe install attempt was rejected by automatic approval review. After additional audit, a different, safer installation was approved: first persist **`features.hooks=false`** using `codex features disable hooks`, and set **`plugins."ecc@ecc".mcp_servers."chrome-devtools".enabled=false`**, then install officially with hook execution also disabled for that invocation. Before changing the hook default, the CLI had no installed plugins, user hook file or explicit hooks preference. No existing custom hook policy was overwritten. This global restriction affects lifecycle hooks for the native CLI, not just ECC; models, other feature flags, sandbox and approvals remain inherited.

No hooks were trusted or run, no MCP server started, no npm dependency install or legacy sync performed. Native discovery/cache resolution are verified; hook/MCP behavior is deliberately **not activated or tested**. This long-lived cloud chat does not hot-load a new native CLI plugin into its pre-existing tool catalog; restart/open a new appropriate Codex session to use the native skill surface.

The repo `.codex/config.toml` repeats hooks=false and the ECC browser-MCP deny setting, but leaves plugin enablement inherited from the user. Native `config/read` currently reports both cloud projects untrusted, so repo config is not loaded until ordinary project trust. The **user-level restrictions are already effective** despite that; do not claim the ignored project overlay enforces anything today. AGENTS.md supplies the project guidance. No copied skills are installed into either repo.

For another local Codex computer, inventory before changing anything:

```bash
codex --version
codex plugin marketplace list --json
codex plugin list --available --json
```

Preserve any installed plugins, custom hooks, explicit features and existing ECC source/version. An existing `ecc` marketplace must resolve to affaan-m/ECC; do not overwrite a collision. `alreadyAdded: true` does not prove its Git revision: compare the registry-returned root HEAD with the reviewed SHA before an explicit refresh; a repeated add is not a forced update. If the developer chooses the same skills-only execution posture, review the effects in [SECURITY_REVIEW.md](SECURITY_REVIEW.md), explicitly disable lifecycle hooks with `codex features disable hooks`, and add only the ECC Chrome DevTools MCP `enabled=false` policy to the user's existing TOML **before** installation. On a machine with other custom hooks, do not turn them off automatically; get the user's policy decision or keep using the project guidance alone.

Then use the official lifecycle:

```bash
codex plugin marketplace add affaan-m/ECC --ref b38be6a7f6350964d59328b52f0624e1d1d552d1
codex --disable hooks plugin add ecc@ecc --json
codex plugin list --marketplace ecc --json
```

These commands are for the local maintainer, not permission to reset their configuration. Read back effective hooks/MCP policy, exact installedPath and version. Using that returned, reviewed absolute path, run:

```bash
node "<installedPath>/scripts/codex/check-plugin-cache.js" --plugin-dir "<installedPath>"
```

The checker is in the installed ECC root, **not** either project's scripts directory. Replace the quoted placeholder with the exact CLI-returned path. Registration/path resolution do not prove skill reasoning quality or hook execution. Restart Codex to expose skills; relevant names are in the table above. Choose only the target project's applicable workflows, not the whole catalog. Hook activation is a separate decision: audit changed files/dependencies, adjust the explicit off policies deliberately, then review/trust the exact definition hash in `/hooks`. No hook trust or sandbox bypass flags are appropriate.

Do not mix the native plugin with legacy sync, copy the full ECC rules/skills/config into either repo, or enable browser/observer/session-learning services by default. The official cache necessarily contains the complete upstream bundle; that is distinct from vendoring it into the projects. Existing superpowers/project rules take priority over incompatible third-party examples.

## Kingdom commands and review lanes

Use a real JDK 17 and the existing wrapper. `AGENTS.md` lists the complete command sequence. `03_verify_project.py` verifies structure/version/resource wiring; focused `identity_names_harness` and `warehouse_room_harness` exercise selected domain behavior with stubs. `stage1_harness` is separate and currently fails to compile because its source list/stubs do not include the newer enterprise/property/container dependencies (189 compiler diagnostics in the inspected baseline). This is not caused by ECC changes and is not a Forge compiler verdict. Repair of that harness is a separate test-maintenance task; do not alter mechanics to satisfy it.

Full acceptance remains `./gradlew test build --no-daemon --max-workers=2 --stacktrace --console=plain` on Java 17, followed by relevant authorized Forge client/dedicated-server checks in a disposable world. Preserve `minecraft=1.20.1-47.3.0`, official mappings 1.20.1, metadata version compatibility and Vosk checksum checks. Existing `.github/workflows/build.yml` remains the owner of the Forge build; no replacement CI or gameplay files are introduced.

Review server/client boundaries, packet ownership and server-thread work, chunk loading, SavedData/migrations, deterministic economy/identity invariants and jar metadata. Separate static contracts from runtime behavior. Required destinations include services.gradle.org/GitHub distribution assets, maven.minecraftforge.net, Maven Central, plugins.gradle.org and Minecraft/Mojang dependency hosts; report actual denied hosts rather than requesting broad `*` access.

At initial setup the machine only had an incomplete Java 21 runtime. A verified Temurin 17.0.21+9 JDK was installed under `/workspace/toolchains/` for checks, without changing the system Java. For this instance select that JDK's `bin` in PATH and JAVA_HOME. On a developer computer select their own trusted JDK 17 instead; do not hardcode the cloud path into Gradle. JVMs need explicit proxy properties and the platform CA trust store when running behind the session proxy; never disable TLS verification.
