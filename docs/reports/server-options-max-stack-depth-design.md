# Runtime maximum stack depth design

## Scope

This design covers only
`task_scheduling_toast_oracle::audit_server_options_max_stack_depth_runtime`.

The committed authority is
`docs/reports/server-options-max-stack-depth-authority.md`. The corrected
managed row sets integer `max_stack_depth` to `60`, enters exactly 58 recursive
verb activations through the stock Test.db eval path, and then receives
`E_MAXREC`. Current Banteng instead recurses until the managed transport times
out because frame admission is unbounded.

## Existing owners

Keep option sampling in the existing `MooRuntime.executeStored` task-start
boundaries and frame ownership in the existing `VmState.frames` deque.

`MooRuntime.executeStored` already has the two required task-start sites:

1. it samples foreground limits immediately before constructing the root
   `VmState`;
2. it samples background limits when a pending fork becomes runnable,
   immediately before replacing the inactive placeholder with its executing
   `VmState`.

`VmState` already owns every root, eval, and verb frame for one task. It also
survives suspension and resume as the same object. No option cache, reload
builtin, helper, limit record, stack wrapper, interface, sender, or adapter is
needed.

## Task-start sample

Add private default constants with value `50` beside the existing execution
limit constants in `MooRuntime` and `VmState`.

At both executing-task construction sites in `MooRuntime.executeStored`, read
`max_stack_depth` from the same currently resolved `#0.server_options` object
used by the tick and seconds limits:

- missing or non-integer: `50`;
- integer below `50`: `50`;
- otherwise: the integer value, without an explicit upper clamp.

The read remains inline and uncached. `load_server_options()` has no role.

Extend the existing five-argument `VmState` execution-limit constructor with a
sixth `long maxStackDepth` argument. Store it as final task-owned state. Keep
the five-argument constructor and all shorter constructors by delegating to
the six-argument constructor with default `50`; this preserves direct VM test
construction without creating a second owner.

Root tasks receive the foreground tick limit, foreground seconds limit, and
the sampled maximum depth. Activated forks receive the background tick limit,
background seconds limit, and their own sampled maximum depth.

Leave the inactive fork placeholder on its existing constructor. It executes
no bytecode and is replaced at activation, so its default depth is not
observable. A suspended task retains its original `VmState` and therefore does
not resample.

## Frame admission

Count the root frame and every eval and verb frame in the existing
`VmState.frames` deque. Before either existing additional-frame method mutates
the deque, compare its current size with the task-owned maximum:

```java
if (frames.size() >= maxStackDepth) {
  return false;
}
```

Change the existing `pushEvalFrame` and `pushVerbFrame` return type from
`void` to `boolean`. A successful atomic push returns `true`; a rejected push
returns `false` with the frame stack unchanged. This changes existing methods
rather than adding a new admission helper or exception type.

The root frame is installed by `ensureRoot()` before execution. At configured
depth 60, the corrected managed path therefore contains:

- one public eval verb root frame;
- one dynamic eval frame;
- 58 entered recursive verb frames.

The next recursive call sees a current size of 60 and is rejected before the
counter can increment again.

## Existing error path

Check the boolean result at every current additional-frame caller in
`MooVm`:

- `pass()` verb dispatch;
- ordinary object and WAIF verb dispatch;
- builtin dynamic eval;
- the `move()` destination `accept` hook;
- the `recycle()` hook.

On `false`, call the existing
`raiseError(state, ErrorValue.E_MAXREC, world)` and return from the current
instruction handler. Do not mutate `pendingError` inside `VmState`.

All five callers have already advanced their current instruction before frame
admission. Preserve that ordering. The existing error router handles try/except,
finally, child unwinding, and uncaught errors from the still-current caller.
Move and recycle side effects remain deferred because rejection occurs before
a hook child frame with completion metadata exists.

## Focused regression

Add one bounded `MooRuntimeTest` regression using a fresh Test.db world and a
Wizard connection.

The setup task sets integer `#6.max_stack_depth` to `80`, creates a dedicated
object with a recursive debug verb, and stores the object in a test-owned
property. The verb terminates at argument zero and otherwise returns one plus
the result of calling itself with the argument decremented.

Then assert in separate foreground tasks:

1. depth `60` completes successfully, proving the above-default configured
   value is applied rather than the default 50;
2. depth `90` is caught as `E_MAXREC`, proving admission stops above the
   configured value.

The recursion is deliberately bounded. Before production changes, current
Banteng must complete depth `90`, making only the final `E_MAXREC` predicate
red. The regression must not hang, fail setup, or depend on the five-second
transport timeout.

The corrected WSL conformance row remains the exact frame-accounting gate: it
must return `E_MAXREC` and then counter `58` at configured depth `60`.

## Explicit exclusions

This slice does not implement or change:

- `load_server_options()` or any option cache;
- tick or seconds accounting;
- suspension, resume, or checkpoint formats;
- task scheduling or fork placeholder ownership;
- Java recursion;
- a separate stack or task-limit object;
- error routing or handler semantics;
- verb lookup, permissions, move, or recycle semantics;
- any maximum-stack option other than `max_stack_depth`.

## Gates

After the bounded regression proves red and the production change turns it
green:

1. rerun the focused regression with `--rerun-tasks`;
2. run the full owning `MooRuntimeTest` and `MooVmTest` classes with
   `--rerun-tasks`;
3. reread the immutable plan after the substantial targeted pass;
4. rebuild `installDist`;
5. copy the distribution to a WSL-native directory;
6. run the exact corrected managed conformance row in WSL and clean only its
   Banteng process;
7. run the task-scheduling family in WSL to the next distinct failure and
   clean only that Banteng process;
8. run the full Java 25 `clean check installDist` gate;
9. reread the immutable plan and commit the kept row-45 source slice.
