# Runtime foreground tick option design

## Scope

This design covers only
`task_scheduling_toast_oracle::audit_server_options_fg_ticks_runtime`.

The committed authority is
`docs/reports/server-options-foreground-tick-authority.md`. A focused runtime
regression now reaches the intended red: its setup succeeds, the fixture's
server-options object stores integer `12345`, and a second eval returns the
predicate result `0` instead of `1`.

## Existing owner

`MooRuntime.executeStored` already owns fresh stored-verb task construction.
It creates one root `VmState` for each distinct eval. `WorldTxn` already owns
inherited object-property reads, and `VmState` already has an explicit
remaining-ticks constructor used for forked task budgets.

No new owner or abstraction is required.

## Production change

Immediately before constructing the root state in `executeStored`:

1. Read object `#0`'s `server_options` property through
   `WorldTxn.readObjectProperty`.
2. If that value is an object, read its `fg_ticks` property through the same
   existing operation.
3. If `fg_ticks` is an integer, construct the root `VmState` with the existing
   explicit-budget constructor and `max(100, fg_ticks)`.
4. If either property is absent, `server_options` is not an object, or
   `fg_ticks` is not an integer, retain the existing default `VmState`
   constructor and its 60,000 foreground budget.

The lookup is inline at the one task-construction boundary. It must not add a
helper, interface, adapter, cache, mutable runtime option, or setter. It must
not implement or depend on `load_server_options()`.

## Timing

The value is sampled once while creating the fresh root task. It cannot alter
the task that performed the assignment. A later eval creates a later root and
observes the current database property.

This is sufficient for the active row. The row does not distinguish Toast's
interpreter-start sampling from Banteng's synchronous root-construction
boundary, because the asserted task is created and immediately run after the
assignment task finishes.

## Regression

The focused `MooRuntimeTest` uses two distinct `executeLine` calls:

- the first adds or assigns `#6.fg_ticks = 12345`, returns `1`, and is asserted;
- the stored world property is asserted as integer `12345`;
- the second evaluates the row's exact predicate and must return `1`.

Before the production change the final output is `{1, 0}`. After the change it
must be `{1, 1}`.

## Gates

After the focused test turns green:

1. rerun the focused regression with `--rerun-tasks`;
2. run the full `MooRuntimeTest` class with `--rerun-tasks`;
3. rebuild `installDist`;
4. run the exact managed conformance row;
5. clean up only the Banteng Java process created by that managed run;
6. run the task-scheduling family to the next distinct failure;
7. clean up only that managed Banteng process;
8. run the full Java 25 `clean check installDist` gate;
9. reread the immutable plan, then commit the kept test and production slice.
