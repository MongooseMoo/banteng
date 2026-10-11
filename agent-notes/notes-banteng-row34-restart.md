# Banteng row 34 restart state

## Target

- Active exact-convergence row: `audit_command_dispatch_ignores_execute_flag` at `gap_followups_toast_oracle.yaml:815-829`.
- Expected behavior: an ordinary input command executes a matching player verb whose permissions are `d` and omit `x`, producing `EXECUTED`.
- Row provenance: moo-conformance-tests commit `4c4423799c4505f06f52a587568c7013e77b9c76`.

## Authority findings

- Barn normative `spec/objects.md:250-256` incorrectly said command dispatch checks execute permission. General direct-call documentation is applicable only to programmed calls.
- Current Barn source intentionally separates modes: parsed command lookup matches name and argument specs without checking permissions; programmed `obj:verb()` lookup requires `VerbExecute`.
- Pinned Toast source identity `aecc51e9449c6e7c95272f0f044b5ba38948459e` does the same. `db_find_command_verb` ignores `VF_EXEC`; callable lookup requires it.
- Exact pinned WSL Toast row passed: 1 selected, 11,504 deselected, 3.67 seconds. Post-run WSL inventory contained only unrelated July 13 PID 19 `/tmp/td.db`, untouched.
- The required Barn normative correction was committed separately as `6c031a7 docs: correct command verb execute semantics`; only `spec/objects.md` was staged. Existing tracked `notes-barn-performance-campaign.md` and all untracked Barn files were untouched.

## Banteng diagnosis

- `MooRuntime.executeLine` obtains player/room command candidates through `WorldTxn.verb(object, name)`.
- `WorldTxn.verb(long,String)` filters matches on permission bit 4, so the row's bit-8-only verb is absent and the runtime emits `I couldn't understand that.`.
- Direct VM calls and `pass()` also use this lookup. Existing `MooVmTest.verbFramesSkipNonExecutableMatchesPopulateLocalsAndScopeProgrammer` proves direct calls must skip a nearer non-executable verb, so globally deleting the filter would be wrong.
- No Banteng production design or test has been frozen or edited yet. The authority record section was appended to tracked `docs/reports/splice-computed-access-authority.md` and includes the still-pending design point.

## What worked and what did not

- Worked: exact durable-row read, spec/Barn/Toast end-to-end source traces, live pinned oracle proof, WSL process inventory, and isolated Barn spec commit.
- Existing Banteng family run already provided the valid red: first 12 rows pass, then this row returns fallback text instead of `EXECUTED`.
- No attempted production fix exists yet; therefore no rejected production approach or active code blocker exists.

## Next exact step

1. Verify Barn post-commit tracked state, preserving the unrelated notes modification.
2. Freeze the smallest Java representation in the Banteng authority record. Likely shape from current evidence: retain executable-only behavior for the existing two-argument named lookup and permit the normal player/room command calls to request lookup without the executable filter. Do not globally remove the filter and do not create an interface/adapter/helper owner.
3. Add a focused `MooRuntimeTest` regression and prove it red on the exact fallback boundary before production edits.
4. Implement only the frozen slice, prove focused green, run exact managed row and substantial family, clean up exact Banteng PIDs, reread the immutable plan, run the full Gradle gate, record receipts, and commit.

## Hygiene

- Never stage this file, other `notes-*.md`, subagent checkpoint notes, or `pyghidra_mcp_projects/`.
- Never touch Ward.

## Focused-regression checkpoint

- Froze the Java design in the tracked authority record: keep `WorldTxn` as the sole named lookup owner, retain executable-only behavior in the existing two-argument call, add one `requireExecutable` mode, and use the non-filtering mode only for normal player/room command candidates.
- Added `MooRuntimeTest.dispatchesMatchingCommandVerbWithoutExecutePermission`, but the first red was invalid: MOO-level setup called named `set_verb_code` on a non-`x` verb, and that separate current lookup limitation meant the verb was not installed with code. The test failed with `NoSuchElementException` before command dispatch.
- Corrected only test setup to use existing indexed `WorldTxn.addVerb` and `setVerbCode` operations so the regression does not absorb named builtin semantics.
- The second run is also not yet a valid behavioral red: it failed at `assertTrue` line 1392 because indexed `setVerbCode` returned false. This likely means the test passed the wrong index convention returned by `addVerb`; that exact existing API contract must be inspected before another test edit.
- No Banteng production source has been edited. Current tracked modifications are the row-34 authority section and the focused test only.
- Current blocker is test setup correctness, not production behavior. Next exact step: read the existing `WorldTxn.addVerb`/`setVerbCode` implementations and their tests, correct the regression to their literal index contract, and rerun until the failure is specifically expected `EXECUTED` versus actual fallback text.

## Implemented-slice checkpoint

- Inspected the indexed API: `WorldTxn.addVerb` returns the new one-based verb number, while `setVerbCode` accepts a zero-based index. Corrected the focused test to subtract one.
- Valid focused red then occurred on the intended boundary: expected `[EXECUTED]`, actual `[I couldn't understand that.]`.
- Implemented only the frozen slice: `WorldTxn.verb(object, name)` delegates to the same owner's new `requireExecutable` mode with `true`; the existing scan accepts matches without bit 4 only when that flag is false. `MooRuntime` passes false only for normal player and room command candidate lookups.
- Focused regression passed in 8 seconds.
- Reread all 407 lines of the immutable plan in two chunks after the focused green. The plan was not edited.
- `spotlessApply` passed in 1 second; `installDist` passed in 4 seconds.
- First exact managed Banteng attempt was invalid and does not count as a semantic gate. The server child exited before listening because the launcher inherited Java 17: class file version 69 was unsupported by runtime maximum 61. Temp directory was `moo_conformance_xnk1cagp`; no semantic row ran.
- Current blocker is only launcher environment. Next exact step: inventory managed Banteng Java processes for the failed temp path, then rerun the same exact managed row with `JAVA_HOME=C:\Users\Q\scoop\apps\openjdk\current` and Java 25 prepended to `PATH` in that command's environment.

## Verification checkpoint

- The invalid Java 17 launcher attempt left no managed Banteng Java process.
- Reran the exact managed row with Java 25 explicitly exported in the same command. It passed: 1 selected, 11,504 deselected, 3.43 seconds.
- Exact run used temp database `moo_conformance_wjvs3_0e/Test.db`, PID 387192. Stopped that exact PID and verified the managed Banteng inventory was empty.
- Substantial `gap_followups_toast_oracle` family passed the first 13 rows and stopped at the next independent row, `audit_task_local_not_inherited_by_fork`: expected success/value `{}`, actual `E_VERBNF`. Receipt: 1 failed, 13 passed, 11,488 deselected in 29.56 seconds.
- Family run used temp database `moo_conformance_ff7ypbbf/Test.db`, PID 102420. Stopped that exact PID and verified the managed Banteng inventory was empty.
- Reread all 407 plan lines in two chunks after the substantial family. Plan remains unedited.
- Final `gradlew clean check installDist` passed in 15 seconds, including all tests, formatting checks, and distribution. This suite now contains 147 JUnit tests (the console does not print the test count).
- Row 34 has no known blocker. Next exact step: append these final receipts and the invalid-test/setup correction to the tracked authority record, run `git diff --check`, inspect the exact tracked diff/status, stage only the row-34 files, commit, and then begin the next row's mandatory authority gate.

## Commit checkpoint

- Appended all final receipts to the tracked authority record.
- `git diff --check` passed; inspected the exact four-file diff.
- Staged only `docs/reports/splice-computed-access-authority.md`, `src/main/java/moo/runtime/MooRuntime.java`, `src/main/java/moo/world/WorldTxn.java`, and `src/test/java/moo/runtime/MooRuntimeTest.java`.
- Committed Banteng row 34 as `ec0321a fix: dispatch command verbs without execute permission`.
- Banteng is now `master...origin/master [ahead 24]` with no tracked modifications. Only the known notes and `pyghidra_mcp_projects/` remain untracked.
- Barn's separate normative correction remains committed as `6c031a7` with the user's unrelated `notes-barn-performance-campaign.md` change left unstaged.
- Active next target is `audit_task_local_not_inherited_by_fork`, the first family failure after 13 passing rows. Its current Banteng result is `E_VERBNF`; no row-35 authority gate or design has begun yet.
