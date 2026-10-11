# Row 39 Barn authority subtask

## Current state

- Delegated target: `task_scheduling_toast_oracle::audit_default_fg_ticks`.
- Scope is read-only inspection in `C:\Users\Q\code\barn`.
- Required output is the exact normative Barn spec for foreground default ticks and `ticks_left()`, plus the current Barn implementation trace from public builtin entry to semantic owner.
- The immutable Banteng plan must not be inspected or changed.
- No Barn files have yet been inspected and no commands have yet been run for this delegated task.

## Observations and attempts

- The task arrived after the parent had completed and committed Banteng row 38.
- Barn's normative task authority is `C:\Users\Q\code\barn\spec\tasks.md`. Its Authority section says to match the pinned Toast behavior; it identifies Toast `src/include/options.h` as the compiled-limit owner and `src/execute.cc` as the `$server_options` override owner.
- `spec/tasks.md` specifies a new foreground task at compiled defaults of 60,000 ticks and 5 seconds; `$server_options.fg_ticks` and `.fg_seconds` override those defaults. Its tick table repeats foreground 60,000 / 5 and says `ticks_left()` reports remaining ticks.
- `spec/builtins/tasks.md` specifies `ticks_left() -> INT` and says it returns the current task's remaining ticks. Its eval-context note says 0 only where no tick limit is configured; that note is not the scheduled foreground-task case.
- Public registration is `builtins/registry.go`: `r.Register("ticks_left", builtinTicksLeft)`. VM dispatch flows through `vm/op_misc.go:executeCallBuiltin` to `Registry.CallByID` / `Registry.dispatch` and then `builtins/system.go:builtinTicksLeft`.
- Foreground limit ownership is `builtins/limits.go`: compiled defaults `defaultFgTicks = 60000`, `defaultFgSeconds = 5.0`; `GetTaskLimits(false)` returns cached foreground limits; `LoadServerOptionsFromStore` resets to defaults then accepts positive integer `fg_ticks` and positive numeric `fg_seconds` overrides.
- Scheduled foreground creation flows through `scheduler/task_factory.go:foregroundTaskLimits` to `builtins.GetTaskLimits(false)`, then `task.NewTaskFull`, which sets both `Task.TicksLimit` and `TaskContext.TicksRemaining` to that limit.
- `scheduler/task_runtime.go:runTask` gives a new VM `bcVM.TickLimit = t.TicksLimit`. `vm/vm.go:executeLoop` increments `vm.Ticks` and synchronizes `TaskContext.TicksRemaining = TickLimit - Ticks` before dispatching a tick-counting opcode.
- `bytecode/opcodes.go:CountsTick` counts `OP_CALL_BUILTIN`; `vm/op_misc.go:executeCallBuiltin` then invokes the registered builtin. Therefore a direct source call to `ticks_left()` observes the foreground limit after the call opcode's own tick has already been charged: normally 59,999 from a fresh default 60,000 task.
- `builtins/system.go:builtinTicksLeft` returns `ctx.TicksRemaining` when positive, otherwise tries `Task.TicksLeft()`, otherwise returns 1. The `Task` fallback is not VM-live because VM execution does not update `Task.TicksUsed`; the live semantic value is the synchronized context field.
- `spec/vm.md` explicitly says Barn's sparse `CountsTick` classification differs from Toast's broader opcode charging and is not normative MOO tick behavior.
- Exact normative citations captured: `spec/tasks.md:7-17` (Toast authority and owners), `spec/tasks.md:24-38` (foreground compiled defaults and server-option overrides), `spec/tasks.md:141-166` (tick charging, 60,000/5 table, and `ticks_left()`), and `spec/builtins/tasks.md:240-246` (builtin contract).
- Exact implementation citations captured: `builtins/limits.go:18-25,41-54,66-73,92-102,177-193`; `scheduler/task_factory.go:19-24,48-56`; `task/task.go:215-220,223-234,326-330`; `scheduler/task_runtime.go:145-149`; `vm/vm.go:157-165,273-282`; `bytecode/opcodes.go:246-255`; `vm/op_misc.go:10-48`; `builtins/registry.go:298,399-407`; `builtins/system.go:136-158`.
- A focused test search found no Barn `_test.go` assertion for `ticks_left()`, 59,999, or a fresh default foreground value. Existing scheduler tests merely call `foregroundTaskLimits()` as setup.
- One narrow `rg` line-number query had Windows quoting errors around an alternation containing quoted `fg_ticks`; this did not affect the findings because the relevant lines were already captured from direct file reads and separate successful searches.
- No Barn source/spec file has been edited. Only this untracked checkpoint note has changed.

## Current blocker and next step

- There is no substantive blocker. The delegated read-only task is ready to report.
- The only uncertainty to flag is whether the conformance row expects the compiled budget itself (60,000) or the first observable `ticks_left()` result after charging the call opcode (59,999). Barn's own normative spec establishes both the 60,000 starting budget and the fact that the builtin-call opcode costs one tick, which implies 59,999 for the direct call; the exact managed Toast row is the external adjudicator.
