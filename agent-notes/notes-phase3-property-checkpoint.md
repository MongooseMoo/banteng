# Phase 3 property slice checkpoint

## Current state

- Git tracked state was clean at commit `dc2ed5b`; branch `master` was 160 commits ahead of `origin/master`.
- Toast passes all 16 rows in `basic/property.yaml`.
- Banteng passes 7 and fails 9 rows. The failures require inherited assignment, explicit clear state, local-definition enumeration, and definition deletion.
- The read-only Toast/Barn audit is complete. Toast requires `properties`, `delete_property`, `clear_property`, and `is_clear_property` with custom target validation and permission behavior.

## Findings and observations

- `WorldProperty` currently has only name, value, owner, and permissions. `WorldTxn.property()` dynamically walks ancestors, and v17 currently requires property-value count to equal local property-name count.
- Toast/Barn authority distinguishes locally defined property names from per-object inherited value slots. A clear inherited slot must survive checkpoint/restart and is encoded as v17 value tag 5.
- Treating absence as clear could pass the immediate managed rows but would not preserve explicit clear/override state through v17 restart, so it does not satisfy the plan's persistent vertical slice.
- The implementation should remain in the existing `WorldProperty`, `WorldTxn`, `BuiltinCatalog`, and `LambdaMooV17Codec` owners. No interface, adapter, sender, or helper layer is needed.
- The active plan explicitly requires complete property persistence in Phase 3 and lists `delete_property` as a required manifest handler.

## What has been tried

- Exact Toast and Banteng runs established the 16/16 versus 7/16 baseline.
- Direct source inspection established Toast's error/permission contracts and Barn's `clear`/`defined` representation.
- The audit agent proposed dynamic absence for this narrow family; that proposal was rejected because it conflicts with v17 restart semantics required by the active plan.

## Blocker

- No external blocker. The source slice has not started yet.

## Next action

- Add red tests for explicit defined/clear slots, inherited assignment and clear behavior, exact builtin contracts, and v17 clear-slot round trip; then implement that single property slice and run the targeted tests, exact managed family, full gates, and commit the kept slice.

## Checkpoint 2

- Added red tests in `WorldTxnTest`, `BuiltinCatalogTest`, and `V17RoundTripTest`.
- The exact targeted WSL/Java 25 run failed to compile for the intended missing surface: `WorldProperty.clear`, `WorldProperty.defined`, the six-field constructor, and `WorldTxn.clearProperty`.
- Production work now in progress: `WorldProperty` has explicit `clear` and `defined`; `WorldTxn` reads through clear slots, writes inherited overrides, copies inherited slots on create, propagates definitions, removes descendant slots on definition deletion, and clears inherited slots.
- Updated the current v4 constructor path to create defined, non-clear slots. The v17 codec and the four builtin handlers are not yet updated, so the slice is intentionally still red and uncommitted.
- No external blocker. Next action is the v17 explicit-clear/name-layout implementation, followed by the builtin handlers and targeted tests.

## Checkpoint 3

- The v17 writer now emits only locally defined names, all value slots, and tag 5 for clear slots. The reader restores names from the parent layout and reconstructs explicit `defined`/`clear` records.
- `BuiltinCatalog` now contains `properties`, `is_clear_property`, `clear_property`, and `delete_property` in the production manifest with world read/write effects and target-specific validation.
- The first post-implementation targeted run found a local-variable name collision in `WorldTxn`; repaired.
- The second run reached NullAway and found one nullable map lookup in v17 restore; repaired with `Objects.requireNonNull` because the map is populated immediately before object construction.
- The slice remains uncommitted and has not yet passed its targeted tests. No external blocker. Next action is rerunning the same three targeted test classes.

## Checkpoint 4

- The uncached targeted rebuild completed successfully in 1m36s for `WorldTxnTest`, `BuiltinCatalogTest`, and `V17RoundTripTest`.
- The managed Banteng `basic/property.yaml` family now passes all 16 rows, matching the already-recorded Toast 16/16 result.
- The required Phase 3 reread identified `objects/property_lookup.yaml` as the next conformance family, but Git accountability requires this kept property slice to be gated and committed first.
- The exact full `JAVA_HOME=/opt/java/25 ./gradlew check` gate passed in 31s.
- No external blocker. Next action is `git diff --check`, review the exact tracked slice, stage only its source/test paths, and commit it before beginning `objects/property_lookup.yaml`.

## Checkpoint 5

- `git diff --check` passed.
- The staged ledger contained exactly the nine intended property source/test files; no notes or environment artifacts were staged.
- Committed the kept slice as `25b4229 feat: add phase 3 property semantics` with 674 insertions and 29 deletions.
- The independent read-only reviewer did not return within the bounded window and was stopped; it made no edits. Local diff review found no blocking defect, and all named authorities above remained green.
- No external blocker. Next action is verify tracked cleanliness, run Toast and Banteng on the next exact family `objects/property_lookup.yaml`, and work only that family.
