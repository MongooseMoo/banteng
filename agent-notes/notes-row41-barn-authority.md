# Row 41 Barn authority handoff

Scope: read-only authority trace for `task_scheduling_toast_oracle::audit_server_options_fg_ticks_runtime`. No tracked files, production code, or Git state have been changed.

## Current findings

- `docs/reports/banteng-implementation-plan.md:131-139` fixes the authority order as freshly verified Toast, durable `moo-conformance-tests` rows, corrected Barn spec, then Barn only as an implementation reference. Barn behavior must therefore be reported separately from the row's Toast authority.
- `docs/reports/banteng-implementation-plan.md:151-167` requires tracing Barn and Toast public entry points through their semantic owners, freezing a Toast-proven row, then implementing; this subtask stops before implementation.
- `docs/reports/banteng-implementation-plan.md:376-387` makes exact convergence one discrepancy at a time and requires the focused row to define the observable.
- Exact row: `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml:84-105`. Step 1 adds or sets `$server_options.fg_ticks = 12345`, calls `load_server_options()`, and returns. Step 2 is a separate harness `run` that asserts `ticks_left() <= 12345 && ticks_left() > 12000`. Cleanup deletes the property and reloads options.
- Harness timing: `runner.py:268-272,420-450` executes each YAML step separately through `transport.execute`; `transport.py:373-391` sends each execution as a distinct `; ...` eval command on the socket. Thus the assertion is in a new foreground eval task, not in the task that called `load_server_options()`.
- Barn cache: `builtins/limits.go:22-25,35-55` defaults FG to 60000 ticks / 5 seconds. `GetTaskLimits(false)` returns the cached FG pair at `limits.go:66-73`.
- Barn reload: `builtins/system.go:605-627` validates zero args and wizard status, then calls `LoadServerOptionsFromStore`. `builtins/limits.go:89-101,118-151,177-182,208-219` resolves `#0.server_options` to the options object, reads its directly defined integer `fg_ticks` when positive, atomically replaces the cache, and returns a count of loaded recognized properties. Therefore setting 12345 then loading makes the Barn cache's foreground tick limit 12345 immediately after the builtin executes.
- Barn ordinary scheduler factory: `scheduler/task_factory.go:48-58` reads the cached foreground limits before `task.NewTaskFull`; `task/task.go:215-232` copies that tick limit into both the task context's `TicksRemaining` and task limit. This is a construction-time snapshot; changing the cache does not mutate an already-created task.
- `builtins/system.go:136-155` returns the context's current `TicksRemaining` from `ticks_left()`. VM synchronization at `vm/vm.go:157-165` sets it to `TickLimit - Ticks`, so the row allows up to 345 ticks of setup/evaluation overhead by accepting 12001..12345 inclusive.
- Barn's exact socket-eval path is now resolved: `scheduler/eval.go:18-19,38-68` compiles each intrinsic eval independently, calls `foregroundTaskLimits()` at lines 67-68, and creates/registers a new task with that tick/seconds snapshot. It sets `bcVM.TickLimit = ticks` at lines 75-79. The VM decrements/counts before the builtin observable and synchronizes `Context.TicksRemaining = TickLimit - Ticks` at `vm/vm.go:157-165,272-282`. Therefore the second row step sees a value slightly below or equal to 12345 and over 12000; the first row step's already-created task retains its old limit even though `load_server_options()` changes the global cache during that task.
- Barn task/spec contract: `spec/tasks.md:24-39` classifies player-input/server-initiative never-suspended tasks as foreground and says `fg_ticks` overrides the 60000 default; `spec/tasks.md:145-168` defines charged opcodes and `ticks_left()` as remaining ticks. The implementation supports this row's timing.
- Barn spec/implementation disagreements unrelated to the row's asserted second-step value must not be confused with Toast authority: `spec/builtins/server.md:97-124` says `load_server_options() -> none`, re-reads a map, validates by raising `E_INVARG`, and takes effect immediately. Current Barn code instead reloads properties on the `$server_options` object, silently ignores an invalid/nonpositive `fg_ticks`, and returns the count of recognized valid properties loaded (`builtins/system.go:609-627`, `builtins/limits.go:92-219`). The row does not assert the reload return value or invalid-value behavior.
- Toast authority path: local Toast `src/functions.cc:527-562` refreshes the cached server options and returns no value from the wizard-only builtin. Toast `src/execute.cc:3055-3061` initializes the tick counter, and `src/execute.cc:3065-3083` reads `server_int_option("fg_ticks", DEFAULT_FG_TICKS)` at interpreter start when `is_fg`. Fresh input/server tasks pass `1/*fg*/` through `src/execute.cc:3326-3336` and input tasks through `src/execute.cc:3340-3371`; resumed tasks pass `0/*bg*/` at `src/execute.cc:3253-3271`. This confirms the authority timing: the reload affects the next foreground task at interpreter start, not retroactively the current task.

## Still unresolved

- None for the requested authority handoff. No live Toast/Barn execution was performed; findings are current-source plus the durable row's stated oracle expectation.

## Next exact reads

1. Report the exact causal sequence and inclusive range asserted by the row.
2. State Barn implementation behavior separately from the Toast-backed expected behavior.
3. Mention the Barn spec/implementation mismatches only insofar as they prevent treating Barn as authority for `load_server_options()` generally.

Current blocker: none.
