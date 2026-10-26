# Issue 94: loop-name case folding

## Goal
Fix issue #94 with normalized registration and one lookup shared by executable and Toast layout compilation. Smallest change: a package-private loop-name helper, replace three lookup loops, normalize both registration representations, and compiler regressions; approximately 90 changed lines.

## State
PR #97 is open on campaign/94-loop-case. Repair commit 6b96cc0 aligns diagnostics with the same normalization as compilation. LoopNames uses the existing local-variable folding semantics (`PropertyOps.normalize`: `toLowerCase(Locale.ROOT)`). MooCompiler registers normalized lists; ActiveLoopTarget normalizes its registered names; diagnostics and both compilation paths call LoopNames.find. Coordinator owns publication and Claude review.

## Decisions and why
Preserve source spelling in the AST and executable variable operands. Normalize only lookup registrations and requests. Search innermost-first as the existing code does. Tests compare generated instruction targets for same-case and mixed-case exits across collection/range/index for loops, named while loops, outer-loop exits, and shadowed loop names.

## Surprises
PropertyOps.normalize is package-private to vm, so the bytecode helper uses the identical existing expression without changing VM API.

## PR 97 follow-up
Hosted CI exposed my incorrect `callBoundaries(0)` test call: `ProgramLayout.vector` uses -1 for main and 0 for the first fork. Fixed the test to assert successful main-vector layout compilation; an empty call-boundary equality assertion did not verify exit targets and was removed. Claude also found diagnostics still used equalsIgnoreCase, which accepts dotless-i against I although variable lowercase matching does not. Diagnostic scopes now normalize on registration and use the same LoopNames.find as both compiler paths; no-match returns -1 so callers report the appropriate diagnostic/error. Added compiler diagnostics cases using accepted dotless-i identifiers. Focused test execution in progress; unrelated scheduler failure remains outside this branch.

Historical failed focused command: `JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin sh ./gradlew test --tests world.mongoose.banteng.bytecode.MooCompilerTest --max-workers=2` in Debian WSL. The unqualified task name also selected subproject tests and ended `BUILD FAILED in 5m 34s`: `:errorprone-checks:test > No tests found for given includes: [world.mongoose.banteng.bytecode.MooCompilerTest](--tests filter)`. First shell attempt interpolated Windows PATH into sh and failed `Syntax error: "(" unexpected`; the explicit Linux PATH command corrected that invocation. The corrected root-only command uses `:test --tests world.mongoose.banteng.bytecode.MooCompilerTest --max-workers=2`; it is currently running compileTestJava after compileJava completed successfully.

## Next step
Coordinator publishes the committed repair to PR #97, obtains Claude review, and reads PR CI. The corrected focused test invocation remains in progress; it is not a publication gate. Regressions explicitly compile executable instructions and the Toast main layout through callBoundaries(-1).

## Draft PR text
Title: Normalize named loop exits in both compiler paths

Named break and continue now fold case when registering and resolving loop names, using one shared innermost-first lookup for executable bytecode and Toast vector layout. Covers mixed-case exits in named while loops and collection, range, and indexed for loops, including nested outer targets and shadowing.

Fixes #94.

Validation: PR CI and Claude re-review pending on the repaired head. Focused compiler test running with the corrected root-only Gradle task.
