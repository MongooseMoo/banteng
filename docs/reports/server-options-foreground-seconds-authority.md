# Server-options foreground seconds authority

## Target

This record freezes the authority for the single conformance target
`task_scheduling_toast_oracle::audit_server_options_fg_seconds_runtime` before
changing Banteng.

The row sets `$server_options.fg_seconds` to `12`. A later, distinct foreground
task evaluates:

```moo
return seconds_left() <= 12 && seconds_left() > 8;
```

The required result is integer `1`.

## Live Toast result

The exact row passed against the pinned WSL Toast oracle:

```text
1 passed, 11518 deselected in 3.69s
```

The pinned source identity remains
`aecc51e9449c6e7c95272f0f044b5ba38948459e` at
`/root/src/toaststunt`.

## Toast task-start semantics

New server and input tasks are foreground at `src/execute.cc:3289-3371`.
`do_task()` enters `run_interpreter()` through `src/execute.cc:3227-3247`.
At interpreter start, `src/execute.cc:3076-3083` directly reads
`server_int_option("fg_seconds", DEFAULT_FG_SECONDS)`.

The value is sampled once for the new task. A change after interpreter start
does not replace that task's existing timer; a later foreground task samples
the current database property.

`load_server_options()` does not control this option. Its caches at
`src/functions.cc:527-550` and `src/include/server.h:197-244` exclude
`fg_seconds`, and interpreter startup uses uncached access. The row's reload
calls are unnecessary for Toast visibility.

## Type, default, and minimum

`src/server.cc:529-541` dynamically resolves `$server_options.fg_seconds`.
`src/server.cc:1420-1427` accepts only an integer; missing or non-integer
values use the supplied default. `src/include/options.h:130-131` defines the
foreground default as `5` seconds.

`src/execute.cc:3054-3061` clamps every integer value below `1` to `1`. There
is no explicit upper clamp.

## Timer authority

`setup_task_execution_limits()` creates the task timer before bytecode begins
at `src/execute.cc:3054-3083`.

The pinned build has `IS_WSL` undefined at `build-release/config.h:65`, so its
Linux `ITIMER_VIRTUAL` path is active. `src/timers.cc:181-205` arms the timer;
the branch at `src/timers.cc:191-199` counts process virtual/user CPU time.
Wall-clock sleeping does not consume this timer. The fallback implementation
at `src/timers.cc:157-178,201-204` would use a wall-clock deadline, but that is
not the pinned oracle path.

`seconds_left()` returns integer `timer_wakeup_interval(task_alarm_id)` at
`src/execute.cc:3658-3666`. The active timer path calls `getitimer()` and
returns only `it_value.tv_sec` at `src/timers.cc:223-230`; fractional
microseconds are discarded. The result is a live whole-second remainder, not
the configured limit.

Calling the builtin costs a MOO tick because it is an `OP_BI_FUNC_CALL`, but
there is no fixed one-second decrement. Seconds change independently with the
active timer clock (`src/code_gen.cc:535-553,803-813`,
`src/include/opcode.h:53-67,86-91,128-130`, `src/execute.cc:972-988`).

## Row predicate

The two exact returned integers are timing-dependent. For this tiny task they
will ordinarily both be `11`; `12,12` and `12,11` are also consistent with the
timer and kernel resolution.

The row intentionally accepts that variance. The first reading is at most 12,
the second is non-increasing, and the tiny expression remains above 8 seconds.
The expected result is therefore integer `1`, without asserting one exact
rounded reading.

## Conformance assertion boundary

The row is at
`C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml:138-158`.

Its setup assigns `fg_seconds`, calls `load_server_options()`, and has no
expectation. The runner executes but does not validate that result. Cleanup is
also best-effort and ignored. Only the second distinct eval is asserted, and
it must successfully return exact integer `1`.

Banteng currently reaches `E_VERBNF` on that asserted step because
`BuiltinCatalog.invoke` has no `seconds_left` case and falls through its
default at `BuiltinCatalog.java:523`. The unsupported reload builtin fails only
inside the ignored setup and is not the target.

## Barn comparison

Barn's current valid-row result is not a live timer implementation:

- `scheduler/eval.go:55-68` samples its cached foreground limit at task
  construction;
- `task/task.go:191-212` stores `SecondsLimit=12` and `SecondsUsed=0`;
- `builtins/system.go:161-180` returns
  `int64(SecondsLimit - SecondsUsed)`;
- no current path advances `SecondsUsed`, so both calls return constant `12`.

Barn requires `load_server_options()`, accepts positive integers or floats,
has no positive minimum clamp, and defaults missing, wrong-type, zero, or
negative values to `5.0`. Toast instead uses an uncached integer-only option,
clamps integers below one to one, and returns a live whole-second timer.

Barn's specs also disagree internally: `spec/builtins/time.md:98-104` says the
result is `INT`, while `spec/builtins/tasks.md:250-256` says `FLOAT`. Current
Barn and Toast both return `INT`. Toast controls Banteng.

## Required Banteng boundary

The active row requires one fresh foreground task to:

- sample integer `#0.server_options.fg_seconds` with default 5 and minimum 1;
- establish task-owned live remaining-time state before bytecode execution;
- expose zero-argument `seconds_left()` as an integer through the existing VM
  builtin invocation path;
- return `E_ARGS` for arguments and classify the builtin as pure;
- keep the option uncached and independent of `load_server_options()`.

The time state belongs with the existing explicit `VmState` limits, and the
timer source must model the pinned process-CPU countdown rather than Barn's
constant limit. This record does not authorize a clock interface, option
cache, reload implementation, background-seconds work, or suspension changes.

A valid focused regression must establish and prove integer
`#6.fg_seconds = 12` in one eval, then execute the exact predicate in a second
eval. Its current failure must be the asserted `E_VERBNF` from `seconds_left`,
not an ignored setup error.
