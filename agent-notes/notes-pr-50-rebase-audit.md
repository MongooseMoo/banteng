# PR 50 post-PR-51 rebase audit

- Read-only audited PR #50 exact head `ced9246a19e4c73347196a5087e5a8bd618a59e5` in `C:\Users\Q\code\banteng-agent-issue-9`.
- Worktree is clean and tracks `origin/agent/issue-9-builtin-hosts` at the same head.
- Merge base is `8369c112d9cf30f5dd096b7c2af0116ea5244ba4`; current `origin/master` is `2920a6a45b4ac32652ec1478660b94d9aeff4ec0`; divergence is 9 ahead and 13 behind.
- Pending PR #51 exact local/live head observed is `5d3e5e9d02489bab1534f0fa5623d5239f790038`, based on current master.
- Current-master overlaps: `PublicationScheduler.java`, `TaskRegistry.java`, `BuiltinCatalogTest.java`, and `TaskRegistryTest.java`.
- PR-51-only overlaps: `rewrite.yml`, `PublicationScheduler.java`, and `PublicationSchedulerTest.java`.
- Future combined-base overlap is six files. A three-tree `git merge-tree` reports only one actual conflict marker: `src/main/java/moo/runtime/PublicationScheduler.java`.
- Exact conflict is issue #9's `BuiltinCall` conversion of `resumeTask` against issue #52's atomic `TaskControlDecision` switch.
- Required combined resolution: retain `resumeTask(BuiltinCall call)` and `call.arguments()/world()/programmer()`, but use `taskRegistry.taskControlDecision(taskId, call.world(), call.programmer())` with `DENIED -> E_PERM`, `MISSING -> E_INVARG`, and `ALLOWED` continuation. Never restore the two-call `mayControl` plus `contains` sequence.
- Standalone GitHub Advanced Security CodeQL check `95885649427` fails at exact head with alert #103, rule `java/toctou-race-condition`, high security severity, at `PublicationScheduler.java:769`; the annotation identifies the unsynchronized state check/use. Workflow CodeQL is separately green, but the standalone alert is real.
- Scope is 17 files. History has nine commits: one tests-first commit, one production commit, then seven recipe/CI diagnostic/fix iterations. Do not silently squash or rewrite those boundaries.
- Post-#51 next action: verify #51 normal merge and exact merge SHA, rebase all nine commits onto that exact merge commit, resolve the single production-commit conflict with the combined shape, semantically inspect five auto-merges, force-with-lease only after verifying the old remote head is still `ced9246`, push, and require exact-head CI plus standalone CodeQL to pass.
- No edits, rebase, build, test, push, or merge were performed.
