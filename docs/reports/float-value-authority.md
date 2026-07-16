# Float value authority

## Scope

This record completes the Phase 2 mandatory authority gate for the public FLOAT
value family. It covers representation limits, construction, conversion,
equality, ordering, map-key identity, truth, indexing, mutation/copy behavior,
literal formatting, v17 serialization, overflow, underflow, encoding, and
error behavior.

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
  commit `a0b7bbc` plus its ancestors. The earlier focused FLOAT edge rows
  landed in ancestor commit `81a27ce`.
- Managed oracle authority: Banteng's owned
  `profiles/toast/stock-wsl-testdb.json` and `scripts/run_toast_wsl.sh`, using
  the bundled disposable `Test.db` fixture.
- Selected stock profile has `option.PROMOTE_NUMBERS: false`; promoted-number
  behavior remains a separately selected profile rather than an implicit FLOAT
  rule.

## Normative Barn specification

- `spec/types.md:45-65` assigns FLOAT type tag 9 and IEEE-754 binary64
  precision. It says NaN and infinity cannot exist in MOO, division by zero
  raises E_DIV, and any operation producing a non-finite result raises E_FLOAT.
- `spec/types.md:194-214` makes zero FLOAT falsy and nonzero FLOAT truthy.
- `spec/types.md:241-252,291-317` specifies strict numeric types: ordinary
  FLOAT arithmetic stays FLOAT, mixed INT/FLOAT arithmetic raises E_TYPE, and
  INT/FLOAT equality is false.
- `spec/operators.md:443-570,626-739` specifies exact same-type equality,
  numeric ordering, arithmetic, division, and power. Its description of FLOAT
  equality as bitwise conflicts with both Barn's current implementation and
  pinned Toast for signed zero.
- `spec/grammar.md:317-333` describes FLOAT literals, but its decimal-point
  requirement conflicts with `spec/types.md` exponent-only examples and with
  pinned Toast's accepted `1e2` form.
- `spec/database.md:52-74,90-109` assigns v17 type tag 9 and textual binary64
  payloads written with `DBL_DIG + 4` significant digits.
- `spec/builtins/types.md` owns conversion contracts. Its accepted input and
  failure descriptions do not consistently match current Barn or pinned Toast,
  so focused managed rows control observable conversion behavior.

The spec is accepted only where the managed Toast authority agrees. Its
bitwise-equality language, literal grammar restriction, and claim that
non-finite values cannot exist in any internal read path are not complete
authority.

## Current Barn implementation path

- `types/value.go:20-44,83-134` stores `math.Float64bits` verbatim in the scalar
  value payload and reconstructs `float64` exactly. `NewFloat` accepts every
  binary64 bit pattern, including NaN and infinities, while `IsNaN` and `IsInf`
  expose those states. This conflicts with the spec's absolute prohibition but
  does not make those states constructible through ordinary MOO source.
- `parser/ast.go` owns the `float64` literal payload. `parser/lexer.go` and
  `parser/parser.go` own source tokenization and conversion before bytecode
  literal construction.
- `types/value.go:136-235` makes both signed zeroes falsy, formats FLOAT with 15
  significant digits plus `.0` where required, and implements ordinary IEEE
  numeric equality. NaN is unequal to every value; `+0.0` equals `-0.0`.
- `types/map.go:16-66` hashes FLOAT keys through the 15-significant-digit
  display string. That implementation is observably wrong: equal signed zeroes
  can hash differently, while distinct adjacent doubles can format identically
  and collide. `goMap.get` trusts the hash without rechecking equality.
- `types/map.go:172-220` orders FLOAT keys with ordinary `<` and `>`. Its raw
  NaN path would compare equal to every FLOAT even though value equality says
  NaN is unequal.
- `vm/op_arith.go` and `vm/operators.go` own numeric arithmetic, division,
  modulo, power, unary minus, numeric promotion options, and finite-result
  checks. Ordinary finite results remain FLOAT; NaN or infinity results become
  E_FLOAT; a zero divisor becomes E_DIV.
- `vm/op_compare.go` and `vm/operators.go` own strict equality and ordering.
  Stock behavior uses numeric FLOAT comparison and does not promote INT/FLOAT.
- `builtins/types.go:90-256` owns conversion. FLOAT identity, INT/OBJ/ERR to
  FLOAT, string parsing, FLOAT to INT/OBJ truncation, and unsupported input
  errors are explicit cases, but some differ from the written builtin spec.
- FLOAT is an inline scalar copied by value and has no mutable payload.
  `spec/tasks.md`'s primitive-copy rule agrees with that path.
- `db/format/reader_value.go:80-96` reads tag 9 through `strconv.ParseFloat` and
  `types.NewFloat`. `db/format/writer.go:142-147,210-220` writes tag 9 and
  `%.19g`. Neither owner rejects a raw non-finite database value even though
  ordinary language construction rejects it.
- `types/value_struct_test.go` explicitly exercises `math.MaxFloat64` and
  `math.SmallestNonzeroFloat64`, confirming that Barn's value layer intends to
  preserve finite maximum and subnormal payloads.

Barn agrees with pinned Toast on the ordinary binary64 payload, numeric
equality, signed-zero truth, scalar copy behavior, display precision, and v17
precision. Its string-derived FLOAT map key and unguarded raw special-value
construction are not copied into the observable contract.

## Pinned Toast implementation path

- `src/include/structures.h:99-119,163-177,212-219` assigns FLOAT tag 9 and
  stores one raw C++ `double` payload. Finite normals, subnormals, and signed
  zero are representable.
- `src/include/my-math.h:24` defines `IS_REAL(x)` as the inclusive
  `-DBL_MAX <= x <= DBL_MAX` test. Finite subnormals and zero pass; NaN and
  infinities fail.
- `src/parser.y:934-1000` accepts decimal-point and exponent forms including
  `.5`, `1.`, `1e2`, and signed exponents. It converts through `strtod` and
  rejects only values failing `IS_REAL`. A finite subnormal and a value
  underflowed by `strtod` to zero therefore remain accepted FLOAT values.
- `src/parser.y:358-368,517-583` constructs FLOAT expressions and
  `src/code_gen.cc:652-714` emits their arithmetic and comparison bytecode.
- `src/numbers.cc:97-120,160-190` owns string and scalar conversion. `tofloat`
  accepts INT, OBJ, ERR, FLOAT, and valid finite strings; BOOL and collection
  families raise E_TYPE; malformed or non-finite strings raise E_INVARG.
- `src/numbers.cc:194-280` owns strict equality, ordering, addition,
  subtraction, and multiplication. Differing numeric tags produce E_TYPE.
  Finite results, including subnormal results and underflow to zero, remain
  FLOAT; non-finite results produce E_FLOAT.
- `src/numbers.cc:282-344` owns modulo and division. A zero FLOAT divisor raises
  E_DIV. Finite results remain FLOAT, including subnormals and signed zero;
  non-finite division results become E_FLOAT.
- `src/numbers.cc:346-410` owns power. Domain or range results failing
  `IS_REAL` become E_FLOAT.
- `src/utils.cc:380-470` owns truth, general comparison, and equality. Both
  signed zeroes are equal and falsy; adjacent finite doubles remain distinct;
  ordinary ordering is numeric.
- `src/map.cc:84-88,223-247,330-348,677-740` uses that same numeric comparator
  for map identity. Signed zeroes name one key while adjacent and boundary
  finite values remain separate when numerically distinct.
- `src/execute.cc:1587-1778` owns scalar/index and assignment dispatch. FLOAT is
  a valid MAP key but not an indexed collection; unsupported FLOAT indexed
  read/write follows the existing E_TYPE path.
- `src/include/structures.h:191-210` and `src/include/utils.h:45-68` copy a
  non-complex FLOAT scalar by value and perform no payload mutation.
- `src/list.cc:376-410,448-467,1161-1209` uses `DBL_DIG`/15-significant-digit
  display formatting and appends `.0` when needed. Signed negative zero
  canonicalizes to `0.0` in the observed literal form.
- `src/db_io.cc:91-104,153-195,313-324,350-378` reads and writes tag 9 with
  `DBL_DIG + 4`, or 19 significant digits. This persistence form is
  intentionally separate from the lossy 15-digit display form.

Toast's raw database reader can physically ingest a non-finite payload because
it does not apply `IS_REAL` after `strtod`. That malformed-input policy belongs
to the later persistence-input slice; it does not authorize ordinary source,
conversion, or arithmetic construction of NaN or infinity.

## Agreements and disagreements

| Surface | Barn | Pinned Toast | Authority decision |
| --- | --- | --- | --- |
| Finite representation | Raw IEEE-754 binary64 | Raw C++ `double` | All finite binary64 classes, including signed zero and subnormals, are representation candidates; observable behavior still requires rows. |
| NaN/infinity | Raw constructor and DB reader admit them despite spec | Raw DB reader can admit them; ordinary source/conversion/arithmetic rejects them | No ordinary MOO value contract includes non-finite values. Malformed DB input is deferred to persistence policy. |
| Literal grammar | Written grammar excludes some exponent-only forms | Accepts `.5`, `1.`, `1e2`, `1E+2` | Existing Toast-proven literal row controls. |
| Equality | Numeric in code; spec says bitwise | Numeric | Numeric equality controls; signed zeroes are equal. |
| Map identity | Broken 15-digit string hash | Numeric comparator | Toast-proven numeric identity controls; do not reproduce Barn's hash. |
| Display | 15 significant digits | 15 significant digits | Existing exact formatting rows control. Display form is not a persistence codec. |
| v17 persistence | Tag 9 plus 19 digits | Tag 9 plus 19 digits | Adjacent doubles are proven; extreme finite/subnormal boundary remains open. |
| Underflow | Finite result accepted by arithmetic checks | Finite result accepted by `IS_REAL` | The managed normal-to-subnormal-to-zero row controls. |

## Existing durable conformance authority

The following existing rows already settle observable decisions that must not
be duplicated under new names:

- `basic/value.yaml` and `basic/types.yaml`: type/constants, ordinary
  construction, truth, conversions and their errors, string/literal forms,
  strict mixed arithmetic and ordering, and ordinary power.
- `basic/arithmetic.yaml` and `builtins/math.yaml`: ordinary arithmetic,
  division/modulo behavior, domain errors, and non-finite math results.
- `language/float_literals.yaml`: `.5`, `1.`, `1e2`, and `1E+2` literal forms.
- `language/float_formatting.yaml`: exact 15-significant-digit fixed/scientific
  formatting and equality of `tostr`/`toliteral` display contracts.
- `language/equality.yaml`:
  `signed_zero_and_adjacent_float_map_identity` proves numeric signed-zero
  equality/order/truth/literal behavior, one signed-zero map key, and distinct
  adjacent finite keys.
- `language/float_overflow.yaml`: addition, subtraction, and multiplication
  overflow raise E_FLOAT.
- `builtins/tofloat_inf_nan.yaml`: string spellings of infinity and NaN, plus
  overflowing magnitude, are rejected; an ordinary finite string succeeds.
- `language/index_and_range.yaml` and `language/in_operator.yaml`: FLOAT scalar
  indexing/range/assignment/membership errors.
- `server/dump_persistence.yaml`:
  `adjacent_floats_survive_dump_and_restart` proves 19-digit persistence keeps
  adjacent finite values and their map-key identity distinct.

The focused managed selection covering the prior FLOAT authority passed 48
rows against pinned Toast; its three added edge rows landed in conformance
commit `81a27ce`.

## Frozen observable contract

| Dimension | FLOAT contract | Authority |
| --- | --- | --- |
| Representation limits | Public ordinary values are finite IEEE-754 binary64, including normals, signed zero, and subnormals. NaN/infinity are rejected by ordinary construction paths. | Barn/Toast source; existing special-value rows; `float_boundary_authority::finite_subnormal_results_and_underflow_remain_float` |
| Construction | Accepted Toast literal grammar and finite results are FLOAT tag 9; non-finite literal results are rejected. | `float_literals.yaml`; Toast parser |
| Conversion | FLOAT identity; supported numeric/tag inputs convert numerically; finite strings parse; malformed/non-finite strings raise E_INVARG; unsupported families raise E_TYPE. | Existing types and `tofloat_inf_nan.yaml` rows |
| Equality | Same-type numeric binary64 equality; signed zeroes equal; adjacent finite values distinct; INT/FLOAT unequal in stock profile. | Existing equality rows |
| Ordering | Same-type numeric ordering; signed zeroes compare equal; mixed INT/FLOAT ordering raises E_TYPE in stock profile. | Existing types/equality rows |
| Hashing/map identity | Observable key identity follows numeric equality, not bit pattern or 15-digit display. | Existing signed-zero/adjacent row; Toast map owner |
| Truth | Both zero signs are falsy; every nonzero finite FLOAT, including a nonzero subnormal, is truthy. | Existing zero rows; `float_boundary_authority::finite_subnormal_results_and_underflow_remain_float` |
| Indexing | FLOAT is a valid MAP key but not an indexed base or LIST/STR position; unsupported indexed/range read/write raises E_TYPE. | Existing index rows; Toast execution owner |
| Mutation/copy | FLOAT is a scalar copied by value and has no mutable payload or alias-visible mutation. | Barn/Toast scalar owners |
| Literal formatting | Canonical 15-significant-digit form, `.0` when required, signed zero canonicalized to `0.0`. | Existing formatting/equality rows |
| Serialization | v17 tag 9 plus 19-significant-digit text; adjacent, maximum finite, minimum normal, and smallest subnormal values preserve numeric and map-key identity through restart. | Barn/Toast DB owners; existing adjacent row; `float_boundary_dump_persistence::maximum_normal_and_subnormal_values_survive_dump_and_restart` |
| Overflow/underflow | Non-finite arithmetic raises E_FLOAT; zero divisor raises E_DIV. Minimum normal can produce a nonzero subnormal; the smallest subnormal and an underflowing literal become FLOAT zero without error. | Existing overflow/math rows; `float_boundary_authority::finite_subnormal_results_and_underflow_remain_float` |
| Encoding | Display and v17 use ASCII decimal/exponent characters. Display uses 15 digits; persistence uses 19. | Formatting and DB owners |
| Error behavior | E_TYPE for mixed/unsupported surfaces, E_DIV for zero division/modulo, E_FLOAT for non-finite arithmetic, E_INVARG for malformed/non-finite string conversion. | Existing focused rows; Barn/Toast source |

## Durable conformance evidence and oracle result

Conformance commit `a0b7bbc` adds exactly two focused rows:

- `language/float_boundary_authority.yaml` proves that minimum normal divided
  by two is a nonzero, truthy, ordered subnormal; the smallest subnormal is
  positive and distinct; and arithmetic plus literal underflow produce FLOAT
  zero with canonical literal `0.0` rather than E_FLOAT.
- `server/float_boundary_dump_persistence.yaml` proves that maximum finite,
  minimum normal, and smallest subnormal values preserve exact equality,
  canonical 15-digit display forms, and three distinct numeric map keys through
  a managed v17 dump/restart.

Both initial expected results passed without correction against pinned Toast
`aecc51e9449c6e7c95272f0f044b5ba38948459e`:

```text
2 passed, 11526 deselected in 6.19s
```

The run used Banteng's owned stock profile and WSL launcher against a disposable
copy of the bundled `Test.db` fixture.

No ordinary row is added for raw non-finite database ingestion. That requires
an externally malformed fixture and belongs to the later persistence-input
acceptance policy, not this ordinary value-construction contract.

## Gate status

The FLOAT semantic contract is frozen. No Java API, record, sealed family,
hashing strategy, numeric helper, parser representation, or production
implementation is authorized by this record alone. The primitive-type matrix
remains blocked on the other family-specific authority gaps, beginning with
STR.
