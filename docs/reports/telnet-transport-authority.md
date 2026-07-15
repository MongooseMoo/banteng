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

## Two-byte IAC command split across reads

The next durable row is
`../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:251-306`,
`audit_telnet_two_byte_command_delivered_across_reads`. It sends `FF`, then
separately sends `F1` without a line terminator on an unauthenticated
connection. The accepting listener's `do_out_of_band_command` records `args`
and `argstr`. The required observable value is
`{{"~FF~F1"}, "~FF~F1"}`.

Barn's normative `../barn/spec/server.md:129-140,165-170,193-204` describes
Telnet-style input but does not define Telnet OOB framing, split-read command
state, binary escaping, the OOB hook, or its task locals.

Barn's `../barn/server/transport.go:51-70,85-151` retains `tState` and
`tCommand` per connection. `FF` enters IAC state and retains the partial raw
command while input blocks or refills; a later `F1` completes raw bytes
`{FF,F1}` and returns them as OOB immediately, without CR, LF, or a third byte.
Barn's Telnet formatter at lines 190-192 is an identity conversion.
`../barn/server/input_processor.go:104-159` queues the command and OOB bit for
the negative connection. OOB dispatch at lines 196-229 precedes held-input,
`read()`, pre-login, and ordinary command routing. Fresh options have
`disable-oob = 0` and `binary = 0` at
`../barn/builtins/network.go:189-213,245-248`.

`../barn/server/input_processor.go:232-257` calls the accepting listener's
`do_out_of_band_command` with the raw command as both its sole argument and
`argstr`; `../barn/command/command.go:114-165` treats raw `FF F1` as one word.
`../barn/scheduler/call_verb.go:25-134` supplies listener `this`, negative
connection `player` and `caller`, hook-owner permissions, raw-byte `args`, and
raw-byte `argstr`. The managed listener is `#0` through
`../barn/cmd/barn/main.go:354-370` and
`../barn/server/listen_spec.go:12-16`. Barn renders those raw nonprintable bytes
as uppercase `~HH` at `../barn/types/str.go:97-125`, producing the row's
observable `~FF~F1`. Barn's transport tests at
`../barn/server/transport_test.go:95-144` do not split this two-byte command
across writes.

Pinned Toast keeps `telnet_state`, `command_stream`, and `telnet_cmd` on each
connection handle at `/root/src/toaststunt/src/network.cc:81-124,575-627`.
The network loop reuses that handle at lines 1437-1476. Each `pull_input` call
uses a fresh local OOB stream but feeds bytes through the persistent parser at
lines 472-572. The first `FF` enters IAC state and remains only in the
persistent command stream at lines 401-407, so it emits no task. A later `F1`
takes the simple-command branch at lines 420-436, copies both accumulated
bytes to that read's OOB stream, returns to normal, and delivers immediately.
Negotiation commands require an option byte and subnegotiations require IAC SE;
those branches remain separate.

Unlike Barn, Toast converts the completed raw command to printable binary form
before the MOO task. `/root/src/toaststunt/src/utils.cc:671-684` renders each
non-graph byte as uppercase `~%02X`, so `FF F1` becomes exact ASCII
`~FF~F1`. `/root/src/toaststunt/src/network.cc:561-563` passes that OOB string
to `/root/src/toaststunt/src/server.cc:1452-1478,1534-1541`, which retains the
negative connection and accepting listener. OOB classification and queueing at
`/root/src/toaststunt/src/tasks.cc:55-64,431-475,1074-1122` bypass in-band and
login dispatch at lines 1628-1689. `do_out_of_band_command` at lines 969-974
calls the listener with `parse_into_wordlist(command)` and the exact command as
`argstr`; `/root/src/toaststunt/src/parse_cmd.cc:35-79,108-124` produces the
one-element argument list. Root server-task setup at
`/root/src/toaststunt/src/execute.cc:3278-3336` supplies listener `this`,
negative connection `player`, `caller = #-1`, hook-owner permissions,
`{"~FF~F1"}` arguments, and `"~FF~F1"` argstr.

Barn and Toast agree on the asserted observation: connection-persistent IAC
state, no task for lone `FF`, immediate OOB dispatch when later `F1` completes
the command, listener ownership, negative connection `player`, and displayed
`args[1]`/`argstr` equal to `~FF~F1`. They disagree outside the row on whether
binary escaping happens before task creation and whether `caller` is the
negative connection or `#-1`.

Committed Banteng `bbf9c4f` retains `afterIac` across reads but consumes every
non-negotiation second byte, so `FF F1` produces no runtime operation. The
targeted family observed the initial empty property. Ordinary
`MooRuntime.executeLine` cannot be reused: negative connections enter login
before textual OOB detection, and `~FF~F1` is not the textual OOB prefix. The
current runtime already constructs the required listener OOB frame for textual
OOB input, but the managed pinned-Toast row must now be rerun before choosing
the concrete transport-to-runtime operation or focused regression. No Java
design is frozen by this source trace.

The exact managed row passed pinned WSL Toast commit
`aecc51e9449c6e7c95272f0f044b5ba38948459e`: one selected, 11,504
deselected, in 5.63 seconds. Immediate split-read delivery with exact ASCII
`~FF~F1` in `args[1]` and `argstr` is therefore frozen before Java design.

The smallest Java boundary is one synchronized transport-OOB operation on the
existing concrete `MooRuntime`. `MooServer` owns the completed Telnet bytes but
cannot execute stored verbs; `MooRuntime` already owns the connection record,
listener identity, verb lookup, locals, and output journal. The operation
requires the existing connection, records activity, reads its current negative
player, looks up the accepting listener's `do_out_of_band_command`, and invokes
it with listener `this`, negative connection `player`, `caller = #-1`, one
`"~FF~F1"` argument, and the same `argstr`. A missing hook yields no output.

The existing `MooServer.handleConnection` IAC branch retains `FF FF` omission
and negotiation-option consumption. When `F1` completes the active command, it
immediately calls the concrete runtime operation with literal `~FF~F1` and
writes the returned ordered lines without waiting for CR or LF. This adds no
class, interface, helper, adapter, sender, general binary encoder, alternate
runtime path, subnegotiation state, or egress rule. Other simple Telnet
commands and larger OOB payloads remain outside this row.

The focused real-socket regression installed only the listener OOB recorder,
flushed `FF` and `F1` separately without a line terminator, used the durable
row's 500 ms wait, and inspected the retained world property. Committed
production was red with `{}` instead of `{{"~FF~F1"}, "~FF~F1"}`. After the
concrete runtime operation and `F1` branch were added, the focused regression
passed under Java 25 in 5 seconds.

The exact managed Banteng row passed with one selected and 11,504 deselected
in 5.51 seconds. Its disposable process was identified by exact temp-database
command line, stopped by PID, and followed by an empty Banteng inventory. The
full `gap_followups_toast_oracle` fail-fast run then passed the first four
selected rows and stopped at the next independent contract, three-byte IAC
negotiation OOB delivery, after 17.67 seconds. That row expected
`{{"~FF~FB~01"}, "~FF~FB~01"}` and observed no hook call; it remains a
separate slice.

The final Java 25 `clean check installDist` gate passed in 13 seconds.
