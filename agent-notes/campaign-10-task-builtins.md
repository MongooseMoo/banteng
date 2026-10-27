# Task builtin extraction

## Goal
Move the 23 task registrations and their implementation closure through the existing OpenRewrite family mover, retaining manifest order, invocation hosts and continuation owners.

## State
Runtime integration preparation is complete in this existing isolated tree. The normal merge of current committed campaign/callback-command-locals resolved two catalog conflicts: retain runtime this::switchPlayer beside Task group2, and keep callFunction only in TaskBuiltins with the runtime's current continuation switch unchanged. The authored MoveBuiltinFamilyTest passed with completed source frozen throughout compilation/execution. Runtime/VM/persistence/app, BuiltinHosts/BuiltinResult and runtime test files are unchanged from the incoming runtime branch. No publication or broad local CI.

Bounded follow-up review scope since the completed Task review: TaskBuiltins imports SuspendedActivation and stores catalog.invoke's result before the exact existing Initialize/DynamicEval/Move/Recycle continuation wrapping switch; the mover changes one return-only text retarget to the bare invocation form (including result declarations); the existing Task fixture now carries that real producer and checks all four payloads, its import and idempotency. Catalog conflicts preserve existing Task insertion order and the incoming runtime switch registration, without a second callFunction or dispatcher. Preserve prior completed review coverage for unchanged extraction code and incoming runtime bodies.

Published as PR132. Initial CI caught the catalog NUMBER constant left unused after its task registrations moved. The existing mover now removes that leftover field while retaining the family's generated constant; generated catalog and authored fixture are corrected. Required hosted CI must pass on the repair.
The mover, declarative configuration, generated family/catalog migration and focused fixture are complete. The focused MoveBuiltinFamilyTest file passed, including Task dependency imports/constants and idempotency. The corrected migration runner completed normally with explicit WSL JDK 25 and normal prerequisites, and generated the required OBJECT constant and BuiltinCall import. Applied its reviewed patch through apply_patch. No publication is authorized for this worker.

## Decisions and why
The smallest change moves about 150 implementation lines and 170 registration lines into TaskBuiltins, with scoped mover adaptations. The existing catalog is captured by the call_function and eval handlers and used only when invoked after catalog construction. Both call the existing public catalog spec/invoke methods; dispatch and protected-function behavior remain owned by the catalog. configureThreadPool becomes static. The two local handler aliases are replaced directly at their registration sites. raise calls ConversionBuiltins.toStringValue.

## Surprises
The second migration run reused the first recipe's classes despite rebuilding its jar: its patch omitted the newly added OBJECT constant and BuiltinCall import, while the authored fixture passed assertions for both against current compiled recipe classes. The installed plugin jar's org.openrewrite.gradle.DelegatingProjectParser constructor bytecode (javap -private -c, offsets 78–139) shows static rewriteClassLoader reuse when its classpath URL list and plugin classloader are unchanged; jar content changes at the same path do not invalidate it. scripts/rewrite.sh now invokes --no-daemon so every recipe invocation loads its completed jar. This corrects the existing invocation rather than adding a verification gate or restarting an unresolved process. Parent explicitly assigned this shared runner repair to this task.

## Next step
Parent integrates this substantive local merge, performs bounded Claude follow-up on the changes named above while retaining completed review coverage, and owns publication/CI. Full production compilation/conformance of the integrated family has not been run locally; this belongs to PR CI. Exact integration validation: explicit WSL JAVA_HOME=/opt/java/25 and PATH=/opt/java/25/bin:/usr/bin:/bin; `./gradlew :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest` passed. git diff --check passed.

## Draft PR body
Moves the 23 task builtin registrations and existing implementations into TaskBuiltins, preserving their manifest positions, permissions, flags, host operations and continuation results. call_function and eval retain explicit access to the existing catalog without duplicating dispatch. Integration retains current runtime reflected-builtin continuation metadata and its switch-player handler rather than restoring the older result behavior.

Extends the existing OpenRewrite family mover and declarative recipe with focused migration coverage.

Validation: WSL JDK 25 scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractTaskBuiltins completed normally, with standard normal prerequisites and the plugin's existing configuration-cache/classpath warnings. :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest passed; its invocation after applying generated source was up-to-date. git diff --check passed. No broad local CI steps were run. Full conformance and required checks belong to PR CI.
