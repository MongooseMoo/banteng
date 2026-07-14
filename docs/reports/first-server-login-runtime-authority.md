# First server login and eval runtime authority

## Scope and proof row

This record freezes the runtime semantics required to execute the first managed
row through the actual bundled database. The fixture is
`../moo-conformance-tests/src/moo_conformance/_db/Test.db`, SHA-256
`1a3f23ebb549e02ccf5341668425118fcdc935b977096add87bc2a8ef29d408e`.
The durable proof row is `arithmetic::addition` at
`../moo-conformance-tests/src/moo_conformance/_tests/basic/arithmetic.yaml:6`.

The exact managed input sequence is:

1. accept the managed harness's readiness probe as pre-login object `#-2`, run
   its initial empty login input, and observe the probe disconnect;
2. accept the test transport as the next negative connection object (normally
   `#-3`), run its initial empty login input, and receive `connect Wizard`;
3. receive `PREFIX -=!-^-!=-` and `SUFFIX -=!-v-!=-`;
4. receive five leading-`;` standard-property setup programs from
   `SocketTransport._ensure_standard_properties()`;
5. receive `; return 1 + 1;` for the selected row.

The transport owner is
`../moo-conformance-tests/src/moo_conformance/transport.py:216-308,348-381`.

## Normative Barn specification

- `../barn/spec/login.md:29-43`: login is a database-verb dispatch, not a host
  recognition of player names.
- `../barn/spec/objects.md:20-55,177-196`: object flags, ownership, movement,
  creation, player status, and property management.
- `../barn/spec/builtins/properties.md:121-141`: `add_property()` and duplicate
  `E_INVARG` behavior.
- `../barn/spec/tasks.md` has no complete opcode-level contract for this path;
  the exact frame, handler, and return behavior below is therefore frozen from
  Toast and the durable managed row rather than filled in from that absence.

## Current Barn path

- `../barn/server/connection_manager.go:63-75` and
  `../barn/server/input_processor.go:65-102,196-230`: negative connection IDs,
  initial input, and pre-login dispatch.
- `../barn/server/input_login.go:19-35`,
  `../barn/scheduler/task_factory.go:108-176`, and
  `../barn/scheduler/task_runtime.go:124-188`: database login verb and frame.
- `../barn/command/command.go:153-247`,
  `../barn/server/input_processor.go:480-605`, and
  `../barn/command/verbs.go:97-193`: leading-`;`, intrinsic delimiters, and
  player/location verb search.
- `../barn/builtins/strings.go:16-36`: `length`.
- `../barn/builtins/objects.go:23-260`,
  `objects_players.go:79-136`, and `objects_movement.go:9-70`: create, player
  flag, and move.
- `../barn/builtins/network.go:833-872,1080-1109`: notify and switch-player.
- `../barn/builtins/tasks.go:181-226`: task permissions.
- `../barn/builtins/properties.go:195-292`: add-property.
- `../barn/vm/registry.go:16-137`, `stack.go:65-119`, and
  `../barn/builtins/types.go:192-208`: dynamic eval, result wrapping, and
  literal formatting.

Barn is an implementation reference. The following freshly read Toast checkout
and durable row decide observable behavior.

## Verified Toast path

The checkout is `/root/src/toaststunt` at
`aecc51e9449c6e7c95272f0f044b5ba38948459e`, verified directly with
`git rev-parse HEAD` on 2026-07-14.

- `src/tasks.cc:106-122,258-319,775-966,1629-1799`: intrinsic delimiters,
  command tasks, login tasks, and switch-player.
- `src/parse_cmd.cc:127-158`: leading `;` becomes the `eval` command.
- `src/objects.cc:60-236,312-450`: move and create, including ignored missing
  `accept`, `exitfunc`, `enterfunc`, and `initialize` verbs at their documented
  boundaries.
- `src/objects.cc:1002-1030`: player-flag mutation.
- `src/property.cc:207-246,336-351`: add-property validation and registration.
- `src/execute.cc:1840-2080` and `src/db_properties.cc:630-700`: built-in and
  ordinary property get/set, including owner, programmer, and wizard fields.
- `src/list.cc:632-665,1189-1215`: length and literal formatting.
- `src/verbs.cc:604-678` and `src/execute.cc:3386-3435`: eval parsing, explicit
  activation setup, and `{success, value}` wrapping.
- `src/execute.cc:3695-3720`: set-task-permissions.
- `src/server.cc:2920-2965`: ordered line notification.
- `src/parser.y:265-273,1212-1339`, `src/code_gen.cc:609-715,890-915,
  1081-1088`, and `src/execute.cc:1466-1512,1855-2080,2201-2219,3030-3034`:
  parser, property opcodes, control flow, handlers, addition, and return.

## Frozen execution

### Initial blank login task

For each accepted connection the frame is `this=#0`, `player=connection`,
`caller=connection`, `programmer=#3`, `verb="do_login_command"`, `args={}`,
and `argstr=""`. Both top-level conditions short-circuit because `args` is
false. The verb falls off its end, returns integer zero, produces no effects,
and leaves the connection negative. The managed server readiness check consumes
`#-2`; the Wizard transport is normally `#-3`. No semantic path may hard-code
either negative ID.

### `connect Wizard`

The same stored verb receives the actual negative connection object,
`args={"connect", "Wizard"}`, and `argstr="connect Wizard"`. String comparison is ASCII-case-insensitive, so
`"Wizard" == "wizard"` is true. Source-order effects are:

1. read `$nothing` as `#0.nothing == #-1`;
2. `create(#-1)` allocates numbered `#8`, initially owner `#3`, no parents,
   location `#-1`, flags zero, and empty ordered members; missing `initialize`
   is ignored by `create`;
3. `#8.owner = #8`;
4. `set_player_flag(#8, 1)` adds the player flag and player index entry;
5. `#8.programmer = 1` and `#8.wizard = 1`, producing flags `7`;
6. `move(#8, #2)` appends `#8` to `#2.contents`, producing `[3,4,8]`; the
   wizard caller may move despite absent `accept`, and absent movement hooks are
   ignored at the Toast builtin boundaries;
7. `switch_player(connection, #8)` reattaches that negative connection;
8. `return;` returns integer zero without introducing a public none value;
9. the connection emits `*** Connected ***\r\n`.

The login is executed from database source. Java must not recognize `Wizard`,
manufacture `#8`, or apply these effects outside the VM and `WorldTxn`.

### Managed setup programs

`PREFIX` and `SUFFIX` are connection intrinsics. Each following setup command is
ordinary dynamic MOO source:

```moo
try
  add_property(#0, name, value, {#0, "rc"});
except (ANY)
  return 0;
endtry
```

The existing `object`, `anonymous`, and `nothing` definitions raise
`E_INVARG`, which the handler catches. `anon=#5` and `sysobj=#0` are appended
as local properties on `#0`, owned by `#0`, with read/change permissions. Each
setup response is framed by the connection delimiters so the harness can
consume it.

### Arithmetic eval

Leading `;` searches the new player and then location `#2`, finding persisted
`#2:eval`. Its frame starts with `this=#2`, `player=#8`, `caller=#8`,
`programmer=#3`, and `argstr="return 1 + 1;"`.

1. `set_task_perms(player)` changes the programmer to wizard player `#8`.
2. The outer `try/finally` and inner `try/except ANY` install explicit handler
   state.
3. `notify()` emits the eval prefix.
4. `eval(argstr)` compiles the dynamic source, pushes a frame on the same
   explicit VM, executes two integer constants, addition, and return, then
   produces `{1, 2}`.
5. `toliteral()` produces `"{1, 2}"`, which `notify()` emits.
6. The inner handler is removed without running.
7. The outer `finally` emits the eval suffix and the verb falls off with zero.

Including connection delimiters, the arithmetic command emits prefix, eval
prefix, `{1, 2}`, eval suffix, and connection suffix as ordered CRLF lines. The
managed transport deliberately consumes the double marker pair.

## Smallest Java representation

- Extend the existing `BytecodeProgram`, `MooCompiler`, `VmState`, and `MooVm`;
  do not add an alternate interpreter or source-result special case.
- Frames, locals, operand stacks, handlers, pending errors, eval markers, and
  task context are explicit heap state. Java exceptions do not represent MOO
  control flow.
- Return-without-value and verb falloff produce integer zero; no `NoneValue` is
  authorized.
- One concrete runtime owner invokes stored verbs and one concrete builtin
  owner dispatches the required calls. There is no builtin interface,
  reflection registry, adapter, or facade.
- Every object, property, flag, player-index, topology, and connection-switch
  read/write goes through the concrete `WorldTxn`. Output is an ordered staged
  effect published only after the world mutation succeeds.

The complete stored login verb must compile, including its unexecuted while,
catch, shutdown, recycle, boot, and suspend branches. Names in unexecuted
branches may be resolved at runtime, but syntax and bytecode lowering may not
reject them. The executed path additionally requires variables, assignments,
conditions, short-circuit `&&`, comparisons, lists, one-based indexing,
property get/set, calls, return zero, nested try/except/finally, dynamic eval,
and catchable MOO errors.
