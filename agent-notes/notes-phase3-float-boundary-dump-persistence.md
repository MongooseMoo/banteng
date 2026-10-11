# Phase 3 float boundary dump persistence family

## Current findings and observations

- The preceding boolean persistence slice is committed as `edffa1c feat: persist phase 3 booleans`.
- `server/error_dump_persistence.yaml` passes 1/1 on both pinned Toast and current Banteng; no code change was authorized or made for that already-green family.
- The next exact family, `server/float_boundary_dump_persistence.yaml`, passes 1/1 on pinned Toast.
- Current Banteng preserves the maximum finite, minimum normal, and minimum subnormal binary64 values across checkpoint and restart. All three type checks, equality checks, map-key lookups, and key count pass.
- The sole mismatch is `toliteral()` for the minimum subnormal: Toast returns `4.94065645841247e-324`; Banteng returns `4.9e-324`.
- The exact row calls the existing `toliteral` builtin, so the active owner is float literal formatting, not v17 persistence.
- Toast `src/list.cc` formats float literals with `snprintf("%.*g", DBL_DIG, value)`, where `DBL_DIG` is 15, then appends `.0` only when the result has neither a decimal point nor exponent.

## State

- The ledger was tracked-clean at slice start on `master`, ahead of `origin/master` by 164 commits.
- Added one red `MooValueTest` assertion for `Double.MIN_VALUE`; it failed exactly with Banteng's prior `4.9e-324` spelling.
- Changed only the existing `FloatValue.toLiteral()` formatter to pass the exact binary64 decimal expansion (`new BigDecimal(value)`) through the existing 15-significant-digit `%g` formatting path.
- `MooValueTest` now passes all 13 tests, including all prior Toast literal cases.
- The same managed Banteng float-boundary family now passes 1/1 through checkpoint and restart, with exact boundary spellings and identities.
- Existing `notes-*.md` files and `pyghidra_mcp_projects/` remain untracked and must not be staged.

## Blocker

- None.

## Next action

- Reread the controlling Phase 3 plan after the passing managed family, run the full Gradle and diff gates, review the two-file source slice, and commit it before advancing to `server/integer_dump_persistence.yaml`.
