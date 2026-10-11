# Mongoose conformance scope spec review notes

## Current findings

- The draft selects only stock WSL Toast and ToastCore for first-release conformance.
- It explicitly makes `PROMOTE_NUMBERS` optional and excludes it from phase, default, conformance, and release gates.
- It calls for removing the two Mongoose manifests, the managed-runner route and its regressions, the Phase 4 fixture-count prerequisites, Phase 5 Mongoose builtin gates, and Phase 8 Mongoose runs.
- Current controlling documents still contain the old three-profile requirements; this is expected because the artifact under review is a proposed spec, not the applied edit.

## Observations

- Current direct default-test dependencies on `fixtures/mongoose.db` are in `QueuedTaskV17CodecTest` and `PublicationSchedulerTest`.
- `V17RoundTripTest` has a synthetic test named after an exact Mongoose object shape but does not read the fixture.
- `run_banteng_wsl.sh` owns generic Boolean manifest plumbing; `run_managed_wsl.sh` owns the fixed selected-profile route.
- An additional authority sentence in `docs/reports/integer-value-authority.md` still calls `language/promote_numbers.yaml` an explicitly separate promoted-number profile; it is outside the draft's target-file list.

## State

- Read-only review otherwise remains in progress. No reviewed file has been modified.
- The worktree was already heavily dirty before review.

## Blocker / next action

- Determine whether the omitted integer-authority wording and the draft's ambiguous attribution of generic manifest handling require a CONCERNS verdict.
- Return a concise verdict to the caller; do not write the assigned review artifact.

## Final integration verification

- The corrected draft now names `integer-value-authority.md`, both fixture-bound tests, the generic multiple-inheritance rename, runner ownership, and historical-report preservation.
- Current selected manifests are exactly stock and ToastCore for Toast and Banteng; both Mongoose manifests are deleted.
- `scripts/run_managed_wsl.sh` accepts only `stock|toastcore`, and its regression explicitly rejects `mongoose`.
- No tracked default test, build file, or script reads `fixtures/mongoose.db`; `.gitignore` retains the optional local-fixture entry.
- `PROMOTE_NUMBERS` remains direct optional application plumbing in `run_banteng_wsl.sh` and application tests, while selected manifests disable it.
- Dated identity/audit reports have no tracked diff.
- Live WSL execution of `bash scripts/test_managed_runners_wsl.sh` passed with exit code 0.

## Verification state / next action

- Inspect existing JUnit XML status and timestamps, Markdown/cross-reference integrity, and diff hygiene.
- Distinguish any unrelated pre-existing test failures from this scope before returning PASS or FAIL.
