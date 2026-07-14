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

## Third-row semantic slice: trusted blank dispatch

Barn's normative `../barn/spec/server.md:129-170,189-240` names
`do_login_command`, the unlogged state, and listener ownership, but does not
specify `trusted_proxies` or `do_blank_command`. Its statement that every
unlogged command routes to `do_login_command` is incomplete for trusted empty
input. That gap is not filled from the Barn implementation: the pinned Toast
source and the already-proven managed row decide it.

Pinned Toast `src/server.cc:530-542` resolves a named server option from the
listener object's `server_options` object, falling back to system object `#0`.
`src/server.cc:1577-1597` resolves `trusted_proxies` through that path, requires
a list, reads the connection's remote IP, and accepts only an equal string
element. `src/tasks.cc:877-916` checks that predicate before ordinary login.
For exactly empty input it invokes `do_blank_command` on the retained listener
handler with the negative connection as player, empty `args`, and empty
`argstr`. A false result returns from the login task without invoking
`do_login_command`; a true result continues into ordinary login processing.
The call uses the listener handler as `this`, and the server task supplies the
same negative connection as `caller`.

Barn `../barn/server/input_login.go:17-35,136-165,315-346` has the same branch,
receiver and arguments. It treats a missing verb, an exception, or a falsey
result as “do not continue to login”; a truthy result permits login. Barn's
server-option lookup uses listener-first and system-object fallback, matching
Toast. There is no MOO-observable disagreement for the exact row. Row four's
nonempty `PROXY` command parsing and command clearing remain outside this
slice.

At committed Banteng baseline `3f54521`, `MooRuntime.openConnection` registers
the dynamic connection and immediately calls `executeLogin(connectionId, "")`.
That method unconditionally requires `do_login_command`; this row's handler
deliberately defines only `do_blank_command`, so the managed process aborts at
`MooRuntime.java:594` before the explicit client send. The smallest owned
change is confined to `MooRuntime.executeLogin`: resolve the exact
listener-first `server_options.trusted_proxies` option through `WorldTxn`,
compare it with the stored `destination_ip`, execute the existing handler verb
with the existing VM path and locals, and stop when the blank result is false.
The focused regression belongs in `MooRuntimeTest`. No server, world, builtin,
value representation, interface, helper, adapter, or sender is added.

The focused Java regression first failed with `NoSuchElementException` from
the `openConnection` call because the handler had no `do_login_command`. After
the owned `executeLogin` change, that exact test passes and records
`{this, player, caller, args, argstr}` as
`{handler, connection, connection, {}, ""}`. The complete Java 25
`check installDist` gate passes. The exact managed row passes with one selected
and 11,503 deselected in 5.06 seconds. The targeted family fail-fast receipt is
three passing rows followed by `audit_proxy_command_clears_login_input`, with
11,481 deselected in 11.53 seconds. Row three is accepted; exact convergence
continues on row four after this slice is committed.

## Fourth row: consume a trusted PROXY prelude

The fourth row is `audit_proxy_command_clears_login_input` at YAML lines
254-342. It trusts the control connection's observed peer IP, replaces the
primary listener's `do_login_command` with a recorder, opens a new primary
connection, and sends exactly
`PROXY TCP4 127.0.0.1 198.51.100.9 4242 7777`. The only asserted contract is
that successful trusted-proxy handling consumes this canonical line before
login dispatch, so `args` is empty and `argstr` is `""`.

Barn's normative `../barn/spec/login.md:25-43` and
`../barn/spec/server.md:129-170` describe login imprecisely as positional
`do_login_command(connection, line)` input. Neither section specifies trusted
proxy detection, parsing, metadata rewrites, failures, or command clearing.
The exact row therefore does not derive those behaviors from the normative
prose.

At pinned Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`, the
public path is `src/network.cc:549,562` through
`src/server.cc:1535-1540` and `src/tasks.cc:1171-1179,1769` to the semantic
owner at `src/tasks.cc:878-916`. Trust is an exact remote-IP string match in
the listener-resolved `trusted_proxies` list at
`src/server.cc:1576-1598`. `src/tasks.cc:891-915` detects a case-sensitive
five-byte `PROXY` prefix, calls `proxy_connected`, and clears the command only
when that call succeeds. `src/server.cc:1601-1654` tokenizes on ASCII spaces,
requires six tokens, maps the announced addresses and ports into the concrete
connection rewrite, and ignores extra tokens. The login task then receives an
empty word list and empty argument string.

Barn's public path is
`../barn/server/input_processor.go:65-160,196-230,368-478` through
`../barn/scheduler/task_factory.go:124-176` and
`../barn/scheduler/task_runtime.go:151-185`. Its canonical successful path
agrees that a trusted PROXY prelude is consumed before login dispatch. Outside
this row Barn differs: it requires the literal prefix `PROXY `, uses
`strings.Fields`, validates only the announced source IP, and clears every
detected trusted line even when parsing fails. Toast's malformed-prefix,
failure, metadata, reverse-name, and caller behaviors are not selected by this
row and require separate durable oracle rows before implementation.

The exact pinned Toast row passes with one selected and 11,503 deselected in
5.19 seconds. At committed Banteng baseline `3984b04`, the exact row fails with
one selected and 11,503 deselected in 5.23 seconds: expected `{{}, ""}` but the
recorder remains `{}`. That receipt does **not** reach PROXY handling. The row's
setup first calls `set_verb_info` and `set_verb_args`; both are absent from
`BuiltinCatalog`, so the setup program aborts with `E_VERBNF` before installing
the recorder code. The earlier diagnosis that the empty recorder proved a
PROXY-clearing discrepancy was wrong.

A temporary focused Java regression that installed the recorder without those
unsupported setup calls proved the later expected PROXY red: Banteng supplied
all six words and the original line instead of `{}` and `""`. That experiment
was fully restored because it is not the first causal failure in the managed
row. Exact convergence remains on row four but moves first to the
`set_verb_info`/`set_verb_args` setup prerequisite. Those verb-metadata
mutations require their own authority gate and committed kept slice before the
PROXY implementation can begin. No PROXY production edit is retained.

## Fourth-row prerequisite: verb metadata setters

The durable positive authorities are `verbs::set_verb_info_wizard_succeeds`
and `verbs::set_verb_args_basic` at
`../moo-conformance-tests/src/moo_conformance/_tests/builtins/verbs.yaml:449-483`.
The exact pinned Toast run passes both rows with 11,502 deselected in 3.61
seconds. They prove successful local-verb mutation and an integer-zero return;
they do not prove the broader invalid-input matrix.

Barn's normative `../barn/spec/builtins/verbs.md:72-129,134-195` describes the
metadata and argument lists, but incorrectly calls both setter returns `none`.
Its `set_verb_info` permission summary is also incomplete. The positive rows
and pinned Toast source select the contract used here.

At pinned Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`, both
functions register at exact arity three in `src/verbs.cc:655-667`.
`src/verbs.cc:77-171,215-236,321-362,421-454` validates a valid target, a local
string or positive one-based descriptor, the exact three-element lists, valid
owner, `r/w/x/d` flags, nonempty names, `none`/`any`/`this` object specs, and a
recognized individual preposition. Both require verb write permission;
`set_verb_info` additionally restricts a nonwizard's new owner. The concrete
mutations are in `src/db_verbs.cc:758-818,848-875`.

Barn registers the same names in
`../barn/builtins/registry.go:174-182` and implements them through
`../barn/builtins/verbs.go:543-665` and
`../barn/db/store/store_verbs.go:405-455`. It agrees on both successful rows
but differs outside them: it accepts only string descriptors, performs weaker
permission and value validation, and stores looser argument text. Those
differences are not selected by this prerequisite and remain for separately
proven verb-family rows.

`WorldVerb` already stores names, owner, packed permission and direct/indirect
argument bits, preposition, and source. The smallest owned Banteng slice adds
the two named transaction-write builtins to `BuiltinCatalog` and two concrete
immutable-record replacements to `WorldTxn`; it does not change the verb data
model. The focused regression invokes the exact row-four setter calls on an
existing local `do_login_command`, expects `{0, 0}`, and verifies the changed
metadata while preserving source. Getter builtins, inherited resolution,
generic resolver/parser helpers, interfaces, adapters, and PROXY behavior are
outside this prerequisite.

The focused Java 25 regression first ended `ERRORED` because both builtin names
were absent. After the owned implementation it returns `{0, 0}` and verifies
packed permissions `173`, preposition `-1`, and unchanged verb source. The
complete Java 25 `check installDist` gate passes. The exact managed row still
fails with one selected and 11,503 deselected in 5.23 seconds, but it now
reaches the intended PROXY assertion: the recorder contains all six PROXY words
and the original line instead of remaining empty. This is a kept reduction.
The setter prerequisite is committed before the rejected PROXY experiment is
reintroduced under its already-frozen row-four authority.

From committed prerequisite baseline `da73a7b`, the focused PROXY regression
fails with the exact six words and original input, then passes after the owned
`MooRuntime.executeLogin` change supplies one local effective empty line to the
existing login path. The complete Java 25 `check installDist` gate passes. The
exact managed row passes with one selected and 11,503 deselected in 5.12
seconds. The targeted family fail-fast receipt is four passing rows followed by
`audit_user_connected_hook_on_first_login`, with 11,481 deselected in 21.70
seconds. Row four is accepted; exact convergence continues on row five after
this slice is committed.
