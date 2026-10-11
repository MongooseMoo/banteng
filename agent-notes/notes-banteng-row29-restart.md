# Banteng row 29 checkpoint

## Ledger

- Row 28 `audit_gmcp_subnegotiation_delivered_across_reads` is committed as `85bc14f` (`feat: parse telnet out of band command words`).
- Tracked state was clean after commit; notes and `pyghidra_mcp_projects/` remained untracked.

## Active target

- The only active target is `audit_binary_mode_dispatches_raw_chunk_without_newline` at `../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:572-663`.
- It logs in a fresh player, enables `set_connection_option(player, "binary", 1)`, sends ASCII `auditbin` without CR/LF, and requires one zero-argument verb invocation recorded as `{\"\"}`.

## Authority and oracle

- Independent Barn, pinned-Toast, and current-Banteng traces completed.
- `docs/reports/telnet-transport-authority.md` records the spec limits, Barn and Toast option/read/dispatch paths, agreements/disagreements, and current Banteng failure path.
- The exact row passed pinned WSL Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`: 1 passed, 11504 deselected in 5.39 seconds.
- No process from that managed run remained; only the unrelated July 13 `/tmp/td.db` Toast process was present and left untouched.
- The row freezes immediate command dispatch for this ASCII chunk without CR/LF. It does not freeze arbitrary non-ASCII handling or a TCP write-to-read message boundary.

## Current Banteng cause and boundary

- `BuiltinCatalog.setConnectionOption` rejects `binary`; its closed option enum lacks it.
- `MooRuntime.applyConnectionOptionRequests` has no binary application.
- `MooServer.handleConnection` reads one byte at a time and dispatches ordinary input only on CR, LF, or EOF.
- Existing `MooRuntime.executeLine` already produces the required empty `argstr` once it receives `auditbin`.
- The existing concrete `BuiltinCatalog.ListenerControl` boundary is implemented only by `MooServer`; it can carry binary-mode changes without adding a new interface, helper, adapter, or sender.
- The likely smallest source surface is: authorize `ConnectionOption.BINARY`, apply it through the existing listener-control boundary, retain binary state in `MooServer`, and have its existing byte owner dispatch one socket read immediately through `executeLine` while binary is active.
- The Java representation and focused regression have not yet been written to the authority record. That is the next required action before any test or production edit.

## Blocker

- None.

## Red-green implementation update

- The live Toast receipt and frozen Java boundary were appended to `docs/reports/telnet-transport-authority.md`.
- Added only the real-socket `MooServerTest.dispatchesBinaryModeChunkWithoutLineTerminator` regression.
- The focused Java 25 test was proven red on committed production at the final property assertion (`MooServerTest.java:457`).
- Production now authorizes `ConnectionOption.BINARY`, applies it through the existing `ListenerControl`, retains binary state in `MooServer`, reads at most 1024 bytes per socket read, and either dispatches that binary chunk immediately or feeds the same bytes through the existing text/Telnet state machine.
- Closing the connection clears server-owned binary state. No new interface, helper, adapter, sender, alternate command path, or binary task model was introduced.
- The focused Java 25 test passed after the production edit in 8 seconds.
- The required post-green immutable-plan reread is incomplete: chunks 1 and 2 have been read, but the checkpoint hook fired before chunk 3. The final chunk is the next required action before formatting or managed Banteng verification.
- No blocker.

## Managed Banteng update

- The post-green plan reread completed after the prior checkpoint; `spotlessApply` and `installDist` then passed.
- The exact managed binary-mode row passed: 1 passed, 11504 deselected in 5.21 seconds.
- Its exact `moo_conformance_l173gal0` Java process was identified, stopped by PID, and followed by an empty Banteng inventory.
- The targeted `gap_followups_toast_oracle` run advanced to eight passing rows and then failed at the next independent row, `audit_output_order_disconnect`, in 27.78 seconds.
- That next row expected `audit-before-boot` followed by `*** Disconnected ***`, but observed only the disconnect message. It remains a separate slice after row 29 is committed.
- The targeted-run exact `moo_conformance_u3d4jy_q` Java process was identified, stopped by PID, and followed by an empty inventory.
- The required post-family immutable-plan reread is incomplete: chunks 1 and 2 have been read, but the checkpoint hook fired before chunk 3. The final chunk is the next required action.
- No blocker; row 29 has a measured kept reduction.
## Restart continuation after final gate

- The Java 25 `clean check installDist` gate passed in 20 seconds.
- The required post-gate reread of the immutable implementation plan initially had a truncated middle chunk during context compaction; after restart, the entire plan was reread from line 1 through EOF in four smaller, non-overlapping chunks.
- That reread confirms row 29 remains the active exact-convergence slice and must be recorded, inspected, and committed before beginning `audit_output_order_disconnect`.
- The existing binary-mode authority section already records the focused red/green result, exact managed Banteng pass, targeted-family first-eight pass, next-row failure, and exact disposable-process cleanup.
- A heading/receipt search found the binary-mode section at lines 464-576 and confirmed that it does not yet contain the final 20-second `clean check installDist` receipt. The following heading begins at line 577.
- No new production or test command has been run after the clean gate. No current blocker exists.
- Next action: append the final 20-second gate receipt to the binary-mode authority section, then run `git diff --check`, inspect the exact five-file diff/status, stage only those tracked files, inspect the index, and commit the kept row-29 slice.
## 2026-07-15 row 30 Barn authority subagent start

- Delegated read-only task: trace `audit_output_order_disconnect` from its durable conformance row through the normative Barn spec and current Barn implementation.
- No row-30 files have been inspected yet in this delegated turn, so there are no findings or observations yet.
- The delegated task has no known blocker. It must not edit production, tests, plans, or authority reports.

## 2026-07-15 row 30 Barn authority checkpoint

- Durable row: `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:665-748`. It installs a player command whose verb calls `notify(player, "audit-before-boot")` and then `boot_player(player)`. The exact expected connection output is `audit-before-boot` followed by `*** Disconnected ***`.
- Barn normative text is inconsistent and does not explicitly state that relative ordering. `spec/login.md:191-219` says output is buffered until task end and gives boot sequence disconnect-message, hook, close. `spec/server.md:179-185` says pending output is discarded when the connection closes. `spec/builtins/network.md:67-88` is terse; `spec/builtins/server.md:254-277` lists hook, close, boot message in a different order.
- Current implementation entry points found: `builtins/network.go:833-871` sends ordinary `notify` immediately with `Connection.Send`; `builtins/network.go:1053-1077` authorizes and delegates `boot_player` to `ConnectionManager.BootPlayer`.
- Current semantic owner found: `server/connection_manager.go:550-562` sends `*** Disconnected ***` then closes. `server/connection.go:73-77` delegates `Send` to transport `WriteLine`; `server/connection.go:117-120` cancels and closes the transport.
- The exact transport/write concurrency path and why the managed row observed only the disconnect line still need to be traced. No blocker.

## 2026-07-15 row 30 Barn implementation trace checkpoint

- `server/transport.go:212-226` proves Barn TCP `WriteLine` serializes on a mutex, appends CRLF, and flushes the buffered writer before returning; `Close` then closes the socket. Thus ordinary `notify` and the boot banner are synchronous writes, not task-end deferred output.
- Exact row call order in current Barn: `builtinNotify` -> `Connection.Send("audit-before-boot")` -> `TCPTransport.WriteLine` and flush; then `builtinBootPlayer` -> `ConnectionManager.BootPlayer` -> `Connection.Send("*** Disconnected ***")` -> second `WriteLine` and flush -> `Connection.Close` -> transport close.
- `server/input_processor.go:315-366` performs manager-map removal and calls `user_client_disconnected` after the closed connection produces its disconnect event. This occurs after both row-visible writes and is not part of their relative order.
- Important distinction: the managed failure that emitted only `*** Disconnected ***` was Banteng’s behavior, not a Barn execution result. Current Barn source agrees with the durable row’s two-line order.
- Remaining checks: locate builtin registration/public name, confirm command task dispatch does not introduce another output buffer, and identify any tests that codify this exact order. No blocker.

## 2026-07-15 row 30 Barn authority final checkpoint

- Public names are registered directly in `builtins/registry.go:189-203`: `notify` -> `builtinNotify`, `boot_player` -> `builtinBootPlayer`.
- `server/server.go:93-121` wires the scheduler's ordinary task-line sender to immediate `Connection.Send` and reserves `Connection.Flush` for the explicit connection output buffer at task finalization. In the durable row, `notify(player, message)` omits `no_flush`, so `builtinNotify` selects immediate `Send`; task-end flushing does not reorder the line.
- No Barn test was found that directly asserts `notify` output precedes the `boot_player` banner. Existing tests include only a stub `BootPlayer` at `builtins/network_test.go:91-94` and shutdown-banner/close coverage at `server/connection_manager_test.go:515+`.
- Evidence conclusion: current Barn implementation agrees with the durable row via two synchronous, sequential transport flushes followed by close. The normative Barn spec is ambiguous/internally contradictory on this exact order and should not alone be cited as proving it.
- No blocker. The delegated investigation is complete except for a final current commit/status identity read.
