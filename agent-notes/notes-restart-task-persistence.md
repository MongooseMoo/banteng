# Phase 4 delayed-fork restart slice

## Exact target and authority

- Active row: `audit_suspended_task_survives_restart` in `task_scheduling_toast_oracle.yaml`.
- Despite its name, the row creates a delayed `fork (4)`, writes marker `before`, requests a dump, restarts the managed server, waits beyond the due time, and requires marker `after`.
- Pinned Toast `aecc51e9449c6e7c95272f0f044b5ba38948459e` passes the selected row: `1 passed, 22 deselected in 12.87s`.
- Toast v17 persists the delayed fork's absolute rounded epoch second, stable task ID, activation identities, named runtime environment, and dot-terminated standalone child source. Startup reparses the child body and restores the same task ID and due time.

## Proven Banteng mismatch

- `MooRuntime` calls the codec with `List.of()` for tasks.
- `LambdaMooV17Codec` rejects nonempty tasks, writes literal `0 queued tasks` and `0 suspended tasks`, requires those zero lines on read, and returns no tasks.
- `Banteng` discards the checkpoint task accessor and passes only the world into `MooServer`.
- `PublicationScheduler` owns the resumable child state but does not retain its timer in an enumerable durable collection.
- Therefore the child exists only in memory and is lost at process restart; the persisted marker remains `before`.

## Plan correction

- The plan previously required these restart rows in Phase 4 while saying queued-task round trips began in Phase 7.
- Commit `023a307` now makes Phase 4 own the minimal delayed-fork v17 round trip and makes Phase 7 extend it to complete queued and suspended task coverage.

## Implemented slice

- Compiler-produced `BytecodeProgram` values retain canonical MOO source, including each nested fork vector; manual constructors retain empty source.
- `LambdaMooV17Codec` reads and writes delayed forks through `QueuedTask(long taskId, long scheduledEpochSecond, String programSource, Map<String, MooValue> initialLocals, long programmer, ObjectValue verbLocation, long taskPlayer)` using the Toast v17 queued-task text shape.
- `PublicationScheduler` retains absolute-time delayed-fork timers, snapshots them for checkpointing, restores stable task IDs and due times, and advances the next task ID beyond restored IDs.
- `Banteng -> MooServer -> MooRuntime` carries restored tasks into the scheduler, and `MooRuntime -> PublicationScheduler` carries live delayed forks into checkpoints.

## Verification

- Focused compiler source, queued-task codec, and architecture tests passed after a clean build.
- `audit_suspended_task_survives_restart`: `1 passed, 22 deselected in 16.52s`.
- `audit_pending_forked_task_survives_genuine_offline_restart`: `1 passed, 22 deselected in 16.19s`.
- Full scheduling family improved from `18 passed, 5 failed` to `20 passed, 3 failed in 35.49s`; both restart rows pass.
- Exact `JAVA_HOME=/opt/java/25 ./gradlew check`: `BUILD SUCCESSFUL in 38s`.
- `git diff --check` passed.
- Remaining scheduling rows are separate Phase 4 targets: live-login `force_input`, `yin` yield, and background limits after resume.
