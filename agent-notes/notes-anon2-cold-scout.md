# Anon2 cold-start scout

- Task: read-only inspection of Banteng's current tracked and uncommitted source plus the sibling `startup_repair_anon2.yaml` and `Anon2.db`.
- Current finding: inspection has not begun because the mandatory notes checkpoint had to be satisfied first.
- Current state: no source, fixture, conformance-row, launcher, JVM-option, or worker-count change has been made by this scout.
- Blocker: none after this required notes update.
- Next action: read the investigation skill, then identify the exact production operations exercised by the passing two-boot test and rank their likely contribution to the 500 ms managed checkpoint miss.

## Current evidence

- The sibling row now contains the corrected Toast expectation, `1 values pending finalization`, and still waits exactly 500 ms before checking `Anon2.db.new`.
- `Anon2.db` starts with no pending values. Its `server_started` verb logs six lines, calls `suspend(0)`, creates two anonymous objects, adds `next` properties and `recycle` verbs, installs each verb body, writes names, writes the `A -> B -> A` cycle, and calls `shutdown()`.
- The current uncommitted Anon2 slice adds anonymous-property add/read/write support in `BuiltinCatalog`, `MooVm`, and `WorldTxn`, plus recursive anonymous reachability in `MooRuntime`.
- Zero-delay suspension already uses the existing synchronized `PublicationScheduler.enqueueWake` path; it does not start a timer.
- At suspension publication, `MooRuntime.finalizePendingAnonymousObjects()` snapshots the world and traverses any pre-existing pending roots. On terminal return, `queueUnreachableAnonymousObjects()` takes another full world snapshot, scans every permanent property, traverses every existing pending root, scans every anonymous identity, and traverses each newly queued anonymous closure.
- The v17 writer uses the already-composed cold-path improvements: JFR event construction is guarded, the temporary is PID-scoped with `CREATE_NEW`, and owner-only POSIX permissions are requested.
- Blocker: none. No managed row has been run and no tracked source or test has been changed by this scout.
- Next action: inspect exact call ownership for create/add-property/add-verb/set-code/property writes, the scheduler retry boundaries, and snapshot/validation costs; then rank the smallest semantics-preserving optimization.

## Owner trace and causal ranking in progress

- The Anon1 composed cold-start fix is committed as `3485294` and the Anon2 work is one uncommitted source slice on top of it.
- Each of the two `set_verb_code` calls enters `BuiltinCatalog.setVerbCode`, joins the supplied source lines, constructs a new `MooCompiler`, and compiles the one-line recycle program before updating the verb record. This is real, required source validation, but it is the only newly exercised operation that performs compilation.
- Each anonymous create, add-property, add-verb, set-code, name write, and `next` write reaches `WorldTxn.replaceAnonymousObject`. That method first copies the anonymous map, then constructs a `World`; the `World` canonical constructor copies players, permanent objects, anonymous objects, WAIFs, and pending roots again. The fixture performs twelve anonymous-body replacements: two creates plus ten subsequent body mutations.
- The initial six `server_log` calls reach one irrevocable-authorization boundary on the first call. `PublicationScheduler` requeues the same compiled VM entry with `irrevocableAuthorized=true`; it does not recompile `server_started` for each log.
- The zero-delay suspension is a real transaction/publication boundary and resumes through `enqueueWake`; that behavior is already the accepted Anon1 source and must remain.
- On terminal return, `queueUnreachableAnonymousObjects` takes one copied `WorldSnapshot`, scans the four permanent objects, and visits exactly two anonymous bodies/properties for this fixture. Its graph walk is constant-small here; its semantic result of one root for the A-B cycle is required.
- Provisional causal ranking: (1) `server_started` parse/compile/class-init and the required transaction boundaries, inherited from the fixture rather than the new source; (2) the two nested recycle-program compilations; (3) repeated immutable-world/map copying across twelve anonymous mutations; (4) the final snapshot/reachability scan; (5) v17 emission of two bodies and two programs.
- Blocker: none. No managed row or source mutation was performed.
- Next action: compare the cost owners against the exact Phase 2 immutable-revision/transaction invariants and select the smallest optimization that removes redundant copying or redundant compiler construction without changing validation, transaction publication, ordering, or persisted bytes.
