# Resume-after-suspend background-limits trace

## Current task

- Read-only trace of `audit_resume_after_suspend_uses_background_limits`.
- Inspect `VmState` snapshot/resume and `PublicationScheduler.startBackgroundTask`.
- Explain the current Banteng result `[0,0,1]` versus expected `[0,1,1]`.
- Assess whether the fixture is valid from Banteng's implementation perspective.
- Do not edit source and do not run Gradle.

## Current findings and observations

- Exact fixture behavior: the task begins before it writes/loads `fg_ticks=50000`, so `before_ticks` is intentionally outside that range (`0`); after `suspend(0)`, Toast requires newly loaded `bg_ticks=9000` and `bg_seconds=7` (`1,1`).
- `VmSnapshot` correctly preserves frames, instruction pointers, remaining ticks, and remaining CPU nanoseconds.
- `VmState.restore` preserves the captured foreground budgets; `VmState.resume` only clears suspension state, pushes the wake result, and continues the saved frames.
- `PublicationScheduler.executeSegment` applies `MooRuntime.startBackgroundTask` only when `startingBackground=true`.
- New fork children carry `startingBackground=true`, but `publishSuspension` records resumed foreground tasks with `startingBackground=false`; `suspend(0)` wakes that unchanged work immediately.
- Therefore resume uses plain `VmState.restore`, retaining roughly the foreground tick budget and producing the current second element `0`.
- The current third element `1` is coincidental rather than proof of correct background seconds: the retained foreground/default CPU budget can satisfy the fixture's broad `>4 && <=7` interval under process-CPU sampling.
- `MooRuntime.startBackgroundTask` already reads live `bg_ticks` and `bg_seconds`, but creates a fresh root state without the suspended frames/IP. Merely marking resume `startingBackground=true` would restart the program and is not a valid fix.
- The fixture is valid from Banteng's perspective: Phase 4 explicitly owns Toast background limit semantics, and Banteng already represents the durable state and scheduler boundary needed to implement the transition. No existing test freezes the current incorrect behavior.

## State and blocker

- State: read-only causal trace complete; production source remains untouched and no Gradle command was run.
- Blocker: none known.

## Next action

- Report the causal trace and fix constraint to the parent agent. The corrective implementation must preserve the suspended snapshot's frames/IP/task state while rebasing tick and CPU budgets from current background options.

## Implementation update

- The original fixture was invalid for its foreground assertion because it changed `fg_ticks` and read `ticks_left()` in the same already-started task while expecting that assertion to be false.
- The fixture now performs option setup in one task and the foreground/suspend/background assertions in a second task, expecting `{1, 1, 1}`. The corrected pinned Toast family passes 23/23. The fixture commit is `b1c1837 fix: prove foreground resume limits` in `C:/Users/Q/code/moo-conformance-tests`.
- The corrected Banteng baseline is 22/23. The one failure returns `{1, 0, 1}`, proving only the post-suspension tick budget is wrong.
- The active Banteng source slice preserves the suspended snapshot and frames through `VmState.restoreBackground`, resets its tick/CPU limits from current background options, and marks suspension wakes as background. An exact scheduler regression test was added.
- Three focused Gradle attempts and a `help` diagnostic timed out before compilation. A fresh project cache and disabled configuration cache did not change the stall.
- A live Java thread dump shows the Gradle daemon worker blocked in an outbound Apache HTTP metadata connection during dependency resolution. The stall is not in Banteng source, test execution, or project-cache locking.
- The attempted generated-cache deletion was rejected before execution; no cache or source was removed.

## Implementation state and blocker

- Banteng has one uncommitted source slice in `VmState.java`, `MooRuntime.java`, `PublicationScheduler.java`, and `PublicationSchedulerTest.java`.
- The slice has not yet produced a passing Java test because Gradle dependency metadata resolution is currently hanging on outbound HTTP.
- Next action: let the current 120-second timed Gradle process exit, identify the exact remote metadata request from daemon evidence, and rerun the focused test once dependency resolution is available without changing the required Java gate.

## Verified result

- Offline dependency resolution allowed the focused regression to run and pass under Java 25 in 54 seconds.
- The corrected Banteng scheduling family passes 23/23 in 35.53 seconds.
- The first exact `JAVA_HOME=/opt/java/25 ./gradlew check` run reached all 251 tests; the new regression passed, while the pre-existing delayed-fork kill timing test failed once because its 200 ms child ran before the fixed 400 ms observation completed.
- The exact failed delayed-fork test passed on immediate rerun in 14 seconds.
- The second exact `JAVA_HOME=/opt/java/25 ./gradlew check` run passed all 251 tests and `check` in 33 seconds.
- `git diff --check` passes. The kept source slice is exactly the four intended Java source/test files; notes and investigations remain untracked and unstaged.
- Blocker: none for this slice.
- Next action: commit and push the four-file Banteng slice, reread Phase 4, and execute its exact seven-path Toast and Banteng gates.
