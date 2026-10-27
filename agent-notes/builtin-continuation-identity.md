# Builtin continuation identity

## Goal
Repair missing builtin rows in callers, task_stack and tracebacks while retaining actual create/recreate and reflected call_function identity through snapshots and supported durable callbacks.

## State
Scoped result/frame/snapshot/durable plumbing and display projections are complete. The explicit authored :test invocation passed with completed source compiled under -Werror. Isolated branch starts from the callback command-local and WAIF naming repairs. No publication is authorized for this worker.

## Decisions and why
Reuse the existing SuspendedActivation.BuiltinContinuation value in continuation-producing results and existing VM/snapshot frames; retain original payload on durable import/export. Existing lifecycle fields and ReturnMode still execute continuations. Add display rows without executable frames, protocol or format changes. Anonymous callbacks retain their current in-memory markers; do not expand anonymous durable limits. Expected size is roughly 150–230 production lines plus focused cases. Preserve all command-local and WAIF naming expressions and startup-worker ownership.

## Surprises
The first test invocation used unqualified test instead of :test, causing Gradle to select subproject tests as well; output showed :errorprone-checks:test FAILED. Stopped that invocation and corrected the selector to :test --tests world.mongoose.banteng.vm.BuiltinContinuationIdentityTest. No suite or safeguard was changed.

The frame retains optional existing typed metadata when the existing result application pushes a callback. Lifecycle fields and command-local/WAIF construction remain unchanged. TaskRegistry.projectStackFrames exposes the existing formatting owner directly for the separately assigned lag worker, including synthetic entries, source lines, variables and anonymization.

The authored fixture initially used ListValue accessors incorrectly; its owner exposes elements() and a one-based Optional get, not a direct getFirst. Corrected those test accesses. The first real cases then passed. Added eval/traceback coverage; that fixture actor needed both wizard and programmer flags because eval requires PROGRAMMER_ONLY. Source review also found that a failed callback push can unwind its caller; metadata transfer now requires that original caller still be active, with an authored max-depth failure case.

Parent separately identified immutable activation-player display/propagation and timeout-stack line reporting work. Those remain outside this commit; this repair does not claim complete callback conformance while those fixes remain outstanding.

## Next step
Parent integrates this local commit, reviews and runs PR CI. Lag worker reuses package-visible TaskRegistry.projectStackFrames after that dependency is integrated. Next independent assigned work is read-only planning for immutable activation player and timeout projection; no such code is included here.

Validation: explicit WSL JAVA_HOME=/opt/java/25 and PATH=/opt/java/25/bin:/usr/bin:/bin; ./gradlew :test --tests world.mongoose.banteng.vm.BuiltinContinuationIdentityTest. Real cases cover direct/reflected create/recreate, anonymous create, suspend/snapshot restore, supported permanent callback dump/read/import/resume, recycle-induced exitfunc, move phase counters, eval, caught error tracebacks and a failed callback push caught above its original caller. git diff --check passed. No broad suite acceptance claim; parent owns required PR checks.
