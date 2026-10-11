# Phase 4 v17 restoration read-only review

## Scope and state

- Review only the current uncommitted Phase 4 v17 task and active-connection restoration slice.
- Corrected plan authority is `docs/reports/banteng-implementation-plan.md`, items 1-6 under the pre-Phase-5 v17 prerequisite.
- Pinned Toast authority was verified read-only at `/root/src/toaststunt`, commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- No implementation or test file has been edited by this review. Ward was not used.

## Current observations and provisional findings

- `LambdaMooV17Codec` reads queued, suspended, interrupted, and active-connection sections. It reverses the serialized Toast base-to-top runtime stack into an internal top-to-base list and reverses it again when writing; this specific order conversion is internally consistent with pinned Toast `write_activ()`.
- `LambdaMooV17Codec.write()` currently emits every `SuspendedTask` in the suspended section and always emits `0 interrupted tasks`, even when `SuspendedTask.interruptionStatus()` is present. This is a concrete loss of the interrupted v17 envelope/status on a read/write round trip and conflicts with the plan's exact interrupted-record requirement.
- Imported tasks retain their original `DurableTask` while waiting, so pre-resume checkpoints currently return the original record. Native timed suspensions are exported through `durableSuspension()`.
- Restored timers are registered dormant and activated through `activateRestoredTasks()`; startup ordering and exactly-once behavior still need direct inspection in `MooRuntime` and tests.
- Production `ToastV17ProgramLayout` contains no fixture task IDs or explicit fixture PC table in the searched surface. Exact fixture PCs occur in tests, as expected.
- `PublicationScheduler.restoreSuspendedTask()` constructs the imported snapshot with hard-coded default background tick/CPU limits and sets `SuspendedWork.startingBackground` to `false`. `executeSegment()` therefore uses `VmState.restore()`, not `MooRuntime.startBackgroundTask()`, so imported resumes ignore the current `$server_options.bg_ticks` and `$server_options.bg_seconds` overrides that pinned Toast applies at every background interpreter run. The earlier provisional note that these fields were zero was stale and has been removed.
- `PublicationScheduler.launchTimer()` removes a due task from `timedWork` before it enqueues the ready/wake entry. `durableTasks()` serializes only `timedWork`. A checkpoint in that gap, or while the task is ready but not yet executing, omits the task and violates exactly-once restart.
- `TaskRegistry.killTask()` removes registry state and marks the task canceled but does not remove its `TimedWork`. Until its due time, `PublicationScheduler.durableTasks()` continues to checkpoint the killed task; after restart it can execute because the cancellation set is not persisted.
- Host-suspended native VMs are placed in `TaskRegistry.hostWork` but never enter `PublicationScheduler.timedWork`, and `durableTasks()` only walks `timedWork`. A checkpoint silently omits native host-suspended work.
- Startup notification execution is correctly ticketed before `server_started`, and restored timers remain dormant until `scheduler.submit(STARTUP)` returns. However, `MooRuntime.activeConnections()` sorts by player/listener before checkpointing instead of preserving the runtime connection/listener order. The next restart therefore receives a fabricated notification order rather than the checkpoint owner's order.
- Pinned Toast defines `ROUND(tvp)` as `tv_sec + (tv_usec > 500000)`. Banteng serializes both queued and suspended native deadlines with `Math.floorDiv(epochMillis, 1000)`, shifting deadlines with a fractional part above 500 ms one second earlier than Toast.
- `timedWork` is a `TreeMap` keyed by task id. Restored equal-due/overdue tasks are enumerated in task-id order, then launched on separate virtual threads whose scheduler-lock acquisition determines ready-ticket order. Pinned Toast inserts restored tasks into the time-sorted waiting list and preserves file order for equal start times. The exact Mongoose fixture's overdue tasks can therefore resume nondeterministically and out of file order.
- Pinned Toast serializes an exhausted map iterator as `TYPE_CLEAR` tag 5 (`db_io.cc`, `dbio_write_var`), while the codec/importer only recognize tag 6 as an empty map cursor. A valid suspended map loop at exhaustion is rejected by `readValue(tag 5)`. Existing synthetic tests use tag 6 and therefore encode the wrong pinned representation.
- `PublicationScheduler` rejects nonzero `functionId`, disabled debug mode, and nonempty activation temporary values. `VmSnapshot.Frame` has no fields for debug, temporary, or `verbNames`, and export forces debug true and makes verb names equal to `verb`. This is not complete retention of the activation metadata and saved values required by plan item 1.
- `QueuedTaskV17CodecTest` reads the exact fixture and asserts ids, frame count, raw catch marker count, root vector, statuses, and six connections, but it does not assert the active loop in that named test class. `PublicationSchedulerTest` imports all sixteen Mongoose tasks only as dormant snapshots; no production-start regression resumes every exact imported task once or proves no pre-suspension effect replay, as plan item 6 requires.
- No fixture task-id/PC table, source-line continuation matching, fake production label table, alternate runtime, alternate scheduler, or external Toast normalization dependency was found in the production path.

## What was checked

- Corrected plan items 1-6.
- Codec task/connection record definitions and read/write paths.
- Pinned Toast `write_task_queue`, `read_task_queue`, `write_activ`, `read_activ`, runtime-stack ordering, and catch/finally marker authority.
- Scheduler import/export, timer staging, wake construction, and durable task enumeration.
- `VmSnapshot` and `VmState` frame/operand/handler/finally/loop representations.
- Runtime startup request ordering, checkpoint task/connection collection, and registry cancellation lifecycle.

## Blocker

- No external blocker. The read-only review is complete.

## Next action

- Parent should repair the findings before committing the current Phase 4 slice, add the exact plan-required regressions, and rerun the focused and full WSL gates.
