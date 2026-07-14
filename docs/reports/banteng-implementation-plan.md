# Banteng implementation plan

## Objective

Build a production-quality Java MOO server that:

- matches stock WSL Toast for the supported language/server surface;
- reads LambdaMOO v4 and ToastStunt v17 databases and writes v17;
- keeps suspended tasks checkpointable;
- executes independent MOO work on multiple cores while preserving Toast's observable task order;
- is simpler and more explicit than Barn's retrofit MVCC branch;
- remains idiomatic Java rather than imitating Go, C++, Kotlin coroutines, or an actor framework.

This plan remains the control surface until all phases are complete or the user changes it.

Relative citation paths in Banteng documentation resolve from the Banteng repository root: `../barn`, `../moo-conformance-tests`, `../moo_interp`, `../../src/toaststunt`, `../../src/lambdamoo-db-py`. Current Java and OTS dependency research is recorded in `docs/reports/java-ots-library-review.md`.

## Chosen architecture

### Language

Use Java 25 without preview features.

Do not use Kotlin, Scala, or Clojure for the production implementation. Kotlin coroutines cannot be checkpointed as MOO VM state and would create a second suspension model beside Loom. Scala adds machinery without a MOO-specific payoff. Clojure STM is useful prior art, but exact tagged-value behavior, low-allocation interpreter loops, explicit persistence, and JFR/JMH integration favor Java.

Use Gradle Wrapper and one Java project initially. Pin the wrapper distribution and checksum, select Java 25 through a Gradle toolchain, compile with `--release 25`, lock dependencies, and commit dependency-verification metadata. Use packages and architecture tests for ownership; do not create a multi-module build until a real dependency or build-time boundary requires it.

Use JUnit 6.1.2 for example-based and managed integration tests. Use JetCheck 0.3.0 for Hypothesis-style generated data, automatic counterexample minimization, reproducible seeds/recheck tokens, and single-threaded stateful command tests, but keep it only after the Phase 1 acceptance spike proves nested MOO-value and `WorldTxn` generators under the pinned build. Use Jazzer 0.30.0 for explicit or nightly coverage-guided input fuzzing and OpenJDK jcstress 0.16 for low-level Java Memory Model outcomes; neither substitutes for property or conformance tests. Do not use jqwik while its current anti-agent usage clause remains in force.

### Concurrency

Use Loom selectively:

- virtual thread per connection reader;
- one serialized connection writer, also allowed to block on a virtual thread;
- virtual threads for opaque host operations that are genuine Toast suspension points;
- a bounded platform-thread executor for CPU-bound MOO VM segments.

Never equate a Java thread with a durable MOO task. A MOO task is explicit heap data: frames, bytecode positions, stacks, locals, handlers, limits, and task-local state.

```text
socket virtual threads
        |
        v
deterministic ready queue -- assigns Toast-order tickets
        |
        v
bounded CPU executor -- runs explicit VM segments speculatively
        |
        v
WorldTxn validation -- waits for ticket publication turn
        |
        +-- conflict --> restore segment start state and retry
        |
        v
atomic world publication + ordered effect journal
        |
        +-- fork/read/suspend/blocking result --> ready queue
```

### State and transactions

The committed world is immutable and versioned. `WorldTxn` is the only production path by which the VM or builtins read or change it.

- Track exact record reads, writes, and scan predicates.
- Stage replacement records and effects locally.
- Publish each successful transaction as one atomic committed revision through the short ticket-ordered publication sequencer. Start with the simplest fixed-snapshot revision mechanism that is correct; commit descriptors and per-record publication machinery require a later kept benchmark result.
- Publish successful segments in scheduler-ticket order so the result matches Toast's queue order, not merely an arbitrary serializable history.
- Retry a conflicted segment from an explicit captured start state.
- Keep older versions only while an active segment or checkpoint can observe them.

There is no generic store interface, backend adapter, mutable-world escape hatch, or compatibility facade.

### Effects

Every builtin is classified before it is enabled:

1. pure;
2. transaction read/write;
3. deferred commit effect;
4. Toast-style suspending host operation;
5. irrevocable segment operation.

Toast's global PRNG state is a transactional global record, so random calls consume values in publication-ticket order. Task-ID allocation is the same kind of publication-ordered global record: serial and parallel runs must assign identical task IDs. Because a shared PRNG record makes every random-consuming segment conflict with every other, conflict-cause telemetry must attribute PRNG contention separately; a per-segment stream derived deterministically from the seed and publication ticket is a permitted later replacement only with proof that serial and parallel modes stay identical and that no MOO-observable behavior distinguishes it from the shared record. Wall-clock reads and other schedule-sensitive observations are publication-sensitive: if an early speculative attempt reaches one, it stops and reruns when its ticket is current. A retry journal is allowed only for task-local nondeterministic inputs whose contract does not depend on publication order. Deferred effects are invisible until commit. A speculative segment that first reaches an irrevocable operation stops before performing it and reruns in the exclusive publication lane. No compensation scheme is allowed.

Treat object-number allocation and recycling as irrevocable initially, since recycling feeds the same number space. Do not optimize either until an implementation can preserve the exact number observed by code inside the creating segment without leaking aborted allocation behavior.

### Persistence

The Toast v17 text database remains the only durable database contract in the first implementation.

- Stream ISO-8859-1 input and output.
- Read v4 and v17; write v17.
- Serialize explicit VM/task state.
- Capture a committed world revision plus task snapshots for checkpoints. Suspension is a segment boundary, so a suspended task never holds an open transaction; checkpoints therefore capture only segment-boundary task states, and a segment executing during a checkpoint appears in its pre-segment state.
- Write to a sibling temporary file, flush it, and atomically replace the target.
- Hold the checkpoint revision through version GC while speculative work continues.

Do not add an internal SQL database, RocksDB, event store, or WAL during initial conformance. Stronger crash durability is a separate future decision.

### Test authority ladder

Use each test tool only for its named proof obligation:

1. JUnit examples for exact local behavior and checked-in regressions.
2. JetCheck properties and state models for generated semantic invariants.
3. Managed WSL Toast differential rows for MOO-observable behavior.
4. Jazzer for malformed and coverage-guided byte/token input exploration.
5. jcstress for publication visibility and Java Memory Model outcomes.
6. JMH for forked performance evidence.

A generated failure must report a reproducible seed or recheck token. Minimize it, promote the smallest counterexample to a durable JUnit regression or conformance fixture, prove that regression red, and only then change production code. A narrower tool never replaces a later named gate in this ladder.

## Ownership map

| Package | Owns | Must not own |
| --- | --- | --- |
| `moo.value` | tagged values, equality, ordering, hashing, literal form | database or VM access |
| `moo.syntax` | lexer, parser, AST, source diagnostics | runtime values beyond literal conversion |
| `moo.bytecode` | compiler, program format, disassembler | task scheduling |
| `moo.world` | immutable records, inheritance/topology, transactions, version retention | sockets or task lifecycle |
| `moo.vm` | frames, operand stack, opcodes, errors, ticks, explicit outcomes | direct committed-world mutation |
| `moo.builtin` | explicit grouped builtin catalog and effect classification | reflection-based discovery or hidden world access |
| `moo.runtime` | task registry, ready tickets, retries, effect publication | parser or database-file syntax |
| `moo.persistence` | v4/v17 codec and checkpoint snapshots | alternate mutation paths |
| `moo.server` | listener, Telnet bytes, sessions, login and command ingress | language semantics |
| `moo.app` | CLI and concrete composition root | domain logic |

Sealed interfaces are allowed only for closed data families such as values, AST nodes, VM outcomes, and effects. Other owners should begin as concrete final classes or records.

## Source-of-truth order

1. Freshly verified WSL Toast behavior through Barn's managed oracle procedure.
2. Durable `moo-conformance-tests` rows proven against that oracle.
3. Corrected normative Barn spec sections.
4. `lambdamoo-db-py` for database structure and differential round trips.
5. `moo_interp` and Barn as implementation references, never as authority over Toast.

If a focused test, Barn spec passage, or reference implementation disagrees with verified Toast, correct the durable test/spec first, record the oracle evidence, and only then implement Banteng.

## Mandatory authority gate for every implementation slice

This gate applies to every semantic or persistence decision in every phase,
including the first definition of primitive types. It must complete before the
first production edit for the slice. Build tooling may be established without
claiming any MOO semantics, but a passing Java test or build never satisfies
this gate.

For the exact surface being implemented:

1. Read the corresponding normative Barn specification. If no section exists,
   record that absence; do not fill it from intuition.
2. Read the corresponding current Barn implementation from public entry point
   through its semantic owner.
3. Read the corresponding Toast implementation from public entry point through
   its semantic owner.
4. Write a repository-local slice evidence record naming the exact Barn spec
   sections, Barn files/symbols, Toast files/symbols, agreements,
   disagreements, unresolved questions, and the conformance rows that cover
   them.
5. Resolve every MOO-observable disagreement or uncertainty with the managed
   WSL Toast oracle. Add or correct a durable `moo-conformance-tests` row and
   prove that row against Toast before designing the Java API.
6. Only after the semantic contract is frozen, choose the smallest idiomatic
   Java representation that implements it without adding observable surface.
7. Prove the focused row or regression red on Banteng, implement one slice,
   prove the focused row green, run the named broader gate, and commit the kept
   slice. Otherwise fully restore it.

The evidence record is a required artifact, not an optional research note.
Adjacent documentation, earlier Banteng code, deleted patches recovered from
session transcripts, Java conventions, passing property tests, or a plausible
design do not replace any step above. If the exact Barn or Toast source path is
missing, unread, ambiguous, or unavailable, the slice is blocked before Java
design or code begins.

For primitive values, this gate applies separately to integers, floats,
strings, object references, errors, lists, maps, booleans, WAIFs, and anonymous
objects, including representation limits, construction, conversion, equality,
ordering, hashing, truth, indexing, mutation/copy behavior, literal formatting,
serialization, overflow, encoding, and error behavior. No sealed hierarchy,
record, class, collection owner, or helper API may be chosen before those
contracts are derived from Barn and Toast and proven by the oracle.

## Execution ledger - 2026-07-14

Completed and committed:

- [x] The three Banteng blueprint reports were restored and committed as
  `203f6b2`.
- [x] The current managed stock-Toast identity, profile manifest, wrapper, and
  exact conformance command were verified and recorded in
  `docs/reports/toast-oracle-identity-2026-07-14.md`; the authority-path
  correction was committed as `9253ff7`.
- [x] The non-semantic Java 25/Gradle 9.6.1 bootstrap was committed as
  `dc097a2`. It includes the pinned wrapper and checksum, dependency locks and
  verification metadata, picocli bootstrap command, JUnit, Spotless,
  Error Prone, NullAway, ArchUnit cycle check, application distribution, and
  `-Xlint:all -Werror`.
- [x] Commit `dc097a2` passed `gradlew.bat clean check installDist` from a
  detached clean worktree using only the committed wrapper and Java 25; the
  installed launcher printed `banteng 0.1.0-SNAPSHOT`, and the proof worktree
  remained clean.

Still unchecked; these are not satisfied by the bootstrap:

- [ ] Complete the remaining Phase 0 Barn spec audit and corrections against
  the current Barn implementation and Toast implementation/evidence.
- [ ] Record the Phase 0 license/provenance decisions, dependency approvals,
  and exact supported conformance-profile set.
- [ ] Complete the remaining Phase 1 ownership-package and ArchUnit boundary
  rules, JetCheck acceptance/recheck spike, Jazzer task, jcstress source set,
  forked JMH artifact, JFR definitions, representative static-analysis spike,
  and start/shutdown smoke test.
- [ ] Begin Phase 2 only with the primitive-type authority matrix and its
  per-family repository-local evidence records and Toast-proven durable
  conformance rows.

The bootstrap contains no MOO value, syntax, bytecode, VM, world, persistence,
server, task, builtin, or concurrency semantics. Its passing tests are not
semantic evidence and cannot satisfy the mandatory authority gate.

## Phase 0 - Freeze authority and land the blueprint

Deliverables:

- Commit `jvm-moo-architecture-research.md`, `java-ots-library-review.md`, and this plan as Banteng's first scoped documentation slice.
- Verify and record the current WSL Toast source identity and executable using
  `../barn/plans/barn-toast-mongoose-convergence-100-line.md`,
  `../barn/profiles/toast/stock-wsl-testdb.json`, and
  `../barn/scripts/run_toast_wsl.sh` before any behavioral claim. Use the exact
  managed command shape recorded in
  `../barn/plans/barn-toast-mongoose-convergence-workstreams.md`; do not use a
  manual Toast process or `moo --version` exit status as a substitute.
- Audit Barn `spec/tasks.md`, `spec/go-design.md`, `spec/vm.md`, `spec/database.md`, and `spec/README.md` against Toast and current Barn.
- Correct the contradicted task/concurrency passages. Separate normative MOO semantics from historical Go sketches; do not replace them with Banteng design notes.
- Fix stale source paths in the spec and add direct Toast source/evidence references.
- Record license/provenance decisions. Until clarified, use Barn, `moo_interp`, and `lambdamoo-db-py` as behavioral references rather than copied code.
- Record every approved OTS dependency's owner, version, license, semantic boundary, profile, transitive surface, and removal criterion. Current-version research alone does not approve an upgrade.
- Fix the supported conformance-profile set explicitly. The primary release gate is the stock WSL Toast profile (`../barn/profiles/toast/stock-wsl-testdb.json`); declare the Mongoose `PROMOTE_NUMBERS` profile and each real-core profile in or out. Every later "selected profile" reference in this plan resolves to that recorded list.

Gates:

- Every changed Barn statement has direct Toast or live-oracle evidence.
- Banteng and Barn documentation changes are committed separately with exact paths.
- No MOO-semantic production code exists yet. The explicitly recorded
  non-semantic build and CLI bootstrap does not authorize a semantic API or
  representation.

## Phase 1 - Java skeleton and architectural guardrails

Deliverables:

- Gradle Wrapper 9.6.1, pinned distribution checksum, Java 25 toolchain, `--release 25`, dependency locking and verification, reproducible test/build/application-distribution tasks, and no preview flags.
- Concrete picocli CLI with database path, checkpoint path, listener address/port, structured log level, validated usage, and stable exit codes.
- Package skeleton matching the ownership map.
- ArchUnit tests enforcing allowed package dependencies and rejecting package cycles.
- JUnit 6.1.2, a JetCheck 0.3.0 property-test acceptance spike, Jazzer 0.30.0 regression/fuzz tasks, a jcstress 0.16 source set, a forked JMH 1.37 benchmark artifact, and JFR event definitions.
- Spotless with pinned google-java-format, including import and wildcard-import policy. Use ordinary Javadoc rather than `///` Markdown documentation comments while the formatter's current handling remains unresolved.
- A Java 25 compatibility decision for Error Prone and for JSpecify plus NullAway, based on representative sealed types, records, generics, and package annotations. `javac -Xlint:all -Werror` remains mandatory regardless of that decision.
- A JetCheck spike that generates nested MOO values and a stateful `WorldTxn` command sequence, demonstrates automatic minimization, reproduces the failure from the emitted recheck token, and runs through the wrapper with JUnit 6. Reject JetCheck rather than building a local property framework if the spike fails.
- One executable smoke test that starts, reports its version, and shuts down cleanly without a database.

Gates:

- A clean checkout builds and tests using only the wrapper.
- The build compiles with `-Xlint:all` and warnings promoted to errors; any suppression is explicit in code with a stated reason.
- Formatting, dependency locking, checksum verification, and the application distribution are checked by the wrapper.
- The JetCheck acceptance spike passes on Java 25 and its recorded falsification can be minimized and rechecked exactly. The deliberately failing spike is not left in the normal committed suite.
- Error Prone and NullAway are each either proven and enabled as blocking checks or explicitly rejected with the observed incompatibility; no partially configured static-analysis gate remains.
- No framework, backend interface, plugin system, dependency injection container, or reactive networking stack is introduced.

## Phase 2 - Values, syntax, bytecode, and pure VM

Begin with a primitive-type authority matrix, not Java type definitions. Apply
the mandatory authority gate separately to each primitive family and commit
the evidence plus Toast-proven conformance rows before creating the Java value
hierarchy. The matrix must distinguish MOO-observable contracts from internal
representation choices and must explicitly record every behavior for which
Barn and Toast disagree.

After that evidence is committed, establish only the representation required
by the first proven semantic slice. Do not infer deep immutability, equality,
hashing, ordering, byte ownership, character encoding, scalar status, or Java
record suitability from ordinary Java value-object practice. Those are outputs
of the authority gate, not premises of the design.

Then implement thin vertical semantic slices. Each kept slice crosses source bytes, lexer and spans, immutable AST, compiler, deterministic disassembler, and explicit-stack VM execution before the next slice begins:

1. literals, error values, truth, return, and literal formatting;
2. variables, arithmetic, comparisons, and control flow;
3. strings, lists, maps, ranges, indexing, and 1-based collection operations;
4. loops, comprehensions, and scatter assignment;
5. calls, returns, exceptions, and `finally`;
6. fork bodies, ticks, and wall-clock limits represented as serializable VM state.

Do not complete an entire lexer, parser, compiler, or VM layer ahead of the next executable semantic path. Keep or fully restore each source slice before starting the next.

Use `moo_interp` for differential generation where it is already proven, but resolve every disagreement through focused WSL Toast evidence and add the result to `moo-conformance-tests` when coverage is missing.

Gates:

- Focused conformance categories for basic values and language constructs pass.
- Parser/compiler round trips and disassembly are deterministic.
- Every VM state required for suspension is serializable data; no correctness depends on a Java call stack.
- JetCheck properties cover value equality/hash/order contracts, Latin-1 round trips, nested collection operations, and compiler control-flow invariants; minimized failures are promoted to durable regressions before a fix.

## Phase 3 - Minimal persistent walking skeleton, then full world

Deliverables, in this order:

1. The minimum streaming v17 reader, immutable world records, concrete `WorldTxn`, and deterministic v17 writer needed by the first managed row. Even this minimal path permits no runtime-visible read or write outside `WorldTxn`.
2. The minimum byte-oriented connection, login, command, serial scheduling, output, checkpoint, and restart path needed to run the existing first end-to-end managed row: boot the bundled database, connect Wizard, evaluate a simple expression, checkpoint, restart, and evaluate again.
3. Expand the proven path to the complete streaming v4/v17 reader and v17 writer; immutable object, property, verb, WAIF, anonymous-object, task, and connection records; and exact parent/child, location/contents, inheritance, property, verb, recycled-object, and player indices.
4. Expand `WorldTxn` to complete read/write/predicate tracking and topology validation.
5. Add the simplest correct short atomic publication of a fixed-snapshot committed revision, immutable version chains, epoch retention, and the deterministic snapshot API used by the writer. Do not introduce commit descriptors before the Phase 6 benchmark justifies them.

Proof fixtures include bundled conformance `Test.db`, Toast test databases, `toastcore.db`, and representative LambdaCore/Mongoose databases already present in the supplied resources. Never run a tracked fixture in place.

Gates:

- The first managed boot/login/evaluate/checkpoint/restart row passes before the codec, world, or server surface is expanded.
- Banteng -> v17 -> `lambdamoo-db-py` and `lambdamoo-db-py` -> Banteng differential round trips preserve functional state.
- Banteng-emitted disposable databases boot successfully under freshly verified WSL Toast.
- JUnit examples and JetCheck state-machine models prove atomic multi-object publication, repeatable reads, predicate conflict detection, topology invariants, and version reclamation safety; jcstress separately proves Java Memory Model publication properties.
- There is no non-transactional production mutator.

## Phase 4 - Complete serial server and task semantics

Deliverables:

- Expand the Phase 3 server seam to a blocking `ServerSocket` accept loop and virtual-thread connection lifecycle.
- Complete the byte-oriented Telnet parser, including split IAC state.
- Complete serialized connection output and input queues.
- Login, command parsing, verb lookup, task creation, explicit suspend/read/resume/fork, and task registry.
- Serial scheduler mode using the same ticket, `WorldTxn`, effect-journal, and publication path intended for parallel execution.
- Focused WSL Toast probes for ready-queue ordering rules, task-ID timing, and output ordering, recorded as durable conformance rows before the ticket sequencer design is frozen.

Gates:

- Focused server lifecycle, command, task, fork, read, Telnet, and persistence rows pass through the managed conformance harness.
- Serial behavior matches WSL Toast for ready ordering, zero-delay forks, task IDs, connection output, and restart.
- No manual server lifecycle substitutes for the managed harness.

## Phase 5 - Builtin surface by classified families

Create an explicit builtin manifest containing name, argument contract, permission rule, tick cost, effect class, and implementation owner. Implement families in conformance-driven slices:

1. conversions, strings, collections, math, regex, JSON;
2. objects, properties, verbs, permissions, movement, lifecycle;
3. tasks, server, connections, listeners, output;
4. crypto, file I/O, SQLite, HTTP/network, exec, and optional extensions.

Before each family, complete the mandatory authority gate using the Barn spec,
Barn implementation, and Toast implementation, then compare the resulting
manifest against Toast's registered names and the generated/focused
conformance rows. Delete accidental extra surface rather than keeping aliases.

Gates:

- Every enabled builtin has exactly one effect classification and focused tests.
- Every Toast builtin required by the selected profile is implemented or explicitly profile-excluded.
- No builtin can access committed world state without the current `WorldTxn`.
- Each family passes its focused managed conformance slice before the next begins.

## Phase 6 - Deterministic multi-core execution

Deliverables:

- A measured fixed-size `ThreadPoolExecutor` for speculative VM segments with an explicitly bounded work queue and admission policy; do not use virtual threads or an unbounded executor for CPU work.
- Toast-order publication tickets.
- Captured segment start state, validation, conflict retry, bounded retry accounting, and exclusive irrevocable rerun.
- Transactional global PRNG state, publication-turn reruns for wall-clock reads,
  and a narrowly scoped journal for genuinely task-local retry-safe inputs.
- Deferred effect publication for output, forks, connection changes, task changes, finalization, and MOO-visible logging.
- JFR and counters for queue delay, execution time, publication wait, commit, conflict cause (including PRNG-record contention), retries, irrevocable reruns, publication-sensitive wall-clock reruns, and version retention.
- A forked JMH commit-engine benchmark comparing the short global publication sequencer against commit descriptors under disjoint and conflicting writes. The sequencer remains the production design unless descriptors produce a material kept gain. A rejected descriptor slice is fully restored; a kept slice is committed and this architecture plan is updated before further source work.

Proof workloads use one common harness for Banteng serial mode, Banteng parallel mode, Barn master, and Barn's `work/mvcc-concurrent-moo` branch:

- independent CPU-heavy read-only verbs;
- disjoint property writes;
- same-property contention;
- inheritance/topology reads plus writes;
- output/fork ordering;
- checkpoint during active work.

Gates:

- Serial and parallel Banteng produce the same committed world, task order, IDs, and output for deterministic example and JetCheck-generated workloads; every minimized disagreement becomes a durable regression before the fix.
- Conflict and retry stress tests are clean under jcstress for publication/JMM claims and the relevant longer-running generated stress suite for scheduler/transaction semantics.
- Parallel Banteng beats its own serial mode on independent CPU work and disjoint writes using the same benchmark artifact.
- On the same machine and harness, Banteng improves on Barn MVCC's throughput/scaling shape without hiding single-worker cost.
- If two consecutive concurrency slices produce no kept measured improvement, stop this target and report it instead of widening the work.

## Phase 7 - Checkpoint, restart, and operational behavior

Deliverables:

- Non-stop-the-world committed-revision checkpoints.
- Atomic replacement and panic-dump behavior.
- Full queued/suspended/interrupted task round trip.
- Version retention bounded across slow checkpoints.
- Graceful shutdown, listener recovery, connection cleanup, and deterministic task restoration.
- JFR templates and a concise operator guide for thread, transaction, retry, GC, and checkpoint diagnosis.

Gates:

- Checkpoint/restart conformance passes while other tasks execute.
- Repeated checkpoint/load cycles are functionally stable under both Banteng and WSL Toast.
- Failure-injection tests never replace the last good database with a partial file.
- Long checkpoints do not prevent unrelated speculative execution and do not leak retained versions afterward.

## Phase 8 - Exact convergence and release proof

Work on one failing conformance family at a time. For each discrepancy:

1. prove expected behavior on freshly verified WSL Toast;
2. add or correct the durable focused conformance row;
3. prove it red on Banteng;
4. if generated testing exposed it, minimize it, record the exact replay token, promote the minimal case to a durable regression, and prove that regression red;
5. implement one owned fix;
6. prove the focused row and any promoted regression green;
7. run the substantial targeted category and reread this plan;
8. keep and commit the slice or fully restore it before the next family.

Final gates:

- Full managed stock-Toast profile passes against the bundled database.
- All selected real-core profiles pass with freshly identified disposable fixtures.
- Full Gradle tests, JetCheck properties, Jazzer fuzz targets, jcstress publication tests, scheduler/transaction stress tests, JMH comparison, database round trips, restart tests, and `git diff --check` pass.
- Every plan phase is complete or explicitly deferred by the user.
- The release artifact runs on a clean Java 25 installation with documented commands and no development checkout dependencies except the optional external conformance run.

## Explicit non-goals for the first release

- arbitrary Java plugin API;
- multiple storage backends;
- actor-per-object architecture;
- Netty/reactive streams;
- Kotlin coroutines;
- persisting Java thread/continuation stacks;
- arbitrary serializable scheduling that changes Toast queue order;
- internal SQL/RocksDB/event-store persistence;
- native-image support before the JVM implementation is conformant and measured.

## Immediate next action

Run `/goal` against this plan and resume at the first unchecked Phase 0 item.
The next slice is one bounded Barn documentation correction, not a concurrent
survey or implementation effort across Barn subsystems. Do not edit production
code. Do not inspect a different document's subsystem while the current
document remains uncorrected.

Process the five named Barn documents in this exact order, completing the edit
for one before reading implementation details for the next:

1. `spec/tasks.md`: correct the task/concurrency claims and authority paths.
   Check only claims already made by this document, including queue selection,
   task-ID allocation, fork timing, suspension/resumption, `yin()`,
   `queued_tasks()`, task-local storage, permissions, and default-limit source
   paths. Use direct current Barn and verified Toast source for each changed
   claim.
2. `spec/go-design.md`: reconcile only its current-package and ownership map
   with tracked Barn source, and keep it explicitly non-normative. Do not turn
   it into a new architecture proposal.
3. `spec/vm.md`: separate current Barn implementation documentation from MOO
   semantics; delete or correct historical pseudo-code, opcode tables, and
   state descriptions that do not match tracked Barn. Correct Toast authority
   paths and cite the direct compiler, opcode, execution, and task-state
   owners. Do not design a Banteng VM here.
4. `spec/database.md`: correct only its existing v4/v17 format, section-order,
   value, object, task-persistence, and current Barn implementation claims
   against direct Toast and tracked Barn codec sources. Correct stale authority
   and reference paths. Do not design Banteng persistence here.
5. `spec/README.md`: make its status, source links, authority order, and managed
   conformance instructions agree with the four corrected documents and the
   already-verified Toast identity record.

For each document, before every source read, name the exact existing passage
whose keep/edit/delete decision the read can change. Once direct evidence
decides that passage, edit the document immediately; another adjacent source
read is forbidden until an identified passage still lacks decision-changing
evidence. Broad package inventories, exhaustive subsystem traces, unrelated
behavior discovery, and speculative follow-up audits are outside this slice.
The later implementation-slice authority gate does not authorize expanding
this documentation correction beyond claims already present in these five
files.

After all five documents are corrected, run the applicable Barn documentation
checks and `git diff --check`, then commit exactly those five Barn paths as one
Barn documentation slice. Only after that commit, update this Banteng plan's
Phase 0 ledger with the completed Barn commit and commit the Banteng plan change
separately. Reread this plan and continue through the remaining Phase 0 and
Phase 1 checkboxes; a passing bootstrap is not permission to skip them.

Before the first Phase 2 semantic edit, create and commit the primitive-type
authority matrix. For each primitive family separately, read and cite the
normative Barn spec, trace the current Barn implementation, trace the current
Toast implementation, resolve disagreements with the exact managed WSL Toast
command, and land the durable `moo-conformance-tests` row. Do not define a Java
value hierarchy, primitive class, record, helper, conversion, equality,
ordering, hash, truth, literal, serialization, overflow, encoding, or error
behavior before its exact family evidence is complete.
