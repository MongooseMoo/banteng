# Server-options background tick authority

## Target

This record freezes the authority for the single conformance target
`task_scheduling_toast_oracle::audit_server_options_bg_ticks_runtime` before
changing Banteng.

The row sets `$server_options.bg_ticks` to `23456`. A later foreground task
forks a zero-delay child that stores `ticks_left()`, suspends at zero, and then
requires the stored value to be at most `23456` and greater than `23000`.

## Live Toast result

The exact row passed against the pinned WSL Toast oracle:

```text
1 passed, 11518 deselected in 3.90s
```

The pinned source identity is
`aecc51e9449c6e7c95272f0f044b5ba38948459e` at
`/root/src/toaststunt`.

## Toast sampling boundary

Fork creation does not read `bg_ticks`:

- `src/execute.cc:2084-2111` handles the fork opcode and calls the enqueue path.
- `src/tasks.cc:1257-1292` copies the environment and computes the start time.
- `src/tasks.cc:1207-1232` stores the activation, body, and start time.

A due fork moves to the background queue at `src/tasks.cc:1635-1645`, is
dequeued at `src/tasks.cc:1673-1681,1772-1783`, and enters
`do_task(..., 0/*bg*/, ...)` at `src/execute.cc:3374-3383`.
Only then does `run_interpreter()` read uncached
`server_int_option("bg_ticks", DEFAULT_BG_TICKS)` at
`src/execute.cc:3076-3083`.

Therefore Toast samples the current database option when the queued child
interpreter starts, not when the fork is created or enqueued.

## Option semantics

`src/server.cc:529-541` resolves `$server_options` and the named property.
`src/server.cc:1420-1427` accepts only an integer; missing or non-integer
values use the supplied default. `src/include/options.h:127-128` defines the
background default as `30000`.

`src/execute.cc:3054-3061` clamps every value below `100` to `100`. There is no
explicit upper clamp.

`load_server_options()` does not control this option. Its caches at
`src/functions.cc:527-550` and `src/include/server.h:197-244` exclude
`bg_ticks`, while the interpreter path uses uncached access. The row's reload
calls are unnecessary for Toast visibility.

## Zero-delay ordering and exact value

The row's literal asserted body is:

```moo
fork (0)
  #0.audit_bg_ticks = ticks_left();
endfork
suspend(0);
return #0.audit_bg_ticks <= 23456 && #0.audit_bg_ticks > 23000;
```

The fork is enqueued before the parent reaches `suspend(0)`. Equal-time waiting
tasks retain insertion order because `enqueue_waiting()` uses strict less-than
ordering (`src/tasks.cc:1182-1205`), and the background queue is FIFO
(`src/tasks.cc:505-527`). The child therefore runs and writes the audit
property before the suspended parent resumes.

The child starts with 23,456 ticks. Its object/property lvalue literals are
unticked. The counted builtin call decrements before dispatch, so
`ticks_left()` returns exactly `23455` (`src/code_gen.cc:803-813`,
`src/execute.cc:966-988,3668-3675`). The following property-write opcode uses
that already-produced value.

The parent resumes as a background task and samples `bg_ticks` again at
interpreter start (`src/execute.cc:3253-3272`). It observes the stored 23,455,
so both row comparisons are true and the result is integer `1`.

## Conformance assertion boundary

The row is at
`C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml:106-139`.

Its setup assigns `bg_ticks`, ensures `#0.audit_bg_ticks`, calls
`load_server_options()`, and has no expectation. The runner does not validate
that step's result. Banteng's current `E_VERBNF` from the unsupported reload
builtin is therefore not the asserted failure. Cleanup results are also
best-effort and ignored.

Only the second step is asserted, and it must successfully return exact
integer `1`.

## Barn comparison

Barn produces this row's valid-value result through different mechanics:

- `load_server_options()` caches a positive integer override in
  `builtins/limits.go:81-102,183-187,208-219`;
- `scheduler/task_factory.go:197-222,246-281` snapshots the cached background
  limit when constructing the forked task;
- `scheduler/task_runtime.go:94-115` starts the preconfigured child without
  resampling.

Barn therefore samples at fork construction and requires the reload call.
It also accepts positive values below 100 without Toast's clamp. Missing,
wrong-type, zero, or negative values fall back to 30,000. These disagreements
do not affect the active row because 23,456 is loaded before the fork is
created. Toast controls Banteng's behavior.

## Current Banteng boundary

`MooRuntime.executeStored` handles a fork outcome by immediately constructing
the child `VmState` with fixed `DEFAULT_BACKGROUND_TICKS = 30000`, then queues
the child. The existing default-background regression proves that the child
records `29999`. The active row consequently returns `0` because 29,999 is not
at most 23,456.

The required change belongs at that existing child-state construction site:

- resolve `#0.server_options` through `WorldTxn`;
- read `bg_ticks` when the child begins its first execution, using an integer
  override clamped to at least 100;
- otherwise retain the 30,000 default;
- do not add a server-options cache or make `load_server_options()` control the
  option.

A focused regression must prove the configured and audit properties first,
then run the exact fork/suspend/predicate body in a distinct eval. Its current
failure must be `{1, 0}`, isolating background budget selection from reload
builtin support.
