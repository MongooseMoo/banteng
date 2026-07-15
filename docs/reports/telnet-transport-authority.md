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

## IAC negotiation command split across reads

The active durable row is
`../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:308-371`,
`audit_telnet_iac_delivered_as_oob_command`. It sends `FF`, waits, then sends
`FB 01` without a line terminator. The accepting listener records
`do_out_of_band_command` and must observe
`{{"~FF~FB~01"}, "~FF~FB~01"}`.

Barn's normative `../barn/spec/server.md:193-200` says only that TCP/TLS use
Telnet-style input; it does not define IAC negotiation, split-read state,
binary rendering, OOB delivery, or hook context.

Barn constructs one `TCPTransport` per socket at
`../barn/server/connection_manager.go:309-324`, and its connection-local
`tState` and `tCommand` live at `../barn/server/transport.go:51-61`.
`ReadInput` at lines 88-161 appends `FF` and enters IAC state, later appends
`FB` and enters command state without emitting, then appends option `01`,
formats all three retained bytes, resets state, and returns OOB immediately.
`formatTelnetCommand` at lines 190-192 deliberately returns the raw byte
string. Barn's focused unit at `../barn/server/transport_test.go:95-119`
expects raw `{FF,FB,01}` OOB but does not delay the writes.

The connection reader queues one OOB event at
`../barn/server/input_processor.go:119-193`. OOB processing precedes held input,
`read()`, and pre-login routing at lines 196-229; fresh connections have
`disable-oob = 0` at `../barn/builtins/network.go:189-212`.
`processOutOfBand` at `../barn/server/input_processor.go:232-257` resolves the
accepting listener, tokenizes the raw bytes as one word through
`../barn/command/command.go:114-165`, and calls `do_out_of_band_command` with
the raw command as both argument and `argstr`. The negative player mapping is
created at `../barn/server/connection_manager.go:63-75,390-400`.
`../barn/scheduler/call_verb.go:25-134` supplies listener `this`, negative
connection `player` and `caller`, and hook-owner permissions. Raw strings and
lists render the observable uppercase `~FF~FB~01` through
`../barn/types/str.go:97-125`, `../barn/types/value.go:162-187`, and
`../barn/scheduler/eval.go:184-203`.

Pinned Toast retains `telnet_state` and `command_stream` on the connection at
`/root/src/toaststunt/src/network.cc:81-124`. `process_telnet_byte` stores `FF`
and enters IAC state at lines 403-406, stores later `FB` and enters command
state without emission at lines 420-430, then stores option `01`, emits the
complete command, and returns to normal at lines 439-445. `pull_input` at lines
471-565 uses a per-read OOB stream but the persistent handle, so the first read
emits nothing and completion emits once. `/root/src/toaststunt/src/utils.cc:671-684`
encodes the three nonprintable bytes as exact uppercase ASCII `~FF~FB~01`.

Toast hands that OOB string through
`/root/src/toaststunt/src/server.cc:1452-1475,1534-1541` and classifies and
dispatches it directly at `/root/src/toaststunt/src/tasks.cc:431-475,969-974,986-998,1074-1095,1650-1690`.
`/root/src/toaststunt/src/parse_cmd.cc:33-79,108-124` yields the one-element
argument list. Root task setup at `/root/src/toaststunt/src/execute.cc:3278-3336`
supplies listener `this`, negative connection `player`, `caller = #-1`, exact
ASCII argument and `argstr`, and hook-owner permissions.

Barn and Toast agree on all asserted behavior: connection-persistent state,
no emission after `FF` or `FF FB`, immediate single OOB emission when option
`01` arrives, accepting-listener ownership, negative connection `player`, and
observable `~FF~FB~01` argument and `argstr`. As in the preceding row, they
disagree outside the assertion on escape timing and `caller`.

Committed Banteng `a2ae743` retains only a boolean `afterNegotiation`. After
`FF FB`, option `01` clears that boolean and is discarded, so the already
correct `MooRuntime.executeTransportOutOfBand` is never called and the targeted
family observed `{}`. The pinned managed Toast row must now be rerun before
freezing how the existing local state retains the negotiation command. No Java
design is frozen by this source trace.

The exact managed row passed pinned WSL Toast commit
`aecc51e9449c6e7c95272f0f044b5ba38948459e`: one selected, 11,504
deselected, in 5.69 seconds. Third-byte completion and exact
`~FF~FB~01` OOB delivery are therefore frozen before Java design.

The smallest Java representation changes only existing local state in
`MooServer.handleConnection`: replace the negotiation boolean with an integer
holding the `FB` through `FE` command byte, initialized to `-1`. When the next
option byte arrives, capture and reset that integer, format retained IAC,
command, and option as uppercase `~FF~%02X~%02X`, and immediately call the
already-committed `MooRuntime.executeTransportOutOfBand` operation. This
preserves `FF FF` omission and the two-byte `FF F1` branch while adding no
runtime method, class, interface, helper, adapter, sender, subnegotiation state,
or egress behavior.

The focused real-socket regression retained the accepting listener's OOB
recorder, flushed `FF`, waited 100 ms, flushed `FB 01`, waited 500 ms, and
inspected the retained world property. Committed production was red with `{}`
instead of `{{"~FF~FB~01"}, "~FF~FB~01"}`. Retaining the negotiation command
byte across reads made the focused regression pass under Java 25 in 3 seconds.

The exact managed Banteng row passed with one selected and 11,504 deselected
in 5.59 seconds. Its disposable process was identified by exact temp-database
command line, stopped by PID, and followed by an empty Banteng inventory. The
full `gap_followups_toast_oracle` fail-fast run then passed the first five
selected rows and stopped at the next independent contract, split-read Telnet
subnegotiation delivery, after 20.73 seconds. That row expected
`{{"~FF~FA~1F~00P~00~18~FF~F0"}, "~FF~FA~1F~00P~00~18~FF~F0"}` and observed
no hook call; it remains a separate slice.

The final Java 25 `clean check installDist` gate passed in 14 seconds.

## IAC subnegotiation split across writes

The active durable row is
`../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:412-488`,
`audit_telnet_subnegotiation_delivered_across_reads`. It replaces the accepting
listener's `do_out_of_band_command` with an `args`/`argstr` recorder, opens an
unlogged connection, and performs four separately flushed writes: `FF`,
`FA 1F 00 50 00 18`, `FF`, and `F0`. After completion it requires the one
argument and identical `argstr` to be
`~FF~FA~1F~00P~00~18~FF~F0`. There are no waits between the four writes, so
the assertion is independent of TCP packet or read boundaries rather than a
claim that each write becomes a distinct socket read. The final 500 ms wait
only permits the resulting MOO task to update the recorder.

Barn's normative `../barn/spec/server.md:193-200` says only that TCP and TLS
listeners use Telnet-style line input. The Barn spec does not define
subnegotiation framing, cross-write parser state, binary rendering, OOB
delivery, or the hook task context.

Barn creates one `TCPTransport` for each accepted socket at
`../barn/server/connection_manager.go:274-329`. Its persistent `tState` and
`tCommand` fields and the normal, IAC, command, subnegotiation, and
subnegotiation-IAC states are at `../barn/server/transport.go:13-33,51-70`.
`ReadInput` at lines 85-188 initializes the retained command on `FF`, enters
subnegotiation on `FA`, appends every payload byte, enters the terminating
state on the later `FF`, and only on `F0` returns the entire retained frame as
one OOB value and resets the state. No earlier fragment emits OOB or enters
ordinary line input. `formatTelnetCommand` at lines 190-192 returns the raw
byte string unchanged.

Barn's connection reader uses the unlogged negative connection identity and
queues the raw OOB value at `../barn/server/input_processor.go:65-160`. OOB
dispatch precedes held-input, `read()`, login, and ordinary command routing at
lines 196-230. `processOutOfBand` at lines 232-257 resolves the accepting
listener and calls `do_out_of_band_command` with the one tokenized raw string
and identical raw `argstr`. `../barn/command/command.go:114-165` produces the
one-element word list. `../barn/scheduler/call_verb.go:25-134` supplies the
listener as `this`, the negative connection as `player` and `caller`, and the
hook owner's permissions. Barn retains raw string bytes and renders bytes
outside printable ASCII as uppercase `~XX` through
`../barn/types/str.go:21-24,64-69,97-125`; printable `50` therefore appears as
`P`.

Pinned Toast retains `telnet_state`, `command_stream`, and `telnet_cmd` on each
connection handle at `/root/src/toaststunt/src/network.cc:81-124`. Its
`process_telnet_byte` at lines 385-461 initializes the command on `FF`, enters
subnegotiation on `FA`, appends every payload byte, enters the
subnegotiation-IAC state on the later `FF`, and only on `F0` copies the entire
retained frame to that read's OOB stream and returns to normal. `pull_input` at
lines 464-565 reuses that persistent handle across reads and submits the OOB
stream once after processing the available bytes. Ordinary input receives no
part of the frame.

Toast's `/root/src/toaststunt/src/utils.cc:671-684` renders raw bytes other
than graphical characters and spaces as uppercase `~%02X`; the printable
`0x50` remains `P`. `/root/src/toaststunt/src/server.cc:1452-1475,1534-1541`
retains the negative connection and accepting listener and queues the rendered
OOB string. `/root/src/toaststunt/src/tasks.cc:431-475,969-974,1074-1122,1650-1690`
classifies it as OOB, dispatches it before login, and calls the listener with
`parse_into_wordlist(command)` and the exact command as `argstr`.
`/root/src/toaststunt/src/parse_cmd.cc:33-79,108-124` produces the one-element
argument list. Root server-task setup at
`/root/src/toaststunt/src/execute.cc:3278-3336` supplies listener `this`, the
negative connection `player`, `caller = #-1`, and hook-owner permissions.

Barn and Toast agree on every asserted observation: connection-persistent
subnegotiation state, no emission before the exact `FF F0` terminator, one
complete OOB command after termination, no ordinary-line payload, accepting
listener ownership, negative connection `player`, one argument, identical
`argstr`, and exact uppercase display
`~FF~FA~1F~00P~00~18~FF~F0`. They disagree outside the row on whether binary
escaping occurs before task creation and whether `caller` is the negative
connection or `#-1`.

Committed Banteng `1d1bfff` has only ordinary line, CR/LF, IAC, and negotiation
command state in `MooServer.handleConnection`. `FF FA` discards `FA`; payload
bytes `1F 00 50 00 18` enter the ordinary line buffer; and `FF F0` discards
`F0`. No line or OOB operation executes before the row inspects the recorder,
so the targeted family observed `{}`. The existing concrete
`MooRuntime.executeTransportOutOfBand` already owns the required listener task
context. The pinned managed Toast row must now pass before any Java
representation or focused regression is frozen.

The exact managed row passed pinned WSL Toast commit
`aecc51e9449c6e7c95272f0f044b5ba38948459e`: one selected, 11,504
deselected, in 6.65 seconds. Complete split-write subnegotiation delivery and
exact `~FF~FA~1F~00P~00~18~FF~F0` argument and `argstr` are therefore frozen
before Java design. Process inventory found no process from this managed run;
one `/tmp/td.db` Toast process dated July 13 was unrelated and left untouched.

The smallest Java representation remains entirely local to the existing
`MooServer.handleConnection` byte loop. A nullable `ByteArrayOutputStream`
retains the subnegotiation frame beginning with `FF FA`, and one boolean records
whether the preceding retained byte was `FF`. While that buffer exists, every
byte is retained and excluded from ordinary line input. Exact `FF F0`
completion renders the retained bytes inline using Toast's rule: graphical
ASCII and space except `~` remain literal, and every other byte becomes
uppercase `~%02X`. The completed string is passed once to the already-committed
concrete `MooRuntime.executeTransportOutOfBand` operation, then the local state
is cleared. This adds no method, class, interface, helper, adapter, sender,
runtime surface, alternate queue, or egress behavior.

The focused real-socket regression installed only the accepting listener OOB
recorder, flushed the durable row's four byte groups separately, waited the
row's final 500 ms, and inspected the retained world property. Committed
production was red at the exact assertion. After the local parser state and
inline rendering were added, the focused regression passed under Java 25 in
3 seconds.

The exact managed Banteng row passed with one selected and 11,504 deselected
in 6.57 seconds. Its disposable process was identified by exact temp-database
command line, stopped by PID, and followed by an empty Banteng inventory. The
full `gap_followups_toast_oracle` fail-fast run then passed the first six
selected rows and stopped at the next independent contract, GMCP
subnegotiation argument parsing, after 24.66 seconds. Its `argstr` already
matched `~FF~FA~C9Core.Hello {"client":"audit"}~FF~F0`, but the row expected
two parsed arguments while Banteng supplied the entire command as one; it
remains a separate slice. The targeted-run disposable process was likewise
identified by exact temp-database command line, stopped by PID, and followed
by an empty Banteng inventory.

The final Java 25 `clean check installDist` gate passed in 14 seconds.

## GMCP OOB word parsing

The active durable row is
`../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:490-570`,
`audit_gmcp_subnegotiation_delivered_across_reads`. It sends one option-201
subnegotiation in five separately flushed writes: `FF`, `FA C9`,
`Core.Hello {"client":"audit"}`, `FF`, and `F0`. The required call frame is
two arguments, `~FF~FA~C9Core.Hello` and `{client:audit}~FF~F0`, plus unchanged
`argstr` `~FF~FA~C9Core.Hello {"client":"audit"}~FF~F0`.

Barn's normative `../barn/spec/server.md:193-200` specifies only Telnet-style
line input, `../barn/spec/builtins/network.md:222-235` specifies the
`disable-oob` option, and `../barn/spec/objects.md:258-269` describes `args`
and `argstr` generally. The Barn spec does not define GMCP, option 201,
subnegotiation framing, `do_out_of_band_command`, or its word parsing.

Barn's already-recorded transport path retains the complete raw frame across
reads and returns it as OOB at `../barn/server/transport.go:51-70,88-192`.
`../barn/server/input_processor.go:119-158,196-248` routes it before login,
passes the untouched raw frame as `argstr`, and builds `args` with
`command.CommandWordList`. `../barn/command/command.go:114-165` splits on
whitespace outside quotes, discards double quotes while preserving their
contents, and consumes backslash escapes. This is generic command-word
tokenization, not JSON parsing. The raw words are therefore
`FF FA C9 Core.Hello` and `{client:audit} FF F0`, while the raw `argstr` still
contains the JSON quotes. `../barn/scheduler/call_verb.go:25-133` supplies the
listener as `this` and the negative connection as both `player` and `caller`.
Barn renders the nonprintable raw bytes as uppercase `~XX` only when the
returned MOO values are displayed through `../barn/types/str.go:97-129`,
`../barn/types/value.go:162-185`, and `../barn/scheduler/eval.go:190-203`.

Pinned Toast retains and completes the frame at
`/root/src/toaststunt/src/network.cc:81-124,402-467,472-565` and renders the
raw bytes to exact escaped ASCII before task creation through
`/root/src/toaststunt/src/utils.cc:671-684`. OOB queueing and dispatch at
`/root/src/toaststunt/src/server.cc:1452-1475,1535-1541` and
`/root/src/toaststunt/src/tasks.cc:969-974,1075-1122,1683-1690` pass
`parse_into_wordlist(command)` as `args` and the unchanged escaped command as
`argstr`. `/root/src/toaststunt/src/parse_cmd.cc:33-79,108-124` splits spaces
outside quotes, removes quotes, and consumes backslash escapes. It therefore
produces exact arguments `~FF~FA~C9Core.Hello` and
`{client:audit}~FF~F0`, while preserving the quoted JSON in `argstr`. Root task
setup at `/root/src/toaststunt/src/execute.cc:3279-3336` supplies listener
`this`, negative connection `player`, `caller = #-1`, and hook-owner
permissions.

Barn and Toast agree on the asserted word boundaries, quote removal,
backslash handling, two argument strings, unchanged `argstr`, listener, and
negative player. They disagree outside the row on raw versus already-escaped
internal command strings and on negative-player versus `#-1` caller.

Committed Banteng `81bc3bb` already produces the exact completed escaped
command and exact `argstr`. The mismatch is solely
`MooRuntime.executeTransportOutOfBand`, which hard-codes `args` as a singleton
containing the entire command. `MooRuntime.executeLine` already contains the
same quote-aware, backslash-aware word scan required by Barn and Toast, but no
Java representation or reuse decision is frozen until the exact managed row
passes pinned Toast.

The exact managed row passed pinned WSL Toast commit
`aecc51e9449c6e7c95272f0f044b5ba38948459e`: one selected, 11,504
deselected, in 7.23 seconds. The exact two-word `args` and unchanged quoted
`argstr` are therefore frozen before Java design. Process inventory found no
process from this managed run; the unrelated July 13 `/tmp/td.db` process was
again left untouched.

The smallest Java change is confined to
`MooRuntime.executeTransportOutOfBand`. Replace its singleton argument
construction with the same inline scan already owned by `MooRuntime` for
ordinary command words: split whitespace outside quotes, omit quote
characters, consume a character following backslash, and retain all other
characters. Encode those local words into the existing `ListValue` while
passing the original command unchanged as `argstr`. This changes no transport
state, line dispatch, generic locals construction, verb lookup, task context,
or egress, and adds no method, class, interface, helper, parser abstraction,
adapter, or sender.

The focused runtime regression called the concrete transport-OOB operation
with the exact escaped GMCP command and inspected only recorded `args` and
`argstr`. Committed production was red at the exact assertion. After the local
word scan replaced the singleton argument, the focused regression passed under
Java 25 in 3 seconds.

The exact managed Banteng row passed with one selected and 11,504 deselected
in 7.02 seconds. Its disposable process was identified by exact temp-database
command line, stopped by PID, and followed by an empty Banteng inventory. The
full `gap_followups_toast_oracle` fail-fast run then passed the first seven
selected rows and stopped at the next independent contract, binary-mode chunk
dispatch without a newline, after 26.62 seconds. That row expected `{\"\"}`
and observed `{}`; it remains a separate slice. The targeted-run disposable
process was likewise identified by exact temp-database command line, stopped
by PID, and followed by an empty Banteng inventory.

The final Java 25 `clean check installDist` gate passed in 19 seconds.

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
