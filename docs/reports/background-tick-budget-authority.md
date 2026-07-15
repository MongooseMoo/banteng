# Background fork tick-budget authority

## Scope and identities

This record covers exactly
`task_scheduling_toast_oracle::audit_default_bg_ticks`. It freezes the
compiled default tick budget of a newly started fork child and the existing
live `ticks_left()` observation inside that child. Runtime
`$server_options.bg_ticks` overrides, delayed option changes, suspended-task
resumption, seconds, exhaustion, timeout hooks, persisted tasks, and task IDs
remain outside this slice.

- Banteng committed base: `08e3d18fcad9df68b9c906e601e8f5820e56e422`
  (`feat: track foreground tick remainder`).
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

`../barn/spec/tasks.md:24-38` defines forked work as background work with a
compiled default of 30,000 ticks and 3 seconds. It names
`$server_options.bg_ticks` and `.bg_seconds` as overrides.

`../barn/spec/tasks.md:46-72` states that a fork waits until its scheduled
start time and then runs with background limits. `../barn/spec/tasks.md:80-100`
defines fork syntax, immediate task-ID allocation, captured variables, queued
execution, and parent continuation.

`../barn/spec/tasks.md:141-166` defines opcode charging, including one tick for
the builtin call that reaches `ticks_left()`, and repeats the 30,000 compiled
background limit.

The exact row therefore requires a distinct child task budget. Reusing the
parent's 60,000 foreground remainder or constructing every VM with one common
default disagrees with the specification.

## Current Barn implementation

- `../barn/bytecode/compiler.go:2532-2572` compiles a fork body and emits
  `OP_FORK` with its captured body range.
- `../barn/vm/vm.go:664-668` dispatches that opcode to `executeFork`.
  `../barn/vm/control.go:24-75` validates the delay, captures the fork body and
  runtime environment, skips the body in the parent, and yields `FlowFork`.
- `../barn/scheduler/task_runtime.go:410-422` drains the yield by immediately
  calling `CreateForkedTask`, binds the child ID, and resumes the parent.
- `../barn/builtins/limits.go:22-68` owns `defaultBgTicks = 30000` and returns
  the selected background limits from `GetTaskLimits(true)`.
- `../barn/scheduler/task_factory.go:199-252` selects those limits during
  `CreateForkedTask`, initializes the child `Task`, assigns the child VM's
  `TickLimit`, then assigns its delayed start time and queues it.
- `../barn/task/task.go:216-251` initializes both `Task.TicksLimit` and
  `TaskContext.TicksRemaining` from the supplied 30,000 budget.
- `../barn/scheduler/task_runtime.go:103-149` later attaches the child context
  to the already configured VM and executes it.

Barn consistently produces a 30,000 default child budget. Barn snapshots the
selected `bg_ticks` value when the parent yields the fork, before a delayed
child actually starts.

## Pinned Toast implementation

- `/root/src/toaststunt/src/include/options.h:99-131` defines
  `DEFAULT_BG_TICKS` as exactly 30,000 and explicitly classifies forked tasks
  as background work.
- `/root/src/toaststunt/src/execute.cc:2084-2113` handles `OP_FORK` and
  `OP_FORK_WITH_ID`, validates the delay, reads the fork vector, and calls
  `enqueue_forked_task2`.
- `/root/src/toaststunt/src/tasks.cc:1257-1292` assigns the task ID, captures
  the activation and runtime environment, computes the start time, and calls
  `enqueue_forked`.
- `/root/src/toaststunt/src/tasks.cc:1182-1232` stores a `TASK_FORKED` in the
  time-ordered waiting queue. `/root/src/toaststunt/src/tasks.cc:1628-1646`
  moves due work into its programmer's background queue.
- `/root/src/toaststunt/src/tasks.cc:1772-1784` dispatches due `TASK_FORKED`
  work through `do_forked_task`.
- `/root/src/toaststunt/src/execute.cc:3374-3384` restores the captured fork
  state and calls `do_task(..., 0)` with `is_fg` false.
- `/root/src/toaststunt/src/execute.cc:3048-3083` selects
  `server_int_option("bg_ticks", DEFAULT_BG_TICKS)` when the child interpreter
  starts, clamps values below 100, and stores the result in `ticks_remaining`.
- `/root/src/toaststunt/src/server.cc:1420-1428` accepts only an INT override;
  a missing or wrong-typed property selects 30,000. `bg_ticks` is uncached, so
  the row's `load_server_options()` call does not select this limit.
- `/root/src/toaststunt/src/execute.cc:972-987,3668-3676` decrements counted
  opcodes before dispatch and returns the live remainder from `bf_ticks_left`.

Unlike Barn, Toast selects the background limit when the queued child begins
interpreter execution. A delayed child can therefore observe an option value
changed after the fork was enqueued. Toast controls that disagreement. The
current default-only row removes `bg_ticks` and makes no intervening option
change, so both implementations select the same compiled 30,000 value.

## Durable row and managed oracle

`../moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml:52-82`
contains `audit_default_bg_ticks`. Its setup removes
`$server_options.bg_ticks`, creates an integer result property, and calls
`load_server_options()`. The evaluated task then forks a zero-delay child that
writes `ticks_left()` into that property, yields with `suspend(0)`, and
requires the captured value to be at most 30,000 and greater than 29,500.

The row was introduced by moo-conformance commit
`78a82923879931dcc90a88aca217e5048ab1e0e2` with the explicit subject
`Add Toast oracle task scheduling audit tests`.

Using the exact managed WSL command, pinned executable and source, bundled
`Test.db`, and stock manifests, the sole selected row passed:

```text
1 passed, 11518 deselected in 3.83s
```

No conformance-row correction is needed.

## Banteng discrepancy

At the committed base, `src/main/java/moo/runtime/MooRuntime.java:1091-1118`
constructs both the foreground root and every fork child through the same
three-argument `VmState` constructor.
`src/main/java/moo/vm/VmState.java:26-64` initializes that constructor path to
the 60,000 foreground default added by the preceding slice.

The family fail-fast run first passed `audit_default_fg_ticks`, then stopped on
`audit_default_bg_ticks`: the child wrote a value near 60,000, so the row's
`<= 30000` predicate returned integer zero instead of one.

## Agreements, disagreements, and unresolved questions

- Barn specification, current Barn, pinned Toast, and the managed Toast row
  agree that a fresh fork child receives a compiled default of 30,000 ticks.
- They agree that the child's first `ticks_left()` observation is slightly
  below 30,000 because counted opcodes run first.
- Barn snapshots a dynamic override when the fork is created; Toast reads it
  when the child starts. Toast is authoritative, but that timing is not
  exercised by this default-only row and must not be frozen from Barn into the
  Java design.
- Banteng disagrees by assigning its foreground default to every child.
- No uncertainty remains for the compiled default selected by this exact row.
  Design and production changes remain unauthorized until this evidence record
  is committed and the focused Banteng red is reproduced.
