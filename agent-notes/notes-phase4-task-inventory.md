# Phase 4 task inventory

## Accepted findings

- The Phase 4 plan requires the placeholder `queued_tasks()` deletion and the real task-registry-backed replacement to be one kept slice. Current production has no `queued_tasks`, `task_id`, or `kill_task` manifest entries, so there is no placeholder to delete.
- `PublicationScheduler` already assigns monotonically increasing internal IDs, but it does not expose a task registry to builtins and it delays the parent result until all fork children finish. Toast fork publication instead assigns the child task ID, queues the child, and lets the parent continue independently.
- The smallest first Phase 4 vertical slice is anonymous-fork publication plus a scheduler-owned task registry and the real `queued_tasks()` builtin. It should not include named-fork parsing/binding or `kill_task` yet.
- That slice should change the three `fork/fork_observation.yaml` rows, the four `capabilities/queued_tasks.yaml` rows, and the anonymous-fork timing rows `fork_zero_delay_executes`, `fork_float_delay`, and `fork_zero_delay` in `fork/fork_timing.yaml`.
- Direct authority tests should cover immediate parent completion independent of delayed/suspended children, exclusion of child output from the parent response, stable assigned child IDs and scheduled start times in the registry, registry removal on completion, and real manifest dispatch for `queued_tasks()`.
- Named fork binding is the next slice: parser/AST/compiler/VM must preserve the optional fork variable, and scheduler publication must assign the actual child ID into the parent continuation. `task_id()` and `kill_task()` then consume that same scheduler identity/registry rather than parallel state.

## Current state

- Inventory only; no source or test changes were made.
- This note was created solely because a mandatory checkpoint required a durable update during the read-only assignment.

## Next action

- Report the exact first-slice boundary and changed tests to the parent agent.
