# Toast v17 layout implementation

## Scope

- Implement `ToastV17ProgramLayout` for pinned Toast v17 vector positions and serialized `error_pc..pc` resolution.
- Retain matching AST call paths on Banteng `CALL` and `CALL_VERB` instructions.
- Add focused tests for the ten exact Mongoose mappings recorded in `notes-toast-pc-mapping.md`, including fork vector 0 and nested calls.
- Do not modify the codec, scheduler, runtime, server, plan, or existing notes.

## Current state

- Branch is `master`; another active slice already modifies the plan, codec, scheduler/runtime/server, and related persistence tests. This slice will not touch those paths.
- `notes-toast-pc-mapping.md` records ten distinct exact mappings: three suspended activation call boundaries and seven interrupted activation-frame boundaries.
- The mapping includes nested same-line calls, so line numbers are not sufficient, and one mapping selects fork vector 0.
- The controlling Phase 4 item requires `ToastV17ProgramLayout`, stable AST paths shared with Banteng call instructions, and source-independent unique matching; fixture-specific hash tables are forbidden.
- `BytecodeProgram.Instruction` now optionally retains an immutable structural `AstPath`; `MooCompiler` indexes the complete parser tree and assigns that path to every emitted `CALL` and `CALL_VERB`, including calls in fork vectors.
- `ToastV17ProgramLayout` reproduces the pinned v17 symbolic vector construction and final operand-width expansion for literals, forks, variables, labels, and stack references. It preserves Toast's child-fork compilation order and distinguishes the two-byte builtin call boundary from the one-byte verb-call boundary.
- `ToastV17ProgramLayoutTest` reads the task section directly from the unchanged `fixtures/mongoose.db`, stops before unrelated world records, and proves all ten audited mappings, exact fork selection, matching compiler AST path, call opcode, and Banteng call index.
- Exact WSL focused gate passed: `JAVA_HOME=/opt/java/25 ./gradlew test --tests moo.persistence.ToastV17ProgramLayoutTest --tests moo.bytecode.MooCompilerTest --console=plain`.
- Owned `git diff --check` passed for `BytecodeProgram.java`, `MooCompiler.java`, `ToastV17ProgramLayout.java`, and `ToastV17ProgramLayoutTest.java`.
- The active follow-up is the exact reverse handler-label API required by plan item 4: given compiled source/vector and structural control paths, expose the physical Toast v17 handler-label program counters and selector structure needed to serialize Banteng `HandlerState` back into Toast runtime stack records.
- Toast catch labels cannot be recovered from the existing anonymous symbolic `LABEL` units alone. The layout builder must retain label identity and target-unit indices, then convert those targets through the already-finalized v17 operand-width layout.
- `Ast.Catch` is one owner plus one selector clause; `Ast.Try` is one owner plus its source-ordered selector clauses. Nested active handler groups must be returned outer-to-inner while Banteng continuation metadata remains clauses-then-owner, inner-to-outer.
- Toast `finally` suspension uses the distinct `TYPE_FINALLY` / `EOP_TRY_FINALLY` label shape. Its exact label is derivable from the same symbolic layout, so it requires a distinct API rather than being represented as a catch clause.
- Public result records for selectors, clauses, handler groups, finally labels, and ordered control labels have been added. The label-identity plumbing, resolution methods, and tests are not yet implemented.
- The layout builder now retains label-operand identity, rejects duplicate label definitions, records each target unit, and converts the target through the finalized Toast literal/fork/variable/label/stack widths.
- `resolveToastControls(source, vector, continuationPath)` now returns active catch groups and finally labels outer-to-inner. Exact-owner methods reject absent or ambiguous handler/finally paths.
- `Ast.Try` catch clauses are recorded in source order at their existing structural paths; `Ast.Catch` records its synthetic clause path at `owner.child(2, 0)` to match `MooCompiler`; `Ast.Try` finally labels are recorded separately.
- Exact Debian Java 25 compilation passed after this wiring: `JAVA_HOME=/opt/java/25 ./gradlew compileJava --console=plain`.
- `ToastV17ProgramLayoutTest` now proves exact Mongoose handler targets 27 and 49 from the unchanged fixture, selector and owner/clause structure, nested outer-to-inner groups, hierarchical Toast-flat fork selection, the 256-byte label-width transition (targets 254 and 258), and a distinct exact finally target.
- The exact combined Debian Java 25 gate passed: `./gradlew test --tests moo.persistence.ToastV17ProgramLayoutTest --tests moo.bytecode.MooCompilerTest --tests moo.vm.MooVmTest.catchExpressionStillBindsBareErrorValue --console=plain` (`BUILD SUCCESSFUL in 9s`).
- Owned tracked and untracked Java files pass `git diff --check` / `git diff --no-index --check`; no commit was made.

## Blocker

- No unsupported syntax blocked the ten exact activation mappings.
- The separate whole-fixture codec test proceeds past all task bytes and then fails on unrelated world loading: `object #8 parents requires unsupported multiple inheritance`. This slice did not broaden into that world-model issue.
- No blocker exists for the reverse handler-label work.

## Next action

- Hand the completed uncommitted reverse-control API to the parent/importer agent for `PublicationScheduler.exportActivation` integration.

## Reverse-control API

- `resolveToastControls(String source, int vector, AstPath continuationPath)` returns active `ToastHandlerGroup` and `ToastFinallyLabel` records outer-to-inner.
- Each handler group contains source-ordered `ToastHandlerClause` records with its exact structural path, `ToastErrorSelector`, and finalized physical Toast handler-label program counter.
- `resolveToastHandlerGroup(...)` and `resolveToastFinallyLabel(...)` uniquely resolve an exact owner path and reject absent or ambiguous matches.
- `vector == -1` selects the main vector; nonnegative vectors use Toast's flat fork-vector numbering while retaining the full hierarchical AST path shared with Banteng.

## Full structural stack-shape follow-up

### Findings

- The pinned source is present at `/root/src/toaststunt` and is exactly commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`, matching the plan authority.
- The existing `VectorBuilder.currentStack` is the right physical-depth authority, but the value at `vector.call(...)` still includes consumed call operands. The required post-argument/pre-result depth is therefore current depth minus one for builtin calls and minus three for verb calls.
- Toast collection loops retain two raw slots through the body: collection at the loop base and iterator at base plus one. Range loops retain start/next and end in two raw slots. Nested loops require all active loop regions, not the existing single innermost `EnclosingIterate` projection.
- Protected catch groups occupy selector/label pairs followed by a marker. Statement-except bodies and expression-catch fallbacks execute after those raw slots are removed, but still require structural phase/control metadata so the importer can synthesize Banteng handler state.
- Protected finally uses one raw `TYPE_FINALLY` marker. Its handler body runs after that marker is removed and with two continuation values on the raw stack; exact reason/value ordering is being verified directly from the pinned `execute.cc` opcode cases.
- `MooCompiler` already assigns stable paths to every `Ast.For`, `Ast.Try`, try clause, and `Ast.Catch`. It emits one owner plus source clauses for statement try, and one owner plus synthetic `owner.child(2, 0)` clause for expression catch.

### State

- The earlier reverse handler-label API and all green tests remain unchanged.
- Public typed shape records and `resolveStructuralStack(source, vector, program, boundary)` are implemented. Each call records its post-argument/pre-result depth and an outer-to-inner/base-to-top snapshot of active catch, protected-finally, finally-continuation, collection-loop, and range-loop structures.
- Exact Banteng handler and iterate instructions are resolved by stable structural path in the call-owning hierarchical program.
- Catch snapshots distinguish `PROTECTED`, `EXCEPT_CLAUSE`, and `EXPRESSION_FALLBACK`; finally snapshots distinguish protected marker state from `FINALLY_HANDLER` continuation state.
- Collection loops expose base/iterator depths, variable names, and an optional statically known LIST/STRING/MAP literal kind. Range loops expose next/end depths, variables, and an optional statically known INTEGER/OBJECT literal kind. Dynamic expression types remain runtime codec/importer data.
- Debian Java 25 `compileJava` passes after the structural source wiring (`BUILD SUCCESSFUL in 24s`). No new structural tests have been added yet.
- The full structural test matrix is now implemented. The first class run passed 15 of 17 tests; one parse failure was test-only (`any` is the reserved ANY token when used as a catch variable) and was corrected to `allErrors`.
- Sixteen of seventeen tests then passed. The sole remaining mismatch was exact Mongoose activation 2: persisted post-argument depth 3 versus computed depth 4.
- The mismatch exposed a real path-depth bug in the pinned layout emulator: `Ast.If` emitted the condition branch opcode/label but did not decrement `currentStack` for Toast's consumed condition before entering the body (and had the same defect for elseif arms). Pinned code generation calls `pop_stack(1)` at that point. The missing decrements are now added; the gate has not yet been rerun after this correction.
- The corrected exact Mongoose structural test passes, and computed post-argument depths equal the unchanged fixture activation stack sizes for both handler-label activations plus the collection-loop activation.
- All 17 `ToastV17ProgramLayoutTest` cases pass. The combined Debian Java 25 gate also passes: `ToastV17ProgramLayoutTest`, `MooCompilerTest`, and `MooVmTest.catchExpressionStillBindsBareErrorValue` (`BUILD SUCCESSFUL in 10s`).
- Owned tracked and untracked Java diff checks pass. The notes and Java files remain uncommitted.

### Blocker

- No blocker.

### Pinned runtime order

- `unwind_stack` replaces a popped `TYPE_FINALLY` marker by reason first and continuation value second; `EOP_CONTINUE` pops value then reason. Base-to-top depths are therefore reason at base and value at base plus one.
- `EOP_FOR_LIST_1/2` names `BASE` as the next-to-top slot and `ITER` as top. Base-to-top order is collection then iterator for LIST, STRING, and MAP.
- `OP_FOR_RANGE` names `from` as next-to-top and `to` as top. After assigning the current value it advances `from`, so a suspended body retains the next value at base and inclusive end at base plus one, for both INT and OBJ ranges.
- `unwind_stack` scans catch pairs from the group's base to marker, proving source-order selector/label pairs beneath `TYPE_CATCH`.

### Next action

- Hand the stable ordered structural API and its one runtime-only type limitation to the importer and parent for integration.

### Stable structural API

- `resolveStructuralStack(String source, int vector, BytecodeProgram program, CallBoundary boundary)` returns `StructuralStackShape(postArgumentDepth, entriesBaseToTop)`.
- Typed entries are `CatchGroup`, `ProtectedFinally`, `FinallyContinuation`, `CollectionLoop`, and `RangeLoop`; every entry carries exact absolute depths and its stable owner path, and handler/loop entries carry exact Banteng controls.
- `CatchGroup` carries source-ordered selector/physical-label clauses, base depth, optional raw marker depth, owner/clause controls, phase, and optional active clause index.
- Literal collection/range expressions expose a statically known runtime kind. For a general expression, Toast's opcode is shared and the actual LIST/STRING/MAP or INTEGER/OBJECT kind is not statically derivable; the API returns an empty optional and the importer must inspect the existing runtime base value.

## FIN_EXIT reverse mapping follow-up

### Findings

- Pinned `Finally_Reason` assigns persisted reason 5 to `FIN_EXIT`. Its continuation value is exactly `[target runtime-stack depth, target raw program counter]`.
- Pinned `code_gen.cc` emits a continue target as the selected loop's `top_stack` plus `top_label`, and a break target as `bottom_stack` plus the linked loop-end label. A named break/continue searches active loops inner-to-outer and matches either collection value or index variable.
- Therefore `ownerControl.specification().endTarget()` is not a general reason-5 target. The raw pair is statically invertible to a loop action and Banteng jump target, but it does not retain which of multiple source break statements selected the same continuation. Source-origin identity is absent and unnecessary for resumption.
- Toast raw target depth includes live structural loop/handler slots. Banteng removes those slots into handler/loop state, so the mapping must carry a separately translated Banteng operand depth; copying raw depth directly is wrong for nested loops.
- Pinned reason 1 (`FIN_RAISE`) carries the four-element catchable exception tuple and resumes ordinary handler search. Reason 2 (`FIN_UNCAUGHT`) stores the five-element tuple in global uncaught-handler state, replaces the finally continuation value with zero, and unwinds without searching `TYPE_CATCH` markers.

### State

- `BytecodeProgram` and `MooCompiler` now retain stable AST paths on source break/continue `JUMP` instructions.
- The layout now retains exact loop top/end target labels and depths, resolves labeled loops, and records every legal break/continue capable of crossing a protected finally.
- `FinallyContinuation` now carries bidirectional `ToastExitTarget` mappings: raw depth/PC, translated Banteng operand depth, exact Banteng target IP, target loop path, and BREAK/CONTINUE action. Lookup methods reject absent or ambiguous mappings.
- Tests have been added for break/continue inversion, labeled outer/inner loops, nested-finally propagation, return separation, hierarchical forks, and widened Toast labels.

### Current gate

- The exact Debian Java 25 `ToastV17ProgramLayoutTest` gate passes (`BUILD SUCCESSFUL in 11s`).
- The combined Debian Java 25 gate passes: `ToastV17ProgramLayoutTest`, `MooCompilerTest`, and `MooVmTest.catchExpressionStillBindsBareErrorValue` (`BUILD SUCCESSFUL in 10s`).
- Owned tracked `git diff --check` returned 0. Both new-file `git diff --no-index --check` commands produced no diagnostic output and returned the expected difference status 1.
- The concurrent `PublicationScheduler` and `VmSnapshot` compile blockers were repaired by their owning agents before these gates ran; there is no remaining blocker for this slice.

### Next action

- Hand the finalized, verified bidirectional FIN_EXIT mapping API to the importer and parent for integration and commit. Keep this note uncommitted.

## RE completion review

- Required exact layout: implemented in `ToastV17ProgramLayout` from pinned `code_gen.cc` symbolic emission and widening rules.
- Stable cross-compiler identity: implemented as `BytecodeProgram.AstPath` and retained on compiler call instructions.
- Ten Mongoose proofs: implemented in `ToastV17ProgramLayoutTest` against the unchanged fixture task bytes, including vector 0 and nested same-line calls.
