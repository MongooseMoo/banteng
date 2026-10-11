# Row 26 pinned Toast authority notes

## 2026-07-15 restart handoff

- Assignment: read-only trace of pinned Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e` at `/root/src/toaststunt` for lifecycle row `audit_telnet_iac_delivered_as_oob_command`.
- Active behavior to explain: the client sends byte `FF`, waits, then sends bytes `FB 01`; before login the listener must receive `do_out_of_band_command` with both `args[1]` and `argstr` equal to `~FF~FB~01`.
- Required evidence: persistent parser state across reads, WILL option third-byte completion, exact OOB command emission and `~HH` encoding, and queued task execution context. Only causally relevant differences from a simple IAC command are in scope.
- Constraints: no edits to Toast, Barn, or Banteng; do not run an oracle; cite exact sources and lines.
- Observed so far: this is a fresh restarted Codex process and no Toast source inspection has yet occurred in this subtask.
- Tried/worked/did not work: no diagnostic commands or source reads have yet been attempted.
- Current blocker: none. Next step is to read the applicable reverse-engineering skill instructions, verify the pinned checkout identity, and trace the exact source path read-only.

## Pinned identity and telnet state machine

- `git -C /root/src/toaststunt rev-parse HEAD` returned the required pinned identity `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- `src/network.cc:81-87` defines persistent telnet parser states `NORMAL`, `IAC`, `COMMAND`, `SUBNEG`, and `SUBNEG_IAC`. `src/network.cc:89-124` stores `telnet_state`, the accumulating `command_stream`, and `telnet_cmd` on the connection's long-lived `nhandle`; these are not locals of one socket read.
- In `process_telnet_byte()` (`src/network.cc:385-469`), byte `FF` (`TN_IAC`) in `NORMAL` changes the connection to `IAC`, resets `command_stream`, and appends `FF` (`403-406`). It emits nothing at that point.
- When a later byte `FB` (`TN_WILL`) arrives in `IAC`, Toast appends it, records it as `telnet_cmd`, and changes to `COMMAND` (`420-430`). It still emits nothing because WILL requires its option byte.
- When the later option byte `01` arrives in `COMMAND`, Toast appends it, passes all three accumulated raw bytes to `stream_add_raw_bytes_to_binary(oob_stream, ...)`, and returns to `NORMAL` (`439-445`). Thus the completion boundary is exactly the third byte, even when the three bytes cross socket reads.
- Causally relevant distinction from a simple two-byte IAC command: a non-`SB`/non-negotiation command in state `IAC` is emitted immediately after byte two (`430-435`), whereas WILL/WONT/DO/DONT enter `COMMAND` and wait for byte three (`428-445`).

## Socket-read persistence and OOB delivery boundary

- `pull_input()` reads at most 1,024 currently available bytes per invocation (`src/network.cc:471-545`). In non-binary mode it allocates only the per-read `oob` stream, then feeds every byte to `process_telnet_byte(h, h->input, oob, ...)` (`552-559`).
- If a read contains only `FF`, the temporary `oob` stream stays empty, so no call to `server_receive_line` occurs (`561-565`). Freeing this stream does not lose parsing progress because `telnet_state` and `command_stream` belong to `h`.
- A later read containing `FB 01` resumes with `h->telnet_state == IAC` and the `h->command_stream` that already contains `FF`; after `01`, the state machine appends all three bytes to that later read's `oob`. `pull_input()` then calls `server_receive_line(h->shandle, reset_stream(oob), 1)` exactly once because the stream is nonempty (`561-563`). The final `1` marks the delivered line out-of-band.
- New connections initialize `telnet_state` to `NORMAL`, allocate the persistent `command_stream`, and clear `telnet_cmd` in `new_nhandle()` (`src/network.cc:574-640`, specifically `625-627`). The state is initialized once per network handle, not once per `pull_input()` call.

## Exact binary-string encoding

- `stream_add_raw_bytes_to_binary()` is defined at `src/utils.cc:671-684`. For each unsigned input byte, it preserves printable graph/space bytes except literal `~`; every other byte is formatted as uppercase `~%02X` (`676-683`).
- The three telnet bytes are all non-printable/high-byte values for this rule: `FF` becomes `~FF`, `FB` becomes `~FB`, and `01` becomes `~01`. Because the state machine passes the accumulated bytes in order, the exact emitted MOO string is `~FF~FB~01`.
- There is no delimiter, newline, or extra protocol label added in this conversion. The escaped string itself becomes the `line` supplied to `server_receive_line`.

## Server handoff and pre-login queue identity

- A newly accepted connection receives a unique negative player ID at `src/server.cc:1452-1475`; `server_new_connection()` stores that negative ID on `shandle` and creates its task queue with `new_task_queue(h->player, h->listener)` (`1472-1475`). This establishes the pre-login queue player and listener.
- `server_receive_line()` (`src/server.cc:1534-1541`) updates activity time and calls `new_input_task(h->tasks, line, h->binary, out_of_band)`. Therefore the exact escaped string and `out_of_band=true` marker from `pull_input()` enter the same connection task queue unchanged.

## OOB task classification and selection

- `TASK_OOB` is the explicit out-of-band task kind (`src/tasks.cc:55-64`). Input tasks store a copied string (`81-95`). The queue documentation explicitly says negative-player login special handling does not apply to tasks handled as out-of-band commands (`142-159`).
- `new_input_task()` sends the received string to `enqueue_input_task()` at the queue rear (`src/tasks.cc:1170-1180`). Because `server_receive_line` supplied `out_of_band=true`, `enqueue_input_task()` classifies it as `TASK_OOB` (`1074-1095`, specifically `1081-1085`) and duplicates the exact string at `1094`.
- The ready-task loop dequeues input and dispatches `TASK_OOB` directly to `do_out_of_band_command(tq, t->t.input.string)` (`src/tasks.cc:1650-1690`). This branch occurs before the in-band/login cases and is independent of whether the queue's player is still its negative pre-login object.
- `do_out_of_band_command()` invokes `run_server_task(tq->player, listener-as-object, "do_out_of_band_command", parse_into_wordlist(command), command, nullptr)` (`src/tasks.cc:969-974`). Thus the exact stored `~FF~FB~01` string is supplied unchanged as the server task's `argstr`, while its parsed word list is supplied as `args`.

## Queue handler and server-task entry

- `new_task_queue()` marks the queue connected and stores the passed listener object as `tq->handler` (`src/tasks.cc:986-998`). Combined with `server_new_connection()` passing its negative player and listener, `do_out_of_band_command()` targets that listener while retaining the negative pre-login player as task player.
- `run_server_task()` delegates unchanged `player`, target object, verb name, `args`, and `argstr` to `run_server_task_setting_id()` (`src/tasks.cc:1825-1837`). That function looks up the callable verb on the supplied target and then calls `do_server_verb_task(..., player, argstr, ...)` (`1844-1871`, specifically `1858-1861`). Missing hooks simulate an empty verb, but a present listener hook receives the supplied context.

## Exact `args[1]`

- `parse_into_words()` splits only on spaces outside quotes and otherwise copies characters unchanged (`src/parse_cmd.cc:33-79`, especially `45-75`). The command `~FF~FB~01` contains no spaces, quotes, or backslashes, so it becomes exactly one word with the same bytes.
- `parse_into_wordlist()` duplicates the command, builds a list with one string per parsed word, and places the first word at MOO list index 1 (`src/parse_cmd.cc:108-124`). Consequently `args == {"~FF~FB~01"}` and `args[1] == "~FF~FB~01"`; the separately passed `argstr` is the same original string.

## Exact MOO execution context

- `do_server_verb_task()` obtains the verb definer, program, owner, and debug flag from the listener hook and delegates to `do_server_program_task()` (`src/execute.cc:3278-3287`). Thus task permissions (`progr`) are the owner of `do_out_of_band_command`.
- `do_server_program_task()` makes the supplied listener object the receiver/`this`, stores the supplied queue player as `player`, and initializes the runtime environment (`src/execute.cc:3289-3336`). It sets `caller` to `#-1` (`3325`), `this` to the listener (`3326`), `argstr` to the unchanged supplied string (`3331`), `verb` to `do_out_of_band_command` (`3319`, `3333`), and `args` to the parsed one-element list (`3334`). Direct/indirect objects are `#-1` and their strings plus `prepstr` are empty (`3327-3332`).
- Before login, the queue player remains the negative connection object assigned by `server_new_connection`; therefore this task's `player` is that negative object. OOB dispatch does not run the login command or rebind the connection first.

## Default queue options and scheduling

- A newly created `tqueue` initializes both `hold_input` and `disable_oob` to false (`src/tasks.cc:431-475`, specifically `467-469`). Therefore the row's fresh connection retains `TASK_OOB` classification.
- Enqueueing an OOB task calls `ensure_usage()` even if ordinary input is held, provided OOB has not been disabled (`src/tasks.cc:1119-1122`). When dequeued with default `disable_oob=false`, the task remains `TASK_OOB`; only `disable_oob=true` converts it to `TASK_INBAND` (`src/tasks.cc:534-595`, specifically `540-544`, `583-586`).
- No row action enables `disable-oob`, so the persistent telnet command reaches the listener OOB branch. This option machinery is common to both the split WILL row and a simple IAC command; it is not the causal difference between them.

## Final requirement review

- Persistent state across reads: answered by **Pinned identity and telnet state machine** and **Socket-read persistence and OOB delivery boundary**: "`telnet_state` and `command_stream` belong to `h`" and the later read "resumes with `h->telnet_state == IAC`".
- WILL third-byte completion: answered by **Pinned identity and telnet state machine**: `FB` enters `COMMAND` without emission and `01` emits all three bytes.
- Exact OOB emission and encoding: answered by **Exact binary-string encoding** and **Server handoff and pre-login queue identity**: uppercase `~%02X` produces `~FF~FB~01`, then `server_receive_line(..., 1)` preserves it.
- Queue and hook context: answered by **OOB task classification and selection**, **Queue handler and server-task entry**, **Exact args[1]**, and **Exact MOO execution context**.
- Causal difference from a simple IAC command: answered by **Pinned identity and telnet state machine**: ordinary two-byte commands emit in `IAC`, but WILL waits in `COMMAND` for its option byte. The later queue, encoding, and hook path are common.
- Read-only constraint: no Toast, Barn, or Banteng production/test file was edited and no oracle was run. Only this untracked authority note was written as required by the RE/checkpoint workflow.
- Current blocker: none. Authority trace is complete and ready for the parent agent to use in row-26 Java design.
