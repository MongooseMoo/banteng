# Legacy database restoration: issues25 and26

## Goal
Complete v4/v5 loading into the existing checkpoint/runtime model, including inherited properties, valid values, recycled slots, unprogrammed verbs, correct hierarchy admission, tasks and active connections. Use Toast fixtures and actual resume/checkpoint behavior. Dependencies are composition117 and slots116; their final merge/review state remains coordinator-owned.

## State
Implementation is complete locally. Shared property restoration is extracted from V17;
both legacy readers use it. V4 accepts recycled slots, common values and unprogrammed verbs,
repairs missing links, and validates hierarchy cycles and reciprocal links. V5 now rejects
bad hierarchy types/cycles/reciprocity while retaining Toast missing-reference repair.
Shared version-aware task-tail parsing covers old clocks, activation headers, named locals,
optional resume values/VM maximum and connection listeners. Typed builtin payload records
map onto existing frame return modes and create/recycle/move fields. Legacy language symbol
counts and structural index contexts are implemented in existing layout/import/export owners.
Toast Suspended.db and Broken1–5 are copied into resources, with direct authored tests.
Only LegacyDatabaseRestorationTest has been invoked locally, under WSL JDK25 with normal
Gradle prerequisites and source frozen through compilation. It passes, including actual
create/recreate/recycle/move-hook checkpoint/resume, real Suspended.db eval resume after
canonical checkpoint, nested assignment/range/index continuations, old queue headers/clocks,
optional resume/VM fields, recursive builtin payloads, Broken1 admission/Broken2–5 rejection,
clear and non-clear inherited overrides, every common legacy value tag, unprogrammed verbs,
and recycled high-water state. Full CI and external review remain coordinator-owned.
The stale SlotLocalsTest constructor inherited from prerequisite composition now references
the moved world DTO directly; the coordinator is repairing that upstream composition too.

## Decisions and why
Smallest complete change reuses existing owners. Extract V17 property-slot reconstruction into one package-private PropertySlotRestorer with input id, ordered parents, local definitions and raw slots; project each reader after its version-specific topology normalization. Roughly170 lines moved and40–70 adapter lines. V4 repairs only missing links then rejects cycles/reciprocal inconsistencies; V5 deletes permissive type/cycle/reciprocity repairs while preserving Toast missing-reference repair. Roughly140–230 changed/deleted topology lines. Keep property owner/perms/clear/defined state, canonical depth-first ancestry, bounded streaming and existing world layout representation.

Share existing task reader methods through one package-private reader parameterized by database version and existing value decoder, rather than duplicating them. Roughly350 lines moved and100–160 version-handling lines. Preserve obsolete clocks, optional old suspended/connection tails, legacy activation headers, named environments, task-local version threshold and indefinite deadlines. V17 encoding stays canonical output.

Actual builtin continuation producers require small typed payload on existing SuspendedActivation: eval, create/recreate, recycle, move and recursive call_function. Translate into existing frame return modes/override/move/recycle fields and support subsequent canonical checkpoint export, about180–260 lines. No new scheduler or continuation framework. LayoutCompiler and ToastV17ProgramLayout need language-version symbol initialization (18 leading names forv4/v5 versus24 current), about40–70 lines. Existing structural stack mapping must reconstruct existing IndexState for calls within index/range expressions, about100–180 lines. Preserve nonempty persisted temporary metadata; establish whether any live translation is needed from actual source instead of blanket rejection.

Parent read Toast db_file190–290/447–743, db_properties52–70/580–625, db_objects779–826; tasks2010–2118, execute310–335/3838–4098 and indexed assignment generation879–915; eval_vm101–137, sym_table57–121; builtin payload writers/readers in objects234–251/546–559/962–985 and execute3437–3495. Parent read current readers, V17 property reconstruction, DurableTaskCodec import/export, scheduler restoration, MoveOps return modes, Frame constants, compiler ENTER_INDEX and VM IndexContext creation. Real Suspended.db has language4 eval/suspend child continuation2; parser-only acceptance cannot establish restoration.

No extra compatibility framework, topology conflict policy, test weakening or conformance edits. Existing clearFallback resolves by name; no evidence yet justifies inventing a duplicate-definition policy. Preserve known accepted semantics and trace a real counterexample before expanding that part.

## Next step
PR CI found stale contracts in ArchitectureTest and the two original legacy reader test files.
Those corrected test files pass locally, with the production source frozen: require the shared value decoder for every reader;
replace v4 subset-only rejection cases with supported integer/connection assertions plus truly
unsupported or truncated cases; retain Broken1 repair checks and require Broken2–5 rejection.
This repair does not alter production readers or protections.
Conformance also exposed a production diagnostic defect. Toast db_file.cc logs all invalid
field types in parents/children/location/contents order before phase-one rejection, and logs
reciprocity failures in location/contents/parents/children order across objects before rejecting.
The existing V5 reader rejects at the first mismatch without those diagnostics. The authorized
repair adds that log-before-reject behavior inside the existing reader, retaining cycle admission
and missing-reference repair. Conformance fixtures and assertions remain unchanged.
Coordinator verifies the implementation and primary-source claims, publishes, obtains Claude
review and PR CI, then normally merges after prerequisites. Nothing has been published by worker.

## Surprises
Toast's old debug value is truthy rather than restricted to zero/one (Suspended.db contains eight).
Legacy activation threading defaults true: execute.cc uses DEFAULT_THREAD_MODE, and options.h
defines it true. Old VM maximum defaults fifty. Indexed assignment layout previously passed
indexed_above true for the outer lvalue; Toast code_gen.cc passes zero and only nested parents
use one. That existing layout error is corrected as part of indexed continuation restoration.
Toast temp is set after indexed RHS evaluation and cleared after straight-line assignment;
there is no call between PUT_TEMP and PUSH_TEMP. Raw temp metadata is retained by read/write,
and runtime import need not feed it into a new VM mechanism.
Local authoring failures were repaired without changing protections: extraction-orphaned
helpers and repair-copy methods were deleted; missing Override annotations, nullable map
assertions and explicit split limits fixed static errors; multiline test literals use text
blocks; direct VM tests now use existing transaction begin/authorize/commit lifecycle.
