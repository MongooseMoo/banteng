# JVM MOO architecture research

## Question

What language and runtime architecture should Banteng use to become an idiomatic JVM MOO server that is Toast-compatible, simpler than Barn, and capable of scaling MOO execution across cores without weakening MOO task semantics?

## Verified facts

### Repository state and authority

- Banteng has no commits and no implementation. Its only existing entry is the unrelated untracked `pyghidra_mcp_projects/` directory.
- Barn `master` is 22 commits ahead of `origin/master` and has extensive unrelated untracked material. Its tracked `AGENTS.md` is already modified. Any Barn documentation correction must therefore be exact-path scoped.
- `moo-conformance-tests` supplies a managed TCP lifecycle and a bundled `Test.db`; its README identifies ToastStunt as the reference implementation (`../moo-conformance-tests/README.md:96-160`). Recent Barn MVCC reports observed roughly 4,000 passing cases, not a small toy suite.
- Barn's canonical Toast authority is the managed WSL flow in `../barn/reports/toast-oracle-wsl.md`, pinned to `/root/src/toaststunt/build-release/moo` and a verified source SHA (`:1-30`). Manual or Windows Toast substitutions are explicitly forbidden (`:54-89`).
- `lambdamoo-db-py` is useful as a structural/differential oracle, not as polished documentation: its README is essentially empty, but its writer emits format 17 and handles the terminating anonymous-object batch (`../../src/lambdamoo-db-py/lambdamoo_db/writer.py:261-323`).
- `moo_interp` is a useful parser/compiler/VM comparison surface. Its iteration log records 910/910 live Toast differential cases for the exercised core, while also recording a large builtin namespace delta (`../moo_interp/reports/iteration-log.md:45-105`). It is not a complete server authority.

### Toast execution and persistence semantics

- Toast runs one ready MOO task at a time. `main_loop()` calls `run_ready_tasks()` (`../../src/toaststunt/src/server.cc:797-898`); `run_ready_tasks()` uses `did_one` and stops after one task (`../../src/toaststunt/src/tasks.cc:1649-1809`).
- Toast's background threads suspend the MOO task, execute opaque host work, and resume through the main task queue. The source explicitly forbids background callbacks from reading or traversing the MOO database because it is not thread-safe (`../../src/toaststunt/src/background.cc:15-35,159-165,220-224`).
- Therefore the observable unit to preserve is a run segment from start/resume through return, abort, or suspension. In Toast, no other MOO task observes intermediate world mutations inside such a segment.
- Toast's current database version is 17: `DBV_Bool` is the last enum member and `current_db_version` is `Num_DB_Versions - 1` (`../../src/toaststunt/src/include/version.h:43-90`). Barn's v4/v17 target is correct.
- The persisted task representation is explicit VM state, not a host-language thread stack. Banteng must retain explicit bytecode frames, instruction positions, operand stacks, locals, handlers, and task-local state if suspended tasks are to checkpoint and restart correctly.

### Barn lessons

- Barn `master` deliberately serializes MOO work on a scheduler goroutine; socket and opaque builtin work may run elsewhere. `../barn/reports/concurrency-prior-art-research.md:14-121` verifies the same structure in Toast.
- Barn's MVCC branch is a retrofit of about 13,000 added lines across 81 files. It had to add transaction-aware store reads, effect deferral, static access-footprint hints, retry plumbing, history GC, and many transaction-specific builtin/VM paths.
- The branch's original architecture is sound in broad shape: runtime read/write sets, task-segment transactions, output/fork deferral, retry before irreversible effects, and a serialized fallback (`work/mvcc-concurrent-moo:plans/mvcc-concurrent-moo-plan.md`).
- Its initial global commit lock erased scaling. Converting published objects to immutable copy-on-write images plus per-object slots improved a commit-dominated disjoint-write microbenchmark from flat/degrading to about 1.1-1.6x at higher worker counts, but still paid a roughly 20-30% one-worker tax (`work/mvcc-concurrent-moo:reports/cow-phase0-coder.md`, `cow-phase1-coder.md`).
- The retrofit repeatedly needed coarse `liveMutated` fallbacks for create/recycle/chparent/move/add-verb and special handling for anonymous objects and inheritance propagation. Banteng can avoid this split authority by making a transaction the only runtime world-access path from the first vertical slice.
- Barn's checked-in spec is mixed-quality authority. Its normative task section says single-threaded cooperative execution (`../barn/spec/tasks.md:323-344`), but its later Go example starts fork bodies in independent goroutines (`:395-414`), which contradicts both that semantic section and Toast. `spec/go-design.md` similarly describes a design that is not current Barn. These sections should be corrected or clearly marked historical/non-normative before Banteng treats the spec as shared authority.

### Current JVM surface

- JDK 25 is the current LTS-era reference implementation. Virtual threads have been final since JDK 21. Records and pattern-switch are final and support closed, explicit data models well.
- OpenJDK states that virtual threads improve scale for high-concurrency blocking work, not CPU speed; CPU-bound work does not improve by increasing thread count beyond available cores. It also says virtual threads should not be pooled and resource limits should use explicit constructs such as semaphores.
- Structured concurrency remains preview in JDK 25, so Banteng should not require preview features. Scoped values are final but are optional; explicit parameters remain clearer for transaction ownership.
- Kotlin coroutines add another suspendable runtime, but do not make Java continuations checkpointable and do not solve MOO transaction semantics. Clojure's STM is conceptually relevant, but its own documentation warns that side effects must be avoided because transactions retry. Scala adds language/runtime surface without a corresponding MOO-specific benefit.

## Recommended decisions

### Language and build

Use Java 25, without preview features, in one Gradle project initially.

- Use records and sealed interfaces only for genuine closed sums: MOO values, AST nodes, bytecode operands, VM outcomes, and staged effects.
- Prefer concrete final owners for the world, transaction, compiler, VM, scheduler, session, and checkpoint code. Do not begin with a generic store backend, network adapter, or pluggable interpreter interface.
- Keep packages acyclic and enforce the dependency direction in tests. Start as one module; split build modules only when measured build or ownership pressure justifies it.
- Use JUnit 6 for examples and managed integration tests, JetCheck after its acceptance spike for generated properties and state models, Jazzer for coverage-guided input fuzzing, jcstress for Java Memory Model claims, JMH for forked benchmarks, and custom JFR events for task segments, commits, conflicts, retries, fallback, and checkpoints. Exact selections and boundaries are recorded in `java-ots-library-review.md`.

Java is recommended over Kotlin because Loom removes the strongest practical reason to adopt coroutines for server I/O, while Java gives the most direct access to JFR/JMH, stable sealed/record modeling, predictable allocation, and a single concurrency model. Kotlin remains a reasonable second choice for syntax alone, but its coroutines are actively misleading for checkpointable MOO tasks.

### Host concurrency

Do not map a durable MOO task to a long-lived Java thread or coroutine.

- Run one virtual-thread reader and one serialized writer per network connection, using ordinary blocking sockets and a byte-level Telnet state machine.
- Run opaque blocking operations that are true Toast suspension points on virtual threads.
- Run CPU-bound MOO segments on a bounded platform-thread executor sized from available processors. Virtual threads do not accelerate these segments.
- Keep MOO suspension explicit in the VM state so task snapshots can be written to v17 databases.

### Deterministic parallel MOO execution

Assign every runnable segment a monotonically increasing scheduler ticket in the exact order Toast would select it. Segments may execute speculatively in parallel, but successful effects publish in ticket order.

1. Capture the segment's explicit VM/task start state.
2. Execute against a transaction-local world view, recording exact logical reads, writes, scans, nondeterministic results, and effects.
3. Wait for the ticket's publication turn.
4. Validate the read set against earlier committed tickets.
5. If valid, atomically publish the world delta and ordered effects.
6. If invalid, rerun from the captured start state against the newer world, reusing recorded nondeterministic results where required.

This produces Toast's deterministic serial order rather than merely some serializable order. Independent CPU work overlaps; conflicting later work retries. Zero-delay forks receive tickets only when the parent publishes, preserving queue order.

### World and transaction model

Make immutable committed records and transaction-only runtime access foundational, not a later MVCC layer.

- Version logical records at the narrowest correctness boundary: object scalars, topology, property namespace/slots, verb namespace/definitions, and global indices.
- A transaction owns all reads and staged writes. Production VM and builtin code has no direct mutable-world escape hatch.
- Writes build immutable replacement records. Multi-object publication must be atomic to transaction-aware readers; begin with the simplest correct fixed-snapshot revision publication through the ticket-ordered sequencer, not sequential naked `AtomicReference` updates. Commit descriptors require the later named benchmark to show a material kept gain.
- Reads cache the observed record/version. Commit validation catches fractured reads; range and topology scans record predicates, not merely objects.
- Start with a short deterministic publication sequencer because exact order is semantically valuable. Do not add a global lock around execution, and do not claim parallel write scaling until a commit-dominated benchmark proves it.
- Retain old versions only while active segments or checkpoints can observe them; reclaim by epoch.

### Effects, nondeterminism, and irrevocable operations

- Buffer `notify`, traceback delivery, fork creation, connection state changes, logs visible to MOO, finalization, and task registry changes until commit.
- Model Toast's global PRNG state as a transactional global record so calls consume
  values in publication-ticket order. A conflict rerun must recompute from the
  newly visible PRNG state; blindly replaying an early random result would break
  Toast ordering.
- Treat wall-clock reads and other schedule-sensitive host observations as a
  publication-sensitive operation. If speculative execution reaches one before
  its ticket is current, stop without exposing effects and rerun the segment at
  its publication turn. Recording the earlier wall clock would allow a later
  Toast-ordered task to observe a time preceding an earlier task's observation.
- Use a retry journal only for nondeterministic inputs whose contract is local to
  the task and independent of publication order. Host entropy and other global
  sources require an explicit transactional or irrevocable policy.
- When execution first encounters a non-deferrable, non-suspending host effect, stop before performing it, discard the speculative attempt, and rerun that segment in an exclusive irrevocable publication lane. Do not attempt compensation.
- Operations that Toast already implements by suspending to a background thread remain ordinary transaction boundaries and execute exactly once after the preceding segment commits.
- Treat `create`/object-number allocation as irrevocable initially. Optimize only if a design preserves the exact object number observed inside the creating segment and does not leak aborted allocations.

### Persistence

- Read v4 and v17; write v17. Use a streaming ISO-8859-1 codec, exact explicit VM/task serialization, and atomic temp-file replacement.
- A checkpoint captures one committed world revision plus immutable task snapshots without stopping speculative execution. Version retention keeps that revision alive until the writer finishes.
- Do not introduce a second internal database or WAL in the first implementation. The v17 database remains the truthful persistence contract; stronger crash durability can be proposed separately after conformance and checkpoint cost are measured.
- Differentially validate Banteng read/write behavior against `lambdamoo-db-py` and validate behavioral results by booting the emitted database under WSL Toast.

## Implementation shape

Suggested packages in a single project:

- `moo.value`: values, equality, ordering, hashing, literal formatting.
- `moo.syntax`: lexer, recursive-descent/Pratt parser, immutable AST, diagnostics.
- `moo.bytecode`: compiler, immutable program, disassembler.
- `moo.world`: committed records, transaction, validation, version retention.
- `moo.vm`: explicit frames/stacks, opcode loop, outcomes, tick/time limits.
- `moo.runtime`: task registry, ticket scheduler, effect publication, retries.
- `moo.builtin`: explicit grouped builtin catalog; no reflection-based discovery.
- `moo.persistence`: v4/v17 streaming reader/writer and checkpoint snapshots.
- `moo.server`: listener, Telnet/session lifecycle, login/command dispatch.
- `moo.app`: CLI and composition root.

The dependency direction should point inward toward values/world/VM. Network and persistence must not become alternate owners of world mutation.

## Open proof obligations

1. Verify the exact ready-queue ordering rules, task-ID timing, and output ordering with focused WSL Toast probes before freezing the ticket sequencer.
2. Build a minimal commit-engine benchmark comparing a short global publication sequencer with commit descriptors under disjoint and conflicting world writes. Keep the simpler mechanism unless descriptors produce a material kept gain.
3. Inventory every builtin as pure, deferred effect, Toast-style suspending host operation, or irrevocable segment operation. No builtin may remain unclassified when enabled.
4. Determine which Barn spec sections are normative truth versus obsolete Go sketches, then patch the contradicted task/concurrency passages with Toast citations.
5. Confirm licensing/provenance before porting code. `moo-conformance-tests` is MIT; no license file was found in the inspected Barn, `moo_interp`, or `lambdamoo-db-py` roots, so treat their code as reference-only until clarified.

## External sources

- OpenJDK JDK 25: https://openjdk.org/projects/jdk/25/
- JEP 444, Virtual Threads: https://openjdk.org/jeps/444
- JEP 505, Structured Concurrency (Fifth Preview): https://openjdk.org/jeps/505
- JEP 395, Records: https://openjdk.org/jeps/395
- JEP 441, Pattern Matching for switch: https://openjdk.org/jeps/441
- Oracle Java 25 virtual-thread guidance: https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html
- Clojure refs and transactions: https://clojure.org/reference/refs
