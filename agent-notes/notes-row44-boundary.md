# Row 44 Banteng boundary

## Scope and current state

- Sole target: `task_scheduling_toast_oracle::audit_server_options_bg_seconds_runtime`.
- Banteng `HEAD` is exact commit `7c8abb0`; tracked files are clean. Pre-existing untracked notes and `pyghidra_mcp_projects/` are outside scope.
- Durable row begins at `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml:160`.
- The complete durable row and its timing conclusion are recorded below.

## Runner boundary

- Ordinary `run` steps execute regardless of whether they have an expectation, but the runner calls `_verify_expectation` only when `step.expect` is present (`runner.py:421-450`).
- Cleanup runs in `finally` and is best-effort; cleanup results are ignored (`runner.py:452-463`).
- Runner semantics relevant to the complete row are fully recorded here; implementation and source findings follow below.

## Complete durable row

- Setup add-or-sets `$server_options.bg_seconds = 9`, add-or-sets `#0.audit_bg_seconds = 0`, calls `load_server_options()`, and returns `1`; it has no `expect`, so its result is runner-ignored.
- The sole asserted run creates `fork (0)`, assigns `#0.audit_bg_seconds = seconds_left()` in the child, executes parent `suspend(0)`, then returns whether the captured value is `<= 9` and `> 5`. Expected exact value is `1`.
- Cleanup deletes both properties and calls `load_server_options()`; its result is best-effort/ignored.
- The durable row itself requires zero-delay background sampling. It contains no delayed fork; whether a delayed regression is required depends on pinned Toast's option-sampling point.

## Current Banteng at `7c8abb0`

- `BuiltinCatalog.seconds_left` is implemented and receives `VmState.remainingSeconds()` through `MooVm`; the builtin itself is not the missing owner.
- `VmState`'s public five-argument constructor stores an explicit seconds limit. Its existing four-argument explicit-ticks constructor delegates with inactive seconds limit `0` (`VmState.java:75-94`). `remainingSeconds()` therefore returns `0` for those states (`VmState.java:172-178`).
- Foreground root construction uses the five-argument constructor with sampled `fg_ticks`/`fg_seconds` (`MooRuntime.java:1093-1111`).
- A fork first creates a queued placeholder with the four-argument constructor and `DEFAULT_BACKGROUND_TICKS` (`MooRuntime.java:1143-1155`). When that child becomes runnable, `pendingForks.remove(task)` causes `MooRuntime` to discard the placeholder, sample current `bg_ticks`, and create the executing child with the four-argument constructor (`MooRuntime.java:1121-1138`). Thus the existing activation boundary already owns background-option sampling, but seconds remain inactive.
- Existing runtime tests prove three background tick timings: configured zero-delay start, zero-delay child after parent mutation before yield, and delayed child sampling at its later start (`MooRuntimeTest.java:865 onward`). These are the idiomatic regression templates for seconds.
- Predicted focused-row red: setup/property assertions pass; the executing child successfully calls `seconds_left()` but captures `0`, so the asserted predicate returns `0` rather than `1`.

## Pinned Toast timing authority

- Pinned Toast source is `/root/src/toaststunt` at exact HEAD `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- `tasks.cc:1772-1784` dispatches a due `TASK_FORKED` through `do_forked_task`.
- `execute.cc:3374-3384` restores the fork VM and calls `do_task(..., 0)`, explicitly marking the child background.
- `execute.cc:3048-3083` calls `setup_task_execution_limits` when that interpreter begins. In the background branch it reads both `server_int_option("bg_seconds", DEFAULT_BG_SECONDS)` and `server_int_option("bg_ticks", DEFAULT_BG_TICKS)` at that moment.
- `execute.cc:3055-3061` clamps seconds below 1 to 1. `server.cc:1420-1428` accepts only an INT property and otherwise uses the default. This lookup is uncached; `load_server_options()` does not choose the task limit.
- Therefore background seconds must be sampled at child interpreter start. A queued placeholder must not capture or start the seconds limit. This applies both to a zero-delay child that starts after the parent mutates the option before yielding and to a delayed child whose option changes while it waits.

## Smallest idiomatic implementation boundary

- Change `MooRuntime.java` only.
- Add `DEFAULT_BACKGROUND_SECONDS = 3` beside `DEFAULT_BACKGROUND_TICKS`.
- Inside the existing `pendingFork != null` activation block, initialize `backgroundSeconds` to 3; from the already-read current `#0.server_options` object, independently accept integer `bg_seconds` and clamp it with `Math.max(1L, seconds.value())`, exactly beside the existing `bg_ticks` read/minimum.
- Construct the activated child with the existing five-argument `VmState(..., backgroundTicks, backgroundSeconds)`.
- Leave the initial queued placeholder on the four-argument constructor with inactive seconds `0`. This preserves the current ownership boundary: no limit or process-CPU anchor exists until the child actually becomes runnable and `MooVm.execute` installs its first frame.
- No `VmState`, `MooVm`, `BuiltinCatalog`, loader/cache, suspension, enforcement, or background queue redesign is needed.

## Exact focused JUnit regressions

Add three adjacent `MooRuntimeTest` methods, mirroring the existing background-tick tests and using fresh `FIXTURE`/runtime/Wizard setup in each:

1. `startsNewBackgroundTaskWithTheConfiguredSecondsLimit`
   - First distinct eval add-or-sets `#6.bg_seconds = 9`, add-or-sets `#0.audit_bg_seconds = 0`, returns `1`, and expects stored-eval output `{1, 1}`.
   - Assert exact world properties `IntegerValue(9)` and `IntegerValue(0)`.
   - Second distinct eval is the durable row body without `load_server_options`: `fork (0)` writes `seconds_left()`, parent `suspend(0)`, return captured `<= 9 && > 5`; expect `{1, 1}`.
   - Current red: final output is `{1, 0}` because the activated child limit is `0`; setup/property assertions pass.

2. `samplesBackgroundSecondsLimitAfterParentMutationBeforeYield`
   - Same setup at 9/0.
   - In the asserted eval, enqueue `fork (0)`, then set `#6.bg_seconds = 6` before parent `suspend(0)`; require child capture `<= 6 && > 2`; expect `{1, 1}`.
   - This proves even a zero-delay child samples at its execution turn, after the parent mutation, rather than at fork creation.
   - Current red: final output `{1, 0}`.

3. `samplesBackgroundSecondsLimitWhenDelayedChildStarts`
   - Same setup at 9/0.
   - In the asserted eval, enqueue `fork (1)`, `suspend(0)`, mutate `#6.bg_seconds = 6`, `suspend(2)`, then require captured `<= 6 && > 2`; expect `{1, 1}`.
   - This mirrors `samplesBackgroundTickBudgetWhenDelayedChildStarts` and proves the queued one-second delay does not pre-sample the limit or consume its runtime budget.
   - Current red: final output `{1, 0}`.

## Boundary conclusion

- The durable row alone proves configured zero-delay background seconds. The pinned source additionally requires both option-sampling timing regressions because `bg_seconds` is read at the same child-interpreter-start boundary as `bg_ticks`.
- Authority gate remains incomplete by parent instruction, so no tracked test or production edit has been made.
- Current blocker: tracked editing is intentionally blocked until the authority gate is complete; the implementation boundary itself is not technically blocked.
