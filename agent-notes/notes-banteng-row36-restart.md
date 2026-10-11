# Banteng row 36 restart state

## Prior slice ledger

- Row 35 was committed in Banteng as `bffaccf feat: isolate task local state across forks`.
- Separate Barn row-35 spec correction is `b06e048 docs: correct task local default`.
- Banteng is `master...origin/master [ahead 25]` with no tracked modifications after commit; only known notes and `pyghidra_mcp_projects/` remain untracked.
- Row-35 gates: exact managed pass 3.62 seconds; family first 14 rows passed then row 36 failed; full 148-test Gradle gate passed in 15 seconds; exact/family PIDs cleaned.

## Active target

- Exact row: `audit_waif_callers_preserve_this_and_verb_location` in `gap_followups_toast_oracle.yaml`, immediately after row 35.
- Setup creates a child of `$waif`, installs `:audit_a` returning `callers()`, `:audit_b` calling `this:audit_a()`, and `:audit_c` calling `this:audit_b()` and checking two caller frames.
- It creates `waif = obj:new()`, calls `waif:audit_c()`, recycles the class object, and expects:
  - caller 1 verb name `:audit_b`, `this` type WAIF, verb location equal to `this.class`;
  - caller 2 verb name `:audit_c`, `this` type WAIF, verb location equal to `this.class`.
- Current family result is `E_VERBNF`; the first actual missing surface has not yet been traced.

## Current blocker and next exact step

- Row-36 mandatory authority gate has only the durable row read completed. No Barn spec, Barn implementation, Toast implementation, oracle, evidence section, design, regression, or production edit exists yet.
- Next exact step is parallel read-only Barn/Toast/Banteng tracing, with the main process reading the normative Barn WAIF/callers sections. Do not infer that the `E_VERBNF` is `callers()` until the current Banteng path proves it.

## Hygiene

- Never stage this file, any `notes-*.md`, subagent checkpoint notes, or `pyghidra_mcp_projects/`.
- Never touch Ward.

## Initial source-trace checkpoint

- Barn and pinned Toast agree on the exact row: WAIF method lookup uses the WAIF class and colon-prefixed verb name, but the activation/runtime `this` remains the concrete WAIF; caller frame `verb_loc` is the defining class object. `callers()` excludes current `:audit_a` and returns prior `:audit_b` then `:audit_c` newest-first.
- Barn exact owners: `vm/op_verb.go`, `builtins/objects_misc.go`, `types/waif.go`, `task/task.go`, `builtins/tasks.go`, and `vm/op_property.go`. Toast exact owners: `src/waif.cc`, `src/execute.cc`, and `src/utils.cc`.
- Normative Barn generic frame shape is in `spec/tasks.md:318-343`; WAIF class inheritance is in `spec/objects.md:428-454`; `spec/types.md:223-237` and `spec/builtins/objects.md:542-555` describe creation. None explicitly states the cross-section rule “WAIF `this`, class/definer `verb_loc`”; the durable row supplies it.
- Banteng first actual `E_VERBNF` is not `callers()`: stock `$waif:new` resolves and begins `set_task_perms(caller_perms()); ...`; missing `caller_perms()` fails first. `new_waif()` is never reached.
- Later row-required gaps are confirmed: no `caller_perms`, `new_waif`, or `callers` catalog cases; no WAIF value/type; CALL_VERB accepts ObjectValue only; frames lack defining-location and WAIF receiver metadata.

## Full primitive gate

- Because row 36 would introduce Banteng's first WAIF value, the immutable plan requires the full primitive-WAIF authority gate before choosing a Java representation. This is still the same target, not a switch.
- Extended Barn and Toast subagent traces are in progress for class/owner/identity, construction, equality/order/hash/truth, conversions/type code/literal formatting, property/index mutation/copy behavior, permission/errors, and v17 serialization/deserialization.
- Main-process normative reads so far:
  - `spec/types.md:223-237`: prototype-based, garbage-collected WAIF, `.:` properties; general truth table makes every non-empty primitive including WAIF true.
  - `spec/database.md:90-111`: DB tag 13; WAIF creation record `c <index>` with class, owner, non-clear indexed properties, `-1`, `.`; later refs `r <index>`, `.`.
  - `spec/operators.md:841-849`: sparse `.:` access statement only.
  - `spec/builtins/objects.md:542-563`: sparse `new_waif()`/`waif_stats` documentation.
- Current Banteng `MooValue` is sealed to integer/float/string/object/error/list/map and Type lacks tag 13. The only persistence production file is `LambdaMooV4Reader.java`; no v17 writer exists under `moo.persistence`, so the row's ephemeral WAIF slice and the plan's eventual durable persistence requirement must be distinguished explicitly in the evidence/design.
- No row-36 tracked evidence section, oracle run, Barn spec correction, Java design, regression, or production edit exists yet. The active blocker is completion of the mandatory full WAIF authority record, not code.

## Restart re-establishment

- The new Codex process fully reread `docs/reports/banteng-implementation-plan.md`; the immutable plan remains unchanged and row 36 remains the only active exact-convergence target.
- Banteng is on `master` at committed row-35 tip `bffaccf`. There are no tracked modifications; only the known untracked notes and `pyghidra_mcp_projects/` are present.
- `moo-conformance-tests` is on `main` with no tracked modifications and extensive pre-existing untracked material. Any durable WAIF coverage edit must be staged by exact tracked path only.
- The established tracked slice-evidence owner is `docs/reports/splice-computed-access-authority.md`. Its existing tail records the complete row-35 authority/design/red/green/gates and stops immediately before row 36; the row-36 section has not yet been written.
- The exact row body at `gap_followups_toast_oracle.yaml:854` was reread in full. It creates a WAIF class and three colon methods, calls `obj:new()`, and requires `callers()` to preserve the concrete WAIF as `this` and the class as `verb_location` in the `:audit_b` and `:audit_c` frames.
- A narrow filename inventory found WAIF coverage only in `basic/types.yaml`, `audit/gap_followups_toast_oracle.yaml`, `builtins/builtin_presence_core.yaml`, `language/waif.yaml`, `builtins/recycle.yaml`, `generated_builtins/new_waif.yaml`, `builtins/waif_stats_call_shapes.yaml`, and `generated_builtins/waif_stats.yaml` (plus an untracked backup that is not authority).
- A Codex subagent is performing the bounded read-only durable coverage inventory for each disputed primitive-WAIF semantic. Until that inventory returns, the exact missing durable rows are the current blocker to completing authority-gate steps 4 and 5.

## Authority-gate step 4 and durable coverage checkpoint

- `docs/reports/splice-computed-access-authority.md` now has a tracked `WAIF values and caller frames` section. It records the exact row, normative Barn sections, current Barn owners, pinned Toast owners, agreements, disagreements, existing coverage, and the missing oracle rows. This is authority-gate step 4 only; it explicitly does not choose a Java representation.
- Current WSL source identity was reverified as `aecc51e9449c6e7c95272f0f044b5ba38948459e`, matching Barn's stock profile and plan. The only running Toast process remains unrelated July 13 PID 19 on `/tmp/td.db`; it was not touched.
- Git blame proves the exact row was introduced by conformance commit `64e3ba3d` on 2026-05-05.
- The completed conformance inventory found existing durable coverage for the exact caller metadata, type constant/runtime typeof, call shape and anonymous construction error, owner/class reads, protected intrinsic writes, class invalidation, inherited colon properties, one recursive two-WAIF cycle, and task metadata.
- Missing durable coverage is: false truth; exact `tostr`/`toliteral`; alias/distinct identity; relational/sort errors; WAIF map keys; recycled-class construction; `.class` write plus `.wizard`/`.programmer` reads; alias-shared mutation; direct WAIF_DICT dispatch/error cases; and v17 restart/backreference identity. Existing nested-map rows are not WAIF_DICT, `is_clear_property` only proves E_INVARG, and the mislabeled recycle row does not construct a WAIF.
- Managed restart is a real harness operation (`dump_database()` plus `restart_server`), so v17 WAIF alias identity can and must be a durable row.
- Pinned Toast `execute.cc` freezes WAIF_DICT: `waif[key]` calls `:_index(KEY)` and `waif[key] = value` calls `:_set_index(KEY, VALUE)`, both with the concrete WAIF as `this`; invalid class is E_INVIND, nonwizard class owner is E_TYPE, and missing handler E_VERBNF is translated to E_TYPE.
- There is no user-expressible WAIF local clear value. Existing `clear_property`/`is_clear_property` rejection is the observable contract; no invented clear syntax will be added.
- The conformance authoring subagent plans one new tracked `language/waif_authority.yaml` plus one WAIF restart row in `server/dump_persistence.yaml`. At this checkpoint `git status` showed neither tracked edit yet. The active blocker remains completion and review of that one test slice before any live oracle run.

## First managed WAIF authority run

- The subagent completed two new untracked candidate files: `language/waif_authority.yaml` with 13 schema-collected language rows and `server/waif_dump_persistence.yaml` with one managed dump/restart alias row. The general `dump_persistence.yaml` remained unchanged. `git diff --check` and collection of all 14 candidates passed.
- Main-process review confirmed the rows use actual WAIF operations rather than nearby substitutes. The persistence row stores `{class, waif, waif}`, dumps, restarts, mutates through one alias, and checks both reference equality and shared mutation through the other alias.
- The managed pinned Toast run selected the 14 new cases plus the original caller row (15 total). The original caller row, false-truth row, exact-format row, and identity-equality row passed.
- The run then stopped on `waif_relational_comparisons_and_sort_reject_values`: expected `{E_TYPE, E_TYPE, E_TYPE, E_TYPE, E_TYPE}`, actual `{E_NONE, E_NONE, E_NONE, E_NONE, E_TYPE}`. This proves the earlier premise that relational WAIF operators reject values was wrong; only `sort()` rejected the WAIFs in this probe.
- No candidate conformance row is accepted or committed. The immediate blocker is correcting the relational row to assert the exact Toast boolean outcomes, then rerunning the unchanged 15-row managed authority slice. Managed Toast process inventory has not yet been rechecked after this failed run and must be checked before the correction run.

## Relational correction and WAIF_DICT blocker

- Post-failure WSL inventory showed only unrelated PID 19; the managed run leaked no Toast process.
- Exact pinned source showed the earlier interpretation error: relational opcodes do not call the generic WAIF comparator. Same-typed WAIFs fall through the opcode switch default to `comparison = 0`, so `<`, `<=`, `>`, `>=` yield `{0, 1, 0, 1}`. `sort()` separately rejects WAIF values with E_TYPE.
- The durable candidate row was corrected to assert `{0, 1, 0, 1, E_TYPE}` directly. Its focused managed Toast run passed: one selected, 11,518 deselected, 3.51 seconds.
- The broader 15-row managed run then passed the original callers row and the first nine language-authority rows through recursive containment. It stopped on `wizard_owned_waif_class_dispatches_index_handlers`, which unexpectedly returned top-level E_TYPE instead of the handler result.
- Another post-run WSL inventory again showed only unrelated PID 19.
- Pinned source still proves WAIF_DICT is compiled and initializes `:_index`/`:_set_index`; it gates calls on `is_wizard(db_object_owner(class))`, translates missing handler E_VERBNF to E_TYPE, and uses the argument shapes already recorded. Therefore the candidate success row's setup or class ownership is invalid until isolated; source presence alone does not validate the row.
- The authoring subagent is performing a read-only diagnosis of the first failing expression and bundled Test.db/harness permission semantics. No WAIF_DICT candidate is accepted, no conformance commit exists, and Banteng Java design remains blocked.

## WAIF_DICT row diagnosis

- The first diagnostic proved the class owner is the Wizard and `_set_index` dispatch succeeds, while the following read failed. A temporary direct-call probe was itself invalid and was removed.
- A second attempt changed `_set_index` to return integer zero and then produced top-level E_TYPE before the diagnostic result could be returned.
- The subagent traced the exact cause in pinned Toast/compiler semantics: indexed assignment calls `:_set_index(KEY, VALUE)` and then writes that handler's return value back into the indexed base local. Returning 42 or 0 therefore rebounded local `waif` to that scalar; the next `waif[...]` or `waif.class` expression correctly raised E_TYPE.
- This rules out the class-ownership, compile-option, handler-name, and direct-read hypotheses. The fixture Wizard is #3, `create($waif)` is Wizard-owned, and the pinned executable contains the WAIF_DICT handler strings.
- The smallest valid correction is for `:_set_index` to return `this`, preserving the concrete WAIF through indexed-assignment writeback. The focused row must then prove the write and subsequent `:_index` result. No invalid WAIF_DICT behavior is accepted or committed yet.

## Toast-frozen conformance authority

- After changing only `_set_index` to return `this`, the focused WAIF_DICT success row passed pinned Toast: one selected, 11,518 deselected, 3.48 seconds.
- The complete managed authority selection then passed all 15 rows in 7.10 seconds: the original callers row, all 13 new language WAIF rows, and the managed v17 dump/restart backreference row.
- The 13 language rows freeze false truth, exact string/literal forms, identity equality, Toast's default-zero relational results plus sort E_TYPE, WAIF map keys, recycled-class construction E_INVIND, intrinsic reads/write denial, shared alias mutation, recursive containment, and WAIF_DICT success/error behavior including assignment writeback.
- The restart row proves a list containing `{class, waif, waif}` preserves the same WAIF identity, shared property mutation, and class reference after dump/restart.
- Post-run WSL inventory showed only unrelated PID 19. The immutable Banteng plan was then reread fully as required.
- The two new conformance files were staged by exact path, reviewed, passed staged `git diff --check`, and committed separately in `moo-conformance-tests` as `e790d04 test: freeze Toast WAIF semantics`. No unrelated paths were staged.

## Barn spec correction boundary

- The plan requires corrected normative Barn spec before Java representation. Barn is on `master`; the only pre-existing tracked modification is `notes-barn-performance-campaign.md`, which must not be staged. The large untracked workspace is also out of scope.
- Exact reads confirm directly contradicted WAIF passages in `spec/types.md`, `spec/operators.md`, and `spec/builtins/types.md`, plus obsolete `.:` examples in `spec/objects.md` and `spec/builtins/objects.md`.
- `spec/database.md` already correctly documents tag 13 and creation/reference backrecords. `spec/builtins/maps.md` already correctly allows WAIF keys by reference; current Barn implementation's rejection is an implementation bug, not a spec correction.
- A read-only Barn authority agent is finalizing the minimal correction list. No Barn spec file has been edited yet.

## Barn authority committed

- Six normative Barn files were corrected against committed conformance authority `e790d04`: `spec/types.md`, `spec/operators.md`, `spec/builtins/types.md`, `spec/objects.md`, `spec/builtins/objects.md`, and `spec/database.md`.
- Corrections include WAIF falsiness, type code 13, exact `tostr`/`toliteral`, identity/alias mutation, ordinary dot syntax, relational `{0,1,0,1}` behavior plus sort E_TYPE, construction/intrinsics/recursion, map-key authority, WAIF_DICT dispatch and `_set_index` writeback, and v17 alias identity.
- Contradiction search found no remaining WAIF code-12 or `waif.:` text in the six-file scope. Exact diff check passed.
- Only those six files were staged and committed in Barn as `c87229f docs: correct WAIF semantics against Toast`. Pre-existing `notes-barn-performance-campaign.md` remains modified and unstaged; no untracked Barn material was touched.

## Banteng design gate after authority

- `docs/reports/splice-computed-access-authority.md` is still modified with the pre-oracle row-36 evidence section. It contains now-invalid statements that WAIF relational operators raise E_TYPE and that the authority rows remain missing; those must be corrected before the evidence commit.
- Current `MooValue` is one sealed interface in `MooValue.java`, with its persisted `Type` enum nested in the same file. It permits only INT/FLOAT/STR/OBJ/ERR/LIST/MAP. There is no separate `Type.java`.
- The smallest value representation must therefore be a new permitted final nested `WaifValue`, not a record: immutable class and owner `ObjectValue` fields, default object identity equality/hash, false truth, type tag 13, and exact literal/string behavior. No wrapper/helper/interface is authorized.
- Existing `MapValue` rejects only LIST and MAP keys, so a default-identity `WaifValue` naturally satisfies the proven WAIF map-key contract without a map-owner change in this row.
- Existing builtin invocation takes name, arguments, `WorldTxn`, programmer, and task-local value. Current frame/runtime locals encode `this` as `ObjectValue`; current call/property paths and frames have no WAIF receiver or verb-location metadata, and the catalog lacks `caller_perms`, `new_waif`, and `callers`.
- The active blocker is updating and committing the tracked Banteng evidence/design record. No Java test or production file has been edited.
## 2026-07-15 Codex process restart continuation

- The user confirmed Codex was restarted; this is a new process continuing the same immutable-plan execution.
- Active slice remains row 36, `audit_waif_callers_preserve_this_and_verb_location`.
- Mandatory WAIF authority evidence and spec corrections remain committed (`e790d04`, `c87229f`, `b3368a4`); no row-36 Java production edit has been made yet.
- `/root/row34_banteng_trace` survived the restart and is still working only on the focused durable regression in `src/test/java/moo/runtime/MooRuntimeTest.java`.
- A targeted symbol search located the existing production paths needed for the smallest implementation: `MooVm` dispatches `GET_PROPERTY` at line 99, `SET_PROPERTY` at line 100, builtins through `invokeBuiltin` at line 107/417, object `CALL_VERB` at line 168, and verb-frame pushes at lines 165/200/494; `VmState.pushVerbFrame` begins at line 147.
- The next decision is whether these existing call/property/builtin paths can carry WAIF receiver and verb-location semantics without a new interface/helper/adapter. Exact method bodies still need to be read.
- Current blocker to production editing: the focused regression must first return and demonstrate the exact expected red (`caller_perms` missing as `E_VERBNF`).
- Ward remains out of scope and must not be touched. The immutable plan remains read-only.

## 2026-07-15 row-36 implementation attempt

- The focused regression returned as the sole tracked pre-production diff in `src/test/java/moo/runtime/MooRuntimeTest.java`. Its setup proves subclass creation, three verb installations, and inherited `new`; committed production failed the execution phase as `{2, {E_VERBNF}}`, isolating missing `caller_perms()`.
- Git accountability check before editing: branch `master`, ahead 26, only that test tracked-modified; all notes and `pyghidra_mcp_projects/` remain untracked and out of scope.
- The WAIF value subagent edited only `src/main/java/moo/value/MooValue.java`: permitted final identity `WaifValue`, type code 13, immutable class/owner, false truth, exact string/literal forms. It did not stage or commit.
- Root edited only the existing owners `VmState`, `MooVm`, and `BuiltinCatalog`: frames now retain a `MooValue` receiver and defining-object verb location; object calls retain their path; WAIF calls use the class plus colon-prefixed verb and concrete WAIF `this`; `.class` is recognized; the catalog receives current/previous frame metadata and implements `caller_perms`, `new_waif`, and `callers`; no new interface, adapter, helper owner, or alternate dispatcher was added.
- The first Java 25 focused run compiled all production/test sources but remained red. Exact actual result changed from `{2, {E_VERBNF}}` to `{2, {E_VARNF}}`.
- A targeted symbol search shows Banteng's existing predefined type-constant resolver in `MooVm` has branches for `INT`, `STR`, and `LIST` near lines 352-363, but no `WAIF`. `BuiltinCatalog.typeof` already returns the new enum code. The durable row's `typeof(...) == WAIF` is therefore the likely first remaining failure, but the exact resolver body must be read before patching.
- Current blocker to keeping/committing the slice: add only the missing authorized `WAIF` predefined constant in the existing resolver, rerun the exact focused regression, and reject/revert if it does not produce a measured green.

## 2026-07-15 row-36 focused and managed green

- Reading the exact `MooVm.loadLocal` body confirmed only `INT`, `STR`, and `LIST` predefined constants existed. One `WAIF` branch returning `MooValue.Type.WAIF.code()` was added in that existing resolver.
- The exact Java 25 focused regression then passed: `gradlew.bat test --tests moo.runtime.MooRuntimeTest.preservesWaifThisAndVerbLocationInCallers`, `BUILD SUCCESSFUL` in 6 seconds.
- As required after the substantial targeted pass, the immutable 407-line `docs/reports/banteng-implementation-plan.md` was reread completely in two read-only chunks. Phase 8 still requires the managed focused row, substantial targeted family, plan reread, full gates, and a committed kept slice before changing targets.
- Java 25 `gradlew.bat installDist` passed in 1 second.
- Managed Banteng `audit_waif_callers_preserve_this_and_verb_location` passed: 1 selected, 11,518 deselected, 3.66 seconds.
- The managed run left PID 390068 with exact temp database `C:/Users/Q/AppData/Local/Temp/moo_conformance_hkij6xjo/Test.db`; that exact process was stopped. Follow-up Banteng JVM inventory was empty.
- The next durable row in the same `gap_followups_toast_oracle.yaml` family is `audit_move_wizard_calls_accept_but_overrides_false` at line 884. It is the next unchecked target after the now-green WAIF row.
- Current blocker to committing row 36: run the substantial managed family prefix through the next row, preserve row 36 only if the prefix remains green and the first stop is that next independent row, then run the final Java 25 gate and record receipts.

## 2026-07-15 row-36 broader and Java gates

- The substantial managed `gap_followups_toast_oracle` fail-fast run selected 17 rows. It passed 15 rows, including the WAIF row, and stopped exactly at the next independent row `audit_move_wizard_calls_accept_but_overrides_false`: expected `[1, 1]`, actual `[0, 1]`, 11,502 deselected, 29.79 seconds.
- The family run's exact disposable Banteng JVM was PID 339748 using temp database `C:/Users/Q/AppData/Local/Temp/moo_conformance_5jxj0qza/Test.db`; it was stopped and the follow-up Banteng inventory was empty.
- The immutable 407-line plan was reread completely again after the substantial category run. Row 36 remains keepable and must be committed before the move row begins.
- First Java 25 `clean check installDist` attempt failed only on two row-local integration gates: Spotless wrapping in edited `BuiltinCatalog`/`VmState`, and `MooValueTest.familyIsClosedOverExactlyTheSevenAuthorizedValues` still expecting seven sealed variants and codes without WAIF.
- `MooValueTest` was updated to the exact eight authorized variants, including `WaifValue`, and type-code list `[0, 1, 2, 3, 4, 9, 10, 13]`. Repository `spotlessApply` passed and formatted the edited Java.
- The repeated Java 25 `clean check installDist` gate passed all 149 tests, formatting, checks, and application distribution: `BUILD SUCCESSFUL` in 28 seconds.
- Current blocker before the required row-36 commit: reread the immutable plan after this passing full suite, append the implementation verification receipt to `docs/reports/splice-computed-access-authority.md`, run `git diff --check`, review/stage only the intended tracked files, and commit the kept slice. Untracked notes and `pyghidra_mcp_projects/` remain excluded.

## 2026-07-15 row-36 precommit review finding

- The plan was reread completely after the passing full suite. The implementation receipt was appended to `docs/reports/splice-computed-access-authority.md`; tracked scope is exactly seven intended files and `git diff --check` is clean.
- A read-only subagent review found one concrete medium bug before commit: `VmState.ensureRoot()` currently infers a root frame's verb location from `this`. For an inherited initial stored verb, `MooRuntime.executeStored()` therefore causes `callers()` to report the receiver rather than the actual defining object. The focused WAIF row inspects only the first two pushed WAIF frames and does not expose the later root tuple.
- The existing pushed object/WAIF frames correctly retain concrete receiver and actual definer. No other bounded-diff finding was reported.
- Exact minimal correction path: keep lookup inline in existing `MooRuntime.executeStored` by walking from `locals.this` until the supplied `WorldVerb` is contained; pass that explicit object to a concrete `VmState` root constructor. Preserve it through `ForkRequest` and child-state construction because fork roots otherwise lose the same frame metadata. This requires no lookup helper, interface, adapter, or alternate dispatcher.
- No correction has been applied yet. Current blocker: add a focused red regression for inherited root stored-verb `callers()` location, prove the current diff fails that exact assertion, then patch only `VmState`/`MooRuntime`, rerun focused and full gates, reread the plan, update the receipt if timing/test count changes, and commit.

## 2026-07-15 inherited-root verb-location correction

- Added focused regression `MooRuntimeTest.recordsDefiningObjectForInheritedRootVerbCallerFrame`. It uses a child listener inheriting `do_login_command` from a parent; a nested inherited probe records `callers()[1][4]`.
- The regression was valid red on the pre-review-fix diff: expected defining parent `#9`, observed child receiver `#10`.
- `MooRuntime.executeStored` now walks the existing object ancestry inline from `locals.this` until the supplied `WorldVerb` is contained and passes that concrete defining object into `VmState`. No lookup helper or alternate dispatcher was added.
- `VmState` now accepts explicit initial root verb location. `ForkRequest` captures the current frame's verb location and the child `VmState` receives it, preventing the same metadata loss at fork-root reconstruction.
- The inherited-root focused regression passed on Java 25 in 8 seconds. After `spotlessApply`, both row-36 focused regressions passed together in 2 seconds.
- The Java 25 managed distribution was rebuilt successfully with `gradlew.bat installDist` in 1 second.
- Current blocker before commit: because production changed after earlier receipts, rerun the exact managed WAIF row, clean up its exact process, rerun the substantial family frontier, clean up, reread the plan, rerun full Java 25 `clean check installDist`, reread the plan again, refresh the receipt, review/stage only intended tracked files, and commit.

## 2026-07-15 refreshed row-36 gates after review fix

- Refreshed exact managed WAIF row passed: 1 selected, 11,518 deselected, 3.53 seconds. Its exact disposable process was PID 379608 using `moo_conformance_m5mygqnr/Test.db`; it was stopped and the follow-up inventory was empty.
- Refreshed substantial `gap_followups_toast_oracle` frontier again passed 15 rows and stopped at the next independent move row with expected `[1, 1]`, actual `[0, 1]`: 1 failed, 15 passed, 11,502 deselected, 29.84 seconds.
- That family run's exact process was PID 388460 using `moo_conformance_uqsnmrea/Test.db`; it was stopped and the follow-up inventory was empty.
- The immutable plan was reread completely after the refreshed category run.
- Refreshed Java 25 `clean check installDist` passed all 150 tests, formatting, checks, and application distribution in 16 seconds.
- The required post-full-suite plan reread has begun; lines 1-204 were reread before this checkpoint. Lines 205-407 remain to be reread before any receipt edit, staging, or commit.
- Current blocker: finish that exact second plan half, refresh the report's final test count/timings and inherited-root red/green receipt, run final diff checks/review, stage only intended tracked files, and commit row 36.

## 2026-07-15 final pre-stage correction and gate

- The durable report was refreshed with the inherited-root regression and post-review managed/full receipts.
- A final local review noticed the two-argument `VmState` constructor inferred `this` before local-name normalization, unlike the pre-slice behavior. It was corrected to normalize first and derive the fallback verb location from `initialLocals`; the managed runtime still uses the explicit three-argument definer constructor.
- The read-only subagent re-review reported no remaining findings. It confirmed the inherited-root regression covers the original bug and that fork request propagation preserves the current defining-object location.
- `spotlessApply` passed. The final Java 25 `clean check installDist` gate passed all 150 tests, formatting, checks, and application distribution in 23 seconds.
- The required plan reread after this final full suite has begun; lines 1-204 were reread before the checkpoint, and lines 205-407 remain.
- Current blocker: finish that exact plan second half, update the receipt's final gate duration from 16 to 23 seconds, run final `git diff --check` and tracked-scope review, stage the exact eight files, commit row 36, and verify the commit/worktree ledger.
## Restarted Codex delegated WAIF value slice

- Delegated scope: edit only `src/main/java/moo/value/MooValue.java`; do not stage or commit.
- Required frozen representation: add `WAIF` to the sealed variants and `Type.WAIF(13)`; add a final nested `WaifValue` class with final `ObjectValue` class and owner fields, identity equality/hash, false truthiness, `[[waif]]` string form, and `[[class = #N, owner = #N]]` literal form.
- No production action has been taken in this delegated turn yet.
- Verification required after the edit: narrow Java 25 compile/test plus a diff check proving only `MooValue.java` changed for this slice.
- Current blocker: none; the mandatory restart/checkpoint note update had to precede code inspection or editing.

### Delegated WAIF value slice result

- Edited only `src/main/java/moo/value/MooValue.java`: added the sealed permit, `Type.WAIF(13)`, and final identity-semantics `WaifValue` with immutable class/owner object references and the frozen truth/string/literal behavior.
- `git diff --check -- src/main/java/moo/value/MooValue.java` passed with no output.
- Java 25 direct source compile passed: `javac --release 25 -d build/tmp/row36-waif-value-check src/main/java/moo/value/MooValue.java` exited 0.
- Java 25 repository compile currently fails at the expected cross-file integration boundary: `BuiltinCatalog.java:379` has an exhaustive `MooValue` switch without a `WaifValue` arm. That file is outside this delegated scope and was not touched.
- Tracked changes now visible are the authorized `MooValue.java` slice plus the pre-existing/concurrent `MooRuntimeTest.java` regression owned by another agent. Nothing was staged or committed.
- Current blocker for this delegated slice: none. Full `compileJava` depends on the parent integration slice updating the exhaustive built-in switch.
## Row 36 tracked-diff review handoff (subagent)

- Assigned a read-only final review of the current tracked Banteng diff for row 36 WAIF caller semantics.
- Review authority is `docs/reports/splice-computed-access-authority.md`, limited to identity `WaifValue` type 13/false/exact formats; concrete receiver and defining object frame metadata; preservation of object behavior; WAIF colon lookup with concrete `this`; `.class`; `caller_perms`/`new_waif`/`callers` arity and context; focused and sealed-family tests.
- No tracked files have been edited, staged, committed, and no managed server has been run by this reviewer.
- Current blocker: none; the tracked diff and frozen authority still need to be inspected.
## Row 37 Toast authority subagent restart state (2026-07-15)

- Assigned read-only authority work for `audit_move_wizard_calls_accept_but_overrides_false`.
- Must verify pinned WSL Toast source identity `aecc51e9449c6e7c95272f0f044b5ba38948459e`, trace `move()` from registration through destination `:accept`, wizard override, mutation, permissions/errors, and run the exact managed Toast row with the pinned Barn profile/runner.
- Must not edit tracked repo files, stage, commit, or stop unrelated Toast PID 19. Only an exact process from this managed run may be cleaned up if needed.
- No investigation commands have run yet. Current blocker: none; the next decision-changing action is verification of the pinned source identity.

### Row 37 Toast authority findings

- `wsl -d Debian -u root -e git -C /root/src/toaststunt rev-parse HEAD` returned the required `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- The exact managed Toast row passed: `audit_move_wizard_calls_accept_but_overrides_false`, 1 passed and 11518 deselected in 3.62s (runner total 5.15s).
- Post-run WSL process inventory showed no leaked managed Toast process. Unrelated PID 19 remains exactly `/root/src/toaststunt/build-release/moo /tmp/td.db /tmp/td.db.new 9482` and was not touched.
- Source authority is `/root/src/toaststunt/src/objects.cc`: `bf_move_data` 111-114; `do_move` 116-210; `bf_move` 212-231. Initial checks are invalid object/destination/negative position -> E_INVARG (127-128), no control of moved object -> E_PERM (129-130), NOTHING/same destination auto-accept (131-132), positive position without destination control -> E_PERM (133-134). Otherwise it calls destination `accept` with moved object as sole argument (136-141); missing accept becomes false (142-145), recursion failure propagates (147-148). Returned accept truth is tested at 153-154. Only a nonwizard is rejected with E_NACC for false (156-158), so wizard false is deliberately overridden. Revalidation/no-op is 160-163; containment cycle E_RECMOVE is 165-168; actual mutation is `db_change_location` at 170-171, before exit/enter hooks. Exit/enter flow is 173-204.
- `bf_move` snapshots object, destination, optional position on first entry and resumes the state machine across verb calls (218-230).
- No blocker. Remaining read-only work is exact registration line numbering and, if needed for an unambiguous report, definitions of `controls`, `is_wizard`, `db_change_location`, and builtin registration type/arity contract. No Java design or repo code edit is authorized in this subtask.

### Row 37 additional exact evidence

- Registration is `objects.cc:1368-1370`: `move` uses `register_function_with_read_write`, arity 2..3, implementation `bf_move`, continuation serializers `bf_move_read`/`bf_move_write`, and argument types `TYPE_OBJ, TYPE_OBJ, TYPE_INT`.
- Exact YAML row is `moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:884-909`: as Wizard it creates `thing` and `dest`, installs `dest:accept` which records `args[1]` then returns `0`, calls `move(thing,dest)`, and expects `{accept_seen == thing, thing.location == dest}` to be `{1,1}`. This proves both that Wizard still invokes `:accept` and that false does not veto Wizard mutation.
- Search found `controls` locally at `objects.cc:36-38`: wizard or object owner. Exact `is_wizard` definition is at `db_objects.cc:1277`; not yet read. The source search itself changes the remaining ambiguity about the `controls` predicate but no tracked edit was made.
- Current blocker: none. Next exact read-only evidence is line-numbered `controls`/`is_wizard` and `db_change_location` definitions, followed by reporting to the parent.

### Row 37 completed source trace

- `objects.cc:35-39`: `controls(who,what)` is exactly `is_wizard(who) || who == db_object_owner(what)`.
- `db_objects.cc:1276-1280`: wizard means a valid object with `FLAG_WIZARD`.
- `objects.cc:47-57`: the argument list passed to `:accept` is exactly one object, the moved object.
- `execute.cc:641-674, 717-782`: `call_verb(where,"accept",where,args,0)` performs normal callable-verb lookup on valid destination, returns only E_INVIND/E_VERBNF/E_MAXREC/E_NONE, creates a verb activation with `this=where`, programmer=verb owner, receiver=where, actual verb definer, verb name `accept`, and supplied args. In this move path E_INVIND is ruled out by the prior destination validity check/source comment; E_VERBNF becomes false acceptance; E_MAXREC propagates.
- `db_objects.cc:1190-1225`: `db_change_location` removes the object from valid old-location contents, inserts it into valid destination contents at requested/default position, assigns its location, and records last-move time/source. This mutation is called from `objects.cc:170-171` before `exitfunc` and `enterfunc`.
- Exact oracle outcome and source agree with the conformance premise. No disagreement or unresolved ambiguity was found. The only deliberate special case is that `where == NOTHING` or same location bypasses `:accept` and is treated accepted; a nonzero position additionally requires control of destination. No managed process leaked; PID 19 is untouched.
- Row 37 Toast authority subtask is complete and unblocked. No tracked source, test, report, plan, staging, or commit action was performed; only this required untracked notes checkpoint was updated.
