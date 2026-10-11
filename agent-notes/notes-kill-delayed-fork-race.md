# Delayed fork cancellation race

## Current target

- Phase 4 full-suite failure in `PublicationSchedulerTest.killedDelayedForkNeverPublishesItsWorldMutation`.
- The test's `kill_task()` result is successful (`{1, 0}`), but the delayed child can still publish `#0.scheduler_counter = 99`.

## Verified observations

- Banteng tracked state is clean at `1c4714d`; only pre-existing untracked notes and investigation artifacts are present.
- The focused test passes, while repeated exact full `check` runs have exposed the failure.
- Delayed fork scheduling computes `scheduledStart` with `Math.round(currentEpochSeconds + delaySeconds)`.
- Timer delay is then derived from that rounded whole epoch second.
- Consequently, a positive delay such as `0.2` seconds can become an immediate wake depending on the current fractional second. The child can execute before the parent reaches `kill_task()`.
- `TaskRegistry.killTask()` removes the task and records its ID in `canceled`; `enqueueReady()` consumes that cancellation before ready-queue insertion. This only protects a child that has not already crossed the timer-to-ready boundary.
- The test waits 400 ms only after the parent command completes; its assertion is not the cause of the child running early.
- The configured WSL distribution is `Debian`. An attempted read-only Toast source query using `Ubuntu` failed with `WSL_E_DISTRO_NOT_FOUND`; no source or repository state changed.

## What has not yet been established

- Pinned Toast's exact positive-delay scheduling behavior still must be read from `/root/src/toaststunt` under the `Debian` distribution.
- The deterministic regression shape and minimal production correction must wait for that authority check.

## Blocker

- The scheduler slice itself is unblocked and verified.
- None. The unrelated invalid `WorldTxnPropertyTest` sampling boundary was replaced in a separate slice with deterministic exhaustive execution of all 64 write pairs.

## Verification completed

- Pinned Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e` uses `timeval` precision for fractional fork delays and unlinks a waiting fork on successful `kill_task()`.
- The deterministic one-worker zero-delay regression failed before the scheduler fix and passed after it.
- The full `PublicationSchedulerTest` class passed.
- Exact Toast Phase 4 paths: 70 passed, 1 profile-defined skip.
- Exact Banteng Phase 4 paths: 70 passed, 1 profile-defined skip.
- Focused `WorldTxnPropertyTest`: passed with all 64 pairs.
- Exact `JAVA_HOME=/opt/java/25 ./gradlew check`: passed after the fixture correction.
- `git diff --check` passed.

## Next action

- Commit and push the isolated property-test correction, verify the Phase 4 ledger, then continue with the first unchecked Phase 5 item.
