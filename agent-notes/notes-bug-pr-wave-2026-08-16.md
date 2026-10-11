# Bug PR wave coordination

## Scope

Resolve all open issues labeled `bug` with isolated worktrees and subagents, TDD,
ready pull requests, exact-head CI monitoring, and normal two-parent merge commits.

## Current base and repository state

- Fetched `origin/master` is `d50652d1ff4b643a759812e4cd37e4a5ca85b1f9`.
- The CI push run for that exact SHA completed successfully.
- GitHub reports that `master` is not branch-protected, so each PR still needs all
  current workflow jobs checked explicitly before merge.
- The primary checkout contains many pre-existing untracked notes/reports and is
  not used for implementation or staging.
- No PR existed when this wave began.

## Open bugs and sequencing

- First parallel wave: #4 programming feedback, #5 real verb cache, #27 queued-task
  source/dispatch metadata.
- Follow-on finalization lane: #1 then #2, sequential because both touch VM/runtime
  finalization state.
- Follow-on persistence lane: #28 then #26, sequential because both touch hierarchy
  validation/repair semantics.

## Active worktrees

- `C:\Users\Q\code\banteng-agent-issue-4` on
  `agent/issue-4-program-feedback`, based on `d50652d`.
- `C:\Users\Q\code\banteng-agent-issue-5` on
  `agent/issue-5-verb-cache`, based on `d50652d`.
- `C:\Users\Q\code\banteng-agent-issue-27` on
  `agent/issue-27-queued-task-fields`, based on `d50652d`.

## Observations

- All three workers established test-first deltas before production changes.
- #4 currently touches MooCompiler/MooRuntime and focused tests. Early audit asked
  the worker to prove plural/recoverable diagnostics and descriptor matching, not
  merely one hard-coded syntax error.
- #5 currently adds a world-owned verb cache and routes live cache statistics
  through WorldHistory/WorldTxn/BuiltinCatalog with focused cache tests.
- #27 currently carries first source line, called verb, and full matching verb name
  through LambdaMooV17Codec and task/scheduler/VM state, with a minimal v17 fixture.
- Pinned semantic authority remains WSL Toast; managed wrapper/profile evidence is
  required where observable strings or persistence grammar are involved.
- Workers were told not to push or create PRs. The user guardrail requires Q to read
  the final publication packet first.

## Current state

- User clarified that CI, not broad local reruns, owns the full verification
  matrix. Workers retain only their focused local TDD red/green evidence.
- Issue #5 is committed locally as
  `c620f166100c96640b140ddef696369cbc3e111e` with one commit and no publication.
  Its candidate diff has seven paths and `git diff --check` emitted no errors.
- Issue #27 is committed locally as
  `ddab89c76194bb6d822610549be781176794826e` with one commit and no publication.
  Its candidate diff has twelve paths; the new fixture SHA-256 is
  `7D4C1AD9EEED8EFB2922B261A96C7833B285A096C3BFB30FC520F01650416367`;
  `git diff --check` emitted no errors.
- Issue #4 is committed locally as `cb29b62` with one commit and no publication;
  its final handoff and structural audit are still pending.
- Read-only adversary audits are active for #5 and #27. They were instructed not
  to run tests or edit files.
- No blocker has been reported.

## Publication and CI update

- Q read and approved the exact #5 and #27 PR title/body packets.
- PR #36 is open and ready (not draft) for issue #27 at exact head
  `ddab89c76194bb6d822610549be781176794826e`; its body contains `Closes #27`.
- PR #37 is open and ready (not draft) for issue #5 at exact head
  `13b2823c93bc879564f9ae83f8db85c4910b8d9a`; its body contains `Closes #5`.
- Continuous watcher sessions are active for both PRs. Latest inspected state for
  each exact head: four checks passed, `Static analysis & tests` and
  `MOO conformance suite` remain in progress, zero failures, no review items,
  mergeable.

## Issue #4 audit correction

- The initial read-only audit incorrectly called the `CompilationResult` invariant
  inverted. Its original expression was correct; worker and auditor both retracted
  that finding. The unpublished commit now uses the equivalent clearer expression.
- Real remaining findings: missing programmer/write permission checks; stale verb
  slot identity across programming; incomplete/misordered and overly generic
  diagnostics; inherited programming displays the definer instead of requested
  object; `.program :verb` feedback differs; tests lacked observable/inherited/
  permission/disappearance/mixed-diagnostic coverage.
- The #4 worker is amending the unpublished commit with narrow fixes and focused
  regression tests. No local suites are being run; CI will own execution after Q
  reads the revised publication packet.

## Next actions

1. Continue consuming PR #36/#37 watchers; diagnose any failed job from raw logs.
2. Before each merge, re-fetch exact PR files, commits, closing phrase, checks,
   reviews, head SHA, and mergeability; use only `gh pr merge <n> --merge`.
3. Verify each merge commit has two parents and the linked issue is closed.
4. Receive and re-audit the amended #4 handoff.
5. Audit final files, diff, commit list, issue scope, and proposed PR title/body.
   Do not rerun local suites; CI owns broad execution per the user's correction.
6. Show Q the exact #4 publication packet before any push/PR creation.
7. After the packet is read/approved, push, create a ready PR with `Closes #4`, run
   continuous watchers, diagnose branch failures, and merge only with
   `gh pr merge <number> --merge` after exact-head all-green/review-clean checks.
8. Advance the overlapping follow-on lanes only from refreshed `origin/master`.
9. Verify each merge has two parents and its issue is closed before advancing the
   overlapping follow-on lanes from the refreshed `origin/master`.

This file is a required uncommitted checkpoint note. Do not stage, commit, or push it.
## 2026-08-17 checkpoint

- PR #36 (`#27`) and PR #37 (`#5`) were merged with normal merge commits and their issues closed.
- Current `origin/master` is `b93e01485f371b4bf4391d66e8c9483a4f5cc4d5`.
- Issue #4 candidate is `4ad62a89d0f8334ec34715cfa9789d00f947570a`; final read-only contract audit is pending. No local build/test execution is planned; CI owns executable verification.
- Issue #28 candidate ends at `2c83a79c0e677bd85c5643078f6e0c3218fe2f9f`; read-only contract audit is pending.
- Issue #1 candidate is `14893f5c1244bf80d6f2802e21f5928c73748b28`; read-only audit is checking literal observable finalization timing coverage.
- Blocker: none yet; publication waits for each audit verdict and the user's review of each exact PR title/body.
- Next action: collect all three audit verdicts, repair any NO-MERGE findings in the owning worktrees, then present exact PR packets before push/create.

## 2026-08-17 second checkpoint

- Issue #4 candidate `a90ff18a9f522b8bbc1f90c5f6ef724b552af93a` earned a strict MERGE adversary verdict. Root structural audit: clean status, diff-check exit 0, merge-tree exit 0. Exact PR packet has been shown to Q; push/creation waits for confirmation of that packet.
- Issue #1 candidate advanced to `00cbea5178fe9dd4bd9c20bb69e1f3b6253ee8e2` with a second tests-first pair removing test-only production instrumentation and separating List/Map path coverage. Root structural audit is clean; final-final adversary gate is running.
- Issue #28 candidate `2c83a79c0e677bd85c5643078f6e0c3218fe2f9f` remains code-aligned but NO-MERGE until CI supplies the mandatory exact managed Toast stock-WSL oracle receipt. Current ordinary CI is not that receipt; CI feasibility research is running. No local oracle/build/test execution is authorized.
- No candidate branch has a pre-existing PR. Nothing from the current wave has been pushed or published yet.
- Read-only issue #2 preparation is running so its overlapping implementation can begin only after #1 merges from refreshed master.

## 2026-08-17 authorization checkpoint

- Issue #4 is audited MERGE at `a90ff18a9f522b8bbc1f90c5f6ef724b552af93a`; exact title/body were shown to Q. It is unpushed and has no PR pending packet confirmation.
- Issue #1 is audited MERGE at `434f8e49eaf6616c9a789faf2bb460ee6f3ef65c`; exact title/body were shown to Q. It is unpushed and has no PR pending packet confirmation.
- Both candidates have clean root structural audits, diff-check exit 0, and merge-tree exit 0 against `b93e01485f371b4bf4391d66e8c9483a4f5cc4d5`. No local builds/tests were run.
- Issue #28 code remains unpushed. Literal completion requires a separate `moo-conformance-tests` data-only prerequisite PR plus separately authorized exact Debian-WSL CI infrastructure/oracle-bundle publication; no self-hosted runners exist. This cross-repository expansion awaits Q's explicit authorization.
- Issue #2 research is complete and must start from refreshed master only after #1 normal-merges. Issue #26 research is complete and must start from refreshed master only after #28 normal-merges.
- Next action after authorization: push/create #1 and #4 with the exact reviewed packets, start continuous exact-head watchers, merge normally only after all checks/reviews/mergeability gates are clean, verify two-parent topology and issue closure, then begin #2 from refreshed master. If cross-repo #28 prerequisites are authorized, create their isolated worktrees/PRs before #28 and #26.

## 2026-08-17 live PR checkpoint

- PR #39 / issue #1 passed all six exact-head checks at `434f8e49eaf6616c9a789faf2bb460ee6f3ef65c`, was normal-merged as `a687f8d10ad27ee39099129436998230ef12dcf4`, has parents `b93e01485f371b4bf4391d66e8c9483a4f5cc4d5` and `434f8e49eaf6616c9a789faf2bb460ee6f3ef65c`, and issue #1 is closed completed. Its watcher emitted `stop_pr_closed` and exited.
- Fresh issue #2 worktree `C:\Users\Q\code\banteng-agent-issue-2` / branch `agent/issue-2-typed-finalization-control` was created exactly from `a687f8d10ad27ee39099129436998230ef12dcf4`; tests-first implementation is delegated and unpushed.
- PR #38 first CI head `a90ff18a9f522b8bbc1f90c5f6ef724b552af93a` failed compile on NullAway initialization plus one unused overload. Narrow CI repair `eed315589c1e645489053d55c82d1ef59212ee99` was committed/pushed and the watcher restarted on that exact head.
- PR #38 second CI run `32078296689`, job `95536000627`, compiled but failed five tests. CI artifact `java-test-reports` digest is `sha256:3a377aaf180701bbb06203873e02057057a087b6e4a826e52649cbeadad9c637`.
- Two existing layout/scheduler tests now throw `unreachable except clause` because ordinary parse mode treats the new recoverable diagnostic as fatal. Three new runtime tests fail on inherited display setup, verb write denial setup, and missing Wizard display name. A read-only investigator is separating implementation defects from fixture errors.
- #28 and dependent #26 remain excluded per Q's instruction to skip the cross-repository conformance prerequisite.
- Next action: finish #38 root-cause audit, make a narrow tests-first/fixture correction without local execution, push a new exact head, restart continuous monitoring, then final-audit and normal-merge only when fully green.

## 2026-08-17 PR 38 third-cycle checkpoint

- Test-only correction `849e3e8e8483a3efb3fdb118c5a18b8d43fb214b` repaired the two invalid EXCEPT arm orders, explicit inherited topology, distinct locked-verb owner, and requested-name expectations. It merged cleanly against `origin/master=a687f8d10ad27ee39099129436998230ef12dcf4` and was pushed.
- CI run `32079216644`, job `95538705954`, reduced the suite from five failures to two: 469 tests completed, both remaining failures were `NoSuchElementException` at named `readVerb` assertions in `MooRuntimeTest`. Artifact digest: `sha256:086e3cc0123fa990a29087eeb33cb1009906aa2f268719e1e5cf5252e92a2556`.
- Root cause: the named `readVerb` helper requires executable permission, but `inherited_program` and `program_reordered` were created with write-only permission `2`. The narrow test fixture fix is permission `6` for those two target verbs; production remains unchanged. The watcher was intentionally interrupted before patching and must restart immediately after the replacement head is pushed.
- Issue #2 is locally complete and unpushed with preserved commits `7b2e5e549e6a6ff9d805dddd99947c14ffc79d48` (tests first) and `c35aaf12da8ba8f1970fc2f4f7c1d3401e4d4a3c` (production), touching only MooRuntime, PublicationScheduler, MooVm, and new FinalizationControlTest. No local executable verification was run.
- Next action: audit/push the two-line #38 fixture permission commit, restart continuous exact-head watch, then independently adversarial-audit #2 before showing its exact PR packet.
## Issue #1 audit repair handoff

- Worktree: `C:\Users\Q\code\banteng-agent-issue-1`
- Branch: `agent/issue-1-name-independent-finalization`
- Current candidate before repair: `14893f5c1244bf80d6f2802e21f5928c73748b28`
- Findings to address: observable renamed-local finalization timing, alias-safe recursive containment inspection, and conservative liveness across `pass()` / call behavior.
- Scope boundary: issue #1 only; do not enter issue #2 sentinel/channel/type-constant work.
- Blocker: none currently; source and lifecycle test patterns must be inspected before editing.
- Next action: add focused tests and implementation changes in the issue #1 worktree, commit, and perform only structural Git checks.

### Issue #1 audit repair progress

- Added an uncommitted scheduler regression that executes the same WAIF last-read program with locals named `waif` and `renamed_local`, suspends at the collection boundary, and compares the observed recycle count.
- Added an uncommitted shared-DAG regression with 13 distinct identity nodes and an exact distinct-visit assertion; the production traversal now uses an identity set and iterative worklist.
- Added an uncommitted inherited `pass()` regression where the parent verb reads a local copied from the child frame; the liveness scan now treats `pass` like `eval` as a local-exposure barrier.
- Current modified scope in the issue #1 worktree: `MooVm.java`, `MooVmTest.java`, and `PublicationSchedulerTest.java`.
- No local build or test has been run; CI remains the execution authority.
- Blocker: complete read-only diff review and confirm the lifecycle helper and inheritance fixture are structurally correct.
- Next action: inspect the final diff, make any narrow corrections, commit the three-file issue #1 repair, then report exact structural Git output to the parent.

### Issue #1 audit repair committed

- New HEAD: `7640c9bac7a05fcec6857e0f31d4c93f4382944f` (`fix: harden finalization reachability analysis`).
- Branch now has two focused commits over `b93e01485f371b4bf4391d66e8c9483a4f5cc4d5`.
- Final branch scope is three files: `MooVm.java`, `MooVmTest.java`, and `PublicationSchedulerTest.java`.
- Worktree status was empty; branch-wide `git diff --check` and commit `git show --check` emitted no diagnostics.
- `git merge-tree --write-tree origin/master HEAD` produced `e8b1a69465bca88c56c160701befff1245bb0138` without conflicts.
- No build or test was run locally, and nothing was pushed or published.
- Blocker: none in the delegated repair; parent must perform its audit and obtain approval for the exact PR packet before publication.
- Next action: parent re-audits the two-commit candidate, presents the exact title/body to Q, then pushes and lets CI execute if approved.

## Issue #4 final parser repair handoff

- Worktree: `C:\Users\Q\code\banteng-agent-issue-4`
- Branch: `agent/issue-4-program-feedback`
- Candidate before this repair: `4ad62a89d0f8334ec34715cfa9789d00f947570a`
- Finding: a multiline scatter assignment must report every extra `@` target at
  the common post-RHS reduction-time line; for the specified source, both
  diagnostics must be line 4.
- Scope: add the focused regression first, then narrowly adjust parser diagnostic
  plumbing while preserving all earlier issue #4 contracts.
- Verification boundary: no local build or test execution; CI owns executable
  verification. Only structural Git checks are allowed before handoff.
- Blocker: none.
- Next action: inspect the current parser and regression patterns, make and commit
  the narrow repair, then report exact HEAD, files, and commit list without
  publishing.

### Issue #4 final parser repair result

- Candidate now ends at `a90ff18a9f522b8bbc1f90c5f6ef724b552af93a`.
- Tests-first commit: `134e457 test: pin multiline scatter diagnostic lines`.
- Implementation commit: `a90ff18 fix: report scatter rest errors at reduction line`.
- The exact multiline source now has a regression expecting both extra-rest
  diagnostics on line 4; the parser reports from the post-RHS current token.
- Final candidate still changes only the existing seven issue #4 production/test
  paths. `git diff --check` and `git merge-tree --write-tree origin/master HEAD`
  both exited 0; the merge tree was
  `34c57b19e8bdf50f6559b2a6500901216458b730`.
- No local build or test command was run. Nothing was pushed and no PR was created.
- Next action: root agent performs the final scope/publication audit and shows Q
  the exact PR title/body before any publication.

## Issue #4 final read-only gate

- Candidate under review: `a90ff18a9f522b8bbc1f90c5f6ef724b552af93a` in
  `C:\Users\Q\code\banteng-agent-issue-4`.
- Review target: the pinned four-line repeated-`@` scatter input must retain two
  extra-rest diagnostics, both on line 4, while all earlier `.program` feedback,
  permission, stable-verb, block-comment, specialized/mixed diagnostic, and
  installed-verb execution contracts remain present.
- State: final adversarial inspection is in progress. No build or test execution
  is authorized; only source, diff, history, and merge-tree inspection will be used.
- Blocker: none established yet.
- Next action: inspect the exact candidate and current `origin/master`, then return
  a strict MERGE or NO-MERGE verdict with concrete evidence and raw structural output.

## Issue #1 final read-only gate

- Candidate under review: `7640c9bac7a05fcec6857e0f31d4c93f4382944f` in
  `C:\Users\Q\code\banteng-agent-issue-1`.
- Review target: literal scheduler-observable renamed-local finalization timing,
  identity-deduplicated nested List/Map reachability, conservative `pass()`/CALL
  local liveness, direct and collection coverage, ordinary-local preservation,
  and strict exclusion of issue #2 sentinel/channel/type-constant work.
- State: final adversarial inspection is in progress. No edit, build, test,
  commit, push, or publication is authorized in the candidate worktree.
- Blocker: none established yet.
- Next action: inspect exact commits/files/diff and current-master merge-tree, then
  return a strict MERGE or NO-MERGE verdict with raw structural output.
## Issue #28 CI oracle feasibility investigation

- State: read-only investigation assigned; no implementation, build, test, oracle execution, push, or publication authorized.
- Question: identify the narrowest CI-owned managed Toast stock-wsl-testdb check for the eleven v17 hierarchy fixtures.
- Blocker: runner/workflow capability and reusable infrastructure path not yet inspected.
- Next action: inspect repository CI files and live GitHub Actions runner/workflow metadata, then recommend PR topology and an exact check name.

## Issue #1 final follow-up

- Worktree: `C:\Users\Q\code\banteng-agent-issue-1`; branch
  `agent/issue-1-name-independent-finalization`.
- Finding: the candidate still exposes a package-visible `Consumer` visit-observer
  overload in production solely for tests, and the containment regressions do not
  independently pin direct List, Map-key, and Map-value paths.
- Scope: remove the production test hook, prove shared-DAG identity handling through
  ordinary behavior, and add separate positive containment regressions while
  preserving scheduler-observable renamed-local timing and the `pass()` barrier.
- Verification boundary: no local build or test execution; CI owns executable
  verification. Structural Git checks only; no push or PR.
- Next action: inspect the exact candidate, add focused tests before implementation,
  commit the correction, and report exact HEAD, commits, files, and PR packet text.

### Issue #1 follow-up progress

- Tests-first commit `4cc65f6 test: cover finalization containment paths` replaces
  the observer-based assertion with an externally observable deep shared-DAG VM
  regression and independently covers direct List, Map-key, and Map-value paths.
- The pending production diff removes the `Consumer` import, package-visible
  observer overload, null check, and callback while retaining private
  identity-deduplicated traversal behavior.
- Structural inspection currently finds only the private production predicate;
  `git diff --check` emitted no diagnostics. No build or test was run.
- Blocker: none.
- Next action: commit the production cleanup, audit the full branch against current
  `origin/master`, and report exact hashes/files/packet without publication.

### Issue #1 follow-up result

- Candidate HEAD: `00cbea5178fe9dd4bd9c20bb69e1f3b6253ee8e2`.
- Follow-up commits: `4cc65f6 test: cover finalization containment paths`, then
  `00cbea5 fix: keep reachability traversal private`.
- The branch changes only `MooVm.java`, `MooVmTest.java`, and
  `PublicationSchedulerTest.java`; the worktree is clean and four commits ahead
  of `b93e01485f371b4bf4391d66e8c9483a4f5cc4d5`.
- `git diff --check origin/master...HEAD` emitted no diagnostics, and
  `git merge-tree --write-tree origin/master HEAD` returned
  `a3b130b1b1193462111f019f975115675ddae50a` with exit 0.
- An exact production-diff search found zero issue #2 marker/channel/type-constant
  matches. No local build or test command was run. Nothing was pushed or published.
- Blocker: executable verification remains CI-owned after the root agent's final
  audit and Q's review of the exact PR packet.
- Next action: root agent performs the final read-only scope audit, shows Q the
  title/body, then publishes only after confirmation.
## Issue #2 research checkpoint

- State: read-only preparation assigned for issue #2; no issue #2 worktree or branch will be created in this subtask.
- Current finding: issue #2 must be mapped against current `origin/master`, the implementation plan/profile, pinned Toast/Barn authority, and the unpublished issue #1 diff.
- Scope boundary: typed finalization-control design and tests-first behavioral acceptance only; exclude issue #1 name/liveness changes and unrelated work.
- Blocker/order: implementation must wait for issue #1 to merge where the two changes overlap.
- Next action: read the researcher skill, fetch/read live evidence, then return a narrow implementation and conflict-ordering packet to the parent.

### Issue #2 research findings

- Current fetched `origin/master`: `b93e01485f371b4bf4391d66e8c9483a4f5cc4d5`.
- Live issue #2 decision: remove finalization control from the MOO value space; use host typed task/queue state, and replace the VM literal ANON type code with `MooValue.Type.ANONYMOUS.code()`.
- Current master already has host-side `RuntimeTransition` finalization kinds plus `RuntimeContinuation.finalizationTarget`, carried on scheduler entries. The remaining defect is the mirrored in-band marker stored in `World.pendingFinalization()` and used as the scheduler's active-finalization signal.
- Narrow design: retain the real ANON/WAIF root in pending finalization until completion for restart safety; make scheduler-held finalization continuations/tasks authoritative for active/deduplicated work; remove marker construction/parsing/recovery with no compatibility shim; remove the raw literal `12`.
- TDD acceptance: forged old marker-shaped lists remain ordinary MOO values; active finalizers retain raw roots, survive task suspension/requeue without duplicate hooks, finish once, and preserve Toast ordering/topology; ANON constant expectation uses the enum code.
- Issue #1 ordering: unpublished branch HEAD observed as `00cbea5178fe9dd4bd9c20bb69e1f3b6253ee8e2` on base `b93e014...`; it modifies `MooVm.java`, `MooVmTest.java`, and `PublicationSchedulerTest.java`, including the exact finalization-test neighborhood. Issue #2 must branch only after #1 merges.
- Likely issue #2 ownership after #1 merge: `MooRuntime.java`, `PublicationScheduler.java`, `MooVm.java`, and focused runtime/scheduler/VM tests. No persistence schema, Barn, conformance-suite, or issue #1 liveness edits.
- Blocker: issue #1 is not merged yet; do not create or publish issue #2 work from the current base.
- Next action: hand the exact evidence and tests-first implementation packet to the parent; parent should refresh `origin/master` after issue #1's normal merge and then create a fresh isolated issue #2 worktree.
## Issue #1 final-final adversary gate

- Candidate: `00cbea5178fe9dd4bd9c20bb69e1f3b6253ee8e2` in `C:\Users\Q\code\banteng-agent-issue-1`.
- Scope: read-only review; no candidate edits, builds, tests, pushes, or publication.
- Current focus: production API leakage, independent direct/list/map coverage, bounded shared-DAG dedup regression, renamed-local scheduler timing, barriers, ordinary-local behavior, and exclusion of issue #2.
- Blocker: none established before inspection.
- Next action: inspect the exact commit, files, source/tests, clean status, diff check, and merge tree against current `origin/master`, then return a strict gate verdict to the parent.

### Issue #1 final-final findings

- Remote and local `origin/master` both resolve to `b93e01485f371b4bf4391d66e8c9483a4f5cc4d5`; candidate HEAD is `00cbea5178fe9dd4bd9c20bb69e1f3b6253ee8e2`.
- Candidate worktree status is clean. Four commits touch only `MooVm.java`, `MooVmTest.java`, and `PublicationSchedulerTest.java`; `git diff --check origin/master..HEAD` exits 0 and `git merge-tree --write-tree origin/master HEAD` exits 0 with tree `a3b130b1b1193462111f019f975115675ddae50a`.
- The containment traversal is private and no production visitor/test hook remains. Direct waif/anonymous, list, map-key, and map-value coverage are independent tests.
- The scheduler test runs identical programs under `waif` and `renamed_local`, compares complete line results, and requires the finalizer count at the suspension boundary.
- Remaining blocker under review: `finalStraightLineReadOfADeepSharedDagCompletesWithoutRepeatedTraversal` relies on `assertTimeout(Duration.ofSeconds(2), ...)`; this is a wall-clock assumption rather than deterministic bounded evidence of identity deduplication.
- Next action: finish barrier/scope and exact-commit inspection, then return strict `MERGE` or `NO-MERGE` with raw structural output.
## Issue #1 final gate checkpoint

- Target: `434f8e49eaf6616c9a789faf2bb460ee6f3ef65c` in `C:\Users\Q\code\banteng-agent-issue-1`.
- Scope: read-only inspection; no edits, builds, tests, pushes, or publication.
- Required gate: confirm the fragile timeout test and test hook are absent; inspect identity-dedup correctness; retain deterministic direct/List/Map-key/Map-value, renamed scheduler timing, pass/eval/barrier, and ordinary-value tests; exclude issue #2 scope; inspect clean status, commits/files, diff-check, and merge-tree against current `origin/master`.
- Observation: `C:\Users\Q\.Codex\AGENTS.md.d\adversary.md` was referenced but does not exist at that exact path.
- Blocker: none yet; audit has not begun.
- Next action: locate the applicable adversary instruction only if available, then audit the exact commit and return strict MERGE/NO-MERGE with raw structural output.

### Issue #1 gate findings

- Exact candidate: `434f8e49eaf6616c9a789faf2bb460ee6f3ef65c`; worktree clean.
- Local `origin/master` and live GitHub `master`: `b93e01485f371b4bf4391d66e8c9483a4f5cc4d5`.
- Five commits affect only `MooVm.java`, `MooVmTest.java`, and `PublicationSchedulerTest.java`; `git diff --check` and `git merge-tree --write-tree origin/master HEAD` both exited 0.
- The final tree has no `assertTimeout`, `Duration.of`, `deepSharedDag`, `distinctValueVisitor`, or `Consumer` test hook. `containsAnonymousOrWaifReference` is private.
- Production traversal is iterative and identity-deduplicated; sealed immutable `MooValue` collections prevent cyclic construction, and direct/list/map-key/map-value coverage remains deterministic.
- The renamed WAIF scheduler test compares canonical and renamed programs at the same `suspend(0)` boundary and observes one recycle callback.
- `eval`, `pass`, mutation, and control-flow barriers remain conservative; the added `pass` test covers locals copied to the inherited verb frame.
- Issue #2 sentinels, typed task control, and ANON type constant are untouched by the final diff.
- Blocker: none found by direct inspection. No build or test was run locally, per assignment.
- Next action: refresh status/master/merge-tree once more, then report strict MERGE with raw structural evidence.

## PR wave merge checkpoint — 2026-08-17 17:19 MDT

- PR #39 merged normally as `a687f8d10ad27ee39099129436998230ef12dcf4`; issue #1 closed completed. The merge has parents `b93e01485f371b4bf4391d66e8c9483a4f5cc4d5` and `434f8e49eaf6616c9a789faf2bb460ee6f3ef65c`.
- PR #38 exact head `54e265399d81662b6c954c2a112133ef0a2f7431` reached six terminal passing checks. It merged normally as `f6f543398efaa62947697d4f600a6fba61f2959c`; issue #4 closed completed. The merge has parents `a687f8d10ad27ee39099129436998230ef12dcf4` and `54e265399d81662b6c954c2a112133ef0a2f7431`.
- Both attached PR watchers observed their PRs closed/merged and exited. No watcher remains attached for #38 or #39.
- Issue #2 candidate remains local and unpublished at `c35aaf12da8ba8f1970fc2f4f7c1d3401e4d4a3`, with tests-first commit `7b2e5e549e6a6ff9d805dddd99947c14ffc79d48`; independent architecture audit is still running.
- No local Gradle build or test was run for issue #2. CI remains its executable verification gate after publication authorization.
- Issues #28 and #26 remain skipped per Q's instruction; do not create the cross-repository conformance prerequisite.

## Issue #2 publication checkpoint — 2026-08-17

- Exact current base: `f6f543398efaa62947697d4f600a6fba61f2959c`.
- Tests-first commit: `219f079847bd2ae90c399499e9baef99c1effc3a` (`test: define typed finalization behavior`).
- Production commit: `f757a8833c268483f2fdaffb02080cdf75665da8` (`fix: type finalization control`).
- Exact scope is four files: `MooRuntime.java`, `PublicationScheduler.java`, `MooVm.java`, and new `FinalizationControlTest.java`.
- Root structural gate: clean branch two commits ahead; `git diff --check` exit 0; conflict-free merge-tree `b159719f19b49bc970d53339f497bc694f9cc709`; no old production sentinel/recovery/magic-number matches; symbolic ANON code present.
- Independent final audit verdict: MERGE after deterministic host-wait/ticket-order proof. Prior compile, error-lifecycle, reflective-test, vacuous-ordering, and `read(player=#-1)` blockers were repaired before this verdict.
- No Gradle build or test was run locally. No push or PR exists yet.
- Publication remains authorization-gated on Q reading and confirming the exact title/body.

### Issue #2 published

- Q confirmed the exact packet with `Go`.
- Ready PR #40: `https://github.com/MongooseMoo/banteng/pull/40`.
- Published head: `f757a8833c268483f2fdaffb02080cdf75665da8`; remote and local heads matched after push.
- Exact title: `Type runtime finalization control`.
- Exact closing phrase: `Closes #2`.
- Continuous watcher session `25491` attached; first snapshot showed one pass, four pending, zero failed, and `MERGEABLE`/`UNSTABLE`.
- Next action: keep consuming the watcher; diagnose branch-owned failures, and after exact-head green run the full final audit before normal merge.

### PR #40 CI checkpoint — repaired head

- First CI head `f757a8833c268483f2fdaffb02080cdf75665da8` failed both `Static analysis & tests` and `MOO conformance suite` at `compileJava` because Error Prone reported unused `Entry.vm` overloads at `PublicationScheduler.java:2171` and `:2189`; `-Werror` stopped both jobs.
- Narrow CI repair `d2c4e38a2f2fba481be992cc765db0b96860f510` deletes only those two unused six- and seven-argument overloads. The surviving eight-argument overload has all three call sites. Root and independent audit found the repair narrow; no local build/test was run.
- Repaired head was pushed and watcher session `94818` attached. Current run `32083792883`, job `95551935183`, compiled production and tests but failed during `:test`.
- Raw test summary: `479 tests completed, 9 failed`.
- Eight failures are in new `FinalizationControlTest`: `anonConstantEvaluatesToItsSymbolicTypeCode`, `mooReturnedMarkerShapeCannotImpersonateFinalizationControl`, `suspendedAnonymousHookKeepsItsRawRootWithoutDuplicateHooks`, `erroredAnonymousFinalizerIsTerminalAndRunsOnce`, `suspendedWaifHookKeepsItsRawRootWithoutDuplicateHooks`, `erroredWaifFinalizerIsTerminalAndRunsOnce`, `ordinaryZeroDelayWakeWaitsForActiveAnonymousFinalizer`, and `anonymousControlSurvivesForkWakeIrrevocableRetryAndZeroDelayWake`.
- One existing test also failed: `MooRuntimeTest.locallyDefinedAnonymousPropertyDoesNotJoinTheSameRecycleWave` at line 448. This makes production behavior suspect; do not treat failures as fixture-only without artifact evidence.
- Blocker: precise expected/actual output and stack traces are not present in the job log summary; retrieve the `java-test-reports` artifact for run `32083792883` and diagnose before any further edit.
- Watcher remains associated with PR #40/repaired head; if paused for a fix, restart it immediately after the next push. No retry budget has been used.
- Next action: inspect the exact JUnit XML/HTML artifact, separate shared fixture/setup failures from the existing behavioral regression, repair only branch-owned defects, commit/push, and resume exact-head monitoring.

## Issue #2 zero-delay ordering test investigation — 2026-08-17

- Scope is read-only: find the narrowest deterministic behavioral test proving a non-finalizer zero-delay wake is queued while ANON finalization remains active; no edits, builds, or tests.
- Current `FinalizationControlTest.anonymousControlSurvivesForkWakeIrrevocableRetryAndZeroDelayWake` creates a zero-delay fork but the child only returns `0`, so it does not observe whether its zero-delay work ran during the finalizer.
- `PublicationScheduler.enqueueZeroDelayWake` places non-finalizer zero-delay work in `finalizationBlocked` while `activeFinalizations` contains an ANONYMOUS control, then `finishActiveFinalization` releases it after terminal completion.
- Existing deterministic test patterns include `Harness.lineAsync`, `CompletableFuture.isDone/get`, `TaskRegistry`, reflection-backed scheduler collection inspection, and bounded property polling. A suspended ANON recycle hook can publish its task id, keeping typed finalization active until a second connection calls `resume(id)`.
- Next action: inspect the existing `resume` test and scheduler queue/task-state helpers, then report exact file/line references and a concrete non-vacuous sequence to the parent.

## PR #40 finalization-wave repair checkpoint — 2026-08-17

- Third published head `8323db3039f6b3094af9b92f0193cdb320e1cabc` compiled but left four anonymous-finalization tests failing because the queue-only repair deferred all pending finalizers; the unchanged recycle-wave test no longer failed.
- Terminal-wave repair `3c2541f2d351f27e8dba4269d00f46a29f36c159` centralizes anonymous success/error cleanup in the scheduler: queue while the current raw root remains, remove the completed target/root, then start the previously pending set.
- Durability follow-up `74a80c9ae951b310d11c9530ea3cc8e48c791fea` adds a final queue-only pass so descendants exposed by cleanup become durable pending roots without joining the same recycle wave.
- Two independent read-only audits found the final head aligned and safe to publish; merge remains blocked until exact-head CI is terminal green.
- Push advanced PR #40 from `8323db3` to exact head `74a80c9`; watcher session `40664` attached. Initial snapshot: zero passed, five pending, zero failed; mergeable `MERGEABLE`, merge state `UNSTABLE`.
- No local Gradle build or test was run. CI remains the executable verification gate.
- Next action: continuously consume the watcher, inspect exact artifacts for any branch-owned failure, and run the full files/commits/scope/closing/checks/merge-method gate before a normal merge.

## PR #40 exact-head CI failure — run 32087042807

- Exact tested head: `74a80c9ae951b310d11c9530ea3cc8e48c791fea`; static/test job `95561624924` compiled and ran 479 tests, with 5 failures. Artifact ID `9307072739` is downloaded under `C:\Users\Q\AppData\Local\Temp\codex-banteng-pr40-fourth-report-32087042807`.
- Four new anonymous-finalization tests still observe zero hook execution: `suspendedAnonymousHookKeepsItsRawRootWithoutDuplicateHooks`, `erroredAnonymousFinalizerIsTerminalAndRunsOnce`, `ordinaryZeroDelayWakeWaitsForActiveAnonymousFinalizer`, and `anonymousControlSurvivesForkWakeIrrevocableRetryAndZeroDelayWake`.
- The unchanged `MooRuntimeTest.locallyDefinedAnonymousPropertyDoesNotJoinTheSameRecycleWave` now passes, so the terminal queue/remove/finalize order repairs premature same-wave admission.
- A new stable regression is `AnonymousObjectPersistenceTest.anon2PreservesOnePendingRootForARecursiveCycleAcrossTwoProductionBoots`: first and second checkpoint byte lengths differ (`1743` versus `1859`). The post-removal queue-only pass is the leading cause because it changes persisted pending roots at the finalizer boundary.
- Current investigation is tracing scheduler publication of runtime `spawnedSteps`, typed active registration, and finalizer wake paths. ANON-only failures make typed admission/dedup more suspect than the generic runtime spawn channel; WAIF lifecycle tests pass.
- Watcher session `40664` remains attached during read-only diagnosis. Do not patch until it is stopped; after any push, restart it immediately.
- No local Gradle build or test was run. Two read-only investigators are tracing the artifact and exact source.
- Next action: identify why newly spawned ANON finalizers never execute, design a narrow explicit wave boundary that preserves stable persistence, then stop the watcher immediately before editing.

### PR #40 refined wave diagnosis

- The failure is not lost scheduler publication. The terminal repair keeps the entire completing ANON body reachable during the first queue pass, and `markReachableAnonymous` follows every body property. That defers anonymous values reached through inherited properties as well as the locally defined descendants that must be isolated.
- Existing finalization traversal already encodes the intended distinction: `markFinalizingAnonymous` follows only properties where `defined == false`, so locally defined property values stay out of the current finalization component.
- Narrow candidate order: capture only values of the completing target's locally defined properties; remove the target/raw root; queue with those captured values as transient extra roots; then finalize the resulting pending set. This admits unrelated/inherited anonymous roots immediately while preserving locally defined descendants for the next ordinary boundary.
- Remove the final post-removal queue-only pass from `74a80c9`; persisting that newly exposed descendant is the leading explanation for the Anon2 two-boot byte mismatch.
- Blocker: the exact transient-root method shape and world-body lookup still need independent confirmation before editing. Watcher session `40664` remains attached; no local build/test has run.
- Next action: await both investigator reports, audit the exact proposed ordering, then stop the watcher before applying a focused production-only repair.

### PR #40 transient-root repair published

- Exact repair commit `6929dd03a49f52be6d0458cde41d34c59e6f5633` changes only `MooRuntime.java` and `FinalizationControlTest.java` (`+29/-10`).
- Production captures only `defined` property values from the completing ANON body, removes the target/raw pending root, scans after removal with those values as transient host roots, then admits pending finalizers. It removes both the broad pre-removal scan and the persisted post-finalize scan.
- New ANON hook fixtures now generate code using the concrete regular class object. The only remaining `this.class` hooks are the valid WAIF fixtures.
- Independent actual-head audit verdict: MERGE for push. Root structural evidence: clean status and full diff-check; full PR scope remains exactly `MooRuntime.java`, `PublicationScheduler.java`, `MooVm.java`, and new `FinalizationControlTest.java`; merge-tree `cca326e9cf4e5447a2c6b9768ed14640b7476eaa` against `f6f5433`.
- Push advanced PR #40 from `74a80c9` to `6929dd0`. Watcher session `52060` attached; initial snapshot is zero passed, five pending, zero failed on exact head.
- No local Gradle build or test was run.
- Next action: consume watcher continuously, diagnose exact artifacts if needed, then perform the full merge guard before normal merge.

### PR #40 conditional next-wave repair published

- Exact head `6929dd03a49f52be6d0458cde41d34c59e6f5633` made all new `FinalizationControlTest` cases pass; its only failure was stable `AnonymousObjectPersistenceTest.anon2PreservesOnePendingRootForARecursiveCycleAcrossTwoProductionBoots`, with unchanged `1743` versus `1859` checkpoint lengths.
- Root cause: the loaded cycle's `B` is transiently deferred when `A` finishes, so the blocked `server_started` zero-delay wake was released before `B` entered its next recycle wave; startup then created a replacement A/B cycle while old B survived.
- Production-only commit `0368fbffed4a4870692e823345bc12e3747bef32` changes `MooRuntime.java` and `PublicationScheduler.java` (`+27/-3`, no test diff). Spawned work is registered, the parent boundary is published, and only then is ready work dispatched.
- A synchronized typed predicate continues the next ANON wave only when blocked zero-delay work exists and the completing control is the sole active ANON finalizer. The true branch drains the recursive startup cycle before wake release; the false branch preserves the stable acyclic local-child deferral.
- Completed actual-head audit verdict: MERGE for push. A second adversarial review did not return and was stopped; no claim is based on it.
- Root structural evidence before push: clean status/diff-check and merge-tree `9e5f6ac4475c049abe9d5341b18a7925bd03a415` against `origin/master`.
- Push advanced PR #40 from `6929dd0` to `0368fbf`. Watcher session `98200` attached; initial snapshot is zero passed, five pending, zero failed.
- No local Gradle build or test was run.
- Next action: continuously monitor exact-head CI and, if green, execute the full files/commits/scope/closing phrase/checks/normal-merge gate.

### PR #40 merged — 2026-08-17

- Exact head `0368fbffed4a4870692e823345bc12e3747bef32` completed all six GitHub checks successfully: both CodeQL checks, Dependency review, MOO conformance suite, Static analysis & tests, and Workflow static analysis.
- Final gate confirmed the approved title/body and `Closes #2`, exactly four scoped files, the nine-commit tests-first history, clean merge state, and conflict-free merge tree `9e5f6ac4475c049abe9d5341b18a7925bd03a415`.
- PR #40 was normal-merged with `gh pr merge 40 --repo MongooseMoo/banteng --merge`.
- Merge commit `4880f025b2e396548bce7cbeb93a670a46b4f2e6` is exact `origin/master` and has two parents: base `f6f543398efaa62947697d4f600a6fba61f2959c` and PR head `0368fbffed4a4870692e823345bc12e3747bef32`.
- GitHub reports PR #40 `MERGED`; issue #2 is `CLOSED` with reason `COMPLETED`.
- Watcher session `98200` observed `stop_pr_closed` on the exact head and exited 0.
- No local Gradle build or test was run; exact-head GitHub CI is the executable verification evidence.
