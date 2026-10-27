# Object builtin family

## Goal
Extract the 23 object registrations and existing static closure through MoveBuiltinFamily, preserving their three manifest positions. Move the existing mutation result conversion directly into BuiltinResult for its nine callers. Keep object_bytes and VM lifecycle execution in their current owners.

## State
Implemented scoped recipe/YAML and applied reviewed migration output through apply_patch. ObjectBuiltins has 640 lines, owns 23 registrations in the original three positions and the 24 existing static implementation methods plus ParentArgument. BuiltinResult owns the original mutationResult body; two family calls and seven retained catalog calls import that owner directly.

Validation: focused MoveBuiltinFamilyTest passed with explicit WSL Java 25. Normal scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractObjectBuiltins completed successfully with normal compilation prerequisites; all sources parsed. Read-only comparison confirmed all 24 moved implementation bodies match original after whitespace normalization and each registration group's order matches original. git diff --check passed. No broad local CI or post-migration compilation run; parent owns publication, CI and review.

## Decisions and why
Use existing registrar and AST mover. Preserve object/anonymous/WAIF checks, mutation and continuation ownership, and flags unchanged. No service or wrapper.

## Surprises
OpenRewrite emitted Gradle runtimeClasspath exclusive-lock warnings for subprojects and configuration-cache incompatibility warnings; the runner nevertheless parsed all sources, generated exactly the three intended production file changes and reported BUILD SUCCESSFUL.

## Next step
Parent verifies source, integrates sibling recipe/YAML changes as needed, obtains required Claude review and publishes for PR CI. This branch remains local only.

## Draft PR body

Move the 23 object lifecycle, topology, WAIF and maximum-object registrations into ObjectBuiltins through the existing family mover, preserving their three manifest positions and existing implementations. Move the shared mutation-result conversion into BuiltinResult for its nine existing callers. Keep VM lifecycle execution and object_bytes in their current owners.

Validation: focused MoveBuiltinFamilyTest; normal ExtractObjectBuiltins rewrite dry-run; read-only original/moved body and registration-order comparison; git diff --check. Required PR CI and Claude review remain pending.
