# V17 VM mapping investigation

## Current state

- Read-only subtask: map pinned Toast v17 `write_vm`/`read_vm` fields to Banteng `VmSnapshot`, `BytecodeProgram`, and `VmState`.
- No production or test files have been changed for this subtask.
- The immediate reported blocker is Banteng rejecting the first suspended task in `fixtures/mongoose.db` with `invalid suspended-task count: 14 suspended tasks`.

## Findings and observations

- Investigation has not started yet because the mandatory notes checkpoint had to be satisfied first.

## Next action

- Read the reverse-engineering skill, inspect the exact pinned Toast and Banteng source, then report lossless mappings, format mismatches, minimum correct design changes, and concrete tests without discarding suspended tasks.

## Toast `write_vm` / `read_vm` (`src/eval_vm.cc:86-137`)

- Verified pinned Toast checkout HEAD: `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- A persisted VM starts with task-local `Var local`, then one header line: `top_activ_stack root_activ_vector func_id max_stack_size`.
- The activation count is implicit and inclusive: exactly `top_activ_stack + 1` activations follow.
- Each activation is written by `write_activ`; `read_activ` receives the persisted `root_activ_vector` only for activation zero and `MAIN_VECTOR` for every later activation.
- `task_id` is not inside the VM block. The enclosing suspended/queued-task record passes it to `read_vm(task_id)`.
- `read_vm` supplies an empty map only for pre-`DBV_TaskLocal` databases and supplies `DEFAULT_MAX_STACK_DEPTH` only for old VM headers that end before the fourth field. V17 writes both fields.
- Next exact format dependency: inspect and document `write_activ` / `read_activ` before mapping Banteng fields.

## Toast `write_activ` / `read_activ` (`src/execute.cc:3973-4104`)

- Each activation persists, in order: language version; the full compiled `Program`; a named runtime environment; the count and values of active runtime-stack slots; activation identity/permissions metadata; `temp`; and `pc bi_func_pc error_pc`.
- If `bi_func_pc != 0`, the block also contains the suspended builtin's registered name and that builtin's function-specific continuation payload.
- Runtime locals are name-keyed on disk and reordered case-insensitively into the just-loaded program's `var_names`, so the persisted representation is not simply a positional local array.
- Runtime operand stack capacity is recomputed from the selected loaded bytecode vector's `max_stack`; only live stack values are persisted.
- `pc` must point immediately after a verb-call or builtin-call opcode in the selected vector. Activation zero uses the VM header's root vector; nested activations always use the program main vector.
- `error_pc` is distinct from `pc`. `bi_func_pc` is both a marker for a suspended builtin continuation and part of reconstruction of its function-specific state.
- Next exact dependency: the activation metadata format is separately owned by `write_activ_as_pi` / `read_activ_as_pi`.

## Toast `write_activ_as_pi` / `read_activ_as_pi` (`src/execute.cc:3816-3894`)

- The v17 activation metadata starts with a legacy dummy integer, then persists `_this` as a general `Var`, `vloc` as a general `Var` (therefore anonymous verb locations are representable), and numeric `threaded` mode.
- The following numeric line retains legacy placeholders but materially persists `recv`, `player`, `progr`, a legacy numeric projection of `vloc`, and `debug`.
- Four legacy parser strings are written and discarded on read. The material final strings are `verb` and `verbname`.
- Therefore a lossless Banteng activation needs distinct receiver (`this`), verb location, task player, programmer, threaded mode, debug mode, resolved verb name, and called/full verb name in addition to program/locals/stack/PC state.
- Next exact format dependency: inspect the enclosing queued/suspended-task records that supply task id, wake condition, and the VM block.

## Toast task-queue envelope (`src/tasks.cc:1904-2150`)

- V17 writes three distinct groups after legacy `0 clocks`: forked `queued tasks`, VM-bearing `suspended tasks`, and VM-bearing `interrupted tasks`.
- A suspended-task record is: rounded-down absolute start epoch seconds, task id, a general resume `Var value`, then `write_vm`. The resume value is semantically independent of the VM task-local map.
- A queued fork is not a `vm`: it writes first source line, rounded absolute start epoch seconds, task id, activation metadata, named runtime environment, and one fork-vector program. Reconstructing it requires a root/fork activation and its selected fork vector.
- An interrupted record contains task id and status text, then the same VM block. On load Toast reconstructs it as an immediately runnable suspended task whose resume value is `E_INTRPT` and start time is zero.
- The task queue stores absolute epoch seconds, not remaining-delay seconds. Restart enqueues those absolute wake times; an overdue task is therefore immediately due.
- The immediate `14 suspended tasks` failure is not corrupt input: Toast's reader explicitly accepts any nonnegative parsed count and reads that many full VM records. Banteng's rejection is a deliberately unsupported valid v17 surface.
- Next: inspect Banteng's v17 task codec and durable scheduler model before examining the first fixture task bytes.

## Banteng `LambdaMooV17Codec` task surface

- `read()` handles only queued forks through `readQueuedTask`; it then requires the literal lines `0 suspended tasks` and `0 interrupted tasks` (`LambdaMooV17Codec.java:162-170`). That literal check is the direct source of the Mongoose fixture rejection.
- `QueuedTask` (`:63-84`) models only delayed fork roots: task id, absolute scheduled epoch second, source text, named initial locals, programmer, object-only verb location, task player, and thread mode.
- `writeQueuedTask` / `readQueuedTask` (`:314-449`) are intentionally much narrower than Toast's general activation/VM format. They round-trip fork activation metadata and a source-form fork program, but require first line 1, object-only verb location, identical `verb`/`verbname`, compatibility debug value 1, and a source program terminated by dot line.
- `QueuedTask` has no resume value, activation stack, program-counter state, operand stack, temp value, root vector/function id/max stack depth, error PC, suspended builtin continuation, or interrupted-task status. It cannot represent a Toast suspended task by construction.
- Minimum ownership conclusion: general suspended/interrupted tasks must not be coerced into the current `QueuedTask` record. The checkpoint task model needs a distinct durable VM-bearing task variant or a common sealed task sum with queued-fork and suspended-VM cases.
- Next: enumerate Banteng `BytecodeProgram`, `VmSnapshot`, and `VmState` fields to determine which Toast activation/VM fields already have lossless owners.

## Banteng `BytecodeProgram` (`src/main/java/moo/bytecode/BytecodeProgram.java`)

- The executable representation is Banteng-native instruction IR plus immutable child fork programs and canonical source text. It is not Toast's persisted opcode vector.
- Source text is the only practical cross-engine bridge for a Toast-persisted `Program`: parse and compile the decompiled source into Banteng IR, then translate Toast bytecode PCs into Banteng instruction indices.
- That PC translation cannot be inferred from source line alone because multiple instructions share a line and Toast's `pc`, `error_pc`, and `bi_func_pc` are byte offsets inside a selected bytecode vector. A correct importer needs an explicit Toast-program decoder/decompiler plus stable old-PC-to-Banteng-PC mapping, or an equivalent source/continuation mapping proven for every suspension point.
- `BytecodeProgram` currently lacks persisted program language version, variable-name table, Toast bytecode vectors, and any old-PC mapping. Those are required import metadata, not properties of ordinary newly compiled Banteng programs.
- Nested Toast activations contain independent full programs. Banteng already permits each VM frame to own its own `BytecodeProgram`, so the frame model can hold the compiled result once imported.
- Next: inspect all `VmSnapshot` fields and invariants.

## Banteng `VmSnapshot` (`src/main/java/moo/vm/VmSnapshot.java`)

- Existing lossless owners for Toast VM-level state: `taskLocal`; ordered frame list; maximum stack depth; and task CPU/tick budgets. The latter budgets are Banteng runtime state, not present in Toast's VM block, so an importer needs defined initial budget values.
- Existing lossless owners for common activation state: each frame's independently compiled program, operand stack, case-normalized named locals, receiver as any `MooValue`, object-only verb location, programmer, per-frame thread mode, and instruction pointer.
- Banteng additionally persists its own structured handler/finally/index/loop continuations and return mode. These are derived from Banteng compilation/execution and cannot be copied from Toast bytes without reconstructing the equivalent Banteng control state at the translated suspension point.
- Missing Toast activation fields: distinct task player on each activation; debug bit; `verb`; `verbname`; general-value verb location (Banteng requires `ObjectValue`); activation `temp`; `error_pc`; Toast suspended-builtin id/name and function-specific continuation payload; VM root vector and `func_id`.
- `PendingBuiltin` is not the same thing as Toast `bi_func_pc` continuation. Banteng's value-only record represents an effect call waiting for publication authorization before execution; Toast state represents a builtin that has already yielded and must re-enter its builtin implementation with builtin-specific saved data.
- Snapshot wake state can represent one timed suspension (`suspensionDelaySeconds`) or host-result wait, but the checkpoint envelope currently stores absolute epoch wake time. Imported suspended tasks need to enter scheduler restoration with an absolute scheduled time and a resume value, not merely preserve a relative delay in `VmSnapshot`.
- Frame comment says frames are ordered current-to-root. Toast writes activations root-to-current (`0..top`). Import must reverse order.
- The current object-only `initialVerbLocation`, `Frame.verbLocation`, and `Fork.verbLocation` cannot losslessly represent a v17 activation whose `vloc` is anonymous. This is a structural type mismatch.
- Next: inspect `VmState.snapshot/restore` and frame construction to verify which fields are executable rather than merely representable.

## Banteng `VmState` execution/restoration (`src/main/java/moo/vm/VmState.java`)

- `snapshot()` iterates the deque from first to last and `restore()` appends in that order, confirming the durable frame order is current-to-root. Toast root-to-current activation order must be reversed.
- `restore()` faithfully reinstates all existing Banteng continuation structures and execution outcome, but its public API accepts only an already-valid `VmSnapshot`; it has no importer for foreign Toast activation state.
- `ensureRoot()` always creates a fresh root at instruction zero with thread mode true. It must not be used to import a suspended VM because doing so loses the saved PC, stack, activation chain, and mode.
- Per-frame task player is observable only indirectly through the frame local named `player`; no independent field exists. Toast persists both runtime locals and activation `player`. An importer can map them losslessly only if they agree or if Banteng adds the independent field and uses it where activation metadata is authoritative.
- The existing frame receiver is general `MooValue`, which matches Toast `_this`; the verb-location field remains object-only and blocks anonymous v17 `vloc` values.
- The default limits are 60,000 ticks, 5 CPU seconds, and max depth 50. Toast persists only `max_stack_size`; the correct imported tick/CPU budgets need a named server policy, while max depth can map from the VM header.
- Minimum executable path is: decode the Toast VM into an import representation; compile/decode each activation program with a proven continuation mapping; construct a fully populated `VmSnapshot` in suspended state; restore it; inject its saved resume value through the same VM resume path used by native suspension; and register it with `PublicationScheduler` under its persisted task id and absolute due time.
- Next: inspect scheduler restoration APIs and current v17 queued-task composition to identify exact integration seams.

## First suspended task in `fixtures/mongoose.db`

- The section begins at fixture line 191 with `14 suspended tasks`; the first record occupies lines 192-452.
- Envelope: start epoch `-1` (already due), task id `348410728`, resume value integer `0`.
- VM: task-local value is an empty map; `top_activ_stack=0` (one frame); root vector `-1` (`MAIN_VECTOR`); `func_id=0`; max stack depth `50`.
- Program: language version 17 and one source program beginning `"Walk to exit, then go through it.";`. It has a unique `suspend();` at source line 24 of the persisted program.
- Runtime environment: 34 named variables. Material task identity includes `player=#7576`, `this=#470`, `caller=#7576`, `verb="e"`; later locals retain a WAIF `exit`, maps/lists/floats, and other movement context. The v17 value reader already supports all value kinds seen here.
- Operand stack: `0 rt_stack slots in use`.
- Activation: receiver `_this=#470`; `vloc=#2903`; threaded mode `1`; player `#7576`; programmer `#13505`; debug `1`; `verb="e"`; `verbname` is the full direction alias string.
- Continuation: `temp=TYPE_NONE`; `pc=240`; `bi_func_pc=0`; `error_pc=238`. Therefore this is an ordinary completed `suspend()` call awaiting injection of integer `0`, not a builtin-specific continuation.
- This task is feasible to execute in Banteng if and only if the persisted source compiles and the Toast suspension boundary (`pc=240`, source line 24, just after `suspend`) is translated to the exact Banteng instruction immediately after its `CALL suspend`; then `VmState.resume(0)` has precisely the required stack effect.
- Next: inspect scheduler restore/wake mechanics and spell out the smallest general task model plus fixture-specific import path.

## Banteng scheduler restoration seam (`PublicationScheduler.java`)

- `restoreQueuedTasks` currently accepts only `List<QueuedTask>`, compiles each source, creates a fresh root state, and schedules it with `resume=false` (`:94-159`). This is correct for delayed fork roots and categorically wrong for suspended VMs.
- The existing internal machinery already distinguishes the necessary scheduling action. `TimedWork.resume=false` enqueues a fresh task; `resume=true` enqueues a saved VM with a completion value (`:730-761`, `:1131-1132`).
- `executeSegment` restores the supplied snapshot and, when `wakeResult` exists, calls `VmState.resume(value)` or `MooVm.resumeWithError` before continuing (`:280-332`). That is the correct execution seam for Toast's saved suspended-task resume value.
- The missing scheduler API is a restore path that creates `SuspendedWork` from a fully imported `BytecodeProgram` plus `VmSnapshot`, registers the persisted task id/metadata, updates `nextTaskId`, and starts an absolute-time `TimedWork(..., resume=true)` whose completion is the persisted `Result` rather than the hard-coded integer zero.
- `TimedWork` presently stores only a boolean and `startTimer` hard-codes `Result.value(0)` for every timed resume. Lossless imported tasks require the timed record to carry the exact `BuiltinCatalog.Result` completion, including errors for interrupted tasks, or an equivalent typed wake field.
- Minimum checkpoint model: preserve current queued fork record; add a VM-bearing suspended task record containing task id, absolute due epoch, resume `Result`, task player, root/current program and full `VmSnapshot`; represent interrupted tasks as the same VM-bearing record with immediate `E_INTRPT` resume. Do not flatten these into queued forks.
- Next: determine whether all 14 fixture tasks share the first task's simple suspension shape or expose additional structural mismatches.

## All 14 Mongoose suspended tasks

- All 14 envelopes have start epoch `-1` and integer-zero resume values. Every VM has empty-map task local, root vector `-1`, `func_id=0`, max depth `50`, zero operand slots in every activation, `temp=TYPE_NONE`, and `bi_func_pc=0`.
- Thirteen tasks have one activation. Twelve of those are the same movement program suspended at Toast `pc=240 error_pc=238`; the remaining one-activation task is likewise an ordinary source/PC boundary.
- Task `312256312` has two activations (`top_activ_stack=1`): root furniture `sit` at `pc=156 error_pc=155`, and current nested `interact_with` at `pc=352 error_pc=350`, where the nested source contains the `suspend()` call. Both stacks are empty and neither has builtin-specific saved data.
- Thus this fixture does not require implementing Toast builtin continuation payloads before it can load. It does require multi-frame program import, frame-order reversal, exact PC translation for both caller and current frame, distinct `verb`/`verbname`, and resume-value scheduling. Loading it as fresh programs at PC zero would rerun already-completed side effects and is incorrect.

## Minimum correct design changes

1. Replace `Checkpoint.tasks: List<QueuedTask>` with a typed durable task sum retaining both existing queued-fork records and VM-bearing suspended records; do not reinterpret suspended tasks as forks.
2. Add a v17 activation/VM decoder in the existing codec for exactly the Toast grammar documented above, including general values and all persisted fields. Unsupported builtin continuation payloads may produce an explicit unsupported-format error, but ordinary suspended tasks must be decoded.
3. Add an import/compiler result that pairs each compiled `BytecodeProgram` with a proven mapping from Toast vector PCs to Banteng instruction boundaries. For these fixtures, verify the saved boundary is the instruction immediately after the source's `suspend` call. Source line alone is insufficient as a general mapping key.
4. Extend `VmSnapshot.Frame`/`VmState.Frame` with the activation data needed for observable MOO semantics: task player, debug, verb/full-verb identity, general-value verb location, and any non-default temp/error continuation data that the VM actually consumes. Do not silently discard fields merely because this fixture has defaults.
5. Construct frames current-to-root from Toast's root-to-current list and restore a suspended `VmSnapshot` without `ensureRoot`.
6. Extend scheduler restoration so a VM-bearing task is scheduled at its absolute persisted epoch with its exact saved `Result`; overdue tasks enqueue immediately via `resume=true`, and interrupted tasks use `E_INTRPT`.
7. Extend checkpoint writing symmetrically so native suspended tasks survive a Banteng checkpoint/restart as suspended VM records rather than disappearing or becoming fresh forks.

## Proposed tests

- Codec fixture test: read the real `fixtures/mongoose.db` and assert 14 VM-bearing tasks, first-task id/time/resume value/task local/max depth/one frame/PCs/identity fields, plus the two-frame task's root/current programs and reversed restored frame order.
- PC import test: for the first movement source, assert Toast `pc=240` maps to the exact Banteng instruction after `CALL suspend`; assert `error_pc=238` maps to the call/error boundary. Add the two-frame task mappings (`156/155` and `352/350`).
- Resume test: restore the first task, inject saved integer zero once, execute, and prove no pre-suspension side effect reruns while the post-suspension `exit:invoke()` path runs once.
- Multi-frame resume test: restore task `312256312`; prove nested `interact_with` resumes and returns into the existing `sit` caller rather than restarting either verb.
- Byte round-trip test: codec read/write/read preserves every decoded VM field and all task values, including `verbname`, PC/error PC, empty stacks, task local, task id, and due epoch.
- Scheduler restart test: checkpoint with a future native suspended task, restart before due time, assert it remains dormant; after due time assert exact once-only resume value delivery. Repeat with an overdue imported task and an interrupted task carrying `E_INTRPT`.
- Negative format test: a nonzero Toast `bi_func_pc` whose builtin continuation has no importer fails with an exact unsupported-continuation diagnostic, never restart-at-zero or task discard.

## RE completion review

- Exact fields and format: documented under Toast VM, activation, activation metadata, and task-queue sections.
- First fixture task: documented under `First suspended task in fixtures/mongoose.db`.
- Lossless mappings and mismatches: documented under `BytecodeProgram`, `VmSnapshot`, and `VmState` sections.
- Feasibility and minimum correct changes: documented under `All 14 Mongoose suspended tasks` and `Minimum correct design changes`.
- Concrete tests: documented under `Proposed tests`.

## 2026-07-21 Phase 4 items 3-4 design-audit checkpoint

- Current codec records now retain `Map<String, Optional<MooValue>>` locals, typed ordinary/control stack slots, general-value receiver and verb location, thread mode, task/player/programmer/debug/verb identities, temp, `pc`, `bi_func_pc`, and `error_pc`; `SuspendedTask` retains due epoch, resume value, task local, root vector, function id, max depth, interruption status, and root-to-current activations.
- Current `VmSnapshot.Frame` still narrows verb location to `ObjectValue` and omits task player, debug, verb names, temporary value, Toast error boundary, and compiler-call-path provenance. Its stack admits only ordinary values, so Toast `TYPE_CATCH`/`TYPE_FINALLY` markers must be translated into existing handler/finally/loop state rather than copied.
- Current scheduler restore rejects suspended tasks, and timed resume retains only a boolean then injects integer zero. It needs an exact persisted wake `Result`, imported `VmSnapshot`, original task id/absolute due time, registry insertion, and delayed activation only after startup notifications.
- Observed fixture state from the prior audit: all 14 ordinary suspended tasks are overdue integer-zero wakes with empty ordinary stacks; the two interrupted tasks contain the two catch markers and the one live loop that exercise structural control-state translation. Activation serialization is root-to-current while Banteng snapshots are current-to-root.
- Blocker for the exact algorithm is not user input: finish reading the current `ToastV17ProgramLayout` structural-call-path API and pinned Toast handler/loop stack-unwind code, then state the deterministic conversion and its exact tests. No edits beyond this mandatory uncommitted checkpoint note.
- Next action: inspect the layout API, pinned Toast VM execution/serialization code, exact fixture control-stack values, and scheduler execution seam; return the minimal implementation order and line evidence to the parent.

### Verified control-state details

- Pinned Toast `execute.cc` encodes a `for` loop as two live runtime-stack slots: collection base and iterator. The iterator is initialized to `TYPE_NONE`; for lists/strings it becomes one-based integer `1`, the current element is assigned, then it is incremented before the loop body executes. Therefore fixture stack `{#249, #3516}`, integer `2` means Banteng `LoopState(values={#249,#3516}, secondaryValues=empty, nextIndex=1, range=false)` at the owning `ITERATE` instruction: the first element was already assigned and the next zero-based index is 1.
- Fixture `start` fork and `parse_server_messages` current frames each serialize stack slots `INT(0)`, handler-label `INT(27 or 49)`, `TYPE_CATCH(1)`. Pinned `unwind_stack` interprets the marker count as one `(error selector, handler PC)` pair, so the ordinary integer is the catch-all selector payload, not an expression operand. The equivalent Banteng frame has an empty ordinary operand stack plus the active `HandlerState` for the structurally matching try/catch `ENTER_HANDLER`, with operand depth 0 and phase TRY.
- `ToastV17ProgramLayout.resolve` plus `locateBantengCall` now gives each activation's exact owning `BytecodeProgram`, call instruction index, and post-call instruction pointer. Its exact fixture test checks all 22 activations through 10 distinct boundaries, including root fork vector 0 and nested caller frames.
- Banteng `ITERATE` keys `VmSnapshot.loops` by the iterate instruction index; its `LoopCursor.nextIndex` is zero-based and is incremented before entering the body. Handler state is keyed by full `HandlerSpec`, not a byte label, and the compiler emits owner and structured-catch handlers before the protected body.
- Remaining audit: determine an exact structural API to locate enclosing Banteng try/for constructs from the call AST path, because call-site lookup alone cannot identify the `ENTER_HANDLER`/`ITERATE` owner. Also inspect general receiver/verb-location and frame return routing requirements, and test once-only scheduler sequencing.

### Verified frame and wake mapping

- Toast serializes activations root-to-current. Banteng's frame deque and `VmSnapshot.frames` iterate current-to-root. Import must reverse the activation list; translated oldest/root frame gets `ReturnMode.ROOT`, and every newer activation gets `ReturnMode.VERB` while `bi_func_pc == 0`. A nonzero builtin continuation remains an explicit unsupported format until its continuation importer exists.
- Every activation's saved `pc` is immediately after the suspended verb/builtin call. `VmState.resume(value)` pushes the wake value on the current frame and changes SUSPENDED to RUNNING; `MooVm.resumeWithError` routes the exact error through structured handlers. Caller frames already sit after their `CALL_VERB`; `finishFrame` removes the current frame and pushes the child return into the caller, so no call is replayed.
- `TYPE_CLEAR`/unset v17 locals must be omitted from Banteng's locals map. `MooVm.loadLocal` treats absent locals as `E_VARNF`, matching Toast's uninitialized runtime environment behavior. Ordinary saved values retain codec top-first order in `VmSnapshot.Frame.operandStack`.
- Root-vector selection is semantic: activation zero uses `rootActivationVector` (`-1` main, otherwise the selected Toast fork). `ToastV17ProgramLayout` resolves the flat Toast vector and `locateBantengCall` returns the owning hierarchical Banteng fork program. Later activations always use main vector.
- Exact wake conversion is `Result.error(E_INTRPT)` when `interruptionStatus` is present; otherwise `Result.value(resumeValue)`. Preserve the absolute epoch second; do not turn overdue `-1` or interrupted `0` into a relative delay. Current `executeSegment` already applies either result exactly once before execution and clears its local wake variable.
- Structural mismatch: Toast activation `_this` and `vloc` are general `MooValue`; `VmSnapshot.Frame.verbLocation`, initial verb location, `VmState.Frame`, constructors, fork records, and scheduler/task registry currently require `ObjectValue`. These must become `MooValue` end-to-end (with world operations validating object type only where semantically required).
- Structural mismatch: Toast `debug` changes whether runtime errors raise or become ordinary error values. Banteng frame state has no debug flag and always routes errors. All inspected interrupted-frame compatibility records have debug=1, but complete v17 import cannot discard this field; add it to snapshot/runtime frames and honor it in error production. `verb`/`verbNames`, per-activation player, and temp likewise need either runtime fields or a proven derived/validated representation for symmetric checkpointing.
- Next action: finish exact fixture metadata inventory, derive structural continuation metadata API for handler/loop owners, inspect temporary-register compilation equivalence, and specify exact scheduler tests and implementation order.
