# Issue 85: opcode dispatch and builtin ownership

## Goal
Remove duplicated operation vocabularies and builtin-name checks from MooVm, preserving existing execution semantics.

## State
Created campaign/85-opcode-dispatch from aabe141 and merged current origin/master so issue93's removed source-shape tests stay removed. Read issue85, all five family wrappers, MooVm CALL/invoke/result paths, BuiltinCall/Result, Catalog registrations/call_function/callers/yin, VmState caller renderer and Toast execute.cc:3679–3749.
Implementation and focused behavior tests passed (direct/indirect callers, yin, pass-local inheritance and maxrec, existing pass error/lookup paths, and BuiltinCall context). Added pass to the existing exact catalog-name inventory; its authored contract test passed too. No broad local suite or CI gate has run. Ready for parent handoff.

## Decisions and why
Smallest implementation: delete five Operation wrappers; dispatch directly to existing void helpers, grouping arithmetic/bitwise/comparison cases by existing Opcode. Remove arithmetic's redundant numeric operation enums as their helpers consume Opcode directly; preserve calculations. Approximately 250–400 changed production lines plus relocating the existing pass body.

BuiltinCall carries Function<Boolean,ListValue> instead of rendered frames. Ordinary and authorized invocation use state::callers; call_function forwards it. PendingBuiltin retains its captured value-only callers list for reachability. Catalog handles callers truthiness and pass registration. A typed BuiltinResult.Pass carries arguments; its VM result handler runs the existing pass body without incrementing IP again, and errors use advanceInstruction=false because invoke already advanced it. Delete VM yin specialcase; ordinary registered yin owns behavior.

## Surprises
ComparisonOps.java does not exist; arithmetic owns comparisons. No source-shape test is recreated. No publication/push authorized for this worker.
Registering pass exposes call_function("pass") as a real caller of the inherited-frame behavior. PropertyOps's existing last-read retention check recognized direct eval/pass only. Added call_function to that existing check; extended the existing anonymous-local pass test to direct and one/two levels of indirect calls, so the inherited frame keeps the copied locals.
MooVm still contains lifecycle verb hook names recycle/initialize/exitfunc. Parent explicitly confirmed these are preserved: issue85 concerns builtin dispatch checks, not moving verb hook ownership for a literal grep. PR claims must distinguish lifecycle verb lookup from builtin selection.

## Next step
Parent owns integration/review/publication. Worker source and focused validation are complete. Integrate the independent arithmetic semantics work and typed-permission changes in their owning branches without restoring the removed enums or builtin checks.

Integration: merged issue86 head 45f206c into issue85 head 2526a73 in a89f711, retaining direct dispatch, typed Pass, callers rendering and typed permission/argument constructors. Git reused prior conflict resolutions in MooVm and MooVmTest; inspected both and migrated the newer callers test constructor that the reused resolution missed. Merged issue91 head eb344dd in 6f2ed86, preserving its runtime/status/JFR removal and PureVmFixture migration. Inspected all MooVmTest execution calls: pure calls use the existing fixture and actual MooVm calls retain explicit transaction/catalog/task context. No production helper was restored. These integrations make #106 and #109 dependencies of the final #107 range; #109 also carries #105. No local CI run; parent owns publication and CI verification.

## Draft PR
Title: Dispatch opcodes directly and keep builtin selection in the catalog

The VM's exhaustive Opcode switch now calls existing void family helpers directly. Arithmetic helpers consume Opcode; duplicated operation enums and always-true wrappers are removed. BuiltinCall carries a standard caller-stack renderer so direct and nested call_function callers requests select source lines in the builtin. Pending requests retain captured value roots. Registered yin runs through ordinary invocation; registered pass produces a typed result handled by the relocated inherited-verb body, preserving the already-advanced instruction pointer.

The existing local-reference last-read check accounts for indirect pass through call_function. Lifecycle verb hooks recycle/initialize/exitfunc remain in the VM; this change removes builtin-specific selection checks, and does not claim those distinct verb hook names disappear.

Validation: focused MooVmTest selections for callers/source lines (including one/two nested call_function levels), registered default yin, direct/indirect pass-local inheritance and maxrec continuation, existing no-parent error/canonical inheritance cases, and BuiltinCompositionTest.builtinCallOwnsTheCompleteInvocationContext completed BUILD SUCCESSFUL. BuiltinCatalogTest.registersEveryReachableBuiltinExactlyOnceWithCompleteContracts also completed BUILD SUCCESSFUL after updating the existing expected inventory for pass. Conformance files unchanged. No full local suite, PR CI, publication or merge by this worker.

Commands ran under WSL Debian with JAVA_HOME=/opt/java/25, using `bash ./gradlew --no-daemon --max-workers=2 :test` and these exact `--tests` selections:

- world.mongoose.banteng.vm.MooVmTest.callersReturnsCallerFramesAndOptionallyAppendsSourceLines
- world.mongoose.banteng.vm.MooVmTest.registeredDefaultYinPreservesItsValueAndSuspensionThreshold
- world.mongoose.banteng.vm.MooVmTest.passRetainsAnonymousLocalsCopiedIntoTheParentVerbFrame
- world.mongoose.banteng.vm.MooVmTest.passAtMaximumDepthReturnsTheErrorAndContinuesWithoutSkippingInstructions
- world.mongoose.banteng.vm.MooVmTest.passFromALocationWithoutParentsRaisesInvalidIndirection
- world.mongoose.banteng.vm.MooVmTest.multipleInheritanceVerbLookupAndPassFollowCanonicalAncestorOrder
- world.mongoose.banteng.builtin.BuiltinCompositionTest.builtinCallOwnsTheCompleteInvocationContext

The separate final invocation selected world.mongoose.banteng.builtin.BuiltinCatalogTest.registersEveryReachableBuiltinExactlyOnceWithCompleteContracts. The only production edits after the behavior selections were import cleanup; that final invocation compiled those changes.

## PR #107 closed-result inventory repair
Nested-assignment integration: merged #106 head 487e532 in b2e2c06 and #109 head 65cf4e0 in d2cf332. Retained direct dispatch and exposed the repaired setIndexedProperty signature with parentDepth; both property index/range dispatches now pass the existing instruction operand, matching the repair's former wrapper dispatch. The Operation enum/wrapper remains deleted. Repaired parent reconstruction and typed debug accesses remain. No local CI or publication.

Integration CI repair: run 40426160951 job 121932333688 reached test compilation and reported two CommandParserTest constructors passing int instead of PropertyPerms at lines 73/130. The independent parser tests arrived through #109 after the typed-permission migration; I missed these constructors in integration. Replaced their zero masks with new PropertyPerms(0), preserving alias data and assertions. No production changes or local CI; parent owns publication and rerun.

CI run 40421688327, job 121917416385 failed BuiltinResultTest.resultHierarchyIsClosedAndEachEffectCarriesOnlyItsOwnState at line 20. I added the requested BuiltinResult.Pass(ListValue arguments) but omitted Pass from this existing test's exact permitted-name and record-field inventories. Read both the production record and the full test; the smallest repair adds exactly Pass and its sole arguments field to those inventories. The sealed-class assertion and both complete equality checks remain unchanged, so unexpected result variants or state still fail. No production code changed; parent owns publication and PR CI verification.
