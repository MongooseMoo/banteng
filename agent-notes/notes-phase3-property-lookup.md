# Phase 3 property lookup family

## Current state

- The preceding property slice is committed as `25b4229 feat: add phase 3 property semantics`; tracked state was clean before this family.
- Toast passes `objects/property_lookup.yaml` 1/1.
- Banteng fails the single row with `E_VERBNF` because `property_info` is required by the row and absent from the production builtin manifest.

## Exact row

- Creates an object, defines mixed-case `CaseProbe` with value 42 and permissions `rw`, then reads `.caseprobe`, `.('CASEPROBE')`, `property_info(object, "caseprobe")[2]`, and `is_clear_property(object, "CASEPROBE")`.
- Expected result is `{42, 42, "rw", 0}`.

## Authority findings

- Toast registers `property_info` with exactly two arguments: `TYPE_ANY`, `TYPE_STR`.
- Non-object returns `E_TYPE`; invalid object returns `E_INVARG`; missing or built-in property returns `E_PROPNF`; denied read returns `E_PERM`.
- Success returns `{owner, permissions}`. Permissions are a lowercase string in canonical `r`, `w`, `c` order.
- Banteng already stores owner and permission bits on `WorldProperty` and resolves names case-insensitively through `WorldTxn.property`, so the narrow owner path is one `BuiltinCatalog` manifest entry and handler plus targeted tests. No new interface or world helper is required.

## Blocker and next action

- No external blocker.
- Wait for the already-requested read-only subagent confirmation, add a red `BuiltinCatalogTest` for the exact manifest and return/error contract, implement `property_info` in `BuiltinCatalog`, rerun the targeted test and this one managed family, then gate and commit the kept slice.

## Checkpoint 2

- The subagent independently confirmed the direct Toast contract and Barn behavior; no conflicting authority was found.
- Added the red manifest/behavior test. Initial targeted run failed exactly because `property_info` was absent.
- Added one `BuiltinCatalog` manifest entry and handler using existing `WorldTxn.property` metadata, case-insensitive lookup, built-in rejection, canonical `rwc` permission formatting, and Toast error ordering.
- The second targeted run now has 22/23 `BuiltinCatalogTest` tests passing. The remaining failure is the new permission assertion at the final `E_PERM` expectation; the actual result has not yet been inspected.
- No external blocker. Next action is inspect that single assertion result, correct the test fixture or handler according to Toast's owner/wizard/read-bit rule, and rerun the same targeted class.

## Checkpoint 3

- The remaining targeted failure was a test-fixture error: `CaseProbe` had the read bit, so Toast correctly permits any programmer. Added a separate write-only `Private` property for the `E_PERM` assertion; production code was unchanged by this correction.
- `BuiltinCatalogTest` now passes all 23 tests.
- Banteng now passes `objects/property_lookup.yaml` 1/1, matching Toast 1/1.
- The exact full `./gradlew check` and `git diff --check` gates pass.
- Diff review shows exactly 53 production lines and 62 test lines across `BuiltinCatalog.java` and `BuiltinCatalogTest.java`; those two files and only those two files are staged.
- No external blocker. Next action is commit this kept slice, verify the ledger, and begin the exact `server/dump_persistence.yaml` family.
