# Runtime background tick option design

## Scope

This design covers only the Toast-authoritative `bg_ticks` sampling contract
for `task_scheduling_toast_oracle::audit_server_options_bg_ticks_runtime` and
the directly observable child-start timing established in
`docs/reports/server-options-background-tick-authority.md`.

The active focused regression reaches the intended red: both setup properties
are correct, but the exact fork/suspend predicate returns `0` because the
child records 29,999 from Banteng's fixed default.

## Existing owners

`MooRuntime.executeStored` already owns:

- the runnable task order;
- delayed-task wake times;
- the map from a child `VmState` to its program;
- conversion of an existing `VmState.ForkRequest` into scheduled child work.

`VmState.ForkRequest` already contains the complete immutable first-start
inputs: program, locals, programmer, verb location, and delay.

No new task abstraction or VM mutation API is required.

## Rejected alternatives

### Fork-time option lookup

Reading `bg_ticks` where Banteng currently constructs the child would pass the
active row but knowingly implement Barn's earlier sampling boundary. It would
fail Toast behavior when the parent changes the option after `fork` but before
yield, and when a delayed child waits while the option changes.

### VM tick setter or activation overload

A setter would create a callable mid-task budget-reset surface. Guarding it
would still require new cross-package start-state API. Adding `MooVm.execute`
overloads and an `ensureRoot` overload would spread this runtime option across
the VM owner and require execution-loop factoring solely to move one runtime
sampling decision.

That is broader than retaining the already-existing fork request until first
dispatch.

### New pending-task type

A record, sealed task-kind family, interface, adapter, or heterogeneous queue
would duplicate the scheduler identity already provided by the unstarted
child `VmState`. The current serial scheduler does not require that new
surface.

## Selected production change

Keep the existing runnable list, timed-task map, program map, and child-state
token. Add one local `LinkedHashMap<VmState, VmState.ForkRequest>` beside those
existing scheduler collections.

At a fork boundary:

1. Create the same unstarted child-state token already required by the current
   runnable and timed collections.
2. Store the existing request under that token.
3. Preserve the current zero-delay runnable insertion or delayed wake time.
4. Do not read `bg_ticks`.

When a runnable entry is removed for execution:

1. Remove its pending fork request from the local map.
2. If no request exists, execute the existing root or resumed task unchanged.
3. If a request exists, remove the never-executed token's program entry.
4. Inline-read `#0.server_options` and then `bg_ticks` through the existing
   `WorldTxn.readObjectProperty` owner.
5. Construct the real child `VmState` from the existing request. An integer
   option uses `max(100, value)`; missing or wrong-type options use the existing
   30,000 background default.
6. Install the request's existing program under the real state and execute it
   through the unchanged VM path.

The placeholder state is never executed. Activation occurs for one dequeued
child at a time, so a child that changes `bg_ticks` can affect a later child
that has not started. A delayed token is activated only after its existing
wake time makes it runnable.

All changes remain in `MooRuntime.java`. There is no helper method, interface,
adapter, cache, setter, VM overload, task-kind enum, new record, or option
owner.

## Regressions

### Configured background row

The existing active regression:

- sets `#6.bg_ticks = 23456` and ensures `#0.audit_bg_ticks = 0` in an asserted
  first eval;
- proves both stored property values;
- executes the exact conformance fork/suspend/predicate body in a second eval;
- changes from `{1, 0}` to `{1, 1}`.

### Mutation after fork, before yield

A second focused regression must:

- begin with `bg_ticks = 23456`;
- queue a zero-delay child that stores `ticks_left()`;
- change `bg_ticks` to `12345` in the parent before `suspend(0)`;
- prove that the child records the 12,345 window, not the creation-time 23,456
  window.

This distinguishes Toast first-start sampling from an incorrect fork-time
lookup.

### Delayed child

A third focused regression must:

- queue a delayed child while `bg_ticks = 23456`;
- yield the parent, change `bg_ticks` to `12345` before the child is due, and
  keep the root suspended until after the child runs;
- prove the child records the 12,345 window.

This establishes that timed promotion does not freeze the option before first
dispatch.

## Gates

After all three focused regressions turn green:

1. rerun each focused regression with `--rerun-tasks`;
2. run the full `MooRuntimeTest` class with `--rerun-tasks`;
3. rebuild `installDist`;
4. run the exact managed conformance row;
5. clean up only that run's Banteng Java process;
6. run the task-scheduling family to the next distinct failure;
7. clean up only that run's Banteng Java process;
8. run the full Java 25 `clean check installDist` gate;
9. reread the immutable plan and commit the kept regression and production
   slice.
