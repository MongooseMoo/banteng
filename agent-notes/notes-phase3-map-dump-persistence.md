# Phase 3 map dump persistence

## Current state

- The immutable implementation plan is active at `docs/reports/banteng-implementation-plan.md`.
- Phase 3 is complete through `server/list_dump_persistence.yaml`.
- The current exact target is `server/map_dump_persistence.yaml`.
- The last kept source slice was committed as `fcd1524 feat: support nested phase 3 index writes`.

## Observations and findings

- The Toast managed oracle passed the exact map family: 1/1.
- Banteng failed during the first setup step, before checkpoint/restart, with `E_VERBNF`.
- The exact row requires `add_property`, `chr`, `create`, `delete_property`, `dump_database`, `encode_binary`, `equal`, `mapkeys`, `new_waif`, `recycle`, and `toliteral`.
- Setup creates a `$waif` child, calls `class:new()` twice, creates two anonymous objects, constructs maps with string/error/float/object/integer/boolean/waif/anonymous keys, records their pre-restart ordering and equality behavior, and stores all state on `#1`.
- The earlier full-file read was truncated; a bounded reread succeeded and exposed the complete row.

## What was tried

- Ran the exact Toast managed family; it passed.
- Reread the controlling Phase 3 plan section.
- Ran the exact Banteng managed family; it failed with `E_VERBNF` in setup.
- Read the exact YAML in a bounded chunk to identify every setup operation.

## What worked

- The Toast oracle establishes that the row and expected behavior are valid.
- The bounded YAML read establishes the exact operations that can account for the setup failure.

## What did not work

- Banteng cannot yet complete the setup step; no persistence behavior has been exercised by this family.
- The earlier unbounded YAML read produced no usable evidence because its output was truncated.

## Blocker

- No external blocker. The precise `E_VERBNF` source within the setup operations still must be isolated from current Banteng behavior.

## Next action

- Add a red complete-pipeline VM test proving that a verb receiver calling `new_waif()` produces a WAIF whose class is that receiver and whose owner is the active programmer. Then implement the exact builtin through the existing catalog context, run the targeted test, and rerun the exact managed family.

## Confirmed cause

- Git ledger verification found `master` with no tracked modifications; only the existing untracked notes and `pyghidra_mcp_projects/` are present.
- Toast and Banteng profile manifests point to the same `Test.db` path and checksum.
- The exact fixture body for `#7:new` is:
  `set_task_perms(caller_perms()); player = caller_perms(); return new_waif();`
- Current Banteng contains `set_task_perms` support but had no `new_waif` manifest entry. The earlier note incorrectly stated that current source also contained `caller_perms`; that was not established by the source search and must not be relied on.
- Toast source at `/root/src/toaststunt/src/waif.cc` registers `new_waif` with arity 0. It returns a WAIF whose class is `caller()` and whose owner is the current programmer; invalid caller yields `E_INVIND`, and an anonymous caller yields `E_INVARG`.
- Banteng's existing builtin invocation boundary already passes the active verb frame receiver and programmer into every handler. The fix therefore belongs in `BuiltinCatalog`; no new interface, helper, sender, or adapter is needed.

## Red-test execution state

- Added `MooVmTest.newWaifUsesCallingVerbReceiverAsClassAndProgrammerAsOwner`, which runs `#7:new()` through parser, compiler, verb dispatch, the exact fixture verb body, and builtin dispatch, then asserts WAIF class `#7` and owner `#1`.
- The first targeted command omitted the plan-required Java environment and exited before tests because WSL had no Java on `PATH`; it produced no test result.
- The corrected Java 25 command reached Gradle but remained in task-graph calculation for more than five minutes with no test result. Live process and daemon-log inspection showed the target wrapper owned the daemon rather than waiting behind another build.
- The execution backend could not send Ctrl-C, so only the exact targeted wrapper PID was terminated with `TERM`; the daemon and compiler workers were not killed.
- No production source has been changed. The tracked diff remains test-only, and the required red result is still outstanding.

## Immediate next action

- Inspect the current catalog and Toast source for `caller_perms`, because the exact `$waif:new` body calls it before `new_waif`. Confirm the uncaught error from the pipeline test, then complete this same WAIF-construction slice through the existing builtin context; do not start a different family or abstraction.

## Production attempt and current failure

- The second unchanged Java 25 red run completed after 12m36s and failed the new complete-pipeline test at the expected outcome assertion (`RETURNED` expected, `ERRORED` actual). This authorized the production edit.
- Added only a zero-argument `new_waif` manifest entry in `BuiltinCatalog`; it uses the existing receiver as class, current programmer as owner, existing world lookup for validity, and returns `E_INVIND` for invalid/non-object receivers.
- The post-edit targeted run reused the configuration cache, completed in 49s, and still failed with `ERRORED` at the same assertion. Therefore the full fixture verb body has another missing semantic before or around `new_waif`; the slice is not green and is not ready to commit.
- Read-only JVM diagnosis during the long red run showed Gradle was blocked in HTTPS metadata resolution. WSL could not reach `plugins.gradle.org` while Windows could, but the build eventually advanced, compiled, and produced the required red result. No WSL restart was performed.

## WAIF path completion

- Current source had no `caller_perms` entry. Toast source proves it is zero-argument and returns the previous activation's programmer, or `#-1` at top level.
- Added `caller_perms` inline to `BuiltinCatalog` using the existing `callerProgrammer` and `callers` context; no VM state field or helper was added.
- The complete-pipeline WAIF test then passed under Java 25 in 46 seconds.
- Reread the Phase 3 plan after that pass. It explicitly names `new_waif` as a Phase 3 vertical dependency, and the next unchecked managed path remains `server/map_dump_persistence.yaml`.

## Managed map result after WAIF fix

- The exact Banteng managed map family advanced from `E_VERBNF` to `E_ARGS` during the same setup step.
- Banteng's `create` manifest accepts exactly one object argument. The first later call with a different arity is `create($anonymous, 1)`, after both WAIF constructors, so it necessarily produces the observed `E_ARGS`.
- Toast source registers `create` with one to four arguments. With a second integer argument, that integer is the anonymous flag, the current programmer remains owner, and a distinct anonymous-object value is returned.
- The row uses two such anonymous values as map keys, persists them, restarts, and requires their identities and insertion order to survive.

## Anonymous vertical-slice inventory

- Current `MooValue` has no anonymous-object value/type.
- Current `WorldSnapshot` carries only players and ordinary objects.
- Current v17 codec writes/reads ordinary values, lists, maps, WAIF is also not yet handled in its value switch, and anonymous values are unsupported.
- The plan-required `AnonymousObjectPersistenceTest` does not exist.
- All six exact startup fixtures `Anon1.db` through `Anon6.db` are present in the conformance repository.
- This is therefore the plan's explicitly required anonymous-object value/world/v17 persistence slice, not a standalone arity patch.

## Next exact action

- Await the user's exact correction to the invalid `AnonymousObjectPersistenceTest` plan line before implementing anonymous persistence. Do not substitute a codec-only test or invent a fixture workflow.

## Controlling-plan blocker

- The Phase 3 plan says `AnonymousObjectPersistenceTest` loads “each of the six committed anonymous-object v4 fixtures,” writes v17, reloads, and compares every anonymous object/property/verb/reference.
- The exact committed files `Anon1.db` through `Anon6.db` all belong to the startup fixture set; inspection of `Anon1.db` proves its header is LambdaMOO format v17, not v4.
- `Anon1.db` contains no serialized anonymous object at load time. Its `#0:server_started` program creates anonymous garbage and shuts down; the anonymous state exists only after executing that startup program and producing a subsequent dump.
- Therefore the named unit-test procedure is not literally executable as written: direct codec load/write/reload has no anonymous object to compare, while executing the startup program is an unstated runtime workflow.
- Under the plan-validation and momentum rules, implementation must stop here rather than silently choosing either interpretation.

## Current uncommitted source slice

- `src/test/java/moo/vm/MooVmTest.java`: complete-pipeline WAIF constructor regression; currently passing.
- `src/main/java/moo/builtin/BuiltinCatalog.java`: `new_waif` and `caller_perms`; both are required by the proven fixture verb path.
- The exact managed map family now reaches `E_ARGS` at `create($anonymous, 1)`, proving the next required production capability is anonymous-object creation/persistence.
- These changes remain one incomplete map-family source slice and are not staged or committed.

## Required user decision

- Resolved by the user: v4 anonymous databases do not exist; the plan must use v17.

## Plan correction applied

- Verified `Anon1.db` through `Anon6.db` all have LambdaMOO v17 headers and all six contain a `#0:server_started` shutdown workflow.
- Updated `docs/reports/banteng-implementation-plan.md` so `AnonymousObjectPersistenceTest` starts the exact six v17 fixtures through the production runtime, waits for fixture-defined shutdown and the resulting v17 checkpoint, reloads it, and compares every anonymous object, property, verb, and reference.
- `git diff --check` passes for the plan file, and the corrected Phase 3 section was reread.

## Active implementation review

- Three read-only subagents were dispatched for exact Toast codec behavior, the smallest Banteng anonymous world/value model, and the six-fixture production test path.
- Toast source already proves anonymous references use type tag 12, receive temporary positive dump IDs on first reference, reuse those IDs on repeated references, and cause full anonymous object records to be emitted in later object batches before the terminating zero batch.
- Main-agent review is currently checking map-key ordering/equality because the managed row requires boolean, WAIF, and anonymous non-total topology to transform across restart.

## Final v17 plan repair

- Directly verified Toast's `test_canned_dbs.rb` lifecycle: boot counts are `Anon1=2`, `Anon2=2`, `Anon3=2`, `Anon4=3`, `Anon5=3`, and `Anon6=1`.
- Corrected the Phase 3 scope so v4 applies only to the legacy permanent-object bootstrap surface; full object/property/verb/WAIF/anonymous input and deterministic output are v17-only.
- Corrected `AnonymousObjectPersistenceTest` to use the exact per-fixture boot counts, feed each checkpoint into the next boot, compare pending roots and recursive alias/cycle topology, and freeze the Toast byte-stability/removal invariants.

## Parallel review results

- Native Codex subagents completed read-only reviews for the Toast codec, Banteng world model, and six fixture lifecycles.
- A programming subagent was attempted for the isolated value type, but the active native-subagent hook denied writes; no subagent source edit occurred. Main-thread implementation is required.
- Agreed model direction: type-12 anonymous values use reference identity; anonymous object bodies are concrete world state separate from permanent numeric objects; v17 read/write uses one checkpoint-wide identity table, later fixed-point anonymous batches, and shared WAIF identity tables; pending-finalization roots are required for `Anon6` and later fixture lifecycle work.

## Type-12 value slice

- Added a red `MooValueTest` for closed-family membership, code 12, false truth, `*anonymous*` literal, reference inequality, and two distinct anonymous map keys. The red run failed with seven missing-symbol errors as expected.
- Added `MooValue.AnonymousObjectValue` as an identity class and `Type.ANONYMOUS(12)`.
- Extended the existing `BuiltinCatalog` exhaustive switches for anonymous map-key rank/comparison and string rendering; no new abstraction was added.
- `JAVA_HOME=/opt/java/25 ./gradlew test --tests moo.value.MooValueTest` now passes in 1m25s.

## Next action

- Reread the corrected Phase 3 plan, then add a red `WorldTxnTest` for anonymous allocation/commit/rollback without permanent object-number or parent-children mutation. Implement the concrete anonymous world record/map directly in the existing transaction/history owner before touching v17 codec code.

## Anonymous world red-gate state

- Added `WorldTxnTest.anonymousAllocationCommitsAndRollsBackOutsidePermanentObjectTopology`, requiring transaction-local anonymous allocation, commit visibility, rollback removal, and no change to permanent object count, maximum object number, or parent children.
- No anonymous world production source has been added yet; the red test must run before that edit.
- The first targeted `WorldTxnTest` Gradle wrapper remained at task-graph calculation for more than ten minutes with no compiler output. Its exact wrapper PID was terminated after exceeding the prior 1m25s focused-test baseline by several multiples.
- A bounded rerun (`180s`, Java 25) also timed out at task-graph calculation. The daemon log contained no project compile/test activity and showed only client disconnection after the timeout.
- `./gradlew --stop` reported no registered daemons, and a direct process check then confirmed no Gradle daemon, worker, or wrapper processes remained.
- A second clean bounded rerun still timed out after 180 seconds at the same task-graph-calculation point. Therefore the required red result remains unavailable and production editing remains unauthorized.

## Current blocker and next action

- The blocker is the WSL Gradle gate hanging during configuration before compilation, not a project test failure.
- Next inspect the Gradle configuration-cache/report state and build configuration to identify the exact configuration operation that is hanging. Restore the same targeted test gate; do not infer the expected compile failure and do not edit production world state until that red result exists.

## Red gate recovered

- `gradle.properties` enables configuration caching globally. The repo-local configuration cache contains thousands of entries and incomplete temporary snapshots from timed-out runs.
- Running the same Java 25 targeted test with `--no-configuration-cache --offline` bypassed the pre-compilation hang and completed in 9 seconds.
- The required red result is now established: `compileTestJava` reports nine missing-symbol errors for `WorldAnonymousObject`, `WorldTxn.createAnonymousObject`, `WorldTxn.anonymousObject`, and `WorldSnapshot.anonymousObjects`.
- Git ledger immediately before production edits: `master` is ahead 166; the existing plan/value/builtin/VM/world test files are the tracked active map-family slice; notes and `pyghidra_mcp_projects/` remain untracked.

## Production model edits in progress

- Added concrete `WorldAnonymousObject` with name, flags, owner, parent, ordered verbs, and ordered properties. It is core world state, not a helper/interface/adapter.
- Extended internal `World` and public `WorldSnapshot` with an insertion-preserving map keyed by identity-based `AnonymousObjectValue`; retained three-argument constructors that default the anonymous map to empty for existing permanent-object callers.
- The active source slice is incomplete and untested after these edits. No commit is authorized yet.

## Immediate next action

- Thread the anonymous map through `WorldHistory` publication and `WorldTxn` allocation/read/replace state, then rerun the exact targeted world test with configuration caching disabled and offline dependency resolution.

## Anonymous world transaction green

- Threaded identity-keyed anonymous objects through `WorldHistory` publication and `WorldTxn` local state.
- `WorldTxn.createAnonymousObject` copies inherited property slots, records an anonymous write, and does not touch permanent object IDs or parent children. `anonymousObject` provides transaction-local/committed lookup.
- Ordinary object replacements preserve the anonymous map instead of dropping it.
- `WorldHistory` publishes only the transaction's anonymous identities and validates that non-root anonymous parents name permanent objects without imposing parent/children reciprocity.
- `JAVA_HOME=/opt/java/25 ./gradlew --no-configuration-cache --offline test --tests moo.world.WorldTxnTest` passes in 1m21s.
- The Phase 3 plan was reread after the pass; the next active dependency remains anonymous `create` for `server/map_dump_persistence.yaml`.

## Anonymous create builtin red/green

- Added the complete parser/compiler/VM/builtin/transaction test `MooVmTest.createWithAnonymousFlagCommitsAnonymousIdentityWithoutPermanentAllocation`.
- Its red run completed in 20 seconds and failed at the expected returned-outcome assertion because the one-argument `create` call shape rejected the second argument.
- Extended the existing `create` call shape with one optional integer. A nonzero second argument calls the concrete world transaction's anonymous allocator and returns its identity; absent or zero retains permanent allocation.
- The exact targeted test now passes in 52 seconds and proves committed anonymous identity, parent, programmer owner, unchanged permanent count/maximum, and unchanged parent children.
- The active map-family source slice remains uncommitted and incomplete.

## Next action

- Reread Phase 3 after the passing targeted test, then rerun the exact Banteng managed `server/map_dump_persistence.yaml` family. Its first new failure decides the next v17 persistence change; do not start a different family.

## Corrected conformance-path authority

- The prior claim that the exact map suite was missing was wrong. It checked the suite relative to the Banteng repository even though the plan's named runner is the path authority.
- `scripts/run_managed_wsl.sh` resolves `conformance_repo` to the sibling `C:/Users/Q/code/moo-conformance-tests`, validates each `src/moo_conformance/...` argument beneath that checkout, then changes into that checkout before executing `moo-conformance`.
- The exact required file is present at `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/server/map_dump_persistence.yaml` and is executable through the plan's exact runner command.
- There is no conformance-path blocker. The next action remains the exact Banteng managed map family; its result decides the next implementation step.

## Managed map rerun after anonymous create

- The exact runner command resolved the sibling conformance checkout and completed in 5.37 seconds.
- The family still fails its first setup program with `E_VERBNF`; the expected `0` is not returned. This contradicts the earlier recorded `E_ARGS` advancement and must be resolved from current state rather than assumed history.
- The target profile's actual `Test.db` SHA-256 is exactly `1a3f23ebb549e02ccf5341668425118fcdc935b977096add87bc2a8ef29d408e`, matching the profile manifest.
- The managed launcher executes `build/install/banteng/lib/*`. Direct inspection of that installed `BuiltinCatalog` class proves it contains `new_waif`, `caller_perms`, and a call to `WorldTxn.createAnonymousObject`; the failure is not explained by a stale pre-edit distribution.
- The exact v4 fixture contains `#7` as the Waif Class, its executable `new` verb, and the verb body `set_task_perms(caller_perms()); player = caller_perms(); return new_waif();`.
- Current `WorldTxn.verb` performs local then parent-chain lookup, so the remaining unverified boundary is invoking inherited `new` on the newly created subclass returned by `create($waif)`.
- The conformance runner deletes its managed temporary directory during teardown, so the failed run's server log is no longer present. No conformance or runner source was modified.

## Next action

- Add a complete parser/compiler/VM/builtin/world red test for `class = create(#7); return class:new();` against the exact fixture verb body and commit path. Its result will decide whether inherited dispatch on newly created objects causes the current `E_VERBNF`; do not change production code before that result.

## Exact `E_VERBNF` cause isolated

- The created-WAIF-subclass hypothesis was wrong: the complete `class = create(#7); return class:new();` parser/compiler/VM/builtin/world test passed in 32 seconds and returned a WAIF with the created subclass as class and the active programmer as owner.
- Removed that non-reproducing diagnostic test immediately; it is not being retained as unrelated coverage.
- Compared the exact YAML `requires.builtins` list with the current catalog. Every named builtin was present except `chr`.
- The setup reaches `high = [chr(200) -> "high"]` only after both anonymous `create` calls. Therefore the later missing `chr` produces the same `E_VERBNF` code that earlier missing dependencies produced; the family advanced rather than regressed.
- Added complete-pipeline test `MooVmTest.chrProducesTheRequestedLatin1ByteThroughBuiltinDispatch`, asserting `encode_binary(chr(200))` returns `"~C8"`. Its red run failed at the returned-outcome assertion in 20 seconds as expected.
- Added an inline `chr` catalog entry with one integer argument, Latin-1 byte output for 0 through 255, and `E_INVARG` outside that range. No helper/interface/adapter was added.
- The inline production change has not yet been tested and the active source slice remains uncommitted.

## Next action

- Run the exact targeted `chr` test. If it passes, reread Phase 3 and rerun only the exact Banteng managed map family to obtain the next persistence failure.

## Checkpoint boundary reached

- The targeted `chr` test passes in 48 seconds and rebuilt the managed install distribution.
- Phase 3 was reread after the pass.
- The exact managed map setup now succeeds; the next step `return dump_database();` returns no value instead of `0`. The family therefore reaches the real v17 checkpoint boundary.
- Current `LambdaMooV17Codec.writeValue` supports scalar/list/map/bool values but throws `IOException` for both WAIF and anonymous values. The setup stores both kinds recursively in `#1.map_persist_authority`, so this directly explains the checkpoint failure.
- Current v17 input also rejects WAIF tag 11 and anonymous tag 12, requires zero pending-finalization values, reads only permanent-object batches into `WorldTxn`, and has no anonymous world-state restoration path.

## Toast v17 source authority

- Verified against `/root/src/toaststunt` source, not a fixture guess.
- Anonymous values write tag 12 followed by a checkpoint-temporary object ID. Invalid anonymous values use `-1`; the first valid reference assigns the next object slot and later references reuse it.
- The writer emits permanent objects first, then successive anonymous-object batches until writing references no longer grows the object table, followed by a zero batch.
- The reader interns anonymous identities when their tag-12 references are first read, before their later object bodies, which is required for aliases and cycles.
- WAIF values write tag 11 followed by either `c <index>` plus class, owner, property-slot data, `-1`, and `.`, or `r <index>` plus `.`. The first creation is registered before recursive values so later references preserve identity.
- The active map-family WAIFs have no property slots, but their identity must still be shared across list elements and map keys after reload.

## Next action

- Add a red `V17RoundTripTest` containing two shared WAIF identities, two anonymous identities with mutually recursive body references, and maps keyed by those same identities. Require restored alias/cycle identity and byte-stable rewrite, then implement the checkpoint-wide identity contexts directly in `LambdaMooV17Codec` and the concrete world constructor path.

## Identity round-trip red established

- Added `V17RoundTripTest.preservesWaifAndAnonymousIdentityAliasesAndCycles` with two distinct WAIF identities, two anonymous identities, a map keyed by all four same references, and anonymous bodies whose properties point to each other.
- Corrected three NullAway errors in the test itself, then reran it.
- The executable red test now reaches `LambdaMooV17Codec.writeAtomic` and fails with `IOException` at the expected first unsupported identity value. No production codec behavior has passed this test yet.
- Dispatched the existing `anon_model` subagent for a read-only independent review of this exact codec/test boundary; it is not editing files or working another family.

## Constructor threading completed

- Added concrete overloads so `WorldHistory` and `WorldTxn` can be initialized with the identity-keyed anonymous-object map. Existing two-argument callers delegate to an empty map.
- These constructor changes are part of the active codec slice and remain unverified until the targeted identity round-trip test runs after the codec implementation.

## Next action

- Implement checkpoint-wide write/read identity contexts in `LambdaMooV17Codec`: tag-11 WAIF create/reference records, tag-12 anonymous IDs, fixed-point anonymous object batches, program records for both permanent and anonymous bodies, and restoration into the concrete anonymous world map. Then rerun only the targeted identity test.

## Identity codec targeted test green

- The read-only subagent confirmed Toast uses WAIF database tag 13 and anonymous tag 12, and identified that the first test draft incorrectly modeled local anonymous property definitions.
- Corrected the test to put `graph` and `other` definitions on permanent parent `#0`, represent both anonymous bodies with inherited slots, add one anonymous verb/program, and rename the test to claim WAIF aliases plus anonymous aliases/cycles accurately.
- Implemented one checkpoint-wide write context with reference-identity tables for WAIFs and anonymous objects.
- Valid anonymous references receive first-use temporary object IDs; missing-body identities write `-1`. Reachable anonymous bodies are written in successive discovery batches after permanent slots, followed by zero.
- Anonymous verb programs are counted after discovery and use their temporary dump object IDs.
- Implemented one read-wide identity context. Tag-12 references intern placeholders before bodies; tag-13 zero-property WAIF creations/references preserve identity. Subsequent batches restore concrete anonymous bodies keyed by those placeholders.
- Permanent and anonymous raw bodies share property inheritance/program lookup during restoration, then are separated into permanent `WorldObject` records and the anonymous world map.
- The first compile run found only three NullAway errors from nullable identity-table lookup; those were corrected with required non-null checks.
- `V17RoundTripTest.preservesWaifAliasesAndAnonymousAliasesAndCycles` now passes in 1m22s, including byte-identical second output.
- Explicit remaining limitation: the current immutable `WaifValue` model has no property slots, so the reader rejects nonzero WAIF property counts rather than silently discarding them. This must be expanded before Phase 3's full WAIF/Anon fixture gate can pass, but the active map family creates zero-property WAIFs.

## Next action

- Reread Phase 3 after the passing substantial targeted test, then rerun only the exact Banteng managed map family. Its restart-side result decides the next same-family change.

## Anonymous inheritance repair

- The first managed rerun after identity codec support checkpointed successfully but restart failed while restoring temporary object `#10`: zero anonymous property slots for one inherited property.
- Exact cause: the setup creates anonymous children first, then adds `map_persist_authority` to their permanent ancestor. `WorldTxn.addProperty` propagated the definition through permanent `children` only, leaving anonymous descendant layouts stale.
- Added red `WorldTxnTest.propertyDefinitionsPropagateToAnonymousDescendants`; it failed with an empty anonymous property list as expected.
- Extended the existing `addProperty` transaction operation directly: after permanent propagation, it walks each anonymous body's permanent ancestor chain, locates the new slot in its immediate parent, inserts the inherited clear/nondefinition slot at the matching layout index, and records the anonymous write.
- The exact targeted propagation test passes in 57 seconds and rebuilt the managed distribution. Phase 3 was reread afterward.

## Remaining managed map mismatch

- The next exact managed run now checkpoints, restarts, reloads every permanent/WAIF/anonymous value, and successfully indexes all restored identity-keyed maps.
- Result groups 1, 2, 3, 4, and 6 match exactly. Only group 5 differs: expected twelve `1` values, actual twelve `0` values.
- Group 5 asserts that `mapkeys` and `toliteral` for forward/reverse boolean, WAIF, and anonymous maps change across checkpoint/restart. Banteng preserves their pre-checkpoint traversal spelling exactly, so every change detector is false.
- This is the active family's final observed mismatch. The next decision belongs to the current map mutation/traversal ownership and v17 insertion path; do not alter unrelated wire values or switch families.

## Next action

- Inspect `MapValue`, VM map assignment, `mapkeys`, `toliteral`, equality, and v17 map reconstruction together. Add an exact red test that freezes the runtime topology versus reload transformation for boolean/WAIF/anonymous non-total keys before changing the map owner.

## Non-total map topology authority and red gate

- Toast `utils.cc::compare` returns `+1` in both directions for distinct same-type Boolean, WAIF, and anonymous keys. `map.cc::rbinsert` therefore places the second of two such keys on the left, and in-order traversal exposes reverse insertion order.
- Toast v17 map writing emits that traversal order; `db_io.cc::dbio_read_var` reconstructs a map by calling `mapinsert` for every disk entry. The read therefore reverses the serialized order again, so post-restart traversal equals original insertion order and differs from the pre-checkpoint traversal.
- Added `V17RoundTripTest.reinsertsNonTotalMapKeysAcrossV17Read`, covering forward and reverse Boolean, WAIF, and anonymous maps through the real codec with concrete anonymous bodies.
- The exact targeted Java 25 test failed in 35 seconds at its first pre-checkpoint assertion: Banteng's `MapValue.with` preserves insertion order instead of exposing Toast's reverse insertion order. This is the required red production gate.
- No external blocker. The next action is to change the existing `MapValue.with` owner so new distinct same-type Boolean, WAIF, and anonymous keys are inserted before their existing family, rerun this same test, then change v17 map reading to reconstruct through `with` if the test reaches the expected reload-side failure.

## Non-total map topology implementation green

- Changed `MapValue.with` to insert according to Toast's scalar-key comparison topology. Comparable keys use Toast type/value order; distinct same-type Boolean, WAIF, and anonymous keys compare `+1`, so a later insertion traverses before an earlier one. Equal keys are erased/reinserted in the resulting immutable snapshot.
- The first post-edit run passed every pre-checkpoint topology assertion and failed at the first restored-order assertion, proving the remaining fault was v17 map reconstruction rather than map mutation.
- Changed only the v17 MAP read branch to start empty and call `MapValue.with` for each disk pair, matching Toast `dbio_read_var` rather than copying disk order into a `LinkedHashMap`.
- `V17RoundTripTest.reinsertsNonTotalMapKeysAcrossV17Read` now passes in 34 seconds for forward/reverse Boolean, WAIF, and anonymous maps, including changed `toliteral` spellings.
- No external blocker. The next action is the required Phase 3 reread, followed by the exact managed Banteng `server/map_dump_persistence.yaml` family. Its result decides whether this active family is conformant or requires another same-family correction.

## Map family completion evidence

- The exact managed Banteng `server/map_dump_persistence.yaml` family passes 1/1 through the production socket, setup, v17 checkpoint, process restart, reload, and every ordering/topology/equality assertion.
- The first affected-class run executed 119 tests and found one obsolete assertion in the earlier identity/cycle test: it required non-total identity-key map order and bytes to remain stable across reload, contradicting the proven Toast transformation.
- Corrected that test to prove WAIF/anonymous aliasing through identity-key lookup while retaining cycle and anonymous-verb assertions. The separate ordinary-world round-trip test still proves byte-stable deterministic output, and the new topology test proves the intentional non-total transformation.
- The combined `MooValueTest`, `MooVmTest`, `WorldTxnTest`, and `V17RoundTripTest` run now passes all 119 tests in 14 seconds.
- `git diff --check` passes. The active tracked slice consists only of the corrected Phase 3 plan and the map-family value/builtin/world/v17 implementation and tests; `WorldAnonymousObject.java` is the one intended new source file. Notes and `pyghidra_mcp_projects/` remain untracked and must not be staged.
- No external blocker. The next action is to stage exactly the intended plan/source/test paths, verify the cached diff, commit the completed map-family slice, reread Phase 3, and continue with the next exact path `server/object_dump_persistence.yaml`.

## Committed result

- Staged exactly the thirteen intended plan/source/test paths, including new `WorldAnonymousObject.java`; cached whitespace validation passed and all notes plus `pyghidra_mcp_projects/` remained untracked.
- Committed the completed family as `dc58837 feat: complete phase 3 map persistence`.
- The next Phase 3 target in the immutable path order is `server/object_dump_persistence.yaml`; begin with its exact Toast managed oracle run before evaluating Banteng.
## 2026-07-20 Toast two-key traversal audit

- Assignment: read-only inspection of Toast map comparison, RB-tree insertion/traversal, and v17 map write/read behavior for BOOLEAN, WAIF, and anonymous-object keys only.
- Initial source owners: `utils.cc::compare`/`equality`, `map.cc::node_compare`/RB insertion/`mapinsert`/`mapforeach`, and `db_io.cc` map serialization/read reinsertion.
- Current state: exact forward/reverse and post-restart orders have not yet been derived. No source, test, or plan file has been changed.
- Blocker: none.
- Next action: read those bounded functions and calculate each two-key tree/order transition from the actual comparator direction and traversal order.
