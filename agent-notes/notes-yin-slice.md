# Phase 4 yin yield slice

## Exact target and current state

- Active row: `audit_yin_yields_when_thresholds_crossed` in `task_scheduling_toast_oracle.yaml`.
- The row sets `fg_ticks` to 60,000, queues a zero-delay fork, calls `yin(0, 59999, 4)`, and requires order `{"main-before", "fork", "main-after"}`.
- Corrected current scheduling family: pinned Toast 23/23; Banteng 21/23. Banteng fails this row with `E_VERBNF`, so the immediate cause is the missing builtin manifest entry.
- Banteng `master` is tracked-clean at pushed `4e7b262`. The conformance fixture correction is committed locally as `31e220f`; that repo is 39 commits ahead of `origin/main` and was not pushed as a stack.

## Pinned Toast authority

- Pinned Toast source is `/root/src/toaststunt` at `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- `src/execute.cc:3542-3577` implements `bf_yield_if_needed`; `src/execute.cc:3779` registers `yin` with zero to three arguments: numeric delay, integer minimum ticks, integer minimum seconds.
- Defaults are delay 0, minimum ticks 2000, and minimum seconds 2.
- It raises `E_INVARG` for negative delay, nonpositive thresholds, or thresholds greater than or equal to configured foreground limits.
- It suspends when remaining ticks are below the requested tick threshold or remaining seconds are below the requested seconds threshold; otherwise it returns zero without suspending.
- The exact row crosses the tick threshold after prior instructions and has a ready zero-delay fork, so the suspension must publish the fork before the caller resumes.

## Banteng implementation surface

- Existing `suspend` already returns `Result.suspend(seconds)`; `MooVm.applyBuiltinResult` turns `delaySeconds` into a VM suspension; `PublicationScheduler` handles the zero-delay wake and ready ordering.
- The builtin invocation already receives `remainingTicks` and `remainingSeconds`, so Yin can decide without a new interface, adapter, or scheduler path.
- The smallest candidate is one `BuiltinSpec` entry plus a pure `yin(arguments, remainingTicks, remainingSeconds, world)` implementation that reads current configured foreground limits for Toast's validation and reuses `Result.suspend(delay)` when a threshold is crossed.
- Required focused tests: manifest/signature/error/no-yield/yield behavior and the exact managed row. No source has been edited yet.

## Fixture correction and clean baseline

- The original row did not isolate the tick predicate because its seconds threshold could independently suspend the task, and same-task option writes did not change that task's snapshotted limits.
- The first uncommitted Yin candidate was fully reverted before fixture work. Its focused test compile failed only because the test lacked an `OptionalDouble` import.
- Conformance commit `cd9ffb1` moves limit setup into a separate task, sets `fg_seconds` to 60, captures `threshold = ticks_left()` in the tested task, and calls `yin(0, threshold, 1)`.
- The corrected family passes pinned Toast 23/23 in 34.22 seconds.
- A first post-revert Banteng run was invalid because the failed Gradle test had already produced a Yin-enabled `installDist`; that stale artifact passed Yin and must not be used as source evidence.
- After rebuilding `installDist` from clean committed source, Banteng produced the trustworthy baseline: 21 passed, 2 failed in 36.14 seconds, with Yin failing `E_VERBNF` and the background-resume row remaining separate.
- Next action: reapply the two-file catalog/test slice, including the missing test import, then run focused Java, corrected full scheduling, exact Java check, and diff hygiene before commit.

## Kept-slice verification

- Focused `moo.builtin.BuiltinCatalogTest`: `BUILD SUCCESSFUL in 44s`.
- Corrected Banteng scheduling family improved from `21 passed, 2 failed` to `22 passed, 1 failed in 38.63s`; the causally isolated Yin row passes.
- Exact `JAVA_HOME=/opt/java/25 ./gradlew check`: `BUILD SUCCESSFUL in 36s`.
- `git diff --check` passes.
- The kept source diff is only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`; it adds no scheduler path, interface, adapter, or alternate execution mode.
- The sole remaining scheduling failure is the separate `audit_resume_after_suspend_uses_background_limits` target.
