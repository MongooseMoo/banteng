# Object-reference value authority

## Scope

This is the Phase 2 mandatory authority record for the public OBJ value family
before its remaining managed-oracle rows are added. It covers representation
limits, source construction, conversion, equality, ordering, map-key identity,
truth, indexing, mutation/copy behavior, literal formatting, v4/v17
serialization, overflow, encoding, and error behavior.

This record does not choose or approve a Java representation, authorize a value
hierarchy, or permit a production edit. The OBJ gate remains open until the
boundary, comparison/map-identity, scalar-indexing, and restart decisions are
durable and proven against pinned Toast.

## Verified identities

- Barn normative specification and implementation reference:
  `5a89ba0250a654bf4ae9383aecd4e6f2b0b363a1`, read only through committed Git
  objects. Barn's working tree was not inspected or modified.
- Toast source and executable authority:
  `/root/src/toaststunt` at
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`, executable
  `/root/src/toaststunt/build-release/moo`.
- Existing durable conformance authority: `../moo-conformance-tests` commit
  `cd27e2e` plus its ancestors.
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
| Width and nominal limits | Full signed 64-bit | `int64_t` with asymmetric nominal MINOBJ | Storage is signed 64-bit; source and conversion boundary behavior remains oracle-owned. |
| Source overflow | Checked compile failure | Unchecked signed accumulation | Pinned executable must settle exact boundary and neighbor results without generalizing portable overflow semantics. |
| Truth | Every OBJ false in code; spec says otherwise | Every OBJ false | Implementation agreement controls; managed row already proves `#0` and the boundary row must prove nonzero/negative cases. |
| Conversion | Broad scalar matrix; checked string parsing | Broad scalar matrix; unchecked string overflow | Existing ordinary rows control; boundary strings remain unresolved. |
| Relational order | Numeric by ID in code; spec excludes OBJ | Numeric by ID | Managed row must correct the stale spec surface. |
| Map identity | Equality-consistent typed ID | Comparator can narrow a 64-bit difference to zero | Managed row must freeze the observable collision separately from ordinary equality. |
| Indexing | OBJ is MAP key; scalar-base/position misuse E_TYPE | Same | One focused scalar-base read/write row is the missing family-specific proof. |
| v17 syntax | Tag 1 plus bare signed decimal | Tag 1 plus bare signed decimal | Managed restart must prove exact type/value/literal preservation at representable edges. |

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

## Provisional observable contract

| Dimension | OBJ contract before final oracle rows | Authority |
| --- | --- | --- |
| Representation limits | Signed 64-bit scalar payload. Nominal MINOBJ is not assumed to be the storage boundary. | Barn/Toast owners; boundary row pending |
| Construction | `#` plus signed decimal. Exact out-of-range behavior belongs only to the pinned executable profile. | Parser owners; boundary row pending |
| Conversion | Existing broad scalar matrix; malformed text becomes `#0`; unsupported collections E_TYPE; exact overflow result pending. | Existing rows; conversion-boundary row pending |
| Equality | Same OBJ type and same signed ID only; validity is irrelevant. | Existing equality rows; Barn/Toast owners |
| Ordering | Same-type OBJ compares numerically by signed ID and returns INT truth values. | Barn/Toast implementations; focused row pending because spec disagrees |
| Hashing/map identity | Ordinary keys use typed ID identity. Pinned Toast's large-difference narrowing collision is observable but not yet frozen. | Existing map rows; collision row pending |
| Truth | Every OBJ is falsy, including nonzero, negative, invalid, and valid references. | Barn/Toast owners; existing `#0` row; boundary row pending |
| Indexing | OBJ is a valid MAP key, not a LIST/STR position or indexed scalar base; misuse E_TYPE and absent key E_RANGE. | Existing position rows; scalar-base row pending |
| Mutation/copy | Pointer-free scalar; assignment/copy preserves type and signed ID without alias-visible mutation. | Barn/Toast owners; focused scalar row pending |
| Literal formatting | Canonical `#` plus signed base-10 ID for valid and invalid references. | Existing rows; boundary/restart rows pending |
| Serialization | v4/v17 read tag 1 plus signed decimal; v17 writes tag 1 plus bare signed decimal. | Barn/Toast DB owners; managed v17 restart pending |
| Overflow | No general law is inferred from Toast's unchecked C++ parser or conversion paths. Only focused pinned-executable observations become contract. | Boundary rows pending |
| Encoding | OBJ source, display, and persistence use ASCII punctuation/digits; no separate character-encoding decision exists. | Barn/Toast formatting and DB owners |
| Error behavior | Compile failure or observed profile result at source overflow; E_TYPE for unsupported conversion/indexing; E_RANGE for absent map key; E_INVIND only on invalid dereference. | Existing rows and owners; boundary/index rows pending |

## Unresolved managed-oracle questions

Exactly four decision groups remain after deduplication:

1. What do the nominal minimum, actual signed minimum, maximum, and their
   out-of-range source neighbors do on the pinned executable, and how do the
   same boundary strings behave through `toobj()`?
2. Do ordinary equality and signed relational order remain distinct from the
   map-key comparator for `#0` and `#4294967296`, and what exact map length and
   lookup results expose the comparator narrowing?
3. Do indexed read and indexed assignment on an OBJ scalar raise E_TYPE?
4. Do nominal minimum, actual signed minimum if constructible, zero, maximum,
   and invalid-but-representable OBJ values preserve exact type, equality,
   literal form, and map behavior through managed v17 dump/restart?

The smallest durable additions are one language authority file covering the
first three groups and one managed restart row covering the fourth. The
language row must distinguish compile outcomes from runtime values instead of
assuming unchecked C++ overflow semantics. Both files must pass through
Banteng's owned stock profile and WSL launcher against pinned Toast.

A new legacy-v4 fixture is not added in this slice. Barn and Toast route v4 OBJ
values through the same signed tag-1 reader used by later versions, and there
is no unresolved OBJ-specific v4 disagreement to justify a new fixture. Full
v4 fixture migration remains part of the ordered persistence phase.

## Gate status

Steps 1 through 4 of the mandatory authority gate are complete for OBJ. Step 5
is open only for the language authority and v17 restart rows above. No Java API,
record, value helper, comparator, map adapter, parser representation, or
production implementation is authorized until those rows pass and this record
is updated with their durable conformance commit and managed result.
