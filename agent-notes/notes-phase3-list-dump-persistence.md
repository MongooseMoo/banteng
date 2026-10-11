# Phase 3 list dump persistence family

## Current findings and observations

- `server/integer_dump_persistence.yaml` passes 1/1 on both pinned Toast and current Banteng; no code change was authorized or made for that already-green family.
- The next exact family, `server/list_dump_persistence.yaml`, passes 1/1 on pinned Toast.
- Current Banteng reaches post-restart verification but fails to compile `updated[2][2] = "changed"` with `indexed assignment requires a local owner`.
- The exact row proves nested heterogeneous list persistence, then requires copy-on-write isolation: only `updated[2][2]` changes; `updated[3]` and the original `before` value remain unchanged.
- `MooCompiler.compileAssignment` accepts only `Identifier` collections for `IndexTarget`. It already accepts one nested `IndexAccess` parent for `RangeTarget` and emits the parent-aware writeback sequence.
- The VM's `SET_RANGE_LOCAL` supports one parent index through its numeric operand. `SET_INDEX_LOCAL` currently has no parent-depth operand and updates only the named local directly.
- Added a complete-pipeline `MooVmTest` for `l[1][2] = 9`; its first run failed exactly at the compiler's local-owner restriction.
- Extended the existing `SET_INDEX_LOCAL` instruction with the same optional one-parent operand contract as `SET_RANGE_LOCAL`, compiled nested `IndexAccess` targets into that path, and applied copy-on-write parent-list writeback in the existing VM handler.
- `MooVmTest` now passes all 86 tests.
- The same managed Banteng list-persistence family now passes 1/1 through restart and verifies nested types, values, map lookup, literal form, and copy-on-write isolation.

## State

- The tracked slice contains the existing instruction contract, compiler, VM handler, and one complete-pipeline test; no new abstraction or alternate execution path was added.
- Existing `notes-*.md` files and `pyghidra_mcp_projects/` remain untracked and must not be staged.

## Blocker

- None.

## Next action

- Run full `JAVA_HOME=/opt/java/25 ./gradlew check`, reread the plan if green, then run `git diff --check`, review the exact four-file slice, and commit it before advancing to `server/map_dump_persistence.yaml`.
