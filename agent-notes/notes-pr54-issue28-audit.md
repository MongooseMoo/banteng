# PR 54 / issue 28 adversarial audit

## State

- Read-only audit target: `C:\Users\Q\code\banteng-agent-issue-28` at exact head `bfa7e782fe593846d89d929607295b5ee28dd53f`.
- PR base and live GitHub `master`: `d212436d37cfc022be736469ec4d27f421322d69`.
- Worktree was clean and tracks `origin/agent/issue-28-v17-hierarchy-validation`.
- No build, test, edit, push, or merge was performed in the issue-28 worktree.

## Aligned findings

- Validator order is repair/type phase, parent and location cycle phase, then four reciprocity directions.
- The phase-3 directions match pinned Toast `ng_validate_hierarchies`: location to contents, contents to location, parents to children, children to parents.
- Missing positive object references are removed from list fields; missing scalar location becomes `#-1`.
- The test commit is a parent of the production commit and contains only the test class plus twelve fixtures. Production commit modifies only `LambdaMooV17Codec`.
- The base contains normal merges for issues #16, #18, and #52; PR #54 has no file overlap with #52 and is already based on all three.

## Blocker

- Pinned Toast source is exact WSL commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Toast phase 1 retains `NOTHING` (`#-1`) entries in parents, children, and contents lists, and Toast phase 3 skips them. PR #54 intentionally retains `-1` in `repairReferences` and skips it in hierarchy checks.
- However, the restored list reaches unchanged `WorldHistory.validateTopology`, which requires every parent/content/child ID to resolve and requires each list unique. It therefore rejects list `#-1` and duplicate links after the codec validator, even though pinned Toast admits them. This violates issue #28's complete admission/repair boundary.
- New fixtures cover invalid top-level field types, missing positive references, both cycles, and all four reciprocity directions, but do not cover valid list `#-1`, duplicate link admission, or non-object elements inside otherwise valid lists.

## Next action

- Finish exact source/line evidence and current CI snapshot, then recommend NO MERGE until the downstream world-topology admission conflict is resolved with focused fixtures. Do not implement during this audit.
