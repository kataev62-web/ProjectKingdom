#!/usr/bin/env bash
# Isolated audit only. Does not merge candidate code or publish a JAR.
set -euo pipefail
repo="$(git rev-parse --show-toplevel)"
candidate="${1:-6bdf94853af9968c0f2fce97c5593aef7560d0d7}"
sha="$(git rev-parse --verify --end-of-options "$candidate^{commit}")"
audit="$(mktemp -d "${TMPDIR:-/tmp}/kingdom-pr72-review.XXXXXX")"
git archive "$sha" | tar -x -C "$audit"
cp "$repo/validation/pr72_review/KingdomSmithyPhysicalTransferReviewTest.java" \
    "$audit/src/test/java/ru/projectkingdom/npc/"
printf 'Candidate: %s\nAudit directory (retained): %s\n' "$sha" "$audit"
cd "$audit"
python3 03_verify_project.py
set +e
bash ./gradlew test build --no-daemon --max-workers=2 --console=plain 2>&1 | tee audit-build.log
result=${PIPESTATUS[0]}
set -e
printf '%s\n' "$result" > audit-build.exit
printf 'Gradle exit: %s. Inspect test-results; red conservation tests block integration.\n' "$result"
exit "$result"
