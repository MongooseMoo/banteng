# Multiple-inheritance implementation checkpoint

## Findings and observations

- The exact Mongoose v17 fixture stores object `#8` parents as the ordered list
  `[#5, #20679]`.
- The prior model reduced every object and anonymous object to one `long parent`; the v17
  reader explicitly rejected lists longer than one and the writer always emitted one object.
- Pinned Toast traverses parents depth-first in stored order and suppresses repeat visits.
- Pinned Toast accepts an object or list of objects for `create()` and parent mutation;
  `parent()` is the deprecated first-parent view, while `parents()` returns the full list.
- Property slots are ordered by the unique ancestor traversal. A clear inherited slot takes
  its effective fallback through the first direct parent providing that property.

## Current implementation state

- `WorldObject` and `WorldAnonymousObject` now store immutable ordered `List<Long> parents`,
  with singular constructors retained only as v4/caller adapters.
- The v17 codec now reads object-or-list parents, writes object-or-list parents, validates
  cycles and reciprocal permanent parent/child relations, and reconstructs MI property slots.
- `WorldTxn` now has canonical ordered ancestry and ordered verb/property lookup; multi-parent
  create/reparent/recycle and property-layout rebuilding are being completed atomically.
- `parent`, `parents`, `children`, `chparent`, `chparents`, and list-parent `create` builtin
  paths have been added and are being compile-integrated.
- VM `pass` and verb-location lookup are being changed from a singular chain to canonical
  ancestry order.

## Compile result and blocker

- WSL Java 25 `./gradlew compileJava --no-daemon` passes cleanly.
- The first focused run executed 172 tests. Existing world, v17 synthetic roundtrip, VM, and
  most builtin tests passed. Two failures remain:
  - the builtin manifest's exact-name fixture still expects 115 entries and must include the
    four newly required MI builtins;
  - exact `mongoose.db` reading advances beyond object `#8` but loses alignment in object `#24`
    property slot 21. The recorded bogus error code is `7863983328251084801`, proving the
    reader is consuming a multiline v17 string as a one-line string.
- There is no external blocker. The fixture failure is now the active codec correction.
- The apparent multiline-string diagnosis was wrong. The exact value
  `7863983328251084801` has low 32 bits `1` (`E_TYPE`). Pinned Toast assigns a 32-bit error
  enum through a union and persists the 64-bit numeric member, so stale high bits are legal;
  the codec now masks persisted TYPE_ERR values to their low 32 bits.
- The exact fixture is `101,244,108` bytes and contains `49,099` `#...` object/program
  headers. Gradle's repository-wide JVM authority is already `-Xmx2g` in
  `gradle.properties`, while `--info` proved Gradle Test Executor was independently forced
  to `-Xmx512m`.
- Duplicate restoration was inspected and removed: permanent and anonymous World records
  now share one restored-property map; each raw object and each restored object owns one
  bounded property-slot list; World/WorldHistory copy maps but retain immutable object and
  property-list references. No cyclic object/property container is introduced.
- `build.gradle.kts` now applies the existing 2 GiB repository ceiling consistently to Test
  workers.
- Exact fixture gate: `QueuedTaskV17CodecTest.readsEveryTaskRecordFromPinnedMongooseFixture`
  passed in 1m16s, including object `#8` parents `[5, 20679]` and reciprocal children.
- Combined focused MI gate passed in 2m40s: diamond ancestry/property layout, exact #8-shape
  roundtrip, mutation validation/recycle, builtin forms, and VM lookup/pass precedence.
- Broader world/v17/builtin/VM gate passed in 57s.
- A final recycle audit found that anonymous children also need every recycled direct parent
  spliced into their ordered parent list. That correction and regression passed; the final
  WorldTxn + MI + MooVm post-fix gate passed in 16s.
- `git diff --check` passed before the final anonymous-recycle edit and must be rerun once.

## Next action

- An adversarial review identified nine unresolved production defects after the initial gates:
  exact `chparent` permissions/errors; complete four-argument `create` plus `initialize`;
  anonymous conflict tracking; direct-parent-subtree-only `pass`; anonymous call/pass; property
  preservation by defining ancestor; recursive anonymous descendant rebuilding; recycle child
  uniqueness; and bounded affected-subtree/cached ancestry work.
- Pinned Toast authorities now confirmed locally include `objects.cc:312-430` for create,
  `objects.cc:553-600` for chparent/chparents, `execute.cc:681-719` for pass, and
  `db_properties.cc:937-1048` for property/anonymous descendant rebuilding.
- The active correction will add each missing regression, rerun the exact fixture and broader
  WSL gates, and leave the cohesive slice uncommitted for parent integration.
- Keep this note untracked and exclude it from all staging and commits.

## Adversarial correction checkpoint

- The accidental Windows Gradle compile was stopped by terminating its isolated Gradle Java
  process. Every remaining gate uses the exact authorized Debian WSL command with
  `JAVA_HOME=/opt/java/25`.
- The exact Debian WSL `compileJava` gate currently fails only because two full `Result`
  constructor sites still need the newly added empty initialize-request component; the compiler
  reported those sites at `BuiltinCatalog.java:4598` and `:4621`, and both sites were patched.
- Corrected so far: chparent/chparents controls2, fertile, invalid, recursive-move distinctions;
  complete create argument parsing and ownership/anonymous-parent permissions; explicit runtime
  initialize request and return-value preservation; anonymous read/write conflict tracking and
  publication rejection; one global tri-color topology traversal instead of repeated per-object
  cycle DFS.
- The initialize request has now been connected to the existing VM verb-frame execution path for
  permanent and anonymous created values. Compilation after that connection is the immediate next
  gate.
- Still incomplete: frame verb-location widening plus direct-parent-subtree pass; preservation by
  defining ancestor; recursive affected anonymous rebuilds; reciprocal child deduplication;
  affected-subtree layout rebuilding/caching; all nine regression groups and final exact fixture.
- No external blocker. The next action is the exact Debian WSL compile, then the remaining source
  corrections in the order above.

## Adversarial correction checkpoint 2

- The exact authorized Debian WSL compile completed successfully in 1m19s after the create and
  anonymous-vloc integration.
- Findings 1 through 5 are implemented in production source: exact chparent permissions/errors;
  complete create parsing/permissions and real initialize call; anonymous transaction conflicts;
  direct-parent-subtree pass; and anonymous receiver/vloc propagation through VM frames,
  snapshots, forks, and queued-task durability.
- Finding 6 is now implemented in the layout algorithm by preserving an inherited override only
  under the same `(defining ancestor id, normalized property name)` key. A same-name definition
  arriving from an unrelated ancestor is initialized as a new clear/CHOWN-derived slot.
- Findings 7 through 9 are currently being integrated at mutation call sites: permanent rebuilds
  are bounded to the changed root and descendants; anonymous bodies whose permanent ancestry
  intersects that affected subtree are rebuilt; recycle reciprocal child insertion is deduplicated;
  topology cycle validation is one global tri-color traversal.
- The immediate next action is another exact Debian compile to catch incomplete call-site
  signatures, then focused regressions for all nine findings and the exact fixture/broader gates.
- The note remains untracked and must not be staged.

## Adversarial correction checkpoint 3

- The post-integration exact Debian compile passed in 1m19s.
- The first four-class focused gate compiled 178 tests and found three failures; 175 passed.
- One new builtin permission fixture was structurally invalid because object #6 named parent #4
  without #4 naming #6 as a child; this is a test-fixture correction, not production behavior.
- The older MI create test now receives no value because its two owner-controlled parents have
  conflicting same-name-free layouts only if create parsing or permission returned an error; the
  exact Result error is the next diagnostic.
- The older verb-frame test now errors because `create(#-1)` goes through the new owner/permission
  path after `set_task_perms(#9)` where #9 is not present in the fixture. The exact Toast rule is
  that an invalid current programmer still defaults as the requested owner and then fails owner
  validity; the existing test may need a valid #9 object or its expectation must reflect authority.
- Immediate next action: fix the invalid reciprocal fixture, inspect exact Result/error values for
  the two behavior failures, correct production only where pinned Toast requires, then rerun the
  focused gate.

## Adversarial correction final gate record

- All nine review findings are corrected in production source and have direct regressions.
- The four-class focused gate passed all 178 tests in 46s after exact fixture repairs.
- The exact pinned 101MB fixture passed with a 2 GiB worker in 47s and with a 1 GiB worker in
  2m27s (the latter rebuilt six tasks because the heap configuration changed).
- The broader world, v17 roundtrip/queued-task, builtin, and VM gate passed in 1m9s at 1 GiB;
  this included the exact fixture and the new anonymous queued-task vloc roundtrip.
- The original 512 MiB worker budget was remeasured after all bounded graph/layout corrections.
  It failed in 56s with `Could not complete execution for Gradle Test Executor 1` and
  `Java heap space`. The build therefore retains 1 GiB, the smallest measured passing budget.
- `git diff --check` passed before the final heap measurement; rerun it once after restoring the
  final 1 GiB line. No files were staged or committed by this subtask.
- This note remains untracked and must not be staged.

## Closure-review correction checkpoint

- Pinned quota authority was clarified: every valid owner, including wizard and delegated-wizard
  owner paths, goes through the existing integer `ownership_quota` property decrement exactly as
  `objects.cc:383-385` and `quota.cc:37-48` specify when quota management is active.
- Create now checks/decrements that property through WorldTxn before permanent/anonymous allocation
  or initialize dispatch. Exhausted quota returns E_QUOTA without allocating either record kind.
- Added quota assertions for permanent, anonymous, delegated owner, wizard owner, exhaustion,
  absent initialize dispatch, and unchanged permanent/anonymous counts on failure.
- Removed the magic create-result local. Frame and VmSnapshot.Frame now carry a typed optional
  create-return override; only `pushCreateInitializeFrame` sets it, snapshot/restore retain it,
  byte-size accounting includes it, and finishFrame consumes it.
- Added an execution collision regression where both ordinary and initialize verbs assign the legal
  local `__banteng_create_result`, plus a direct snapshot/restore regression proving the typed
  override survives durability and ordinary frames retain ordinary return values.
- Added the indirect anonymous recycle regression: an anonymous object parented below a permanent
  descendant is rebuilt after its indirect ancestor is recycled.
- Remaining closure work: bounded synthetic large-DAG regression, exact Debian compile/focused
  tests, exact fixture, broader gate, and diff-check. No current blocker; nothing is staged.

## Closure-review final gate record

- Added the bounded 800-object two-parent DAG regression. Full topology validation succeeds; a
  leaf reparent replaces only the leaf and its four old/new direct-parent records, while unrelated
  object #400 retains reference identity.
- Exact Debian compileTestJava passed in 56s after correcting two test-only NullAway diagnostics.
- Focused WorldTxn/MI/BuiltinCatalog/MooVm/VmSnapshot gate passed in 17s.
- Exact pinned 101MB fixture passed at the final 1 GiB worker budget in 49s.
- Broader world, v17 roundtrip/queued-task, builtin, VM, and VmSnapshot gate passed in 1m4s.
- `git diff --check` passes. The old magic local name occurs only in collision regressions and no
  longer appears in production source.
- All closure-review defects and coverage gaps are resolved. No files were staged or committed;
  this note remains untracked.
