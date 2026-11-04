## Scope

What changes and why? State explicitly whether gameplay behavior changes. For engineering/ECC work, application mechanics, data formats, dependencies and assets should remain unchanged.

## Evidence

- Source SHA / Java / Gradle / Forge versions:
- Static verifier: PASS / FAIL / NOT RUN
- Focused javac harnesses (identity / warehouse): PASS / FAIL / NOT RUN
- Legacy stage1 harness: PASS / FAIL / NOT RUN (known baseline compile drift is not a pass)
- Gradle/JUnit + Forge jar build: exact command and result / blocker
- Client / dedicated-server / test-world acceptance: PASS / FAIL / NOT RUN
- Test counts / skipped / coverage (only if measured):

## Review

- [ ] Scope and user changes preserved; no unrelated mechanics/dependency edits
- [ ] Client/server classes, packets, permissions, loaded chunks and server-thread work checked
- [ ] Identity, economy invariants, SavedData and migrations checked when relevant
- [ ] No secret values, unsafe hook/MCP activation, broad permissions or checksum bypass
- [ ] Diff and current CI results reviewed; partial/static checks are labelled accurately

## Remaining risks or unrun checks

Include the exact prerequisite/permission needed. Do not mark unrun gameplay or a failing baseline as verified.
