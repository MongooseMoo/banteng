# Runtime foreground seconds design

## Scope

This design covers only
`task_scheduling_toast_oracle::audit_server_options_fg_seconds_runtime` and
the zero-argument `seconds_left()` surface required by that row.

The committed authority is
`docs/reports/server-options-foreground-seconds-authority.md`. The focused
runtime regression now reaches the intended red: its setup and stored integer
option pass, while the exact second eval returns `E_VERBNF` for
`seconds_left()`.

## Exact timer choice

The pinned Toast executable uses `ITIMER_VIRTUAL` process CPU time. Generated
`build-release/config.h` leaves `IS_WSL` undefined, the compiled timer object
calls `setitimer/getitimer(ITIMER_VIRTUAL)`, and managed server logs identify
the timeout clock as server CPU seconds.

Use `ProcessHandle.current().info().totalCpuDuration()` as Banteng's current
standard-Java process CPU duration. Do not substitute `System.nanoTime()` or
wall time. If the current JVM cannot report its own process CPU duration,
fail explicitly rather than silently changing MOO timer semantics.

This process-wide clock matches the pinned serial Toast execution model for
the active slice. Parallel task-local accounting remains a later scheduler
design obligation; this row does not authorize a clock interface or executor
change.

## `VmState` representation

Keep time limits beside the existing explicit tick limit in `VmState`:

- `secondsLimit` is the task's configured whole-second limit;
- `executionStartedCpuNanos` is initialized only when `ensureRoot()` installs
  the first frame;
- `remainingSeconds()` reads current process CPU duration, subtracts the
  nonnegative elapsed duration from the saturated nanosecond limit, clamps at
  zero, and returns whole seconds with the fractional remainder discarded.

Use `TimeUnit.SECONDS.toNanos(secondsLimit)` for saturating conversion. The
current-process CPU duration is monotonic for the life of the process, so
nonnegative subtraction does not require an absolute deadline or unchecked
addition.

The canonical explicit constructor becomes:

```java
VmState(
    Map<String, MooValue> locals,
    long programmer,
    ObjectValue verbLocation,
    long remainingTicks,
    long secondsLimit)
```

The ordinary root constructors retain Toast foreground defaults of 60,000
ticks and 5 seconds. The existing four-argument explicit-ticks constructor
delegates with no active seconds limit. Its current production callers are
background placeholder/child states; silently assigning them the foreground
five-second limit would expose known-wrong background behavior before the
separate `bg_seconds` authority slice.

An inactive seconds limit returns zero. The next background-seconds slice must
supply its separately proven limit at the existing child activation boundary.

An absolute process CPU anchor is runtime state, not a durable cross-process
timestamp. Current Banteng does not yet serialize `VmState`. Future suspended
task/checkpoint work must persist a duration/limit and re-anchor on interpreter
resume; it must not serialize this process-relative sample as a durable
deadline.

## Foreground root construction

`MooRuntime.executeStored` already samples uncached `fg_ticks` immediately
before constructing each fresh foreground root. Extend that same inline
boundary to read integer `fg_seconds` from the existing `#0.server_options`
object:

- missing or non-integer: 5;
- integer below 1: 1;
- otherwise: the integer value.

Construct the root once with explicit foreground ticks and seconds. Private
runtime constants may state the two Toast defaults because the combined
constructor requires both primitives. Do not add an option helper, record,
cache, adapter, or `load_server_options()` dependency.

## Builtin path

`MooVm` already passes live `remainingTicks()` into `BuiltinCatalog.invoke` at
the builtin dispatch point. Pass live `remainingSeconds()` immediately beside
it.

`BuiltinCatalog` must:

- accept the additional primitive invocation value;
- implement zero-argument `seconds_left()` returning `IntegerValue`;
- return `E_ARGS` when arguments are present;
- propagate the value through existing `call_function` recursion;
- classify `seconds_left` as `PURE` beside `ticks_left`.

There is no context object, supplier, clock interface, helper, or hidden world
read in the builtin owner.

## Regressions

The runtime regression uses two distinct evals:

- asserted setup adds or assigns `#6.fg_seconds = 12` and proves the stored
  integer;
- the second eval runs the exact row predicate and expects integer `1`.

Add one VM-level contract for the new builtin path:

- direct and `call_function("seconds_left")` results are integers in the
  default range and non-increasing;
- an argument produces `E_ARGS`;
- effect classification is `PURE`.

Do not freeze one exact immediate result because the CPU timer's whole-second
reading can validly be 5 or 4 near activation.

## Explicit exclusions

This slice does not implement:

- elapsed-time quota enforcement;
- background `bg_seconds` sampling;
- suspension timer cancellation or resume re-anchoring;
- checkpoint serialization;
- `load_server_options()`;
- a clock abstraction, cache, helper, adapter, or task-limit record.

Those are separate observable surfaces with separate authority gates.

## Gates

After the focused regressions turn green:

1. rerun the runtime row regression and VM builtin regression separately with
   `--rerun-tasks`;
2. run the full `MooRuntimeTest` and `MooVmTest` classes with `--rerun-tasks`;
3. reread the immutable plan after the substantial targeted pass;
4. rebuild `installDist`;
5. run the exact managed conformance row and clean only its Banteng process;
6. run the task-scheduling family to the next distinct failure and clean only
   that Banteng process;
7. run the full Java 25 `clean check installDist` gate;
8. reread the immutable plan and commit the kept source slice.
