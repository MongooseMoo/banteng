# Issue 13 checkpoint

- Worktree: `C:\Users\Q\code\banteng-agent-issue-13`
- Branch: `agent/issue-13-opcode-families`
- Exact base/HEAD before commits: `2920a6a45b4ac32652ec1478660b94d9aeff4ec0`
- Live issue: decompose `MooVm` into `ListOps`, `PropertyOps`, `IndexOps`, `ArithmeticOps`, `LoopOps`, and `ErrorOps`; read/dispatch the raw opcode exactly once; use exhaustive narrowed family operations; collapse comparison to one three-way mapping; custom static-method move recipe; `applyBuiltinResult` is out of scope.
- Current source observation: issue prose about zero instance fields is stale because `MooVm` now owns `ValueSemantics`; architecture decision still applies. The repository now has one supported `rewrite-recipes` subproject and no `buildSrc`, so the custom recipe belongs there.
- Current uncommitted RED files in the isolated worktree:
  - `src/test/java/moo/vm/OpcodeFamilyArchitectureTest.java`
  - `rewrite-recipes/src/test/java/moo/rewrite/MoveMooVmOpcodeFamiliesTest.java`
- RED contracts cover six owners, one raw `instruction.opcode()` read plus one raw switch, narrowed exhaustive `Operation` switches without defaults/raw opcode types, moved concerns absent from `MooVm`, shared comparison mapping, input-driven promotion of distinct method bodies to all six owners, collision no-op, and declarative recipe activation.
- No Gradle/build/test/OpenRewrite command has been run, per instruction.
- Blocker: none. Next action: inspect static diff, commit tests-only, then extract production method closures and implement the recipe without running local builds/tests.

## Production extraction checkpoint

- Tests-only commit: `913bb60 test: enforce opcode family decomposition` (two test files only).
- Uncommitted production files now exist for all six required owners plus a modified `MooVm.java`.
- `MooVm` has been mechanically reduced to the central dispatch and builtin concern; `applyBuiltinResult` remains there and is only package-visible for `ListOps`, matching the live issue's explicit out-of-scope boundary.
- `IndexOps` and `LoopOps` were regenerated from the exact committed `MooVm` method bodies after catching an inclusive line-boundary extraction error; the bad intermediate content was not committed.
- Remaining work before any production commit: replace the tick-count opcode switch with an enum set so raw opcode is switched once; restore exact PropertyOps body structure rather than retain incidental cleanup; run read-only symbol/source/diff checks; add the custom recipe and YAML activation; ensure formatting/static imports by inspection only.
- No local Gradle/build/test/OpenRewrite command has been run.

## Recipe API checkpoint

- All six helper owners remain uncommitted; the old tick-count raw opcode switch has been replaced by set membership, leaving the central raw switch as the only current-opcode switch.
- `PropertyOps` was regenerated to preserve the committed method bodies except for the deliberate future-instruction liveness scan rewrite that removes its opcode switch.
- A read-only `javap` attempt found no `C:\Users\Q\.gradle\caches\modules-2\files-2.1\org.openrewrite\rewrite-core\8.89.0` directory, so no OpenRewrite API signature was inferred from a missing jar.
- Current blocker: exact `ScanningRecipe` generation signatures are not cached locally. Next action: search other isolated worktrees/current PR #51 recipe source for the already implemented 8.89 pattern, read-only, then implement in the sole `rewrite-recipes` project.

## 2026-08-18 read-only adversarial audit

- Clean local head is `ad86b00654e404d6146702ffc0d81c26647847a7`, exactly two commits over `origin/master` `2920a6a45b4ac32652ec1478660b94d9aeff4ec0`: tests-only `6363ad4`, then production `ad86b00`. Aggregate scope is 12 files.
- The six required package-private static owners exist; production contains exactly one `instruction.opcode()` occurrence, in `MooVm`, and one `switch (opcode)`. No family reads the raw opcode. Only `moo.vm` production files change, so current allowed package dependencies and cycle direction remain unchanged.
- Literal compiler-exhaustiveness is not satisfied: the central dispatch and each family `execute` use ordinary statement switches over enum selectors. Under JLS 25 section 14.11.2, enum switch statements are legacy switches and need not be exhaustive. Adding an opcode or family `Operation` can therefore compile while silently doing nothing. The RED test only checks for `switch (operation)` and absence of `default`, which does not prove compiler enforcement.
- The OpenRewrite proof is not a real-body promotion proof. Its fixture contains six independent one-line methods with no callers. The recipe deletes private static methods from MooVm and generates private methods in cloned helper classes, but it has no method-invocation retargeting, no visibility promotion, and no dependency-closure/cross-family rewrite. Applying it to the real pre-refactor MooVm would leave unresolved unqualified callers and inaccessible private helper methods before the hand rewrite.
- Pending PR #51 is currently OPEN at `3585b1fcb9faacb137f0bee7356b5b858dcc2e63`, also based on `2920a6a`. Exact changed-file overlap is only `rewrite.yml`. `git merge-tree` reports it changed in both but produces a clean combined result: #13 prepends its recipe document while #51 appends its 95-line recipe document.
- No edits, rebase, build, tests, OpenRewrite, push, or publication were performed in the issue #13 worktree. Next action is to report the two literal blockers; after they are repaired locally, wait for #51's normal merge, rebase the two-commit branch onto the exact merge commit, retain both YAML documents, and repeat static package/opcode/recipe checks before publication.
## 2026-08-18 blocker repair authorization

- Parent authorized local, tests-first repair of all three issue #13 blockers.
- The owned issue-13 worktree was clean at `ad86b00654e404d6146702ffc0d81c26647847a7` before repair.
- RED scope: require compiler-exhaustive family switch expressions; replace the toy recipe proof with a real-source caller-retargeting and dependency-closure contract that fails closed; add explicit NaN relational/equality mapping coverage preserving prior semantics.
- GREEN scope: narrow production/recipe repairs only after RED commits, with static verification only.
- Keep the branch based on `2920a6a45b4ac32652ec1478660b94d9aeff4ec0` until PR #51 merges. Do not run Gradle/OpenRewrite, rebase, push, or publish.

## 2026-08-18 repair findings checkpoint

- Structural RED currently accepts ordinary `switch (operation)` statements in all five narrowed enum families; those statements are not compiler-exhaustive. Next edit requires `return switch (operation)` in each family.
- `ArithmeticOps.primitiveDoubleCompare` maps NaN to integer zero, which makes `<=` and `>=` true. The pre-refactor implementation used primitive relational operators, so all four relational results were false for unordered operands; `FloatValue.equals` also makes separate NaN values unequal.
- The recipe test currently uses six unrelated one-line static methods. The recipe ignores the actual instance `membership`, `arithmetic`, `equality`, `mooEquals`, and `comparison` methods, has no caller retargeting, and admits incomplete family inventories.
- The base source inventory confirms the recipe needs the exact moved method set, all four `raiseError` overloads, the `CollectionElement` nested record dependency, `normalize`, and `target`. Instance arithmetic-family methods need an explicit `ValueSemantics` parameter after promotion, with every moved or retained caller retargeted.
- Next action: patch and commit only the structural, NaN, realistic recipe, dependency-closure, and fail-closed RED contracts; production remains untouched until those commits exist.

## 2026-08-18 repair implementation checkpoint

- RED commits are complete: `5f8bd42` requires compiler-exhaustive family dispatch and exercises the NaN truth table; `e25aacd` replaces the six toy methods with instance arithmetic/comparison, caller-retargeting, full-family closure, overload rejection, and nested-record contracts.
- GREEN commits complete so far: `eb4c9ef` converts all five narrowed family dispatchers to `return switch (operation)` expressions; `32bbca8` introduces explicit LESS/EQUAL/GREATER/UNORDERED mapping so NaN is false for every relational operator while equality remains unequal.
- Recipe production is currently modified but uncommitted. It now captures instance methods, requires the exact method-name/count inventory including four `raiseError` overloads, captures `CollectionElement`, adds `ValueSemantics` to promoted instance arithmetic methods, strips private visibility, and retargets unqualified callers across families.
- Static OpenRewrite 8.89 source inspection confirmed the used `MethodDeclaration` and `MethodInvocation` mutation APIs (`isConstructor`, `withParameters`, `withMethodType`, `withSelect`, `withArguments`) exist. No recipe/build/test execution occurred.
- Remaining risk: statically inspect recipe formatting/type details and fixture completeness, then commit the production recipe only if the diff is coherent. Keep base `2920a6a`; do not push/rebase/run Gradle/OpenRewrite.

## 2026-08-18 post-#12/#9 rebase checkpoint

- Fetched `origin`; exact `origin/master` is the required `b27ee872add4f551bc27f9e71dcc3fc850827b8f` merge of PR #50, with PR #51 already in its ancestry.
- The clean seven-commit #13 branch rebased without conflicts from `2920a6a` onto `b27ee872`; new clean head is `bb0a3d6806ddb6088ac09b6dbf0928748380a2a2`.
- `git range-diff` maps all seven commits in order. Six are patch-identical; the NaN/exhaustive RED differs only because landed issue #9 added the `BuiltinHosts` import context in `MooVmTest`.
- Current `rewrite.yml` retains the #13 recipe plus landed `ExtractBuiltinResult`, `UseBuiltinCall` (#9), `UseStringValueFactories`, `ShareDbReaderInfrastructure`, unary-float, and `DecomposeToastV17ProgramLayout` (#12) recipes. The rebase reported no rewrite.yml conflict.
- Live issue #13 remains OPEN and requires six static family owners, exactly one raw opcode dispatch, narrowed exhaustive family dispatch, shared comparison mapping, and the executable move recipe; `applyBuiltinResult` remains explicitly out of scope.
- Static diff check is empty; the worktree is clean. No Gradle/build/test/OpenRewrite command ran.
- Next action: complete the full static architecture/scope/history audit against current master, inspect recipe wiring and landed API accommodations, then prepare the exact PR title/body packet. Do not push or create a PR until root confirms Q read the packet.

## 2026-08-18 PR #56 publication and first CI checkpoint

- Q read the exact packet and root relayed Go. Pushed `agent/issue-13-opcode-families` and created non-draft PR #56: https://github.com/MongooseMoo/banteng/pull/56.
- GitHub live verification matched the approved title/body exactly. Local, remote-tracking, remote branch, and PR head all equal `bb0a3d6806ddb6088ac09b6dbf0928748380a2a2`; seven commits are present; PR is OPEN, MERGEABLE, and initially UNSTABLE.
- First one-shot watcher snapshot: actions idle; checks 1 passed, 4 pending, 0 failed; no review items; no retries.
- Continuous watcher session is active under session id `93785`. It then reported 2 passed, 1 pending, and exact-head failures in Static analysis & tests job `95948511000` and MOO conformance suite job `95948510894`, run `32212760063`; no review items.
- Direct `gh run view --job --log` attempts returned `run 32212760063 is still in progress; logs will be available when it is complete` for both jobs. No failure classification or edits have been made yet.
- Next action: keep ownership of the watcher, wait for the remaining job/run to terminate, fetch exact failed logs, classify branch ownership, patch only deterministic #13 failures, then push and restart continuous monitoring. Do not merge or run local Gradle/build/tests/OpenRewrite.

## 2026-08-18 PR #56 CI repair checkpoint

- Exact failed-head logs on `bb0a3d6806ddb6088ac09b6dbf0928748380a2a2` showed branch-owned compile misses: absent `OptionalDouble` import in `MooVm`, unqualified `setProperty` in `IndexOps`, and private visibility on `PropertyOps.setProperty`. The recipe test also asserted nonexistent `left`/`right` fixture identifiers instead of the qualified real fixture call.
- Committed and pushed only those corrections as `2ea117ae02ed4978aa7af1197a22ae0bea3eb197` (`codex: fix CI failure on PR #56`), 4 files, +6/-3. The live PR packet remains exact and PR #56 is OPEN/non-draft.
- First watcher snapshot on exact replacement head `2ea117a` was 1 passed, 4 pending, 0 failed. Exact conformance job `95949278737` then failed while other jobs remained pending.
- Raw conformance log shows one deterministic branch-owned `-Werror` failure at `ArithmeticOps.java:321`: Error Prone `LongDoubleConversion` for `dividesByZero(operation, right.value())`; it explicitly recommends `(double) right.value()`.
- Continuous watcher session `99350` was interrupted before the next edit. No local Gradle/build/test/OpenRewrite command ran.
- Next action: apply only the explicit double cast, commit and push it, inspect the still-running Static analysis & tests job for any independent failure, then restart exact-head continuous monitoring. Never merge.

## 2026-08-18 post-green audit repair checkpoint

- Follow-up cast commit `b09adf34e0220742af3e8d9c23c8817b1ef495f7` passed all 6 checks on exact head; raw watcher state was OPEN, CLEAN, MERGEABLE, 6 passed, 0 pending, 0 failed, and no review items. It was not merged.
- Independent audit returned NO-MERGE for two literal gaps: the recipe collision proof used an incomplete candidate that already failed closure, and CodeQL alert #105 identified `ArithmeticOps.execute`'s required `Instruction` parameter as unused.
- The exact-head watcher session `30330` was interrupted before edits.
- Separate tests-first commit `aefe703` replaces the vacuous collision candidate with `completeMooVmSource()` plus an existing `ListOps` target, asserts zero recipe changes, adds a second-application idempotence proof, and adds a structural contract requiring `Objects.requireNonNull(instruction, "instruction")` at the arithmetic family boundary.
- No production edit for the audit findings is committed yet. No local Gradle/build/test/OpenRewrite command ran.
- Next action: add only the truthful non-null validation/import to `ArithmeticOps`, commit production separately, statically inspect the exact two-commit diff, push both commits, and restart exact-head continuous watching. Do not merge or dismiss the CodeQL alert.
