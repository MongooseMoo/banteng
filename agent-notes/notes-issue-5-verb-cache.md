# Issue 5 verb cache checkpoint

## Findings

- Assigned worktree: `C:\Users\Q\code\banteng-agent-issue-5`, branch
  `agent/issue-5-verb-cache`, base `d50652d1ff4b643a759812e4cd37e4a5ca85b1f9`.
- Issue #5 requires a real Toast-aligned verb cache, real counters, mutation
  generation invalidation, real depth histogram, and exact log/result shape.
- Pinned authority is Toast commit
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`, especially
  `src/db_verbs.cc` and `src/extensions.cc`.
- Toast lazily allocates 7,507 buckets, counts positive hits, negative hits,
  and misses independently, caps histogram depth at 16, and keeps 17 buckets
  in the returned histogram. The log summary omits negative hits.
- Toast invalidates the allocated table and advances generation for mutations
  affecting callable lookup. Verb source/owner changes do not invalidate;
  name, executable flags, argument specs, add/delete, relevant parent changes,
  and object destruction do.
- The pinned managed oracle command passed:
  `scripts/run_managed_wsl.sh toast stock src/moo_conformance/_tests/builtins/verb_cache.yaml`.
- Banteng callable lookup is owned by `WorldTxn`; the shared committed revision
  owner is `WorldHistory`. A shared cache must reject stale or locally modified
  MVCC transactions so an old snapshot cannot repopulate entries after a
  committed invalidation.

## State

- Added test-only `src/test/java/moo/world/VerbCacheTest.java` in the assigned
  worktree.
- Strengthened the existing builtin test to require live counters and a real
  7,507-bucket histogram.
- Production code has not been edited yet.
- No blocker.
- The first focused compile RED failed on the intentionally missing
  `WorldTxn.VerbCacheStats` / `verbCacheStats()` API, but also exposed a
  test-constructor edit that matched an earlier `WorldObject` call in
  `BuiltinCatalogTest`. Restore that unrelated list argument and rerun RED
  before production edits so the retained RED is scoped only to the new API.

Current state supersedes the earlier production-edit line:

- Implemented the shared chained hash table, MVCC-safe lookup integration,
  publication invalidation, live builtin values, and exact log rows in the
  assigned worktree.
- Focused RED was the missing `WorldTxn.VerbCacheStats` and
  `verbCacheStats()` API only.
- Focused GREEN passed for `VerbCacheTest` and the two exact builtin tests.
- The broader world/VM/runtime/builtin regression slice passed.
- A redundant full `check` reached `:test` and was interrupted only for this
  worktree after the user clarified that CI owns broad verification; it is not
  a verification result.
- Added `investigations/verb-cache-toast-authority.md` to preserve the required
  slice-local authority decision.

## Next action

Audit the final diff, stage only the six source/test paths plus the authority
record, commit locally, and return the no-publication handoff packet.

This note is checkpoint-only and must remain uncommitted.

## Post-merge current-master audit (2026-08-18)

- Live issue #5 is closed by normal-merged PR #37. Merge commit `b93e01485f371b4bf4391d66e8c9483a4f5cc4d5` has parents `60e46a0105d8d656e323f2c11da2101e3f32ed7c` and issue branch head `13b2823c93bc879564f9ae83f8db85c4910b8d9a`.
- The existing worktree is clean at `13b2823`; against current master `8369c112d9cf30f5dd096b7c2af0116ea5244ba4`, ancestry is `100 0` (master-only, branch-only). The branch has no unique work to replay.
- PR #37 has six terminal successful checks.
- Current master retains the real 7,507-bucket chained cache, case-insensitive Toast hash, separate positive/negative/miss counters, lazy allocation, generation invalidation, and 17-slot capped depth histogram.
- `WorldHistory` remains the synchronized committed-revision/cache owner. Only a transaction whose base is the current world and whose working world is still its base can populate the shared cache; stale or locally modified transactions resolve uncached and cannot repopulate after invalidation.
- Publication invalidates only when callable lookup can change: object/anonymous deletion, parent changes, verb count/order, names, executable permission, or preposition. Source/owner-only updates retain cache contents.
- The #18 typed-error migration changed relevant mutation APIs/tests to `WorldResult`/`.isOk()` while preserving publication writes and cache invalidation semantics.
- `verb_cache_stats()` still returns live hits, negative hits, misses, generation, and histogram; `log_cache_stats()` still emits the Toast summary and every depth row from those live values.
- Focused current-master tests retain positive/negative caching, collision depth, lazy generation, published invalidation, code-only retention, stale-transaction exclusion, and builtin output shape.
- No local build/test command was run for this audit. No code, branch, tracker, or PR mutation was performed.

## Post-merge disposition

- No repair is needed or authorized. Leave issue #5 and its merged branch untouched; no PR packet is prepared.
