# Error value authority

## Scope

This is the Phase 2 mandatory authority record for the public ERR value family
before its remaining managed-oracle rows are added. It covers representation
and enumeration limits, source construction, conversion, equality, ordering,
map-key identity, truth, indexing, mutation/copy behavior, literal formatting,
v4/v17 serialization, encoding, and error behavior.

This record does not choose or approve a Java representation, authorize a value
hierarchy, or permit a production edit. The ERR gate remains open until the
full enumeration, nonzero scalar semantics, map equality, indexed-base errors,
and restart behavior are durable and proven against pinned Toast.

## Verified identities

- Barn normative specification and implementation reference:
  `5a89ba0250a654bf4ae9383aecd4e6f2b0b363a1`, read only through committed Git
  objects. Barn's working tree was not inspected or modified.
- Toast source and executable authority:
  `/root/src/toaststunt` at
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`, executable
  `/root/src/toaststunt/build-release/moo`.
- Existing durable conformance authority: `../moo-conformance-tests` commit
  `276376e` plus its ancestors.
- Managed oracle authority: Banteng's owned
  `profiles/toast/stock-wsl-testdb.json` and `scripts/run_toast_wsl.sh`, using
  the bundled disposable `Test.db` fixture.

## Normative Barn specification

- `spec/types.md:115-145` and `spec/errors.md:5-31` define ERR type tag 3 and
  describe named error constants as fixed ordinal values stored in databases.
  Both passages claim 18 codes and stop at `E_EXEC == 17`; current Barn and
  pinned Toast also define `E_INTRPT == 18`.
- `spec/types.md:215-218` says only enumerated zero/empty values are false and
  everything else is true. Current Barn and pinned Toast instead make every ERR
  false, including nonzero codes.
- `spec/types.md:278` describes `tostr(E_TYPE)` as `"E_TYPE"`, while
  `spec/builtins/types.md:59-70` describes `"Type mismatch"`. Current Barn and
  pinned Toast use the human description for `tostr` and the symbolic name for
  `toliteral`.
- `spec/builtins/types.md` and `spec/types.md` omit or reject ERR inputs for
  conversions that both implementations accept: `tofloat(ERR)` and
  `toobj(ERR)` expose the ordinal payload.
- `spec/types.md:321-332` limits relational ordering to INT, FLOAT, and STR.
  Current Barn and pinned Toast order two ERR values numerically by ordinal.
- `spec/database.md:46-59` assigns persisted tag 3 followed by the raw numeric
  code. It does not define how an out-of-table code should be accepted.
- `spec/builtins/strings.md:264-285` rejects ERR from `encode_binary`, matching
  both implementations and existing conformance.

The spec is accepted only where managed Toast agrees. Its enumeration count,
truth, conversion, `tostr`, and relational-order passages are stale or
incomplete.

## Current Barn implementation path

- `types/typecode.go:7-12` assigns `TYPE_ERR == 3`.
  `types/errorcode.go:4-26` defines unrestricted `ErrorCode int` constants
  `E_NONE == 0` through `E_INTRPT == 18`. `types/value.go:93,121-125` stores the
  scalar payload inline without range validation.
- `parser/parser_error.go:3-23` recognizes only the 19 named source constants;
  `bytecode/parser_literals.go` lowers them through `ErrorFromString` and
  `NewErr`. Unknown names are identifiers and fail normally when unresolved.
- `types/errorcode.go` owns the canonical symbolic name and human message.
  `builtins/types.go` routes ERR `tostr` to the message, `toliteral` to the name,
  and `toint`, `tofloat`, and `toobj` to the numeric ordinal.
- `types/value.go:Equal` compares exact type and payload. `vm/op_compare.go`
  orders same-type ERR by ordinal despite the narrower normative passage.
- `types/map.go` accepts ERR keys and ordinarily hashes/orders them by type and
  ordinal. Unrestricted invalid codes all format as `E_UNKNOWN`, so the Barn
  hash owner can collide for invalid payloads.
- `builtins/types.go:comparePairKeys` declares ERR ordering for `equal()` map
  comparison but does not compare same-type ERR payloads. Maps with multiple
  ERR keys may therefore compare insertion-order-dependently in Barn even
  though their public key/value sets are equal.
- `types/value.go:Truthy` has no ERR case, so every ERR is false.
- `vm/op_index.go` and `vm/collection_helpers.go` accept ERR as a MAP key and
  reject ERR as a LIST/STR position or indexed scalar base with E_TYPE. An
  absent ERR map key raises E_RANGE.
- ERR is an inline immutable scalar. Assignment, stack/local copying, and
  containing-collection copy-on-write preserve the value without an ownership
  or mutation rule.
- `db/format/reader_value.go`, `reader_v4.go`, and `writer.go` read v4/v17 tag 3
  plus an integer without validation and write v17 tag 3 plus the raw ordinal.
- `builtins/crypto.go` rejects ERR binary encoding with E_INVARG. JSON is a
  later builtin-family surface; current Barn's `parse_json("null") -> E_NONE`
  disagreement with its JSON spec is already covered by dedicated conformance.

## Pinned Toast implementation path

- `src/include/structures.h:65-74,99-119,163-177` assigns `TYPE_ERR == 3`,
  stores an `enum error`, and fixes 19 database-visible ordinals from E_NONE to
  E_INTRPT. The source explicitly forbids reordering and permits only appending.
- `src/keywords.gperf:48-66` lists every named literal. The original constants
  are prehistory syntax; E_FLOAT, E_FILE, E_EXEC, and E_INTRPT are enabled at
  their corresponding database language versions.
- `src/parser.y:379-383,1003-1020` constructs only keyword-recognized ERR
  literals with the enum payload.
- `src/unparse.cc:37-177` owns human descriptions, canonical symbolic names,
  and case-insensitive name parsing. Unknown raw codes render as `"Unknown
  Error"` and `"E_?"` rather than becoming named source values.
- `src/list.cc:389-390,458-459` uses the description for `tostr` and the
  symbolic name for `toliteral`.
- `src/numbers.cc:122-189` exposes ERR ordinals through integer and floating
  conversion; `src/objects.cc:255-269` uses the integer conversion owner for
  `toobj`.
- `src/utils.cc:381-399,408-440,444-492` makes every ERR false, compares exact
  enum equality, and orders ERR by ordinal. `src/execute.cc:1295-1370` owns the
  corresponding language operators.
- `src/map.cc` accepts ERR as a scalar key and delegates identity/order to the
  generic comparison owner. Equal maps are compared by their key/value sets,
  so Barn's private insertion-order behavior is not assumed.
- `src/execute.cc:1587-1778` accepts ERR map keys, rejects ERR LIST/STR
  positions, and rejects ERR as an indexed scalar base with E_TYPE.
- `src/include/utils.h:45-68` treats ERR as non-complex: ref/dup copies the
  scalar unchanged and free is a no-op.
- `src/db_io.cc:153-221,346-400` reads and writes tag 3 plus the raw numeric
  payload through the common value path. `src/include/version.h:36-90` owns the
  language-version sequence and current v17 output.
- `src/functions.cc:352-363` and `src/execute.cc:851-896` distinguish an ERR
  value from the control action of raising it. A caught error is an ordinary
  ERR value again.

## Agreements and disagreements

| Surface | Barn | Pinned Toast | Authority decision before final rows |
| --- | --- | --- | --- |
| Public enumeration | 0..18 including E_INTRPT; spec stops at 17 | 0..18 including E_INTRPT | The full ordered table needs one durable row. |
| Truth | Every ERR false; spec says otherwise | Every ERR false | A nonzero managed observation must correct the stale spec rule. |
| Conversion | ERR ordinal accepted by toint/tofloat/toobj | Same | Existing zero-code rows are insufficient for the nonzero disagreement. |
| Equality/order | Exact ordinal equality and numeric order | Same | Existing equality and relational rows control. |
| Map identity/equality | Ordinary keys work; private equal-map comparator is insertion-sensitive | Set-based map equality | Managed reversed-insertion maps must settle the observable result. |
| Indexing | ERR is MAP key; scalar-base/position misuse E_TYPE | Same | Position and scalar-base decisions require family-specific deduplication. |
| v17 persistence | Tag 3 plus raw ordinal | Tag 3 plus raw ordinal | All named codes, especially E_INTRPT, need exact restart proof. |

## Existing durable conformance authority

The following existing rows settle decisions that must not be duplicated:

- `basic/value.yaml` and `basic/types.yaml`: ERR type, `tostr(E_TYPE)` human
  description, `toliteral` symbolic names, ordinary ordinal conversion,
  E_NONE `tofloat`/`toobj`, and E_NONE false truth.
- `language/equality.yaml`: ordinary same-code equality.
- `language/error_comparison.yaml`: same-type ordinal `< <= > >=` across low and
  high codes plus mixed ERR/INT E_TYPE.
- `builtins/map.yaml`: ordinary ERR keys, lookup, deletion, missing-key E_RANGE,
  and literal/value formatting.
- `language/in_operator.yaml` and generic scalar-operator rows: ERR collection
  misuse where already covered.
- `basic/string.yaml`: unsupported binary encoding behavior.
- `language/try_except.yaml`, error traceback, and task rows: the distinction
  between raised control flow and caught ERR values, including E_INTRPT as an
  interruption code.
- Dedicated JSON-null rows already freeze the later builtin-specific E_NONE
  behavior; it is not part of this primitive slice.

## Provisional observable contract

| Dimension | ERR contract before final oracle rows | Authority |
| --- | --- | --- |
| Representation limits | Fixed named table 0..18, append-only by database contract; ordinary source cannot construct an unknown code. | Barn/Toast owners; full-table row pending |
| Construction | Version-gated `E_*` keywords produce ERR. Unknown names are not ERR literals. | Barn/Toast parser owners |
| Conversion | `toint`, `tofloat`, and `toobj` expose the ordinal; human `tostr` and symbolic `toliteral` remain distinct. | Existing rows; nonzero conversion row pending |
| Equality | Same ERR type and ordinal only; a caught code is an ordinary equal ERR value. | Existing equality/exception rows |
| Ordering | Same-type ordinal order; mixed-type relational comparison E_TYPE. | Existing `error_comparison.yaml` |
| Hashing/map identity | Named ERR values are distinct scalar keys ordered by ordinal; equal maps must be insertion-order-independent. | Existing map rows; reversed-order equality pending |
| Truth | Every ERR is false, including nonzero E_TYPE and E_INTRPT. | Barn/Toast owners; nonzero row pending |
| Indexing | ERR is a valid MAP key, not a LIST/STR position or indexed scalar base; misuse E_TYPE and absent key E_RANGE. | Existing map rows; scalar-base row pending |
| Mutation/copy | Inline scalar; assignment/copy preserves type and ordinal without alias-visible mutation. | Barn/Toast owners; scalar row pending |
| Literal formatting | `tostr` is the human description; `toliteral` is the canonical `E_*` name for all named codes. | Existing rows; full-table row pending |
| Serialization | v4/v17 read tag 3 plus numeric code; v17 writes tag 3 plus raw ordinal. All 19 named values require restart proof. | Barn/Toast DB owners; restart row pending |
| Overflow | No language arithmetic constructs ERR codes. Unknown raw database codes are malformed-input policy, not ordinary source overflow. | Barn/Toast construction owners |
| Encoding | Source/display/persistence names and ordinals are ASCII; `encode_binary(ERR)` raises E_INVARG. | Existing encoding row; Barn/Toast owners |
| Error behavior | ERR values do not raise merely by existing; unsupported indexing/conversion surfaces use their focused E_TYPE/E_INVARG/E_RANGE rules. | Existing exception and operator rows; index row pending |

## Unresolved managed-oracle questions

Exactly three decision groups remain after deduplication:

1. Does the complete named table remain exactly E_NONE..E_INTRPT == 0..18,
   with canonical literal names, nonzero false truth, and nonzero ordinal
   `tofloat`/`toobj` conversions on the pinned executable?
2. Do reversed-insertion maps with the same ERR key/value pairs compare equal,
   and do ERR-scalar indexed read and indexed assignment raise E_TYPE?
3. Do all 19 named ERR values preserve tag, ordinal, canonical literal, and
   false truth through a managed v17 dump/restart?

The smallest durable additions are one language authority file covering the
first two groups and one managed restart row covering the third. Both files
must pass through Banteng's owned stock profile and WSL launcher against pinned
Toast.

No malformed raw-code fixture or new v4 fixture is added in this primitive
slice. Both source owners use the same unvalidated tag-3 reader across v4 and
v17, and no ordinary language path constructs an unknown ERR code. Malformed
database acceptance and full v4 fixture migration remain in the ordered
persistence phase.

## Gate status

Steps 1 through 4 of the mandatory authority gate are complete for ERR. Step 5
is open only for the language authority and v17 restart rows above. No Java API,
record, enum, value helper, comparator, parser representation, or production
implementation is authorized until those rows pass and this record is updated
with their durable conformance commit and managed result.
