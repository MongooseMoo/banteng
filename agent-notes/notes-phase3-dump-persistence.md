# Phase 3 dump persistence family

## Current state

- The preceding property-info slice is committed as `d945f4c feat: add phase 3 property info`; tracked state was clean before this family.
- Toast passes `server/dump_persistence.yaml` 2/2.
- Banteng passes `inherited_override_survives_dump_and_restart`, proving the new explicit property override survives `dump_database()` and managed restart.
- Banteng fails `adjacent_floats_survive_dump_and_restart` with `E_VERBNF` at the final assertion expression.
- Git is on `master`, ahead of `origin/master` by 162 commits. The only tracked change is the intended red test in `src/test/java/moo/builtin/BuiltinCatalogTest.java`; all `notes-*.md` files and `pyghidra_mcp_projects/` remain untracked.
- The targeted `BuiltinCatalogTest` is red for exactly the missing `mapkeys` manifest entry (and the manifest-size expectation), after fixing its required `LinkedHashMap` import.
- Added the one-argument pure VM `mapkeys` manifest entry and handler directly in `BuiltinCatalog`; the handler returns keys in the Toast scalar type/value order without collapsing adjacent floats.
- `JAVA_HOME=/opt/java/25 ./gradlew test --tests moo.builtin.BuiltinCatalogTest` passes in WSL: 24 tests, build successful.

## Exact cause and authority

- The final expression calls `mapkeys(mapping)`; Banteng has no `mapkeys` manifest entry or implementation.
- Toast registers exactly `mapkeys(MAP)` and returns a list of keys in the map's canonical tree order. Wrong arity/type are handled by the call-shape dispatcher as `E_ARGS`/`E_TYPE`; there is no permission or world mutation.
- Banteng `MapValue.entries()` is immutable and insertion-preserving. The active row only decides that two adjacent float keys remain distinct after restart; `length(mapkeys(mapping))` must return 2. Exact broader key-order authority can be encoded in the targeted builtin test using existing `MooValue.compareTotal` if that is the current canonical value ordering.
- Direct source inspection found no existing `MooValue.compareTotal` API. `StringValue` does already own exact case-insensitive byte comparison, and the red test fixes the Toast scalar type order as integer, object, error, float, string while preserving adjacent float keys.

## Blocker and next action

- No external blocker.
- The requested read-only Toast audit is complete and recorded separately in `notes-mapkeys-toast-audit.md`.
- Next: reread the controlling plan after the passing targeted test, then run the same two-row managed Banteng `server/dump_persistence.yaml` family. If it passes 2/2, reread the plan again and run the Phase 3 Gradle gates before committing this kept slice.
