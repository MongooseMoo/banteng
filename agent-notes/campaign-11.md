# Issue #11: durable task codec

## Goal
Move scheduler durable conversion into persistence.DurableTaskCodec using a custom OpenRewrite recipe after manual boundary preparation, replace the concrete runtime callback field with its actual interface, and enforce the compiled callback dependency direction with ArchUnit.

## State
Read issue #11, the scheduler conversion closure, scheduler callback calls, runtime callback implementations, existing OpenRewrite mechanism and ArchitectureTest. Branch campaign/11-durable-task-codec starts at 90ecb19 in an isolated worktree. At this implementation commit, worker had not published; coordinator owns publication, Claude review and hosted CI.

## Decisions and why
Smallest change: approximately 1,000 moved lines, a roughly 150-line recipe and the actual callback interface. Restore registration, timers and VmSnapshot construction remain scheduler-owned for #19. Export currently takes private TimedWork/SuspendedWork; expose its existing primitive/program/snapshot/wake inputs instead, keeping cache selection and continuation rejection in scheduler. This avoids persistence depending on runtime without new record protocols.

## Surprises

Coordinator found that generated DurableTaskCodec referenced OptionalLong.empty four times without importing OptionalLong. The pre-extraction PublicationScheduler imported java.util.OptionalLong. The original intermediate source/AST is unavailable, so the stage that lost the import is unknown. The earlier claim that cleanup loses static-call owners was unsupported: Collections survives, and OpenRewrite 8.89.0 RemoveUnusedImports collects source identifier names and attributed unqualified type names (upstream RemoveUnusedImports.java lines269-284); CompilationUnit.getTypesInUse rebuilds when its cached compilation unit differs. The recipe explicitly adds the import required by the generated importActivation references; this is not evidence that the original cause was fixed. The final codec's restored OptionalLong import remains. Source inspection found no other missing imports. No local CI suite or production compilation was run for this repair.

A temporary authored DurableCodecImportDiagnosticTest parsed the original 90ecb19 scheduler with only durableTask renamed to exportQueuedTask to satisfy the recipe inventory, retaining the original importActivation AST. Its input OptionalLong identifiers were attributed java.util.OptionalLong. It generated the moved class and then applied RemoveUnusedImports again: OptionalLong remained in both generated and cleaned imports. This bounded reconstruction did not reproduce the loss and does not reconstruct the original manual scalar export preparation. The focused diagnostic command was :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.DurableCodecImportDiagnosticTest --no-configuration-cache with JDK25; it completed successfully. The temporary diagnostic was removed after use, not added as a guard. Its first invocation failed because Linux Git could not resolve this Windows worktree's .git path; using Windows git.exe for the read-only git show corrected that diagnostic setup.
The issue's pure static exports depend on private scheduler records; copying them unchanged would violate the existing persistence dependency rule. Scheduler also uses MooRuntime nested data types; the interface cuts its concrete field/constructor callback coupling while preserving those existing data contracts.

The requested rewrite program compiles all source sets before applying recipes. The first attempt failed because I referenced the destination before generation; recipe now retargets the initially local calls itself. Rewrite's Gradle plugin cannot use this project's configuration cache, so this invocation sets GRADLE_OPTS=-Dorg.gradle.configuration-cache=false without changing repository configuration. Compilation is prerequisite tool work, not a local CI gate.

Before running the recipe, boundary preparation was manual: split the queued export out of durableTask into exportQueuedTask, change queued/suspended export inputs from scheduler-private TimedWork/SuspendedWork to their existing scalar/program/snapshot values, and keep BuiltinResult resume-value conversion in the scheduler. The recipe's METHODS requires exportQueuedTask, which does not exist at base 90ecb19: it operates on that prepared intermediate, not the untouched base. SchedulerRuntime and MooRuntime callback adaptation were also manual.

The requested rewrite completed successfully in Debian with JDK 25 (8m43) on the manually prepared intermediate. It moved 31 static methods and four records. Comparison against the starting source found 29 method texts unchanged except public entrypoint visibility. Restore registration and construction remain scheduler-owned.

SchedulerRuntime declares the 18 existing callbacks, MooRuntime implements them with unchanged bodies, and scheduler fields/constructors accept the interface. ArchitectureTest checks the compiled dependency on concrete MooRuntime for the callback field/calls. It does not forbid every source reference: DEFAULT_* constants still reference MooRuntime in source and are inlined by Java; existing nested MooRuntime data types also remain. Existing structural activation round-trip test helpers target DurableTaskCodec. No new source-shape assertions were added.

## PR draft
Title: Extract durable task conversion and narrow scheduler runtime callbacks

Manually prepare the scheduler-private export boundary, then move its static durable task import/export closure and four support records into persistence.DurableTaskCodec with OpenRewrite. Keep restore registration, timers and snapshot construction in the scheduler; manually use its actual SchedulerRuntime callbacks instead of a concrete runtime field. Existing activation round-trip tests use the new owner, and ArchUnit checks the compiled concrete callback dependency. Existing MooRuntime constants and nested data types remain source references.

Closes #11.

Validation: requested scripts/rewrite.sh --apply world.mongoose.banteng.persistence.ExtractDurableTaskCodec completed with -Werror compilation prerequisites. Hosted CI must validate the final refactor, persistence and restart suites; the worker did not run those suites locally.

Follow-up: restore the generated codec's required OptionalLong import and explicitly include that import in the recipe output. The original import-loss stage remains unknown; source inspection found no other missing imports. Hosted CI must compile this final head.

Focused edited-file validation: :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.RecipeCatalogTest passed. The requested extraction command logged Gradle plugin classpath-lock warnings during subproject scanning but completed successfully and generated the codec. Final production compilation and ArchUnit execution remain unverified until PR CI.

## Next step
Commit the scoped candidate and pass its exact commit to coordinator for Claude review, publication and CI. At this implementation commit, worker had not published; central campaign note owns live PR state.
