# Phase 4 tracked-diff adversarial review

## Current state

- Read the supplied AGENTS instructions and `docs/reports/banteng-implementation-plan.md` Phase 4 lines 495-624.
- This bounded pass is inspecting only the tracked diff for `BuiltinCatalog.java`, `BuiltinHandler.java`, `MooRuntime.java`, `PublicationScheduler.java`, `TaskRegistry.java`, and the three matching tests.
- Read the complete production diffs and the relevant scheduler, registry, builtin, runtime-construction, and matching-test contexts.

## Findings and observations under verification

- The implementation carries thread mode on activation frames and snapshots, passes it through builtin dispatch, and preserves both queued-task v17 values.
- Host work is represented as a callable returning a value-or-MOO-error result and is submitted through the scheduler's existing executor after suspension publication.
- Host registry rows are installed before submission and use a string status, activation metadata, source line, and recursive snapshot byte-size calculation.
- Concrete finding: `PublicationScheduler.restoreQueuedTasks()` restores and starts each task's timer one at a time. An overdue earlier timer can run and allocate task IDs before later restored IDs have raised `nextTaskId`, causing collisions or duplicate registry rows. Restoration must install the complete restored set and establish the maximum next task ID before starting any timer.
- Cancellation terminal ownership appears serialized through `TaskRegistry`, but late-wake, ingress completion, `thread_pool`/`threads`, and test coverage still require final review.

## Blocker

- None in the code review. A mandatory checkpoint required this note despite the read-only task boundary; this file must remain uncommitted.

## Next action

- Finish the three matching test diffs and terminal-path review, then return severity and exact path/line evidence to the parent agent.
