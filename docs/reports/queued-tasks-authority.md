# Queued-tasks first-slice authority

## Scope and identities

This record covers exactly the eight managed `queued_tasks::` rows selected at
the start of the slice. It freezes the smallest observable first slice for
`queued_tasks` and the named `function_info` descriptions required to keep the
generated rows active. It does not authorize a scheduler interface, task-view
adapter, sender, registry facade, invented task record, or guessed enumeration
of Banteng's ephemeral runtime collections.

- Banteng committed base: `2d3b2d27ea4c70d7bc31a90aa45718910186fa9d`
  (`feat: complete managed sqlite
  semantics`).
- Toast identity and procedure:
  `docs/reports/toast-oracle-identity-2026-07-14.md`.
- Toast source: `/root/src/toaststunt` at
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Barn implementation reference at audit time:
  `864de996a111674adfe15c330f8e85813f4641f0`.
- Durable conformance authority: `../moo-conformance-tests` at
  `4de57abc69614ccac71ae8fb0848a0771fde4ea2`.

## Normative Barn specification

`../barn/spec/tasks.md:293-316` specifies `queued_tasks` with zero through two
INT arguments. With zero or one argument it returns visible task rows; a true
first argument requests appended variables. With two arguments, a true second
argument returns the visible task count. Wizards may see all tasks and other
programmers may see only their own. Ordinary rows have the shape
`{id, start, 0, 30000, programmer, verb-location, verb-name, line, this,
bytes [, variables]}`. Reading tasks use start `-1`; external queues may use a
status string.

`../barn/spec/tasks.md:483-503` explicitly records that current Barn does not
yet implement all of that Toast contract. No Barn specification section
fully specifies `function_info`; its current implementation and Toast source
below agree on the active named-description shape.

## Current Barn implementation

- `../barn/builtins/registry.go:331-337` registers `queued_tasks`.
- `../barn/builtins/function_signatures_generated.go:146` records its exact
  signature as minimum 0, maximum 2, argument types `{0, 0}`.
- `../barn/builtins/tasks.go:21-71` validates the arguments, reads Barn's task
  manager, filters and sorts the result, and implements count mode. It
  incorrectly treats the first argument as a player filter, does not implement
  Toast caller visibility or appended variables, and globally start-sorts the
  available Barn task states.
- `../barn/task/manager.go:79-100` exposes only queued and suspended Barn tasks,
  with one scaffold exclusion. `../barn/task/task.go:482-536` constructs a
  fixed ten-field row and hardcodes bytes to zero.
- `../barn/builtins/registry.go:287` registers `function_info`.
  `../barn/builtins/signatures.go:42-60,128-159` constructs
  `{name, min, max, argument-type-list}`, performs named lookup, and reports
  `E_ARGS`, `E_TYPE`, or `E_INVARG` for invalid calls.

Barn agrees with Toast on the active `queued_tasks` signature and named
`function_info` row. Its broader queue enumeration is not used as authority.

## Pinned Toast implementation

- `src/tasks.cc:3037-3042` registers `queued_tasks` with zero through two
  `TYPE_INT` arguments.
- `src/include/structures.h:98-121` assigns public database type code 0 to INT.
- `src/tasks.cc:2496-2507` interprets the first and second flags.
- `src/tasks.cc:2509-2585` applies programmer visibility and enumerates idle and
  active reading VMs, active background tasks, time-ordered waiting tasks, and
  external queues.
- `src/tasks.cc:1183-1205` preserves time order and stable equal-time insertion
  for waiting tasks. `src/tasks.cc:1303-1318,1629-1646` moves timed or
  indefinite suspended tasks into and out of that lifecycle.
- `src/functions.cc:463-486` constructs the four-element `function_info`
  description and masks public argument type codes.
- `src/functions.cc:490-509,568` implements named lookup, list-all behavior,
  and registration as zero through one STR argument.

Toast controls every broader disagreement with Barn. Those broader contracts
remain deliberately unrepresented until a durable row observes them.

## Durable rows and managed oracle

The exact `queued_tasks::` selector contains eight rows from two same-basename
files:

- `../moo-conformance-tests/src/moo_conformance/_tests/capabilities/queued_tasks.yaml:14-48`
  has four provider rows. They assert LIST type, nonnegative length, and only
  conditionally inspect the first row's LIST type and minimum length. With an
  empty queue, both structural assertions pass through their explicit empty
  branches.
- `../moo-conformance-tests/src/moo_conformance/_tests/generated_builtins/queued_tasks.yaml:22-45`
  asserts `function_info("queued_tasks") == {"queued_tasks", 0, 2, {0,
  0}}`, `E_ARGS` for three arguments, and `E_TYPE` for a non-INT first or
  second argument. The suite requires both `function_info` and `queued_tasks`.

The rows do not create a queued task. They do not assert current-task
inclusion, task IDs, row values, visibility, variable inclusion, ordering,
timing, or any distinction among forked, reading, suspended, background,
waiting, and external tasks. No profile manifest or row correction is needed.

The exact managed Toast procedure from the identity record, with only selector
`-k "queued_tasks::"`, collected 11,504 tests and passed all eight selected
rows:

```text
8 passed, 11496 deselected in 3.63s
```

The fail-fast Banteng run at committed base `2d3b2d2` proved the first row red:
`queued_tasks()` raised `E_VERBNF`.

## Frozen representation for this slice

Banteng's current `MooRuntime.executeStored` has only per-execution local
`runnable`, timed, and host-result collections. `VmState` has no task ID,
durable registry, complete Toast row metadata, or public enumeration seam. At
the instant the active rows query it, there is no other durable queued task.
An empty visible queue is therefore the only honest current result.

- Add `queued_tasks` directly to the existing `BuiltinCatalog` switch. Validate
  zero through two arguments and require every supplied argument to be an INT.
  Return an empty LIST for zero arguments and for one argument. For two
  arguments, return integer zero when the second flag is true and otherwise an
  empty LIST. This preserves the proven empty-queue list/count shapes without
  inventing task records or exposing runtime internals.
- Add `function_info` directly to the same switch for the named descriptions
  required by this slice. `function_info("queued_tasks")` returns exactly
  `{"queued_tasks", 0, 2, {0, 0}}`; describing `function_info` itself returns
  `{"function_info", 0, 1, {2}}` so the harness can establish its required
  builtin. With zero arguments, return a list containing those two descriptions
  in catalog order; later signature slices expand that list as their own
  authority is frozen. More than one argument raises `E_ARGS`, a non-STR named
  argument raises `E_TYPE`, and any other name raises `E_INVARG`. Other
  signatures remain outside this one target family and must be added from their
  own authority, not guessed here.
- Classify `function_info` as pure. Classify `queued_tasks` as an external read:
  its semantic owner is mutable task-runtime state even though this first
  implementation can truthfully return the constant empty view.
- Add focused VM/runtime tests for the exact signature, list and count shapes,
  arity, both type errors, and the managed stored-eval envelope. Do not add a
  manifest class, registry interface, scheduler API, task record, helper,
  adapter, or sender.

The kept slice must prove the focused regressions green, pass `gradlew check`,
rebuild `installDist`, pass all eight managed `queued_tasks::` rows without
skips, receive a read-only review, be reread against the immutable plan, and be
committed before another family starts.
