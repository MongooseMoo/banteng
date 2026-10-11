# Banteng row 27 Toast authority notes

## Current assignment

- Read-only authority trace for `audit_telnet_subnegotiation_delivered_across_reads`.
- Inspect only pinned WSL Toast source at `/root/src/toaststunt` from socket receive through persistent Telnet subnegotiation parsing, escaping, OOB queue, verb dispatch, and task locals.
- Do not edit project files, run Banteng, or design Java.

## Current state

- Exact row located at `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:412-487`.
- Setup installs `#0:do_out_of_band_command`, whose body records `{args, argstr}` in `#0.audit_sb_oob_seen` (`:415-445`).
- The fresh connection receives four separate writes: `ff`, `fa1f00500018`, `ff`, `f0` (`:446-460`). The split boundaries therefore occur after the initial IAC and after the subnegotiation body plus terminating IAC.
- After 500 ms the asserted recorder value is `{{"~FF~FA~1F~00P~00~18~FF~F0"}, "~FF~FA~1F~00P~00~18~FF~F0"}` (`:461-467`). Printable byte `0x50` is literal `P`; the other binary bytes are `~HH` escaped.
- Toast parser symbols found in `/root/src/toaststunt/src/network.cc`: persistent `TelnetState` enum at `82-87`, per-handle state fields at `115-117`, `process_telnet_byte()` at `386`, `pull_input()` at `472`, handle initialization at `625-627`, and `network_process_io()` at `1437` with readable-handle dispatch near `1470`.
- Downstream task entry symbol found at `/root/src/toaststunt/src/tasks.cc:1075` (`enqueue_input_task`).
- No blocker currently known. An initial broad `rg` named a nonexistent top-level `include` directory but still returned the source hits; this did not affect the trace.
- Next step: read exact source ranges for parser state transitions, socket read handling, OOB enqueueing, verb lookup/dispatch, and task local initialization.

## Toast source trace findings

- Pinned WSL source identity is `aecc51e9449c6e7c95272f0f044b5ba38948459e`. The source worktree has pre-existing untracked files/build output, but no tracked modification was made by this task.
- `/root/src/toaststunt/src/network.cc:81-117` defines persistent per-`nhandle` `telnet_state`, `command_stream`, and `telnet_cmd`; `new_nhandle` initializes them at `:625-627`.
- `/root/src/toaststunt/src/network.cc:386-469` is the state machine:
  - NORMAL + `FF` resets the persistent command stream, appends `FF`, enters IAC (`:401-406`).
  - IAC + `FA` appends `FA` and enters SUBNEG without emitting (`:420-429`).
  - SUBNEG appends every byte, including `00`; `FF` enters SUBNEG_IAC (`:447-452`).
  - SUBNEG_IAC appends the following byte; `F0` encodes the entire accumulated command into the OOB stream and returns to NORMAL (`:454-465`).
- `/root/src/toaststunt/src/network.cc:472-570` shows each `pull_input` performs one `read(..., 1024)` (`:482-484,544`), constructs a fresh per-read OOB stream (`:552`), feeds all bytes through the persistent per-handle parser (`:554-559`), and calls `server_receive_line(..., 1)` once if that read completed any OOB bytes (`:561-565`). Thus incomplete reads emit nothing, but the command accumulation survives because it is on `nhandle`, not the temporary OOB stream.
- `/root/src/toaststunt/src/network.cc:1437-1475` shows the network multiplexer calls `pull_input` whenever the handle is readable.
- `/root/src/toaststunt/src/utils.cc:671-684` proves raw-to-binary escaping: printable graph/space bytes except `~` remain literal; all others become uppercase `~%02X`. Therefore bytes `FF FA 1F 00 50 00 18 FF F0` become exactly `~FF~FA~1F~00P~00~18~FF~F0`.
- `/root/src/toaststunt/src/server.cc:1535-1540` updates activity and calls `new_input_task(..., out_of_band=true)`.
- `/root/src/toaststunt/src/tasks.cc:1075-1129,1171-1180` classifies the input as `TASK_OOB`, stores the string, appends it to the connection task queue, and activates it even when ordinary input is held unless OOB is disabled.
- `/root/src/toaststunt/src/tasks.cc:1630-1690` dequeues `TASK_OOB` and calls `do_out_of_band_command`; `:969-974` calls the connection listener/handler verb with `parse_into_wordlist(command)` and the complete command as `argstr`.
- `/root/src/toaststunt/src/parse_cmd.cc:108-123` makes `args`; since the escaped command contains no spaces, it is a one-element list containing the complete escaped command.
- `/root/src/toaststunt/src/server.cc:1452-1475` assigns a fresh pre-login negative `player`, sets the listener object as task-queue handler, and creates the task queue. Thus this row dispatches on listener `#0` with that negative connection object as `player`.
- `/root/src/toaststunt/src/tasks.cc:1825-1870` performs callable-verb lookup and invokes `do_server_verb_task`. `/root/src/toaststunt/src/execute.cc:3279-3336` establishes root task locals: `this` is the listener `#0`, `player` is the negative pre-login connection object, `caller` is `#-1`, `verb` is `do_out_of_band_command`, `args` is the one-element escaped-command list, and `argstr` is the escaped command.

## Split-read implications

- Applying the row's four writes to the Toast state machine yields: after write 1 (`FF`) state IAC and no emission; after write 2 (`FA 1F 00 50 00 18`) state SUBNEG and no emission; after write 3 (`FF`) state SUBNEG_IAC and no emission; after write 4 (`F0`) state NORMAL and one complete OOB emission.
- Parser correctness is independent of how those writes are coalesced into actual `read()` calls because both state and command bytes persist on the connection handle. However, the YAML has no waits between the four `send_bytes` steps, so separate application writes alone do not prove four separate kernel/socket reads. The conformance transport implementation still needs one final check to state whether it inserts any read-boundary forcing.

## Provable Barn disagreements

- Barn `server/transport.go:85-192` has an equivalent persistent state machine, but `formatTelnetCommand` returns the raw byte string (`:190-192`). Toast converts the command to binary-escaped ASCII before verb dispatch. Barn's `types/str.go:97-125` escapes nonprintable raw bytes only when rendering a MOO literal. This can make the externally rendered value look the same while the in-VM string content differs.
- Barn `scheduler/call_verb.go:25-133` sets server-hook `caller` equal to `player` (`:110,126,130`), whereas Toast sets root server-task `caller` to `#-1` (`execute.cc:3325`). For this pre-login row Barn's caller is the negative connection object; Toast's is `#-1`.
- Toast gathers all completed Telnet commands from one socket read into one temporary OOB stream and calls `server_receive_line` once at read end (`network.cc:552-565`). Barn's `ReadInput` returns immediately after each completed command (`server/transport.go:146-178`), so multiple complete commands coalesced in one read are concatenated into one Toast OOB input but become separate Barn inputs. This row contains only one command, so that disagreement does not affect its expected result.

## Current blocker / next step

- No blocker.
- `moo-conformance-tests/src/moo_conformance/runner.py:311-327` executes each YAML `send_bytes` as a separate `TestConnection.send_bytes` call.
- `moo-conformance-tests/src/moo_conformance/transport.py:128-133` calls `socket.sendall(data)` and then `_receive_until_quiet()` after every write. `_receive_until_quiet` sets a 0.5-second receive timeout and waits until timeout when there is no output (`:148-175`). Therefore the four writes are separated by approximately 0.5 seconds each in the client workflow; this strongly exercises state persistence across separate server reads in practice. TCP still does not make application write boundaries identical to read boundaries as a formal guarantee.
- Trace is complete. Next step is only to report these findings to the parent agent.
