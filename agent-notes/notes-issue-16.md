# Issue #16 working notes

## Current state

- Live issue `MongooseMoo/banteng#16` is open and requests shared `DbScanner`, `ValueTagDecoder`, `ProgramSlot`, and `RawVerb` persistence infrastructure plus OpenRewrite retargeting.
- Current exact base is normal merge `ef6931e3c742775cf7748934c4c054eac50b255b` (PR #46 / issue #17), with parents `bc58a8296dce96782fdc089320d7828623e5411a` and `f6ba2d28cb05232240ba1c219ca3ae18e4de239c`.
- The pre-existing worktree `C:\Users\Q\code\banteng-agent-issue-16` remains untouched. It is one commit ahead and 45 commits behind `origin/master`, with modified `rewrite.yml` and four untracked production files.
- Safe continuation uses distinct worktree `C:\Users\Q\code\banteng-agent-issue-16-continuation`, branch `agent/issue-16-shared-db-reader-v2`.
- Tests-first commit: `26141f252788ffc7a41b075e6eb37deac4a10738 test: define shared persistence reader contracts`.
- No local Gradle/build/test command has been run, per parent instruction.

## Findings and observations

- The preserved tests patch was reusable after replacing its obsolete public `StringValue` constructor call with `StringValue.of(String)` from merged issue #17.
- The tests are honestly RED by compilation on the base because the four requested shared persistence types do not exist.
- V4, V5, and V17 still own duplicate scanner primitives. V5 and V17 still duplicate common value tags 0, 1, 2, 3, 4, and 9. All three currently have nested `ProgramSlot` and `RawVerb` records.
- V17 list decoding includes location-specific nested diagnostics. The shared decoder therefore needs an indexed nested callback so extracting the common list body does not weaken those messages; a one-argument callback overload preserves the simple V5/test surface.
- Added but not yet committed: `DbScanner`, `ValueTagDecoder`, top-level `ProgramSlot`, and top-level `RawVerb` in the clean continuation worktree.
- V4 and V5 have now been retargeted to shared scanner imports; their duplicate scanner methods and nested record pairs are deleted. V5 delegates tags 0-4 and 9 to `ValueTagDecoder.readV5`.
- V17 now asks `ValueTagDecoder.readCommon` for shared tags before its extension switch. The indexed callback preserves the exact `location + " list element N"` recursion labels. V17 duplicate scanner methods and nested records are deleted; its two format-specific count readers remain local.
- The merged `rewrite-recipes` project only contains the custom issue #17 inline-encoding visitor/test. Issue #16 can use standard declarative `ChangeType`, `ChangeMethodName`, and `ChangeMethodTargetToStatic`; no capture-based custom recipe is needed.

## Blocker

- No external blocker. Production is incomplete until the OpenRewrite recipe is added without disturbing the issue #17 recipe, residual imports/duplicates are audited, and CI proves compilation/behavior.
- `scripts/rewrite.sh --apply` invokes Gradle, so it cannot be executed locally under the explicit no-local-build/test instruction. The branch must still carry the declarative recipe and CI-executed proof appropriate to the existing rewrite infrastructure.

## Next action

1. Add the issue-specific declarative `ChangeType` / `ChangeMethodName` / `ChangeMethodTargetToStatic` recipe alongside the merged issue #17 recipe.
2. Inspect residual duplicates, imports, source/API contracts, diff size, and `git diff --check` without invoking Gradle.
3. Commit production separately; then disclose exact PR title/body before any push/PR creation.
