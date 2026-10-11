# Issue execution wave - 2026-08-18

## Current state

- User reauthorized tools and subagents with "do your work" after the prior no-tools restriction.
- Requested workflow remains isolated worktrees, tests-first commits, CI-only test execution, exact-head monitoring, and normal merge commits.
- PR #41 (`agent/issue-3-object-flags`) was opened from exact head `2f08d195bb609a36d57fe64b4514a1ab48329294` with `Closes #3`.
- The first PR #41 watcher snapshot reported 1 passed, 4 pending, 0 failed, mergeable `MERGEABLE`.
- Subagent lanes own #6 and #7. The #23 lane found a literal dependency on #7 and was redirected to #17 without editing #23.
- Root created `C:\Users\Q\code\banteng-agent-issue-21` at `4880f025b2e396548bce7cbeb93a670a46b4f2e6` for the fifth stream.
- No local Gradle build or test has been run in this wave.

## Issue #21 observations

- Live issue requires `.gitignore` additions, tracking `investigations/resume-after-suspend-background-limits.md`, deleting `StringValue.equalsCaseSensitively`, and demoting `WorldTxn.stageEffect` plus `ToastV17ProgramLayout.resolveToastFinallyLabel`.
- Primary checkout holds the untracked investigation file and the other user-owned notes/scratch paths; preserve everything outside issue #21's explicit targets.
- The requested ArchUnit rule needs an implementation that does not create false failures for legitimate external public APIs.
- Attempt to inspect a cached ArchUnit jar found no Gradle cache at `C:\Users\Q\.gradle`; no build was invoked.

## Blockers

- #23 must wait for #7 before its unary-float consolidation can honestly close the issue.
- No blocker is currently known for #3, #6, #7, #17, or #21.

## Next actions

1. Continue consuming PR #41 watcher output and diagnose any exact-head failure.
2. Add issue #21 RED repository-hygiene/visibility contracts and commit tests only.
3. Implement the narrow #21 source and ignore changes, preserve commit boundaries, then push/open its PR.
4. When any exact-head PR becomes green, verify files, commits, issue scope, closing phrase, and normal merge command before merging.

## Merge gate update

- PR #41 exact remote/local head is `2f08d195bb609a36d57fe64b4514a1ab48329294`.
- GitHub reported all six check runs `SUCCESS`, `mergeStateStatus=CLEAN`, and `mergeable=MERGEABLE`.
- PR #41 is non-draft, targets `master`, contains exactly the tests-first commit `e7d2bb7` and production commit `2f08d19`, and its body begins `Closes #3`.
- The branch-protection API returned HTTP 404 (`Branch not protected`); the six visible exact-head checks are therefore the complete live check rollup rather than a protected required-check set.
- Final diff is the 13 issue-owned object-flag production/test files already audited; `git diff --check origin/master...HEAD` emitted no diagnostics.
- Exact authorized merge command remains `gh pr merge 41 --repo MongooseMoo/banteng --merge`.

## Issue #21 implementation update

- Tests-first commit: `d6e28dd test: define repository hygiene contract`.
- Production commit: `f54c654 fix: clean repository hygiene surface`.
- Branch is clean and ahead two; no local build/tests were run.
- The untracked Ghidra project was moved without overwrite to `C:\Users\Q\pyghidra_mcp_projects\banteng-oracle-project-from-repo` because the requested home root already contained a different project database.
- The exact generated `C:\Users\Q\code\banteng\moo` tree contained 55 class files, no non-class files, and no reparse points; it was sent to the Windows Recycle Bin and is recoverable.
- The tracked investigation copy and primary untracked source have identical SHA-256 `C6FBC291CBED278F1A343244E1BD043B2C72C3CBA361D3D51FD0998E9EE9838F`.
- The exact #21 PR title/body was shown to the user; next step is push/open PR and start its watcher.

## Delivery update 2

- PR #42 for issue #21 was repaired on CI with test-only import fix `ab2ed28`; exact-head rollup then reported six successes, CLEAN, and MERGEABLE.
- PR #42 merged normally as `ed6b030194eac1b8d915b19de5ade124c622d224`, with parents `daa0d5e...` and `ab2ed28...`; issue #21 is closed and feature-head ancestry returned exit 0.
- PR #43 for issue #6 is open at head `b07b4b1a81c7cc519daca34dcea8b924bc8e0d44`; its subagent owns current CI diagnosis/repair.
- PR #45 for issue #7 is open at head `6e8651a802ab8a6a51a7bdf30479c45a9ff2ae18`; its watcher initially reported five pending checks and no failures.
- PR #44 for issue #20 was opened at `12a6aa5`; CI's GuardedBy failures identified three real missing lock contracts. Repair `6471048166a4e952ed9109be42ef7d74e966cd1e` synchronizes queued-task ID restoration and active-connection snapshotting, and marks two WorldHistory helpers as requiring the history monitor. New CI is pending.
- Issue #17 remains in the implementation lane; its PR packet has not yet been received.
- No local Gradle build/test has run. All behavior/static execution remains in GitHub CI.

## Immediate next actions

1. Continue PR #44 monitoring and repair any remaining exact GuardedBy findings.
2. Receive and disclose #17's exact PR packet, then authorize its publication.
3. Merge #6/#7/#20/#17 only after each final files/commits/scope/closing/check gate.
4. After #7 merges, resume #23 because its unary-float item explicitly depends on promote-numbers.

## Delivery update 3

- PR #45 reached exact-head green at `9d8da65ee6d6d17327750791464b6d9b2f9283e2`: six successes, CLEAN, MERGEABLE, no review items, clean worktree, and empty diff-check.
- Final scope gate found a possible literal mismatch: issue #7 requires porting the Mongoose `test_math.rb` promotion tests as conformance tests, while PR #45's 14-file list contains Java tests but no conformance fixture/YAML file.
- Issue #7 also says each remaining unsupported construct is a tracked completeness gap requiring audit/implementation; the PR file summary shows only small diagnostic edits in compiler/codecs, so those diffs require direct review before merge.
- PR #45 must not merge until those two scope requirements are either proven present through existing Java/conformance infrastructure or repaired on the branch.
- PR #44 third repair is `794e7058ced4fad1199acab8be2108aafa58df1e`; CI is running after compiler findings drove explicit synchronized helper boundaries and a lambda-local snapshot for WorldHistory retention.
- PR #46 for issue #17 is open; its subagent has already pushed at least one CI repair (live head advanced from `d9304ee` to `2d75f6c`).

## Delivery update 4

- PR #43 reached six exact-head successes at `0602bee20e5bd2788355b2b14760ef3a703a4754`; final gate confirmed only `ServerLog` itself retains `System.err` as the default sink.
- PR #43 merged normally as `f507d1bd7f2a0b8401cd83c2815991ce52bbbd13`, with parents `ed6b030...` and `0602bee...`; issue #6 is closed and feature-head ancestry returned exit 0.
- The former #6 agent is assigned issue #18 after its #43 watcher observes the closed terminal state.
- PR #45 remains unmerged despite green because the literal conformance-test and unsupported-site audit requirements are not yet proven. Its agent was instructed to repair or return exact evidence.
- Merging #6 made PRs #45 and #46 conflict on shared runtime/catalog files; both agents were instructed to update onto current master, preserve both behaviors, and rerun CI.
- PR #44 passed the GuardedBy compiler stage but `compileTestJava` failed because CLASS-retained annotations were absent from the test compile classpath. Repair `696b465` adds `testCompileOnly` and the locked `testCompileClasspath` entry.
- Because #6 changed `MooRuntime`, PR #44 was cleanly rebased from old remote/local `696b465286e556c79e60d6deaf08886fa1ace7bb` onto current master `f507d1bd7f2a0b8401cd83c2815991ce52bbbd13`; all seven commits replayed without conflict. The rewritten head has not yet been pushed.

## Immediate next actions 2

1. Verify the rebased #44 diff/commit sequence, force-push with an exact lease on old head `696b465...`, and restart CI monitoring.
2. Continue #45 scope audit and conflict repair; merge only after current-base CI and literal issue proof.
3. Continue #46 conflict/CI repair and inspect its final scope before merge.
4. Start #18 in the freed agent lane and resume #23 only after #7 actually merges.
