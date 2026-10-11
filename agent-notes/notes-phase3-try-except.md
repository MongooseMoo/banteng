# Phase 3 try/except family handoff

## Measured state

- Starting commit: `7fc855f feat: add phase 3 property creation`; tracked worktree clean, branch ahead 158.
- Exact Toast managed result for `src/moo_conformance/_tests/language/try_except.yaml`: 30 passed in 5.26 seconds.
- Banteng baseline: 24 passed, 6 failed in 6.33 seconds.
- Every ordinary `try`, `except`, `catch` expression, nested handler, return-through-finally, and error-through-finally row passes.
- The six failures are exactly `raise_basic`, `raise_with_message`, `raise_with_value`, `raise_caught_by_catch_expr`, `raise_default_message_is_string`, and `raise_propagates_through_finally`; all return `E_VERBNF` because the `raise` builtin is absent.

## Current ownership finding

- `MooVm` already accepts builtin `Result.errorDetails()` and routes it through the structured `raiseError` path used by the passing exception handlers and finally continuations.
- Toast registers `raise` in `execute.cc:3777` with arity 1..3 and types `TYPE_ANY, TYPE_STR, TYPE_ANY`; implementation begins at `execute.cc:3499`.
- No production or test change has been made for this family yet.

## Next action

- Read the exact Toast `bf_raise` implementation plus Banteng's existing error-details representation, then add a focused red `BuiltinCatalogTest` for call shape, error validation, default/custom message, and custom value. Add only the direct manifest handler if that red is exactly the missing spec/count.
- Keep the slice only if the managed family improves from 24/30; otherwise restore it before another source slice.
- This note must remain untracked and unstaged.

## Raise implementation state

- Toast `execute.cc:3499-3516,3777` implements `raise(code [, message [, value]])`, defaulting message to the code's literal string and value to zero; caught structured exceptions are `{code, message, value, stack}`.
- The focused red was exact: 20 tests ran with two failures, the manifest count and absent `raise` spec.
- Production now registers `raise` with one required `ANY` code and optional `STRING, ANY`, constructs the existing `Result.errorDetails` shape `{message, value, {}}`, and delegates propagation/catching/finally behavior to the existing VM.
- Banteng's current VM keys handlers by `ErrorValue`; active Phase 3 rows all use error codes. Non-error Toast codes are accepted by the call shape and currently map to `E_INVARG` rather than introducing a new arbitrary-code exception model into this slice.
- Focused `BuiltinCatalogTest` passes 20/20 after implementation.
- Next action: reread Phase 3 after the substantial targeted pass, then run the exact managed Banteng `language/try_except.yaml` family. Keep only if it improves from 24/30.

## Keep evidence

- Exact managed Banteng rerun: 30 passed in 6.03 seconds, matching Toast's 30 passed. The kept reduction is 6 failures to zero.
- The plan was reread after both the focused pass and managed family pass. The handler enters through the managed socket and uses the existing VM structured-error/finally path.
- Exact plan gate `JAVA_HOME=/opt/java/25 ./gradlew check` completed `BUILD SUCCESSFUL` in 31 seconds. The separate `git diff --check` gate is clean.
- Next action: inspect, stage, and commit only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`. Then reread Phase 3 and advance to `basic/object.yaml`.
