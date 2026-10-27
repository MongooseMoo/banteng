# Lag callback

## Goal
Implement Toast-compatible lag callback timing, payload and fresh root context, including first-slice-before-error-handler ordering and detachment on actual suspension. Preserve the original boundary/result/resumption and commit-safe effects.

## State
Scoped runtime/scheduler repair is complete, within the approved bound. Base contains callback-local and WAIF repairs; the immutable continuation stack projection branch is integrated. Production and authored test compile under -Werror, and the focused LagCallbackTest passes. Temporary direct-hook and elapsed-time diagnostics are removed. Local commit only; parent owns integration, review, CI and publication.

## Decisions and why
Time one logical interpreter slice across internal FORKED/PENDING_BUILTIN iterations, using existing runtime nanoTime. Execute lag first slice through the existing scheduler/runtime continuation path before routing original boundary. Reuse TaskRegistry.projectStackFrames from the continuation worker. Suspended lag work must retain both its snapshot and optional host callable; current RuntimeStep only carries a snapshot, so carry that actual optional boundary work through existing RuntimeStep/Entry rather than drop host suspension or add a queue/service. Admit detached work only after successful publication.

## Surprises
Original result is retained in the enclosing scheduler invocation while the fresh lag first slice runs; no added executable original-task frame or persistent timer is needed. Inline hook output was initially discarded; existing ConnectionIo stages ordinary current-player notifications on VM effects, so transfer handled output into the original state and retain the lag marker when extracting first-suspension output. Authored helper initially used String.split and failed Error Prone; direct first-space lookup repairs it. Direct-hook diagnostic exposed notify E_PERM because a distinct nonwizard owner cannot notify another player; only the output test gives its distinct owner wizard permission. Parent traced the background test's eval this to #-1; the real producer now calls player:cb_lag(). These were authored fixture defects, not evidence of missing lag dispatch.

## Next step
Parent integration must preserve both LAG_HANDLER_RETURN and outcome worker's UNCAUGHT_HANDLER_RETURN suspension markers, plus its START_SCRIPT/CHECKPOINT suspended-output routes. No publication from this worker. Next independently assigned task is read-only WAIF indexed-assignment ownership planning.

## Validation
Explicit WSL JDK25 with JAVA_HOME=/opt/java/25 and PATH=/opt/java/25/bin:/usr/bin:/bin: ./gradlew --no-daemon :test --tests world.mongoose.banteng.runtime.LagCallbackTest. Successful frozen-source run; only subsequent source edit is indentation in the authored test. No broad local CI.

## Draft PR body
Invoke handle_lagging_task after a slow interpreter slice using the live FLOAT threshold and Toast-compatible outcome payload. Run the fresh-root hook's first slice before original error/timeout routing, then detach actual suspension through existing publication ownership while preserving original task roots, host work and requested output.

Validated with the authored LagCallbackTest filter under JDK25. Parent owns current-branch integration and full PR CI; this local branch has not been published.
