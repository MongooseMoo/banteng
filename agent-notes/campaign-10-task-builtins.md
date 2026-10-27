# Task builtin extraction

## Goal
Move the 23 task registrations and their implementation closure through the existing OpenRewrite family mover, retaining manifest order, invocation hosts and continuation owners.

## State
The mover, declarative configuration, generated family/catalog migration and focused fixture are complete. The focused MoveBuiltinFamilyTest file passed, including Task dependency imports/constants and idempotency. The corrected migration runner completed normally with explicit WSL JDK 25 and normal prerequisites, and generated the required OBJECT constant and BuiltinCall import. Applied its reviewed patch through apply_patch. No publication is authorized for this worker.

## Decisions and why
The smallest change moves about 150 implementation lines and 170 registration lines into TaskBuiltins, with scoped mover adaptations. The existing catalog is captured by the call_function and eval handlers and used only when invoked after catalog construction. Both call the existing public catalog spec/invoke methods; dispatch and protected-function behavior remain owned by the catalog. configureThreadPool becomes static. The two local handler aliases are replaced directly at their registration sites. raise calls ConversionBuiltins.toStringValue.

## Surprises
The second migration run reused the first recipe's classes despite rebuilding its jar: its patch omitted the newly added OBJECT constant and BuiltinCall import, while the authored fixture passed assertions for both against current compiled recipe classes. The installed plugin jar's org.openrewrite.gradle.DelegatingProjectParser constructor bytecode (javap -private -c, offsets 78–139) shows static rewriteClassLoader reuse when its classpath URL list and plugin classloader are unchanged; jar content changes at the same path do not invalidate it. scripts/rewrite.sh now invokes --no-daemon so every recipe invocation loads its completed jar. This corrects the existing invocation rather than adding a verification gate or restarting an unresolved process. Parent explicitly assigned this shared runner repair to this task.

## Next step
Parent owns independent source verification, Claude review, publication and CI. The task is recorded in one substantive local commit. Full production compilation/conformance of the extracted family has not been run locally; this belongs to PR CI.

## Draft PR body
Moves the 23 task builtin registrations and existing implementations into TaskBuiltins, preserving their manifest positions, permissions, flags, host operations and continuation results. call_function and eval retain explicit access to the existing catalog without duplicating dispatch.

Extends the existing OpenRewrite family mover and declarative recipe with focused migration coverage.

Validation: WSL JDK 25 scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractTaskBuiltins completed normally, with standard normal prerequisites and the plugin's existing configuration-cache/classpath warnings. :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest passed; its invocation after applying generated source was up-to-date. git diff --check passed. No broad local CI steps were run. Full conformance and required checks belong to PR CI.
