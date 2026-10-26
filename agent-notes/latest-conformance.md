# Latest MOO conformance

## Goal

Make Banteng pass the latest tests on MongooseMoo/moo-conformance-tests main.
Q authorized implementation and a PR on 2026-10-25 with "Make it so!".
Necessary redesign/cleanup is in scope; weakening tests or skipping failures is not.

## State

- Isolated branch `fix/latest-conformance` starts at Banteng master `2ccad0597d7a6889e1607174cd2e6513a2032542`.
- Existing master CI run 32452595044 passed against suite `6528b8cf6d72c065c722469b070fea16d626e602` in August; it is not current evidence.
- Suite main observed as `8dbe801819c41529122cfb1f98f68e9b709db383` at task start and is actively changing.
- No implementation changes yet. Publish this note to obtain the existing PR CI baseline.

## Decisions and why

Smallest initial change: this task note only, roughly 30 lines. The existing CI
already checks out suite main, runs admission/conformance with strict markers and
unexpected-skip failure, and uploads results. No new runner or validation gate is
needed. Implementation scope and line estimate will follow actual failure evidence.
Use PR CI, not a local rerun of CI commands. Preserve unrelated worktrees/files.
No merge or release has been requested explicitly; deliver the reviewed passing PR
and resolve any remaining publication decision with Q.

## Surprises

The primary checkout was behind remote master and contains an unrelated untracked
investigation. It remains untouched.

## Next step

Open the PR, inspect the suite checkout SHA and conformance failures, and trace
each failure into current tests and Banteng source before changing behavior.
