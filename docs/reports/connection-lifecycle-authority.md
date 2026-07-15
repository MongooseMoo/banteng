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
