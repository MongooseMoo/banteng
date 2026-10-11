# Banteng row 26 restart checkpoint

## Current state

- This is a restarted Codex process continuing the immutable Banteng implementation plan.
- The active exact-convergence slice is `audit_telnet_iac_delivered_as_oob_command`; no other target is open.
- The row-26 authority gate is complete and recorded in `docs/reports/telnet-transport-authority.md` from the Barn spec and implementation, pinned Toast source, and a passing live pinned-Toast run.
- The focused Banteng regression was proven red before the production edit and is now green.
- The uncommitted production change keeps the Telnet negotiation command byte across reads and dispatches the completed three-byte sequence through the existing out-of-band runtime path as `~FF~FB~01`.
- The immutable plan has now been reread completely in three non-overlapping chunks after the prior process's single read was truncated.

## Observations

- The previous kept slices are committed: row 23 `6fdd142`, row 24 `bbf9c4f`, and row 25 `a2ae743`.
- Row 26 currently changes only `src/main/java/moo/server/MooServer.java`, `src/test/java/moo/server/MooServerTest.java`, and `docs/reports/telnet-transport-authority.md`.
- Ward was uninstalled by the user and remains entirely out of scope.
- Existing notes and `pyghidra_mcp_projects/` are unrelated untracked paths and must not be staged.

## What was tried

- The focused test `dispatchesTelnetNegotiationSplitAcrossReadsAsOutOfBandInput` failed on committed code with an empty listener result.
- Replacing the one-bit negotiation state with the pending Telnet command byte made that focused test pass without adding a helper, interface, adapter, or runtime method.

## Next required step

- Run `spotlessApply`, then `installDist`, the exact managed row, the targeted gap-followup family, the required plan reread, record receipts, run the clean Gradle gate, review the exact diff, and commit the kept row-26 slice.

## Blockers

- None currently.

## Managed verification update

- `spotlessApply` passed.
- `installDist` passed.
- The first exact managed-row attempt did not execute Banteng because the harness inherited Java 17 and rejected class-file version 69; process inventory confirmed no leftover server. This was corrected by exporting the required Java 25 `JAVA_HOME` and `PATH` to the same exact managed command.
- `audit_telnet_iac_delivered_as_oob_command` then passed: 1 passed, 11504 deselected in 5.59 seconds.
- The exact managed Banteng process for that run was identified by its temporary database and stopped.
- The targeted `gap_followups_toast_oracle` family advanced to five passing rows and then failed at `audit_telnet_subnegotiation_delivered_across_reads`, which expected `~FF~FA~1F~00P~00~18~FF~F0` but observed an empty recorder.
- The exact family-run Banteng process was identified and stopped; process re-inventory was empty.
- This is a measured kept reduction for row 26. The subnegotiation failure is the next frontier only after row 26 is fully recorded, gated, and committed.

## Final gate update

- The complete immutable plan was reread after the targeted family run.
- The managed Banteng receipts and next independent frontier were appended to `docs/reports/telnet-transport-authority.md`.
- Java 25 `clean check installDist` passed in 14 seconds.
- The complete immutable plan was reread again after that passing full Gradle suite.
- The clean-gate receipt was appended to the authority record.
- Row 26 is ready for exact diff review, `git diff --check`, staging of only its three tracked files, and commit.
- There is no current blocker.
