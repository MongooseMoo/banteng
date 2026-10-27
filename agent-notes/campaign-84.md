# Issue84: compiler-owned locals

## Goal
Implement issue84's fixed leading variable slots, compiler name table, array frame storage and compile-time PUSH_CLEAR pass; preserve v17 named environment format.

## State
Implementation complete locally on the issue84 branch, based on task-state22. The frozen four-file focused run and the final suspended-fork export regression both passed under WSL Java25. No push or publication; coordinator owns PR CI, Claude review and later dependency integration.

## Decisions and why
Smallest change is one compiler-owned normalized name inventory shared by the enclosing program and all fork vectors, numeric local addressing and MooValue[] sized from that table. Reuse LayoutCompiler's existing fixed names and recursive symbol inventory rather than inventing runtime name assignment. About 450–650 production lines plus 100–200 targeted tests and mechanical caller migrations. Null denotes uninitialized. Populate built-in values at frame construction; remove runtime fallback comparisons and lookahead scan. Translate names only at snapshot, activation and call boundaries. Preserve v17 format.

Instruction operand already carries nested index/range depth and catch-load tick semantics; preserve these in explicit separate metadata while making local operand the slot as the issue specifies. Include handler, loop and scatter destinations. Share the complete enclosing table across fork vectors. DurableTaskCodec source-line adjustment must retain the table. Handbuilt test programs declare their real variable tables; do not add a runtime compatibility translator.

Toast's backwards basic-block pass, not the old runtime heuristic, defines clearing. Read its eligibility, branch/exception boundaries and fixed-variable call masks directly. No new guard or runtime forward scan.

PASS maps caller values into the callee's static slots. This deliberately follows the issue's table-sized array decision: caller-only names absent the callee table no longer appear in the callee environment. Parent verified current CompiledVerbTest112–130 asserts such a subject entry and TaskRegistry305–325/369–372 exposes snapshot locals through task_stack. Make the test's subject a statically referenced name when asserting inherited named values; retain a separate assertion of table-bounded storage. Do not claim this is invisible to introspection or anonymous reachability. No extra map or dynamic table expansion. Existing language PASS case reads subject in the callee and remains supported.

## Next step
Coordinator publishes the substantive implementation, obtains Claude review and PR CI, addresses verified findings, integrates dependencies and merges normally. No worker publication. Existing full conformance/restart suites were not run locally and remain PR CI work.

## Implementation checkpoint
Compiler now lowers local instructions to numeric slots after collecting the enclosing inventory from LayoutCompiler; forks receive that same table. Instruction localAux retains nested update depth and catch-read tick count. Loop/scatter destinations and catch bindings carry precomputed slots. Frame owns a table-sized array; snapshot and PASS boundaries produce named maps. Runtime lookahead and reference-graph inspection have been removed. Toast source read: sym_table.h fixed leading indices, eval_env.cc fill_in_rt_consts, code_gen.cc reverse basic-block pass and NON_VR_VAR_MASK, execute.cc PUSH_CLEAR dispatch. Focused SlotLocalsTest compilation is running under WSL Java25. Remaining: migrate handcrafted snapshot fixtures and direct array assertions, preserve cleared constants on restore, update fork ID binding, finish focused evidence and commit. No push or publication.

## Current implementation and evidence
All local instruction operands are numeric after compiler lowering. Catch/loop/scatter destinations are precomputed too. BytecodeProgram records the shared normalized inventory; its instruction metadata retains catch tick flags and nested-update parent depths. Slot conversion only applies to local opcodes, so full-width integer and float operands retain their values. Constant values are created once and copied into each frame; restored frames then replace every slot from the saved named environment so cleared constants remain uninitialized.

The reverse pass follows Toast's first-32-slot eligibility, overwrite/return marks, branch boundaries, protected-body restrictions and player/command-field preservation across verb calls. The compiler conservatively treats pass/eval/call_function as environment-read barriers for Banteng's current caller-environment semantics. No runtime instruction lookahead remains in PropertyOps.

Existing queued-fork persistence stores the standalone child body as source. On restart that body is recompiled and therefore reconstructs its statically referenced inventory, rather than expanding it with enclosing-only names from a saved map. Coordinator reviewed this boundary and instructed keeping issue84 bounded with no new persistence metadata or propagation mechanism. Referenced fork variables retain their named mapping; enclosing-only names remain an introspection limitation after standalone recompile.

Primary implementation pointers: BytecodeProgram variableNames and localAux/localSlots/catchSlot; LayoutCompiler.variableNames reuses its existing recursive inventory; MooCompiler.slotProgram/clearLastReads/insideHandler; VmState.Frame constantValues/namedLocals and restore; PropertyOps.loadLocal; DurableTaskCodec.shiftSourceLines and exportActivation. Toast authority: src/include/sym_table.h37-70, eval_env.cc76, code_gen.cc94-100 and1264-1329, execute.cc2940-2984; protected catch/finally generation is code_gen.cc1090-1145.

## Validation in progress
Only authored files are selected: SlotLocalsTest, CompiledVerbTest, VmSnapshotTest and MooCompilerTest. First compilation rejected null writes until array element nullability was explicit. Next run passed main compilation and snapshot cases but exposed two migrated assertion mistakes, now corrected. One run failed inside Error Prone StringConcatToTextBlock after Java source moved during compilation; the current run uses frozen Java source. Coordinator found and the worker corrected unconditional conversion of full-width numeric literals to int during local analysis; the authored regression covers both Long.MAX_VALUE and float bits. PR CI/conformance, Claude review, publication and merge remain coordinator work.

## Draft PR text
Title: Compile local-variable slots and last-use clearing

Local reads and writes now address a compiler-owned slot table shared by each program and its forks. Activations store values in arrays with fixed Toast built-in slots, and compilation emits PUSH_CLEAR at eligible final reads. This removes per-instruction name normalization, built-in fallback comparisons, runtime reference scanning and forward bytecode scans. Catch bindings, loops, scatter assignment and nested updates retain their existing control/tick metadata while using precomputed destinations. Named snapshot and v17 environments translate through the program table; PASS inherits names present in the callee's static table.

Validation: focused authored-file tests under WSL Java25 passed, including named snapshot restoration, table-bounded PASS, numeric operands and real suspended-fork export. Conformance tests are unchanged and reserved for PR CI.

## Focused results and final boundary adaptation
The frozen four-file run completed with BUILD SUCCESSFUL: WSL Debian Java25, `bash ./gradlew --no-daemon --max-workers=2 :test --tests world.mongoose.banteng.vm.SlotLocalsTest --tests world.mongoose.banteng.vm.CompiledVerbTest --tests world.mongoose.banteng.vm.VmSnapshotTest --tests world.mongoose.banteng.bytecode.MooCompilerTest --console=plain`.

After that result, the existing activation export boundary was adapted to compare expected handler catch slots against the live frame's table. A canonical standalone fork recompilation can have different numeric slot indices from its enclosing shared table; every other HandlerSpec field, operand depth and handler phase check remains intact. The new SlotLocalsTest regression executes a real fork vector to suspend inside try/except, demonstrates differing canonical/shared catch indices and exports its v17 suspension. The authored SlotLocalsTest file alone is now running for that final delta; Java source stays frozen through the run.

Final delta result: BUILD SUCCESSFUL, same WSL Java25 command selecting only `--tests world.mongoose.banteng.vm.SlotLocalsTest`. Main and test compilation ran on frozen source. No broader local CI/conformance command was run. No outstanding focused-test failure remains.
