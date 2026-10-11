# Row 35 Barn authority trace

- Exact row: `audit_task_local_not_inherited_by_fork` sets the parent task-local value to `{"parent", 7}`, forks immediately, suspends child and parent, then expects the child's `task_local()` result to be `{}`.
- Observed builtin path: `builtins/system.go` reads and writes `task.TaskLocal` through `GetTaskLocal` and `SetTaskLocal`; both builtins require wizard permissions.
- Observed task state: `task.Task` owns `TaskLocal`; both `NewTask` and `NewTaskFull` initialize it to an empty map.
- Observed fork search: `scheduler/task_factory.go` owns `CreateForkedTask`; fork creation and suspend scheduling still need exact inspection.
- Tried: exact row read, task-local symbol search, builtin/task-state inspection, fork-symbol search.
- Worked: the parent storage owner and default empty state are established from source.
- Did not work: nothing has failed.
- Current blocker: none.
- Next step: inspect `CreateForkedTask`, VM fork yield, scheduler run/resume ordering, and normative task specification without edits or tests.

## Checkpoint 2

- `vm/control.go:24-117` snapshots only VM locals and verb context into `ForkInfo`; `ForkInfo` has no task-local field.
- `scheduler/task_factory.go:199-281` constructs the child with `NewTaskFull`, copies VM locals and selected execution context, inherits programmer permissions, then explicitly assigns `t.TaskLocal = types.NewEmptyMap()` and attaches that child task to its context.
- Therefore the fork child clears rather than inherits the parent's task-local value.
- `scheduler/task_runtime.go:197-228` creates the fork child and resumes the parent; `:230-257` queues suspensions. `scheduler/task_queue.go:21-38` orders zero-delay suspensions so an already-ready fork can run first.
- Current blocker: none. Remaining work is the exact `suspend(0)` builtin/state path and normative spec comparison.

## Checkpoint 3

- `builtins/tasks.go:104-148` routes `suspend(0)` to the global task manager and returns `FlowSuspend`.
- `task/manager.go:147-164` implements zero seconds as `Suspend(0)` followed immediately by `Resume(0)`, leaving the task queued as a yield point.
- `task/task.go:393-438` owns suspend/resume state; zero-duration suspend does not assign a future wake time.
- `scheduler/task_factory.go:31-45` assigns monotonically increasing queue sequence numbers when child tasks are queued.
- Combined with the prior queue/runtime findings, the child is created with empty task-local state before either parent or child resumes, and suspension does not copy or replace task-local state.
- Current blocker: none. Next step is only the exact normative-spec search and comparison.
