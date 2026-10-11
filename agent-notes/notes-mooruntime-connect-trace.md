# MooRuntime explicit-connect trace

- Task: read-only trace of why explicit `connect Wizard` against the checksum-pinned ignored `fixtures/mongoose.db` returns `0` instead of authenticating.
- Control surface: `docs/reports/banteng-implementation-plan.md`, Phase 4. No source edits, commits, staging, or other-repository access are authorized.
- Current findings:
  - `fixtures/mongoose.db` is ignored by `.gitignore` and has exact SHA-256 `33201970097d3d2d2bfc0d5f875f087d587601bf8255ef31ef19b416d65ac925`.
  - `MooRuntimeTest.authenticatesWizardUsingPinnedMongooseLoginVerb` reads that fixture, opens connection `-47`, sends `connect Wizard`, and expects `*** Connected ***` plus a nonnegative player association.
  - The latest persisted test result fails: explicit input returns the Mongoose fallback message, leaves the connection unassociated, and records `do_login_command` returning integer `0`.
  - Persisted diagnostics show this explicit path: `#0:do_login_command` calls `#72:incoming_connection`, `#10:redlisted`, `#10:maybe_limit_commands`, `#10:parse_command({"connect", "Wizard"})`, then `#10:?({"connect", "Wizard"})`; VM outcome is `RETURNED`, value `0`, no error.
  - `MooRuntime.finishLogin` associates only from `state.switchedPlayer()` or an object-valued return that is in the world player set; integer `0` therefore falls through to unauthenticated output.
- Current state: the worktree was already heavily dirty and four commits ahead. No existing repository source file has been changed by this investigation. This note exists only because execution checkpoints required an accepted uncommitted notes update.
- Blocker: a fresh targeted Gradle run did not reach tests because another process was concurrently writing/deleting `build/classes/java/main`; it failed on directory deletion. Existing persisted test results are available and directly show the symptom.
- Next action: extract the exact current fixture source for `#0:do_login_command`, `#10:parse_command`, and `#10:?`, then trace the first parser/compiler/VM semantic mismatch that makes `connect` miss and fall through.
