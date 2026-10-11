# Phase 3 value code inventory

## Scope

Read-only inventory of the Phase 3 first slice against the exact current managed
`basic/value.yaml`. No production or tracked test edits, no suite runs, and no
Git writes.

## Current findings

- Phase 3 requires end-to-end value expansion through the production scheduler,
  VM, `WorldTxn`, and checkpoint path.
- The managed authority is
  `C:\Users\Q\code\moo-conformance-tests\src\moo_conformance\_tests\basic\value.yaml`.
- That file contains 37 named rows, not 39. The coordinator confirmed that 37 is
  the correct current authority.
- Row breakdown: `typeof` 5, `tostr` 9, `toliteral` 8, `toint` 4, `toobj` 3,
  `tofloat` 3, and `equal` 5.
- Current `BuiltinCatalog` registers and implements `typeof` and `toliteral`.
- Current `BuiltinCatalog` has no manifest entries or handlers for `tostr`,
  `toint`, `toobj`, `tofloat`, or `equal`.
- Current `MooVm` routes calls through `BuiltinCatalog`; unknown builtins raise
  `E_VERBNF`. Its bytecode `==`/`!=` path calls `MooValue.equals`, but that does
  not supply the missing `equal()` builtin registration.
- Current `MooValue` supplies type tags and `toLiteral()` for all closed value
  variants. Its `StringValue.equals` is case-insensitive, which is incompatible
  with the five managed `equal()` rows requiring case-sensitive string equality.
- Current focused tests register only `typeof` and `toliteral` in
  `BuiltinCatalogTest.REACHABLE_NAMES`; there are no current test calls to the
  five missing builtin names.
- `MooVmTest` already covers the five managed `typeof` value categories through
  `typeof(...) == INT/STR/FLOAT/LIST/OBJ` expressions.
- Existing `MooValueTest` literal tests cover integer, float, object, string,
  error, list, and map rendering, but not the builtin-specific difference
  between `tostr({1, 2}) == "{list}"` and `toliteral({1, 2}) == "{1, 2}"`.
- Prior `tostr` production and one broad VM test were introduced by
  `e3b85f15c0fb65b136b3fbd7b778b65e1e6d23ca` in `BuiltinCatalog.java` and
  `MooVmTest.java`; production was removed by
  `ae03f407b3bd575f896667bd5fb0509473fc25e2`, and the remaining old VM test
  block was removed by `7f12c5903d960dc6a0066d81c601c660cdf67a86`.
- Prior exact `equal` production plus a VM test was introduced by
  `86b55b297b0216965570038ed312ab1af59a493a` in the same two files and removed
  by `2d379386829251adcf3704dcb0170586c2fc459c`.
- Prior `toint` production/test slices matching all four managed rows were
  introduced in sequence by `e9b8c604075331d0be7b2153872a27187170e6f8`
  (integer), `76b3b7e2900778db2c231a76631b3d4f5e75a9a9` (float),
  `c0fe7f32c214798ec3d488d28b8533e22c322d86` (object), and
  `23d8decd8f6fb4cc7ea1d6519d870d830c57ec25` (string), all in
  `BuiltinCatalog.java` and `MooVmTest.java`; removal was `2d379386...`.
- `33fa3ff4b5ae410ead3f1f3d2d839421d834c63c` introduced `toobj`, `tofloat`,
  and initial `toint`, but only for converting `ErrorValue` codes. Its one VM
  test covered `E_INTRPT`, not any current managed `toobj`/`tofloat` row. Those
  implementations/tests were removed by `2d379386...`.

## State and blocker

No active blocker. The earlier path and row-count mismatches were resolved by the
coordinator. No tracked files have been modified by this inventory.

## Next action

Inspect focused current tests and bounded Git history for prior deleted
implementations/tests of only `tostr`, `toint`, `toobj`, `tofloat`, and `equal`,
then report exact missing symbols, prior commit/file locations, and minimal tests.
