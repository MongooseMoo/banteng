# Tick exhaustion row audit

## Current findings

- Target row: `audit_tick_exhaustion_not_catchable` at lines 459-483 of `../moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml`.
- The row sets `fg_ticks` to 100, reloads server options, then runs an infinite loop inside `try/except (ANY)` and expects `Task ran out of ticks`.
- `MooVm.executeInstruction` charges counted instructions by calling `VmState.decrementRemainingTicks()` before dispatch.
- `VmState.decrementRemainingTicks()` only decrements the counter. It does not detect zero or abort the task, so the loop can continue with negative ticks.
- Normal MOO errors flow through `MooVm.raiseError`, which searches active handlers; routing tick exhaustion through that path would make `except (ANY)` able to catch it and would violate this row.
- The publication scheduler currently turns `VmState.Outcome.ERRORED` into a successful segment result containing staged output. Java failures instead go through `finishFailure`, remove task/ingress registry entries, and complete the root future exceptionally.
- `PublicationScheduler.submit()` unwraps and rethrows an exceptional completion; `MooServer` only catches `IOException` in its connection loop. Therefore throwing a generic Java exception at the VM tick boundary would not produce the row's expected output and could terminate connection handling.
- Runtime continuations turn terminal VM snapshots into normal output through `VmState.output()`. There is currently no resource-abort outcome or abort-message field in `VmState`/`VmSnapshot`.
- `MooRuntime.publishVmState()` applies staged connection/boot/checkpoint effects before it handles terminal-state cleanup, so an uncatchable resource abort must be modeled carefully enough that task-local staged effects are not accidentally published.

## State and blocker

- Authorized source and focused test edits are now present but uncommitted.
- The two focused VM tests pass: zero-before-dispatch and active `except (ANY)` bypass.
- The scheduler regression first failed because the v4 fixture has no `fg_ticks`; setup now adds it when absent.
- Its second run reached the abort path and completed normally with `[prefix, Task ran out of ticks]`, but the asserted existing suffix was missing. The mutation rollback assertion was not reached on that run.
- The exact remaining issue is whether the 100-tick abort occurred in the `do_command` gate before selected eval dispatch, or in the selected eval with a lost continuation. The runtime code proves that `LINE_SELECTED_COMMAND_APPEND_OUTPUT` itself appends both state output and suffix when the connection exists.

## Next action

- Implement the authorized slice in `VmState`, `MooVm`, and `PublicationScheduler`: add `ABORTED`, abort after a counted decrement reaches zero, carry abort state through an existing runtime continuation, mark the final `SegmentResult`, and close rather than commit the transaction/context.
- Add focused `MooVmTest` coverage for zero-before-dispatch and an active `except (ANY)` not catching the abort.
- Add focused `PublicationSchedulerTest` coverage that an eval mutation before exhaustion is rolled back while the normally completed output retains connection framing and `Task ran out of ticks`.
- Trace the single continuation transition used by the failing focused test; do not broaden testing.

## Implementation observations

- `SegmentResult` is a private record with direct factories for returned, boundary, and irrevocable results. It can carry one additional `aborted` boolean without introducing another production type.
- `MooRuntime.finishAttempt()` detaches and returns the attempt-local session/effect context. Skipping `runtime.publishAttempt(context, ...)` discards those staged effects and session changes.
- The scheduler can close the aborted child transaction and return an ordinary published result with no spawned work; the existing result-publication path will then complete root ingress normally from the framed output.
- The relevant eval continuation (`LINE_SELECTED_COMMAND_APPEND_OUTPUT`) appends VM output and the connection suffix, so retaining the aborted terminal snapshot through the existing continuation preserves framing without changing `MooRuntime`.
