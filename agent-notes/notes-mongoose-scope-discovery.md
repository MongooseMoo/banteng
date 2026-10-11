# Mongoose scope discovery checkpoint

## State

- Read-only survey completed against the current dirty `master` worktree; no tracked files were edited.
- The normative release requirement originates in `docs/reports/supported-conformance-profiles.md` and is propagated into phase/final gates by `docs/reports/banteng-implementation-plan.md`.
- Optional execution support exists independently in the two Mongoose manifests, the managed runner, the Banteng manifest-driven promote-number switch, and runner/CLI tests.
- Default JUnit tests now directly read the ignored local Mongoose fixture, so `./gradlew check` implicitly depends on it.

## History

- Commit `a880aace84e142e5bf765ab08497d27856bc3dfd` (2026-07-15) introduced the explicit first-release Mongoose/PROMOTE_NUMBERS requirement.
- Commit `85a388ea81a37cfcf4e20f7540053a410863f416` (2026-07-18) added Phase 5 Mongoose gates and Phase 8 final Mongoose gates.
- The current "Before Phase 5" Mongoose v17 restoration block is uncommitted (`git blame` reports `00000000`), so it is newer working-tree ordering, not repository history.

## Blocker

- None. Parent requested immediate conclusion.

## Next action

- Hand off the concise classification and exact file/line evidence to the parent agent.
