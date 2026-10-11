# Banteng row 39 restart handoff

## Current state

- Codex restarted after the prior full-suite run had been launched through exec cell `1862`.
- The old cell was unavailable after restart. The old managed suite runner was gone, while its Banteng server remained orphaned as PID `338272` on temp database `moo_conformance_r01awkxr`.
- PID `338272` was stopped and a follow-up inventory showed no managed `moo.app.Banteng` Java process.
- The identical full managed stock-profile fail-fast suite was rerun with the installed Banteng distribution and Java 25.
- Result: `1 failed, 63 passed in 87.49s`; the first failure is `task_scheduling_toast_oracle::audit_default_fg_ticks`.
- Failing step: `return ticks_left() <= 60000 & ...`; expected `1`, Banteng returned `E_VERBNF`.
- The managed server from this rerun is still live as PID `347748`, using temp database `C:/Users/Q/AppData/Local/Temp/moo_conformance_qhfehafk/Test.db` on port `24051`.

## What worked

- Process inspection proved the first pre-restart run was orphaned before rerunning it.
- The rerun produced an exact first failing row and family, so `task_scheduling_toast_oracle` is now the sole convergence target.

## What did not work

- The restarted Codex process could not recover exec cell `1862`, so that run's pytest result was unavailable.
- No diagnosis or production edit has been made for `ticks_left()` yet.

## Current blocker and exact next step

- There is no external blocker. Cleanup must finish before diagnosis.
- Stop only PID `347748`, then re-inventory managed Banteng Java processes.
- After cleanup, begin the mandatory row authority gate for `audit_default_fg_ticks`: read the immutable plan, then establish Barn spec/current implementation and pinned Toast behavior/source, commit repo-local evidence, obtain a valid focused Banteng red, commit the smallest design, implement, and run the focused row/family/full Java gates before committing the kept slice.
- Never stage this notes file or unrelated `pyghidra_mcp_projects/` artifacts.

## Authority-gate progress after cleanup

- Managed Banteng PID `347748` was stopped; follow-up inventory showed no `moo.app.Banteng` Java process.
- The immutable control surface is `docs/reports/banteng-implementation-plan.md`; it was reread in full. Phase 8 and the mandatory per-slice authority gate remain active.
- Git ledger before editing: `master...origin/master [ahead 7]`, no tracked changes; only the existing untracked notes and `pyghidra_mcp_projects/` artifacts are present.
- Exact durable row: `../moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml`, `audit_default_fg_ticks`.
- The row deletes `$server_options.fg_ticks`, calls `load_server_options()`, then requires a new foreground task to observe `ticks_left() <= 60000 && ticks_left() > 59000`.
- A source search found no Banteng `ticks_left` or `ticksLeft` implementation. `BuiltinCatalog` contains nearby task builtins such as `suspend` but currently no `ticks_left` dispatch, matching the observed `E_VERBNF`.
- Pinned Toast identity was independently verified as `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- The exact managed Toast row passed: `1 passed, 11518 deselected in 3.57s`, exit code 0. The unrelated Toast PID 19 was not touched.
- Barn spec/implementation and Toast source traces are still in progress. No evidence record, test, design, or production edit has been made yet.

## Completed row authority gate

- Barn normative authority: `../barn/spec/tasks.md:7-17,24-38,141-166` and `../barn/spec/builtins/tasks.md:240-247`. They specify a 60,000 foreground default, zero-argument `ticks_left()`, and opcode-level charging before builtin dispatch.
- Barn current path: `builtins/limits.go` selects the 60,000 foreground limit; `scheduler/task_factory.go` and `task/task.go` place it on the task/context; `scheduler/task_runtime.go` transfers it to the VM; `vm/vm.go` owns the live remainder; `builtins/registry.go` and `builtins/system.go` register and return it.
- Pinned Toast path: `src/include/options.h` defines `DEFAULT_FG_TICKS 60000`; input/server tasks enter `run_interpreter(..., is_fg=1)`; `execute.cc` selects `server_int_option("fg_ticks", DEFAULT_FG_TICKS)`, clamps values below 100, decrements counted opcodes before dispatch, and `bf_ticks_left` returns live `ticks_remaining` as INT. `fg_ticks` is uncached, so the row's `load_server_options()` call is irrelevant to its default selection.
- Toast registers `ticks_left` with arity 0. The exact managed row passed against pinned Toast: `1 passed, 11518 deselected in 3.57s`.
- The durable conformance row came from moo-conformance commit `78a82923879931dcc90a88aca217e5048ab1e0e2` (`Add Toast oracle task scheduling audit tests`). No row correction is needed.
- Banteng has no tick state, tick charging, or `ticks_left` catalog case; the full-suite failure is a valid missing-semantic-owner discrepancy, not just an alias problem.
- Required evidence record `docs/reports/foreground-tick-budget-authority.md` was committed alone as `99963ce docs: freeze foreground tick budget`.
- No Java design, regression, or production edit exists yet. Exact next step: run the sole focused managed Banteng row and prove the expected `E_VERBNF` red, clean its managed process, then commit the smallest design before implementation.

## Focused red and frozen design

- At committed evidence base `99963ce`, the exact managed Banteng selector `task_scheduling_toast_oracle and audit_default_fg_ticks` collected 11,519 rows, selected one, and failed in 3.73 seconds with the expected `E_VERBNF` from `ticks_left()`.
- Managed Banteng PID `370840` on temp database `moo_conformance_y__kese_` was stopped; follow-up inventory was empty.
- The focused red is valid and matches the authority discrepancy.
- The frozen design has been appended to `docs/reports/foreground-tick-budget-authority.md` but is not yet staged or committed.
- Design: store 60,000 remaining foreground ticks in existing explicit `VmState`; have `MooVm` decrement only Banteng opcodes corresponding to Toast counted opcodes; pass the primitive live remainder through existing `BuiltinCatalog.invoke`; add zero-argument `ticks_left` directly to the catalog and its existing pure/task-local-read classification; thread the primitive through existing `call_function` recursion.
- The design explicitly forbids a new interface/context adapter/scheduler API and forbids a constant-return stub or one-tick-per-Java-instruction approximation.
- The focused JUnit regression will assert exact live values for two `ticks_left()` calls and `E_ARGS` for one argument.
- Background selection, server-option overrides, suspension resets, tick exhaustion, persistence, and `seconds_left` remain outside this exact row.
- Exact next step: stage only the report change, check the staged diff, commit the design, then delegate the bounded Java implementation to a Codex subagent and verify its test red before production edits.

## Java slice implementation and review state

- Design commit landed as `086d893 docs: design foreground tick accounting`.
- Codex programming subagent added the focused regression before production edits. RED: `gradlew.bat test --tests moo.vm.MooVmTest.exposesTheLiveForegroundTickRemainderAndRejectsArguments` failed with `Optional[E_VERBNF]` at the intended assertion.
- The same focused test is GREEN after implementation: `BUILD SUCCESSFUL in 9s`.
- Exact regression values are 59,999 for the first call and 59,997 for the second, reflecting the counted first call, first singleton-list formation, and second call. One supplied argument returns `E_ARGS`; catalog classification is `PURE`.
- Current tracked diff is limited to `BuiltinCatalog.java`, `MooVm.java`, `VmState.java`, and `MooVmTest.java`. Untracked notes/artifacts remain unstaged.
- `VmState` now owns a 60,000 remaining-tick field with package-private read/decrement methods.
- `MooVm` decrements before dispatch through an explicit counted-instruction predicate. It counts first `LIST_APPEND` only, `LIST_EXTEND`, assignments, property/index operations, calls, arithmetic/comparisons/unary operators, fork, conditionals, handler entry, iteration, and scatter. It does not use one tick per Java instruction.
- `BuiltinCatalog.invoke` and its existing `call_function` recursion receive the primitive remainder; `ticks_left` validates zero args and returns it; the existing effect classification includes it as `PURE`.
- Initial diff review found no new interface/class/context/scheduler surface and no out-of-scope background/option/timeout implementation.
- Remaining verification before acceptance: run the focused JUnit test independently, run a compilation/broader Java test gate, rebuild `installDist`, run the exact managed row, clean its process, then run the full task-scheduling family and full Java gate. The slice is not committed yet.

## Verification through family frontier

- Independent focused JUnit rerun with `--rerun-tasks` passed in 12 seconds with all three tasks executed.
- Full `moo.vm.MooVmTest` rerun with `--rerun-tasks` passed in 11 seconds with all three tasks executed.
- The immutable plan was reread in full after the substantial VM test pass. Phase 8 still requires the focused managed row, broader frontier, full Java gate, and kept-slice commit.
- `installDist` rebuilt successfully from the current patch.
- Exact managed Banteng row `task_scheduling_toast_oracle::audit_default_fg_ticks` passed: `1 passed, 11518 deselected in 3.61s`.
- Exact-row managed PID `340924` was stopped; follow-up inventory was empty.
- Substantial family fail-fast selector `task_scheduling_toast_oracle` selected 23 rows. The repaired foreground row passed, then the run stopped on the next distinct row `audit_default_bg_ticks`: expected 1, actual 0. Summary: `1 failed, 1 passed, 11496 deselected in 3.85s`.
- That next failure matches the frozen scope: child `VmState` still starts with the foreground 60,000 budget, so the background predicate requiring <=30,000 is false. It does not invalidate the kept foreground slice.
- Family managed PID `368756` was stopped. Cleanup re-inventory is still required immediately after this checkpoint.
- Remaining row39 work: verify no managed Java process, run full Java 25 `clean check installDist`, reread the immutable plan after it passes, run `git diff --check`, stage only the four intended Java/test files, commit the kept implementation, and then start `audit_default_bg_ticks` as the next sole target.

## Row 39 complete

- Cleanup inventory after the family frontier was empty.
- First full Java gate failed only because the new test needed Spotless formatting; compilation and tests otherwise passed. `gradlew.bat spotlessApply` changed only the intended test formatting.
- Rerun `gradlew.bat clean check installDist` passed in 21 seconds.
- The immutable plan was reread in full after the passing full Java gate.
- `git diff --check` and staged `git diff --cached --check` passed.
- Only `BuiltinCatalog.java`, `MooVm.java`, `VmState.java`, and `MooVmTest.java` were staged.
- Kept implementation commit: `08e3d18 feat: track foreground tick remainder` (100 insertions, 1 deletion across four files).
- Post-commit ledger: `master...origin/master [ahead 10]`, no tracked changes; only pre-existing untracked notes/artifacts remain.
- Row39 authority and design commits are `99963ce` and `086d893`; implementation is `08e3d18`.
- Exact next sole target is `task_scheduling_toast_oracle::audit_default_bg_ticks`, proven by the family frontier after the foreground row passed. Begin a new mandatory authority gate before any production edit; do not reuse the foreground evidence/design as a substitute for background task creation semantics.

## Row 40 authority state

- Sole target: `task_scheduling_toast_oracle::audit_default_bg_ticks`.
- Exact managed pinned Toast row passed: `1 passed, 11518 deselected in 3.83s`; source identity again verified as `aecc51e9449c6e7c95272f0f044b5ba38948459e`; unrelated Toast PID 19 untouched.
- Barn spec says fork children are background tasks with default 30,000 ticks and 3 seconds, overridden by `$server_options.bg_ticks`/`.bg_seconds`.
- Barn current path compiles `OP_FORK`, yields a captured child request, and `CreateForkedTask` chooses `GetTaskLimits(true)`, installs 30,000 in both Task and VM, then queues the delayed child. Barn snapshots the budget at fork creation.
- Pinned Toast path executes `OP_FORK`, captures a new `TASK_FORKED`, queues it, and only when due dispatches `do_forked_task -> do_task(..., is_fg=0) -> run_interpreter`, which selects uncached `server_int_option("bg_ticks", DEFAULT_BG_TICKS)` and clamps below 100. Toast selects the budget at child start.
- Barn/Toast timing disagreement is not observable in this default-only row because it removes `bg_ticks` and does not mutate it while the child waits. Both yield a 30,000 starting budget, and counted `ticks_left()` observes slightly less.
- Banteng current path creates every child in `MooRuntime.executeStored` with the same three-argument `VmState` constructor as the foreground root, so the child starts at 60,000. That exactly explains row40 actual 0 for the <=30,000 predicate.
- No row40 evidence record, design, test, or production edit exists yet. Next: directly verify cited Barn/Toast passages, write and commit a separate background-tick evidence record, then reproduce the focused Banteng red before design.

## Row 40 evidence and focused red

- Direct reads verified all cited Barn and pinned Toast paths, including Toast's child-start option selection and Barn's fork-creation snapshot timing.
- Separate required evidence record `docs/reports/background-tick-budget-authority.md` was committed alone as `13ee846 docs: freeze background fork budget`.
- At committed evidence base `13ee846`, the exact managed Banteng row selected one test and failed in 3.73 seconds with expected integer 1 and actual integer 0 in the fork-result predicate.
- This is the valid failure boundary: setup succeeded, `ticks_left()` exists, and the child ran; only its initial 60,000 budget is wrong.
- Managed server from that red is PID `127716`, temp database `moo_conformance_omjpihzp`, port 6128. It still needs to be stopped, followed by an empty re-inventory, before design.
- No Java design, regression, or production edit exists for row40 yet.
- Exact next step: stop PID `127716`, re-inventory, then freeze the smallest Java design that gives fork children the 30,000 compiled default without claiming Barn's wrong dynamic-option snapshot timing.

## Row 40 design and Java implementation

- Focused-red server PID `127716` was stopped and follow-up inventory was empty.
- Frozen design was appended to the evidence record and committed as `ea41153 docs: design background fork budget`.
- Codex programming subagent added the focused runtime regression first. RED: exact eval output expected `{1, 29999}` but received `{1, 59999}`; the fork/yield/property path otherwise succeeded.
- Production edits then stayed within the frozen owners: `VmState.java` gained one explicit primitive-tick constructor overload and existing constructors delegate with 60,000; `MooRuntime.java` gained a private 30,000 default and uses the explicit constructor only for fork children.
- No factory/helper/interface/context/task-kind/setter, server-option selection, queue change, builtin change, or suspension change was introduced.
- GREEN: same focused method passed, and independent rerun with `--rerun-tasks` passed in 11 seconds with all tasks executed.
- Full `moo.runtime.MooRuntimeTest` rerun passed in 18 seconds with all tasks executed.
- Current tracked diff is limited to `MooRuntime.java`, `VmState.java`, and `MooRuntimeTest.java`; `git diff --check` was clean at subagent handoff.
- The immutable plan was reread in full after the substantial runtime test pass. Row40 is not committed yet.
- Next: rebuild `installDist`, run exact managed row green, clean its server, run the task-scheduling family fail-fast frontier, clean its server, run full Java 25 gate, reread plan, and commit only the three intended files.
## Row 40 Java implementation subagent state (2026-07-15)

- The committed authority/design is `docs/reports/background-tick-budget-authority.md`; it freezes only the 30,000 compiled default for a fresh fork child.
- Before this slice, `git status --short --branch` showed no tracked changes and only the known untracked notes/artifacts.
- Current production state constructs both the root and fork children through the same foreground-default `VmState` path; `MooRuntime.java:1115-1117` is the exact child construction boundary.
- Added one focused `MooRuntimeTest` beside the fork/task-local regression. It creates `#0.audit_background_ticks`, forks a zero-delay child that writes `ticks_left()`, yields the parent once, expects returned and stored value `29999`, and deletes the property in `finally`.
- No production file has been edited. Current blocker: none. Next action is the exact focused JUnit red, which must expose the current near-59999 child value before production changes.
