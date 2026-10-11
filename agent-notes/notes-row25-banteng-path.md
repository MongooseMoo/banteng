# Row 25 Banteng path trace

## Current assignment

- Read-only trace at committed Banteng commit `bbf9c4f` for exact conformance row `audit_telnet_two_byte_command_delivered_across_reads`.
- The committed byte loop currently consumes any non-negotiation Telnet command byte after `IAC`.
- The parent agent reports that the full targeted run therefore observed an empty hook result.
- Required trace surfaces: existing runtime/server out-of-band command detection and dispatch, binary escaping conventions, listener handler selection while the connection is still negative, handler `args` and `argstr`, and existing tests.
- Required result: identify the smallest extension to existing concrete owners. No new class, interface, helper, adapter, or sender.

## Boundaries

- Do not edit tracked files.
- Do not run servers.
- Do not inspect Barn or Toast.
- A focused real-socket regression is expected to be red in current committed state, but this subtask is trace-only.

## Current state and blocker

- Git is on `master`, ahead of `origin/master` by 14 commits, with no tracked changes. Only known notes and `pyghidra_mcp_projects/` are untracked.
- At commit `bbf9c4f`, `MooServer.handleConnection` owns the connection-local byte state as three locals: `afterCarriageReturn`, `afterIac`, and `afterNegotiation`, plus a `ByteArrayOutputStream` ordinary-line buffer.
- In the `afterIac` branch, every following command byte is consumed. Bytes `0xFB` through `0xFE` additionally enter one-byte negotiation-option state. Thus `IAC NOP` (`FF F1`) is discarded instead of emitted.
- `IAC IAC` is also discarded by the same branch, matching the prior escaped-IAC row's current behavior.
- `MooRuntime.executeLine` already detects textual `#$#` OOB, looks up `do_out_of_band_command` on `connection.listenerHandler`, and executes it with `this=listener handler`, current `player`, `caller=#-1`, verb name `do_out_of_band_command`, parsed string words as `args`, and the original line as `argstr`.
- The existing real-socket test `MooServerTest.stripsEscapedIacSplitAcrossReadsFromLoginInput` sets a `do_login_command` verb on listener `#0`, sends `FF` and `FF` in separate writes while unauthenticated, and verifies the pair is removed from normal login input.
- No focused `IAC NOP` real-socket regression exists in the committed test slice read so far.
- No current blocker. Next action is to inspect the exact binary OOB encoding expected by the row/report and the remaining runtime connection model so the recommendation names the smallest concrete-owner extension without inventing a new abstraction.

## Completed trace and recommendation

- The exact conformance row sends raw `FF` and raw `F1` in two separately flushed writes, sends no line terminator, and expects the listener's `do_out_of_band_command` to record `args={{"~FF~F1"}}` and `argstr="~FF~F1"`. Dispatch therefore must occur immediately when `F1` completes the pending `IAC` command.
- `MooRuntime.executeLine` cannot receive the encoded string through its ordinary path: before any textual OOB detection it obtains the connection player and routes a negative player directly to `executeLogin`. Even for an authenticated player, `~FF~F1` lacks the textual `#$#` marker. Passing a fabricated marker would corrupt the asserted argument and conflate transport provenance.
- The existing OOB frame contract is established by `MooRuntimeTest.dispatchesTextualOutOfBandInputToTheConnectionListener`: lookup on `ConnectionState.listenerHandler`; `this=listener handler`; `player=current connection player`; `caller=#-1`; `verb="do_out_of_band_command"`; parsed string words in `args`; original encoded command in `argstr`; stored-verb output returned.
- `MooRuntime.openConnection` creates `ConnectionState(listenerHandler, ...)`, and the world maps the negative connection ID as its current player. A transport OOB operation can therefore use the same handler and negative player before login; it must not call `executeLogin`.
- Banteng has no generic main-code `~XX` binary encoder to reuse. For this complete two-byte command, the transport representation is uppercase two-digit hex for both bytes, each prefixed by `~`: `FF F1` becomes exact Latin-1/ASCII `~FF~F1`.
- Smallest concrete-owner extension: add one public synchronized runtime operation whose semantic owner is transport OOB execution (for example `executeOutOfBand(long connectionId, String command)`). It should require the existing connection, get the current world connection player (negative is valid), resolve `do_out_of_band_command` on the stored listener handler, return empty if absent, and execute the verb with the established frame. For the active command payload, `args` is a one-element list containing the exact encoded command and `argstr` is the same string. This is a concrete runtime boundary, not a new class/interface/helper/adapter/sender.
- In the existing inline `MooServer.handleConnection` `afterIac` branch, keep current behavior for `IAC IAC` and `IAC` negotiation (`FB`-`FE` plus option). When a complete two-byte command is recognized (`F1` for the active NOP row), immediately call the runtime transport-OOB operation with `~FF~F1` and write its returned lines through the existing `writeLines`. Do not append either byte to the ordinary line buffer and do not wait for CR/LF.
- Focused red real-socket regression belongs beside `stripsEscapedIacSplitAcrossReadsFromLoginInput` in `MooServerTest`. Use the real Test.db/world and server, connect a Wizard control socket, install `#0.audit_nop_oob_seen` and listener `#0:do_out_of_band_command` that stores `{args, argstr}` (optionally notifies the negative `player` to synchronize), open an unauthenticated target socket, write/flush `0xFF`, then write/flush `0xF1`, and assert exact `{{"~FF~F1"}, "~FF~F1"}`. On `bbf9c4f` this is red because the `afterIac` branch consumes `F1` and never invokes the hook.
- Do not route through the existing textual `#$#` branch, change ordinary/forced-input handling, introduce a parser class, or alter `StringValue.toLiteral`; none is causal to this row.
- No blocker. No tracked file was edited and no server/test was run by this subagent.
