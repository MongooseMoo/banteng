# Phase 5 `sort` causal-order audit

Date: 2026-07-21
Mode: read-only. This note must remain untracked. No tracked file was edited, and no Gradle, conformance, commit, or push command was run.

Audit-start state: `HEAD 9bfb95c` (`Implement explode builtin`) matched `origin/master` with clean tracked state. During the audit, another agent began the expected two-file tracked `reverse` slice; this audit identified that ownership and left the diff untouched.

## Conclusion

`sort` cannot be implemented exactly in Phase 5 using the current Banteng infrastructure.

The missing prerequisite is not an implementation deliverable assigned to Phase 6. Phase 6 at `docs/reports/banteng-implementation-plan.md:586-637` only adds proof and stress artifacts for production concurrency that the architecture and Phase 4 already say exists. The runtime prerequisite belongs before Phase 5 under Phase 4's "complete ... task ... suspend, read, resume" requirement at lines 495-510 and the architecture's host-operation boundary at lines 46-75.

The causal defect is therefore: the plan leaves the required generic `SUSPENDING_HOST` substrate implicit and ungated in Phase 4, and current code passed into Phase 5 with only a dormant value-only wake skeleton. Phase 5 then requires every builtin implementation to be complete at lines 544-561, but pinned `sort` depends on the absent substrate.

The minimal order correction is to reopen Phase 4 and make that existing Phase 4 prerequisite explicit and gated before its "Commit the kept phase before Phase 5" line 542. Do not move `sort` to Phase 6, and do not treat Phase 6 hardening as authority to create the missing runtime path after `sort`.

## Why pinned `sort` requires the prerequisite

Both selected profile commits implement `sort` as:

```c
return background_thread(sort_callback, &arglist);
```

Both profile configurations set `TOTAL_BACKGROUND_THREADS 2` and `DEFAULT_THREAD_MODE true`.

Therefore the default pinned behavior is:

1. registration validates arity and declared argument types;
2. `background_thread` attempts bounded host-worker setup;
3. setup failure returns catchable `E_QUOTA` before callback validation;
4. successful setup suspends the current MOO task;
5. `sort_callback` computes later and resumes the same task with either a value or a MOO error such as `E_INVARG`/`E_TYPE`;
6. per-task thread mode can disable offload and execute the same callback synchronously.

A synchronous catalog method is not exact. It changes suspension, CPU accounting, publication timing, error precedence, `E_QUOTA`, and the behavior of `set_thread_mode`.

## Current path audit

| Required path | Current artifact | Exact deficiency |
|---|---|---|
| Builtin declares host suspension | `src/main/java/moo/builtin/EffectClass.java`; `read` manifest entry in `BuiltinCatalog.java` | `SUSPENDING_HOST` is only metadata; it does not select an execution path. |
| Builtin produces host work | `BuiltinCatalog.Result.hostResult` at `BuiltinCatalog.java:3151-3168` on audit-start `HEAD 9bfb95c` | Field type is `Optional<CompletableFuture<MooValue>>`; no current factory or builtin constructs it. |
| Suspended VM retains wake source | `VmState.hostResult` / `suspend(...)` at `VmState.java:49,441-448` | Only a live `CompletableFuture<MooValue>` is retained; no MOO-error outcome is representable. |
| Host callback resumes with value or MOO error | `VmState.resume(MooValue)` at `VmState.java:451-460` | Resume always pushes a value. There is no host-error resume path that raises a catchable MOO error. |
| Snapshot/restoration | `VmSnapshot.awaitingHostResult`; `VmState.restore(...)` at `VmState.java:590-592` | Snapshot stores only a boolean; restore explicitly clears the future and has no owner that can reattach or resolve pending host work. |
| Scheduler publishes suspension | `PublicationScheduler.publishSuspension(...)` at lines 601-638 | It starts a virtual waiter for an already-existing future; it does not own or submit the host computation. |
| Exceptional host completion | `publishSuspension` lines 631-636; `failWaiting(...)` lines 690-704 | A failed future becomes scheduler/root-task failure, not a MOO error delivered to the suspended activation. |
| Bounded host worker | `PublicationScheduler.executor` and architecture lines 71-75 | The only bounded executor is the private platform-thread VM-segment executor. No host-work submission owner or Toast-style `E_QUOTA` rejection contract exists. |
| Per-task thread mode | `VmState`, `VmSnapshot`, builtin catalog | No thread-mode field or `set_thread_mode` implementation exists. |
| v17 thread-mode persistence | `LambdaMooV17Codec.java:383-384` | Reader rejects every queued-task value except literal `1` and discards it instead of storing/restoring task state. |
| Existing `SUSPENDING_HOST` proof | `read` entry and `MooRuntime.read(...)` at lines 1639-1673 | `read` returns immediate values/errors, including `0` for absent input; it never exercises `hostResult`, suspension, or host resume. |
| Executable tests | `BuiltinCatalogTest`, `VmSnapshotTest`, `PublicationSchedulerTest` | Tests assert metadata and that an awaiting-host boolean survives snapshot; no production builtin produces a host wake and no test proves value/error resume or worker rejection. |

## Existing infrastructure that is real but insufficient

The following pieces are not placeholders in isolation:

- `MooVm` recognizes a populated `Result.hostResult` and changes the VM outcome to suspended.
- `PublicationScheduler` publishes the suspension boundary in ticket order.
- A virtual waiter can enqueue a completed host value back through the ready queue.
- `VmState.resume(MooValue)` resumes the exact captured VM with that value.

Those pieces establish a value-only continuation skeleton. They do not supply the missing producer, bounded worker, MOO-error result, thread-mode state, or restoration owner. Their presence does not make exact `sort` executable.

## Plan-order finding

The plan does not literally assign generic host-work implementation to Phase 6:

- architecture lines 46-75 require one production scheduler/executor from the first vertical slice and reserve virtual threads for host-operation boundaries;
- Phase 4 lines 495-510 require complete production task suspend/read/resume semantics through that path;
- Phase 4 line 542 requires the kept phase committed before Phase 5;
- Phase 5 lines 544-561 require complete builtin implementations, including exact effect contracts;
- Phase 6 lines 591-606 name only concurrency proof/stress artifacts for the already-production executor.

Phase 7's later complete suspended-task checkpoint/restart work does not authorize deferring ordinary host execution and error resume past Phase 5. It may extend durability, but `sort` still needs a working default host suspension path to satisfy Phase 5's managed builtin gates.

Thus the plan's high-level causal order is Phase 4 substrate -> Phase 5 `sort` -> Phase 6 hardening, but the Phase 4 text is not literally executable enough to force the substrate to exist. Current state demonstrates that omission: Phase 4 was treated as complete without it.

## Minimal plan-order correction

Before Phase 4's gates and before line 542, add one explicit prerequisite deliverable requiring the existing production path to provide all of the following before Phase 5:

- durable per-task thread mode in `VmState` / `VmSnapshot`, default enabled for the pinned profiles and round-tripped by `LambdaMooV17Codec`;
- an error-bearing host completion from `BuiltinCatalog.Result` through `MooVm`, `VmState`, and `PublicationScheduler`, capable of resuming the same activation with either a value or a catchable MOO error;
- one named bounded host-work owner whose submission/resource failure maps to `E_QUOTA` and whose completion re-enters the existing ready queue/publication-ticket path;
- shutdown, cancellation, snapshot, and retry behavior consistent with the existing single scheduler;
- executable proof in the existing `VmSnapshotTest`, `PublicationSchedulerTest`, and `QueuedTaskV17CodecTest` that covers value resume, MOO-error resume, rejection, thread-mode round trip, and no alternate scheduler.

Then rerun and commit the corrected Phase 4 gate before returning to Phase 5. Phase 6 remains where it is and continues to prove/harden the already-working production concurrency.

This is the minimum causal correction. Moving only a test, marking `sort` `PURE`, validating callback errors synchronously, using the VM executor as an unnamed host pool, returning an immediately completed future, or deferring the gap to Phase 6 would each be a workaround and is not authorized.

## State

Audit complete. Exact missing artifact: the production, bounded, error-bearing, per-task-thread-mode-aware `SUSPENDING_HOST` path across `BuiltinCatalog.Result` -> `MooVm` / `VmState` -> `PublicationScheduler`, with v17 state ownership. It is a Phase 4 prerequisite that must be explicit and green before Phase 5 `sort`; no existing code path currently satisfies it.
