# Banteng row 30 restart note

## State

- Row 29 `audit_binary_mode_dispatches_raw_chunk_without_newline` was committed on `master` as `67183df` (`feat: dispatch binary connection chunks without delimiters`).
- The tracked worktree was clean immediately after that commit. Existing `notes-*.md` files and `pyghidra_mcp_projects/` remain untracked and must never be staged.
- The active exact-convergence slice is now only `audit_output_order_disconnect` in `../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:665-739`.

## Observations

- The durable row creates a fresh player verb `auditbye` whose body calls `notify(player, "audit-before-boot")`, then `boot_player(player)`, then returns.
- After login, sending `auditbye` must produce exact ordered output `audit-before-boot`, then `*** Disconnected ***`.
- The immediately preceding managed Banteng family run observed only `*** Disconnected ***`; this is the proven current target failure.
- Barn `spec/login.md:181-226` says `notify` sends to the player connection, output is buffered and flushed at task end, buffer limit, or explicit flush, and `boot_player` sends a disconnect message, calls `#0:user_disconnected`, then closes. It does not yet explicitly state the relative ordering of already-buffered notify output and the boot message.
- Barn `spec/builtins/network.md:67-99` only says `boot_player` disconnects and `notify` sends to the connection; it does not define output ordering. Its `no_flush` description says true buffers instead of immediate send.
- Three read-only subagents are currently tracing Barn, pinned Toast, and current Banteng respectively. They were told not to edit or run managed servers.

## Work performed

- Read the full durable row and its cleanup.
- Searched Barn specs for `boot_player`, disconnect, notify, output buffering, and order.
- Read the relevant `spec/login.md` and `spec/builtins/network.md` passages.
- No production, test, conformance, or documentation edit has been made for row 30.

## Blocker and next step

- No blocker currently exists. The mandatory authority gate is incomplete.
- Next: personally read the remaining relevant normative Barn passages, then Barn implementation and pinned Toast implementation from public entry point through the output/boot owner; collect subagent traces; write the repository-local authority section; run the exact managed Toast row; only then freeze Java design and prove a focused regression red.

## Authority progress after first checkpoint

- Additional Barn spec reads confirmed internal inconsistency: `spec/server.md:169-185` says pending output is discarded when a connection closes; `spec/builtins/server.md:254-277` lists the boot hook, close, then boot message; `spec/builtins/tasks.md:489-516` does not define ordering. None proves the row's exact order.
- Current Barn implementation does prove the healthy-connection row: default two-argument `builtinNotify` calls `Connection.Send`, which synchronously locks, writes the line plus CRLF, and flushes; `builtinBootPlayer` then delegates to `ConnectionManager.BootPlayer`, which synchronously sends and flushes `*** Disconnected ***` and only then closes. Exact owners are `builtins/network.go:833-871,1053-1077`, `server/connection.go:72-120`, `server/transport.go:212-226`, and `server/connection_manager.go:550-562`.
- The row description's word `already-buffered` is imprecise for current Barn: the row uses default `notify`, not `no_flush=1`, so Barn sends immediately. The observable two-line order still agrees.
- Current committed Banteng's exact cause was independently traced: VM output and the boot target are staged, but `MooRuntime.executeStored` calls `applyBootPlayerTargets` before returning staged command output. That path writes/flushed the disconnect line and closes the socket; only afterward does `MooServer.handleConnection` receive and try to write the staged notify line, whose closed-socket IOException is ignored. Exact owners include `BuiltinCatalog.java:519-530,1403-1409`, `MooVm.java:399-423`, `MooRuntime.java:640-659,1071-1093,1195,1279-1324`, and `MooServer.java:195-225,312-328`.
- Pinned Toast source personally read so far: `server.cc:1782-1802` enqueues default notify output while connected, then `boot_player` only sets `disconnect_me`; the main loop `server.cc:805-925` runs ready tasks before processing that flag, then enqueues the boot message and calls `network_close`. `server.cc:545-568` sends the default boot message through the same `network_send_line` FIFO path. `network.cc:1337-1410` appends output blocks to the handle queue in call order. The close-path flush implementation is currently being read to prove queued delivery before destruction.
- Both Barn and Banteng subagent traces completed read-only. The Toast subagent remains pending. No row-30 tracked file edit or managed run has occurred.
- Current blocker: none; the authority gate remains incomplete until Toast close-path flushing, the Toast subagent trace, repository-local evidence, and the live managed Toast row are complete.
- Immediate next action: finish reading `push_output`, `close_nhandle`, and refcount close behavior in pinned Toast.

## Live authority and Banteng seam

- Pinned Toast identity was personally reverified as `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- The exact managed Toast row passed: one selected, 11,504 deselected, in 4.82 seconds.
- Post-run WSL inventory found only the unrelated PID 19 `/tmp/td.db` process started July 13; it was left untouched. No managed-row process remained.
- Toast close behavior is now fully traced: `network_close` decrements the handle reference; final release calls `close_nhandle`; `close_nhandle` invokes `push_output` once before closing and frees residual blocks. The public contract says close flushes as much pending output as possible. Thus delivery is best-effort under backpressure, but delivered small-row lines preserve FIFO order.
- The repository-local `docs/reports/telnet-transport-authority.md` now contains the complete row-30 source evidence, spec disagreements, agreements, Banteng causal failure, pinned identity, exact live receipt, and process receipt. It currently says no Java design is yet selected.
- Personal Banteng read confirmed `executeLine` only receives `executeStored(...).output()` after `executeStored` returns. Inside `executeStored`, every VM segment immediately applies connection options, forced input, boot targets, and recycled-player closes before returning root state. `applyBootPlayerTargets` removes runtime/world connection state, runs `user_disconnected`, builds the boot message, and invokes `ListenerControl.bootConnection`, which closes the socket.
- Therefore the exact design decision still to freeze is how to keep the current existing boundaries while delivering root task output before the staged boot effect closes the same connection. No helper/interface/adapter/sender is authorized.
- No production or test edit has occurred for row 30. Current blocker: none.
- Next action: finish reading the exact VM output/boot staging and server boot/write boundaries, select and document the smallest change on the existing runtime seam, then add a focused real-socket regression and prove it red on committed production.

## Frozen design and first regression attempt

- The authority report now freezes the smallest existing-boundary design: `executeStored` supplies the root task's existing `player` identity when applying boot targets; a self-boot first synchronously publishes already-staged task output through existing `ListenerControl.writeConnection`, then follows the unchanged remove/hook/banner/boot-close path; the selected command branch does not re-add returned output if its connection was removed. No new interface/helper/adapter/sender/output path is introduced.
- A focused real-socket test `MooServerTest.writesStagedCommandOutputBeforeSelfBootMessage` was added. It creates/logs in a fresh player, installs `auditbye` with notify then self-boot, sends the command, and expects notify, disconnect banner, EOF.
- The first focused run failed in 5 seconds, but for the wrong reason: expected `audit-before-boot`, actual `I couldn't understand that.` at the first command-output assertion. This proves verb dispatch did not reach the target effect ordering and is not an acceptable red regression.
- Production remains unmodified for row 30. Only the authority report and focused test are tracked modifications.
- Current blocker: the focused test setup/dispatch mismatch must be corrected before production work. The immediately preceding committed binary-mode test uses the same general fresh-player/login structure and successfully dispatches a zero-argument player verb, so the next action is a literal comparison of those setup details and the command parser's verb argument classification, followed by a test-only correction and rerun.

## Invalid-red diagnosis progress

- The new test's source diff matches the durable row's `auditbye` name, `rxd` permissions, `{none, none, none}` argument spec, notify/boot/return code, and fresh-player login hook.
- `MooRuntime.executeLogin` returns only `*** Connected ***` for a successful stored login, so the observed `I couldn't understand that.` is not intended login output by that method.
- Normal command lookup first calls `world.verb(player, firstWord)`, classifies no direct/indirect object as `none`, and accepts permission bits whose packed direct/indirect specs are zero. `WorldTxn.verb` matches exact lowercase names and requires the executable bit.
- `BuiltinCatalog.addVerb` maps `rxd` to executable permissions, maps direct/indirect `none` to zero, preposition `none` to -1, and persists the name/source on the fresh object. No discrepancy has yet been found in these paths.
- Two read-only `rg` diagnostics had malformed Windows/regex patterns earlier but changed no files; corrected reads succeeded.
- Current blocker remains the invalid red. No production code is modified. Next action: instrument only the focused test's in-memory world setup enough to establish whether `auditbye` exists with the expected metadata immediately before target dispatch, then remove any diagnostic-only assertion once the test reaches the target disconnect-order failure.

## Harness step-boundary finding

- Test-only in-memory assertions established that immediately before target dispatch the fresh player's `auditbye` verb exists, has exact name `auditbye`, packed permissions 13 (`rxd`, direct none, indirect none), and preposition -1. Those assertions passed; lookup metadata is not the invalid-red cause.
- After successful target login and a 500 ms wait, `BufferedReader.ready()` was true. This proves `I couldn't understand that.` was pending output from the login send step, not the later audited `auditbye` command.
- The durable harness behavior was personally read: each `send` calls `TestConnection.send`, and `send` immediately calls `_receive_until_quiet`; that method drains all output available through a 0.5-second initial and 0.1-second post-data quiet window. Because the login step has no expectation, its drained lines are discarded before the later `auditbye` send/expect step.
- Therefore the focused socket test must likewise drain the login step's pending output after the row's 500 ms wait before sending `auditbye`. This is not a semantic assertion or production workaround; it reproduces the named harness step boundary. The temporary metadata assertions and `ready()` failure probe should be removed once the corrected test reaches the expected missing-notify versus disconnect failure.
- No production code is modified. Current blocker is now resolved in principle; next action is test-only cleanup/drain, followed by the focused red rerun.

## Correct red, implementation, and exact managed pass

- The focused test was corrected to reproduce the harness boundary: after the login step's 500 ms wait it drains any ready login-step lines before sending `auditbye`. Temporary metadata/ready probes were removed.
- The corrected focused test proved the intended red exactly: expected `audit-before-boot`, observed `*** Disconnected ***`.
- Production implementation changed only `MooRuntime`: the selected player-command branch retains `VmState` and returns its output only while the command connection remains registered; `executeStored` derives the existing root `player` local and supplies it to boot application; a self-boot writes already-staged task output through existing `ListenerControl.writeConnection` before logical removal/hook/banner/close. No new class/interface/helper/adapter/sender/queue/path was added.
- The focused regression then passed under Java 25 in 6 seconds.
- `spotlessApply` passed, followed by `installDist`.
- The exact managed Banteng row passed: one selected, 11,504 deselected, in 5.17 seconds.
- Exact post-run process inventory identified PID 381904 with temp database `moo_conformance_byevac2n`; that exact PID was stopped, and the subsequent Banteng inventory was empty.
- The authority report now records the invalid-red rejection, correct red, focused green, exact managed pass, and process cleanup.
- Current tracked modifications are expected to be only `docs/reports/telnet-transport-authority.md`, `src/main/java/moo/runtime/MooRuntime.java`, and `src/test/java/moo/server/MooServerTest.java`. This has not yet been reverified by Git.
- Current blocker: none. Next action is the substantial `gap_followups_toast_oracle` fail-fast managed Banteng gate, followed by exact process cleanup and the mandatory full immutable-plan reread.

## Substantial gate and final suite state

- The substantial managed `gap_followups_toast_oracle` fail-fast gate selected 17 rows, passed the first nine (including row 30), and stopped at `audit_property_flags_deny_nonowner_read_and_write` after 28.57 seconds. The next row expected `E_PERM` for a non-owner property read but Banteng returned `E_TYPE`; this is the next independent slice.
- Exact process inventory identified PID 358292 with temp database `moo_conformance_7hl0ltrn`; that exact PID was stopped and the subsequent Banteng inventory was empty.
- The authority report records this targeted-family receipt and next-row boundary.
- The mandatory post-targeted full reread of `docs/reports/banteng-implementation-plan.md` completed from line 1 through EOF in four non-overlapping chunks. It confirmed row 30 must be committed before beginning property flags.
- Final Java 25 `clean check installDist` passed in 16 seconds, including formatting checks, compilation, full tests, check, and rebuilt distribution.
- Because that was a passing full suite, a second mandatory full plan reread began. Chunks 1-120 and 121-240 have been completely read. The reread is currently incomplete only because this checkpoint fired before chunks 241-360 and 361-EOF.
- Current blocker: none. Immediate next action is to complete those final two immutable-plan chunks, then append the 16-second final gate receipt, inspect the exact tracked diff/status, stage only intended files, and commit row 30.
