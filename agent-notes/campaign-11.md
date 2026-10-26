# Issue #11: durable task codec

## Goal
Move scheduler durable conversion into persistence.DurableTaskCodec using a custom OpenRewrite recipe, replace the concrete runtime callback field with its actual interface, and enforce the field direction with ArchUnit.

## State
Read issue #11, the scheduler conversion closure, scheduler callback calls, runtime callback implementations, existing OpenRewrite mechanism and ArchitectureTest. Branch campaign/11-durable-task-codec starts at 90ecb19 in an isolated worktree. At this implementation commit, worker had not published; coordinator owns publication, Claude review and hosted CI.

## Decisions and why
Smallest change: approximately 1,000 moved lines, a roughly 150-line recipe and the actual callback interface. Restore registration, timers and VmSnapshot construction remain scheduler-owned for #19. Export currently takes private TimedWork/SuspendedWork; expose its existing primitive/program/snapshot/wake inputs instead, keeping cache selection and continuation rejection in scheduler. This avoids persistence depending on runtime without new record protocols.

## Surprises
The issue's pure static exports depend on private scheduler records; copying them unchanged would violate the existing persistence dependency rule. Scheduler also uses MooRuntime nested data types; the interface cuts its concrete field/constructor callback coupling while preserving those existing data contracts.

The requested rewrite program compiles all source sets before applying recipes. The first attempt failed because I referenced the destination before generation; recipe now retargets the initially local calls itself. Rewrite's Gradle plugin cannot use this project's configuration cache, so this invocation sets GRADLE_OPTS=-Dorg.gradle.configuration-cache=false without changing repository configuration. Compilation is prerequisite tool work, not a local CI gate.

The requested rewrite completed successfully in Debian with JDK 25 (8m43). It moved 31 static methods and four records. Comparison against the starting source found 29 method texts unchanged except public entrypoint visibility; the queued and suspended exports take the existing values instead of scheduler-private records. BuiltinResult resume-value conversion stays in scheduler to preserve the existing persistence package dependency boundary. Restore registration and construction remain scheduler-owned.

SchedulerRuntime declares the 18 existing callbacks, MooRuntime implements them with unchanged bodies, and scheduler fields/constructors accept the interface. ArchitectureTest now forbids the scheduler depending on the concrete MooRuntime class. Existing structural activation round-trip test helpers target DurableTaskCodec. No new source-shape assertions were added.

## PR draft
Title: Extract durable task conversion and narrow scheduler runtime callbacks

Move the scheduler's static durable task import/export closure and four support records into persistence.DurableTaskCodec. Keep restore registration, timers and snapshot construction in the scheduler; use its actual SchedulerRuntime callbacks instead of a concrete runtime field. Existing activation round-trip tests use the new owner, and ArchUnit enforces the concrete runtime boundary.

Closes #11.

Validation: requested scripts/rewrite.sh --apply world.mongoose.banteng.persistence.ExtractDurableTaskCodec completed with -Werror compilation prerequisites. Hosted CI must validate the final refactor, persistence and restart suites; the worker did not run those suites locally.

Focused edited-file validation: :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.RecipeCatalogTest passed. The requested extraction command logged Gradle plugin classpath-lock warnings during subproject scanning but completed successfully and generated the codec. Final production compilation and ArchUnit execution remain unverified until PR CI.

## Next step
Commit the scoped candidate and pass its exact commit to coordinator for Claude review, publication and CI. At this implementation commit, worker had not published; central campaign note owns live PR state.
