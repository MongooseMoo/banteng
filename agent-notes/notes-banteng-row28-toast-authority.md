# Row 28 Toast authority subtask

- Assigned read-only authority trace for `audit_gmcp_subnegotiation_delivered_across_reads`.
- Required evidence: pinned `/root/src/toaststunt` path from a completed Telnet GMCP frame through binary escaping, `parse_into_wordlist`, argument construction, and task context.
- No source files have been inspected yet in this restarted subagent.
- No findings or observed blockers yet.
- Next step: read the applicable reverse-engineering skill instructions, then inspect pinned Toast source without running a server or changing implementation.
- Concurrent checkpoint state: the note is now present; the authority trace remains unstarted and unblocked.

## Source-backed findings

- `src/network.cc:81-124` stores `telnet_state` and `command_stream` on the per-connection `nhandle`; initialization at lines 625-627 makes the state persist across separate socket reads.
- `src/network.cc:402-467`: `FF` starts IAC and resets/appends to the persistent command stream; `FA` enters subnegotiation; all payload bytes append; terminal `FF F0` copies the entire accumulated raw frame to the OOB stream and returns to normal.
- `src/network.cc:472-565`: each read feeds bytes through that persistent state, and only a nonempty completed OOB stream calls `server_receive_line(..., true)`.
- `src/utils.cc:671-684`: raw non-graph bytes and tilde become uppercase `~HH`, while graph characters and spaces remain literal. The frame becomes `~FF~FA~C9Core.Hello {"client":"audit"}~FF~F0`.
- `src/server.cc:1535-1541` passes the OOB marker to `new_input_task`; `src/tasks.cc:1075-1122` classifies Telnet input as `TASK_OOB`; `src/tasks.cc:1683-1690` dispatches it through `do_out_of_band_command`.
- `src/tasks.cc:969-974` passes `parse_into_wordlist(command)` as `args` and the unchanged escaped command as `argstr`.
- `src/parse_cmd.cc:33-79,108-124` splits at spaces outside quotes and removes quote characters. Exact args are `{"~FF~FA~C9Core.Hello", "{client:audit}~FF~F0"}`.
- `src/server.cc:1452-1475` assigns a fresh connection a negative player and its accepting listener as handler. `src/tasks.cc:969-974,1825-1870` invokes the handler's verb with that negative player. `src/execute.cc:3279-3336` establishes `this=handler/listener`, `player=negative connection object`, `caller=#-1`, `args` as above, and exact escaped `argstr`.
- The conformance row itself at `gap_followups_toast_oracle.yaml:490-550` expects those exact two args and exact argstr.
- Current blocker: none. Remaining work is to check Barn's corresponding parser path for any disagreement and report to the parent; no server run or Java design is needed.

## Barn comparison in progress

- `barn/server/transport.go:51-70` likewise stores Telnet state and the accumulated command on one transport across reads.
- `barn/server/transport.go:101-186` retains the complete raw subnegotiation frame through split reads and returns it only at terminal IAC SE.
- Barn's `formatTelnetCommand` at `server/transport.go:190-192` returns the raw byte string; unlike Toast, Barn does not perform `~HH` binary escaping in the network transport itself.
- `barn/server/input_processor.go:119-160,196-248` propagates the OOB marker, parses `input.Line` with `command.CommandWordList`, and invokes the listener with the same line as `argstr`.
- `barn/command/command.go:153-165` routes the input through its command tokenizer. The tokenizer details and scheduler environment are the only remaining comparison points.
- Current blocker: none.

## Binary-mode row authority trace

- Conformance row `gap_followups_toast_oracle.yaml:572-631` logs in a fresh player, enables `set_connection_option(player, "binary", 1)`, sends ASCII bytes `auditbin` without newline, and expects the player's `auditbin` verb to observe empty `argstr`.
- Toast `src/server.cc:2973-2995,3294-3299` implements and registers `set_connection_option`; the wizard/player permission gate resolves the connection and routes the option to the server connection option table.
- `src/server.cc:965-977` sets both the server handle's `binary` flag and the network handle through `network_set_connection_binary`; `src/network.cc:1719-1725` immediately stores that flag.
- `src/network.cc:472-570`, especially 544-550: each successful OS/TLS read in binary mode is immediately binary-escaped and delivered without CR/LF. The fixed read buffer is 1024 bytes. Chunk boundaries are read boundaries, not logical message or caller-write boundaries.
- `src/utils.cc:671-684` leaves ASCII `auditbin` unchanged; non-printable bytes and tilde would become uppercase `~HH`.
- `src/server.cc:1535-1541` passes the server handle binary flag into `new_input_task`; `src/tasks.cc:1075-1095,1171-1179` makes it `TASK_BINARY`.
- `src/tasks.cc:1673-1770` sends a binary task to a pending `read()` if present; otherwise, for this logged-in row, it enters normal command dispatch through `do_command_task`.
- `src/tasks.cc:811-875`, `src/parse_cmd.cc:126-237`, and `src/execute.cc:3339-3371`: `auditbin` parses as the player verb with no arguments and empty `argstr`; the executed verb sees `this=player`, `player=logged-in player`, `caller=player`, `verb="auditbin"`, `args={}`, and `argstr=""`.
- Barn comparison so far: `barn/builtins/network.go:1251-1316` stores the binary option and wakes the input reader; `barn/server/input_processor.go:119-160` selects `ReadChunk` for logged-in binary connections; `barn/server/transport.go:194-209` blocks for one byte then drains only bytes already buffered by `bufio.Reader`, without Toast-style binary escaping.
- Current blocker: none. Remaining bounded work is only Barn command dispatch/context and precise boundary disagreement.
