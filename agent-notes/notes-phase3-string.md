# Phase 3 `basic/string.yaml` slice

## Current state

- `basic/types.yaml` is committed as `15f699e` and tracked state was clean afterward.
- `basic/arithmetic.yaml` required no source change: Toast and Banteng both pass 10/10.
- Pinned Toast passes `basic/string.yaml` with 46 passed and 2 explicitly skipped crypt rows.
- Banteng passes 4, fails 42, and skips the same 2 crypt rows.

## Observed

- Every Banteng failure is `E_VERBNF`.
- The exact absent builtins are `strsub`, `index`, `rindex`, `strcmp`, `decode_binary`, and `encode_binary`.
- The existing `BuiltinCatalog` is the single manifest and handler owner. No competing implementation or adapter exists.
- `StringValue` already owns defensive Latin-1 byte arrays and exposes `bytes()` and `length()`.
- Toast primary ownership is `src/list.cc` plus byte/search primitives in `src/utils.cc`.
- `strsub`, `index`, and `rindex` default to case-insensitive matching; a truthy optional flag makes matching case-sensitive.
- `strcmp` returns only the sign of case-sensitive byte comparison.
- `decode_binary` parses `~XX`; its truthy optional argument emits every byte as an integer, while default mode groups printable bytes, space, and tab into strings and emits other bytes as integers. Invalid escapes raise `E_INVARG`.
- `encode_binary` accepts zero or more arbitrary values, recursively accepts only strings, integers 0..255, and lists, then escapes tilde and non-printable bytes as uppercase `~XX`; invalid values raise `E_INVARG`.

## Tried

- Ran exact managed Toast and Banteng commands for the family.
- Read the active Phase 3 plan after the passing Toast run.
- Started two bounded read-only Codex audits for Toast semantics and Banteng ownership; both are pending.
- Inspected the exact YAML rows and primary Toast functions directly.

## Blocker

- None.

## Next action

- Added focused manifest and handler tests first; they failed only because the six specs were absent.
- Added the six exact Toast call contracts and handlers to the existing `BuiltinCatalog` owner. No other production file changed.
- The handlers operate directly on owned `StringValue` bytes and implement substitution, forward/reverse search, sign-normalized byte comparison, `~XX` decoding, default printable grouping, full byte decoding, recursive raw-value encoding, and uppercase escaping.
- The fourth `index`/`rindex` offset position is part of Toast's registered 2-4 argument contract even though this YAML uses only the first three positions.
- Focused `BuiltinCatalogTest` passes.
- Both Codex audits completed and confirmed the pinned source semantics and single Banteng owner. Their required notes remain untracked.

## Next action

- Managed Banteng `basic/string.yaml` now matches pinned Toast exactly: 46 passed and the same 2 explicit crypt skips.
- Full `JAVA_HOME=/opt/java/25 ./gradlew check` passed.
- `git diff --check` passed.
- The exact tracked diff contains only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`.
- A final import-order/line-wrap correction changed no behavior; the full Gradle check passed again afterward.

## Next action

- Reread the active Phase 3 plan after the passing full check, rerun `git diff --check`, stage only the two exact files, verify the staged ledger excludes all notes, commit the kept string slice, confirm tracked cleanliness, and start `basic/list.yaml`.
