# Phase 3 index/range handoff

- Prior commits: `115aac4 feat: add phase 3 while control flow`; `496d424 feat: add phase 3 for ranges`.
- Active exact family: `src/moo_conformance/_tests/language/index_and_range.yaml`.
- Toast stock authority: 129 passed, one explicit `decompile_with_index_operators` skip.
- Banteng stock baseline: 126 passed, three failed, the same one skip.
- Exact failures:
  - `dollar_list_nested_in_function_call`: `{1, 2, 3, 4, 5, 6, 7}[max($, 1)]` returns `E_VERBNF` because `max` is absent from `BuiltinCatalog`.
  - `dollar_list_nested_in_random`: `t[random($)]` returns `E_VERBNF` because `random` is absent.
  - `disassemble_with_index_operators`: setup succeeds through `create`, `add_verb`, and `set_verb_code`, then `disassemble` returns `E_VERBNF` because it is absent.
- The existing compiler emits `ENTER_INDEX` before compiling the nested call argument, so `$` still has the indexed collection context. The first failure is not evidence of an index-context bug.
- Active tracked slice: pure numeric `max` in `BuiltinCatalog` plus its focused catalog test and manifest expectation.
- Focused red: `catalog.spec("max")` was absent and manifest completeness failed. Focused green: integer/float maximum selection, mixed-type `E_TYPE`, and manifest completeness all pass under WSL/Java 25.
- `max` uses the existing `ArgType.NUMBER`, requires at least one argument, accepts numeric variadics, and enforces homogeneous integer or float arguments without promotion.
- Managed result after `max`: 127 passed, two failed, one skip; `dollar_list_nested_in_function_call` now passes with no regression. Full `./gradlew check` passes and `git diff --check` is clean.
- Commit `13d3f6a feat: add phase 3 max builtin` closes the `max` slice.
- Remaining failures are exactly `dollar_list_nested_in_random` and `disassemble_with_index_operators`.
- Next action: inspect the existing Phase 2 scheduler/transaction PRNG ownership before starting `random`. The plan requires PRNG changes to publish in ticket order, so a host-global or thread-local RNG is not authorized. Add a focused red test only after identifying the exact production owner, then measure/commit or restore before `disassemble`.
- This note must remain untracked and unstaged.

## Random slice (active)

- The tracked worktree was clean at `13d3f6a` before this slice; untracked notes remain excluded from Git.
- The managed `index_and_range` baseline after `max` is 127 passed, 2 failed, 1 skipped. The remaining rows are `dollar_list_nested_in_random` and `disassemble_with_index_operators`.
- The exact random row evaluates `t[random($)]` for a seven-element list and requires a result in `1..7`.
- The plan requires PRNG changes to publish in ticket order. The existing VM suspends `IRREVOCABLE` builtins, and `PublicationScheduler` validates and authorizes them only when their ticket owns publication.
- The shared production `BuiltinCatalog` is therefore the existing RNG state owner; no adapter or alternate execution path is needed.
- Toastcore is clean at `~/src/toastcore`. Its database documentation states `random([mod])` uses positive inclusive integer bounds and raises `E_INVARG` for non-positive bounds; the conformance corpus also defines the two-argument inclusive range.
- A focused red test now requires the exact manifest contract, `IRREVOCABLE` VM ownership, inclusive one- and two-argument bounds, and invalid-bound errors. Production code has not yet been changed.
- Next action: run the focused test to establish red, then implement the catalog entry and RNG operation directly in `BuiltinCatalog`.

### Random implementation state

- The first focused invocation exposed only a Java generic-inference error in the new test table; after correcting it, the valid red report had 2 failures: the manifest expected 30 entries but had 29, and `spec("random")` was absent.
- Production now owns one `java.util.Random` instance directly in `BuiltinCatalog`. The `random` manifest entry accepts zero, one, or two integer arguments, is `ANY`, zero-cost, `IRREVOCABLE`, and VM-owned.
- `random()` uses `1..Long.MAX_VALUE`; `random(max)` uses `1..max` and rejects non-positive maxima; `random(min,max)` uses inclusive bounds and rejects reversed ranges. The inclusive range calculation handles overflow by rejection sampling.
- `JAVA_HOME=/opt/java/25 ./gradlew test --tests moo.builtin.BuiltinCatalogTest` completed `BUILD SUCCESSFUL` in 51 seconds. The tool output stream detached before completion, but the active daemon log recorded the exact successful build; no second run was started concurrently.
- Next action: run the exact managed `language/index_and_range.yaml` family and require improvement from 127 passed / 2 failed / 1 skipped to 128 passed / 1 failed / 1 skipped before keeping this slice.

### Random keep evidence

- Exact managed command: `scripts/run_managed_wsl.sh banteng stock src/moo_conformance/_tests/language/index_and_range.yaml`.
- Managed result: 128 passed, 1 failed, 1 skipped in 8.72 seconds. `dollar_list_nested_in_random` now passes. The sole failure is the untouched `disassemble_with_index_operators` row; the explicit `decompile_with_index_operators` skip still matches Toast.
- The Phase 3 plan was reread after the managed pass. This remains an end-to-end managed-socket slice through the production scheduler, VM, and `WorldTxn`; Phase 3 is not complete.
- `JAVA_HOME=/opt/java/25 ./gradlew check` completed successfully. Its wrapper output detached, and the Gradle daemon recorded a successful build result at 23:46:47. `git diff --check` is clean.
- Next action: review, stage, and commit only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`, leaving every `notes-*.md` file untracked. Then begin the separate `disassemble` slice in the same exact family.

## Disassemble slice (active)

- The random slice is committed as `50bfa79 feat: add ordered random builtin`; the tracked ledger was clean before this slice.
- The only managed-family failure is `disassemble_with_index_operators`: it creates a local `foobar` verb with source `return "foobar"[^..$];`, calls `disassemble(o, "foobar")`, and requires at least one returned list element matching `FIRST`.
- Two read-only Codex subagents were used. Both stopped when their mandatory notes hook conflicted with the explicit no-edit assignment; they changed no files. Their partial ownership findings agree with the direct audit.
- Toast primary authority is `C:\Users\Q\src\toaststunt\src\disassemble.cc:460-502` plus `verbs.cc:216-236`: exactly two `TYPE_ANY` arguments; non-object and invalid descriptor types return `E_TYPE`; non-positive integer descriptors and invalid objects return `E_INVARG`; only verbs defined on the target object are selected; absent verbs return `E_VERBNF`; owner/read-bit/wizard authorization applies; success is a LIST of disassembly-line strings.
- The generated and hand-authored conformance suites confirm the same call shape and error surface. The active row asserts only LIST matching behavior, but the handler can preserve the complete known Toast contract within the same builtin slice.
- Existing Banteng owners are sufficient: `WorldTxn.object/verb`, immutable `WorldVerb`, `MooCompiler.compile`, and `BytecodeProgram.disassemble`. Architecture rules explicitly allow `moo.builtin` to depend on `moo.bytecode`, `moo.syntax`, and `moo.world`.
- No new interface, adapter, result channel, or world method is needed. Named lookup can use existing non-executable `WorldTxn.verb` and accept the result only when the returned verb is the same instance as one in the target object's local verb list; integer descriptors map from Toast's 1-based number to the existing zero-based lookup.
- Next action: add a focused `BuiltinCatalogTest` covering the manifest contract, FIRST/LAST list output, descriptor/object errors, local-not-inherited lookup, and owner/read/wizard permissions; establish red before production changes.

### Disassemble implementation state

- The focused test is valid red: 14 tests ran with two failures, exactly the absent `disassemble` manifest entry and the manifest count short by one.
- The production edit is confined to `BuiltinCatalog`: manifest entry `ANY, ANY`, `TRANSACTION_READ`, world-owned; explicit Toast-order validation; 1-based integer or string local definition selection; owner/read/wizard authorization; compile stored source and return deterministic disassembly lines as MOO strings.
- The first green attempt failed before tests because Error Prone rejects reference equality. The local-definition filter now uses `target.verbs().contains(candidate)`. Because `WorldVerb` record equality includes the matching names and all remaining fields, an equal local record is itself a valid local definition; this preserves the intended semantics without a new world helper.
- The corrected focused test invocation is still running within the established WSL Gradle baseline. No result has been claimed yet.
- Next action: read the completed focused report. If green, reread Phase 3 and run the exact managed family; if red, change only the evidenced failure.

### Managed disassemble dependency correction

- The corrected focused test is green: 14 tests, 0 failures, 0 errors. Phase 3 was reread immediately afterward.
- The exact managed `index_and_range` family still reports 128 passed, 1 failed, 1 skipped; the disassemble row returns `E_VERBNF`.
- The generated `disassemble` signature suite passes 3/3 under the managed Banteng server, proving the installed manifest contains the new builtin and exact arity. `javap` on the installed jar also confirms the production handler is present.
- The `E_VERBNF` is not yet proven to come from the handler. The same row first invokes `add_verb` and `set_verb_code`, and neither name exists in the source or installed `BuiltinCatalog` manifest. The harness reports only the unhandled MOO error, not which call raised it.
- Therefore the earlier statement that setup had succeeded was unsupported and is withdrawn. The controlling Phase 3 text explicitly requires every handler invoked by its exact paths, so these setup calls are same-row vertical dependencies rather than a reason to bypass the row.
- Current tracked slice remains `BuiltinCatalog.java` and `BuiltinCatalogTest.java`; it is not keepable or committable until the managed row improves.
- Next action: run the checked-in generated `add_verb` signature suite. If it returns `E_VERBNF`, implement `add_verb` as the next missing dependency within this same row slice before testing `set_verb_code`; if it passes, inspect the handler result instead.

### Add-verb dependency state

- The generated managed `add_verb` signature suite fails 5/5. `function_info("add_verb")` returns `E_INVARG`; all direct calls return `E_VERBNF`. This proves it is the first missing setup dependency in the active row.
- Toast source registration is `TYPE_ANY, TYPE_LIST, TYPE_LIST`. `validate_verb_info` requires `{valid owner object, permission string using r/w/x/d only, nonblank names}`. `validate_verb_args` requires three strings with direct/indirect values `none|any|this` and a preposition; the active row uses `{"this", "none", "this"}`.
- Banteng already has the exact mutation owner: `WorldTxn.addVerb` returns the one-based new verb number and stores names, owner, permissions, preposition, and empty source. Direct/indirect specs are already encoded in permission bits 4-5 and 6-7; Toast enum values are none=0, any=1, this=2.
- A focused red test now requires the manifest contract and active-row success shape, including encoded `xd`, `this/none/this`, and the staged local `WorldVerb`. Production `add_verb` has not yet been added.
- Next action: read the focused red report, then add the direct manifest handler if the failures are exactly the missing spec/count.

### Set-verb-code dependency state

- The focused catalog class passes 15/15 after the direct `add_verb` handler was added.
- The active managed family still returns `E_VERBNF` on the disassemble row. The generated `add_verb` suite then passed 5/5, proving that dependency is installed and complete at its source-derived signature surface.
- The generated `set_verb_code` suite fails 4/4: `function_info` returns `E_INVARG` and direct calls return `E_VERBNF`. This proves the next missing active-row dependency.
- Toast registers `set_verb_code(TYPE_ANY, TYPE_ANY, TYPE_LIST)`. It validates every code element as a string, object and descriptor types/validity, a local defined verb, programmer plus verb-write permission, compiles the joined lines, stores only on successful parse, and returns a list of compile errors (empty on success).
- Banteng already has `WorldTxn.setVerbCode`, `MooCompiler`, local verb records, and permission bits. No new result channel or world abstraction is required.
- A focused test now requires the exact manifest contract, empty-list success, valid compilation of the index/range source, and staged source replacement. It has not yet been run.
- Next action: run the focused test for a valid red. If it fails only on missing spec/count, add the direct setter handler and rerun.

### Disassemble slice keep evidence

- The focused `BuiltinCatalogTest` passes 16/16 after adding the direct `set_verb_code` handler.
- The generated managed `set_verb_code` suite established the missing dependency before implementation; the existing `WorldTxn.setVerbCode` remains the mutation owner.
- Exact managed command: `scripts/run_managed_wsl.sh banteng stock src/moo_conformance/_tests/language/index_and_range.yaml`.
- Managed result: 129 passed, 1 skipped in 7.33 seconds. `disassemble_with_index_operators` now passes; the only remaining row is Toast's explicit `decompile_with_index_operators` skip.
- The plan was reread after the substantial managed pass. `add_verb`, `set_verb_code`, and `disassemble` are all handlers invoked by this exact Phase 3 managed-socket row, so they form one vertical slice.
- Exact plan gate `JAVA_HOME=/opt/java/25 ./gradlew check` completed `BUILD SUCCESSFUL` in 32 seconds. The separate `git diff --check` gate is clean.
- Next action: inspect and commit only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`, leaving this note and every other `notes-*.md` file untracked. Then advance to the next exact Phase 3 suite path, `language/looping.yaml`.
