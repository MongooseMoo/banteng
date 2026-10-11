# Delayed fork kill race trace

## Current task

- Read-only Phase 4 investigation of `PublicationSchedulerTest.killedDelayedForkNeverPublishesItsWorldMutation` and the production delayed-fork timer / `kill_task` paths.
- No production or test edits, Gradle runs, or commits are authorized for this subtask.

## Current state

- The reported symptom is that `kill_task` can return success while the delayed child still publishes world mutation `99` under full-suite load.
- `PublicationScheduler.publishVmCompletionOutsideMonitor()` registers the delayed child, advances the parent publication boundary, queues the parent continuation, and starts a timer for the child (`PublicationScheduler.java:539-583`).
- Persisted delayed-task support changed the timer from an exact relative duration to `scheduledEpochSecond = Math.round(nowSeconds + delay)` and later computes a nonnegative millisecond wait to that whole second (`PublicationScheduler.java:549-550, 630-647`). A requested `0.2` second delay therefore becomes zero whenever the call occurs in roughly the first 0.3 seconds of a wall-clock second, and otherwise varies up to about 0.7 seconds. This makes the race wall-clock-phase dependent.
- On wake, the timer removes its own `timedWork` row while holding the scheduler monitor, releases that monitor, then calls `enqueueReady()` (`PublicationScheduler.java:648-657`). `enqueueReady()` checks `TaskRegistry.discardIfCanceled()` exactly once before adding the child to `ready` and dispatching it (`PublicationScheduler.java:675-679`).
- `TaskRegistry.killTask()` is synchronized only on the registry. Success means that the task row existed and permission passed; it removes the row and adds the task id to `canceled` (`TaskRegistry.java:65-89`). It does not remove a scheduler `ready` entry, an executor entry, a completed speculative attempt, or an in-flight attempt.
- Exact failing interleaving: timer reaches `enqueueReady()` before the parent invokes `kill_task`, finds no cancellation marker, queues/dispatches the child; the child can execute `#0.scheduler_counter = 99` in its speculative `WorldTxn`; the parent then invokes authorized irrevocable `kill_task`, which removes the registry row, records `canceled`, and returns `0`; publication tickets still drain parent before child, but `publishAttempt()` validates and commits the child without any cancellation recheck (`PublicationScheduler.java:396-434, 477-530`). The marker therefore survives unused while the child publishes `99` after the parent has reported success.
- The smallest correction using the existing control structures is a publication-time cancellation fence at the start of `publishAttempt()`: call the existing `taskRegistry.discardIfCanceled(entry.taskId())`; if true, close the speculative transaction and route through the existing failed/terminal publication path so the ticket advances and no `SegmentResult`, spawned work, world mutation, or deferred effect is published. A `CancellationException` can represent the internal terminal outcome; registered fork tasks have no ingress future, so this is silent to the killed child while preserving the parent’s successful `kill_task` result.
- This fence complements rather than replaces the existing timer admission check: cancellation before admission still suppresses enqueue; cancellation after admission but before the child publication ticket now suppresses commit.

## Blocker

- None for the read-only diagnosis. No code change or test run was authorized for this subtask.

## Next action

- Parent agent can implement the publication-time fence, add a deterministic regression that forces the child past admission before the parent kill, then run the focused test and required Phase 4 gates.
