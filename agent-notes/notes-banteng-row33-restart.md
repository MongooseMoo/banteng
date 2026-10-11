# Banteng row 33 restart notes

## Ledger and active target

- Row 32 is committed on `master` as `4f40393 feat: round trip intrinsic command options`.
- Post-commit tracked state was clean; branch was ahead of origin by 22. Known notes and `pyghidra_mcp_projects/` remain untracked and must never be staged.
- Active exact-convergence target remains `gap_followups_toast_oracle`, row 33 `audit_intrinsic_command_table_rejects_unknown` at `../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:804-813`.
- Exact row: Wizard calls `set_connection_option(player, "intrinsic-commands", {"PREFIX", "NOT_A_TOAST_INTRINSIC"})`; expected `E_INVARG`; cleanup restores with integer 1.

## Authority and frozen design

- Reread the exact durable row.
- Added tracked evidence section `Intrinsic command table unknown-name rejection` to `docs/reports/splice-computed-access-authority.md`.
- Normative Barn network spec remains silent on member validation.
- Current Barn `builtins/network.go:1283-1305` validates every LIST element as an exact string member before its store and returns `E_INVARG` on unknown members, leaving state unchanged.
- Pinned Toast `tasks.cc:263-345` accumulates local `newflags`, rejects non-string or unresolved `icmd_index` members, and assigns `tq->icmds` only after the whole list validates. `TASK_CO_TABLE` and `server.cc:2974-2995` turn that failure into `E_INVARG` atomically.
- Committed Banteng `4f40393` intentionally checked only element type and stored unknown strings, confirmed by the prior family failure: success value 0 instead of `E_INVARG`.
- Exact managed pinned Toast row passed at identity `aecc51e9449c6e7c95272f0f044b5ba38948459e`: 1 selected, 11,504 deselected, in 3.53 seconds.
- Post-Toast WSL inventory found only unrelated July 13 PID 19 for `/tmp/td.db`; left untouched.
- Appended live receipt and frozen design to tracked evidence.
- Frozen design: one inline condition in the existing `BuiltinCatalog.setConnectionOption` LIST loop requiring decoded string to be one of `.program`, `PREFIX`, `SUFFIX`, `OUTPUTPREFIX`, `OUTPUTSUFFIX`. Existing store remains after loop, so rejection is atomic. No new constant, collection, helper, interface, state, or rollback path. Case variants, `.program` abbreviations, duplicates, non-string elements, and other value kinds remain outside row 33.

## TDD state

- Added separate focused regression `MooVmTest.rejectsUnknownIntrinsicCommandWithoutChangingTable`.
- It opens connection #-47 attached to Wizard #3, executes the exact invalid setter, requires `ERRORED`/`E_INVARG`, and verifies the canonical default list remains unchanged.
- The new regression has not yet been run. No row 33 production edit exists yet.
- Current blocker: none. Immediate next step is to run only this regression and prove current Banteng returns success (valid red), then add the one inline membership condition and rerun green.

## Row 33 implementation and managed gates

- Focused regression produced the valid red: expected `ERRORED`, actual `RETURNED` at `MooVmTest.java:863`, exactly matching current success-with-zero behavior.
- Added only one inline condition in the existing `BuiltinCatalog.setConnectionOption` LIST loop. It rejects decoded names outside `.program`, `PREFIX`, `SUFFIX`, `OUTPUTPREFIX`, and `OUTPUTSUFFIX` with existing `E_INVARG` before the existing store.
- Focused regression passed in 4 seconds, including verification that the canonical table stayed unchanged.
- Reread the complete immutable implementation plan after the focused green.
- `gradlew spotlessApply` passed in under 1 second.
- `gradlew installDist` passed in 1 second.
- Exact managed Banteng row `audit_intrinsic_command_table_rejects_unknown` passed: 1 selected, 11,504 deselected, in 3.52 seconds.
- Exact-row process was PID 389852, temp DB `moo_conformance_a03x69m4/Test.db`, port 58851. Stopped only PID 389852 and confirmed managed Banteng inventory empty.
- Substantial `gap_followups_toast_oracle` run advanced the retained prefix to 12 passing rows, then failed independently at `audit_command_dispatch_ignores_execute_flag`: expected output `EXECUTED`, actual `I couldn't understand that.` Receipt: 12 passed, 1 failed, 11,488 deselected in 29.28 seconds.
- The family process has not yet been inventoried or stopped after this checkpoint.
- Current blocker: none. Immediate next step is to inventory and stop only the family run's exact Banteng PID, prove inventory empty, reread the plan, run the clean full gate, record receipts, inspect/stage the exact row 33 diff, and commit before row 34.

## Restart continuation checkpoint

- Codex restarted into a different process; durable row-33 state was recovered.
- Reread the entire immutable `docs/reports/banteng-implementation-plan.md` in two non-truncated chunks (407 lines). The plan was not edited.
- Appended the final row-33 receipts to the tracked authority record: valid focused red (success instead of `E_INVARG`), focused green in 4 seconds with unchanged-table assertion, exact managed Banteng pass in 3.52 seconds, exact PID 389852 cleanup, 12-row family prefix pass followed by the independent row-34 execute-flag failure, family PID 357360 cleanup, and full 146-test `clean check installDist` pass in 15 seconds.
- `git diff --check` passed.
- Inspected the exact row-33 diff. It contains only the required authority section, one inline five-name membership check in `BuiltinCatalog`, and one focused VM regression. No helper, interface, adapter, state owner, or unrelated production surface was added.
- Current slice is ready for status verification, narrow staging of exactly the three tracked row-33 files, and commit. No row-33 blocker is known.
- The next conformance discrepancy after committing row 33 is `audit_command_dispatch_ignores_execute_flag`: the first 12 family rows pass, then Banteng returns `I couldn't understand that.` instead of `EXECUTED`.
- Never stage this notes file, other `notes-*.md`, or `pyghidra_mcp_projects/`.
