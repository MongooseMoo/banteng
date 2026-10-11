# Issue #1 name-independent finalization

## Findings

- Assigned worktree: `C:\Users\Q\code\banteng-agent-issue-1` on `agent/issue-1-name-independent-finalization`, exact base `b93e01485f371b4bf4391d66e8c9483a4f5cc4d5`.
- Live issue #1 requires deleting the `waif`/`anon` identifier-spelling gate while retaining last-use liveness only for values that directly or transitively contain an anonymous object or WAIF.
- `MooVm.loadLocal` currently removes only locals named `waif` or `anon` after `isFinalStraightLineLocalRead` succeeds.
- Existing `MooVmTest.finalStraightLineReadsMoveTemporaryValuesOutOfSuspendedLocals` proves only the two scarred names and direct values.
- Pinned anonymous-value authority says Toast finalization is driven by loss of the last reachable reference; existing lifecycle/finalization rows are the observable authority.

## State

- No production or test edits have been made in the assigned worktree yet.
- No builds or tests will be run locally; CI owns execution.
- The main-checkout note is intentionally uncommitted and must not be staged with issue #1 work.

## Blocker

- None.

## Next action

- Add focused tests first for renamed direct and nested ANON/WAIF values, ordinary locals (including the names `waif` and `anon`), and conservative control-flow behavior; then implement the smallest recursive value-containment predicate at `LOAD_LOCAL` and create one intentional local commit.
## Deterministic shared-DAG regression follow-up

- Scope: replace only the fragile `assertTimeout` identity-dedup regression in `C:\Users\Q\code\banteng-agent-issue-1`.
- Constraint: no production test hook, API, telemetry, arbitrary time budget, local build, or local test execution.
- Required proof: deterministic black-box evidence that recursively shared List/Map identities are visited once, while retaining separate direct, List, Map-key, and Map-value coverage.
- Finding: `ListValue` is final and snapshots its input with `List.copyOf`; `MapValue` is final and copies entries into its own `LinkedHashMap` before wrapping it. Their accessors expose only those owned immutable snapshots.
- Finding: `MooValue` is sealed and every traversable collection implementation is closed/final, so the test cannot supply an observable subtype either.
- Blocker: for a legitimate acyclic immutable DAG, identity dedup changes traversal work but not externally visible semantics. Exact access counts are unavailable; termination only differs for an illegitimate cycle; time/CPU/heap bounds are arbitrary resource proxies.
- Resolution authorized by parent: removed only the `assertTimeout` shared-DAG regression and its unused timing imports; production traversal and deterministic direct/List/Map/renamed/pass coverage remain intact.
- Commit: `434f8e49eaf6616c9a789faf2bb460ee6f3ef65c test: remove timing-based DAG assertion`.
- Verification boundary: structural checks only. Both `git diff --check origin/master..HEAD` and `git show --check HEAD` emitted no diagnostics. No build/test, push, or PR action was performed.
- Next action: return the five-commit candidate to the parent for final audit/publication handling.
