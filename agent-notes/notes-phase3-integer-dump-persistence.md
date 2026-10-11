# Phase 3 integer dump persistence family

## Current findings and observations

- The float-boundary slice is committed as `3cf451a fix: format phase 3 float boundaries`.
- Its targeted value test, managed Banteng family, exact rerun of full `./gradlew check`, and `git diff --check` all passed before commit. The earlier full-gate failure was JetCheck generator exhaustion and did not recur with the unchanged float slice.
- The next exact family is `src/moo_conformance/_tests/server/integer_dump_persistence.yaml`.
- Pinned stock Toast passes its one row, `full_range_integers_survive_dump_and_restart`, through the managed runner.

## State

- No source slice for integer persistence has started.
- Existing `notes-*.md` files and `pyghidra_mcp_projects/` remain untracked and must not be staged.

## Blocker

- None.

## Next action

- Reread the controlling Phase 3 plan after the passing Toast run, then run the same exact integer YAML family against current Banteng. If it is red, inspect only that row and begin one Git-accountable slice; if green, advance to `server/list_dump_persistence.yaml` without inventing work.
