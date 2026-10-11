# ANON Toast authority audit checkpoint

## Current findings

- Pinned Toast source identity was verified first: `/root/src/toaststunt` is at `aecc51e9449c6e7c95272f0f044b5ba38948459e` on branch `master`.
- Durable conformance repository HEAD is `e5b1e474c1e9016540acc8a47c2ce3cc904a6589` on branch `main`.
- Banteng HEAD is `3451bd740fdba464ee4cf0c2f9051218d0c38869` on branch `master`.
- All three worktrees already contain untracked files; no tracked modification has been observed or made by this audit.

## Scope and observations

- Read-only audit only.
- Barn must not be inspected and has not been inspected.
- Toast inspection is restricted to the verified pinned checkout.
- Conformance coverage must be inventoried from tracked files at conformance HEAD and deduplicated, including MAP authority rows relevant to ANON key identity.
- No implementation and no tracked writes are authorized.
- Principal tracked ANON coverage at conformance HEAD is in `language/anonymous.yaml`, `builtins/create.yaml`, `builtins/recycle.yaml`, `language/map_authority.yaml`, `server/map_dump_persistence.yaml`, and `server/startup_repair_anon1.yaml` through `startup_repair_anon6.yaml`, with additional cross-type rows in basic types, JSON, GC, objects, map, and waif suites.
- `language/anonymous.yaml` actively covers creation from `$nothing`/empty parents, type/lifecycle/reference invalidation, parent and child surfaces, ownership and flag errors, inherited properties and verbs, caller identity, invalid-reference errors, reference loss finalization, and one-shot recycle hooks.
- Several apparent task/reference rows are not substantive managed decisions: the wizard `callers`, `task_stack`, and `queued_tasks` rows are explicitly skipped; the non-owner and error-stack rows are placeholders that only assert a list type or constant `1`.
- The MAP authority rows specifically cover ANON keys in VM lookup/insertion and `mapvalues`, rejection by `maphaskey`/`mapdelete`, reverse insertion topology, and persistence of forward/reverse ANON-key maps.
- `builtins/create.yaml` covers anonymous argument positions, ownership/fertility/anonymous-flag permissions, type `ANON`/numeric value `12`, no `max_object()` increment, no parent `children()` membership, duplicate-parent rejection, and the anonymous-own-owner rejection.
- `builtins/recycle.yaml` covers explicit recycle, repeated recycle error, owner/wizard permission paths, inherited recycle hooks, invalid saved references, and nested ANON values during enclosing object/ANON recycling.
- Exact pinned Toast owners established so far:
  - `src/include/structures.h:99-119,163-197,234-240`: `_TYPE_ANON`/`TYPE_ANON`, `Var::v.anon`, collection/object classification, constructor.
  - `src/objects.cc:312-450`: `bf_create` argument decoding, owner/parent validation, anonymous-vs-fertile permission gate, quota, anonymous allocation/parenting, `initialize` dispatch.
  - `src/objects.cc:788-949`: `bf_recycle` type/validity/control gates, `FLAG_RECYCLED`, inherited `recycle` dispatch, quota return, anonymous destruction.
  - `src/utils.cc:127-348`: `aux_free`, `complex_free_var`, `complex_var_ref`, and `complex_var_dup`; ANON copies are reference increments, not structural duplication, and last-reference loss queues finalization.
  - `src/utils.cc:380-400`: `is_true`; only nonzero INT/FLOAT, nonempty STR/LIST/MAP, and true BOOL are truthy. ANON falls through to the default `0`, so every ANON value is false in boolean context.
  - `src/utils.cc:410-493`: `compare` and `equality`; ANON identity is raw `Object*` pointer identity. Comparator returns `0` for same pointer and `1` for any distinct pointer, so distinct ANON keys are comparator-equivalent in both directions rather than strictly ordered.
  - `src/utils.cc:735-750`: `anonymizing_var_ref`; wizard/owner sees the valid reference while other viewers receive an ANON value with null pointer.
  - `src/db_objects.cc:48-53,1030-1139`: separate `anonymous_objects` parent index and `db_change_parents`; ANON does not enter normal children lists.
  - `src/map.cc:677-715` with `src/utils.cc:407-440`: VM/map insertion accepts ANON keys, but the RB-tree comparator treats all distinct ANON pointers as `1` in both directions. This explains reverse-insertion topology and why map equality can depend on traversal pairing despite pointer-identity equality.
  - `src/list.cc:376-430,448-520`: both `tostr` and `toliteral` render ANON as `*anonymous*`; `sort` rejects ANON at `src/list.cc:945-975`.
  - `src/numbers.cc:122-189`: `toint`/`toobj` integer conversion and `tofloat` reject ANON with `E_TYPE`.
  - `src/include/version.h:36-90`: `DBV_Anon` is numeric format 13; current `DBV_Bool` is format 17. Thus ANON exists in current v17 but is absent from v4 by construction.
  - `src/db_io.cc:180-220,365-400`: `_TYPE_ANON`/`TYPE_ANON` delegates to `db_read_anonymous`/`db_write_anonymous`.
  - `src/db_file.cc:294-365,863-979,1041-1101`: v13+ reads/writes pending-finalization values and task references before permanent and iterative anonymous object bodies; pre-v13 paths omit all ANON sections, and v4 uses the legacy object reader/upgrader.
  - `src/server.cc:569-650,740-779`: lost references enter a deduplicated pending-finalization queue, receive `FLAG_RECYCLED`, run inherited `recycle`, and are restored from the pending list on startup.
  - `src/execute.cc:451-493`, `src/tasks.cc:2354-2426`, and `src/utils.cc:735-750`: `callers`, `task_stack`, and `queued_tasks` expose actual ANON `this`/verb-location references only to wizard or owner; everyone else receives an invalid null-pointer ANON reference.
  - `src/crypto.cc:473-517`: `value_hash` hashes `unparse_value`; because ANON unparses as `*anonymous*`, all ANON identities (including invalid references) have the same hash input.
  - `src/json.cc:380-413,425-508`: ANON is rejected both as a JSON value and as a map key; the managed JSON suite actively covers direct, list, map-value, and map-key placement in all three modes.
  - `src/property.cc:206-260` and `src/verbs.cc:174-269`: structural property/verb add/delete requires `obj.is_obj()`, not `is_object()`, so ANON mutation is `E_TYPE`; query/read/dispatch paths accept object-like ANON and resolve inherited definitions.
  - `src/db_objects.cc:527-541,853-901`: validity is non-null pointer, not invalid, and all parents valid; owner/parents/children/flags dispatch through ANON-aware `*2` accessors.

## Deduplicated managed coverage status

- Active direct coverage exists for representation/type constant and `typeof`; create argument positions, ownership/fertility/anonymous permission gates, quota/max-object behavior, initialization, parents/no-children; explicit recycle, copy-reference invalidation, permissions, finalization and one-shot hooks; inherited properties/verbs and invalid property/verb references; JSON rejection; VM/map ANON-key lookup plus key-builtin rejection, reverse topology, equality, literal spelling, and v17 restart persistence; canned-DB pending-finalization/GC repair.
- No active ANON-specific managed row was found for boolean truth (`is_true` makes ANON false), `toint`/`toobj`/`tofloat` (`E_TYPE`), direct `tostr`/`toliteral` (`*anonymous*`), `value_hash` identity collapse, or direct same-vs-distinct ANON equality outside MAP behavior.
- Task/reference names are not durable coverage: wizard `callers`, `task_stack`, and `queued_tasks` rows are skipped; non-owner/error-stack rows are placeholders that do not assert ANON reference validity/redaction. Owner-specific task exposure is absent.
- Direct `properties(anon)` / `verbs(anon)` query results are not isolated by active rows; inherited property access and inherited verb dispatch are covered, while structural add is covered as `E_TYPE`.
- `src/execute.cc:1216-1255,1624-1640` rejects direct ANON index assignment/read because only LIST/STR/MAP are indexable; no active ANON-specific row isolates these `E_TYPE` outcomes.
- No tracked conformance row explicitly proves that a v4 database contains no ANON section/type. Current v17 ANON persistence is exercised indirectly but materially by `server/map_dump_persistence.yaml::ordering_spelling_bytes_and_non_total_topology_round_trip` and directly for startup/finalization by `startup_repair_anon1` through `startup_repair_anon6`.
- Final identity/status verification still reports Toast `aecc51e9449c6e7c95272f0f044b5ba38948459e`, conformance `e5b1e474c1e9016540acc8a47c2ce3cc904a6589`, and no tracked modifications in Toast, conformance, or Banteng.

## Blocker

- None currently.

## Next action

- Return the completed read-only audit to the parent; no further inspection or implementation.
