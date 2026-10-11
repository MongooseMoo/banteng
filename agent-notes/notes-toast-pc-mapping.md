# Toast v17 PC to Banteng instruction mapping audit

Date: 2026-07-21
Scope: read-only mapping audit for the suspended activations in `fixtures/mongoose.db`.

## Required answers

- Inventory every distinct fixture program/vector/`pc`/`error_pc` tuple.
- Prove mappings for `pc/error_pc` pairs `240/238`, `156/155`, and `352/350`.
- Identify pinned Toast compiler, disassembler, and line-number facilities that can interpret those offsets.
- Identify Banteng parser/compiler source-position information available for resumption.
- Decide the smallest production-grade general mapping and whether it can avoid embedding Toast.

## Pinned source identity

Exact commands:

```text
wsl.exe -d Debian -- bash -lc 'git -C /root/src/toaststunt rev-parse HEAD; git -C /root/src/toaststunt-mongoose rev-parse HEAD'
```

Results:

- stock Toast: `aecc51e9449c6e7c95272f0f044b5ba38948459e`
- Mongoose Toast: `72e3c7f96ce7a41fdeba793aef8818dc4408072e`

## Toast lookup surface

Repository search found these exact production facilities in both pinned trees:

- `src/decompile.cc:900`: `program_to_tree(Program *, int vector, int pc_vector, int pc)`
- `src/decompile.cc:1055`: `find_line_number(Program *, int vector, unsigned pc)`
- `src/disassemble.cc:196`: `disassemble(Program *, Printer, void *)`
- `src/execute.cc:3990`: v17 activation serialization writes `a.pc`, `a.bi_func_pc`, and `a.error_pc`
- `src/execute.cc:4072-4073`: activation input restores `error_pc` (or derives it from `pc` for old DB versions)
- `src/include/decompile.h:22`: the only exported source-location API is `find_line_number`

This establishes that Toast itself interprets a serialized PC against the exact compiled `Program`; no source offset is serialized alongside the PC.

## `program_to_tree` and `find_line_number`

Pinned stock `src/decompile.cc:900-1072` proves:

- `program_to_tree` selects the bytecode vector containing the saved PC (`MAIN_VECTOR` or a numbered fork vector).
- It sets a global `hot_byte` to `bc.vector + pc`, decompiles bytecode back to an AST, and marks the AST node/position corresponding to that exact byte.
- `find_line_number` walks the decompiled tree from `prog->first_lineno` and returns the one-based unparsed-program line containing the hot byte.
- It returns only a line number. It does not expose expression span, AST path, stack effect, or a portable continuation identifier.

Thus `find_line_number(program, vector, error_pc)` is exact for Toast's diagnostic source line, but it is many-to-one and cannot by itself distinguish multiple calls or suspension points on one line.

## `disassemble`

Pinned stock `src/disassemble.cc:196-431` proves:

- `disassemble` prints every Toast instruction with its byte offset and decoded operands.
- It is file-local (`static`). Public C++ entry points only print the whole program to a `FILE *` or stderr.
- The in-server `disassemble(obj, verb)` builtin operates only on a database verb, not an arbitrary persisted activation program.

This is sufficient as an audit oracle after compiling the persisted source with the pinned Toast compiler, but it is not a reusable source-to-PC API and cannot directly inspect the program embedded in an activation through a public builtin.

## Activation PC semantics and validity

Pinned stock `src/execute.cc:803-850,3980-4097` proves:

- The interpreter resumes at `bc.vector + RUN_ACTIV.pc`; the saved `pc` is the byte offset of the *next* instruction.
- `error_pc` is independently saved and points at the byte used for exception/source-line attribution.
- `check_pc_validity` accepts a suspended activation only when the byte immediately before `pc` is `OP_CALL_VERB` or the two bytes immediately before `pc` begin `OP_BI_FUNC_CALL`.
- Consequently, the saved continuation is always immediately after a verb-call or builtin-call opcode. It is not the offset of the call itself.
- `read_activ` recompiles the serialized source to a Toast `Program` and validates the serialized byte offset against that exact program before accepting the task.

The mapping must therefore identify the exact dynamic call expression represented by the instruction ending at `pc`, then resume after that call with the serialized runtime stack and resumption value/error. Mapping only `error_pc` to a line cannot supply that identity.

## Fixture activation inventory

Exact line-oriented commands over only the suspended-task section (`191..3562`) found 14 task envelopes and 15 activation footers:

```text
task envelopes: grep pattern ^-1 [0-9]+ 0$
activation PCs: grep pattern ^[0-9]+ [0-9]+ [0-9]+$
```

Task/program inventory:

| Task id | Activation source | Vector | pc | error_pc |
|---:|---|---:|---:|---:|
| 348410728 | movement | -1 | 240 | 238 |
| 318626873 | movement | -1 | 240 | 238 |
| 742084068 | movement | -1 | 240 | 238 |
| 1974334222 | movement | -1 | 240 | 238 |
| 2095908208 | movement | -1 | 240 | 238 |
| 1010622025 | movement | -1 | 240 | 238 |
| 1306650200 | movement | -1 | 240 | 238 |
| 29350098 | movement | -1 | 240 | 238 |
| 1055267112 | movement | -1 | 240 | 238 |
| 312256312 | furniture `sit` root | -1 | 156 | 155 |
| 312256312 | `interact_with` current | -1 | 352 | 350 |
| 1507282594 | movement | -1 | 240 | 238 |
| 1961792276 | movement | -1 | 240 | 238 |
| 424636050 | movement | -1 | 240 | 238 |
| 2117750150 | movement | -1 | 240 | 238 |

The 13 extracted movement source ranges all hash to `9634890f9a963ae4dffd791c282e1b78d9a3bf76fe131a702b6d2eca1484eb9e`. The `sit` source hashes to `e1290c52e742abb6a26b57374aefafcf4b458dfcbdd707665878f03fdce88b12`; `interact_with` hashes to `717bb7a0e80a6a29a7458bab93ebc2c74c8c2a7adca7e55273ffa63fa4173c22`. Therefore the fixture has exactly three distinct source/vector/PC/error-PC mappings.

## Banteng source-location surface

`Ast.SourceSpan` retains half-open character offsets and a one-based start line/column. The parser attaches spans to named calls and verb calls, including the entire call expression.

`MooCompiler.compileExpression` recursively lowers each expression and then assigns the expression's *start line* to every emitted instruction which does not already carry a more-specific child line. `BytecodeProgram.Instruction` persists only that one source-line integer; it discards source offsets, columns, and AST identity. `BytecodeProgram.sourceLine(index)` exposes only that integer.

For a builtin call, Banteng emits `BUILD_LIST`, argument instructions, and `CALL name`; the continuation instruction pointer is the index immediately after `CALL`. For a verb call it analogously ends with `CALL_VERB`.

Consequences:

- The parser currently has sufficient spans to assign a stable call-expression identity during compilation (for example start/end offsets plus call kind/name).
- The compiled program currently does not retain that identity; its line-only metadata is insufficient when one source line contains more than one call.
- A general importer can avoid Toast at normal runtime only if the import path decodes Toast instructions and derives the corresponding source-call identity, while Banteng compilation emits the same source-call identity to instruction-boundary metadata.

## Proven mappings for the 14 suspended records

Pinned Toast emergency-mode disassembly, opened on the exact fixture and terminated with `abort` (no database save), proves:

| Source | Toast call | Toast error_pc -> pc | Banteng call index -> continuation |
|---|---|---:|---:|
| movement line 24 | builtin `suspend` | `238 -> 240` | `198 -> 199` |
| `sit` line 16 | verb `interact_with` | `155 -> 156` | `121 -> 122` |
| `interact_with` line 40 | builtin `suspend` | `350 -> 352` | `257 -> 258` |

The Toast offset points at the call opcode and the saved PC points immediately after its encoded bytes. The Banteng mapping follows the same semantic boundary: call instruction index, then the next instruction index. The requested three mappings are therefore exact, not line-only approximations.

## Two interrupted records: seven activation frames

The section starts at fixture line 3563. Record `500919854` (`waiting on thread 1`) has four root-to-current activations; record `249208170` (`interrupted reading task`) has three. Exact pinned Toast disassembly and Banteng compilation prove:

| Task | Frame source/vector | Call identity | Toast error_pc -> pc | Banteng call index -> continuation |
|---:|---|---|---:|---:|
| 500919854 | `checkpoint_started`, main | outer `checkpoint_channel:transmit(...)`, line 4 | `71 -> 72` | `54 -> 55` |
| 500919854 | `transmit`, main | `$mcmp:play(...)`, line 20 | `181 -> 182` | `126 -> 127` |
| 500919854 | `play`, main | outer `this:send(...)`, line 25 | `205 -> 206` | `148 -> 149` |
| 500919854 | GMCP `send`, main/current | builtin `occupants(who)`, line 8 | `82 -> 84` | `59 -> 60` |
| 249208170 | `start`, fork vector 0 | `this:do_start()`, line 3 | `11 -> 12` | fork `5 -> 6` |
| 249208170 | `do_start`, main | `this:parse_server_messages()`, line 16 | `112 -> 113` | `90 -> 91` |
| 249208170 | `parse_server_messages`, main/current | builtin `read(conn)`, line 6 | `43 -> 45` | `37 -> 38` |

The first and third rows demonstrate why line-only matching is unsound: line 4 of `checkpoint_started` contains an inner `ampm(...)` verb call and the outer `transmit(...)` call; line 25 of `play` contains inner `merge(...)` and outer `send(...)` calls. Only exact call-expression identity selects the saved boundary.

The `start` record also proves that root vector selection is material. Its VM header selects fork vector `0`; mapping its saved PC against the main vector would be invalid.

## Toast operand-stack control markers in interrupted records

Pinned `structures.h` assigns raw v17 type code `7` to `TYPE_CATCH` and `8` to `TYPE_FINALLY`; `db_io.cc` persists their numeric payloads. `execute.cc` shows that Toast pushes handler PCs, code selectors, and catch/finally markers directly on its operand stack.

The current `parse_server_messages` frame has three live slots immediately before `read(conn)` resumes: integer `0`, handler label `49`, and `TYPE_CATCH(1)`. The `start` fork frame similarly has integer `0`, handler label `27`, and `TYPE_CATCH(1)` for its active `try/except`. These are not MOO values and cannot be copied into Banteng's `List<MooValue>` operand stack. They must become Banteng `HandlerContinuation` state, with only genuine expression operands retained.

The interrupted `transmit` frame has two live slots representing its active `for` state (the listener list plus the iteration index). Banteng represents this as a structured `LoopContinuation`; it likewise cannot be copied blindly as operand values.

Therefore PC translation alone is necessary but insufficient for a production VM import. A correct importer must translate Toast's implicit stack-encoded handlers/loops/finally state into Banteng's explicit handler/loop/finally continuation fields at the translated instruction boundary.

## Exact generality boundary

Pinned `db_io.cc:410-415` proves `dbio_write_program` writes only fully parenthesized unparsed source followed by a dot line. It does **not** serialize the Toast opcode vector, a source map, an AST path, or compiler identity beyond the language-version number written by `write_activ`.

Pinned `parser.y:1212-1279` and `code_gen.cc:1389-1430` prove that Toast restores a `Program` by parsing that source and regenerating bytecode under the running binary. The numeric saved PC becomes meaningful only after that exact compile.

Accordingly:

- A general direct v17 importer cannot derive the call identity from source plus `pc/error_pc` using Banteng's parser alone. The PC is a byte offset in an absent, implementation-specific Toast vector.
- A fixture-only exact mapper can use `(SHA-256 of exact source bytes, language version, vector, pc, error_pc)` as a lookup key. For this fixture it needs ten entries (the three suspended mappings and seven interrupted mappings above). This is exact for the pinned fixture but is not complete v17 input and is not a production-grade general solution.
- A general exact direct importer must incorporate the exact Toast compile/layout logic (by linking/invoking the pinned compiler or faithfully porting its parser/code generator/opcode widths) and expose the call AST path covering the opcode at `error_pc`. That is embedding Toast compiler semantics even if no Toast interpreter is embedded.
- Toast can be kept out of the long-running Java server only by an external one-time normalization tool which uses the exact pinned Toast compiler to emit a Banteng-native checkpoint. That changes the startup/input contract; the current plan requires the Banteng target to consume the exact fixture directly, so it is not an authorized substitute.

## Concrete general mapping algorithm (if Toast compiler semantics are accepted)

1. Decode activation source, language version, selected vector, `pc`, and `error_pc`.
2. Compile with the exact Toast parser/code generator used by the selected compatibility profile.
3. Validate the same invariant as `check_pc_validity`: `pc` is exactly the end of a call instruction and `error_pc` identifies that call's blame byte.
4. Instrument the compiler/decompiler to return a stable AST call path for that opcode: vector path, statement path, expression child path, call kind, static name where present, and exact source span. Do not use line number or call ordinal alone.
5. Compile the same parsed source with Banteng while retaining the same stable AST call path on each `CALL`/`CALL_VERB` instruction.
6. Select the unique Banteng call with that path, set its instruction pointer to the following instruction, and map `error_pc` to the call instruction for error attribution.
7. Symbolically decode the Toast operand stack at that point. Convert catch/finally labels and markers plus for-loop list/index pairs into Banteng handler/finally/loop continuations; retain only genuine MOO operands.
8. Reject the activation if any call identity or control-state translation is non-unique or unsupported. Restarting at line start, at program start, or after an arbitrary same-line call is not exact.

The smallest exact production-grade implementation for direct loading is therefore a bounded Toast-v17 continuation importer containing the pinned compiler/layout and control-stack translator. There is no smaller general mapping based only on currently serialized data.

## Interrupted-source hashes for exact audit keys

- `checkpoint_started`: `54b8e3937c809b34ee685ee8a69f7f7ecb078381558c613f679cf144caa58684`
- `transmit`: `0b8533a518a34b0171dfbe5e4e27126a90debda8bcd69f853dbfc8820ad986c9`
- `play`: `84f0628090b92b5d304fc15977a64083c7a00302fc91b062579941f2f7dd401f`
- GMCP `send`: `3dbdc9512ec00c1a3ca558029b10f74f84987970d202b06eeaccf24ad97ce842`
- `start`: `6d768691bec9458e86ac1e8c36d79441edcc45178fd3074b1566ad3c49421250`
- `do_start`: `660212f0a7787e3f0fe02b5d5b9b708b6cdb53b97c911f85dccf531e3adb677e`
- `parse_server_messages`: `60ebc32a81b71c55ecce6ef97f24bbf9bf579a0002f72dfb738f58e983784334`

## RE completion review

- Inventory every distinct fixture mapping: documented under `Fixture activation inventory` and `Two interrupted records: seven activation frames`.
- Prove `240/238`, `156/155`, and `352/350`: documented under `Proven mappings for the 14 suspended records`.
- Identify Toast APIs and Banteng positions: documented under `Toast lookup surface`, `program_to_tree and find_line_number`, `disassemble`, and `Banteng source-location surface`.
- Decide the smallest production-grade mapping and generality: documented under `Exact generality boundary` and `Concrete general mapping algorithm`.
