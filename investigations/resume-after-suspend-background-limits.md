# Investigation: resume after suspend uses background limits

## Facts (verified)

- The requested current Banteng result is `[0,0,1]`; the expected result is `[0,1,1]`.
- The investigation is read-only. Source edits and Gradle runs are out of scope.
- The exact fixture sets `fg_ticks=50000`, `bg_ticks=9000`, and `bg_seconds=7`, calls `load_server_options()`, captures `before_ticks`, executes `suspend(0)`, then captures `after_ticks` and `after_seconds`.
- Its expected first element is deliberately false: the already-running foreground task keeps the limit with which it began. Its second and third elements require the resumed segment to use the newly loaded background limits.
- `VmSnapshot` stores the complete frame stack, instruction pointers, `remainingTicks`, `elapsedCpuNanos`, and `remainingCpuNanos`.
- `VmState.restore` restores `snapshot.remainingTicks()` and `snapshot.remainingCpuNanos()` unchanged, then clears only the live CPU anchor.
- `VmState.resume` clears the suspension wake state, pushes the wake value, and marks the existing frames running; it does not change execution limits.
- `PublicationScheduler.executeSegment` selects `MooRuntime.startBackgroundTask(snapshot)` only when `Entry.startingBackground` is true; otherwise it calls `VmState.restore(snapshot)`.
- Fork children are created with `startingBackground=true`. `publishSuspension` creates the suspended foreground work with `startingBackground=false`, and zero-delay wake preserves that false value.
- `MooRuntime.startBackgroundTask` reads current `bg_ticks` and `bg_seconds`, but constructs a fresh `VmState` from only initial locals/programmer/verb location. It is suitable for an unstarted fork child, not a suspended task with live frames.
- Existing scheduler tests cover background fork tick aborts and ordinary suspension behavior, but no test asserts that a foreground task resumes with background limits.

## Theories (plausible)

1. `VmState` snapshot omits or alters execution-limit state needed by resume. Ruled out: it durably captures both limits and full execution state.
2. Resume restores the wrong state or program-counter position. Ruled out for the observed result: it restores the captured frame/IP correctly, but also restores the wrong class of limits.
3. The scheduler classifies only new fork children as background starts and classifies resumed foreground tasks as ordinary restoration. Supported and causal.
4. The fixture expects behavior that Banteng's intended execution contract does not support. Ruled out: the plan explicitly owns Toast background task limits in Phase 4, and Banteng already models every required state component.

## Tests Run

| Test | Hypothesis | Result | Rules Out | Supports |
|------|------------|--------|-----------|----------|
| Read exact YAML row | Fixture shape/meaning | The row distinguishes inherited foreground start from background resume | Ambiguous expected transition | H3 |
| Trace `VmState.snapshot/restore/resume` | H1/H2 | Frames/IP and remaining budgets are preserved exactly; resume does not rebase budgets | Missing/corrupted snapshot | H3 |
| Trace scheduler boundary and wake | H3 | Suspended work is stamped `startingBackground=false`; zero-delay wake preserves it | Background loader being called on resume | H3 |
| Trace `startBackgroundTask` | Fix constraint | It reads correct live background options but creates a new root state | Reusing it unchanged for a suspended snapshot | Need a resume-specific budget rebase |

## Current Best Theory

The scheduler has no foreground-suspended-to-background-resumed state transition. It restores the captured foreground `remainingTicks`, producing the second `0`. The third `1` is not evidence that background seconds were applied: the retained foreground/default CPU budget can also lie in the fixture's broad `5..7` acceptance window, especially with process-CPU sampling granularity.

## Open Questions

- The exact Toast-side implementation mechanism is outside this Banteng-only trace; the fixture's pinned-Toast validation is a separate authority check.

## Next Action

Provide the parent agent the causal trace and fix constraint. A corrective slice should preserve the snapshot's frames/IP/task state while replacing its post-suspension tick and CPU budgets from current background options; simply setting `startingBackground=true` would restart the task from its root and is incorrect.
