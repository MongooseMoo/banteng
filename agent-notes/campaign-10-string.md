# Core String builtin extraction

## Goal
Continue issue 10 using the existing MoveBuiltinFamily recipe, preserving behavior and manifest order.

## State
The eight core text builtins are extracted into StringBuiltins. The existing mover generated exactly StringBuiltins.java, BuiltinCatalog.java and ValueSemantics.java; its dry-run patch was applied through apply_patch. The authored MoveBuiltinFamilyTest passed under WSL JDK 25. No publication or broad local CI. This worktree starts from the preceding List branch, whose PR checks passed; parent owns dependency integration and publication.

## Decisions and why
Smallest change: move eight core text registrations (explode, strsub, strtr, parse_ansi, remove_ansi, index, rindex, strcmp) and their existing implementation closure into StringBuiltins. Keep three registration groups around reverse and simplex_noise. Approximately 260 implementation lines move, with 30–45 registration lines and 60–100 recipe lines plus a focused migration fixture.

BuiltinCatalog lines 2474–2536 use the catalog's shared Random for parse_ansi: pass that same instance explicitly, without a new generator. matchesAt at 3234–3251 also serves locateByName at 3631: move it into existing ValueSemantics beside foldAscii and retarget both callers directly. Include the existing AnsiReplacement record. Extend the existing recipe only for these concrete members and groups. Binary, regex, conversions and remaining text utilities remain later families; this does not finish issue 10.

## Next step
Parent checks primary source, integrates the merged List dependency, publishes, obtains Claude review, follows CI and merges. The migration runner compiled the source before applying its generated extraction; required checks on the resulting production code remain with PR CI.

## Validation
- WSL JDK 25: ./gradlew :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest --console=plain.
- WSL JDK 25 with configuration cache disabled: bash scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractStringBuiltins. Normal compilation prerequisites ran unchanged; dry run succeeded and proposed exactly the three production files named above.
- Read-only source comparison established that all nine moved implementation bodies and the shared matchesAt body match their originals ignoring whitespace. The parseAnsi signature alone gains static and the explicit Random parameter; its body remains unchanged.
- git diff --check.

## Surprises
The existing rewrite plugin again logged IllegalResolutionException for subproject runtimeClasspath resolution without an exclusive lock. It continued, parsed the root sources, and completed successfully. This extraction does not repair the plugin diagnostic or change the runner. The focused fixture covers the three manifest positions, shared Random argument, irrevocable registration, nested record, STRING constant, shared matchesAt owner, and no-op second recipe application.
