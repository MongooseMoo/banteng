# Issue 2 typed finalization control checkpoint

## Findings

- Live issue #2 requires removing forgeable anonymous and WAIF finalization marker lists and replacing the ANON VM literal 12 with `MooValue.Type.ANONYMOUS.code()`.
- The active implementation converts raw pending ANON and WAIF roots into marker-shaped `ListValue` values, parses those lists during completion/startup recovery, and uses marker presence for zero-delay ordering.
- The selected profile makes pinned stock Debian-WSL Toast the behavioral authority. Barn is reference-only and is not an implementation dependency.
- Scheduler finalization control must survive `RuntimeContinuation`, `Entry`, `SegmentResult`, `SuspendedWork`, and wake/ready requeue boundaries.
- Candidate production removes marker construction/decoding/startup recovery, retains raw ANON/WAIF roots, introduces validated typed kind+target control, uses typed active state for dedup/ANON zero-delay ordering, and replaces the ANON literal with `MooValue.Type.ANONYMOUS.code()`.
- Hard blocker: `FinalizationControlTest.oldMarkerShapedPendingListsRemainOrdinaryMooValues` calls `assertEquals(List.of(), runtime.startServer())`, but `MooRuntime.startServer()` returns `void`; the candidate is statically uncompilable without changing the test.
- Test-contract blocker: the new boundary/dedup assertions inspect record component names and the private `activeFinalizations` map through reflection rather than proving fork, wake, retry, terminal success/failure, and zero-delay behavior externally.
- Failure-semantics blocker: a finalizer hook ending `ERRORED` resumes its finalization continuation, but `MooRuntime.resumeRuntime` returns diagnostics before the terminal finalization transition removes the raw root. `finishSuccess` then removes the typed active control anyway. The raw root is therefore eligible to spawn the hook again on the next collection wave, unlike the base marker state which remained deduplicated until restart.

### Re-audit of rewritten candidate

- Rewritten hashes: tests `07271da1d84aa5dc7a826c9a035f94904a1630ce`, production `64fef13b5dd4151d50a712581efd1a9925821327`, base/current live master `f6f543398efaa62947697d4f600a6fba61f2959c`.
- The prior static compile blocker is resolved: the marker-shaped pending-list test now calls `runtime.startServer();` as a statement.
- The prior terminal-error lifecycle blocker is resolved for MOO `ERRORED` ANON and WAIF hooks: `resumeRuntime` calls `finishErroredFinalization`, which removes the target/raw pending root, and the behavioral tests assert one call plus empty pending finalization.
- The rewritten tests contain no reflection, private-map inspection, record-shape assertion, or production-source text inspection. They behaviorally exercise marker-shaped data, suspended-hook dedup, fork, an irrevocable `random(1)` authorization rerun, suspension wake, terminal errors, and ANON evaluation.
- Remaining strict blocker: the combined fork/irrevocable/suspend test observes zero-delay ordering without a deterministic overlap. The first command can return after the spawned finalizer has already advanced or completed; in that schedule both `{1, {1, 1}}` assertions pass even if non-finalizer zero-delay wakes are never blocked by active typed ANON state. The test therefore does not reliably prove the required typed ordering behavior.

### Deterministic ordering repair

- The replacement behavioral proof keeps the ANON finalizer indefinitely suspended and forks the ordinary observer before a host-blocked controller. Scheduler publication order forces the observer's `suspend(0)` classification before the setup ingress returns.
- The controller cannot resume the finalizer until explicit input is delivered. A later irrevocable `threads()` ingress is ordered after both child boundaries; without typed ANON blocking, the observer wake must publish before that barrier and the pre-release `{observer_woke, observer_saw_finished}` assertion fails.
- No reflection, private-map/source inspection, or wall-clock timeout is used for the ordering proof. Production did not require a further change.

## State

- Assigned worktree: `C:\Users\Q\code\banteng-agent-issue-2`.
- Branch: `agent/issue-2-typed-finalization-control`.
- Exact base: `a687f8d10ad27ee39099129436998230ef12dcf4`.
- Read-only final gate assigned for candidate `c35aaf12da8ba8f1970fc2f4f7c1d3401e4d4a3c`.
- Candidate is expected to contain exactly two commits, with tests-first boundary; this has not yet been independently verified in the current gate.
- Live issue #2 remains open. During the audit, shared `origin/master` advanced from the requested base to `f6f543398efaa62947697d4f600a6fba61f2959c`, and the live GitHub branch now matches that tip; the candidate is 7 commits behind and 2 commits ahead.
- Exactly two commits are present: tests-only `7b2e5e5` followed by production-only `c35aaf1`; the candidate worktree is clean and `git diff --check` produced no output.
- Refreshed `git merge-tree --write-tree origin/master c35aaf1` produced `906202187872d6c2cf16c98044b3a9c145d12a01`; it is conflict-free against current `origin/master`, and the merged diff remains exactly the three production files plus the new test file.
- Rewritten candidate is exactly two commits ahead of current live `master`, the worktree is clean, `git diff --check` produced no output, and `git merge-tree --write-tree origin/master 64fef13b` produced conflict-free tree `ee4d1f82c478fb6a4783b027efe72cd8962e2be5`.
- Latest rewritten commits are tests-first `219f079847bd2ae90c399499e9baef99c1effc3a` and production `f757a8833c268483f2fdaffb02080cdf75665da8`, based on `f6f543398efaa62947697d4f600a6fba61f2959c`. The ordering fixture stores its primary connected Wizard in `reader_player`, because finalizer-local `player` is `#-1`, and the hook calls `read(this.class.reader_player)`.
- Final structural evidence: worktree clean/ahead two, `git diff --check origin/master..HEAD` clean, exact four-file scope, production byte-identical to the prior candidate, and conflict-free merge tree `b159719f19b49bc970d53339f497bc694f9cc709`.

## Blocker

- Strict gate status is `NO-MERGE` because the committed test source is uncompilable and the new tests do not behaviorally cover required propagation/failure paths.
- The old `NO-MERGE` blockers do not apply to the rewritten hashes. Final disposition remains `NO-MERGE` solely because typed zero-delay ordering lacks deterministic behavioral proof.
- The gate explicitly forbids local builds/tests and all source edits, commits, pushes, or publication.
- No implementation blocker remains after the deterministic ordering repair. Builds/tests remain forbidden locally and are not claimed.

## Next action

- Return the read-only `NO-MERGE` report to the parent with exact file/line blockers and raw Git evidence; do not modify the candidate.
- Return the strict rewritten-candidate `NO-MERGE` report with the single remaining coverage blocker and the resolved prior findings; do not modify the candidate.
- Return the exact commits, four-file diff, non-vacuous ordering explanation, diff-check and merge-tree evidence, plus the terse PR packet without pushing or publishing.

## PR d2c4e38 regression trace checkpoint (2026-08-17)

### Current findings and observations

- Assigned read-only comparison: base `f6f5433` versus PR production `d2c4e38`, limited to the failing `MooRuntimeTest.locallyDefinedAnonymousPropertyDoesNotJoinTheSameRecycleWave` expectation `1`, actual `2`.
- Investigation will isolate exact changed call order, collection roots, and dedup behavior, then identify the smallest causal production hunk and a narrow repair that preserves issue #2 typed finalization control.
- No source edit, build, or test is authorized for this subtask.

### State

- Trace has not yet begun beyond reading the existing issue #2 handoff notes.
- Workspace changes, branch state, and target diff have not yet been inspected in this subtask.

### Blocker

- No blocker currently known.

### Next action

- Inspect repository status and the exact production diff `f6f5433..d2c4e38`, then trace only the anonymous-property recycle-wave path implicated by the named test.

### Trace findings update

- CI failure is the explicit-recycle half of the unchanged test: after `go(1)` invokes `recycle(x)`, the next property read expected `recycle_called == 1` and observed `2`.
- `markFinalizingAnonymous` is unchanged and deliberately follows only inherited (`!property.defined()`) anonymous properties, so locally defined `x.next` is not part of `x`'s initial finalization set.
- Explicit `recycle(x)` removes `x` in the VM and records values from its locally defined properties in `VmSnapshot.anonymousCollectionDeferrals`, keeping the child rooted through that task's completion.
- The smallest scheduler behavior change under scrutiny is the new typed-active zero-delay gate: `enqueueZeroDelayWake` now exempts work whose `FinalizationControl` is anonymous, and `enqueueSpawned` registers each newly discovered child finalizer as active before the finishing parent's active control is removed.
- Terminal call order at `d2c4e38` is: finish parent target; immediately run `collectAfterAnonymousFinalization`; queue/finalize the now-unrooted child; publish and register the child typed control; only then finish/remove the parent's typed control. Distinct targets mean same-target dedup correctly permits the child.

### Updated state

- Exact refs and the unchanged test, explicit-recycle deferral path, collection path, spawned-step publication order, and active-control lifecycle have been inspected read-only.
- No build, test, production edit, stage, commit, or push has occurred.

### Updated blocker

- Base uses superficially similar post-parent collection, but its wave gate is marker/world-state based rather than scheduler active-map based. The final report must state the causal scheduling hunk without overstating that same-target dedup itself failed.

### Updated next action

- Report the smallest causal scheduler hunk and recommend retaining typed target dedup while preventing a newly exposed locally-defined child from extending the finishing parent's recycle wave.

## Third CI artifact design audit checkpoint (2026-08-17)

### Current findings and observations

- Current candidate is `8323db3039f6b3094af9b92f0193cdb320e1cabc` atop fixture repair `89ee847c4a9916f0b6bfbbe76fac856b50003cd2` and CI-overload repair `d2c4e38a2f2fba481be992cc765db0b96860f510`.
- `8323db3` deletes only the immediate `finalizePendingObjects()` call from `collectAfterAnonymousFinalization`; the helper now discovers/roots newly unreachable anonymous objects but does not admit any pending root.
- Ordinary VM suspension and terminal publication still call `finalizePendingObjects()` in `publishVmState`, but the four affected tests require top-level roots discovered at an ordinary collection boundary to be admitted without waiting for an additional command.
- Explicit `recycle(x)` preserves locally defined descendants through the current task via `anonymousCollectionDeferrals`. When the next ordinary boundary discovers the child, that child must be admitted then. By contrast, a descendant first exposed by removal of a completing finalizer target must remain pending until the next ordinary boundary.
- Returned and errored finalizer continuations both remove the target before scheduler-side post-finalization collection: success uses `ANONYMOUS_FINALIZATION_RETURN -> finishAnonymousFinalization`; error uses `finishErroredFinalization`. Any repair must apply the same post-removal discovery/admission rule to both paths.
- Raw-root lifecycle requires discovered descendants to remain in `pendingFinalization` while deferred; only admission may move them into a hook, and the completing target is removed only by its terminal success/error cleanup.

### State

- Source-only tracing is in progress. No candidate source edit, build, test, stage, commit, push, or publication has occurred.

### Blocker

- Queue-only post-finalizer collection has no explicit wave boundary token/snapshot. It distinguishes neither roots already pending before target completion nor descendants newly queued because the target was removed.

### Next action

- Inspect the attempt/publication sequencing for the narrowest API split: ordinary boundaries should queue then admit; finalizer completion should admit only the preexisting pending set, remove the target, then queue newly exposed descendants without admitting them. Confirm symmetric returned/error behavior and report a minimal architecture-aligned ordering.

## Selected terminal-wave design checkpoint (2026-08-17)

### Current findings and observations

- Current audited head is `74a80c9ae951b310d11c9530ea3cc8e48c791fea`; a worker is preparing a replacement terminal-wave design.
- The selected design retains the active anonymous target and its raw pending root through returned or errored hook completion, captures only values of properties whose slots are locally defined on that target, then removes the target/root.
- Post-removal reachability scanning receives those defined-property values as transient roots. This mirrors `MooVm.applyAnonymousRecycle`, which collects references only from `property.defined()` values into `anonymousCollectionDeferrals` before explicit target removal.
- Inherited anonymous references are intentionally not transiently protected because `markFinalizingAnonymous` already traverses only non-defined property slots into the initial finalizing component.
- After the post-removal scan, `finalizePendingObjects()` admits preexisting and independently discovered pending roots. The protected locally-defined descendants are not pending and therefore cannot join the completing target's wave; the next ordinary collection boundary discovers them.
- Anon2 requires one durable pending root `A` whose locally defined `next` property reaches `B`; broad post-finalize queueing can change the pending-root topology. The transient-root scan preserves `B` through `A` without promoting `B` into the same/current pending wave.
- The current test hook strings using `this.class` are invalid for ANON finalizer execution. Tests should embed the concrete regular class object reference when incrementing/reading class counters. WAIF behavior and tests should remain unchanged.

### State

- Design-only source audit is in progress. No candidate source edit, local build/test, stage, commit, push, or publication has occurred.

### Blocker

- No architectural blocker found yet. Implementation must avoid broad prequeue/post-finalize scans, must route both returned and MOO-errored ANON completion through one terminal method, and must not alter WAIF cleanup.

### Next action

- Verify the worker's exact diff, especially the transient-root API shape, single cleanup call on success/error, fork/suspend/retry propagation, and preservation of checkpoint rooting before a strict verdict.

## Final-head adversarial review checkpoint (2026-08-17)

### Current findings and observations

- Assigned read-only review target is local `HEAD` `74a80c9` versus `origin/master`; published PR head is `8323db3`, with local follow-up commits `3c2541f` and `74a80c9` not yet reviewed.
- Required review dimensions are literal typed host finalization control, TDD boundaries, stable recycle-wave isolation, terminal success/error symmetry, raw-root durability, the extra post-finalize queue-only pass, and file/commit scope.
- The adversary skill was selected. Its required `ward set adversary` activation could not run because `ward` is not installed; review continues with manual read-only discipline.
- A quick memory registry search found only general Banteng finalization context, not evidence for these two final commits.

### State

- No repository implementation files have been edited, built, tested, staged, committed, pushed, or published by this review.
- Actual Git state, commit contents, and file/line evidence remain to be inspected after this mandatory checkpoint update.

### Blocker

- No substantive review blocker is known yet. Local builds/tests are explicitly forbidden, so any safe-to-push recommendation must be source-and-history based and cannot claim local test execution.

### Next action

- Read the project principle, inspect exact refs and commit/file diffs, then trace queue/admission ordering and terminal cleanup paths at `74a80c9`; return `MERGE` or `NO-MERGE` with exact evidence.

## Anon2 alternative-design audit checkpoint (2026-08-17)

### Current findings and observations

- Read-only target is exact head `6929dd03a49f52be6d0458cde41d34c59e6f5633`; the worktree was clean when inspected.
- The four typed ANON control tests are reported green remotely. The remaining reported failure is `AnonymousObjectPersistenceTest.anon2PreservesOnePendingRootForARecursiveCycleAcrossTwoProductionBoots`, still `1743` versus `1859` bytes.
- `Anon2.db` suspends before recreating A and B; A and B have locally defined `next` properties pointing to each other, then the fixture shuts down. The old marker flow kept the ordinary zero-delay wake blocked through A's and B's finalizer waves, after which the fixture recreated an identical cycle.
- Head `6929dd0` treats every value from A's locally defined properties as a transient root. Because A.next is B, B is never queued as the next typed finalizer wave. A becomes the last active anonymous finalizer, releases the fixture wake, and the next checkpoint can contain old B plus the newly created cycle.
- Alternative A is an SCC/back-reachability rule: only transiently defer an anonymous identity reachable from a defined property when that identity cannot reach the finishing target in the pre-removal snapshot. A.next=B and B.next=A therefore remains a cycle and B is admitted next; an acyclic x.next child remains transient.
- The SCC rule must filter individual anonymous identities. Whole list/map/WAIF values may mix a cycle member with an acyclic child; deferring the container would incorrectly protect both. Runtime reachability already traverses ANON bodies, WAIF bodies, list elements, and map keys and values. Explicit-recycle deferral currently traverses ANON/list/map direct references but not WAIF bodies.
- Alternative B conditions wave continuation on `finalizationBlocked` and reorders scheduler publication to register the parent boundary before dispatching spawned work. This couples collection semantics to the accidental presence of an ordinary blocked wake and expands scheduler ordering risk.

### State

- No build, test, PR-worktree edit, stage, commit, push, or publication has occurred. This notes-only checkpoint is intentionally uncommitted.

### Blocker

- Need final design verdict on whether SCC/back-reachability should share an extracted graph traversal with explicit recycle, especially for WAIF-contained references, or remain a narrowly automatic-finalization helper.

### Next action

- Compare alternatives A and B against mixed list/map/WAIF graphs, retry/publication ordering, and behavior when no ordinary task is blocked; report the safest narrow production design.

## Conditional-wave final-head adversarial checkpoint (2026-08-17)

### Current findings and observations

- Assigned read-only target is actual local `HEAD` `0368fbffed4a4870692e823345bc12e3747bef32`, parent `6929dd03a49f52be6d0458cde41d34c59e6f5633`, and `origin/master` `f6f543398efaa62947697d4f600a6fba61f2959c`; the worktree was clean when inspected.
- The published PR #40 head is still `6929dd0`; local `0368fbf` changes only `MooRuntime.java` and `PublicationScheduler.java` relative to that parent.
- The local scheduler change registers spawned work first, publishes the parent boundary next, and dispatches only afterward. This prevents a spawned ticket from running and completing against a still-unpublished parent boundary.
- The conditional second ANON queue/finalize pass occurs only when `finalizationBlocked` is nonempty, the completing control is active ANON, and it is the sole active ANON. Source tracing of terminal success/error, ticket order, stable child deferral, and Anon2 lifecycle is still in progress.
- Issue #2 itself requires typed host-side finalization control and symbolic `MooValue.Type.ANONYMOUS.code()`; the branch diff retains those architectural decisions.

### State

- No PR-worktree source edit, build, test, stage, commit, push, or publication has occurred. This mandatory notes update is in the main checkout and remains uncommitted.
- The adversary protocol was selected; its required `ward set adversary` activation failed because `ward` is unavailable.

### Blocker

- No final blocker has been adjudicated yet. Builds and tests are explicitly forbidden, so the eventual safe-to-push verdict will be source/history based and will not claim local execution.

### Next action

- Finish tracing restoration/durable-task behavior, terminal failure cleanup, conditional-wave membership, and exact origin/master scope; then return an evidence-cited safe-to-push or no-push verdict.

## Final delivery — 2026-08-17

- Exact PR head `0368fbffed4a4870692e823345bc12e3747bef32` passed all six GitHub checks.
- PR #40 was merged with the authorized normal merge method.
- Merge commit: `4880f025b2e396548bce7cbeb93a670a46b4f2e6`.
- Merge parents: `f6f543398efaa62947697d4f600a6fba61f2959c` and `0368fbffed4a4870692e823345bc12e3747bef32`.
- `origin/master` resolves to the merge commit; the exact PR head is its second parent and ancestor.
- Issue #2 is closed as completed. The attached watcher observed the merged state and exited 0.
- No local Gradle build or test was run.
