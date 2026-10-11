# Issue #3 object flags delivery

## Current findings and observations — 2026-08-18

- Live issue #3 is open and requests `moo.world.ObjectFlags` as the single home for object flag bits and the required raw-bit predicates.
- Exact base is merge commit `4880f025b2e396548bce7cbeb93a670a46b4f2e6` on `origin/master`.
- Isolated worktree: `C:\Users\Q\code\banteng-agent-issue-3`; branch: `agent/issue-3-object-flags`.
- Pinned Toast authority is checkout `/root/src/toaststunt` at `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Toast object flag values are USER=1, PROGRAMMER=2, WIZARD=4, obsolete/reserved=8, READ=16, WRITE=32, obsolete/reserved=64, FERTILE=128, ANONYMOUS=256, INVALID=512, RECYCLED=1024.
- `FLAG_ANONYMOUS` is a permission bit for anonymous-child admission, not an anonymous-object type marker; no `isAnonymous()` predicate should be added.
- The issue undercounts production scope: 32 bare wizard masks, 3 programmer masks, 3 read masks, 5 write masks plus one mutation, fertile projection/mutation, and 7 duplicate private constants.
- A blanket `& 4` ban is invalid because property/verb permissions, POSIX mode bits, and noise math contain legitimate unrelated uses.
- Tests-first commit: `4e81f9f test: define canonical object flag contract`.
- Production commit: `35007c3 fix: centralize object flags`.
- Current branch is clean and ahead 2. `git diff --check` emitted no diagnostics.
- Current full diff is 8 files: six existing production consumers, new `ObjectFlags.java`, and new `ObjectFlagsTest.java`; +201/-64.
- No Gradle build or test has been run locally; CI remains the executable verification authority.

## State

- Production migration is committed locally and unpublished.
- Independent authority and source-inventory reviews are complete.
- A follow-up audit is required because several test fixtures/assertions may still spell object flag values numerically outside `ObjectFlags`; only unambiguous object-flag sites should migrate, while unrelated permission masks must remain.

## Blocker

- No implementation blocker. Publication is intentionally blocked until the actual-head diff is independently audited and the exact PR title/body is shown to Q for approval.

## Next action

- Inspect the final commits and searches, incorporate any missing unambiguous test-side object flag migrations while preserving tests-first history, run structural checks only, then obtain an independent actual-head verdict and prepare the exact PR packet with `Closes #3`.

## Final local candidate checkpoint — 2026-08-18

- Final tests-first commit: `e7d2bb70bfe7d5d2f99c8db49352cda8b3cad4b7 test: define canonical object flag contract`.
- Final production commit: `2f08d195bb609a36d57fe64b4514a1ab48329294 fix: centralize object flags`.
- The strengthened source contract rejects numeric canonical flag bits passed to `replaceFlags` as well as numeric object-flag masks and duplicate postfix constants.
- Final independent actual-head verdict: `MERGE`; no source, semantic, architecture, scope, or commit-boundary blocker found.
- Fresh remote check: issue #3 remains open, `origin/master` and merge-base are both `4880f025b2e396548bce7cbeb93a670a46b4f2e6`, no PR exists for the branch, and the worktree is clean/ahead 2.
- Final merge tree before publication: `f62cf1de16cea757fec6f16d3319af69691b3341`.
- Exact proposed title: `Centralize object flag checks`.
- Exact proposed body begins `Closes #3` and lists `./gradlew test` as CI verification.
- Publication remains blocked only on Q reading and approving the exact PR packet. No local Gradle build or test has run.

## Follow-up checkpoint — 2026-08-18

### Findings and observations

- The unpublished history was rewritten after the test-fixture audit: tests-first commit `1119ffad7060ec551d8d9639d27c6b56af32b09b`, production commit `6e1bdd037c22d3e7d4b5f2cecd0dac97ec5f4e59`.
- The tests-first commit now owns the focused contract test and unambiguous object-flag fixture/assertion vocabulary changes; the production commit owns only `src/main` changes.
- Current production and test searches find no numeric `flags & literal` object masks and no legacy postfix flag constants.
- Final audit identified one remaining contract-test hole: the source guard does not detect a numeric canonical bit passed directly to `replaceFlags(object, literal, enabled)`.
- No Gradle, build, or test command has run locally.

### State

- Branch `agent/issue-3-object-flags` is clean, unpublished, and two commits ahead of exact base `4880f025b2e396548bce7cbeb93a670a46b4f2e6`.
- Implementation behavior is unchanged; only the tests-first source-enforcement boundary needs tightening.

### Blocker

- Publication remains held until the mutation-argument enforcement hole is closed and the two-commit history is re-audited.

### Next action

- Add focused multiline-capable detection for numeric permanent object bits passed to `replaceFlags`, autosquash it into the tests-first commit, replay the unchanged production commit, and report the new hashes plus structural evidence.

## Current-master follow-up and PR #55 — 2026-08-18

- Existing issue-3 branch was clean and fully contained in current master through normally merged PR #41. It was fast-forwarded to exact master/base `c278f236e5c619dfc7d46540bf14fe8fa71db8fa` before adding the follow-up.
- Tests-first commit `ef52f35f67acadcf85abdbd2f57e9cc64c40f249` adds a RED syntax matrix for numeric permanent object-flag `&`, `|`, `^`, reversed operands, compound mutations, complement-clear, and multiline `replaceFlags`; it also pins Toast `FLAG_USER` naming and ANONYMOUS parent-permission behavior.
- Repair commit `33fccf57c3c6d86bffee35210ce3152666508954` broadens only the test-side source detector. Production behavior is unchanged. Final diff is exactly `BuiltinCatalogTest.java` and `ObjectFlagsTest.java`; `git diff --check` emitted no diagnostics.
- Static residuals before publication: zero numeric object-flag operations, zero numeric permanent bits passed to `replaceFlags`, zero legacy postfix constants, and zero `FLAG_PLAYER`/`isAnonymous(...)` production surface.
- Q read the exact packet. Branch was ordinary fast-forward pushed from remote `2f08d195` to `33fccf57`; non-draft PR #55 is `https://github.com/MongooseMoo/banteng/pull/55` with exact approved title/body and base `c278f236`.
- Exact-head CI job `95899157093` failed only `ConcurrentSchedulerStressTest.saturationConflictsAndRetriesRemainBoundedAndEventuallyProgress(Path)` with `TimeoutException` at line 67: 538 tests completed, 1 failed. This is unchanged unrelated code and matches the known flaky stress signature; do not alter issue-3 files or scheduler tests.
- Continuous babysitter session owns PR #55. After pending jobs terminate, use the allowed same-head failed-check retry (maximum three) and keep watching. Never merge.
