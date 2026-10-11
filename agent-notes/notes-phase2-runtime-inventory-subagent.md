# Phase 2 runtime/server inventory

## Current state

- Assigned a read-only inventory of Phase 2 production runtime, server, application, and builtin code/tests against plan items 4-6 and 8.
- No source inspection or source edits have occurred in this subtask yet.
- Follow-up read-only scheduler preparation inspected plan lines 43-74 and committed `WorldTxn` API at `a360c86`; no source files were edited.

## Findings and observations

- Git is on `master`, ahead of `origin/master` by 142 commits; tracked state is clean and the visible changes are untracked notes/artifacts.
- The Phase 2 plan items under review require one complete production path: bounded executor, tickets, validation/retry/ordered effects, socket ingress, Wizard login/eval, manifest-dispatched `dump_database`, target-manifest-controlled `--promote-numbers`, restart/reconnect, and removal/replacement of placeholders or direct-world paths exercised by the row.
- Current production files relevant to this inventory are limited to `moo/runtime/MooRuntime.java`, `moo/server/MooServer.java`, `moo/app/Banteng.java`, `moo/builtin/BuiltinCatalog.java`, `moo/world/World.java`, `moo/world/WorldTxn.java`, plus their existing tests. No dedicated publication scheduler or durable task state source file is present in the path listing.
- `MooRuntime` is explicitly documented as serialized. Its public connection methods are `synchronized`; `executeStored` runs a local `ArrayList<VmState>` loop and calls `vm.execute(..., world, builtins)` directly against the shared `WorldTxn`. It has no production executor, ticket, read/write-set validation, retry, publication queue, or effect journal.
- `MooServer` has real blocking socket ingress and per-connection virtual reader threads, but those threads invoke the synchronized runtime methods directly. Socket output is written directly by the server callbacks; it is not ticket-published.
- `WorldTxn` is one mutable owner around a replace-on-write `World` snapshot. It provides direct reads and mutations but no base version, isolated transaction snapshot, validation, atomic commit, or retry contract.
- `BuiltinCatalog` is a large independent name-dispatch switch plus a separate `effectClass` switch. There is no `BuiltinSpec`, `CallShape`, permission/cost rule, owner, or handler catalog, and no `dump_database` dispatch case.
- `Banteng` accepts `--checkpoint` but deliberately leaves it unused; it has no `--promote-numbers`. It reads only v4 through `LambdaMooV4Reader`, starts `MooServer`, and has no checkpoint writer or restart path.
- At `a360c86`, the root `WorldTxn` owns `WorldHistory`; `begin()` retains the current revision and returns a child transaction. Child `commit()` returns `CommitResult(COMMITTED|CONFLICT, revision, conflictingRecords, conflictingPredicates, effects)` and releases its base. `close()` releases an unpublished base. Reads/writes/scans and staged effects are already recorded internally.
- Current `VmState` has no `VmSnapshot` API in the working tree or current commit. The requested conflict-restore design therefore depends on a still-pending slice not yet visible to this subagent.
- Commit `359b401` now supplies `VmState.snapshot()` and `VmState.restore(VmSnapshot)`. The obsolete observation above records the earlier checkpoint only; it is no longer a blocker.
- The assigned kept slice is limited to `src/main/java/moo/runtime/**`, `src/test/java/moo/runtime/PublicationSchedulerTest.java`, and necessary `MooRuntimeTest` changes. It must use one concrete scheduler, `WorldTxn.begin()/commit()`, and the committed VM snapshot API, then run the two exact WSL gates and commit only owned paths.
- Existing runtime/server tests exercise login, command evaluation, and socket behavior against the serialized/direct-world implementation. Existing application tests only cover version, invalid port, and required database. No named Phase 2 publication scheduler, v17 round-trip, durable-task architecture, or restart test exists in the current path listing.

## Blocker

- No inspection blocker. The requested Phase 2 behavior is broadly absent and cannot be completed as an isolated `dump_database` addition without replacing the serialized/direct-world runtime path.
- Scheduler design cannot name exact snapshot restoration calls until the pending `VmSnapshot` API is committed or visible; its required use is nevertheless clear: capture immediately before each segment, recreate state on conflict, and resubmit under the same publication ticket.
- The snapshot API blocker is resolved by `359b401`.

## Next implementation action

- Replace the inline `MooRuntime.executeStored` scheduler with the sole concrete production scheduler, add its exact tests, run the named WSL gates, reread the plan after each passing substantial gate, and commit only the owned runtime paths.

## Expanded coherent slice

- Parent explicitly expanded the still-clean slice to necessary `moo/runtime/**`, `moo/vm/**`, and `moo/builtin/**` production and focused test paths.
- The slice must replace builtin name/effect switches with the exact executable manifest contract, add the Phase 2 `dump_database` deferred checkpoint request, make VM workers yield value-only pending irrevocable calls, authorize them once at publication turn, and then complete the sole production scheduler.
- Owned tracked paths were clean immediately before the first source edit.
- Exact VM boundary selected: worker dispatch consults the executable builtin spec. `IRREVOCABLE` and `DEFERRED_COMMIT` calls become a value-only pending builtin request in `VmState`/`VmSnapshot`; publication commits the pre-call child, authorizes the exact request once, resumes the same task, and continues through the sole scheduler. Other builtin effects continue through manifest dispatch on the worker.
- No source edit has occurred yet; builtin manifest implementation is delegated to a bounded agent owning only builtin production/tests, while this agent owns runtime/VM integration.
- Mid-slice tracked state now has: value-only VM pending builtin/checkpoint state; manifest-aware VM yield/authorization; concrete bounded `PublicationScheduler`; public runtime ingress routed through concrete value requests; child-transaction attempt/session snapshots; ticket-order session/effect publication; and timeout work routed back through the scheduler. The old inline VM loop has been removed from `MooRuntime`; its concrete scheduler-owned VM task loop and publication-turn authorization are the immediate next edit.
- Compile/tests have not run yet because the coherent manifest migration is still being edited in the shared builtin-owned paths.
- The builtin agent reports the 60-entry manifest, exact `dump_database` spec/request, removed name/effect switches, and focused catalog test are now present.
- `PublicationScheduler` now owns the former inline runnable/timed/host VM loop. `MooRuntime.executeStored` only builds initial state and submits it to that scheduler. Publication-turn pending-builtin authorization commits the pre-call child, publishes session/effects, begins a continuation child, authorizes exactly once, and resumes through the same scheduler path.
- Immediate next action: compile the coherent in-progress slice, repair only owned compile errors, then add/finish scheduler tests and run the named gates.
- The delegated builtin agent is finished and no longer running tools. Its focused compile found only the runtime `ThreadLocalUsage` warning, which is fixed by the single static attempt context. A concurrent Gradle daemon left `.gradle/buildOutputCleanup` locked; `./gradlew --stop` stopped that daemon. Next action remains a lone compile rerun.

## Next action

- Trace exact test names and recent file history, then report the smallest coherent owned file set and which existing paths must be replaced rather than extended.

## 2026-07-18 focused-gate checkpoint

- `compileTestJava` passes in WSL with `JAVA_HOME=/opt/java/25`.
- The focused gate (`MooVmTest`, `VmSnapshotTest`, `DurableTaskStateArchitectureTest`, and `BuiltinCatalogTest`) ran 119 tests and failed 11. Every failure was an existing direct-VM test observing `PENDING_BUILTIN` instead of its prior returned, errored, or suspended outcome.
- The cause was overly broad boundary selection: `DEFERRED_COMMIT` calls were incorrectly treated like irrevocable calls, and transactional `create`/`recycle` were incorrectly classified `IRREVOCABLE`.
- The current correction yields only `EffectClass.IRREVOCABLE`; deferred effects execute speculatively and remain staged until ordered publication. `create` and `recycle` are now `TRANSACTION_WRITE`. Direct VM test helpers explicitly authorize true irrevocable requests; the isolated convenience execution path commits before authorization and resumes in a new child transaction.
- The user's earlier removal instruction referred to the separate invented conformance-cleanliness gate already removed in commit `83b9632`. No publication-boundary deletion occurred.
- No current external blocker. Next action is to rerun the identical focused gate, then continue to the runtime scheduler gate only if it passes.

## 2026-07-18 runtime-test checkpoint

- The identical focused VM/builtin gate now passes all 119 tests.
- `MooRuntimeTest` ran 59 tests and failed 49. The failures shown in the XML are all `IllegalStateException: begin a transaction before reading or writing world state` at stale test assertions that still call root `WorldTxn.object`, `property`, `verb`, or connection methods after the versioned-transaction commit.
- Runtime commands completed far enough to reach those assertions. The immediate correction is test-only immutable/child transaction reads; live connection state must be queried from its Phase 2 owner, `MooRuntime`, rather than the committed world root.
- A first bulk mechanical Perl replacement command failed before changing the file because its regex escaping was invalid (`Unmatched (`). No source was modified by that command.
- Next action: apply the test read migration with bounded patches or a corrected mechanical rewrite, add the minimal package-private runtime session queries needed by same-package tests, compile, and rerun `MooRuntimeTest`.

## 2026-07-18 runtime-test migration checkpoint

- Test compilation passes after migrating root object/property/verb assertions to short child-transaction reads and connection assertions to concrete package-private `MooRuntime` session queries.
- The `MooRuntimeTest` rerun improved from 49 failures to 16 failures out of 59. All 16 remaining failures are the same root-access contract at stale `world.objectCount()` calls.
- Those calls are now mechanically replaced with immutable `world.snapshot().objects().size()`. The one direct post-runtime test mutation (`addVerb`/`setVerbCode`) is now performed and committed through a child `WorldTxn`.
- No production behavior failure is currently established by this test run. Next action is another identical `MooRuntimeTest` rerun; if it passes, implement the required `PublicationSchedulerTest` and run the exact combined gate.

## 2026-07-18 scheduler-test checkpoint

- `MooRuntimeTest` now passes all 59 tests after the root-read migration.
- `PublicationSchedulerTest` now exists with all 13 exact required cases: monotonic tickets; fixed bounded executor; overflow retained in ready queue; reverse completion; turn-time validation; same-ticket conflict restore/retry; conflicted-effect discard; retry cursor hold; irrevocable turn execution; ordered output/forks/continuations; revision release; distinct child transaction per segment attempt; and no serialized fallback.
- The first scheduler-test run stopped at `compileTestJava` with two NullAway errors in the new test only (nullable exception-message dereference and an uninitialized listener scheduler reference). Both are corrected using exact message equality and an initialized `Optional<PublicationScheduler>`. Reported locale/reflection/unused-field warnings were corrected in the same test file.
- No production failure has been measured yet because the scheduler tests have not executed past compilation. Next action is rerun exactly `PublicationSchedulerTest`; then fix only actual production failures exposed by those named cases.

## 2026-07-18 scheduler-defect checkpoint

- The 13 scheduler tests executed far enough to prove two production defects: one child `WorldTxn` was reused across a suspend boundary, and a stale attempt waiting to authorize an irrevocable builtin could requeue and remain blocked behind the publication cursor indefinitely.
- Two reported output/effect assertions were test-observation mistakes, not production defects: `notify()` output is returned from the runtime request rather than sent through `ListenerControl.writeConnection`. Those tests now assert the exact returned output and retain ordering/duplicate-discard checks.
- Production correction 1 retries a stale publication-turn attempt inline on the same executor worker and same ticket instead of requeueing it into the cursor wait. The scheduler's close path also notifies waiters so a closed scheduler cannot strand them.
- Production correction 2 publishes `FORKED` and `SUSPENDED` boundaries, commits the current child, and installs a newly begun child before the continuation segment runs.
- The required rerun used `timeout 120s`. It stopped at test compilation only because two `RecordingListener` query methods became unused after correcting output observation, and Werror rejected those warnings. No scheduler test executed on that rerun.
- Next action: delete exactly those two unused test methods and rerun the same 13-test class with the same 120-second cap.

## 2026-07-18 remaining-deadlock checkpoint

- The 120-second capped 13-test rerun compiled cleanly, entered `:test`, and hit the cap without producing a completed XML report.
- An isolated irrevocable test stack showed both custom scheduler workers waiting on the same scheduler monitor: the earlier ticket at `publishSegmentBoundary`, the later ticket at `authorizePendingBuiltin`. Neither worker could advance the cursor.
- One proven cursor race was corrected by incrementing `nextPublicationTicket` and notifying waiters before completing the caller-visible future for success and failure publication. This ensures a synchronous `submit()` cannot return while its ticket still owns the cursor.
- The isolated irrevocable case still did not complete under a subsequent 60-second cap, so that ordering correction was insufficient. The current test worker is live for one more state/stack inspection.
- The distinct-child correction remains in place: fork and suspend outcomes publish their segment boundary and install a new child transaction before continuation.
- Current blocker is only the exact isolated irrevocable case. Next action is inspect the live post-correction stacks and scheduler state, fix that remaining cursor ownership defect only, then rerun the isolated case followed by the exact 13-test class with the 120-second cap.

## 2026-07-18 structural scheduler correction

- Parent review identified that the scheduler remained structurally invalid regardless of the deadlock: `executeVm` was the forbidden serialized runnable/timer/host loop transplanted from `MooRuntime`, and worker calls performed publication waits and commits.
- The exact invalid methods are now deleted from `PublicationScheduler`: the full `executeVm` local programs/timedTasks/hostTasks/pendingForks/runnable loop, `executeSegment`, `authorizePendingBuiltin`, `publishSegmentBoundary`, and `awaitPublicationTurn`.
- The worker call chain that depended on those methods still requires replacement; the tree is intentionally mid-slice and will not compile until the new segment-entry scheduler is installed.
- Controlling replacement: one ready-queue entry equals one captured `VmSnapshot` segment attempt with a distinct child `WorldTxn`; a worker executes once and returns an immutable attempt without waiting, validating, committing, sleeping, or performing host effects. `publishCompleted` alone handles turn-time validation/commit, same-ticket retry from segment-start snapshot, pending irrevocable authorization, and deterministic fork/suspend/host continuation enqueueing.
- The root ingress completion must remain correlated through intermediate segments and complete only at root return/error. The transient five-second diagnostic added during deadlock isolation must be removed before the kept tests/commit.
- Next action is replace the remaining request-shaped `Entry`/worker execution path with the value-only segment entry/attempt flow and adapt `MooRuntime.executeStored` to submit a captured root segment rather than invoking any local task loop.
## 2026-07-18 segment scheduler design checkpoint

- Current assignment: read Phase 2 plan lines 43-75 and the uncommitted `MooRuntime`, `PublicationScheduler`, and `VmState` only; make no production edits.
- Current user direction: the immediately preceding request says to remove the unrequested gate. The parent agent owns that removal; this subtask is limited to identifying the literal Phase 2 scheduler shape and any scope mismatch.
- Known state: the current scheduler still contains a worker-side VM loop and worker-side publication-turn waiting/authorization, which conflicts with the requested one-entry-per-captured-VM-segment design.
- Blocker: none for the read-only design review.
- Next action: inspect the exact plan lines and named files, then return concrete state/record shapes and control flow to the parent without editing source.
