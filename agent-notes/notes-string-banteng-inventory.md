# Banteng basic/string inventory

## Scope and state

- Read-only bounded audit for exactly `strsub`, `index`, `rindex`, `strcmp`,
  `decode_binary`, and `encode_binary`.
- Branch is `master`, ahead of `origin/master` by 149 commits. Tracked worktree and
  index were clean at audit start; pre-existing untracked notes and
  `pyghidra_mcp_projects/` remain untouched.
- No implementation files have been edited.

## Current findings

- At audit start, `BuiltinCatalog.buildManifest()` registered 16 builtins, from
  `length` through `dump_database`; none of the six scoped string builtins was
  present. The shared worktree later acquired uncommitted manifest entries and direct
  implementations for all six, so that statement is no longer the latest snapshot.
- `BuiltinCatalog` already owns byte-preserving ISO-8859-1 conversions via private
  `decode(StringValue)` and `encode(String)`, exact recursive equality via
  `exactlyEqual(...)`, and pure VM manifest patterns with `CallShape` validation.
- `StringValue` is declared in `src/main/java/moo/value/MooValue.java`; its exact API
  and tests still need to be fully inventoried.
- Active conformance authority is
  `../moo-conformance-tests/src/moo_conformance/_tests/basic/string.yaml`.
  It already encodes core and edge semantics for all six scoped builtins, including
  case flags, zero-on-no-match, replacement behavior, binary escape/list/vararg
  behavior, invalid escapes/bytes/types, and roundtrip behavior.
- During the audit, the shared worktree changed concurrently: a later bounded search
  found `BuiltinCatalogTest.java` now names all six scoped builtins and contains
  focused invocations for their contracts and semantics. Those lines were absent from
  the earlier complete file read. Current source, status, and diff must therefore be
  reread before treating either snapshot as authoritative.
- The latest production diff keeps all behavior in `BuiltinCatalog.java` but adds
  `stringSubstitute`, `stringIndex`, `stringCompare`, `decodeBinary`, `encodeBinary`,
  plus private `appendBinaryBytes`, `matchesAt`, and `foldAscii` helpers. It is the
  first implementation found: `git log --all -S` found no committed Java history for
  any of the six names.
- The new direct tests encode pure VM ownership, permission ANY, zero fixed cost, and
  representative search/substitution/comparison/binary results and errors. The
  conformance YAML remains fuller: it also covers no-match, empty input/replacement,
  repeated substitutions, equality/empty comparisons, empty/null/full-byte decode,
  tilde/list/vararg/empty encode, negative/object errors, and binary roundtrip.
- Scope mismatch to keep visible: active `basic/string.yaml` exercises `index` and
  `rindex` only with two arguments or a third `case_matters` argument. The new test and
  manifest declare a second optional INTEGER position, thereby adding an unexercised
  four-argument offset contract.
- Minimal current owners are `BuiltinCatalog.java` for registration and behavior,
  `BuiltinCatalogTest.java` for direct contracts/representative behavior,
  `MooValue.java` for `StringValue`, and the one conformance YAML for full active
  semantics. Generic `MooVm`, `CallShape`, and `ArgType` already dispatch/validate the
  declared contract and need no scoped production change.
- Directly reusable existing APIs are `StringValue.bytes()`, `length()`, and
  `compareIgnoringCase()`, plus `BuiltinCatalog`'s private ISO-8859-1 `encode`/`decode`
  and existing `Result`, value, error, and manifest patterns. `StringValue.equals()`
  is Unicode-aware for valid UTF-8 while `compareIgnoringCase()` is ASCII-folded
  unsigned-byte ordering, so they are not interchangeable with exact byte comparison.

## Observation

- A broad initial text search was noisy because `fixtures/mongoose.db` contains many
  MOO-level `index`, `rindex`, and `strsub` calls. The remaining audit is narrowed to
  tracked Java owner/test files, the one conformance YAML, and Git history for the six
  exact builtin names.

## Blocker

- No external blocker. Concurrent edits required distinguishing audit-start,
  red-test-only, and latest uncommitted implementation snapshots.

## Next action

- Report the completed read-only inventory to the parent. Do not edit implementation,
  stage, commit, push, or act on the concurrent production/test diff.
