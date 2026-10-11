# Banteng row 35 restart state

## Target

- Active exact-convergence row: `audit_task_local_not_inherited_by_fork` at `gap_followups_toast_oracle.yaml:831-852`.
- Row provenance: moo-conformance-tests commit `4c4423799c4505f06f52a587568c7013e77b9c76`.
- Parent stores `{"parent", 7}`; zero-delay fork child suspends, writes its own `task_local()` into a property; parent suspends twice and expects the property to be `{}`.

## Authority findings

- Barn authoritative `spec/tasks.md:347-367` says new foreground/forked tasks start with an empty map, a task preserves its value through suspension, and fork children do not inherit parent task-local state.
- Barn unaudited `spec/builtins/tasks.md:358-389` is stale only on unset default: it says integer 0 instead of empty map.
- Current Barn source agrees with `spec/tasks.md`: every Task initializes task-local to an empty map; fork snapshots omit it; `CreateForkedTask` explicitly installs a fresh empty map. Zero-delay child/parent suspension ordering makes the child write visible before parent return.
- Pinned Toast source identity `aecc51e9449c6e7c95272f0f044b5ba38948459e` agrees. Fork queue records omit `current_local`; TASK_FORKED sets a new map. Suspended VM state captures/restores the current task's own local value.
- Exact Toast source paths and ordering were appended to the tracked Banteng authority record under `Forked task-local state`.

## Banteng diagnosis

- Current committed base is `ec0321a`; tracked state was clean before the authority-record edit.
- First actual row failure is `BuiltinCatalog.invoke` default `E_VERBNF` on `set_task_local`; execution never reaches the fork.
- Neither `set_task_local` nor `task_local` exists in builtin dispatch or effect classification, and `VmState` has no task-local state.
- Existing parser/compiler fork, VM fork boundary, runtime fresh child creation, zero-delay ordering, suspend/resume, and property assignment paths exist. Fork captures ordinary frame locals and programmer only; those locals must not substitute for task-local state.
- No Java design, regression, or production edit is frozen yet.

## Current blocker and next step

- Mandatory authority step 5 remains: run the exact row against the managed pinned WSL Toast oracle, inventory processes, then correct and separately commit the stale Barn builtin-spec sentence before Java design.
- After that, freeze the smallest idiomatic representation for one per-`VmState` arbitrary MOO value initialized to empty map, preserved by same-state suspension, omitted from fresh fork child construction, and synchronously readable/writable through the two Wizard-only builtins. The exact return value of `set_task_local` and permission/error contracts must come from source/oracle, not assumption.
- Focused regression must cover the complete runtime/fork row, not merely the first missing builtin.

## Hygiene

- Never stage this file, other `notes-*.md`, subagent checkpoint notes, or `pyghidra_mcp_projects/`.
- Never touch Ward.

## Oracle and spec-correction checkpoint

- Exact managed pinned WSL Toast row passed: 1 selected, 11,504 deselected, 3.80 seconds.
- Post-run WSL inventory contained only unrelated July 13 PID 19 `/tmp/td.db`, untouched.
- Corrected only the stale unset-default sentence in Barn `spec/builtins/tasks.md`, adding the proven fresh-empty-map fork child and same-task suspension preservation language.
- Barn `git diff --check` passed; staged only `spec/builtins/tasks.md`; the user's tracked `notes-barn-performance-campaign.md` change remained unstaged and all untracked Barn artifacts were untouched.
- Committed the correction separately as `b06e048 docs: correct task local default`.
- Barn is now `master...origin/master [ahead 4]` with only the pre-existing tracked notes change.
- Mandatory authority steps 1-5 are complete. No active blocker remains before freezing the smallest Java representation and adding a complete runtime/fork regression.

## Frozen design checkpoint

- Verified exact source contracts: both builtins are Wizard-only; `task_local()` requires zero args and returns the current arbitrary MOO value; `set_task_local(value)` requires one arg, replaces immediately, and returns no value/integer zero. Arity errors are `E_ARGS`; non-Wizard is `E_PERM`.
- Froze the smallest representation in the tracked authority record: one `MooValue` field directly on `VmState`, initialized to `MapValue(Map.of())`; package-owned getter/setter only.
- `MooVm` passes the current value into existing `BuiltinCatalog.invoke` and applies an optional replacement carried by the existing `BuiltinCatalog.Result` after successful validation. This avoids a builtin-to-VM package cycle and avoids callbacks/interfaces/helpers.
- `task_local` remains catalog-owned and is classified with pure task-state reads; `set_task_local` is catalog-owned and classified with immediate task-state effects like `set_task_perms`.
- Same `VmState` suspension preserves the field. Existing fork runtime constructs a distinct child `VmState` from program/ordinary locals/programmer only, so the child gets the empty-map initializer without copying task-local state or changing scheduling.
- No task-local wrapper, keyed store, interface, callback, helper class, fork field, runtime map, or world record is authorized.
- Next exact step: add one complete logged-in Wizard runtime regression using the durable row body, prove current `E_VERBNF` red, then implement only this frozen slice.

## TDD implementation checkpoint

- Added `MooRuntimeTest.startsForkedTaskWithEmptyTaskLocalState` using the durable row body through logged-in Wizard runtime, with successful `[]` output plus stored-property `[]` assertions and cleanup.
- Focused regression produced the valid red in 6 seconds: expected prefix/`[]`/suffix, actual prefix/`{2, {E_VERBNF}}`/suffix. This matches the first missing `set_task_local` builtin and occurs before fork execution.
- Implemented only the frozen three-owner path:
  - `VmState` now owns one `MooValue taskLocal` initialized to an empty `MapValue`, with package-owned getter/setter.
  - `BuiltinCatalog.invoke` receives the current value, implements exact arity/Wizard validation for `task_local` and `set_task_local`, classifies them alongside `task_perms`/`set_task_perms`, and carries an optional replacement on the existing `Result` record. Existing Result factories use a delegating constructor so unrelated result paths are unchanged.
  - `MooVm` passes the current value into the catalog and applies a successful replacement before continuing bytecode.
- `MooRuntime`, `ForkRequest`, scheduler ordering, world state, and persistence were not edited. A fresh fork child still gets only the new `VmState` empty-map initializer; suspension resumes the same state.
- Implementation has not yet been compiled or tested. Current next step is the focused regression; any compile failure must be treated as implementation feedback, not a semantic green/red receipt.

## Focused-green checkpoint

- First focused implementation run found one compile-only omission: existing `call_function` recursively invokes the catalog and needed the same current task-local argument. Updated its existing signature/call; this was plumbing required by the frozen catalog contract, not a semantic test result.
- Next focused run reached the correct property value but the test expected raw `[]`; the fixture eval wire contract returns success as `{1, value}`. Corrected only the test expectation to `{1, []}` while retaining the stored-property `[]` assertion.
- Focused runtime regression then passed in 5 seconds.
- Reread all 407 lines of the immutable plan in two chunks after the focused green; the plan was not edited.
- `spotlessApply` passed in 1 second. `installDist` passed in 6 seconds.
- Current next step is the exact managed Banteng row with Java 25 explicitly exported in the same command, followed by exact PID cleanup, substantial family gate, plan reread, and full Gradle gate.

## Managed verification checkpoint

- Exact managed Banteng row passed: 1 selected, 11,504 deselected, 3.62 seconds.
- Exact run used `moo_conformance_fi_ajv29/Test.db`, PID 339684. Stopped that exact PID and verified managed Banteng inventory empty.
- Substantial `gap_followups_toast_oracle` family passed its first 14 rows and stopped at the next independent `audit_waif_callers_preserve_this_and_verb_location` row with `E_VERBNF`: 1 failed, 14 passed, 11,488 deselected in 29.86 seconds.
- Family run used `moo_conformance_b3hfmlme/Test.db`, PID 4880. Stopped that exact PID and verified managed Banteng inventory empty.
- Reread all 407 plan lines after the family run.
- Final `gradlew clean check installDist` passed in 15 seconds. The test report count still needs reading before recording a numeric count in tracked evidence.
- Mandatory post-full-suite plan reread is currently incomplete: only lines 1-240 of 407 were read before this checkpoint. Next exact action after notes is lines 241-407; do not treat the reread as complete before that.
- Row 35 has no known blocker. After completing the plan reread: record final receipts, `git diff --check`, inspect exact diff/status, stage only the row-35 tracked files, and commit before row 36.
