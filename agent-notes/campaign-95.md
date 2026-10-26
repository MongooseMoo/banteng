# Issue #95: eval recursion failure

## Goal
Preserve the next instruction when eval cannot push a frame in a non-debug activation.

## State
Implemented the one-argument correction in MooVm.applyDynamicEval and a focused regression in MooVmTest. PR #96 is open; hosted CI and final review are pending. No local suite was run. The coordinator's campaign note tracks subsequent delivery state.

## Decisions and why
The smallest change is to pass false to ErrorOps.raise because invokeBuiltin already advanced past CALL. Existing error routing remains the owner. The regression uses a non-debug root activation with maximum stack depth 1 and evaluates `x = eval("return 1;"); y = 2; return {x, y};`, checking `{E_MAXREC, 2}`. Approximately 30 added test lines and one changed implementation line.

## Surprises
None. Source matches the issue: MooVm.applyDynamicEval used the three-argument overload; ErrorOps.raise advances that overload in a non-debug frame.

## Next step
Coordinator reviews, publishes the PR, obtains Claude review, reads CI, and merges when eligible.

## PR draft
Title: Preserve assignments after eval reaches the recursion limit

Body:
When eval cannot push a frame in a non-debug activation, raise E_MAXREC without advancing past the instruction that follows CALL. CALL has already advanced the instruction pointer.

Add a regression at maximum stack depth that checks `x = eval("return 1;"); y = 2;` assigns E_MAXREC to x and 2 to y.

Fixes #95.

Validation: PR CI pending; no local suite run.
