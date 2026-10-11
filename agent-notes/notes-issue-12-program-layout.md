# Issue #12 Toast v17 program layout notes

## Current state

- Live issue #12 is open.
- Fresh isolated worktree: `C:\Users\Q\code\banteng-agent-issue-12`.
- Branch: `agent/issue-12-program-layout`.
- Exact base/HEAD: `8369c112d9cf30f5dd096b7c2af0116ea5244ba4`.
- Worktree was clean at creation; no matching local or remote branch or PR existed.
- No local Gradle, build, or test task will be run; CI is the execution gate.
- Tests-first commit: `dcc97af test: require decomposed v17 program layout`.
- Production commit: `3fab16d4897a4e8b3594ced85beac263212da278 refactor: decompose v17 program layout`.
- Issue worktree is clean with exactly two commits above base.

## Findings and observations

- Current `ToastV17ProgramLayout.java` is 2,092 physical lines and still owns the public model declarations, query facade, private compiler, vector builder, compiler state, and pending layout records.
- `ArchitectureTest` still imports `ToastV17ProgramLayout` solely to carve it out of the `moo.persistence..` -> `moo.syntax..` ban.
- Current production consumers use the nested model types from `PublicationScheduler`; focused persistence/runtime tests do the same.
- The compiler half imports both `moo.syntax` and `moo.bytecode`. The existing architecture rules allow `moo.bytecode` -> `moo.syntax` but forbid the reverse and forbid `moo.persistence` -> `moo.syntax` once the carve-out is removed.
- The pure public model should therefore move to a separate `moo.bytecode.ToastV17ProgramModel` owner. This lets both the promoted compiler and runtime/persistence consumers use it without creating a bytecode-to-persistence dependency.
- Promote the exact nested compiler concepts to top-level `moo.bytecode.LayoutCompiler` and package-private `moo.bytecode.VectorBuilder`. The persistence facade will delegate compilation/query data access without importing syntax.
- Use the repository's `scripts/rewrite.sh` mechanism with `ChangeType` recipes to retarget public nested model references. Run dry-run, inspect the generated patch, then apply. Manual extraction remains necessary for promoting private nested classes.
- A rewrite dry run was started before a later instruction prohibited all local OpenRewrite/Gradle execution. It emitted only Gradle startup/configuration output and was interrupted; no patch/result was produced, reviewed, or applied, and no rewrite success claim is valid.
- The committed `moo.persistence.DecomposeToastV17ProgramLayout` recipe covers `LayoutCompiler`, `VectorBuilder`, and the complete current public declaration set. Static extraction and equivalent retargeting were performed without further local Gradle/OpenRewrite execution; CI owns recipe and test execution.
- Current decomposition: `ToastV17ProgramModel.java` owns the complete public model; public top-level `LayoutCompiler.java` owns parsing/compilation and compiled-layout queries; package-private top-level `VectorBuilder.java` owns vector assembly; `ToastV17ProgramLayout.java` is a 273-line persistence query facade.
- The current file contains 19 public records, six public enums, and one public sealed interface; all moved. The issue body's count of 21 records does not match current master, so no synthetic declarations were invented.
- `PublicationScheduler` and the existing persistence/runtime tests now reference `ToastV17ProgramModel` types. The facade retains its public operations and delegates compilation without importing `moo.syntax`.
- PR #51 is open at `https://github.com/MongooseMoo/banteng/pull/51`; first published head was `3fab16d4897a4e8b3594ced85beac263212da278`.
- First exact-head watcher snapshot: 2 passed, 2 failed, 1 pending; PR open, mergeable, merge-state `UNSTABLE`, no review items.
- Both failed jobs (`MOO conformance suite` 95880137678 and `Static analysis & tests` 95880137691) stop at the same branch-owned compile error: `VectorBuilder.java:194` cannot resolve `isBodyDescendant(AstPath, AstPath, int)`.
- Static inspection found the helper remained private in `LayoutCompiler` while both compiler and vector assembly use the same structural path predicate. No additional failure is exposed in either job log.
- CI repair `93704461b6f816953fef8327c5f160c9ff2bc969` shares `isBodyDescendant` package-privately between compiler and vector builder.
- CI repair `1c31cf1b169a21475966723e81e982641e2935ae` restores the `PublicationSchedulerTest` reflection signature to the query facade type; its predecessor ran 521 tests with only the three resulting `NoSuchMethodException` failures.
- Exact head `1c31cf1` reached 6/6 green, `CLEAN`, and `MERGEABLE`, but final review found the declarative recipe had no execution proof.
- Test commit `081420b66f9a8f29fc3bc01fd9164b8cb8d83ba0` adds a named `RewriteTest` for representative `CallBoundary`, `LayoutCompiler`, and `VectorBuilder` mappings and packages the root `rewrite.yml` into the recipe module's standard resource path.
- That head failed before test execution because dependency verification lacked four new artifacts. Their Maven Central SHA-256 values were measured independently and added in `3f2c116cbf8ba24895a432887ef4e9baa0cf90a2`; metadata diff is 16 additions/0 deletions with mixed EOL preserved.
- Runtime verification then exposed 11 transitive artifacts. Their Maven Central SHA-256 values were independently measured and added in `0ac61a0b69bfc9772490574ec00cf0a13554621e`; metadata diff is 47 additions/0 deletions with mixed EOL preserved.
- Exact run 32191431964 reached `:rewrite-recipes:test`: the named recipe contract executed, and only `DecomposeToastV17ProgramLayoutTest.retargetsModelCompilerAndBuilderTypes` failed with `AssertionFailedError` at line 32. The condensed log does not include the before/after mismatch.
- The same run's MOO conformance job failed independently before branch code because Maven Central returned HTTP 429 while resolving the already-pinned `rewrite-bom` 8.89.0. No code change or blind retry is justified until the recipe assertion is repaired.
- The run API currently lists no downloadable artifacts even though the upload step succeeded, so transformed output still needs authoritative retrieval or reconstruction before changing the expectation.
- Diagnostic-only commit `f462d33b65c2492aa6026924082ce2325a619cab` enabled full rewrite-module test exception logging without changing recipe or expectation.
- Exact job 95887699973 then exposed the authoritative diff: actual retained `ToastV17ProgramModel.CallBoundary` but omitted `import moo.bytecode.ToastV17ProgramModel`; the expected import was the only mismatch. `LayoutCompiler` and `VectorBuilder` both transformed with correct imports.
- Commit `374faf6` added target model/compiler/builder parser stubs and removed diagnostic logging, but exact-head CI still failed the same RewriteTest assertion.
- Diagnostic-only commit `8f914bd4c01972fc6cce67366553436afa036641` re-enabled module-local full exception logging. Exact job `95889308561` proved the target stubs had no effect: actual output still omitted only `import moo.bytecode.ToastV17ProgramModel` while retaining `ToastV17ProgramModel.CallBoundary`; the other two imports remained correct.
- Official OpenRewrite `ChangeType` documentation requires `$` for nested target names, matching the recipe. Primary migration composites append `ShortenFullyQualifiedTypeReferences` after their `ChangeType` entries to shorten attributed references and add conflict-free imports.
- Evidence-backed repair commit `0038e2f` appends `org.openrewrite.java.ShortenFullyQualifiedTypeReferences` to the named composite, keeps the valid expected output unchanged, removes temporary full logging, and deletes the three ineffective target stubs. Static diff was 3 files, 2 insertions, 31 deletions; `git diff --cached --check` was clean before commit.
- `0038e2f` was pushed. The first watcher launch occurred before GitHub attached checks and returned `no checks reported`; this is not a CI status claim.
- Independent audit found `0038e2f` is not completion even if CI turns green: live issue #12 requires OpenRewrite to perform the nested compiler promotion through `scripts/rewrite.sh`, while the current recipe only retargets references and the production promotion was hand-authored.
- The YAML contains all 28 required mappings (two promoted compiler classes plus 26 model declarations), but the executable RewriteTest covers only three. Its executable proof commit also followed the production commit, violating the requested mechanism-tests-first boundary.
- `RepositoryHygieneTest` only forbids public nested types and would miss retained private copies. The mechanism RED must prove the facade contains no nested `LayoutCompiler` or `VectorBuilder` declaration at any visibility.
- Live origin master advanced to `d212436`; the repaired replacement stack must rebase onto that exact master after mechanism work.
- Official OpenRewrite 8.89 exposes `ChangeType` and source-generation APIs but no stock nested-to-top-level promotion recipe. GitHub source/index searches found no Move/Promote nested-class recipe. `ScanningRecipe<T>` is the supported custom mechanism: scan/capture the source, generate new source files with source-root-relative paths, and visit/remove the originals.
- The actual compiler classes depend on a private sibling closure and facade-owned helper methods. A toy extraction cannot compile. The custom recipe must promote the complete compiler dependency closure and reproduce the current narrow public query API, while leaving the production architecture unchanged.
- Planned proof: real pre-split source before/after promotion test, explicit absence of both nested compiler declarations, named-resource execution, and exhaustive 28/28 reference retargeting. A test resource containing the exact 2,228-line pre-split `ToastV17ProgramLayout.java` from base `8369c11` has been added locally but is not committed yet.
- Research refined the mechanism to a source-derived, non-template `PromoteToastV17LayoutCompilers extends ScanningRecipe<Accumulator>`: atomically capture the exact attributed outer CU plus direct nested pair, generate source-root-relative bytecode CUs from the captured AST, and remove only the captured declarations. It must fail closed on partial pairs, duplicates, or target collisions and be idempotent.
- The earlier large real-source/template fixture draft was deleted before commit. The accepted RED uses unique nonempty compiler/builder sentinels and comments so a hardcoded template cannot pass.
- Mechanism RED was committed as original `86af76e` (now rebased `c6071ec`): exhaustive 28-entry named-composite contract; two generated paths; exact old-facade removal; public-final LayoutCompiler/package-final VectorBuilder; two-cycle fixed point; partial/collision no-op; and a RepositoryHygiene reflection guard forbidding private nested copies too. No local Gradle/test/OpenRewrite run.
- Custom recipe production was committed as original `d9fb03b` (now rebased `cf87211`). YAML order is promoter, 28 ChangeTypes, RemoveUnusedImports, OrderImports, Shorten. Static cached diff-check was clean; no local execution.
- A first rebase onto exact current master `d212436d37cfc022be736469ec4d27f421322d69` completed without content conflicts, but the temporary PowerShell sequence editor used an unsupported Windows PowerShell `utf8NoBOM` encoding value. It failed to rewrite the todo, so Git replayed the 13 commits in their old order rather than performing the intended tests-first reorder/fixups. Current exact HEAD is `cf872119fcea7e20f4d06415728ff67ce6e6f864`; no push occurred. `rebase.updateRefs` also moved the local backup ref, but the pre-rewrite head remains recoverable at reflog `HEAD@{15}` / `d9fb03b`.

## Blocker

- PR #51 remains open and is not merge-ready. Files are rebased onto current master, but commit order still violates the mechanism-tests-first requirement and diagnostic-only commits remain in local history.

## Next action

- Repair the sequence editor with Windows PowerShell-compatible encoding, rerun interactive rebase over the rebased 13-commit range to place/fixup structural and mechanism tests before production, drop diagnostic-only commits, and preserve the final tree. Verify final diff/commit list, then measured force-with-lease push and exact-head watch. Never merge.

## Exact recipe failure evidence after history repair

- The repaired three-commit stack is `0907c4f` structural tests, `d173fde` executable mechanism tests, and `0c258c2` production, all above exact master `d212436`; its tree is byte-identical to the prior audited `cf87211` tree.
- Temporary diagnostic head `51356f4` enabled only rewrite-module full exception output. Exact job `95895187184` ran six recipe tests and exposed four failures.
- The exhaustive 28-entry retarget output changed every field correctly. Its only mismatch was the missing enclosing `import moo.bytecode.ToastV17ProgramModel`; `LayoutCompiler` and `VectorBuilder` imports were present.
- Partial-pair and target-collision cases correctly made no changes. They failed only because the class-wide test default required one changing cycle.
- Successful promotion generated both source files and removed the nested pair, but the generated `VectorBuilder` retained the nested class body's extra indentation level.
- Official OpenRewrite 8.89 APIs confirm per-invocation RewriteTest cycle expectations and `ShiftFormat.indent` for moving a captured block one indentation level.
- Evidence-backed repair `be2e8ec` makes the changing-cycle expectation positive-test-only, shifts captured class formatting left once before top-level auto-format, adds a post-ChangeType reference-gated `ToastV17ProgramModel` import recipe, and removes diagnostic exception logging. No expected Java was weakened and no local Gradle/OpenRewrite task ran.

## Current next action

- Push `be2e8ec`, restart exact-head CI monitoring, and inspect raw recipe output before any history cleanup or readiness claim. Never merge.

## `be2e8ec` CI checkpoint

- Exact-head static job `95896543948` compiled both rewrite production and test sources successfully, then ran six recipe tests with two failures.
- The partial-pair and existing-target-collision tests no longer appear among failures, which is direct CI evidence that their no-op cycle assertion defect is fixed.
- Remaining failures are the exhaustive 28-entry retarget test at line 54 and the successful promotion test at line 22. With FULL logging removed, the job log exposes neither actual/expected diff, so no further import or indentation change is justified yet.
- Exact head advanced to diagnostic-only `eb3a894`, which re-adds only rewrite-module FULL test exception output. It was pushed after interrupting the old exact-head watcher.

## Current blocker and next action

- Blocker: authoritative actual/expected output for the two remaining recipe assertions is hidden on `be2e8ec`, and uploaded workflow artifacts still exclude the rewrite-recipes reports.
- Restart the watcher on `eb3a894`, capture both full diffs from its exact static job, remove diagnostic logging with the evidence-backed correction, push, and resume monitoring. No local Gradle/OpenRewrite execution; never merge.

## Closure mechanism repair and replacement stack

- Exact diagnostic job `95897288875` proved the exhaustive retarget output was correct but completed in two cycles because the explicit model import appeared one cycle late. The promotion mismatch was exact: the generated VectorBuilder comment was at column zero while method/body indentation remained four/eight spaces.
- Independent real-source audit found the prior scanner captured only the two nested compiler declarations. Applying it to base `d212436` would omit the sibling compiler closure and could not generate a compilable LayoutCompiler/VectorBuilder pair.
- Tests-first closure commit was rebuilt as `7b28b7a`. Its positive fixture covers all 19 compiler-support declarations and all six helpers, proves the 18 moved support declarations/five moved helpers leave the facade, preserves copied facade-owned `IndexedControl` and `isBodyDescendant`, asserts generated files and visibility/import shape, and requires exactly one changing cycle across two cycles. Complete-closure collision, incomplete closure, and partial-pair cases all fail closed.
- Production recipe now atomically requires/captures the full named closure. It moves `UnitKind` through `LiteralKey`, copies `IndexedControl`, moves compiler-only helpers, copies `isBodyDescendant`, moves `referenceWidth`, and prepends them to captured LayoutCompiler in current ownership order. Shared copies receive randomized IDs.
- VectorBuilder generation adds its exact two static helper imports and 14 nested LayoutCompiler support imports. Support visibility matches the committed architecture, including package-visible LabelReference/GlobalState fields required by VectorBuilder.
- Nested-member formatting shifts bodies one level then explicitly normalizes top-level member/comment prefixes to two spaces. The model-import recipe now keys off the already-retargeted `ToastV17ProgramModel` AST owner in the same cycle instead of waiting for refreshed type attribution.
- Diagnostic FULL logging is removed. Public compiler query API/String-constructor reshaping remains the separately hand-authored semantic part of the production refactor; the recipe does not claim to synthesize non-nested APIs.
- Origin master advanced to exact `c278f236e5c619dfc7d46540bf14fe8fa71db8fa`. The branch rebased without content conflicts and was rebuilt to exactly three commits: `a6221ae` structural tests, `7b28b7a` executable mechanism tests, `97e3d86` production.
- Static audit: merge-base equals exact origin master; no diagnostic commits; no FULL logging; no nested compiler copies; `git diff --check` clean; worktree clean. Runtime production architecture files are byte-identical to audited `cf87211`; only rewrite mechanism/tests changed.
- Exact old remote `eb3a8949440f5eedd42ac9177c25f01142b1e24a` was measured, then replaced with `97e3d8669c240256bfca0c0d97233d9d4112cbb6` by explicit force-with-lease.

## Current next action after replacement push

- Start a fresh exact-head watcher on `97e3d86`, inspect raw closure recipe results and all other branch checks, repair only branch-owned failures, and keep watching. No local Gradle/tests/OpenRewrite; never merge.

## `97e3d86` compile checkpoint

- Exact static job `95900641688` stopped at `:rewrite-recipes:compileJava`, before recipe tests.
- Raw compiler diagnostics were three type errors and six warnings: `RandomizeIdVisitor.visitNonNull` returns `J` and required explicit ClassDeclaration/MethodDeclaration casts; generic `Comment.withSuffix` made the stream infer `List<Object>`; three raw `AddImport` constructions produced paired raw/unchecked warnings under `-Xlint:all -Werror`.
- Narrow compile repair `4e5cf3e` adds the two exact J subtype casts, one exact Comment cast, and `AddImport<ExecutionContext>` type arguments. No recipe behavior, test contract, runtime source, YAML, or build configuration changed.
- Static diff-check was clean; the one-file repair was 12 additions/six deletions. It was pushed normally from `97e3d86` to exact head `4e5cf3e`.

## Current blocker and next action after compile repair

- Blocker: closure recipe assertions have not executed yet because the prior exact head did not compile.
- Restart exact-head monitoring on `4e5cf3e`, capture the first executable closure test output, repair only demonstrated branch-owned failures, and later fold the compile-only fix into the production commit before the final exact-head gate. No local Gradle/tests/OpenRewrite; never merge.

## Exact recipe execution through `33d4434`

- After the remaining `List<Comment>` compile fix, the branch was rebuilt above exact master `2920a6a45b4ac32652ec1478660b94d9aeff4ec0` as structural tests `ce9c391`, executable mechanism tests `5a75b06`, and production `ca77705`.
- Diagnostic-only head `e0103bc` enabled module-local FULL test exceptions. Exact job `95903034462` proved two failures: the exhaustive 28-map output was correct but needed two cycles, and generated VectorBuilder omitted all 14 `LayoutCompiler` nested-type imports and retained four/eight-space nested indentation.
- Evidence-backed repair `33d4434520b8fd725842e5ff5ae6cfbeb2678c96` moved model-owner import detection before all ChangeTypes and keys it to the 26 attributed legacy nested model types; changed nested imports to owner package `moo.bytecode.LayoutCompiler`; and formats generated CUs with an explicit two-space `TabsAndIndentsStyle`. It also removes diagnostic FULL logging. Static `git diff --cached --check` was clean; no local Gradle/OpenRewrite ran.
- Exact push advanced the branch normally from `e0103bc` to `33d4434`. First watcher snapshot reported five pending checks at exact head.
- Exact static job `95904720084` compiled and ran all seven recipe tests. Five tests passed, but the same two positive tests failed at `DecomposeToastV17ProgramLayoutTest.java:54` and `PromoteToastV17LayoutCompilersTest.java:22`. The condensed log does not expose actual/expected details because FULL logging was removed.

## Current blocker and next action at `33d4434`

- Blocker: the production changes did not satisfy either positive contract, and the exact new mismatches are hidden. No additional import/format guess is justified.
- The exact-head watcher session was interrupted before replacing the branch. Add only module-local FULL test exception output in a diagnostic commit, push, capture both authoritative diffs from exact-head CI, then remove the diagnostic with evidence-backed production fixes. Keep expectations unchanged, run no local Gradle/OpenRewrite, and never merge.

## Diagnostic evidence at `26be86e`

- Diagnostic-only commit `26be86ee40e4fcf9b07aaafeda06f0d213ca472c` re-enabled only module-local FULL test output. Exact static job `95905244134` compiled and ran all seven recipe tests, with the same two positive failures.
- The exhaustive 28-map actual output is correct except for the missing enclosing model import. The pre-ChangeType import was added too early and then removed by `RemoveUnusedImports` against stale target attribution.
- The positive closure test now reaches generated `LayoutCompiler.java`; all mismatch text is formatting-only. Whole-CU AutoFormat adds a blank after the class open, expands every empty record/interface body, and changes continuation indentation. The expected source-derived compact formatting remains the contract.
- Parent audit confirms the VectorBuilder nested-type import repair is solved in the prior actual output; no import expectation change is needed.
- Official OpenRewrite 8.89 `ShiftFormat.indent(J, Cursor, int)` finds the enclosing JavaSourceFile from the supplied cursor and shifts every newline-bearing whitespace/comment suffix according to that CU's `TabsAndIndentsStyle`, defaulting to IntelliJ. Correct use requires a cursor rooted in the captured source tree and should shift the captured subtree exactly one nesting level without whole-file reformatting.

## Current blocker and next action at diagnostic head

- Blocker: importer placement and subtree movement remain wrong, although all reference retargets, generated closure membership, collision/fail-closed behavior, and nested imports are evidenced correct.
- Move the target-reference-gated model import after all ChangeTypes and after `RemoveUnusedImports` so no later removal drops it; retain `OrderImports`/shortening afterward. Remove whole-CU AutoFormat and shift captured compiler/builder subtrees one source indentation level using a cursor anchored to the captured source CU, preserving internal compact bodies/comments. Remove FULL logging in the same repair, keep expected Java unchanged, push, and restart exact-head monitoring. No local Gradle/OpenRewrite; never merge.

## Source-confirmed final repair draft

- OpenRewrite 8.89 `ShiftFormat` does not infer indentation from source text; it reads `TabsAndIndentsStyle` from `NamedStyles` markers and otherwise falls back to IntelliJ's four-space style. The correct source-derived move therefore uses a cursor anchored at a temporary copy of the captured CU carrying Banteng's explicit two-space style.
- The uncommitted repair shifts each captured compiler/builder class subtree once before assembly, resets only its top-level prefix, prepends already outer-level support/helper members without shifting them, and removes whole-CU AutoFormat entirely. This preserves compact empty bodies, comments, permits alignment, and relative continuation indentation while moving only one nesting level.
- The uncommitted importer repair runs after all 28 ChangeTypes and `RemoveUnusedImports`, before `OrderImports`/shortening. It structurally recognizes either simple or fully-qualified `ToastV17ProgramModel` field-access owners for the exact 26 member names; it does not render/search source text.
- The diagnostic FULL block is removed in the same draft. Expected Java and tests remain unchanged.

## Current state and next action before replacement push

- Worktree contains the final production repair plus diagnostic removal, not yet committed. The exact remote head remains diagnostic `26be86e`.
- Statistically inspect imports/API use, line lengths, exact four-file diff, and `git diff --check`. Commit the repair temporarily, then rebuild history to the exact three TDD commits above master `2920a6a`, folding all production repairs into commit three and dropping both diagnostics. Measure remote head before force-with-lease, push, and restart exact-head watching. No local Gradle/OpenRewrite; never merge.

## Diagnostic evidence and rebuilt head `ad796b9`

- The first three-commit replacement head `caaf15a` compiled and ran all seven recipe tests in exact job `95906837776`; the same two positive tests failed and five other tests passed.
- Diagnostic-only head `57de08d` exposed job `95907366401`. The exhaustive 28-map output content was exact but still required two changing cycles. Generated VectorBuilder matched its body and both static imports exactly, but default import layout folded the 14 nested-type imports to `LayoutCompiler.*`, and the retained `final` modifier carried one leading inter-modifier space onto the top-level class line.
- Evidence-backed production repair gives generated CUs a non-printing Banteng NamedStyles marker with two-space indentation and an `ImportLayoutStyle` star threshold of `Integer.MAX_VALUE`, so both AddImport and later OrderImports retain the 14 explicit imports. It normalizes the first retained class modifier prefix to empty, removing the top-level leading space.
- To make the composite single-cycle, `RemoveUnusedImports` and the model importer now run before all 28 ChangeTypes. The importer structurally detects the exact 26 legacy or target model field-access owners and adds the target enclosing import after cleanup but before retargeting; later OrderImports/Shorten can consume it in the same cycle without a subsequent cleanup removing it.
- FULL diagnostic output was removed and expectations were unchanged. The repaired tree was rebuilt into exactly three commits above master `2920a6a`: `ce9c391` structural tests, `5a75b06` executable mechanism tests, and new production `ad796b941c5a9d55b3e89ea3bea35d0d626276eb`. Tree identity was checked before/after the soft-history rebuild.

## Current state and next action at `ad796b9`

- Local exact head is `ad796b9`; worktree should be clean after the history rebuild. Remote remains diagnostic `57de08d` until measured replacement.
- Re-run static base/history/order/count/diff checks, verify current origin master and exact remote branch head, force-with-lease only that measured remote to `ad796b9`, and restart exact-head watching. No local Gradle/OpenRewrite; never merge.

## Independent API review and exact pushed head `c7705c7`

- Independent OpenRewrite 8.89 source review found that nested member imports fold according to `nameCountToUseStarImport`, not only `classCountToUseStarImport`, and that IntelliJ's default layout orders normal imports before static imports, contrary to the exact expected generated source.
- Final import style uses `ImportLayoutStyle.builder()` with static-all first, one blank line, normal-all second, and both class/name star thresholds at `Integer.MAX_VALUE`. The generated CU retains this non-printing NamedStyles marker through later `OrderImports`.
- Review independently confirmed the first-modifier prefix normalization and composite order promoter -> RemoveUnused -> legacy/target importer -> 28 ChangeTypes -> OrderImports -> Shorten.
- The one-file API correction was amended into production, preserving the exact three-commit stack. New exact production/head is `c7705c78595f98a8f2814e3c234518bf21747da9`, with parent `5a75b06`; base remains exact current master `2920a6a`.
- Static audit before publication: worktree clean, `git diff --check` clean, no FULL logging, 28 mappings, merge base exact `2920a6a`. Remote diagnostic head was independently measured as `57de08d83f70b8ab32a122461f7526e5d9ee898e` and replaced only with explicit force-with-lease to `c7705c7`.

## Current state and next action at `c7705c7`

- Exact replacement is pushed; no watcher has started yet after the notes checkpoint.
- Start continuous exact-head watching on PR #51, capture raw recipe/static and all other check states, repair only branch-owned failures, and never merge. No local Gradle/OpenRewrite.

## Exact diagnostic job `95909395541`

- Exact diagnostic head `04549ab` proves the exhaustive mapping output is content-correct but still changes in two cycles. The failure contains no source diff, only the explicit cycle-count assertion.
- Generated compiler import layout and top-level class prefix are now fixed. The remaining LayoutCompiler mismatch is lexical spacing caused only by stripping `private`: each support declaration's first surviving class kind/modifier retains one inter-token space; LabelReference/GlobalState fields retain one before their type; and the two shared methods retain one before `static`. The captured compiler sentinel also lacks the expected blank line after `referenceWidth`.
- Evidence-backed formatting draft normalizes the first surviving class kind/modifier, field modifier/type, and method modifier prefix when removing `private`. It restores exactly two newlines before the first captured compiler-body statement. Expected sources are unchanged.
- To diagnose rather than weaken the one-cycle contract, a temporary additional RewriteTest runs the same 28-map source with two expected changing cycles, then deliberately reports every `Result` path and complete recipe stack from `RecipeRun.getChangeset()`. The original one-cycle test remains unchanged and failing; module FULL logging remains on the diagnostic head.

## Current state and next action at diagnostic draft

- Worktree at remote diagnostic head `04549ab` has two uncommitted files: the evidence-backed formatting repair and the temporary cycle-recipe reporter test. Static diff-check is clean; no local Gradle/OpenRewrite ran.
- Commit/push this diagnostic pair, capture the exact recipe stacks from CI, identify the cycle-two actor before changing YAML/import logic, then delete the reporter/FULL logging and fold only evidence-backed production repair into the three-commit stack. Never merge.

## Cycle-specific diagnostic replacement

- Cumulative `Result.getRecipes()` stacks cannot attribute a change to a recipe cycle. Official OpenRewrite 8.89 records cycle-specific `SourcesFileResults.Row` entries in the recipe execution context before `LargeSourceSet.afterCycle` throws the one-cycle assertion.
- The temporary diagnostic now preserves the original one-changing-cycle test. It supplies an explicit `InMemoryExecutionContext`, catches only the test's `AssertionError`, reads `DataTableExecutionContextView.view(context).getDataTableStore().getRows(SourcesFileResults.class)`, filters exact `row.getCycle() == 2`, and rethrows with parent recipe, leaf recipe, and before/after source paths. No separate relaxed-cycle test remains.
- Formatting was also narrowed from exact job `95909395541`: the first moved UnitKind support member now receives one newline plus two spaces, removing the sole extra blank after the generated compiler class open; later moved members retain blank separation. All private-removal token-prefix fixes remain.
- The prior cumulative diagnostic commit was amended to exact head `23b58b62104d07e70914f24949bb716db2b22550`. Static diff-check was clean. Measured remote `c7dd9911c69fe911899aeebbe4c72d90a7d03da4` was replaced only with exact force-with-lease to `23b58b6`; master remained `2920a6a`.

## Current blocker and next action at `23b58b6`

- The first watcher attempt occurred before GitHub attached checks and returned `no checks reported`; this is not a CI status claim.
- Restart the watcher once checks attach, capture exact cycle-two rows from the static job, then remove all diagnostic test/FULL changes, implement only the demonstrated cycle repair, fold production into the three-commit stack, and rerun exact-head CI. No local Gradle/OpenRewrite; never merge.
## 2026-08-18 diagnostic correction after head 23b58b6

- Exact-head CI still has two positive recipe-test failures; five negative/idempotence cases pass.
- `SourcesFileResults` cannot diagnose cycle 2 because OpenRewrite data tables write only in cycle 1.
- Authoritative replacement diagnostic: keep FULL module-local failure output and run the unchanged 28-map test with `cycles(3).expectedCyclesThatMakeChanges(1)` so the cycle-2 source delta is printed before the final cycle.
- The promotion mismatch was mechanically reduced to one source-preservation detail: input `PendingFinallyContinuation` is already wrapped across two lines, while the expected generated source incorrectly joined it. The expected fixture should preserve the two-line wrap; production must not add a targeted join.
- Do not change YAML/recipe ordering until CI reports the exact cycle-2 edit.
- Next: stop the existing watcher, apply only those test/diagnostic changes, commit and force-push with a measured lease, then restart exact-head monitoring.

## 2026-08-18 source-backed cycle and facade repair

- Diagnostic head `3d39893f138442abb6e441cc9bf1506bc7784532` was pushed from exact lease `23b58b62104d07e70914f24949bb716db2b22550`; exact-head static job `95912697793` failed as intended while the diagnostic was active.
- Final cycle diagnosis: the mapping test parser supplied only legacy types. `RemoveUnusedImports` dropped the unattributed target model import in cycle 1; `AddToastV17ProgramModelImport` re-added it in cycle 2. Production recipe ordering is not the defect.
- Current uncommitted repair adds test-only attributed target stubs for `ToastV17ProgramModel` plus all 26 model members and public `LayoutCompiler`/`VectorBuilder` stubs. The test retains `cycles(3).expectedCyclesThatMakeChanges(1)` as fixed-point proof.
- The retained facade was reformatted by `maybeAutoFormat` in the promotion visitor. Current repair replaces it with direct `visited.withBody(...)`, preserving retained facade text while removing only captured members.
- The positive generated-source expectation preserves the input's existing two-line `PendingFinallyContinuation` wrap. No production join is added.
- Module-local FULL logging and the impossible DataTable diagnostic are removed. YAML/recipe ordering remains unchanged.
- Static checks completed without running Gradle/OpenRewrite: `git diff --check` exit 0; branch status contains only the three expected files (`build.gradle.kts`, promotion recipe, decomposition RewriteTest).
- Next: place the target-stub/fixed-point contract in the tests-first recipe commit, fold all recipe changes into the production commit, remove diagnostic commits, verify exact three-commit topology/diff, then force-push from a newly measured remote lease and restart the exact-head watcher.

## 2026-08-18 rebuilt stack and remaining fixed-point failure

- Rebuilt and pushed exact three-commit head `c49b09d158430f796705322835811f06c5c74029` on base `2920a6a45b4ac32652ec1478660b94d9aeff4ec0`: `ce9c391` structural tests, `f62409a` executable rewrite tests, `c49b09d` production.
- Rebuilt tree was byte-identical to temporary final snapshot (`git diff --exit-code a1f94b9 c49b09d`, exit 0), `git diff --check` exited 0, and diagnostic string search had no matches.
- Exact job `95914259872` at `c49b09d` ran seven rewrite tests; promotion passed and only `DecomposeToastV17ProgramLayoutTest.retargetsAllTwentyEightNestedTypes` failed at line 60. Non-FULL output did not disclose the assertion.
- Authorized diagnostic-only head `69127b0ffc068eb529564864a7f63945da4e15e8` adds only module-local FULL test exception logging. No recipe/test input/expected/cycle/production change.
- Exact diagnostic job `95915046041` reports: `Expected recipe to complete in 1 cycle, but took 2 cycles. This usually indicates the recipe is making changes after it should have stabilized.` It reports no output diff, so the expected final content is accepted and only fixed-point behavior remains.
- Existing target stubs did not eliminate cycle two. Promotion source-preservation repair is proven by its positive test passing.
- Current blocker: exact cycle-two actor is still unidentified. Per root instruction, stop after reporting the assertion and make no repair guess.
- Next: report the exact assertion/job/head to root and await an evidence-backed cycle repair direction. FULL diagnostic must be removed and final history returned to exactly three commits before final CI. Watcher remains active unless stopped for repair.

## 2026-08-18 marker repair did not close fixed-point RED

- Root-directed repair at final three-commit head `68c3a5ee0805ee753461da8a46e586db904acc1e` made `PromoteToastV17LayoutCompilers.BANTENG_FORMAT` package-visible and reused it in `AddToastV17ProgramModelImport`.
- AddToast resets a per-CU relevance flag, sets it when a legacy/target model owner is visited, then returns the relevant CU with `visited.getMarkers().add(BANTENG_FORMAT)`. It does not remove or replace any existing NamedStyles. YAML order, 28 mappings, expected output, `cycles(3).expectedCyclesThatMakeChanges(1)`, promotion recipe behavior, and target stubs were unchanged.
- Final tree at `68c3a5e` had exactly three commits, matched the assembled tree, passed `git diff --check`, and had no FULL/DataTable diagnostic strings. No local Gradle/OpenRewrite run.
- Exact static job `95916586210` still ran seven rewrite tests and failed only `DecomposeToastV17ProgramLayoutTest.retargetsAllTwentyEightNestedTypes` at line 60; promotion remained passing. Concise output hid whether the failure stayed cycle-only or became textual.
- Authorized diagnostic-only head `5d3e5e9d02489bab1534f0fa5623d5239f790038` adds only module-local FULL exception logging and is pushed fast-forward from `68c3a5e`. First watcher start returned `no checks reported` because GitHub had not yet attached the new suite.
- Current blocker: capture the exact assertion/diff from the new static job; make no recipe/test repair before reporting it.
- Next: poll until checks attach, run continuous watcher, fetch the exact static-job log, report to root, stop before repair. FULL must later be removed and history restored to three commits.

## 2026-08-18 cycle-two state instrumentation preparation

- Exact FULL head `5d3e5e9` again confirmed the mapping recipe takes two changing cycles. Root authorized a single diagnostic AddToast change while preserving FULL.
- Required diagnostic triggers only after `super.visitCompilationUnit` when the CU is relevant and `context.getCycle() == 2`; it must throw with cycle, current imports, effective import thresholds, attached NamedStyles names, and printed CU.
- Official OpenRewrite v8.89.0 source was read directly before editing:
  - `Style.from(Class<S>, SourceFile)` returns nullable merged effective style from all NamedStyles.
  - `Markers.findAll(NamedStyles.class)` returns all attached style markers; `NamedStyles.getName()` is available.
  - `ImportLayoutStyle.getClassCountToUseStarImport()` and `getNameCountToUseStarImport()` return effective integer thresholds.
  - `Tree.printTrimmed(Cursor)` prints individual imports; `SourceFile.printAll()` prints the current CU.
- Use an explicit fallback label when `Style.from` returns null. Construct an import cursor with the current CU as parent. Do not change expected source, YAML, mappings, cycle assertion, production behavior outside the diagnostic throw, or test input.
- Current state remains diagnostic head `5d3e5e9` with FULL enabled; watcher stopped before instrumentation.
- Next: add only the cycle-two throw/imports required to compile the diagnostic, static diff-check, commit/push, capture exact job output, then stop before repair.
