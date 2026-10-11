# Row 42 implementation checkpoint

- Scope: committed `bg_ticks` child-start sampling design; production edit only in `src/main/java/moo/runtime/MooRuntime.java`; test regressions already present in `MooRuntimeTest.java`.
- Added local `LinkedHashMap<VmState, VmState.ForkRequest> pendingForks` beside the existing scheduler maps.
- At each fork boundary, the existing unstarted child token retains its program and runnable/timed scheduling and is associated with the existing request. No `bg_ticks` read occurs there.
- At runnable dequeue, a pending token is removed before program lookup. Its placeholder program entry is removed; `#0.server_options.bg_ticks` is read inline; an integer is clamped with `Math.max(100L, ticks.value())`, otherwise `DEFAULT_BACKGROUND_TICKS` is used; a real child is constructed from the request and its program is installed before the unchanged VM execution path.
- Focused green: `startsNewBackgroundTaskWithTheConfiguredTickBudget` (`BUILD SUCCESSFUL`, 14s).
- Focused green: `samplesBackgroundTickBudgetAfterParentMutationBeforeYield` (`BUILD SUCCESSFUL`, 13s).
- Focused green: `samplesBackgroundTickBudgetWhenDelayedChildStarts` (`BUILD SUCCESSFUL`, 14s).
- Formatting matched the surrounding Google Java shape, so Spotless was not applied. `git diff --check` passed.
- Production diff: 20 insertions in `MooRuntime.java`; cumulative tracked diff including the three test regressions is 146 insertions across two files.
- Remaining required action: none within this task.
- Current blocker: none.
