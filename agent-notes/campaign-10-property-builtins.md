# Property builtin family

## Goal
Move six property registrations and exactly six implementations into PropertyBuiltins, preserving manifest position. Move the existing ownership preamble into Access for addVerb/addProperty. Reuse existing BuiltinResult.mutationResult.

## State
Published as PR133 with completed review. Prepared the normal merge of latest campaign/10-object-builtins, including its current runtime/WAIF ancestry and corrected Initialize continuation producers. Catalog and shared recipe fixture merged mechanically without conflicts. PropertyBuiltins, Access ownership preamble, the mover, Property fixture and rewrite.yml are unchanged from the reviewed Property head. The shared fixture file inherits only the incoming Object Initialize-payload coverage. ObjectBuiltins, BuiltinHosts/BuiltinResult and all incoming runtime/VM/persistence/bytecode/app and runtime test bodies are unchanged. No Property implementation/mover adaptation, local CI or repeat review was needed.

Applied reviewed migration output via apply_patch. PropertyBuiltins has 276 lines and owns exactly six registrations and methods. Existing Access owns the unchanged ownership preamble; addVerb/addProperty import it directly. Existing BuiltinResult owns mutationResult as before.

Validation: focused MoveBuiltinFamilyTest passed with explicit WSL Java 25. Normal scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractPropertyBuiltins completed with normal compilation prerequisites, all sources parsed and BUILD SUCCESSFUL. Read-only comparison confirmed all six implementation bodies and the ownership preamble match original after whitespace normalization; retained addVerb, callers and anonymizeTaskReference bodies also match. Registration order preserved. git diff --check passed. No broad local CI or post-migration compilation run.

## Decisions and why
Preserve permission checks, object/anonymous/WAIF acceptance differences and WorldTxn calls unchanged. Keep task methods in catalog and use existing shared owners directly.

## Surprises
Runner emitted existing Gradle runtimeClasspath exclusive-lock and configuration-cache compatibility warnings; it nevertheless parsed all sources and generated only PropertyBuiltins, BuiltinCatalog and Access changes. The existing runner session finished successfully during interruption and was inspected on resume; it was not restarted.

## Next step
Parent owns publication and required PR CI after runtime lands. Object must land before Property is retargeted to master and merged. Reuse the completed Property review because its code and Access ownership are unchanged; ancestry integration does not require another review. No publication performed by this worker.

Earlier Verb migration compilation exposed that generated PropertyBuiltins omitted its normal BuiltinResult import. The mover merged imports solely by owner type name and incorrectly treated the existing static mutationResult import as equivalent. Corrected the existing import comparison to compare the full imported name and staticness, then restored PropertyBuiltins' missing normal import. No behavior changed.

## Draft PR body

Move the six property registrations and existing implementations into PropertyBuiltins through the family mover, preserving their manifest position. Move the shared ownership preamble into existing Access for addVerb/addProperty and reuse BuiltinResult.mutationResult. Keep task caller anonymization in its current owner.

Validation: focused MoveBuiltinFamilyTest; normal ExtractPropertyBuiltins rewrite dry-run; read-only original/moved body and registration-order comparisons; git diff --check. Required PR CI and Claude review remain pending.
