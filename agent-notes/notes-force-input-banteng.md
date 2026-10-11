# Force-input Banteng trace

## Current findings and observations

- The worktree is on `master`; tracked files were clean at the start of this read-only subtask. Numerous pre-existing `notes-*.md` and tool-artifact paths are untracked.
- `force_input` is registered in `BuiltinCatalog` and has focused builtin coverage in `BuiltinCatalogTest`.
- `MooRuntime.ConnectionState` already owns the single `pendingInput` FIFO plus `holdInput`, `disableOob`, and `flushCommand` state.
- Current runtime references show ordinary/forced input converging through `executeLineNow`, while `read()` consumes `pendingInput` and committed `INPUT` effects are detached back into the scheduler.
- Existing authority text records a prior managed result of `{1, 0, 0, 1, 0}` and attributes the zero-valued audit handlers to their use of two-argument `listappend`, but this must still be checked against the current production code and exact fixture before concluding the current defect.

## State and blocker

- Read-only tracing is in progress. No source files, tests, Git state, or Gradle state have been changed.
- No external blocker is known. The immediate required checkpoint was this notes update.

## Next action

- Inspect the exact current implementations of `forceInput`, `executeLineNow`, `read`, committed `INPUT` effect application, server socket dispatch, and the exact conformance row; then report the smallest causal defect and relevant tests to the parent agent.

## Completed causal trace

- Exact row: `../moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml:665-755`. Setup replaces `#0:do_login_command` with a verb that records `{!valid(player), args, argstr}` and always returns a newly created player. It then opens a socket without sending input, finds its negative identity via `connected_players(1)`, calls `force_input(neg, "audit-force-login")`, yields with `suspend(0)`, and expects the handler to have seen `{1, {"audit-force-login"}, "audit-force-login"}` and the old negative identity to be absent.
- `MooServer` allocates negative IDs from `-2` downward and registers the socket before its virtual `handleConnection` calls `runtime.openConnection(...)` (`MooServer.java:45,103-114,139-146`).
- `WorldTxn.openConnection` maps a fresh negative ID to itself. `connectedPlayers(true)` returns those mapped values newest-first; after login, `switchConnectionPlayer` retains the connection key but replaces its visible mapped value with the positive player (`WorldTxn.java:194-235,290-296`). This registry is sufficient for the row.
- `force_input` itself is registered with the correct two required arguments plus the optional front argument and stages a concrete `ForcedInputRequest`; the builtin intentionally does not validate that the live target exists (`BuiltinCatalog.java:649-656,977-996`). Its direct test covers staging and permission/type/arity but not execution (`BuiltinCatalogTest.java:500-545`).
- At VM publication, `applyForcedInputRequests` resolves an exact negative connection ID first, otherwise resolves a positive attached player, then stages the existing `RuntimeEffect.input` (`MooRuntime.java:1713-1727`). Committed input effects call `scheduler.enqueueDetached(RuntimeRequest.line(...))` (`MooRuntime.java:2084`). Because that detached input gets its ticket during publication before the `suspend(0)` continuation is re-enqueued, the forced line is causally ordered before the caller resumes. No second input queue or registry is missing.
- `executeLineNow` sends a negative connection directly to `executeLogin(connectionId, line)` (`MooRuntime.java:236-243`). For the exact forced text, `executeLoginAuthentication` tokenizes one argument, preserves the full `argstr`, invokes the current listener's `do_login_command`, and `finishLogin` associates its returned player (`MooRuntime.java:931-1007,1009-1209`). This is the expected row path.
- `read()` is not on this row's causal path. Its current nonblocking handler resolves the same negative-or-attached-player registry and consumes the existing `ConnectionState.pendingInput` FIFO (`MooRuntime.java:1635-1668`). Forced input only reaches that FIFO when the existing hold-input path intercepts the injected line; with default hold false it executes as input.

## Smallest causal defect

- `MooRuntime.openConnectionNow` unconditionally returns `executeLogin(connectionId, "")` immediately after registering every accepted connection (`MooRuntime.java:168-184`). In this row, that synthetic empty line invokes the just-installed `do_login_command` before the test calls `force_input`, so the fresh connection is already associated with the new positive player. Consequently `connected_players(1)` no longer contains the new negative identity, `neg` remains its default `#-1`, and the forced nonempty line is sent to the wrong existing connection. The observed empty `args`/`argstr` frame is the synthetic open-time login, not failed forced-input tokenization.
- This is exactly what the old deleted broad regression failed to detect: commit `7137d8c` opened the target after installing an unconditional login verb, but asserted only that the target player became connected. It therefore passed even if the open-time empty call, rather than `force_input`, performed the login.
- The correction belongs at the open/initial-login boundary, not in `force_input`, `pendingInput`, `read`, scheduler ordering, or the connection registry. A focused runtime regression should install the row's recording login verb, open a fresh untrusted connection, assert it remains negative and the recording property is still empty before injection, then call exact `force_input(negative, "audit-force-login"); suspend(0);` and assert the full nonempty handler frame and association transition. Current `MooRuntimeTest` has no such end-to-end force-input regression; current `MooServerTest` only covers the walking skeleton.

## Final state

- Read-only trace complete. No production/test file, Gradle state, or Git state was changed. This notes file remains untracked and unstaged. No blocker remains.
