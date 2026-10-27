# Regex builtin extraction

## Goal
Issue10: move match/rmatch and PCRE registrations beside the existing package-private services, preserving behavior and catalog ordering.

## State
Published as PR127 with completed initial Claude review. Integrated merged Random work, preserving both family implementations. Two shared mover expressions combine the existing family selections and apply the Regex call replacement after the existing Random registration helper. Latest runtime/callback/WAIF ancestry is now merged locally without conflicts; this mechanical integration changes no Regex implementation, service, mover, fixture or YAML code. Existing completed reviews remain reusable for unchanged code.

## Decisions and why
Smallest change from the approved campaign plan: relocate the two existing services unchanged into builtin.family, move the seven-line toastMatch wrapper to private static with an explicit ToastRegexService argument, and move five registrations in two separated groups to RegexBuiltins.registerLegacy/registerPcre. Allocate one service in each registration closure per catalog, preserving shared PCRE cache, synchronization, flags, permissions and irrevocable behavior. About432 service lines and39 registration lines move, with roughly25 scaffold lines and a focused mover adaptation/test. Reuse the existing MoveBuiltinFamily recipe, including the service relocations; no facade or replacement regex implementation. Retarget the historical PcreService method pattern.

## Surprises
ChangeType alone moved the service package declarations without adding the BuiltinResult import those package-private services now need. The scoped mover therefore relocates those units directly, adds that import, and preserves file attributes. Both service bodies compare exactly with baseline after removing only package/import changes. The migration runner logged existing subproject classpath-lock and configuration-cache diagnostics but completed successfully. The initial focused test fixture failed compilation because its parser stream was typed SourceFile; explicitly casting to CompilationUnit corrected it.

## Next step
Parent owns source verification, Claude review, publication, PR CI and merge. Production after migration has not been compiled locally; PR CI supplies that verification.

## Runtime ancestry integration
Merged the current campaign/callback-command-locals ancestry normally into the existing clean Regex tree. No conflict or source adaptation was needed. Compared RegexBuiltins, ToastRegexService, PcreService, the rewrite subproject and rewrite.yml against the prior Regex head: no changes. Compared runtime, VM, persistence, bytecode, app and all test files against the captured runtime merge parent: no differences. Its saved receiver/player, switch, lag/outcome, continuation and WAIF assignment bodies are preserved exactly. The catalog merge retains the existing Regex extraction registration positions.

No local CI/test/migration or new review was run for this unchanged code/ancestry integration. git diff --check passed. Parent owns publication and PR CI; the task remains local until parent publishes it.

## Validation
- WSL JDK25: bash scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractRegexBuiltins completed with normal compilation prerequisites against frozen source; its patch changed exactly the four planned production files.
- WSL JDK25: ./gradlew :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest passed on final source, including file-attribute preservation.
- Read-only comparison: both service bodies are identical to baseline except package declaration and BuiltinResult import.
- Read catalog insertion positions and all five shapes, permissions and irrevocable flags against the removed specifications and BuiltinRegistrar defaults.
- git diff --check.

## Draft PR body
Moves legacy and PCRE registrations into RegexBuiltins beside the existing package-private services. Each catalog retains one service per registration group, including the PCRE cache shared by its handlers. Registration order, permissions and irrevocable behavior remain the same.

Validation: the scoped migration dry-run, focused MoveBuiltinFamilyTest file, read-only service-body comparison and git diff --check. PR CI must run the existing conformance workflow.

## Login and layout repair ancestry
Merged the latest coordinator runtime login-output and pinned-layout-boundary repairs normally, without conflicts or family source adaptations. Family implementations, services, dependencies, extraction mover, authored fixtures and YAML are unchanged. No local tests, CI, migration or repeated review ran for this mechanical integration. Parent owns publication, hosted checks and merge.

## Queue overlap integration
Normally merging the published, reviewed Binary and SQLite extraction tips so Regex can follow both in the merge queue. Binary overlap in the existing mover, focused fixture and YAML is additive: retain both families' original sections; the catalog merged automatically. RegexBuiltins and its two package-private services remain byte-identical. No local tests or repeated review run for this mechanical integration; parent owns publication and hosted checks.
