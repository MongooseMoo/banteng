# Mongoose database history rewrite

## Findings

- The accidentally committed database was exactly `fixtures/mongoose.db`.
- It was introduced by old commit `667574b build: pin Toast profile identities`.
- The tracked blob was `d960078cb3dea40d1358a718d4119a6039faac5d`, size 101,244,108 bytes.
- Local and remote `master` initially pointed to `e2a6cac39d69a4fc9a8f1e869a73922cb9a43a10`.
- There were no local or remote tags and no other advertised branches.

## Preservation

- The uncommitted tick-exhaustion slice was saved as `stash@{0}` with message `preserve tick-exhaustion slice during mongoose history rewrite`.
- The local database was copied to `C:\Users\Q\code\banteng-mongoose.db.local-backup`.
- Source and backup SHA-256 matched: `33201970097D3D2D2BFC0D5F875F087D587601BF8255EF31EF19B416D65AC925`.

## Rewrite and push

- A fresh remote mirror at `C:\Users\Q\code\banteng-history-rewrite` was rewritten with `git filter-repo --path fixtures/mongoose.db --invert-paths`.
- Rewritten `master` is `298cd3a2208fd9567d3a2f61714bdf01c4f7be34`.
- The rewritten mirror has no history entry for `fixtures/mongoose.db`; `git cat-file -e d960078c...` exits 1.
- `git push --force origin --all` force-updated GitHub `master` from `e2a6cac` to `298cd3a`.
- `git push --force origin --tags` reported everything up to date.
- A separate fresh GitHub mirror at `C:\Users\Q\code\banteng-history-verify` advertises only `refs/heads/master` at `298cd3a`; it has no path entry and cannot resolve the removed blob.

## Final state

- The original working clone fetched rewritten `origin/master` and reset local `master` to it after preserving the active source slice.
- Commit `37b06d1 build: ignore local Mongoose database` added `/fixtures/mongoose.db` to `.gitignore` and was pushed normally after the force-push.
- Local and remote `master` now both point to `37b06d1f21dd2cc0a52c64e064ed671278beda3f`.
- The local fixture was restored at `fixtures/mongoose.db`; it is ignored and retains the verified SHA-256 and 101,244,108-byte size.
- The tick-exhaustion stash was reapplied successfully and dropped; its same five tracked source/test files are again modified.
- Local reflogs were expired and `git gc --prune=now --aggressive` completed. Local `git cat-file -e d960078c...` now exits 1.
- A fresh GitHub mirror fetched final `master` at `37b06d1`; it has no `fixtures/mongoose.db` history entry and cannot resolve the removed blob.
- Attempted cleanup of the two temporary mirrors and backup copy was rejected by the command policy before execution. Exact leftover paths are `C:\Users\Q\code\banteng-history-rewrite`, `C:\Users\Q\code\banteng-history-verify`, and `C:\Users\Q\code\banteng-mongoose.db.local-backup`. Both mirrors had already been verified free of the removed blob; the backup is outside every repository.
- No history-rewrite blocker remains. The active plan can resume from the restored tick-exhaustion slice.
