# Issue #33 read-only adversarial audit checkpoint

- Current exact authority: `origin/master` is `f7784aa2de3059beb80d3f0fa137cbe0e647a0b2`; live issue #33 is OPEN.
- PR #57 watcher observed `stop_pr_closed` with merged head `8127cf7a95158b29037a704cb751673a01da0c76`; merge commit is `f7784aa2de3059beb80d3f0fa137cbe0e647a0b2` with parents `5ef47c4` and `8127cf7`.
- Existing issue lanes: stale `agent/issue-33-owned-namespace` at `3d3d899` (forked from `8369c11`, three unique commits) and clean v2 lane at exact master.
- Stale commits: `db48225` tests, `b97cd66` named recipe, `3d3d899` namespace migration.
- Initial finding: stale structural diff predates landed #9/#12/#13/#14. Exact master now contains custom rewrite code and named composites under `moo.rewrite`, `moo.vm`, `moo.builtin`, `moo.persistence`, and `moo.world` that the stale proof did not exercise.
- Corrected runner scope: although root alone applies `org.openrewrite.rewrite`, official OpenRewrite multi-module documentation says a root application parses and refactors all sources from all projects. Therefore `scripts/rewrite.sh` can structurally migrate root plus the 14 rewrite-recipes and 2 errorprone-checks Java files. The required RED must prove this repository-specific multi-project coverage; separately applying the plugin to subprojects would be redundant.
- Exact current Java inventory: root main/test/jmh/jcstress = 91/58/1/2; rewrite-recipes main/test = 7/7; errorprone-checks main/test = 1/1.
- The stale branch has no executable `RewriteTest` or `recipeFromResources` proof for `world.mongoose.banteng.ChangePackage`; grep finds only its YAML declaration. Its RED commit initially omitted both errorprone source roots, and the production commit broadened the test while implementing.
- Current custom recipe hard-coded namespace evidence includes #9 `UseBuiltinCall` FQNs, #13 `MoveMooVmOpcodeFamilies.SOURCE_TYPE`, #12 promotion/model/package/import FQNs and paths, and #14 `DecomposeWorldTxn` source/parameter FQNs. Stock `ChangePackage` cannot be assumed to rewrite Java string literals, text-block fixtures, YAML recipe IDs, or YAML assertions.
- Landed path additions were inventoried from merge second parents: #9 adds `UseBuiltinCall` recipe/test plus builtin protocol classes/tests; #12 adds two recipe classes/two recipe tests plus three bytecode classes; #13 adds recipe/test plus six opcode-family classes and architecture test; #14 adds recipe/test plus server/builtin/world ownership classes and tests.
- Skill helper blocker: `ward set adversary` is unavailable (`CommandNotFoundException`); read-only enforcement is being applied manually.
- No builds, tests, rewrite runs, ref changes, edits to tracked files, pushes, or publications have been performed.
- Concurrency warning: the v2 issue-33 worktree was initially clean at exact master, but now reports untracked `src/test/java/world/` from another worker. It has not been touched or inspected further. Any remaining exact-master evidence must come from `git show`/`git ls-tree` against `f7784aa`, not the worktree overlay.
- The stale branch fixed-point CI addition only runs `scripts/rewrite.sh world.mongoose.banteng.ChangePackage` and checks the root patch is empty; it does not prove initial migration of auxiliary subprojects.
- Stale RED mutation: production commit changes `NamespaceOwnershipTest` materially (adds errorprone roots, `.github`, service-adjacent scan scope, broader residual classification). Therefore the final contract was not fully RED before production.
- Uncovered extensionless artifact: the Error Prone service descriptor contains `moo.errorprone.NarrowIllegalArgumentCatch`, but the stale residual scanner only accepts named text extensions and has no service-loader assertion. The implementation changed it without a pre-production RED.
- Current exact-tree curated cases include JFR annotation/config/test strings, ArchUnit package strings, Error Prone package predicate, build selectors/main class/group/JMH/jcstress regexes, scripts and generated source paths, package-derived resources, custom recipe FQN/path strings, YAML IDs/method patterns, and the service descriptor.
- Next action: finish exact hard-coded FQN/service-loader/JFR/resource inventory, define the minimal truthful runner partition, and provide required pre-production RED contracts and adversarial verdict.

## PR #58 final audit checkpoint

- Exact PR head: `2ebceff12396cf2343e2df8eaa4e06ead1d7595b`, exact base `f7784aa2de3059beb80d3f0fa137cbe0e647a0b2`, non-draft, mergeable, issue #33 still open.
- Eight commits: structural RED; named recipe/CI; recipe-catalog RED; migrated test/fixture contracts; residual RED; ownership RED; historical classification RED; final production migration.
- Current raw check state is not green: Workflow static analysis PASS, Dependency review PASS, Static analysis & tests FAILURE, CodeQL pending, MOO conformance pending. Existing failed-job log is not downloadable yet because the overall run remains in progress; no rerun is authorized or performed.
- Exact head is clean in `banteng-agent-issue-33-v2` and tracks `origin/agent/issue-33-owned-namespace-v2`.
- Commit inspection underway: `b663209` adds `ChangePackageTest` and `NamespaceOwnershipTest`; `fdb6809` adds the declarative recipe and CI fixed-point/script proof; `e0b76f2` adds complete catalog contracts; `6590ab1` migrates all test roots plus exactly all 16 package resources before production main-source migration.
- Next action: finish remaining commit boundaries, inspect exact head residual/catalog/recipe source, classify CI failure if logs become available, and issue MERGE/NO-MERGE independent of CI color.
- Exact-head counts match the committed contract: root main/test/jmh/jcstress `91/59/1/2`, errorprone main/test `1/1`, rewrite-recipes main/test `7/9`; no old Java roots found.
- Exactly 16 test resources are present under `src/test/resources/world/mongoose/banteng`, with zero under the old resource root.
- No `package moo`, `import moo`, old-path Java tree, or `module-info.java` remains. Alias grep found only ordinary MOO object/preposition aliases, not compatibility namespace code.
- `ChangePackageTest` loads the named packaged recipe and requires one changing cycle; `RecipeCatalogTest` resolves all nine named recipes. `NamespaceOwnershipTest` enforces all eight roots/counts, all 16 resources, service provider, build/launchers, runner proof, JFR template, residual classification, and old-root/module absence.
- CI proof creates old-package fixtures in both errorprone-checks and rewrite-recipes, asserts the root dry-run patch moves both, removes the fixtures/patch, then asserts the exact repository fixed point. Generic RewriteTest covers package/import behavior.
- Service provider and Error Prone package predicate are correctly retargeted. JFR annotations, recording consumers, and all seven JFC names are consistently retargeted.
- Two targeted `rg` commands using Bash brace expansion failed under PowerShell; no conclusion was drawn from those failures. They will be rerun with directory-wide filters.
- Corrected directory-wide recipe scan confirms #9/#12/#13/#14 production constants, generated paths/imports, fixtures, recipe IDs, and fixed-point expectations all use `world.mongoose.banteng`; only `ChangePackageTest` retains old-package input snippets.
- `git grep` finds 76 old namespace-shape occurrences, all confined to named historical reports, migration proof/input, Toast CMake paths, the residual report, and the audit implementation. Non-historical output was enumerated and matches those categories exactly.
- Residual report explicitly documents migration proof, external MOO/conformance/runtime identities, Toast paths, and line-classified historical evidence; the current implementation plan has no old Banteng package/selector/path references in the targeted scan.
- Exact topology: merge-base is `f7784aa`, 8 commits, 197 files changed, 1945 insertions, 1356 deletions; `git diff --check` produced no output.
- Live CI progressed: CodeQL now PASS; Static analysis & tests remains FAILURE at step 9, namespace proof step skipped; MOO conformance still running. Job annotations expose only exit code 1 and no test reports artifact yet, so failure cause is not yet known.
- Next action: obtain failed log/artifact once the run is terminal, classify the exact failure, finish open-issue/tracker and PR-scope checks, then issue the final verdict.
## Final adversarial gate (2026-08-18)

- Requested audit artifact: PR #58 commit `2ebceff12396cf2343e2df8eaa4e06ead1d7595b` on base `f7784aa2de3059beb80d3f0fa137cbe0e647a0b2`.
- Live PR head advanced during the audit to diagnostic-only `7b6ade7346143f971184a1f3c9dfda1faa2573ef` (`ci: expose rewrite recipe assertion details`), adding six test-logging lines in `rewrite-recipes/build.gradle.kts`.
- Authoritative exact-head CI failure: `ChangePackageTest.movesRecursivePackagesAndReferences` changes the caller package but leaves `import moo.example.Example`; expected `import world.mongoose.banteng.example.Example`. Job `95964933350` ran 22 rewrite tests, failed 1, and stopped before the workflow's named-recipe/fixed-point proof (step 10 skipped).
- Static audit of `2ebceff`: TDD stack and scope align; all eight Java roots are owned-namespace roots; 16/16 package-derived resources moved; no old Java root, old resource root, old package/import declaration, compatibility facade, or `module-info.java`; #9/#12/#13/#14 recipe constants and catalog are owned; build/scripts/JFR/service/residual classification are aligned.
- Tracker corrections for #10/#11/#15/#19 were separately shown to Q and are prepared for application after #33 lands; no tracker mutation belongs in PR #58.
- Verdict: **NO-MERGE**. The named declarative recipe does not satisfy its executable structural-migration contract. Diagnose and fix from the exact import diff, remove diagnostic logging, obtain fixed-point and full exact-head CI proof, then re-audit.

## Replacement-head final audit (2026-08-18)

- Target: PR #58 exact replacement head `30013be9969603967155d0c3e0f8f1a073b23774` against live issue #33 and repository/global guardrails.
- Required focus: literal stock recursive `ChangePackage` plus its companion mechanism, TDD/history, documentation and residual classification, CodeQL alerts #107-109 as alleged base-identical rename re-alerts, closing phrase, and narrow scope.
- State: evidence collection not yet complete; no verdict yet.
- Current blocker: none. The previous head's import-retarget failure must be shown repaired by executable source and exact-head evidence rather than inferred from CI color.
- Next action: inspect exact commit topology/diff, live PR/issue/check and CodeQL alert data, recipe implementation/tests/fixed-point workflow, and branch-versus-base alert locations. Remain read-only and do not rerun, push, or merge.

## Exact final-head audit (2026-08-19)

- Target: PR #58 head `4e0fd4db03fe33e068f6c7f43b22f9dc516ec7df`, exact base `f7784aa2de3059beb80d3f0fa137cbe0e647a0b2`, live issue #33.
- Worktree is clean and exactly tracks the remote branch. PR is open, non-draft, mergeable/CLEAN, and contains literal `Closes #33`.
- Exact-head raw checks are all successful: Static analysis & tests, MOO conformance, CodeQL, Dependency review, and Workflow static analysis.
- Post-`eb429d0` repair is narrow: `117dca9` changes only `scripts/rewrite.sh` mode `100644 -> 100755`; `4e0fd4d` adds 33 independently labeled Maven Central verification hashes required by the real rewrite invocation.
- Static audit remains aligned: literal stock recursive `ChangePackage` runs before the narrow single-segment companion; tests require one changing cycle/fixed point and preserve `moo`-named instance access; residual classifier is line-exact and documentation names each proof class; all eight Java roots, 16 resources, service/JFR/build/scripts/docs are owned; no old package tree, alias/facade, or module descriptor.
- CodeQL comments #107-109 match base alerts #31/#32/#34 at identical source lines/messages; only the package/path spelling changed. Treat as base debt, not branch semantic regressions.
- Reviews/comments: no issue comments, three GitHub Advanced Security review comments only, no human review decision.
- Current blocker: none found yet. Next action is to inspect exact fixed-point job log output and dependency-metadata scope, then issue MERGE/NO-MERGE without rerun or mutation.

## Exact-head 39b476b final gate

- Retargeted authority: PR #58 exact head `39b476b433395f81fac0ae11547b93b94732e255`, exact base/current master `f7784aa2de3059beb80d3f0fa137cbe0e647a0b2`; base ancestry is direct and GitHub reports MERGEABLE/UNSTABLE while checks run.
- Exact tree: 269 tracked files, 172 Java files, all 172 beneath `world/mongoose/banteng` with path/package congruence; zero old path segments, package declarations, imports, or resources; 16 owned package resources.
- Recipe catalog: nine YAML IDs equal nine catalog-test IDs; eight custom recipe classes. `ChangePackage` composes stock recursive `ChangePackage` with `RetargetSingleSegmentPackageReferences`.
- Companion semantics are attributed and bounded: imports with the old root retarget; field accesses require an old-root source name, an old/new migrated type package, and exact equality to the attributed old type name. Tests prove package/import migration, attributed fully-qualified migration, fixed point, and preservation of `moo.example` through a `moo`-named instance.
- Current docs now contain zero old namespace shapes in all six previously stale reports and exact new references. Historical classification is line-exact via `HISTORICAL_NAMESPACE_EVIDENCE`, not filename-wide.
- Residual inventory: 93 old namespace-shape lines in 15 files; 65 broad lowercase-`moo` files, all inside audited roots. The companion's sole bare production residual is required `OLD_PACKAGE = "moo"` and is line-exact classified; its unnecessary prose residual was removed.
- Scope/topology: 204 changed paths = 184 renames, 15 modifications, 5 additions; 17 branch commits; 2257 insertions and 1377 deletions. The final two commits are a test-only exact residual classifier followed by one production description reword.
- Tracker packet: live #10/#11/#15/#19 remain unchanged with old future targets and issue #33 has no public comments. The separately prepared correction packet remains intentionally unpublished pending its separate review; no tracker mutation belongs in this PR.
- Current exact-head checks observed: Workflow static PASS, Dependency review PASS; Static analysis/tests, CodeQL, and MOO conformance still in progress. No merge verdict until all exact-head required checks are terminal.
- Next action: re-read exact-head check rollup when naturally terminal, inspect any failure read-only, then return MERGE only if all required checks pass and no head advance occurs.

## Exact-head eb429d0 final gate

- Retargeted authority: PR #58 exact head `eb429d0ef8887e041000803908eb7a71157e6815`, based on `f7784aa2de3059beb80d3f0fa137cbe0e647a0b2`; base ancestry check returned zero.
- New delta is tests-first: `c4c4356` changes only `NamespaceOwnershipTest`, replacing whole-file fixture allowance with exact stripped-line classification and a negative unrelated-`moo` assertion; `eb429d0` changes only the residual report to enumerate the six permitted ChangePackage fixture forms and negative control.
- Preserved issue-33 worktree is currently checked out at `3d3d899`, not the PR head. All final source/topology evidence must therefore be read directly from commit `eb429d0`; no checkout, edit, or ref mutation is authorized.
- Final state: source and topology checks align, but exact-head run `32220387159` failed `Verify namespace rewrite coverage and fixed point` before either recipe proof ran: `scripts/rewrite.sh: Permission denied` (exit 126). Both base and head store the script as mode `100644`, while the new CI step invokes it directly at lines 105 and 111.
- Verdict: **NO-MERGE**. Make the documented shebang script executable (`100755`) or otherwise repair the direct invocation, then require exact-head CI to prove both fixture moves and the empty second dry run. No rerun, edit, push, or merge was performed by this audit.
