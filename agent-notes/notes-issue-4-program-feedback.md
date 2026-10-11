# Issue #4 program feedback handoff

## Current state

- Assigned worktree: `C:\Users\Q\code\banteng-agent-issue-4`
- Branch: `agent/issue-4-program-feedback`
- Base/HEAD: `d50652d1ff4b643a759812e4cd37e4a5ca85b1f9`
- Worktree was clean at initial inspection.
- No push, PR, comment, or tracker mutation is authorized.

## Issue contract

Issue #4 requires `.program` feedback matching ToastStunt: per-line `Line N: <diagnostic>` parse errors, `Verb programmed.` on success, `Verb not programmed.` after failure, and proper malformed/invalid/undefined verb-descriptor errors. Existing swallowed `IllegalArgumentException` and `NumberFormatException` paths must be removed in favor of surfaced compile results.

## Authority observations

- Controlling plan: `docs/reports/banteng-implementation-plan.md`.
- Supported-profile authority: `docs/reports/supported-conformance-profiles.md`.
- Semantic work is read-before-implement gated by current Barn spec/implementation, Toast implementation, and managed WSL Toast behavior.
- Managed Toast binary is `/root/src/toaststunt/build-release/moo`; exact diagnostic strings must be captured from the pinned WSL oracle.
- `docs/reports/supported-conformance-profiles.md` confirms stock Test.db and ToastCore are the selected targets; a focused local test is diagnostic only.
- `docs/reports/toast-oracle-identity-2026-07-14.md` requires slice-local Barn-spec, Barn-code, Toast-code, and managed-oracle evidence.
- The Windows Barn checkout exists at `C:\Users\Q\code\barn`, including `spec` and `plans`; the older `reports/toast-oracle-wsl.md` path is absent as documented.
- The initial WSL existence check did not find `/root/src/toaststunt/src/tasks.cc` or `/root/src/toaststunt/build-release/moo` in the command's default distribution/user context. The exact Debian/root context and current checkout layout must be checked before oracle use.
- Rechecking explicitly with `wsl.exe -d Debian -u root` confirmed Toast source HEAD `aecc51e9449c6e7c95272f0f044b5ba38948459e` and an executable `/root/src/toaststunt/build-release/moo`.
- Pinned `src/tasks.cc` defines the full visible protocol: descriptor failures (`You must specify a verb; use the format object:verb.`, object-match messages, `That object does not have that verb definition.`, `Permission denied.`); a successful start message `Now programming <object-name>:<verb-names>.  Use "." to end.`; each parser diagnostic via `notify`; then `<N> error(s).` and `Verb programmed.` or `Verb not programmed.`.
- Toast also detects object/verb disappearance at termination, but the current Banteng runtime uses one locked world transaction during synchronous ingress, so concurrent disappearance is not evidently reachable in this slice.
- Current Barn code is not authoritative over Toast and is incomplete here: it emits diagnostics but omits the Toast start, error-count, and completion lines. Its compiler diagnostic formatter does preserve Toast's `Line %d:  %s` two-space shape.

## Code observations

- `src/main/java/moo/runtime/MooRuntime.java` lines 459-504 own the current protocol.
- On `.` termination it compiles, mutates the verb, catches every `IllegalArgumentException`, clears programming state, and returns no output.
- `.program` descriptor parsing accepts only one `#object:verb` token; malformed numbers, missing objects, and missing verbs all return no output.
- `MooParser.ParseException` already carries line and column information, but the runtime does not expose it.
- Focused connection-level coverage belongs in `src/test/java/moo/runtime/MooRuntimeTest.java` using `openConnection` and `executeLine`.

## Oracle transcript

The pinned profile-verifying wrapper was run against a disposable copy of Test.db. The observed command/output pairs were:

- `.program` -> `Usage:  .program object:verb`
- `.program #999999:foo` -> `I don't see "#999999" here.`
- `.program #0:does_not_exist` -> `That object does not have that verb definition.`
- `.program #0:do_login_command` -> `Now programming System Object:do_login_command.  Use "." to end.`
- invalid body `return (` then `.` -> `Line 2:  syntax error`, `1 error(s).`, `Verb not programmed.`
- valid body `return 0;` then `.` -> `0 error(s).`, `Verb programmed.`

The owned disposable directory `/tmp/banteng-program-oracle.iIp2SK` was explicitly validated, stopped, and removed after capture.

## TDD state

- Added focused tests only in `MooCompilerTest` and `MooRuntimeTest`.
- RED command: `JAVA_HOME=/opt/java/25 ./gradlew --no-daemon test --tests moo.bytecode.MooCompilerTest.returnsToastFormattedSourceDiagnosticsWithoutThrowing --tests moo.runtime.MooRuntimeTest.dotProgramReportsToastFeedbackAndOnlyInstallsValidSource --console=plain`
- RED result: `compileTestJava` failed because `MooCompiler.CompilationResult`, `MooCompiler.Diagnostic`, and `compileResult(String)` do not exist. Production files were untouched before this run.
- Initial production draft added those types and runtime feedback, but the first intended GREEN run failed in `compileJava`: Error Prone reported `[SelfAssignment] Variable assigned to itself` in the compact `CompilationResult` constructor. The raw run ended `BUILD FAILED`; no GREEN claim is valid.
- The draft is also incomplete against the literal issue contract: it catches only one parser exception and resolves only numeric `#id` descriptors.
- Extended tests were added before the next implementation work: multiple recoverable `^` diagnostics, malformed numeric text, and `me`/`here`/`$server_options` target resolution.
- Expanded RED command ran the two compiler-result tests and the runtime protocol test. Production compiled; `returnsEveryRecoverableSourceDiagnosticInProgramOrder()` failed at `MooCompilerTest.java:35`, and `dotProgramReportsToastFeedbackAndOnlyInstallsValidSource()` failed at `MooRuntimeTest.java:472`. Raw Gradle summary: `3 tests completed, 2 failed`, `BUILD FAILED in 5m 10s`.
- The run was slow but not abandoned: the owned wrapper PID explicitly referenced the issue-4 worktree and its child daemon remained CPU-active; a separate older Gradle daemon was observed and left untouched.
- Focused GREEN command reran the same three expanded tests after implementation. Raw terminal result: `> Task :test`, `BUILD SUCCESSFUL in 2m 51s`, `8 actionable tasks: 6 executed, 2 up-to-date`.
- Final acceptance audit found valid-syntax control-flow failures (`break;`/`continue;`) still escaped `compileResult`. A focused test was added first. RED command ran only `returnsEveryControlFlowCompilationDiagnosticInProgramOrder`; raw result: the test failed with `java.lang.IllegalArgumentException`, `1 test completed, 1 failed`, `BUILD FAILED in 21s`.
- Production now gives `Ast.Break`/`Ast.Continue` source spans and performs ordered control-flow validation before lowering, using exact pinned Toast messages.
- Final focused GREEN reran only `returnsEveryControlFlowCompilationDiagnosticInProgramOrder`. Raw result: `> Task :test`, `BUILD SUCCESSFUL in 2m 56s`, `8 actionable tasks: 6 executed, 2 up-to-date`.
- Per the user's correction, no redundant full local suite will be run; CI owns broad verification.

## Final local diff before formatting/commit

- Final committed slice contains six issue-owned files: `MooCompiler.java`, `MooRuntime.java`, `Ast.java`, `MooParser.java`, `MooCompilerTest.java`, and `MooRuntimeTest.java`.
- Final diff size: 484 insertions, 34 deletions.
- `git diff --check` and `git diff --cached --check` both completed successfully.
- Local commit: `cb29b625687fa8f953cc37223153b79a48f19e4c` (`fix: report .program feedback`).
- Issue worktree is clean and one commit ahead of `origin/master`. No push/PR/comment occurred.

## Prepublication invariant audit

- A review claimed `if (program.isPresent() == !diagnostics.isEmpty())` inverted the `CompilationResult` invariant. Its truth table actually accepted the two valid forms and rejected both-invalid forms; the focused GREEN evidence is compatible with that code.
- The expression was nevertheless rewritten, without behavioral change, to the direct equivalent `if (program.isPresent() != diagnostics.isEmpty())` for clarity.
- Per user instruction, no tests were run for this amendment. `git diff --check`, `git diff --cached --check`, and final `git show --check` completed successfully.
- Amended local commit: `6252d79ff1b11ce741188def87fa56ce6101e678` (`fix: report .program feedback`). The prior `cb29b62` hash is superseded.
- Exact revision note: the earlier focused commands ran against uncommitted worktree content based on `d50652d`, not the eventual commit object. The invariant expression was already present for those runs; later changes included control-flow diagnostics and a descriptor-colon correction before the original commit. No exact-commit CI claim was made.

## Expanded authority findings

- Toast `parser.y` increments `nerrors` for every callback and can report syntax plus semantic compile diagnostics such as illegal `^`/`$` context, invalid scattering targets, loop-control misuse, malformed literals, and missing quotes. A hard-coded single syntax diagnostic is insufficient.
- Toast `.program` calls ordinary `match_object`, so target scope is not numeric-only. The Banteng runtime already implements relevant command matching for `#id`, `me`, `here`, and visible names/aliases; `$system-object` lookup is available through #0 properties.
- Second disposable-oracle capture proved `return ^;` on lines 1 and 2 yields `Line 1:  Illegal context for \`^' expression.`, `Line 2:  Illegal context for \`^' expression.`, `2 error(s).`, and `Verb not programmed.`. It also proved `me` and `$server_options` reach verb lookup, `here:eval` enters programming mode, and malformed `#not-a-number` yields `I don't see "#not-a-number" here.`. The explicitly validated disposable directory was removed.
- Narrow next design: add recoverable ordered parser diagnostics for multiple malformed statements, preserve the parser's diagnostic text where Toast has a specific message, and factor/reuse one object-reference matcher for `.program` rather than retaining the numeric-only draft.

## Blocker

No blocker. Focused TDD and the local scoped commit are complete.

## Next action

Return the raw evidence and unpublished PR title/body to the parent. Do not push or publish without fresh authorization.

## Follow-up audit (2026-08-16)

- Pinned Toast authority remains `/root/src/toaststunt` at `aecc51e9449c6e7c95272f0f044b5ba38948459e` under Debian/root WSL.
- `start_programming()` displays `db_object_name(oid)` for the requested object, then stores `db_verb_definer(h)` plus the requested verb descriptor. `end_programming()` re-resolves that descriptor on the defining object and emits exact object/verb disappearance messages before parsing.
- `find_verb_for_programming()` admits programming only when the selected verb grants write permission to the player; denial is exactly `Permission denied.`. An empty object substring in `:verb` reaches object matching and therefore produces `"" is not a valid object.`.
- Toast lexer-specific messages confirmed in pinned generated parser source: `Malformed object number`, `Malformed floating-point literal`, `Floating-point literal out of range`, `Missing quote`, and `End of program while in a comment`. Banteng's current lexer directly supports mappings for object-number, float-range, and missing-quote errors; it does not lex block comments.
- Current Banteng `MooParser.ParseResult` discards its partial AST when any parser diagnostic exists, which prevents earlier `break`/`continue` diagnostics from being combined with later parse diagnostics. The narrow repair is to retain the partial program, combine parser and control-flow diagnostics, and sort them by source line.
- Current runtime stores a verb slot index across programming, displays the defining object instead of the requested object for inherited verbs, lacks start-time write permission, and compiles before checking whether the defining object/verb still exists.
- Per current instruction, add focused tests but run no build/test command; CI owns execution. Amend the single unpublished issue commit only.

## Follow-up next action

Patch the six issue-owned production/test files narrowly, inspect diff/status, run only diff checks, and amend `fix: report .program feedback` without publishing.

## Follow-up completion

- Amended unpublished commit: `2d6c0a926a4d78227fa677f936b2696b7e5c677f` (`fix: report .program feedback`).
- Final issue diff from `d50652d`: seven files, 748 insertions, 52 deletions. The added lexer file change is the narrow malformed-exponent diagnostic needed to preserve Toast's malformed-float text.
- `git diff --check d50652d..HEAD` and `git show --check HEAD` both exited 0. The issue worktree is clean and one commit ahead.
- No build or test command was run for this follow-up, per instruction; CI owns execution. No push, PR, comment, or other publication occurred.
- Next action: hand the exact local revision and proposed PR copy to the parent for review.

## Programmer-bit gate audit

- Pinned Toast `tasks.cc::do_intrinsic_command()` returns 0 for `.program` when `is_programmer(tq->player)` is false, before usage or target handling. Pinned `db_objects.cc::is_programmer()` checks only `FLAG_PROGRAMMER`; wizard status is not a substitute.
- Banteng's ordinary command fallback returns `I couldn't understand that.` when no command or `huh` verb is selected. The narrow runtime gate therefore lets a non-programmer `.program` line fall through to that existing path rather than emitting `Permission denied.`.
- Focused regression logs in Wizard, directly clears only the built-in `programmer` property through a committed world transaction, proves programmer bit 2 is clear while wizard bit 4 remains set, then expects `.program #0:do_login_command` and the following `return 5;` to both be ordinary unknown commands.
- No build or test command was run, per instruction. The same unpublished commit was amended to `a5e7a39` and remains unpushed.
- Next action: collect exact full hash, status, file/stat, and diff-check output for parent handoff.

## Final diagnostic completeness audit

- Recovering `parseStatementsUntil()` now catches body-level `ParseException`s itself, preserving completed nested statements inside `if`, `while`, `for`, `fork`, `try`, except, and finally bodies. At EOF it returns a partial body and compound terminators tolerate EOF only in diagnostic-collection mode, so earlier nested break/continue nodes remain available to control-flow validation.
- Added source-ordered nested mixed diagnostics across `if`, named `while`, named `for`, `fork`, and `try`, plus an unterminated `if` case proving partial AST retention at EOF.
- Pinned Toast exact strings now covered for bare `$`, multiple scatter `@` targets, too many scatter targets, unreachable EXCEPT, too many EXCEPT clauses, and EOF inside `/* ...` comments.
- Pinned grammar condition is literal: the too-many-EXCEPT callback checks existing count `> 255` before adding a clause, so the focused regression triggers on clause 257 at line 515 while preserving the exact `Too many EXCEPT clauses (max. 255)` text.
- Banteng previously had no block-comment tokenization, making EOF-in-comment structurally impossible; the smallest lexer addition skips closed `/* ... */` comments and throws one source-located internal diagnostic only at EOF, mapped by `MooCompiler` to Toast text.
- No build or test command was run. The same unpublished commit was amended to short hash `158f8f9`; no push/publication occurred.
- Next action: collect final exact hash/status/stat/diff-check output and return it to the parent.

## Exact-contract closing audit

- Pinned Toast increments `lineno` only while skipping whitespace outside a block comment; its `/* ... */` scan consumes internal newlines without incrementing the diagnostic line. Banteng now uses a comment-only offset/column advance, records the comment-start line for EOF, and resumes ordinary newline accounting only after the closing delimiter.
- Added multiline comment regressions: an unterminated comment reports its start line, and a terminated multiline comment does not shift the later bare-`$` diagnostic by its internal newline.
- Toast `vet_scatter()` calls `yyerror` for every rest target after the first and continues scanning. Banteng diagnostic collection now records each excess rest target at that identifier span while still constructing the scatter target; three rest targets yield two ordered diagnostics.
- The inherited programming success regression retains protocol and defining-object storage assertions, then invokes `me:inherited_program()` through runtime eval/verb execution and expects the production result wrapper containing `17`.
- Current uncommitted follow-up is four files, 44 insertions and 7 deletions. No build/test command has run; no blocker.
- Next action: inspect/stage the four exact paths, amend the same unpublished commit, then collect hash/status/stat/diff-check output.

## Exact-contract completion

- Final unpublished commit: `4ad62a89d0f8334ec34715cfa9789d00f947570a` (`fix: report .program feedback`).
- Final issue diff from `d50652d`: seven files, 995 insertions, 62 deletions. Worktree status has no path entries and reports one commit ahead/four behind current `origin/master`; no unrelated base commits were imported.
- `git diff --check d50652d..HEAD` and `git show --check HEAD` each exited 0. No build or test command ran; no push/publication occurred.
- Next action: return exact final revision and raw audit output to the parent.

## Post-merge current-master audit (2026-08-18)

- Live issue #4 is closed by normal-merged PR #38. Merge commit `f6f543398efaa62947697d4f600a6fba61f2959c` has parents `a687f8d10ad27ee39099129436998230ef12dcf4` and issue branch head `54e265399d81662b6c954c2a112133ef0a2f7431`.
- The existing worktree is clean at `54e2653`; against current master `8369c112d9cf30f5dd096b7c2af0116ea5244ba4`, ancestry is `95 0` (master-only, branch-only). The issue branch has no unique work to rebase or republish.
- PR #38 contains the main implementation plus focused diagnostic/fixture repair commits and has six terminal successful checks.
- Current master retains source-ordered one-based compiler diagnostics, exact Toast diagnostic/completion text, multiline-comment/scatter line handling, and partial-AST control-flow diagnostics.
- Current runtime still displays the requested object's name while storing inherited code on the defining object. The focused test invokes the newly programmed inherited verb through runtime execution and observes result `17`.
- Current runtime still gates `.program` on the programmer flag, separately enforces verb owner/wizard/write permission, re-resolves the verb by name at termination after reordering, and reports verb/object disappearance before compilation.
- Issues #16 and #18 changed adjacent persistence/runtime code, but the #4 protocol implementation and focused behavioral contracts remain present on current master.
- No local build/test command was run for this audit. No code, branch, tracker, or PR mutation was performed.

## Post-merge disposition

- No repair is needed or authorized. Leave issue #4 and its merged branch untouched; no PR packet is prepared.
