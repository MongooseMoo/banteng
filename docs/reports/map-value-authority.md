# Map value authority

## Scope

This is the Phase 2 mandatory authority record for the public MAP value family.
It covers representation and configured limits, construction and evaluation
order, conversion, equality, ordering and key identity, truth, indexing and
ranges, mutation/copy behavior, literal formatting, v17 serialization,
encoding, quotas, and errors.

This record does not choose or approve a Java representation, authorize a value
hierarchy, or permit a production edit. The MAP semantic contract is frozen by
the durable managed evidence below, while the primitive-type matrix remains
incomplete.

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

- `spec/types.md:182-205` and `spec/builtins/maps.md:27-30,213-228` form a
  three-way contradiction about valid key types: the general builtin passage
  says every type is hashable, the types passage explicitly permits BOOL,
  LIST, MAP, ANON, and WAIF, and the detailed map-builtin passage includes
  BOOL/ANON/WAIF while rejecting LIST/MAP.
- `spec/operators.md:454-470,915-925` defines deep MAP equality and empty-MAP
  false truth. `spec/builtins/maps.md:258-269` and
  `spec/statements.md:179-201` deny insertion-order authority and describe
  value/key iteration.
- `spec/operators.md:531-554` says `in` tests MAP keys, while
  `spec/builtins/maps.md:283-296` says MAP membership is broken. Current Barn
  and Toast instead search values in canonical key order.
- `spec/builtins/maps.md:32-45` and `spec/operators.md:89-102` specify indexed
  add/replace with value semantics. MAP range behavior and the distinction
  between case-insensitive `==` and case-sensitive `equal()` are not stated
  completely.
- `spec/builtins/types.md:45-79` distinguishes opaque `tostr(MAP)` from
  recursive `toliteral(MAP)`. `spec/database.md:11-18,47-66` assigns tag 10
  with count followed by key/value pairs in v17. Native MAP values do not exist
  in v4.
- The normative MAP text does not define the configured recursive value-byte
  boundary, the actual controlling server option, duplicate comparator-key
  replacement, literal operand evaluation order, or the LIST-valued
  `mapdelete` extension.

The spec is accepted only where managed Toast agrees. Its key-validity,
membership, range-order, and option-name statements are not sufficient
authority for Banteng.

## Current Barn implementation path

- `parser/parser.go:766-829:parseMapExpr` and
  `bytecode/compiler.go:2870-2893:compileMap` lower literals into an empty MAP
  plus repeated indexed assignment, compiling each value before its key.
  `vm/collection_helpers.go:18-65:setAtIndex` owns literal key validation and
  configured limit checks.
- `types/map.go:10-215` owns the insertion-ordered `goMap`, rendered-value
  `keyHash`, COW set/delete, deep equality, literal output, and
  `CompareMapKeys`; public accessors are at `:226-281`.
- `types/value.go:136-195,217-279` owns MAP truth, equality, formatting, and
  length dispatch. `builtins/types.go:24-82,90-208,214-320` owns conversion,
  `tostr`, `toliteral`, and `equal()`.
- `bytecode/compiler.go:1452-1534` and `vm/op_index.go:10-424` own indexed and
  ranged read/write plus `^`/`$`. `vm/op_iter.go:9-55` owns snapshot iteration;
  `vm/op_compare.go:11-320` owns equality, ordering, and value membership.
- Assignment rejects BOOL, WAIF, LIST, and MAP keys with E_TYPE. Direct read
  indexing pre-rejects only LIST/MAP, so absent BOOL/WAIF keys return E_RANGE;
  `mapvalues` likewise returns E_RANGE for composite missing-key arguments.
- `builtins/maps.go:14-204` owns `mapkeys`, `mapvalues`, `mapdelete`,
  `maphaskey`, and the undocumented `mapmerge`; registration is at
  `builtins/registry.go:134-139`.
- `types/value_bytes.go:11-47` and `builtins/limits.go:18-55,126-170,319-374`
  own recursive MAP size accounting and E_QUOTA checks. Barn starts at 32
  bytes plus recursive key/value sizes, defaults `max_map_value_bytes` to
  64,537,861, and raises E_QUOTA only when `size > limit`.
- `db/format/reader_value.go:12-113` and
  `db/format/writer.go:56-75,168-303` own tag-10 persistence. Barn rejects MAP
  before v17; reads bypass normal key/quota validation and collapse duplicate
  or case-equivalent keys later-wins, while negative counts can panic. The
  writer emits insertion-order pairs without key/quota validation. Both paths
  preserve raw Go string bytes rather than validating/transcoding the spec's
  stated Latin-1 contract.

Barn's public MAP surfaces generally sort through `CompareMapKeys`, but its
range positions use internal insertion order and its `^`/`$` markers use
canonical minimum/maximum keys. The implemented order is
`INT < OBJ/ANON < FLOAT < ERR < STR`, while its comments claim
`INT < FLOAT < OBJ`; its actual valid-key set is
INT/FLOAT/STR/OBJ/ANON/ERR structurally, with ANON rejected specifically by
`maphaskey` and `mapdelete`. BOOL, WAIF, LIST, and MAP are rejected by
assignment. Its rendered-value hash can collapse distinct floats with
identical 15-digit text, distinguish signed zero despite equality, and retrieve
NaN keys despite `Equal(NaN, NaN)` being false. Its Unicode
`ToLower`/`EqualFold` string behavior also diverges from the byte-oriented MOO
model. These are reference observations, not Toast authority.

## Existing Barn reference tests

- `parser/parser_map_test.go:5-100` covers literal syntax, including syntactic
  LIST/MAP keys.
- `types/value_struct_test.go:8-50` covers INT/FLOAT/STR hash separation.
- `builtins/maps_test.go:10-51` covers composite missing keys returning E_RANGE
  from `mapvalues`, including registry dispatch.
- `builtins/review_data_test.go:222-259` covers current mixed-key order;
  `vm/review_bugs_test.go:12-82` covers MAP `in` searching values in canonical
  order; `vm/promote_numbers_test.go:263-276` covers promoted numeric value
  membership.
- `vm/string_inplace_aliasing_test.go:38-44` covers captured MAP value/key
  isolation under string append, and `types/typecode_test.go:6-24` covers type
  code 10.

No direct committed Barn tests were found for general MAP COW, case-colliding
keys, BOOL/ANON/WAIF key validity, float edge-key identity, MAP equality/truth,
ranges/markers, quota boundaries, or v17 MAP round trips. These absences do not
create new Toast rows where the durable conformance suite already supplies the
authority.

## Pinned Toast implementation path

- `src/include/structures.h:99-119,163-192` defines MAP as complex type 10
  backed by `rbtree *`.
- `src/parser.y:681-695` and `src/code_gen.cc:522-531,803-805` compile literal
  pairs in source order while evaluating each value expression before its key
  expression.
- `src/map.cc:63-88,288-366,374-460` owns the comparator and RB-tree. Storage,
  traversal, rendering, hashing, and database output are comparator-sorted,
  never insertion-ordered.
- `src/map.cc:584-763` owns construction, lookup, COW, insertion, and
  replacement; deletion uses `rberase` at `:374-460` and public
  `bf_mapdelete` at `:1004-1063`. LIST/MAP keys are rejected.
  Comparator-equal replacement removes the prior pair and stores the new key
  spelling and value.
- `src/map.cc:765-943` and `src/utils.cc:390-492,608-634` own equality, truth,
  recursive size, ordering, ranges, and range replacement.
- `src/map.cc:1004-1147` owns the public map builtins. ANON keys are accepted by
  literal/index/range VM paths but rejected by `maphaskey` and `mapdelete`.
  LIST-valued `mapdelete` is a multi-key deletion overload, not LIST-key
  validity.
- `src/execute.cc:1086-1115,1216-1255,1624-1808,2290-2438,2642-2760` owns
  literal creation, index/range operations, markers, nested COW, and loops.
  MAP relational operators raise E_TYPE; `==` uses case-insensitive equality.
- `src/list.cc:376-506,631-655,1162-1209` owns opaque display and recursive
  comparator-ordered literal rendering. `src/crypto.cc:473-517:value_hash`
  hashes that canonical literal.
- `src/db_io.cc:153-221,338-396` and
  `src/db_file.cc:48-60,837-875,1031-1044` own v17 count/pair persistence and
  reload through ordinary `mapinsert()`.
- `src/include/server.h:197-217` incorrectly registers
  `SVO_MAX_MAP_VALUE_BYTES` against `max_list_value_bytes`.
  `src/map.cc:1004-1063:bf_mapdelete` contains no local result-size check.
  Managed Toast nevertheless raises E_QUOTA for `mapdelete` under the active
  LIST-backed limit, so an enclosing producer path enforces the observable
  result. MAP literal construction bypasses that same limit.

The comparator is not equivalent to scalar equality or a total order. INT and
OBJ comparison narrows subtraction to `int`, so INT values and OBJ references
separated by `2^32` can collapse. Unequal BOOL, WAIF, and ANON values compare
as positive in both directions, making topology dependent on insertion order.
INT and FLOAT remain distinct key types. Durable rows freeze the analogous INT
and OBJ collisions, adjacent-float and signed-zero behavior, BOOL lookup
anomaly, and insertion-dependent BOOL/WAIF/ANON topology.

## Agreements and disagreements

| Surface | Barn | Pinned Toast | Frozen authority decision |
| --- | --- | --- | --- |
| Storage/order | Insertion storage, canonically sorted public paths; ranges use insertion positions | Comparator-sorted RB-tree for all storage/public paths | Toast comparator order controls; existing ordinary order/range rows are durable. |
| Key validity | Assignment permits INT/FLOAT/STR/OBJ/ANON/ERR; `maphaskey`/`mapdelete` additionally reject ANON | VM also accepts BOOL/WAIF/ANON; `maphaskey`/`mapdelete` reject ANON while multi-key `mapvalues` accepts it | Positive ANON VM/`mapvalues` use succeeds; `maphaskey`/`mapdelete` raise E_TYPE. |
| INT comparator | Rendered type/value hash keeps wide INTs distinct | Narrowed subtraction collapses INT values separated by `2^32` | Keys `0` and `4294967296` collide; later insertion/update supplies the retained key/value. |
| Case replacement | Equal-fold key replaces spelling/value in original slot | Equal-fold comparator replacement stores new spelling/value | New spelling/value survives v17 restart. |
| Equality/truth | Deep equality; empty false | Same for total-comparator keys; reverse insertion changes BOOL/WAIF/ANON topology and produces unequal maps | Existing ordinary rows plus managed non-total rows control. |
| Ranges/markers | Mixed insertion/canonical models | Comparator order throughout | Dense current Toast-derived rows control; no duplicate generic range row. |
| MAP limit | Checks configured `max_map_value_bytes` on producers | MAP option token uses `max_list_value_bytes`; indexed/ranged mutation and `mapdelete` enforce it, literal construction bypasses it | Corrected managed rows freeze the actual option and producer split. |
| Persistence | v17 tag 10; writer emits insertion pairs | v17 tag 10; writer emits comparator order and reader reinserts, reversing non-total topology | Managed restart freezes total order, replacement/high-byte preservation, topology reversal, and stable mappings/equality outcomes. |

## Existing durable conformance authority

- `builtins/map.yaml`, `basic/types.yaml`, and `basic/value.yaml`: construction,
  type, empty/nonempty truth, conversion errors, opaque `tostr`, and recursive
  `toliteral`.
- `builtins/map.yaml`: deep equality and case modes; sorted INT/FLOAT/STR
  `mapkeys`/`mapvalues`; indexed add/replace; ordinary deletion, lookup,
  key-presence, missing-key, and call-shape behavior; LIST/MAP key rejection;
  multi-key deletion.
- `language/equality.yaml`: nested MAP equality, case-insensitive scalar key
  identity, new spelling after equal-key replacement, signed-zero/adjacent
  float identity, and scalar type separation.
- `language/boolean_authority.yaml`, `language/object_authority.yaml`,
  `language/error_authority.yaml`, and `language/waif_authority.yaml`: the
  specifically asserted BOOL lookup anomaly, OBJ comparator collision, ERR
  equality, and one-direction WAIF key identity. They do not freeze reverse-
  insertion equality/topology for BOOL/WAIF, or any positive ANON key path.
- `language/index_and_range.yaml`: dense indexed/ranged read/write, inverted
  ranges, integer-key ambiguity, `^`/`$`, and E_TYPE/E_RANGE coverage.
- `builtins/collection_improvements.yaml`: top-level and nested MAP COW and
  alias isolation.
- `language/looping.yaml`: value/key iteration in canonical key order.
- Managed FLOAT, STR, OBJ, ERR, BOOL, and WAIF restart rows already prove many
  constituent scalar values, adjacent FLOAT keys, OBJ comparator collisions,
  and case-folded STR lookup through v17 restart.
- `builtins/algorithms.yaml` freezes empty-MAP hashes. Nonempty `value_hash` is
  deferred to its later builtin family because canonical `toliteral` order is
  already a primitive MAP decision.

Descriptions that call `mapdelete(map, LIST)` a LIST-key test are misleading:
that syntax invokes the multi-delete overload. Their expected results are not
used as evidence that LIST is a valid scalar key.

## Frozen observable contract

| Dimension | MAP contract | Authority |
| --- | --- | --- |
| Representation limits | Comparator-keyed heterogeneous mapping with recursive value-byte accounting. On pinned Toast, checked MAP producers read `$server_options.max_list_value_bytes`; `$server_options.max_map_value_bytes` does not control them. | Toast owners; corrected managed limit rows |
| Construction | `[...]` evaluates each value before its key, then inserts in source pair order; comparator-equal keys replace. | Toast compiler/source; `map_authority::map_literal_evaluates_each_value_before_its_key` |
| Conversion | `tostr` is `[map]`; `toliteral` recursively emits comparator-ordered `[key -> value, ...]`; numeric/object conversions E_TYPE. | Existing value/map rows |
| Equality | Deep mapping equality ignores insertion history for total-comparator keys; `==` is recursively case-insensitive and `equal()` is case-sensitive. Reverse-inserted BOOL/WAIF/ANON maps have opposite topology and compare unequal under both modes. | Existing map/equality rows; managed non-total rows |
| Ordering | MAP relational operators raise E_TYPE; ordering exists only for key topology/traversal. | Toast execution owner; existing rows |
| Key identity | Ordinary indexing/replacement/deletion and default `maphaskey` compare STR keys case-insensitively; `maphaskey(map, key, 1)` and multi-key `mapvalues(map, keys...)` are case-sensitive. Equal-fold replacement stores new spelling. INT `0` and `4294967296` collide; INT/FLOAT remain distinct; LIST/MAP are invalid; ANON is VM-valid but rejected by `maphaskey`/`mapdelete`. | Existing case/type rows; `language/map_authority.yaml` |
| Truth | Empty MAP false; every nonempty MAP true. | Existing rows |
| Indexing/ranges | Key lookup/update and comparator-ordered inclusive ranges; `^`/`$` are comparator minimum/maximum keys. | Existing dense index/range rows |
| Mutation/copy | Public value semantics with recursive alias isolation; comparator-equal update replaces key/value. | Existing collection/equality rows |
| Literal formatting | Comparator-ordered recursive literal, independent of ordinary insertion order where the comparator is total. | Existing formatting/order rows |
| Serialization | v17 tag 10/count/comparator-ordered pairs; reload reinserts through the same comparator. Total-key order, replacement spelling, and high bytes survive. BOOL/WAIF/ANON traversal/literal topology reverses, while mappings and equality outcomes remain stable. Native MAP is absent from v4. | Toast DB owners; `map_dump_persistence::ordering_spelling_bytes_and_non_total_topology_round_trip` |
| Overflow/quotas | Indexed/ranged MAP mutation and `mapdelete` enforce the LIST-backed configured size; MAP literal construction bypasses it. With `max_concat_catchable` enabled a checked failure is E_QUOTA, otherwise Toast aborts the task as out-of-seconds. | Corrected managed limit rows |
| Encoding | Ordinary database persistence recursively encodes pairs; `value_hash` hashes canonical MAP literal text. JSON and exact hash outcomes belong to later builtin-family gates. | Toast DB/hash owners; existing rows |
| Error behavior | E_TYPE for invalid scalar-key/type/relational use and E_RANGE for missing keys. ANON works in VM/`mapvalues` but raises E_TYPE in `maphaskey`/`mapdelete`. Multi-key deletion reports `E_RANGE`, `Key 99 not found in map`, and raised value `99` for the frozen case. | Existing rows; focused managed authority rows |

## Durable conformance evidence and oracle result

Conformance commit `e5b1e47` contains exactly three MAP paths:

- `language/map_authority.yaml` proves INT `2^32` comparator collision and
  replacement, ANON surface asymmetry, reverse BOOL/WAIF/ANON topology and
  inequality, value-before-key literal evaluation, and complete multi-key
  deletion error detail.
- `server/limits.yaml` corrects four rows to freeze LIST-backed indexed/ranged
  mutation and `mapdelete` enforcement plus MAP-literal quota bypass.
- `server/map_dump_persistence.yaml` proves total mixed-key order, replacement
  spelling, high-byte identity, non-total topology reversal, stable mappings,
  and stable equality outcomes through a managed v17 dump/restart.

The first complete source-derived selection produced:

```text
9 passed, 3 failed, 11540 deselected in 6.42s
```

Pinned Toast disproved the proposed `mapdelete` bypass and MAP-literal check,
and disproved unchanged non-total topology across restart. A focused diagnostic
recorded the exact write/reinsert reversal. After replacing those expectations
with the observed behavior, the three corrected rows passed:

```text
3 passed, 11549 deselected in 6.04s
```

The complete intended selection then passed:

```text
12 passed, 11540 deselected in 6.29s
```

All runs used Banteng's owned stock profile and WSL launcher against a
disposable copy of the bundled `Test.db` fixture.

No new generic truth, formatting, ordinary equality, total-order key,
LIST/MAP rejection, range, COW, membership, iteration, or basic encoding row is
justified. Barn's registered `mapmerge` extension is absent from its generated
signatures and specification, and pinned Toast's `register_map()` has no
`mapmerge` public entry point; it is not an unresolved Toast MAP behavior.
Exact nonempty cryptographic hashes remain a later builtin-family decision.

## Gate status

The MAP semantic contract is frozen. No Java API, record, collection owner,
comparator, copy helper, parser representation, or production implementation
is authorized by this record alone. The primitive-type matrix remains blocked
on the remaining family-specific authority gap, ANON.
