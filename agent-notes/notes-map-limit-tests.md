# MAP limit conformance correction

## Current findings

- `docs/reports/map-value-authority.md` identifies two disputed limit rows in the sibling conformance suite.
- Pinned Toast registers the MAP option token against `max_list_value_bytes`, while `bf_mapdelete` performs no post-result `value_bytes` check.
- `C:\Users\Q\code\moo-conformance-tests` is on `main`, ahead of `origin/main` by 23 commits, with no tracked changes and many unrelated untracked artifacts that must remain untouched.
- The target file contains four MAP-limit rows: deletion, indexed mutation, range mutation, and literal construction.
- Those four rows have been corrected in place: the three producers now distinguish the ignored `max_map_value_bytes` setting from the controlling `max_list_value_bytes`, and deletion now expects an unchecked over-limit result.
- `apply_patch` initially made the edited hunks LF within the CRLF file; `unix2dos --keepdate` restored the complete file to CRLF, confirmed by `git ls-files --eol` as `i/crlf w/crlf`.
- Read-only inspection at pinned Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e` confirms all relevant producer sites call `server_int_option_cached(SVO_MAX_MAP_VALUE_BYTES)` while `server.h` defines that token against the `max_list_value_bytes` property.
- The pinned `bf_mapdelete` erases the key and returns the MAP directly, with no `value_bytes` result check.
- `git diff --check` passes and the exact diff is limited to the four existing MAP rows.

## State

- Only `src/moo_conformance/_tests/server/limits.yaml` has been edited in the target repo.
- No managed Toast process has been started.
- No commit has been made.
- A non-server `uv run python -m moo_conformance.lint_duplicates --help` verification attempt did not run because `uv` could not remove the existing `.venv\lib64` path (`Access is denied`, OS error 5). No environment repair was attempted.

## Blocker

- Static YAML validation through the repository's `uv` environment is unavailable because of the existing `.venv\lib64` access error. This does not prevent diff/scope verification.

## Next action

- Verify final tracked-file scope, file line endings, and target status without starting managed Toast, then report the provisional diff and the blocked static-validation uncertainty.
