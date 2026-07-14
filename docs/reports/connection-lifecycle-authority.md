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

## Fifth row: fresh `user_connected` hook

The active durable row is `audit_user_connected_hook_on_first_login` at
`../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:344-449`.
It creates an existing player, installs the accepting listener handler's
`do_login_command` to return that player, opens a fresh connection, and records
the `user_connected` frame. The row now asserts the complete observable frame:
`this == #0`, `player == returned_player`, `caller == #-1`,
`args == {returned_player}`, and `argstr == ""`. The corrected row is committed
in the conformance repository as `a1264bb` and passes pinned WSL Toast with one
selected and 11,503 deselected in 4.35 seconds.

Barn's normative `../barn/spec/login.md:25-43,75-140` agrees that a returned
existing player is associated before a fresh `user_connected(player)` hook and
that hook failure does not undo the association. It inaccurately describes the
hook as always residing on `#0` and does not specify the exact task frame.

At pinned Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`,
`src/tasks.cc:878-958` validates the returned player, associates it with the
connection, completes the login task, and calls `player_connected`.
`src/server.cc:1657-1723,519-526` selects `user_connected` for this fresh
existing player and starts the notifier on the accepting listener handler with
one player argument and an empty argument string. `src/tasks.cc:1825-1869` and
`src/execute.cc:3279-3336` establish the exact frame selected by the corrected
row, including `caller == #-1`. The notifier return value is discarded; a
missing or failing hook does not reverse the already-completed association.

Barn's public path is
`../barn/server/input_processor.go:368-478`,
`../barn/server/input_login.go:99-133,202-223,237-312`, and
`../barn/scheduler/task_factory.go:61-105`. It agrees on returned-player
acceptance, association before notification, the accepting listener handler,
`this`, `player`, `args`, and `argstr`. It disagrees by supplying the accepted
player as `caller`; the corrected managed row resolves that observable field in
favor of pinned Toast's `#-1`. Anonymous-player acceptance, reconnect routing,
connection-message policy, suspended hooks, and traceback output remain outside
this row and require their own durable evidence before implementation.

At committed Banteng baseline `0913cce`, the original row fails with one
selected and 11,503 deselected in 6.04 seconds: the player association succeeds
but the recorder remains `{}`, so the comparison returns zero. The smallest
accepted slice changes only the existing login semantic owner: after a fresh
returned-player association it synchronously executes the accepting listener
handler's existing `user_connected` verb with the corrected row's exact frame.
No listener, world-model, scheduler, reconnect, helper, adapter, or interface
change belongs to this slice.

The first attempted hook slice proves its focused Java regression and the
corrected managed row in isolation, but it is fully restored rather than kept.
The two-row family sequence passes row four and then times out before row five's
setup. The harness defaults each test to programmer permission, so it closes
the Wizard control connection and logs in again between rows. Row four's save
block calls `verb_info`, `verb_args`, and `verb_code`; all three are absent in
Banteng, so its `E_VERBNF` handler records that `do_login_command` did not exist.
Cleanup consequently deletes the real login verb. The next control connection
cannot authenticate, and its framed setup command produces no response. A live
thread dump rules out a runtime deadlock: the server connection threads are
back in `BufferedReader.readLine`. This causally earlier getter prerequisite
must be committed before the hook slice is retried.

## Fifth-row prerequisite: verb metadata getters

The positive durable rows are `verb_info_basic`, `verb_args_basic`, and
`set_verb_code_basic` at
`../moo-conformance-tests/src/moo_conformance/_tests/builtins/verbs.yaml:205-296,533-550`.
They prove the three getter result shapes, expanded preposition spelling, and
nonempty code-list round trips. The added
`verb_getters_restore_canonical_source` row freezes the remaining exact fields:
the owner is the calling player, permissions are emitted in canonical `r`, `w`,
`x`, `d` order as `"rxd"`, argument specs round trip unchanged, and source installed
as `"1   ;"` is returned canonically as `"1;"`, accepted unchanged by
`set_verb_code`, and executes with the same result after restoration. The row is
committed in the conformance repository as `0244098` and passes pinned WSL
Toast with one selected and 11,504 deselected in 3.51 seconds. The three earlier
positive rows pass together with three selected and 11,501 deselected in 3.53
seconds. The pinned row-four/row-five sequence also passes with two selected and
11,502 deselected in 6.32 seconds.

Barn's normative `../barn/spec/builtins/verbs.md:72-109,134-200,202-238`
specifies `{owner, perms, names}`, `{dobj, prep, iobj}`, and a source-line list.
The current public path is `../barn/bytecode/compiler.go:1320-1369`,
`../barn/vm/vm.go:655`, `../barn/vm/op_misc.go:10-47`, and
`../barn/builtins/registry.go:175,399-435`. Its concrete owners are
`../barn/builtins/verbs.go:163-345`, with local-only lookup in
`../barn/db/store/store_verbs.go:266-309`.

At pinned Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`,
`src/verbs.cc:215-236` validates and resolves descriptors only on the named
object. `bf_verb_info` at `src/verbs.cc:272-317` emits the exact owner,
canonical `r`, `w`, `x`, `d` permission order, and stored names.
`bf_verb_args` at `src/verbs.cc:382-417` emits direct object, expanded
preposition, and indirect object strings. `bf_verb_code` at
`src/verbs.cc:469-500` canonically unparses the installed program. Registration
at `src/verbs.cc:660-675` fixes two arguments for `verb_info` and `verb_args`
and two through four for `verb_code`.

Barn and Toast agree on local named lookup and the exact info/argument shapes.
They disagree on code representation: current Barn returns stored raw source,
while Toast returns canonical decompilation. The new durable row resolves that
observable difference for this slice. Optional `verb_code` formatting flags,
numeric descriptors, invalid inputs, permission failures, inherited lookup,
and generic expanded-preposition setter compatibility remain outside this
prerequisite.

Local named lookup includes stored MOO wildcard patterns rather than literal
alias equality. Existing durable rows `verb_lookup_wildcard_expands` and
`verb_lookup_rejects_literal_wildcard_name` freeze both sides: a stored
`foo*bar` is found by `foobar`, while the literal lookup string `foo*bar` raises
`E_VERBNF`. Both rows pass together on pinned WSL Toast with two selected and
11,503 deselected in 3.40 seconds. The expansion row is red on the initial
Banteng getter implementation because exact alias comparison returns
`E_VERBNF`; the three getter owners must use the already-established MOO name
pattern algorithm while remaining local to the named object.

The smallest honest Banteng representation adds the three named read-only
builtin owners to the existing concrete `BuiltinCatalog`. They resolve one
local string descriptor directly against the existing immutable `WorldVerb`,
emit existing `MooValue` lists, and add no world mutator, interface, adapter, or
public model. Canonical `verb_code` cannot come from `WorldVerb`, which stores
only raw source, or from `MooCompiler`, whose output is executable bytecode.
It therefore requires a syntax-owned unparser over the existing closed `Ast`
model. That unparser is the semantic owner of source reconstruction rather than
a `BuiltinCatalog` formatting workaround. Its focused syntax tests must prove
precedence and escaping plus parse-unparse-parse preservation across every AST
variant accepted by Banteng. The runtime regression exercises exact owner,
`rxd`, `this/none/this`, canonical one-line code, and unchanged setter
restoration before the family sequence is rerun.

### Banteng prerequisite receipt

The focused runtime regression first fails with the expected metadata recorder
still `{}` because the first getter raises `E_VERBNF`. After the getter and
syntax-owner implementation, the combined `MooUnparserTest` plus
`readsAndRestoresCanonicalLocalVerbMetadata` run passes, and `check installDist`
passes on Java 25 after applying the repository formatter.

Against the freshly built managed Banteng distribution,
`verb_getters_restore_canonical_source` passes with one selected and 11,504
deselected. `verb_args_basic` and `set_verb_code_basic` also pass in the
three-row getter command. `verb_info_basic` reaches its post-getter expression
but then raises `E_VARNF` because Banteng does not yet define the unrelated
`OBJ` global used by that row; this is not a getter failure and is outside this
prerequisite. The adjacent `verb_info_with_read_permission` row passes with one
selected and 11,504 deselected, proving the positive getter path without that
constant dependency.

Adversarial review then identifies exact-alias comparison as inconsistent with
the recorded wildcard contract. The strengthened runtime regression stores
`get*ter_probe` and uses `getter_probe` through all three getters and all three
restorations; it is red with the original exact comparison and green after the
three direct owners adopt MOO pattern matching. The final substantial managed
getter command selects seven rows covering canonical source, positive info and
args values, code restoration, wildcard expansion, and literal-wildcard
rejection. All seven pass, with 11,498 deselected in 4.45 seconds. The final
Java 25 `check installDist` gate also passes.

Most importantly, the ordered lifecycle run now passes
`audit_proxy_command_clears_login_input` and reaches
`audit_user_connected_hook_on_first_login`'s final observation, which returns
zero instead of the expected one. The earlier setup timeout is eliminated.
This is the required kept causal reduction: row-four cleanup preserves the
real login verb, and exact convergence can return to the missing fresh-login
hook after this prerequisite is committed.

## Fifth-row Banteng receipt

The focused Java regression is red with the player associated but the exact
`user_connected` frame recorder still `{}`. The accepted implementation changes
only the existing login semantic owner: after a fresh returned-player
association it synchronously invokes the accepting handler's
`user_connected` with `this` equal to that handler, `player` equal to the
returned player, `caller == #-1`, `args == {player}`, and `argstr == ""`.
The hook's raised error and return value are ignored after association. The
focused regression and the Java 25 `check installDist` gate pass.

Managed `audit_user_connected_hook_on_first_login` passes in isolation with
one selected and 11,504 deselected in 4.60 seconds. The complete
`connection_lifecycle_toast_oracle` fail-fast run then passes the first five
rows and reaches `audit_user_connected_continues_after_zero_delay_fork`, where
the final observation is `[1, 0]` instead of `[1, 1]`. Row five is accepted;
row six is the next causally relevant unchecked lifecycle target.

## Sixth row: zero-delay fork continuation

The active durable row is
`audit_user_connected_continues_after_zero_delay_fork` at
`../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:451-570`.
Its original final-state assertion could not distinguish a correctly queued
child from an incorrect child executed inline at the fork opcode. The corrected
row now records a shared order list and a fork-time local marker. It requires
the parent to append `"parent"` before the child appends `"child"`, and it
requires the child to observe marker `"before"` even though the parent changes
its own marker to `"after"` after the fork. Existing assertions continue to
prove that both activations receive the authenticated player through `args[1]`.
The correction is committed in the conformance repository as `b3b05ad`.

The corrected row passes pinned WSL Toast with one selected and 11,504
deselected in 4.44 seconds. It is red on committed Banteng `cec1f60`: the
parent, child, order, and snapshot observations are `[1, 0, 0, 0]` instead of
`[1, 1, 1, 1]`. The parent therefore continues correctly, but its queued child
is never selected after the root activation returns.

Barn's normative `../barn/spec/tasks.md:90-120` requires allocation and
queueing of a copied child environment followed by immediate parent
continuation. Even at delay zero, the child cannot run before the parent passes
the fork and runs only after the current interpreter run returns control.
`../barn/spec/tasks.md:403-450` makes the child independent background work:
later scheduler selection runs one task until return, suspension, or abort, and
a child error cannot affect its already-continued parent. Task-local inheritance
and complete queue selection remain outside this row.

Current Barn implements the parent boundary in
`../barn/vm/control.go:24-117`, which skips the child bytecode in the parent,
copies locals and frame context, and returns `FlowFork`.
`../barn/scheduler/task_runtime.go:94-199,408-418` creates the child, supplies
its ID, and resumes the parent until no fork boundary remains.
`../barn/scheduler/task_factory.go:199-281` builds the background child with
copied locals and queues it; `../barn/scheduler/scheduler.go:140-201` and
`../barn/server/input_processor.go:163-187` select it on a later scheduler
turn. Barn agrees for this unnamed-fork row. Its broader caller-frame and named
fork-ID-copy discrepancies are not observable here and require separate
durable rows before implementation.

At pinned Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`,
`src/execute.cc:2084-2113` handles `OP_FORK` by calling
`enqueue_forked_task2` and then continuing with the next parent opcode.
`src/tasks.cc:1258-1292` allocates the child ID, copies the runtime environment,
computes its start time, and queues it. `src/tasks.cc:1208-1232` retains that
copied activation and environment in `waiting_tasks`.
`src/tasks.cc:1629-1814` promotes due work only when `run_ready_tasks` begins
and executes at most one selected task, while `src/server.cc:851-856` invokes
the selector again on later server-loop iterations. The child then runs through
`src/tasks.cc:1772-1783` and `src/execute.cc:3375-3383` as independent
background work. `src/eval_env.cc:65-72` performs the value-preserving runtime
environment copy used by the corrected marker assertion.

Barn's normative contract, current Barn for this unnamed fork, and pinned Toast
agree on the fields frozen by the corrected row: the child is captured at the
fork boundary, the parent completes first, and the child later observes the
copied `args` and local marker. The row does not freeze task IDs, named-fork ID
binding, task-local state, full child frame fields, background limits, delayed
fork retention, cross-player scheduling, output ordering, or error handling.
Those surfaces remain excluded from this slice.

Banteng already has the required owned path. `MooCompiler` emits a separate
fork vector, `MooVm` advances the parent and produces a `ForkRequest`, and
`VmState.ForkRequest` copies the current locals and programmer. In
`MooRuntime.executeStored`, however, every child is inserted into the local
timed-task map. Once the root returns, the method exits before its due-task scan
and discards the zero-delay child. The smallest representation for the corrected
row places only a delay-zero child directly on the existing serialized runnable
queue. The existing loop still resumes the parent to completion before taking
the child, so the fork opcode never runs the child inline and the child sees the
captured locals. Nonzero delayed children keep their existing timed path. No
parser, compiler, VM, world, server, interface, adapter, helper, or persistent
task-registry change belongs to this slice.

## Sixth-row Banteng receipt

The focused Java regression is red with the parent observation present but the
child, exact order, and captured marker absent: the expected value is
`{#10, #10, {"parent", "child"}, "before"}`, while committed Banteng produces
`{#10, {}, {"parent"}, {}}`. The accepted implementation changes only
`MooRuntime.executeStored`: an exactly zero-delay child is appended to the
existing serialized runnable queue, while nonzero children retain the existing
timed-task path. Parent execution still continues to completion before the
runnable child is selected. The focused regression, the adjacent VM fork
boundary regression, the existing delayed-fork SQLite interruption regression,
and the Java 25 `check installDist` gate pass.

Managed `audit_user_connected_continues_after_zero_delay_fork` passes in
isolation with one selected and 11,504 deselected in 4.59 seconds. The complete
`connection_lifecycle_toast_oracle` fail-fast run then passes the first seven
rows and reaches
`audit_user_connected_confunc_calls_continue_after_fork_and_setting_task_perms`,
where its observation expression raises `E_RANGE` instead of returning the
expected `[1, 1, 1, 1, 1]`. Row six is accepted; the `confunc` and
`set_task_perms` row is the next causally relevant unchecked lifecycle target.

## Eighth row: nested suspension, callable verbs, and task permissions

The active durable row is
`audit_user_connected_confunc_calls_continue_after_fork_and_setting_task_perms`
at
`../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:695-849`.
It queues a zero-delay child from `user_connected`, calls a login-player-owned
`location:yield_once` that executes `suspend(0)`, resumes the complete parent
activation, changes the parent activation's programmer to the login player,
and calls login-player-owned executable `confunc` verbs on the location and
player. The child records its inherited user and programmer; the parent path
records the resumed `this`, both callable frames, and the lowered programmer.

The original row recorded but did not assert the fork child's programmer. It
therefore allowed an implementation to apply the later parent
`set_task_perms(user)` retroactively to the already-captured child. The corrected
row compares the entire child observation to `{p, player}`, where `player` is
the setup wizard and `p` is the login player. It is committed in the
conformance repository as `cf3f39e`. No child-versus-resumed-parent ordering was
added: Toast places this fork and suspended parent in different programmer
queues, so accumulated-usage selection does not guarantee one exact order.
The corrected row passes pinned WSL Toast with one selected and 11,504
deselected in 4.21 seconds.

Barn's corrected normative `../barn/spec/tasks.md:99-116` requires the fork to
capture and queue the child before the parent continues, without inline child
execution. `../barn/spec/tasks.md:198-205` requires suspension to preserve the
explicit VM, including its activation stack, environments, instruction
positions, task identity, and task-local value. `../barn/spec/tasks.md:289-290`
defines `task_perms()` as the current activation's programmer, distinct from
the calling activation returned by `caller_perms()`. The permission contract at
`../barn/spec/tasks.md:385-395` changes only the top activation: a non-wizard
may select only the current programmer, while a wizard may select another
object, and the check uses the running programmer rather than the connected
player. `../barn/spec/objects.md:461-468` separately requires an ordinary
callable verb to have the execute bit.

The older `../barn/spec/builtins/tasks.md:46-67` contradicts that corrected
contract by describing `set_task_perms` as simply wizard-only and
`task_perms()` as an alias for `caller_perms()`. Current Barn and pinned Toast
both disprove those statements; they are not authority for this slice.

Current Barn enters the hook through
`../barn/server/input_login.go:202-224,239-312` and
`../barn/scheduler/task_factory.go:61-105`. Its fork path is
`../barn/vm/control.go:24-117` followed by
`../barn/scheduler/task_runtime.go:408-418`. Ordinary callable dispatch in
`../barn/vm/op_verb.go:30-269` requires the execute bit and creates each callee
with the target `this`, caller activation's `this`, inherited player, supplied
arguments, and the called verb's owner as programmer. The nested
`builtinSuspend` at `../barn/builtins/tasks.go:104-149` preserves the VM through
`../barn/scheduler/task_runtime.go:230-257`; returning through
`../barn/vm/stack.go:100-119` restores the saved `user_connected` activation.
`builtinSetTaskPerms` at `../barn/builtins/tasks.go:181-226` checks and changes
the current programmer and top frame, while `builtinTaskPerms` at
`../barn/builtins/signatures.go:187-192` directly returns that current
programmer. Current Barn agrees with every observation frozen by the corrected
row. Its task-record ownership after a later suspension is broader than this
row and remains excluded.

At pinned Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`,
`src/execute.cc:2084-2113` and `src/tasks.cc:1258-1292` capture and queue the
fork. `src/execute.cc:2115-2198` dispatches an ordinary verb through
`call_verb2`; `src/db_verbs.cc:228-237,483-668` requires the execute bit, and
`src/execute.cc:663-782` installs the callee verb owner as its programmer along
with the exact receiver, caller, player, and arguments. `bf_suspend` at
`src/execute.cc:3520-3539`, `suspend_task` at `src/execute.cc:221-238`, and
`enqueue_suspended_task` at `src/tasks.cc:1296-1318` retain the complete nested
VM. `src/tasks.cc:1629-1814,1786-1795` later selects and restores it, and
`src/execute.cc:3253-3272` resumes its activation stack.

Pinned Toast's exact permission owners are `bf_set_task_perms` at
`src/execute.cc:3695-3707`, which permits a different target only for the
current wizard programmer and then changes `RUN_ACTIV.progr`, and
`bf_task_perms` at `src/execute.cc:3710-3717`, which returns that same current
activation field. `bf_caller_perms` at `src/execute.cc:3720-3729` independently
reads the preceding activation. Registrations at `src/execute.cc:3786-3788`
freeze one object argument for the setter and zero arguments for both getters.
The child therefore retains the setup wizard programmer captured before the
parent changes permissions, both `confunc` frames run under their login-player
verb owner, and the restored parent retains its explicit login-player
programmer.

The corrected row is red on committed Banteng `1fa90bf`: it returns
`[0, 1, 0, 0, 0]` instead of `[1, 1, 1, 1, 1]`. The successful second element
proves that the complete nested call resumes and records the location. The
other observations all execute `task_perms()`. Banteng's direct
`BuiltinCatalog` implements `set_task_perms` but has no `task_perms` case, so
the child and both parent-side callable paths receive `E_VERBNF` before their
assignments. The earlier `E_RANGE` report was only the original observer
indexing the empty child value; it was not the semantic failure.

The smallest Java representation is a direct `BuiltinCatalog.invoke` case:
zero arguments return an object reference for the `programmer` already supplied
from the current VM frame, while any argument returns `E_ARGS`. The existing
catalog effect classification must mark this read-only builtin as pure. The
existing `VmState` programmer field, fork snapshot, suspension stack,
`set_task_perms` result, callable dispatch, compiler, parser, runtime queues,
and world path require no change. Permission-denial cases, caller permissions,
post-lowering task ownership, full frame introspection, background limits, and
cross-programmer scheduling remain outside this row and require their own
durable authority.

## Eighth-row Banteng receipt

The focused Java regression is red with the expected nested-suspension marker
present but the child and all permission-dependent callable observations empty:
the expected value is
`{{#10, #8}, #11, {#11, #10, #10}, {#10, {}, #10}, {#10, #10}}`,
while Banteng produces `{{}, #11, {}, {}, {}}`. The accepted implementation
adds only the direct zero-argument `task_perms` result and its pure effect
classification in `BuiltinCatalog`. The focused regression, four adjacent
fork/suspend/call-frame regressions, formatting, and the Java 25
`check installDist` gate pass.

Managed
`audit_user_connected_confunc_calls_continue_after_fork_and_setting_task_perms`
passes in isolation with one selected and 11,504 deselected in 4.63 seconds.
The complete `connection_lifecycle_toast_oracle` fail-fast run then passes the
first eight rows and reaches `audit_user_client_disconnected_hook`, where the
final observation is zero instead of one. Row eight is accepted; the client
disconnect hook is the next causally relevant unchecked lifecycle target.
