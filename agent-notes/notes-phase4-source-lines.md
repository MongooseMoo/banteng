# Phase 4 source-line retention handoff

## Current state

- Read `docs/reports/banteng-implementation-plan.md` Phase 4 requirements around steps 5 and 7 before editing.
- Modified only the assigned source/test files:
  - `src/main/java/moo/bytecode/BytecodeProgram.java`
  - `src/main/java/moo/bytecode/MooCompiler.java`
  - `src/test/java/moo/bytecode/MooCompilerTest.java`
- `BytecodeProgram` now exposes `sourceLine(int instructionIndex)` and retains a one-based source line on every program instruction.
- `MooCompiler` assigns parser-provided statement/expression source lines, retains them across numeric jump patching, and locates implicit return instructions.
- `MooCompilerTest` has a multiline regression asserting the exact source line for every emitted instruction.
- `git diff --check` passed for the three assigned files.

## Blocker

The exact required WSL Java 25 command was started:

`JAVA_HOME=/opt/java/25 ./gradlew test --tests moo.bytecode.MooCompilerTest`

It emitted only Gradle task-graph calculation output and remained unchanged for more than four minutes, versus the parent-provided 36-second focused baseline. Per explicit parent instruction, only this invocation's wrapper process was terminated. It exited with code 1 and produced no test result. A concurrent `VmSnapshotTest` Gradle session was left untouched.

## Next action

After the other active Gradle session finishes, rerun the exact targeted `MooCompilerTest` command under WSL Java 25 and address only failures in the three assigned files.
