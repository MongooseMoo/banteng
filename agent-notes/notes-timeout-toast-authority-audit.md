# Timeout handler Toast authority audit

## Current findings

- Scope is only `audit_handle_task_timeout_invoked` at sibling `moo-conformance-tests` HEAD `3db3c88c417caa8bd35ef2e4abf46dd454d442b5` and pinned Toast HEAD `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- `profiles/toast/stock-wsl-testdb.json` identity was verified live in WSL: Toast source HEAD, binary SHA-256 `72fb1cf96cb303647a8ee72808e7c1ff62a491ecf44f547e6757e71ba2402bde`, options SHA-256 `a88a8c6c37b66ca65a08a318988361827f131421edeff25e5b4af83fb3fa8036`, and Test.db SHA-256 `1a3f23ebb549e02ccf5341668425118fcdc935b977096add87bc2a8ef29d408e` all match the manifest.
- The exact selected row ran live on pinned Toast in WSL and passed: `1 passed, 22 deselected in 6.10s`.
- Initial pinned-source search locates the timeout resource/message and handler-info construction in `src/execute.cc:581-601`; stack-list construction is in `src/execute.cc:452-516`; in-database handler execution/fallback is in `src/execute.cc:3066-3191`.

## Observations

- The row replaces `#0:handle_task_timeout`, sets `$server_options.bg_ticks` to 100, forks an infinite loop, waits, and observes `{"ticks", 1, 1, 1, 1}`: resource name plus nonempty stack/traceback lists containing the original timeout text.
- The pinned Toast checkout has unrelated untracked files, but tracked source is at the pinned commit and all pinned manifest checksums match.

## State and blocker

- Audit is in progress. No source, test, profile, or Git state has been changed.
- No technical blocker. This notes file is required by the session checkpoint and must remain uncommitted.

## Next action

- Read the exact pinned `src/execute.cc` regions for timeout argument construction and handler dispatch, plus only directly called owner functions needed to establish permissions/player/programmer, stack and traceback shapes, ordering, failure behavior, and original output; then report precise source lines and the live observable.
