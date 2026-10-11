# Control-flow managed-server crash audit

- Scope: read-only diagnosis of the managed server connection death during `language/control_flow.yaml`.
- Current finding: no repository-level cause established yet; the only evidence reviewed so far is prior-memory indexing that identifies `fork_with_id_variable` as involving parser, VM fork requests, runtime task IDs, and queued-task ownership.
- State: source files, tests, and runtime artifacts have not been modified or executed.
- Blocker: none established yet.
- Next action: inspect the exact conformance runner, server/runtime exception boundary, and fork/parser paths to identify the first server-fatal event and its owner.

## Checkpoint 2

- `scripts/run_managed_wsl.sh` passes Banteng to `moo-conformance` through `scripts/run_banteng_wsl.sh`; the launcher itself does not reinterpret per-connection failures.
- `MooServer.handleConnection` calls `MooRuntime` directly for each complete input line.
- Its catch block handles only `IOException`. Any unchecked parser/runtime exception escapes the connection's virtual thread; the unconditional `finally` then calls `runtime.closeConnection`, removes output/binary/connection registrations, and the try-with-resources closes the socket.
- This establishes the connection-death mechanism but not yet the first throwing control-flow row.
- Next action: inspect `control_flow.yaml`, runtime dispatch, parser fork grammar, and VM fork processing to identify the first unchecked exception and its semantic owner.

## Checkpoint 3

- `control_flow.yaml` reaches its first fork row at `fork_with_id_variable`, whose source begins `fork task_id (0)`.
- `MooParser.parseFork` accepts only `fork (<delay>)`; the AST/compiler have no fork-ID field or assignment. Thus named fork syntax is unsupported and raises `MooParser.ParseException` (an `IllegalArgumentException`) when compiled directly.
- That parse failure is not automatically the socket-fatal cause: the interactive `.program` completion path wraps `compiler.compile(source)` in `catch (IllegalArgumentException)` and silently keeps the prior verb code.
- Stored verb execution later calls `compiler.compile(verb.programSource())` without a local catch, and scheduler/fork paths still need inspection for the first unchecked failure actually propagated to `executeLine`.
- Next action: inspect the conformance runner's exact statement execution protocol and the publication scheduler's failure propagation/fork task-ID behavior.

## Checkpoint 4

- The conformance fixture's `#2:eval` verb executes `notify(player, toliteral(eval(argstr)))` inside a MOO `try/except`.
- The Python runner sends each YAML `statement` through that ordinary socket evaluation protocol; it does not use `.program` for these rows.
- Therefore `fork_with_id_variable` reaches Banteng through the `eval()` builtin's dynamic-source path. The earlier `.program` catch does not protect it.
- `PublicationScheduler.executeAttempt` captures any thrown `Throwable`, `finishFailure` completes the ingress future exceptionally, and `submit(...).join()` explicitly unwraps and rethrows any `RuntimeException` to `MooRuntime.executeLine`'s caller.
- Combined with `MooServer.handleConnection` catching only `IOException`, an unchecked dynamic-eval parse/compile exception is socket-fatal even though the surrounding MOO code has `except e (ANY)`; Java exceptions bypass MOO error handling.
- Next action: locate the VM dynamic-eval compilation line and, if current artifacts permit, run only the exact `fork_with_id_variable` row to confirm its exception text and first-failure position.

## Checkpoint 5

- `MooVm.applyBuiltinResult` compiles `eval()` dynamic source inside `try/catch (IllegalArgumentException)` and converts syntax errors into the MOO-level eval result `{0, {"Parse error: ..."}}`. Therefore the missing fork-ID grammar is a row failure, not the server-fatal exception.
- The repository is already dirty in tracked parser/compiler/AST/unparser and VM-test files. Those edits predate this audit and have not been touched.
- `build/install/banteng/lib` exists, but without provenance it may be stale relative to the dirty source. Running it would not reliably identify the current source owner.
- Next action: inspect the tracked diff and existing focused-run evidence for the first valid fork row that throws outside the parser's caught path; run one exact row only if binary/source provenance can be established without a build.

## Checkpoint 6

- Current tracked edits add named/unnamed `while` loop support only; fork parsing and scheduler behavior are unchanged.
- `PublicationScheduler.finishSuccess` does not complete a root ingress future until `pendingChildren == 0`. A fork therefore blocks the foreground socket response until all descendant fork tasks return.
- The first valid delayed-child row is `fork_float_delay`: the child is released after 0.5 seconds and immediately calls `suspend(5)`, so the foreground `executeLine` waits about 5.5 seconds.
- `SocketTransport` sets a 3-second socket timeout and its eval `_receive` does not catch `socket.timeout`. Thus this row deterministically times out before the scheduler publishes the parent result, making the managed connection appear dead/unresponsive even if the JVM and socket remain alive.
- This is scheduler ownership, not parser ownership. The parser's missing fork-ID syntax causes an earlier ordinary conformance failure but is converted into an eval parse result.
- Next action: inspect recent managed temp logs/process evidence for an actual unchecked server exception. If absent, report the first connection-level failure as foreground completion incorrectly waiting for the fork child, with exact scheduler and harness owners.

## Checkpoint 7

- One focused `fork_float_delay` invocation was attempted with the toastcore profile, but it never reached the row: Banteng exited at startup because `LambdaMooV17Codec` rejects the fixture's `4 queued tasks` section. This result is not evidence about the control-flow connection failure.
- The controlling plan explicitly runs this family as `banteng stock`, using the version-4 conformance `Test.db` fixture. The profile mismatch is exact and the same row must be rerun under stock to test the active path.
- No tracked files were changed. The managed harness cleaned up its failed temp server.
- Next action: rerun only `control_flow::fork_float_delay` with the exact stock target/oracle manifests and stock fixture.

## Final audit checkpoint

- Correct focused run: stock-profile `control_flow::fork_float_delay` passed (`1 passed, 25 deselected`) in 6.71 seconds on the current installed JAR. It did not disconnect or throw.
- The scheduler does wait for fork children before completing foreground ingress, but that behavior did not exceed the effective harness timeout in this exact run and is not the reported fatal cause.
- Static disposition of remaining unsupported surfaces:
  - for-range syntax produces `MooParser.ParseException`, caught by `MooVm.applyBuiltinResult` and returned as eval parse data;
  - named fork syntax does the same;
  - missing `queued_tasks()` produces the normal MOO `E_VERBNF` result through builtin dispatch.
  None of those paths should escape as a Java exception or close the socket.
- The proven connection-fatal boundary remains `PublicationScheduler.submit` rethrowing a worker `RuntimeException` into `MooServer.handleConnection`, whose catch handles only `IOException`; its `finally` closes/removes the connection. However, no concrete control-flow row in the inspected current source was shown to throw through that boundary.
- Exact conclusion: the first server-fatal cause cannot be named from the retained family evidence because the prior managed output was truncated and no row/Java stack was preserved. Claiming parser or fork-delay ownership would be false. The exact missing artifact is the first failing row plus `server.log` stack from the full managed run.
- Repository state: no tracked files edited, staged, committed, or pushed by this audit. Only this mandatory untracked checkpoint note was written.
