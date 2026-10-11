# Row 40 Barn authority subtask

## Scope and state

- Delegated target: `task_scheduling_toast_oracle::audit_default_bg_ticks`.
- Read-only Barn authority trace is complete. No Barn file was edited and no test was run.

## Normative findings

- `C:\Users\Q\code\barn\spec\tasks.md:7-17` makes pinned Toast behavior authoritative and identifies Toast `src/include/options.h` as compiled-limit ownership and `src/execute.cc` as `$server_options` override ownership.
- `spec/tasks.md:31-38` says `fork` statements create background tasks with compiled defaults of 30,000 ticks and 3 seconds; `$server_options.bg_ticks` and `.bg_seconds` override those values.
- `spec/tasks.md:57-72` says a fork waits until its start time and then runs with background limits.
- `spec/tasks.md:87-115` specifies public `fork` syntax, nonzero task-ID allocation, child environment capture, delayed queueing, and parent continuation.
- `spec/tasks.md:141-166` defines tick charging, repeats background defaults 30,000 / 3, and says `ticks_left()` returns remaining ticks. Builtin-call opcode cost is one tick.

## Current implementation trace

- Public source compilation: `bytecode/compiler.go:2532-2573`, `Compiler.compileFork`, compiles delay and emits `OP_FORK` plus the bytecode body range.
- VM dispatch: `vm/vm.go:664-665` sends `OP_FORK` to `vm.executeFork`.
- Fork capture/yield: `vm/control.go:24-117`, `executeFork`, validates delay, records body/context/locals, skips the body in the parent, and yields `FlowFork` with `ForkInfo`.
- Scheduler handoff: `scheduler/task_runtime.go:408-420`, `drainForks`, immediately calls `CreateForkedTask` while processing the parent's yield, then binds the returned child ID and resumes the parent.
- Background-limit selector: `scheduler/task_factory.go:23-25`, `backgroundTaskLimits`, calls `builtins.GetTaskLimits(true)`.
- Limit owner: `builtins/limits.go:18-25,41-54,66-73,92-102,183-199`; compiled `defaultBgTicks = 30000`, cached default 30,000, and positive `bg_ticks` / numeric `bg_seconds` overrides.
- Child creation: `scheduler/task_factory.go:199-281`, `CreateForkedTask`. At lines 215-221 it selects current background limits, initializes `Task` with them, creates the child VM, and sets `childVM.TickLimit = ticks`.
- `task.NewTaskFull` at `task/task.go:216-242` puts the selected value into both `TaskContext.TicksRemaining` and `Task.TicksLimit`, with zero ticks used.
- `vm.NewVM` at `vm/vm.go:82-93` starts with zero consumed ticks; `CreateForkedTask` explicitly overwrites its default tick limit with the selected cached background value.
- Only after limit selection, `CreateForkedTask` sets `t.StartTime = now + forkInfo.Delay` at line 246 and queues the task at line 281. Therefore limits are snapshotted at fork-child creation while the parent is being drained, not when the delay expires or the child first executes.
- On first child execution, `scheduler/task_runtime.go:103-114` attaches the child Task context to the preconfigured VM and runs `ExecuteLoop` without replacing the selected limit.

## Agreements and uncertainty

- Barn agrees with the 30,000 compiled background budget and `$server_options.bg_ticks` override semantics at child creation.
- Because `OP_CALL_BUILTIN` is charged before dispatch and the fresh child begins at zero VM ticks, a direct first child call to `ticks_left()` should observe 29,999 from a 30,000 budget.
- Potential timing uncertainty: Barn snapshots the cached background limit at `CreateForkedTask`, before the delay. The Barn spec states the value used by a fork but does not explicitly say whether an override changed during the delay should affect an already queued fork. The managed Toast row is the adjudicator, although the default-value row does not mutate overrides.
- No substantive blocker remains; this subtask is ready for parent handoff.
