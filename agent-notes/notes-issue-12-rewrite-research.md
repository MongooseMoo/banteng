# Issue 12 OpenRewrite promotion research

## State

- Investigation is read-only in `C:\Users\Q\code\banteng-agent-issue-12` at `0038e2f2de477f802206cbc2042338a975d7ae11`.
- PR #50 is paused separately at `ced9246a19e4c73347196a5087e5a8bd618a59e5`.
- No build, test, edit, push, or publication action has been taken in the issue-12 worktree.

## Findings so far

- `rewrite.yml` currently contains 28 `ChangeType` mappings: nested `LayoutCompiler`, nested `VectorBuilder`, and 26 nested model types, followed by `ShortenFullyQualifiedTypeReferences`.
- `DecomposeToastV17ProgramLayoutTest` proves only three consumer references are retargeted. It does not prove either nested class is promoted into a new compilation unit or removed from `ToastV17ProgramLayout`.
- The branch already contains hand-extracted `moo.bytecode.LayoutCompiler` and `moo.bytecode.VectorBuilder`, but there is no corresponding custom promotion recipe source under `rewrite-recipes/src/main/java`.
- The sole recipe project uses OpenRewrite Java/test `8.89.0`; `scripts/rewrite.sh` activates resource recipes through the root Gradle rewrite plugin.

## Blocker / next action

- The initially assumed Gradle cache path under `C:\Users\Q\.gradle` does not exist. Locate the actual Gradle user-home/cache or inspect dependency artifacts already present in the repository/tooling, then verify `ScanningRecipe` and Java AST/source-generation APIs before recommending the custom recipe design.
