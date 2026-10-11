# Issue 25 v4 reader implementation notes

## Current state

- Assigned isolated worktree: `C:\Users\Q\code\banteng-agent-issue-25`
- Branch: `agent/issue-25-v4-reader`
- Exact base/HEAD before edits: `4880f025b2e396548bce7cbeb93a670a46b4f2e6`
- Worktree was clean before edits.
- Live GitHub issue #25 is OPEN: `Complete the v4 reader for normal Toast database state`.
- No Gradle/build/test command may be run locally for this task; CI will execute tests.

## Findings and observations

- The live acceptance contract requires recycled slots, local/inherited/clear property slots, every valid v4 value tag, verbs without compiled programs, queued and suspended tasks plus active connections restored into the runtime checkpoint model, direct committed Toast v4 fixtures including `Suspended.db`, and a streaming/bounded reader.
- Memory audit evidence identifies `Suspended.db` as currently rejected with `invalid suspended-tasks tail: 1 suspended tasks`.
- The controlling v4 authority record pins Toast source `aecc51e9449c6e7c95272f0f044b5ba38948459e` and identifies `db_file.cc`, `db_io.cc`, `tasks.cc`, and `server.cc` as the wire-format authorities.
- `docs/reports/first-server-v4-authority.md` explicitly scoped the existing reader to a bootstrap subset; issue #25 is the authorized expansion beyond that frozen subset.
- The selected conformance profile remains stock WSL Toast/Test.db; direct unmanaged Toast execution is not an authority gate.

## Blocker

- No blocker identified yet. Exact runtime checkpoint representation and pinned Toast v4 task/value layouts still need to be traced before tests are authored.

## Next action

- Finish reading the controlling implementation plan/profile and v4 authority.
- Inspect `LambdaMooV4Reader`, focused tests, runtime checkpoint records, all repository fixtures, and pinned Toast source/fixtures at the exact authority revision.
- Commit focused failing tests/resources first, then implement and commit production separately.
