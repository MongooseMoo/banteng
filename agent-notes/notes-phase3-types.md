# Phase 3 `basic/types.yaml` slice

## Current state

- The preceding `basic/value.yaml` slice is committed as `b660059`.
- Tracked files were clean before this slice; existing `notes-*.md` files and `pyghidra_mcp_projects/` are untracked and must remain unstaged.
- The controlling Phase 3 plan requires managed-socket vertical slices through the Phase 2 scheduler, VM, `WorldTxn`, and checkpoint path, while exact convergence stays on one suite family at a time.

## Observed

- Pinned Toast `/root/src/toaststunt` is at `aecc51e9449c6e7c95272f0f044b5ba38948459e` and passes `basic/types.yaml`: 126/126.
- Banteng passes 92/126 and fails 34 rows.
- Thirty-three failures are the missing Boolean/type-constant surface: `true`/`false`, `NUM`, `ERR`, `MAP`, `ANON`, `BOOL`, Boolean `typeof`, truth, equality with integers, conversions, `tostr`, and `toliteral`.
- One failure is integer `0 ^ -1`, which returns `0` instead of `E_DIV`.
- Toast represents booleans as distinct `TYPE_BOOL` values with type code 14, truth payload, literal forms `true`/`false`, and equality with integer 1/0. Runtime constants are installed in `fill_in_rt_consts()`.
- Toast `do_power()` raises `E_DIV` for integer zero with a negative exponent.
- Banteng currently resolves existing type constants in `MooVm.loadLocal()`, has no Boolean `MooValue`, and returns zero for every negative integer exponent.

## Tried

- Ran the exact managed Toast and Banteng commands for `src/moo_conformance/_tests/basic/types.yaml`.
- Read the active Phase 3 plan after the passing substantial Toast run.
- Inspected primary Toast sources: `eval_env.cc`, `structures.h`, `utils.cc`, `numbers.cc`, `unparse.cc`, and `db_io.cc`.
- Started two read-only Codex subagents. The Banteng inventory agent stopped because its read-only instruction conflicted with this mandatory notes-edit checkpoint. The Toast source audit is still pending.

## Blocker

- None currently. The failing family and primary semantics are identified.

## Next action

- The Toast audit confirmed the primary semantics and source ownership. The Banteng inventory subagent remained blocked by its read-only/checkpoint conflict.
- Added focused tests first; they failed to compile because `BooleanValue` and `Type.BOOLEAN` did not exist.
- Implemented a distinct canonical `BooleanValue` in `MooValue`, runtime resolution for `true`, `false`, `NUM`, `ERR`, `MAP`, `ANON`, and `BOOL` in the existing `MooVm.loadLocal()` owner, bool/int VM equality, the exact bool conversion/rendering branches in `BuiltinCatalog`, and `E_DIV` for integer `0 ^ negative` in the existing arithmetic path.
- Focused `MooValueTest` and `MooVmTest` pass.
- Managed Banteng `basic/types.yaml` now passes 126/126, matching pinned Toast 126/126.
- Full `JAVA_HOME=/opt/java/25 ./gradlew check` passes.

## Next action

- `git diff --check` passed and the exact five-file slice was committed as `15f699e feat: add phase 3 boolean types`.
- Tracked state was clean after the commit; all notes remained unstaged.
- The next listed family, `basic/arithmetic.yaml`, already passes both pinned Toast and Banteng at 10/10. No source slice or commit is warranted for it.

## Next action

- Reread the active Phase 3 plan after the passing Banteng arithmetic run, then start the next exact family `basic/string.yaml` with pinned Toast followed by Banteng.
