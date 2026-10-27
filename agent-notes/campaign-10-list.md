# Issue10: List builtin family

## Goal
Extract the complete List family through existing mover and registrar with behavior/manifest order unchanged. Partial issue10; final assembler target remains outstanding.

## State
List extraction implemented locally. Map114 and Runtime115 prerequisite source is integrated in this tree; parent owns their live publication/CI/merge state. Parent read every selected implementation, shared limits closure, natural sort helpers, reverse/length branches and actual shared ASCII callers.

## Decisions and why
One family owns length, listappend, listinsert, listdelete, listset, setadd, setremove, all_members, sort, reverse. Keep polymorphic length/reverse implementations whole. Four direct registration methods preserve existing order: length, list edits, sets/all_members/sort, reverse. Map/JSON/explode stay at their existing interleaved positions. Move local allMembers/sort handlers with their group, preserving thread-mode host work and cancellation. Use call.hosts().valueSemantics from runtime prerequisite for existing configured semantics; retain sort's existing STANDARD comparison.

Move valueBytes, list limit constants and enforceListValueLimit into builtin.ValueLimits and retarget actual catalog value_bytes/objectBytes, ListBuiltins and vm.ListOps callers directly. No catalog forwarding methods. Shared foldAscii belongs in existing ValueSemantics: move three-line helper, use it from sort/string translation/matches and the comparator's identical inline folds. No new ASCII facade.

Extend existing MoveBuiltinFamily only for concrete four registration groups and selected local handlers/shared closure retargeting. Reuse identifier-selected constants. Roughly310 existing production lines moved,45–65 registration lines,90–140 recipe changes,15–25 caller/import changes. One substantive family commit; conformance on PR before next family. No semantic fixes folded into extraction, no new guards or broad local CI.

## Next step
Parent: review the substantive commit, publish the PR, obtain Claude review and read PR CI including conformance before merging. Required dependencies must merge before List PR merges. This is a partial issue10 delivery; the final assembler target remains outstanding.

## Surprises

The authored fixture exposed that the existing mover visitor stopped at non-manifest methods and therefore skipped the ListOps invocation; corrected traversal in that visitor. Migration succeeded twice. OpenRewrite logged subproject runtimeClasspath exclusive-lock resolution exceptions but continued through root source parsing and exited successfully. The dry-run report described existing ValueSemantics as new even after reparsing retained its compilation-unit ID; the authored fixture proves its result retains a before-file. The exact runner-metadata cause is unverified. Inspected the generated content: all original ValueSemantics content remains, with only the two inline folds replaced and the shared helper appended. Applied that bounded update directly to the existing file with apply_patch, preserving its original content and path.

## Source evidence

Before migration, BuiltinCatalog buildManifest lines244–253 contain the two local handlers and thread-mode hostWork selection. Its length registration at272, list edit registrations348–387, set/sort registrations405–440, and reverse registration449 are interleaved with Math, Map, JSON and explode. Length at2238 handles string/list/map; reverse at3052 handles string/list and returns E_INVARG otherwise. The list edit/set methods at2559–2626 call the shared limit; enforceListValueLimit at2629–2653 consults server options and returns either E_QUOTA or SecondsAbort. valueBytes at2365–2404 recursively sizes lists/maps/waifs. Sort at2671–2830 retains STANDARD comparison, cancellation checks and ASCII natural-sort helpers. Catalog string translation at2872/2879 and matchesAt at3694/3695 share foldAscii at3704. ValueSemantics compareStrings contains the identical inline ASCII fold. vm.ListOps pushCheckedList directly calls the old catalog limit method at61.

## Validation

Only the actively authored recipe test file was run: `gradlew.bat :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest`. Passed after correcting visitor traversal. The migration runner is the existing `:rewriteDryRun` task with ExtractListBuiltins, Java25 in WSL, configuration cache disabled and warning-mode summary. Its compilation prerequisites are part of running the migration; no broad local CI task was requested.

Both migration dry runs exited successfully. Generated production changes were applied with apply_patch after each runner had exited. A read-only comparison of all twenty moved method bodies against the prerequisite baseline matched after whitespace normalization; this includes seventeen List methods, two ValueLimits methods and foldAscii. Current ListBuiltins registerLength/registerListEdits/registerSetsAndSort/registerReverse are at35/44/66/93; its original sort STANDARD comparison is at260. Catalog calls them at260/326/342/350, preserving the interleaved positions. ValueLimits owns valueBytes/enforceListValueLimit at24/65. ValueSemantics owns foldAscii at120; ListOps calls the shared limit at54. Production after extraction has not been compiled or behavior-tested locally; PR CI must establish that result.

## Draft publication description

Extract the ten List builtins into ListBuiltins through the existing mover and registrar. Four registration methods preserve the existing manifest positions around Math, Map, JSON and explode; all_members/sort retain their thread-mode host work and cancellation behavior. Move list value limits to ValueLimits for direct catalog/family/VM callers, and share the existing ASCII fold through ValueSemantics. This is partial issue10 work.

Validation: authored MoveBuiltinFamilyTest; successful ExtractListBuiltins migration dry run. Required PR checks and Claude review remain parent-owned.
