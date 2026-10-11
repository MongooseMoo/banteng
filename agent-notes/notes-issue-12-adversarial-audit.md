# Issue #12 / PR #51 adversarial audit notes

## State

- Audit worktree: `C:\Users\Q\code\banteng-agent-issue-12`
- Branch/head: `agent/issue-12-program-layout` at `8f914bd4c01972fc6cce67366553436afa036641`
- Merge base: `8369c112d9cf30f5dd096b7c2af0116ea5244ba4`
- Current `origin/master`: `d212436d37cfc022be736469ec4d27f421322d69`; branch is 3 behind / 10 ahead.
- Audit is read-only. No edits, builds, tests, pushes, or merges in the issue #12 worktree.
- `ward set adversary` could not run because `ward` is not installed; protocol is being applied manually.

## Current findings

- Live issue #12 requires all public layout model declarations moved out, `LayoutCompiler` and `VectorBuilder` promoted to top-level compiler-side classes, the persistence-to-syntax architecture exemption deleted, and OpenRewrite used with `ChangeType` through `scripts/rewrite.sh`.
- PR changes 13 files, 2,562 insertions / 2,161 deletions. It adds `moo.bytecode.LayoutCompiler`, package-private `moo.bytecode.VectorBuilder`, and `moo.bytecode.ToastV17ProgramModel`; shrinks the persistence facade to roughly 280 lines; deletes the three-line architecture carve-out.
- Old/new public type inventories currently appear complete and matching: 19 public records, five public enums, and the public sealed structural-stack interface. The issue text calls these 21 records, but the source inventory is 19 records plus other public model types.
- Persistence facade has no `moo.syntax` import. `LayoutCompiler` owns `moo.syntax.Ast` and `moo.syntax.MooParser` imports.
- Potential blockers under investigation: PR is behind current master; public `LayoutCompiler`/constructor/query methods may expand API; OpenRewrite recipe may only retarget types and not prove promotion; runtime/test files show large near-wholesale formatting/import churn; ten-commit sequence contains repeated CI/debug-only commits and likely weak logical boundaries.

## Next action

- Inspect architecture test, recipe YAML and executable recipe test, repository-hygiene contract, full dependency scans, exact semantic diff in runtime/test files, and per-commit boundaries.
- Produce a blocker-first ALIGNED/VIOLATED/GAP verdict with raw command/output summaries, excluding CI diagnosis.

## Final audit update

- PR advanced during the audit to `0038e2f2de477f802206cbc2042338a975d7ae11`; its only delta from the inspected worktree head removes temporary FULL test logging and target parser stubs, adds `ShortenFullyQualifiedTypeReferences`, and leaves the architecture/mechanism findings unchanged.
- Static model extraction is exact: the complete old public model block and new `ToastV17ProgramModel` block compare byte-for-byte equal (`14578` characters each). Source inventory is 19 public records, 6 public enums, and 1 public sealed interface.
- YAML contains all 28 expected mappings: 26 public model types plus `LayoutCompiler` and `VectorBuilder`. No expected mapping is absent.
- Blocker: the recipe only applies `ChangeType` plus shortening; it does not promote/extract either nested private class, contrary to the live issue's explicit OpenRewrite mechanism. The test fabricates those legacy classes as public and only checks three mappings, so it is not proof against the actual private nested source shape and leaves 25 mappings unexercised.
- Blocker: recipe YAML landed in production commit `3fab16d`; executable recipe proof first appeared later in `081420b`, so the mechanism lacks a tests-first commit boundary. Latest `0038e2f` mixes a recipe change with test-fixture changes.
- Contract gap: `RepositoryHygieneTest` proves top-level target classes and one model record, but would allow copied compiler/builder classes to remain privately nested. Current production is statically correct, but the RED structural contract does not fully encode the move.
- Scope is otherwise narrow: runtime and existing tests only retarget type ownership; dependency metadata supports the new recipe test; persistence has no syntax imports; architecture exemption is gone. `LayoutCompiler` is public because the persistence facade crosses into `moo.bytecode`; `VectorBuilder` remains package-private.
- Current master is three commits ahead with issue #52 and overlaps `PublicationScheduler.java`; `git merge-tree` reports changed-in-both but no conflict markers. Rebase/update and re-audit exact head before merge.

## Replacement-head audit checkpoint (2026-08-18)

- Requested audit target is `0c258c289fa52af44ef8d1b56dc176361cc4e5a0` on exact base/merge-base `d212436d37cfc022be736469ec4d27f421322d69`.
- The PR/worktree advanced during audit to `51356f41ac8c3ccafbbe883f12e39b1f2236ff98` (`test: expose v17 promotion recipe failures`); architectural inspection remains anchored to requested `0c258c2`, with head drift reported separately.
- Replacement adds `PromoteToastV17LayoutCompilers`, a `ScanningRecipe` that captures the exact attributed outer type/path, requires a complete private-static-final nested pair, refuses either target-path collision, generates two compilation units from captured class ASTs, and removes both captured statements by UUID. It does not itself collect surrounding dependency declarations; it only promotes already-prepared nested classes.
- The promotion test uses unique body/import sentinels and expected generated source paths, runs two cycles with exactly one changing cycle, plus partial-pair and one-target collision no-op cases. The fixture is still a small synthetic pair rather than the real compiler dependency closure.
- The 28-mapping test constructs all 28 old and expected target fields from one shared `TYPE_MOVES` list and asserts list size 28. This checks executable retargeting but risks self-fulfilling coverage because fixture and expected output derive from the same mapping list; YAML must be independently compared.
- YAML order is promotion first, then 28 `ChangeType` entries, then import cleanup/ordering/shortening. Because generated compiler bodies initially retain types attributed to old nested model ownership, later `ChangeType` cycles are load-bearing.
- Next: inspect per-commit tests-before-production boundary, production import/visibility/dependency closure, private-copy guard, architecture carve-out removal, YAML-vs-independent expected mapping inventory, exact CI failure, and collision/path/import AST edge cases. No issue-12 worktree edits/build/tests/push/merge.

## Replacement-head raw CI/source findings

- Exact requested head CI run `32194124926` completed cancelled after replacement, but its `Static analysis & tests` job `95894533243` failed `:rewrite-recipes:test`: 6 tests, 4 failed. The failing tests are the exhaustive 28-map proof and all three promotion tests.
- Failure diagnoses supplied from the exact reports: all 28 `ChangeType` transformations occur but the result lacks the enclosing `ToastV17ProgramModel` import; the partial-pair and collision cases correctly make zero changes but inherit the class default `expectedCyclesThatMakeChanges(1)`; generated `VectorBuilder` keeps nested indentation despite `AutoFormatVisitor`.
- Production/tree contracts are otherwise structurally strong: the first test commit removes the architecture carve-out and adds target ownership checks; the second commit adds both executable recipe tests before production; the production commit adds the custom recipe/YAML and implementation. `RepositoryHygieneTest` lines 64-79 checks both top-level targets and explicitly forbids private facade copies; line 86 caps facade at 500 lines.
- YAML independently contains 28 unique old and 28 unique new targets. The exhaustive test has a separate expected `TYPE_MOVES` inventory of 28 and builds before/after consumers from it, so its failing assertion is a real executable mismatch, not a missing mapping count.
- Architectural blocker independent of formatting: base dependency/helper declarations occur before the nested classes (`handlerControl` line 656, `IndexedControl` 743, `UnitKind` 760, `VectorBuilder` 1043, `referenceWidth` 1292, `LayoutCompiler` 1299). Final `LayoutCompiler.java` is 1,403 lines and contains those surrounding declarations at lines 42-398. The scanner only captures the two class declaration ASTs and cannot produce the real final dependency closure from base input; its 73-line synthetic fixture does not prove real-input promotion.
- Recipe edge findings: generated compilation units copy the source CU imports wholesale and depend on later cleanup/type rewrites; `AutoFormatVisitor` alone demonstrably does not normalize a formerly nested class to top-level indentation. Missing owner import likely requires explicit import reconciliation (for example a visitor calling `maybeAddImport` after `ChangeType`), not only `ShortenFullyQualifiedTypeReferences`.
- Scope diff outside extraction is ownership retargeting only under `--ignore-all-space`: `PublicationScheduler` and existing tests replace `ToastV17ProgramLayout.*` model references with `ToastV17ProgramModel.*`; no behavioral change was observed in the reviewed hunks.
- Next: inspect official vendored 8.89 import/format API availability, record precise recipe edge corrections, then issue fail-closed NO MERGE to root. No worktree edits/build/tests/push/merge.

## OpenRewrite 8.89 API verification

- Official tag `v8.89.0` `JavaVisitor` exposes `maybeAddImport(String)` and `maybeAddImport(String, boolean)`; both schedule the `ImportService.addImportVisitor` with `doAfterVisit`. A narrowly scoped post-ChangeType visitor can therefore add `moo.bytecode.ToastV17ProgramModel` only where referenced. The official `AddImport` visitor also supports `onlyIfReferenced`, but its reference discovery depends on correctly attributed target types.
- Official tag `v8.89.0` `AutoFormatVisitor` already runs NormalizeFormat, Spaces, WrappingAndBraces, NormalizeTabsOrSpaces, TabsAndIndents, MergeSpaces, BlankLines, line-break normalization, and trailing-whitespace removal. The observed retained nested indent therefore is not fixed by simply repeating AutoFormat.
- Official tag `v8.89.0` provides `org.openrewrite.java.format.ShiftFormat.indent(J, Cursor, int)`, documented for moving code a definite number of indentation levels left/right. Correct use is to shift the captured nested declaration one level left while its cursor still has the enclosing `JavaSourceFile`, then reset its top-level prefix and autoformat. The current static `captureNestedPair` stores raw nested ASTs without a cursor and cannot call this API correctly.
- Immediate verdict remains NO MERGE: exact requested-head recipe suite is red, the real source dependency closure is not extracted by the scanner, and the promotion test remains a synthetic two-class fixture. Final report should distinguish fixable API defects from the deeper real-input closure/proof blocker.

## Final replacement head `be2e8ec79946453d55a6e71c5dd6d7fc07cb2e44`

- Clean worktree and remote branch both point to `be2e8ec79946453d55a6e71c5dd6d7fc07cb2e44`; exact base/merge-base remains `d212436d37cfc022be736469ec4d27f421322d69`.
- `be2e8ec` removes temporary full test logging, adds `AddToastV17ProgramModelImport`, applies it after all 28 ChangeTypes, uses official `ShiftFormat.indent(..., -1)` before AutoFormat, and places `expectedCyclesThatMakeChanges(1)` only on the changing promotion case. These are narrow fixes to the three prior CI mismatches.
- Current exact-head CI snapshot during audit: workflow/static and dependency review succeeded; static/tests, CodeQL, and conformance were still in progress; PR state `UNSTABLE`. No local build/test was run.
- Production layout remains structurally aligned: facade 273 lines; compiler 1,403; vector builder 275; model 404. Facade has zero compiler/builder declarations, zero public nested declarations, and zero syntax references. Compiler-side files have zero persistence references. Architecture carve-out is deleted and private-copy guard is explicit.
- TDD history is acceptable: `0907c4f` structural RED, `d173fde` mechanism RED tests, `0c258c2` production, `51356f4` diagnostic only, `be2e8ec` narrow recipe fixes. The 28-entry expected inventory is independent of YAML and exercises all mappings in one before/after consumer.
- Final blocker remains unchanged: base source places compiler closure outside both captured nested class declarations (helper/type siblings begin at lines 656/743/760, VectorBuilder at 1043, `referenceWidth` at 1292, LayoutCompiler at 1299), while the recipe scanner only stores `LayoutCompiler` and `VectorBuilder` declarations at recipe lines 126-145. Final compiler contains the sibling closure at lines 42-398. Therefore running the published recipe on the real base cannot generate the committed compiler source; the synthetic test proves AST-body copying but not real dependency-closure promotion.
- Verdict: NO MERGE until the ScanningRecipe captures/promotes the actual dependency closure (or a separately executable preparatory recipe produces the exact prepared source) and an input-driven RewriteTest uses a representative real closure, proves both generated files/removal, then runs idempotently/collision-safe. Exact-head CI must also complete green after that change.
