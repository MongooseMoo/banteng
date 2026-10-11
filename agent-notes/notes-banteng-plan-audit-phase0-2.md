# Banteng plan executability audit: Objective through Phase 2

## Current findings

- Audit scope is `docs/reports/banteng-implementation-plan.md` from Objective through Phase 2, using only Banteng and the plan's named sibling references.
- All five relative sibling paths declared at plan lines 16-18 currently resolve.
- Phase 0's managed profile and launcher files exist, but the launcher does not mechanically verify the manifest's Toast commit or executable identity before launch; the phrase "pinned Toast executable" is therefore not an executable gate as written.
- Phase 0's "Barn has no Banteng-authored changes" cannot be evaluated without a recorded pre-execution Barn HEAD/status baseline; authorship is not derivable from current Git status alone.
- Phase 1's named build artifacts, CLI, package skeleton, ArchUnit test, tool source sets/tasks, static-analysis configuration, JetCheck spike, and executable smoke test exist in the current tree.
- Phase 2's "focused conformance categories" gate names no exact selectors or commands, and its four JetCheck obligations are not represented by the current JetCheck acceptance test.
- `VmState` contains a `CompletableFuture` host result and process CPU timing anchor; the plan's claim that every suspension-relevant state is serializable data is not an executable or currently satisfied gate without an explicit snapshot schema and round-trip test.

## State and blocker

- No source, test, plan, or sibling repository has been edited by this audit.
- A complete defect list and literal replacement wording are still being assembled.
- The only blocker was this mandatory notes checkpoint; this file satisfies it.

## Next action

- Finish checking Phase 2's exact conformance selectors and test obligations, then return the concise line-by-line defect/replacement list to the parent agent.

## Phase 0 implementation inventory

- `scripts/verify_toast_profile_wsl.sh` and `scripts/test_verify_toast_profile_wsl.sh` do not exist.
- `scripts/run_toast_wsl.sh` still accepts only database and port, permits `TOAST_MOO` override, performs Windows-path conversion and NAT `/etc/hosts` mutation, and does not run manifest preflight.
- `scripts/run_banteng_wsl.sh` still accepts only database and port, requires `BANTENG_JAVA_HOME`, launches Windows `java.exe`, converts paths to Windows, and does not accept/read a target manifest or select `PROMOTE_NUMBERS`.
- All three Toast manifests lack the required source path, executable checksum, canonical fixture path, login/standard-property fields, and `test_suites`; ToastCore also lacks its fixture-source path/commit fields.
- `../barn/mongoose_fresh2.db` exists; `fixtures/mongoose.db` does not.
- `scripts/run_toast_wsl.sh` is tracked as mode `100644`; `scripts/run_banteng_wsl.sh` is `100755`.

State: inventory was read-only except for this mandatory notes checkpoint. No script, profile, fixture, source, test, or sibling repository was modified.

Next action: return the exact file-by-file missing behavior for Phase 0 items 1-5 and 10.
