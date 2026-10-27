# Issue 10: Map builtin family

## Goal
Move the contiguous Map registrations and private implementations into MapBuiltins through the existing family mover, preserving behavior and registration order. Partial issue10 only.

## State
Math PR112 completed Claude review and all PR checks including conformance. Existing mover now accepts declarative Jackson properties; the Map recipe selects only the four registrations and six methods. The authored fixture loads that declarative recipe and executes its clone successfully. Migration dry run completed and generated exactly MapBuiltins.java plus the catalog extraction; applied through apply_patch. Parent published PR114; CI and Claude identified the unused generated NUMBER constant. The mover cause and generated Map instance are corrected locally; parent publishes the repair and reads PR checks.

## Decisions and why
Smallest next family is mapdelete, mapkeys, mapvalues and maphaskey: contiguous registrations at BuiltinCatalog 382–420 and six static implementation methods mapKeys, mapValues, mapHasKey, mapDelete, deleteMapKey, invalidMapBuiltinKey at 2632–2713. Parent read these methods and registrations directly. Move bodies unchanged; preserve ValueSemantics.STANDARD and orderedEntries versus entries behavior as currently written. Do not fold independent semantics changes into extraction.

Reuse MoveBuiltinFamily with its existing configurable constructor. Expose configuration to declarative recipe construction using the existing OpenRewrite/Jackson mechanism if supported; do not build a new moving framework. Preserve Math default and explicit clone contract. New family uses existing ANY/INTEGER constants and existing marked package. Approximately 100 implementation lines moved, 40 registration lines condensed, plus small recipe configuration and focused fixture changes. One substantive family commit with this note.

The researched List family requires noncontiguous registration groups, shared value-limit ownership and semantics callbacks. Map can use the existing mover directly; List remains explicitly outstanding, including sort and polymorphic operations. Final issue10 catalog-size target remains outstanding.

## Surprises
PR CI and Claude review identified the generated unused NUMBER field: the mover emitted all three type constants for every family even when only ANY and INTEGER were referenced. Corrected the existing mover first to collect unqualified identifier references from registrations and moved implementations and emit only referenced constants; then removed the unused Map NUMBER field. Math defaults and clone behavior are unchanged. No compiler checks were relaxed, and production verification remains on PR CI.

The first migration invocation left its dotted -Drewrite.activeRecipe argument unquoted in PowerShell, which split it and produced `Task '.activeRecipe=world.mongoose.banteng.builtin.ExtractMapBuiltins' not found`. Quoting that argument corrected invocation. Existing rewrite runner logged the known subproject runtimeClasspath exclusive-lock diagnostics but parsed root sources and completed successfully. The runner was not changed. Migration compilation prerequisites took most of the run; no tests were executed by the migration command.

## Next step
The family and unused-constant repair are committed locally. Parent publishes the repair to PR114, resolves its reviewed finding and obtains required PR CI before normal merge. No worker publication or broad local CI. The corrected generated production code has not been compiled locally; PR CI owns those checks. Issue 10 remains open for later families and catalog reduction.

## Validation
WSL JDK 25: `./gradlew :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest --console=plain` passed. No broad local CI was run.
The same authored fixture passed after the unused-constant repair; it verifies referenced ANY/INTEGER fields remain while qualified ArgType.NUMBER does not generate a NUMBER field. `git diff --check` passed after the repair.

WSL JDK 25 with `GRADLE_OPTS=-Dorg.gradle.configuration-cache=false`: `./gradlew :rewriteDryRun --warning-mode summary -Drewrite.activeRecipe=world.mongoose.banteng.builtin.ExtractMapBuiltins --console=plain` completed successfully. Quote the -D argument when invoking from PowerShell. Existing root compilation prerequisites execute as part of this migration program.

Read-only comparison against the pre-extraction catalog confirms the entire six-method closure matches ignoring whitespace. The rest of the catalog matches after removing imports and substituting the registration block with MapBuiltins.register(entries). Extracted registration order remains mapdelete, mapkeys, mapvalues, maphaskey. `git diff --check` passed.

## Draft PR text
Title: Extract Map builtins through the existing family mover

Moves mapdelete, mapkeys, mapvalues and maphaskey registrations and their six static implementation methods into MapBuiltins. Uses the existing registrar defaults and preserves registration order and implementation bodies. Exposes the mover's existing family configuration through Jackson properties and exercises declarative loading and clone execution in its focused fixture.

Partial delivery of #10; List and other builtin families and the final catalog-size target remain. Validation: focused MoveBuiltinFamilyTest, existing migration dry run under WSL JDK 25, and read-only body/order comparisons. Required production checks and conformance belong to PR CI. Existing rewrite-plugin subproject classpath-lock diagnostics remain documented above.
