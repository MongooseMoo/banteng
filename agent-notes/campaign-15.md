# Issue 15: runtime decomposition

## Goal
Extract FinalizationCollector, LoginFlow and ConnectionIo in the requested order, decompose command execution into named phases, and replace ambient attempt access with explicit context. Preserve behavior and publication ownership.

## State
Implementation authorized by the campaign go-ahead. Based on the prepared compiled-verb branch; its PR and the semantics dependency must merge before this change. Source investigation settled the context boundary; implementation has not started.

## Decisions and why
Smallest change: move the existing approximately 450-line collector, 415-line login flow and 400-line connection plumbing into the named owners, use the existing OpenRewrite subproject for static-member moves, then thread existing AttemptContext explicitly through runtime/scheduler methods and named dispatch phases. Roughly 3,500–5,500 changed lines, chiefly moves and argument plumbing, with one class per substantive commit as the issue requests. Retain one owner of pending reads and existing synchronization/publication.

Use existing BuiltinHosts on BuiltinCall as the invocation carrier. MooRuntime.builtinHosts currently binds ambient closures; bind them to the concrete AttemptContext instead and pass hosts from PublicationScheduler.executeAttempt through VM execution/builtin call construction. Preserve hosts through call_function reconstruction. Replace eagerly captured manifest callbacks with invocation-host access. Keep the single BuiltinCatalog and its FileIoService/SQLite/RNG/regex lifetimes. No new context facade, per-attempt catalog, compatibility wrapper or relaxed architecture rule.

Primary source: MooRuntime.builtinHosts and ATTEMPT/openAttempt/requireAttempt; PublicationScheduler.executeAttempt/executeSegment; BuiltinCall and BuiltinHosts; BuiltinCatalog constructors and callFunction; ArchitectureTest package boundaries prohibit runtime dependencies from VM/builtin. Existing runtime methods and explicit context can remain runtime-owned; ThreadLocal is confined to the scheduler attempt-entry boundary.

## Next step
Implement only the requested decomposition in this isolated worktree, keeping the existing task note current and preserving later dependency fixes. Parent owns publication, Claude review, PR CI and normal merging. Run only an actively authored focused test; do not run broad local CI or add new guards. The issue is not complete until all requested extractions and explicit-context threading work together.

## Checkpoint
Read issue 15 and the existing extraction recipe; the requested order is collector, login, connection I/O, dispatch phases, then explicit attempt plumbing.
The worktree has only the prepared task note untracked; implementation has not started.
The existing DecomposeWorldTxn recipe provides the static member move pattern.
No blocker is established; next action is to identify the collector's exact member closure before editing.

The MoveFinalizationTracing recipe and its focused recursive-closure/idempotence test are authored; that focused test passes.
The recipe dry run is running under Debian with Java 25 because native signals require POSIX APIs unavailable to the Windows compiler.
Its prerequisites completed; it is scanning root sources after the known subproject classpath-lock diagnostics.
Source correction: this branch's BuiltinCall has no hosts component yet, so explicit invocation-host plumbing must add it and update its callers.
The collector's instance closure and login timeout/publication boundaries have been read.

FinalizationCollector now owns the static tracing closure and the instance finalization/queueing methods. Its instance entry points receive AttemptContext explicitly; the existing SchedulerRuntime callback delegates to this owner. The runtime's remaining ambient access is intentionally pending the final context-threading commit. The focused recipe test passes, and the recipe ran against root sources. Generated import delimiters were corrected during the hand extraction; no root test suite was run.

The collector is committed, and the parent's canonical installed-verb compilation repair is integrated. LoginFlow now owns authentication, redirected association, connected hooks and the unauthenticated timeout monitor. Its attempt methods take AttemptContext; the monitor continues submitting timeout requests to the same scheduler and starts only from committed publication.

LoginFlow is committed. ConnectionIo now owns read registration/waiting/delivery, options, forced input, notifications, boot requests and recycled-player connections. Its pendingReads map has one owner and retains the runtime monitor used by publication; read registrations still become visible only inside publishAttempt after committed session publication. Attempt methods receive the existing AttemptContext.

Next: commit ConnectionIo, finish the remaining oversized command phases, then thread explicit context through the runtime, scheduler and builtin invocation carrier.
