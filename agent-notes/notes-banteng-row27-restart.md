# Banteng row 27 checkpoint

## Ledger

- Row 26 `audit_telnet_iac_delivered_as_oob_command` is committed as `1d1bfff` (`feat: dispatch telnet negotiation as out of band input`).
- After that commit the tracked worktree was clean; only pre-existing notes and `pyghidra_mcp_projects/` were untracked.
- Row 26 passed its focused regression, exact managed row, five-row measured family frontier, `git diff --check`, and Java 25 `clean check installDist`.

## Active target

- The only active exact-convergence target is `audit_telnet_subnegotiation_delivered_across_reads` in `../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:412-488`.
- The durable row installs a listener `do_out_of_band_command` recorder, opens a fresh connection, and sends four separately flushed fragments: `FF`, `FA 1F 00 50 00 18`, `FF`, and `F0`.
- After 500 ms it requires exact `args` and `argstr` for `~FF~FA~1F~00P~00~18~FF~F0`.
- The preceding targeted family run observed an empty recorder on committed row-26 Banteng.

## Authority work in progress

- Read-only Codex subagents are tracing the normative Barn/current Barn path, pinned Toast path, and current committed Banteng path independently.
- No production or test edit is authorized until those traces, the repository-local evidence record, and the live pinned-Toast row are complete.

## Blockers

- None. The mandatory authority gate is incomplete and is the current required work, not a blocker.

## Authority gate completed

- Independent Barn, pinned-Toast, and current-Banteng traces completed without project source edits.
- `docs/reports/telnet-transport-authority.md` now contains the normative Barn spec absence; current Barn and pinned Toast receive, parser, escaping, OOB queue, dispatch, and task-context paths; agreements and disagreements; the current Banteng failure path; and the exact durable row.
- The live managed row passed pinned WSL Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`: 1 passed, 11504 deselected in 6.65 seconds.
- WSL inventory found no process from that managed run. A matching `/tmp/td.db` Toast process was dated July 13 and was left untouched as unrelated.
- The frozen smallest design is local to `MooServer.handleConnection`: retain the raw subnegotiation frame and whether its prior byte was IAC, exclude all retained bytes from ordinary line input, render the completed frame inline with Toast's binary rule, and call the existing concrete OOB runtime operation once. No new helper, type, adapter, sender, or runtime API is authorized.

## Red regression

- Added only `MooServerTest.dispatchesTelnetSubnegotiationSplitAcrossWritesAsOutOfBandInput`, using the durable row's four flushed writes and final 500 ms observation wait.
- The focused Java 25 Gradle run failed on committed production at `MooServerTest.java:387`, proving the regression red before the server edit.
- No blocker. The next exact action is the single owned `MooServer.handleConnection` local-state implementation.

## Managed verification update

- The owned `MooServer.handleConnection` edit added only local subnegotiation buffer/state, inline Toast binary rendering, and the existing concrete OOB dispatch call.
- The focused regression passed under Java 25 in 3 seconds.
- The complete immutable plan was reread in three chunks after that passing focused test.
- `spotlessApply` and `installDist` passed.
- The exact managed Banteng row passed: 1 passed, 11504 deselected in 6.57 seconds. Its exact temporary-database Java process was identified, stopped, and followed by an empty Banteng inventory.
- The targeted `gap_followups_toast_oracle` run advanced to six passing rows and then failed at the next independent row, `audit_gmcp_subnegotiation_delivered_across_reads`, in 24.66 seconds.
- That GMCP row expected two parsed arguments, `~FF~FA~C9Core.Hello` and `{client:audit}~FF~F0`, but Banteng supplied the entire command as one argument; `argstr` already matched exactly.
- The targeted-run Banteng process has not yet been inventoried or stopped because the checkpoint hook fired immediately after the result. Exact cleanup is the next required action.
- No blocker. Row 27 has a measured kept reduction; GMCP remains a separate slice after row 27 is gated and committed.

## Final gate update

- The targeted-run Banteng process was identified by its exact `moo_conformance_i2q_42zw` database command line, stopped by PID, and followed by an empty inventory.
- The complete immutable plan was reread after the targeted family run.
- Row-27 focused, exact-managed, family-frontier, and process-cleanup receipts were appended to `docs/reports/telnet-transport-authority.md`.
- Java 25 `clean check installDist` passed in 14 seconds.
- The complete immutable plan was reread again after that passing full Gradle suite.
- The clean-gate receipt was appended to the authority record.
- Row 27 is ready for exact diff review, `git diff --check`, staging of only its three tracked files, and commit.
- There is no current blocker.
