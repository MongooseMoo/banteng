# Canonical bytecode source subtask

## Scope

- Modify only `BytecodeProgram.java`, `MooCompiler.java`, and focused compiler tests.
- Do not commit.

## Findings and implementation

- `MooCompiler` has exactly two `BytecodeProgram` emission sites: the root `Ast.Program` and an `Ast.Fork` body.
- `BytecodeProgram` now has immutable `source` state. Existing one-argument and two-argument public constructors remain available for manually built programs and use an empty source sentinel.
- Root compiler output uses `MooUnparser.unparse(program)` on the exact parsed `Ast.Program`.
- Every fork vector, including nested vectors, uses `MooUnparser.unparse(new Ast.Program(forkStatement.body()))` on the exact fork body.
- The focused regression recompiles both an outer fork vector and its nested fork vector from `source()` and requires identical disassembly. It also exercises both preserved manual constructors.

## Verification state

- The first focused WSL Java 25 Gradle invocation stalled during configuration and was terminated by its exact wrapper PID after exceeding the observed build baseline.
- The bounded offline rerun reached `compileJava` but did not reach tests. A concurrent unauthorized-for-this-subtask change in `PublicationScheduler.java` triggered an Error Prone `StringConcatToTextBlock` internal `NoSuchElementException` at line 66.
- No scheduler/persistence/runtime/app/server file was modified by this subtask.

## Blocker and next action

- The scheduler compile issue was fixed, and the next run compiled the authorized production files. It then stopped at a concurrent persistence-test Error Prone warning; that file's owner fixed the warning.
- Two later focused reruns were invalidated by shared-build interference: one received an external Gradle daemon stop during `compileTestJava`; the next lost `build/classes/java/main/moo/vm/VmSnapshot$FinallyState.class` while Gradle hashed outputs.
- Root confirmed the shared build-directory interference and directed this agent to stop all Gradle activity. Root now has sole ownership of build commands and will perform final verification.
- No focused test reached execution, so this subtask does not claim a passing test result. The authorized production files did compile before the subsequent unrelated test-compilation/interference failures.
- No commit was made.
