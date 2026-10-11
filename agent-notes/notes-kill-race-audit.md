# Kill race audit

- Task: read-only Phase 4 authority and determinism audit of `PublicationSchedulerTest.killedDelayedForkNeverPublishesItsWorldMutation`.
- Current state: no production or test changes made; Gradle will not be run; no commit will be created.
- Findings:
  - The test expectation is valid against pinned stock Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`. Toast `double_to_start_tv()` preserves the fractional delay with `timeval`, and `kill_task()` unlinks and frees a matching waiting fork before returning success.
  - The current `counter == 99` failure is real. A fixed 400 ms sleep cannot manufacture that committed mutation; it only observes it.
  - Commit `4e7b262` replaced the prior relative nanosecond timer with `scheduledStart = Math.round(nowSeconds + delay)`, then sleeps until that whole epoch second. A requested `0.2` second delay can therefore become zero or vary by wall-clock phase, unlike Toast.
  - Once the prematurely due child passes `discardIfCanceled()` and is admitted to `ready`/the executor, a concurrent `kill_task()` only removes the registry entry and records `canceled`; execution and publication never recheck cancellation. That admitted child can still commit `scheduler_counter = 99`.
  - The existing fixed sleep makes race coverage nondeterministic and can miss an admitted-but-not-yet-run child, but the observed failure is not a false failure.
  - Deterministic regression shape: use the test's existing reflected executor/latch technique with one worker; create a long-delay fork; capture its existing `TimedWork`; occupy the worker; explicitly enqueue that existing work so its entry is admitted behind the blocker; invoke the existing `TaskRegistry.killTask` while the child is admitted; release the blocker; wait on scheduler ticket/publication quiescence with a deadline; assert the child mutation never committed and the registry is empty. This requires no production interface, helper, adapter, or clock abstraction.
- Blocker: none for the audit. The checked-out WSL Toast source HEAD is currently `1c4714d...`, not the manifest commit, so authority was read with `git show aecc51e...:<path>` rather than substituting the checkout HEAD.
- Next action: parent agent can use the deterministic admitted-before-kill regression to drive the smallest scheduler/task-registry fix; live timer precision should remain separate from the v17 whole-second serialized field.
