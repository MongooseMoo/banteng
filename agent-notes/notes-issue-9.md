# Issue #9 working notes

## Current state

- Live issue `MongooseMoo/banteng#9` is open.
- Fresh isolated worktree: `C:\Users\Q\code\banteng-agent-issue-9`.
- Branch: `agent/issue-9-builtin-hosts`.
- Exact base: normal merge `64c2ce3b7f49f5292b47722a1c04a26d5df1993a` (PR #49 / issue #16).
- Tests-first commit: `62bc365b84fd1ceab487d10dce0864878b16f49e test: define BuiltinCall and BuiltinHosts contracts`.
- No local Gradle/build/test command has been run, per parent instruction.

## Findings and observations

- `BuiltinHandler` currently declares a ten-argument abstract `invoke` plus an eleven-argument default overload adding `threadMode`.
- Current `BuiltinCatalog` has 20-plus telescoping constructors. The literal replacement surface is exactly `(BuiltinHosts)` and `(ListenerControl, BuiltinHosts)`.
- The 14 builder-owned catalog dependencies are `ValueSemantics`, 12 `BuiltinHandler` hooks (`queuedTasks`, `killTask`, `read`, `threadPool`, `threads`, `connectionOptions`, `dbDiskSize`, `flushInput`, `outputDelimiters`, `queueInfo`, `taskStack`, `resumeTask`), and `ServerLog`.
- The current catalog has four anonymous `BuiltinHandler` implementations (`setThreadMode`, `allMembers`, `sort`, `callFunction`), not the three named in the older issue body. All four must collapse to record-based lambdas.
- The duplicated handler signature also exists in `MooRuntime.read`, `PublicationScheduler.threadPool` / `resumeTask`, and four `TaskRegistry` host methods. These should accept `BuiltinCall` directly so method references remain honest.
- 142 of 152 `new BuiltinCatalog` sites are no-argument construction. The remaining sites configure one or more hosts, listener ownership, or `ValueSemantics` and must migrate to the builder.
- Existing `rewrite-recipes` is the merged single build-only recipe infrastructure; issue #9 should add `UseBuiltinCall` there rather than recreate the issue's stale `buildSrc` suggestion.
- RED tests pin: all eleven `BuiltinCall` components, one-method `BuiltinHandler`, every defaulted builder dependency, exactly two catalog constructors, and a representative executable ten-parameter lambda rewrite.
- Added but not committed: `BuiltinCall`, `BuiltinHosts` with 14 builder-owned dependencies and behavior-preserving defaults, and the one-method `BuiltinHandler`.
- `BuiltinCatalog` now has exactly the two decided constructors and stores one `BuiltinHosts` record instead of the individual host-hook fields. The four anonymous handlers have been collapsed to record-based lambdas.
- The old 21,757-character constructor telescope was removed from the clean issue-9 worktree. Current catalog host references are being changed to `hosts.*()` accessors.
- AST inspection finds 152 remaining ten-parameter handler lambdas in `BuiltinCatalog`; 150 use the short `(a, w, p, t, id, rt, rs, r, cp, c)` spelling and two use descriptive names. Additional short lambdas exist in runtime/tests.
- A token-aware structural rewrite migrated all 152 catalog lambdas to `call ->` plus `BuiltinCall` accessors; AST inspection now reports zero ten-parameter lambdas in `BuiltinCatalog`.
- `TaskRegistry`'s four host operations, `PublicationScheduler.threadPool` / `resumeTask`, and `MooRuntime.read` now accept `BuiltinCall` directly.
- `MooRuntime` now builds one `BuiltinHosts` through a private composition method and passes it to the standalone or listener-owning catalog constructor. `MooVm` supplies value semantics through the builder.
- All 142 no-argument catalog test constructions have been mechanically migrated to `BuiltinHosts.builder().build()`. The remaining specialized test constructors are being expressed as builder configuration.
- Specialized catalog callers are now expressed through `BuiltinHosts.Builder`; current production diff is uncommitted on tests-first head `62bc365b84fd1ceab487d10dce0864878b16f49e`.
- `UseBuiltinCall` now exists in the build-only `rewrite-recipes` project and is activated as `moo.builtin.UseBuiltinCall` in `rewrite.yml`. Its committed RED test exercises descriptive and short legacy parameter names. CI still must prove the inferred OpenRewrite API compiles and performs the rewrite.
- Direct test calls into the migrated `TaskRegistry` methods now construct `BuiltinCall`; `PublicationSchedulerTest` has the same migration. `ServerLogRoutingTest` now recognizes `BuiltinHosts` as the catalog's shared-log owner.
- Static inspection currently finds exactly two public `BuiltinCatalog` constructors and only record-based host invocation. `git diff --check` is clean. No local Gradle/build/test command has been run.
- `origin/master` advanced during the work and was last observed at `8369c112d9cf30f5dd096b7c2af0116ea5244ba4`; production must be committed and rebased onto the then-current exact master before publication.
- Production is now committed separately as `71da897919f486db0abf62f2c6398337df78cdc4 refactor: compose builtins through invocation records`; tests remain the preceding `62bc365` commit.
- A fresh `git fetch origin master` confirms exact current master `8369c112d9cf30f5dd096b7c2af0116ea5244ba4`. The issue branch is still on its older base and is clean immediately before rebase.
- The branch rebased successfully onto `8369c112d9cf30f5dd096b7c2af0116ea5244ba4` as tests commit `88fd195` plus production commit `9f8ecb7`, and PR #50 was created non-draft with the exact Q-reviewed packet.
- First exact-head CI on `9f8ecb7` found two branch-owned failures: missed legacy `dumpDatabase` method-reference signature and a redundant `(J)` cast in `UseBuiltinCall`. Commit `9f0367ddf3b93bb8b3222e9ee67473ac17743b3a` fixed and pushed both without local builds/tests.
- Replacement CI compiles production and now fails only `UseBuiltinCallTest` with `ClassCastException`. OpenRewrite 8.89.0 source confirms `JavaTemplate.apply` visits the lambda cursor and returns the rewritten `J.Lambda` when the coordinate is `LAMBDA_PARAMETERS_PREFIX`; assigning that result to `J.Lambda.Parameters` caused the runtime cast.
- The pending narrow correction assigns the template result to `J.Lambda` and then attaches the already rewritten body. The before/after recipe proof remains unchanged.
- Commit `2fa0b700a4390f4a96bcd15cc7a30946bae06a2c` applied that inferred `J.Lambda` return-shape correction, but exact-head run `32189243909` reproduced the identical `ClassCastException` in `UseBuiltinCallTest`.
- Terminal run state: four checks passed; only `Static analysis & tests` failed. GitHub artifact API returned exactly one artifact, `conformance-results`; the workflow upload path omits `rewrite-recipes/build`, so no Java/XML report exists to download.
- OpenRewrite 8.89.0 source shows `JavaTemplate.doApply` passes changed expressions through `ParenthesizeVisitor.maybeParenthesize`; a parameter-coordinate rewrite can therefore return `J.Parentheses` despite the generic `J.Lambda` assignment, explaining the repeated cast.
- Parent authorized the source-grounded resolution: remove the parameter `JavaTemplate`, rename the first valid lambda parameter AST to `call`, replace parameters with that singleton, set `parenthesized(false)`, and retain the existing typed accessor rewrite plus unchanged before/after proof.

## Blocker

- No external blocker. Exact-head CI is the compilation/test authority because local Gradle/build/test execution remains forbidden.
- `scripts/rewrite.sh --apply` invokes Gradle and therefore cannot run locally under the explicit no-local-build/test instruction. CI must execute the custom recipe test.

## Next action

1. Implement, commit, and push the authorized direct lambda-parameter AST construction as a PR #50 CI fix.
2. Restart exact-head continuous watcher and diagnose only new branch-owned failures; rerun unrelated flakes only under watcher policy.
3. Never merge; remain on watch until a strict watcher stop condition or parent/user intervention.
