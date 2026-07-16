# Boolean value authority

## Scope

This record completes the Phase 2 mandatory authority gate for the public BOOL
value family only. It freezes representation limits, construction, conversion,
equality, ordering, map-key identity, truth, indexing, mutation/copy behavior,
literal formatting, v17 serialization, overflow, encoding, and error behavior.
It does not choose or approve a Java representation, change existing Banteng
production code, or authorize the later JSON builtin surface.

## Verified identities

- Barn normative specification and implementation reference:
  `5a89ba0250a654bf4ae9383aecd4e6f2b0b363a1`, read only through committed Git
  objects. Barn's working tree was not inspected or modified.
- Toast source and executable authority:
  `/root/src/toaststunt` at
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`, executable
  `/root/src/toaststunt/build-release/moo`.
- Durable conformance authority after this slice:
  `../moo-conformance-tests` commit `39ec3d8`.
- Managed oracle authority: Banteng's owned
  `profiles/toast/stock-wsl-testdb.json` and `scripts/run_toast_wsl.sh`, using
  the bundled disposable `Test.db` fixture.

## Normative Barn specification

- `spec/types.md:5-23,194-214` assigns BOOL type code 14, public values
  `true`/`false`, and false/true truth behavior. Its opening enumeration says
  there are nine types and omits ANON; that defect does not change BOOL's tag.
- `spec/types.md:182-205` says BOOL is a valid map key.
- `spec/operators.md:443-493` says different types are never equal. That claim
  conflicts with both current Barn and Toast for BOOL versus INT 0/1.
- `spec/operators.md:495-508` restricts relational ordering to INT, FLOAT, and
  STR and therefore implies E_TYPE for BOOL.
- `spec/database.md:47-67` assigns persisted tag 14 and payload 0 or 1.

The spec is accepted only where the managed Toast oracle agrees. Its strict
cross-type equality, BOOL ordering, and map-key claims are not authority.

## Current Barn implementation path

- `types/typecode.go:3-16` assigns `TYPE_BOOL = 14`.
- `types/value.go:68-128` owns the zero-allocation BOOL constructor and accessor;
  `NewBool` normalizes the payload to 0/1.
- `types/value.go:138-158` makes only false falsy; `types/value.go:162-193`
  formats BOOL as `true` or `false`; `types/value.go:217-262` compares two BOOL
  values by tag and normalized payload.
- `vm/op_compare.go:11-62` routes `==` and `!=` through `boolIntEqual` before
  ordinary value equality. `vm/operators.go:307-329` makes only false equal INT
  0 and true equal INT 1, symmetrically.
- `vm/op_compare.go:228-319` has no BOOL relational case and returns E_TYPE.
- `types/map.go:272-281` excludes BOOL from both internal and builtin map keys.
- `vm/op_index.go:10-78` handles LIST, STR, and MAP only; BOOL indexed read and
  write return E_TYPE through the existing owner paths.
- `db/format/reader_value.go:207-219` accepts tag 14 only for v17+, reads an
  integer payload, and normalizes it through `NewBool`; `db/format/writer.go:216-233`
  emits tag 14 and payload 0/1.

Barn agrees with Toast on the public tag, normalized payload, truth, forms,
BOOL/INT equality, indexed errors, and v17 encoding. It disagrees on same-type
ordering and map-key eligibility.

## Pinned Toast implementation path

- `src/include/structures.h:76-119,163-178,255-260` separates public/database
  tags from the in-memory complex flag, assigns BOOL tag 14, stores a C++
  `bool`, and normalizes construction to true/false.
- `src/eval_env.cc:76-119` exposes `$bool`, `$true`, and `$false` for v17.
  `src/parser.y:358-383` has no dedicated boolean literal production; source
  `true` and `false` resolve through the runtime environment. This is observable
  construction behavior, not a mandate for Banteng's parser organization.
- `src/utils.cc:381-399` owns truth. `src/utils.cc:444-492` owns equality and
  makes BOOL equal INT only for true/1 and false/0.
- `src/execute.cc:1295-1309` returns INT 0/1 from equality. Relational dispatch
  at `src/execute.cc:1312-1379` accepts same-type BOOL but falls through its
  impossible-type default with comparison zero. Thus `<` and `>` are false,
  while `<=` and `>=` are true, for either pair of BOOL values. Mixed BOOL/INT
  ordering raises E_TYPE.
- `src/map.cc:63-88,223-247,677-710` uses `compare`, not a hash table, for map
  identity and accepts BOOL keys. `src/utils.cc:408-440` returns zero for equal
  BOOL values and positive one for distinct BOOL values in both directions,
  violating comparator antisymmetry. BOOL and INT remain different map-key
  types even when language equality says they are equal.
- `src/numbers.cc:122-189,420-450` converts BOOL to INT but rejects BOOL to
  FLOAT. `src/objects.cc:254-280` converts BOOL to OBJ through the integer path.
- `src/list.cc:376-429,448-521,1161-1210` renders both `tostr()` and
  `toliteral()` as `true` or `false`.
- `src/execute.cc:1216-1256,1624-1653` rejects BOOL as an indexed collection.
- `src/db_io.cc:153-221,346-401` reads and writes tag 14 with numeric payload
  0/1 recursively inside other values.

Toast's JSON BOOL-key path is intentionally outside this record. Source review
shows a potential panic rather than an ordinary rejection; it must receive its
own managed safe-process discriminator before the later JSON builtin slice.

## Frozen observable contract

| Dimension | BOOL contract | Authority |
| --- | --- | --- |
| Representation limits | Exactly two public values; type code 14; persisted payload normalized to 0/1. Host field width and Java class shape are internal choices. | Barn/Toast source; `types::typeof_bool_true_value`, `types::typeof_bool_false_value`, `types::constant_BOOL_value` |
| Construction | Source `true` and `false` evaluate to BOOL values. Toast exposes them through its runtime environment rather than a parser literal node. | Toast source; existing `types::` rows |
| Conversion | `toint(true/false)` is 1/0; `toobj(true/false)` is #1/#0; `tofloat(BOOL)` raises E_TYPE; `tostr` is `true`/`false`. | `basic/types.yaml` existing rows; Toast conversion owners |
| Equality | Same BOOL payloads are equal; true equals INT 1 and false equals INT 0; other BOOL/INT pairs are unequal; results are INT 0/1. | Existing `types::bool_*equal*` rows; Barn/Toast source |
| Ordering | Any same-type BOOL pair has default comparison zero: `<` and `>` false, `<=` and `>=` true. BOOL versus INT ordering raises E_TYPE. | `boolean_authority::boolean_relational_comparisons_use_default_zero`, `boolean_integer_relational_comparisons_raise_type` |
| Hashing/map identity | Toast has no BOOL hash contract; maps use the non-antisymmetric comparator. FALSE and TRUE can coexist pairwise in either insertion order. BOOL and equal INT keys are distinct, but in the proven four-key sequence TRUE is retained by `mapkeys()` and unreachable through `maphaskey`. Banteng must reproduce the observable results, not the broken tree mechanism. | `boolean_authority::distinct_boolean_keys_survive_both_insertion_orders`, `boolean_and_equal_integer_mixed_map_reachability` |
| Truth | false is falsy; true is truthy; logical negation returns INT 1/0. | Existing `types::bool_*truth*`, `types::truthy_true`, `types::truthy_false` rows |
| Indexing | Indexed read and write on BOOL raise E_TYPE. | `boolean_authority::boolean_indexed_read_and_write_raise_type` |
| Mutation/copy | BOOL has no mutable payload or collection owner. Assignment copies/rebinds a scalar value; no alias-visible mutation exists. This does not select a Java representation. | Barn/Toast scalar source paths; indexed error row |
| Literal formatting | `toliteral(true/false)` is exactly `true`/`false`. | Existing `types::toliteral_bool_true`, `toliteral_bool_false` rows |
| Serialization | v17 writes tag 14 and payload 1/0; type and both values survive managed dump/restart inside a list. | `boolean_dump_persistence::boolean_type_and_values_survive_dump_and_restart` |
| Overflow | Not applicable: construction and persistence normalize to the two values. Integer arithmetic after conversion belongs to INT authority. | Barn/Toast source |
| Encoding | Not applicable to the BOOL payload; only its ASCII string/literal forms are exposed. | Formatting rows and source |
| Error behavior | Unsupported FLOAT conversion, indexed read/write, and mixed relational ordering raise E_TYPE. | Existing conversion rows and new authority rows |

## Durable conformance evidence and oracle result

Existing BOOL rows in `basic/types.yaml` already prove tag/constants,
construction, truth, logical negation, BOOL/INT equality, conversion, and
string/literal formatting.

Conformance commit `39ec3d8` adds five focused language rows in
`language/boolean_authority.yaml` and one managed restart row in
`server/boolean_dump_persistence.yaml`.

The first managed run selected six rows. Four passed; the proposed combined-map
lookup raised E_RANGE, and the restart result exposed the harness's BOOL value
representation as strings `"true"`/`"false"`. The row was narrowed to observe
key order and reachability without performing a failing lookup, and the
harness-level expectation was corrected. The second run passed exactly:

```text
6 passed, 11519 deselected in 6.21s
```

Both runs used Banteng's owned stock profile and WSL launcher with pinned Toast
`aecc51e9449c6e7c95272f0f044b5ba38948459e` against a disposable fixture.

## Gate result

The BOOL semantic contract is frozen. No Java API, hierarchy, record, hash
algorithm, collection adapter, or helper is authorized by this record. The
complete primitive-type matrix remains blocked on the other family-specific
authority gaps; no production representation work may begin from this BOOL
record alone.
