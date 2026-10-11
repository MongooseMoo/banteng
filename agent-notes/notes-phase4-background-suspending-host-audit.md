# Phase 4 background/SUSPENDING_HOST read-only audit

## Current findings

- Banteng Phase 4 currently names server/task semantics and managed stock path gates, but does not name the active background-thread suites.
- The pinned stock and Mongoose checkouts match their profile commits.
- Their `src/background.cc` substrate and `background_test` blocks are identical: threaded mode packages a suspension, registers the task, runs the callback on the MAIN pool, and resumes it through the network callback; unthreaded mode invokes the same callback synchronously.
- `background_test` is registered as 0..2 arguments (`STR`, then `INT`) when `BACKGROUND_TEST` is enabled and returns the requested/default string after the callback delay.
- Active `background_threads.yaml` uses `background_test` as the observable producer for synchronous thread-mode behavior and for the suspended handle visible through `threads()`.
- Active `set_thread_mode` YAML proves activation-local 0/1 mode state, integer truthiness, no leak to the next task, 0..1 arity, and `E_TYPE` for a non-integer argument.
- Current Banteng already classifies connection `read` as `SUSPENDING_HOST`, but its wake is value-only: `BuiltinCatalog.Result`, `VmState`, and `PublicationScheduler` carry `CompletableFuture<MooValue>` and `VmState.resume` can only push a value. Host failure becomes scheduler failure rather than a catchable MOO error.
- `PublicationScheduler` already owns one bounded platform-thread executor and the ticketed ready/publication queues; virtual threads only wait for host/timer completion. This is the substrate the repaired Phase 4 gate must extend rather than duplicate.
- Existing Java authorities are `VmSnapshotTest`, `PublicationSchedulerTest`, `QueuedTaskV17CodecTest`, and `BuiltinCatalogTest`; they already cover durable host-wait markers, bounded executor/queue ownership, timed resume, v17 queued-task bytes, and catalog effect contracts, but not thread-mode state or value/error host completion.
- Pinned stock and Mongoose `all_members` are unconditional, byte-identical `background_thread(...)` producers with exact registration `all_members(ANY, LIST)`. Unlike `background_test`, this is a production canonical builtin in both source profiles.

## State

- The amended repaired plan is committed and pushed at `2cd7f73f43f0755fcaebb8610deeb13f50a5b1c3`; tracked state is clean, with pre-existing untracked notes and artifacts left untouched.
- The repaired Phase 4 lines 512-557 require one production `SUSPENDING_HOST` path, per-task thread mode with both v17 encodings, `set_thread_mode`, `all_members`, scheduler-owned bounded-executor submission, and value-or-catchable-error host completion.
- Live code still carries value-only `CompletableFuture<MooValue>` wake state through `BuiltinCatalog.Result`, `VmState`, `SegmentResult`, and `PublicationScheduler.Entry`; task thread mode is not yet present in `VmState`, `VmSnapshot`, or `QueuedTask`.
- `BuiltinHandler` currently has ten contextual parameters and 97 production/test lambdas use the common parameter tuple. Any thread-mode propagation must change the existing handler contract rather than introduce a second interface or adapter.
- The targeted Java gate now names `VmSnapshotTest`, `MooVmTest`, `PublicationSchedulerTest`, `QueuedTaskV17CodecTest`, and `BuiltinCatalogTest`, resolving the earlier missing authority.
- The existing `PublicationScheduler` has exactly one fixed-size `ThreadPoolExecutor` with an `ArrayBlockingQueue` of `workers * 4` and `AbortPolicy`; the production host callback must submit through that exact executor only after publication of the suspended VM boundary.
- Existing `MooValue.StringValue.equals` is case-insensitive, and recursive list/map equality is already represented by the immutable value types except for explicit boolean/integer compatibility; `all_members` can compute canonical 1-based matching positions without adding a new equality helper.
- The active tracked slice now changes the existing builtin invocation contract to receive activation thread mode without adding another interface, adds manifest entries for `set_thread_mode` and `all_members`, changes host work to `Callable<BuiltinCatalog.Result>`, and adds `VmState`/`VmSnapshot` thread-mode state.
- `MooVm` now applies a staged thread-mode change, dispatches host work, and exposes MOO-error resumption through its existing handler routing. `LambdaMooV17Codec.QueuedTask` now retains and writes either encoded mode and rejects values outside 0/1.
- `all_members` reuses the existing canonical `setremove` equality implementation against singleton lists to collect 1-based positions; it does not introduce an equality helper.
- The controlling plan is now at `cb380070938027648b7295c240d61cbf5c9b090d`. Thread mode has been corrected from task-global state to `VmState.Frame`/`VmSnapshot.Frame`: root and fork activations default enabled, verb/eval frames inherit, and frame return restores the caller's unchanged value. A nested verb-plus-eval isolation test is present but has not yet run.
- The amended cancellation item requires each host wait to be registered in the existing `TaskRegistry` before executor submission. The registry now stores the submitted `FutureTask`, cancels it from `kill_task`, and exposes an atomic terminal claim so normal/reject/cancel/failure paths cannot all win.
- No test has run since the activation-local or cancellation corrections. No commit, push, managed gate, sibling repository, or additional scheduler/executor has been changed or run.
- This untracked note exists only because the mandatory session checkpoint required an accepted notes update.

## Active adversarial review

- HEAD is `cb380070938027648b7295c240d61cbf5c9b090d`; the corrected active diff has exactly 13 tracked files, adding `TaskRegistry.java` to the prior production/test surface. No review gate has been run.
- Host work is submitted through the sole existing bounded `PublicationScheduler.executor`, wake entries now carry `BuiltinCatalog.Result`, and `MooVm.resumeWithError` routes MOO errors through captured handlers.
- `all_members` reuses the existing recursive, case-insensitive `setremove` equality and returns one-based matching positions.
- The earlier activation-local P1 is structurally corrected: mode now lives on each `VmState.Frame` and `VmSnapshot.Frame`; root creation defaults enabled, eval/verb frames inherit, and snapshot/restore preserves each frame independently. Fork and queued-v17 paths still require adversarial verification.
- The earlier unkillable-ingress P1 is structurally corrected at the registration point: `publishSuspension` now creates a `FutureTask<Result>` and calls `TaskRegistry.registerHost(...)` before `executor.execute(...)`. Terminal-race, late-wake, ingress completion, rejection/failure cleanup, and Toast metadata parity still require line-level verification.
- Concrete remaining P1: forked tasks stay registered from `registerFork(...)` until terminal completion, but a fork that later suspends in `all_members` calls `registerHost(...)`, whose `tasks.putIfAbsent(...)` rejects the already-live task ID. `publishSuspension(...)` has already advanced `nextPublicationTicket` before this exception, so the fork-host path throws instead of suspending and can strand ordered publication. The new tests cover ingress host cancellation but not fork-to-host suspension.
- Concrete Toast metadata mismatch: pinned Toast's external background enumerator labels `queued_tasks()` row field 2 with a string such as `waiting on thread <handle>` and derives programmer, activation, source line, receiver, and byte size from the suspended VM; its external listing intentionally omits runtime variables. Banteng registers host waits with integer `0` in field 2, source line `0`, byte size `0`, and returns copied locals when variables are requested. Visibility exists, but the observable row is not Toast-compatible.
- For ingress host waits, normal completion/error, cancellation, executor rejection, and worker failure are serialized by the registry terminal claim; successful cancellation consumes the canceled marker, removes ingress, completes it exceptionally, and prevents a later wake. No additional ingress terminal-race defect is currently evidenced.

## Blocker

- None. This is a read-only review; the mandatory checkpoint is the only file mutation and this note remains untracked.

## Next action

- Finish the narrow verification of root/fork/v17 thread-mode state and report the two concrete remaining findings if no further defect is evidenced.
