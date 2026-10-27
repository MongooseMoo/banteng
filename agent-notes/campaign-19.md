# Issue19: persistence and app composition

## Goal
Implement the approved DbCodec interface, domain-owned durable records, app construction of collaborators and single injected MooRuntime constructor, with the issue's requested ArchUnit rules.

## State
Implemented production assembly and single injected runtime constructor, DbCodec with canonical V17 output delegated by legacy readers, and world-owned checkpoint records. App constructs the actual runtime collaborators and shares a deferred runtime accessor with server and scheduler; no app factory facade. Fixture worker completed direct test assembly and callers. Existing OpenRewrite runner completed reference retargeting in an isolated worktree at current campaign15, avoiding half-migrated test APIs in active19; generated mappings were transferred with apply_patch and the temporary worktree removed. Authored CompositionRootArchitectureTest passed through explicit root :test selection, including main/test compilation under -Werror. Full suite and restart/checkpoint tests remain for PR CI. No CI checks were excluded.

## Decisions and why
Smallest interface exposes existing read(Path), write(BufferedWriter, WorldSnapshot, tasks, connections), writeAtomic(Path,...), writePanic(Path,...) operations, all IOException. Move existing public durable records into world through requested OpenRewrite type/reference moves; keep wire internals in persistence. World placement is an issue-permitted option required by the existing package-cycle rule: runtime already consumes persistence DbCodec and DurableTaskCodec, so persistence cannot consume runtime records. SuspendedStackSlot carries semantic VALUE/NONE/CATCH/FINALLY kinds and maps numeric tags within persistence only. Preserve record invariants and immutable copies, queued uninitialized slots, exact checkpoint format, atomic promotion and panic semantics.

App constructs one V17 codec for canonical output (settled campaign design), injects that output instance into V4/V5 readers which implement DbCodec and delegate output operations. Input read returns runtime Checkpoint; until issues25/26, legacy readers wrap their current world and empty task/connection tails. App selects input codec by declared header rather than separate format-specific read branches. Retarget actual reader callers to Checkpoint.world; no compatibility wrappers or legacy output implementation.

App creates shared ConnectionRegistry, server, VM, TaskRegistry, scheduler, stable catalog and codec, then hands actual instances into one runtime constructor. Concrete cycle: server and scheduler need runtime while runtime needs them. Use one app-owned nullable runtime field and requireNonNull accessor passed as Supplier to server/scheduler; assign immediately after runtime construction before serve. Their constructors/restoration do not invoke runtime, as parent verified. No factory facade or attachment lifecycle. Keep runtime.builtinHosts(context) binding per-attempt operations, one catalog/service lifetime, shared pending-read publication owner. Remove telescoping runtime constructors and server's runtime construction. Update fixtures through actual assembly, keeping test seams.

Approximately 300 constructor lines removed, 70–120 assembly lines, interface/delegation about85 lines, mostly unchanged durable record moves and mechanical callers. Only requested ArchUnit rules added: runtime/server cannot reference V17 nested DTOs; production top-level collaborator construction belongs to app. No extra gates or machinery.

## Surprises
MooServer's registry field was read only for its former runtime construction; after moving construction to app, no server code consumes the registry. Removed its unused injection, leaving address, port and runtime supplier. Private ConnectionRegistry snapshots and self constructor delegation remain legitimate; the requested assembly architecture rule targets cross-owner public collaborator construction.

Initial active-tree runner stopped before rewriting because ErrorProne threw NoSuchElementException during concurrent fixture edits. A later test attempt reproduced stale-position ErrorProne diagnostics after the implementation worker removed imports during compilation. Corrected the existing shared test workflow sentence to require completed source edits through compilation, then unchanged-source compilation exposed actual missing override annotations, which were fixed. A first test command also selected tooling subprojects; explicit root :test selection runs the authored file without sending its filter to tooling test tasks.

## Next step
Parent owns exact-code review, PR, Claude review, full PR CI and normal merge after115. No publication performed by implementation worker. Preserve concurrent84 bytecode work for later integration, avoid changing bytecode design here.

Integrated the runtime dependency's obsolete login-timeout admission check and its stress-test barrier. The only composition-specific adaptation is invoking the new callback through the scheduler's existing runtime supplier. No local CI or additional tests were run for this integration; parent owns publication and changed-line review.

Integrated master after the runtime dependency merged. Resolved the sole conflict by retaining the durable-record migration and both existing Math/Map builtin extraction recipes with their configurations unchanged. Master contributes the existing registrar, family extraction recipe and Math/Map families; composition Java sources needed no adaptation. No local CI was run for this integration.

Integrated master's slot-locals dependency. Its new SlotLocalsTest activation-import fixture still constructed the former nested SuspendedActivation type; retargeted that constructor to the existing world domain record without changing arguments or assertions. Source scan found no other old nested durable-record references in production, tests or rewrite recipe Java sources. No local CI was run; parent owns bounded review and PR CI.

## Draft PR description
Move durable checkpoint records into the world domain and expose database reads and canonical checkpoint writes through DbCodec. Banteng selects input from the header and assembles the VM, catalog, scheduler and injected runtime; legacy readers delegate output to the same V17 codec. Runtime and server no longer consume codec nested records.

Validation: existing scripts/rewrite.sh applied ChangeType reference moves; ./gradlew :test --tests world.mongoose.banteng.CompositionRootArchitectureTest passed. Full suite and restart/checkpoint behavior remain for PR CI.
