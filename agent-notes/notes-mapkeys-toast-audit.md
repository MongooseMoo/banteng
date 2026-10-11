# `mapkeys` audit checkpoint

## Current findings

- Toast registers `mapkeys` as exactly one `TYPE_MAP` argument at `C:/Users/Q/src/toaststunt/src/map.cc:1145` and implements it at `src/map.cc:1065-1080`.
- Toast returns keys by in-order red-black-tree traversal (`src/map.cc:809-823`) using the map's `compare()` ordering (`src/map.cc:84-88`, `src/utils.cc:408-440`).
- Barn registers `mapkeys` at `builtins/registry.go:135`, declares one map argument at `builtins/function_signatures_generated.go:118`, and implements it at `builtins/maps.go:14-40`.
- Barn returns keys sorted by `types.CompareMapKeys`; the implemented order is INT, OBJ/ANON, FLOAT, ERR, STR, with values ordered within type and strings compared case-insensitively (`types/map.go:178-223`).
- Banteng's existing map representation is `MooValue.MapValue` at `src/main/java/moo/value/MooValue.java:526-621`.

## Observations

- `mapkeys` performs no permission check in either Toast or Barn.
- Toast delegates wrong arity/type errors to builtin dispatch; Barn's handler explicitly returns `E_ARGS` then `E_TYPE`.
- Banteng `MapValue` is immutable and insertion-preserving, backed by an unmodifiable `LinkedHashMap`; map keys reject only list and map values.

## State

Read-only audit is complete. No source was edited and no tests were run.

## Blocker

None. This file exists only because the higher-priority checkpoint required an uncommitted note.

## Next action

Return the exact findings to the parent agent and perform no broader map work.
