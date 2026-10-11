# MAP persistence conformance subtask

## Scope

- Authorized deliverable: only `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/server/map_dump_persistence.yaml`.
- Do not modify any other tracked file, commit, touch Barn, or start managed Toast.
- Parent separately authorized this untracked checkpoint note because the runtime required it before work could continue.

## Current findings

- `docs/reports/map-value-authority.md` authorizes one provisional managed v17 restart row preserving comparator order, equal-fold replacement key spelling, a high-byte STR key, and non-total BOOL/WAIF/ANON key topology.
- The authority says Toast writes MAP pairs in comparator order and reloads them through ordinary `mapinsert()`.
- Existing managed persistence rows store test data in a temporary property on `#1`, call `dump_database()`, wait 1500 ms, use `restart_server` with `wait_ms: 300`, assert after reload, and clean up the property plus created objects.
- Existing WAIF setup creates a class under `$waif`, adds a `:marker` property, and instantiates with `class:new()`.
- Existing high-byte STR setup uses `chr()` and verifies raw bytes with `encode_binary()`.

## State

- Target repo branch is `main`, ahead of `origin/main` by 23 commits.
- Target repo has many unrelated untracked artifacts and no tracked modifications reported by `git status --short --branch`.
- Created only `src/moo_conformance/_tests/server/map_dump_persistence.yaml` in the target repo.
- The row stores exact ordinary comparator order, equal-fold replacement spelling, and a raw `chr(200)` key, plus pre-restart `mapkeys`, `toliteral`, `==`, and `equal()` baselines for forward/reverse BOOL, WAIF, and ANON maps.
- Cleanup deletes the temporary `#1.map_persist_authority` property, recycles both anonymous objects, and recycles the temporary WAIF class.

## Blocker

- Static pytest collection did not start: both `uv run pytest --collect-only ...` and `uv run --no-sync pytest --collect-only ...` failed while `uv` tried to remove locked `.venv/lib64` (`Access is denied`). No pytest cases and no managed server started.
- Managed Toast expectations remain provisional and will be run by the parent.

## Next action

- Perform read-only YAML/schema inspection without touching the locked environment, verify Git scope, and report the exact file plus setup/transport caveats to the parent.
