# Yin yield Toast authority

## Current state

- Active task: read-only authority trace for `audit_yin_yields_when_thresholds_crossed` in Phase 4.
- No repository source or tracked files have been edited.
- Findings: not yet collected; the exact YAML row and pinned Toast source remain to be inspected.
- Blocker: none.
- Next action: locate and read the exact fixture row, then trace the pinned Toast implementation for the yin signature, threshold semantics, and scheduling order.

## Findings

- Exact fixture: `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml:776-808` at conformance HEAD `31e220fecdd6dc1651475a76829ce409876dccdc`.
- `git blame` assigns the whole row to `68c698f0dd6458d75b53bb1c842e88215d2a1b73` (`Add hard task scheduling Toast oracles`, Christopher Toth, 2026-05-05).
- The managed stock profile pins `/root/src/toaststunt`; its HEAD was verified as `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Exact live managed run passed: `PYTEST_ADDOPTS="-k audit_yin_yields_when_thresholds_crossed" timeout 30s scripts/run_managed_wsl.sh toast stock src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml` -> `1 passed, 22 deselected in 5.81s`.

### Exact Toast contract

- `src/execute.cc:3542-3577`: `yin([time [, minimum ticks [, minimum seconds]]])`; defaults are time `0`, minimum ticks `2000`, and minimum seconds `2`. It suspends iff `ticks_remaining < min_ticks || timer_wakeup_interval(task_alarm_id) < min_seconds`.
- `src/execute.cc:3569-3572`: when at least one argument is present, negative time, nonpositive thresholds, `min_ticks >= fg_ticks`, or `min_seconds >= fg_seconds` return `E_INVARG`.
- `src/execute.cc:3779`: arity is 0..3; time is numeric and both thresholds are integers.
- `src/execute.cc:3055-3083` and `src/include/options.h:106-131`: a foreground task snapshots its tick/second execution limits at interpreter entry; stock defaults are 60000 ticks and 5 virtual-CPU seconds.
- `src/execute.cc:977-985`: counted bytecodes decrement ticks before execution and abort at zero.
- `src/timers.cc:218-237`: the seconds predicate reads the virtual timer's whole remaining seconds.
- `src/tasks.cc:1183-1204`: zero-delay waiting tasks are time ordered with stable insertion on equal timestamps.
- `src/tasks.cc:506-524,1629-1645,1673-1680,1772-1795`: due tasks move in waiting order into a per-programmer FIFO background queue; one runnable task executes per scheduler visit. The earlier `fork (0)` therefore runs before the later zero-delay suspended parent resumes.
- `src/server.cc:798-856`: each main-loop visit processes I/O and then invokes `run_ready_tasks()`.

### Fixture validity decision

- The current row is valid as a positive observation that `yin` suspended the foreground task and allowed an already queued zero-delay fork to run before the parent continued. That is exactly what the returned order `{main-before, fork, main-after}` proves.
- It does not prove the complete signature, defaults, validation errors, non-yield case, strict boundary behavior, seconds-threshold behavior, cross-player order, or equal-timestamp ordering.
- Its stated tick-cause proof is not fully isolated. The stock task starts at 60000 ticks and executes many counted bytecodes before `yin(0, 59999, 4)`, so the tick predicate is certainly true. However, Toast combines the tick and seconds predicates with OR, and the row also supplies a seconds threshold of 4. If the setup consumes more than one virtual-CPU second, the seconds predicate is independently true too.
- The row also writes `fg_ticks` inside the same task. That changes `yin`'s validation ceiling, but execution limits were already snapshotted at task entry. It happens to be valid on the pinned Test.db because the property is absent and the compiled foreground default is already 60000; it is not a general proof that runtime option mutation changed the current task budget.

## Recommended correction

- Put execution-limit setup in a separate run step so the measured task begins with known `fg_ticks` and `fg_seconds`.
- Give the task large seconds headroom and use minimum seconds `1`.
- Capture `threshold = ticks_left()` before creating the fork, then call `yin(0, threshold, 1)` after the fork has consumed additional counted bytecodes. This makes the strict tick-below condition causal without relying on a hard-coded initial count, while the virtual-CPU seconds condition remains far from its threshold.
- Preserve the existing expected order. If exact signature/default/error/boundary semantics are required, add separate focused rows; this one should not claim them.

## State

- Read-only authority work is complete. No tracked file or Git state was changed.
- Blocker: none.
- Next action: parent decides whether to correct the row to isolate the tick predicate before implementing Banteng `yin`.
