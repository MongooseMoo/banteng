# Object builtin family

## Goal
Extract the 23 object registrations and existing static closure through MoveBuiltinFamily, preserving their three manifest positions. Move the existing mutation result conversion directly into BuiltinResult for its nine callers. Keep object_bytes and VM lifecycle execution in their current owners.

## State
Implemented scoped recipe/YAML and applied reviewed migration output through apply_patch. ObjectBuiltins owns 23 registrations in the original three positions and the 24 existing static implementation methods plus ParentArgument. BuiltinResult owns the original mutationResult body; two family calls and seven retained catalog calls import that owner directly.

Final assembly integration now merges current campaign/callback-command-locals, including WAIF, switch-player, callback-entry and saved-player changes. ObjectBuiltins retains current anonymous Initialize Optional.empty and permanent create/recreate CreateContinuation(false/true,id) payloads directly. No compatibility constructor or runtime-tree edit. Current sealed BuiltinResult is preserved exactly except the existing WorldResult import and mutationResult addition.

Validation: focused MoveBuiltinFamilyTest passed with explicit WSL Java 25. Normal scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractObjectBuiltins completed successfully with normal compilation prerequisites; all sources parsed. Read-only comparison confirmed all 24 moved implementation bodies match original after whitespace normalization and each registration group's order matches original. git diff --check passed. No broad local CI or post-migration compilation run; parent owns publication, CI and review.

## Decisions and why
Use existing registrar and AST mover. Preserve object/anonymous/WAIF checks, mutation and continuation ownership, and flags unchanged. No service or wrapper.

## Surprises
OpenRewrite emitted Gradle runtimeClasspath exclusive-lock warnings for subprojects and configuration-cache incompatibility warnings; the runner nevertheless parsed all sources, generated exactly the three intended production file changes and reported BUILD SUCCESSFUL.

Final assembly conflicted at catalog sections already extracted to this family and the adjacent switchPlayer header. Resolution retains extracted ownership and the current switchPlayer(BuiltinCall) declaration. Read-only current-catalog comparison confirms all24 moved implementation bodies match after whitespace normalization. The authored Object recipe fixture now covers all three existing Initialize payload forms and retains idempotence/shared mutation helper checks.

## Next step
Published as PR129; this latest integration remains local only. Earlier Claude review and original PR CI are historical evidence, not acceptance of the new integration delta. Parent owns any bounded review of the three payload/import adaptations and recipe fixture, publication and current PR CI before normal merge.

## Final integration validation

Explicit WSL JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin ./gradlew --no-daemon :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest passed with source frozen. git diff --check passed. No broad local CI or root production compilation; parent owns those hosted checks. No recipe implementation or YAML changes were required by this integration.

## Draft PR body

Move the 23 object lifecycle, topology, WAIF and maximum-object registrations into ObjectBuiltins through the existing family mover, preserving their three manifest positions and existing implementations. Move the shared mutation-result conversion into BuiltinResult for its nine existing callers. Keep VM lifecycle execution and object_bytes in their current owners.

Integrate current runtime continuation metadata into the existing extraction: anonymous create retains its empty payload, while permanent create/recreate retain their existing typed CreateContinuation payloads. Preserve the current sealed result definitions, mutationResult owner, extraction recipe and three manifest positions.

Validation: focused MoveBuiltinFamilyTest; original normal ExtractObjectBuiltins rewrite dry-run; current-catalog/moved24-body comparison; git diff --check. Parent owns publication, bounded review of the integration delta and current PR CI.

## Login and layout repair ancestry
Merged the latest coordinator runtime login-output and pinned-layout-boundary repairs normally, without conflicts or family source adaptations. Family implementations, services, dependencies, extraction mover, authored fixtures and YAML are unchanged. No local tests, CI, migration or repeated review ran for this mechanical integration. Parent owns publication, hosted checks and merge.

## Queue prerequisite integration
Normally merged the published Crypto tip, including reviewed Host, Regex, Binary and SQLite prerequisites. Mover resolution retains both Object ParentArgument and Crypto nested types, all independent family generation/import branches and members. Fixture and YAML conflicts retain every existing family section. Reused catalog resolution retains the original Object registration positions alongside the prerequisite families and canonical catalog-owned crypt dependency, matching final assembly. ObjectBuiltins and incoming family owners remain byte-identical. No local tests, repeated review or publication; parent owns hosted validation and merge.
