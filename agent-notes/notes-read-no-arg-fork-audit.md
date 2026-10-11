# No-argument fork read audit

## Target

`audit_read_no_arg_from_fork_denied` at HEAD `18e2e0d` only.

## Current findings

- The row calls `read()` from a zero-delay wizard fork and expects `E_PERM`.
- The current `read` manifest delegates no-argument calls to `MooRuntime.read`, which rejects all calls except two-argument truthy nonblocking calls with `E_INVARG`.
- Scheduler `Entry` owns `taskId`, `taskPlayer`, continuation, and the transient `startingBackground` bit. `BuiltinHandler` receives `taskId` but not a connection context or background classification.
- `taskPlayer` is inherited by a fork and therefore does not identify the last input-task context. The current connection ID remains in `RuntimeContinuation`; it is not transported to builtins. Inferring context from task IDs would invent a second task-identity rule.
- The smallest direct current-row change is a manifest-level `arguments.isEmpty()` guard returning `E_PERM` before delegation. Explicit read calls remain unchanged.

## State and blocker

- The rejected transactional ConnectionState association was removed because it incremented global session revision and masked deterministic world-conflict events.
- `PublicationScheduler` now owns one player-to-latest-LINE-task-ID map. Both submitted and detached authenticated LINE requests update it before dispatch; fork entries do not.
- `MooRuntime.read` checks the scheduler-owned association for zero-argument reads. The catalog independently requires a wizard programmer before delegating an implicit read.
- The focused runtime fork test, focused catalog permission/routing test, and complete `PublicationSchedulerTest` class all pass together.
- Verified command completed successfully in 16 seconds with 7 Gradle tasks (6 executed, 1 up-to-date).
- This notes file was required by the active checkpoint and must remain uncommitted.

## Next action

- Report the scheduler-owned association, removed transactional plumbing, passing tests, and uncommitted state. Do not commit.
