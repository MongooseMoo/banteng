# Regex builtin extraction

## Goal
Issue10: move match/rmatch and PCRE registrations beside the existing package-private services, preserving behavior and catalog ordering.

## State
Published as PR127 with completed initial Claude review. Integrated merged Random work, preserving both family implementations. Two shared mover expressions now combine the existing family selections and apply the Regex call replacement after the existing Random registration helper; review of those actual integration edits and fresh CI follow. Existing regex production implementations are unchanged.

## Decisions and why
Smallest change from the approved campaign plan: relocate the two existing services unchanged into builtin.family, move the seven-line toastMatch wrapper to private static with an explicit ToastRegexService argument, and move five registrations in two separated groups to RegexBuiltins.registerLegacy/registerPcre. Allocate one service in each registration closure per catalog, preserving shared PCRE cache, synchronization, flags, permissions and irrevocable behavior. About432 service lines and39 registration lines move, with roughly25 scaffold lines and a focused mover adaptation/test. Reuse the existing MoveBuiltinFamily recipe, including the service relocations; no facade or replacement regex implementation. Retarget the historical PcreService method pattern.

## Surprises
ChangeType alone moved the service package declarations without adding the BuiltinResult import those package-private services now need. The scoped mover therefore relocates those units directly, adds that import, and preserves file attributes. Both service bodies compare exactly with baseline after removing only package/import changes. The migration runner logged existing subproject classpath-lock and configuration-cache diagnostics but completed successfully. The initial focused test fixture failed compilation because its parser stream was typed SourceFile; explicitly casting to CompilationUnit corrected it.

## Next step
Parent owns source verification, Claude review, publication, PR CI and merge. Production after migration has not been compiled locally; PR CI supplies that verification.

## Validation
- WSL JDK25: bash scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractRegexBuiltins completed with normal compilation prerequisites against frozen source; its patch changed exactly the four planned production files.
- WSL JDK25: ./gradlew :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest passed on final source, including file-attribute preservation.
- Read-only comparison: both service bodies are identical to baseline except package declaration and BuiltinResult import.
- Read catalog insertion positions and all five shapes, permissions and irrevocable flags against the removed specifications and BuiltinRegistrar defaults.
- git diff --check.

## Draft PR body
Moves legacy and PCRE registrations into RegexBuiltins beside the existing package-private services. Each catalog retains one service per registration group, including the PCRE cache shared by its handlers. Registration order, permissions and irrevocable behavior remain the same.

Validation: the scoped migration dry-run, focused MoveBuiltinFamilyTest file, read-only service-body comparison and git diff --check. PR CI must run the existing conformance workflow.
