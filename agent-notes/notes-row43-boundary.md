# Row 43 boundary checkpoint

- Sole target: `task_scheduling_toast_oracle::audit_server_options_fg_seconds_runtime`.
- Exact row: `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml:138-158`.
- Setup as Wizard adds-or-sets `$server_options.fg_seconds = 12`, calls `load_server_options()`, and returns `1`; the setup step has no `expect` (`yaml:141-149`).
- The second, distinct Wizard run evaluates `return seconds_left() <= 12 && seconds_left() > 8;` and expects exact integer `1` (`yaml:150-153`).
- Cleanup deletes `fg_seconds` and calls `load_server_options()` (`yaml:154-158`).
- Memory contains only general Banteng authority guidance, not row-43 semantic facts; all conclusions must come from current repositories.
- Runner boundary: `runner.py:421-450` executes every step but verifies a result only when the step has `expect`; `_verify_expectation` (`runner.py:707-744`) requires success and then exact value equality.
- Consequently, an uncaught setup error is ignored by the runner and the second run still executes. Only `E_INVARG` from `add_property` is caught inside the setup program. The second run is the sole asserted result.
- Cleanup is best-effort and its result is ignored; because its two expressions share one run, a failing `delete_property` prevents the trailing `load_server_options()` call.
- Exact current `E_VERBNF` owner: `BuiltinCatalog.invoke` has no `seconds_left` case and its default branch at `BuiltinCatalog.java:523` returns `Result.error(ErrorValue.E_VERBNF)`.
- `load_server_options` is absent too and reaches that same default, but only in ignored setup/cleanup. A focused regression must omit it to isolate the asserted `seconds_left` boundary.
- `VmState` stores `remainingTicks` but no execution deadline or remaining-seconds state. `MooVm.invokeBuiltin` and `BuiltinCatalog.call_function` propagate ticks only.
- `MooRuntime` already owns foreground task-limit configuration: root task construction reads `#0.server_options.fg_ticks` and selects the root `VmState` tick budget. It is the narrow existing owner for reading configured `fg_seconds` at new foreground-task start.
- Remaining inspection: pin the focused regression against existing `MooRuntimeTest` conventions and state the smallest existing-owner slice.
- Current blocker: none.

## Focused regression

- Mirror `MooRuntimeTest.startsNewForegroundTaskWithTheConfiguredTickBudget()` (`MooRuntimeTest.java:805-832`) as `startsNewForegroundTaskWithTheConfiguredSecondsBudget`.
- First Wizard eval: add-or-set `#6.fg_seconds` to `12`, return `1`, and assert exact stored-eval output `{1, 1}`. Omit `load_server_options()` deliberately because that builtin is outside the asserted row boundary and currently also returns `E_VERBNF`.
- Assert `world.property(6, "fg_seconds")` is exact `IntegerValue(12)` so the configured source is independently proven.
- Second, distinct Wizard eval: exact row predicate `return seconds_left() <= 12 && seconds_left() > 8;`, expecting stored-eval output `{1, 1}`.
- This is red for the asserted reason only: current `seconds_left()` reaches `BuiltinCatalog.invoke`'s unknown-builtin default. It does not admit ignored `load_server_options()` as a substitute cause.

## Smallest likely existing-owner slice

- `MooRuntime`: at root-task construction, read `fg_seconds` from the same resolved `#0.server_options` object already used for `fg_ticks`, and sample the foreground deadline when that new root task starts.
- `VmState`: own the task's monotonic deadline/remaining-seconds state next to `remainingTicks`; this keeps live task budget state with the existing VM task owner.
- `MooVm`: pass the live remaining-seconds value through the existing single builtin invocation boundary.
- `BuiltinCatalog`: add zero-argument `seconds_left` returning the supplied integer, reject arguments with `E_ARGS`, classify it `PURE`, and propagate the same value through existing `call_function` recursion because that recursion calls the same expanded `invoke` signature.
- The single `MooRuntimeTest` regression above is sufficient for this row. Do not add a separate VM test, helper, adapter, clock interface, cached server-options loader, or `load_server_options` implementation.
- Scope limit: foreground only. Do not preempt `bg_seconds`, suspension accounting, or unrelated wall-clock timeout enforcement unless a later exact row requires them.

## Boundary completion

- The original read-only boundary stage completed with no production or test edit. The later parent-authorized focused regression edit is recorded below; this note remains untracked/unstaged.

## Focused regression programming checkpoint

- Parent authorized one test-only edit: `MooRuntimeTest.startsNewForegroundTaskWithTheConfiguredSecondsLimit` immediately after the configured foreground tick test.
- The first distinct eval add-or-sets `#6.fg_seconds = 12`, returns `1`, asserts stored-eval output `{1, 1}`, and the test independently asserts exact world property `IntegerValue(12)`.
- The second distinct eval is exact: `return seconds_left() <= 12 && seconds_left() > 8;`, expecting stored-eval output `{1, 1}`.
- No `load_server_options`, helper, clock, production edit, or commit was added.
- Pre-edit tracked state was clean on `master`; pre-existing untracked notes and `pyghidra_mcp_projects/` were left alone.
- Default shell Java is 17. Java 25.0.2 is confirmed at `C:/Users/Q/scoop/apps/openjdk/current`.
- Required run executed under Java 25.0.2: `.\\gradlew.bat test --tests "moo.runtime.MooRuntimeTest.startsNewForegroundTaskWithTheConfiguredSecondsLimit" --rerun-tasks`.
- Exact red: one test, one failure at the final assertion (`MooRuntimeTest.java:857`); expected connection output containing `{1, 1}`, actual output contained `{2, {E_VERBNF}}`.
- Because the failing assertion follows both setup assertions, the setup output and exact `world.property(6, "fg_seconds") == IntegerValue(12)` assertion passed.
- `git diff --check` passed. Tracked diff is exactly one file, `MooRuntimeTest.java`, with 29 insertions; no production diff. The checkpoint note remains untracked/unstaged.
- Current blocker: the production `seconds_left` builtin/deadline path is absent, as intentionally exposed by this red regression. Production work was not authorized in this task.

## Row-43 production implementation checkpoint

- Parent authorized edits only to `VmState.java`, `MooRuntime.java`, `MooVm.java`, and `BuiltinCatalog.java`, preserving the two red regressions and making no commit.
- `VmState.java` now has foreground default seconds `5`, a public five-argument limits constructor, existing default root constructors using `60000/5`, the four-argument explicit-ticks constructor delegating with inactive seconds `0`, a first-root process-CPU anchor, and saturated/nonnegative whole-second `remainingSeconds()` accounting.
- `MooRuntime.java` now samples integer-only `fg_ticks` and `fg_seconds` from the current server-options object at each root construction, applies minimums `100/1` and defaults `60000/5`, and always creates the root with the five-argument constructor. Background activation was not changed.
- Still pending: pass remaining seconds through `MooVm`; implement and propagate `seconds_left` plus `PURE` classification in `BuiltinCatalog`; run Spotless only if needed; run the two focused Java-25 regressions separately with `--rerun-tasks`; run diff check.
- Current blocker: none.
