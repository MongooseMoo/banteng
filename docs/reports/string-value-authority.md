# String value authority

## Scope

This is the Phase 2 mandatory authority record for the public STR value family
before its remaining managed-oracle rows are added. It covers byte
representation and limits, source construction and escapes, conversion,
equality, ordering, map-key identity, truth, indexing, mutation/copy behavior,
literal formatting, v4/v17 serialization, concatenation, encoding, and error
behavior.

This record does not choose or approve a Java representation, authorize a value
hierarchy, or permit a production edit. The STR gate remains open until the
source-escape and raw-byte restart rows are durable and proven against pinned
Toast.

## Verified identities

- Barn normative specification and implementation reference:
  `5a89ba0250a654bf4ae9383aecd4e6f2b0b363a1`, read only through committed Git
  objects. Barn's working tree was not inspected or modified.
- Toast source and executable authority:
  `/root/src/toaststunt` at
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`, executable
  `/root/src/toaststunt/build-release/moo`.
- Existing durable conformance authority: `../moo-conformance-tests` commit
  `a0b7bbc` plus its ancestors.
- Managed oracle authority: Banteng's owned
  `profiles/toast/stock-wsl-testdb.json` and `scripts/run_toast_wsl.sh`, using
  the bundled disposable `Test.db` fixture.

## Normative Barn specification

- `spec/types.md:67-91` assigns STR type tag 2 and describes immutable binary
  byte sequences measured and indexed by byte. It promises source escapes for
  backslash, quote, newline, tab, carriage return, and hexadecimal bytes.
- `spec/builtins/strings.md` sometimes describes `length()` in characters and
  gives a Unicode-character example. That conflicts with the byte model in
  `spec/types.md`, current Barn, pinned Toast, and the raw-byte conformance rows.
- `spec/types.md:194-214` makes the empty string falsy and every nonempty string
  truthy.
- `spec/types.md:241-252,291-317` specifies no implicit numeric conversion and
  permits only STR plus STR concatenation.
- `spec/operators.md:443-570,626-648` owns equality, ordering, concatenation,
  and type errors. It does not clearly separate ordinary case-insensitive MOO
  equality from the case-sensitive `equal()` and `strcmp()` builtins.
- `spec/types.md:335-392` and `spec/grammar.md` specify one-based byte indexing,
  inclusive ranges, strict bounds, `^` as one, `$` as current length, and empty
  results for valid inverted ranges.
- `spec/database.md:52-74,90-109` assigns persisted tag 2, requires v4/v17
  reads and v17 writes, and names Latin-1 as the database encoding.

The spec is accepted only where managed Toast agrees. Its C-style escape table,
Unicode-character `length()` wording, and incomplete case description are not
authority.

## Current Barn implementation path

- `types/str.go` owns `strRep`, `NewStr`, `byteLen`, `str`, `appendRep`,
  `literal`, `Value.Str`, and `Value.StrAppend`. Storage is a Go string or byte
  slice; `byteLen()` and `Value.Len()` count bytes, not runes.
- `types/str.go:strRep.appendRep` may reuse uncommitted capacity behind a new
  header, but its watermark preserves every prior alias's content. The public
  behavior is immutable/copy-on-write.
- `parser/lexer_string.go:Lexer.readString` removes a backslash and preserves
  only the following byte. It does not decode `\n`, `\t`, `\r`, or `\xHH` as
  documented by the spec. `bytecode/parser_literals.go` then constructs
  `types.NewStr` from that byte content.
- `types/value.go:Value.Equal` uses `strings.EqualFold`; `types/map.go:keyHash`
  uses `strings.ToLower`; and `CompareMapKeys` uses lowercased Go strings.
  Those are Unicode-aware operations and violate the normative raw-byte model
  for non-ASCII sequences.
- `types/map.go:goMap.set` makes case-equivalent string keys share an entry,
  preserves the entry's order position, and replaces its stored spelling.
- `types/value.go:Value.Truthy` makes only the empty STR falsy.
- `vm/op_index.go`, `vm/collection_helpers.go`, and `Value.Len` own one-based
  byte indexing, ranges, and replacement behavior.
- `types/str.go:strRep.literal` quotes the string, escapes quote/backslash,
  preserves a literal tab, and emits other non-printable or high bytes as
  textual `~XX`. That is not pinned Toast's ordinary literal contract; Barn's
  own parser does not reverse those `~XX` spellings.
- `vm/op_arith.go`, `types/str.go`, `builtins/limits.go`, and
  `kernel/context.go` own concatenation and configured string limits. The
  representation has no fixed language maximum; configured producer limits
  raise E_QUOTA.
- `db/format/reader.go`, `reader_v4.go`, `reader_v17.go`,
  `reader_value.go`, and `writer.go` own v4/v17 string persistence. The spec's
  Latin-1 claim is authority only where the Toast-proven raw-byte round trip
  agrees.

Barn agrees with Toast on byte length, empty truth, generic backslash quoting,
one-based collection behavior, and observable immutability. Its Unicode-aware
case folding and `~XX` literal rendering are not copied into the STR contract.

## Pinned Toast implementation path

- `src/include/structures.h:76-119` assigns public/database tag 2 and marks STR
  complex only for in-memory ownership.
- `src/storage.cc:30-125` owns refcounted NUL-terminated byte buffers.
  `str_dup` uses `strlen` and `strcpy`, so every non-NUL byte can be represented
  but an embedded NUL cannot be part of a STR value. The empty string is
  canonicalized.
- `src/parser.y:1032-1046` owns source string literals. Backslash generically
  quotes exactly the next byte; `"` yields quote and `\\` yields backslash,
  while `\n`, `\t`, `\r`, and `\x41` yield `n`, `t`, `r`, and `x41`. A literal
  newline or EOF before the closing quote reports a missing quote.
- `src/utils.cc:300-348` owns refcount aliasing and explicit duplication.
  Normal copies may share an immutable buffer; mutation paths duplicate first.
- `src/utils.cc:54-80,408-480` owns ASCII `strcasecmp`, case-sensitive
  `strcmp`, general comparison, and equality. Ordinary language equality and
  relational ordering pass case-insensitive mode. Bytes above ASCII are not
  Unicode-folded.
- `src/execute.cc:1304-1350` routes ordinary `==`, `!=`, and relational
  operators through case-insensitive string comparison. `equal()` and
  `strcmp()` remain separately case-sensitive as proven by existing rows.
- `src/map.cc` delegates key identity and ordering to the same comparison
  owner. Case-different ASCII spellings name one ordinary map key.
- `src/utils.cc:380-400` makes only the empty string falsy.
- `src/execute.cc:1216-1257,1587-1778` owns one-based indexing, ranges, and
  assignment. Indexed replacement requires exactly one byte; bounds failures
  raise E_RANGE and a replacement of the wrong byte length raises E_INVARG.
  Mutation duplicates before changing the byte buffer.
- `src/list.cc:448-510,1161-1209` owns `toliteral` and `tostr`. Literal output
  surrounds STR with quotes and prefixes only quote and backslash with a
  backslash. High bytes remain raw; `encode_binary` is the separate explicit
  `~XX` representation.
- `src/execute.cc:1389-1532` owns STR concatenation and configured quota
  checks. Mixed additions raise E_TYPE and an exceeded configured producer
  limit raises E_QUOTA.
- `src/utils.cc:608-634` accounts STR value bytes as the payload length plus
  its terminator and value overhead.
- `src/utils.cc:672-724` and `src/list.cc:1472-1612` own conversion between raw
  bytes and explicit binary `~XX` strings. Existing rows freeze high-byte and
  invalid-code behavior.
- `src/db_io.cc:112-150,153-205,332-336,347-385` reads and writes tag 2 as one
  raw database line. This common value path serves the supported database
  versions. Because storage is NUL-terminated and persistence is line-based,
  embedded NUL/newline are not ordinary persistent STR payloads; high bytes are
  the decision-changing Latin-1 boundary.

## Agreements and disagreements

| Surface | Barn | Pinned Toast | Authority decision |
| --- | --- | --- | --- |
| Value model | Binary byte sequence | NUL-terminated byte sequence | STR is byte-oriented. Embedded NUL is not an ordinary value; every other byte requires exact preservation. |
| Source escapes | Generic backslash quoting in code; spec promises C-style decoding | Generic backslash quoting | Managed row must correct the stale spec expectation. |
| Equality/order | Unicode-aware case fold | ASCII byte case fold | Toast's ASCII-only fold controls. High bytes remain distinct. |
| Map identity | Unicode-aware lowercase hash | ASCII case-insensitive comparator | Toast's equality-consistent ASCII identity controls. |
| Literal high bytes | Barn emits textual `~XX` | Toast preserves raw bytes | Toast controls; `encode_binary` alone owns `~XX`. |
| Copy/mutation | Header-level copy-on-write | Refcount plus duplicate-before-mutate | Prior aliases remain unchanged; storage mechanism is internal. |
| Persistence | Claims Latin-1 | Raw one-line byte string | Managed high-byte restart row is required. |

## Existing durable conformance authority

The following existing rows already settle observable decisions that must not
be duplicated under new names:

- `basic/value.yaml`, `basic/types.yaml`, and `basic/string.yaml`: type,
  ordinary construction/conversion, truth, quote/backslash literal formatting,
  length, string operations, and conversion errors.
- `language/string_comparison_case.yaml`: ASCII case-insensitive ordinary
  equality and relational order, contrasted with case-sensitive `equal()` and
  `strcmp()`. Its introductory sentence incorrectly calls `==` case-sensitive;
  the Toast-proven expected row is correct and only the description must be
  repaired.
- `language/equality.yaml`, `builtins/map.yaml`, and
  `language/in_operator.yaml`: case-insensitive equality, map-key identity,
  replacement behavior, and membership.
- `language/index_and_range.yaml`: one-based byte reads, `^`/`$`, inclusive and
  inverted ranges, indexed/range assignment, bounds, type, and replacement
  errors.
- `builtins/chr_raw_bytes.yaml`: one-byte high values, byte-counting
  concatenation, high-byte non-folding, and explicit `encode_binary` output.
- `basic/string.yaml` and generated binary rows: binary encode/decode forms,
  invalid escapes, empty input, and byte-list round trips.
- `server/limits.yaml`: exact configured string concatenation boundaries and
  E_QUOTA producer behavior.
- Existing parser, list, and mutation rows prove quote/backslash construction
  and value-like assignment behavior.

## Provisional observable contract

| Dimension | STR contract before final oracle rows | Authority |
| --- | --- | --- |
| Representation limits | Immutable sequence of non-NUL bytes; length is byte count. Configured producer limits are semantic quotas, not a representation width. | Barn/Toast owners; raw-byte and limit rows |
| Construction | Double-quoted source; backslash generically quotes the next byte. Literal newline/EOF before close is invalid. | Barn/Toast parser owners; generic-escape row pending |
| Conversion | `tostr(STR)` returns its raw content; `toliteral` quotes it; numeric/object conversions and their failures follow existing focused rows. | Existing value/types rows |
| Equality | Ordinary `==` is ASCII case-insensitive and type-strict; high bytes are not Unicode-folded. `equal()` is case-sensitive. | Existing comparison and raw-byte rows |
| Ordering | Ordinary relational ordering is ASCII case-insensitive byte ordering; `strcmp()` is case-sensitive byte ordering. | Existing comparison rows; Toast owner |
| Hashing/map identity | Ordinary map identity matches ASCII case-insensitive equality; case-equivalent keys name one entry. | Existing equality/map rows |
| Truth | Empty STR is falsy; every nonempty STR is truthy. | Existing types rows |
| Indexing | One-based bytes, inclusive ranges, `^ == 1`, `$ == length`; strict bounds E_RANGE; indexed replacement exactly one byte or E_INVARG. | Existing index/range rows |
| Mutation/copy | Assignment may share immutable storage; every visible indexed/range change returns/rebinds a value without changing prior aliases. | Barn/Toast owners; existing mutation rows |
| Literal formatting | Quotes around raw bytes; quote and backslash prefixed with backslash; high bytes remain raw. `~XX` belongs only to `encode_binary`. | Existing quote/backslash rows; high-byte restart row pending |
| Serialization | v4/v17 tag 2 plus one raw database line. Empty, quote/backslash, and high-byte restart behavior remains unresolved. | Barn/Toast DB owners; restart row pending |
| Concatenation/limits | STR plus STR concatenates bytes; mixed addition E_TYPE; configured producer limit overflow E_QUOTA. | Existing arithmetic and limit rows |
| Encoding | Raw STR bytes are not UTF-8 characters; explicit binary encoding maps unsafe/high bytes to uppercase `~XX`. | `chr_raw_bytes.yaml` and binary rows |
| Error behavior | E_TYPE for unsupported operations/index types, E_RANGE for bounds, E_INVARG for invalid replacement/binary forms, E_QUOTA for configured producer limits. | Existing focused rows |

## Unresolved managed-oracle questions

Exactly two observable decisions remain after deduplication:

1. Do `\n`, `\t`, `\r`, and `\x41` source spellings yield the literal bytes
   `n`, `t`, `r`, and `x41`, confirming generic backslash quoting rather than
   the stale C-style spec table?
2. Do empty, quote/backslash, and raw high-byte STR values preserve exact byte
   length, equality, Toast literal form, binary encoding, ASCII-case map
   identity, and copy-then-index mutation behavior across managed v17 restart?

The smallest durable additions are one language construction row and one
managed restart row. The existing contradictory introductory sentence in
`language/string_comparison_case.yaml` must be corrected without changing its
already-correct expected behavior. All three changes belong to one STR
conformance slice and must pass through Banteng's owned stock profile and WSL
launcher against pinned Toast.

No newline or NUL persistence row is authorized: Toast's ordinary STR storage
and line-oriented database representation cannot carry those bytes as a normal
value. No duplicate truth, comparison, indexing, concatenation, or quota row is
justified.

## Gate status

Steps 1 through 4 of the mandatory authority gate are complete for STR. Step 5
is open only for the construction and restart rows plus the contradictory
description correction above. No Java API, record, byte owner, string helper,
case-folding adapter, codec, or production implementation is authorized until
both rows pass and this record is updated with their durable conformance commit
and managed result.
