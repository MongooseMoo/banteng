# Issue 12 cycle diagnosis checkpoint

- Scope: read-only diagnosis of PR #51 exact head `c49b09d158430f796705322835811f06c5c74029`.
- Worktree: `C:\Users\Q\code\banteng-agent-issue-12`; clean and tracking the same remote head.
- Current failure: `DecomposeToastV17ProgramLayoutTest.retargetsAllTwentyEightNestedTypes` fails at the `rewriteRun` call on line 60 despite expected content being correct.
- Fixture: 28 mappings are declared; parser dependencies include the legacy outer type, target model outer type, target `LayoutCompiler`, and target `VectorBuilder`.
- YAML order: promoter, remove-unused, model-owner importer, 28 `ChangeType` recipes, order imports, shorten fully qualified references.
- Current hypotheses under inspection: pre-change import insertion mutates the source before `ChangeType`; one of the later normalization recipes changes that import or a type reference in a subsequent cycle; parser stubs may cause a target/legacy attribution edge.
- Blocker: the workflow run is still active, so the failed job log is not yet downloadable through `gh run view`; check annotations contain only the generic exit-code failure.
- Next action: compare exact AST-facing importer behavior and OpenRewrite recipe cycle semantics against the fixture, then report only a source-proven candidate.

## Follow-up evidence

- Completed prior CI run `32199603423`, head `c7dd991`, printed cumulative recipe-change stacks in chronological order: `AddToastV17ProgramModelImport`, all 28 `ChangeType` recipes, then `RemoveUnusedImports`, then `AddToastV17ProgramModelImport` again. This proves the second cycle is remove/re-add import churn, not a missing type mapping.
- Current `c49b09d` target stubs do not change import-layout behavior.
- `ChangeType` 8.89 adds imports for both the target owning class and target nested type; 26 model mappings therefore exercise import folding.
- `AddImport` reads `ImportLayoutStyle` from source markers and otherwise falls back to IntelliJ. The mapping consumer has no generated-source `BANTENG_FORMAT` marker, so its imports use default folding thresholds rather than the explicit no-star style attached only to promoted compilation units.
- Candidate under final verification: attach the no-star import layout to the mapping consumer through the declarative recipe or a narrowly scoped style recipe so all `ChangeType` imports stay explicit during cycle one, avoiding the cycle-two `RemoveUnusedImports`/importer churn.
- Next action: confirm default threshold values and the narrowest supported style-attachment point, then report to root.
