# Issue #7 delivery notes

## Current findings

- Parent assigned end-to-end issue #7 delivery in a new isolated worktree at `C:\Users\Q\code\banteng-agent-issue-7` on branch `agent/issue-7-promote-numbers` from current `origin/master`.
- Required workflow: focused RED tests committed separately without running local builds/tests, narrow production implementation, push, non-draft PR with literal `Closes #7`, then exact-head CI/review monitoring without merge.
- The `babysit-pr` skill and its CI/review heuristics have been read. Branch-related failures may be fixed; unrelated/flaky infrastructure failures must not be patched around.
- No Gradle or local tests may be run for this task.
- Live issue #7 has two parts: full Mongoose `PROMOTE_NUMBERS` runtime semantics, plus removal of build-phase vocabulary from operator-visible compiler/v17-codec diagnostics.
- Current `origin/master` is `4880f025b2e396548bce7cbeb93a670a46b4f2e6`; the isolated worktree and requested branch were created from that exact commit.
- `Banteng.promoteNumbers` is parsed but unused. Production construction currently flows `Banteng -> MooServer -> MooRuntime`, while `MooRuntime` constructs a default `MooVm` and `BuiltinCatalog` with no value-semantics configuration.
- `MooVm` currently accepts homogeneous integer or float arithmetic, permits float-base/integer-exponent power, rejects other mixed arithmetic and ordering, and preserves integer/boolean equality.
- Named ToastStunt authority commits show promotion for mixed int/float binary arithmetic, equality/order comparisons, the unary MATH_FUNC family (`sqrt` through `floor`), and `atan`/`atan2`. In that exact range, `round`, `trunc`, `frandom`, and `floatstr` remain float-only.
- Current AST and `MooValue` families are sealed and the compiler/codec already cover their concrete variants. The remaining listed fallbacks are defensive diagnostics, not evidence of a presently representable missing construct; they should use domain vocabulary.

## Observations

- The primary checkout is not an allowed implementation location; all implementation must occur in the isolated worktree.
- This note is checkpoint bookkeeping only and must remain unstaged, uncommitted, and unpushed.
- No source or test edits, worktree creation, commits, pushes, or GitHub mutations have occurred yet.
- Worktree creation has now occurred; no source/test edits, commits, pushes, or GitHub mutations have occurred.
- The primary checkout was already heavily dirty with unrelated untracked notes. It has not been used for implementation; this task note is the only task-owned primary-checkout file.

## State

- Skill intake, memory quick-pass, live issue inspection, exact-base worktree creation, current-code survey, and named ToastStunt authority review are complete.
- Focused RED tests are committed separately as `1ccd297` (`test: specify promote numbers semantics (#7)`). No local test/build execution occurred before or after that commit.
- Production implementation is in progress: `ValueSemantics` exists; `MooVm` consumes it for mixed arithmetic, recursive equality, ordering, and membership; `BuiltinCatalog` constructor/configuration and promoted math contracts/handlers are being threaded; `MooRuntime` now owns matching VM/catalog configuration.
- Production work remains uncommitted.
- Production was committed separately as `1534a80` (`feat: wire promote numbers semantics (#7)`) without local build/test execution.
- During implementation, shared `origin/master` advanced from `4880f025...` to merge `daa0d5e...` for issue #3. Because the branch was still unpublished, it is being rebased onto the current base before push.
- Rebase step 1 stopped on two import-only conflicts in `BuiltinCatalogTest` and `MooVmTest`; resolution retains both issue #3 `ObjectFlags` imports and issue #7 `ValueSemantics` imports. The rebase has not yet continued.

## Blocker

- None currently.
- No external blocker. Rebase continuation may reveal production overlaps because issue #3 touched `BuiltinCatalog`, `MooRuntime`, and `MooVm`; any resolution must preserve both independently scoped changes.
- Parent explicitly requires the exact PR title/body packet before `gh pr create`; implementation, commit, and push preparation may continue, but PR creation must pause for that handoff.

## Next action

- Stage the two resolved test imports, continue the rebase, resolve any production overlaps without dropping issue #3 changes, then inspect the rebased two-commit diff. Prepare the exact PR packet for the parent before any `gh pr create`; no local Gradle/test execution.

## 2026-08-18 PR scope-gate update

- PR #45 is open and non-draft at `https://github.com/MongooseMoo/banteng/pull/45`.
- Exact head is `9d8da65ee6d6d17327750791464b6d9b2f9283e2`. Its six CI checks are terminal and passing; GitHub reports `MERGEABLE` / `CLEAN`; the continuous watcher remains active. The PR has not been merged.
- The branch has the separate RED and production commits plus `9d8da65` (`fix: complete value semantics catalog delegation (#7)`), which repaired the branch-owned constructor compile failure reported by the first CI run.
- Final scope review found two unresolved literal requirements: the issue calls for the Mongoose `test_math.rb` promotion cases as conformance tests, while the PR currently adds only Java tests; and the unsupported compiler/v17 sites need a construct-by-construct audit rather than rewording alone.
- The pinned Mongoose promotion test commit `167b814` adds exactly two program cases: `return 1 + 0.0` and `return 1.0 + 0`, both expecting `1.0`.
- The external `MongooseMoo/moo-conformance-tests` main branch currently resolves to `6528b8cf6d72c065c722469b070fea16d626e602`; a tracked `src/moo_conformance/_tests/language/promote_numbers.yaml` already exists there. Its exact content and execution/profile wiring still need inspection.
- Banteng CI currently checks out the external suite and runs the stock profile with `--promote-numbers=false`; therefore the existing green conformance job does not prove the promotion cases.
- `docs/reports/supported-conformance-profiles.md` says optional promotion proof must use an explicitly scoped command and minimal disposable fixture, and must not broaden the stock/ToastCore managed profiles.

### Current blocker

- No external blocker. The branch is green but not yet merge-ready because the two literal scope gates are still under audit.

### Next action

- Inspect the external promotion YAML at the exact remote main head and determine the narrow Banteng CI selector/second invocation needed to execute it with `--promote-numbers=true` without altering the stock profile.
- Enumerate all compiler and v17 sealed variants, tag dispatches, and fallback paths against ToastStunt v17 source; add focused structural/behavioral RED contracts or exact reachability evidence, then make only scoped branch changes.
- Keep PR #45 open, preserve the exact-head watcher, run no local Gradle/tests, and do not merge.

## 2026-08-18 audit and rebase update

- Added `9a53e83` (`test: port mongoose promotion conformance (#7)`) with the exact two `test_math.rb` cases from Mongoose commit `167b814`.
- Added `94d7e6a` (`ci: run promote numbers conformance (#7)`) to run only the Banteng-owned promotion suite with `--promote-numbers=true`, using the external suite's trusted candidate-root/suite-root mechanism. The stock managed profile remains unchanged and still runs with promotion disabled.
- Added `e467230` (`test: specify audited unsupported diagnostics (#7)`) before `5ab9043` (`fix: close audited unsupported diagnostics (#7)`). The audit found all 9 sealed statement variants, all 5 assignment targets, all 10 sealed runtime value types, and every durable Toast v17 tag implemented. Standalone splice/scatter nodes are contextual pseudoexpressions, not missing syntax constructs.
- Production audit closure gives splice/scatter exact context diagnostics, changes unreachable closed-family statement/target fallbacks to assertions, removes the unreachable sealed-value writer default, and preserves the necessary unknown external v17 tag error with precise pending/task/object/nested-value locations.
- No local Gradle build or tests were run. Only `git diff --check` was used before the follow-up commits.
- `origin/master` advanced to `f507d1bd7f2a0b8401cd83c2815991ce52bbbd13` through issue #6 ServerLog routing. Before rebase, remote PR head was explicitly verified as `9d8da65ee6d6d17327750791464b6d9b2f9283e2`; that is the required force-with-lease expectation.
- Rebase onto `origin/master` is active and stopped while applying the issue #7 production commit. Conflicts are limited to the expected ServerLog/value-semantics overlaps in `Banteng`, `BuiltinCatalog`, `LambdaMooV5Reader`, `MooRuntime`, and `MooServer`.
- `Banteng` is being resolved to pass both the shared `ServerLog` and `ValueSemantics`; `LambdaMooV5Reader` is resolved to retain ServerLog routing with the new domain-vocabulary validation messages.

### Current blocker

- Rebase conflicts remain in `BuiltinCatalog`, `MooRuntime`, and `MooServer`. They require combined constructors/configuration that preserve both issue #6 logging ownership and issue #7 promotion semantics.

### Next action

- Finish the three combined constructor resolutions, verify no conflict markers and inspect the exact staged diff, continue all seven rebase commits, then compare final history/diff against current master.
- Force-push only with `--force-with-lease=refs/heads/agent/issue-7-promote-numbers:9d8da65ee6d6d17327750791464b6d9b2f9283e2`, restart exact-head CI/review watching, fix only branch-owned failures, and do not merge.

## 2026-08-18 conflict-resolution checkpoint

- All five rebase conflicts are resolved and staged; no conflict markers remain and `git diff --check` is clean.
- `Banteng` now opens/passes the issue #6 shared `ServerLog` and also passes issue #7 `ValueSemantics` into `MooServer`.
- `LambdaMooV5Reader` keeps all validation output on `ServerLog` while using object-reference, inheritance-cycle, and relationship domain vocabulary.
- `BuiltinCatalog` now has combined full constructors that own both `ValueSemantics` and `ServerLog`; legacy/config-only overloads delegate with standard semantics or the default log as appropriate.
- `MooRuntime` and `MooServer` likewise preserve existing overloads while adding combined log/semantics construction used by the CLI path. Runtime constructs both `MooVm` and `BuiltinCatalog` from the same configured semantics and passes the shared log to the catalog.

### Current blocker

- No unresolved file conflict. The rebase is paused only because `git rebase --continue` has not yet been invoked after staging.

### Next action

- Continue the rebase, resolve any later commit replay conflicts, inspect final history/diff, and perform the already specified exact leased force-push. Then restart the babysit watcher on the new exact head; do not merge.
