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

## Ninth row: client disconnect notification

The active durable row is `audit_user_client_disconnected_hook` at
`../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:851-963`.
The original row proved only that a genuine client close eventually called
`user_client_disconnected` with `args[1]` equal to the authenticated player. It
did not freeze the accepting handler, full root frame, once-only invocation, or
whether MOO already considered the connection disconnected when the hook ran.

The corrected row appends each invocation's
`{this, player, caller, args, argstr, connection_info_succeeds}` to one
observation list. The hook catches the exact `E_INVARG` from
`connection_info(args[1])`; the final assertion requires the singleton
`{{#0, p, #-1, {p}, "", 0}}`. This freezes exactly one call on the accepting
handler, the authenticated player frame, root caller, exact argument list and
empty argument string, plus logical disassociation before hook execution. The
correction is committed in the conformance repository as `e7b2e70` and passes
pinned WSL Toast with one selected and 11,504 deselected in 4.78 seconds.

Barn's `../barn/spec/login.md:69,125-140,200-226` and
`../barn/spec/server.md:133-183` document only the older generic
`#0:user_disconnected(player)` hook. They do not distinguish a genuine client
or network close from a forced boot, do not name the accepting listener, and
give contradictory cleanup ordering. `../barn/spec/vm.md:6-9` makes verified
Toast and the durable managed row authoritative over those stale passages. No
corrected normative Barn section currently defines `user_client_disconnected`;
that absence is recorded rather than filled from the nearby generic hook.

Current Barn receives reader failure in
`../barn/server/input_processor.go:65-196` and serializes it into
`processDisconnect` at `../barn/server/input_processor.go:314-365`.
`processDisconnect` captures the player and accepting handler, removes the
connection and player mappings, performs connection cleanup, and then calls
`callUserHook(handler, "user_client_disconnected", player)`.
`../barn/server/input_login.go:202-224` supplies exactly one player argument,
ignores a normal result, and logs a non-`E_VERBNF` exception without undoing
cleanup. Barn therefore agrees on hook name, handler, player, args, empty
argument string, once-only serialized close, and MOO-logical disassociation.
Its server-task construction at
`../barn/scheduler/task_factory.go:61-105`, however, sets the root caller to the
player rather than Toast's `#-1`. Barn also routes forced transport closes from
`../barn/server/connection_manager.go:187-209,550-566` through the same client
disconnect path because it does not retain the disconnect cause. Those Barn
differences are not copied into Banteng.

At pinned Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`, a
genuine network close reaches `server_close` from `src/network.cc:1468`.
`src/server.cc:1544-1557` logs the close, sets `h->disconnect_me = true`, calls
`call_notifier(h->player, h->listener, "user_client_disconnected")`, and only
then frees the internal server handle. `src/server.cc:519-527` supplies exactly
`{player}` with empty `argstr` to `run_server_task` and discards the result.
`src/execute.cc:3279-3336` creates the root frame with `this` equal to the
accepting listener, `player` equal to the disconnected authenticated player,
`caller == #-1`, programmer equal to the resolved hook verb owner, the exact
argument list, and empty command strings.

Toast retains the internal handle until `free_shandle` removes it at
`src/server.cc:188-194`, but that is not MOO-visible connected state.
`bf_connection_info` at `src/server.cc:3032-3043` returns `E_INVARG` whenever
the retained handle has `disconnect_me` set. `bf_connected_players` at
`src/server.cc:2752-2775` likewise excludes such a handle. The corrected row
uses `connection_info`, which Banteng already implements, so it freezes logical
disassociation without introducing a separate `connected_players` prerequisite.

Pinned Toast routes a server-initiated boot through `user_disconnected`, not
this hook, at `src/server.cc:875-910,1793-1807`. Cross-listener reconnection has
its own old-listener client-disconnect call at `src/server.cc:1698-1705` and is
covered by the following lifecycle row. Forced boot, unlogged timeout,
recycling, reconnect, graceful shutdown, panic shutdown, hook return/error or
suspension behavior, and multiple simultaneous connections remain outside this
row.

The corrected row is red on committed Banteng `bc2cb01`: the final singleton
comparison returns zero because no observation is recorded. `MooServer` already
routes client EOF through `MooRuntime.closeConnection`, but that method only
removes the world connection and runtime delimiter state. The smallest owned
representation changes only `MooRuntime.closeConnection`: capture its existing
`ConnectionState` and authenticated player, remove the connection from the
world and runtime map so MOO observes `E_INVARG`, then synchronously invoke the
accepting handler's optional `user_client_disconnected` with the existing exact
server-task `verbLocals` frame. Missing hooks and pre-login negative players do
not invoke it; MOO hook return and error outcomes are ignored. No server,
world, builtin, parser, compiler, VM, interface, helper, sender, adapter, or
generic disconnect-cause abstraction belongs to this slice.

## Ninth-row Banteng receipt

The focused Java regression is red because the connection association is
removed but the disconnect observation remains `{}`. The accepted
implementation changes only `MooRuntime.closeConnection`: it captures the
existing connection state and authenticated player, removes both connection
maps, and invokes the accepting handler's optional hook with the exact frozen
frame. The focused regression, four adjacent lifecycle regressions, formatting,
and the Java 25 `check installDist` gate pass.

Managed `audit_user_client_disconnected_hook` passes in isolation with one
selected and 11,504 deselected in 5.15 seconds. The complete
`connection_lifecycle_toast_oracle` fail-fast run then passes the first nine
rows and reaches `audit_user_reconnected_cross_listener_hooks`. Its final
observation is `[0, 0, 4]` instead of `[1, 1, 4]`: the old-listener client
disconnect and new-listener connected hooks are absent, while the unset
`user_reconnected` observation correctly remains a list with type code four.
Row nine is accepted; cross-listener
reconnection is the next causally relevant unchecked lifecycle target.

## Tenth row: cross-listener reconnection

The active durable row is `audit_user_reconnected_cross_listener_hooks` at
`../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:967-1091`.
Its original assertions proved only eventual old-listener
`user_client_disconnected(player)`, eventual new-listener
`user_connected(player)`, and absence of `user_reconnected`. They did not
freeze either root frame, invocation count, hook order, or the old-hidden and
new-active connection transition.

The corrected row records append-only singleton full frames, an exact shared
order, and connection metadata. It requires the old hook to observe
`connection_info(player) == E_INVARG`, then the new hook to observe the new
accepted connection's `source_port`; it requires order
`{"old_client", "new_connected"}`, an untouched empty `user_reconnected`
sentinel, and final `connection_info(player)["source_port"]` equal to the new
listener port. `source_port` is intentional: the managed fixture and Banteng's
server metadata use it for the accepting local port, while `destination_port`
is the peer port. The correction is committed in the conformance repository as
`415dc56` and passes pinned WSL Toast with one selected and 11,504 deselected in
5.73 seconds.

Barn's `../barn/spec/login.md:85-106,230-239` says every reconnect boots the old
connection and calls `user_reconnected`, and says lifecycle hooks live on
`#0`. Both statements are stale for listener-owned Toast behavior.
`../barn/spec/vm.md:3-9` makes verified Toast plus the durable managed row the
observable authority. There is no durable same-listener row, so this slice
freezes only two different handler objects; port equality is not the branch
condition.

Pinned Toast accepts the returned login player at
`src/tasks.cc:913-958`, changes the new task queue to that player, moves queued
work and pending input, and calls `player_connected`. At
`src/server.cc:1658-1673`, `player_connected` resolves the existing and new
handles, assigns the player and connection time to the new handle, and briefly
has two internal handles for the same player. It captures the old listener,
sends configured redirect messages, and closes or marks the old handle before
any lifecycle notifier at `src/server.cc:1675-1697`.

The branch at `src/server.cc:1698-1705` compares listener object identity. An
equal listener receives only `user_reconnected`. Different listeners cause the
new handle to be marked `disconnect_me`, old-listener
`user_client_disconnected` to run, the new handle to be re-enabled, and
new-listener `user_connected` to run, in that exact order. During the old hook,
`bf_connection_info` at `src/server.cc:3032-3043` returns `E_INVARG`; during
the new hook and afterward, it returns the new handle metadata. All notifiers
use `src/server.cc:519-527`, supplying exactly `{player}` and empty `argstr` and
discarding outcomes. `src/execute.cc:3279-3336` freezes each root frame:
`this` is the branch-selected listener, `player` is the authenticated player,
`caller == #-1`, programmer is the resolved hook owner, `args == {player}`,
and command strings are empty.

Current Barn enters `loginPlayer` through
`../barn/server/input_processor.go:423-460`. Its implementation at
`../barn/server/input_login.go:239-301` detects an existing player connection,
assigns the new connection, replaces the routing map, and always calls old
`user_client_disconnected` followed by new `user_connected`. It neither compares
listener identity nor closes the old connection. Thus its cross-listener hook
names and order agree, but it exposes the new connection during the old hook,
can later deliver a duplicate old disconnect hook, and never implements the
same-listener branch. Its shared notifier at
`../barn/server/input_login.go:202-224` supplies the player argument and ignores
normal return, but `../barn/scheduler/task_factory.go:61-105` incorrectly sets
the root caller to the player rather than `#-1`. These Barn divergences are not
copied into Banteng.

The corrected row is red on committed Banteng `2787ced`: it returns
`[0, 0, 0, 1, 0]` instead of five ones. Both required hook frames and their
order are absent; the empty `user_reconnected` sentinel is correctly preserved;
and final `connection_info` still resolves the retained old connection rather
than the new listener's metadata. `MooRuntime.executeLogin` currently sees the
old player metadata, marks the returned association non-fresh, associates the
new connection without evicting the old one, and invokes no reconnect hook.

The smallest representation changes only `MooRuntime.executeLogin` for a
returned-player cross-listener association. Before switching the new
connection, it scans the existing concrete runtime connection entries for a
different connection already associated with that player and compares the two
stored listener handlers. For different handlers, it captures the old ID,
calls the already-frozen `closeConnection(oldId)` before switching the new
connection so the old hook sees no logical player connection, switches the new
connection to the player, and invokes the existing exact `user_connected` path
on the new handler. It never looks up `user_reconnected` in this cross branch.
The old ID must be captured before mutating the `LinkedHashMap`; no iterator is
kept across close. Fresh-login behavior remains unchanged.

Same-listener reconnection, physical old-socket closure, redirect messages,
task-queue transfer, pending-input discard, refcount-deferred close,
`switch_player`, hook outcome independence, multiple prior connections, and
server shutdown remain excluded. `MooServer` owns sockets in an unkeyed set, so
closing the displaced physical socket would require a separate server contract
and durable row; no helper, interface, sender, adapter, world mutator, or socket
callback belongs to this slice.

## Tenth-row Banteng receipt

The strengthened Java regression is red before the production change because
neither cross-listener hook runs and the retained old association supplies the
final connection metadata. The accepted implementation changes only
`MooRuntime.executeLogin`: it captures a different-handler connection already
associated with the returned player, closes that logical connection before
switching the replacement, and then uses the existing new-handler
`user_connected` path. The focused regression, five adjacent lifecycle
regressions, formatting, and the Java 25 `check installDist` gate pass.

Managed `audit_user_reconnected_cross_listener_hooks` passes in isolation with
one selected and 11,504 deselected in 6.47 seconds. The complete
`connection_lifecycle_toast_oracle` fail-fast run then passes the first ten rows
and reaches `audit_connect_timeout_server_option`. That row expects
`typeof(#0.audit_timeout_seen) == 1`, but Banteng returns type code four.
Row ten is accepted; the `connect_timeout` server option is the next causally
relevant unchecked lifecycle target.

## Eleventh row: unauthenticated connect timeout

The active durable row is `audit_connect_timeout_server_option` at
`../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:1093-1208`.
Its original form set `$server_options.connect_timeout = 1`, opened one
unauthenticated connection, waited 2.5 seconds, and asserted only that
`user_disconnected` stored an object-typed argument. It did not freeze idle
reset, listener routing, the root frame, or once-only delivery.

The corrected row was introduced as `c3ccfbc` and finalized through
`d21cbca`, `2b0400e`, and `39db818`. The follow-up corrections remove
dependencies on the predefined `OBJ` symbol, the unrelated `index` builtin,
and object relational ordering, and widen the timing margin after a live WSL
schedule exposed the 600-millisecond probe as flaky. The final row sets a
three-second timeout, records append-only full frames, observes no hook after
1.5 seconds, sends one ordinary unauthenticated line, observes no hook 2.5
seconds later past the original connection deadline, then waits two seconds
and requires exactly one frame. The frame requires `this == #0`, an
object-typed `player` distinct from `#-1`, `caller == #-1`,
`args == {player}`, and empty `argstr`. A final wait proves the observation
remains exactly once.
Physical socket closure, timeout output, default and invalid option values,
listener-specific override precedence, authenticated and outbound exclusions,
and exact boundary timing remain outside this row.

The final row passes pinned WSL Toast with one selected and 11,504 deselected
in 11.25 seconds. Its semantic predecessor is red on committed Banteng
`baec58c`: both pre-timeout empty checks pass, but the final exact-frame
assertion returns scalar zero because the observation remains empty. The final
row changes only independent assertion spellings and timing margins; the
missing Banteng observation is unchanged.

Barn's `../barn/spec/login.md:64-71` says an unlogged connection times out after
`connect_timeout`, calls `#0:user_disconnected(connection)` if
`do_login_command` ran, closes the connection, and sends an
implementation-defined message. `../barn/spec/server.md:145-170` records an
integer option with default 300 seconds and the same unauthenticated lifecycle.
The spec is stale or incomplete about the actual listener receiver, frame,
message order, and activity reset.

Current Barn enters through
`../barn/server/connection_manager.go:308-377` and
`../barn/server/input_processor.go:65-145`. It reads the global option, accepts
only a positive integer over its five-minute fallback, applies a fresh read
deadline before each unauthenticated read, sends the timeout message, calls the
accepting listener's `user_disconnected` with the negative connection object,
and returns so deferred cleanup closes the transport. Its notifier at
`../barn/server/input_login.go:202-224` supplies one object argument, but
`../barn/scheduler/task_factory.go:63-105` incorrectly sets the root caller to
the negative connection instead of `#-1`.

Pinned Toast defines `DEFAULT_CONNECT_TIMEOUT == 300` in
`src/include/options.h:234-248`. `src/server.cc:530-542` dynamically resolves
`listener.server_options` first and falls back to `#0.server_options` only when
the listener lacks its own options object. `src/server.cc:1496-1516` initializes
an inbound unauthenticated handle with a negative object player and current
`last_activity_time`; `src/server.cc:1577-1582` refreshes that time on each
received line. Successful authentication sets `connection_time` and excludes
the handle from this timeout.

The timeout scan at `src/server.cc:903-934` applies only to inbound handles
whose `connection_time == 0`. A missing option uses 300 seconds. A present
value enables timeout only when it is a positive integer; zero, negative, or a
wrong type disables it without falling back. The scan uses whole seconds and
strictly requires `now - last_activity_time > timeout`. It calls the accepting
listener's `user_disconnected` before logging, optional `timeout_msg`, network
close, and handle release. `src/server.cc:518-527` supplies exactly the negative
player argument, and the server-task root established through
`src/tasks.cc:1825` and `src/execute.cc:3279-3336` supplies the frozen
`this`, `player`, `caller`, `args`, and `argstr` values. Hook outcomes do not
prevent the subsequent close path.

Committed Banteng has no timeout owner. `MooServer.handleConnection` blocks in
`BufferedReader.readLine`, while `MooRuntime.openConnection` stores only the
listener and print-message flag. `MooRuntime.executeLogin` resolves
`server_options` only for `trusted_proxies`; `executeLine` records no activity
time; and `closeConnection` calls only authenticated
`user_client_disconnected`. Therefore an idle negative connection remains
present indefinitely and the row's sentinel stays `{}`.

The smallest representation remains inside concrete `MooRuntime` and its
existing `ConnectionState`. The state gains a monotonic last-activity time.
`openConnection` starts one virtual timeout monitor for the negative
connection after the initial login task; `executeLine` refreshes activity
before processing an unauthenticated line. The monitor repeatedly resolves the
same listener-first dynamic option, applies the source-backed missing and
invalid-value rules, and compares floored monotonic seconds with the strict
greater-than boundary. If the same connection is still present and negative,
it invokes the accepting handler's optional `user_disconnected` through the
existing exact server-task path, then removes the logical runtime/world
connection so a later socket-finally close cannot repeat the hook. It exits
without notification once the connection closes or authenticates.

This slice does not add an interface, helper abstraction, sender, adapter,
world mutator, scheduler framework, or socket callback. Physical socket close
and timeout-message delivery remain excluded because `MooServer` owns sockets
in an unkeyed set and the corrected row deliberately does not freeze those
surfaces.

## Eleventh-row Banteng receipt

The focused Java regression is red before the production change at the absent
eventual timeout frame. The accepted implementation changes only
`MooRuntime` and its existing `ConnectionState`: it records monotonic activity,
starts the virtual timeout monitor after initial login processing, refreshes
activity on unauthenticated input, dynamically applies the listener-first
option contract, invokes the exact `user_disconnected` frame, and removes the
logical connection once. The focused regression, five adjacent lifecycle
regressions, formatting, and the Java 25 `check installDist` gate pass.

Managed `audit_connect_timeout_server_option` passes in isolation with one
selected and 11,504 deselected in 12.04 seconds. The complete
`connection_lifecycle_toast_oracle` fail-fast run then passes the first eleven
rows and reaches `audit_flush_command_flushes_pending_input`. That row expects
`{\"\"}` but observes `{}`. Row eleven is accepted; flush-command pending-input
semantics are the next causally relevant unchecked lifecycle target.

## Twelfth row: flush held pending input

The active durable row is `audit_flush_command_flushes_pending_input` at
`../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:1209-1326`.
Its original form enabled `hold-input`, set `flush-command` to `.flush`, sent
two identical commands, sent the flush token, released hold, sent one fresh
command, and required the execution record to equal `{\"\"}`. A server that
discarded every held line without ever owning a pending queue could pass.

The corrected row is committed in the conformance repository as `0f52393`.
It sends distinct `auditflush first` and `auditflush second` lines while held,
then sends mixed-case `.FlUsH`. It requires exact client output, in order:

1. `>> Flushing the following pending input:`
2. `>>     auditflush first`
3. `>>     auditflush second`
4. `>> (Done flushing)`

After releasing hold it retains the original final assertion that one fresh
`auditflush` executes with empty `argstr`. The strengthened row therefore
freezes queue presence, FIFO reporting and removal, case-insensitive exact
flush-token matching, consumption of the flush line, no resurrection on
release, and continued ordinary dispatch afterward. It passes pinned WSL
Toast with one selected and 11,504 deselected in 8.00 seconds.

The corrected row is red on committed Banteng `f68cebc`: the flush send
returns no output instead of the four exact lines. Banteng then also retains
the earlier final `{}` observation because the connection-option calls have no
implemented effect.

Barn's `../barn/spec/server.md:325-328` says WebSocket and stream transports
share held-input flush behavior. `../barn/spec/builtins/network.md:224-243`
documents `set_connection_option` and `connection_options`, but omits
`flush-command`; the normative option surface is incomplete.

Current Barn enters through
`../barn/server/input_processor.go:65-229`. It serializes transport input and
calls `builtins.HandleHeldInput` before read delivery or command dispatch.
`../barn/builtins/network.go:108-239,561-615` owns per-player connection
options and a held-command queue. Barn agrees that an explicitly configured
flush token is consumed before normal dispatch and that release normally
reinjects remaining held commands. It diverges by defaulting the token to the
empty string, comparing case-sensitively, emitting no flush feedback, and
clearing only its command queue while leaving its duplicate HTTP held-input
buffer populated.

Pinned Toast has one authoritative pending-input owner,
`tqueue.first_input`, and one per-queue `flush_cmd` at
`src/tasks.cc:168-184`. New queues read
`$server_options.default_flush_command` and default to `.flush` at
`src/tasks.cc:423-428,447-468`. The task connection-option table at
`src/tasks.cc:995-1057` handles option names case-insensitively:
`flush-command` stores a nonempty string and otherwise disables the token;
`hold-input` stores MOO truth and makes remaining input runnable when released.

Raw Telnet line construction preserves spaces and tabs through
`src/network.cc:386-415`. `src/server.cc:1535-1540` refreshes activity and
passes the full normalized line to `new_input_task`. At
`src/tasks.cc:1171-1180`, that owner uses `strcasecmp` for an otherwise exact
match. A matching line calls `flush_input` and is never enqueued, assigned a
task ID, or given a MOO frame. `src/tasks.cc:1144-1168` dequeues every existing
input-kind task in FIFO order, frees it, and emits the exact header, one line
per removed input, and completion message through direct network notification.
It preserves already-running, forked, suspended, waiting, and later input.

The public builtin is registered at `src/server.cc:3385-3388` as
`set_connection_option(OBJ, STR, ANY)`. Its body at
`src/server.cc:3063-3083` returns `E_PERM` when the target differs from a
nonwizard programmer, `E_INVARG` for a missing/disconnecting target or unknown
option, and zero on success. The task table accepts any value for recognized
options: nonempty strings enable `flush-command`; other values disable it;
MOO truth controls `hold-input`.

Committed Banteng has none of this vertical surface. `MooServer` correctly
continues reading and forwards every line to serialized
`MooRuntime.executeLine`, but `ConnectionState` has no hold flag, flush token,
or pending FIFO. `BuiltinCatalog` has no `set_connection_option`; invocation
falls through to `E_VERBNF` and its effect class is unimplemented. There is no
runtime connection-option request to bridge a MOO task to ephemeral connection
state.

The smallest representation extends the existing explicit effect channel,
not package ownership. `BuiltinCatalog.Result` gains one closed
`ConnectionOptionRequest` value after validating arity, types, target
connection, permission, and the two row-required option names. `MooVm` stages
the request on `VmState`; `MooRuntime.executeStored` applies staged requests in
task order. Concrete `ConnectionState` alone owns `holdInput`, `flushCommand`,
and a FIFO of raw lines. `executeLine` compares a nonempty flush token with the
raw line case-insensitively before hold or normal dispatch, returns exact FIFO
feedback, clears the queue, consumes the flush line, queues other input while
held, and otherwise follows the existing dispatch path. `hold-input = 0`
updates the flag; this row proves the pending queue is already empty at that
point.

This slice does not add an interface, callback, sender, adapter, alternate
queue, server mutation, world record, generic effect framework, or physical
socket behavior. Default flush-command initialization, whitespace variants,
empty-queue feedback, non-inband input kinds, `force_input`, an active
`read()`, releasing a nonempty queue, and `connection_options` queries remain
excluded.

## Twelfth-row Banteng receipt

The strengthened conformance row was corrected once more in commit `c7ec8ca`
to replace its incidental `listappend()` dependency with MOO list expansion.
That change preserves the distinct held lines, mixed-case flush token, exact
four-line FIFO feedback, and final singleton empty `argstr` assertion. The
corrected row passes pinned WSL Toast with one selected and 11,504 deselected
in 8.07 seconds.

The Banteng slice adds only the closed `ConnectionOptionRequest` data record to
the existing builtin-result effect path, ordered staging on `VmState`, concrete
application by `MooRuntime`, and per-connection hold, flush-token, and raw-line
FIFO state. The focused Java regression and the complete Java 25
`check installDist` gate pass. The corrected managed row passes with one
selected and 11,504 deselected in 8.42 seconds.

The complete managed `connection_lifecycle_toast_oracle` family passes the
first twelve rows and stops at
`audit_listener_print_messages_suppresses_connect_msg`: Banteng returns no
output where Toast requires `I couldn't understand that.` after the suppressed
listener connect message. The flush slice is accepted; listener
`print-messages` command output is the next causally relevant unchecked
lifecycle target.

## Thirteenth row: corrected listener print-messages proof

The original `audit_listener_print_messages_suppresses_connect_msg` row did
not observe the behavior named in its description. Its listener
`do_login_command` returned a player for Toast's automatic initial blank input,
so authentication and any `connect_msg` occurred during `new_connection`.
The harness drains and discards connection-open output before the first
assertion. The later expected `I couldn't understand that.` therefore tested
only post-login unknown-command fallback; a server that incorrectly printed
the connect message could still pass.

Pinned WSL Toast source identity was reverified as
`aecc51e9449c6e7c95272f0f044b5ba38948459e`. In `src/server.cc`,
`bf_listen` at lines 3090-3165 defaults `print_messages` to false and enables
it for a MOO-truthy `print-messages` option. `new_slistener` stores the flag;
`server_new_connection` at lines 1455-1492 copies it to the accepted
connection and queues the initial blank input. `player_connected` at lines
1658-1724 emits `connect_msg`, defaulting to `*** Connected ***`, only when
that accepted connection's flag is true.

Conformance commit `e3d8dbd` replaces the invalid observation with paired
controls in the same isolated row. The handler returns zero for the automatic
blank input and authenticates only on each observed nonblank send. The false
listener must produce exact empty output; the true listener must produce exact
`*** Connected ***` output. Both sends record their exact `argstr`, proving the
assertions belong to the inputs that authenticated the two connections.

The corrected row passes pinned WSL Toast with one selected and 11,504
deselected in 5.38 seconds. It also passes committed Banteng `3b70123`
unchanged, with one selected and 11,504 deselected in 5.17 seconds. The earlier
Banteng failure was a test-authority defect, not a `print-messages` production
defect; no Java change is kept for this row.

The complete managed `connection_lifecycle_toast_oracle` family then passes
the first thirteen rows and stops at `audit_boot_player_messages`: after
`boot_player`, Toast requires `*** Disconnected ***` on the booted connection,
while Banteng returns no output. Boot-player messaging is the next causally
relevant unchecked lifecycle target.

## Fourteenth row: boot one live player connection

The active durable row is `audit_boot_player_messages`. Conformance commit
`f97de48` requires the controlling `boot_player()` task to complete before it
observes the booted connection. The corrected row passes pinned WSL Toast with
one selected and 11,504 deselected in 4.90 seconds. Committed Banteng
`9787106` fails the same row because the builtin is absent and returns
`E_VERBNF`; that is the production baseline for this slice.

Pinned Toast registers `boot_player(OBJ)` in `src/server.cc`. `bf_boot_player`
at lines 2960-2970 accepts exactly one object, permits the target player or a
wizard programmer, calls `boot_player`, and returns zero. `boot_player` at
lines 1793-1799 marks an existing network handle for disconnection and treats
an absent or already disconnected target as a successful no-op. The sweep at
lines 904-916 first invokes the accepting listener's `user_disconnected`, then
uses `send_message` for the boot message when the accepted connection's
`print_messages` flag is true, and finally closes the network handle.

Toast's `send_message` at lines 545-563 resolves the accepting listener's
`server_options` before falling back to #0. A string `boot_msg` emits one line;
a list emits its string elements in order and ignores nonstrings; a present
wrong-typed value emits nothing; an absent value emits the default
`*** Disconnected ***`. A connection marked for disconnection is already
treated as disconnected by connection queries and notification before the
listener hook runs.

Barn agrees on self-or-wizard permission and an absent-target no-op, but its
current implementation hardcodes an unconditional disconnect message and does
not route the exact listener hook and option lookup. Its written specifications
also disagree about permission, event order, and missing-target behavior.
Pinned Toast therefore decides all three conflicts for Banteng.

The smallest Banteng representation extends only existing concrete owners.
`BuiltinCatalog.Result` gains one optional boot-player target after exact
arity/type/permission validation. `MooVm` stages ordered target IDs on
`VmState`, and
`MooRuntime.executeStored` applies it in task order through the same explicit
effect path already used for connection options. The runtime resolves the
target through its existing live-connection map, removes the logical/world
connection before invoking the accepting listener's `user_disconnected`,
resolves the exact boot-message option, and asks the existing
`ListenerControl` owner to write those lines and close that connection ID.
`MooServer` indexes its existing socket and writer ownership by connection ID;
the connection thread's eventual `closeConnection` becomes a no-op because the
logical runtime entry is already gone.

This slice does not add a new interface, sender, adapter, helper layer, generic
effect framework, world mutation, or alternate connection registry. The
durable row freezes builtin completion and the default message on a primary
`print-messages` listener. Focused Java coverage additionally freezes logical
removal before the listener hook and physical EOF. Custom `boot_msg` strings
and lists, wrong-typed options, `print-messages = false`, missing targets, and
the permission matrix remain outside this exact row.

## Fourteenth-row Banteng receipt

The kept Banteng slice extends the existing explicit builtin-result effect
path with one optional boot target and ordered VM staging. `MooRuntime` removes
the target connection from runtime and world state before invoking the
accepting listener's `user_disconnected`, resolves the Toast-compatible
message, and uses the existing `ListenerControl` boundary. `MooServer` now
indexes its existing socket and writer ownership by connection ID, serializes
the final write, and closes exactly that target. No new interface, request
record, sender, adapter, or generic effect framework was added.

The focused real-socket Java regression passes after formatting and proves the
controlling task returns, the hook observes prior logical disassociation, the
target receives `*** Disconnected ***`, and EOF follows. The corrected managed
row passes with one selected and 11,504 deselected in 5.22 seconds. The full
Java 25 `clean check installDist` gate passes.

The complete managed `connection_lifecycle_toast_oracle` fail-fast run passes
the first fourteen rows and stops at `audit_recycle_active_player_message`:
Banteng emits no output where Toast requires `*** Recycled ***`. The boot slice
is accepted; recycling one active player is the next causally relevant
unchecked lifecycle target.

## Fifteenth row: recycle one active player

The active durable row is `audit_recycle_active_player_message`. It creates a
fresh player, authenticates one primary-listener connection as that player,
runs `recycle(player)` as wizard, and requires exact target output
`*** Recycled ***`. The row is red on committed Banteng `eb374f1`: one
selected and 11,504 deselected, expected the banner and observed no output in
5.81 seconds. It passes pinned WSL Toast at source commit
`aecc51e9449c6e7c95272f0f044b5ba38948459e` with one selected and 11,504
deselected in 4.91 seconds.

Barn `spec/objects.md:309-327` specifies the object lifecycle only: call the
target's `recycle` verb, detach its relationships, and mark its slot recycled.
It does not specify what happens to an active connection. The generic close
claim in `spec/server.md:179-185` says any close calls `user_disconnected`,
which conflicts with Toast's dedicated recycle branch.

Barn implements the public path in `builtins/objects.go:305-381`: after the
target lifecycle verb and store recycle it calls
`ConnManager.RecyclePlayer`. `server/connection_manager.go:565-576` hardcodes
`*** Recycled ***`, sends it, and closes the socket. Barn therefore agrees
with the row's default banner-before-close behavior, but it ignores listener
`printMessages` and configurable message options. Its normal reader cleanup
then reaches `user_client_disconnected`, another Toast divergence.

Pinned Toast `src/objects.cc:788-926` validates one object, applies
owner-or-wizard control, runs the target object's `recycle` verb, detaches its
contents and hierarchy, destroys the object, and completes the builtin.
`src/db_objects.cc:313-370` removes a destroyed player from the player set and
makes the object invalid. In the next main-loop connection sweep,
`src/server.cc:893-903` recognizes an authenticated handle whose player is no
longer valid, conditionally sends `recycle_msg` with default
`*** Recycled ***`, then closes and frees the handle. This branch invokes
neither `user_disconnected` nor `user_client_disconnected`; those belong to
different close causes.

Toast's shared message path at `src/server.cc:529-567` resolves the accepting
listener's `server_options` before #0 fallback. A string `recycle_msg` emits
one line; a list emits only its string elements in order; a present unsupported
type emits nothing; an absent option emits the default. The entire message is
suppressed when that accepted listener's `print_messages` flag is false.

The smallest Banteng representation changes no builtin, world, VM, interface,
sender, adapter, or effect record. After each existing VM execution boundary,
`MooRuntime` reconciles its concrete live connections: a positive attached
player absent from the current world is recycled. It removes that logical and
world connection without invoking either disconnect hook, resolves the exact
recycle message, and uses the existing concrete final-lines-plus-close method
on `ListenerControl`. `MooServer` already serializes that write and closes the
selected connection ID.

The durable row freezes wizard recycling of an active player and the default
banner on a primary `print-messages` listener. Focused Java coverage also
freezes successful controlling-task completion and EOF after the banner.
Owner permissions, target `recycle`-verb behavior, custom/list/wrong-type
`recycle_msg`, print suppression, and exact logical-visibility timing remain
outside this row.

## Fifteenth-row Banteng receipt

The kept Banteng slice changes only `MooRuntime` and one real-socket regression.
After each existing VM execution boundary, the runtime reconciles live positive
player attachments against the current world, removes an attachment whose
player was recycled without invoking a disconnect hook, resolves the exact
listener/#0 `recycle_msg`, and reuses the existing serialized final-lines-plus-
close transport operation. No builtin, VM, world, server, interface, sender,
adapter, or effect record was added or changed.

The focused Java regression passes after formatting and proves successful
controlling-task completion, the exact default recycle banner, and EOF. The
managed Banteng row passes with one selected and 11,504 deselected in 5.10
seconds. The full Java 25 `clean check installDist` gate passes.

The complete managed `connection_lifecycle_toast_oracle` fail-fast run passes
the first fifteen rows and stops at `audit_login_timeout_message`: Banteng
emits no output where Toast requires
`*** Timed-out waiting for login. ***`. The recycle slice is accepted; login
timeout messaging is the next causally relevant unchecked lifecycle target.

## Sixteenth row: unauthenticated login timeout message

The active durable row is `audit_login_timeout_message`. It sets
`$server_options.connect_timeout` to one second, opens a primary-listener
connection without authenticating it, waits 2.5 seconds, and requires exact
output `*** Timed-out waiting for login. ***`. The complete lifecycle run is
red on committed Banteng `cf24822`: the first fifteen rows pass, then this row
expects the banner and observes no output. The isolated row passes pinned WSL
Toast with one selected and 11,504 deselected in 6.57 seconds.

Barn `spec/server.md:151-170` says that an unlogged connection times out after
`connect_timeout`, and `spec/server.md:179-185` gives a generic
`user_disconnected` claim for connection close. It does not specify the
negative unauthenticated connection object, timeout hook frame, message option
lookup, print suppression, or timeout action ordering. The connection-loss
sequence in `spec/login.md:221-226` does not fill those timeout-specific gaps.

Barn's public connection path is `server/input_processor.go:63-158`.
`HandleConnection` selects a positive integer timeout, applies it as a read
deadline while unauthenticated, and on timeout sends the hardcoded default
banner before calling the accepting listener's `user_disconnected` with the
negative connection ID. Its deferred generic cleanup reaches
`processDisconnect` at lines 314-366, which removes the connection maps before
any hook and calls `user_client_disconnected` only for an authenticated
connection. Barn therefore agrees only on the default banner and positive
`connect_timeout`; its message-before-hook order, fixed message, and cleanup
path diverge from Toast.

Pinned WSL Toast source identity was reverified as
`aecc51e9449c6e7c95272f0f044b5ba38948459e`. `server_new_connection` in
`src/server.cc:1456-1480` creates an unauthenticated handle with
`connection_time = 0`, the current activity time, a distinct negative player
object, the accepting listener, and that listener's `print_messages` flag.
The main-loop sweep at `src/server.cc:861-892` applies timeout only to a
non-outbound never-authenticated handle whose network reference count permits
cleanup. A present `connect_timeout` must be a positive integer and elapsed
time must be strictly greater than it; an absent option uses the default, and
a present zero, negative, or wrong-typed value disables this timeout branch.

On timeout Toast synchronously invokes the accepting listener's
`user_disconnected` first. `call_notifier` at `src/server.cc:513-527` runs the
server task with the negative connection object as task player and sole
argument, the listener as receiver, and empty `argstr`; `run_server_task` at
`src/tasks.cc:1826-1838` completes that task synchronously. Toast then emits
the timeout message when `print_messages` is true, closes the network handle,
and finally frees the logical server handle.

The shared option path at `src/server.cc:529-567` resolves the accepting
listener's `server_options` before falling back to #0. A string `timeout_msg`
emits one line; a list emits only its string elements in order; a present
unsupported type emits nothing; and an absent option emits the default
`*** Timed-out waiting for login. ***`. The durable row freezes only the
primary listener's default message after elapsed timeout. It does not freeze
the hook frame, strict boundary, activity reset, outbound/refcount exclusions,
custom/list/wrong-type messages, print suppression, EOF, or physical-versus-
logical close ordering.

The smallest Banteng representation changes only the existing timeout owner.
`MooRuntime.monitorUnauthenticatedConnection` already owns activity tracking,
listener-first timeout lookup, strict whole-second expiry, the exact
`user_disconnected` frame, and once-only logical cleanup. After its existing
synchronous hook it can resolve `timeout_msg` with the frozen Toast rules, use
the existing concrete `ListenerControl.bootConnection` final-lines-plus-close
operation, then remove the runtime/world connection. One real-socket
`MooServerTest` will freeze the default banner followed by EOF; the existing
runtime regression continues to freeze activity reset, hook frame, and
once-only removal.

This slice adds no interface, helper, sender, adapter, request/effect record,
builtin, VM, world mutation surface, or server production change. The
different Windows Toast checkout at `e8a3536` is explicitly excluded; all
Toast claims above come from the pinned WSL source used by the passing managed
oracle run.

## Sixteenth-row Banteng receipt

The focused real-socket regression was red before production with a target
read timeout, then passed after the runtime-only implementation. It proves the
exact default timeout banner followed by EOF. The existing runtime regression
continues to prove activity reset, the negative-ID hook frame, and once-only
logical removal.

The managed Banteng row passes with one selected and 11,504 deselected in
6.53 seconds. A preceding launch under the restarted process's inherited Java
17 failed before Banteng loaded and is excluded from semantic evidence; the
passing run used the required Java 25 environment. The full Java 25
`clean check installDist` gate passes in 13 seconds.

The complete managed `connection_lifecycle_toast_oracle` fail-fast run passes
the first sixteen rows and stops at `audit_redirect_messages`: its final
observation receives `E_VERBNF` instead of the Toast-proven redirect outputs.
The timeout slice is accepted; redirect messaging is the next causally
relevant unchecked lifecycle target.

## Seventeenth row: same-listener login redirect

The active durable row is `audit_redirect_messages`. It installs a primary-
listener `do_login_command` that always returns one newly created player, then
opens two connections. The row is red on committed Banteng `9a55019`: the
complete lifecycle run passes the first sixteen rows, but its final observation
returns `E_VERBNF`. It passes pinned WSL Toast at source commit
`aecc51e9449c6e7c95272f0f044b5ba38948459e` with one selected and 11,504
deselected in 5.68 seconds.

The row is valid but narrower than its description. Harness
`TestConnection.connect` and `open_connection` in
`src/moo_conformance/transport.py:114-165,358-362` drain initial output from
each new socket. Both connections therefore authenticate during their
automatic blank input. The later explicit sends are ordinary invalid player
commands. The row freezes the new connection's exact
`I couldn't understand that.` response and absence of subsequent output, the
old connection's exact default
`*** Redirecting connection to new port ***` line, and membership of the
player in `connected_players(1)`.

The harness discards the new connection's initial redirect output. The row
therefore does not freeze `redirect_to_msg`, EOF or continued usability on the
old socket, message option lookup or print suppression, hook choice/frame/
order, cross-listener behavior, close timing, or uniqueness of the remaining
player connection. A server can omit the new-side message, leave the old
socket open, or call the wrong hook and still pass this row.

Barn `spec/login.md:77-123,230-237` says a reconnect boots the old connection,
associates the new one, calls `user_reconnected`, and enters the command loop.
`spec/server.md:129-154` also names that hook. The written contract does not
define accepting-listener ownership, the two redirect options and defaults,
their value types, independent print suppression, or exact hook frame and
ordering. Its high-level old-close and same-listener reconnection result agrees
with Toast, but it is incomplete authority for observable details.

Barn's public path runs from `server/input_processor.go` through
`processPreLogin` and `dispatchLoginCommand`, then
`scheduler/task_factory.go:CreateLoginHookTask`, and finally
`server/input_login.go:loginPlayer`. On reconnect `loginPlayer` replaces the
player map with the new connection, sends a hardcoded unconditional old-side
default, calls old-listener `user_client_disconnected`, sends the new side's
ordinary `connect_msg`, and calls new-listener `user_connected`. It neither
closes nor marks the old socket disconnected in this path. Barn therefore
passes this narrow row while diverging on old-socket lifetime, same-listener
hook, new-side message, option lookup, print suppression, and hook frame.

Barn implements `connected_players([show_all])` in
`builtins/network.go:966-995`. It returns attached players from the connection
manager, includes unauthenticated connection objects for a truthy argument,
and deduplicates values. The normative network-builtin spec documents the
zero-or-one-argument surface but does not define order or duplicate behavior.
Toast, rather than Barn's deduplication, decides those unresolved details.

Pinned Toast creates a negative connection identity and queues initial blank
input in `src/server.cc:1438-1492`. `do_login_task` in
`src/tasks.cc:878-966` runs the accepting listener's `do_login_command`,
transfers task ownership when it returns a player, and calls
`player_connected(old_negative_id, player, newly_created)`. The semantic owner
is `src/server.cc:1658-1724`.

For this same-listener redirect, Toast first attaches the new handle to the
positive player. It conditionally sends old-listener `redirect_from_msg` with
default `*** Redirecting connection to new port ***`, then new-listener
`redirect_to_msg` with default
`*** Redirecting old connection to this port ***`. It closes and frees the old
handle immediately when its network reference count permits, otherwise marks
it for deferred disconnection. Only after that close decision does it invoke
new-listener `user_reconnected`.

The shared message owner at `src/server.cc:518-567` applies each endpoint's
`print_messages` flag independently and resolves that accepting listener's
`server_options` before #0 fallback. An absent named option uses the supplied
default; a string sends one line, including an empty string; a list sends only
its string members in order; and any other present type suppresses output.
The same-listener notifier frame has the new listener as `this`, the positive
player as task player and sole argument, `caller = #-1`, and empty `argstr`.

Toast registers `connected_players` for zero or one argument in
`src/server.cc:3288-3289`; `bf_connected_players` at lines 2752-2780 returns
authenticated handles by default and every live handle for a truthy argument,
excluding handles marked for disconnection. It enumerates handles in live
server order without Barn's player deduplication. After this redirect, only the
replacement handle contributes the positive player.

The reported Banteng `E_VERBNF` is specifically the absent
`connected_players` builtin, propagated from `BuiltinCatalog` through the VM.
A separate same-listener redirect defect is masked: `MooRuntime.executeLogin`
detects an existing returned-player connection only when its listener differs,
so it currently keeps both logical mappings, emits no redirect messages,
leaves the old socket open, and omits `user_reconnected`.

The smallest row-completing representation stays in existing concrete owners.
`WorldTxn` exposes the currently attached connection values in live order;
`BuiltinCatalog` implements the zero-or-one-argument external read; and
`MooRuntime.executeLogin` handles an existing returned-player connection by
resolving both endpoint messages, using the existing serialized final-lines-
plus-close transport operation for the old socket, removing its logical
mapping, and invoking the existing stored-verb path with the Toast notifier
frame. The new-side message remains the normal return from `executeLogin`.

One real-socket regression will freeze the two default messages, old-side EOF,
same-listener hook frame after old logical removal, later command behavior, and
`connected_players(1)` membership. The existing cross-listener runtime
regression remains authoritative for that separate branch. This slice adds no
new interface, helper, sender, adapter, effect/request record, or server
production method.

The regression exposed one further behavior already asserted by the durable
row but previously masked by its final `E_VERBNF`: after redirect, the new
authenticated connection's unmatched command must emit exact output
`I couldn't understand that.`. Barn has no normative command-dispatch passage
for this fallback. Its current owner in
`server/input_processor.go:482-549` runs listener `do_command`, searches player
and location command verbs, optionally selects a `huh` verb according to
`player_huh`, and otherwise sends that hardcoded line before the output suffix.

Pinned Toast owns the same decision in `do_command_task` at
`src/tasks.cc:801-869`. After the intrinsic and listener `do_command` paths, it
searches the player, location, direct object, and indirect object for a command
verb, then the location or player `huh` selected by `player_huh`. If all paths
decline, it notifies the player with exact
`I couldn't understand that.`, clears the last input task ID, and then emits
the output suffix. The active row's newly created player has no matching
command or usable `huh`, so this hardcoded final branch is the relevant frozen
surface. Banteng's existing selected-verb-null branch already owns that point
and currently returns only prefix/earlier output plus suffix; adding this one
line there requires no new owner or API.

The isolated managed Banteng row initially passed after the return-path
implementation, but the complete lifecycle family exposed a real ordering
defect: the new-side redirect was returned to `MooServer` only after
`user_reconnected` completed, so under the broader run it arrived after the
harness's initial `new_connection` drain. The subsequent explicit send then
captured `*** Redirecting old connection to this port ***` instead of the
unknown-command line. The row therefore does freeze one aspect of new-side
timing: any such output must complete early enough that no redirect line
remains for the next input.

That evidence disproves the earlier claim that the return value alone is a
sufficient representation. Toast sends both endpoint messages before closing
the old handle and before the notifier. Banteng already registers both socket
writers before `MooRuntime.openConnection`, but its existing
`ListenerControl` capability exposes only final-lines-plus-close. The smallest
ordering-correct transport change is one additional write-only operation on
that same existing capability, implemented by `MooServer` through its existing
connection-ID writer map and serialized `writeLines`. The runtime can then
write old lines, write new lines, close the old socket, remove its logical
mapping, and invoke the notifier in Toast order. This adds no new interface,
helper, sender object, adapter, registry, or server owner; the broader managed
failure is the proof that the existing capability needs this one operation.

## Redirect-family cleanup dependency: `delete_property`

The full lifecycle gate failed after the focused redirect row passed. A
managed bisection isolated the causal predecessor to
`audit_listener_handler_do_blank_command`: that row temporarily adds
`trusted_proxies` to `#6` and removes it in cleanup with
`delete_property(#6, "trusted_proxies")`. Banteng did not implement that
builtin, so the cleanup caught `E_VERBNF` and left the option active. The next
row's initial blank inputs were consequently treated as trusted; because #0
has no `do_blank_command`, login returned before `do_login_command`. Its later
explicit input performed the login and emitted the redirect, explaining both
the late redirect line and the absent unknown-command response. The two-row
managed reproduction makes property deletion a causal dependency of this
lifecycle slice rather than unrelated builtin expansion.

Barn's general object table in `../barn/spec/objects.md:184-194` names
`delete_property`. Its detailed description in
`../barn/spec/builtins/properties.md:146-172` says an inherited-only property
is a successful no-op and that deletion of an override restores inheritance.
Those statements disagree with Toast. Barn's implementation at
`../barn/builtins/properties.go:327-371`, through
`../barn/db/store/store_properties.go:363-381,492-514,619-644`, instead
requires a definition local to the exact target. It nevertheless diverges
from Toast by accepting anonymous objects, returning `E_INVIND` for a
never-existing object, omitting the permission check, and not traversing
anonymous descendants when removing stored slots.

Pinned WSL Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`
registers `delete_property` for exactly two arguments with a string second
argument in `src/property.cc:343-344`. `bf_delete_prop` at lines 239-259
requires an ordinary object, returns `E_INVARG` for an invalid or recycled
object, applies `db_object_allows(obj, progr, FLAG_WRITE)`, and calls
`db_delete_propdef(obj, pname)`. The owner in
`src/db_properties.cc:351-407` deletes only a property definition local to
that object and recursively removes its value slots from permanent and
anonymous descendants. An inherited property, a child value override, or a
built-in property is therefore `E_PROPNF`, not a successful no-op. Object
write authority is owner, wizard, or the object's public-write flag; property
ownership and flags do not decide deletion. Names match case-insensitively.
Success returns integer zero through `src/functions.cc:392-405`.

The durable row
`../moo-conformance-tests/src/moo_conformance/_tests/builtins/properties.yaml:275`
(`delete_property_works`) passed the pinned live Toast oracle on 2026-07-15:
one selected, 11,504 deselected, in 3.55 seconds. The same file contains
recycled, missing, built-in, nominal public-write/wizard, definition-order,
parent-propagation, and inherited-child rows. Its only negative permission row
is skipped, and its nominal permission rows execute as the target owner, so
they do not independently freeze permission precedence. Generated call-shape
rows cover arity and the string argument. No current durable row isolates
anonymous rejection or cleanup, a never-allocated object, or a child override.

Banteng's current representation needs no additional abstraction for the
proven local-deletion path. A `WorldObject` already owns its local immutable
`WorldProperty` list, inherited lookup is dynamic, and no separate descendant
override slots exist yet. `WorldTxn` can remove the case-insensitive local
entry; descendants then stop resolving that definition automatically.
`BuiltinCatalog` applies the Toast object-write check, returns `E_PROPNF` when
no local entry was removed, and classifies the builtin as a transaction write.
This adds no interface, helper, adapter, or alternate mutation path.

## Seventeenth-row verification receipt

The pinned WSL Toast redirect row passed before implementation: one selected,
11,504 deselected, in 5.68 seconds. The initial Banteng regression was red on
the replacement-side default; the direct-write ordering regression and the
unknown-command regression then passed locally. The isolated managed redirect
row passed in 5.49 seconds after transport ordering was corrected.

The first complete lifecycle run exposed the cleanup-dependent failure above.
After the `delete_property` authority gate, its focused Java regression was
red because the local definition remained. The implemented regression and the
existing property-cleanup consumer then passed together. The exact managed
predecessor pair (`audit_listener_handler_do_blank_command` followed by
`audit_redirect_messages`) passed two selected rows in 7.25 seconds, and
Banteng's managed `delete_property_works` row passed in 3.44 seconds.

The complete managed lifecycle category then passed rows 1 through 17 and
stopped first at row 18,
`audit_oob_prefix_dispatches_do_out_of_band_command`: 17 passed, one failed,
11,482 deselected, in 41.56 seconds. The final Java 25
`clean check installDist` gate passed in 17 seconds after applying the pinned
formatter. Row 17 is therefore the kept frontier; row 18 remains a separate
unchecked slice.

## Eighteenth row: textual out-of-band dispatch

The active durable row is
`audit_oob_prefix_dispatches_do_out_of_band_command` at
`../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:1727`.
It installs `#0:do_out_of_band_command`, sends exact input
`#$#audit-oob alpha beta`, and observes only that the verb received string
arguments `{"#$#audit-oob", "alpha", "beta"}`. The harness sends the command
unchanged as UTF-8 followed by bare LF; it does not assert the handler's output
or return value. The row passed pinned WSL Toast commit
`aecc51e9449c6e7c95272f0f044b5ba38948459e`: one selected, 11,504 deselected,
in 6.59 seconds. Committed Banteng `1bbce66` passes the preceding 17 lifecycle
rows and leaves the observed argument property empty at this row.

Barn's normative surface is incomplete. `../barn/spec/server.md:321-323` says
`#$#` lines are application text on WebSockets, and
`../barn/spec/builtins/network.md:224-235` lists `disable-oob`; neither defines
text-prefix recognition, word parsing, accepting-listener ownership, the verb
frame, error handling, or output. Current Barn detects an exact case-sensitive
position-zero prefix in `../barn/server/input_processor.go:196-248`. Its OOB
path bypasses held input, a pending `read()`, and normal command dispatch;
targets the connection's accepting listener; tokenizes through
`../barn/command/command.go:120-165`; supplies the original line as `argstr`;
discards an ordinary return; ignores `E_VERBNF`; and reports other exceptions.
Barn agrees on this row's three words but splits a broader Unicode-whitespace
set. It also constructs server hooks with `caller = player`, which disagrees
with Toast outside this row's observation.

Pinned Toast defines `OUT_OF_BAND_PREFIX` as exact `#$#` in
`src/include/options.h:78-97`. `src/server.cc:1534-1540` forwards each line and
transport OOB flag to `new_input_task`; `src/tasks.cc:1074-1093` selects
`TASK_OOB` for a position-zero textual prefix, and lines 1683-1689 dispatch
that task before ordinary input. `do_out_of_band_command` at lines 969-974
calls the server-task owner with the current player, the connection's accepting
listener handler, `parse_into_wordlist(command)`, and the exact command as
`argstr`, while passing a null result slot so an ordinary return is discarded.
`src/parse_cmd.cc:33-79,108-124` removes quotes, honors backslash escaping,
and splits literal ASCII spaces; that yields the row's exact three strings.

The server-task path at `src/tasks.cc:1825-1870` treats a missing handler verb
as an empty successful verb. `src/execute.cc:3279-3335` fixes the frame:
`this` is the accepting listener, `player` is the connected player,
`programmer` is the verb owner, `caller = #-1`, `args` and `argstr` are as
above, direct and indirect objects are `#-1`, and object/preposition strings
are empty. Tracebacks are enabled for an uncaught handler error.

Banteng's `MooServer` already forwards the exact socket line to
`MooRuntime.executeLine`. The runtime has no OOB branch: it builds the existing
command word list, invokes listener `do_command`, and then enters normal
command lookup. The smallest row-owned change is a position-zero `#$#` branch
immediately after that existing word-list construction and before
`do_command`. It resolves `do_out_of_band_command` on the existing connection
listener, executes it through `executeStored` and `verbLocals` with Toast's
server-task frame, returns only emitted output, and treats a missing verb as
no output. No interface, helper, server method, VM operation, world method, or
builtin is required.

This row does not authorize the separate `disable-oob`, held-input/read bypass,
OOB quote prefix, forced-input queueing, Telnet parser, or binary-escaped OOB
surfaces. Those already have later durable rows, including
`audit_connection_hold_and_oob_options` and the gap-followup Telnet cases, and
remain separate targets.

### Eighteenth-row verification receipt

The focused Java regression was red before implementation because the OOB line
fell through to the normal unknown-command response and the handler frame was
never stored. After the single runtime branch, that regression passed in three
seconds and froze Toast's `caller = #-1` server-task frame in addition to the
row's argument list.

Managed Banteng then passed the exact durable row: one selected, 11,504
deselected, in 6.57 seconds. The complete lifecycle category passed rows 1
through 18 and stopped first at row 19,
`audit_connection_name_method0_hostname`: 18 passed, one failed, 11,482
deselected, in 44.71 seconds. The final Java 25 `clean check installDist` gate
passed in 15 seconds after applying the pinned formatter. Row 18 is therefore
the kept frontier; `connection_name` remains a separate unchecked slice.

## Nineteenth row: `connection_name(player, 0)`

The active durable row is `audit_connection_name_method0_hostname` at
`../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:1759`.
It runs as Wizard, calls `connection_name(player, 0)`, and asserts only that the
result is a nonempty string: `{typeof(name), length(name) > 0} == {2, 1}`. Its
description is imprecise. An explicit method `0` requests Toast's full legacy
connection string, not the bare saved hostname. The exact row passed pinned WSL
Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`: one selected, 11,504
deselected, in 3.27 seconds. Committed Banteng `27eb88c` reaches the builtin
dispatcher without a `connection_name` entry and returns `E_VERBNF`.

Barn's normative documents disagree with one another. The WebSocket metadata
passage in `../barn/spec/server.md:264-279` identifies method `0` as the legacy
form, although its abbreviated example omits the numeric address in brackets.
`../barn/spec/builtins/network.md:25-41` instead documents string-valued method
names such as `"legacy"` and `"ip-address"`; that is stale and contradicts both
the registered integer signature and Toast. The other builtin summaries do not
freeze the permission rule. These documents therefore do not independently
supply the active contract.

Barn's public signature table at
`../barn/builtins/function_signatures_generated.go:34` registers one required
object and one optional integer. `builtinConnectionName` at
`../barn/builtins/network.go:998-1051` resolves the active connection, returns
the saved resolved name or remote host when the method is omitted, returns the
numeric remote host for method `1`, and formats every other integer, including
`0`, as `port <listen-port> from <name> [<ip>], port <remote-port>`. That agrees
with the active inbound Wizard row. Outside this row, Barn diverges by omitting
Toast's wizard-or-self permission check, always spelling the direction `from`,
and allowing an internal connection-target fallback broader than the public
object signature.

Pinned Toast owns the public builtin in `bf_connection_name` at
`/root/src/toaststunt/src/server.cc:2819-2850` and registers it at line 3293 as
`connection_name(OBJ [, INT])`. With no second argument it returns
`network_connection_name`; with method `1` it returns `network_ip_address`;
every other integer, including `0`, calls
`full_network_connection_name(handle, true)`. The semantic owner at
`/root/src/toaststunt/src/network.cc:1573-1633` formats the legacy value as
`port <source-port> <to|from> <saved-name> [<destination-ip>], port
<destination-port>`. The active accepted socket is inbound, so its direction is
`from`. The saved name is the peer's resolved name or its numeric address when
name lookup is unavailable or disabled.

Toast's signature gate supplies `E_ARGS` and `E_TYPE`. After attempting the
connection lookup, `bf_connection_name` permits only a wizard or the queried
player itself; another programmer receives `E_PERM`. A wizard or self querying
a missing or disconnecting connection receives `E_INVARG`. The permission test
precedes the final missing-result test. Barn does not currently agree on that
permission behavior, but the active row runs as Wizard against a live player
and leaves the disagreement unobserved.

The broader durable contract is recorded by
`../moo-conformance-tests/src/moo_conformance/_tests/builtins/connection_name_semantics.yaml`,
which covers the omitted method, method `1`, several legacy-method integers,
legacy-string shape, and self access. Generated builtin rows cover the public
arity and object/integer types; connection call-shape and server-admin rows
cover invalid-connection and string-result cases. No current durable row
isolates another-player `E_PERM`, and the active slice does not widen to that
missing coverage or to outbound `to` formatting.

Banteng already stores the required inbound fields in `MooServer`: listener
port as `source_port`, peer address as both `destination_address` and
`destination_ip`, and peer port as `destination_port`. `WorldTxn.connectionInfo`
already resolves that map by either connection object or attached player. The
smallest Java representation is therefore one `connection_name` dispatcher
case beside `connection_info`, using that existing lookup and value types. For
method `0` it formats the four existing fields as
`port <source_port> from <destination_address> [<destination_ip>], port
<destination_port>`, applies the existing wizard-or-self check, returns
`E_INVARG` for no active connection, and is classified with the existing
external connection reads. A focused runtime regression will seed synthetic
metadata, attach the connection to Wizard, and prove that exact string red
before the production edit. No interface, helper, adapter, sender, metadata
owner, server method, world method, or VM operation is required.

### Nineteenth-row verification receipt

The focused runtime regression first failed at the committed production
frontier with exact eval result `{2, {E_VERBNF}}`. After the single catalog
case and effect classification were added, the same regression passed on Java
25 in six seconds and froze the exact synthetic legacy string
`port 7777 from client.example [198.51.100.7], port 4567`.

The managed Banteng row passed with the current Java 25 distribution: one
selected, 11,504 deselected, in 3.35 seconds. The complete managed lifecycle
category then passed rows 1 through 19 and stopped first at row 20,
`audit_connection_info_source_fields`, with `E_VARNF`: 19 passed, one failed,
11,482 deselected, in 44.62 seconds. Row 19 is therefore the kept frontier;
row 20 remains a separate unchecked slice. The final Java 25
`clean check installDist` gate passed in 13 seconds.

## Twentieth row: connection metadata and predefined type constants

The active durable row is `audit_connection_info_source_fields` at
`../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:1770`.
Wizard calls `connection_info(player)`, requires the presence of the eight
canonical source, destination, protocol, and direction keys, and checks that
`source_address` is `STR` while both observed ports are `INT`. The row passed
pinned WSL Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`:
one selected, 11,504 deselected, in 3.31 seconds.

The earlier premise that committed Banteng lacked connection metadata was
wrong. `MooServer` already creates all eight keys from the actual local and
remote socket endpoints, `WorldTxn.connectionInfo` resolves them by connection
or attached player, and `BuiltinCatalog.connection_info` already enforces the
Toast lookup and permission order before returning that map. Earlier lifecycle
rows successfully index `destination_ip` and `source_port`. Row 20 instead
reaches its final type predicate and raises `E_VARNF` on the predefined
identifier `STR`; `INT` would fail next. The independent durable
`types::constant_STR_value` row reproduces the same `E_VARNF` on committed
Banteng `35102ee`.

Barn's relevant normative text is incomplete and partly stale.
`../barn/spec/server.md:264-279` correctly assigns `source_port` to the accepting
listener, destination fields to the peer, and `protocol` to the IP family.
`../barn/spec/builtins/network.md:46-60` instead documents unrelated telemetry
keys such as `connected_at` and byte counts. No Barn normative section defines
the runtime predefined type constants; the database type-tag table in
`../barn/spec/database.md:82-105` supplies the corresponding numeric tags but
does not define identifier lookup.

Barn registers `connection_info` as exactly one object argument in
`../barn/builtins/function_signatures_generated.go:33`. Its implementation at
`../barn/builtins/network.go:1161-1221`, through the connection owners in
`../barn/server/connection.go` and `connection_manager.go`, returns the same
eight keys with the active row's value categories. It diverges from Toast by
omitting the wizard-or-self permission check and by using a loopback fallback
for inbound source address rather than the actual bound interface. Neither
divergence is observed by this Wizard, type-and-presence-only row.

Barn's runtime-constant owner is `NewEnvironment` at
`../barn/vm/environment.go:12-32`. It prepopulates `INT` with integer zero and
`STR` with integer two, matching `typeof`, along with the other predefined type
identifiers. Those bindings are available through the ordinary environment
lookup before user code executes.

Pinned Toast registers `connection_info(OBJ)` at
`/root/src/toaststunt/src/server.cc:3300`. `bf_connection_info` at lines
3031-3074 rejects a missing or disconnecting handle with `E_INVARG`, then a
connected other-player lookup by a nonwizard with `E_PERM`, and constructs the
eight-key map at lines 3045-3067. TLS builds may add a ninth key, so the durable
row correctly requires presence rather than exact key equality. The endpoint
accessors at `/root/src/toaststunt/src/network.cc:1574-1679` own the saved peer
name, numeric addresses, ports, and `IPv4`/`IPv6` protocol strings.

Toast's builtin-name owner at `/root/src/toaststunt/src/sym_table.cc:80-116`
assigns the identifiers `STR` and `INT` to runtime environment slots. The
semantic owner `fill_in_rt_consts` at
`/root/src/toaststunt/src/eval_env.cc:75-120` fills those slots with integer
`_TYPE_STR` and `TYPE_INT` respectively. The durable rows
`types::constant_INT_value` and `types::constant_STR_value` at
`../moo-conformance-tests/src/moo_conformance/_tests/basic/types.yaml:80-103`
passed the pinned live Toast oracle together: two selected, 11,503 deselected,
in 3.51 seconds. They freeze `INT == 0` and `STR == 2` independently of the
connection row.

Generated `connection_info` rows freeze the one-object signature and
`E_ARGS`/`E_TYPE`. `builtins/server_admin.yaml` covers the map, the eight keys,
field types, inbound direction, invalid-player `E_INVARG`, and programmer self
access. `builtins/network_matrix.yaml` covers the same key family for outbound
connections. No change to those durable rows is needed.

Banteng's `MooCompiler` compiles every identifier as the existing `LOAD_LOCAL`
operation. `MooVm.loadLocal` first checks the frame locals, then already handles
predefined `LIST`, and otherwise raises `E_VARNF`. The smallest representation
for the active causal dependency is to extend that existing local-miss fallback
with `INT -> MooValue.Type.INTEGER.code()` and
`STR -> MooValue.Type.STRING.code()`, preserving local-first lookup and the
existing integer value representation. A focused VM regression will execute
`return {typeof(1) == INT, typeof("x") == STR};`, prove the current `E_VARNF`
red, and expect `{1, 1}`. No server, world, builtin, compiler, parser, helper,
interface, adapter, or new environment owner is required. Other predefined
type constants remain separate unchecked surfaces.

### Twentieth-row verification receipt

The focused VM regression was red before implementation: it expected a returned
`{1, 1}` and observed the VM's error outcome at the first missing predefined
constant. After the two local-miss branches were added, that same regression
passed on Java 25 in three seconds.

Managed Banteng then passed the exact durable connection row with the current
distribution: one selected, 11,504 deselected, in 3.35 seconds. The complete
managed lifecycle category passed rows 1 through 20 and stopped first at row
21, `audit_do_login_command_argstr_original`: 20 passed, one failed, 11,482
deselected, in 45.83 seconds. Row 20 is therefore the kept frontier. Row 21's
backslash-sensitive login word parsing remains a separate unchecked slice. The
final Java 25 `clean check installDist` gate passed in 13 seconds.

## Twenty-first row: login word list and original `argstr`

The active durable row is `audit_do_login_command_argstr_original` at
`../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:1786`.
It installs `do_login_command` on the accepting listener, sends the literal
line `auditlogin foo\ bar  baz`, and requires `args` to be
`{"auditlogin", "foo bar", "baz"}` while `argstr` remains the byte-for-byte
original line, including the backslash and doubled space. The exact row passed
pinned WSL Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`:
one selected, 11,504 deselected, in 4.60 seconds.

Barn has no normative word-list contract for login input.
`../barn/spec/login.md:29-43` and `../barn/spec/server.md:165-170` instead
describe `do_login_command(connection, line)` as though those were its explicit
arguments. Generic context passages in `../barn/spec/login.md:163-177`,
`../barn/spec/objects.md:258-274`, and
`../barn/spec/builtins/verbs.md:317-333` call `argstr` the original argument
string but do not define escape removal, quote handling, space collapsing, or
the login hook's actual `args`. That prose is incomplete and disagrees with
both Barn's implementation and Toast for the active row.

Barn's public login dispatch at
`../barn/server/input_processor.go:423-464`, with synchronous fallback at
`../barn/server/input_login.go:37-64`, targets the accepting listener, parses
the line with `command.CommandWordList`, converts the words to strings, and
passes the untouched line separately as `argstr`. The task frame in
`../barn/scheduler/task_factory.go:124-153` uses the negative connection object
as `player`, the listener as `this`, the calling player as `caller`, and the
verb owner as programmer.

Barn's parser at `../barn/command/command.go:114-166` removes a backslash and
copies the following byte, omits quote delimiters, retains spaces inside quotes,
and collapses separating whitespace. It agrees on the active input. Outside
this row it treats a trailing backslash differently from Toast, accepts a
broader Unicode-whitespace set, and special-cases leading quote, colon, and
semicolon characters. Those differences remain unresolved by this row and are
not part of this slice.

Pinned Toast receives the unchanged line in
`/root/src/toaststunt/src/server.cc:1534-1540` and queues it through the
pre-login task path. `do_login_task` at
`/root/src/toaststunt/src/tasks.cc:878-916` passes
`parse_into_wordlist(command)` as `args` and the unchanged `command` separately
as `argstr` to `do_login_command` on the accepting listener. The pre-login
player is the negative connection object; the handler is the accepting
listener.

Toast's semantic word-list owner is
`/root/src/toaststunt/src/parse_cmd.cc:34-78,109-123`. It skips leading and
repeated ASCII spaces, removes a backslash and copies the next byte into the
current word, omits double-quote delimiters while retaining quoted spaces, and
drops a trailing backslash. Its copy is mutated for word parsing; the distinct
original command passed as `argstr` remains unchanged. The active line
therefore deterministically yields the three expected words and the exact
original `argstr`.

Related durable rows include
`audit_do_command_receives_quoted_backslash_wordlist` in
`audit/command_parser_toast_oracle.yaml`, the accepting-listener login frame at
the start of `connection_lifecycle_toast_oracle.yaml`, and OOB rows using the
same Toast parser. Prefix shortcuts, non-ASCII or tab whitespace, quotes,
unbalanced quotes, trailing backslashes, authenticated commands, and OOB input
remain separate observable surfaces.

Committed Banteng `039fda1` preserves the original login line in
`MooRuntime.executeLogin` and passes it unchanged as `argstr`. Its `args` path,
however, uses Java `StringTokenizer`, so the active line becomes
`{"auditlogin", "foo\\", "bar", "baz"}`. The complete lifecycle gate recorded
that exact discrepancy after 20 preceding rows passed.

The smallest active-row representation stays inside `executeLogin`: replace
only its `StringTokenizer` word loop with an inline Toast-shaped scan that
handles backslash, quotes, and ASCII-space word boundaries, while continuing to
pass the untouched `loginLine` as `argstr`. A focused runtime regression will
install a listener login hook, submit the exact active line, and prove both the
parsed words and original string red before production changes. No parser,
compiler, server, world, builtin, helper, interface, adapter, or new tokenizer
owner is required.

The focused Java 25 regression
`MooRuntimeTest.tokenizesEscapedLoginWordsWhilePreservingOriginalArgstr` first
failed on committed Banteng `039fda1` with the intended discrepancy: expected
`{{"auditlogin", "foo bar", "baz"}, "auditlogin foo\\ bar  baz"}` but received
`{{"auditlogin", "foo\\", "bar", "baz"}, "auditlogin foo\\ bar  baz"}`. This
proved that only the word list was wrong and that the original `argstr` was
already preserved. After the in-place `executeLogin` scanner change, the same
regression passed.

The exact managed Banteng row then passed under Java 25: one selected, 11,504
deselected, in 4.59 seconds. The complete managed lifecycle category passed
rows 1 through 21 and stopped first at row 22,
`audit_connection_hold_and_oob_options`, with 21 passed, 11,482 deselected, in
46.09 seconds. Row 21 is therefore the kept frontier; row 22's `E_VERBNF`
failure remains a separate unchecked slice.

The final Java 25 `clean check installDist` gate passed in 15 seconds.

## Twenty-second row: held input and disabled OOB

The active durable row is `audit_connection_hold_and_oob_options` at
`../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:1860-2017`.
It logs a newly created player in through a forced line, then combines
`force_input`, `set_connection_option`, and `suspend(0)` to require
`{1, 1, 1, 1, 0}`: ordinary input is blocked while held and executes after
release; enabled OOB bypasses hold; disabled OOB remains blocked; and after
release while OOB is still disabled, that line is not dispatched as OOB.

Barn's normative surface is incomplete. `../barn/spec/builtins/network.md:120-135`
defines `force_input` and `flush_input` only at a high level, while
`../barn/spec/builtins/network.md:224-243` names `set_connection_option`,
`hold-input`, `disable-oob`, and `connection_options`.
`../barn/spec/builtins/tasks.md:172-213,519-545` describes suspension and
duplicates the high-level input builtins. `../barn/spec/tasks.md:401-418` says
runnable input precedes background work unless input is held, and
`../barn/spec/server.md:310-329` discusses application text and
`flush-command`. No Barn specification section defines negative pre-login
targets, forced login dispatch, OOB bypass of held input, disabled-OOB
disposition, held-queue release, the exact `force_input`/`suspend(0)` ordering,
or `do_out_of_band_command`.

Barn registers the relevant builtins at `../barn/builtins/registry.go:197-208`
and validates forced input at `../barn/builtins/signatures.go:550-575`. The
concrete option and held-command owners are
`../barn/builtins/network.go:169-248,561-619,1252-1348`. Clearing `hold-input`
stores false, drains held lines in order, and re-injects them. Negative live
connections are mapped and resolved at
`../barn/server/connection_manager.go:399-445`; forced input enters at
`../barn/server/input_processor.go:269-314`; and login dispatch and association
run through `../barn/server/input_processor.go:368-478`,
`../barn/scheduler/task_factory.go:108-177`, and
`../barn/server/input_login.go:239-300`. OOB is classified before held ordinary
input at `../barn/server/input_processor.go:196-258`. The zero-duration
suspension path through `../barn/task/manager.go:148-165`,
`../barn/scheduler/task_runtime.go:231-252`, and
`../barn/server/input_processor.go:163-193` requeues the caller while pending
input is serviced. Barn's implementation therefore derives the row's five
expected values.

Pinned Toast owns queue kinds, per-connection state, and option defaults at
`/root/src/toaststunt/src/tasks.cc:55-64,142-219,423-475`. Its
`force_input` builtin at `src/tasks.cc:2880-2897` targets the live queue and
enqueues text. `src/tasks.cc:1074-1130` classifies binary, transport OOB,
quoted, textual `#$#` OOB, and ordinary input; the exact prefixes are in
`src/include/options.h:78-97`. Held ordinary input is not activated, but
enabled OOB is.

Toast's `set_connection_option` entry point is
`src/server.cc:2973-2995`. `src/tasks.cc:1015-1058` owns `flush-command`,
`hold-input`, and `disable-oob`, and clearing hold reactivates queued input.
Option names compare case-insensitively at `src/include/server.h:345-360`.
Dequeue at `src/tasks.cc:532-596` returns no OOB-only task while OOB is disabled
and converts a subsequently dequeued OOB task to in-band. Ready selection and
dispatch at `src/tasks.cc:1628-1811` sends OOB to the listener hook and in-band
to login or ordinary command handling. `do_out_of_band_command` receives parsed
words and the exact input as `argstr` at `src/tasks.cc:969-974`.

For the row's initial forced login, `src/tasks.cc:877-966` runs
`do_login_command` for the negative queue identity; `src/server.cc:933-945,1657-1725`
reassociates the same queue and shandle to the returned player.
`connected_players(1)` includes negative pre-login handles at
`src/server.cc:2751-2778`. `suspend(0)` snapshots and blocks the VM at
`src/execute.cc:221-239,330-359,3520-3539`; its immediately due continuation
enters the background queue through `src/tasks.cc:1182-1205,1295-1321,1628-1646`.
The active-queue ordering at `src/tasks.cc:382-420` exposes each pending input
effect before the yielded audit task resumes.

The resulting Toast sequence is unambiguous. The forced login line associates
the new player. Held `auditq` stays queued, then executes with empty `argstr`
after release. Enabled `#$#audit-oob-free` bypasses hold. With both hold and
disable-OOB set, `#$#audit-oob-held` remains queued; clearing hold while OOB is
still disabled converts it to ordinary in-band input, so the OOB hook remains
empty. This yields exactly `{1, 1, 1, 1, 0}` and agrees with both the durable
row and Barn's implementation.

Adjacent behavior remains outside this slice: transport or quoted OOB,
`force_input` front insertion, multiple alternating queued lines, option
persistence across login, option-name case and invalid values, getter/list
shape, `flush-command`, and the eventual ordinary-command side effect of the
disabled OOB line. Barn additionally differs outside the active row by ignoring
front insertion for ordinary live input and dropping disabled transport OOB
where Toast converts it to in-band.

The exact managed row passed pinned WSL Toast commit
`aecc51e9449c6e7c95272f0f044b5ba38948459e`: one selected, 11,504
deselected, in 3.66 seconds. The active semantic contract is therefore frozen
before Java design.

Committed Banteng `9ccf880` already registers `set_connection_option` in
`BuiltinCatalog.invoke`, but its option owner accepts only `hold-input` and
`flush-command`. The row's first `E_VERBNF` is instead `force_input`, which has
no builtin case or effect classification. `MooRuntime.ConnectionState` already
owns `pendingInput`, `holdInput`, and `flushCommand`; a second input queue is
neither necessary nor authorized. Current input handling checks hold before
OOB dispatch, and clearing hold changes the flag without replaying the FIFO.

The smallest Java representation extends the existing deferred-effect path
with a concrete forced-input request, staged in the existing VM state and
applied by `MooRuntime` at the existing VM boundary before a zero-duration
suspension resumes. Runtime target resolution uses the existing negative
connection IDs and positive attached-player mapping, invokes the existing
`executeLine` path, and publishes any returned lines through the existing
listener control. The existing connection option enum and connection state gain
`disable-oob`; enabled textual OOB is classified before held ordinary input,
while disabled OOB follows the held/ordinary path. Clearing `hold-input`
snapshots, clears, and replays the existing FIFO through `executeLine` while
the disabled-OOB state remains active.

Only the row's two-argument `force_input(target, line)` contract is in scope.
Front insertion, a generic input effect framework, a second queue, and any new
helper, interface, adapter, or sender remain outside this slice. A focused
runtime regression will combine the existing held-input and OOB seams and must
fail at the initial `force_input` before production changes.

The first connection implementation reduced the managed result from
`E_VERBNF` to `{1, 0, 0, 1, 0}`. Runtime-boundary diagnostics, removed after
use, proved that target resolution, held FIFO release, enabled-OOB bypass,
disabled-OOB handling, listener selection, and player-verb selection were all
correct. The two selected audit verbs both stopped at their existing
`listappend(list, argstr)` calls. The initial focused regression had masked
this dependency by using list-splice syntax; it was corrected and then
reproduced the managed `{1, 0, 0, 1, 0}` result exactly.

### Required two-argument `listappend` dependency

Barn specifies `listappend(list, value [, index])` at
`../barn/spec/builtins/lists.md:1-5,30-59`. For the active two-argument form,
the first argument is a list, the second is any value, and the result contains
the original elements followed by that value. `../barn/spec/types.md:149-180,436-460`
defines heterogeneous shallow copy-on-write lists: the returned outer list is
logically independent while nested values may remain shared. Wrong arity and
optional-index type errors are omitted locally but supplied by Barn's generic
signature rules; list-size overflow is globally `E_QUOTA`.

Barn registers and validates the builtin through
`../barn/builtins/registry.go:87-98,349-378,399-435`,
`../barn/builtins/function_signatures_generated.go:104`, and
`../barn/builtins/signatures.go:55-62,85-125`. The semantic owner at
`../barn/builtins/lists.go:15-51` defaults the missing index to the current
length, inserts after it, checks the list-size quota, and returns the result.
`../barn/types/list.go:188-202` allocates and copies the top-level elements, so
the source list is unchanged. No permission check, suspension, or deferred
effect is involved.

Pinned Toast registers the same `LIST, ANY, INT` two-to-three-argument shape at
`/root/src/toaststunt/src/list.cc:1755-1771`; generic validation at
`src/functions.cc:239-275` supplies `E_ARGS` and `E_TYPE`. The builtin owner at
`src/list.cc:695-729` selects `length + 1` for two arguments and delegates to
`doinsert` at `src/list.cc:196-250`. Existing elements retain order and the new
value becomes last. Toast may physically reuse a uniquely owned temporary, but
otherwise allocates a new outer list and retains nested references through
`src/include/utils.h:52-68` and `src/utils.cc:267-347`; this is observably the
same shallow copy-on-write contract as Barn. The operation is one synchronous
builtin-call tick through `src/include/opcode.h:46-74,128-130` and
`src/execute.cc:972-988,2224-2281`, with no intrinsic permission or host
effect.

Barn and Toast agree completely on the active normal-size two-argument call.
They differ outside this slice on oversized results: Toast's
`src/list.cc:615-627,714-720` yields `E_QUOTA` only when
`max_concat_catchable` is enabled and otherwise aborts as out-of-seconds. The
optional-index clamp and all limit behavior remain owned by their dedicated
rows. Related durable coverage is `basic/list.yaml:listappend_to_end`, the
two-argument cases in `builtins/listappend_call_shapes.yaml`, signature/error
cases in `generated_builtins/listappend.yaml`, and the separate
`server/limits.yaml` rows.

The direct managed `listappend_to_end` row passed pinned WSL Toast commit
`aecc51e9449c6e7c95272f0f044b5ba38948459e`: one selected, 11,504
deselected, in 3.51 seconds. The active normal-size two-argument contract is
therefore frozen before Java implementation.

Banteng's existing `MooValue.ListValue` constructor already takes an immutable
snapshot of its supplied outer elements. The smallest active representation is
therefore one inline `BuiltinCatalog.invoke` case: require exactly two
arguments, require a `ListValue` first argument, copy its elements into a local
list, append the second value, and return a new `ListValue`. Add `listappend` to
the existing pure effect classification. No value-owner change, permission,
effect request, helper, interface, adapter, or optional-index implementation is
required for this row.

Committed Banteng `9ccf880` first failed the exact lifecycle row with
`E_VERBNF`; the focused runtime regression stored and reproduced that exact
error before production changes. After the forced-input and connection-option
path was implemented, the focused test initially passed only because its audit
verbs used list splicing. Correcting them to the durable row's exact
`listappend` source made the local result `{1, 0, 0, 1, 0}`, identical to the
managed result. The direct `listappend_to_end` row then failed Banteng with
`E_VERBNF` before the inline pure builtin was added.

After both owned changes, the corrected focused Java 25 lifecycle regression
passed. The direct managed Banteng `listappend_to_end` row passed with one
selected and 11,504 deselected in 3.47 seconds. The exact managed lifecycle row
passed with one selected and 11,504 deselected in 3.73 seconds. The complete
managed lifecycle category passed rows 1 through 22 and stopped first at row
23, `audit_user_created_hook_for_new_login_object`, with 22 passed, 11,482
deselected, in 47.03 seconds. Row 22 is therefore the kept frontier; row 23's
`[4, 1, 0]` result remains a separate unchecked slice.

The final Java 25 `clean check installDist` gate passed in 13 seconds.

## Twenty-third row: `user_created` for a returned new player

The active durable row is `audit_user_created_hook_for_new_login_object` at
`../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:2019-2130`.
Its listener `do_login_command` creates an object, sets its player flag, stores
it, and returns it. The same listener's `user_created` stores `args[1]`. The row
requires both stored values to be objects and to be the same object.

Barn does not normatively define `user_created`. `../barn/spec/login.md:29-43,75-140`
describes return-based association and `user_connected`, while
`../barn/spec/server.md:129-139,157-177` and
`../barn/spec/objects.md:491-516` omit `user_created` from their hook lists.
Generic context and permission passages at `../barn/spec/login.md:163-177`,
`../barn/spec/objects.md:250-274,453-484`, and
`../barn/spec/tasks.md:371-395` do not bind this hook's trigger, handler,
metadata, permissions, association ordering, message ordering, or relationship
to `user_connected`. The claim that lifecycle hooks live on `#0` also disagrees
with Barn's listener-owned implementation.

Barn login dispatch at `../barn/server/input_processor.go:368-464` snapshots
`store.MaxObject()` before running the listener's `do_login_command`. The login
task context is built at `../barn/scheduler/task_factory.go:124-153`. Return
acceptance at `../barn/server/input_login.go:80-93,118-130` requires a positive
player/user object; the alternative `switch_player` path is distinct. Barn
classifies the return as new solely when its object number exceeds the
pre-login maximum, at `../barn/server/input_processor.go:431-460`.

Barn's `loginPlayer` at `../barn/server/input_login.go:237-313` removes the
negative mapping, associates the returned player, records the login, sends the
connect message, invokes `user_created` when classified new, and then invokes
`user_connected`. `callUserHook` at
`../barn/server/input_login.go:202-224` targets the listener with the returned
player as its only argument. The task path at
`../barn/scheduler/task_factory.go:61-105` and
`../barn/scheduler/task_runtime.go:124-185` supplies listener `this`, new-player
`player` and `caller`, `{new player}` as `args`, empty `argstr`, and hook-owner
permissions. Missing hooks are ignored; other errors do not undo association
or prevent Barn's next lifecycle hook.

Pinned Toast creates the negative connection and records its listener at
`/root/src/toaststunt/src/server.cc:1454-1492`, queues the unchanged line at
`src/server.cc:1534-1541`, and selects the login task at
`src/tasks.cc:1762-1769`. `do_login_task` at `src/tasks.cc:878-958` snapshots
`db_last_used_objid()` before `do_login_command`, then accepts a returned value
only if the connection remains open, the queue still has its negative player,
the value is exactly `TYPE_OBJ`, and `is_user` is true. It classifies the player
as new solely when the returned object number exceeds the snapshot, then
rebinds the task queue before calling `player_connected`.

Toast's `set_player_flag` path at `src/objects.cc:1002-1023` and
`src/db_objects.cc:1251-1265,1288-1292` sets and tests the user flag, so the
active row satisfies return acceptance. `player_connected` at
`src/server.cc:1658-1724` associates the network handle, sets connection time,
logs creation, queues `create_msg`, and synchronously invokes listener
`user_created`. It does not also invoke `user_connected` on this newly-created
branch.

Toast's `call_notifier` at `src/server.cc:518-527` calls `run_server_task` with
the returned player, listener, verb name `user_created`, `{returned player}` as
arguments, and empty `argstr`. `src/execute.cc:3278-3336` establishes listener
`this`, exact returned-object `player`, `caller = #-1`, hook-owner task
permissions, and inherited callable-verb lookup. Association and queued
`create_msg` precede the hook at `src/server.cc:1714-1723` and
`src/network.cc:1400-1404`. A missing hook is harmless. Hook failure or
suspension does not undo association; a suspended hook may resume later, and
there is no chained `user_connected` on Toast's new-player path.

Barn and Toast agree on the active assertion: snapshot the object-number high
water mark before login, accept the returned player, associate it, and invoke
the accepting listener's `user_created` with that exact object as `args[1]`.
They disagree outside this row: Toast accepts only ordinary objects, uses
`caller = #-1`, queues `create_msg`, and invokes no subsequent
`user_connected`; Barn admits its anonymous-object form, uses the player as
caller, sends its connect message, and then invokes `user_connected`.

Deferred adjacent behavior includes `switch_player`, hook metadata beyond
`args[1]`, configured messages, inherited hook ownership, hook errors or
suspension, existing-player redirection, recycled IDs, and the false-provenance
case where a login returns some other object allocated after the snapshot.

The exact managed row passed pinned WSL Toast commit
`aecc51e9449c6e7c95272f0f044b5ba38948459e`: one selected, 11,504
deselected, in 4.18 seconds. The active `args[1]` contract is therefore frozen
before Java design.

Banteng's committed pre-slice lifecycle run failed this row with `[4, 1, 0]`:
the returned user was an object, but the hook-observation property retained its
initial list because `MooRuntime.executeLogin` had no `user_created` dispatch.
`WorldTxn.objectCount()` could not serve as the pre-login high-water mark:
recycling removes live map entries, while allocation selects one above the
greatest live object number. The kept Java representation therefore adds only
`WorldTxn.maximumObjectId()`, snapshots it immediately before executing
`do_login_command`, and directly invokes listener `user_created` after fresh
association when the returned player exceeds that snapshot. The hook receives
listener `this`, returned-player `player`, `caller = #-1`, the returned player
as its only argument, and empty `argstr`; existing Banteng `user_connected`
behavior remains the recorded out-of-row divergence.

The focused JUnit regression creates and flags the returned player inside
`do_login_command`, observes `connection_info(args[1])` from `user_created`,
checks the hook locals, and checks Banteng's `user_created`-then-`user_connected`
ordering. It passed under Java 25 in 3 seconds. The exact managed Banteng row
then passed with one selected and 11,504 deselected in 4.30 seconds. The full
managed lifecycle category passed all 23 selected rows with 11,482 deselected
in 46.89 seconds. Both managed runs used disposable databases; their remaining
Banteng processes were identified by exact temp-database command lines, stopped
by PID, and followed by empty Banteng process inventories.

The final Java 25 `clean check installDist` gate passed in 13 seconds.
