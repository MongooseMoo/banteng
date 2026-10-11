# Phase 4 timeout-handler slice

## Current authority and observation

- Active exact row: `audit_handle_task_timeout_invoked` in `task_scheduling_toast_oracle.yaml`.
- Pinned Toast `aecc51e9449c6e7c95272f0f044b5ba38948459e` passes the row.
- Toast calls executable inherited `#0:handle_task_timeout` after tick abort with `this = #0`, the original task player, caller `#-1`, and args `{"ticks", stack_list, traceback_strings}`. The handler verb owner is the programmer. A truthy return suppresses fallback timeout output.
- Banteng currently snapshots `ABORTED` but reduces it to output plus an `aborted` flag. Publication rolls back the transaction and schedules no handler, so the row observes `{}`.
- The required Banteng boundary is: preserve the aborted snapshot, roll back the exhausted attempt, and continue the same logical task through the existing scheduler/runtime transition path in a fresh `WorldTxn`; `MooRuntime` owns handler lookup, locals, compilation, and stored-verb activation.

## Work in progress

- Added an uncommitted regression `PublicationSchedulerTest.invokesTaskTimeoutHandlerAfterBackgroundTickAbort` that installs a handler, sets `bg_ticks = 100`, launches an infinite background fork, and requires the handler to record valid three-argument timeout data.
- No production code has been edited yet.
- The first focused Gradle invocation did not reach a test result because another WSL process was running a large `make -j32` build and the Gradle wrapper remained starved for more than two minutes. Only this slice's wrapper PID was terminated; the unrelated build was not touched.

## Repository state

- Branch was `master...origin/master` before the test edit.
- Tracked changes are currently limited to the new regression in `src/test/java/moo/runtime/PublicationSchedulerTest.java`.
- All `notes-*.md` files remain untracked and must not be staged.

## Next action

Implement the timeout continuation in `PublicationScheduler` and `MooRuntime`, then run the focused regression with an explicit bounded WSL timeout. If the unrelated build still prevents execution, wait for it rather than changing the test or production contract.

## Subsequent implementation and gate state

- Production changes are now present but uncommitted in `PublicationScheduler` and `MooRuntime`: the aborted snapshot survives rollback, the same task ID receives a fresh runtime-transition attempt, and runtime invokes executable inherited `#0:handle_task_timeout` with the Toast argument shapes. Truthy return suppresses fallback output; false/missing/aborted handler falls through to the original timeout output.
- A handler that itself exhausts ticks is explicitly prevented from recursively invoking itself; its transaction rolls back and the original timeout output wins.
- `git diff --check` passes.
- After the unrelated `make -j32` job exited, the focused WSL Gradle test still timed out at task-graph calculation before compilation.
- A bounded diagnostic `./gradlew --no-daemon --no-configuration-cache help` also timed out before task execution. Therefore the live blocker is Gradle initialization in WSL, below this test and independent of configuration-cache reuse.
- Next action: inspect the Gradle build initialization inputs and live daemon stack narrowly, restore the required WSL gate, then run the focused regression. Do not commit until the focused test, exact conformance row, full scheduling path, Java check, and diff check have completed as required.

## Verified result

- Gradle initialization was blocked by online resolution; the locked local cache initialized successfully with `--offline`. After that warmed the configuration, the plan's exact online `JAVA_HOME=/opt/java/25 ./gradlew check` command also succeeded.
- Focused scheduler regression passed in 16 seconds.
- Exact Banteng row `audit_handle_task_timeout_invoked` passed: `1 passed, 22 deselected in 6.29s`.
- Full task-scheduling family improved from 17 pass / 6 fail to 18 pass / 5 fail. The timeout row is green; the first remaining failure is `audit_suspended_task_survives_restart` (`before` persisted instead of the expected `after`).
- Exact Java gate passed in 31 seconds and `git diff --check` passed.
- Next action: commit only the two production files and one regression test for this kept slice, leave all notes untracked, then begin the suspended-task restart target.
