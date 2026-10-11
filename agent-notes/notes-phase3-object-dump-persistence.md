# Phase 3 object dump persistence

## Current state

- The immutable implementation plan remains active at `docs/reports/banteng-implementation-plan.md`.
- Phase 3 is complete and committed through `server/map_dump_persistence.yaml`; the map-family commit is `dc58837 feat: complete phase 3 map persistence`.
- The current exact target is `server/object_dump_persistence.yaml`.
- Git is on `master`, ahead of `origin/master` by 167 commits, with no tracked modifications before this family. Existing notes and `pyghidra_mcp_projects/` remain untracked.

## Oracle and Banteng results

- The exact Toast managed object family passes 1/1.
- The exact Banteng managed object family completes setup, v17 checkpoint, process restart, and property reload, then returns `E_TYPE` from the post-restart verification program.
- The row stores six signed object-reference boundaries plus a map built from `#0` and `#4294967296`. After restart it checks all object types/literals and calls `length(identity_map)` before looking up both keys.

## Confirmed immediate cause

- Banteng's `length` manifest call shape accepts only STRING and LIST, and its implementation returns `E_TYPE` for MAP.
- Barn `builtins/strings.go::builtinLength` explicitly accepts string, list, and map; the generated Barn signature is one `TYPE_ANY` argument.
- Toast `src/list.cc::bf_length` explicitly returns `maplength` for `TYPE_MAP`, and registers `length` with one `TYPE_ANY` argument.
- Therefore the observed `E_TYPE` is caused before the row can expose its later object-key identity assertions.

## Next action

- Add a complete parser/compiler/VM/builtin red test for `length([#0 -> "small", #4294967296 -> "large"])`, then extend only the existing `length` call shape and handler to accept MAP. Run that targeted test, reread Phase 3, and rerun the exact Banteng object family to expose the next same-family result.

## Runtime object-map red gates

- Added `MooVmTest.objectMapKeysUseToastComparatorIdentityThroughBuiltinDispatch`, which builds the row's exact two-key map and requires `{length, lookup #0, lookup #4294967296}` to return `{1, "large", "large"}`.
- The first run failed at the returned-outcome assertion because `length(map)` produced `E_TYPE`, confirming the managed failure cause.
- Extended the existing `length` call shape and handler directly to accept MAP and return `MapValue.size()`; no new helper or abstraction was added.
- The second targeted run returns successfully but produces `{2, "small", "large"}`. This is the required red result for the row's remaining object-map identity behavior.
- Toast `utils.cc::compare` returns `lhs.v.obj - rhs.v.obj` from an `int` function while `Objid` is signed 64-bit in this profile. The low 32 bits therefore define map comparator identity; `#0` and `#4294967296` compare as zero. Toast `mapinsert` replaces the first entry with the second key/value, and `maplookup` uses the same comparison, so both lookups return `"large"` and length is one.
- No external blocker. The next action is to make the existing `MapValue` comparator use Toast's low-32-bit subtraction for integer/object keys and make `MapValue.get` search by that same comparator identity, then rerun the exact targeted test.

## Object family implementation green

- Changed the existing `MapValue` integer/object comparison to the low 32 bits of signed subtraction, matching the Toast 64-bit build's `Num`/`Objid` return through `int`.
- Changed `MapValue.get` to locate keys through that same comparator identity rather than Java record equality. No new interface/helper/adapter was added.
- `MooVmTest.objectMapKeysUseToastComparatorIdentityThroughBuiltinDispatch` passes in 1m04s.
- After the required Phase 3 reread, the exact managed Banteng `server/object_dump_persistence.yaml` family passes 1/1 through socket setup, v17 checkpoint, process restart, six signed 64-bit object values, literal rendering, and both comparator-identical map lookups.
- No external blocker. The next action is to run the affected `MooValueTest`, `MooVmTest`, and `V17RoundTripTest` classes together, then validate/stage/commit this completed object-family slice if they pass.

## Committed result

- The combined affected `MooValueTest`, `MooVmTest`, and `V17RoundTripTest` run passes.
- `git diff --check` and cached whitespace validation pass; exactly the intended builtin, value, and VM-test files were staged. Notes and `pyghidra_mcp_projects/` remained untracked.
- Committed the completed object family as `e3683ac fix: preserve phase 3 object map identity`.
- The next exact Phase 3 path is `server/string_dump_persistence.yaml`; start with its Toast managed oracle run.
