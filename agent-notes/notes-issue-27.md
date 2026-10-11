# Issue 27 handoff

## Findings

- Assigned worktree: `C:\Users\Q\code\banteng-agent-issue-27`
- Branch/base: `agent/issue-27-queued-task-fields` at `d50652d1ff4b643a759812e4cd37e4a5ca85b1f9`
- Issue #27 requires durable queued-task first source line, called verb, and full matching verb name through v17 read/write and restored fork activation without reconstruction from locals.
- The controlling Phase 4 plan requires queued-root v17 fields to be retained/restored; the selected authority is pinned WSL Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e` and `/root/src/toaststunt/build-release/moo`.
- Pinned Toast `write_forked_task()` writes `find_line_number(ft.program, ft.f_index, 0)`, `write_activ_as_pi()` writes distinct `a.verb` and `a.verbname`, and `read_task_queue()` assigns the retained line to `program->first_lineno` before enqueue/activation.

## Current state

- Added a focused test and minimal v17 fixture in the issue worktree only:
  - `src/test/java/moo/persistence/QueuedTaskV17CodecTest.java`
  - `src/test/resources/moo/persistence/queued-task-source-and-dispatch.db`
- Exact RED command: `.\gradlew.bat --no-daemon test --tests moo.persistence.QueuedTaskV17CodecTest --console=plain`
- Raw result: `6 tests completed, 1 failed`; the new `preservesQueuedTaskSourceAndDispatchFieldsFromToastFixture(Path)` fails with `java.io.IOException` at test line 122 during `codec.read(input)`.
- Implementation is partially applied in the issue worktree:
  - `QueuedTask` now has explicit first source line, called verb, and full verb name fields.
  - v17 codec read/write uses those fields and no longer requires called/full names or the `verb` local to agree.
  - `VmState`, `VmSnapshot`, activation frames, and fork requests are being extended with explicit called/full names.
  - `PublicationScheduler` rebases restored compiled source lines to the retained first line and carries names into the restored root activation.
  - `TaskRegistry` is being switched to activation metadata for queued rows and task stacks.
- Compilation has not yet been attempted after these partial production edits; constructor/call-site cleanup remains.
- Constructor/call-site cleanup is complete and `compileJava compileTestJava` succeeded on Windows.
- Focused codec GREEN: `.\gradlew.bat --no-daemon test --tests moo.persistence.QueuedTaskV17CodecTest --console=plain` ended `BUILD SUCCESSFUL in 10s`.
- The restored-activation test cannot initialize on Windows because `BuiltinCatalog` cannot load `libcrypt.so.1`; this is a host dependency failure before test execution.
- Plan-authorized WSL/Java 25 restored-activation GREEN: `wsl -d Debian --cd /mnt/c/Users/Q/code/banteng-agent-issue-27 env JAVA_HOME=/opt/java/25 ./gradlew --no-daemon test --tests moo.runtime.PublicationSchedulerTest.restoresQueuedTaskSourceAndDispatchMetadataIntoItsActivation --console=plain` ended `BUILD SUCCESSFUL in 5m 20s`.
- The minimal hand fixture loads under pinned Toast and reaches shutdown/dump. Its first line is normalized to 1 on Toast re-save because it was not generated from a live fork vector; called/full names remain distinct. A genuine live-fork fixture generation attempt successfully added/programmed `#0:audit_task_aliases`, but calling `#0:audit_t()` returned `E_VERBNF` because the stored name lacked a Toast wildcard abbreviation pattern.

## Blocker

- No implementation blocker. Genuine Toast fixture generation needs the verb names string changed to an abbreviation pattern such as `audit_task_aliases audit_t*`, then rerun. The collaborator thread limit prevented spawning an additional explorer.

## Next action

- Generate the live Toast fixture with a matching abbreviation pattern, replace the synthetic fixture if suitably minimal, remove the temporary helper script, rerun focused tests, then run proportionate gates and commit exact paths.

This note is a required uncommitted handoff artifact. Do not stage or commit it with issue #27.
