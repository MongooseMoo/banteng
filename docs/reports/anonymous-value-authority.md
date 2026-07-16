# Anonymous object value authority

## Scope

This is the Phase 2 mandatory authority record for the public ANON value family
before its remaining managed-oracle decisions are resolved. It covers type and
identity, creation and lifecycle, conversion, equality and ordering, map
identity, truth, object and indexing surfaces, mutation/copy behavior, literal
formatting, v17 persistence, encoding, limits, permissions, and errors.

This record does not choose or approve a Java representation, authorize a value
hierarchy, or permit a production edit. The ANON gate remains open only for the
scalar, object/index, and task-reference decisions below.

## Verified identities

- Barn normative specification and implementation reference:
  `5a89ba0250a654bf4ae9383aecd4e6f2b0b363a1`, read only through committed Git
  objects. Barn's working tree was not inspected or modified.
- Toast source and executable authority: `/root/src/toaststunt` at
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`, executable
  `/root/src/toaststunt/build-release/moo`.
- Existing durable conformance authority: `../moo-conformance-tests` commit
  `e5b1e47` plus its ancestors.
- Managed oracle authority: Banteng's owned
  `profiles/toast/stock-wsl-testdb.json` and `scripts/run_toast_wsl.sh`, using
  the bundled disposable `Test.db` fixture.

## Normative Barn specification

- `spec/types.md:5-23` says there are nine types, omits ANON, and calls numeric
  code 12 reserved. `spec/builtins/types.md:15-27` assigns code 12 to WAIF.
  Current Barn and Toast instead assign ANON code 12.
- `spec/objects.md:402-419` and `spec/builtins/objects.md:523-538` say ANON
  values cannot be stored permanently. `spec/database.md:11-18,30-43,51-66,
  155-169` contradicts that prose by defining v17 type 12 and a batched
  anonymous-object section. Pinned Toast persists its reachable ANON graph;
  current Barn proves property-reachable graph emission, with pending-only
  reachability unresolved below.
- Creation examples at `spec/objects.md:402-408` and
  `spec/builtins/objects.md:34-40,523-529` use owner `$nothing`, but current
  Barn rejects that owner and pinned Toast has its own positional permission
  rules. The documented signatures also omit the supported init-args form.
- `spec/types.md:217-220` implies ANON truth because it is not in the false
  list. Current Barn and Toast make every ANON false.
- `spec/builtins/maps.md:213-254` permits ANON map keys by reference. Language
  indexing does, but `maphaskey` and `mapdelete` reject ANON on both current
  implementations.
- `spec/builtins/objects.md:68-95` restricts recycle to owner or wizard.
  Current Barn's permission check is incomplete, while existing Toast-derived
  conformance rows already control ordinary recycle permissions.
- The conversion tables do not define ANON and misnumber WAIF. They cannot
  decide ANON text or numeric conversion behavior.

The normative specification is therefore stale on type numbering, truth,
persistence, creation signatures, conversion, and several permission details.
It is accepted only where managed Toast agrees.

## Current Barn implementation path

- `types/value.go:40-44,78-120` stores ANON as `TYPE_ANON` plus an `int64`
  identity. `types/typecode.go:6-17` assigns ANON code 12 and WAIF code 13;
  `bytecode/compiler.go:585` and `vm/environment.go:30` expose `ANON`.
- Runtime bodies live separately in `Store.anonObjects` at
  `db/store/store_core.go:29-62,258-279`. `db/store/store_lifecycle.go:10-64`
  allocates regular and anonymous identities from the shared `highWaterID`,
  does not advance `maxObjID` for ANON, and copies inherited property slots.
  Identity/dump counters increment without overflow guards.
- `builtins/objects.go:23-275` owns creation arguments, permission/error order,
  owner validation, parent fertility/anonymous flags, and init args.
  `$nothing` is rejected as ANON owner.
- `types/value.go:136-195,217-242` owns false truth, exact tag-plus-identity
  equality, and identity-bearing `*#N` literal form. OBJ with the same numeric
  payload is distinct.
- `vm/op_compare.go:228-319` and `vm/operators.go:584-660` reject ANON
  relational comparison with E_TYPE. `types/map.go:23-34,170-214,272-281`
  hashes/orders ANON by identity with OBJ, while map builtins reject ANON.
- `builtins/types.go:14-22,24-83,85-255` exposes type 12, `tostr` as `#N`,
  `toliteral` as `*#N`, numeric conversions as the identity, and `toobj` as the
  original ANON. `builtins/crypto.go:1043-1078` hashes identity-bearing text.
- `builtins/properties.go:209-346` and `builtins/verbs.go:340-379` permit
  inherited property/verb use but reject defining new structure on ANON.
  `vm/op_verb.go:67-125,161-200,230-258` preserves the ANON value as `this`.
  `vm/op_property.go:400-420,453-522,550-557` owns flags/ownership and ensures
  clearing `.a` cannot change a real ANON's identity.
- `vm/op_index.go:10-52,426-451` rejects indexing ANON itself with E_TYPE but
  accepts its identity as a range endpoint.
- `builtins/objects.go:321-440` owns explicit recycle, inherited `:recycle`,
  recursive ANON cleanup, and recycled state. The documented permission check
  is incomplete in the implementation.
- `db/store/store_reachability.go:59-199`, `vm/anonymous_gc.go:10-105,127-233`,
  and scheduler lifecycle owners trace regular properties, nested collections,
  task roots, and deferred collection. Pending-finalization extraction omits
  bare ANON locals while scanning composite locals/stacks.
- `db/store/store_snapshot.go:50-62,93-105,126-278` plus the v17 reader/writer
  owners persist property-reachable ANON graphs. Snapshot cloning copies
  pending-finalization values, but the rewrite plan seeds reachability only
  from regular-object properties; emission/remapping of an ANON body reachable
  solely through pending finalization is unresolved. V4/v5 have no ANON
  section, although Barn's common value reader lacks an explicit version guard
  for malformed type-12 values.

Barn's runtime identity-bearing formatting/conversions, identity-based MAP
ordering, and recycle implementation are reference behavior only; they
disagree materially with pinned Toast. Direct relational E_TYPE agrees.

## Existing Barn reference tests

- `types/review_test.go:8-25` and `types/value_struct_test.go:195-206` guard
  identity/accessors.
- `db/store/review_test.go:138-190` guards snapshot inclusion/remapping;
  `db/store/store_txn_test.go:335-398,1677-1730` guards transactional ANON
  relationships and mutation.
- `db/store/anon_concurrent_race_test.go:11-198` guards concurrent ANON writes;
  `db/store/store_test.go:164-191` guards pending-finalization cloning.
- `vm/anonymous_gc_test.go:22-115` guards reference selection, and
  `db/format/startup_repair_reader_test.go:34-43` exercises fixture loading.

No committed Barn MOO-level tests were found for ANON truth, creation/error
precedence, conversions/formatting, map behavior, object-surface restrictions,
recycle permissions/cascade, task redaction, or a full v17 write/load graph.
These absences do not create new Toast rows where durable conformance already
controls the decision.

## Pinned Toast implementation path

- `src/include/structures.h:99-119,163-197,234-240` defines complex,
  pointer-backed, object-like ANON type 12.
- `src/objects.cc:312-450:bf_create` owns the positional anonymous flag, owner
  validation, anonymous/fertile permission gate, quota, parent indexing,
  temporary-object conversion, and inherited `initialize` call.
- `src/db_objects.cc:373-541` owns pointer identity, serialized references,
  destruction/invalid state, parent validation, `anon_valid`, and `is_valid`.
- `src/utils.cc:127-348` increments references on copy, forbids structural
  `var_dup`, and queues finalization when the last reference is lost.
  `src/server.cc:569-650` and `src/garbage.cc:44-57,345-366` own one-shot
  pending recycle and cyclic finalization.
- `src/objects.cc:788-949:bf_recycle` owns type/valid/control checks, the
  recycled flag, inherited `recycle`, quota return, and destruction.
- `src/utils.cc:380-493` makes every ANON false, compares equality by pointer
  identity, returns zero for the same pointer, and returns positive for two
  distinct ANON pointers in both comparison directions. This comparator owner
  does not by itself settle direct relational opcode behavior; the managed
  scalar row must do so.
- `src/map.cc:677-715` and the MAP execution owners accept ANON in VM map
  literals/indexing but map key builtins reject it. Durable MAP authority now
  freezes reverse-insertion topology and restart transformation.
- `src/numbers.cc:122-189` rejects `toint`, `toobj`, and `tofloat` with E_TYPE.
  `src/list.cc:376-430,448-520` renders both `tostr` and `toliteral` as
  `*anonymous*`; `src/crypto.cc:473-517` therefore gives distinct identities
  the same `value_hash` input.
- `src/property.cc:206-260` and `src/verbs.cc:174-269` reject structural
  add/delete on ANON but retain inherited property reads and verb dispatch.
  Direct indexed ANON read/write is E_TYPE in the execution owners.
- `src/json.cc:380-413,425-508` rejects ANON values and keys with E_INVARG.
- `src/utils.cc:735-750`, `src/execute.cc:451-493`, and
  `src/tasks.cc:2354-2426` preserve task ANON references for wizard/owner and
  redact them to invalid null-pointer ANON references for other viewers.
- `src/include/version.h:36-90` introduces `DBV_Anon` at format 13.
  `src/db_io.cc:180-220,365-400` and
  `src/db_file.cc:294-365,863-979,1041-1101` persist pending values, task
  references, and iterative ANON bodies in current v17. V4 predates ANON.

## Agreements and disagreements

| Surface | Barn | Pinned Toast | Authority decision before final rows |
| --- | --- | --- | --- |
| Type/representation | Type 12 plus numeric runtime identity and out-of-band body | Type 12 plus pointer identity/body | Type 12 is public; internal identity representation is not prescribed. |
| Truth/equality | False; tag plus numeric identity | False; pointer identity | Same/copy versus distinct identity needs one direct scalar row. |
| Ordering | Relational E_TYPE; MAP orders by numeric identity | Direct relational behavior unresolved; MAP comparator is non-total | MAP topology is already durable; the scalar row must settle language relational operators. |
| Conversion/format | Numeric identity exposed; `#N`/`*#N` | Numeric conversions E_TYPE; both text forms `*anonymous*` | Managed scalar row must freeze Toast, not Barn. |
| Object surface | Inherited properties/verbs; structural add rejected; index E_TYPE; ANON identity accepted as range endpoint | Same broad object shape; direct query/index and range-endpoint details unresolved | One focused object/index row must settle the remaining public results. |
| Lifecycle | Explicit recycle plus custom batched reachability/finalization | Refcount/cyclic finalization plus explicit recycle | Existing lifecycle/finalization rows control observable behavior. |
| Persistence | Reachable v17 graph; malformed older type-12 acceptance possible | ANON introduced at format 13; current v17 graph persistence | Current v17 is already durable; malformed historical input is deferred. |

## Existing durable conformance authority

- `language/anonymous.yaml` and `builtins/create.yaml`: ANON constant/type,
  positional create forms and init args, object-number independence,
  initialize dispatch, parent/children behavior, owner/flag permissions,
  parent mutation and validity, inherited properties/verbs, structural
  add-property/add-verb errors, recycle/reference invalidation, recycle hooks,
  lost-reference and one-shot finalization, and invalid-reference operations.
- `builtins/recycle.yaml`: explicit recycle permissions and invalid/recycled
  behavior.
- `language/map_authority.yaml` and `server/map_dump_persistence.yaml`: positive
  ANON VM/`mapvalues` keys, builtin rejection, reverse-insertion topology and
  inequality, and v17 key-identity/topology transformation.
- JSON rows cover ANON as direct/list/map value and map key across the default,
  common-subset, and embedded-types modes.
- Managed persistence/finalization rows cover pending and cyclic finalization,
  garbage cleanup, reachable chains, loaded ANON rewrite, and invalid pending
  value removal.
- Startup repair fixtures cover current anonymous-object graph repair and
  rewrite paths.

Several `callers`, `task_stack`, and `queued_tasks` ANON visibility rows are
skipped placeholders. They do not control task-reference redaction. Because
pinned Toast can replace a hidden reference with an invalid null-pointer ANON
value, this behavior constrains the Phase 2 value representation and remains in
the ANON gate.

## Provisional observable contract

| Dimension | ANON contract before final oracle rows | Authority |
| --- | --- | --- |
| Representation limits | Public type code 12 and object-like identity; no MOO-visible numeric identity. | Toast representation owners; scalar row pending |
| Construction | `create` positional anonymous flag with Toast owner/parent/permission/error order and inherited initialize. | Existing create/anonymous rows |
| Conversion | Numeric/object conversions E_TYPE; both direct text forms are `*anonymous*`. | Toast owners; scalar row pending |
| Equality | Same reference/copy equal; distinct ANON identities unequal. | Toast owners; scalar row pending |
| Ordering | MAP comparator is non-total and insertion-dependent; direct relational behavior remains pending. | MAP authority; scalar relational row pending |
| Hashing/map identity | Distinct ANON values hash the same text but retain reference identity; VM keys valid, `maphaskey`/`mapdelete` reject. | MAP authority; scalar hash row pending |
| Truth | Every ANON is false. | Toast owner; scalar row pending |
| Indexing | Direct ANON index read/write and ANON-as-range-endpoint behavior remain pending; object properties/verbs remain inherited object surfaces. | Existing object rows; focused query/index/range row pending |
| Mutation/copy | Reference copy preserves identity; mutation through aliases of the same ANON is shared; structural add/delete is rejected. | Existing anonymous rows |
| Literal formatting | `tostr` and `toliteral` both return `*anonymous*`. | Toast owners; scalar row pending |
| Serialization | ANON first appears in DB format 13; current v17 writes references and reachable bodies. V4 has no ANON type/section. | Toast DB owners; existing managed persistence rows |
| Overflow/quotas | Creation observes object quota; pointer identity is not arithmetic. | Existing create rows; Toast owners |
| Encoding | JSON rejects ANON values/keys with E_INVARG; `value_hash` uses the common anonymous spelling. | Existing JSON rows; scalar hash row pending |
| Task references | Wizard/owner visibility and other-viewer redaction may produce an invalid ANON value while preserving type 12. | Toast task/reference owners; managed redaction row pending |
| Error behavior | E_TYPE for numeric conversion/structural misuse; direct relational/index/range errors remain pending; E_INVARG/E_PERM follow focused lifecycle/permission rules. | Existing rows plus three focused pending groups |

## Unresolved managed-oracle decisions

After source tracing and row-by-row deduplication, exactly three primitive ANON
decision groups remain:

1. One scalar row proving false truth; same/copy versus distinct identity;
   direct relational results; E_TYPE numeric/object conversions; common
   `*anonymous*` `tostr`/`toliteral`; and identical `value_hash` for distinct
   identities.
2. One object-surface row proving `properties(anon)` and `verbs(anon)` query
   results, direct indexed read/write behavior, and ANON range-endpoint
   behavior, without duplicating existing inherited property/verb or
   structural-mutation rows.
3. One managed task-reference scenario proving owner/wizard versus other-viewer
   handling across `callers`, `task_stack`, `queued_tasks`, and error stacks,
   including the type/validity/literal behavior of any redacted ANON value.

Malformed v4/v5 type-12 rejection is explicitly deferred to the Phase 3 full
historical persistence gate. Native v4 has no ANON contract, and current v17
ANON persistence is already covered.

## Gate status

Steps 1 through 4 of the mandatory authority gate are complete for ANON. Step 5
is open only for the three managed decisions above. No Java API, record, value
helper, object owner, parser representation, or production implementation is
authorized until they pass and this record is updated with their durable
conformance commit and managed result.
