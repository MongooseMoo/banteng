# First server slice authority

## Scope

The first production slice boots the bundled conformance `Test.db`, accepts one
TCP connection, executes the database-defined login for `connect Wizard`, runs
`; return 1 + 1;` through the database-defined eval path, emits the exact Toast
wire response, and shuts down cleanly.

The managed transport also establishes response delimiters and standard system
properties before it submits that row. Those setup commands are part of the
first causal production path; the arithmetic row cannot run without consuming
their marked responses.

The fixture is
`../moo-conformance-tests/src/moo_conformance/_db/Test.db`, SHA-256
`1a3f23ebb549e02ccf5341668425118fcdc935b977096add87bc2a8ef29d408e`.
Its first line is `** LambdaMOO Database, Format Version 4 **`. This slice
therefore implements v4 input. A v17-only reader would not boot the authoritative
fixture. Dynamic source compilation uses the current v17 language.

## Normative Barn specification

- `../barn/spec/database.md:32-80`: read v4 and v17, write v17, and preserve the
  declared database section order.
- `../barn/spec/server.md:9-17`: bind listeners, run startup hooks, then accept.
- `../barn/spec/login.md:29-43`: login is dispatched through the listener's
  `do_login_command` verb rather than recognized by host code.

The current fixture and verified Toast behavior refine the login prose: the
verb receives the full line as `argstr`, parsed words as `args`, and the
negative pre-login connection object as `player`.

## Verified Toast authority

Source identity is `/root/src/toaststunt` at
`aecc51e9449c6e7c95272f0f044b5ba38948459e`, as recorded in
`docs/reports/toast-oracle-identity-2026-07-14.md`.

- `src/server.cc:1937,2166-2171,2262-2287,2304-2352`: process entry, database
  initialization/load, listener construction, and listener startup.
- `src/db_file.cc:48-51,838-903,924-1024`: database header/version dispatch,
  v4 header/object/program/task loading, and after-load work.
- `src/db_io.cc:76-221,274-282` and `src/structures.h:99-119,163-177`:
  newline-delimited scalars, tagged values, and stored-program parsing.
- `src/server.cc:1455-1510,1535-1541`: negative pre-login connection IDs and
  the initial blank input task.
- `src/network.cc:385-570,708-803,1233,1292-1403`: CR/LF and Telnet input,
  accept, CRLF output, and socket writes.
- `src/tasks.cc:812-966,1629-1799`: login and command task dispatch.
- `src/tasks.cc:106-122,258-319,775-798,812-876`: intrinsic-command
  registration, `PREFIX`/`SUFFIX` storage, and delimiter emission around a
  command task.
- `src/parse_cmd.cc:127-158`: leading `;` becomes the `eval` command with the
  remaining source in `argstr`.
- `src/property.cc:207-246,336-351`: `add_property()` validation, duplicate
  rejection with `E_INVARG`, and builtin registration.
- `src/verbs.cc:604-678`, `src/parser.y:265-273,1212-1339`,
  `src/code_gen.cc:609-715,1081-1088`, `src/execute.cc:1466-1512,2201-2219,
  3030-3034`, and `src/numbers.cc:251-278`: eval parsing, integer immediates,
  addition, return, and the `{success, value}` result.

## Current Barn implementation reference

- `../barn/cmd/barn/main.go:113-153,302-341`: command line, server
  construction, database load, and start.
- `../barn/db/format/reader.go:14-53,75-130`: database owner and version
  dispatch; the conformance fixture must take its v4 path.
- `../barn/server/server.go:74-226`: concrete server composition and startup.
- `../barn/server/connection_manager.go:91-151,275-330,390-402,643-659`:
  listener, accept, connection creation, and negative connection IDs.
- `../barn/server/input_processor.go:67-230,368-478,480-605`: serialized input,
  login dispatch, command parsing, and eval output.
- `../barn/server/input_processor.go:500-605` and
  `../barn/server/connection.go:20-27,190-206`: `PREFIX`/`SUFFIX` ownership and
  ordered output delimiters.
- `../barn/builtins/properties.go:195-292`: `add_property()` validation and
  store mutation. `../barn/spec/builtins/properties.md:121-141` is the normative
  Barn builtin section; it agrees that duplicates raise `E_INVARG`.
- `../barn/scheduler/task_factory.go:124-176` and
  `../barn/scheduler/task_runtime.go:24-193,359-366`: database-verb login task
  construction and VM execution.
- `../barn/compiler/compiler.go:37-55`, `../barn/bytecode/compiler.go:472-505,
  588-610,2177-2188`, and `../barn/vm/vm.go:179-282,427-469,540-542`:
  parse/lower/execute of integer addition and return.
- `../barn/server/transport.go:88-172,212-222`: line input and flushed CRLF
  output.

Barn is an implementation reference. Fresh WSL Toast and the durable
conformance row decide observable disagreements.

## Frozen observable contract

1. Load the actual v4 fixture and its object, property, verb, and source data.
2. Bind the configured address and port before accepting connections.
3. Allocate decreasing negative unauthenticated connection objects and deliver
   an initial empty login input before each connection's first socket line. The
   managed readiness probe consumes `#-2` and disconnects; the test transport
   is normally `#-3`. Do not hard-code either ID into login semantics.
4. Run `#0:do_login_command` from the database. Do not recognize `Wizard` in
   Java. For `connect Wizard`, the fixture creates object `#8`, makes it a
   player/programmer/wizard, moves it to `#2`, and switches the connection.
5. Accept intrinsic `PREFIX -=!-^-!=-` and `SUFFIX -=!-v-!=-` commands and
   store those delimiters on the connection. Emit them around each later
   command in the same serialized output stream.
6. Execute the transport's five exact standard-property setup programs through
   the same leading-`;`, parser, bytecode, VM, builtin, and `WorldTxn` path:
   `try add_property(#0, name, value, {#0, "rc"}); except (ANY) return 0;
   endtry`. Existing `object`, `anonymous`, and `nothing` definitions raise
   `E_INVARG` and are caught; `anon = #5` and `sysobj = #0` are added to `#0`.
7. Preserve case-insensitive MOO string equality.
8. Route leading `;` through command parsing and the database's `#2:eval` verb.
   Do not recognize the arithmetic source or manufacture its result.
9. Compile and execute integer literals, integer addition, and return through
   explicit VM state. Successful `eval()` returns `{1, 2}`.
10. Serialize input/task execution through one owner and serialize connection
   output.
11. For the arithmetic command, emit the connection prefix, then the eval
    verb's prefix, `{1, 2}`, eval suffix, and connection suffix, all as CRLF
    lines with no trailing spaces. The transport deliberately accepts the
    resulting double delimiter pair.
12. Close the listener and active connection cleanly when the server stops.

## Conformance proof

The existing durable row is
`../moo-conformance-tests/src/moo_conformance/_tests/basic/arithmetic.yaml:6`,
ID `arithmetic::addition`, with source `1 + 1` and expected value `2`.
`MooTestCase.get_code_to_execute()` wraps it as `return 1 + 1;`, the socket
transport prepends `; `, and the session connects as `Wizard`. The resulting
wire input is exactly the scope above. Before the row, current
`SocketTransport.connect()` sends `PREFIX` and `SUFFIX`, then
`_ensure_standard_properties()` sends and receives the five `add_property()`
programs described above (`../moo-conformance-tests/src/moo_conformance/transport.py:216-308,348-381`).

Target command after `installDist`:

```powershell
uv run --project C:\Users\Q\code\moo-conformance-tests moo-conformance `
  --server-command "C:/Users/Q/code/banteng/build/install/banteng/bin/banteng.bat --database {db} --checkpoint {db}.new --listen-address 127.0.0.1 --port {port}" `
  -k "arithmetic::addition"
```

The production test is red until the real socket/database/VM path passes this
row. Focused Java tests do not replace the managed row or the full Gradle gate.

## Deliberately deferred from this slice

Checkpoint/restart, v17 input/output, remaining value families, arbitrary MOO
programs, multiple connections, and parallel execution remain required later.
They must extend this production path rather than introduce a second server or
interpreter.
