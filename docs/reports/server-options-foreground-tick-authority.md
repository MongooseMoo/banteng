# Server-options foreground tick authority

## Target

This record freezes the authority for the single conformance target
`task_scheduling_toast_oracle::audit_server_options_fg_ticks_runtime` before
changing Banteng.

The row sets `$server_options.fg_ticks` to `12345` in one task. A later,
distinct foreground task evaluates:

```moo
return ticks_left() <= 12345 && ticks_left() > 12000;
```

The required result is integer `1`.

## Live Toast result

The exact row passed against the pinned WSL Toast oracle:

```text
1 passed, 11518 deselected in 3.67s
```

The pinned source identity is
`aecc51e9449c6e7c95272f0f044b5ba38948459e` at
`/root/src/toaststunt`.

## Toast source authority

Toast treats new server-program and player-input tasks as foreground tasks:

- `src/execute.cc:3336`
- `src/execute.cc:3371`
- `src/execute.cc:3227-3247`

At the start of each foreground interpreter run,
`src/execute.cc:3076-3083` reads
`server_int_option("fg_ticks", DEFAULT_FG_TICKS)`. The lookup is uncached.
`src/execute.cc:3054-3061` installs that value as the task counter and clamps
values below `100` to `100`.

The option lookup resolves `$server_options` and its named property from the
current database at `src/server.cc:529-541`. `src/server.cc:1420-1427` accepts
only an integer value; a missing or non-integer value uses the supplied
default. `src/include/options.h:127-131` defines the foreground default as
`60000`. This path has no upper clamp.

The sampling boundary is interpreter start. Changing `fg_ticks` does not
alter an already-running task. A later foreground task samples the new value.

## `load_server_options()` is not the mechanism

Toast's `load_server_options()` refreshes the caches shown at
`src/functions.cc:527-550`. The complete cached-misc list at
`src/include/server.h:197-244` does not include `fg_ticks`. Cached integer
access is separately exposed by `server_int_option_cached()` at
`src/include/server.h:266-275`; foreground task setup does not use it.

Therefore the row's `load_server_options()` calls neither enable nor refresh
the foreground tick option. The next task observes the assignment because it
reads the database directly.

## Exact predicate accounting

With `fg_ticks = 12345`, the second task starts with 12,345 ticks.

- The first `ticks_left()` call is counted and returns `12344`.
- `<=` is counted, leaving `12343`.
- `&&` is counted, leaving `12342`.
- The second `ticks_left()` call is counted and returns `12341`.
- `>` is counted, leaving `12340`.
- Return is not counted.

The relevant compiler, opcode, and interpreter locations are
`src/code_gen.cc:535-540`, `630-641`, `668-714`, `809-813`;
`src/include/opcode.h:53-67`, `86-91`; and
`src/execute.cc:972-988`, `1312-1379`, `1535-1547`, `3668-3675`.

Both comparisons are deterministically true, so the row returns `1`.

## Conformance assertion boundary

The row is at
`C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml:84-104`.

Its first step adds or updates `fg_ticks`, calls `load_server_options()`, and
has no `expect`. The runner executes such a step but does not validate its
result (`runner.py:420-450`). Banteng currently reaches `E_VERBNF` at its
unimplemented `load_server_options`, but that ignored error is not the row's
assertion.

The second step is a separate `transport.execute` and fresh `;` command. Its
result is asserted as exactly `1`. On Banteng, the command dispatch reaches
`MooRuntime.executeStored`, which creates a fresh root `VmState` for each eval.
That root currently always receives the fixed 60,000 foreground default, so
the predicate returns `0`.

## Barn comparison

Barn produces the same valid-value observable through a different mechanism.
Its `load_server_options()` copies positive integer `fg_ticks` into a global
cache (`builtins/system.go:605`, `builtins/limits.go:92`, `177`, `208`). A new
foreground task snapshots the cache at construction (`scheduler/eval.go:55`,
`scheduler/task_factory.go:48`).

Toast instead samples the uncached database option at interpreter start. The
row does not expose that timing difference because its second task is both
created and started after the assignment. Toast controls Banteng's behavior
for this row.

## Required Banteng boundary

The change belongs at fresh foreground root-state construction in
`MooRuntime.executeStored`:

- resolve `#0.server_options` using the existing world property owner;
- if it is an object whose `fg_ticks` property is an integer, use that value;
- otherwise retain the 60,000 default;
- clamp values below 100 to 100;
- sample once for the new task, without adding a server-options cache or making
  `load_server_options()` control this option.

A valid focused regression must establish `fg_ticks = 12345`, verify the
property value, then execute the exact predicate in a second eval. The current
failure must be `{1, 0}`, after successful setup, so it proves the root budget
boundary rather than the missing `load_server_options()` builtin.
