# PR #40 existing-test regression diagnosis

## 2026-08-17 checkpoint

- Task: read-only diagnosis of PR #40 at head `d2c4e38` in `C:\Users\Q\code\banteng-agent-issue-2` against base `f6f5433`.
- Exact reported CI evidence: `MooRuntimeTest.locallyDefinedAnonymousPropertyDoesNotJoinTheSameRecycleWave` at line 448 expected framed `{1, 1}` but got `{1, 2}`.
- Required competing hypotheses: typed active-dedup loss; finalization-wave ordering/root removal; finish/error cleanup; scheduler wake changes.
- Current findings: none yet; source and diff have not been inspected in this diagnostic turn.
- State: checkpoint recorded; all subsequent work remains read-only. No build, test, edit in the PR worktree, stage, commit, or push is authorized.
- Blocker: none currently.
- Next action: read the investigation instructions and relevant memory pointers, then inspect exact source/test/diff evidence and adjudicate the hypotheses.

## 2026-08-17 investigation update

### Facts and observations

- The assigned worktree is clean but its local `HEAD` is `89ee847`, one commit ahead of the requested PR head `d2c4e38`; all diagnosis is therefore using explicit `git show`/`git diff` refs for `d2c4e38` and `f6f5433`, not the checked-out file contents.
- The existing test is unchanged by PR #40. It creates anonymous `x`, adds a locally defined `x.next` containing a second anonymous object, and expects only one `recycle` call for both explicit recycle and GC-triggered recycle cases. The CI failure is the second assertion at `MooRuntimeTest.java:448-450`: expected `{1, 1}`, observed `{1, 2}`.
- `markFinalizingAnonymous` is unchanged across base and PR and follows anonymous-valued properties only when `!property.defined()`. It therefore does not initially add `x.next` to `x`'s finalization set; the regression is not caused by that traversal predicate changing.
- PR #40 replaces marker-shaped pending roots with raw anonymous roots and introduces scheduler-owned `activeFinalizations` keyed by typed `FinalizationControl`.
- Exact terminal call order at `d2c4e38`: executing `ANONYMOUS_FINALIZATION_RETURN` calls `finishAnonymousFinalization` (removes `x` and its raw pending root); `executeSegment` then calls `collectAfterAnonymousFinalization`; that method runs `queueUnreachableAnonymousObjects` followed immediately by `finalizePendingObjects`; the now-unreferenced child can be queued and a child finalizer step spawned before `publishSegmentResultOutsideMonitor` calls `finishSuccess`/`finishActiveFinalization` for the parent.
- The child has a distinct typed target, so `enqueueSpawned`'s active-dedup check against the parent cannot reject it. This supports a finalization-wave isolation/order defect rather than same-target typed dedup loss.
- Scheduler zero-delay wake changes do not create the extra finalizer; they only govern when ordinary suspended work is released. Finish/error cleanup is relevant to lifecycle correctness but does not explain this successful parent-finalizer path by itself.

### Hypothesis status

1. Typed active-dedup loss: weakened. The duplicate count is for a different child target, not a second hook for the same `FinalizationControl`.
2. Finalization wave ordering/root removal: leading. Removing the parent before post-finalizer collection exposes its locally defined child and immediately calls `finalizePendingObjects` in the same terminal transition.
3. Finish/error cleanup contamination: weakened for this failure. The assertion is within one runtime/test, and the successful finalizer follows normal finish cleanup.
4. Scheduler wake changes: ruled out as root cause of membership; no `suspend(0)` occurs in the failing `recycle` hook or property-read command.

### State, blocker, next action

- State: read-only source/diff diagnosis continues; no build, test, PR-worktree edit, stage, commit, or push has occurred.
- Blocker: the base code has a superficially similar `collectAfterAnonymousFinalization` sequence, so the remaining task is to identify the precise base mechanism that preserved wave isolation and the exact PR change that removed it.
- Next action: trace explicit-recycle collection deferrals and base-vs-PR propagation through `publishVmState`, `executeSegment`, spawned-step draining, and pending-root representation; then provide the narrow production recommendation without weakening the stable test.

## 2026-08-17 base reconciliation correction

- The earlier statement that post-parent queue/finalize was itself the PR regression was incomplete: `collectAfterAnonymousFinalization()` calls `queueUnreachableAnonymousObjects()` and then `finalizePendingObjects()` in both `f6f5433` and `d2c4e38`.
- In both revisions, after the parent is removed, that unchanged sequence discovers the locally defined child and produces a spawned child-finalizer step. Base stores a child marker; PR stores the raw child and registers its typed control, but both enqueue a child with a scheduler ticket before the child's hook can execute.
- `submit()` and `dispatch()` ticket assignment are unchanged. The base scheduler enqueues `published.spawned()` before finishing the parent attempt; PR does the same. The PR registers the child in `activeFinalizations` during that enqueue and clears only the parent control afterward. This does not logically make the child earlier than the next ingress once its ticket has already been allocated.
- Therefore no source-supported PR hunk deterministically changes wave membership for this simple, non-suspending hook. The stable assertion depends on whether the caller submits its observation ingress before or after the already-supported child enqueue; PR bookkeeping/raw-root changes can expose that race, but source inspection alone cannot assign deterministic causality to one changed hunk.
- The causal production defect remains lack of an explicit wave boundary. The robust narrow repair is to queue children found after parent completion without immediately admitting them to finalization, or to carry explicit per-wave deferrals. The stable test should remain unchanged and production should make its expected observation deterministic.
- Active-control lifetime is not the repair: at `d2c4e38` the parent control remains active during collection, the child is a distinct target, and same-target dedup must not suppress it permanently.
- State: read-only; no build/test/PR-worktree edit/commit/push. Blocker: exact runtime scheduling evidence is unavailable because builds/tests are forbidden and GitHub job-log retrieval hit API rate limiting. Next action: report the corrected source-grounded conclusion to the parent.

## 2026-08-17 static audit of head 8323db3

- Exact worktree state: clean branch at `8323db3039f6b3094af9b92f0193cdb320e1cabc`, matching `origin/agent/issue-2-typed-finalization-control`.
- `8323db3` changes one production line only: `collectAfterAnonymousFinalization()` still queues newly unreachable roots but no longer immediately calls `finalizePendingObjects()`.
- This changes behavior only when removing a completed anonymous finalizer exposes another anonymous root. Each `FinalizationControlTest` anonymous scenario creates one subject and no anonymous-valued child/property, so static source tracing finds no test whose counter assertion must change because of the one-line production edit.
- Potentially sensitive emptiness assertions are lines 206, 291, and 325. They would fail only if the corresponding finalizer exposes some additional unreachable anonymous object: fork/wake/irrevocable finalizer; read-blocked ordering finalizer; or errored anonymous finalizer. Their test setup defines no such child. WAIF terminal assertions are outside `collectAfterAnonymousFinalization()`.
- All helper-backed fixture properties (`typed_marker_class`, `typed_anon_class`, `typed_waif_class`, `typed_boundary_class`, `typed_order_class`, `typed_error_class`, and `typed_error_waif_class`) are unique in the test source and no occurrence was found in the pinned `Test.db` fixture.
- `objectPropertyId()` reads a property from permanent object `#0` only after the preceding synchronous `executeLine()` returns. Each test adds that exact `#0` property in the setup command, so no static stale-read path exists. The concrete failure shape, if setup itself errors, is `NoSuchElementException` at `readProperty(...).orElseThrow()` line 397, reached from helper line 402; the setup commands generally do not assert their output.
- Highest helper-specific risk is therefore not the queue-only production change but masked setup failure in any of the seven helper callers (lines 90, 119, 149, 183, 233, 310, 343). The marker test at line 90 at least retains setup output for a later marker-content assertion; the other setup outputs are discarded.
- State: read-only; no edit/build/test beyond this mandated notes update, no stage/commit/push.
- Blocker: no CI artifact/log is available yet, so no particular assertion is claimed failed.
- Next action: report concrete risky assertions and line traces only; await artifact evidence before suggesting a patch.
