# Verb builtin family

## Goal
Extract 11 verb registrations in four manifest groups and their 12-method closure, including parsePreposition, into VerbBuiltins. Preserve checks and WorldTxn behavior; reuse Access.resolveOwnershipPreamble and BuiltinResult.mutationResult.

## State
Implemented scoped mover/YAML/fixture and applied reviewed migration output via apply_patch. VerbBuiltins has 733 lines, 11 registrations in the original four manifest groups and the 12-method closure. Existing catalog is passed explicitly only to the metadata/code group and setVerbCode implementation. All implementations remain private static.

Validation: focused MoveBuiltinFamilyTest passed with explicit WSL Java 25. Normal ExtractVerbBuiltins migration completed with all compilation prerequisites, all sources parsed and BUILD SUCCESSFUL; source remained frozen through compilation. Read-only comparison confirmed all 12 method bodies match original after whitespace normalization and the planned catalog.spec qualifier, all 11 registration names retain order, caller/anonymization methods remain unchanged, and both normal and static BuiltinResult imports are present. git diff --check passed. No broad local CI or post-migration compilation run.

## Decisions and why
Use existing catalog.spec predicate unchanged, retaining live manifest lookup at invocation. No copied catalog, service or compiler abstraction. Keep object and anonymous-object acceptance differences intact.

## Surprises
Initial normal migration stopped at compilation because my Property generation omitted the normal BuiltinResult import: import merging confused it with the static mutationResult import. Fixed existing import deduplication first on the Property branch, restored its generated import, committed that repair and brought it into this base. The first comparison repair used deprecated printTrimmed and failed -Werror; replaced it with nondeprecated owner/final-name/staticness accessors. Focused fixture passed afterwards. Current migration uses GRADLE_OPTS=-Dorg.gradle.daemon=false through the existing wrapper to avoid the stale recipe-loader behavior independently diagnosed by the Task worker; Gradle confirms a single-use daemon that stops after the build.

## Next step
Parent verifies source, integrates shared mover/YAML changes, obtains required Claude review and publishes for PR CI. Local only; no publication performed.

## Draft PR body

Move the 11 verb registrations and their existing implementation closure into VerbBuiltins, preserving four manifest positions and object/anonymous-object checks. Pass the existing catalog explicitly to setVerbCode for its builtin-name predicate and reuse Access.resolveOwnershipPreamble and BuiltinResult.mutationResult.

Validation: focused MoveBuiltinFamilyTest; normal ExtractVerbBuiltins migration dry-run with a fresh Gradle process and normal prerequisites; read-only original/moved body, import and registration-order comparisons; git diff --check. Required Claude review and PR CI remain pending.
