# Phase 3 splice family handoff

## Measured state

- Starting commit: `5b55b4a feat: add phase 3 looping dependencies`; tracked worktree was clean.
- Toast exact managed result for `src/moo_conformance/_tests/language/splice.yaml`: 25 passed in 6.27 seconds.
- Banteng baseline: 22 passed, 3 failed in 6.75 seconds.
- The failures are exactly `computed_property_assignment_returns_rhs`, `computed_property_assignment_evaluation_order`, and `computed_property_write`, each with undifferentiated `E_VERBNF`.
- Computed-property reads, non-string errors, computed verb calls, callable dispatch, evaluation order, and guarded-expression cleanup already pass.

## Active slice

- Each failing row first creates an object, then calls `add_property`; the builtin was absent. The rows later exercise the already-present computed-property assignment VM path and `recycle` handler.
- `WorldTxn.addProperty` is the existing real mutation owner and rejects duplicate local or inherited names.
- Toast `property.cc:109-137,207-238,341-342` defines `add_property(ANY, STRING, ANY, LIST)`, validates `{owner, perms}`, accepts permission letters `r/w/c`, requires object write plus owner-or-wizard authority, and returns zero.
- Focused red was exact: 19 tests ran with two failures, the manifest count and absent `add_property` spec.
- Production now contains only the direct manifest handler delegating to `WorldTxn.addProperty`; compiler, VM, and world transaction structure were not changed.
- Focused `BuiltinCatalogTest` passes 19/19 after implementation.

## Next action

- Reread Phase 3 after the substantial targeted pass, then rerun the exact managed Banteng `language/splice.yaml` family. Keep the slice only if it improves from 22/25; otherwise restore it before another source slice.
- This note must remain untracked and unstaged.

## Keep evidence

- Exact managed Banteng rerun: 25 passed in 6.44 seconds, matching Toast's 25 passed. The kept reduction is 3 failures to zero.
- The plan was reread after both the focused catalog pass and managed family pass. `add_property` is explicitly named as a Phase 3 vertical dependency.
- Exact plan gate `JAVA_HOME=/opt/java/25 ./gradlew check` completed `BUILD SUCCESSFUL` in 30 seconds. The separate `git diff --check` gate is clean.
- Next action: inspect, stage, and commit only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`. Then reread Phase 3 and advance to `language/try_except.yaml`.
