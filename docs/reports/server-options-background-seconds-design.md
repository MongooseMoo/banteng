# Runtime background seconds design

## Scope

This design covers only
`task_scheduling_toast_oracle::audit_server_options_bg_seconds_runtime`.

The committed authority is
`docs/reports/server-options-background-seconds-authority.md`. Toast requires
an uncached integer `bg_seconds` sample when a queued fork starts its
interpreter, with default 3 and minimum 1. Current Banteng activates every
background child with the four-argument `VmState` constructor, which has no
active seconds limit and therefore reports zero.

## Existing owner

Keep ownership in the existing `MooRuntime.executeStored` task loop.

The pending-fork activation block already performs the required lifecycle:

1. an inactive placeholder represents the queued or delayed fork;
2. the placeholder becomes runnable;
3. immediately before bytecode execution, the block removes the pending fork;
4. it samples the current integer `bg_ticks` option;
5. it replaces the placeholder with an activated `VmState`.

This is the exact interpreter-start boundary proven by Toast. No new owner,
helper, task-limit record, adapter, or option cache is needed.

## Background seconds sample

Add the Toast background default beside the existing runtime constants:

```java
private static final long DEFAULT_BACKGROUND_SECONDS = 3;
```

Inside the existing pending-fork activation block, read `bg_seconds` from the
same already resolved `#0.server_options` object used for `bg_ticks`:

- missing or non-integer: 3;
- integer below 1: 1;
- otherwise: the integer value.

The read remains inline and uncached. `load_server_options()` has no role.

The activated child is constructed through the existing five-argument
constructor:

```java
new VmState(
    pendingFork.locals(),
    pendingFork.programmer(),
    pendingFork.verbLocation(),
    backgroundTicks,
    backgroundSeconds)
```

`VmState`, `MooVm`, and `BuiltinCatalog` already own the live process-CPU timer
and integer `seconds_left()` behavior committed in row 43. They require no
row-44 change.

## Placeholder boundary

Leave fork placeholder creation on the four-argument `VmState` constructor.
The placeholder does not execute bytecode and must not capture an observable
limit.

Sampling only while replacing the placeholder preserves both Toast timing
cases:

- a zero-delay child observes a parent mutation performed after `fork` but
  before the parent yields;
- a delayed child observes a mutation performed while it waits for its wake
  time.

Sampling at fork creation would be observably wrong in both cases.

## Regressions

Add three focused `MooRuntimeTest` regressions.

### Configured background limit

Set integer `#6.bg_seconds = 9`, fork a zero-delay child that stores
`seconds_left()`, yield the parent, and assert the exact row range:

```moo
#0.audit_bg_seconds <= 9 && #0.audit_bg_seconds > 5
```

The current expected red is final result `{1, 0}` instead of `{1, 1}`.

### Zero-delay activation sample

Start with `bg_seconds = 9`, create a zero-delay fork, mutate the property to
`7` before yielding, and assert the child result is at most 7 and safely above
the tiny-task floor. This proves the limit was not captured at fork creation.

The current expected red is again the final integer predicate, not setup.

### Delayed activation sample

Start with `bg_seconds = 9`, create a delayed fork, yield, change the property
to `7` before the child wake time, wait for the child, and assert the same
configured range. This proves queued delay does not consume or freeze the
child's execution limit.

Use range predicates rather than exact immediate values because Toast and
Banteng expose a live whole-second process-CPU remainder.

## Explicit exclusions

This slice does not implement or change:

- a timer or clock abstraction;
- `VmState` time accounting;
- foreground limits;
- background tick semantics;
- elapsed-time quota enforcement;
- suspension cancellation or resume re-anchoring;
- checkpoint serialization;
- `load_server_options()` or an option cache;
- fork scheduling or placeholder ownership.

## Gates

After the three regressions prove red and the one-file production change turns
them green:

1. rerun each focused regression with `--rerun-tasks`;
2. run the full `MooRuntimeTest` class with `--rerun-tasks`;
3. reread the immutable plan after the substantial targeted pass;
4. rebuild `installDist`;
5. run the exact managed conformance row and clean only its Banteng process;
6. run the task-scheduling family to the next distinct failure and clean only
   that Banteng process;
7. run the full Java 25 `clean check installDist` gate;
8. reread the immutable plan and commit the kept source slice.
