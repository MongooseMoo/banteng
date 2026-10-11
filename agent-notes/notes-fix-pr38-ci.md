PR #38 CI repair

- Finding: `recoveringDiagnostics` is a nullable parser-mode field but lacked a declaration-site nullable annotation and initializer, triggering NullAway constructor analysis.
- Finding: `reportRecoverable(Optional<Ast.SourceSpan>, String)` has no callers and triggers Error Prone's unused-private-method check.
- Current state: the issue-4 worktree has the narrow source patch applied: annotate and initialize the mode field, and remove the unused overload.
- Blocker: none.
- Next action: inspect the structural diff, commit only `MooParser.java` as `codex: fix CI failure on PR #38`, and report the exact HEAD/diff without running builds or tests.

2026-08-17 exact-head CI diagnosis

- Scope: read-only diagnosis of PR #38 head `eed315589c1e645489053d55c82d1ef59212ee99`, run `32078296689`, job `95536000627`; no PR-worktree edits, builds, or tests.
- Observation: the two legacy failures both use an `except (ANY)` clause before a later selected-error clause. `MooParser.parseTry()` now calls `reportRecoverable(..., "unreachable except clause")`, and strict `MooParser.parse()` turns that diagnostic into a `ParseException`. The tests are `ToastV17ProgramLayoutTest.exposesMultiarmCatchSourceOrderDepthsAndBantengControls` and `PublicationSchedulerTest.roundTripsProtectedNestedCatchFinallyLoopAndCatchHandlerPhases`.
- Observation: CI artifact XML confirms runtime actuals: inherited-object test fails before runtime at `requested.parents().getFirst()` because Wizard has no parents; permission test receives `Now programming System Object:locked_program...` because connected Programmer is object/owner 8; reorder test receives `Now programming :program_reordered...` because the fixture's connected Wizard object has an empty stored name.
- Current best theory: production parser/runtime behavior is consistent with the new Toast checks; all five failures are invalid test setup/source assumptions, not one shared production regression.
- Blocker: exact Toast authority for whether ordinary internal parsing must continue accepting unreachable catch arms is not yet independently rechecked, but issue #4 explicitly requires the Toast `.program` diagnostic and the new compiler test pins it.
- Next action: inspect fixture login identities/object fields and test helper patterns, then recommend the smallest explicit setup changes and test-first sequence without implementation.
