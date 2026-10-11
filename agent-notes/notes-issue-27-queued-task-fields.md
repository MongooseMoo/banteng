# Issue #27 queued-task fields notes

## Current state

- Existing isolated worktree: `C:\Users\Q\code\banteng-agent-issue-27`.
- Branch: `agent/issue-27-queued-task-fields`.
- Worktree is clean and currently at `ddab89c76194bb6d822610549be781176794826e`, tracking `origin/agent/issue-27-queued-task-fields`.
- Live GitHub issue #27 is `CLOSED` because PR #36 already normal-merged this exact branch.
- Current master named by the handoff is merge commit `8369c112d9cf30f5dd096b7c2af0116ea5244ba4`.
- No local Gradle, build, or test task will be run; CI is the execution gate.

## Findings and observations

- Issue #27 requires durable queued-task fields for first source line, called verb, and full matching verb name; read/write must preserve Toast values and resumed fork activations must restore them.
- The acceptance fixture must have a non-1 first line and differing called/full verb names, with Toast -> Banteng -> checkpoint -> Toast behavior covered.
- Repository memory identifies #27 as the v17 queued-task-field gap and warns that the persistence stack has recently changed through issues #16 and #18.
- The injected researcher instruction path `C:\Users\Q\.Codex\AGENTS.md.d\researcher.md` was absent when read; no repository `AGENTS.md` path was returned by the initial worktree scan.
- PR #48 monitoring observed `stop_pr_closed` after its external normal merge; this agent did not merge it.
- PR #36 merge commit is `60e46a0105d8d656e323f2c11da2101e3f32ed7c`, with parents `d50652d1ff4b643a759812e4cd37e4a5ca85b1f9` and branch head `ddab89c76194bb6d822610549be781176794826e`.
- Against current master `8369c112d9cf30f5dd096b7c2af0116ea5244ba4`, `git rev-list --left-right --count origin/master...HEAD` reports `100 0`: the branch is wholly ancestral and has no unique commits.
- Current master retains `firstSourceLine`, `calledVerb`, and `fullVerbName`; the v17 reader reads them separately, the writer writes the retained values, and scheduler restoration propagates them into `VmState`, task registration, and recursively shifted fork-vector source lines.
- The committed fixture still has first source line `4`, called verb `audit_t`, and full matching name `audit_task_aliases audit_t*`; focused codec and scheduler tests remain in current master after #16 and #18.
- PR #36 has six terminal successful checks. No current repair or duplicate PR is warranted.

## Blocker

- None. The delegated lane was already completed and merged before this audit.

## Next action

- Leave issue #27 and its merged branch untouched; switch to the next delegated issue.
