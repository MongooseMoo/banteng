# Phase 2 scheduler rebuild checkpoint

- Branch: `master`.
- Kept commits named by the controlling task: `a360c86` (`WorldTxn`) and `359b401` (`VmSnapshot`).
- Active shared tracked diff: builtin catalog, runtime, VM state/snapshot, and runtime/VM tests; builtin contract sources and scheduler tests are also untracked.
- Current blocker: `PublicationScheduler.java` is untracked and incomplete while `MooRuntime` still requires the scheduler execution entry point, so the slice does not compile.
- Controlling architecture: one deterministic ticket queue and one fixed bounded executor for both VM segments and runtime transitions; worker attempts are value-only and publication is scheduler-owned in ticket order.
- Next action: read the existing scheduler/runtime/test diff and the exact 13 required test names, then complete only the runtime scheduler path and necessary already-in-slice tests without committing.

## First compile and edit checkpoint

- First WSL `compileJava compileTestJava` failed only at `MooRuntime.java:1157`: the deleted synchronous loop left a call to missing `PublicationScheduler.executeVm(BytecodeProgram, VmState, long)`.
- The replacement does not restore that method. `MooRuntime` now has the exact closed runtime-transition enum and value-only `RuntimeContinuation`/`RuntimeStep` records.
- Close, line, transport OOB, login authentication/association hooks, and login-timeout paths are being split at their existing stored-verb boundaries so each worker can execute one attempt without waiting for VM publication.
- Current state is intentionally non-compiling mid-refactor; `PublicationScheduler` still needs replacement and the VM boot-player transition still needs conversion.
- Next action: finish the remaining runtime transition conversion, replace `PublicationScheduler` with value-only entries and scheduler-owned ingress futures, then compile before touching tests.

## Scheduler replacement compile checkpoint

- `PublicationScheduler.java` is now a value-only entry scheduler: `EntryKind` is exactly `VM_SEGMENT` or `RUNTIME_TRANSITION`; ingress futures are held only in `Map<Long, CompletableFuture<List<String>>> ingress`; intermediate segments retain their ticket; workers execute one attempt and return an immutable `Attempt` record.
- Publisher code now owns validation/commit, runtime effect publication, pending-builtin authorization, conflict requeue, fork/suspend/timer/host wake enqueue, and root future completion.
- The first compile against the replacement reached only static-contract failures: Error Prone rejected redundant compact-record self-assignments, NullAway rejected nullable convenience arguments, and one login helper parameter is unused.
- No behavioral test has run yet. Current blocker is only those exact compile diagnostics.
- Next action: remove the redundant assignments and nullable convenience calls, compile again, then reread the plan before the focused scheduler test.

## Focused scheduler test checkpoint

- `compileJava compileTestJava` passed after the narrow static-contract fixes.
- The focused 13-method scheduler class completed under the 120-second cap with 9 passing and 4 failing methods.
- The four failures were: a test incorrectly used nonblocking `suspend()` as executor saturation; two exact-output assertions did not account for the existing Test.db eval prefix/return/suffix protocol; and the irrevocable test used invalid port `0`, so `listen` correctly returned `E_INVARG` before reaching the fake listener.
- A speculative production in-flight cap change was made from the invalid overflow premise; root explicitly rejected that premise. It must be reverted before any rerun.
- Parent/child output accounting is being kept as scheduler behavior so the root result can preserve publication-order child output.
- Next action: revert only the speculative capacity hunk, make the overflow test occupy real platform workers and executor slots, assert protocol-relative output without filtering, use a valid fixed fake-listener port, remove the transient wait diagnostic, and rerun all 13 under the same cap.

## Overflow-test correction checkpoint

- The first corrected rerun passed 12 of 13 tests; only the overflow test failed because its direct executor saturation submitted blockers before the first blocker was reliably active.
- After changing the test to await one active blocker before filling four queue slots, the capped run entered `:test` but produced no new XML and was terminated by the 120-second wrapper.
- The existing XML remains the prior 12-of-13 result. Directly injecting latch runnables into the production executor is therefore a racing and unreliable test method.
- Production capacity logic remains restored to the original fixed-executor active/remaining-capacity check; no speculative in-flight cap remains.
- Next action: replace direct executor injection with a scheduler-controlled test worker gate that blocks only test-selected worker attempts, fill exactly one active plus four queued scheduler entries, assert the sixth remains in the value ready queue, release, and rerun all 13 under the cap.

## Scheduler-owned overflow fixture checkpoint

- Root identified the direct-injection hang precisely: latch runnables placed directly in the private executor never execute scheduler `complete()`/`dispatch()`, so releasing them cannot drain scheduler-ready entries.
- The direct executor fixture is removed. No production hook or capacity change was added.
- The overflow test now blocks the sole scheduler worker inside `RecordingListener.setConnectionBinary` while publishing a valid `set_connection_option(player, "binary", 1)` task.
- While that scheduler-owned attempt is blocked, the test submits four scheduler tasks for the executor queue and one excess value entry for `ready`, asserts the ready entry and pending root, releases the listener latch, then joins all six scheduler roots and asserts `ready` drains.
- Next action: rerun all 13 scheduler methods under the exact 120-second cap and report the exact result before proceeding to `MooRuntimeTest`.

## Publication-drainer proof checkpoint

- The scheduler now uses one boolean `publicationDraining` election. Completion buffering and drainer election occur under the scheduler monitor; the elected thread removes only the current ticket there, releases the monitor for validation, commit, session/listener effects, pending-builtin authorization, timer setup, and future completion, then reacquires only for cursor/ready/dispatch mutations.
- Other worker completions buffer and return while the boolean drainer is active. No executor, lock, callback interface, or test hook was added.
- `compileJava compileTestJava` passed in 21 seconds after this refactor.
- The isolated scheduler-owned overflow test passed under the exact 120-second wrapper in 8 seconds, proving blocked listener publication no longer prevents ingress queue fill and ready drain.
- The production architecture clauses were reread after both passing gates.
- Current blocker: none in the isolated overflow path.
- Next action: run all 13 `PublicationSchedulerTest` methods under the same cap, reread the plan if green, then run `MooRuntimeTest`.

## Ticket-rotation checkpoint

- All 13 scheduler methods passed under the 120-second cap before the runtime suite.
- `MooRuntimeTest` then exposed one forced-input assertion failure and hung in `interruptsActiveSqliteQueryAfterExactDelayedForkAndSuspend`.
- Live thread evidence proved the hang: the parent kept its ticket while suspended on the SQLite host future; its delayed fork child had a later ticket and could not publish `sqlite_interrupt`, while all platform VM workers were idle.
- Ticket semantics now match the explicit correction: only conflict retry reuses a ticket. Every successful validation/commit/publication advances the current cursor; runtime transitions, VM continuations, post-irrevocable continuations, fork parent, and ready fork child receive `nextTicket++`; timed and host-wake continuations receive `nextTicket++` only when the wake is enqueued.
- Stable task/root IDs still correlate continuation tickets and fork output.
- `compileJava compileTestJava` passed in 16 seconds after this change.
- The forced-input assertion remains untouched pending the ticket-rotation proofs.
- Next action: run only `interruptsActiveSqliteQueryAfterExactDelayedForkAndSuspend` under the 120-second cap, then all 13 scheduler methods, then reobserve the full runtime suite.

## Per-segment test-alignment checkpoint

- The isolated `interruptsActiveSqliteQueryAfterExactDelayedForkAndSuspend` test passed in 11 seconds under the exact 120-second wrapper after ticket rotation.
- The first post-rotation 13-test scheduler rerun completed in 13 seconds with 5 passing and 8 failing. The failures were all stale whole-root ticket assumptions except for one production result-order defect: child output preceded parent/root output.
- Production now records root output and child output separately and combines them root-first. `dispatch()` also returns when the scheduler is closed, preventing executor submission after shutdown.
- The conflict/retry tests are being aligned to exact per-segment evidence: a completed later segment is buffered behind an earlier CPU-slow segment, conflict retry reuses only its exact ticket, and successful re-entry receives a strictly newer ticket. The test helper identifies the exact completed binary-option segment from its `VmSnapshot` rather than assuming `rootTicket + 1`.
- The focused suite has not been rerun since the latest production and test patches. The current possible blocker is compile fallout from remaining callers of the changed conflict helper and the still-stale discard-effects/irrevocable tests.
- Next action: update those remaining exact named tests, compile, then rerun all 13 scheduler methods under the exact 120-second cap before any runtime-suite diagnosis.

## Production-event conflict fixture checkpoint

- `compileJava compileTestJava` passed in 22 seconds after the first per-segment test migration.
- The next capped scheduler reruns were 6/13 and then 7/13. Every remaining conflict-family failure came from an invented five-second poll of the scheduler's transient private `completed` map; changing the searched snapshot field did not make that fixture valid.
- Root explicitly rejected that substitute: the kept test must have no invented five-second await helper, must establish conflict completion/publication order through explicit production latches/events, and must capture actual segment tickets.
- The rebuilt scheduler had retained the existing production JFR event definitions but did not emit them. It now commits `moo.TaskSegment` when a VM attempt finishes and `moo.WorldConflict` when publication rejects a world transaction, carrying the actual task and ticket values.
- The replacement fixture will use one production fork to create adjacent parent/child VM entries. The later child finishes first; the earlier parent commits and blocks at the real listener latch; the child then conflicts and retries under its captured ticket after the parent is released.
- Current blocker: the old polling helper and its conflict tests still need replacement with `RecordingStream`-driven latches, and the generic five-second await helper must be deleted from the entire kept scheduler test.
- Next action: replace the conflict scenario and its seven consumers, convert overflow waits to explicit latches/events, compile, and rerun exactly `PublicationSchedulerTest` under the external 120-second cap.

## Read-only production-diff audit checkpoint

- Scope inspected: only the five modified and ten untracked production files under `src/main/java` for Phase 2 items 4-8; no production file was edited and no test or conformance command was run.
- Current finding: `dump_database()` produces and stages a `CheckpointRequest`, but the changed runtime publication path drains only connection, forced-input, and boot requests. No production consumer drains checkpoint requests, so the builtin can return `0` without writing or atomically replacing a checkpoint.
- Current finding: the scheduler executes an irrevocable builtin before `WorldTxn.commit()` validation. If that commit conflicts, it requeues the already-authorized entry and can repeat external listener or SQLite effects.
- Current finding: the public convenience `MooVm.execute(program, state)` now owns a second transaction/commit/pending-builtin authorization loop outside `PublicationScheduler`, contrary to the one-scheduler/one-execution-path requirement.
- Current finding: Phase 2 manifest construction registers the entire existing builtin surface, including the placeholder `queued_tasks()` implementation, although the plan says the manifest is expanded only by later vertical slices.
- Blocker: none for completing the audit report; the current production diff is not a coherent Phase 2 items 4-8 commit until these contradictions are resolved.
- Next action: finish the remaining scoped read-only checks, then report exact actionable findings with file and line evidence to the parent agent.
