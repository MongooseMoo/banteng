# Foreground tick-budget authority

## Scope and identities

This record covers exactly
`task_scheduling_toast_oracle::audit_default_fg_ticks`. It freezes the
observable default foreground tick budget and the zero-argument
`ticks_left()` result needed by that row. Background budgets, runtime
`$server_options` overrides, suspension resets, timeout handling,
`seconds_left()`, `yin()`, task IDs, and queued-task reporting remain outside
this slice.

- Banteng committed base: `bfa8c106fa031d9dba79347dd000bf6254727450`
  (`feat: clear intrinsic fertile flag`).
- Barn implementation reference:
  `c87229f66a4cf6e45234275f1eed53db8722aa77`.
- Durable conformance authority:
  `../moo-conformance-tests` at
  `e790d04dd0c32899ad9dcc8d60e2e4285de9a0e0`.
- Toast identity and procedure:
  `docs/reports/toast-oracle-identity-2026-07-14.md`.
- Pinned Toast source: `/root/src/toaststunt` at
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`.

## Normative Barn specification

`../barn/spec/tasks.md:7-17` names the pinned Toast behavior as the task
authority and points limit defaults to `src/include/options.h` and task-start
selection to `src/execute.cc`.

`../barn/spec/tasks.md:24-38` specifies that a task started by player input or
server initiative and not yet suspended is foreground work. Its compiled
default is 60,000 ticks, and `$server_options.fg_ticks` overrides that default.

`../barn/spec/tasks.md:141-166` specifies opcode-level charging. In particular,
the builtin-call opcode costs one tick, and `ticks_left()` returns the current
task's remaining ticks. `../barn/spec/builtins/tasks.md:240-247` separately
freezes the signature as `ticks_left() -> INT` with no arguments.

The specification therefore requires a live task-owned remainder. A constant
presence stub would not satisfy the named contract even though this first row
accepts a range.

## Current Barn implementation

- `../barn/builtins/limits.go:22-58` owns `defaultFgTicks = 60000` and the
  foreground limit cache. `GetTaskLimits(false)` returns the selected
  foreground budget.
- `../barn/builtins/limits.go:78-125` resets the cache to the compiled defaults
  and then applies valid server-option values in
  `LoadServerOptionsFromStore`.
- `../barn/scheduler/task_factory.go:19-23,51-62` resolves foreground limits
  and passes them to `task.NewTaskFull` when it creates a player-input task.
- `../barn/task/task.go:216-251` stores the selected budget as
  `Task.TicksLimit` and initializes `TaskContext.TicksRemaining` from it.
- `../barn/scheduler/task_runtime.go:145-149` copies `Task.TicksLimit` into the
  executing VM.
- `../barn/vm/vm.go:157-166` owns the live remainder:
  `syncContextTicks()` stores `TickLimit - Ticks` in the task context.
- `../barn/bytecode/opcodes.go:246` classifies the builtin-call opcode as a
  counted opcode. `../barn/vm/op_misc.go:10-32` dispatches that opcode through
  the existing builtin registry.
- `../barn/builtins/registry.go:298` registers `ticks_left`.
  `../barn/builtins/system.go:136-155` validates zero arguments and returns the
  synchronized task-context remainder.

Barn agrees with its specification on the scheduled foreground path. Its
fallback behavior when no live budget exists is not exercised by this managed
foreground row and is not authority for Banteng.

## Pinned Toast implementation

- `/root/src/toaststunt/src/functions.cc:34-88` invokes `register_execute()`
  during builtin startup.
- `/root/src/toaststunt/src/include/options.h:100-131` defines task limits and
  sets `DEFAULT_FG_TICKS` to 60,000. It states that
  `$server_options.fg_ticks` overrides the default.
- `/root/src/toaststunt/src/tasks.cc:811-871` admits parsed input through the
  foreground task path. `/root/src/toaststunt/src/execute.cc:3225-3248,3339-3372`
  forwards `is_fg = 1` into the interpreter.
- `/root/src/toaststunt/src/execute.cc:3064-3083` starts execution by calling
  `setup_task_execution_limits` with either the foreground server option or
  `DEFAULT_FG_TICKS` when `is_fg` is true.
- `/root/src/toaststunt/src/execute.cc:3048-3062` stores the selected budget in
  `ticks_remaining` and clamps configured values below 100 to 100.
- `/root/src/toaststunt/src/server.cc:529-542,1420-1428` resolves
  `$server_options.fg_ticks` for each foreground run and accepts only an INT;
  absence or a wrong type selects the supplied 60,000 default. `fg_ticks` is
  not one of the cached options refreshed by `load_server_options()`.
- `/root/src/toaststunt/src/include/opcode.h:128-130` defines ordinary opcode
  tick charging. `/root/src/toaststunt/src/execute.cc:972-982` decrements
  `ticks_remaining` before dispatching a counted opcode and aborts when the
  remainder reaches zero.
- `/root/src/toaststunt/src/execute.cc:3668-3676` implements
  `bf_ticks_left` by returning the live `ticks_remaining` value as an INT.
- `/root/src/toaststunt/src/execute.cc:3778-3785` registers `ticks_left` with
  minimum and maximum arity zero.
- `/root/src/toaststunt/src/functions.cc:527-562` implements the wizard-only
  `load_server_options()` call present in the row setup. That call does not
  affect uncached `fg_ticks`; the following foreground task reads the absent
  property and selects the compiled fallback at task start.

Toast agrees with Barn on every behavior observable by this row. Because the
call opcode is charged before `bf_ticks_left`, a first direct call observes a
value below 60,000; the exact row deliberately permits the preceding work by
requiring a value greater than 59,000.

## Durable row and managed oracle

`../moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml:35-52`
contains `audit_default_fg_ticks`. Its first managed command removes
`$server_options.fg_ticks` if present and calls `load_server_options()`. Its
next foreground command requires both calls to satisfy
`ticks_left() <= 60000 && ticks_left() > 59000`.

The row was introduced by moo-conformance commit
`78a82923879931dcc90a88aca217e5048ab1e0e2` with the explicit subject
`Add Toast oracle task scheduling audit tests`.

Using the exact managed WSL command, source, executable, bundled `Test.db`, and
stock manifests from the Toast identity record, the sole selected row passed:

```text
1 passed, 11518 deselected in 3.57s
```

No conformance-row correction is needed. The row is valid against the current
pinned Toast authority.

## Banteng discrepancy

At the committed base, `src/main/java/moo/vm/VmState.java` contains no tick
limit or remaining-tick field, and `src/main/java/moo/vm/MooVm.java:36-50`
executes instructions without charging ticks.
`src/main/java/moo/builtin/BuiltinCatalog.java:43-519` has no `ticks_left`
dispatch case, and its default reports `E_VERBNF`.

The full managed stock-profile fail-fast run therefore stopped on this row
after 63 passes. Its second step expected integer `1` but received
`E_VERBNF` from `ticks_left()`.

## Agreements, disagreements, and unresolved questions

- Barn specification, current Barn scheduled execution, pinned Toast source,
  and the managed Toast row agree on a 60,000 compiled foreground budget.
- They agree that `ticks_left()` takes no arguments and returns the live INT
  remainder after opcode charging.
- Banteng disagrees by having neither a tick budget in explicit VM state nor
  the builtin.
- Barn's no-budget fallback differs from its own prose but does not affect
  this scheduled foreground slice and will not be copied.
- No MOO-observable disagreement or unresolved question remains for the exact
  selected row. Design and implementation are intentionally not authorized by
  this record until the evidence commit is complete and the focused Banteng
  red is reproduced.
