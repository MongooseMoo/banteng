# Issue 94: loop-name case folding

## Goal
Fix issue #94 with normalized registration and one lookup shared by executable and Toast layout compilation. Smallest change: a package-private loop-name helper, replace three lookup loops, normalize both registration representations, and compiler regressions; approximately 90 changed lines.

## State
Implementation is local on campaign/94-loop-case. LoopNames uses the existing local-variable folding semantics (`PropertyOps.normalize`: `toLowerCase(Locale.ROOT)`). MooCompiler registers normalized lists; ActiveLoopTarget normalizes its registered names; both compilation paths call LoopNames.find.

## Decisions and why
Preserve source spelling in the AST and executable variable operands. Normalize only lookup registrations and requests. Search innermost-first as the existing code does. Tests compare generated instruction targets for same-case and mixed-case exits across collection/range/index for loops, named while loops, outer-loop exits, and shadowed loop names.

## Surprises
PropertyOps.normalize is package-private to vm, so the bytecode helper uses the identical existing expression without changing VM API.

## PR 97 follow-up
Hosted CI exposed my incorrect `callBoundaries(0)` test call: `ProgramLayout.vector` uses -1 for main and 0 for the first fork. Fixed the test to assert successful main-vector layout compilation; an empty call-boundary equality assertion did not verify exit targets and was removed. Claude also found diagnostics still used equalsIgnoreCase, which accepts dotless-i against I although variable lowercase matching does not. Diagnostic scopes now normalize on registration and use the same LoopNames.find as both compiler paths; no-match returns -1 so callers report the appropriate diagnostic/error. Added compiler diagnostics cases using accepted dotless-i identifiers. Focused test execution in progress; unrelated scheduler failure remains outside this branch.

Focused command: `JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin sh ./gradlew test --tests world.mongoose.banteng.bytecode.MooCompilerTest --max-workers=2` in Debian WSL. The unqualified task name also selects subproject tests; `:errorprone-checks:test FAILED` was printed before root compilation finished. Root `:compileJava` remains running with no compiler error printed. Use `:test` when restricting the invocation to the root authored test file. First shell attempt interpolated Windows PATH into sh and failed `Syntax error: "(" unexpected`; the explicit Linux PATH command above corrected that invocation.

## Next step
Coordinator publishes the committed candidate, obtains Claude review, and reads PR CI. No local tests run: coordinator requested return without waiting for shared WSL resources. Regressions explicitly compile both executable instructions and Toast layout through callBoundaries(0).

## Draft PR text
Title: Normalize named loop exits in both compiler paths

Named break and continue now fold case when registering and resolving loop names, using one shared innermost-first lookup for executable bytecode and Toast vector layout. Covers mixed-case exits in named while loops and collection, range, and indexed for loops, including nested outer targets and shadowing.

Fixes #94.

Validation: PR CI pending; no local CI or targeted test run.
