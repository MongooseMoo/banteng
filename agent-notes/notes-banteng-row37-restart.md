# Banteng row 37 restart handoff

## Ledger and active target

- Row 36 WAIF caller-frame semantics is committed on `master` as `95764ba feat: preserve WAIF caller frame semantics`.
- The row-36 commit contains exactly eight tracked files. Post-commit status has no tracked changes; existing notes and `pyghidra_mcp_projects/` remain untracked and must not be staged.
- Row 36 final proofs: exact managed row 1 passed / 11,518 deselected in 3.53 seconds; family frontier 15 passed and stopped at the next move row in 29.84 seconds; Java 25 `clean check installDist` passed all 150 tests in 23 seconds; all disposable Banteng JVMs were stopped by exact PID and final inventories were empty.
- Active exact-convergence target is row 37, `audit_move_wizard_calls_accept_but_overrides_false`, in `../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:884`.

## Exact row and current observation

- The row creates `thing` and `dest`, installs executable `dest:accept` that records `args[1]` and returns false, invokes `move(thing, dest)` as Wizard, and expects `{accept_seen == thing, thing.location == dest}` to be `{1, 1}`.
- Current committed Banteng family run observes `[0, 1]`: movement already occurs, but destination `:accept` is not called. This is the exact first frontier after 15 kept rows.
- The durable row originated in conformance commit `81d1157 Add move and chparent trust Toast oracles`; that commit added 54 lines only to `gap_followups_toast_oracle.yaml`.

## Mandatory authority gate state

- No row-37 Banteng production edit, branch, staging, or commit has begun.
- `/root/row37_barn_move_authority` is reading the exact Barn normative spec and implementation path read-only.
- `/root/row37_toast_move_authority` is verifying pinned WSL Toast identity `aecc51e9449c6e7c95272f0f044b5ba38948459e`, tracing source semantics, and running the exact managed Toast row read-only.
- The main agent has read the exact durable row and its immediate next row only. It has not designed Java.
- Current blocker: complete all authority steps, resolve every observable disagreement with the managed Toast result, write and commit the repository-local row-37 evidence record before any production edit, then choose the smallest Java representation and prove a focused regression red.
- Ward is uninstalled and remains out of scope. The implementation plan is immutable and must not be edited.

## Completed row-37 authority trace

- Barn normative prose is incomplete: `spec/builtins/objects.md:316-340`, `spec/objects.md:347-370`, and `spec/errors.md:378-389` omit destination `accept`, its argument, false-result `E_NACC`, missing-verb behavior, and wizard override.
- Current Barn runtime agrees with the row. `builtins/objects_movement.go:11-69` always calls destination `accept(what)` before `Store.MoveObject` for non-`$nothing` destinations; non-`E_VERBNF` exceptions propagate; a false normal result raises `E_NACC` only for nonwizards. Wizard side effects remain and relocation proceeds. Current tests cover invalid references only.
- Pinned Toast source identity was freshly verified as `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Exact Toast owner: `src/objects.cc:117-231,1368-1370`; it validates, calls `where:accept(what)`, converts missing verb to false, rejects false only for nonwizard, revalidates, checks recursion, relocates, then handles hooks. `src/db_objects.cc:1190-1225,1276-1280` owns location mutation and wizard flag.
- Exact managed Toast row passed: 1 selected, 11,518 deselected, 3.62 seconds. No run process leaked; unrelated Toast PID 19 was untouched.
- Frozen row-only contract: wizard must call destination `accept` with moved object as sole argument before relocation; side effects persist; a normal false result is overridden and movement proceeds.
- Explicit exclusions: missing `accept`, nonwizard refusal, position, permission/control, recursive containment, revalidation, exit/enter hooks, last-move metadata, and other errors remain unauthorized for this slice.
- Main agent personally reread the cited Barn spec/runtime and Toast source owners.
- New repository-local evidence record `docs/reports/object-movement-authority.md` has been created but is not yet validated, staged, or committed. No Banteng production file has been inspected or edited for row 37.
- Current blocker: run `git diff --check`, review/stage only the evidence report, commit it separately, verify the ledger, then trace the current Banteng owner and choose the smallest row-only design.

## Evidence commit and initial Banteng owner trace

- `docs/reports/object-movement-authority.md` was validated and committed separately as `91d67ef docs: freeze wizard movement acceptance` (one file, 142 lines). Post-commit ledger has no tracked changes and is ahead of `origin/master` by one; all notes remain untracked.
- Current Banteng `BuiltinCatalog` dispatches `case "move"` to `move(arguments, world)` at line 353.
- `BuiltinCatalog.move` at lines 1436-1444 accepts exactly two object arguments and calls `world.move(...)` immediately, returning zero or `E_INVARG`. This explains `[0, 1]`: the move occurs with no destination verb activation.
- `BuiltinCatalog.Result` already carries explicit continuation/effect fields including `recycleTarget`, but no move-accept field. `MooVm.invokeBuiltin` already interprets `recycleTarget`, finds/compiles a hook, pushes a verb frame, and `routeReturn` completes the recycle after that frame returns.
- `VmState.Frame` currently has only `OptionalLong recycleTarget` for builtin-owned verb continuation metadata. No row-37 design has been chosen and no production file has been edited.
- Current design blocker: read the complete existing `Result` factories, `invokeBuiltin` recycle branch, `routeReturn`, error unwind, and `WorldTxn.move` exact semantics. Decide whether the row can reuse those concrete owners with direct move target/destination fields and one pushed `accept` frame, without a new interface/helper/request/dispatcher. Then record the smallest design in the committed evidence report before adding a focused red regression.

## Frozen Banteng design and focused-red gate

- The evidence report was extended with the current Banteng trace and smallest row-only design, then committed separately as `f9588c5 docs: trace Banteng movement continuation`. The tracked worktree was clean before the regression edit; branch was ahead of origin by two evidence commits.
- `WorldTxn.move` at lines 579-598 already performs the required physical relocation and reciprocal contents changes; no new world method/mutation path is authorized.
- Existing recycle continuation proves the concrete shape: `BuiltinCatalog.Result` carries a target, `MooVm.invokeBuiltin` pushes a normal verb frame, `MooVm.routeReturn` performs the mutation on normal return, and error unwind skips the normal-return action.
- Frozen smallest implementation: add two optional long fields to the existing `Result` and `Frame` for moved object/destination. Only a wizard catalog call returns that continuation. VM looks up destination `accept`; missing verb preserves the current immediate move; present verb runs with destination `this`, moved object sole arg, inherited player, original receiver caller, verb owner programmer, and direct destination vloc. Normal return ignores value and performs `WorldTxn.move`; errors propagate without moving. Nonwizard path remains unchanged. No request record/helper/interface/adapter/alternate dispatcher.
- `pushVerbFrame` has exactly three production call sites plus its definition, so direct signature extension is bounded. `BuiltinCatalog` has 13 `new Result(...)` factory constructions; the existing private constructor can fill new move fields empty, while only the task-local canonical construction and new move factory require explicit additions.
- `/root/row37_move_regression` is editing only `src/test/java/moo/runtime/MooRuntimeTest.java` and must prove exact red `{accept_seen=0, moved=1}` before production. No production edit has begun.
- Current blocker: wait for and verify that exact red regression; reject it if setup/wire fails. Only after valid red may the production continuation slice be edited.
## 2026-07-15 restarted-process continuation

- Confirmed this is a new Codex process and resumed from the row-37 handoff rather than restarting or changing targets.
- Verified the interrupted `BuiltinCatalog.java` patch had landed before making any further edit.
- Row 37 remains the sole active exact-convergence slice: `audit_move_wizard_calls_accept_but_overrides_false`.
- The committed authority/design records remain `91d67ef` and `f9588c5`; the valid focused regression remains `MooRuntimeTest.invokesDestinationAcceptWhenMoveReturnsFalse`.
- Wired the frozen design through only the existing `BuiltinCatalog.Result`, `VmState.Frame`, and `MooVm` continuation machinery. No helper, interface, adapter, sender, alternate dispatcher, or new world mutation method was introduced.
- Wizard `move()` now defers relocation, invokes destination `accept` with the moved object as the sole argument, ignores its false normal result, and calls the existing `WorldTxn.move` only after normal hook return. Non-Wizard movement remains on its existing immediate path. Hook errors unwind without relocation.
- Exact Java 25 focused regression passed: `gradlew test --tests "moo.runtime.MooRuntimeTest.invokesDestinationAcceptWhenMoveReturnsFalse"` (`BUILD SUCCESSFUL`, 2026-07-15).
- Current procedural blocker: because a substantial targeted test passed, the immutable plan at `docs/reports/banteng-implementation-plan.md` must be reread in full before formatting, broader gates, managed conformance, or commit.
- Next step: read the immutable plan fully, then execute its next applicable row-37 verification action without editing the plan.
- Keep this notes file and all `notes-*.md` / `pyghidra_mcp_projects/` artifacts untracked and unstaged.

## 2026-07-15 row-37 verification checkpoint

- Reread `docs/reports/banteng-implementation-plan.md` in full after the first focused pass; the plan was not edited.
- Applied the pinned formatter and reran the exact focused Java 25 regression successfully.
- Verified the Git ledger before broader testing: exactly four tracked row-37 files are modified (`BuiltinCatalog.java`, `MooVm.java`, `VmState.java`, `MooRuntimeTest.java`); `git diff --check` is clean; branch was `master...origin/master [ahead 2]`; notes and `pyghidra_mcp_projects/` remain untracked.
- Rebuilt the managed distribution with Java 25 via `gradlew clean installDist`; it succeeded.
- Exact managed Banteng row passed: `audit_move_wizard_calls_accept_but_overrides_false`, 1 passed / 11,518 deselected in 3.56s.
- Cleaned the exact leftover managed Banteng process PID 368088 tied to `moo_conformance_ia5nfaqs/Test.db`; re-inventory was empty.
- Substantial fail-fast `gap_followups_toast_oracle` category reached the intended frontier: 16 passed, then `audit_chparent_nonwizard_requires_fertile_parent` failed in 31.24s because Banteng returned `E_TYPE` where the row expects `E_PERM`.
- This category result keeps row 37: its exact row and the ordered 16-row prefix pass. The `chparent` discrepancy is the next row/family slice and must not be mixed into the row-37 implementation commit.
- Immediate required action after this checkpoint: inventory and clean only the managed Banteng process tied to the just-completed `moo_conformance_aiayvivp` temporary database, then reread the immutable plan because a substantial targeted run passed its row-37 prefix, run the full Java gate, update the committed evidence report with receipts, review/stage only the four row-37 implementation files plus the committed evidence report, and commit row 37 before beginning `chparent` authority work.
- Current blocker: none for completing row 37. The next convergence frontier is the separately owned `chparent` semantic slice (`E_TYPE` vs `E_PERM`).

## 2026-07-15 row-37 committed state

- Full Java 25 `gradlew clean check installDist` passed in 17 seconds, including formatting checks and the full test suite.
- Reread the immutable implementation plan in full after that passing gate; did not edit it.
- Added only observed implementation receipts to the already-committed `docs/reports/object-movement-authority.md` evidence record.
- Reviewed the full tracked diff and staged exactly five paths: the movement evidence report, `BuiltinCatalog.java`, `MooVm.java`, `VmState.java`, and `MooRuntimeTest.java`. Staged `git diff --check` passed.
- Committed the kept row-37 implementation as `0bec65f feat: honor wizard movement acceptance hooks` (5 files, 181 insertions, 9 deletions).
- Post-commit status is clean for tracked files. `master...origin/master [ahead 3]`; only intentionally untracked notes and `pyghidra_mcp_projects/` remain.
- Row 37 is complete. The exact next plan-controlled slice is `audit_chparent_nonwizard_requires_fertile_parent`, currently returning `E_TYPE` instead of the Toast-proven row expectation `E_PERM`.
- Current blocker: none. Before any row-38 production edit, execute the complete authority gate: Barn spec, Barn implementation, Toast implementation, repository-local evidence record, current Toast identity and exact managed oracle row, frozen contract/design, then a valid focused red regression.
