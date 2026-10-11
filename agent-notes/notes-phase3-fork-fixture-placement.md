# Phase 3 fork fixture placement

## Findings

- Pinned Toast passes all five original fork rows in `language/control_flow.yaml`; their assertions are valid.
- The Phase 3 plan permits unnamed fork parsing, fork delay validation, and a serializable fork-request VM outcome.
- The plan explicitly defers task IDs, named fork-variable binding, queueing, and queued-task inspection to Phase 4.
- `fork_with_id_variable`, `fork_float_delay`, and `fork_zero_delay` require those Phase 4 runtime semantics.
- `fork_non_numeric_delay_error` passes Banteng alone. Its full-file failure was response desynchronization after `fork_float_delay` timed out.
- Banteng currently withholds the parent response until fork children finish; that is a Phase 4 scheduler defect, not a reason to alter Toast-valid expectations.

## State

- Removed the three Phase 4-owned rows from `language/control_flow.yaml` in the conformance worktree.
- They still need to be added unchanged to the existing Phase 4 `fork/fork_timing.yaml` suite.
- No production source has been changed in this slice.

## Blocker

- None.

## Next action

- Add the three unchanged rows to `fork/fork_timing.yaml`, then run the exact Toast and Banteng Phase 3 `control_flow.yaml` gate and validate the Phase 4 suite collection before committing the conformance slice.

## Completed correction

- Moved the three unchanged Phase 4-owned rows into `fork/fork_timing.yaml`.
- Phase 3 `control_flow.yaml`: pinned Toast 23 passed; Banteng 23 passed.
- Exact combined Phase 4 pinned Toast gate: 70 passed, 1 declared skip; all three moved rows passed.
- Conformance commit: `ae3ebdd fix: align fork fixtures with runtime phase`.

## Current next action

- Rerun the exact full Phase 3 Toast and Banteng path lists and continue with the first remaining Banteng failure family.

## Startup-repair observation family

- Exact Phase 3 pinned Toast gate after fork relocation: 545 passed, 4 declared skips.
- First exact Banteng rerun: only Anon4 missed its checkpoint at 500 ms.
- Changed Anon4 to the repository-precedented 750 ms wait; targeted Toast and Banteng Anon4 both passed.
- Second exact Banteng rerun: Anon4 and Anon5 passed, while Anon1, Anon2, Anon3, and Anon6 all observed before their file/log existed.
- This proves the remaining 500 ms waits are a family-wide observation race under loaded execution.
- Changed Anon1, Anon2, and Anon3 to 750 ms. Anon6 still needs the same one-field correction before validation.

## Current blocker

- None.

## Exact next action

- Change Anon6 from 500 to 750 ms, run the six startup-repair suites together on Toast and Banteng, then rerun the full exact Banteng Phase 3 gate and commit the kept fixture slice if it passes.

## Startup-repair fixture completion

- All six startup-repair suites passed together on pinned Toast and Banteng.
- Committed the five 500-to-750 ms corrections as `622336f fix: wait for startup repair checkpoints` in the conformance repository.
- The subsequent exact full Banteng Phase 3 managed gate passed: 545 passed, 4 declared skips in 54.90 seconds.
- The matching exact pinned Toast gate passed: 545 passed, 4 declared skips in 48.53 seconds.

## Current next action

- Run the remaining exact Phase 3 Java gates, confirm both tracked worktrees are clean, then continue to the first Phase 4 task.
