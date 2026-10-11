# Issue #52 delivery checkpoint

- Parent transferred this lane from paused issue #33 to live issue #52.
- Issue #33 remains preserved and clean in `C:\Users\Q\code\banteng-agent-issue-33` at unpublished head `3d3d899a65427b5fc3c8193ff0950db2a69cd39a`; no #33 push or PR exists.
- Fresh isolated #52 worktree: `C:\Users\Q\code\banteng-agent-issue-52`.
- Branch: `agent/issue-52-atomic-task-control`.
- Exact fetched base/origin master at creation: `8369c112d9cf30f5dd096b7c2af0116ea5244ba4`.
- Required scope from parent: a deterministic RED contract for atomic `ALLOWED`/`DENIED`/`MISSING` task-control classification; one synchronized `TaskRegistry` decision operation; map `resumeTask` without a second lookup; no unrelated work.
- Workflow constraints: TDD tests-first with a separate test commit, no local Gradle/build/test execution, exact PR title/body shown before any push/create, and never merge.
- Current blocker: none. Next action: read the live issue body and repository instructions, inspect `TaskRegistry`, `MooRuntime.resumeTask`, and focused tests, then author and commit the smallest deterministic RED contract before production edits.
- Live issue confirmed OPEN: #52 `Make task-control authorization atomic`. The literal defect is `PublicationScheduler.resumeTask` calling synchronized `TaskRegistry.mayControl` and `contains` separately, allowing removal to change denied `E_PERM` into missing `E_INVARG`.
- Repository-specific `AGENTS.md`: none; global merge/completion/verification guardrails apply.
- RED commit complete: `b16a687` (`test: specify atomic task control decisions`). It changes only `TaskRegistryTest`, requires exact enum order `ALLOWED`, `DENIED`, `MISSING`, proves owner and wizard allowed, unrelated programmer denied, removed task missing, and reflectively requires `taskControlDecision(long, WorldTxn, long)` to be synchronized. No test/build command was run.
- Production working tree currently adds nested `TaskRegistry.TaskControlDecision`, replaces `mayControl` plus `contains` with one synchronized `taskControlDecision`, and maps its three outcomes exhaustively in `PublicationScheduler.resumeTask` without the second lookup.
- Current blocker: none. Next action: statically audit exact callers/residual old methods, inspect the narrow diff and diff-check it, commit production separately, then compare live issue scope and prepare the exact PR title/body packet before any push/create.
