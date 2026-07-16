# List value authority

## Scope

This is the Phase 2 mandatory authority record for the public LIST value family.
It covers representation and configured limits, construction and splicing,
conversion, equality, ordering, map-key validity, truth, indexing and ranges,
mutation/copy behavior, literal formatting, v4/v17 serialization, encoding,
quotas, and errors.

This record does not choose or approve a Java representation, authorize a value
hierarchy, or permit a production edit. The LIST semantic contract is frozen by
the durable managed evidence below, while the primitive-type matrix remains
incomplete.

## Verified identities

- Barn normative specification and implementation reference:
  `5a89ba0250a654bf4ae9383aecd4e6f2b0b363a1`, read only through committed Git
  objects. Barn's working tree was not inspected or modified.
- Toast source and executable authority:
  `/root/src/toaststunt` at
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`, executable
  `/root/src/toaststunt/build-release/moo`.
- Existing durable conformance authority: `../moo-conformance-tests` commit
  `5a9d9e5` plus its ancestors.
- Managed oracle authority: Banteng's owned
  `profiles/toast/stock-wsl-testdb.json` and `scripts/run_toast_wsl.sh`, using
  the bundled disposable `Test.db` fixture.

## Normative Barn specification

- `spec/types.md` defines LIST type tag 4, ordered heterogeneous values,
  one-based indexing, inclusive ranges, recursive structural equality,
  empty-list false truth, and value semantics under mutation.
- The spec prescribes a refcounted immutable/COW implementation. That is an
  implementation sketch rather than observable authority; Barn uses a
  watermark slice and Toast uses refcounted arrays.
- `spec/types.md` describes configured list value-byte limits and rejects LIST
  and MAP as map keys. A separate stale map-key passage overstates which newer
  types are valid; current Barn and Toast both reject collection keys.
- `spec/builtins/lists.md` describes `listappend` and `listinsert` positions but
  does not state Toast's clamping of positions below the first or above the
  last insertion point. Current Barn and Toast both clamp.
- The operator specification permits LIST plus LIST concatenation and LIST plus
  any non-LIST append. It does not state whether this producer observes
  `max_list_value_bytes`.
- `spec/database.md` assigns tag 4 followed by an element count and recursively
  encoded values for v4/v17 input and v17 output.

The spec is accepted only where managed Toast agrees. Storage mechanics are
not public contract, and explicit-position clamping plus LIST-plus quota
behavior require managed observations.

## Current Barn implementation path

- `types/value.go` stores LIST through `*sliceList`. `types/list.go` owns a
  watermark-backed immutable-value representation: ordinary values may share
  committed backing storage, while `set` copies and append reuses only
  uncommitted capacity. `Elements()` exposes the backing slice internally and
  must not become a public mutation path.
- Parser/bytecode list literal and splice paths lower through `vm/op_list.go`.
  Non-LIST splice operands raise E_TYPE and every literal/splice producer calls
  `CheckListLimit`.
- `vm/op_arith.go` implements LIST plus LIST concatenation and LIST plus scalar
  append without `CheckListLimit`. This disagrees with the otherwise enforced
  configured value-byte limit and requires Toast authority.
- `builtins/lists.go` and `types/list.go` own append/insert/delete/set. Explicit
  positions are clamped to the first or final insertion point; they are not
  rejected merely for being outside the current list.
- `types/list.go` and `types/value.go` implement recursive structural equality.
  Nested strings inherit ordinary case-insensitive language equality, while
  `equal()` uses its case-sensitive mode. LIST relational comparison raises
  E_TYPE.
- `types/map.go:IsValidMapKey` rejects LIST as a map key. `vm/op_index.go`
  rejects LIST/MAP keys before lookup; collection-key construction and update
  raise E_TYPE rather than creating hash identity.
- `types/value.go:Truthy` makes the empty LIST false and every nonempty LIST
  true, regardless of element truth.
- `vm/op_index.go` owns one-based indexing, inclusive ranges, `^`/`$`, nested
  updates, and E_TYPE/E_RANGE. A reversed range returns empty before ordinary
  bounds validation, matching the managed range rows.
- `types/list.go` recursively formats `{...}` literal form. `tostr(LIST)` uses
  the deliberately generic `{list}` display rather than the literal contents.
- `db/format/reader_value.go`, `reader_v4.go`, and `writer.go` read/write tag 4,
  element count, and recursive values. The reader does not apply runtime list
  quotas to persisted values.
- `types/value_bytes.go` recursively accounts LIST storage, while
  `builtins/limits.go` owns the configured producer limit and E_QUOTA result.

## Pinned Toast implementation path

- `src/include/structures.h` marks LIST as complex in memory while retaining
  database tag 4. `src/list.cc:new_list` stores length in slot zero and elements
  in one-based slots; a shared canonical empty list is refcounted.
- `src/utils.cc:complex_var_ref,complex_var_dup` and
  `src/list.cc:list_dup,listset` implement refcount sharing plus duplicate on
  visible mutation. Nested prior aliases retain their old value.
- `src/parser.y` and `src/execute.cc` own literal construction, splicing, and
  the LIST-only splice check. Literal/splice producers enforce the configured
  list value-byte ceiling.
- `src/list.cc:insert_or_append` clamps explicit `listappend` and `listinsert`
  positions to the first or final legal insertion point. It then enforces the
  value-byte ceiling.
- `src/execute.cc:1470-1510` implements LIST `+` through `listconcat` or
  `listappend` without a value-byte check. This matches Barn's code path but
  conflicts with the broader limit policy.
- `src/list.cc:listequal` recursively delegates element equality; ordinary
  `==` is case-insensitive for nested strings and `equal()` is case-sensitive.
  `src/execute.cc` rejects relational LIST operands with E_TYPE.
- `src/map.cc` and `src/execute.cc` reject LIST as a scalar map key with E_TYPE.
  The `mapdelete(map, LIST)` overload treats the LIST as multiple keys to
  delete; it is not evidence that LIST itself is hashable.
- `src/utils.cc:is_true` makes only the empty LIST false.
- `src/execute.cc` and `src/list.cc` own one-based indexing, ranges, mutation,
  and COW. Existing conformance densely covers their result/error matrix.
- `src/list.cc` owns recursive literal rendering, generic `{list}` `tostr`,
  value-byte accounting, binary list conversion, and mutation builtins.
- `src/db_io.cc:dbio_read_var,dbio_write_var` read/write tag 4 plus a recursive
  element sequence for every supported database version. No persisted LIST
  quota validation occurs on this path.

## Agreements and disagreements

| Surface | Barn | Pinned Toast | Frozen authority decision |
| --- | --- | --- | --- |
| Storage/COW | Watermark slice | Refcounted array | Observable value isolation controls; neither mechanism is prescribed. |
| Literal/splice quota | Enforced | Enforced | Existing limit rows control. |
| LIST `+` quota | Not enforced | Not enforced | Both scalar append and LIST concatenation bypass the configured ceiling. |
| Explicit append/insert position | Clamped | Clamped | Positions clamp to the first or final legal insertion point. |
| Equality | Recursive; ordinary/case-sensitive modes | Same | Existing nested/equal rows control. |
| Map key | LIST rejected | LIST rejected | Existing construction/lookup/update rows control; multi-key `mapdelete` is separate. |
| Persistence | Recursive tag 4 | Recursive tag 4 | Nested heterogeneous values and post-restart value isolation are preserved. |

## Existing durable conformance authority

- `basic/list.yaml`: construction, all core mutation builtins at ordinary
  positions, LIST `+` concatenation/append, nested-list concatenation, and
  wrong-left-type errors.
- `basic/value.yaml`, `basic/types.yaml`, and `language/equality.yaml`: type,
  empty/nonempty truth, generic `tostr`, recursive `toliteral`, structural
  equality, nested case behavior, and `equal()` basics.
- `language/splice.yaml`: successful, empty, multiple, nested, call-argument,
  and non-LIST splice behavior.
- `language/index_and_range.yaml`: dense one-based `^`/`$`, read/write, range,
  inverted-range, nested, bounds, and type behavior.
- `builtins/collection_improvements.yaml`: COW and alias isolation at multiple
  LIST/MAP nesting depths and repeated mutation.
- `language/in_operator.yaml`, set/list builtin rows, and call-shape rows:
  membership, case modes, arity, types, and ordinary boundary errors.
- `builtins/map.yaml`: collection-key rejection and the distinct multi-key
  `mapdelete` overload.
- `server/limits.yaml`, `features/limits_dynamic.yaml`, and value-byte rows:
  literal/splice, builtin mutation, index/range mutation, binary decode, nested
  value accounting, and configured E_QUOTA behavior.
- Binary/JSON rows already cover LIST as an encoding container where those
  later builtin contracts apply.

## Frozen observable contract

| Dimension | LIST contract | Authority |
| --- | --- | --- |
| Representation limits | Ordered heterogeneous value sequence; configured recursive value-byte ceiling applies to named checked producers, while LIST `+` append/concatenation bypasses it. | Barn/Toast owners; existing limit rows; managed LIST-plus row |
| Construction | `{...}` with LIST-only `@` splice; non-LIST splice E_TYPE; explicit append/insert positions clamp to the first or final legal insertion point. | Existing splice rows; `list_authority::explicit_append_and_insert_positions_clamp` |
| Conversion | Generic `tostr` is `{list}`; `toliteral` recursively emits source-like `{...}`. Unsupported numeric/object conversions raise E_TYPE. | Existing value/types rows |
| Equality | Recursive structural equality; ordinary nested strings are case-insensitive, `equal()` mode is case-sensitive. | Existing equality rows; Barn/Toast owners |
| Ordering | Relational LIST comparison raises E_TYPE. | Barn/Toast owners |
| Hashing/map identity | LIST is not a map key; literal/update/lookup misuse raises E_TYPE. Multi-key `mapdelete` is a separate overload. | Existing map/index rows |
| Truth | Empty LIST false; every nonempty LIST true. | Existing types rows |
| Indexing | One-based; inclusive ranges; `^ == 1`, `$ == length`; strict E_RANGE/E_TYPE except proven inverted-range behavior. | Existing index/range rows |
| Mutation/copy | Public value semantics with recursive alias isolation; storage may share until a visible update. | Existing collection-improvement rows |
| Literal formatting | Recursive `{...}` with element literal forms; generic display remains `{list}`. | Existing value/types rows |
| Serialization | v4/v17 read tag 4/count/elements; v17 writes the same. Nested heterogeneous values preserve exact recursive type/value/literal behavior and post-load isolation. | Barn/Toast DB owners; `list_dump_persistence::nested_heterogeneous_lists_survive_dump_and_restart` |
| Overflow/quotas | Checked producers raise E_QUOTA at the configured recursive byte limit; LIST `+` scalar append and LIST concatenation are explicit unchecked exceptions. | Existing limit rows; `limits::list_plus_bypasses_max_list_value_bytes` |
| Encoding | LIST is the byte-list container for explicit binary APIs; ordinary persistence recursively encodes element values. | Existing binary rows; DB owners |
| Error behavior | E_TYPE for invalid splice/key/type/relational use, E_RANGE for invalid indexing/ranges, and E_QUOTA for checked producers. Explicit insert/append positions clamp instead of raising E_RANGE. | Existing rows; managed position row |

## Durable conformance evidence and oracle result

Conformance commit `5a9d9e5` adds exactly the three focused LIST changes:

- `language/list_authority.yaml` proves all 12 `listappend` and `listinsert`
  position results below zero, at zero, within the list, at the final insertion
  point, and beyond the list.
- `server/limits.yaml::list_plus_bypasses_max_list_value_bytes` proves that both
  scalar append and LIST concatenation succeed with results larger than the
  temporarily configured list value-byte ceiling, then restores that option.
- `server/list_dump_persistence.yaml` proves exact nested heterogeneous LIST
  types, values, literal form, case-insensitive MAP access, equality, and
  copy-on-write isolation after a managed v17 dump/restart.

The complete intended selection passed without correction against pinned Toast
`aecc51e9449c6e7c95272f0f044b5ba38948459e`:

```text
3 passed, 11541 deselected in 6.24s
```

The run used Banteng's owned stock profile and WSL launcher against a disposable
copy of the bundled `Test.db` fixture. No new truth, formatting, splice,
ordinary equality, map-key, indexing, generic COW, or checked-producer quota
row was added because existing durable rows already settle those decisions.

No new v4 fixture is added in this primitive slice. Both source owners route
v4 LIST values through the same recursive tag-4 reader, with no LIST-specific
v4 disagreement. Full fixture migration remains in the ordered persistence
phase.

## Gate status

The LIST semantic contract is frozen. No Java API, record, collection owner,
copy helper, parser representation, or production implementation is authorized
by this record alone. The primitive-type matrix remains blocked on the other
family-specific authority gaps, beginning with MAP.
