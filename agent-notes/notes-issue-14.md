# Issue #14 checkpoint

- Worktree: `C:\Users\Q\code\banteng-agent-issue-14`
- Branch/PR: `agent/issue-14-worldtxn-decomposition`, PR #57
- Published head before repair: `55aa340dd4b336a55f62c49f4a7d47e440d28814`
- Base: `5ef47c4382868fed9ad2dc5ca3192777ec0e8903`
- Old-head CI was 6/6 green, but independent architecture audit returned NO-MERGE.

## Findings

1. `DecomposeWorldTxn` only appends captured members to an already-existing `PropertyLayoutEngine`; exact base has no engine, so the mechanical migration is not truthful.
2. Recipe capture/removal is keyed by simple method name and a `Map<String, MethodDeclaration>`. It must use overload-safe declaration identity, require the complete ten-method closure plus `PropertyDefinition`, and fail closed on missing, extra/duplicate, partial, or target-file collision cases.
3. Exact-base `changeParent(long,long)` callers must be mechanically migrated to retained `changeParents(long,List<Long>)`; current recipe merely deletes the method after hand-authored caller repair.
4. CodeQL finding 106 points to unread pattern variable `WorldAnonymousObject _` at `WorldTxn.java:1741`; fix source rather than dismissing the alert.

## State

- Old-head watcher stopped after a final raw snapshot showed PR OPEN/CLEAN and 6 passed checks.
- No local Gradle, build, test, or OpenRewrite execution is permitted.
- No repair edits or commits have been made yet.

## Next action

Add focused executable RED contracts first for base-like source generation, exact overload-safe closure/fail-closed cases, collision/idempotence, and `changeParent` caller migration. Commit tests separately, then implement input-driven generation and the narrow CodeQL source fix, audit the diff/history, push, and restart exact-head CI watching. Never merge.

## Concrete repair design

- Model the moved closure with exact method identities: name plus erased parameter types and `static`, rather than a simple-name map. The ten expected names contain one exact static declaration each; a same-name static overload is an invalid extra and makes the recipe a no-op. The retained three-argument instance `collectAncestry` is not part of the closure.
- Require the exact private `PropertyDefinition(long, String)` record. Missing method, missing record, duplicate/extra moved-name static overload, partial closure, duplicate source, or `PropertyLayoutEngine.java` collision all fail closed with zero changes.
- Generate `PropertyLayoutEngine.java` from the captured source compilation unit/class shell and captured method/record ASTs. Only the small owner shell is synthesized; algorithm bodies and comments remain source-derived. Randomize generated IDs and preserve fixed-point behavior by treating the generated target as a collision on subsequent runs.
- Retarget only invocations whose attributed declaration identity matches the captured static method. Separately migrate the attributed exact `WorldTxn.changeParent(long,long)` caller to `changeParents(long,List.of(long))` and remove only the exact dead declaration.
- RED fixture will contain every moved declaration with unique sentinels, the retained instance overload, an exact external base caller, generated-file assertions, and second-pass idempotence. Separate no-change cases cover missing closure, missing record, an extra static overload, and target collision.
- Add a source-level test forbidding the unread `case WorldAnonymousObject _` pattern before replacing it with an unnamed pattern in production.

## Repair progress

- Tests-first commit: `436a7e0f648401e1d30de4d8939dc8b0879aba1f test: require truthful WorldTxn extraction recipe`.
- The RED commit modifies only `DecomposeWorldTxnTest` and `WorldTxnDecompositionTest`; it has not been pushed.
- Production is edited but uncommitted in exactly two files: `rewrite-recipes/src/main/java/moo/rewrite/DecomposeWorldTxn.java` and `src/main/java/moo/world/WorldTxn.java`.
- The custom recipe now captures one typed WorldTxn source, exact signature-keyed static closure declarations, the exact nested record, exact dead method IDs, one attributed base caller, and target collision state. `ready()` gates both generation and visitor mutation.
- Generation clones the input CU/class shell, filters engine imports, creates a private constructor from a source constructor, inserts the captured source-derived closure, randomizes IDs, and emits `PropertyLayoutEngine.java`. The visitor removes only recorded UUIDs, qualifies exact attributed static calls, and pluralizes the exact external caller.
- The CodeQL source repair changes the sealed switch's unread `case WorldAnonymousObject _` arm to `default`.
- Static `git diff --check` is clean. No Gradle, build, test, or OpenRewrite execution has occurred.

## Current audit risk and next action

- Before committing production, inspect cached OpenRewrite 8.89 bytecode signatures for the withers and import helpers used by generation; this is a read-only API audit, not a build.
- Recheck primitive `long` identity handling, source-derived constructor shaping, generated imports, exact fail-closed gates, and retained-call qualification. Then commit production separately, perform history/diff/scope audit, push with the measured lease, and restart PR #57 exact-head watching.
