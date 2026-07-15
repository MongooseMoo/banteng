# SQLite builtin and task-prerequisite authority

## Scope

This record covers the exact contract required by the 14 managed `sqlite::`
rows. The family includes the nine SQLite builtins and the language/task
prerequisites those rows execute: `typeof`, the built-in `LIST` constant,
binary `in`, `call_function`, an unnamed delayed `fork`, and timed `suspend`.

This record does not authorize a generic database interface, store adapter,
builtin sender, call adapter, scheduler interface, Java-thread task model,
synchronous interrupt substitute, or timer shortcut. SQLite is an external,
suspending process resource; it is not Banteng's durable world database.

## Verified identities

- Banteng committed base: `7d7f8b464dedf93a10bae664432c3c43340115bc`.
- Banteng oracle procedure:
  `docs/reports/toast-oracle-identity-2026-07-14.md`.
- Toast source: `/root/src/toaststunt` at
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Toast executable: `/root/src/toaststunt/build-release/moo`, built with SQLite
  and PCRE2 enabled.
- Barn implementation reference at audit time:
  `864de996a111674adfe15c330f8e85813f4641f0`.
- Durable conformance authority:
  `../moo-conformance-tests` at
  `4de57abc69614ccac71ae8fb0848a0771fde4ea2`.
- Stock profile: `profiles/toast/stock-wsl-testdb.json`.

## Normative Barn specification

- `../barn/spec/builtins/sqlite.md` specifies the nine names, wizard access,
  ordinary handle/result shapes, asynchronous query/execute behavior, and SQL
  errors returned as strings. It is incomplete or stale for the exact option
  name, flags, handle quota and reuse, duplicate paths, close-under-load,
  conversion, binding, locking, and idle-interrupt behavior described below.
- `../barn/spec/builtins/types.md:9-40` and
  `../barn/spec/types.md:480-503` specify `typeof(value)` and `LIST == 4`.
- `../barn/spec/operators.md:578-604` specifies one-based LIST membership and
  zero when absent.
- `../barn/spec/builtins/verbs.md:300-314` specifies
  `call_function(name, args...)`, but says an unknown function raises
  `E_INVARG`; that contract is outside the active SQLite rows.
- `../barn/spec/tasks.md:96-121` specifies that a fork allocates and queues a
  child before the parent continues, and that even a zero-delay child does not
  run inline at the fork opcode.
- `../barn/spec/tasks.md:186-224` specifies explicit timed suspension, captured
  VM state, automatic wakeup, and background limits.
- `../barn/spec/vm.md:21-31,113-140` assigns explicit fork/suspend outcomes and
  captured VM state to the VM and task lifecycle/queues to the scheduler.
- `../barn/spec/statements.md:506-522,533-540,558-608,693-720` specifies that
  one `try` has an ordered list of exception clauses, the first matching clause
  handles the error and receives the error package when it names a variable,
  and a `finally` clause runs after either the body or the selected handler and
  during error or return unwinding.
- `../barn/spec/statements.md:5,24-40` specifies that a bare semicolon is a
  valid empty statement and has no effect.

## Current Barn implementation path

SQLite registration and behavior are owned by:

- `../barn/builtins/registry.go:273-282` and
  `../barn/builtins/signatures.go:28-36` for names and signatures;
- `../barn/builtins/sqlite.go` for connection handles, queries, execution,
  result conversion, limits, interruption, and suspension;
- `../barn/scheduler` and `../barn/task` for forked and suspended task
  lifecycle.

The prerequisites are owned by:

- `../barn/builtins/types.go:15-19` and
  `../barn/builtins/registry.go:56` for `typeof`;
- `../barn/bytecode/compiler.go:532-545` and
  `../barn/vm/environment.go:21-30` for built-in type constants;
- `../barn/bytecode/opcodes.go:57`,
  `../barn/bytecode/compiler.go:641`, and
  `../barn/vm/op_compare.go:134-190` for binary `in`;
- `../barn/builtins/signatures.go:161-181` and
  `../barn/builtins/registry.go:286` for `call_function`;
- `../barn/bytecode/compiler.go:2533-2567`, `../barn/vm/control.go:16-118`,
  and `../barn/scheduler` for fork capture and scheduling;
- `../barn/builtins/tasks.go:104-148`, `../barn/vm/op_misc.go:66-72`, and
  `../barn/scheduler/task_runtime.go:230-260` for suspension and resumption.
- `../barn/verb/ir.go:478-496`,
  `../barn/parser/parser_stmt.go:535-598`, and
  `../barn/bytecode/compiler.go:2211-2277` preserve the ordered exception
  clauses on one try statement and emit every clause before the finalizer;
  `../barn/vm/control.go:173-207` and `../barn/vm/vm.go:727-788` preserve source
  order on the handler stack, select the first matching clause, and bind the
  structured error package. `../barn/vm/builtin_error_value_test.go:9-29`
  freezes that package shape.
- `../barn/parser/parser_stmt.go:27-45,48-78` consumes a bare semicolon while
  parsing a statement sequence, and `../barn/bytecode/compiler.go:1688-1692`
  emits no bytecode for the resulting nil expression.

Barn agrees with Toast on the nine public names and signatures, wizard access,
the active row result shapes, type code 4, one-based LIST membership,
`call_function` forwarding, and the need to suspend a MOO task while a query is
active. It disagrees with Toast in observable SQLite details:

- it ignores open flags and has no Toast handle quota, duplicate-path
  rejection, or final-close handle-ID reset;
- it removes and waits when closing an active handle, while Toast raises
  `E_PERM`;
- it sorts handles, while Toast's order is unspecified;
- it returns driver-native query values rather than Toast's flag-controlled
  text parsing and sanitization;
- it binds additional value kinds and 64-bit integers, while Toast binds only
  STR, 32-bit INT, FLOAT, and OBJ and leaves unsupported values NULL;
- it lexically guesses whether SQL returns rows;
- it shadows limits instead of calling the real SQLite limit operation;
- it queues interruption for a future operation, while Toast immediately calls
  SQLite and treats an idle interrupt as a no-op;
- it serializes connection work, while Toast allows background submissions to
  complete without a FIFO guarantee.

Toast controls every disagreement.

## Current Toast implementation path

### SQLite registry and handles

`src/sqlite.cc:664-672` registers exactly:

- `sqlite_open(STR [, INT])`;
- `sqlite_close(INT)`;
- `sqlite_handles()`;
- `sqlite_info(INT)`;
- `sqlite_query(INT, STR [, ANY])`;
- `sqlite_execute(INT, STR, LIST)`;
- `sqlite_last_insert_row_id(INT)`;
- `sqlite_limit(INT, ANY, INT)`;
- `sqlite_interrupt(INT)`.

All nine bodies require a wizard programmer. Connections are process-global,
start at positive handle 1, default to a maximum of 20 handles, and default to
flags 6: parse types and objects, but do not sanitize strings. Closing the last
handle resets the next ID to 1; otherwise IDs are monotonic with gaps. Resolved
duplicate non-memory paths are rejected, while `:memory:` and an empty path do
not participate in path duplicate detection. `sqlite_handles()` order is not
specified. `sqlite_close()` raises `E_INVARG` for a missing handle and `E_PERM`
when the handle has active locks.

`sqlite_info()` returns a map with exactly `path`, `parse_types`,
`parse_objects`, `sanitize_strings`, and `locks`.

### Query, execute, conversion, limits, and interruption

- `src/sqlite.cc:485-535` makes a valid `sqlite_query` a background operation.
  An invalid handle returns the ErrorValue `E_INVARG` as a value. SQLite errors
  return SQLite's message as a MOO string. Header mode produces a row of
  `{column-name, value}` pairs.
- `src/sqlite.cc:360-484` owns `sqlite_execute`, prepared bindings, row
  conversion, and the active lock count. Invalid handles also return
  ErrorValue `E_INVARG` as a value. STR binds as text, INT uses the 32-bit
  SQLite binding, FLOAT uses double, and OBJ binds its `#number` text.
  Unsupported values remain NULL.
- Both query and execute read SQL values as text. With default flags, integer
  text becomes INT, float text becomes FLOAT, and `#integer` becomes OBJ.
  NULL becomes the string `"NULL"`. Disabling type parsing leaves non-NULL
  values as strings. Sanitizing converts newlines to tabs.
- `sqlite_last_insert_row_id` directly returns SQLite's connection value and
  raises `E_INVARG` for a missing handle.
- `sqlite_limit` accepts exact case-sensitive names or integers 0 through 11,
  calls `sqlite3_limit`, returns the prior value, and raises `E_INVARG` for an
  invalid category or handle.
- `src/sqlite.cc:628-650` makes `sqlite_interrupt` immediately call
  `sqlite3_interrupt` on a valid handle and return zero. An invalid handle
  raises `E_INVARG`.
- `src/background.cc:148-255` suspends the calling MOO VM while the worker is
  active and resumes that same VM with the worker result. This is the semantic
  boundary; blocking the server's serialized runtime or returning a placeholder
  is not equivalent.

### Language prerequisites

- `src/objects.cc:272-279,1339-1340` registers `typeof` for exactly one ANY
  argument and returns `argument.type & TYPE_DB_MASK` with no permission check.
- `src/sym_table.cc:78-92`, `src/eval_env.cc:75-90`, and
  `src/include/structures.h:84-115` establish `LIST` as a built-in runtime
  variable with public value 4.
- `src/code_gen.cc:690`, `src/execute.cc:1383-1408`, and
  `src/collection.cc:45-57` compile and execute binary `in`. LIST membership
  returns the one-based first equal index or zero and compares without case
  sensitivity. A non-collection right operand raises `E_TYPE`.
- `src/execute.cc:3436-3467,3770-3776` and
  `src/functions.cc:186-287` register `call_function` with one required STR
  name, perform case-insensitive lookup, remove the name, and invoke the target
  through ordinary arity, type, permission, result, error, and suspension
  handling. The active row therefore preserves `sqlite_info`'s raised
  `E_INVARG`.

### Fork, suspension, and the interrupt row

- `src/parser.y:212-234` parses unnamed and named fork statements.
- `src/code_gen.cc:1065-1074` evaluates the delay in the parent and stores the
  body as a distinct fork bytecode vector.
- `src/execute.cc:2084-2110` accepts an INT or FLOAT delay, raises `E_TYPE` for
  other types and `E_INVARG` for a negative delay, and enqueues rather than
  running the child inline.
- `src/tasks.cc:1257-1320` checks queue quota, allocates a task ID, copies the
  runtime environment, queues the fork by absolute start time, and later
  captures a suspended VM with a zero default resume value.
- `src/execute.cc:3520-3539` registers `suspend` with zero or one numeric
  argument and rejects a negative duration with `E_INVARG`.
- `src/tasks.cc:1628-1646,1772-1795` wakes due forked and suspended tasks and
  resumes the saved VM.

### Ordered exception clauses used by the presence row

- `src/parser.y:317-348` builds the exception clauses in source order and
  rejects a clause after `ANY`; `src/include/ast.h:135-142,174-196` keeps that
  ordered list on one try statement.
- `src/code_gen.cc:1089-1124` emits every clause for the one protected body.
  `src/execute.cc:277-300` scans those guards from first to last and selects
  only the first match. `src/execute.cc:269-276,2577-2613` owns finalizer and
  unwind behavior after selection.
- `src/parser.y:121-135,275-282` parses statement sequences, accepts a bare
  semicolon as a null statement, and then parses try/except through `ENDTRY`.

The exact `sqlite_exists_in_runtime` body has `except (E_VERBNF)` followed by
`except (ANY)`. If `sqlite_open` is absent, the first clause returns zero. If
the builtin exists, its deliberate nine-argument call raises `E_ARGS`, the
first clause does not match, and the second clause returns one. The conformance
schema appends a terminal semicolon and the socket transport flattens the body,
so the exact managed source ends in `endtry;`; the final semicolon is a separate
empty statement. The managed Toast family pass therefore proves ordered
multi-clause selection and acceptance of that empty statement. Banteng already
preserves `List<ExceptClause>` in `Ast.Try` and `MooParser`, but initially
rejected both multiple clauses in the compiler and a bare semicolon in the
parser. Both prerequisites are fixed in the active slice. Before the parser
fix, the empty statement failed before the compiler or SQLite dispatch and
dynamic `eval` converted that parse failure to `E_INVARG`.

The exact interrupt row first queues `fork (1)`. When that child starts, it
calls `suspend(1)`, so interruption occurs approximately two seconds after the
original fork enqueue, subject to scheduler latency. Meanwhile the parent's
`sqlite_query` is suspended on a background worker. The child interrupts that
active connection; the worker then resumes the parent with a SQLite error
string containing `interrupt`.

## Durable conformance evidence

`../moo-conformance-tests/src/moo_conformance/_tests/builtins/sqlite.yaml`
contains exactly 14 stock-profile rows:

1. `sqlite::sqlite_exists_in_runtime`;
2. `sqlite::sqlite_open_close_tracks_handles_and_info`;
3. `sqlite::sqlite_execute_insert_updates_last_insert_row_id`;
4. `sqlite::sqlite_execute_select_returns_rows`;
5. `sqlite::sqlite_query_with_headers_returns_column_names`;
6. `sqlite::sqlite_limit_returns_prior_value_and_updates_current`;
7. `sqlite::sqlite_limit_invalid_category`;
8. `sqlite::sqlite_close_invalid_handle`;
9. `sqlite::sqlite_query_invalid_handle`;
10. `sqlite::sqlite_execute_invalid_handle`;
11. `sqlite::sqlite_info_invalid_handle`;
12. `sqlite::sqlite_info_invalid_handle_through_call_function`;
13. `sqlite::sqlite_interrupt_invalid_handle`;
14. `sqlite::sqlite_interrupt_aborts_long_running_query`.

The rows freeze the exact active surface: positive handles, default info,
tracking, execute/query row shapes, header names, last insert ID, real limit
updates, raised versus returned invalid-handle errors, `call_function`
forwarding, and active-query interruption. They also execute `typeof`, `LIST`,
LIST membership, fork, and timed suspension through the managed server.

The active rows deliberately do not freeze duplicate file paths, handle quota
and reuse, close-under-lock, non-default flags, unsupported execute bindings,
NULL/sanitized conversion, idle interruption, exact SQLite error strings, or
concurrent completion ordering. Those contracts are not inferred into this
slice.

## Managed oracle result

The exact managed procedure from
`docs/reports/toast-oracle-identity-2026-07-14.md` ran from
`C:\Users\Q\code\barn`, changing only the selector to `-k "sqlite::"`.
It collected 11,504 tests and passed all 14 selected SQLite rows:

```text
14 passed, 11490 deselected in 5.37s
```

The active-query interrupt row passed in that same run. This proves that the
pinned executable's effective thread mode and SQLite library support the exact
background suspension/interruption contract the durable row requires. No
active disagreement or missing discriminator was found, so no conformance-row
change is required for this slice. Exact version-specific SQLite error text
remains deliberately unfrozen; the durable row requires only `interrupt`.

## Representation boundary after the oracle gate

The evidence record was committed before API design as Banteng commit
`375320b`. The smallest representation for the active rows is now frozen:

- Add xerial `sqlite-jdbc` 3.53.2.0 as the one implementation dependency and
  update the existing Gradle lock and verification records. Its concrete
  `SQLiteConnection.getDatabase()` exposes both native `limit(int, int)` and
  `interrupt()`, so Banteng does not emulate either operation.
- Keep SQLite handle ownership inside the one process-lifetime
  `BuiltinCatalog` instance already owned by `MooRuntime`. Add one private
  concrete handle state there for the JDBC connection, path, flags, and active
  lock count. Do not add a database interface, registry interface, adapter, or
  alternate store package.
- Add all nine names directly to the existing catalog switch with explicit
  wizard checks and one effect classification: query/execute are suspending
  host operations; open/close/limit/interrupt are irrevocable external
  operations; handles/info/last-insert-ID are external reads.
- Extend the existing concrete `BuiltinCatalog.Result` with only the two
  suspension shapes the VM must hand to the runtime: a timed delay or a
  `CompletableFuture<MooValue>` host result. `call_function` recursively invokes
  the same catalog after removing its string name, preserving the target result
  without a sender or adapter.
- Run query and execute on one virtual thread per host operation. Increment the
  handle lock before launch, decrement it in the worker's final path, return SQL
  failures as MOO strings, and resume the exact suspended `VmState` with the
  resulting MOO value. Native `interrupt()` remains callable while the worker
  is active.
- Add `typeof` directly to the catalog. Resolve `LIST` directly in the existing
  VM local-load path from `MooValue.Type.LIST.code()`; do not add an environment
  abstraction.
- Extend the existing binary-expression path with `IN`. The active slice
  implements structural LIST membership, returning the first one-based index
  or zero and raising `E_TYPE` for a non-LIST right operand. Do not add a
  membership helper.
- Add one closed `Ast.Fork(delay, body)` statement and `FORK` opcode. Store fork
  bodies as immutable child `BytecodeProgram` vectors on their parent program,
  with the instruction carrying the vector index. Do not encode source text or
  a Java callback as the child.
- Extend the existing `VmState` with explicit FORKED and SUSPENDED outcomes,
  one concrete fork request containing the child program, copied locals,
  programmer, and delay, and direct resume with a supplied MOO value. The VM
  stops at those explicit segment boundaries; it never blocks on JDBC or a
  timer.
- Keep task coordination in the existing concrete `MooRuntime`. On a fork
  boundary it queues the child state by wake time and immediately resumes the
  parent before any child can run. On timed or host suspension it retains the
  same `VmState`, wakes it with zero or the future result, and continues it.
  Runtime maps of concrete `VmState` instances are sufficient for this first
  task slice; no scheduler interface or Java-thread-as-task model is added.
- The exact interrupt row has two delays: the child is queued for one second,
  then suspends itself for another second. The runtime must preserve both
  boundaries while the parent's query future remains active.
- Preserve ordered statement handlers with the existing `HandlerSpec` rather
  than adding a second handler model. Lower one owner handler first, carrying
  the shared `finally` and end targets, then lower each existing
  `Ast.ExceptClause` as a contiguous handler in reverse source order so the
  first source clause is on top at runtime. Normal completion leaves those
  clause handlers in source order and then leaves the owner. On a structured
  clause match, the VM removes the matching handler and the remaining
  contiguous structured sibling handlers before entering the selected body,
  leaving only the owner to run the shared finalizer or reach the shared end.
  This removal is required: an error raised by the selected handler is not
  eligible for a later sibling clause. Expression catches retain their existing
  single-handler behavior. No new handler record, interface, or dispatch
  helper is added.
- Consume and skip `SEMICOLON` directly in the existing statement-sequence
  parser as an empty no-op. The lexer already identifies it, and the semantic
  contract requires no AST node or bytecode. This applies wherever a bare
  semicolon appears, including the harness-generated terminator immediately
  after `endtry`; do not special-case try statements or managed input.

Focused Java tests must cover the exact prerequisite expressions, fork-vector
lowering, ordered first-match statement handlers, a later-clause match, an
error raised inside the selected handler escaping all sibling clauses, shared
`finally` execution, parent-before-child behavior, timed resumption, native
limit updates, raised versus returned invalid-handle errors, result conversion,
and active interruption. The kept slice must pass focused tests, `gradlew
check`, `installDist`, the managed 14-row `sqlite::` family, a read-only review,
and a fresh plan reread before commit.
