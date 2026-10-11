# ERR Barn authority audit checkpoint

- Scope: read-only Barn audit at commit `5a89ba0250a654bf4ae9383aecd4e6f2b0b363a1`; Barn working tree remains unread and unmodified.
- Current findings: ERR is persisted type code 3 with an inline integer payload; Barn implements codes 0 through 18, including `E_INTRPT`, while `spec/types.md` and `spec/errors.md` claim 18 codes and stop at `E_EXEC` (17).
- Representation owners: `types/errorcode.go`, `types/typecode.go`, and `types/value.go`; literals lower through `parser/parser_error.go` and `bytecode/parser_literals.go`.
- Behavioral owners located: conversion and literal formatting in `builtins/types.go` and `types/value.go`; equality/truth in `types/value.go`; map identity/order in `types/map.go`; VM comparisons/indexing in `vm/op_compare.go` and `vm/op_index.go`; database codec in `db/format/reader_value.go` and `db/format/writer.go`.
- Serialization observation: ERR reads and writes as type tag `3` plus an unchecked integer payload in both v4-compatible and v17 paths.
- Blocker: none. Some owner bodies and normative passages still need narrow line-specific inspection because earlier combined output was truncated.
- Next action: inspect the exact VM ordering/indexing, conversion/encoding, and DB-version passages via `git show`/`git grep`, then report disagreements and the smallest Toast oracle questions.
