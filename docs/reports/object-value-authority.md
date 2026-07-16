# Object-reference value authority

## Scope

This record completes the Phase 2 mandatory authority gate for the public OBJ
value family. It covers representation
limits, source construction, conversion, equality, ordering, map-key identity,
truth, indexing, mutation/copy behavior, literal formatting, v4/v17
serialization, overflow, encoding, and error behavior.

This record does not choose or approve a Java representation, authorize a value
hierarchy, or permit a production edit. The complete primitive-type matrix
remains required before any further Java value representation is designed.

## Verified identities

- Barn normative specification and implementation reference:
  `5a89ba0250a654bf4ae9383aecd4e6f2b0b363a1`, read only through committed Git
  objects. Barn's working tree was not inspected or modified.
- Toast source and executable authority:
  `/root/src/toaststunt` at
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`, executable
  `/root/src/toaststunt/build-release/moo`.
- Durable conformance authority after this slice: `../moo-conformance-tests`
  commit `276376e` plus its ancestors.
- Managed oracle authority: Banteng's owned
  `profiles/toast/stock-wsl-testdb.json` and `scripts/run_toast_wsl.sh`, using
  the bundled disposable `Test.db` fixture.

## Normative Barn specification

- `spec/types.md` section 2.4 defines OBJ as an object reference whose ID is a
  signed integer. It identifies invalid or recycled references as values that
  remain representable even when dereference fails.
- `spec/types.md` section 2.8 and section 17 say only their enumerated
  zero/empty values are false and everything else is true. That conflicts with
  both current Barn and pinned Toast, where every OBJ is false.
- `spec/types.md` section 3.2 lists only INT and STR as `toobj()` inputs. Both
  current Barn and pinned Toast accept a broader scalar input matrix.
- `spec/operators.md` section 9.2 restricts ordering to INT, FLOAT, and STR.
  Both current Barn and pinned Toast implement numeric ordering for OBJ.
- `spec/database.md` section 3.1 assigns persisted tag 1 but describes the OBJ
  payload as `#N`. Barn's writer and pinned Toast both write a bare signed
  decimal payload after the tag.
- `spec/database.md` section 10.1 requires Latin-1 database bytes. OBJ's tag and
  signed decimal payload are ASCII and therefore do not expose a separate
  encoding choice.

The spec is accepted only where the managed Toast authority agrees. Its OBJ
truth, conversion-input, relational-order, and payload-spelling passages are
stale or incomplete.

## Current Barn implementation path

- `types/objid.go:3-17` defines `ObjID` as `int64` and names the ordinary
  negative sentinels. `types/value.go:17-38,86-87,112-116` stores the signed
  bits inline; `NewObj` does not validate existence, recycling, or range beyond
  the host type.
- `parser/lexer.go:391-416` recognizes `#-?[0-9]*` and
  `parser/parser.go:636-643` uses `strconv.ParseInt` at 64 bits. An overflowing
  source reference is a compile error rather than a wrapped value.
- `builtins/types.go:builtinToint,builtinToobj,valueToStr` owns conversion and
  string form. `toobj()` accepts OBJ, ANON, INT, FLOAT, ERR, BOOL, and STR;
  FLOAT truncates, malformed or overflowing STR becomes `#0`, and unsupported
  collections raise E_TYPE.
- `types/value.go:Equal` compares exact type and exact 64-bit ID. OBJ and ANON
  with the same numeric payload are unequal.
- `vm/op_compare.go:compareValues` orders OBJ numerically by signed ID despite
  the narrower normative operator passage.
- `types/map.go:keyHash,CompareMapKeys,IsValidMapKey` gives OBJ typed-ID map
  identity and canonical numeric order. Invalid or recycled IDs remain ordinary
  scalar keys.
- `types/value.go:Truthy` has no OBJ case, so every OBJ is false. This agrees
  with pinned Toast and conflicts with the normative Barn truth passage.
- `vm/op_index.go:executeIndex` accepts OBJ as a MAP key and rejects OBJ as a
  LIST/STR index or indexed base with E_TYPE. An absent OBJ map key raises
  E_RANGE. The range helper separately permits OBJ endpoints by numeric ID.
- OBJ is a pointer-free scalar in `types.Value`; ordinary assignment, stack,
  local, and `copy()` paths preserve its type and bits without an ownership or
  cloning rule.
- `types/value.go:Value.String` and `builtinToliteral` emit `#` followed by the
  signed decimal ID for valid, invalid, and recycled references alike.
- `db/format/reader.go`, `reader_value.go`, `reader_helpers.go`, and
  `writer.go` route v4, v5, and v17 OBJ values through tag 1 plus signed decimal
  parsing/writing. The reader accepts a bare or `#`-prefixed payload; the v17
  writer emits the bare form.

Barn agrees with Toast on signed scalar storage, ordinary equality and order,
all-OBJ false truth, map-key eligibility, indexed-base errors, literal form,
and the v17 payload. Its checked source parser and equality-consistent map
comparator do not reproduce Toast's unchecked boundary and narrowing paths.

## Pinned Toast implementation path

- `src/include/structures.h:30-55` makes `Objid` an alias of `Num`. The selected
  build uses signed `int64_t`, `MAXOBJ == 9223372036854775807`, and the
  asymmetric nominal `MINOBJ == -9223372036854775807` even though the storage
  type can hold the actual signed minimum.
- `src/parser.y:910-931` accumulates decimal object-literal digits without an
  overflow check. Out-of-range source behavior is therefore not a portable C++
  source contract and requires the pinned executable.
- `src/numbers.cc:63-95,123-157` and `src/objects.cc:255-269` own `toobj()`.
  INT and OBJ expose their payload; ERR and BOOL convert numerically; finite
  FLOAT truncates; STR accepts the object-number syntax; malformed text becomes
  zero; LIST, MAP, ANON, and WAIF raise E_TYPE. Native string overflow is not
  checked and needs oracle evidence at the boundary.
- `src/utils.cc:381-400` has no OBJ truth case, so every object reference is
  false regardless of validity or ID.
- `src/utils.cc:444-455` implements exact same-type Objid equality, and
  `src/execute.cc:1295-1305` routes ordinary equality through it.
- `src/execute.cc:1312-1371` implements same-type OBJ relational operators
  using the safe signed `compare_integers()` owner in
  `src/numbers.cc:211-218`.
- `src/utils.cc:408-440` compares OBJ map keys by subtracting IDs and returning
  `int`; `src/map.cc:85-87,223-244,344-348` uses that comparator for lookup and
  insertion. A difference such as `4294967296` can narrow to zero even though
  ordinary equality says the IDs differ. This is an observable Toast quirk,
  not a representation technique to copy blindly.
- `src/include/utils.h:53-67` copies non-complex OBJ directly. There is no
  alias-visible payload or clone operation.
- `src/execute.cc:1587-1664,1668-1743` accepts OBJ as a MAP key and rejects OBJ
  as an indexed base with E_TYPE. LIST/STR positions require INT, so an OBJ
  position also raises E_TYPE.
- `src/list.cc:378-393,449-498,1162-1200` emits `#` plus signed decimal for both
  string and literal rendering.
- `src/db_io.cc:77-88,154-183,308-329,348-375` reads and writes type tag 1 plus
  an unchecked signed numeric payload for the common value path used by the
  supported database versions. `src/db_file.cc:220-438` owns legacy and current
  object-record loading/writing, while `src/include/version.h:42-84` selects
  current v17 output.
- `src/list.cc:1558-1587` rejects OBJ binary encoding with E_INVARG; an existing
  managed row already freezes that result.

## Agreements and disagreements

| Surface | Barn | Pinned Toast | Authority decision before final rows |
| --- | --- | --- | --- |
| Width and nominal limits | Full signed 64-bit | `int64_t` with asymmetric nominal MINOBJ | Observable OBJ values occupy the full signed 64-bit range; nominal MINOBJ is not the storage boundary. |
| Source overflow | Checked compile failure | Unchecked signed accumulation | The managed row freezes only the pinned executable's exact wrap observations; no portable overflow law is inferred. |
| Truth | Every OBJ false in code; spec says otherwise | Every OBJ false | Every OBJ is false, including nonzero and negative boundary IDs. |
| Conversion | Broad scalar matrix; checked string parsing | Broad scalar matrix; unchecked string overflow | Existing ordinary rows control; the managed boundary row freezes native positive/negative overflow clamping. |
| Relational order | Numeric by ID in code; spec excludes OBJ | Numeric by ID | Same-type OBJ uses signed numeric order; the normative operator passage is stale. |
| Map identity | Equality-consistent typed ID | Comparator can narrow a 64-bit difference to zero | Ordinary equality remains exact, but pinned Toast collapses `#0` and `#4294967296` as map keys. |
| Indexing | OBJ is MAP key; scalar-base/position misuse E_TYPE | Same | OBJ scalar indexed read/write raises E_TYPE. |
| v17 syntax | Tag 1 plus bare signed decimal | Tag 1 plus bare signed decimal | Boundary and invalid references preserve type, ID, literal, and observed map behavior through restart. |

## Existing durable conformance authority

The following existing rows settle observable decisions that must not be
duplicated under new names:

- `basic/value.yaml`: OBJ type, ordinary `tostr`/`toliteral`, `toint`, ordinary
  string/INT `toobj`, and malformed-string-to-`#0` behavior.
- `basic/types.yaml`: OBJ constant, `#0` truth, broader `toobj()` scalar matrix,
  unsupported collection errors, arity, and negative literal formatting.
- `language/equality.yaml`: ordinary exact OBJ equality, cross-type inequality,
  and an ordinary OBJ map key.
- `builtins/map.yaml`: OBJ keys/values, literal ordering, lookup, update, and
  copy-on-write behavior at ordinary IDs.
- `language/index_and_range.yaml`: E_TYPE when OBJ is used as a LIST/STR index;
  it does not cover OBJ as the indexed scalar base.
- `basic/string.yaml`: `encode_binary(OBJ)` raises E_INVARG.
- Object lifecycle, property, verb, and validity rows exercise dereference and
  E_INVIND separately. They do not change the scalar OBJ value contract.

## Frozen observable contract

| Dimension | OBJ contract | Authority |
| --- | --- | --- |
| Representation limits | Signed 64-bit scalar payload, including actual INT64_MIN. Nominal MINOBJ is not the storage boundary. | Barn/Toast owners; `object_authority::object_literal_boundaries_follow_pinned_64_bit_lexer` |
| Construction | `#` plus signed decimal. Positive `2^63` and negative `-2^63` both produce actual INT64_MIN; the two next out-of-range neighbors wrap to nominal MINOBJ and MAXOBJ as observed. No general portable-overflow law is inferred. | Managed boundary row; Toast parser owner |
| Conversion | Existing broad scalar matrix; malformed text becomes `#0`; unsupported collections E_TYPE; `toobj()` string overflow clamps positive to MAXOBJ and negative to actual INT64_MIN. | Existing rows; `object_authority::toobj_string_boundaries_clamp_on_native_overflow` |
| Equality | Same OBJ type and same signed ID only; validity is irrelevant. | Existing equality rows; Barn/Toast owners |
| Ordering | Same-type OBJ compares numerically by signed ID and returns INT truth values. | `object_authority::scalar_equality_order_and_narrowed_map_comparator_are_distinct` |
| Hashing/map identity | Ordinary IDs use typed identity, but pinned Toast's narrowed comparator makes `#0` and `#4294967296` one map key; the later value replaces the former and both lookups return it. | Managed scalar/map row and restart row |
| Truth | Every OBJ is falsy, including nonzero, negative, invalid, and valid references. | Existing `#0` row; managed boundary row |
| Indexing | OBJ is a valid MAP key, not a LIST/STR position or indexed scalar base; misuse E_TYPE and absent key E_RANGE. | Existing position rows; managed scalar-base read/write rows |
| Mutation/copy | Pointer-free scalar; assignment/copy preserves type and signed ID without alias-visible mutation. | Barn/Toast owners; managed scalar row |
| Literal formatting | Canonical `#` plus signed base-10 ID for valid and invalid references. | Existing rows; managed boundary/restart rows |
| Serialization | v4/v17 read tag 1 plus signed decimal; v17 writes tag 1 plus bare signed decimal. Nominal minimum, actual minimum, ordinary invalid refs, comparator-collision IDs, and maximum survive managed restart. | Barn/Toast DB owners; `object_dump_persistence::boundary_and_invalid_object_references_survive_dump_and_restart` |
| Overflow | Only the exact managed literal wrap and conversion clamp observations are contract; no general rule is inferred from unchecked C++ arithmetic. | Managed boundary rows |
| Encoding | OBJ source, display, and persistence use ASCII punctuation/digits; no separate character-encoding decision exists. | Barn/Toast formatting and DB owners |
| Error behavior | E_TYPE for unsupported conversion/indexing; E_RANGE for absent map key; E_INVIND only on invalid dereference. Pinned boundary literals produce the observed OBJ values rather than compile errors. | Existing rows; managed boundary/index rows |

## Durable conformance evidence and oracle result

Conformance commit `276376e` adds exactly two focused OBJ files:

- `language/object_authority.yaml` proves source and `toobj()` boundaries,
  all-OBJ false truth at nonzero/negative edges, exact scalar copy/equality,
  signed relational order, the `#0`/`#4294967296` narrowed map collision, and
  OBJ-scalar indexed read/write E_TYPE.
- `server/object_dump_persistence.yaml` proves nominal minimum, actual signed
  minimum, ordinary invalid references, both collision IDs, and maximum retain
  OBJ type, exact signed ID, canonical literal form, and the observed map
  collision through a managed v17 dump/restart.

The first complete selection exposed one harness-shape error: the multi-line
scalar/map body used `code:` and was therefore wrapped as `return small = #0;`,
returning before its assertions. Changing only that field to `statement:` made
the exact row pass. The complete intended selection then passed against pinned
Toast `aecc51e9449c6e7c95272f0f044b5ba38948459e`:

```text
6 passed, 11530 deselected in 6.28s
```

The run used Banteng's owned stock profile and WSL launcher against a disposable
copy of the bundled `Test.db` fixture.

A new legacy-v4 fixture is not added in this slice. Barn and Toast route v4 OBJ
values through the same signed tag-1 reader used by later versions, and there
is no unresolved OBJ-specific v4 disagreement to justify a new fixture. Full
v4 fixture migration remains part of the ordered persistence phase.

## Gate status

The OBJ semantic contract is frozen. No Java API, record, value helper,
comparator, map adapter, parser representation, or production implementation
is authorized by this record alone. The primitive-type matrix remains blocked
on the other family-specific authority gaps, beginning with ERR.
