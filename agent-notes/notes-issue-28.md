# Issue 28 handoff notes

## Scope and state

- Assigned worktree: `C:\Users\Q\code\banteng-agent-issue-28`
- Branch: `agent/issue-28-v17-hierarchy-validation`
- Exact base: `b93e01485f371b4bf4391d66e8c9483a4f5cc4d5`; candidate HEAD: `2c83a79c0e677bd85c5643078f6e0c3218fe2f9f`.
- Issue: `MongooseMoo/banteng#28`, "Match Toast v17 hierarchy validation and repair semantics"
- Tests-first commit: `2a61c4f test: define Toast v17 hierarchy admission`.
- Production implementation is committed as `2c83a79 fix: match Toast v17 hierarchy admission`.
- No builds or tests may be run locally; CI owns execution.
- Do not push, create a PR, comment, or publish. Final local commits and a ready PR packet go back to the root agent for Q to read.

## Authority findings

- Pinned WSL Toast checkout is available in Debian at `/root/src/toaststunt` and is at `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Toast hierarchy admission is in `src/db_file.cc:595-747`, `ng_validate_hierarchies()`.
- Phase 1 rejects wrong field shapes: parents must be an object or list of objects; children and contents must be lists of objects; location must be an object. If field shapes are valid, missing references are repaired: missing parents, children, and contents entries are removed; a missing location becomes `#-1`.
- Phase 2 rejects both parent cycles (`db_ancestors`) and location cycles (`db_all_locations`).
- Phase 3 rejects reciprocity failures in all four directions: location to contents, contents to location, parents to children, and children to parents.
- `LambdaMooV17Codec.read()` currently calls `validateInheritanceGraph()` after parsing. Parsing already enforces field types. Existing validation rejects missing inheritance references, checks parent cycles, and checks parent/children reciprocity; it does not implement Toast repair semantics, location cycles, or location/contents reciprocity.
- The tests-first commit adds `V17HierarchyValidationTest` plus eleven minimal resources under `src/test/resources/moo/persistence/v17-hierarchy/`: four phase-1 field-type rejection fixtures, one phase-1 missing-reference repair fixture, parent and location cycle fixtures, and one fixture for each of the four phase-3 reciprocal directions. Rejection tests assert the focused error text rather than accepting an arbitrary parse failure.
- The unstaged production delta replaces the inheritance-only pass with a three-stage v17 hierarchy pass: reconstruct permanent raw objects with only missing references repaired; validate parent and location cycles independently; validate location/contents and parents/children in both directions. It retains `#-1` references as Toast does and ignores `#-1` during ancestry/property restoration.
- The production delta removes Banteng's prior duplicate-parent/duplicate-child rejection because pinned Toast's admission pass checks reciprocal membership and does not reject duplicate list entries.
- Preserve issue #27 metadata work already present in base. Keep the change independent of reader deduplication issue #16 and do not broaden to v5/issue #26.

## Next action

1. Finish static review of the unstaged production delta against Toast `src/db_file.cc:614-746`, including permanent-versus-anonymous boundaries and `#-1` handling.
2. Inspect diffs and source formatting only; do not run builds or tests locally.
3. Commit the implementation locally as the second intentional commit.
4. Run structural Git inspections only (`git diff --check`, scope/name/status/log/diff statistics); make no test status claim.
5. Return exact files/commits, structural diff checks, and a proposed ready PR title/body containing `Closes #28`, plus verification commands only and no test status claims.

## Blockers

- Final adversary audit found the implementation and eleven fixtures aligned with pinned Toast `ng_validate_hierarchies()`: field-shape rejection, phase-1 repair before both phase-2 cycle checks, then all four phase-3 reciprocal checks. The combined missing-reference fixture also proves repaired links do not create false reciprocity failures.
- The candidate changes only `LambdaMooV17Codec`, one focused test class, and eleven v17 fixtures. Issue #16 reader deduplication, v5, issue #27 queued-task metadata, concurrency, identity, and anonymous-object representations are untouched.
- GitHub `master`, local `origin/master`, and the stated base are all `b93e01485f371b4bf4391d66e8c9483a4f5cc4d5`; the candidate is the intentional test-only commit followed by the production commit.
- Architecture blocker under `docs/reports/toast-oracle-identity-2026-07-14.md:8-9`: this note records pinned Toast source analysis, but no slice evidence records Barn spec, current Barn code, or managed-oracle execution. No tests or builds were run in the final audit, per task constraint.
