# Connection lifecycle authority

## Selected discrepancy

The active family is
`connection_lifecycle_toast_oracle`. Its first row is
`audit_listener_handler_do_login_command` at
`../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:34-94`.
The row allocates a port, creates a handler, installs
`handler:do_login_command`, calls
`listen(handler, port, ["print-messages" -> 0])`, connects to that port, and
sends `listener-login alpha beta`. The final observation must be
`{this == handler, args, argstr}` equal to
`{1, {"listener-login", "alpha", "beta"}, "listener-login alpha beta"}`.

The exact pinned Toast command passed with one selected row and 11,503
deselected in 5.28 seconds. The exact managed Banteng baseline failed with one
selected row and 11,503 deselected in 3.63 seconds. Banteng raised `E_VERBNF`
from the setup program because `listen` was absent from `BuiltinCatalog`; the
earlier property loop was not the failure.

The focused real-socket Java regression first failed with expected listener
port output versus actual `{2, {E_VERBNF}}`. After the owned fix, the complete
Java 25 `check installDist` gate passes. The final exact managed Banteng row
passes with one selected row and 11,503 deselected in 6.43 seconds. The full
targeted family receipt is one passing row, twenty-two failing later rows, and
11,481 deselected in 179.54 seconds. The first later failure is
`audit_listener_handler_do_command`; those later failures are not substituted
for this accepted first-row proof.

## Exact proof commands

Pinned Toast:

```powershell
$wslIp = (wsl -d Debian -u root -e hostname -I).Trim()
uv run --project ..\moo-conformance-tests moo-conformance `
  --moo-host $wslIp `
  --server-command "wsl -d Debian -u root -e env TOAST_MOO=/root/src/toaststunt/build-release/moo bash /mnt/c/Users/Q/code/barn/scripts/run_toast_wsl.sh {db} {port}" `
  --server-db C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_db/Test.db `
  --oracle-profile-manifest C:/Users/Q/code/barn/profiles/toast/stock-wsl-testdb.json `
  --target-profile-manifest C:/Users/Q/code/barn/profiles/toast/stock-wsl-testdb.json `
  -k "audit_listener_handler_do_login_command"
```

Managed Banteng:

```powershell
uv run --project C:\Users\Q\code\moo-conformance-tests moo-conformance `
  --server-command "C:/Users/Q/code/banteng/build/install/banteng/bin/banteng.bat --database {db} --checkpoint {db}.new --listen-address 127.0.0.1 --port {port}" `
  -k "audit_listener_handler_do_login_command"
```

## Pinned Toast authority

The source identity is `/root/src/toaststunt` at
`aecc51e9449c6e7c95272f0f044b5ba38948459e`.

- `src/server.cc:3089-3097,3157-3176` reads the handler object passed to
  `listen` and passes it unchanged to `new_slistener`.
- `src/server.cc:201-229` stores it as `slistener.oid`.
- `src/server.cc:1454-1492` copies that identity into the accepted connection,
  creates its task queue with that listener, and queues the initial blank login
  input.
- `src/tasks.cc:986-997` stores the listener as the task queue handler.
- `src/tasks.cc:877-916,1762-1769` selects login dispatch for a negative
  connection, parses the later input, and invokes the handler's
  `do_login_command` with the parsed words and original command string.
- `src/tasks.cc:921-959` is not entered because this row's verb returns zero.
- `src/server.cc:3189-3207` closes the listener selected by `unlisten`.

The initial blank input occurs before the row's sent command and may write the
recorder once. It cannot explain the final observation: the later nonblank
input overwrites it. The authoritative identity chain is:

```text
listen(handler)
  -> slistener.oid
  -> shandle.listener
  -> tqueue.handler
  -> do_login_command receiver/this
```

## Barn ownership reference

- `../barn/spec/server.md:202-230,267` assigns startup listeners and MOO
  `listen()` to the same listener owner and assigns connection lifecycle to the
  listener object.
- `../barn/server/connection_manager.go:91-219,579-662` owns listener binding,
  registration, accept, and removal.
- `../barn/server/connection.go:247-261` retains the listener object and port on
  the connection.
- `../barn/server/input_processor.go:417-473` dispatches login to the retained
  listener handler.
- `../barn/builtins/signatures.go:678-757` and
  `../barn/builtins/network.go:736-875` validate listener builtin arguments and
  delegate concrete socket ownership to the connection manager.

Barn is an implementation reference. The pinned Toast source and durable row
decide observable behavior.

## Measured baseline and accepted representation

At baseline, `MooServer` owned one fixed `ServerSocket` and accepted
connections without a listener-handler identity. `MooRuntime.openConnection`
stored no handler, `executeLogin` hard-coded both lookup and `this` to `#0`,
and `BuiltinCatalog` fell through to `E_VERBNF` for `listen` and `unlisten`.

The first owned fix is exactly one direct listener path:

1. validate `listen(handler, port, options)` and `unlisten(port)` in the
   existing builtin catalog, including wizard permission and
   `print-messages`;
2. keep socket binding, listener registration, accept loops, and closing in
   the existing concrete `MooServer` owner;
3. retain handler and `print-messages` on each listener and accepted
   connection;
4. dispatch blank and nonblank pre-login input to that handler with
   `this=handler` and the negative connection as `player` and `caller`;
5. allocate negative connection IDs safely across the primary and dynamic
   accept loops; and
6. close every listener during server shutdown while `unlisten` closes only
   the selected listener.

The accepted implementation keeps socket binding and accept loops in the
existing `MooServer`, stores handler identity before the initial blank login,
uses atomic decreasing connection IDs across listeners, and closes listener
and connection state on every failure path. `listen`, `unlisten`, and `close`
serialize listener registry mutation so shutdown cannot race a newly bound
socket into a leak.

The production package-cycle gate forbids importing `moo.server` back into
`moo.builtin` or `moo.runtime`. The single nested `ListenerControl` capability
is the composition boundary needed to preserve direct `moo.server` socket
ownership without a package cycle. It does not own state or adapt another
network API. No parser, compiler, VM, world model, alternate server, listener
adapter, sender, or general networking abstraction is part of this slice.

## Family inventory

The durable family contains these 23 rows, in order:

1. `audit_listener_handler_do_login_command`
2. `audit_listener_handler_do_command`
3. `audit_listener_handler_do_blank_command`
4. `audit_proxy_command_clears_login_input`
5. `audit_user_connected_hook_on_first_login`
6. `audit_user_connected_continues_after_zero_delay_fork`
7. `audit_user_connected_dynamic_handler_continues`
8. `audit_user_connected_confunc_calls_continue_after_fork_and_setting_task_perms`
9. `audit_user_client_disconnected_hook`
10. `audit_user_reconnected_cross_listener_hooks`
11. `audit_connect_timeout_server_option`
12. `audit_flush_command_flushes_pending_input`
13. `audit_listener_print_messages_suppresses_connect_msg`
14. `audit_boot_player_messages`
15. `audit_recycle_active_player_message`
16. `audit_login_timeout_message`
17. `audit_redirect_messages`
18. `audit_oob_prefix_dispatches_do_out_of_band_command`
19. `audit_connection_name_method0_hostname`
20. `audit_connection_info_source_fields`
21. `audit_do_login_command_argstr_original`
22. `audit_connection_hold_and_oob_options`
23. `audit_user_created_hook_for_new_login_object`

Each stateful row owns explicit cleanup. Exact convergence remains on the first
row until its focused Java test, exact managed row, and substantial targeted
category pass and the kept slice is committed.

## Second slice: returned login player and handler command fallback

The second row is `audit_listener_handler_do_command` at YAML lines 95-173.
Its handler `do_login_command` returns a newly player-flagged object. After
that login input, a later `postlogin alpha beta` line must call the same
handler's `do_command` with `this=handler`, `player=login_player`, parsed words
as `args`, and the original line as `argstr`.

Pinned Toast at the same verified source identity passes the exact row with one
selected and 11,503 deselected in 5.35 seconds. `src/tasks.cc:913-959` accepts a
valid user object returned by `do_login_command`, changes the task queue's
player from the negative connection to that object, and enters the connected
path. `src/tasks.cc:1762-1769` consequently selects `do_command_task` for later
input. `src/tasks.cc:812-875` invokes `do_command` on `tq->handler`, supplies
the authenticated player, parsed word list, and original command, and only
falls through to ordinary command matching when the handler returns false.

The exact managed Banteng baseline failed with one selected row and 11,503
deselected in 7.18 seconds. The final assertion raised `E_RANGE` because the
handler recorder remained empty: Banteng ignored the returned player object,
left the connection negative, and dispatched every later line as another
login input. The focused Java regression proved the same cause directly:
expected connection player `#10`, actual `#-48`.

The accepted second slice changes only `MooRuntime`. A login return switches
the connection when it is an object in the world's player index, while the
existing explicit `switch_player` result retains precedence. Post-login
`do_command` lookup and `this` use the listener handler already stored in the
connection; `player` and `caller` remain the authenticated player. No server,
socket, builtin, parser, compiler, VM, or world representation changes are in
this slice.

The focused Java regression and complete Java 25 `check installDist` gate
pass. The exact managed row passes with one selected and 11,503 deselected in
7.09 seconds. The targeted family fail-fast receipt is two passing rows, then
`audit_listener_handler_do_blank_command` first failing, with 11,481
deselected in 9.11 seconds. Rows one and two are accepted; exact convergence
continues on row three.

## Third-row prerequisite: connection network metadata

The third row is `audit_listener_handler_do_blank_command` at YAML lines
174-252. Before it creates the dynamic listener, its setup reads
`connection_info(player)["destination_ip"]` from the harness's authenticated
control connection and installs that address in `#6.trusted_proxies`. The
blank-command behavior cannot be reached until that exact prerequisite works.

The pinned Toast row passes with one selected and 11,503 deselected in 5.13
seconds. The managed Banteng baseline failed during setup with one selected
and 11,503 deselected in 4.53 seconds because `connection_info` raised
`E_VERBNF`. The focused real-socket Java regression likewise returned
`{2, {E_VERBNF}}` before the implementation and returns
`{1, "127.0.0.1"}` afterward.

There is no dedicated normative `connection_info` contract in the Barn
specification. Toast `src/server.cc:3032-3072` resolves either a live negative
connection object or a connected player, raises `E_INVARG` when no connection
exists, permits only that player or a wizard, and returns the eight network
fields `source_address`, `source_ip`, `source_port`, `destination_address`,
`destination_ip`, `destination_port`, `protocol`, and `outbound`. Barn
`../barn/builtins/network.go:1161-1220` implements the same lookup, permission,
and field surface through its concrete connection manager. The implementations
agree on the observable contract needed by this row; the managed Toast receipt
resolves the setup behavior.

The accepted prerequisite keeps immutable per-connection metadata beside the
existing connection record in `WorldTxn`, populated directly from the accepted
socket by `MooServer`. `BuiltinCatalog.connection_info` validates exact arity
and type, obtains the record only through `WorldTxn`, applies the Toast
permission rule, and returns the stored map. Closing a connection removes both
the player attachment and metadata. No alternate networking owner, adapter, or
non-transactional world access is introduced.

The complete Java 25 `check installDist` gate passes. The exact managed row now
advances through setup and fails after the dynamic connection is accepted in
3.68 seconds: `MooRuntime.executeLogin` looks for `do_login_command` and raises
`NoSuchElementException`. That later failure is the row's actual trusted-proxy
blank-dispatch discrepancy, so it is proof of a kept reduction but is not
claimed as a passing row. The `connection_info` prerequisite is committed
before the next semantic slice begins.
