# Phase 3 WAIF dump persistence

## Current state

- The immutable implementation plan remains active at `docs/reports/banteng-implementation-plan.md`.
- Phase 3 is committed through `server/object_dump_persistence.yaml`; the object-family commit is `e3683ac fix: preserve phase 3 object map identity`.
- `server/string_dump_persistence.yaml` passes both Toast and Banteng 1/1 with no source change, so no empty or unrelated commit was created.
- The current exact target is `server/waif_dump_persistence.yaml`.
- The tracked worktree is clean before this family; existing notes and `pyghidra_mcp_projects/` remain untracked.

## Oracle result

- The exact Toast managed `server/waif_dump_persistence.yaml` family passes 1/1.
- No external blocker.

## Next action

- Reread the controlling Phase 3 plan after the passing Toast run, then run the exact Banteng managed WAIF family. Its first current failure decides the next same-family implementation step; do not switch to an anonymous startup family or broaden the target.

## Banteng failure and confirmed model gap

- The exact Banteng managed WAIF family fails its setup program with `E_TYPE`, before checkpointing.
- The exact setup creates a subclass of `$waif`, adds `:marker`, constructs a WAIF, assigns `waif.marker = 7`, then stores the same WAIF reference twice.
- Current VM WAIF property reads special-case only `.class`; every other WAIF property returns `E_PROPNF`. Current VM property writes accept only permanent `ObjectValue` receivers, so `waif.marker = 7` produces the observed `E_TYPE`.
- Current `WaifValue` carries only identity plus immutable class/owner. Current committed world state has permanent and anonymous maps but no WAIF body/state map. Current v17 writing always emits zero WAIF property slots; current v17 reading explicitly rejects a nonzero WAIF property count.
- Toast source confirms WAIF property definitions derive from class properties and that WAIF values have per-identity property slots. `new_waif`, `waif_get_prop`, `waif_put_prop`, `write_waif`, and `read_waif` are the exact owner routines under review.
- Two existing native subagents are performing read-only audits: one for exact Toast WAIF property/wire semantics and one for Banteng/Barn ownership and the smallest concrete vertical model. They are not editing files or working another family.
- No external blocker. The next action is to finish the exact Toast/Barn source read, then add red VM/world and v17 backreference tests that require transaction-owned WAIF state before adding the concrete world record/map and codec slots.

## Exact Toast WAIF property and wire findings

- `gen_waif_propdefs` collects only class/ancestor property definitions whose stored names begin with `:`. MOO access strips that prefix: `waif.marker` resolves the class property `:marker` case-insensitively.
- `new_waif` captures the class's ordered WAIF property-definition layout and begins every mappable value clear. A clear WAIF slot reads the current class property value; a written slot overrides it for that WAIF identity.
- `.class` and `.owner` are read-only built-ins; `.wizard` and `.programmer` read false and are not writable.
- WAIF property permissions come from the class property. Read/write flags grant access directly; otherwise the class property owner grants access, except `+c` makes the WAIF owner the effective property owner; wizards also pass.
- v17 WAIF creation writes `c INDEX`, class, owner, total property-definition count, then sparse `INDEX`/tagged-value pairs for non-clear slots, `-1`, and `.`. A repeated identity writes `r INDEX` and `.`.
- The reader registers the new WAIF identity before reading sparse property values, allowing recursive backreferences, and binds property names/metadata from the class definitions after permanent objects are loaded.
- Banteng's codec already has checkpoint-wide WAIF identity tables and registers a creation before slots, but hardcodes a zero property count on write and rejects nonzero counts on read.
- The correct concrete ownership is transaction/world state keyed by `WaifValue` identity, parallel to anonymous bodies; the value remains the identity/class/owner handle. Runtime get/set and codec lookup must go through that world-owned body.
- No external blocker. The next action is to add red runtime and v17 tests for one `:marker` slot and shared WAIF identity, then thread a concrete WAIF body map through `World`, `WorldSnapshot`, `WorldHistory`, and `WorldTxn` before implementing VM access and sparse codec slots.

## World transaction red gate

- Added `WorldTxnTest.waifPropertyStateCommitsAndRollsBackByReferenceIdentity` with a concrete class `:marker` definition. It requires WAIF creation, clear-slot fallback to class value `0`, identity-local override to `7`, commit visibility, snapshot membership, transaction-local override to `42`, and rollback back to `7`.
- The exact targeted Java 25 run fails during test compilation with nine missing symbols: `WorldTxn.createWaif`, `readWaifProperty`, `writeWaifProperty`, and `WorldSnapshot.waifs`. This is the required red result; production world edits are now authorized.
- No external blocker. The next action is to add one concrete immutable `WorldWaif` body record and thread its identity-keyed map through the existing world revision/snapshot/history/transaction owners, then implement the three tested transaction operations and rerun this exact test.
## 2026-07-20 transaction-owned WAIF state

- Observation: the exact managed WAIF row fails before checkpoint because `MooVm.setProperty` accepts only permanent objects; transaction state previously had no WAIF body.
- Implemented in the active uncommitted slice: immutable `WorldWaif` bodies keyed by `WaifValue` identity are threaded through `World`, `WorldSnapshot`, `WorldHistory`, and `WorldTxn`.
- Implemented transaction operations: `createWaif`, `waif`, `readWaifProperty`, and `writeWaifProperty`; publication copies only written WAIF identities and rollback discards transaction-local overrides.
- What worked: `WorldTxnTest.waifPropertyStateCommitsAndRollsBackByReferenceIdentity` passes under the exact WSL Java 25 Gradle command (`BUILD SUCCESSFUL` in 1m34s).
- What has not been attempted yet: VM property dispatch, `new_waif` transaction allocation, v17 WAIF slot persistence, or the exact managed WAIF row.
- Blocker: none.
- Next action: reread Phase 3 as required after the passing substantial targeted test, then add the complete-pipeline VM red test before changing VM or builtin production code.
## 2026-07-20 VM red gate

- Added `MooVmTest.waifPropertyAliasesUseTransactionOwnedStateThroughTheCompletePipeline` using parser, compiler, verb dispatch, `new_waif`, two WAIF aliases, property writes, property read, and `.class`.
- Red result: the exact targeted test reaches execution and fails because the VM outcome is `ERRORED` instead of `RETURNED`, matching the known pre-checkpoint `E_TYPE` property-write gap.
- Production changes in progress: `new_waif` is now classified `TRANSACTION_WRITE` and delegates allocation to `WorldTxn.createWaif`; `WorldTxn.waifProperty` exposes the governing colon-prefixed class property for VM authorization.
- Not yet changed: `MooVm` WAIF property read/write dispatch. No v17 codec changes have started.
- Blocker: none.
- Next action: update the existing `MooVm.getProperty` and `setProperty` owners to delegate WAIF state to `WorldTxn`, then rerun only the exact VM test.
## 2026-07-20 v17 WAIF body gate

- Added `V17RoundTripTest.preservesWaifPropertySlotsAndBackreferences` with one class `:marker` slot, `{class, waif, waif}` aliases, first checkpoint value `7`, transaction mutation to `42`, and a second checkpoint/reload.
- Initial red result: restored `WorldSnapshot.waifs()` had no body because the writer emitted property count `0` and the reader discarded body state.
- Implemented v17 creation records with total slot count plus sparse non-clear index/value pairs; identity is registered before recursive value writing/reading; references continue to use `r INDEX`.
- Reader validates creation order, backward references, property-index range, duplicate indices, and restored class-layout count, then rebuilds immutable `WorldWaif` bodies after permanent objects load.
- What worked: the exact v17 WAIF test passes (`BUILD SUCCESSFUL` in 29s), including alias identity and value `42` after the second reload.
- Blocker: none.
- Next action: run the exact managed `banteng stock` `server/waif_dump_persistence.yaml` row through socket, checkpoint, process restart, and reconnect.
## 2026-07-20 full-check findings

- Exact managed result: `server/waif_dump_persistence.yaml` passes 1/1 through socket, checkpoint, process restart, reconnect, alias mutation, and `.class`.
- Affected-class result: all of `WorldTxnTest`, `MooVmTest`, and `V17RoundTripTest` pass together.
- Full `./gradlew check` did not pass: `BuiltinCatalogTest.registersEveryReachableBuiltinExactlyOnceWithCompleteContracts` reported manifest size 49 versus registered-name set 46, and the unrelated randomized `WorldTxnPropertyTest.generatedOverlappingWritesConflictWithoutPartialPublication` exhausted its generator.
- Investigation: the registered-name set was stale for three existing manifest entries: `new_waif`, `chr`, and `caller_perms`. Adding only `new_waif` changed the mismatch to 49 versus 47, proving `chr` and `caller_perms` remain.
- Correction: no production behavior is being changed for this manifest-size failure; only the exact stale registered-name authority is being aligned.
- Blocker: none.
- Next action: add `chr` and `caller_perms` to the set, rerun the exact builtin contract test, then rerun the full check to distinguish the known randomized exhaustion from WAIF-slice regressions.
## 2026-07-20 WAIF family kept-slice gate

- Corrected the builtin registered-name authority with the three already-present manifest entries `new_waif`, `chr`, and `caller_perms`; the exact builtin contract test now passes.
- Reran the exact full plan gate `JAVA_HOME=/opt/java/25 ./gradlew check`; it passes (`BUILD SUCCESSFUL` in 32s). The prior randomized generator exhaustion did not recur.
- Kept evidence for this slice: exact managed WAIF row 1/1; affected `WorldTxnTest`, `MooVmTest`, and `V17RoundTripTest` together; exact builtin manifest contract; full Gradle check; and `git diff --check` before final staging.
- State: the source slice remains uncommitted only until exact-path staging and cached-diff verification. All `notes-*.md` files and `pyghidra_mcp_projects/` must remain unstaged.
- Blocker: none.
- Next action: stage only the WAIF production/test paths, verify the cached file list and diff check, commit the slice, reread Phase 3, then continue with `startup_repair_anon1.yaml` under the corrected v17-only anonymous-database plan.
