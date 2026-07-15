# Telnet transport authority

This report records the authority gate for Banteng's byte-oriented Telnet
transport slices. Each slice remains separate and must add its own source and
managed-oracle receipt before production design.

## Escaped IAC split across reads

The active durable row is
`../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:189-249`,
`audit_telnet_escaped_iac_stripped_from_login_input`. It opens an unauthenticated
connection and sends four chunks: ASCII `iac-`, byte `FF`, byte `FF`, and ASCII
`-login` followed by CRLF. The accepting listener records `do_login_command`
`args` and `argstr`. The required result is `{{"iac--login"}, "iac--login"}`:
the parser state must survive separate socket reads, and the entire `FF FF`
pair must be omitted from ordinary input.

### Barn specification and implementation

Barn's normative `../barn/spec/server.md:189-200` describes TCP/TLS as
Telnet-style line input but does not define escaped-IAC behavior, parser state,
or read-boundary semantics. The active contract therefore cannot be derived
from Barn prose.

Barn's connection-owned transport state at
`../barn/server/transport.go:51-61` contains `tState`, `tCommand`, `lineBuf`, and
`lastWasCR`. `TCPTransport.ReadInput` at
`../barn/server/transport.go:85-140` reads incrementally. The first `FF` leaves
the connection in `telnetStateIAC`; a later `FF` takes the escaped-IAC branch,
returns to the normal state, and appends neither byte to the line. The branch's
comment calls the input a literal `0xFF`, but that comment disagrees with the
executable behavior. Printable bytes append at lines 123-130, so the retained
text is `iac--login`. CR completes that line at lines 107-112, and the
immediately following LF is suppressed at lines 113-122.

`../barn/server/input_processor.go:119-159,203-229,368-415` enqueues the
ordinary line unchanged and routes a negative connection through pre-login
processing. `dispatchLoginCommand` at lines 423-464 passes
`command.CommandWordList(line)` as the arguments and the unchanged line as
`argstr`. `../barn/command/command.go:153-165` produces the one-word list, and
`../barn/scheduler/task_factory.go:124-151` stores that list and original line
in the login task. Barn therefore agrees exactly with the durable assertion.
Its tests at `../barn/server/transport_test.go:62-144` cover CRLF suppression
and persistent mid-line Telnet state, but not the split `FF FF` branch.

### Pinned Toast implementation

Pinned WSL Toast source identity is
`aecc51e9449c6e7c95272f0f044b5ba38948459e`. In
`/root/src/toaststunt/src/network.cc:81-117`, each `nhandle` owns persistent
input, `last_input_was_CR`, and `telnet_state`. New handles initialize that
state once at lines 600-627. The network loop reuses the handle across
`pull_input` calls at lines 1445-1479; `pull_input` reads chunks at lines
471-545 and feeds each nonbinary byte to `process_telnet_byte` at lines
546-566. Socket-read boundaries therefore do not reset either partial text or
Telnet state.

`process_telnet_byte` at `/root/src/toaststunt/src/network.cc:385-469` appends
ordinary graph characters, spaces, and tabs to the in-band stream. The first
`FF` changes from `NORMAL` to `IAC` and records that byte only in the Telnet
command stream at lines 402-406. A following `FF`, including one received by a
later read, appends to that command stream and returns to `NORMAL` at lines
420-423. This branch copies neither byte into the in-band stream and does not
deliver the pair as out-of-band input. Single-byte commands, negotiations, and
subnegotiations take distinct branches at lines 425-465. CR queues and resets
the retained `iac--login` line; the following LF is suppressed.

`/root/src/toaststunt/src/server.cc:1534-1541` passes the in-band line to
`new_input_task`. `/root/src/toaststunt/src/tasks.cc:1075-1129,1171-1180`
queues the unchanged C string as in-band input. `run_ready_tasks` at lines
1648-1770 selects `do_login_task` for the negative pre-login connection.
`do_login_task` at lines 878-916 passes `parse_into_wordlist(command)` as
`args` and the same `command` as `argstr`; the word-list owner is
`/root/src/toaststunt/src/parse_cmd.cc:108-123`. The final values are therefore
`{"iac--login"}` and `"iac--login"`. Binary-mode connections bypass this
Telnet path at `network.cc:546-550`; that adjacent mode is outside this row.

### Committed Banteng baseline and unresolved design

The first full managed stock-profile run after lifecycle convergence passed 48
rows, then failed this row in 69.80 seconds. The harness encountered a raw
`FF` in returned data and could not decode that line as UTF-8. That later
decode failure is evidence of Banteng's byte leak, not the semantic authority
for what `FF FF` should mean.

Committed `src/main/java/moo/server/MooServer.java:101-128` uses an
ISO-8859-1 `BufferedReader.readLine()` directly and owns no Telnet parser or
connection-local Telnet state. The split `FF` chunks therefore become two
`U+00FF` characters. `src/main/java/moo/runtime/MooRuntime.java:707-745` and
its Latin-1 encoder at lines 1336-1337 preserve the already-wrong line;
`src/main/java/moo/value/MooValue.java:143-183` is not the source of the input
error. `MooServer.writeLines` at lines 143-149 can then emit exposed `U+00FF`
as raw `FF`, which explains the later harness symptom.

Barn and Toast agree on every observation asserted by the row: parser state is
connection-local and persists across reads, `FF FF` is consumed completely,
the surrounding text joins as `iac--login`, and login receives that exact
one-word argument and `argstr`. Barn prose is silent, and Barn's literal-IAC
comment is contradicted by both implementations. The managed pinned-Toast row
must now be rerun and recorded before choosing Banteng's byte representation,
focused regression, or egress behavior. No Java design is frozen by this
source trace.

The exact managed row passed pinned WSL Toast commit
`aecc51e9449c6e7c95272f0f044b5ba38948459e`: one selected, 11,504
deselected, in 6.61 seconds. The split-read `FF FF` omission and resulting
`{{"iac--login"}, "iac--login"}` login context are therefore frozen before
Java design.

The smallest Java representation replaces only the `BufferedReader` inside
the existing concrete `MooServer.handleConnection` owner with a byte input
loop. A byte line buffer plus CR, IAC, and negotiation-option state live for
the connection loop and therefore survive individual socket reads. In normal
state, `FF` enters IAC state without entering the line. A second `FF` returns
to normal and appends nothing. `FB`, `FC`, `FD`, or `FE` after IAC consumes the
following option byte; this preserves the already-kept preceding negotiation
row. CR completes one line, and its following LF is suppressed. Each completed
byte line is converted through ISO-8859-1 only at the existing
`runtime.executeLine` boundary.

This slice adds no transport class, interface, helper, adapter, sender, or
alternate runtime path. Complete Telnet commands, out-of-band delivery,
subnegotiation, binary mode, and output-byte quoting remain separate durable
rows. In particular, the current escaped-IAC row removes the pair before any
MOO value or output exists, so an egress rewrite would not be causal to this
failure.

The focused socket regression retained the real `WorldTxn`, installed a login
recorder, sent `iac-`, `FF`, `FF`, and `-login` plus CRLF as separately flushed
raw writes, and read the recorded world property directly. Committed production
was red with `{{"iac-ÿÿ-login"}, "iac-ÿÿ-login"}` instead of the frozen
`{{"iac--login"}, "iac--login"}`. After the inline byte-state change, the
focused regression passed under Java 25 in 2 seconds.

The exact managed Banteng row passed with one selected and 11,504 deselected
in 6.54 seconds. Its disposable database process was identified by exact
command line, stopped by PID, and followed by an empty Banteng process
inventory. The full `gap_followups_toast_oracle` fail-fast run then passed the
first three selected rows and stopped at the next independent contract,
two-byte IAC NOP out-of-band delivery, after 15.10 seconds. That next row
expected `{{"~FF~F1"}, "~FF~F1"}` and observed no hook call; it remains a
separate slice rather than being folded into escaped-IAC handling.

The final Java 25 `clean check installDist` gate passed in 13 seconds.
