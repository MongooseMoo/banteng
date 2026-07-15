# Server-options background seconds authority

## Target

This record freezes the authority for the single conformance target
`task_scheduling_toast_oracle::audit_server_options_bg_seconds_runtime` before
changing Banteng.

The row sets `$server_options.bg_seconds` to `9`. A later foreground task
creates a zero-delay fork that stores `seconds_left()` in a property, yields,
and evaluates:

```moo
return #0.audit_bg_seconds <= 9 && #0.audit_bg_seconds > 5;
```

The required result is integer `1`.

## Live Toast result

The exact row passed against the pinned WSL Toast oracle:

```text
1 passed, 11518 deselected in 3.86s
```

The pinned source identity remains
`aecc51e9449c6e7c95272f0f044b5ba38948459e` at
`/root/src/toaststunt`.

## Toast fork activation semantics

Encountering a fork creates and queues its task at
`src/execute.cc:2084-2111` and `src/tasks.cc:1207-1232,1257-1292`. Those paths
do not read `bg_seconds`.

The scheduler later dequeues the fork at
`src/tasks.cc:1628-1645,1673-1681,1772-1783` and calls
`do_forked_task()` at `src/execute.cc:3374-3383`. That path enters
`do_task()` and `run_interpreter(..., is_fg=0)` through
`src/execute.cc:3227-3247`.

At interpreter start, `src/execute.cc:3076-3083` directly reads
`server_int_option("bg_seconds", DEFAULT_BG_SECONDS)`. The option is therefore
sampled when the child starts executing, not when the fork is encountered or
queued. A property change while a fork is waiting affects that fork's limit.

`load_server_options()` does not control this option. Its caches at
`src/functions.cc:524-550` and `src/include/server.h:197-244,266-275` exclude
`bg_seconds`, and interpreter startup uses uncached access. The row's reload
calls are unnecessary for Toast visibility.

## Type, default, and minimum

`src/server.cc:529-541` dynamically resolves `$server_options.bg_seconds`.
`src/server.cc:1420-1427` accepts only an integer; missing or non-integer
values use the supplied default. `src/include/options.h:127-131` defines the
background default as `3` seconds.

`src/execute.cc:3054-3061` clamps every integer value below `1` to `1`. There
is no explicit upper clamp.

## Timer authority

`setup_task_execution_limits()` creates a new timer for the child before its
bytecode begins at `src/execute.cc:3054-3083`. The timer is canceled when that
interpreter run ends at `src/execute.cc:3110-3120`.

The pinned executable uses the Linux `ITIMER_VIRTUAL` path and measures
process user CPU time, not wall time. Its compiled timers object and linked
executable import `setitimer()` and `getitimer()`, and the server reports that
task timeouts are measured in server CPU seconds.

`seconds_left()` returns integer `timer_wakeup_interval(task_alarm_id)` at
`src/execute.cc:3658-3666`. The active timer path calls `getitimer()` and
returns only `it_value.tv_sec` at `src/timers.cc:137-153,181-205,223-230`;
fractional microseconds are discarded. The result is a live whole-second
remainder, not the configured limit.

Calling the builtin costs a MOO tick, but there is no fixed one-second
decrement. Time advances independently with the active process-CPU timer.

## Row predicate

The child stores an integer satisfying `5 < value <= 9`. An immediate read of
a nine-second timer is ordinarily `8`; `9` is also consistent with timer and
kernel resolution. The exact stored integer is not exposed by the row.

The row intentionally accepts that variance. It requires a live background
task timer with the configured bound without asserting one exact rounded
reading.

## Conformance assertion boundary

The row is at
`C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml:160-190`.

Its setup assigns `bg_seconds`, adds the result property, calls
`load_server_options()`, and has no expectation. The runner executes but does
not validate that result. Cleanup is also best-effort and ignored. Only the
second eval is asserted, and it must successfully return exact integer `1`.

Banteng reaches the asserted step and returns integer `0`. Its background
activation currently reconstructs the fork child with the four-argument
`VmState` constructor at `MooRuntime.java:1124-1140`. That constructor gives
background tasks a zero seconds limit, so the row's `seconds_left()` stores
zero. The unsupported reload call inside ignored setup is not the target.

## Barn comparison

Barn agrees with the row only for the configured positive value, but its
implementation is not the Toast contract:

- `builtins/limits.go:15-55,90-219` caches options and accepts positive
  integers or floats;
- `scheduler/task_factory.go:199-269` samples the cached background limits
  while constructing the child, before it waits in the queue;
- `task/task.go:191-212,333-337` stores `SecondsLimit=9` and
  `SecondsUsed=0`;
- `builtins/system.go:161-180` returns
  `int64(SecondsLimit - SecondsUsed)`;
- no current path advances `SecondsUsed`, so the result remains constant.

Barn requires `load_server_options()`, accepts positive floats, and defaults
missing, wrong-type, zero, or negative values to `3.0`. Toast instead uses an
uncached integer-only option, samples at child interpreter start, clamps
integers below one to one, and returns a live whole-second timer. Toast
controls Banteng.

## Required Banteng boundary

The active row requires a newly activated background fork to:

- sample integer `#0.server_options.bg_seconds` with default 3 and minimum 1;
- sample only when the queued child becomes runnable and starts, not when the
  fork placeholder is created;
- establish task-owned live remaining-time state before child bytecode;
- expose that state through the already implemented integer
  `seconds_left()` builtin;
- keep the option uncached and independent of `load_server_options()`.

The smallest implementation boundary is the existing pending-fork activation
block in `MooRuntime`. It already samples `bg_ticks` at interpreter start and
replaces the queued placeholder. It must also sample `bg_seconds` there and
construct the activated child through the existing five-argument `VmState`
constructor.

Valid focused regressions must cover the exact configured row, a zero-delay
fork whose parent mutates `bg_seconds` before yielding, and a delayed fork
whose option changes before it starts. Each current failure must be the final
asserted integer `0`, not an ignored setup error.

This record does not authorize a helper, option cache, clock abstraction, VM
timer change, fork-placeholder limit, foreground change, suspension change,
or deadline enforcement.
