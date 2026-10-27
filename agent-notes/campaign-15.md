# Issue 15: runtime decomposition

## Goal
Extract FinalizationCollector, LoginFlow and ConnectionIo in the requested order, decompose command execution into named phases, and replace ambient attempt access with explicit context. Preserve behavior and publication ownership.

## State
The three owner extractions, command phases and explicit attempt/host context plumbing are implemented and committed. Current compiled-verb ancestry and the task-state owner branch are integrated. Parent inspection found that both constructors assigned the shadowing valueSemantics parameter instead of the new final field; both assignments now use this.valueSemantics. Publication, review and PR CI remain parent-owned.

## Decisions and why
Smallest change: move the existing approximately 450-line collector, 415-line login flow and 400-line connection plumbing into the named owners, use the existing OpenRewrite subproject for static-member moves, then thread existing AttemptContext explicitly through runtime/scheduler methods and named dispatch phases. Roughly 3,500–5,500 changed lines, chiefly moves and argument plumbing, with one class per substantive commit as the issue requests. Retain one owner of pending reads and existing synchronization/publication.

Use existing BuiltinHosts on BuiltinCall as the invocation carrier. MooRuntime.builtinHosts currently binds ambient closures; bind them to the concrete AttemptContext instead and pass hosts from PublicationScheduler.executeAttempt through VM execution/builtin call construction. Preserve hosts through call_function reconstruction. Replace eagerly captured manifest callbacks with invocation-host access. Keep the single BuiltinCatalog and its FileIoService/SQLite/RNG/regex lifetimes. No new context facade, per-attempt catalog, compatibility wrapper or relaxed architecture rule.

Primary source: MooRuntime.builtinHosts and ATTEMPT/openAttempt/requireAttempt; PublicationScheduler.executeAttempt/executeSegment; BuiltinCall and BuiltinHosts; BuiltinCatalog constructors and callFunction; ArchitectureTest package boundaries prohibit runtime dependencies from VM/builtin. Existing runtime methods and explicit context can remain runtime-owned; ThreadLocal is confined to the scheduler attempt-entry boundary.

## Next step
Commit the constructor field-initialization repair with this note, then return the worktree to the parent for publication, Claude review, answers to findings, PR CI and normal merging after prerequisites. No local CI is authorized for this repair. The authored recipe test result does not establish that the complete runtime change passes PR CI.

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

ConnectionIo is committed. Command execution now separates program-input compilation from programming/output control, and command selection shares the existing argument-specification predicate. The command methods are package-private and each command phase is below 100 lines; parsing and OOB continue using the existing parser and transport phase.

The dispatch phases are committed. Runtime attempt methods and all extracted owners now receive AttemptContext explicitly. The sole ThreadLocal is at PublicationScheduler.executeAttempt and is removed in finally; downstream ambient accessors are deleted. Scheduler execution passes per-attempt BuiltinHosts through both normal and authorized VM builtin paths into BuiltinCall, including call_function reconstruction. Manifest callbacks select call.hosts rather than eagerly capturing runtime callbacks. The single catalog and its file/SQLite/RNG/regex instances are retained.

Source showed MooServer.openNetworkConnection re-entering runtime.registerOutboundConnection on the attempt thread. The existing listener call now takes an OutboundConnectionRegistrar bound by invocation hosts to that same context; server registration and cleanup order are preserved. Existing listener and direct VM/builtin fixtures were updated for these explicit parameters.

Context plumbing is committed. Current compiled-verb ancestry is integrated. The task-state owner branch is now integrated: moved LoginFlow/ConnectionIo/runtime effect callers use EffectOutbox, VM metering uses TaskBudget, and conflicting constructor fixtures retain TaskOrigin/TaskBudget plus invocation hosts. Snapshot values and the single catalog remain unchanged by this integration.

No broad local CI has been run. The authored static-move recipe test passed; root-source recipe execution completed; source inspection found no remaining ambient attempt lookup or unresolved conflict markers. The integration is committed. Parent inspection subsequently found the constructor field-initialization defect described in State; the source repair is included with this note update. Parent owns publication, Claude review, answers to findings, PR CI and normal merge after the task-state prerequisite.
