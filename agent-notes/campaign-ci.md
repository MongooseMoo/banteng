# Merge queue CI bootstrap

## Goal
Make the existing CI checks execute for merge queue candidates. Smallest change: add the merge_group checks_requested event and include that event in the dependency-review job condition (four changed YAML lines).

## State
Implemented in campaign/merge-queue-ci from origin/master 90ecb19. No local CI, publication or repository settings changes performed. Coordinator owns publication, Claude review, hosted CI and normal merge.

## Decisions and why
Keep every existing check name, command, action pin and dependency-review severity/license setting. The existing pinned dependency-review action handles merge_group refs according to coordinator's source inspection; no extra inputs are needed.

## Surprises
None in this scoped edit. CodeQL pin ambiguity is outside scope and the existing pin is preserved.

## Next step
Coordinator checks the diff, publishes the PR, obtains Claude review, and observes hosted CI. Configure repository protection/queue only after the bootstrap merges.

## PR draft
Title: Run CI checks for merge queue candidates

Enable CI on merge_group checks_requested events and run dependency review for those candidates. Existing checks, action pins and dependency policy stay intact.

Validation: hosted PR CI pending; local CI was not run under Q's CI-on-PR instruction.
