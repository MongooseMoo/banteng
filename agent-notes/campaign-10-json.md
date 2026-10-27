# JSON builtin extraction

## Goal
Continue issue10 with the existing family mover; preserve JSON behavior and registration order.

## State
The two JSON registrations and their existing wrappers now live in JsonBuiltins, with JsonCodec beside them and still package-private. The existing MoveBuiltinFamily and ChangeType generated exactly those three production file changes; the dry-run patch was applied through apply_patch. This isolated branch starts from String, whose PR completed Claude review and all checks including conformance. No publication or broad local CI; parent owns integration and publication.

## Decisions and why
Smallest change: move generate_json/parse_json registrations and generateJson/parseJson wrappers into JsonBuiltins. Parent read the catalog wrappers, all JsonCodec callers and its package-private visibility. Move the existing566-line JsonCodec unchanged into builtin.family beside its wrappers; preserve package-private API, with no facade. About31 implementation lines and14 registration lines leave catalog,20–30 scaffold/registration lines and12–18 declarative recipe lines are added. Reuse String's STRING constant support and existing MoveBuiltinFamily plus ChangeType to relocate the codec. Preserve the group between Map registration and List sets/sort. No JSON semantic changes or new generic migration machinery.

## Next step
Parent checks source, integrates the String dependency, publishes, obtains Claude review and merges after PR CI. No mover extension or new fixture was needed: the existing generic recipe already handles this family and ChangeType handles codec relocation.

## Validation
- WSL Debian JDK 25: bash scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractJsonBuiltins completed successfully. Normal compilation prerequisites ran against frozen pre-migration source; the resulting production changes await PR CI.
- Read-only source comparison confirmed generateJson and parseJson are unchanged ignoring whitespace, and JsonCodec is unchanged except its package declaration.
- BuiltinCatalog retains the JSON registration call between MapBuiltins.register and ListBuiltins.registerSetsAndSort. JsonBuiltins retains generate_json before parse_json and both original CallShapes. BuiltinRegistrar defaults to ANY permission and false irrevocable, matching the removed specifications.
- git diff --check.

## Surprises
The existing rewrite plugin logged subproject runtimeClasspath IllegalResolutionException diagnostics and configuration-cache incompatibilities, then parsed root sources and completed successfully. The generated patch contains precisely the planned production changes. This task does not alter the plugin or its runner.
