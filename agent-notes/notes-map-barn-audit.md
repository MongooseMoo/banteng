# MAP Barn authority audit checkpoint

## Authority and state

- Barn authority is commit `5a89ba0250a654bf4ae9383aecd4e6f2b0b363a1` (2026-07-09, `Merge branch 'master' into work/mvcc-concurrent-moo`).
- Barn has been read only through commit-scoped `git -C ..\barn log`, `grep`, and `show` commands. Its working tree has not been inspected or modified.
- No tracked file has been modified. This untracked checkpoint exists only because the checkpoint hook required it.

## Current findings

- Normative surfaces found: `spec/builtins/maps.md`, `spec/types.md`, `spec/operators.md`, `spec/statements.md`, `spec/database.md`, `spec/builtins/types.md`, `spec/builtins/lists.md`, and `spec/vm.md`.
- Main semantic owner is `types/map.go`: `goMap`, `keyHash`, COW `set`/`delete`, insertion-order `keys`/`pairsList`, deep order-insensitive `equal`, canonical-output sorting, `NewMap`, `CompareMapKeys`, `MapGet`/`MapSet`/`MapDelete`, `GetWithCase`, `KeyPosition`, and key validators.
- `types/value.go` owns MAP truthiness, literal dispatch, equality dispatch, and length.
- Public execution owners found: `bytecode/compiler.go:compileMap`; `vm/op_list.go:executeMakeMap`; `vm/op_index.go` indexing/ranges/mutation; `vm/op_iter.go` iteration; `vm/op_compare.go` equality/ordering/`in`; `builtins/maps.go` mapkeys/mapvalues/mapdelete/maphaskey; `builtins/limits.go` map-value size limit; `db/format/reader_value.go` and the corresponding writer for persistence.
- Confirmed spec contradictions: `spec/builtins/maps.md:30` says all MOO types are hashable while `:215-228` rejects LIST/MAP; `spec/types.md:185` says LIST/MAP keys are allowed. Implementation `types/map.go:273-281` permits INT/FLOAT/STR/OBJ/ANON/ERR structurally and rejects ANON for map builtins, with no BOOL/WAIF/LIST/MAP.
- Spec claims implementation-defined/non-insertion iteration (`spec/builtins/maps.md:258-269`), while `types/map.go:18-20,48-66,92-106,238-242` explicitly preserves insertion order. Output literal sorting is separate (`:122-134,163-215`).
- Spec `in` claims conflict internally: `spec/operators.md:531-554` says key presence, while `spec/builtins/maps.md:283-296` says broken/always false. Current owner `vm/op_compare.go:173-200` searches VALUES and returns the 1-based canonical key-sorted position.
- Map equality agrees at a high level: deep pair-set equality ignoring insertion order (`spec/operators.md:454-470`; `types/map.go:109-120`; `types/value.go:219-254`).
- Empty/non-empty MAP truth agrees (`spec/operators.md:915-925`; `types/value.go:136-158`).
- Current hash identity is type-tag plus rendered value; strings are Unicode-lowercased before hashing (`types/map.go:23-34`). This makes string identity case-insensitive and keeps INT/FLOAT distinct, but float hashing by 15-digit `String()` may collapse distinct values despite `Value.Equal` using exact IEEE equality. This needs a managed Toast row.
- Literal construction is `parser/parser.go:768-829` -> `bytecode/compiler.go:2871-2893` -> empty `OP_MAKE_MAP` plus repeated `OP_INDEX_SET`; the actual key/limit authority is therefore `vm/collection_helpers.go:setAtIndex` (`:18-65`), not the non-empty path in `vm/op_list.go:20-39`.
- `setAtIndex` uses `IsValidMapKey` and performs COW `MapSet` plus `CheckMapLimit`; later duplicate keys overwrite in place without changing their stored order slot, but replace the stored key spelling/value (`types/map.go:48-66,142-155`).
- Public `mapkeys` and one-arg `mapvalues` sort by `CompareMapKeys`, not insertion order (`builtins/maps.go:14-47,49-88`). Multi-key `mapvalues` is explicitly case-sensitive through `GetWithCase(..., true)`.
- `mapdelete` supports an undocumented LIST-of-keys extension, rejects invalid builtin keys, errors E_RANGE on a missing key, and enforces the size limit even after deletion (`builtins/maps.go:90-141`). `maphaskey` defaults to case-insensitive, requires the third arg to be INT (not BOOL), and uses the stricter builtin key validator (`:143-175`). `mapmerge` exists at `:177-204` and is registered at `builtins/registry.go:139`, but has no generated signature alongside the other map builtins at `builtins/function_signatures_generated.go:116-119` and is absent from the normative map spec.
- Direct indexing `vm/op_index.go:10-53` rejects only LIST/MAP before lookup (other impossible key types become E_RANGE), whereas assignment uses the strict validator. Map range read is positional over insertion-order `Pairs()` (`:285-365`); map range assignment accepts INT positions or non-LIST/MAP keys resolved by insertion-order `KeyPosition`, requires a MAP replacement, rebuilds through `NewMap`, and checks quota (`:90-282`). `^`/`$` markers are canonical minimum/maximum keys via `CompareMapKeys`, not insertion endpoints (`:367-424`).
- Iteration sorts snapshot pairs canonically and produces value/key pairs (`vm/op_iter.go:9-55`), agreeing with snapshot behavior but disagreeing with spec's implementation-defined ordering claim.
- `spec/database.md:11-18,47-66,238-257` requires v4 read, v17 MAP count+pairs, and v17 writes. Implementation owners are `db/format/reader_value.go:12-113` and `db/format/writer.go:168-300`; exact validation/order behavior still needs inspection.
- Persistence inspection: `readValue` accepts type 10 only when database version >=17, reads a signed `int` count, recursively reads key/value pairs, then calls `NewMap` without validating key types or quota (`db/format/reader_value.go:12-113`). A negative count can reach `make(..., count)` and panic; duplicate/case-fold-colliding persisted keys collapse by later-wins semantics. Type 10 in v4 explicitly errors. Writer always emits a v17 header (`db/format/writer.go:58`) and writes MAP type 10, insertion-order count/pairs recursively with no key/quota validation (`:169-224,289-303`).
- `tostr(MAP)` agrees with spec's opaque `"[map]"` (`spec/builtins/types.md:45-79`; `builtins/types.go:24-82`) and applies the string-result quota. `toliteral` uses `Value.String()` and therefore canonical key sorting (`builtins/types.go:192-208`; `types/value.go:160-195`; `types/map.go:122-168`).
- Map byte accounting is recursive: base 32 bytes plus `ValueBytes(key)+ValueBytes(value)` per entry (`types/value_bytes.go:11-47`). The default `max_map_value_bytes` is 64,537,861, canonicalized to 1,021..`MaxInt32-1021` from `#0.server_options` (`builtins/limits.go:18-55,126-170`); `CheckMapLimit` returns E_QUOTA only when size is strictly greater than limit (`:347-374`). The normative spec does not define this limit surface.

## Blockers / uncertainties

- No blocker. Remaining work is committed-object inspection only.
- Need exact conversion scope, encoding behavior, overflow guards, and existing tests.
- Need reduce oracle needs to the smallest MOO-observable managed Toast rows, especially key type acceptance, BOOL/ANON/WAIF keys, float identity edge cases, duplicate-case spelling/order, deletion-missing behavior, ordering across construction/mutation/restart, `in`, and persistence.

## Next action

Continue commit-scoped `git show`/`git grep` inspection of the named owners and tests, then return the direct audit without implementation.
