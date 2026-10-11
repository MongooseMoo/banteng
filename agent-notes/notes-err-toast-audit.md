# ERR Toast authority audit

## Scope

- Read-only audit of ToastSTUNT pinned at `aecc51e9449c6e7c95272f0f044b5ba38948459e` and existing managed rows in `C:\Users\Q\code\moo-conformance-tests`.
- Barn is excluded. This note must remain untracked and must not be staged or committed.

## Current findings and observations

- The pinned Toast checkout was verified at the required commit.
- `src/include/structures.h:65-74,99-119,163-177` defines 19 ordered error codes (`E_NONE` through `E_INTRPT`), `TYPE_ERR == 3`, and scalar union storage as `enum error`.
- `src/keywords.gperf:48-66` enumerates source tokens and their language-version gates; `src/parser.y:1003-1020,379-383` resolves a recognized keyword to `TYPE_ERR` plus the enum payload.
- `src/unparse.cc:37-128,135-177` gives description/name rendering and case-insensitive name parsing.
- `src/numbers.cc:122-189` converts ERR to its numeric ordinal for `toint`/`tofloat`; unsupported collection conversions return `E_TYPE` while ERR conversion itself succeeds.
- `src/include/utils.h:45-68` proves ERR is copied/ref'd/free'd as an inline scalar; no allocation, aliasing, or mutable payload exists.
- `src/utils.cc:381-399,408-492` makes all ERR values false, compares same-type ERR by ordinal, and tests equality by exact enum identity; cross-type equality is false.
- `src/execute.cc:1295-1370` confirms language equality and relational operators use exact identity / ordinal ordering for ERR.
- `src/execute.cc:1690-1828` allows ERR as a map key/index and map range endpoint; ERR as a list index or ERR as the indexed base raises `E_TYPE`, while absent ERR map keys raise `E_RANGE`.
- `src/map.cc:677-715` accepts ERR keys and applies copy-on-write only to the containing map, leaving the scalar ERR key/value unchanged.
- Existing conformance candidates include `language/error_comparison.yaml`, `language/equality.yaml`, `language/index_and_range.yaml`, `basic/value.yaml`, `builtins/primitives.yaml`, encoding/value builtin rows, and server dump-persistence rows.
- Broad searches produced noisy generated-builtin/log matches; the next pass will inspect only exact source symbols and managed YAML files.

## State / blocker / next action

- State: enum/parser/scalar/copy/truth/comparison/index-map authority traced; no tracked files changed.
- Blocker: none after explicit permission to create this untracked checkpoint note.
- Next action: trace literal formatting, v4/v17 persistence, encoding, and raised-error mechanics; inventory managed row coverage, deduplicate, and report only the smallest genuine oracle gaps.
