# Equality and collection-construction authority

## Scope

This record freezes the semantic contract for the `equality` conformance
family. The required executable surface is string concatenation and equality,
recursive list and map equality, list splicing, map literals, map indexed
lookup and assignment, and `mapkeys()` sufficient to observe map-key identity.

This slice does not authorize a collection interface, equality service,
map-key adapter, mutable map facade, runtime splice value, or alternate
evaluation path. Map ordering, ranges, deletion, iteration, complete map
builtins, and database persistence remain owned by their later focused
families.

## Verified source identities

- Banteng before this slice: `8deaf7b3f15f27581912927dccfa5edaf3544e63`.
- Banteng oracle procedure:
  `docs/reports/toast-oracle-identity-2026-07-14.md`.
- Toast source and executable: `/root/src/toaststunt` at
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`, executable
  `/root/src/toaststunt/build-release/moo`.
- Barn implementation reference at audit time:
  `7950bf4bdb1512193d34c90572b562ddf6c2e19f`.
- Durable conformance authority after the focused equality and raw-byte
  additions:
  `../moo-conformance-tests` at
  `e9517c8ab67346b2b766cac310d869ff35087753`.
- Stock profile: `../barn/profiles/toast/stock-wsl-testdb.json`, including
  `option.PROMOTE_NUMBERS: false`.

## Normative Barn specification

- `../barn/spec/operators.md:480-524` specifies recursive, positional LIST
  equality and recursive, insertion-order-independent MAP equality.
- `../barn/spec/operators.md:545-549` claims that `==` and `!=` compare
  strings case-sensitively. This is wrong for current Toast.
- `../barn/spec/operators.md:224-242` specifies list splicing as list
  construction and requires a LIST operand.
- `../barn/spec/grammar.md:163,199-200` defines `@expression` inside list
  literals and map-entry syntax.
- `../barn/spec/builtins/maps.md:215-243` specifies scalar map keys, MAP type
  tag 10, and signed-zero key identity. Its description of FLOAT equality as
  bitwise is wrong because bitwise equality would distinguish signed zero.

The spec is authoritative only where it agrees with the managed Toast oracle.
The case-sensitive string claim and bitwise FLOAT wording are not copied into
Banteng.

## Current Barn implementation path

- `../barn/bytecode/compiler.go:628-631` lowers binary equality to `OP_EQ`.
- `../barn/vm/vm.go:485-489` and `../barn/vm/op_compare.go:11-35` dispatch
  `OP_EQ` to `Value.Equal`.
- `../barn/types/value.go:219-254` owns scalar, string, list, and map equality.
- `../barn/types/list.go:117-126` implements recursive positional LIST
  equality.
- `../barn/types/map.go:24-41,117-127` normalizes string and signed-zero keys
  and implements order-independent MAP equality.
- `../barn/bytecode/compiler.go:2599-2622` and
  `../barn/vm/op_list.go:78-100` lower and execute list splicing.

Barn code agrees with Toast on recursive collection equality, distinct INT and
FLOAT keys, signed-zero identity, and ordinary splice behavior. It uses
Unicode-aware `strings.EqualFold` and `strings.ToLower` over raw Go strings.
Banteng must likewise retain owned bytes while recognizing valid UTF-8 byte
sequences for equality; it must not replace the value representation with an
unconstrained Java UTF-16 string.

## Current Toast implementation path

- `src/execute.cc:1295-1305` dispatches `OP_EQ` to `equality(rhs, lhs, 0)`.
- `src/utils.cc:408-440,444-492` performs case-insensitive string comparison,
  exact numeric binary64 comparison, and recursive list/map dispatch.
- `src/list.cc:359-373` implements recursive positional LIST equality.
- `src/map.cc:84-88,223-247` uses the same value comparison for map lookup.
- `src/map.cc:678-715` replaces an existing case-insensitively equal key and
  rejects collection keys.
- `src/map.cc:766-788` recursively compares canonical map entries independent
  of source insertion order.
- `src/code_gen.cc:535-550` lowers splices directly into list construction;
  `src/execute.cc:1149-1168,1277-1284` checks for LIST operands and
  concatenates their elements.

There is no runtime splice value and no separate equality algorithm for maps.
MAP keys use ordinary MOO value equality: string keys fold case, INT and FLOAT
remain different types, signed FLOAT zero is one key, and adjacent finite
FLOAT values are different keys.

## Durable conformance evidence

The original twelve rows are in
`../moo-conformance-tests/src/moo_conformance/_tests/language/equality.yaml`.
They cover scalar equality, string concatenation before equality, list
splicing, map construction and assignment, cross-type inequality, and signed
zero versus adjacent FLOAT map identity.

Existing broader rows cover:

- map insertion-order independence, recursive map values, and folded string
  keys in `builtins/map.yaml:201-235`;
- rejected LIST/MAP keys with `E_TYPE` in `builtins/map.yaml:433-479`;
- ordinary, empty, multiple, nested, and invalid splices in
  `language/splice.yaml:11-67`.

Conformance commit `b68b1d7` adds three focused discriminators:

- `equality::nested_collection_equality` proves LIST to MAP to LIST recursive
  equality and a nested unequal result;
- `equality::map_scalar_key_identity` proves folded string-key replacement
  while INT `1` and FLOAT `1.0` remain separate keys;
- `equality::string_equality_folds_valid_utf8_case` proves both ASCII `A`/`a`
  and the valid UTF-8 encodings of `À`/`à` compare equal.

Conformance commit `e9517c8` adds the raw-byte discriminator
`chr_raw_bytes::raw_high_bytes_are_not_utf8_case_folded`. It proves that raw
single bytes `C0` and `E0` remain different while ASCII `A` and `a` compare
equal. Together the two rows distinguish UTF-8-aware comparison from either
ASCII-only or Latin-1-code-page folding.

## Managed oracle results and corrected disagreement

Every oracle command used the exact managed procedure from
`docs/reports/toast-oracle-identity-2026-07-14.md`, from
`C:\Users\Q\code\barn`, changing only `-k`. Toast was never run directly
against the tracked fixture.

- The full original `equality.yaml` family passed all 12 rows. The selector
  also matched eight other equality-named rows, for `20 passed` total.
- The first run of the three proposed discriminator rows passed nested
  equality and scalar key identity but disproved the proposed ASCII-only
  expectation: Toast returned `{1, 1}` for
  `{"A" == "a", "À" == "à"}`.
- After correcting the durable expectation and initial row name, the exact three-row
  selection passed: `3 passed, 11490 deselected` out of 11,493 collected.
- The later raw-byte row passed its exact managed selection, and the corrected
  valid-UTF-8 equality row passed its exact managed selection, each `1 passed,
  11493 deselected` out of 11,494 collected.

The failed first expectation and later raw-byte discriminator are retained
because they change the Java decision. The managed Toast process runs under
`en_US.UTF-8`; `strcasecmp` folds valid UTF-8 byte sequences, but raw invalid
high bytes are not folded as Latin-1 characters. Banteng's pre-slice
`StringValue.equals/hashCode` folds ASCII bytes only and is therefore
observably wrong. The correct representation remains owned bytes: equality and
hashing apply one consistent Unicode fold only when both byte arrays are valid
UTF-8, and otherwise use the existing ASCII byte fold. TCP input remains the
plan's ISO-8859-1 byte-preserving stream; decoding the connection as UTF-8
would destroy arbitrary MOO bytes and is not authorized.

## Pre-slice Banteng failure trace

The managed Banteng `equality::` baseline selected 12 rows and produced
`7 passed, 5 failed, 11478 deselected`.

- `string_equality` reaches `MooVm.arithmetic()` and raises `E_TYPE` because
  `ADD` handles only numeric pairs. Parsing, compilation, and the equality
  opcode already succeed.
- `list_equality_with_splicing` fails in `MooLexer` because `@` has no token.
  Existing ordinary `ListValue.equals/hashCode` is already recursive and
  positional.
- The three map rows fail while parsing `[]` because there is no MAP literal
  expression. The value hierarchy has no MAP type, the compiler has no map
  construction or indexed-assignment opcode, VM indexing handles only LIST,
  and `BuiltinCatalog` has no `mapkeys` case.

These are syntax/construction gaps, not justification for a second equality
owner. Existing `MooVm.equality()` must continue to call `left.equals(right)`.

## Frozen smallest Java representation

- Keep `StringValue` as an immutable owned byte array. Concatenation joins the
  two owned byte sequences directly. Equality and hash code use the same
  UTF-8-aware fold for valid UTF-8 byte sequences and the same ASCII-only byte
  fold for arbitrary invalid byte sequences. Raw `C0` and `E0` remain
  different; the UTF-8 encodings of `À` and `à` compare equal. UTF-8 decoding
  is a comparison operation, not the stored representation and not a TCP
  transcoding step.
- Keep the existing recursive `ListValue.equals/hashCode` unchanged.
- Add one concrete immutable nested `MapValue` with persisted type code 10.
  It owns an insertion-preserving unmodifiable snapshot of a
  `LinkedHashMap<MooValue, MooValue>`. Direct `Map.equals/hashCode` supplies
  order-independent recursive equality using the already frozen value
  equality and hash contracts. Do not add a key wrapper or comparator.
- Map construction and update reject LIST and MAP keys with `E_TYPE`. Updating
  an equal key replaces its value without mutating the prior `MapValue`.
- Add concrete MAP-literal and splice syntax nodes only. A splice node is
  compile-time syntax and never enters the runtime value hierarchy.
- Lower ordinary list elements and splices with explicit append/extend
  bytecode, so a splice runtime operand must be a LIST or raises `E_TYPE`.
- Lower a map literal with one explicit entry-count-bearing construction
  opcode. Its stack inputs are alternating evaluated key/value pairs.
- Add one explicit indexed-update opcode. For the local-owner form required by
  this family it consumes collection, key, and value, creates the replacement
  immutable map, rebinds the existing local owner, and leaves the assigned
  value as the expression result. It does not introduce mutable collection
  state or an indexing abstraction.
- Extend the existing VM `INDEX` switch directly for MAP lookup. A missing key
  raises `E_RANGE`; an invalid collection key raises `E_TYPE`.
- Add `mapkeys` directly to the existing `BuiltinCatalog` dispatch and return
  an ordinary `ListValue`. No builtin registry or map service is introduced.

The targeted Java tests must first demonstrate the five original managed
failures plus the three new discriminator contracts. The kept implementation
then must pass focused parser/compiler/value/VM/runtime tests, `gradlew check`,
the installed-distribution managed `equality::` family with all 15 rows, and a
fresh plan reread before commit.
