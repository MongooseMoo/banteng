# Phase 5 numeric-family checkpoint

## Current state

- Branch: `master`.
- Active source slice is uncommitted and limited to the 29 missing pinned `numbers.cc` builtins plus the runtime flags required by exact `ftime()` clock behavior.
- Previous commits are `e8bf5ab feat: complete Phase 4 task and host semantics` and `b00bf65 fix: pin Toast build configuration`.
- The worktree also contains many pre-existing untracked notes and investigation artifacts; none are to be staged.

## Findings and observations

- Pinned Release preprocessing produces the same 238 registered builtin names for stock Toast and Mongoose. Banteng had 86 valid names before this slice and lacked 152. `anon` is absent from the Release authority because `-DNDEBUG` disables it.
- The numeric-family implementation added the 29 registrations missing from pinned `numbers.cc`.
- Focused Java tests passed before the final adversarial corrections.
- Toast stock targeted numeric/custom rows passed: 335 passed and 3 declared skips.
- Banteng stock targeted rows exposed three pre-existing arithmetic defects: signed 64-bit minimum division and two modulus-sign cases.
- Banteng Mongoose targeted rows cannot start because `LambdaMooV17Codec.read` rejects a valid database containing 14 suspended tasks.
- Direct pinned-source and live-oracle review found final active-slice defects: `random_bytes()` needs structured `E_INVARG` details; large finite `acosh()` and `asinh()` need stable formulas; `ftime()` must distinguish realtime, monotonic, and monotonic-raw clocks; `ctime()` clamps its positive input; a shorter second `distance()` list yields `E_TYPE`; and the `MATH_FUNC` builtins must map domain and non-finite outcomes like Toast.
- Java 25 FFM can directly call Linux `clock_gettime`; this is required to implement `CLOCK_MONOTONIC_RAW` rather than treating it as ordinary monotonic time.

## Changes in progress

- `BuiltinCatalog` now contains stable and error-mapped numeric implementations, structured `random_bytes()` failure, `ctime()` clamping, `distance()` `E_TYPE`, and direct `clock_gettime` use for `ftime()`.
- `build.gradle.kts` is being updated so test workers and installed distributions grant native access required by Java FFM.
- `scripts/run_banteng_wsl.sh` still needs the same native-access JVM flag.
- Numeric tests still need exact assertions for the corrected boundary and structured-error behavior.

## Blockers

- No blocker in the active numeric slice.
- After this slice is gated and committed, the earlier arithmetic failures and Mongoose v17 suspended-task decode failure must be fixed before expanding to another Phase 5 builtin family.

## Next action

1. Add the native-access flag to the exact WSL server runner.
2. Add regression assertions for each corrected numeric semantic.
3. Run the focused Java test, exact targeted Toast/Banteng numeric paths, full WSL `gradlew check`, and `git diff --check`.
4. Commit the kept numeric slice alone.
5. Fix and commit the three earlier VM arithmetic defects, then fix and commit the Mongoose v17 suspended-task decode defect.

## Later checkpoint

- Focused `BuiltinCatalogTest` now passes under Java 25 in WSL.
- `bash -n scripts/run_banteng_wsl.sh` and `bash scripts/test_managed_runners_wsl.sh` pass with the native-access JVM flag.
- Full WSL `JAVA_HOME=/opt/java/25 ./gradlew check` passes.
- The exact stock Banteng numeric path run now reports 332 passed, 3 declared skips, and only the same 3 earlier VM arithmetic failures. Every newly implemented numeric builtin row passes.
- A final read-only adversarial comparison against pinned `numbers.cc` found additional active-slice defects that must be fixed before commit: unstable `atanh()` near zero; incorrect `round()` immediately below half boundaries; Java Formatter's wrong `floatstr()` tie rule; `frandom()` incorrectly sharing `random()` state and excluding the upper endpoint; missing `random_bytes()` construction-limit behavior; `distance()` incorrectly rejecting overflow with `E_FLOAT`; and Java-based `ctime()` having a smaller range and different expanded-year formatting than libc.
- Stock and Mongoose numeric builtin bodies are identical, so one corrected implementation serves both pinned profiles.
- The active slice remains uncommitted. It is not ready to commit until these source-backed defects are corrected and the same focused, managed, launcher, full-Java, and diff gates pass again.

## Revised next action

1. Correct the additional numeric defects inside the current slice, using the existing FFM boundary for libc-compatible `ctime()`.
2. Add focused regressions for stable `atanh`, half-boundary `round`, ties-to-even `floatstr`, independent `frandom`, configured `random_bytes` limits, `distance` overflow, and expanded-year `ctime`.
3. Repeat every active-slice gate and commit only after all newly implemented rows and Java gates pass.

## Implementation checkpoint

- The additional corrections are now in the worktree but have not yet been compiled:
  - `atanh()` uses a stable `log1p` identity.
  - `round()` compares the exact fractional remainder instead of adding `0.5` first.
  - `floatstr()` uses locale-root `DecimalFormat` with ties-to-even rounding and Toast-compatible scientific exponent signs.
  - `frandom()` has independent generator state and a 31-bit fraction that includes both endpoints; `reseed_random()` remains scoped to the integer generator.
  - `distance()` now returns Toast's direct floating result even when it is infinite.
  - `ctime()` now calls libc `localtime_r` and `strftime` through the existing Java 25 FFM boundary, preserving Toast's range and expanded-year formatting.
  - `random_bytes()` reads `max_string_concat` and `max_concat_catchable` through the current `WorldTxn`, returns `E_QUOTA` for catchable overflow, and requests an uncatchable seconds abort otherwise.
  - `BuiltinCatalog.Result`, `MooVm`, and `VmState` now carry and apply that explicit seconds-abort outcome.
- Focused regressions have been added for the new numeric boundaries, separate PRNG state, endpoint inclusion, configured construction limits, infinity, and expanded years.
- No gate has been run since these latest changes. The active source slice remains uncommitted.

## Immediate next action

1. Finish the VM-level regression proving the uncatchable seconds-abort result bypasses MOO handlers.
2. Compile and run `BuiltinCatalogTest` and `MooVmTest`; correct only defects in this active slice.
3. Repeat the exact managed numeric path, launcher gate, full WSL `gradlew check`, and `git diff --check` before committing.

## Committed numeric slice

- The exact managed numeric path repeated at 332 passed, 3 declared skips, and only the same 3 earlier VM arithmetic failures.
- Launcher syntax and managed-runner regression passed.
- Full WSL `JAVA_HOME=/opt/java/25 ./gradlew check` passed.
- `git diff --check` passed.
- The complete kept numeric builtin slice was committed as `13eace1 feat: implement Phase 5 numeric builtins` with exactly seven tracked files; notes and unrelated artifacts remained untracked.

## Active arithmetic slice

- The next active source slice is the three reproducible `builtins/math.yaml` VM arithmetic rows that blocked the otherwise-green numeric family:
  - `minint_division_edge_case_64bit`: code `-9223372036854775807 / -1`, Toast/fixture expected `-9223372036854775807`, Banteng returned `9223372036854775807`.
  - `int_modulus_neg_pos`: code `-15 % 4`, Toast expected `1`, Banteng returned `-3`.
  - `int_modulus_pos_neg`: code `15 % -4`, Toast expected `-1`, Banteng returned `3`.
- Current `MooVm` uses Java integer `/` and `%` directly. Its floating remainder path already adjusts a nonzero remainder to the divisor's sign, matching the required modulus convention.
- Pinned Toast dispatch routes both operators through `do_divide()` and `do_modulus()` in `numbers.cc`; those exact bodies are the next authority to read before editing.
- The worktree has no tracked modifications after `13eace1`; only the pre-existing untracked notes and investigation artifacts remain.

## Arithmetic next action

1. Read the exact pinned `do_divide()` and `do_modulus()` implementations and existing VM arithmetic tests.
2. Add exact regressions and correct the one integer arithmetic dispatch path.
3. Prove the three rows and their full `math.yaml` suite against Banteng, run affected Java and full checks, then commit or fully restore this arithmetic slice.
# Checkpoint: Mongoose v17 suspended-task persistence

## Continuation checkpoint

- The implementation plan had a causal ordering defect: its Phase 5 `banteng mongoose` gate boots the exact Mongoose fixture, but complete suspended/interrupted VM import and checkpointed-connection startup handling were deferred to Phase 7. The plan was corrected so these are explicit pre-Phase-5 Phase 4 prerequisites. Phase 7 now extends that complete path with retention/reclamation/graceful shutdown instead of first adding task round trips.
- The corrected plan explicitly requires direct v17 queued/suspended/interrupted records, exact Toast-compatible program layout to map `error_pc..pc` to AST call paths, matching Banteng call-site paths, Toast stack-control translation, existing-scheduler restoration, native/imported checkpoint symmetry, and six formerly active connection notifications before `server_started`. It forbids source-line matching, fixture hash/PC tables, external Toast normalization/runtime, task discard, opaque retention, and restart-at-zero.
- Exact audit now covers all 22 activation frames. All sources compile with the current Banteng parser/compiler. The ten distinct saved call mappings are proven: `240/238 -> IP199`, `156/155 -> 122`, `352/350 -> 258`, interrupted task `500919854` maps four frames to IPs `55,127,149,60`, and interrupted task `249208170` maps root fork vector 0 plus two frames to IPs `6,91,38`.
- General mapping cannot be derived from line numbers or raw serialized source/PC alone. The direct loader must reproduce pinned Toast compiler layout and map the resulting call AST path to the same path retained by Banteng. Nested calls on the same saved line prove line-only matching invalid.
- Exact stack-control evidence: `parse_server_messages` and root `start` fork each hold integer zero, a handler label, and `TYPE_CATCH(1)`; `transmit` holds an active for-loop list and index. These must become Banteng handler/loop continuation state rather than ordinary operands.
- `LambdaMooV17Codec` now has typed queued and VM-bearing task records, complete activation metadata, optional/clear locals, value/control runtime stack slots, suspended/interrupted envelopes, interrupted `E_INTRPT` normalization, active-connection records, and symmetric supported-record writing. A focused synthetic suspended-VM round-trip test and exact Mongoose ids/frame/control/connection assertions were added.
- The codec's first rerun reached an unsupported v17 tag 6 in activation locals, proving undefined locals are present. The model was corrected to retain each suspended local as optional and write/read tag 6 exactly. A later rerun was invalidated by the concurrently editing call-path subagent and must be repeated after that bounded edit completes.
- `Banteng`, `MooServer`, and `MooRuntime` now carry durable tasks plus checkpointed connections. Startup queues exact `listener:user_disconnected(player)` requests before `server_started`; checkpoint writing now includes current active player/listener pairs. Tests for this startup ordering remain to be added.
- `PublicationScheduler.restoreTasks` currently restores queued tasks and explicitly rejects VM-bearing tasks; this is a temporary red boundary inside the active uncommitted source slice, not a kept result. It must be replaced by exact imported `VmSnapshot` restoration before any commit.
- The call-path/layout Codex subagent is actively editing `BytecodeProgram` and `MooCompiler` and adding `ToastV17ProgramLayout`; its worktree is currently mid-edit and was the cause of transient compile errors. Do not run or interpret full Gradle gates until it reports completion.
- Next action: accept and verify the completed call-path/layout implementation, rerun the focused codec test, then implement Toast control-stack to `VmSnapshot` translation and exact scheduler restore/checkpoint behavior before the original Mongoose managed gate.

- Commit `c343509 fix: match Toast integer division and modulus` completed the two remaining managed `math.yaml` arithmetic divergences. The full managed stock math family passed with 203 rows and 3 declared skips; focused Gradle, full WSL `check`, and `git diff --check` passed before commit.
- The next Phase 5 Mongoose gate reaches Banteng startup and fails in `LambdaMooV17Codec.read()` because the exact pinned `fixtures/mongoose.db` contains `14 suspended tasks`; Banteng currently requires the literal line `0 suspended tasks` and also requires `0 interrupted tasks`.
- Both pinned Toast trees are at their exact profile commits: stock `aecc51e9449c6e7c95272f0f044b5ba38948459e`, Mongoose `72e3c7f96ce7a41fdeba793aef8818dc4408072e`. Their v17 task grammars are byte-identical. The fixture row is valid, not malformed.
- The fixture contains 14 suspended tasks and 2 interrupted tasks. Toast suspended records contain absolute wake epoch, task id, resume value, task-local value, VM header, and complete root-to-current activation records. Interrupted records contain task id/status plus the same VM and restore as an immediate `E_INTRPT` wake.
- Banteng currently models only delayed queued forks with `QueuedTask`; it drops timed `resume=true` work from checkpoints and has no VM-bearing v17 task representation.
- All 14 fixture suspended tasks are overdue (`-1`), resume with integer zero, have empty operand stacks, `TYPE_NONE` temp, and no builtin continuation (`bi_func_pc=0`). Thirteen have one frame; task `312256312` has two frames. Import must reverse Toast root-to-current ordering.
- The hard correctness boundary is program-counter translation. Toast persists byte offsets in Toast bytecode; Banteng persists instruction indices in different IR. Restarting source at zero would replay side effects and is forbidden. Current-frame mappings must resume immediately after `suspend()`; the two-frame caller must resume immediately after its `interact_with` verb call.
- A failing regression was added to `QueuedTaskV17CodecTest`: reading the exact Mongoose fixture must return all 16 task records. The focused WSL test currently fails with the expected `IOException` at that read.
- A disposable live Toast Mongoose process was stopped cleanly; `/tmp/banteng-mongoose-task-audit.db*` was removed and port 47892 was verified closed.
- Active source slice: implement exact VM-bearing v17 task parsing, proven PC-to-Banteng continuation mapping for every fixture activation, scheduler restoration with exact wake values/errors, and symmetric durable representation without skipping, opaque preservation, or restart-at-zero.
- Current blocker under investigation: determine the smallest general, source-backed Toast-PC mapping that does not embed or invoke Toast as an alternate production runtime. A read-only Codex subagent is auditing Toast compiler/disassembly and all distinct fixture PC pairs.
- Next action: finish that mapping proof, then implement the typed durable task model and exact codec/scheduler restoration in this same source slice; rerun `QueuedTaskV17CodecTest` and the original Banteng Mongoose managed gate.
## 2026-07-21 v17 task-fixture inventory subtask

- Current accepted state: numeric builtins are committed in `13eace1`; integer division/modulus corrections are committed in `c343509`.
- Current production blocker: `LambdaMooV17Codec.read` rejects the exact Mongoose v17 fixture at `14 suspended tasks`; the codec currently models queued delayed forks only and hard-requires zero suspended/interrupted tasks.
- Observed pinned grammar: suspended/interrupted records contain complete Toast VM activation stacks, including source text and Toast bytecode program counters. Exact restoration requires mapping those saved boundaries to Banteng VM state without discarding or bypassing tasks.
- Active bounded action: inventory every suspended/interrupted task and activation in `fixtures/mongoose.db`, with special focus on the two interrupted tasks; report saved verb, counters, source/call boundary, uniqueness against Banteng compiled instructions, and unsupported constructs.
- No production or test edits are authorized in this subtask. This notes file remains untracked and must not be staged or committed.
- Next action: read the reverse-engineering protocol, inspect the exact fixture and pinned Toast task/activation readers, then produce the inventory for the parent agent.

### Pinned Toast task/VM grammar findings

- `write_suspended_task` emits `start_epoch task_id <resume-value>` followed by `write_vm`; interrupted records emit `task_id <status-string>` followed by the same `write_vm` payload.
- `write_vm` emits the VM-local value; `top_activ_stack root_activ_vector func_id max_stack_size`; then exactly `top_activ_stack + 1` activations. Frame zero uses the saved root vector and every later frame uses the main vector.
- `write_activ` emits language version, complete unparsed program source, named runtime environment, runtime operand stack, activation identity, temp value, and `pc bi_func_pc error_pc`; builtin continuation data follows when `bi_func_pc != 0`.
- `read_activ` recompiles the saved source and accepts the saved `pc` only when the immediately preceding compiled Toast opcode is a verb call or builtin call. The PC is therefore a post-call resume boundary in Toast bytecode, not a source character or line offset.
- Interrupted tasks are deliberately restored as suspended tasks with start time zero and resume value `E_INTRPT`; their serialized textual status is read but does not determine restoration behavior.
- Current worktree has tracked modifications in the codec/runtime/server/test owned by the parent or another active slice. This read-only inventory will not touch them.
- The attempted read-only query to the previously launched port `47892` returned no bytes, so the inventory will be derived from the exact fixture and pinned Toast source/tooling rather than assuming that server is available.
- Next action: identify or build a read-only parser/dumper for the exact activation payload, then inventory all sixteen records and correlate saved post-call PCs to source expressions and Banteng instructions.

### Read-only oracle access attempt

- A disposable exact Mongoose fixture copy loaded successfully under the pinned Mongoose Toast binary on port `47893`; all 16 saved task records passed Toast's own reader and PC-validity checks.
- The Mongoose pre-login flow is application-specific: raw `connect Wizard` is rejected, while `welcome` says to enter `login`, then prompts for username/email and password. No authorized credential is recorded in the selected profile, whose `login_commands` array is empty.
- Because credential inference would not be exact authority, the server was shut down cleanly. Shutdown wrote only disposable `/tmp/banteng-v17-inventory.db.new.#1#` state; repository files were not changed.
- The Toast binary documents `-e` as exact emergency wizard mode. That mode is the next read-only inspection path because it uses the pinned executable and fixture without inventing a Mongoose login credential.

### Exact emergency-mode task inventory observations

- Pinned Toast emergency mode exposes task state without running queued work. `queued_tasks()` returns 16 tasks. `task_stack(task_id, 1)` supplies activation identities and saved source lines; serialized activations are root-to-top, while `task_stack` returns top-to-root.
- Interrupted task `500919854`, textual status `waiting on thread 1`, has serialized frames: `#0:checkpoint_started` line 4 at `pc/error_pc 72/71`; `#140:transmit` line 20 at `182/181`; `#1811:play` line 25 at `206/205`; `#1811:send` line 8 at `84/82`. Every `bi_func_pc` is zero.
- Those four saved source boundaries are, respectively: `this.checkpoint_channel:transmit(...)`; `$mcmp:play(...)`; `this:send(...)`; and `who = occupants(who);`.
- Interrupted reading task `249208170` has serialized frames: `#2836:start` line 3 at `12/11`; `#2836:do_start` line 16 at `113/112`; `#2836:parse_server_messages` line 6 at `45/43`. Every `bi_func_pc` is zero.
- `#2836:start` line 3 is `this:do_start();`. The exact call expressions for the later two reading-task frames remain to be correlated from their serialized source.
- All 14 ordinary suspended records have one frame except task `312256312`, which has two. Their saved PC triples are visible in the fixture and will be tabulated next.

### Banteng compiler boundary audit

- All 22 serialized activation programs compile successfully with Banteng's current parser/compiler. No activation source exposes an unsupported parser or compiler construct.
- The 13 movement activations share byte-identical saved source (`def18d019a56` SHA-256 prefix). Each saves line 24 `suspend();`; Banteng has exactly one candidate call there, instruction `198 CALL suspend`.
- Task `312256312` root `#31320:sit` line 16 `player:interact_with(this);` has one Banteng candidate, `121 CALL_VERB`; its top `#249:interact_with` line 40 `suspend();` has one, `257 CALL suspend`.
- Interrupted task `500919854`: `#0:checkpoint_started` line 4 has candidates `45 CALL toint`, `49 CALL_VERB` (`ampm`), and `54 CALL_VERB` (`transmit`); the adjacent child activation `#140:transmit` uniquely selects instruction 54. `#140:transmit` has sole line candidate `126 CALL_VERB` (`play`). `#1811:play` has candidates `146 CALL_VERB` (`merge`) and `148 CALL_VERB` (`send`); adjacent child `#1811:send` uniquely selects 148. Top `#1811:send` has sole line candidate `59 CALL occupants`.
- Interrupted reading task `249208170`: its VM header selects root fork vector 0. In that fork vector, `#2836:start` line 3 has sole candidate `5 CALL_VERB` (`do_start`). `#2836:do_start` line 16 has sole candidate `90 CALL_VERB` (`parse_server_messages`). Top `#2836:parse_server_messages` line 6 has candidates `37 CALL read` and `44 CALL typeof`; the exact serialized status `interrupted reading task` uniquely selects `read` as the suspended operation.
- Therefore every saved activation boundary in this fixture is uniquely identifiable from saved source line plus the exact adjacent activation/status/root-vector metadata. Raw Toast PC values cannot be reused as Banteng instruction pointers; they map to different instruction indices.
- This boundary uniqueness does not by itself prove exact snapshot restoration: Toast runtime stacks/environments still must be mapped into Banteng frame state, including any handler/loop/index/finally state required at the selected instruction.
- The disposable Toast emergency server was ended with `abort` (`NOT saving database`). The temporary compiler-audit source and `/tmp/banteng-v17-inventory*` files were removed. Repository production/tests were not edited by this subtask.
- Next action: deliver the exact 22-row activation table and boundary conclusion to the parent agent.
## 2026-07-21 Phase 4 v17 snapshot importer implementation checkpoint

- Controlling work is corrected Phase 4 prerequisite items 3-4: direct `SuspendedTask` to existing `VmSnapshot`/`PublicationScheduler` restoration, without source-line or fixture-PC tables and without an alternate executor/runtime.
- Current exact fixture facts used by the importer: activations are serialized root-to-current; Banteng frames are current-to-root; only the oldest activation selects `rootActivationVector` (all newer activations use main vector `-1`); `ToastV17ProgramLayout.resolve()` plus `resolveContinuation()` supplies the unique call, enclosing handler, and loop structures.
- The two catch stacks decode top-first as `TYPE_CATCH(1), handler-label, INT(0)` after codec reversal. They must leave no ordinary operands and become the structurally resolved catch clause plus owner `HandlerState` entries at depth 0 in `TRY` phase.
- The interrupted `transmit` activation's file stack is `LIST{#249,#3516}, INT(2)` bottom-to-top, therefore codec top-first is `INT(2), LIST{#249,#3516}`. It must become the exact enclosing `ITERATE` `LoopState` with `nextIndex=1`, leaving no ordinary operands.
- Timer correction in progress: replace the boolean resume flag with `Optional<Result>` so ordinary suspended tasks wake with their exact serialized value and interrupted tasks wake with `E_INTRPT`; queued forks have no wake result. Restored timers will be registered and visible but dormant until the runtime has completed checkpoint disconnect callbacks and `server_started`.
- Imported durable task provenance will remain attached to the existing timed-work record for symmetric checkpoint output while dormant. Native timed suspension requires a direct typed conversion from its `VmSnapshot`; no opaque source substitute is permitted.
- No blocker is known. Next action is patching `PublicationScheduler`, then `MooRuntime.startServer`, compiling with Debian `/opt/java/25`, and adding focused exact fixture/timer tests. The current worktree is deliberately uncommitted and notes remain untracked.

### Importer source and gate update

- `PublicationScheduler` now imports each `SuspendedTask` into one structured `VmSnapshot`: validates function/builtin/language/debug/temp/max-depth boundaries, compiles exact source, selects the root vector only for the oldest activation, resolves the unique call/control site, omits TYPE_CLEAR locals, reverses root-to-current activations into Banteng current-to-root frames, installs ROOT/VERB return modes, and retains exact task player, programmer, receiver, verb location, task-local, and max depth.
- Catch and loop conversion is implemented without leaving control operands: each exact catch marker consumes its label/selector pair and installs the structurally resolved clause+owner handlers; the exact transmit top-first `[INT(2), LIST]` becomes its enclosing `ITERATE` loop with `nextIndex=1`.
- `TimedWork` now has `Optional<Result>` rather than a boolean resume flag. Restored ordinary suspension uses the exact stored value or stored error; interrupted restoration uses `E_INTRPT`; queued forks have no wake result. Timer removal remains a single conditional `Map.remove(id, timed)` before enqueue.
- Restored timers are inserted into the scheduler/registry but no wake threads start until guarded `activateRestoredTasks()`. `MooRuntime.startServer()` calls this only after the ordered checkpoint disconnect submissions and blocking `server_started` submission complete.
- Imported durable records remain typed and attached unchanged while dormant. Native timed suspension has direct typed `VmSnapshot` to `SuspendedTask` export for structurally positioned calls, including supported handler/loop state; runtime continuations and unsupported frame controls are rejected explicitly rather than silently lost.
- Exact Debian compile gate passed before the newest tests: `compileJava compileTestJava`, exit 0, `BUILD SUCCESSFUL in 14s`.
- The first focused exact fixture test reached its final equality assertion after successfully importing all 16 tasks/22 frames and validating all frame IP, handler, loop, return-mode, empty-control-stack, dormant timer, and registry assertions. The sole failure was test-only list ordering: scheduler timers are task-id-sorted while fixture records are file-ordered. The assertion has been corrected to compare the unchanged records independent of order.
- Next action: finish exact ordinary-value/E_INTRPT once-only timer tests, rerun the focused fixture and scheduler tests, correct any source failure, then run the required compile gate and inspect the scoped diff. No current semantic blocker is known.

### Focused restoration test update

- Exact ordinary-value wake now passes: a dormant restored task resumes with stored integer `73`, executes only the continuation after `suspend()`, publishes the mutation once, removes both timer and registry rows, and a second activation call is rejected.
- Exact interrupted wake passes: an imported TYPE_CATCH task receives `E_INTRPT`, routes it through the structurally restored clause+owner handlers, executes its `except (E_INTRPT)` body, and removes timer/registry rows.
- Exact fixture importer passes all 16-task/22-frame checks including task 312256312 IPs and return modes, task 500919854 four IPs and transmit loop cursor, task 249208170 three IPs and both two-handler frames, empty ordinary control stacks, dormant visibility, and typed durable-record equality independent of TreeMap ordering.
- Startup-order test initially observed only the restored task mutation because the synthetic callback verbs lacked MOO's executable permission bit and were therefore correctly absent from `WorldTxn.verb(..., requireExecutable=true)`. After setting permission bit 4, the test passes with exact publication order `#41 user_disconnected`, `#42 user_disconnected`, `server_started`, then restored timer.
- The startup-order test proves restored timers do not run merely because their epoch is overdue; `MooRuntime.startServer()` completes both checkpoint disconnect callbacks in file order and the blocking `server_started` task before activating restored timers.
- Remaining assigned proof is native timed suspension checkpoint/restart exactly once, then the combined focused scheduler/VM/codec gates and required Debian compile gate. No blocker is known.

### Expanded reverse-control boundary

- Combined restoration tests passed in 1m20s. Full `PublicationSchedulerTest`, `TaskRegistryTest`, `VmSnapshotTest`, `QueuedTaskV17CodecTest`, and `ToastV17ProgramLayoutTest` passed in 1m40s. The required Debian compile gate passed, and `git diff --check` was clean.
- Native future non-handler suspension checkpoint/restart now passes exactly once. Imported Mongoose handler activations remain attached to their typed original `SuspendedTask` while dormant and resume through structurally imported handlers.
- A correctness audit found that general native active-handler export cannot use a placeholder Banteng target as a Toast label. The exact required layout authority is structural continuation path to finalized Toast handler/finally target PCs and exact selector values/order. The temporary fake label was removed and the exporter rejects that unsupported boundary rather than corrupting v17 state.
- The layout work now has a basic reverse API for catch/finally labels, but the controlling Phase 4 requirement is broader: exact stack-shape/phase authority for multi-clause and non-ANY catches, nested catch/finally/loop interleaving, running-finally reason/payload states, all loop kinds/cursors/base identities, and exact reverse output. The current fixture-only importer is not plan completion.
- A first import-side integration patch crossed the instruction to hold for the expanded API. It was reversed exactly, without Git restore and without removing earlier green work. The shared tree returned to the last compile-stable handler-rejection boundary; exact Debian `compileJava compileTestJava` passed in 4s afterward.
- Current state: source is intentionally held stable. Next action is to continue only when the expanded ordered stack-shape layout API is announced stable, then implement the full typed inverse in `VmSnapshot`/`VmState`/`MooVm`/`PublicationScheduler` and add exact nested phase/loop roundtrip and once-only tests.

### Exact structural inverse implementation checkpoint

- The expanded `ToastV17ProgramLayout.StructuralStackShape` and typed `VmSnapshot` finally/loop state APIs are now stable in the shared worktree. The VM child is holding its files unchanged while the scheduler consumer is completed.
- The scheduler import integration and helper patch did land despite the earlier truncated tool response. It now resolves every activation's structural shape and consumes typed catch, protected/running finally, collection-loop, and range-loop slots instead of fixture-specific control operands.
- One import correctness defect remains in the landed helper: catch/protected-finally `operandDepth` is materialized while structural entries are still being consumed, so nested lower structural slots can be counted as ordinary operands. Handler materialization must be deferred until the complete consumed-slot mask is known.
- `exportActivation` is still the legacy implementation. It rejects all finally states and handlers and calls removed untyped loop accessors, so the shared source cannot compile until it is replaced by the exact structural inverse.
- Required next action: defer imported handler-depth synthesis, implement exact base-to-top raw slot export for every structural entry (including FIN_EXIT reverse mapping and TYPE_CLEAR map exhaustion), validate complete handler/finally/loop consumption, then run the focused Debian/WSL compile and test gates.
- No external blocker is known. This note remains untracked and must not be staged, committed, or pushed.

### Structural scheduler compile checkpoint

- `PublicationScheduler` now has a complete typed structural importer and exporter. Import defers handler operand-depth synthesis until every structural raw slot is consumed, preventing nested outer controls from being counted as ordinary operands.
- Export resolves the same `StructuralStackShape`, emits exact catch selectors/labels/markers, protected-finally markers, typed running-finally reason/payload pairs, collection/range loop state, map `TYPE_CLEAR`, and reverse-mapped FIN_EXIT pairs. It validates every handler/finally/loop state is claimed and fills only the remaining ordinary raw slots.
- The first real compile found only one Error Prone warning in scheduler FIN_EXIT import; passing the resolver its declared long arguments fixed it. Subsequent `compileJava` passed the scheduler and whole main source.
- VM-owned serializer/test warnings were corrected by that subagent. Exact Debian Java 25 `compileJava compileTestJava` is now `BUILD SUCCESSFUL in 37s`.
- The layout subagent's exact `ToastV17ProgramLayoutTest` gate passed in 11s and its combined owned regression gate is currently running. Gradle remains serialized; the VM gate will run after layout reports completion.
- Existing scheduler fixture assertions were migrated from removed untyped loop accessors to exact `CollectionLoop` base/cursor assertions.
- Next action: add and run scheduler raw import/export/reload matrix cases using the stable structural source forms already proved by layout tests, then run the focused combined scheduler/VM/codec gates and inspect the scoped diff. No representation blocker is known.

### Full structural matrix and broad-gate checkpoint

- `PublicationSchedulerTest` now contains exact raw import/export cases for mixed protected catch/finally/loop interleaving, statement except and expression fallback phases, finally reasons 0/1/2/3/5 plus reason-4 rejection, list/string length-plus-one cursors, map key and `TYPE_CLEAR` exhaustion, integer/object maximum range values, and nested loops.
- The first scheduler run's sole failure was the older interrupted-task test's fake hardcoded Toast handler label `1`; the exact importer correctly rejected it. That fixture now derives the exact raw catch stack from its source/layout. The full 28-test scheduler class then passed in 1m14s.
- The combined scheduler, registry, VM, codec, and layout gate passed in 1m40s. It includes the exact 16-task/22-frame Mongoose fixture import.
- The broader 337-test run found exactly two failures. `ArchitectureTest` reported 321 persistence-to-syntax dependency violations because the new layout compiler was placed in `moo.persistence` despite depending only on bytecode/syntax. `MooRuntimeTest.evalRuntimeErrorUnwindsIntoPersistedCallerExceptAndFinally` showed ordinary structured catches receiving the new four-field internal finally tuple instead of their established `{error, message}` value.
- The runtime correction restores default catch details to the original message-only list; `exceptionTuple` still normalizes to the exact four-field persisted finally payload at the finally boundary.
- The architecture correction in progress changes `ToastV17ProgramLayout` and its test package to `moo.bytecode`, updates scheduler imports, and will physically relocate both files to package-matching paths. The existing architecture rule already permits bytecode to depend on syntax, so no gate weakening is needed.
- Next action: complete the two file moves, rerun the two failed tests, rerun the full 337-test task, then compile and diff-check. No semantic blocker is known.

### Corrected named-path final gate checkpoint

- The attempted layout relocation was rejected because the controlling plan immutably names `src/main/java/moo/persistence/ToastV17ProgramLayout.java`. The in-flight relocated-path run was discarded as authority, and both production and test files were restored to their exact `moo.persistence` paths/packages.
- `ArchitectureTest` now permits syntax in the persistence package's broad allowed set only alongside a second narrow prohibition: every persistence class except `ToastV17ProgramLayout` and its nested classes remains forbidden from depending on `moo.syntax`. The package cycle gate is unchanged.
- The exact corrected targeted gate passed in 1m18s: complete `ArchitectureTest`, `MooRuntimeTest.evalRuntimeErrorUnwindsIntoPersistedCallerExceptAndFinally`, and `moo.persistence.ToastV17ProgramLayoutTest`.
- The full 337-test Gradle `test` task passed in 2m7s on the exact named paths. The final Debian Java 25 `compileJava compileTestJava` gate passed in 1s with both tasks up to date.
- `git diff --check` is clean. No files were staged or committed by this subtask, and all `notes-*.md` files remain untracked.
- Next action: inspect the exact scoped diff/path list for stale package references or accidental artifacts, report the completed scheduler/VM/layout slice and gate evidence to the parent, and leave commit ownership to the parent.
