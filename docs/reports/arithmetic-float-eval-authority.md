# Arithmetic FLOAT and eval-error authority

## Scope

This record freezes the Java contract required to finish the current
`arithmetic` conformance family. It covers FLOAT representation and source
literals, strict FLOAT arithmetic and ordering, and propagation of a runtime
arithmetic error out of a dynamic `eval()` activation into the persisted
`#2:eval` verb's `except ANY` handler.

It does not authorize numeric promotion. The selected stock Toast profile has
`option.PROMOTE_NUMBERS: false`. It also does not implement the later map,
database-writer, conversion-builtin, complete structured-traceback, or task
effect-classification slices merely because those contracts were needed to
choose a correct FLOAT representation.

## Verified source identities

- Banteng authority procedure:
  `docs/reports/toast-oracle-identity-2026-07-14.md`.
- Toast source and executable: `/root/src/toaststunt` at
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`, executable
  `/root/src/toaststunt/build-release/moo`.
- Barn implementation reference at audit time:
  `2741fa469c06cd691e42dca0867b19f3acd1d411`.
- Durable conformance authority after the FLOAT edge additions:
  `../moo-conformance-tests` at
  `81a27ce85e775a6601cf7df69a7844b90f2a2dc0`.
- Stock profile:
  `profiles/toast/stock-wsl-testdb.json`, including
  `option.PROMOTE_NUMBERS: false`.

## Normative Barn specification

- `../barn/spec/types.md:13-18,45-65`: FLOAT has persisted type tag 9 and
  IEEE-754 binary64 semantics; source and arithmetic paths must not expose a
  non-finite result.
- `../barn/spec/types.md:241-252,291-317`: arithmetic is strict by type,
  FLOAT/FLOAT subtraction returns FLOAT, and mixed INT/FLOAT arithmetic or
  ordering raises `E_TYPE`.
- `../barn/spec/operators.md:491-570,626-668`: equality, ordering, addition,
  and subtraction contracts.
- `../barn/spec/grammar.md:317-333`: `.5`, `1.`, `1e2`, and signed exponent
  forms are FLOAT literals.
- `../barn/spec/database.md:90-109`: database FLOAT values use tag 9 and a
  textual binary64 encoding with `DBL_DIG + 4` significant digits.
- `../barn/spec/builtins/verbs.md:366-378`: documents `eval()`, but its claim
  that runtime errors become `{0, error}` disagrees with current Toast.
- `../barn/spec/statements.md:533-541`: documents exception handlers, but its
  claim that the catch variable is only an ERR disagrees with Toast's
  structured exception value.

## Current Barn implementation path

The public source-to-execution path is:

1. `../barn/cmd/barn/main.go:605-629`, `evalExpression`.
2. `../barn/compiler/compiler.go:37-55`, `CompileMOO`.
3. `../barn/parser/lexer.go:324-385`, `readNumber`, and
   `../barn/parser/parser.go:151-160,349-377,718-724`.
4. `../barn/bytecode/literal_values.go:10-15`, `valueFromLiteral`, and
   `../barn/bytecode/compiler.go:587-632`, `compileBinary`.
5. `../barn/vm/vm.go:103-120,467-497`, `Run` and `Execute`.

The semantic owners are:

- `../barn/types/value.go:40-44,83-84,106-110`: FLOAT is a tagged raw
  `float64` payload.
- `../barn/types/value.go:136-157,162-235`: truth, 15-significant-digit
  literal formatting, and numeric FLOAT equality.
- `../barn/vm/op_arith.go:39-104,159-195`: checked strict addition and
  subtraction.
- `../barn/vm/op_compare.go:65-131,228-270`: strict comparison.
- `../barn/config/options.go:9-20`: numeric promotion defaults off.
- `../barn/vm/registry.go:23-49,78-136`: dynamic eval compilation and frame
  creation.
- `../barn/vm/stack.go:65-97`: a normal eval return becomes `{1, value}`.
- `../barn/vm/vm.go:709-848`: errors search handlers across activations,
  unwind the eval frame, and bind a structured exception in the caller.

Barn disagrees with Toast in two relevant places. Its lexer rejects `.5` and
does not recognize `1.` as a FLOAT. Its map hash in
`../barn/types/map.go:23-34` hashes the 15-digit display form, which separates
signed zeros and collapses some distinct adjacent doubles even though MOO key
identity does the opposite. Neither Barn behavior is copied into Banteng.

## Current Toast implementation path

- `src/parser.y:934-1000`: recognizes decimal and exponent FLOAT forms,
  converts with `strtod`, and rejects an out-of-range literal.
- `src/parser.y:358-368,517-583`: creates `TYPE_FLOAT` and binary expression
  nodes.
- `src/include/structures.h:99-119,163-177,212-219`: FLOAT is a distinct tag
  with one raw `double` payload.
- `src/code_gen.cc:652-714`: emits arithmetic and comparison opcodes.
- `src/execute.cc:1312-1380,1411-1532`: dispatches comparisons, subtraction,
  and addition to the numeric semantic owner.
- `src/numbers.cc:194-280`: differing numeric tags produce `E_TYPE`;
  FLOAT/FLOAT operations produce a checked `double`, with non-finite results
  becoming `E_FLOAT`.
- `src/utils.cc:380-470`: FLOAT truth is `value != 0.0`; equality and ordering
  use numeric double comparison, so both signed zeros are equal and false.
- `src/map.cc:84-88,330-348,677-740`: map keys use the same comparison, so
  signed zeros name one key while adjacent finite doubles remain distinct.
- `src/list.cc:376-410,448-467,1161-1209`: FLOAT literals use the C `%.15g`
  contract and append `.0` when needed. The managed oracle proves that
  `toliteral(-0.0)` canonicalizes to `"0.0"`.
- `src/db_io.cc:91-104,153-195,313-324,350-378`: database FLOAT values use
  tag 9 and 19-significant-digit text.
- `src/verbs.cc:604-652`, `bf_eval`: parse failure returns `{0, errors}`;
  normal dynamic execution returns `{1, value}`.
- `src/execute.cc:3386-3425`: constructs the eval activation.
- `src/execute.cc:245-435,535-565`: runtime errors construct
  `{code, message, value, traceback}`, unwind across activations and finally
  blocks, and enter the first matching caller handler.

For mixed FLOAT/INT addition Toast constructs `E_TYPE` through
`PUSH_TYPE_MISMATCH`: the exception code is `E_TYPE`, its value describes the
expected FLOAT and received INT types, and the caller's `except ANY` matches
the code in element one. Complete message, value, and traceback parity remains
owned by the later exception/traceback family; this arithmetic slice must not
invent a parallel exception class or prevent that structured value from being
added to the existing list-value path.

## Durable conformance evidence

The original arithmetic rows are in
`../moo-conformance-tests/src/moo_conformance/_tests/basic/arithmetic.yaml`:

- `arithmetic::float_subtraction`: `11.0 - 5.5` returns `5.5`.
- `arithmetic::mixed_int_float_addition_type_error`: `1.0 + 1` raises
  `E_TYPE`.
- `arithmetic::mixed_int_float_comparison_type_error`: `5.5 > 5` raises
  `E_TYPE`.

The primitive-family gaps were added and committed in conformance commit
`81a27ce`:

- `equality::signed_zero_and_adjacent_float_map_identity` proves numeric
  signed-zero equality and ordering, false truth, canonical literal `"0.0"`,
  one signed-zero map key, and two distinct adjacent-double keys.
- `float_literals::float_literal_forms` proves `.5`, `1.`, `1e2`, and
  `1E+2`.
- `dump_persistence::adjacent_floats_survive_dump_and_restart` proves the
  19-digit finite database round trip preserves adjacent doubles and their
  key identity.

Existing durable rows cover type tag/constants, ordinary truth, conversions,
ordinary equality and map keys, exact formatting, rejection of non-finite
conversion strings, and arithmetic overflow. The focused managed selection of
those rows passed `48 passed, 11436 deselected`.

## Managed oracle results

Every command used the exact managed procedure from
`docs/reports/toast-oracle-identity-2026-07-14.md`, from
`C:\Users\Q\code\barn`, with only `-k` changed. Toast was never run directly
against the tracked database.

- Full arithmetic family: `10 passed, 11474 deselected` before the three new
  rows existed.
- Existing FLOAT contract selection: `48 passed, 11436 deselected`.
- First run of the three new rows: two passed and the signed-zero row proved
  the proposed `"-0.0"` expectation wrong by returning `"0.0"`.
- Corrected durable rows: `3 passed, 11484 deselected`; collection also found
  exactly those three among 11,487 tests.

The failed first expectation is retained here because it is the evidence that
corrected the Barn-derived formatting assumption. The committed conformance
row contains only the Toast-proven `"0.0"` result.

## Persisted eval wrapper and error transition

`../moo-conformance-tests/src/moo_conformance/_db/Test.db:223-234` defines the
public test path. Persisted `#2:eval` sets task permissions, enters nested
`try`/`except ANY`/`finally`, calls `eval(argstr)`, emits `{2, e}` from the
catch, and always emits its suffix from `finally`.

The required runtime transition is therefore:

1. execute the dynamic FLOAT expression in an explicit eval frame;
2. on strict mixed-type `E_TYPE`, search that frame's handlers;
3. when none match, discard the eval frame without ending the task;
4. continue the same error search in the caller frame;
5. enter persisted `#2:eval`'s matching handler, then its `finally`;
6. end the task only if no activation catches the error.

Current Banteng stops at step 3 incorrectly:
`src/main/java/moo/vm/MooVm.java:397-424` searches only the active frame and
calls `failUncaught`. `src/main/java/moo/vm/VmState.java:102-117` already has
the correct normal eval-return transition and can be extended directly; no
sender, adapter, alternate evaluator, or Java call-stack exception path is
authorized.

## Frozen Java representation for this slice

- Add one concrete immutable `FloatValue` with one primitive `double` payload
  and `Type.FLOAT(9)`. Do not add a numeric interface or promotion helper.
- `FloatValue` must implement MOO equality explicitly with numeric `double ==`
  semantics. It cannot use generated record equality because Java record
  equality distinguishes signed zero.
- `hashCode` must normalize both signed zeros to one hash and must preserve
  distinct adjacent finite doubles, keeping it consistent with MOO equality.
- Truth is `value != 0.0`.
- Literal formatting is Toast's 15-significant-digit form, with integral
  forms retaining `.0` and negative zero canonicalized to `0.0`. Database
  serialization later uses 19 digits and must not reuse this display form.
- Add one explicit `PUSH_FLOAT` opcode using the existing numeric instruction
  operand for raw double bits. This keeps bytecode immutable and serializable
  without adding another operand abstraction.
- The lexer/parser must accept all Toast-proven FLOAT forms and reject
  non-finite literals at the parser boundary.
- FLOAT/FLOAT subtraction returns FLOAT. Mixed INT/FLOAT addition and ordering
  raise `E_TYPE`; no coercion occurs.
- An error unhandled in an eval frame must expose the caller frame and continue
  handler/finally routing. It must not terminate the entire `VmState` at the
  eval boundary.

Non-finite database ingestion remains a later persistence-input decision. Both
Barn and Toast raw constructors/readers can physically hold such bits, while
normal MOO source, conversion, and arithmetic paths reject them. That
disagreement does not change the authorized payload from one primitive
`double` and does not authorize FLOAT database writing in this arithmetic
slice.
