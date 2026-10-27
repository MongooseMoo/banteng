# Issue 83: compiled verbs and one invocation path

## Goal
Implement issue83's stored compiled program, transaction-aware resolved lookup, centralized verb locals and one frame push. Preserve exact persisted source and all existing call/lifecycle policies. No new cache or compatibility wrapper.

## State
Created branch campaign/83-compiled-verb-entry from permissions86 abc436d. Integrated opcode85 2526a73 (merge3f22e0d) and parser88 4217b8e. Source migration complete; focused authored tests passed. WorldVerb compiled storage, current-transaction resolved lookup, centralized invocation, one FrameOrigin push including eval/root, root typed context and deleted runtime cache. Historical DurableTaskCodec source reconstruction and independent export validation unchanged. Parent owns publication; no push or publication by worker.

## Decisions and why
Smallest plan is450–650 added/moved production lines and650–900 deleted, plus mechanical constructor/test migration. Read issue83 and root campaign Next step. WorldVerb gains BytecodeProgram alongside exact source; installation passes existing successful compilation to avoid compiling twice, loaders translate invalid source to IOException. Metadata edits retain the same program. WorldTxn.resolveVerb returns current transaction's WorldVerb and defining MooValue location after existing VerbCache dependency replay; cache retains only location/index/read dependencies, not stale verb instances.

API: WorldTxn.ResolvedVerb(WorldVerb verb, MooValue location); resolveVerb(receiver,name,requireExecutable) for long and typed receivers. MooVm.callVerb takes state, receiver, resolution, args, name, closed VerbCallKind and the existing optional lifecycle target/move/root context. It builds locals then calls the sole VmState.pushFrame(program,locals,FrameOrigin). FrameOrigin names existing receiver/location/owner/verb/debug and lifecycle fields; eval uses the same push. RootVerbCall and CommandArguments replace root-local maps with explicit player/caller/command fields; execution budgets remain on each VmState. No shared mutable MooVm context. Preserve PASS copied locals, NORMAL command inheritance, recycle caller from locals.this and max-rec continuation, anonymous validation/deferral, initialize results and move phases.

Runtime stored entry uses verb.program and central root invocation, deleting compiledPrograms. PublicationScheduler historical embedded-source reconstruction and independent canonical export validation remain compilation boundaries. No conformance changes. Integrate91 later without rebuilding its removed helper.

## Surprises
Dependency merge conflicts are limited to direct dispatch/pass relocation, expanded pass/callers tests and the corrected deterministic executor-rejection fixture. Resolve by combining exact behavior and typed constructors, not selecting a whole branch.

First focused run exited at compileJava: `MooVm.java:634: warning: [UnusedVariable] The parameter 'frame' is never read`, then `error: warnings found and -Werror specified`. Removed the obsolete protected-call parameter; no check change. Read-only audit during that stable-source compile found DurableTaskCodec.exportQueuedTask:458 and LambdaMooV17Codec:642 consume initialLocals: ROOT push now copies constructed locals into that existing task-owned map, and the focused root test asserts snapshot preservation. Added an inherited cached-lookup read-conflict case proving the receiver read survives a warm cache hit. No test result yet; no source edits occurred while compilation ran.

Second focused run passed production compilation, then test compilation found three dependency-integration misses: CommandParserTest:73/130 `int cannot be converted to PropertyPerms` and MooVmTest:4725 old five-argument WorldVerb constructor. Migrated exactly those arguments to86 typed permissions/argument specs; these are combined85/86/88 fixture corrections. No tests executed in either failed run and source remained stable throughout compilation.

## Sources and validation
- Toast db_file.cc:996–1001 rejects an unparsable persisted program. RawVerb.compile is the shared load boundary for V4, V5 and permanent/anonymous V17 verbs; invalid source becomes IOException. WorldVerb's source constructor compiles once, while its canonical source/program constructor supports validated installation and metadata reuse.
- Toast execute.cc:663–747 obtains the defining verb/program and enters a single activation path. Actual local policies are retained from this branch's prior MooVm CALL_VERB/pass/protected/recycle/anonymous/initialize bodies, MoveOps.callHook, IndexOps.dispatchWaifIndexHandler and MooRuntime.startStored/verbLocals.
- WorldTxn.resolveVerb uses the existing cached coordinate/read-dependency result and resolves the actual verb against this transaction. WorldHistory's cache still stores no WorldVerb. Metadata edits retain verb.program; already validated set_verb_code and .program pass their CompilationResult program through installation. disassemble uses stored bytecode.
- FrameOrigin names return mode, programmer, receiver, defining location, called/full names, create override, recycle target, move object/destination/position, anonymous recycle target and debug. RootVerbCall/CommandArguments keep explicit root inputs. ROOT push retains existing initialLocals as well as frame locals for durable-task consumers.
- DurableTaskCodec:31/75 still compiles historical embedded task source; :833 independently recompiles frame source for export consistency validation. This file and its checks are unchanged. VmState restoration still restores historical frames directly; it is not stored-verb invocation.
- Focused command: WSL Debian/root Java25 `bash ./gradlew --no-daemon --max-workers=2 :test --tests world.mongoose.banteng.vm.CompiledVerbTest --tests world.mongoose.banteng.persistence.CompiledVerbLoadTest --tests world.mongoose.banteng.vm.VmSnapshotTest`. Final result `BUILD SUCCESSFUL in 2m 49s`; all source stable during each run. Only authored files selected; production and test compilation retained all analyzers/-Werror. No full local CI/conformance run. Conformance files unchanged.
- Final source inventory: one VmState.pushFrame; no stored-verb compiler in MooVm/IndexOps/MoveOps/MooRuntime; no runtime compiledPrograms or verbLocals; only .program compilation remains in MooRuntime. Remaining main-source compiler sites are source installation/eval and the three historical-task load/validation sites above.

## Draft PR
Title: Store compiled verbs and centralize verb entry

Stored verbs previously compiled during calls and built their activation locals in separate paths. Store immutable bytecode alongside exact persisted source, reuse it across metadata edits, and resolve the verb and defining location together against the current transaction. Route normal calls, pass, protected calls, lifecycle hooks, indexing and runtime roots through one entry path and one named frame push. Remove the unbounded runtime source cache.

Preserve root player/caller/command context and execution budgets, pass local copying, lifecycle continuations and transaction lookup read dependencies. Persisted-task reconstruction and independent export validation continue compiling their embedded historical source. Include typed fixture corrections needed to combine85/86/88.

Validation: focused CompiledVerbTest, CompiledVerbLoadTest and updated VmSnapshotTest passed with stable source. Conformance unchanged; full CI/conformance and exact-head review remain for the parent-owned PR. Closes #83.

## Next step
Commit local implementation and hand off to parent. Parent drains the existing PR stack, integrates dependencies, performs exact-head review and PR CI before publication/merge. No worker push/publication; no blocker remains in the focused implementation.
