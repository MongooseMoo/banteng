# Issue #28 delivery notes

## Current state

- Worktree: `C:\Users\Q\code\banteng-agent-issue-28`
- Branch: `agent/issue-28-v17-hierarchy-validation`
- Existing clean head: `2c83a79c0e677bd85c5643078f6e0c3218fe2f9f`
- Existing base: `b93e01485f371b4bf4391d66e8c9483a4f5cc4d5`
- Current `origin/master`: `d212436d37cfc022be736469ec4d27f421322d69`
- Initial divergence: 100 behind, 2 ahead; no tracked or untracked worktree changes.
- No local Gradle, build, test, or OpenRewrite execution is authorized.

## Existing unpublished work

1. `2a61c4f test: define Toast v17 hierarchy admission`
   - Adds only `V17HierarchyValidationTest` and 11 minimal v17 fixtures.
   - Covers four phase-1 field type failures, missing-reference repair, parent and location cycles, and both directions of parent/child and location/contents reciprocity.
2. `2c83a79 fix: match Toast v17 hierarchy admission`
   - Changes only `LambdaMooV17Codec`.
   - Repairs missing/non-permanent hierarchy references, validates parent and location cycles, and validates both reciprocity pairs.

## Audit observations

- Live issue #28 remains OPEN and requires Toast-equivalent type/missing-reference handling, both cycle kinds, both reciprocal directions, minimal fixtures, and independence from issue #16.
- The existing history is tests-first and scoped exactly to issue #28 persistence validation.
- Current master changed the same codec through #16 reader consolidation and other intervening work. `git merge-tree` reports the codec as changed in both branches but emits no conflict markers, so the two commits appear replayable with narrow adaptation.
- The old implementation still refers to the pre-#16 scanner shape at the read call site; semantic validation helpers are otherwise isolated near the hierarchy validation section.

## Next action

- Inspect current-master codec hierarchy parsing/type representation and the exact phase fixtures to confirm Toast repair semantics still fit.
- Preserve both commits, then rebase the branch onto exact `d212436` without resetting or squashing; resolve only issue #28 overlap if Git requires it.
- Perform static residual/diff audits only. Add or amend tests before production only if a literal acceptance gap is found.
- Show the exact PR title/body to the root agent before any push or PR creation. Never merge.

## Rebase and publication checkpoint

- Created a local preservation ref for original head `2c83a79c0e677bd85c5643078f6e0c3218fe2f9f`. Because repository `rebase.updateRefs` initially moved that ref, it was explicitly restored to the original hash after the rebase.
- Rebase onto `d212436d37cfc022be736469ec4d27f421322d69` completed without conflicts.
- New RED commit: `141ac7974a3faa6b3b2d3288877a9091f1bc1ae4`.
- New production commit/head: `bfa7e782fe593846d89d929607295b5ee28dd53f`.
- Stable patch IDs match both original commits exactly, proving the rebase introduced no content change.
- Branch is clean and `0` behind / `2` ahead of `origin/master`; `git diff --check` emitted no output.
- No remote feature branch and no existing PR were found.
- Exact proposed packet sent to the root agent; no push or PR creation is authorized yet.

## PR and CI checkpoint

- Q read and authorized the exact packet.
- Non-draft PR: `https://github.com/MongooseMoo/banteng/pull/54`.
- Initial exact PR head: `bfa7e782fe593846d89d929607295b5ee28dd53f`.
- Initial raw watcher state: one passed, four pending, zero failures; then two passed, two pending, one failed.
- Watcher identified sole current failed job `95892456767`, `Static analysis & tests`, on exact head `bfa7e78`; no review items.
- Root supplied the exact branch-owned diagnosis: NullAway at `V17HierarchyValidationTest.java:108` because `IOException.getMessage()` is nullable.
- Authorized narrow fix: bind `String message = Objects.requireNonNull(error.getMessage());` and assert against `message`; test-only commit, no production change and no local build/test.
- Continuous watcher was interrupted before editing and must be restarted immediately after the fix push.

## Final delivery

- PR `#54`: `https://github.com/MongooseMoo/banteng/pull/54`.
- Final exact head: `d701b6e56c9b00edc23dfa25832c851848be1ed0`.
- Follow-up commits:
  - `822f296eefd8344bd2a79c4e151c4b2264aaff89` fixes only the test's NullAway message binding.
  - `38e0910d9847a58cc8ea269fd55845726038ad1a` adds RED sentinel and duplicate-admission fixtures/contracts.
  - `0432b9e669bd35d3d52494a1860363accb81f3ff` narrows permanent `WorldHistory` validation to Toast's sentinel/duplicate rules.
  - `d701b6e56c9b00edc23dfa25832c851848be1ed0` adds the final test-only non-object list-element contract.
- Final watcher state: six passed, zero failed, zero pending, no review items, CLEAN/MERGEABLE.
- PR was merged externally; this agent did not merge it.
- Normal two-parent merge commit: `c278f236e5c619dfc7d46540bf14fe8fa71db8fa`, parents `d212436d37cfc022be736469ec4d27f421322d69` and final feature head `d701b6e56c9b00edc23dfa25832c851848be1ed0`.
- Issue #28 is closed; fresh `origin/master` is the merge commit.
