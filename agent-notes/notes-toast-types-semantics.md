# Toast types semantics source trace

## Current findings

- Verified `/root/src/toaststunt` HEAD is `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- `src/include/structures.h` defines user-visible type ordinals in enum order: integer 0, object 1, string 2, error 3, list 4, float 9, map 10, anonymous object 12, waif 13, boolean 14.
- Complex in-memory types add `TYPE_COMPLEX_FLAG` (`0x80`), while database/type-facing ordinals use the masked `_TYPE_*` values.
- `eval_env.cc::fill_in_rt_consts()` installs `NUM` as integer type 0, `ERR` as 3, `MAP` as 10, `ANON` as 12, `BOOL` as 14, and installs `true`/`false` as `TYPE_BOOL` values in dedicated runtime slots.
- `sym_table.cc` exposes the boolean names and resolves variable names case-insensitively, so uppercase `TRUE`/`FALSE` resolve to those same slots.
- `objects.cc::bf_typeof()` returns `(arg.type & TYPE_DB_MASK)` in a `TYPE_INT`, stripping the in-memory complex flag.
- `utils.cc::is_true()` uses the stored boolean truth field; `utils.cc::equality()` compares booleans directly and has an explicit boolean/integer bridge (`true == 1`, `false == 0`). Equality results are integer values in `execute.cc`.
- `numbers.cc::become_integer()` converts booleans to 1/0, so both `toint()` and `toobj()` accept them; `become_float()` explicitly returns `E_TYPE` for booleans.
- `list.cc::stream_add_tostr()` and `unparse_value()` both render booleans as lowercase `true` or `false`, owning `tostr()` and `toliteral()` respectively.
- `numbers.cc::do_power()` returns `E_DIV` for integer base 0 with any negative integer exponent; `execute.cc` raises that returned error at `EOP_EXP`.

## Observations

- The active family is `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/basic/types.yaml`.
- Relevant owners found so far include `eval_env.cc`, `sym_table.cc`, `objects.cc`, `utils.cc`, `numbers.cc`, `unparse.cc`, and `include/structures.h`.

## State

- Source investigation is complete; no implementation source has been edited.
- This note is intentionally uncommitted as required by the workspace checkpoint.

## Blocker

- None currently.

## Next action

- Hand the concise source-location findings to the requesting agent.
