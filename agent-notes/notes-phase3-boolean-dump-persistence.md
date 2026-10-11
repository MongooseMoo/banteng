# Phase 3 boolean dump persistence family

## Current findings and observations

- The preceding `server/dump_persistence.yaml` slice is committed as `406d3b0 feat: add phase 3 map keys`.
- Its targeted `BuiltinCatalogTest`, managed Banteng family (2/2), full `./gradlew check`, and `git diff --check` all passed before commit.
- The next exact Phase 3 family is `src/moo_conformance/_tests/server/boolean_dump_persistence.yaml`.
- Pinned stock Toast passes its one row, `boolean_type_and_values_survive_dump_and_restart`, through the managed runner.
- Current Banteng fails that row at `return dump_database();`: the managed socket receives no value, so the failure occurs before restart verification.
- `LambdaMooV17Codec.writeValue` and `readValue` have no boolean case. Pinned Toast `src/db_io.cc` writes boolean type tag `14` followed by numeric `1` or `0`, and reads the same numeric payload.

## State

- The ledger was clean at slice start on `master`, ahead of `origin/master` by 163 commits.
- The only tracked change is a red `V17RoundTripTest` fixture change that places `{true, false}` in a local property, forcing nested boolean v17 write/read coverage.
- The red targeted run failed exactly with `unsupported Phase 2 v17 value: BOOLEAN` at the nested list element.
- Added only `BooleanValue` tag-14 numeric payload write/read cases to `LambdaMooV17Codec`.
- `V17RoundTripTest` now passes in WSL.
- The same managed Banteng boolean family now passes 1/1 through checkpoint and restart.
- Full `JAVA_HOME=/opt/java/25 ./gradlew check` and `git diff --check` pass.
- The reviewed tracked diff contains only `LambdaMooV17Codec.java` and `V17RoundTripTest.java`.
- The existing `notes-*.md` files and `pyghidra_mcp_projects/` remain untracked and must not be staged.

## Blocker

- None.

## Next action

- Stage exactly the two reviewed files, commit the kept boolean persistence slice, then advance to the next exact Phase 3 family: `server/error_dump_persistence.yaml`, Toast first and Banteng second.
