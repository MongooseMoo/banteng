# Issue 94: loop-name case folding

## Goal
Fix issue #94 with normalized registration and one lookup shared by executable and Toast layout compilation. Smallest change: a package-private loop-name helper, replace three lookup loops, normalize both registration representations, and compiler regressions; approximately 90 changed lines.

## State
Implementation is local on campaign/94-loop-case. LoopNames uses the existing local-variable folding semantics (`PropertyOps.normalize`: `toLowerCase(Locale.ROOT)`). MooCompiler registers normalized lists; ActiveLoopTarget normalizes its registered names; both compilation paths call LoopNames.find.

## Decisions and why
Preserve source spelling in the AST and executable variable operands. Normalize only lookup registrations and requests. Search innermost-first as the existing code does. Tests compare generated instruction targets for same-case and mixed-case exits across collection/range/index for loops, named while loops, outer-loop exits, and shadowed loop names.

## Surprises
None. PropertyOps.normalize is package-private to vm, so the bytecode helper uses the identical existing expression without changing VM API.

## Next step
Coordinator publishes the committed candidate, obtains Claude review, and reads PR CI. No local tests run: coordinator requested return without waiting for shared WSL resources. Regressions explicitly compile both executable instructions and Toast layout through callBoundaries(0).

## Draft PR text
Title: Normalize named loop exits in both compiler paths

Named break and continue now fold case when registering and resolving loop names, using one shared innermost-first lookup for executable bytecode and Toast vector layout. Covers mixed-case exits in named while loops and collection, range, and indexed for loops, including nested outer targets and shadowing.

Fixes #94.

Validation: PR CI pending; no local CI or targeted test run.
