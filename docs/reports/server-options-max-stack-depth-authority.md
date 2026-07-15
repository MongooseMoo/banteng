# Server-options maximum stack depth authority

## Target

This record freezes the authority for
`task_scheduling_toast_oracle::audit_server_options_max_stack_depth_runtime`
before changing Banteng.

The durable row is corrected in `moo-conformance-tests` commit `fcc853a`. It
sets `$server_options.max_stack_depth` to `60`, creates a recursive debug verb
that increments a persisted counter on every entered activation, expects the
recursive call to raise `E_MAXREC`, and then expects the counter to equal
`58`.

## Invalid earlier row

The earlier row set `max_stack_depth` to `8`, called
`load_server_options()`, and asserted only that unbounded recursion eventually
raised `E_MAXREC`.

That row did not test its description. Pinned Toast clamps every configured
depth below the compiled default of `50` back to `50`. Ignoring the property
entirely would still have produced eventual `E_MAXREC` and passed the row.

The reload also poisoned Banteng's captured setup result. Banteng does not
implement `load_server_options()`, so setup returned `E_VERBNF`; the runner
captured that error as `{obj}` and substituted the asserted expression as
`E_VERBNF:audit_depth()`. Banteng correctly rejected the error value as a verb
receiver and returned `E_TYPE`. Recursion never began.

The corrected durable row removes both irrelevant reload calls, uses an
above-default value, and asserts the exact number of entered activations.

## Live Toast results

The original row passed against pinned Toast, but only weakly:

```text
1 passed, 11518 deselected in 12.40s
```

The distinguishing temporary probe at depth 60 passed and observed counter
58:

```text
1 passed, 11519 deselected in 11.10s
```

The corrected committed row then passed:

```text
1 passed, 11518 deselected in 11.07s
```

All runs used pinned Toast source and executable identity
`aecc51e9449c6e7c95272f0f044b5ba38948459e`, the Barn WSL wrapper, bundled
`Test.db`, and both stock Toast profile manifests.

## Toast option semantics

`src/include/options.h:102-125` defines `DEFAULT_MAX_STACK_DEPTH` as `50` and
states that the database option overrides it only when larger than the
default.

`src/server.cc:1419-1427` accepts only `TYPE_INT`; missing or wrong-type values
use the supplied default. `src/execute.cc:3211-3218` reads the option for a new
task and clamps every value below `50` upward to `50`. There is no explicit
upper clamp in this path.

`max_stack_depth` is not a cached server option. The cache population at
`src/functions.cc:526-550` does not include it. `load_server_options()` is
therefore irrelevant to visibility.

Input, server, and forked tasks call `current_max_stack_size()` when they
start through `src/execute.cc:3290-3383`. A task samples its limit once at that
boundary. Suspended tasks persist `max_stack_size` and restore it without
resampling through `src/execute.cc:221-233,3252-3263` and
`src/eval_vm.cc:88-126`.

## Toast frame enforcement

`src/execute.cc:607-615` owns activation admission. `push_activation()` allows
activation indices `0` through `max_stack_size - 1`; an attempted push while
the top activation is already at the last index fails without changing the
stack.

`call_verb2()` at `src/execute.cc:663-729` resolves the target verb first, then
maps a rejected activation push to `E_MAXREC`. `OP_CALL_VERB` propagates that
error through the ordinary error path at `src/execute.cc:2115-2192`.

For the corrected row at configured depth 60, the exact Test.db eval path is:

- index 0: public `#2:eval` command verb;
- index 1: compiled activation created by builtin `eval()`;
- indices 2 through 59: 58 entered `audit_depth` activations;
- the next recursive call attempts another push and receives `E_MAXREC`.

At effective default depth 50, the same row would enter only 48 recursive
activations. Counter 58 therefore distinguishes honoring 60 from ignoring the
option or applying the default.

## Corrected conformance boundary

The corrected row is in
`C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml`
and committed as `fcc853a fix: prove runtime max stack depth`.

Its setup creates the object, counter property, recursive verb, and integer
option, then returns the object as the captured value. The first asserted step
must return `E_MAXREC`. The second asserted step must return integer `58`.
Cleanup recycles the object and deletes the option.

No reload result, setup error, or eventual-default overflow can satisfy the
corrected assertions.

## Barn comparison

Barn agrees that recursive overflow returns `E_MAXREC`, but differs from the
Toast option contract:

- `builtins/limits.go:92-219` caches the option and accepts every positive
  integer, including values below 50;
- `builtins/system.go:609` requires `load_server_options()` to refresh that
  cache;
- `scheduler/task_factory.go:27` copies the cached limit into a new VM;
- `vm/vm.go:96-100` rejects a frame when `len(Frames) >= MaxStackDepth`;
- `scheduler/task_load.go:73-81` resamples the current cache when restoring a
  queued task instead of preserving its sampled limit.

Barn's specs correctly name integer default 50 and recursive `E_MAXREC`, but
omit Toast's minimum and sampling lifetime. Barn is a useful implementation
reference for frame admission, not authority for option visibility or values
below 50.

## Current Banteng result

With the corrected row, current Banteng enters recursion but never returns.
The conformance transport times out after five seconds. No managed Banteng
process remains afterward.

Static and dynamic tracing show:

- `MooCompiler` emits type-correct receiver, verb name, empty argument list,
  and `CALL_VERB` for `this:audit_depth()`;
- `MooVm` resolves the verb and calls `VmState.pushVerbFrame`;
- `VmState.pushVerbFrame` unconditionally pushes the frame;
- `E_MAXREC` exists as a value and message but has no frame-depth raise site;
- `MooRuntime.executeStored` does not sample `max_stack_depth`.

The timeout is therefore the intended red for missing stack-depth semantics.

## Required Banteng boundary

Banteng must:

- sample uncached integer `#0.server_options.max_stack_depth` when each new
  task starts;
- use default and minimum `50`, accepting larger integers without an explicit
  upper clamp;
- retain the sampled maximum as task-owned `VmState` data;
- reject a verb or eval frame before pushing when the current frame count has
  reached the sampled maximum;
- raise ordinary `E_MAXREC` through the existing VM error path;
- leave the frame stack unchanged on rejection.

The focused regression must use bounded recursion so a missing limit returns
success rather than hanging the Java test. It must prove an above-default
configured depth succeeds below the limit and returns `E_MAXREC` above it.

This record does not authorize an option cache, reload builtin, helper,
separate stack object, Java recursion, tick enforcement, suspension changes,
checkpoint changes, or a broader task-limit refactor.
