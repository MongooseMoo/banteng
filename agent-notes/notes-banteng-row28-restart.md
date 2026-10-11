# Banteng row 28 checkpoint

## Ledger

- Row 27 `audit_telnet_subnegotiation_delivered_across_reads` is committed as `81bc3bb` (`feat: parse telnet subnegotiation as out of band input`).
- Tracked state was clean after that commit; all notes and `pyghidra_mcp_projects/` remained untracked.

## Active target

- The only active exact-convergence target is `audit_gmcp_subnegotiation_delivered_across_reads` at `../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:490-570`.
- The transport frame and `argstr` already match. The targeted family expected two arguments, `~FF~FA~C9Core.Hello` and `{client:audit}~FF~F0`, but Banteng supplied the entire escaped command as one argument.

## Authority state

- Independent read-only Barn, pinned-Toast, and current-Banteng traces completed.
- The row is generic OOB command-word tokenization, not GMCP or JSON parsing: whitespace splits words outside quotes, quotes are removed from `args`, backslash escapes consume the following character, and `argstr` remains unchanged.
- Barn and Toast agree on the asserted arguments and `argstr`; they differ outside the row on raw versus already-escaped internal strings and on listener task `caller`.
- The exact mismatch owner is `MooRuntime.executeTransportOutOfBand`, which hard-codes a singleton `args` list. Transport and generic `verbLocals` are already correct.
- `MooRuntime.executeLine` already has an inline word scan with the frozen semantics.
- `docs/reports/telnet-transport-authority.md` now contains the row-28 spec absence, Barn and Toast source traces, agreements/disagreements, and current Banteng failure path.

## Next step and blocker

- The next required action is the exact live pinned-Toast row. Only after it passes may the smallest Java representation and focused regression be frozen.
- No blocker; the mandatory authority gate is in progress.

## Oracle and implementation update

- The exact managed row passed pinned WSL Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`: 1 passed, 11504 deselected in 7.23 seconds.
- WSL inventory found no process from that run; only the unrelated July 13 `/tmp/td.db` process remained and was left untouched.
- The frozen smallest design is inline command-word scanning inside `MooRuntime.executeTransportOutOfBand`, replacing only the singleton `args` preparation while leaving exact `argstr`, transport, and task context unchanged. No helper/parser abstraction/interface/adapter/sender is authorized.
- The live receipt and design are recorded in `docs/reports/telnet-transport-authority.md`.
- Added only `MooRuntimeTest.parsesTransportOutOfBandWordsWithoutChangingArgstr` at the semantic owner.
- The focused test was proven red on committed production at `MooRuntimeTest.java:1333`.
- The owned runtime edit added the inline word scan and replaced only the singleton `ListValue` contents.
- The same focused test then passed under Java 25 in 3 seconds.
- The required post-green immutable-plan reread is incomplete: chunks 1 and 2 have been read, but the checkpoint hook fired before chunk 3. The final chunk is the next required action before formatting or managed Banteng verification.
- No blocker.

## Managed Banteng update

- The post-green plan reread completed after the prior checkpoint, then `spotlessApply` and `installDist` passed.
- The exact managed Banteng GMCP row passed: 1 passed, 11504 deselected in 7.02 seconds.
- Its exact `moo_conformance_2_95bmzi` Java process was identified, stopped by PID, and followed by an empty Banteng inventory.
- The targeted `gap_followups_toast_oracle` run advanced to seven passing rows and then failed at the next independent row, `audit_binary_mode_dispatches_raw_chunk_without_newline`, in 26.62 seconds.
- That next row expected `{\"\"}` and observed `{}`; it remains a separate slice after row 28 is fully committed.
- The targeted-run exact `moo_conformance_mfpgiio2` Java process was identified, stopped by PID, and followed by an empty inventory.
- The required post-family immutable-plan reread is incomplete: chunks 1 and 2 have been read, but the checkpoint hook fired before chunk 3. The final chunk is the next required action.
- No blocker; row 28 has a measured kept reduction.

## Final gate and staging update

- The post-family immutable-plan reread was completed after the checkpoint.
- Row-28 focused, exact-managed, seven-row frontier, and cleanup receipts were appended to `docs/reports/telnet-transport-authority.md`.
- Java 25 `clean check installDist` passed in 19 seconds.
- The complete immutable plan was reread again after that passing full Gradle suite.
- The clean-gate receipt was appended to the authority record.
- `git diff --check` passed, and the exact diff review confirmed only the authority record, `MooRuntime.executeTransportOutOfBand`, and its focused runtime regression.
- Exactly those three tracked files are staged. All notes and `pyghidra_mcp_projects/` remain untracked.
- Cached diff check, cached name verification, commit, and clean tracked-state verification remain.
- No blocker.
