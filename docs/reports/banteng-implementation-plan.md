# Banteng implementation plan

## Objective

Build a Java 25 MOO server that:

- passes every non-excluded row for the three profiles in
  `docs/reports/supported-conformance-profiles.md`;
- reads LambdaMOO v4 permanent-object bootstrap databases and ToastStunt v17
  databases, reads anonymous objects only from ToastStunt v17 databases, and
  writes deterministic ToastStunt v17 databases;
- restores queued and suspended tasks after checkpoint and restart;
- runs production VM work through the transaction and publication scheduler
  defined below; and
- contains no alternate serial scheduler, mutable-world escape hatch, storage
  adapter, actor framework, or second durable store.

This plan is the execution order. A later phase does not authorize work while
an earlier gate is failing. Existing code may be reused only after it passes the
gate for the phase that owns it. Every kept source slice ends in a commit; every
rejected source slice is fully restored before another source slice begins.

## Exact authorities

All relative paths resolve from the Banteng repository root:

- managed conformance suite: `../moo-conformance-tests`;
- normative read-only specification: `../barn/spec`;
- Toast source: `../../src/toaststunt`;
- ToastCore source and fixture: `../../src/toastcore`;
- optional read-only implementation references: `../moo_interp` and
  `../../src/lambdamoo-db-py`.

Toast is the behavioral oracle. A conformance row is authoritative only after
its exact YAML path and test name pass against the profile's pinned Toast
source, executable, configuration, and disposable fixture. Barn, `moo_interp`,
and `lambdamoo-db-py` never override a Toast result and are never build or
runtime dependencies. Do not modify Barn while executing this plan.

Managed conformance and Gradle gates execute in Debian WSL. Java commands use
`JAVA_HOME=/opt/java/25`. Python commands use `uv`; never use bare Python, pip,
or pytest. Never run a tracked database fixture in place.

## Production architecture

There is one scheduler and one execution path from the first production slice:

```text
socket reader -> deterministic ready queue -> publication ticket
              -> bounded platform-thread VM executor
              -> WorldTxn validation
              -> ticket-ordered world and effect publication
              -> ready queue / socket writer / checkpoint snapshot
```

- A durable MOO task is explicit heap data: program identity, frames,
  instruction positions, operand stacks, locals, handlers, limits, task-local
  values, and pending outcomes. It contains no Java thread, future, socket,
  continuation, live clock anchor, or Java-stack dependency.
- The committed world is immutable and versioned. `WorldTxn` is the only
  production path for runtime-visible reads and writes.
- Each transaction records exact record reads, writes, and scan predicates,
  validates against its fixed snapshot, and stages replacement records and
  effects locally.
- The ready queue assigns monotonically increasing publication tickets. VM
  segments may complete in any order; committed world changes, task IDs, PRNG
  changes, forks, output, and other effects publish in ticket order.
- A conflict restores the captured segment-start task state and retries through
  the same production executor. An irrevocable operation reruns only when its
  ticket owns the publication turn.
- The CPU executor is a fixed `ThreadPoolExecutor` whose worker count defaults
  to `max(2, Runtime.getRuntime().availableProcessors())`. Its work queue is an
  `ArrayBlockingQueue` of `workers * 4`; the scheduler stops dispatching while
  that queue is full. Virtual threads are used only for blocking socket and
  host-operation boundaries.
- Checkpoints hold one committed revision and serialize world and task snapshots
  to ISO-8859-1 v17 text in a sibling temporary file. The writer calls
  `FileChannel.force(true)`, closes the file, then calls
  `Files.move(temp, target, ATOMIC_MOVE, REPLACE_EXISTING)`. If the move throws
  `AtomicMoveNotSupportedException`, the checkpoint fails, the old target stays
  unchanged, and the temporary file is deleted. There is no non-atomic fallback.

## Package ownership

| Package | Owns |
| --- | --- |
| `moo.value` | tagged values, equality, ordering, hashing, literals |
| `moo.syntax` | Latin-1 lexer, spans, parser, immutable AST |
| `moo.bytecode` | compiler, program format, deterministic disassembly |
| `moo.vm` | explicit frames, opcodes, errors, limits, segment outcomes |
| `moo.world` | immutable records, indices, `WorldTxn`, revisions |
| `moo.runtime` | tasks, tickets, retries, publication, effect journal |
| `moo.persistence` | streaming v4 permanent-object bootstrap input, streaming v17 input including anonymous objects, and deterministic v17 output |
| `moo.builtin` | builtin manifest, contracts, implementations |
| `moo.server` | sockets, Telnet bytes, sessions, login, command ingress |
| `moo.app` | picocli options and concrete composition root |

`ArchitectureTest` rejects package cycles. Phase 2 makes the committed `World`
and revision implementations package-private and adds
`WorldAccessArchitectureTest`, which rejects production dependencies on those
implementations from outside `moo.world`; the public immutable snapshot used by
the v17 writer exposes no mutation operation.

## Builtin contract

The builtin manifest begins in Phase 2 and is expanded only by later vertical
slices. `BuiltinSpec` contains `String name`, `List<CallShape> callShapes`,
`BuiltinPermissionRule permission`, `BuiltinCostRule tickCost`,
`EffectClass effect`, `BuiltinOwner owner`, and `BuiltinHandler implementation`.
Each `CallShape` contains required and optional position lists of allowed
`ArgType` sets plus an optional variadic allowed-type set. Multiple call shapes
represent overloads. The permission rule, tick-cost rule, and handler are the
production callables used directly by dispatch; there is no independent name,
permission, cost, effect, or dispatch switch. `EffectClass` and `BuiltinOwner`
are closed enums. `BuiltinPermissionRule.WIZARD_ONLY` is the shared Wizard
predicate and `BuiltinCostRule.fixed(long)` constructs a fixed dynamic charge.

## Phase 0 - Owned, executable authorities

Deliverables:

1. Add `scripts/verify_toast_profile_wsl.sh MANIFEST`. For a profile manifest, it must
   verify the WSL source HEAD, executable SHA-256 and executable bit,
   configuration SHA-256, fixture SHA-256, and support status before launch.
2. Add the required Toast source path, executable checksum, and canonical
   fixture path to all three manifests under `profiles/toast/`. The ToastCore
   manifest also records fixture source path `/root/src/toastcore` and fixture
   source commit `1887eacd591d97fdc55d258a76e2167899b1951d`; its preflight verifies
   that HEAD separately from the Toast executable source. No environment
   variable may bypass a failed identity check.
3. Make `scripts/run_toast_wsl.sh` invoke that preflight before `exec`.
4. Verify `../barn/mongoose_fresh2.db` has SHA-256
   `33201970097d3d2d2bfc0d5f875f087d587601bf8255ef31ef19b416d65ac925`,
   copy that exact file into `fixtures/mongoose.db`, and verify the copy has the
   same checksum. This is the only authorized read of the Barn fixture.
   Thereafter no Banteng command may read `../barn/mongoose_fresh2.db`.
5. Make `scripts/run_banteng_wsl.sh` run `/opt/java/25/bin/java` inside WSL,
   accept `{db} {port} {manifest}`, and select `PROMOTE_NUMBERS` only from the
   supplied target manifest. It must not launch Windows Java or require a
   Windows-host gateway.
6. Fix the advertised `moo-conformance FILE_OR_DIR...` interface so exact YAML
   file and directory arguments collect only rows below those paths even though
   the CLI loads the installed `moo_conformance` package. Add a conformance CLI
   regression at `tests/test_cli_file_selection.py` proving one YAML file does
   not collect another, one directory collects every and only its descendant
   suites, a literal YAML `skip:` remains allowed, and fixture, profile,
   capability, collection, or runtime skips fail under
   `--fail-on-unexpected-skip`. Commit that conformance-repository slice before
   using path-selected gates below.
7. After the stock Toast preflight verifies `/root/src/toaststunt`, generate
   `startup-fixtures.sha256` from `Anon1.db` through `Anon6.db` and `Broken1.db`
   through `Broken5.db` in `/root/src/toaststunt/test/tests/`, with sorted
   basename-only entries. Then copy those exact files into
   `../moo-conformance-tests/src/moo_conformance/_db/startup/`. Commit those
   eleven fixtures plus the source-generated manifest in the conformance
   repository. Never regenerate the manifest from the destination copies.
8. Add `test_suites` arrays to the three Toast oracle manifests: stock and
   Mongoose contain `src/moo_conformance/_tests`; ToastCore is empty until Phase
   2 commits the persistent walking-skeleton row. Stock and Mongoose set
   `login_commands` to an empty array and `skip_standard_properties` to false;
   ToastCore sets `login_commands` to `["connect wizard"]` and
   `skip_standard_properties` to true. Add corresponding checked-in
   Banteng target manifests under `profiles/banteng/`; each identifies
   implementation `banteng`, the matching feature flags and fixture, and the
   same `test_suites` array as its Toast oracle manifest.
9. Add `scripts/run_managed_wsl.sh TARGET PROFILE SUITE...`, where `TARGET` is
   exactly `toast` or `banteng`, `PROFILE` is exactly `stock`, `mongoose`, or
   `toastcore`, and `SUITE...` is one or more exact conformance YAML paths,
   directories, or the single word `profile`. It selects the profile's Toast
   oracle manifest plus either that oracle manifest or the matching
   Banteng target manifest, invokes `uv run
   moo-conformance` with the exact suite paths, server command, fixture, oracle
   and target manifests, `--server-db-dir
   src/moo_conformance/_db/startup`, `--fail-on-unexpected-skip`, login commands,
   and standard-property behavior. It rejects every other argument shape and is
   checked in executable. `profile` expands only to the target manifest's
   nonempty `test_suites` array.
10. Add `scripts/test_verify_toast_profile_wsl.sh`. It must prove the verifier
   returns nonzero for a wrong source HEAD, executable checksum, configuration
   checksum, fixture checksum, and unsupported status, using temporary manifest
   and fixture copies only. For every failure case, repeat the invocation with
   valid alternate paths supplied through `TOAST_SOURCE_DIR`,
   `TOAST_EXECUTABLE`, `TOAST_CONFIG`, `TOAST_FIXTURE`, and
   `TOAST_SUPPORT_STATUS=supported`; it must still return nonzero from the
   manifest failure.
11. Add `scripts/test_managed_runners_wsl.sh`. Using only temporary Git
   repositories, manifests, fixtures, stub executables, and an argument-capture
   stub for `uv run moo-conformance`, it proves: Toast preflight occurs before
   launch; Banteng invokes `/opt/java/25/bin/java`; only the target manifest
   controls `PROMOTE_NUMBERS`; every invalid target, profile, and suite shape is
   rejected; `profile` expands only the selected nonempty suite array; login and
   standard-property options match the selected profile; and
   `--fail-on-unexpected-skip` is forwarded.
12. Keep the selected fixtures exact:
   `../moo-conformance-tests/src/moo_conformance/_db/Test.db` at SHA-256
   `1a3f23ebb549e02ccf5341668425118fcdc935b977096add87bc2a8ef29d408e`,
   `fixtures/mongoose.db` at the checksum above, and
   `/root/src/toastcore/toastcore.db` at SHA-256
   `8013b703c61a9894866f836f2b934eada7118cdf0b3cd56181e4bf9205b2f557`
   from ToastCore commit `1887eacd591d97fdc55d258a76e2167899b1951d`.

Gates:

```bash
cd /mnt/c/Users/Q/code/banteng
bash -n scripts/verify_toast_profile_wsl.sh
bash -n scripts/run_toast_wsl.sh
bash -n scripts/run_banteng_wsl.sh
bash -n scripts/run_managed_wsl.sh
bash scripts/test_verify_toast_profile_wsl.sh
bash scripts/test_managed_runners_wsl.sh
scripts/verify_toast_profile_wsl.sh profiles/toast/stock-wsl-testdb.json
scripts/verify_toast_profile_wsl.sh profiles/toast/mongoose-wsl-mongoose.json
scripts/verify_toast_profile_wsl.sh profiles/toast/stock-wsl-toastcore.json
test "$(git ls-files -s scripts/verify_toast_profile_wsl.sh | cut -d' ' -f1)" = 100755
test "$(git ls-files -s scripts/run_toast_wsl.sh | cut -d' ' -f1)" = 100755
test "$(git ls-files -s scripts/run_banteng_wsl.sh | cut -d' ' -f1)" = 100755
test "$(git ls-files -s scripts/run_managed_wsl.sh | cut -d' ' -f1)" = 100755
git diff --check
cd /mnt/c/Users/Q/code/moo-conformance-tests
UV_PROJECT_ENVIRONMENT=/root/.venvs/moo-conformance uv run pytest tests/test_cli_file_selection.py
cd src/moo_conformance/_db/startup
sed 's#  #  /root/src/toaststunt/test/tests/#' startup-fixtures.sha256 | sha256sum -c -
sha256sum -c startup-fixtures.sha256
cd /mnt/c/Users/Q/code/moo-conformance-tests
git diff --check
```
Record no Phase 0 result in a new report; the manifests, scripts, tests, and
kept commit are the evidence.

## Phase 1 - Java skeleton and guardrails

The build remains one Gradle project through Phase 8. It uses Gradle Wrapper
9.6.1, Java 25 with `--release 25` and no preview features, dependency locking
and verification, JUnit 6.1.2, JetCheck 0.3.0, Jazzer 0.30.0, jcstress 0.16,
JMH 1.37, Error Prone, JSpecify, and NullAway. Do not add another test framework,
dependency-injection container, plugin system, or reactive networking stack.

The committed `JetCheckAcceptanceTest` is the only replay-token authority.
Remove the duplicated token from `docs/reports/jetcheck-acceptance-spike.md` so
that the report points to the test instead of asserting another token.

Gates:

```bash
cd /mnt/c/Users/Q/code/banteng
JAVA_HOME=/opt/java/25 ./gradlew clean check
JAVA_HOME=/opt/java/25 ./gradlew fuzzTest
JAVA_HOME=/opt/java/25 ./gradlew jcstress
JAVA_HOME=/opt/java/25 ./gradlew jmh
JAVA_HOME=/opt/java/25 ./gradlew installDist
rg -n 'JetCheckAcceptanceTest' docs/reports/jetcheck-acceptance-spike.md
! rg -n 'AOudqRMBCgMI|rechecking\(' docs/reports/jetcheck-acceptance-spike.md
git diff --check
```

If any command fails, Phase 1 is the active phase until it passes and the fix is
committed.

## Phase 2 - First production vertical slice

Deliverables, in order:

1. Add
   `../moo-conformance-tests/src/moo_conformance/_tests/server/persistent_walking_skeleton.yaml`
   with suite `persistent_walking_skeleton` and test
   `boot_login_eval_checkpoint_restart_eval`. Starting from disposable bundled
   `Test.db`, the row logs in as Wizard, evaluates `return 6 * 7;` and requires
   `42`, evaluates `return dump_database();` and requires `0`, restarts with
   `restart_server`, reconnects as Wizard, evaluates `return 40 + 2;`, and
   requires `42`.
2. After its schema and CLI tests pass, commit the candidate row by itself in
   `moo-conformance-tests`. Prove it green against pinned stock Toast. If its expectation is wrong,
   correct only that row and amend the same candidate commit before rerunning.
   Then run it against the current Banteng HEAD. If red, amend the conformance
   commit with trailer `Banteng-red: <exact HEAD>` before any Phase 2 corrective
   production edit. If already green, keep it as validation and do not invent a
   corrective slice for that row.
3. Add the committed row path to the ToastCore oracle and Banteng target
   manifests' `test_suites` arrays and commit that Banteng manifest slice.
4. Implement the complete production path used by the row: Latin-1 source
   bytes, lexer, parser, AST, compiler, deterministic disassembly, explicit VM
   state, minimum v4 `Test.db` permanent-object bootstrap input with no
   anonymous-object requirement, minimum v17 checkpoint input, deterministic
   v17 output, immutable world records, real `WorldTxn`, production executor,
   tickets, validation, retry, ordered effects, socket ingress, Wizard login,
   command evaluation, `dump_database()`, atomic checkpoint replacement,
   process restart, reconnect, and evaluation.
5. Create the builtin manifest defined above with
   `new BuiltinSpec("dump_database", List.of(new CallShape(List.of(), List.of(),
   Optional.empty())), BuiltinPermissionRule.WIZARD_ONLY,
   BuiltinCostRule.fixed(0), EffectClass.DEFERRED_COMMIT, BuiltinOwner.SERVER,
   BuiltinCatalog::dumpDatabase)`. The VM call
   opcode owns its ordinary tick; this builtin adds no dynamic tick charge.
   Production evaluation dispatches through that entry. Prove its zero-argument
   call shape and `E_PERM` behavior with
   `server/dump_database.yaml` and `generated_builtins/dump_database.yaml` in
   the Phase 2 managed gate.
6. Add `--promote-numbers` to the concrete application configuration. Only the
   checked target manifest controls it; application code may not infer it from
   the database filename or server command.
7. Add `DurableTaskStateArchitectureTest`. It recursively rejects durable task
   snapshot fields assignable to `Thread`, `Future`, `CompletionStage`,
   `Socket`, `Channel`, or `Clock`; `VmSnapshotTest` proves
   the snapshot stores elapsed CPU budget as a value rather than a live anchor.
8. Delete or replace every placeholder and direct-world path exercised by this
   row. Do not add an alternate scheduler or alternate execution mode.

Before committing the candidate row, run its exact collection gate:

```bash
cd /mnt/c/Users/Q/code/moo-conformance-tests
UV_PROJECT_ENVIRONMENT=/root/.venvs/moo-conformance uv run moo-conformance \
  --collect-only \
  src/moo_conformance/_tests/server/persistent_walking_skeleton.yaml \
  -q -k boot_login_eval_checkpoint_restart_eval
```

Before any Phase 2 corrective production edit, run these exact single-row
managed commands from WSL after the candidate conformance commit:

```bash
cd /mnt/c/Users/Q/code/banteng
scripts/run_managed_wsl.sh toast stock \
  src/moo_conformance/_tests/server/persistent_walking_skeleton.yaml
JAVA_HOME=/opt/java/25 ./gradlew installDist
scripts/run_managed_wsl.sh banteng stock \
  src/moo_conformance/_tests/server/persistent_walking_skeleton.yaml
```

After the production implementation, run these exact broader managed commands:

```bash
cd /mnt/c/Users/Q/code/banteng
scripts/run_managed_wsl.sh toast stock \
  src/moo_conformance/_tests/server/persistent_walking_skeleton.yaml \
  src/moo_conformance/_tests/server/dump_database.yaml \
  src/moo_conformance/_tests/generated_builtins/dump_database.yaml
JAVA_HOME=/opt/java/25 ./gradlew installDist
scripts/run_managed_wsl.sh banteng stock \
  src/moo_conformance/_tests/server/persistent_walking_skeleton.yaml \
  src/moo_conformance/_tests/server/dump_database.yaml \
  src/moo_conformance/_tests/generated_builtins/dump_database.yaml
```

Additional gates:

```bash
cd /mnt/c/Users/Q/code/banteng
JAVA_HOME=/opt/java/25 ./gradlew test --tests moo.persistence.V17RoundTripTest
JAVA_HOME=/opt/java/25 ./gradlew test --tests moo.world.WorldTxnTest --tests moo.world.WorldTxnPropertyTest
JAVA_HOME=/opt/java/25 ./gradlew test --tests moo.runtime.PublicationSchedulerTest --tests moo.ArchitectureTest --tests moo.world.WorldAccessArchitectureTest --tests moo.runtime.DurableTaskStateArchitectureTest
JAVA_HOME=/opt/java/25 ./gradlew jcstress
JAVA_HOME=/opt/java/25 ./gradlew check
git diff --check
```

The named test classes are deliverables of this phase. `V17RoundTripTest` reloads
the newly written v17 file and compares restored world and task state; the tests
also cover byte-stable v17 output, repeatable reads, read/write/predicate
conflicts, atomic multi-object publication, topology validation, reverse-order
segment completion, ordered effects, and version reclamation. This phase
establishes the production execution path; externally observable concurrency
proof occurs after Phase 4 supplies real task creation and fork behavior.

## Phase 3 - Expand language, world, and persistence through production

Expand only through end-to-end slices that enter through the managed socket and
use the Phase 2 production scheduler, VM, `WorldTxn`, and checkpoint path:

1. values, variables, arithmetic, comparisons, strings, lists, maps, ranges,
   indexing, and collection updates;
2. control flow, loops, comprehensions, scatter assignment, calls, exceptions,
   and `finally`;
3. complete streaming v4 input only for the legacy permanent-object bootstrap
   surface; do not define or implement v4 anonymous-object input because those
   databases do not exist. Use complete v17 input plus deterministic v17 output
   for objects, properties, verbs, WAIFs, and anonymous objects;
4. parent/child, location/contents, inheritance, property, verb, player, and
   recycled-object indices; and
5. anonymous fork parsing and a serializable fork-request VM outcome. Task IDs,
   queueing, and named fork-variable binding remain Phase 4 runtime behavior.

Expand the Phase 2 builtin manifest with every handler invoked by the exact
Phase 3 suite paths below, including `add_property`, `delete_property`,
`create`, `recycle`, `new_waif`, and `dump_database`. These are Phase 3 vertical
slice dependencies, not deferred Phase 5 work.

Every VM snapshot round-trips at return, error, fork, suspension, and resume
boundaries and contains none of the forbidden live Java state listed above.

Add `MooValuePropertiesTest` for equality/hash/order, Latin-1 round trips, and
nested collection operations; `MooCompilerPropertiesTest` for generated
control-flow targets and byte-identical disassembly; and `VmSnapshotTest` for
the five named VM boundaries and the durable-state restrictions. Add
`AnonymousObjectPersistenceTest`, which starts each of the six committed v17
anonymous-object fixtures (`Anon1.db` through `Anon6.db`) through the production
runtime. It runs the exact boot counts `Anon1=2`, `Anon2=2`, `Anon3=2`,
`Anon4=3`, `Anon5=3`, and `Anon6=1`; every boot waits for fixture-defined
shutdown and its resulting v17 checkpoint, and the next boot consumes that
checkpoint. After every boot it reloads the checkpoint and compares every
anonymous object, pending-finalization root, property, verb, and recursive
reference while preserving aliases and cycles. It also freezes the Toast
canned-database lifecycle invariants: the second `Anon1` and `Anon2` checkpoints
are byte-identical to their first checkpoints, both `Anon3` checkpoints are
byte-identical to the input, the final `Anon4` checkpoint is byte-identical to
the input, the second and third `Anon5` checkpoints are byte-identical, and
`Anon6` removes its invalid pending-finalization value. Add
`WorldIndexPropertyTest`,
which generates create, recycle, reparent, move, player-flag, property, and verb
mutations and after every committed transaction recomputes and compares the
parent/child, location/contents, inheritance, property, verb, player, and
recycled-object indices.

Gates:

```bash
cd /mnt/c/Users/Q/code/banteng
JAVA_HOME=/opt/java/25 ./gradlew test --tests moo.value.MooValuePropertiesTest --tests moo.bytecode.MooCompilerPropertiesTest --tests moo.vm.VmSnapshotTest --tests moo.persistence.AnonymousObjectPersistenceTest --tests moo.world.WorldIndexPropertyTest
JAVA_HOME=/opt/java/25 ./gradlew check
git diff --check
```

Run the same exact path list once with `toast stock` and once with
`banteng stock`:

```bash
cd /mnt/c/Users/Q/code/banteng
scripts/run_managed_wsl.sh toast stock \
  src/moo_conformance/_tests/basic/value.yaml \
  src/moo_conformance/_tests/basic/types.yaml \
  src/moo_conformance/_tests/basic/arithmetic.yaml \
  src/moo_conformance/_tests/basic/string.yaml \
  src/moo_conformance/_tests/basic/list.yaml \
  src/moo_conformance/_tests/language/error_authority.yaml \
  src/moo_conformance/_tests/language/control_flow.yaml \
  src/moo_conformance/_tests/language/index_and_range.yaml \
  src/moo_conformance/_tests/language/looping.yaml \
  src/moo_conformance/_tests/language/scatter.yaml \
  src/moo_conformance/_tests/language/splice.yaml \
  src/moo_conformance/_tests/language/try_except.yaml \
  src/moo_conformance/_tests/basic/object.yaml \
  src/moo_conformance/_tests/basic/property.yaml \
  src/moo_conformance/_tests/objects/property_lookup.yaml \
  src/moo_conformance/_tests/server/dump_persistence.yaml \
  src/moo_conformance/_tests/server/boolean_dump_persistence.yaml \
  src/moo_conformance/_tests/server/error_dump_persistence.yaml \
  src/moo_conformance/_tests/server/float_boundary_dump_persistence.yaml \
  src/moo_conformance/_tests/server/integer_dump_persistence.yaml \
  src/moo_conformance/_tests/server/list_dump_persistence.yaml \
  src/moo_conformance/_tests/server/map_dump_persistence.yaml \
  src/moo_conformance/_tests/server/object_dump_persistence.yaml \
  src/moo_conformance/_tests/server/string_dump_persistence.yaml \
  src/moo_conformance/_tests/server/waif_dump_persistence.yaml \
  src/moo_conformance/_tests/server/startup_repair_anon1.yaml \
  src/moo_conformance/_tests/server/startup_repair_anon2.yaml \
  src/moo_conformance/_tests/server/startup_repair_anon3.yaml \
  src/moo_conformance/_tests/server/startup_repair_anon4.yaml \
  src/moo_conformance/_tests/server/startup_repair_anon5.yaml \
  src/moo_conformance/_tests/server/startup_repair_anon6.yaml
scripts/run_managed_wsl.sh banteng stock \
  src/moo_conformance/_tests/basic/value.yaml \
  src/moo_conformance/_tests/basic/types.yaml \
  src/moo_conformance/_tests/basic/arithmetic.yaml \
  src/moo_conformance/_tests/basic/string.yaml \
  src/moo_conformance/_tests/basic/list.yaml \
  src/moo_conformance/_tests/language/error_authority.yaml \
  src/moo_conformance/_tests/language/control_flow.yaml \
  src/moo_conformance/_tests/language/index_and_range.yaml \
  src/moo_conformance/_tests/language/looping.yaml \
  src/moo_conformance/_tests/language/scatter.yaml \
  src/moo_conformance/_tests/language/splice.yaml \
  src/moo_conformance/_tests/language/try_except.yaml \
  src/moo_conformance/_tests/basic/object.yaml \
  src/moo_conformance/_tests/basic/property.yaml \
  src/moo_conformance/_tests/objects/property_lookup.yaml \
  src/moo_conformance/_tests/server/dump_persistence.yaml \
  src/moo_conformance/_tests/server/boolean_dump_persistence.yaml \
  src/moo_conformance/_tests/server/error_dump_persistence.yaml \
  src/moo_conformance/_tests/server/float_boundary_dump_persistence.yaml \
  src/moo_conformance/_tests/server/integer_dump_persistence.yaml \
  src/moo_conformance/_tests/server/list_dump_persistence.yaml \
  src/moo_conformance/_tests/server/map_dump_persistence.yaml \
  src/moo_conformance/_tests/server/object_dump_persistence.yaml \
  src/moo_conformance/_tests/server/string_dump_persistence.yaml \
  src/moo_conformance/_tests/server/waif_dump_persistence.yaml \
  src/moo_conformance/_tests/server/startup_repair_anon1.yaml \
  src/moo_conformance/_tests/server/startup_repair_anon2.yaml \
  src/moo_conformance/_tests/server/startup_repair_anon3.yaml \
  src/moo_conformance/_tests/server/startup_repair_anon4.yaml \
  src/moo_conformance/_tests/server/startup_repair_anon5.yaml \
  src/moo_conformance/_tests/server/startup_repair_anon6.yaml
```

## Phase 4 - Complete production server and task semantics

Complete the blocking `ServerSocket` listener, virtual-thread connection
lifecycle, split-IAC Telnet parser, ordered input/output queues, login, command
parsing, verb lookup, task creation, task IDs, task registry, suspend, read,
resume, fork, kill, and queued-task inspection. Every operation uses the Phase 2
production tickets, executor, `WorldTxn`, effect journal, and ordered
publication path. Install and register the real task-registry-backed
`queued_tasks()` implementation before other Phase 4 source work.
Expand the builtin manifest with every task, object, property, verb, listener,
connection, and server handler invoked by the exact Phase 4 suite paths below.
Those handlers are Phase 4 vertical slice dependencies; Phase 5 completes only
the remaining manifest surface.
Phase 4 also includes v17 round-trip and startup restoration of the delayed
fork state exercised by `audit_suspended_task_survives_restart` and
`audit_pending_forked_task_survives_genuine_offline_restart`.

Before Phase 5, complete the one production `SUSPENDING_HOST` path. This is
Phase 4 task infrastructure, not Phase 6 concurrency work:

1. Add per-task thread mode to `VmState` and `VmSnapshot`, defaulting to enabled
   for both pinned profiles. `LambdaMooV17Codec` must read, retain, write, and
   restore both valid v17 thread-mode values instead of accepting only literal
   `1` and discarding it.
2. Register `set_thread_mode` in the single manifest with its pinned contract:
   zero or one `INTEGER` argument, `BuiltinPermissionRule.ANY`, fixed zero cost,
   `EffectClass.DEFERRED_COMMIT`, and `BuiltinOwner.VM`. A zero-argument call
   returns the current activation's mode; a one-argument call applies MOO
   truthiness to that activation only and returns zero. The state change must
   survive suspension, retry, and v17 round-trip without leaking to another
   task.
3. Replace the value-only host wake across `BuiltinCatalog.Result`, `MooVm`,
   `VmState`, `VmSnapshot`, and `PublicationScheduler` with one completion that
   can resume the same activation with either a value or a catchable MOO error.
   A Java exception remains scheduler failure and must not substitute for a MOO
   error.
4. `PublicationScheduler` owns host-work submission through its existing
   bounded CPU executor after the invoking VM segment yields. Do not add a
   second scheduler or executor. Submission rejection maps to `E_QUOTA`;
   completion re-enters the existing ready queue and publication-ticket path.
   Virtual threads may wait for completion but may not execute the CPU work.
5. Register unconditional pinned builtin `all_members` as the first production
   `SUSPENDING_HOST` producer, before `sort`, with contract `ANY, LIST`,
   `BuiltinPermissionRule.ANY`, fixed zero cost, `EffectClass.SUSPENDING_HOST`,
   and `BuiltinOwner.VM`. Enabled thread mode must suspend and resume through
   the production host path; disabled mode runs the same callback synchronously.
6. Extend existing `VmSnapshotTest`, `MooVmTest`, `PublicationSchedulerTest`,
   `QueuedTaskV17CodecTest`, and `BuiltinCatalogTest` to prove thread-mode
   round-trip, value resume, catchable-MOO-error resume, `E_QUOTA` rejection,
   task-local mode isolation, `all_members` threaded/synchronous execution, and
   absence of an alternate scheduler or executor.

Do not add `background_test`. It is guarded by `#ifdef BACKGROUND_TEST` in both
pinned Toast sources, neither pinned configuration defines that macro, and its
managed rows explicitly skip when the builtin is absent. Adding it to Banteng
would create a canonical name absent from the pinned production runtimes.

The task CPU limit uses Toast's virtual/process-CPU semantics, not wall time.
The exact authority is
`audit_task_scheduling_toast_oracle::audit_server_options_fg_seconds_runtime`
and `audit_task_scheduling_toast_oracle::audit_server_options_bg_seconds_runtime`
in the Phase 4 path list below.

Run this exact path list once with `toast stock` and once with `banteng stock`:

```bash
cd /mnt/c/Users/Q/code/banteng
scripts/run_managed_wsl.sh toast stock \
  src/moo_conformance/_tests/server/lifecycle.yaml \
  src/moo_conformance/_tests/server/command_parsing.yaml \
  src/moo_conformance/_tests/fork/fork_observation.yaml \
  src/moo_conformance/_tests/fork/fork_timing.yaml \
  src/moo_conformance/_tests/capabilities/queued_tasks.yaml \
  src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml \
  src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml
scripts/run_managed_wsl.sh banteng stock \
  src/moo_conformance/_tests/server/lifecycle.yaml \
  src/moo_conformance/_tests/server/command_parsing.yaml \
  src/moo_conformance/_tests/fork/fork_observation.yaml \
  src/moo_conformance/_tests/fork/fork_timing.yaml \
  src/moo_conformance/_tests/capabilities/queued_tasks.yaml \
  src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml \
  src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml
scripts/run_managed_wsl.sh toast stock \
  src/moo_conformance/_tests/builtins/background_threads.yaml \
  src/moo_conformance/_tests/builtins/set_thread_mode_call_shapes.yaml \
  src/moo_conformance/_tests/generated_builtins/set_thread_mode.yaml \
  src/moo_conformance/_tests/builtins/all_members_call_shapes.yaml \
  src/moo_conformance/_tests/generated_builtins/all_members.yaml
scripts/run_managed_wsl.sh banteng stock \
  src/moo_conformance/_tests/builtins/background_threads.yaml \
  src/moo_conformance/_tests/builtins/set_thread_mode_call_shapes.yaml \
  src/moo_conformance/_tests/generated_builtins/set_thread_mode.yaml \
  src/moo_conformance/_tests/builtins/all_members_call_shapes.yaml \
  src/moo_conformance/_tests/generated_builtins/all_members.yaml
JAVA_HOME=/opt/java/25 ./gradlew test \
  --tests moo.vm.VmSnapshotTest \
  --tests moo.vm.MooVmTest \
  --tests moo.runtime.PublicationSchedulerTest \
  --tests moo.persistence.QueuedTaskV17CodecTest \
  --tests moo.builtin.BuiltinCatalogTest
JAVA_HOME=/opt/java/25 ./gradlew check
git diff --check
```

Commit the kept phase before Phase 5.

## Phase 5 - Complete builtin manifest and implementation

Complete the single `BuiltinSpec` table defined above. Delete the remaining
independent dispatch-name and effect-class switches, aliases absent from Toast,
and every placeholder result.

Add `scripts/extract_toast_builtin_names_wsl.sh MANIFEST OUTPUT`. It verifies the
manifest first, reads only that manifest's pinned Toast source, and writes the
sorted canonical registered-name set. Commit exact stock and Mongoose name
fixtures under `src/test/resources/moo/builtin/`. `BuiltinCatalogTest` compares
the production manifest with those fixtures without accessing an external
checkout. The separate extraction gate below regenerates temporary files from
the pinned sources and compares them byte-for-byte with the fixtures.

`BuiltinCatalogTest` proves one-to-one correspondence between manifest entries
and dispatch implementations, complete contract fields, and complete canonical
name coverage for each selected profile. No builtin reads or changes committed
world state except through the current `WorldTxn`.

Gates:

```bash
cd /mnt/c/Users/Q/code/banteng
scripts/extract_toast_builtin_names_wsl.sh profiles/toast/stock-wsl-testdb.json /tmp/banteng-builtins-stock.txt
cmp src/test/resources/moo/builtin/toast-builtins-stock.txt /tmp/banteng-builtins-stock.txt
scripts/extract_toast_builtin_names_wsl.sh profiles/toast/mongoose-wsl-mongoose.json /tmp/banteng-builtins-mongoose.txt
cmp src/test/resources/moo/builtin/toast-builtins-mongoose.txt /tmp/banteng-builtins-mongoose.txt
rm /tmp/banteng-builtins-stock.txt /tmp/banteng-builtins-mongoose.txt
JAVA_HOME=/opt/java/25 ./gradlew test --tests moo.builtin.BuiltinCatalogTest
scripts/run_managed_wsl.sh toast stock src/moo_conformance/_tests/builtins src/moo_conformance/_tests/generated_builtins
scripts/run_managed_wsl.sh banteng stock src/moo_conformance/_tests/builtins src/moo_conformance/_tests/generated_builtins
scripts/run_managed_wsl.sh toast mongoose src/moo_conformance/_tests/builtins src/moo_conformance/_tests/generated_builtins
scripts/run_managed_wsl.sh banteng mongoose src/moo_conformance/_tests/builtins src/moo_conformance/_tests/generated_builtins
JAVA_HOME=/opt/java/25 ./gradlew check
git diff --check
```

ToastCore uses the same pinned stock Toast executable and therefore the same
stock canonical builtin-name fixture. Its checked-in profile contains only the
Phase 2 persistent walking skeleton and has no separate Phase 5
builtin-directory gate.

## Phase 6 - Prove and harden production concurrency

Do not create a serial scheduler, serial mode, commit-descriptor alternative, or
Barn benchmark dependency.

Add these exact proof artifacts:

- `moo.runtime.ConcurrentExecutionTest`: independent CPU segments overlap on
  distinct production executor threads;
- `moo.runtime.ConcurrentSchedulerPropertyTest`: generated completion orders,
  conflicts, retries, task IDs, and effects preserve invariants frozen by the
  exact Phase 4 managed rows; concurrent PRNG consumption must equal execution
  of the same generated task list in ready order with the same seed, using the
  Toast-proven `random` and `reseed_random` rows from Phase 5;
- `moo.runtime.ConcurrentSchedulerStressTest`: bounded-queue saturation,
  repeated conflicts, retry bounds, and eventual progress;
- `moo.jcstress.WorldPublicationTest`: readers observe only complete committed
  revisions.

Add Gradle task `schedulerStress` that runs only the named stress class. Extend
`jcstress` to include both checked-in publication tests.

Gates:

```bash
cd /mnt/c/Users/Q/code/banteng
JAVA_HOME=/opt/java/25 ./gradlew test --tests moo.runtime.ConcurrentExecutionTest --tests moo.runtime.ConcurrentSchedulerPropertyTest
JAVA_HOME=/opt/java/25 ./gradlew schedulerStress
JAVA_HOME=/opt/java/25 ./gradlew jcstress
scripts/run_managed_wsl.sh toast stock \
  src/moo_conformance/_tests/server/lifecycle.yaml \
  src/moo_conformance/_tests/server/command_parsing.yaml \
  src/moo_conformance/_tests/fork/fork_observation.yaml \
  src/moo_conformance/_tests/fork/fork_timing.yaml \
  src/moo_conformance/_tests/capabilities/queued_tasks.yaml \
  src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml \
  src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml
scripts/run_managed_wsl.sh banteng stock \
  src/moo_conformance/_tests/server/lifecycle.yaml \
  src/moo_conformance/_tests/server/command_parsing.yaml \
  src/moo_conformance/_tests/fork/fork_observation.yaml \
  src/moo_conformance/_tests/fork/fork_timing.yaml \
  src/moo_conformance/_tests/capabilities/queued_tasks.yaml \
  src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml \
  src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml
JAVA_HOME=/opt/java/25 ./gradlew check
git diff --check
```

The phase passes only when concurrent work is observed and every externally
visible result matches the exact Toast-proven rows for task order, task IDs,
world state, and output.

## Phase 7 - Operational checkpoint and restart

Extend the Phase 4 delayed-fork checkpoint path with complete queued and
suspended task round trips, checkpoint-time revision retention, bounded
reclamation, and graceful shutdown.
There is no panic-dump feature in this plan.

Add:

- `../moo-conformance-tests/src/moo_conformance/_tests/server/checkpoint_operational.yaml`
  with suite `checkpoint_operational` and exact tests
  `queued_task_runs_once_after_restart`,
  `suspended_task_resumes_once_after_restart`, and
  `checkpoint_during_active_task_exposes_only_committed_state`;
- `moo.persistence.ActiveCheckpointTest`;
- `moo.persistence.AtomicCheckpointFailureTest`;
- `moo.persistence.RepeatedRestartTest`;
- `moo.world.CheckpointRetentionTest`;
- `moo.runtime.GracefulShutdownTest`;
- `moo.runtime.JfrTemplateTest`;
- `moo.app.OperationsCommandTest`;
- `src/main/resources/jfr/banteng-production.jfc`; and
- `docs/operations.md` containing only executable launch, checkpoint, JFR, and
  recovery commands.

Commit the candidate three-row conformance suite, prove it green on pinned
stock Toast, and run it on the current Banteng HEAD. Amend only the candidate
suite until Toast-green. If Banteng is red, amend trailer
`Banteng-red: <exact HEAD>` before Phase 7 production edits; if already green,
keep it as validation and do not invent a corrective slice.
`RepeatedRestartTest` performs 100 checkpoint/restart cycles and after every
cycle compares the committed world, queued/suspended task IDs and states, and
ordered output with the prior cycle. `GracefulShutdownTest` requires listener
closure, executor termination, final checkpoint completion, and zero live
Banteng-owned threads. `JfrTemplateTest` loads the checked-in JFC with the JDK
JFR configuration API. `OperationsCommandTest` executes every fenced shell
command in `docs/operations.md` against disposable paths. For a command that
starts the server, the test launches the exact command, waits for its documented
listener to accept a connection, sends `SIGTERM`, requires exit zero, and
verifies the documented final checkpoint and process cleanup. `--help` never
substitutes for executing a documented operational command.

Gates:

```bash
cd /mnt/c/Users/Q/code/banteng
scripts/run_managed_wsl.sh toast stock src/moo_conformance/_tests/server/checkpoint_operational.yaml
scripts/run_managed_wsl.sh banteng stock src/moo_conformance/_tests/server/checkpoint_operational.yaml
JAVA_HOME=/opt/java/25 ./gradlew test --tests moo.persistence.ActiveCheckpointTest --tests moo.persistence.AtomicCheckpointFailureTest --tests moo.persistence.RepeatedRestartTest --tests moo.world.CheckpointRetentionTest --tests moo.runtime.GracefulShutdownTest --tests moo.runtime.JfrTemplateTest --tests moo.app.OperationsCommandTest
JAVA_HOME=/opt/java/25 ./gradlew schedulerStress
JAVA_HOME=/opt/java/25 ./gradlew check
git diff --check
```

Failure injection must prove the last good database is never replaced by a
partial file. Active checkpoints must not stop unrelated VM segments, and all
checkpoint-held revisions must become reclaimable after completion.

## Phase 8 - Exact convergence and release

Work on one failing YAML suite or test family at a time. Prove the exact row on
its pinned Toast profile, prove it red on Banteng, implement one owned fix,
prove the row green, run its entire suite file, and either commit or fully
restore the slice before selecting another failure.

After the last conformance change is committed, add
`profiles/conformance-suite.ref` containing only the exact
`moo-conformance-tests` HEAD. `scripts/run_managed_wsl.sh` must reject a checkout
whose HEAD differs from that file.

Add executable `scripts/release_smoke_wsl.sh`. It creates a unique path with
`mktemp -d /tmp/banteng-release.XXXXXX`, removes that empty directory, registers
a detached `git worktree` at the path, installs an EXIT trap that runs
`git worktree remove --force` for the registered path, and prints exactly one
line `BANTENG_RELEASE_WORKTREE=<absolute-path>` before build output. After
registration it sets `JAVA_HOME="${JAVA_HOME:-/opt/java/25}"` and runs the clean commands
`./gradlew clean installDist`, `build/install/banteng/bin/banteng --version`,
and `build/install/banteng/bin/banteng --help`. The trap must succeed after both
passing and deliberately failing invocations. `scripts/test_release_smoke_wsl.sh`
runs the smoke script normally, then runs
`JAVA_HOME=/nonexistent bash scripts/release_smoke_wsl.sh` and requires a
nonzero exit. After each invocation it proves that the created path is absent
from both the filesystem and `git worktree list --porcelain`, using only the
path parsed from that invocation's `BANTENG_RELEASE_WORKTREE=` line.

Final gates, in order:

```bash
cd /mnt/c/Users/Q/code/banteng
scripts/run_managed_wsl.sh toast stock profile
scripts/run_managed_wsl.sh banteng stock profile
scripts/run_managed_wsl.sh toast mongoose profile
scripts/run_managed_wsl.sh banteng mongoose profile
scripts/run_managed_wsl.sh toast toastcore profile
scripts/run_managed_wsl.sh banteng toastcore profile
JAVA_HOME=/opt/java/25 ./gradlew clean check
JAVA_HOME=/opt/java/25 ./gradlew fuzzTest
JAVA_HOME=/opt/java/25 ./gradlew jcstress
JAVA_HOME=/opt/java/25 ./gradlew schedulerStress
JAVA_HOME=/opt/java/25 ./gradlew jmh
JAVA_HOME=/opt/java/25 ./gradlew installDist
bash scripts/test_release_smoke_wsl.sh
bash scripts/release_smoke_wsl.sh
git diff --check
```

The project is complete only after all final gates and every earlier phase gate
pass on the same kept Banteng revision and the exact conformance revision in
`profiles/conformance-suite.ref`.

## First-release exclusions

- no alternate serial scheduler and no Banteng-internal behavioral reference;
  pinned Toast remains the behavioral authority;
- no mutable Barn branch, Barn fixture, or Barn working-tree dependency;
- no arbitrary Java plugin API;
- no alternate storage backend, SQL world store, RocksDB, event store, or WAL;
- no actor-per-object architecture, Netty, or reactive streams;
- no Kotlin coroutine or persisted Java continuation/thread stack; and
- no native-image support in the first release.
