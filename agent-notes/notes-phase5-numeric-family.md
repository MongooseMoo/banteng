# Phase 5 numeric-family slice

## Current state

- Branch: `master`
- Starting HEAD: `d668c565d51c55b2d9d4ab5a3f7b3e03d2cf36de`
- Tracked worktree was clean before edits; numerous pre-existing untracked notes and investigation artifacts were present.
- The `min` production and test edits are complete; only those two tracked files are modified.
- Parent narrowed this source slice to canonical `min` only. The other 30 missing names are deferred to later source slices and must not be patched in this commit.

## Authority checked

- `docs/reports/banteng-implementation-plan.md` Phase 5 lines 544-579 requires the single existing `BuiltinSpec` manifest/implementation path and exact `BuiltinCatalogTest` contract coverage.
- WSL Toast checkout `/root/src/toaststunt` is exactly at pinned commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Pinned `src/numbers.cc` registrations and implementations were read directly.
- Corresponding YAML was found under `../moo-conformance-tests/src/moo_conformance/_tests/generated_builtins/`; behavioral authority also exists in `builtins/math.yaml`, `builtins/min_call_shapes.yaml`, and `builtins/floatstr_call_shapes.yaml`.

## Findings

The six `numbers.cc` builtins already genuinely implemented in `BuiltinCatalog` are `toint`, `tofloat`, `max`, `abs`, `random`, and `time`.

The 31 remaining canonical registrations are:

`min`, `reseed_random`, `frandom`, `round`, `random_bytes`, `ctime`, `ftime`, `floatstr`, `sqrt`, `cbrt`, `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `sinh`, `cosh`, `tanh`, `acosh`, `atanh`, `asinh`, `atan2`, `exp`, `log`, `log10`, `ceil`, `floor`, `trunc`, `distance`, and `relative_heading`.

Pinned call-shape groups:

- `min`: one or more numeric arguments, homogeneous at runtime.
- `reseed_random`: zero arguments, wizard only.
- `frandom`: one or two floats.
- `round`: one float.
- `random_bytes`: one integer.
- `ctime`: zero or one integer.
- `ftime`: zero or one integer.
- `floatstr`: float plus integer, optional third argument of any type.
- Unary float functions: `sqrt`, `cbrt`, `sin`, `cos`, `tan`, `asin`, `acos`, `sinh`, `cosh`, `tanh`, `acosh`, `atanh`, `asinh`, `exp`, `log`, `log10`, `ceil`, `floor`, `trunc`.
- `atan`: one or two floats.
- `atan2`: exactly two floats.
- `distance` and `relative_heading`: exactly two lists.

## Verification and process state

- The focused `min` test and complete `BuiltinCatalogTest` class both pass in separate clean cached WSL Java 25 runs.
- Two earlier overlapping Gradle invocations were terminated and their results discarded; they are not evidence.
- `git diff --check` passes.

## Blocker

None for the narrowed `min` slice.

## Next action

Commit and push only canonical `min` in `BuiltinCatalog.java` and `BuiltinCatalogTest.java`, reread Phase 5, then continue the still-incomplete manifest with the next canonical entry.
