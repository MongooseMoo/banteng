# Issue 86: typed permissions

## Goal
Implement issue 86's typed verb/property flags, independent verb argument specifications, and shared world access checks. Preserve current semantics, including executable-only verb lookup, debug behavior, property chown inheritance, and owner/programmer restrictions.

## State
Implemented typed records and migrated production consumers, model mutations, test constructors and persistence V4/V5/V17 paths. Branch campaign/86-typed-permissions started at 6f43aa0; PR #106 now targets master. PR #104 has merged. PR #105 is the remaining merge dependency, including its integrated nested-assignment repair. Parent published head 487e532 and owns review, CI and merge. Focused TypedPermissionsTest passed; no broader local CI command has run.

## Decisions and why
Smallest change: immutable integer-backed VerbPerms/PropertyPerms, ArgSpec enum, typed WorldVerb/WorldProperty fields, Access helpers using public WorldTxn.isWizard; migrate producers/readers directly, without compatibility overloads. Approximately 350–550 production lines plus mechanical test callsite migration. Only codecs pack/unpack argument bits.

## Surprises
Assigned worktree did not exist; created it from the assigned commit. No repository AGENTS.md exists; global instructions supplied in conversation apply.
The authored-test compiler caught missing imports, reliance on Enum.ordinal(), stale wizard locals, and redundant parentheses introduced during migration. Corrected source rather than suppressing existing checks. ArgSpec now explicitly maps Toast values independently of enum order; builtin parsing produces enum values directly.

## Next step
Parent verifies current-head review and CI, merges PR #105 before PR #106, and owns publication and merge. Worker implementation is complete; this note-only correction does not change source or tests.

## PR #106 CI repair
Claude review cleanup removed only blank import lines introduced by removed imports; the WorldResultTest repair is preserved, and no local tests were run for this cosmetic change.

CI run 40419707970, job 121910790465 failed `WorldResultTest.everyPublicBooleanWorldMutationUsesWorldResult` at line 35: its explicit read-only predicate inventory omitted `isWizard`. Issue #86 Decision 4 explicitly requires public `WorldTxn.isWizard(long)`; `WorldTxn.java:1658-1661` reads the object and its wizard flag without mutating the world. The smallest repair is two test lines: add `isWizard` to that explicit inventory and sort the reflected names before comparison so reflection order cannot affect it. Boolean mutations are still rejected; no blanket method exclusion was added. No production code changed. No local CI rerun; parent owns publication and PR CI verification.

## Draft PR
Integrated issue105 nested-property-assignment repair e06c048 through merge f0fdd58. The merge had no conflicts; IndexOps retains typed debug access. Migrated the two incoming CommandParserTest property constructors to PropertyPerms(0), matching WorldProperty's typed signature; NestedPropertyAssignmentTest constructs WorldObject only. No local CI, push or publication; parent owns final verification.

Title: Type verb and property permissions and separate verb argument specifications

Verb/property flags now use immutable typed values and shared Access checks backed by WorldTxn.isWizard. WorldVerb stores independent direct/indirect argument specifications; V4/V5 readers and both permanent/anonymous V17 codec paths translate the persisted packed representation. All producers and consumers use the typed fields, including cache invalidation, command matching, property inheritance and VM debug flags.

Validation: `bash ./gradlew --no-daemon --max-workers=2 :test --tests world.mongoose.banteng.world.TypedPermissionsTest` under WSL Debian Java25 completed `BUILD SUCCESSFUL`. The focused test covers the flag grant matrix, metadata preservation and executable-only lookup, and all nine direct/indirect pairs through permanent and anonymous V17 verbs with a literal packed-value expectation. Main and test compilation completed as dependencies of this authored-test invocation. Conformance files are unchanged. No PR CI, full suite, publication or merge has been performed by this worker.
