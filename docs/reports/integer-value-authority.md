# Integer value authority

## Scope

This is the Phase 2 mandatory authority record for the public INT value family.
It records the evidence available before the remaining managed-oracle row is
added. It covers representation limits, construction, conversion, equality,
ordering, map-key identity, truth, indexing, mutation/copy behavior, literal
formatting, v17 serialization, overflow, encoding, and error behavior.

This record does not choose or approve a Java representation, authorize a value
hierarchy, or permit a production edit. The INT gate remains open until the
unresolved persistence row is durable and proven against the pinned Toast
oracle.

## Verified identities

- Barn normative specification and implementation reference:
  `5a89ba0250a654bf4ae9383aecd4e6f2b0b363a1`, read only through committed Git
  objects. Barn's working tree was not inspected or modified.
- Toast source and executable authority:
  `/root/src/toaststunt` at
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`, executable
  `/root/src/toaststunt/build-release/moo`.
- Existing durable conformance authority: `../moo-conformance-tests` at commit
  `39ec3d8` plus its ancestors.
- Managed oracle authority: Banteng's owned
  `profiles/toast/stock-wsl-testdb.json` and `scripts/run_toast_wsl.sh`, using
  the bundled disposable `Test.db` fixture.

## Normative Barn specification

- `spec/types.md:29-43` defines INT as signed 64-bit with the full
  `-9223372036854775808..9223372036854775807` range. It says overflow for
  integer addition, subtraction, and multiplication is undefined and may wrap,
  saturate, or behave unpredictably.
- `spec/operators.md:649-674` specifies signed integer division truncated
  toward zero and E_DIV for zero. It does not specify the minimum-value divided
  by negative one edge.
- `spec/operators.md:676-697` specifies C-style remainder with the dividend's
  sign. That disagrees with both current Barn and pinned Toast, which adjust a
  nonzero remainder to the divisor's sign.
- `spec/operators.md:702-739` says a negative INT exponent raises E_TYPE. That
  disagrees with both current Barn and pinned stock Toast for the existing
  strict-profile rows.
- `spec/database.md:52-74` assigns persisted type tag 0 followed by a signed
  integer line.

The spec is accepted only where the managed Toast authority agrees. Its modulo
sign and negative-exponent passages are stale, and it does not define source
literal overflow or the division overflow edge.

## Current Barn implementation path

- `types/value.go:20-42,80-107` stores INT payload bits in a `uint64`, constructs
  from `int64`, and recovers the signed value without changing the bits.
- `parser/lexer.go:160-163,337-386` tokenizes minus separately.
  `parser/parser.go:566-598` parses a non-negative decimal magnitude and reduces
  overflow modulo 2^64 before `bytecode/parser_literals.go:9-13` constructs the
  runtime value. Thus source `9223372036854775808` constructs the actual signed
  64-bit minimum.
- `vm/op_arith.go:39-48,159-170,197-208,438-451` uses Go `int64` operations for
  addition, subtraction, multiplication, and unary minus. The implementation
  therefore wraps two's-complement even where the spec deliberately leaves
  overflow undefined.
- `vm/op_arith.go:235-256` raises E_DIV for zero and explicitly returns the
  actual signed minimum for `INT_MIN / -1`.
- `vm/op_arith.go:298-358` raises E_DIV for zero and adjusts a nonzero remainder
  to the divisor's sign. `INT_MIN % -1` is zero.
- `vm/op_arith.go:360-435` implements negative integer exponents rather than
  raising the error stated by the spec, and uses unchecked wrapping
  multiplication for nonnegative integer exponentiation.
- `builtins/types.go:90-256` owns numeric conversions. INT converts to itself;
  finite FLOAT truncates; OBJ, ANON, ERR, and BOOL expose their numeric payload;
  strings accept surrounding whitespace and decimal or float syntax; pure
  decimal string overflow clamps; failed conversion becomes zero; unsupported
  collections raise E_TYPE.
- `types/value.go:136-166,217-225` makes only integer zero falsy, formats signed
  base-10 decimal, and compares the complete INT payload.
- `vm/op_compare.go:1-63,230-269` returns classic INT zero/one comparison
  results, orders INTs by signed value, and separately owns BOOL/INT equality.
- `types/map.go:16-66,172-236` keeps INT keys type-distinct, orders them by
  signed value, and performs persistent copy-on-write updates.
- `vm/op_index.go:11-51` accepts INT as a LIST/STR index and MAP key, enforces
  one-based collection indexing, raises E_RANGE for invalid positions or
  missing keys, and raises E_TYPE when INT is used as an indexed base.
- `db/format/reader_value.go:12-25` and
  `db/format/writer.go:12-30,168-182,244-253` read and write v17 tag 0 plus the
  full signed decimal value, including values nested in task state.

Barn agrees with the intended public width, scalar copy behavior, formatting,
ordinary comparisons, truth, indexing errors, and v17 syntax. Its deterministic
overflow behavior and actual-minimum division special case are implementation
choices that do not match pinned Toast's source at every edge.

## Pinned Toast implementation path

- `src/include/options.h:486-491` leaves the default 64-bit mode enabled.
  `src/include/structures.h:29-50` defines `Num` as `int64_t`, MAXINT as
  `9223372036854775807`, and the asymmetric nominal MININT as
  `-9223372036854775807`. The storage type can still hold the actual signed
  minimum constructed by the observed overflowing-literal path.
- `src/parser.y:359-364,597-614,936-993` parses a positive decimal token and
  applies unary minus separately. Magnitudes above MAXINT use unchecked signed
  accumulation, so the C++ source does not define portable overflow behavior.
- `src/numbers.cc:251-283` uses unchecked signed C++ addition, subtraction, and
  multiplication. Overflow is undefined behavior in the implementation
  language and is not a stable source contract.
- `src/execute.cc:1563-1584` directly negates INT. Negating nominal MININT is
  deterministic and yields MAXINT; negating the actual signed minimum is
  undefined in C++.
- `src/numbers.cc:286-327` raises E_DIV for a zero divisor. It explicitly
  returns nominal MININT for `-9223372036854775807 / -1`, even though MAXINT is
  mathematically representable, and yields zero for nominal MININT modulo
  negative one. The actual signed minimum bypasses that special case and enters
  undefined native division/remainder behavior.
- `src/numbers.cc:330-376` defines negative integer-exponent behavior but uses
  unchecked multiplication for positive exponents.
- `src/numbers.cc:55-76,126-164,390-432` owns conversions. INT is unchanged;
  OBJ, ERR, and BOOL expose numeric payloads; finite representable FLOAT values
  truncate toward zero; strings accept surrounding whitespace and decimal or
  float syntax; failed string conversion becomes zero; LIST, MAP, ANON, and
  WAIF raise E_TYPE; non-real FLOAT values raise E_FLOAT. Native out-of-range
  casts and unchecked conversion errno are not portable source contracts.
- `src/utils.cc:381-400,444-492` owns truth and equality. Only zero is falsy,
  ordinary INT equality is exact, and BOOL equals only INT zero or one as
  already frozen by BOOL authority.
- `src/numbers.cc:216-246` and `src/execute.cc:1295-1379` own safe signed
  relational comparison and classic INT zero/one results.
- `src/utils.cc:408-440` and `src/map.cc:85-87,228-242` use numeric INT map-key
  identity, but the extreme-key comparator subtracts signed values and can
  overflow. The stable contract does not require reproducing that unsafe
  internal mechanism.
- `src/execute.cc:1587-1665` makes INT a valid LIST/STR index and MAP key, with
  E_RANGE for invalid one-based positions or absent keys and E_TYPE when INT is
  the indexed base.
- `src/include/structures.h:100-122,191-210` and
  `src/include/utils.h:45-68` treat INT as a non-complex scalar copied by value.
- `src/list.cc:376-413,448-454,1161-1209` renders plain signed base-10 decimal
  for both string and literal forms.
- `src/include/version.h:36-90` and
  `src/db_io.cc:76-89,153-183,307-311,346-375` assign v17 tag 0 and read/write a
  signed decimal line. The source path supports all `int64_t` payload bits, but
  the full-range managed restart result is not yet a durable row.
- `src/json.cc:213-245` decodes a JSON integer token as INT only when native
  conversion succeeds within Toast's nominal MININT/MAXINT bounds; otherwise it
  falls through to FLOAT when possible. JSON is a later builtin-family surface,
  not a reason to expand this value slice.

## Agreements and disagreements

| Surface | Barn | Pinned Toast | Authority decision |
| --- | --- | --- | --- |
| Public storage width | Full signed 64-bit | `int64_t`, but asymmetric nominal MININT macro | Full signed 64-bit values are observable through existing literal/conversion rows; do not infer the nominal macro as a storage limit. |
| Source literal overflow | Explicit modulo 2^64 | Unchecked C++ signed accumulation | Existing managed rows freeze the pinned executable's observed modulo results; source portability is not claimed. |
| Runtime `+ - *` overflow | Deterministic Go wrap | C++ undefined behavior | Only already-proven managed observations are contract. Do not add general overflow laws. |
| Unary actual-minimum negation | Deterministic wrap | C++ undefined behavior | Existing literal row observes the pinned executable result; do not generalize it. |
| Nominal MININT divided by -1 | Barn special case is for actual INT_MIN | Toast explicitly returns nominal MININT | Existing focused Toast row controls. |
| Modulo sign | Divisor sign | Divisor sign | Existing four-sign matrix controls; Barn spec is stale. |
| Negative INT exponent | Implemented | Implemented | Existing stock-profile rows control; Barn spec is stale. |
| INT map key | Typed numeric identity | Typed numeric identity, unsafe extreme comparator | Preserve observable typed identity and ordering results, not subtraction-based comparison. |
| v17 syntax | Tag 0 plus signed decimal | Tag 0 plus signed decimal | Source agrees; full-range managed restart remains to be proven. |

## Existing durable conformance authority

The following existing rows already settle the observable decisions that a new
INT authority file would otherwise duplicate:

- `basic/value.yaml`: type, string form, literal form, and ordinary conversion.
- `basic/types.yaml`: INT/NUM constants, truth, conversions and errors, signed
  formatting, mixed-type ordering errors, and integer power behavior.
- `basic/arithmetic.yaml` and `builtins/math.yaml`: ordinary arithmetic,
  division and modulo by zero, signed division, the four modulo sign cases, and
  the pinned 64-bit nominal-MININT division/modulo quirks.
- `language/integer_literal_overflow.yaml`: actual-minimum construction,
  `INT_MAX + 1` as observed on the pinned executable, and several modulo-2^64
  literal cases.
- `builtins/toint_overflow.yaml`: exact boundaries and observed native string
  overflow clamping.
- `language/index_and_range.yaml`: INT indexed-base E_TYPE behavior.
- `language/boolean_authority.yaml`: BOOL/INT equality and distinct map-key
  identity where those surfaces intersect.
- `language/promote_numbers.yaml`: INT/FLOAT map-key distinction in the
  explicitly separate promoted-number profile.

Because those rows are already durable, adding renamed copies would not change
an authority decision and is not authorized.

## Provisional observable contract

| Dimension | INT contract before final oracle row | Authority |
| --- | --- | --- |
| Representation limits | Observable values occupy the full signed 64-bit range. Toast's nominal MININT macro is not the storage boundary. | Barn/Toast source plus literal and conversion boundary rows |
| Construction | Decimal source tokens exhibit the pinned executable's modulo-2^64 behavior, including actual INT64_MIN. This is an observed profile contract, not portable C++ semantics. | `integer_literal_overflow.yaml` |
| Conversion | INT identity; representable finite FLOAT truncation; numeric OBJ/ERR/BOOL payload conversion; supported string syntax and observed overflow clamping; unsupported collections E_TYPE; non-real FLOAT E_FLOAT. | Existing `types.yaml` and `toint_overflow.yaml` rows; Toast source |
| Equality | Exact signed numeric equality within INT; BOOL equals only INT 0/1; comparison results are INT 0/1. | Existing types and BOOL rows |
| Ordering | Safe signed numeric relational ordering. Cross-type ordering follows its focused profile rows. | Existing types rows; Toast source |
| Hashing/map identity | INT keys use typed numeric identity. Ordinary repeated numeric keys replace/lookup the same entry. No subtraction-overflow algorithm is part of the contract. | Barn/Toast source; existing map rows |
| Truth | Zero is falsy; every nonzero INT is truthy. | Existing types/control-flow rows |
| Indexing | INT is a one-based LIST/STR index and valid MAP key; invalid positions or missing keys raise E_RANGE; using INT as indexed base raises E_TYPE. | Existing indexing rows; Barn/Toast source |
| Mutation/copy | INT is scalar and has no mutable payload; assignment copies/rebinds the value. | Barn/Toast source |
| Literal formatting | Plain canonical signed base-10 decimal, identical for `tostr` and `toliteral`. | Existing value/types rows; Barn/Toast source |
| Serialization | v17 tag 0 plus signed decimal. Full-range managed restart is unresolved. | Barn/Toast source; new row required |
| Overflow | Existing managed observations are exact. No general contract is inferred for Toast's unchecked signed C++ arithmetic or casts. | Existing focused rows; source disagreement |
| Encoding | Source and v17 forms use ASCII decimal characters, a subset of the database's ISO-8859-1 byte contract. JSON belongs to its later builtin slice. | Formatting and database owners |
| Error behavior | E_DIV for zero division/modulo; E_TYPE for unsupported conversion/indexing/ordering surfaces; E_RANGE for invalid collection positions and absent map keys; E_FLOAT for non-real FLOAT conversion. | Existing focused rows; Barn/Toast source |

## Unresolved managed-oracle question

One missing decision remains after deduplication:

- Do nominal MININT, zero, MAXINT, and actual INT64_MIN preserve exact INT type,
  value, and decimal literal form across a managed v17 dump/restart when nested
  in a property value?

The smallest new durable row is one managed restart row covering those four
values. It must use Banteng's owned stock profile and WSL launcher against the
pinned Toast executable. If the initial expected result is disproved, this
record and the row must be corrected to the observed result before Java design.

## Gate status

Steps 1 through 4 of the mandatory authority gate are complete for INT. Step 5
is open only for the full-range v17 managed restart row above. No Java API,
record, sealed family, hashing strategy, collection helper, numeric adapter, or
production implementation is authorized until that row passes and this record
is updated with its durable conformance commit and managed result.
