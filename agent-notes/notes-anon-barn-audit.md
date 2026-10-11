# ANON Barn authority audit checkpoint

## Scope and state

- Read-only audit of Barn commit `5a89ba0250a654bf4ae9383aecd4e6f2b0b363a1` for the ANON primitive family.
- Barn has been accessed only through commit-scoped `git -C ..\barn cat-file`, `ls-tree`, `grep`, and `show` commands. Its working tree has not been inspected or modified.
- No tracked file has been modified, staged, or committed. This note is the only permitted untracked task artifact.

## Current findings

- Normative type prose is internally stale: `spec/types.md:5-23` omits ANON and calls code 12 reserved; `spec/builtins/types.md:15-27` assigns WAIF to 12. Committed implementation has `TYPE_ANON=12`, `TYPE_WAIF=13` in `types/typecode.go:6-17` and exposes `ANON` in the compiler/VM environment.
- Normative object prose says ANON has no persistent ID and cannot be stored (`spec/objects.md:402-419`, `spec/builtins/objects.md:523-538`), while v17 spec and implementation persist reachable anonymous graphs in a batched section (`spec/database.md:155-169`; `db/format/reader_object.go:297-330`; `db/store/store_snapshot.go:50-62,93-105,126-206`). Runtime identity is out-of-band; dump IDs are assigned above `max_object()` and references are rewritten.
- Value representation is a tagged scalar `(TYPE_ANON, identity id)` (`types/value.go:40-44,86-120`). Equality requires same tag and id (`types/value.go:217-242`); literal is `*#N`, while `tostr` emits `#N` (`types/value.go:160-195`; `builtins/types.go:24-83`). `typeof` returns 12; `toint`/`tofloat` expose the identity id; `toobj` preserves ANON (`builtins/types.go:14-22,85-189,210-255`).
- `Value.Truthy()` makes ANON false (`types/value.go:136-158`), contradicting `spec/types.md:217-220` (“Everything else” true). This needs a managed Toast row.
- Language map indexing/assignment permits ANON keys by typed identity (`types/map.go:23-34,272-281`; `vm/op_index.go:10-52`; `vm/collection_helpers.go:18-65`), but `mapdelete`/`maphaskey` reject ANON via `IsValidBuiltinMapKey` (`builtins/maps.go:90-175`). Spec says ANON is hashable by reference (`spec/builtins/maps.md:213-254`). Ordering folds ANON into OBJ numeric-ID order for map output (`types/map.go:170-214`), while relational operators accept OBJ but not ANON (`vm/operators.go:584-660`; `vm/op_compare.go:228-319`).
- Creation parses parent/owner/anonymous/init args in `builtins/objects.go:12-275`; store ownership is `db/store.Store.CreateObject` (`db/store/store_lifecycle.go:10-64`). ANON uses `s.anonObjects`, increments high-water identity without affecting `max_object`, and returns `types.NewAnon`.
- Creation prose/example `create($thing, $nothing, 1)` conflicts with implementation, which rejects anonymous owner `$nothing` (`builtins/objects.go:187-245`), and the spec signatures omit init args. Exact create argument/error precedence needs managed Toast rows.
- Anonymous objects inherit property/verb structure but cannot add properties or verbs (`builtins/properties.go:209-346`; `builtins/verbs.go:340-379`). Calls dispatch through inherited verbs and preserve ANON as `this` and task activation `ThisValue` (`vm/op_verb.go:67-125,161-200,230-258`). Built-in property writes restrict owner/programmer/wizard on ANON, but `.a` directly changes the flag rather than identity (`vm/op_property.go:453-557`), an observable uncertainty.
- Reachability roots include regular-object properties, nested ANON properties, and live VM locals/stacks (`db/store/store_reachability.go:59-199`; `vm/anonymous_gc.go:10-105,127-233`). Scheduler task completion/suspend boundaries defer batched recycle while snapshotting sibling task references. Explicit recycle cascades through referenced anonymous property values before recycling the target (`builtins/objects.go:321-440`).
- DB v17 reads type 12 references and batched anonymous objects; ANON does not appear in v4. Reader currently accepts type 12 without an explicit version guard (`db/format/reader_value.go:93-121`), so v4 type-12 rejection is a test/managed-oracle gap.

## Blocker

- None for the commit-scoped Barn audit. Toast behavior is intentionally not being inferred where Barn spec and implementation disagree; those cases will be reduced to managed conformance rows.

## Next action

- Finish committed Barn test inventory and exact line ranges, then report agreements, disagreements, public-entry-to-owner traces, and only the smallest MOO-observable Toast uncertainties to the parent agent.

## Test inventory update

- Direct ANON tests are sparse and implementation-layer focused: `types/review_test.go:8-25` guards OBJ-vs-ANON equality; `types/value_struct_test.go:195-206` checks constructor/accessors; `db/store/review_test.go:138-190` checks reachable runtime ANON inclusion/remapping in snapshots; `db/store/store_txn_test.go:335-398,1677-1730` checks renumber relationships and transactional anonymous mutation; `db/store/anon_concurrent_race_test.go:11-49,69-...` checks serialized concurrent access; `vm/anonymous_gc_test.go:22-115` checks pending-finalization roots; `db/store/store_test.go:164-191` checks pending-finalization snapshot cloning; `db/format/startup_repair_reader_test.go:34-43` only loads the `Anon6.db` pending-finalization fixture.
- No committed MOO-level ANON tests were found for truth, create optional-argument/error precedence, ownership/fertility permission matrix, conversion/literal formatting, map key identity/builtin rejection/order, property/verb restrictions and inherited dispatch, `.a` mutation, explicit/cascading recycle, task `this`/`callers()` preservation, or a full v17 write-load graph round trip.
- Format support is split: `reader.go:108-125` dispatches v4/v5/v17; only v17 invokes the anonymous section; writer is always v17 and writes type 12 plus the anonymous batch. `reader_value.go:115-121` accepts type 12 without the v17 guard used for MAP at `:93-96`, leaving malformed v4/v5 type-12 acceptance uncovered.
