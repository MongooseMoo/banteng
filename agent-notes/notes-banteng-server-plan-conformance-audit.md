# Banteng server/plan/conformance audit checkpoint

## Current findings

- `master` is four commits ahead of `origin/master`: Phase 4 task/host semantics,
  Toast build-configuration pinning, Phase 5 numeric builtins, and the integer
  division/modulus correction.
- The working tree is an active uncommitted implementation, not a completed
  phase: 34 tracked files differ by 6,136 insertions and 720 deletions, with
  critical untracked production/test files including `ToastV17ProgramLayout`
  and its test.
- Current WSL `JAVA_HOME=/opt/java/25 ./gradlew --no-daemon check` ran 340 tests
  and failed 7. Failures cover queued/suspended Mongoose v17 import, four exact
  Toast program-layout fixture expectations, one scheduler import regression,
  and non-total map-key ordering across v17 round trip.
- The current pinned `fixtures/mongoose.db` checksum is the plan's new
  `29ae...ec91` value, but its actual task section is 6 queued, 16 suspended,
  0 interrupted, and 0 active connections. The dirty plan still requires the
  old checkpoint's 14 suspended, 2 interrupted, and 6 connections. Tests are
  split between the two identities: `QueuedTaskV17CodecTest` expects 16 while
  `ToastV17ProgramLayoutTest` seeks literal `14 suspended tasks`.
- The queued-task codec still represents queued locals as
  `Map<String, MooValue>` and calls the generic value reader, so a real tag-6
  unset local fails with `unsupported Phase 2 v17 value tag 6`.
- The exact current Phase 4 Banteng stock managed command collected 83 tests
  but executed only capability admission. Admission failed for
  `feature.connectable_listener_port` and `feature.ephemeral_listen`; no Phase 4
  semantic row ran. Banteng has `listen` and `boot_player` but lacks
  `listeners`, `open_network_connection`, and `unlisten`.
- Phase 0 authority is currently healthy: managed-runner self-test passed, all
  three pinned Toast profile verifiers passed, and the conformance CLI selection
  regressions passed 32/32.
- The sibling conformance checkout is at `origin/main` commit `71e2ba2` but is
  dirty: four tracked YAML files add 15 uncommitted rows and many unrelated
  untracked artifacts. The Phase 4 admission failure is in clean current-main
  controller code, not those four YAML changes.
- Phase 5 is incomplete. The catalog has 122 entries (115 at HEAD plus seven
  dirty additions), while the plan-required extraction script and both checked-
  in canonical builtin-name fixtures do not exist. `load_server_options` and
  `run_gc` still return placeholder zero results.

## Architecture observations

- The core direction remains substantially aligned: one `PublicationScheduler`,
  one platform-thread `ThreadPoolExecutor`, `WorldTxn` attempt/commit flow,
  ticket-ordered publication, package-private world internals, and value-only
  durable snapshots. The architecture tests were not among the seven failures.
- One exact plan invariant is violated after `thread_pool` reconfiguration:
  `workers` and executor core/max sizes change, but the `ArrayBlockingQueue`
  remains at its constructor-time capacity. It is therefore no longer
  `workers * 4` after a runtime size change.
- Phase ordering/status is invalid: `e8bf5ab` claims Phase 4 complete, but the
  dirty plan later added substantial Phase 4 prerequisites that remain
  uncommitted and red. Phase 5 work is not authorized while that gate is red.

## Blocker

The plan, fixture identity, and live conformance admission contract disagree.
Until those authorities are reconciled, further builtin expansion cannot yield
a truthful completed phase.

## Next action

1. Repair the plan authority first: explicitly separate or select the pristine
   task-restoration checkpoint versus the login-capable Mongoose fixture, and
   make counts/tests match the chosen identity without dropping interrupted or
   active-connection proof silently.
2. Record an exact conformance revision for each kept phase gate and account for
   the current admission prerequisites (`listeners`,
   `open_network_connection`, `unlisten`) before rerunning Phase 4.
3. Recover the dirty Phase 4 work as narrow slices: tag-6 queued locals and
   exact v17 task restoration, program-layout fixture alignment, map round-trip,
   then admission builtins. Make `gradlew check` green before Phase 5.
4. Run the exact Phase 4 stock and Mongoose managed paths on the selected clean
   conformance revision and commit only reviewed, phase-owned paths.
5. Resume Phase 5 with the extractor and canonical name fixtures first, then
   complete builtin families one Toast-proven slice at a time.

This file is an untracked continuation note and must not be staged or committed.
# 2026-08-12 Mongoose scope correction checkpoint

## Current findings

- The conformance authority selects stock `Test.db` and ToastCore only.
- Mongoose and `fixtures/mongoose.db` were locally introduced as a third release profile; the conformance suite does not require them.
- `PROMOTE_NUMBERS` is desired optional product behavior, but no selected conformance row or profile requires it.

## Current state

- Rewrote the supported-profile report and implementation plan around exactly two selected profiles.
- Deleted the tracked Mongoose target/oracle manifests.
- Removed the Mongoose route from the managed runner and added a rejection assertion.
- Preserved direct true/false `PROMOTE_NUMBERS` launcher coverage and the application option.
- Removed default JUnit dependencies on `fixtures/mongoose.db`, including scheduler and v17 layout fixture tests.
- Renamed the remaining synthetic multiple-parent round-trip test to describe its generic behavior.
- Preserved historical identity/audit reports and unrelated dirty worktree changes.

## Review state

- Codex review raised concrete scope concerns; the draft and integration address them.
- Gemini CLI could not authenticate (`UNSUPPORTED_CLIENT`); an independent adversarial fallback review approved the corrected scope.
- Ward is not installed, so commands are being run directly and the fallback is recorded in the review artifacts.

## Blocker

- No blocker to the scope correction. The pre-existing generic map-ordering test failure and incomplete server/conformance rows may remain outside this correction and must be reported rather than disguised.

## Next action

- Run focused compilation/tests and runner checks, audit all active Mongoose/PROMOTE references, create the verification report, and request a final read-only verifier decision.

# 2026-08-12 stock conformance execution checkpoint

## Current findings

- The selected managed command ran Banteng against stock `Test.db` and the full checked-in conformance tree in Debian WSL.
- Pytest collected 12,762 cases and stopped at the canonical capability-admission test before packaged execution.
- The authoritative first failure is `admission::feature.connectable_listener_port` plus `admission::feature.ephemeral_listen`.
- The admission probes require:
  - `listeners()` to expose the primary bound listener and its `port`;
  - `open_network_connection("localhost", port)` to return a live negative connection object;
  - `listen(player, 0, ["print-messages" -> 1])` to bind an ephemeral listener; and
  - `unlisten(0)` to close the listener selected by its requested descriptor.
- Banteng already implements the concrete `listen` socket owner and records the assigned port, but the builtin catalog does not register `listeners`, `unlisten`, or `open_network_connection`.
- This failure is independent of Mongoose and `PROMOTE_NUMBERS`; the selected stock manifest correctly records promotion as false.

## Current state

- No conformance implementation edit has been made yet in this execution slice.
- Existing dirty worktree changes remain preserved and uncommitted.
- A focused admission rerun with JSON evidence is still collecting because YAML discovery over the Windows-mounted conformance repository is slow.

## Blocker

- No external blocker. Correct outbound-connection lifecycle ownership must be integrated with the existing scheduler/runtime transaction boundary rather than added as an untracked socket side effect.

## Next action

- Capture the focused admission evidence, inspect the existing irrevocable/host-work publication path, add conformance-grounded tests for the three missing builtins and host listener inventory/outbound lifecycle, then implement the smallest coherent listener-control slice and rerun admission.

## Listener admission implementation checkpoint

### Evidence captured

- Focused canonical admission JSON confirmed both failures are `E_VERBNF`:
  - `admission::feature.connectable_listener_port`
  - `admission::feature.ephemeral_listen`
- `option.OUTBOUND_NETWORK` passed as true and `option.PROMOTE_NUMBERS` passed as false.
- A Linux-focused JUnit test was added and observed red because `listeners` was absent from the builtin catalog.

### Implementation state

- Added Toast call-shape registrations for `listeners`, `unlisten`, and `open_network_connection`.
- Added listener inventory rows containing handler object, requested description, actual port, address family, print-message flag, and interface address.
- Changed dynamic listener ownership to retain requested description separately from an ephemeral assigned port, so `unlisten(0)` closes a listener created with description zero.
- Added the optional IPv6 discriminator used by the conformance network rows.
- Added outbound loopback socket opening to `MooServer` and current-attempt connection registration to `MooRuntime`; this keeps the negative connection visible to transactional connection builtins and stages physical boot through the existing publication path.
- Updated existing listener test doubles for the expanded host contract.

### Current blocker

- No architectural blocker. The first compile found only an invalid shorthand call-shape constant (`MAP`); it has been replaced with `Set.of(ArgType.MAP)` and compilation must be rerun.

### Next action

- Compile, run the Linux listener builtin tests, add/execute concrete server listener and outbound lifecycle tests as needed, rebuild `installDist`, and rerun canonical admission before attempting packaged conformance rows.

## Post-admission conformance checkpoint

### Passing evidence

- Canonical stock admission now passes all four probes:
  - `option.OUTBOUND_NETWORK=true`
  - `feature.connectable_listener_port=true`
  - `feature.ephemeral_listen=true`
  - `option.PROMOTE_NUMBERS=false`
- The exact checkpoint lifecycle suite now passes both admission and
  `audit_checkpoint_hooks_order_status_and_boundary`.
- Checkpoint sequencing now runs `checkpoint_started`, commits and writes that
  snapshot, then enqueues `checkpoint_finished(status)` after the write; restart
  therefore restores only the started mutation as required.

### Next failure

- The isolated command-parser authority family is 16 passed / 3 failed.
- The only failures are the leading quote, colon, and semicolon shortcut rows:
  `audit_say_shortcut_reparses_preposition`,
  `audit_emote_shortcut_reparses_preposition`, and
  `audit_eval_shortcut_reparses_preposition`.
- Actual output shows ordinary `huh` for quote/colon and direct eval parsing for
  semicolon, even though current `MooRuntime.java` already contains the intended
  post-`do_command` rewrite and retokenization block.

### Current blocker

- The mismatch may be an execution-path or built-artifact discrepancy; editing
  the parser again before proving why the existing block is not reached would
  risk duplicating behavior.
- A local `javap` attempt used a nonexistent Windows JDK path and made no change.

### Next action

- Inspect the actual installed class/distribution used by WSL, then trace the
  continuation input at the shortcut rewrite. Once the discrepancy is proven,
  add a focused runtime regression and make the smallest correction before
  rerunning the three-row family.

## Command parser completion and next fail-fast boundary

### Passing evidence

- The shortcut rewrite was present and reached in the installed server. Fixture
  setup was silently failing because `add_verb()` accepted only `none` and
  `any`, while the conformance verbs use canonical `at` and `in` prepositions.
- Added only the existing runtime preposition codes for canonical `at` (1) and
  `in` (3); temporary diagnostic output was removed.
- Java test compilation passes.
- Focused managed conformance passes admission plus the three shortcut rows:
  4 passed / 15 deselected.
- The entire command-parser authority family passes: 19/19.
- Full-tree fail-fast collection found 12,762 cases and advanced through 34
  passing cases plus the declared SQLite skip before the next failure.

### Current failure

- `audit_checkpointed_connections_disconnect_before_server_started` is the
  next authoritative red.
- Expected restart facts are `[1, 1, 1, 1, 1, 1]`; Banteng returns
  `[0, 1, 0, 0, 0, 1]`.
- The implementation already enqueues checkpoint-restored
  `user_disconnected` requests before submitting `server_started`, so the next
  investigation is the saved active-connection record and request execution
  semantics, not a speculative new feature.

### Blocker and next action

- No external blocker. Need to inspect the exact six assertions, checkpoint
  active-connection serialization, and scheduler ordering/await semantics.
- Reproduce only this row, add the narrow regression at the owning runtime or
  codec boundary, correct the lifecycle behavior, rerun the whole connection
  lifecycle family, then resume full-tree fail-fast traversal.

## Connection restart completion and world metadata boundary

### Passing evidence

- The restart failure was caused by `activeConnections()` filtering out every
  negative player identity. An unauthenticated connection is represented by its
  negative connection object, so it was omitted from the v17 checkpoint.
- Removed that filter; every published live session is now serialized with its
  listener.
- Java compilation passes, the exact restart row plus admission pass 2/2, and
  the complete connection lifecycle family passes 25/25.
- The next full-tree fail-fast run advanced to 48 passes and 84 declared skips
  before the next failure.

### Current failure

- `audit_last_move_survives_checkpoint_restart` fails with `E_PROPNF` while
  reading the moved object's `last_move` metadata after the managed restart.
- The conformance case requires `last_move` to retain both `source` and `time`,
  while location topology also remains correct.
- The v17 codec currently writes integer zero in the object `last_move` slot and
  explicitly discards that slot during read because `WorldObject` has no
  corresponding field.

### Blocker and next action

- Need to confirm where the live pre-checkpoint value is currently supplied and
  then model the server-owned metadata in `WorldObject` rather than disguising
  it as an ordinary mutable property.
- Add a world/codec red test for move metadata plus byte-stable round trip,
  implement the v17 field on read/write and move update path, rerun the exact
  audit row and its complete family, then resume full-tree traversal.

## Last-move model implementation checkpoint

### Corrected diagnosis and authority

- The isolated row completes in 5.83 seconds and fails at the first aggregate
  assertion, before `dump_database`; live `last_move` is absent, not merely lost
  during restart.
- Local Toast source confirms the exact contract:
  - new permanent objects receive an empty map;
  - movement inserts `time = time(nullptr)` and `source = old_location`;
  - the value is an explicit object field written and read in the v17 slot;
  - non-map legacy/sentinel state is replaced with a map on the next move; and
  - `last_move` is a protected built-in property, not an ordinary property.

### Implementation state

- Added an explicit `lastMove` value component to `WorldObject`, with existing
  constructors defaulting new/v4 objects to an empty map.
- `WorldTxn.readObjectProperty()` now exposes `last_move`; `move()` records the
  prior location and current epoch second in a map.
- `MooVm` rejects user assignment to `last_move` with `E_PERM`.
- The v17 codec now writes, reads, retains, and restores the explicit slot.
- Existing production object-copy paths are being updated to preserve the
  metadata instead of resetting it through compatibility constructors.

### Blocker and next action

- No external blocker. Finish the constructor-preservation audit, add focused
  world/codec tests, compile and fix mechanical call-site issues, then rerun the
  exact world-metadata conformance row.

## Last-move completion and crypt boundary

### Passing evidence

- Production compilation succeeds after auditing all object-copy paths.
- Focused Java tests pass for live move metadata preservation and v17 metadata
  round-trip.
- Managed stock conformance passes admission plus the full live/restart
  `last_move` row (2/2).
- The adjacent built-in-property suite passes 7/7, including an empty-map value
  on fresh objects and `E_PERM` on assignment.
- Full-tree fail-fast now advances to 140 passes and 84 suite-declared skips.

### Unrelated dirty-worktree evidence

- The wider `WorldTxnTest` plus `V17RoundTripTest` run has one failure in the
  pre-existing dirty test `reinsertsNonTotalMapKeysAcrossV17Read`: expected
  boolean key order `[false, true]`, actual `[true, false]`.
- The map implementation and this test were already modified outside the
  `last_move` slice; the only map implementation diff is method visibility.
  Per scope/isolation guardrails, this separate ordering work remains untouched.

### Current failure and next action

- The next authoritative conformance red is
  `basic/string.yaml::crypt_hashes_string`: expected `SAEmC5UwrAl2A`, actual
  `E_VERBNF`.
- Conformance declares `crypt` as one or two string arguments. The same corpus
  contains Unix DES, SHA, bcrypt variants, permission/rounds ordering, and call
  shape coverage, so implementation must follow the Toast builtin rather than
  special-case the first vector.
- Read the local Toast `bf_crypt` implementation and available Java/native
  dependency surface, add focused call-shape/vector tests, implement the
  smallest faithful crypto owner, run the whole string family, then resume
  full-tree traversal.

## Crypt implementation checkpoint

### Authority and implementation state

- Local architecture review explicitly selects Apache Commons Codec 1.22.0 as
  the candidate for Unix DES/MD5/SHA crypt, requiring Toast vector proof and
  excluding bcrypt from that library's responsibility.
- Local Toast source confirms `crypt()` accepts one or two strings, generates a
  two-character salt when omitted/short, applies permission rules to strength,
  routes non-bcrypt forms through platform `crypt(3)`, and owns bcrypt
  separately.
- Added pinned Commons Codec 1.22.0, updated the dependency lock, and generated
  SHA-256 verification metadata through Gradle's supported workflow.
- Added a `crypt` catalog entry with one required and one optional string,
  marked irrevocable because one-argument calls generate salt.
- Added the stock DES vector and unsupported `$2b$` failure-marker regression.

### Current blocker and next action

- The focused Java `crypt` regression passes, and the managed stock
  `basic/string.yaml` family now passes 49/49 including capability admission,
  the Unix DES vector, and the declared unsupported-bcrypt marker.
- A full stock-tree traversal collected 12,762 cases and advanced past the
  prior failure. The first newly observed failure in suite order is
  `builtins/chr.yaml::chr_multiple_args`; the run was interrupted after that
  boundary rather than spending time on later independent failures.
- The exact `builtins/chr.yaml` family currently has 6 passes and 7 failures.
  Banteng's one-integer call shape causes `E_ARGS`/`E_TYPE` for zero, variadic,
  string, and list inputs, and it lacks the non-wizard integer range check.
- Local Toast source (`src/list.cc`, `bf_chr`) defines the coherent contract:
  zero or more arguments; recursively encode integers, strings, and lists;
  strings pass through; integer range 32..254 for non-wizards and 0..255 for
  wizards; unsupported values and out-of-range integers return `E_INVARG`.
- Production and focused-test edits now express that exact contract. Next run
  the focused Java test, then the complete `chr.yaml` and `chr_call_shapes.yaml`
  stock families before resuming full-tree traversal.

## `chr` and nested collection checkpoint

- The focused Java `chr` regression passes, and the two managed stock families
  `builtins/chr.yaml` plus `builtins/chr_call_shapes.yaml` pass 17/17.
- The next full-tree failure is
  `builtins/collection_improvements.yaml::nested_map_assignment`: expected the
  updated nested map, actual `E_TYPE`.
- Root cause: the VM rebuilt only one indexed-assignment parent and hard-coded
  that parent as a list. The compiler likewise represented at most one parent
  depth. This was the actual stock failure, not a Mongoose or promote-numbers
  dependency.
- Generalized local indexed assignment to compile arbitrary local-rooted paths
  and rebuild each parent immutably for both lists and maps. Existing one-level
  list behavior and a new alternating map/list depth regression pass.
- The complete stock `collection_improvements.yaml` family now passes 17/19.
  Every ordinary nested assignment/reference/COW row is green. The final two
  10,000-write stress rows fail with `Task ran out of ticks`.
- Those rows call `suspend(0)` below 2,000 ticks. The production scheduler wakes
  suspended tasks through `VmState.restore(snapshot)`, preserving the depleted
  foreground budget, although `VmState.restoreBackground` and the existing
  scheduler regression specify a fresh background budget on resume. The next
  actual problem is therefore scheduler resume-budget selection, not collection
  reconstruction.
- Next action: verify the focused scheduler regression against the current dirty
  worktree, then make the narrow wake-restore correction only if it does not
  conflict with the existing scheduler work; rerun the two stress rows and the
  full collection family afterward.

## Logical precedence and create-family checkpoint

- The existing scheduler regression proving that foreground `suspend(0)` resumes
  with fresh background limits passes. This falsified the preliminary scheduler
  hypothesis.
- Toast `parser.y` declares `tOR tAND` in one `%left` precedence group. Banteng
  incorrectly gave `&&` higher precedence, so the stress-row idiom short-circuited
  before `suspend(0)` when ticks were low. Parser, unparser, and VM focused tests
  now prove the shared left-associative precedence.
- Managed stock `collection_improvements.yaml` now passes 19/19.
- The next stock family boundary is `builtins/create.yaml`. It initially passed
  63/67; the first failure was `wizard_can_set_anonymous_flag`, `E_PROPNF`, because
  the protected built-in `a` flag property (bit 256) was absent.
- Added `a` read/write behavior at the world-record owner and a focused record
  test. `create.yaml` now passes 65/67. Its only remaining failures both call the
  absent `max_object()` builtin and receive `E_VERBNF`.
- Next action: implement and test the zero-argument, read-only `max_object()`
  builtin using permanent-object topology only; anonymous allocation must not
  change its result. Then rerun all 67 create cases and resume stock traversal.

## `create` and Argon2 checkpoint

- Added `max_object()` as a zero-argument transactional read of the greatest
  permanent object ID. Focused Java proof includes an anonymous allocation that
  leaves the result unchanged. Managed stock `create.yaml` now passes 67/67.
- A true full-tree `-x` traversal then stopped at exactly one next failure after
  434 passes and 338 declared skips:
  `builtins/argon2.yaml::argon2_non_wizard_denied`, expected `E_PERM`, actual
  `E_VERBNF`.
- Local Toast source defines wizard-only `argon2` (2..5 arguments) and
  `argon2_verify` (2 arguments), Argon2id v19, 32-byte output, defaults t=3,
  m=4096 KiB, p=1, and verify returning integer 0 for mismatch/invalid input.
- The checked-in Java library review already selected Bouncy Castle 1.84 for
  Argon2. Added that exact pinned dependency, updated Gradle locks and SHA-256
  verification metadata, and implemented the PHC encoding/verification owner.
- The focused stock-vector/permission Java regression passes. Managed stock
  `builtins/argon2.yaml` passes 13/13 including admission.
- Next action: resume the true full-tree fail-fast traversal and take only its
  next failure as the following implementation boundary.

## Bcrypt boundary checkpoint

- The next full-tree `-x` run advanced to 457 passes and 341 declared skips,
  then stopped at
  `builtins/bcrypt_high_byte_semantics.yaml::crypt_bcrypt_2x_high_byte_hash_body_differs_from_2y`.
  The original Commons Codec path returned a short failure marker, causing the
  test's `[4..60]` range to raise `E_RANGE`.
- Local WSL `libcrypt.so.1` proves the exact required boundary: it produces the
  stock `$2a$` vector and distinct `$2x$` versus `$2y$` bodies for byte 128.
  Bouncy Castle 1.84 normalizes all four prefixes and cannot implement Toast's
  historical `$2x$` sign-extension compatibility.
- Replaced Commons Codec with a serialized Java FFM call to platform `crypt(3)`;
  removed the Commons dependency and refreshed locks. Added parsing for Toast's
  bcrypt/SHA strength-permission ordering and a focused exact `$2x$` regression.
- The focused Java crypt regression passes. The earlier stock basic string
  family remains 49/49, including the explicit unsupported incomplete `$2b$`
  marker.
- The first combined managed run exposed an implementation error: bcrypt's
  default cost 5 must be allowed to non-wizards. That source check is corrected.
  The immediately following managed rerun still returned `E_PERM` because the
  managed launcher used the pre-correction installed distribution; no rebuild
  had occurred after that source edit.
- Next action: rebuild via the focused Java crypt test, rerun the two-row
  high-byte managed family, then validate adjacent bcrypt prefix/algorithm rows
  before resuming full-tree traversal.

## Bcrypt completion and buffered-output boundary

- After rebuilding the installed distribution, managed stock
  `builtins/bcrypt_high_byte_semantics.yaml` passes 2/2. A new full-tree
  fail-fast traversal advanced to 463 passes and 369 declared skips.
- The exact next failure is
  `builtins/builtin_presence_core.yaml::buffered_output_length_exists_in_runtime`:
  expected the builtin to exist, but Banteng returned `E_VERBNF`.
- This is a stock Phase 5 manifest requirement. Toast registers
  `buffered_output_length` with 0..1 arguments and an optional `OBJECT`. With no
  argument it returns the maximum queued-output setting; with a connection it
  first rejects a missing connection with `E_INVARG`, then applies self-or-wizard
  permission, then returns currently queued bytes.
- Implementing the real catalog signature, live player-to-connection resolution,
  permission ordering, and listener-owned queued-byte query now. This boundary
  is unrelated to Mongoose and leaves `PROMOTE_NUMBERS=false` as admission
  metadata only.

- Focused Java catalog plus publication-scheduler regressions pass under the
  strict WSL Java 25 build. Managed stock generated signature coverage passes
  4/4 (admission plus all three generated cases), and the exact presence row now
  passes.
- Running the complete presence family confirms its following ordered failure is
  `call_function_exists_in_runtime`. The family currently has 26 passes, 3
  declared skips, and 21 independent missing-manifest failures; those later
  failures are inventory, not permission to add placeholder entries in bulk.
- The broader `server_admin` buffered-output rows are suite-gated by other
  missing Phase 5 builtins and are currently declared skipped. The real
  queued-byte growth row will require the later notify/output-queue boundary;
  it was not falsely claimed as passing here.

## `call_function` checkpoint

- Implemented the next ordered stock builtin through the existing single
  manifest: canonical case-insensitive lookup, removal of the name argument,
  recursive invocation with the same programmer, task-local value, task ID,
  live tick/seconds remainders, receiver, caller data, and activation thread
  mode. Unknown names return Toast's `E_INVARG`; target arity/type/permission
  errors propagate unchanged.
- It does not compile/eval source and therefore adds no verb/eval/caller frame.
  The conservative `IRREVOCABLE` wrapper authorization keeps indirect target
  effects from running speculatively.
- Focused Java recursion/error tests pass. Managed stock generated signature,
  variadic call-shape, and call-stack suites pass 22/22, including nested type
  forwarding and the explicit no-extra-frame row.
- The next ordered presence failure is `connection_options`; continue with its
  real closed option surface rather than bulk-registering the remaining 20
  missing names.

## Newly admitted verb-dispatch boundary

- A new full-tree fail-fast traversal did not reach `connection_options`.
  Adding canonical `call_function` satisfied the suite-level requirements for
  `audit/verb_dispatch_toast_oracle.yaml`, which had previously been declared
  skipped. The traversal now stops after 63 passes and 61 declared skips at
  `audit_pass_preserves_command_variables`.
- This is not a regression in an earlier passing row. A focused admission plus
  exact-row rerun reproduces the failure: setup succeeds, but raw command
  `auditpass auditpasswidget in auditpassbox` returns the default English
  missing-command line.
- Precise cause: Banteng resolves both object strings correctly but command
  selection currently checks only player then location. Toast `tasks.cc`
  selects in order player, location, direct object, indirect object, applying
  each receiver's `this` classification to the stored argument specification.
  The row's executable child verb is on the indirect object, so Banteng never
  considers it and never reaches the already-implemented `pass()` frame logic.
- Next action: extend the existing inline command-selection owner with the two
  later Toast receivers and the same argspec predicate, add a focused runtime
  regression for indirect-object selection plus inherited `pass()` command
  locals, then rerun the exact oracle row and fail-fast traversal. Do not work
  on `connection_options` until this newly admitted earlier boundary is green.

- Production compilation passes. Focused test compilation currently fails only
  because the test used nonexistent `spec.effectClass()` instead of the
  established `spec.effect()` accessor.
- Correct the accessor, run the Java vector test, then run the full basic string
  family. Do not claim Commons Codec implements bcrypt; allow later conformance
  ordering to introduce that separate owner.

## Indirect-object dispatch green; callers frontier

- Extended command verb selection in the existing runtime owner to match the
  Toast receiver order: player, location, direct object, indirect object, then
  `huh`. Each object receiver uses Toast's `this`/`none`/`any` argument
  classification relative to that receiver. Parsing is now unconditional for a
  nonempty command, so a command whose only matching verb is on the direct or
  indirect object is not rejected before object resolution.
- `MooRuntimeTest` and the installed distribution build pass under WSL Java 25.
  Managed stock admission plus
  `audit_pass_preserves_command_variables` passes 2/2. The activated full
  `audit/verb_dispatch_toast_oracle.yaml` suite advances through 19 tests and
  next fails at `audit_call_function_callers_has_no_extra_builtin_frame`.
- Correction to the earlier checkpoint: focused `call_function` catalog tests
  proved that the wrapper forwards an already supplied callers list without
  adding a frame. They did not prove the complete managed Toast row. The full
  row returns `{1, 2, 2}` instead of Toast's `{1, 3, 3}`: direct `callers()` and
  `call_function("callers")` agree, but Banteng's underlying stack is one entry
  short.
- Toast source explains the exact missing entry. `bf_callers()` delegates to
  `make_stack_list()`, which emits a synthetic builtin frame when an activation
  has a suspended builtin continuation (`bi_func_pc`). `bf_eval()` creates a
  child activation and suspends as builtin `eval`, so callers from a verb invoked
  by evaluated code contain the dynamic-eval activation, a synthetic `eval`
  builtin frame (`#-1`, `"eval"`, `#-1`, `#-1`, player), and the surrounding
  stored eval-command activation. Banteng models the two MOO/eval frames but
  omits the synthetic builtin continuation, producing two instead of three.
- Next action: add a focused VM regression for callers inside dynamic `eval`,
  update `VmState.callers()` to emit the Toast synthetic eval continuation in
  the correct position (including the current-eval case), then rerun the exact
  managed row and the fail-fast verb-dispatch suite. This remains stock-suite
  work; Mongoose and numeric promotion are not involved.

## Verb-dispatch audit closed; next stock frontier established

- Added the Toast synthetic suspended-`eval` entry to `VmState.callers()` in
  the position used by `make_stack_list()`, including when dynamic eval is the
  current frame. Focused direct/line-number VM regressions pass, and the exact
  managed no-extra-`call_function` row passes.
- The next row in the same audit showed ordinary nested MOO verb calls were
  rebuilding command locals with an empty `argstr` and no `dobj`, `dobjstr`,
  `prepstr`, `iobj`, or `iobjstr`. Toast's `call_verb` copies all six from the
  caller activation. Banteng's `CALL_VERB` path now does the same; a focused VM
  regression and the exact managed nested-command row pass.
- The entire newly activated
  `audit/verb_dispatch_toast_oracle.yaml` suite now passes 24/24.
- A fresh complete stock fail-fast traversal collected 12,762 tests and now
  advances to 490 passes and 346 declared skips. Its first failure is
  `builtins/builtin_presence_core.yaml::connection_options_exists_in_runtime`.
  This confirms `connection_options` is genuinely next only now, after the
  earlier suite activated by `call_function` was made conformant.
- Next action: derive the exact `connection_options` signature, permission and
  option-name behavior from Toast plus the admitted conformance rows, implement
  that one builtin through the existing listener/runtime owner, and rerun its
  focused rows before another complete fail-fast traversal. No Mongoose fixture
  and no numeric-promotion implementation are part of this step.

## `connection_options` implementation in progress

- Toast registers `connection_options` as one required OBJECT plus one optional
  STRING. It rejects a non-live connection with `E_INVARG` before checking
  self-or-wizard permission, then either returns one named value or a list of
  `{name, value}` pairs. Toast's ordered tables are server `binary`; task
  `flush-command`, `hold-input`, `disable-oob`, `intrinsic-commands`; network
  `client-echo`, `keep-alive`.
- Banteng already had committed runtime ownership and mutation behavior for
  binary, flush-command, hold-input, disable-oob, plus a WorldTxn-backed
  intrinsic-command table. It did not expose the read builtin, and its setter
  did not yet accept intrinsic-commands.
- Added a production-injected `connection_options` handler to the single
  builtin manifest, with Toast's exact signature, external-read effect, and
  connection ownership. The runtime handler resolves a live negative ID or
  attached player, applies Toast's invalid-before-permission order, and reports
  the five currently owned values. Binary is now retained in retryable session
  state as well as published to the listener.
- Extended `set_connection_option` with validated intrinsic-command updates.
  Integer truthiness restores all/none; lists accept only canonical intrinsic
  names, deduplicate, and return them in Toast order. The WorldTxn is updated
  immediately so a same-task set/read sequence sees speculative state, while
  the existing deferred request publishes session state only after commit.
- The first focused compile exposed only NullAway rejecting nullable sentinel
  returns in the normalizer. It now returns `Optional<ListValue>`; rerun is the
  immediate next action. After Java green, run generated signature/call-shape,
  exact presence, and the newly admitted gap-followup suite. Network-only
  `client-echo` and `keep-alive` are not being falsely claimed before their
  owning rows become active.

## `connection_options` green; `db_disk_size` boundary

- Focused catalog/publication tests and the rebuilt distribution pass. Managed
  generated signature, call-shape, and presence coverage for
  `connection_options` passes 8/8. The broader gap-followup file remains gated
  by its other declared requirements; no network-only option behavior is being
  claimed.
- Continuing the ordered `builtin_presence_core` family advances through
  `connection_options` and stops at
  `db_disk_size_exists_in_runtime`: 11 passed, 1 declared skip, 1 failure. This
  is the exact current stock-suite frontier.
- Toast registers `db_disk_size()` with zero arguments and no permission gate.
  It returns an integer file size and maps an unavailable database file to
  `E_QUOTA`. The admitted server-admin contract requires an integer greater
  than or equal to zero; generated coverage also proves extra arguments produce
  `E_ARGS`.
- The correct Banteng owner is the server/database-file boundary, not Mongoose.
  Production composition currently passes only the checkpoint output path into
  `MooRuntime`, so blindly sizing it at startup would inspect the wrong file and
  could inspect a nonexistent output. The implementation in progress propagates
  the actual `--database` input path separately, reads that file initially, and
  switches to the checkpoint output only after `writeAtomic` succeeds. Focused
  in-memory runtimes return `0`; inaccessible configured files return
  `E_QUOTA`.
- The manifest entry is zero-argument, `ANY`, `EXTERNAL_READ`, and
  `BuiltinOwner.SERVER`, backed by a production-injected handler rather than a
  constant production placeholder. The constructor and composition-root edits
  have been applied but not yet compiled.
- Next action: add focused catalog/runtime proof for the signature and real file
  size, compile under WSL Java 25, correct only concrete failures, then run the
  generated `db_disk_size` rows and ordered presence family before another
  fail-fast traversal. Mongoose and numeric promotion remain out of scope.

## `db_disk_size` green; `flush_input` frontier

- The production implementation now receives the real loaded `--database`
  path through `Banteng` -> `MooServer` -> `MooRuntime`. It reports that file's
  size until a checkpoint is successfully published, then reports the new
  checkpoint file's size. In-memory runtimes return `0`; an inaccessible
  configured file maps to Toast's `E_QUOTA`.
- Focused `BuiltinCatalogTest` and real-socket `MooServerTest` pass. The socket
  regression proves both the initial input-file size and the post-checkpoint
  output-file size. The rebuilt distribution completed successfully.
- Managed stock `generated_builtins/db_disk_size.yaml` passes 3/3: admission,
  exact `function_info` signature, and extra-argument `E_ARGS`.
- The complete presence inventory passes through `db_disk_size`, the existing
  `distance`, and `dump_database`; its first missing name is `flush_input`.
- A fresh 12,762-case whole-tree `-x` traversal confirms there is no newly
  activated earlier suite. It stops at exactly
  `builtin_presence_core::flush_input_exists_in_runtime` after 494 passes and
  347 declared skips in 302.13 seconds.
- Next action: derive `flush_input` from pinned Toast source plus its generated
  and behavioral stock rows, implement the real live input-queue operation,
  and repeat focused/generated/presence/fail-fast validation. Do not bulk-add
  the later missing presence names.

## `flush_input` green; `locate_by_name` frontier

- Toast defines `flush_input(OBJECT [, ANY])`, returning integer zero. It checks
  self-or-wizard permission before creating/finding the target task queue. The
  optional value uses ordinary MOO truthiness to select diagnostic messages.
- Banteng now exposes that exact manifest contract as a deferred connection
  mutation. The production handler clears the selected connection's retryable
  pending-input queue; when requested it stages Toast's empty/nonempty flush
  messages for commit-time output. A nonexistent target still succeeds for a
  wizard, while an unrelated programmer receives `E_PERM`.
- Focused catalog/runtime tests and the installed distribution pass. The
  runtime regression proves wizard access to a nonexistent target and
  programmer denial on another object.
- Managed generated signature plus `connection_input_call_shapes.yaml` pass
  20/20, including every `ANY` optional argument type. The ordered presence run
  passes `flush_input`, `force_input`, `frandom`, `function_info`, and `listen`,
  then stops at `locate_by_name_exists_in_runtime`: 19 passed, 2 declared skips,
  1 failure.
- All stock references to `flush_input` are now accounted for: its generated
  suite and connection-input family are green; `task_management` remains gated
  by other absent builtin requirements. Therefore `locate_by_name` is the next
  implementable stock boundary without needing another five-minute whole-tree
  traversal at this checkpoint.
- Next action: derive `locate_by_name` signature and world-search semantics from
  pinned Toast and its admitted rows, implement that one world query, validate
  its exact family, then reconsider whole-tree fail-fast activation.

## `locate_by_name` green; `locations` implementation in progress

- `locate_by_name(STRING [, INTEGER])` is now in the single manifest as a
  wizard-only transactional world read. It scans live permanent objects in
  numeric object-ID order, performs substring matching, uses ASCII-insensitive
  comparison by default, and exact byte comparison when the optional integer
  is truthy.
- Focused contract/behavior proof passes, as does the rebuilt distribution.
  Managed generated coverage passes 6/6: admission, exact `function_info`
  signature, arity boundaries, and both argument type checks.
- The ordered presence family passes `locate_by_name` and next stops at
  `locations_exists_in_runtime`: 20 passed, 2 declared skips, 1 failure.
- Toast defines `locations(OBJECT [, OBJECT [, INTEGER]])`. It rejects an
  invalid starting object with `E_INVIND`, walks the object's containing
  locations outward, omits the optional stopping object, and when the third
  argument is truthy stops before the first location that is descended from the
  optional base object. With no base it stops at an invalid location.
- The manifest and production query are implemented with that contract. The
  first focused run reached compilation and distribution success but the test
  fixture itself failed topology validation: its room IDs were accidentally
  placed in the inheritance `children` fields instead of containment
  `contents`. The fixture was corrected; this is a test-data error, not a
  production failure.
- Next action: rerun the corrected focused `locations` test, then managed
  generated `locations` coverage and fail-fast presence. Do not move to the
  following missing name until those are green.

## `locations` green; `memory_usage` implementation in progress

- The corrected focused containment fixture passes. Managed generated coverage
  for `locations` passes 7/7, including its exact signature, argument types,
  invalid-start `E_INVIND`, outward traversal, base exclusion, and ISA-aware
  stopping behavior.
- The ordered presence family passes through `locations` and
  `log_cache_stats`; its first missing builtin is now
  `memory_usage_exists_in_runtime`: 22 passed, 3 declared skips, 1 failure.
- `log_cache_stats()` was implemented at the intervening required row as a
  wizard-only irrevocable server operation. Banteng has no verb lookup cache,
  so it truthfully emits Toast's summary/table shape with zero hits, misses,
  generations, and depth counts, then returns integer zero. Its generated
  signature suite passes 4/4.
- Toast registers `memory_usage()` with zero arguments and no permission gate.
  On Linux it reads all seven `/proc/self/statm` fields and returns a five-float
  list containing total size, resident pages, shared pages, text pages, and
  data-plus-stack pages (indices 0, 1, 2, 3, and 5). File-open failure maps to
  `E_FILE`; malformed or nonnumeric content maps to `E_NACC`.
- Banteng now exposes that exact `ANY`, zero-argument, `EXTERNAL_READ`,
  server-owned contract. The implementation reads `/proc/self/statm` directly
  and returns the five Toast fields as binary64 `FloatValue`s. Focused catalog
  behavior and the rebuilt distribution pass under WSL Java 25. An initial
  `String.split` implementation was rejected by the project's warnings-as-errors
  static analysis and was replaced with `StringTokenizer` without changing the
  contract.
- Next action: run the managed generated `memory_usage` file and its exact
  admitted behavioral row, then rerun ordered presence to identify the next
  stock requirement. Mongoose and numeric promotion remain outside this work.

## `memory_usage` green; exact `next_recycled_object` model in progress

- Focused `memory_usage` tests and distribution build pass. Managed stock
  `generated_builtins/memory_usage.yaml` passes its admission, exact signature,
  and extra-argument rows (3/3). The broader server-admin and time/server audit
  files remain declared-skipped because they require other missing builtins, so
  their skips are not being misreported as behavioral proof.
- The full ordered presence inventory now passes `memory_usage`; its first
  missing name is `next_recycled_object`. The inventory later reports twelve
  additional absent names, but they are not the current implementation target.
- Toast defines `next_recycled_object([OBJ])` as an unrestricted transactional
  world read. It defaults its inclusive scan start to object zero, rejects a
  negative start or one above `db_last_used_objid()` with `E_INVARG`, scans
  invalid permanent slots while the index is strictly below the last-used
  boundary, returns the first such slot as an object value, and otherwise
  returns integer zero.
- A first local implementation used Banteng's existing `maximumObjectId()`,
  which was only the greatest live object. Source inspection showed that would
  be an architectural approximation: Toast's last-used ID survives recycling,
  and its object-slot queries plus v17 checkpoints depend on that durable
  boundary. The approximation has therefore not been accepted as complete.
- The world model now carries `lastUsedObjectId` separately from the live object
  map through immutable `World`, public `WorldSnapshot`, `WorldHistory`, and
  `WorldTxn`. Compatibility constructors derive it from live records; the v17
  reader supplies `permanentSlotCount - 1`, the writer emits through that full
  boundary (preserving trailing recycled slots), and permanent creation advances
  it. Every world replacement preserves it. `maximumObjectId()` now exposes
  this Toast boundary rather than recomputing a live maximum.
- `next_recycled_object` is in the manifest with exact 0..1 OBJECT signature,
  `ANY` permission, transactional-read effect, and world ownership. Its focused
  test uses an explicit last-used boundary above the greatest live ID to prove
  the distinction. Main and test compilation succeeds under WSL Java 25.
- Next action: run focused catalog, world, and v17 persistence tests; correct any
  concrete regression; then run generated `next_recycled_object` coverage and
  presence again. Do not advance to `output_delimiters` or other later names
  until this boundary and checkpoint persistence are green.

## `next_recycled_object` green; `output_delimiters` implementation in progress

- Focused catalog and world tests pass with the explicit durable last-used
  object boundary. The new v17 regression proves a world with live object #0
  and last-used ID #3 writes and restores trailing `#3 recycled` state; the
  existing byte-stable checkpoint test also passes.
- Running the entire `V17RoundTripTest` class exposes an existing failure in
  `reinsertsNonTotalMapKeysAcrossV17Read`: a forward boolean map restores keys
  in `{true, false}` rather than the test's `{false, true}` order. The exact
  failure repeats alone, while the new trailing-slot and existing byte-stable
  tests pass together. This unrelated stable test has not been edited to hide
  the failure.
- Managed generated `next_recycled_object` coverage passes 4/4: admission,
  exact 0..1 OBJECT signature, excess arity, and wrong-type rejection. The
  broader object-hierarchy file remains honestly gated by other absent
  builtins. Presence now passes `next_recycled_object`; its first absent name is
  `output_delimiters`.
- Toast defines `output_delimiters(OBJ)` as a connection-owned external read.
  Permission is checked first: non-wizards may query only themselves, otherwise
  `E_PERM`. It then requires a live connected task queue or returns `E_INVARG`.
  Success is a two-string list containing the current output prefix and suffix,
  with unset values represented as empty strings. Both `PREFIX`/`OUTPUTPREFIX`
  and `SUFFIX`/`OUTPUTSUFFIX` set the same underlying fields in Toast.
- Banteng already owns per-connection prefix/suffix state and uses it around
  command output. A production-injected `output_delimiters` handler and exact
  one-OBJECT manifest entry have been added. The runtime implementation resolves
  the connected player/negative connection through the transaction, preserves
  Toast's permission-before-validity order, and returns the retryable session's
  current delimiters. Compilation and focused tests have not yet run.
- Next action: add focused manifest and runtime proof, including self access,
  wizard invalid-target `E_INVARG`, programmer other-target `E_PERM`, and exact
  prefix/suffix values; compile; then run generated coverage and presence.

## `output_delimiters` and `owned_objects` green; `parse_ansi` frontier

- `output_delimiters` now has focused catalog and real runtime proof. A connected
  programmer reads the exact live prefix/suffix pair; querying another object
  fails `E_PERM`; a wizard querying an unconnected object fails `E_INVARG`.
  The test also accounts for the stock fixture's nested eval framing without
  confusing those protocol lines with the builtin's returned delimiter values.
- Managed generated `output_delimiters` coverage passes 5/5: admission, exact
  one-OBJECT signature, both arity boundaries, and wrong-type rejection.
- `owned_objects(OBJ)` is implemented as an unrestricted transactional world
  read. It rejects an invalid/recycled owner with `E_INVIND`, scans permanent
  slots from zero through the durable last-used boundary, skips recycled slots,
  and returns matching live objects in numeric ID order.
- Focused `owned_objects` behavior proves numeric ordering independent of input
  map insertion order, exclusion of holes/trailing recycled slots, and invalid
  owner handling. Managed generated coverage passes 5/5.
- Based on the ordered presence inventory already run after
  `next_recycled_object`, the next missing name after `output_delimiters` and
  `owned_objects` is `parse_ansi`. A fresh full presence rerun is unnecessary
  before deriving this immediately adjacent row; exact generated/presence proof
  will follow its implementation.
- Next action: derive `parse_ansi` exclusively from pinned Toast source and
  stock generated/behavioral YAML, identify its exact byte/string result shape
  and errors, then implement only that contract. Mongoose and numeric promotion
  remain unrelated and absent.

## `parse_ansi` green; `queue_info` frontier

- `parse_ansi(STRING)` is now registered as an unrestricted VM operation with
  exact arity/type metadata. Its implementation preserves raw ISO-8859-1 bytes
  and applies Toast's complete ordered tag table with ASCII-insensitive matches:
  foreground/background colors, normal/inverse/underline/bold/bright variants,
  blink variants, gray/grey, beep, and `[null]` removal.
- `[random]` mirrors Toast's source exactly: six selectable entries are used,
  with magenta duplicated and cyan excluded by Toast's `% 6` indexing despite
  a seventh array entry in C++. The builtin is classified irrevocable because
  this path advances the shared PRNG, consistent with Banteng's `random()`.
- Focused proof checks exact escape/bell bytes, mixed-case tags, `[null]`,
  unknown-tag preservation, and membership in Toast's random result set. The
  catalog build and installed distribution pass.
- Managed generated `parse_ansi` coverage passes 5/5. The broader
  `parse_remove_ansi.yaml` file requires both builtins and remains declared
  skipped until the later ordered `remove_ansi` row; its tests have not been
  claimed green prematurely.
- The next missing presence name is `queue_info`, immediately after
  `parse_ansi`. Later missing recycling/ANSI/admin names remain deferred.
- Next action: derive `queue_info` from pinned Toast task-queue source plus all
  stock generated and behavioral rows, map the query to the existing
  `TaskRegistry` owner without creating a second scheduler, then implement and
  validate that one boundary.

## `queue_info` implementation checkpoint

- Derived stock behavior has been mapped to the existing owners only: the
  retryable runtime connection-state map supplies connected queues and the sole
  scheduler-owned `TaskRegistry` supplies background-task programmers/counts.
  No second queue, scheduler, fixture, Mongoose dependency, or numeric-promotion
  work was introduced.
- The catalog now registers `queue_info([OBJ])` as an unrestricted external task
  read. No-argument calls return queue player objects. A nonwizard object query
  returns only that player's background task count. A wizard query returns the
  stock task-queue map or integer zero when no queue exists.
- The wizard map uses Toast's key order and field shapes. It intentionally
  mirrors Toast's observable `hold_input` source behavior, which reads the
  zero-valued usage field, while connected/input/OOB state comes from the live
  retryable session snapshot.
- Windows compilation passed. Windows unit execution was invalid because the
  JVM cannot initialize the Linux-only `libcrypt.so.1`; this is an environment
  failure before test logic. The authoritative WSL Java 25 focused catalog and
  task-registry gate passed.
- Current blocker: none in the implementation. Runtime behavior and stock
  generated/behavioral conformance proof have not yet been run.
- Next action: add focused real-runtime proof for programmer and wizard result
  shapes, run it in WSL, then run managed stock generated `queue_info` and its
  admitted behavioral rows followed by ordered presence to find the next red.

## `queue_info` green; `recreate` frontier

- Added a real stock-fixture runtime regression covering logged-in Programmer
  and Wizard calls. It proves no-argument list membership, the nonwizard integer
  count, wizard map identity/connected/background fields, and nonexistent queue
  integer zero. The focused WSL Java 25 test passes.
- Managed generated `queue_info` passes 4/4: admission, exact function metadata,
  excessive arity, and wrong optional type. The broad `task_management.yaml`
  remains suite-gated by unrelated missing requirements; its skipped rows are
  not counted as behavioral proof.
- The complete ordered presence inventory passes `queue_info` and the existing
  `read`, then first fails at `recreate`. Its later failures are
  `recycled_objects`, `remove_ansi`, `reset_max_object`, `simplex_noise`,
  `usage`, `verb_cache_stats`, and `waif_stats`; none should be bulk-added.
- Pinned Toast registers `recreate(OBJ old, OBJ parent [, OBJ owner])` as a
  read/write builtin. It only accepts a positive recycled ID at or below the
  durable last-used boundary, defaults owner to the programmer, ignores an
  invalid explicit owner, applies owner/fertile/quota checks, creates at the
  requested ID, and invokes inherited `initialize()` before returning it.
- Current blocker: none. Banteng can reuse its existing create-initialize VM
  continuation, but `WorldTxn` needs one explicit-ID allocation operation that
  preserves the durable last-used boundary and reciprocal parent/child links.
- Next action: add that narrow world operation, register `recreate` with the
  exact stock signature/error order, prove it with focused Java tests, then run
  generated/call-shape/behavioral managed stock rows before rerunning presence.

## Recycling and ANSI frontier checkpoint

- `recreate` now uses one explicit-ID `WorldTxn` allocation path that accepts
  only positive recycled IDs inside the durable last-used boundary, preserves
  that boundary, rebuilds inherited properties, and updates reciprocal child
  links. The builtin reuses the existing create-initialize VM continuation.
- Managed stock `recreate` proof passes 15/15 across generated signature,
  call-shape, and behavioral suites: ID reuse, parent/child topology, explicit
  owner, inherited `initialize`, invalid zero/live IDs, arity, and types.
- `recycled_objects()` is an unrestricted transactional world read that scans
  zero through the durable last-used ID inclusive and returns every invalid
  slot as an object value in numeric order. Generated proof passes 3/3; a
  focused WSL Java test proves leading, interior, and trailing holes plus arity.
  The broad `object_hierarchy.yaml` remains gated by unrelated missing
  requirements, so its skipped rows are not counted as behavioral proof.
- `remove_ansi(STRING)` is a pure VM operation using Toast's exact ordered tag
  list and ASCII-insensitive removal while preserving unknown bracketed text.
  Focused Linux proof passes, and managed generated plus combined ANSI behavior
  passes 18/18, including all colors/backgrounds/formatting/random/null tags.
- Current blocker: none. Based on the prior ordered inventory, the next missing
  builtin after `remove_ansi` is `reset_max_object`; later gaps remain deferred.
- Next action: derive `reset_max_object` from pinned Toast source and all stock
  generated/behavioral rows, determine its exact mutation of the durable
  last-used boundary, then implement and validate only that boundary.

## `reset_max_object` and Simplex green; `usage` verification pending

- `reset_max_object()` is registered wizard-only and resets the durable
  last-used object boundary to the greatest live permanent ID without changing
  live objects. A focused Linux test proves permission, return zero, the new
  maximum, and reuse by the next create. Managed generated stock passes 4/4.
- `simplex_noise(LIST)` is a direct Java port of Toast's embedded public-domain
  SimplexNoise1234 implementation, including its fixed permutation/simplex
  tables and 1D through 4D scaling constants. It rejects non-float members with
  raised `E_TYPE`, while invalid dimension counts return the `E_TYPE` value as
  Toast does.
- Focused Linux Simplex proof passes. The managed optional-extension suite has
  six passes (admission plus all five applicable Simplex rows) and four honest
  skips for unrelated optional spellcheck/jemalloc builtins.
- `usage()` has been added as a wizard-only external server read. Its intended
  result is Toast's ten elements: three fixed-point load averages, user/system
  time floats, and seven `getrusage(RUSAGE_SELF)` integer counters. It uses the
  pinned Linux ABI through the same Java foreign-function boundary already used
  by server builtins; compile and test compilation pass.
- Current blocker: none, but `usage` has not yet passed focused Linux execution
  or managed generated/server-admin coverage and must not yet be called green.
- Next action: run the focused `usage` test in WSL, correct any ABI/shape issue,
  run generated stock coverage and applicable behavioral rows, then continue
  the ordered presence frontier to `verb_cache_stats`.

## `usage` and cache-stats green; `waif_stats` verification pending

- Focused WSL execution proves `usage()` is wizard-only and returns Toast's
  ten-element shape with a three-integer load list, user/system floats, and
  seven integer resource counters. Managed generated stock passes 3/3.
- `verb_cache_stats()` now exposes Toast's five-element wizard-only shape:
  four nonnegative counters and a 17-bucket integer histogram. Banteng has no
  Toast-style database verb-lookup cache, so this surface truthfully reports an
  empty cache rather than fabricating hits/misses. Focused Linux proof and
  generated stock pass 3/3.
- The stronger `verb_cache.yaml` delta behavior is gated by the separate
  missing `renumber` builtin and is not claimed. If `renumber` is later added,
  the absence of a corresponding lookup-cache owner must be resolved before
  that suite can be called conformant.
- `waif_stats()` has been registered as an unrestricted transactional world
  read using the existing WAIF registry. It returns a map containing `total`,
  `pending_recycle`, and object-keyed per-class counts. Compile and focused test
  compilation pass.
- Current blocker: none, but `waif_stats` has not yet executed under Linux or
  managed conformance and must not yet be called green.
- Next action: execute the focused WAIF registry test, run generated and
  call-shape stock suites, then rerun the complete presence inventory. If that
  is green apart from declared optional skips, run broader fail-fast discovery
  to identify the next actual suite failure outside presence.

## Presence complete; full-tree fail-fast identifies the extension frontier

- Focused WSL execution and managed generated/call-shape coverage now prove
  `waif_stats()` against a real WAIF registry. The builtin reports live total,
  pending-recycle count, and per-class counts from the current world snapshot.
- The complete stock core-presence inventory passes 47 cases with three
  explicitly declared optional skips (`background_test`, `finished_tasks`, and
  `malloc_stats`) and no failures. Every non-optional core-presence name is now
  present in the runtime.
- The managed stock tree collected 12,762 cases and ran fail-fast. It reached
  529 passes and 349 declared skips before the first failure:
  `builtins/builtin_presence_extensions.yaml::file_chmod_exists_in_runtime`.
  The expected presence result is `1`; Banteng currently returns `0`.
- This is the first observed stock-suite red after the completed core presence
  frontier. It is unrelated to Mongoose and unrelated to numeric promotion;
  `PROMOTE_NUMBERS=false` remains only profile metadata for gate selection.
- Current blocker: the exact required/optional status and behavioral contract
  of Toast's `file_chmod` extension must be established before implementation.
- Next action: inspect the extension presence/behavior suites, Banteng's
  existing file-policy boundary, and pinned Toast source; implement only the
  first required contract if the stock profile actually admits it.

## File-I/O family authority and ownership checkpoint

- The checked-in implementation plan, Phase 5, requires the complete canonical
  builtin name set extracted from the pinned stock Toast source and explicitly
  gates both `src/moo_conformance/_tests/builtins` and
  `src/moo_conformance/_tests/generated_builtins`. The stock Banteng profile
  selects all of `src/moo_conformance/_tests`. Therefore the extension-presence
  failure is in scope; it is not an inferred Mongoose dependency.
- `builtin_presence_extensions.yaml` states that fileio/sqlite/pcre extension
  surfaces are required. Only specifically declared optional rows such as
  `curl` and `spellcheck` may skip. `file_chmod` has no skip declaration.
- Pinned Toast registers one coherent file-I/O family in `fileio.cc`: handle
  lifecycle, text/binary reads and writes, seek/stat/directory mutations,
  `file_chmod`, grep/count helpers, and handle enumeration. Every operation is
  wizard-only. Toast resolves accepted relative names beneath the process
  working directory's `files/` subdirectory and caps live handles at 256.
- The managed conformance server creates exactly that `files/` directory and
  launches Banteng with the managed temporary directory as its working
  directory. A server-owned catalog-scoped file service rooted at `files/` is
  therefore the narrow production owner; file handles must persist across
  builtin calls and must not enter world snapshots.
- Toast path admission rejects an empty-leading `..` prefix and any `/.`
  component; the conformance security suite additionally requires Unix-safe
  handling of backslash cases. Modes are exact four-character strings, chmod
  modes are exact three-octal-digit strings, and invalid handles/modes raise
  `E_INVARG` after the wizard permission boundary.
- Current blocker: implementation has not started. The catalog currently has no
  file service or registered file builtin, so the first extension-presence row
  returns `0`.
- Next action: add a single synchronized, catalog-scoped `FileIoService` rooted
  at `files/`, register the exact Toast signatures/effect ownership for the
  family, prove focused path/handle/mode behavior, then run presence, generated
  signatures, and the complete fileio behavioral/security family.

## File-I/O family green; PCRE is the next required extension frontier

- Added one catalog-scoped `FileIoService` rooted at the process working
  directory's managed `files/` subdirectory. It owns a synchronized 256-handle
  table, exact four-character modes, text/binary conversion, position/EOF
  state, stat/directory/chmod operations, and confined path resolution. Handles
  are transient server state and never enter `WorldTxn` or checkpoint state.
- Registered all 29 pinned-Toast file builtins with exact generated call shapes,
  wizard-only permission, server ownership, and irrevocable effects so a
  scheduler retry cannot repeat external reads, writes, or position changes.
- Linux Java 25 `compileJava compileTestJava` and `installDist` pass.
- Managed extension presence passes every file row. The full presence file now
  reaches the next non-file requirements and reports only the expected later
  gaps: three PCRE names and nine SQLite names; four explicitly optional rows
  skip. This run is not called globally green.
- All generated `file_*.yaml` signature/call-shape cases pass 120/120.
- The complete applicable file family passes 107/107 across `fileio.yaml`,
  `fileio_errors.yaml`, `fileio_host_write.yaml`, `fileio_security.yaml`, and
  `fileio_verified.yaml`, including real managed-host disk assertions.
- Current blocker: `pcre_cache_stats`, `pcre_match`, and `pcre_replace` are the
  next required extension-presence failures. No PCRE implementation exists yet.
- Next action: derive the exact three signatures, match/replace result shapes,
  option/error behavior, and cache-stat ownership from pinned Toast plus every
  PCRE conformance row; implement and validate that family only.

## PCRE family green; SQLite is the next ordered stock failure

- Added one catalog-scoped PCRE service with the exact pinned signatures:
  `pcre_match(STR, STR [, INT [, INT]])`, `pcre_replace(STR, STR)`, and
  wizard-only `pcre_cache_stats()`. Match results use Toast's map/list shape,
  named groups replace their numbered keys, positions are one-based inclusive,
  and default behavior is case-insensitive find-all.
- The observable bounded pattern cache is server-owned; matching is marked
  irrevocable so scheduler retries cannot fabricate cache-hit increments.
  Replacement remains a pure VM operation.
- Linux Java 25 compile/install passes. Managed PCRE behavioral plus all three
  generated signature suites pass 65/65, including named and mixed captures,
  backreferences, alternate delimiters, invalid patterns/syntax, permissions,
  and cache visibility.
- A fresh fail-fast extension-presence run now passes admission, Argon2, every
  file name, and every PCRE name (35 passes, two declared skips) before stopping
  at `sqlite_close_exists_in_runtime`, expected presence `1`, actual `0`.
- SQLite is not a Mongoose dependency and is not connected to numeric
  promotion. It is the next required stock Toast extension family. The project
  already carries the locked `org.xerial:sqlite-jdbc:3.53.2.0` dependency, but
  Banteng has no SQLite builtin owner or registered SQLite names.
- Current blocker: none for investigation; implementation of the nine-name
  SQLite family has not started. Its concurrency-sensitive `sqlite_interrupt`
  contract must be designed with a cancellable active-statement owner rather
  than a globally synchronized stub.
- Next action: finish local catalog/static validation for the file/PCRE slice,
  then derive the SQLite handle, path, query/result, limit, and interrupt
  contracts from pinned Toast and the complete SQLite conformance family.

## File/PCRE local gate complete

- The first focused `BuiltinCatalogTest` run correctly exposed that the closed
  reachable-name assertion still contained the pre-extension 151-name set.
  The production manifest contained 183 unique entries. Updated that explicit
  test authority with exactly the 29 file names and three PCRE names; no stable
  behavioral expectation was weakened.
- The complete focused `BuiltinCatalogTest` now passes all 78 tests under WSL
  Java 25. Scoped `git diff --check` passes for `BuiltinCatalog`, both new
  service owners, and the catalog test.
- No source, test, note, or report has been staged, committed, pushed, or
  merged. The mandatory audit notes remain untracked.
- Current blocker remains the unimplemented required SQLite family, beginning
  at the exact fail-fast row `sqlite_close_exists_in_runtime`.
- Next action: start the SQLite family from its pinned Toast source and managed
  SQLite suites, preserving a cancellable active-statement owner for
  `sqlite_interrupt` and the same confined `files/` path boundary.

## SQLite family and extension inventory green

- Added one catalog-scoped `SqliteService` using the already locked
  `sqlite-jdbc` dependency. It owns a 20-handle transient connection table,
  default Toast options (`PARSE_TYPES | PARSE_OBJECTS`), duplicate-path checks,
  query/execute conversion, last insert IDs, runtime limits, and connection
  information. No SQLite state enters the world transaction or checkpoint.
- Extracted `ConfinedFileRoot` so file-I/O and SQLite use one exact `files/`
  path boundary. `:memory:`/empty SQLite paths bypass filesystem resolution;
  ordinary paths remain confined. This is a real ownership move, not a facade.
- `sqlite_query` and `sqlite_execute` return existing suspending-host work and
  hold a per-connection execution lock. `sqlite_interrupt` remains immediately
  callable and invokes both the active JDBC statement's cancellation and
  SQLite's native connection-wide interrupt. The managed delayed-fork,
  long-recursive-query interruption row passes.
- Registered all nine exact Toast signatures and preserved the irregular error
  contracts: invalid query/execute handles return the `E_INVARG` value, while
  close/info/last-id/limit/interrupt raise `E_INVARG`.
- Linux compile, catalog test, install distribution, and scoped diff check pass.
- Managed generated signatures plus expanded call shapes pass 58/58. Managed
  lifecycle, query/execute, type/object parsing, headers, limits, security, and
  interrupt behavior pass 26/26.
- The entire required extension-presence inventory is now green: 44 passes and
  four explicitly declared optional skips (`curl`, `spellcheck`, `url_decode`,
  `url_encode`). There are zero failures.
- Current blocker: none within extension presence. The next actual conformance
  gap is not yet known after this slice.
- Next action: run the complete stock profile fail-fast, record its first real
  failure, and select only that next contract family.

## SQLite completion boundary and next stock failure

- The complete managed stock profile was rerun with fail-fast after the SQLite
  slice. In collection order it stopped before the later SQLite files, at
  `builtins/create_call_shapes.yaml::create_two_args_invalid_parent_bad_arg_obj_float`.
- Exact result: 788 passed, 358 skipped, one failed in 323.72 seconds. The
  failing call combines an invalid parent with a float second argument;
  Toast/conformance requires `E_TYPE`, while Banteng currently returns
  `E_INVARG` from parent validation first.
- This failure is unrelated to SQLite, Mongoose, or `PROMOTE_NUMBERS`. SQLite's
  dedicated behavior, generated call shapes, security, interrupt, and complete
  extension-inventory gates remain green; the full fail-fast result must not be
  misreported as having traversed those later-collected SQLite files.
- Scoped diff checking for `ConfinedFileRoot`, `FileIoService`, `SqliteService`,
  `BuiltinCatalog`, and `BuiltinCatalogTest` remains clean. No work was staged,
  committed, pushed, or merged; unrelated dirty and untracked material was
  preserved.
- Current blocker: none for the requested SQLite family. The next ordered stock
  contract is `create()` two-argument error precedence, specifically validating
  the optional second argument's type before rejecting the parent value.
- Next action: do not broaden this SQLite slice. If continuing conformance work,
  audit the complete `create_call_shapes` product table and pinned Toast source,
  then make the smallest error-precedence correction with focused regression
  coverage.

## `create()` optional-argument precedence green

- Pinned Toast `objects.cc::bf_create` first checks that the parent is an object
  or list of objects, parses positions two through four, and only then validates
  parent and owner identities. Banteng's shared `parentArgument` returned
  `E_INVARG` for a missing parent before the optional argument parser could
  return Toast's required `E_TYPE`.
- Kept the existing structural parent-type check first, deferred only an
  `E_INVARG` parent result until after optional argument parsing, and added a
  catalog regression covering both an invalid object parent and a list holding
  an invalid parent. Valid optional forms still return `E_INVARG` for those
  parents; a float optional argument now returns `E_TYPE` first.
- The complete generated product table passes 2,801/2,801, covering all one-
  through four-argument type and precedence combinations. The complete
  `BuiltinCatalogTest` passes, and the scoped diff check is clean.
- Current blocker: none for `create()`. The next stock-profile failure has not
  yet been observed after this correction.
- Next action: rerun the complete managed stock profile fail-fast and repair the
  next actual Banteng mismatch without changing the conformance suite.

## `eval()` programmer permission and varargs green

- The stock rerun passed the repaired `create()` matrix and stopped at
  `builtins/eval.yaml::eval_requires_programmer`: a verb owned by an object with
  its programmer bit cleared successfully evaluated code instead of raising
  `E_PERM`. Exact boundary: 3,699 passed, 432 skipped, one failed.
- Pinned Toast `verbs.cc::bf_eval` requires `is_programmer(progr)`, accepts one
  or more strings, and parses the entire argument list as one program. Banteng's
  manifest allowed every caller, declared exactly one string, and retained only
  the first source argument.
- Added the distinct `PROGRAMMER_ONLY` manifest predicate using flag bit 2,
  registered `eval` as one required string plus variadic strings, and join all
  supplied source lines before the existing dynamic-eval continuation. Wizard
  bit 4 alone deliberately does not satisfy this permission.
- Added catalog regression evidence for both ordinary and wizard-only denial,
  the exact manifest shape, and multi-source preservation. `BuiltinCatalogTest`
  passes and the scoped diff check is clean.
- All applicable eval authorities pass 21/21 across handwritten behavior,
  explicit vararg call shapes, and the generated Toast signature suite.
- Current blocker: none for `eval()`. The next stock-profile failure is not yet
  known after this correction.
- Next action: rerun the complete managed stock profile fail-fast and repair the
  next observed Banteng contract mismatch.

## GC and map-family checkpoint

- The stock rerun passed the repaired eval family and stopped at
  `gc_stats_requires_wizard_perms` because Banteng had `run_gc` but no
  `gc_stats`. Exact boundary: 3,892 passed, 433 skipped, one failed.
- Added the zero-argument wizard-only `gc_stats` signature and all seven Toast
  color keys. Banteng's reachability collector completes at task publication
  rather than exposing Toast's in-progress trial-deletion buckets, so completed
  color counts are reported as zero; JVM heap statistics are not substituted.
- The focused GC run now has 46/49 passing. Two failures are only the missing
  `maphaskey` dependency used to assert key presence; direct indexing and type
  checks prove the `purple` and `black` entries exist. One behavioral failure
  remains: unreachable-anonymous finalization does not dispatch its `recycle`
  hook, leaving `recycle_called` at zero.
- The complete pinned map authority showed that Banteng only registered
  `mapkeys`; `mapdelete`, `mapvalues`, and `maphaskey` were absent. Added the
  coherent four-builtin map family with Toast signatures, scalar/anonymous-key
  asymmetry, case rules, variadic value lookup, deletion, and multi-key raised
  error details.
- Current state: production map code compiles after one strict operator-
  precedence formatting correction. The catalog test currently has one local
  assertion failure: case-sensitive `maphaskey(["Foo" -> 1], "foo", 1)` still
  returns one because `StringValue.equals` is case-insensitive; the new exact-
  case path must compare raw bytes instead.
- Current blocker: none. No work is staged, committed, pushed, or merged, and
  unrelated dirty/untracked material remains preserved.
- Next action: change only the case-sensitive string-key comparison to exact
  bytes, rerun catalog plus the complete map authorities, then trace and repair
  anonymous `recycle` hook dispatch before rerunning the GC family.

## Map family green; anonymous GC hook is the active boundary

- Added raw-byte exact comparison for case-sensitive string map lookups and
  made map equality/exact equality compare the map's observable entry order.
  The latter preserves Toast's authority-tested reverse topology for maps with
  BOOL, WAIF, and anonymous-object keys without changing ordinary sorted maps.
- `BuiltinCatalogTest` passes after the ordered-equality change. The complete
  focused map surface passes 41/41 across `mapdelete`, `maphaskey`, `mapkeys`,
  `mapvalues`, all generated signatures and call shapes, and the pinned map
  authority anomalies (including 2^32 integer-key collision behavior).
- The legacy `builtins/map.yaml` file is still feature-gated through
  `server_version("features")`; its earlier cascade is not evidence of 130 map
  behavior failures. That feature-reporting surface will be handled only when
  the stock fail-fast ordering reaches it.
- Current blocker: automatic collection removes pending anonymous objects
  directly in `MooRuntime.finalizePendingAnonymousObjects()` and therefore
  never dispatches an inherited `recycle` hook. The focused GC family otherwise
  establishes that `gc_stats` has the required keys and contracts.
- Next action: reuse Banteng's existing stored-task/continuation machinery to
  run the anonymous object's `recycle` hook exactly once before removal, keep
  anonymous objects created by that hook out of the current sweep, add runtime
  regression coverage, and rerun the focused GC family.

## Anonymous finalization lifecycle implemented; one ordering row remains

- `MooRuntime` now claims an anonymous finalization atomically in the persisted
  pending-root list, starts an inherited executable `recycle` verb as a stored
  VM task, and removes the original identity in a dedicated runtime
  continuation whether the hook returns or errors. The claim marker keeps the
  object and its closure rooted while the hook runs and prevents optimistic
  retries from enqueueing it twice. Startup converts an interrupted claim back
  to an ordinary pending root so a crash cannot strand it permanently.
- The hook task runs with the anonymous identity as `this`; `startStored` now
  resolves either permanent or anonymous verb locations. Objects created by a
  hook are observed before the removal continuation, so they are outside the
  captured finalization set and can survive the current sweep when rooted.
- Production compilation passes. The complete focused GC surface improved from
  46/49 to 48/49: all permissions, map keys, generated signatures, basic and
  cyclic collection shapes, inherited-anonymous cases, and all other recycle
  variants pass.
- The first managed run after this source change still reported
  `gc_recycle_hook_created_anonymous_survives_current_sweep` red, but that run
  followed only `compileJava`; the managed launcher executes `build/install`,
  which still contained the pre-fix runtime. This was stale distribution
  evidence, not a scheduler race. Running the new production-runtime regression
  rebuilt `installDist`; the unchanged focused GC authority then passed 49/49.
- Added a runtime regression reproducing the authority's self-cyclic orphan,
  inherited hook, hook-created rooted anonymous object, and `suspend(0)`
  observation. It proves both one hook call and validity of the new stash.
  `MooRuntimeTest`, `PublicationSchedulerTest`, and
  `AnonymousObjectPersistenceTest` all pass together.
- Current blocker: none in the GC/map slice. No scheduler-ordering change was
  needed or made. No work is staged, committed, pushed, or merged.
- Next action: run the complete managed stock profile fail-fast from the rebuilt
  distribution and repair only the next observed Banteng mismatch.

## JSON builtin family green

- The rebuilt stock profile cleared the prior GC boundary and stopped at
  `builtins/json.yaml::generate_json_int` with `E_VERBNF`. Exact boundary:
  3,952 passed, 670 skipped, one failed. Neither JSON builtin existed in
  Banteng.
- Pinned Toast `src/json.cc`: `parse_json(STR [, STR])` and
  `generate_json(ANY [, STR [, ANY]])`, case-insensitive `common-subset` and
  `embedded-types` modes, JSON `null` mapped to `E_NONE`, 32-bit integer
  parsing with float fallback, and anonymous/WAIF values rejected.
- Added a self-contained `JsonCodec` and registered both pure VM builtins with
  the exact call shapes. It covers recursive lists/maps, embedded scalar key
  types, booleans/null, Toast's accepted root-number trailing garbage, invalid
  syntax/modes, non-finite rejection, binary escapes, UTF-8 Unicode escapes,
  surrogate pairs, and the optional binary-escape control.
- The first focused run passed 152/168. All 16 failures identified one Toast
  representation detail: MOO binary strings at this boundary use textual
  `~XX`; generation decodes control escapes and parsing encodes control and
  non-ASCII UTF-8 bytes. Tab retains a distinct legacy split: JSON `\\t` parses
  as a raw tab, while MOO `~09` generates `\\u0009` and round-trips as `~09`.
- After pinning that behavior, `BuiltinCatalogTest` passes and the unchanged
  JSON authorities pass 168/168 across `json.yaml`, `json_null.yaml`, and the
  generated `parse_json` signature suite.
- Current blocker: none in JSON. No conformance-suite files were changed. No
  work is staged, committed, pushed, or merged.
- Next action: rerun the complete managed stock profile fail-fast and fix the
  next actual server mismatch.

## Listen precedence green; feature reporting is the active boundary

- The next complete stock run stopped at
  `listen_two_args_rejects_non_integer_description_float`: Banteng returned
  `E_INVARG`, while Toast and the pinned authority require `E_TYPE` when the
  second argument is not an integer. Exact boundary: 4,128 passed, 670 skipped,
  one failed.
- Split the `listen` validation order so a non-integer descriptor is rejected
  with `E_TYPE` before handler-validity checking; an integer naming an invalid
  handler still returns `E_INVARG`. The focused unchanged
  `listen_call_shapes.yaml` authority now passes 13/13, and the production
  catalog regression is green.
- During regression placement, the new assertion was initially inserted into
  the adjacent buffered-output test. The resulting local failure exposed the
  mistake immediately; it was removed and placed in the actual listen test
  before any conformance evidence was taken. No production workaround or
  stable-test change remains from that error.
- The next full stock run advanced to 4,165 passed and 670 skipped, then
  `builtins/map.yaml::empty_map` failed admission because
  `server_version("features")` is absent (`E_VERBNF`). This is a feature-probe
  failure, not a map-behavior regression: the complete focused map authority
  already passes 41/41.
- Current blocker: pin Toast's `server_version` call and return contracts and
  the suite's feature vocabulary. Banteng must advertise only features it
  actually implements; reporting unsupported capabilities would incorrectly
  admit later suites.
- Next action: implement the exact feature-reporting surface, verify its own
  generated/call-shape authority plus `map.yaml`, then resume the complete
  stock fail-fast run.

## Stock feature reporting aligned; move position is the active boundary

- Pinned Toast's complete `server_version` contract: no arguments returns its
  version string; one `TYPE_ANY` argument returns the full list metadata tree
  unless it is a nonempty string, in which case it resolves a slash-delimited
  path or raises `E_INVARG`. Registration is exactly 0..1 `TYPE_ANY`.
- A direct unchanged Toast stock run of `builtins/map.yaml` passed admission
  but skipped all 127 behavior rows: this Toast build's `features` list does
  not contain `maps`. Therefore Banteng must not advertise `maps` merely to run
  that suite. Doing so would diverge from the oracle and turn intentional stock
  exclusions into Banteng-only admissions.
- Added `server_version` with Banteng's stable `0.1.0-SNAPSHOT` version, the
  Toast-shaped top-level metadata and path lookup, truthful `OUTBOUND_NETWORK`
  and `PROMOTE_NUMBERS` option values, and an empty stock feature list. The
  whole `BuiltinCatalogTest` passes. Unchanged focused authority passes all 10
  server-version/call-shape rows and skips the same 127 map rows as Toast.
- The rebuilt full stock profile collected 12,762 cases, cleared SQLite
  admission and all earlier repaired areas, and stopped at
  `builtins/move.yaml::move_position_reorders_existing_contents`. Exact
  boundary: 4,415 passed, 869 skipped, one failed. Banteng returned `E_ARGS`,
  showing its `move` manifest does not accept the authority's optional position
  argument.
- Current blocker: pin Toast's exact `move` signature, validation precedence,
  and destination-contents reordering rules. This is a real server mismatch,
  not an admission or harness failure.
- Next action: add focused red coverage for optional-position movement, repair
  the builtin/runtime mutation contract without changing the suite, run the
  complete move authorities, then resume stock fail-fast.

## Move position semantics pinned; continuation wiring remains

- Toast registers `move(OBJ, OBJ [, INT])`. Its `do_move` rejects negative
  positions with `E_INVARG`, requires control of the destination for a positive
  position, runs the ordinary destination `accept` lifecycle, and carries the
  requested position in its persisted builtin continuation.
- Toast's database mutation removes the object from its old contents first,
  then inserts it one-based in the destination. Position zero appends; a
  position beyond the post-removal contents length also appends. This makes
  same-location reordering well-defined rather than a no-op.
- Added a focused world-layer regression for front insertion, zero append, and
  oversized append. The initial red compile also caught one locally malformed
  `WorldObject` fixture construction; its argument order was corrected before
  production evidence.
- Added the optional integer to the builtin manifest and validation, and added
  `WorldTxn.move(object, destination, position)` while retaining the two-arg
  append overload. Production and test compilation are green.
- Current blocker: Banteng's existing deferred move result and VM hook frame
  retain only object and destination. A move whose destination `accept` verb
  suspends would therefore lose the requested position unless it is added to
  that continuation state. The new result field exists but is not yet consumed.
- Next action: thread `movePosition` through immediate VM application,
  `pushVerbFrame`, snapshots, restore/export guards, and post-`accept`
  completion; then run world/VM tests and every unchanged move authority.

## Move continuation green; recursive call-shape defect exposed

- Threaded `movePosition` through the builtin result, VM immediate path,
  destination-`accept` verb frame, VM snapshot/restore, v17 export guard, and
  post-hook completion. Added an end-to-end regression proving a destination
  `accept` verb can run and return without losing the requested position.
  Focused world mutation, move-continuation, and snapshot tests pass.
- A wider Java VM selection still has five failures in pre-existing map-order
  and eval/thread-mode expectations. They reproduce individually and are not on
  the new move path; they were not hidden or rewritten. The exact move-specific
  tests are isolated and green.
- The first fresh focused conformance run passed admission plus all three
  `builtins/move.yaml` positional rows. It then timed out on
  `move_call_shapes.yaml::move_two_args_accepts_objects` (`move(#0,#0)`). Every
  later failure was a cascade from the same unresponsive shared server, so none
  is independent signature evidence yet.
- Toast's lifecycle explains the hang: it validates recursive containment and
  bypasses destination `accept` for a same-location move. Banteng instead sent
  `move(#0,#0)` down the hook path and had no `E_RECMOVE` distinction. The
  world primitive also rejected `$nothing`, although Toast accepts `#-1` as the
  no-location destination.
- Added recursive destination-chain validation returning `E_RECMOVE`, the
  same-location zero-position no-op and positive-position direct reorder path,
  and `$nothing` removal support in `WorldTxn.move`. These changes are applied
  but not yet rebuilt or authority-tested.
- Current blocker: none known; fresh evidence is required because the prior
  shared-server cascade is invalid after its first timeout.
- Next action: compile and run focused Java tests, then rerun all three unchanged
  move suites on a new managed server. If green, resume full stock fail-fast.

## Move family green; notify call shape green

- Rebuilt after recursive-location, same-location, and `$nothing` fixes. The
  complete unchanged focused move surface now passes 16/16 across positional
  behavior, call shapes, invalid objects/destinations, and generated signature
  checks. The previous timeout cascade does not reproduce.
- The next complete stock run cleared move and stopped at
  `notify_call_shapes.yaml::notify_three_args_accepts_flag_float`. Exact
  boundary: 4,424 passed, 874 skipped, one failed. Banteng accepted only the
  two-argument form and returned `E_ARGS`; Toast registers
  `notify(OBJ, STR [, ANY [, ANY]])`.
- Widening the manifest made all generated signature rows pass but exposed a
  uniform value mismatch across the 55 optional-flag product rows: Banteng's
  notification result hard-coded `0`, while Toast returns `1` for the
  unconnected `#0` target used by the authority.
- Changed the notification result value to one. The full unchanged focused
  notify surface now passes 61/61, and the whole catalog regression remains
  green.
- Observation: these call-shape rows prove signature, MOO truthiness acceptance,
  and the unconnected-target return value. They do not prove that Banteng routes
  notifications to an arbitrary live target or honors no-flush/no-newline at
  the socket layer; current `Result.output` still models only a line on the
  calling task. Do not claim those transport semantics until a later authority
  exercises them.
- Current blocker: none in move or the reached notify slice. No conformance
  files were changed; nothing is staged, committed, pushed, or merged.
- Next action: resume the complete managed stock profile fail-fast and repair
  the next observed Banteng mismatch.

## Primitive prototype dispatch is the next stock boundary

- The post-notify stock run reached 4,566 passed and 1,092 skipped before
  stopping at `builtins/primitives.yaml::integer_prototype_no_verb`.
  Banteng returned `E_TYPE`; the unchanged authority expects `E_VERBNF` after
  its setup installs `#0.int_proto` as an object with no matching verb.
- This is not connected to SQLite, Mongoose, or `PROMOTE_NUMBERS`. The stock
  suite directly requires primitive receiver dispatch through prototype
  properties on `#0`: `int_proto`, `float_proto`, `str_proto`, `err_proto`,
  `list_proto`, and `map_proto`.
- The complete suite contract also checks inherited lookup, preservation of
  the primitive as `this` in `callers()`, the prototype object as the queued
  task's dispatch location, and `pass()` from a prototype override to its
  parent implementation.
- Banteng's `CALL_VERB` currently supports only objects, anonymous objects,
  and WAIFs, so every ordinary primitive falls through to `E_TYPE`. Its
  special `pass()` path likewise rejects primitive `this` before considering
  the already-recorded object verb location.
- Added focused VM tests for prototype dispatch/primitive `this`, missing
  prototype versus missing verb error distinction, and primitive-prototype
  `pass()`. The Windows test process did not reach those semantics: catalog
  initialization failed because this host lacks `libcrypt.so.1`. This is the
  known platform limitation, not a test-world topology defect; authoritative
  Java verification must run under WSL with `/opt/java/25`.
- Current blocker: obtain the semantic red result under WSL, then implement
  the six-property prototype resolver in the VM dispatch path and allow
  supported primitive receivers through `pass()` while retaining the
  prototype as the defining verb location.
- Next action: make the focused tests semantically red, implement the narrow
  dispatcher change, run all VM tests affected by call frames, then run the
  complete unchanged `builtins/primitives.yaml` authority before resuming the
  full stock profile.

## Primitive prototypes green; forced-input read is next

- The focused primitive tests were semantically red under WSL before the
  production change: ordinary primitive calls returned `E_TYPE`, including
  the configured-prototype and `pass()` cases. The earlier Windows
  `libcrypt.so.1` initialization failure was not counted as red evidence.
- Implemented a closed VM resolver for the six authority-defined `#0`
  properties: `int_proto`, `float_proto`, `str_proto`, `err_proto`,
  `list_proto`, and `map_proto`. `CALL_VERB` now uses the configured prototype
  for inherited lookup/defining location while keeping the original primitive
  value as the frame receiver and `this`.
- The `pass()` path now admits those six primitive receiver families and walks
  the parents of the stored defining prototype location. Unsupported receiver
  types and primitives without a configured object prototype still return
  `E_TYPE`; a configured prototype without the requested verb returns
  `E_VERBNF`.
- Three focused VM regressions pass under WSL `/opt/java/25`. The entire
  unchanged primitive authority passes 23/23, including all six no-prototype
  and configured-prototype pairs, queued-task prototype location, primitive
  `callers()` identity, inheritance, and `pass()`.
- The resumed full stock run cleared the primitive boundary and stopped at
  `builtins/read.yaml::read_returns_forced_input`. Exact boundary: 4,660
  passed, 1,169 skipped, one failed. After the test queues
  `force_input(player, "test_read_line")`, Banteng's `read(player)` returns
  `E_INVARG` instead of the queued string.
- Current blocker: determine whether the forced line is lost during the
  irrevocable builtin handoff, stored against the wrong connection/player, or
  rejected by `read()`'s connection lookup/precedence.
- Next action: inspect the unchanged read authority and Banteng's
  `force_input`/`read` request flow, add a focused runtime regression at the
  failing lifecycle boundary, repair that state transition, run the complete
  read authority, then resume full stock fail-fast.

## Blocking read green; explicit anonymous recycle hook is next

- Root cause of the read failure was direct: `MooRuntime.read` implemented
  only the two-argument truthy nonblocking form and hard-coded every blocking
  form to `E_INVARG`. `force_input` publication itself was already working.
- Added publication-owned blocking-read waiters keyed by concrete connection
  ID. A waiter is registered only after its VM attempt successfully publishes,
  preventing retry leakage. A committed forced-input effect or later socket
  line completes the waiter with the string instead of dispatching that line
  as a command. The zero-argument form resolves the scheduler's exact last
  input-task player; a fork remains outside that context and returns `E_PERM`.
- Focused runtime coverage proves both `read()` and `read(player)` resume from
  zero-delay `force_input`, while the prior forked no-argument denial remains
  green. Complete `MooRuntimeTest` and `PublicationSchedulerTest` classes pass.
  The complete unchanged read authority passes 17/17 across behavior, call
  shapes, and generated signature rows.
- The resumed stock run cleared read and stopped at
  `builtins/recycle.yaml::recycling_anonymous_calls_recycle_verb`. Exact
  boundary: 4,700 passed, 1,170 skipped, one failed. The parent object's
  `recycle_called` property remained zero after `recycle(create(parent, 1))`.
- This explicit-recycle path is distinct from the already-green automatic
  anonymous GC finalization lifecycle. `BuiltinCatalog.recycle()` currently
  removes an anonymous object immediately and returns zero; it never asks the
  VM to invoke the anonymous object's inherited `recycle` verb. Permanent
  objects already have a synchronous VM hook continuation.
- Current blocker: represent the anonymous identity in the explicit recycle
  continuation without conflating it with permanent object IDs or the create
  return override, and preserve it through snapshots/suspension so the hook
  completes before the builtin returns and the anonymous object is removed.
- Next action: add focused VM coverage for explicit anonymous hook execution,
  recursive `recycle(this)` rejection, error propagation/removal order, and
  suspension persistence; implement the typed continuation; run all recycle
  authorities and VM snapshot/runtime regressions; then resume stock.

## Anonymous recycle hook implemented; invalidation ordering under repair

- Added a distinct anonymous-recycle request to builtin results and a typed
  anonymous-recycle VM frame continuation. The continuation is included in VM
  snapshots and rejected by the v17 queued-task export guard, rather than
  encoding an anonymous identity as a permanent object number.
- The first focused conformance attempt was invalid evidence because the
  managed runner used the previously installed distribution. After rebuilding
  with Gradle, the unchanged recycle authority passed its first 25 cases and
  failed only
  `calling_recycle_in_recycle_verb_on_anonymous_fails`: the hook counter was
  48 instead of 1.
- Exact cause: the initial continuation kept the anonymous body registered
  until the hook returned. Its `recycle(this)` therefore redispatched the same
  hook recursively until `E_MAXREC`. The suite requires the hook to observe
  `this` as type `ANON`, but a nested recycle of that identity must get
  `E_INVARG`; a saved reference must likewise be invalid after the hook.
- The corrected lifecycle captures and compiles the inherited hook and its
  defining location, removes the anonymous body before pushing the hook frame,
  and treats the frame marker only as the builtin return continuation. Error
  unwinding no longer attempts a second removal. This preserves external hook
  writes and the typed identity while making `valid(this)` false and nested
  `recycle(this)` fail exactly once.
- Current state: the ordering change is applied but not yet compiled or tested.
  No conformance fixture has been edited.
- Next action: add focused VM regressions for one-shot nested recycle and saved
  invalid identity, rebuild/install under WSL, rerun all 32 unchanged recycle
  cases, then resume the full stock fail-fast profile at the next boundary.

## Anonymous recycle lifecycle reaches 31/32; recursive GC wave isolated

- Added and passed focused VM regressions proving that explicit anonymous
  recycle invalidates before its hook, nested `recycle(this)` raises
  `E_INVARG` after exactly one hook call, and a saved `this` retains type
  `ANON`, exact reference identity, and `valid() == 0`. VM snapshot tests and
  the complete runtime test class pass under WSL.
- The first rebuilt recycle authority run reached 28/32. Its four remaining
  rows all required anonymous values detached by recycling a permanent or
  anonymous record to enter the existing finalization lifecycle before the
  next observer command.
- Ordinary returned/errored VM tasks now queue unreachable anonymous values
  and launch pending finalizers immediately. Finalization traversal is
  deliberately distinct from reachability traversal: all properties retain
  values while an object is live, but destruction of an anonymous root
  cascades in the same wave only through inherited property slots
  (`defined == false`). This matches the authority's explicit distinction
  between locally defined and inherited properties.
- That change reached 31/32. The sole failure was
  `recycling_anonymous_recycles_property_values_on_object`, expected counter 1
  but observed 2. The excluded local child was correctly absent from the
  outer finalization set, but completion of the outer hook itself started a
  second immediate GC wave and finalized that newly orphaned local child
  before the observer command.
- The publication API now receives whether completion collection is allowed.
  `PublicationScheduler` disables it only for VM work carrying
  `ANONYMOUS_FINALIZATION_RETURN`; ordinary tasks retain immediate collection,
  and inherited descendants already selected into the original wave retain
  their finalizers. This change is applied but not yet compiled or tested.
- Current blocker: verify that continuation-sensitive suppression produces
  32/32 without regressing suspension-based GC or runtime scheduling.
- Next action: rebuild and run all runtime/VM lifecycle tests, rerun all 32
  unchanged recycle cases, then resume the full stock fail-fast profile.

## Final recycle row reduced to explicit local-descendant deferral

- Continuation-sensitive suppression compiled cleanly; complete
  `MooRuntimeTest`, `PublicationSchedulerTest`, focused anonymous-recycle VM
  tests, and `VmSnapshotTest` passed. The recycle authority nevertheless
  remained 31/32 with the same local-property row at counter 2.
- Added a focused runtime reproduction with the authority's exact `go` verb
  and its two calls. This established that the first failing half is the
  explicit path: empty `args` is false, so `args || recycle(x)` invokes
  `recycle(x)`. The expected counter is 1 before the observer; Banteng returns
  2.
- Replaced order-dependent unreachable-root selection with component-root
  selection based on anonymous references. This is independently correct (a
  nested identity cannot become a second component root merely because map
  order visits it first), but it did not change this row.
- A temporary diagnostic at `finalizePendingAnonymousObjects` showed the sole
  pending root has only inherited `recycle_called:false`; it is the local child,
  not the explicit outer object. The outer body was already invalidated before
  its captured hook, exactly as required. The child becomes an ordinary
  unreachable root at publication and is finalized immediately, producing the
  unwanted second hook call.
- Precise missing state: before explicit anonymous removal, collect anonymous
  descendants reachable through properties with `defined == true`, retain
  those identities as task-scoped collection deferrals, and exclude them from
  every collection pass for that task (including a suspended recycle hook).
  Inherited property slots remain eligible immediately. Deferrals must survive
  `VmSnapshot` restore and must not be encoded as v17 activation data.
- Current blocker: implement that typed task-state field and remove the
  temporary diagnostic print. The focused runtime regression currently fails
  at its first observer with 2 versus 1.
- Next action: add snapshot-preserved deferrals, route them into runtime
  unreachable selection, remove diagnostics, make the focused runtime test and
  all 32 recycle rows green, then resume stock.

## Recycle 32/32 green; set_player_flag call shape green

- Implemented snapshot-preserved, task-scoped anonymous collection deferrals.
  Explicit anonymous recycle captures anonymous identities reachable through
  locally defined property values before invalidating the outer body. Runtime
  reachability treats those identities as roots only for that task's
  publication, including across a suspended hook. Inherited slots are not
  deferred. The v17 export path fails closed when a suspended task contains
  this Banteng-only state.
- Added a direct snapshot round-trip assertion and a focused runtime authority
  reproduction covering both `go()` (explicit recycle) and `go(1)` (automatic
  collection). Removed the temporary diagnostic print. Complete runtime,
  publication scheduler, focused VM lifecycle, and snapshot tests pass.
- The complete unchanged recycle authority now passes 32/32. No conformance
  fixture was edited.
- Resumed the stock profile. It cleared the prior boundary and reached 4,759
  passed, 1,325 skipped at 47%, then failed
  `set_player_flag_call_shapes.yaml::set_player_flag_value_float`: expected
  `E_INVARG` for missing `#999999`, observed call-shape `E_TYPE` on the float.
- Root cause was a manifest/handler mismatch. The authority declares the value
  `TYPE_ANY`; Banteng declared `INTEGER` and cast it. Changed only that argument
  to `ANY`, preserved object validation ordering, and apply ordinary MOO
  truthiness for a valid target. A focused catalog test proves invalid-target
  precedence plus truthy enable/falsy disable on a real object.
- The complete unchanged set-player-flag call-shape authority passes 8/8.
- Current blocker: none at the focused boundary; the next stock failure is not
  yet known.
- Next action: resume the full stock fail-fast profile on the rebuilt
  distribution and repair the next unchanged authority failure.

## Direct-eval `args` boundary at 9,166

- Full stock crossed the complete unchanged `language/in_operator.yaml` family
  and stopped at 9,166 passed, 2,164 skipped (88%) on
  `language/moocode_parsing.yaml::expression_exception_syntax`.
- The unchanged expression is `` `args ! ANY => 0' `` and expects `[]`.
  Banteng returned the tokenized generated source
  `{\"return\", \"`args\", \"!\", \"ANY\", \"=>\", \"0';\"}`.
- The lexer, parser, AST, compiler, and unparser already implement the complete
  backtick catch form, including `ANY` and `=>`. The observed value proves the
  catch expression compiled and returned `args`; the defect is that Banteng's
  server-side direct-eval frame seeds `args` from its generated source words.
  Toast direct eval supplies an empty argument list.
- Replaced the aliased eval locals with Toast's fresh predefined environment:
  `player` is preserved; `caller` is the calling activation's `this`; `this`,
  `dobj`, and `iobj` are `#-1`; command strings, `verb`, and `args` are empty.
  The eval activation's receiver and verb location are also `#-1`, while its
  programmer and thread mode remain inherited. A focused VM regression for the
  entire environment passes, and the exact `expression_exception_syntax` row
  is green against the rebuilt distribution.
- The complete unchanged parsing family advanced through 14 rows and stopped
  at `bitwise_and`: `1 &. 2` reports a lexer error expecting a second `&`.
  The authority continues with `&.`, `|.`, and `^.` execution, disassembly,
  and decompilation rows, so this is a missing Toast bitwise operator family,
  not an isolated ampersand spelling.
- Current blocker: derive the bitwise operators' precedence, integer/error
  semantics, bytecode names, and exact unparse spelling from pinned Toast, then
  add the smallest coherent AST/lexer/compiler/VM implementation.
- Next action: add focused parser/VM red tests for the three bitwise forms,
  implement their full owned path, rebuild, and resume the same unchanged
  `language/moocode_parsing.yaml` family from its first failing row.

## Parsing authority 52/52 applicable green; `toobj` boundary at 9,218

- Added Toast's coherent bitwise expression family: `&.`, `|.`, `^.`, `<<`,
  `>>`, and unary `~`; exact precedence; canonical unparse spellings; named
  `BITAND`, `BITOR`, `BITXOR`, `BITSHL`, `BITSHR`, and `COMPLEMENT` bytecodes;
  integer-only execution; logical right shift; and the 64-bit shift boundary
  and `E_INVARG` rules. Focused parser and VM tests pass.
- The first family rerun exposed the Toast lexer ambiguity between XOR `^.` and
  first-index followed by a range `^..`. Added the pinned two-dot guard so only
  a single following dot forms XOR. The complete unchanged
  `language/moocode_parsing.yaml` authority then passed 52 applicable rows with
  23 declared 32-bit skips.
- The full stock profile crossed that repaired family and stopped at 9,218
  passed, 2,187 skipped (89%) on
  `language/object_authority.yaml::toobj_string_boundaries_clamp_on_native_overflow`.
  In-range negative/positive limits and exact `Long.MIN_VALUE` pass, but
  Banteng returns `#0` for three overflowing decimal strings where Toast clamps
  negative overflow to `Long.MIN_VALUE` and positive overflow to
  `Long.MAX_VALUE`.
- Current blocker: locate `toobj`'s string conversion owner and distinguish
  malformed/no-digit input from native signed overflow without changing object
  literal lexing or numeric conversion builtins.
- Next action: add focused catalog regressions for both overflow directions and
  malformed input, implement Toast's saturating string-to-object conversion,
  rebuild, run the complete unchanged `language/object_authority.yaml`, then
  resume full stock.

## `toobj` overflow fixed; full-width object ordering is active

- Added a focused catalog regression proving positive overflow saturates to
  `Long.MAX_VALUE`, negative overflow saturates to `Long.MIN_VALUE`, and
  malformed/suffixed strings remain `#0`. It failed red and passes after the
  conversion repair.
- Replaced `Long.parseLong` plus catch-to-zero with Toast's `parse_object` /
  `strtoimax` contract: leading spaces, optional `#`, C-style leading
  whitespace before an optional sign and decimal digits, trailing literal
  spaces only, malformed input to zero, and signed overflow saturation. The
  previously failing unchanged `toobj_string_boundaries_clamp_on_native_overflow`
  row is green.
- The same unchanged object authority next stops on
  `scalar_equality_order_and_narrowed_map_comparator_are_distinct`. Pinned Toast
  compares object scalars at the full native width, while its map comparator
  intentionally narrows the object-id difference. Banteng already has the
  narrowed map-key comparator, so `#0` and `#4294967296` correctly collapse to
  one map entry, but its VM relational comparison omitted OBJECT/OBJECT and
  raised `E_TYPE`.
- Added a focused VM regression for full-width relational order plus the
  narrowed map collision and implemented OBJECT/OBJECT comparison with exact
  signed-long ordering. The test's first green attempt used a catalog-free VM
  helper despite calling `length()`; that harness issue is being corrected to
  the normal catalog-backed execution path and is not a production finding.
- Current blocker: verify the corrected focused test, then prove the remaining
  unchanged object-authority rows, including scalar object index read/write
  `E_TYPE` behavior.
- Next action: rerun the focused VM test on the catalog-backed helper, rerun all
  of `language/object_authority.yaml`, then resume the full stock fail-fast run
  beyond 9,218.

## Structured traceback contract green; uncaught publication remains

- Full stock crossed the built-in privilege family and stopped at 9,061
  passed, 2,034 skipped (86%) on
  `error_traceback::caught_exception_has_four_elements`: Banteng bound a
  caught error as a two-element `{code, message}` value instead of Toast's
  `{code, message, value, traceback}`.
- The VM already contained an unused four-slot exception normalizer and
  six-field caller-frame data. Error propagation now completes default error
  messages and values, captures frames from the raising activation through the
  matching catch activation, retains source lines, and preserves explicit
  `raise()` message/value payloads. `ErrorValue` now owns the single Toast
  description table used by both `tostr(error)` and exception defaults.
- Added a focused root-catch pipeline regression covering all four fields, the
  `Division by zero` default, value `0`, one six-field eval frame, and line 1.
  It passes.
- The complete unchanged traceback family now passes its first 21/29 rows:
  tuple shape, custom payloads, frame fields/order, line numbers, two-verb
  chains, direct eval, and verb-to-eval catch boundaries are all green.
- The remaining eight rows are a distinct runtime-publication gap. Uncaught
  command errors produce no player traceback output, and uncaught background
  errors neither call `#0:handle_uncaught_error` nor write a fallback log.
  Consequently handler-audit properties remain empty and later reads raise
  `E_RANGE`; eval/stored-verb-name printed-trace rows fail for the same reason.
- Current blocker: the VM currently retains only `uncaughtError`, not the full
  completed exception tuple, after unwinding. PublicationScheduler treats
  `ERRORED` as an ordinary silent terminal result.
- Next action: retain the full uncaught exception through `VmState`/snapshot,
  route foreground command errors to exact Toast traceback output, route
  background errors through `#0:handle_uncaught_error` with fallback logging,
  then rerun all 29 unchanged traceback rows.

## Complete traceback authority 29/29 green

- Uncaught VM termination now retains the complete four-element exception on
  the terminal state while preserving the existing `uncaughtError` code API.
  Runtime formatting resolves stored verb name specs, renders direct/eval and
  caller frames with source lines, includes the built-in `eval()` boundary,
  and appends the exact `(End of traceback)` terminator.
- Foreground command continuations return those formatted lines to the player.
  Background/forked terminal errors start a fresh server task for
  `#0:handle_uncaught_error` with `{code, message, value, traceback,
  formatted}`; absence of a handler falls back to server stderr. The handler
  task has no caller activation, matching the fixture's server-task guard.
- The first intermediate rerun improved from 21/29 to 26/29, proving all
  foreground formatting rows. After adding the background terminal route, the
  complete unchanged `language/error_traceback.yaml` passes 29/29, including
  forked handler logs, exact six-field handler frames, custom uncaught payload,
  eval labels, and stored wildcard verb names. No conformance file or fixture
  was edited.
- Current blocker: none at the traceback boundary; the next stock failure is
  unknown.
- Next action: resume the full stock fail-fast profile against the rebuilt
  distribution and repair the next unchanged authority failure.

## `in` operator 18/18 green at the 9,105 boundary

- Full stock crossed complete traceback authority and stopped at 9,105
  passed, 2,034 skipped (86%) on `in_operator::in_string_found`. Banteng's VM
  membership owner implemented LIST and MAP values only, so a STR right-hand
  side raised `E_TYPE`.
- Added a focused VM regression covering found, case-folded, empty-needle, and
  absent substring results. Added the missing Latin-1 byte substring search to
  `StringValue` and the STR/STR branch to `MooVm.membership`; results are
  one-based, with zero for absence.
- The focused test passes and the complete unchanged
  `language/in_operator.yaml` passes 18/18. This also confirms existing deep
  LIST equality, first-match behavior, MAP-value positions, and scalar RHS
  `E_TYPE` behavior remain correct. No conformance file or fixture was edited.
- Current blocker: none at this focused boundary; the next stock failure is
  unknown.
- Next action: resume the full stock fail-fast profile against the rebuilt
  distribution and repair the next unchanged authority failure.

## Boolean authority 6/6 green; Toast map lookup topology restored

- The focused mixed BOOL/INT regression failed red because `MapValue.get`
  linearly scanned exposed entries. Toast's `maphaskey()` instead searches the
  map's red/black tree with its non-antisymmetric BOOL comparator, so a key can
  remain visible to traversal while being unreachable from the tree root.
- Added a private Toast-style lookup tree to `MapValue`. Ordinary exposed map
  entry order remains unchanged, while `with()` now preserves lookup topology
  for distinct insertions and `get()` follows the same left/right comparison
  path. Case-sensitive string lookup checks exact bytes at each candidate but
  navigates using the case-insensitive tree relation, matching pinned Toast
  `rbfind`.
- The focused catalog regression is green. The complete unchanged
  `language/boolean_authority.yaml` passes 6/6, including direct indexed reads
  for both two-BOOL insertion orders and the four-key topology where
  `mapkeys()` retains `true` but `maphaskey(..., true)` returns zero. No
  conformance file or fixture was edited.
- The broader `MooValueTest` run compiled the production change and ran 15
  tests; its only failure was the existing map-equality/order contract at line
  240. The new lookup tree does not participate in `MapValue.equals()` or
  `hashCode()`, so that independent contradiction remains for final suite
  audit rather than being hidden by changing a stable test.
- Current blocker: none at the focused BOOL boundary; the next stock failure
  is unknown.
- Next action: resume the full stock fail-fast profile against the rebuilt
  distribution and repair the next unchanged authority failure.

## Built-in privilege writes 3/3 green at the 9,002 boundary

- Full stock crossed the Boolean authority and stopped at 9,002 passed, 2,034
  skipped (86%) on
  `builtin_property_write_permissions::programmer_cannot_promote_regular_object_to_wizard`.
  An ordinary programmer's `player.wizard = 1` returned success instead of
  `E_PERM`.
- Pinned Toast `OP_PUT_PROP` handles `BP_PROGRAMMER` and `BP_WIZARD` before
  ordinary property permissions and rejects either write unless the current
  programmer is a wizard. Banteng looked for an ordinary property record;
  built-ins have none, so it fell through to the unrestricted world mutation.
- Added a focused VM pipeline regression for both `.wizard` and `.programmer`
  assignment by a non-wizard programmer, including proof that flags remain
  unchanged. It failed red and now passes after enforcing the built-in
  privilege gate in `MooVm.setProperty`.
- The complete unchanged
  `language/builtin_property_write_permissions.yaml` passes 3/3. No suite or
  fixture was edited.
- Current blocker: none at this focused boundary; the next stock failure is
  unknown.
- Next action: resume the full stock fail-fast profile on the rebuilt
  distribution and repair the next unchanged authority failure.

## Dynamic limits green; fixture provenance mismatch isolated at 8,226

- The unchanged dynamic list-limit authority now passes 2/2. Banteng enforces
  `max_list_value_bytes` for the six mutating list builtins and for VM list
  construction/append opcodes, including construction of builtin argument
  lists. Binary list `+` remains the explicit Toast exception.
- Full stock advanced to 8,226 passed, 1,826 skipped and stopped at
  `fixture_provenance/panic_live_waif_anon_roots.yaml::panic_root_recipe_output_matches_checked_in_fixture_bytes`.
  The generated database differs from the checked-in Toast fixture only in
  the installed `#0:server_started` program text: Banteng stores raw
  `set_verb_code` input, including lower-case `waif`/`anon` and four two-space
  block indents, making the dump eight bytes longer.
- Pinned Toast source confirms that compiled-program symbol slots render type
  constants canonically (`NUM`, `OBJ`, `STR`, `LIST`, `ERR`, `INT`, `FLOAT`,
  `MAP`, `ANON`, `WAIF`, `BOOL`), while ordinary runtime slots remain lower
  case. Toast database program serialization also emits the block body without
  leading indentation; `verb_code` can add presentation indentation later.
- Added a focused `set_verb_code` regression using the exact WAIF/ANON/fork
  shape. It fails red against raw storage. Production changes now canonicalize
  built-in variable slots at AST unparse boundaries and have `set_verb_code`
  store a parsed, validated, unparsed, indentation-free program. Existing
  loaded verb source and global V17 writer behavior are deliberately untouched.
- Current blocker: the new production change has not yet been compiled or
  tested; its focused regression is the immediate gate.
- Next action: run the focused catalog/unparser tests, repair any regression,
  rebuild `installDist`, rerun the unchanged fixture-provenance row, then
  resume the stock fail-fast profile.

## First provenance fix green; nested pending-WAIF boundary at 8,227

- The focused `set_verb_code` and complete unparser tests pass after adding
  Toast's `$property`/`$verb()` rendering for `#0` static names. The unchanged
  panic-live-root provenance row now byte-matches its checked-in fixture.
- Full stock resumed and stopped at 8,227 passed, 1,826 skipped on
  `fixture_provenance/pending_waif_nested_topology.yaml`. Actual output is
  2,555 bytes versus the 2,593-byte Toast fixture, first differing at header
  offset 52: Banteng writes `0 values pending finalization`; Toast writes one
  top-level WAIF root containing its nested child WAIF.
- Direct diff confirms this is the only output difference. The recipe creates
  a parent and child WAIF, stores the child in the parent's property, drops
  both locals, and shuts down. The canonical pending value is the parent only;
  the child must remain nested rather than becoming a second pending root.
- Root cause is in runtime collection ownership. `queueUnreachableAnonymousObjects`
  traverses WAIFs while marking anonymous reachability, and its shared
  `visitedWaifs` set already identifies WAIFs reachable from world/task roots,
  but the method only computes and queues unclaimed anonymous components. It
  never computes unclaimed WAIF components, so unreachable WAIFs exist in the
  transaction but have no serialized root and disappear from the checkpoint.
- A second checked-in authority (`PendingWaifRecycleShutdown.db`) requires one
  unreachable WAIF with a `:recycle` verb to be serialized pending, then on the
  next boot to execute that hook, allow `shutdown()` to return, and checkpoint
  with zero pending values. Current `finalizePendingAnonymousObjects` ignores
  WAIF pending roots, so queueing alone will expose the subsequent lifecycle
  gap.
- Current blocker: implement WAIF component rooting and deferred recycle
  lifecycle without perturbing anonymous-object collection or promoting nested
  WAIFs to separate roots.
- Next action: add focused runtime/persistence red tests for one nested pending
  WAIF root and WAIF recycle completion, implement root selection plus the
  WAIF finalization continuation/removal path, rebuild, and rerun both unchanged
  fixture-provenance and startup pending-WAIF families.

## Pending-WAIF provenance green; lifecycle implemented and locally green

- Added a production persistence regression that boots the nested-WAIF recipe
  and compares the resulting checkpoint byte-for-byte with the canonical Toast
  fixture. It failed red at the pending-finalization header and is now green.
- `queueUnreachableAnonymousObjects` now also computes unreachable WAIF
  components. It derives candidate roots from graph incoming edges, stores only
  one top-level root per component, and traverses nested WAIF and anonymous
  property values. This preserves the parent/child topology without promoting
  the child to a second pending root.
- Both unchanged recipe provenance suites now pass: nested topology 2/2 and
  recycle-shutdown 2/2. The unchanged nested startup lifecycle also passes.
- The recycle startup authority initially timed out exactly as predicted:
  pending WAIF roots were never finalized. Added a second production red test
  booting `PendingWaifRecycleShutdown.db`; it now passes.
- Startup now processes pending WAIF roots with executable inherited
  `:recycle` hooks before `server_started`, with `this` set to the WAIF and a
  dedicated runtime continuation. On hook return it removes the complete WAIF
  component and pending root, then continues remaining pending WAIFs and normal
  startup. `WorldTxn.removeWaif` supplies tracked transactional deletion.
  WAIF finalization, like anonymous finalization, is excluded from ordinary
  post-task reachability collection to avoid immediately re-queueing itself.
- The two focused persistence tests compile and pass after the implementation;
  `installDist` was rebuilt as part of that run.
- Current blocker: the unchanged recycle startup suite has not yet been rerun
  against the rebuilt implementation, and broader anonymous/WAIF runtime
  regressions remain to be checked.
- Next action: rerun both unchanged pending-WAIF startup suites, run the full
  persistence/runtime focused regressions, then resume stock fail-fast beyond
  8,227.

## Pending-WAIF lifecycle green; boolean boundary at 8,997

- Both unchanged pending-WAIF startup suites pass 3/3 after the lifecycle
  implementation. No conformance fixture or suite was edited.
- The broader Java run found three legacy anonymous multi-boot expectation
  failures and one scheduler-host-wait failure. The scheduler failure is
  precisely explained by the new Toast list limit: its synthetic argument list
  is about 160 MB and now correctly exceeds the default 64 MB limit before the
  host wait registers. Production semantics were not weakened for that test.
  The anonymous failures are outside the new WAIF paths and need a separate
  final regression audit rather than speculative expectation edits.
- Full stock advanced through both repaired provenance rows and stopped at
  8,997 passed, 2,034 skipped (86%) on
  `language/boolean_authority.yaml::boolean_relational_comparisons_use_default_zero`.
  Toast treats both boolean operands as the same default numeric zero for
  relational comparison: strict comparisons are false and inclusive ones are
  true, regardless of boolean truth value. Mixed boolean/integer relational
  comparisons remain `E_TYPE`.
- Added an exact eight-result VM regression red then green. The VM comparison
  opcode now has a boolean/boolean-only branch; arithmetic and mixed-type
  behavior are unchanged. The focused test passes and rebuilt `installDist`.
- The unchanged boolean family then passed the relational and distinct-key
  rows but stopped on `boolean_and_equal_integer_mixed_map_reachability`:
  expected `maphaskey(values, true) == 0`, actual `1`, while both report
  canonical keys `{0, 1, false, true}`. This is a map lookup topology rule,
  not relational coercion.
- Current blocker: pin why Toast's lookup misses `true` in this mixed insertion
  topology even though the true key survives map traversal; Banteng's linear
  `MapValue.get` currently finds it directly.
- Next action: inspect pinned Toast `bf_maphaskey` and map search-tree
  comparator behavior, add the narrow mixed boolean/integer map regression,
  fix lookup without collapsing key storage, rerun the full boolean authority,
  then resume stock.

## List value limit boundary at 8,217

- Full stock crossed the corrected `do_command` row and stopped at 8,217
  passed, 1,825 skipped on
  `features/limits_dynamic.yaml::setadd_checks_list_max_value_bytes`.
  Banteng returned an oversized list; the unchanged authority expected
  catchable `E_QUOTA`.
- A WSL-authority Java regression now fails red on the same behavior. The
  attempted Windows test run was discarded because `BuiltinCatalog` could not
  load Linux `libcrypt.so.1`; it did not execute the assertion.
- Pinned Toast `src/list.cc` proves the contract applies to all six checked
  list-result builtins: `setadd`, `setremove`, `listappend`, `listinsert`,
  `listdelete`, and `listset`. Each post-checks `value_bytes(result)` even when
  the operation returns an unchanged/no-op list. LIST `+` remains an explicit
  unchecked exception. Oversize returns `E_QUOTA` when
  `max_concat_catchable` is true and otherwise aborts as out of seconds.
- Pinned option canonicalization clamps positive configured limits below 1,021
  bytes to 1,021 and nonpositive or over-maximum values to 2,147,482,626;
  absent configuration defaults to 64,537,861.
- Implemented one shared post-result guard for those six builtins and corrected
  their manifest ownership from `PURE`/`VM` to
  `TRANSACTION_READ`/`WORLD`, since the result now reads live server options.
  Updated the exact contract test accordingly.
- Current blocker: focused compilation found `all_members` calling the internal
  `setRemove` helper without a world after that helper became world-aware.
  `all_members` is a separate suspending host builtin and should retain its
  pure equality/removal calculation without applying the checked builtin's
  server-option postcondition internally.
- Next action: extract/reuse a pure set-removal value helper for
  `all_members`, keep the list-limit guard only at the public checked builtin
  boundary, rerun focused WSL tests, then rebuild and run the unchanged limits
  authority before resuming full stock.

## Precise dynamic-limit root cause: checked VM list construction

- Extracted the pure set-removal value calculation for `all_members`; the four
  focused WSL Java tests for list contracts, ordinary list semantics,
  recursive set removal, and the new `setadd` limit regression pass. The
  distribution rebuilt successfully.
- The unchanged Banteng dynamic-limit row still returned `E_NONE`, while the
  exact same row passes against the pinned Toast oracle. This disproves the
  hypothesis that the public `setadd` post-result guard alone closes the row.
- A read-only temporary Toast probe measured the same primitive/list sizes
  Banteng reports: `value_bytes({}) == 32`, `value_bytes({1}) == 48`, and a
  90-integer list is 1,472 bytes. With the configured limit set to 1,488,
  pinned Toast raises `E_QUOTA` while `x` is still the 89-element, 1,456-byte
  list.
- Therefore Toast raises while constructing the two-element builtin argument
  list `{x, i}`: at that point its recursive size is 1,504 bytes, before
  `bf_setadd` runs. Pinned `execute.cc` confirms `OP_LIST_ADD_TAIL` and
  `OP_LIST_APPEND` both enforce `SVO_MAX_LIST_VALUE_BYTES`; empty-list creation
  does not. LIST binary `+` remains the separately proven unchecked exception.
- Banteng lowers literals, builtin/verb argument lists, and splices through
  `BUILD_LIST 0`, `LIST_APPEND`, and `LIST_EXTEND`, but its `appendList` and
  `extendList` currently push the constructed value without any limit check.
- Current blocker: no Java VM regression yet freezes the argument-list failure
  independently of the already-green public builtin guard.
- Next action: add a WSL VM regression using a 1,488-byte limit and the exact
  `setadd` loop, prove it red at VM list construction, route `LIST_APPEND` and
  `LIST_EXTEND` through the shared list-value guard (without changing binary
  ADD), then rerun focused tests and the unchanged dynamic authority.

## Dynamic limit green; fixture provenance boundary at 8,226

- Added the exact WSL VM regression at a 1,488-byte catchable list limit. It
  failed red by returning `E_NONE`, then passed after routing Banteng
  `LIST_APPEND` and `LIST_EXTEND` through the shared checked-list producer
  guard. Binary ADD was not changed, preserving the authority-backed LIST `+`
  exception.
- The focused VM/list tests and rebuilt distribution pass. The complete
  unchanged `features/limits_dynamic.yaml` authority passes 2/2 against
  Banteng; the same row was also independently confirmed green against pinned
  Toast. No conformance suite or fixture was edited.
- Full stock then passed the repaired dynamic row and stopped at 8,226 passed,
  1,826 skipped on
  `fixture_provenance/panic_live_waif_anon_roots.yaml::panic_root_recipe_output_matches_checked_in_fixture_bytes`.
- Banteng's generated `RecipePanicLiveWaifAnonRoots.db.new` is 2,661 bytes;
  checked-in `PanicLiveWaifAnonRoots.db` is 2,653 bytes. The first mismatch is
  byte offset 2,126. This is an exact checkpoint serialization/provenance
  failure, not permission to edit or regenerate the authority fixture.
- Current blocker: the specific serialized field responsible for the eight-byte
  difference is not yet isolated, and the managed temporary server directory
  may have been removed after pytest teardown.
- Next action: recover or reproduce the generated checkpoint, compare decoded
  database lines around offset 2,126 with the checked-in fixture, trace the
  responsible v17 writer path, add a focused serializer regression, and rerun
  only the unchanged provenance suite before resuming full stock.

## strtr added from stock authority; 55 focused rows green

- The rebuilt stock run cleared `set_player_flag` and all recycle/GC families,
  then stopped at 4,973 passed and 1,406 skipped on
  `string_operations.yaml::strtr_swap_characters`. Banteng returned
  `E_VERBNF`; the builtin was genuinely absent.
- Added `strtr` as a pure VM builtin with the Toast-derived signature of three
  required strings plus one optional `TYPE_ANY` case flag. The implementation
  operates on Banteng's binary string bytes, uses ASCII folding when the flag
  is false, preserves the source character's ASCII case in replacements,
  applies the last mapping for duplicate `from` characters, ignores surplus
  `to` bytes, and deletes matches whose `from` position has no `to` byte.
- Added the builtin to the reachable manifest and extended exact contract and
  behavior tests for insensitive/sensitive case, deletion, and duplicate-key
  precedence. Focused Java catalog tests pass under WSL and rebuild the
  installed distribution.
- The unchanged behavioral suite, `TYPE_ANY` call-shape suite, and generated
  Toast signature suite pass together: 55/55. No conformance fixture changed.
- Current blocker: none at the focused boundary; the next stock failure is not
  yet known.
- Next action: resume the full stock fail-fast profile and repair the next
  unchanged authority failure.

## switch_player boundary: call shape fixed; stub semantics exposed

- The rebuilt stock profile cleared the `strtr` boundary and stopped at 5,014
  passed, 1,416 skipped on
  `builtins/switch_player.yaml::third_arg_must_be_int_if_provided`. Banteng
  returned `E_ARGS`; Toast requires `E_TYPE` for a non-integer third argument.
- Root cause of that row was exact and narrow: Banteng declared only two
  required object arguments even though the stock contract has an optional
  integer `silent` argument. Added a focused red catalog test, changed the
  manifest shape to `{OBJ, OBJ [, INT]}`, and made the Java test green.
- The unchanged focused authority now passes the third-argument row, but its
  complete family reveals that `switch_player` is otherwise a stub: 7/15 rows
  pass and eight validation rows return success `0` where the suite requires
  `E_PERM` or `E_INVARG`. The current handler blindly stages the second object
  as the switched player and performs no permission, same-player, live-old-
  connection, valid-new-object, or player-flag validation.
- Existing runtime state provides the required inputs without a new fixture:
  `WorldTxn.connectionId(oldPlayer)` resolves a live connection and
  `WorldTxn.players()` identifies valid player objects. The implementation
  must preserve login-time switching, where the old object can be the live
  unauthenticated connection object, and must keep runtime session state in
  sync for ordinary connected-player switches.
- Current blocker: determine the correct publication/runtime ownership for
  applying a validated switch. `VmState.switchedPlayer` is currently consumed
  explicitly by login completion but is not visibly applied by ordinary
  command continuations; directly changing the transaction's auxiliary
  connection map alone may not update `MooRuntime.ConnectionState`.
- Next action: trace publication of VM connection effects, add focused tests
  for permission and validity ordering, implement the full stock validation
  contract at the owning layers, rerun all 15 unchanged `switch_player` rows,
  then resume the stock fail-fast profile.

## switch_player 23/23 green; yin performance boundary at 78%

- Completed the `switch_player` stock contract exposed by the family. The
  builtin is now wizard-only, accepts the optional integer `silent` argument,
  rejects identical old/new objects, requires the old object to resolve to a
  live connection, and requires the destination to be in the transaction's
  player index. A focused catalog test covers the positive staging result and
  `E_TYPE`, `E_PERM`, and `E_INVARG` ordering.
- The unchanged `builtins/switch_player.yaml` authority passes 15/15 and the
  generated Toast source-signature authority passes 8/8. No conformance file
  or fixture was edited.
- The resumed stock profile crossed the prior switch boundary and stopped at
  8,179 passed, 1,825 skipped (78%) on
  `builtins/yin_semantics.yaml::yin_refreshes_tick_budget`. The socket timed
  out after five seconds while executing a 300,000-iteration loop containing
  `yin()`; all earlier yin return, validation, and no-yin exhaustion rows pass.
- Scheduler inspection shows that a suspension wake already uses
  `MooRuntime.startBackgroundTask`, which refreshes ticks/seconds. The observed
  failure is therefore not the originally suspected preserved-budget bug.
  The hot no-argument `yin()` path was unnecessarily reading `$server_options`
  twice on every one of 300,000 calls even though those options are needed only
  to validate explicit thresholds.
- Added a semantics-preserving no-argument fast path: fixed default thresholds
  (`2000` ticks and `2` seconds), suspend at zero only when below them, and no
  world/property reads otherwise. Explicit-argument validation remains
  unchanged. A first compile correctly failed closed on an Error Prone unused
  initializer warning introduced by the refactor; removed it. The focused
  catalog contract test now passes and rebuilt the distribution.
- Current blocker: the unchanged 300,000-iteration yin authority has not yet
  been rerun, so it is not known whether removing the world reads is sufficient
  to meet its five-second behavioral contract.
- Next action: run the complete unchanged yin semantics family, measure the
  refresh row, profile/optimize the actual hot path further if it still times
  out, then resume full stock at the 78% boundary.

## yin root cause isolated; exact runtime reproduction now 2.45 seconds

- The no-world-read `yin()` path remained over the authority timeout. Added an
  exact publication-scheduler reproduction using the Test.db command route and
  the same 300,000-iteration loop. It completed correctly but took 11.827
  seconds, proving the issue was execution cost rather than a deadlock or a
  failure to refresh the budget.
- Disassembly is a 20-instruction program with an 11-instruction loop body.
  The initially suspected generic builtin plumbing was not the dominant cost:
  a default-`yin` VM intrinsic preserving the same return/suspend thresholds
  did not materially reduce the 11.9-second runtime.
- Precise root cause: `VmState.remainingSeconds()` called
  `ProcessHandle.current().info().totalCpuDuration()` on every `yin()`. That
  expensive process-info query ran 300,000 times. It was also architecturally
  wrong for concurrent workers because each task was charged for CPU consumed
  by the entire Banteng process.
- Replaced process-wide CPU accounting with cached `ThreadMXBean` current-
  thread CPU accounting. Segment anchors, snapshot charging, and restored
  budgets retain the existing structure, but now measure only the worker
  executing the task. Added a focused VM test proving the intrinsic returns at
  the 2000-tick boundary and suspends below it, plus the exact end-to-end
  publication scheduler regression.
- Focused VM and runtime tests pass. The exact 300,000-iteration scheduler test
  now takes 2.451 seconds, down from 11.827 seconds and below the conformance
  transport's five-second deadline.
- Current blocker: the unchanged `yin_semantics.yaml` family has not yet been
  rerun against the rebuilt distribution after the CPU-clock correction.
- Next action: run all 12 unchanged yin rows; if green, run relevant VM/runtime
  budget tests and resume the full stock profile beyond the 78% boundary.

## yin 12/12 green; do_command server caller fixed at 8,206

- The rebuilt unchanged yin authority passes all 12 rows, including the
  300,000-iteration refresh case. Snapshot accounting, seconds exhaustion, and
  background timeout regressions also pass after the thread-CPU correction.
- Full stock then crossed yin and stopped at 8,206 passed, 1,825 skipped on
  `command/parser.yaml::do_command_sees_command_verb_and_argstr`. The hook saw
  the correct `verb` and `argstr` but recorded `{caller == player, caller ==
  #-1}` as `{1, 0}` instead of Toast's `{0, 1}`.
- Root cause was one literal runtime-frame construction error: ordinary
  connected commands built `do_command` locals with `caller = player`, unlike
  other server-initiated hooks such as `do_out_of_band_command`, which already
  use `#-1`. Changed only the do-command hook caller to `-1`; `player` remains
  the authenticated connection player.
- Added an end-to-end publication scheduler regression that installs a real
  `#0:do_command`, sends a command, and inspects the stored `{player, caller}`
  frame. It failed red as `{#8, #8}` and is green as `{#8, #-1}`.
- The complete unchanged `command/parser.yaml` family passes 37/37. No suite or
  fixture edits were made.
- Current blocker: none at the focused boundary; the next full-stock failure is
  unknown.
- Next action: resume the full stock fail-fast profile on the rebuilt
  distribution and repair the next unchanged authority failure.

## Full-width object authority green; parser nesting is the next host-safety boundary

- Corrected two placement mistakes in the new full-width object-ordering work
  before accepting it: the regression's builtin-backed execution helper had
  accidentally replaced an unrelated parser/compiler smoke-test call, and the
  production `ObjectValue` relational branch had accidentally landed inside
  arithmetic instead of comparison. Restored the smoke test and moved the
  branch into `MooVm.comparison`.
- The focused VM regression now proves that `#0 < #4294967296` and the reverse
  `>` comparison use the full signed 64-bit object IDs while the existing map
  comparator intentionally narrows those two keys to one identity. The rebuilt
  unchanged `language/object_authority.yaml` family passes 6/6, including
  `toobj` overflow saturation and scalar index read/write `E_TYPE` rows.
- The resumed full stock profile passed the complete object-authority family
  and stopped at 9,222 passed, 2,187 skipped (89%) on
  `language/parser_resource_limits.yaml::parser_nesting_resource_limit`.
  The row builds a stored-verb body containing 2,560 nested parentheses and
  calls `set_verb_code`; Banteng returned no value and closed the conformance
  socket, so cleanup encountered `BrokenPipeError`.
- The exact ownership boundary is established. `BuiltinCatalog.setVerbCode`
  already catches `IllegalArgumentException` from parse/compile and returns a
  diagnostic list, which is the row's required type. `MooParser` recursively
  calls `parseExpression` through `parsePrefix` for every parenthesis without
  an explicit depth bound, allowing host `StackOverflowError` to bypass that
  MOO-level diagnostic path. This is parser resource isolation, not a missing
  Mongoose fixture, number promotion, SQLite behavior, or conformance edit.
- Pinned WSL Toast passes the unchanged parser-resource family 2/2. Its Bison
  parser starts with `YYINITDEPTH=200`, grows its parser stacks, and caps them
  at `YYMAXDEPTH=10000`; it reports `memory exhausted` instead of exhausting
  the C call stack. Banteng needs the analogous explicit failure boundary for
  its recursive-descent implementation, at a Java-safe depth.
- Current blocker: no Banteng parser-depth regression or production bound has
  been added yet. The server-fatal host-stack path remains active.
- Next action: add a focused parser regression using the exact 2,560-level
  generated source and prove the current `StackOverflowError`; add an explicit
  expression nesting counter that throws `MooParser.ParseException` before the
  host stack is endangered; run parser tests and the unchanged parser-resource
  family, then resume full stock beyond 9,222.

## Parser host-stack failure confirmed and bounded

- The first ordinary-thread Java regression refined the evidence: the current
  parser completed the 2,560-parenthesis input under the Gradle test worker, so
  a server-safe failure could not be justified solely from that context.
  Reproduced the unchanged managed row under `--pdb` to preserve its ephemeral
  server directory and inspected the actual 60 KB server log before teardown.
- The log begins with `Exception in thread "" java.lang.StackOverflowError`
  and then alternates `MooParser.parseExpression` and
  `MooParser.parsePrefix`; the top live frame is lexer advancement. This proves
  the production scheduler platform worker exhausts its host stack while
  parsing the supplied verb, and explains why its task/connection completion
  disappears and the harness sees `BrokenPipeError`.
- Added parser-owned `MAX_EXPRESSION_DEPTH = 256` accounting around every
  `parseExpression` call. The counter is restored in `finally`, so successful
  subexpressions and ordinary parse failures cannot contaminate later work in
  the same parser instance. Crossing the boundary throws the existing
  source-located `MooParser.ParseException` with `expression nesting limit
  exceeded`; `set_verb_code` already converts that exception to a diagnostic
  list.
- Added the exact 2,560-parenthesis regression and a lower-bound assertion:
  255 nested parentheses plus the root expression (256 recursive expression
  frames) still parse, while the conformance depth is rejected before host
  exhaustion. The focused regression and the complete `MooParserTest` class
  pass under WSL Java 25, and the distribution was rebuilt.
- Current blocker: the unchanged managed
  `language/parser_resource_limits.yaml` family has not yet been rerun against
  the rebuilt distribution. The Java owner tests are green but do not alone
  prove socket/server liveness.
- Next action: run the unchanged resource family 2/2. If green, resume full
  stock fail-fast beyond 9,222 and repair its next concrete boundary.

## Parser resource family green; stock reaches planned sort work at 9,228

- The rebuilt unchanged `language/parser_resource_limits.yaml` family passes
  2/2. The deep-source call returns a list and the subsequent `1 + 1` succeeds
  over the same managed transport, proving both diagnostic routing and server
  liveness.
- Full stock then crossed that row plus the remaining object/parser neighbors
  and stopped at 9,228 passed, 2,187 skipped on
  `language/promote_numbers.yaml::sort_rejects_mixed_integer_and_float_values`.
  Expected `E_TYPE`; Banteng returned `E_VERBNF` because `sort` was absent.
- This row does not request numeric promotion and the stock profile still has
  `option.PROMOTE_NUMBERS=false`. It asserts Toast's no-promotion behavior by
  requiring `sort({2.0, 1})` to reject mixed key types. The same suite's list
  membership and map-key identity rows already pass.
- The implementation plan independently names unconditional `sort` directly
  after `all_members` as the next `SUSPENDING_HOST` builtin. Toast registers
  `sort(LIST [, LIST [, INT [, INT]]])`; a nonempty second list supplies keys,
  length mismatch is `E_INVARG`, keys must share one scalar type, list/map/
  anonymous/WAIF keys are `E_TYPE`, strings optionally use `strnatcasecmp`, and
  the fourth argument reverses the sorted index order.
- Added a focused catalog regression (currently red at absent `sort`) covering
  the manifest contract, enabled-thread host work, disabled-thread synchronous
  work, ascending/reverse integer order, sort-by keys, natural string order,
  key-length mismatch, mixed INT/FLOAT rejection, and WAIF rejection. Added
  `sort` to the catalog reachability set.
- Added production registration using the existing `BuiltinHandler` dual-mode
  pattern and existing `Result.hostWork`; no scheduler or executor was added.
  The callback sorts indices so a key list reorders the original value list,
  checks interruption for task cancellation, validates homogeneous Toast key
  types, preserves floating signed-zero equivalence, ports Toast's C-string
  case-insensitive comparison, and ports its `strnatcasecmp` digit-run rules.
- Current blocker: the new sort code and regression have not yet been compiled
  or run. Generated sort signature/call-shape suites were previously skipped
  while the builtin was absent and will become active once it is registered.
- Next action: run the focused catalog regression, repair compile/semantic
  defects, then run unchanged `sort_call_shapes`, generated `sort`,
  `promote_numbers`, and WAIF authority families before resuming full stock.

## Sort contract green; WAIF authority exposes a post-suspension error boundary

- The first focused compile failed only because Error Prone rejected reference
  identity as the marker for selecting a separate key list. Replaced it with an
  explicit `usingSeparateKeys` boolean; no value-equality behavior changed.
- The focused `BuiltinCatalogTest` sort contract now passes under WSL Java 25,
  and the production distribution rebuilt successfully. This proves the
  manifest call shape/effect/owner, synchronous and host-work modes, ascending
  and reverse integer order, keyed sorting, natural string ordering, mismatched
  key length `E_INVARG`, mixed INT/FLOAT `E_TYPE`, and WAIF `E_TYPE` at the
  callback boundary.
- The unchanged four-family managed run activated 28 collected rows. It passed
  admission plus all four `sort_call_shapes` rows, all seven generated sort
  signature/error rows, all three no-promotion rows, and the first three WAIF
  authority rows: 18 conformance rows passed before the next failure.
- The failing unchanged row is
  `waif_relational_comparisons_use_default_zero_and_sort_rejects_values`. Its
  MOO source catches `sort({first, second})` with `except error (ANY)`, then
  evaluates four WAIF relational comparisons, recycles the class, and returns
  `{0, 1, 0, 1, E_TYPE}`. Banteng instead returns an uncaught `E_TYPE` for the
  whole statement.
- The sort callback itself returns the intended `Result.error(E_TYPE)`, and the
  scheduler already resumes host-work errors through
  `MooVm.resumeWithError`; existing VM and scheduler regressions cover captured
  handler routing for other host completions. Therefore the current evidence
  does not yet establish that sort's error escaped its handler: a later WAIF
  comparison or recycle operation could be the uncaught `E_TYPE`.
- Current blocker: identify the exact expression emitting the uncaught error.
  Do not change the conformance row, make sort synchronous, or weaken its WAIF
  rejection merely to clear the result.
- Next action: add/run a focused managed scheduler regression using the exact
  statement and progressively return after the caught sort error, relational
  comparisons, and recycle. Repair the proven owner, rerun the focused Java
  test and the unchanged four-family cluster, then resume full stock.

## Sort suspension exonerated; WAIF primitive gaps admitted behind it

- Added a production scheduler regression using the exact WAIF-authority
  statement and split it into caught-sort, relational-only, and combined
  executions. The caught-sort execution returns `E_TYPE` normally, proving the
  existing host-work wake path and captured exception handler are correct.
- The standalone relational execution instead raised the uncaught `E_TYPE`.
  `MooVm.comparison` implemented Toast's default-zero comparison for two
  booleans but omitted the same rule for two WAIF values. Added only the
  same-type WAIF branch; mixed types still take the ordinary `E_TYPE` path and
  sort still rejects WAIF keys.
- The focused scheduler regression now passes, including the exact combined
  continuation `{0, 1, 0, 1, E_TYPE}`, and the distribution rebuilt under WSL
  Java 25.
- The unchanged four-family cluster then passed admission, every sort call
  shape/signature/error row, every no-promotion row, the WAIF relational row,
  and the next four WAIF rows: 23 conformance rows passed before the next
  independent failure.
- The new boundary is
  `waif_rejects_direct_and_collection_recursion`. Banteng returned no value for
  a statement that assigns the WAIF itself, `{waif}`, and
  `["self" -> waif]` to one WAIF property under three catch-all handlers; Toast
  requires `E_RECMOVE` for each assignment.
- Banteng's `MooVm.setProperty` checks permissions and calls
  `WorldTxn.writeWaifProperty` directly. The transaction method replaces the
  property override without inspecting the value graph. Pinned Toast's
  `waif_put_prop` explicitly constructs the current WAIF identity, calls
  `refers_to(val, me, true)`, and returns `E_RECMOVE` before storing when the
  value graph refers to that WAIF.
- Current blocker: port the recursive value-reference predicate with identity
  semantics and a host-safe cycle guard, then place it at the WAIF property
  assignment boundary before mutation. Direct, list, and map containment must
  be covered without accidentally treating merely equal scalar values as the
  same WAIF.
- Next action: inspect Toast `refers_to` collection traversal details, add a
  focused WAIF property regression for all three unchanged shapes, implement
  the narrow pre-write guard, run owner tests and the unchanged WAIF/sort
  cluster, then resume full stock.

## Sort and newly admitted WAIF authority cluster green 28/28

- Ported Toast's WAIF self-reference guard at the property-write boundary.
  `WorldTxn.valueRefersToWaif` uses an iterative identity traversal with a
  visited set, follows nested WAIF property bodies, list elements, and map keys
  and values, and is called only after the target property exists and write
  permission succeeds. Direct, list-contained, and map-contained self
  references now raise catchable `E_RECMOVE` before mutation.
- Added a focused scheduler regression using the exact three conformance
  assignments. It and the prior caught-sort/WAIF-relational regression pass
  against the rebuilt distribution.
- The unchanged cluster next exposed absent WAIF dictionary dispatch:
  `waif[key]` and `waif[key] = value` took Banteng's generic collection
  `E_TYPE` path instead of Toast's `:_index` and `:_set_index` class verbs.
- Added narrow WAIF branches to simple index read and local indexed assignment.
  They require a live class (`E_INVIND` otherwise), require the class owner to
  be a wizard (`E_TYPE` otherwise), map a missing handler to `E_TYPE`, compile
  and enter the inherited handler through the existing verb-frame machinery,
  preserve the WAIF as `this`, and pass `{key}` or `{key, value}` as `args`.
  No new dispatch engine or compatibility surface was added.
- Added an end-to-end scheduler regression for the exact wizard-owned handler
  setup and result. The three focused scheduler regressions pass under WSL Java
  25, and the complete unchanged WAIF authority family passes 14/14, including
  missing-handler, nonwizard-owner, and recycled-class error cases.
- The combined unchanged sort call-shape, generated sort, no-promotion, and
  WAIF authority cluster passes all 28 collected rows. The stock profile still
  has numeric promotion disabled; this work did not add promotion behavior.
- Current blocker: none within the sort/WAIF cluster. The full stock suite has
  not yet been resumed beyond its prior 9,228 boundary.
- Next action: run full stock fail-fast against the rebuilt distribution and
  repair the next concrete authoritative boundary, if any.

## Full stock crosses sort and WAIF work; equality builtin boundary at 9,328

- Full stock collected 12,762 items and crossed the previous 9,228 boundary,
  including parser resources, all three no-promotion rows, the new sort row,
  and the newly admitted WAIF authority family. The repaired region remained
  green in the complete managed ordering.
- The run stopped after 503.17 seconds with 9,328 passed and 2,176 skipped at
  `language/toast_equality_matrix.yaml::equality_int_zero__bool_false`.
- The unchanged row expected `{1, 0, 1, 1, 1}` but Banteng returned
  `{1, 0, 1, 0, 0}`. The first three values demonstrate that operator `==`,
  operator `!=`, and symmetry already treat integer zero and boolean false as
  Toast requires. Only the final two probes disagree, so the current evidence
  points to the `equal()` builtin's case-mode paths rather than VM operator
  equality or numeric promotion.
- Current blocker: inspect the exact generated statement and the shared
  recursive equality helper to identify why `equal(0, false, ...)` diverges
  from operator equality. Preserve string case-mode behavior and recursive
  list/map semantics while aligning the scalar BOOL/INT relation.
- Next action: add a focused builtin-backed regression for the exact five-value
  row, repair the narrow equality owner, run its full authoritative matrix or
  relevant family, rebuild, and resume stock beyond 9,328.

## Toast equality matrix green 352/352

- Pinned Toast's `equality()` implementation confirms BOOL/INT equivalence is
  part of the same recursive equality relation used by `bf_equal`, in both
  operand orders. `bf_equal` supplies case-sensitive string mode; it does not
  use a separate scalar relation.
- Added the two missing symmetric BOOL/INT branches to Banteng's existing
  `exactlyEqual` helper before its string/list/map handling. Operator equality,
  arithmetic, numeric promotion, and map-key identity were not changed.
- Added a focused catalog regression covering `0`/false, true/`1`, a mismatch,
  nested list recursion, and preservation of case-sensitive string inequality.
  The regression passes under WSL Java 25 and rebuilt the distribution.
- The complete unchanged source-derived Toast equality matrix now passes all
  352 collected rows, including scalar symmetry, signed float zero, recursive
  list/map values, and case-sensitive `equal()` versus case-insensitive
  operator equality.
- Current blocker: none in equality. Full stock has not yet been resumed beyond
  its 9,328 boundary against this rebuilt distribution.
- Next action: rerun full stock fail-fast and repair the next authoritative row,
  if one remains.

## Full stock crosses equality; legacy WAIF invalidity is the next boundary

- A fresh complete stock rerun collected 12,762 cases and preserved every
  prior repaired boundary, including SQLite, sort, the source-derived equality
  matrix, and the newly admitted WAIF authority family.
- Exact fail-fast result: 10,050 passed, 2,436 declared skips, one failed in
  516.28 seconds. The unchanged failing row is
  `language/waif.yaml::waifs_are_invalid` at 97% collection progress.
- The row expects successful value `0`; Banteng instead raises uncaught
  `E_TYPE`. This is the first post-equality stock mismatch and is unrelated to
  SQLite, Mongoose, or numeric promotion.
- Current blocker: identify the exact legacy WAIF operation in the unchanged
  row and pin Toast's invalid/recycled WAIF result and error boundary before
  changing production code.
- Next action: inspect the row, reproduce its individual expressions through
  the managed server, inspect pinned Toast WAIF validity handling, add a narrow
  regression at the proven owner, rebuild, rerun the unchanged WAIF file, and
  then resume stock beyond 10,050.

## Legacy WAIF validity and hierarchy boundary repaired; class invalidation next

- Pinned Toast `bf_valid`: it accepts object-flavored OBJ/ANON/WAIF values and
  returns zero for an ordinary WAIF. Banteng's handler accepted `ANY` at the
  manifest but omitted WAIF and raised `E_TYPE`. Added the missing WAIF-zero
  branch and a focused catalog assertion; scalar non-object errors remain
  unchanged.
- Pinned Toast `bf_parent`, `bf_parents`, and `bf_children`: WAIF passes the
  object-flavored type boundary, then fails object validity with `E_INVARG`.
  Added only those three WAIF branches plus focused coverage. The two focused
  catalog methods pass and the distribution rebuilt under WSL Java 25.
- The unchanged 38-row `language/waif.yaml` file now passes its first 18 rows.
  Its next failure is `recycling_parent_invalidates_waif`: expected
  `w.class == #-1` after recycling the class, actual zero.
- Toast invalidates the WAIF by setting its mutable `_class` to `NOTHING` when
  access discovers the class object no longer exists. Banteng deliberately
  represents WAIF identity with immutable class/owner references and stores
  instance bodies in the transactional world map. `MooVm.getProperty` currently
  returns the identity's historical class unconditionally, even after the
  class has disappeared from the transaction.
- Current blocker: preserve immutable/reference identity while making the
  observable `class` property return `#-1` when its class object is absent.
  Do not rewrite equality, replace WAIF identities, or introduce mutable world
  values merely for this lifecycle result.
- Next action: add an end-to-end VM regression that creates a WAIF, recycles its
  class, and reads `waif.class`; make that getter consult `WorldTxn.object`, run
  focused Java plus unchanged WAIF conformance, and inspect the next admitted
  row.

## WAIF lifecycle boundary repaired; missing `task_stack` is the admitted gap

- Existing WAIF identities remain immutable. `MooVm.getProperty` now consults
  the active transaction before returning the historical class and reports
  `#-1` when that object has been recycled. A focused end-to-end VM regression
  passes; no equality, identity, or world-value mutability was introduced.
- Pinned Toast `bf_new_waif` validates the caller first, then returns
  `E_INVARG` for a live anonymous receiver. Banteng now preserves that order:
  live ANON is `E_INVARG`; missing/non-object callers remain `E_INVIND`. Its
  focused VM regression passes and the distribution rebuilt.
- The unchanged `language/waif.yaml` file now passes 20 rows before
  `task_stack_returns_valid_waifs_for_owners`, which raises `E_VERBNF`.
  `task_stack` is absent from Banteng's manifest and production catalog; this
  is not a failed WAIF verb dispatch.
- Pinned Toast registers `task_stack(INT [, ANY [, ANY]])`. It rejects a
  missing/current task with `E_INVARG`, allows the task programmer or a wizard,
  and returns current-to-root activation rows: receiver, verb name, programmer,
  verb location, player; optional source line; optional locals map.
- Banteng already retains every required field in `VmSnapshot.Frame` at timed
  and host suspension. `TaskRegistry` currently flattens only the active frame
  into `TaskInfo` for `queued_tasks`; it discards the snapshot reference even
  though the scheduler still owns that exact durable value.
- Current blocker: expose the existing suspended snapshot through the one
  scheduler-owned `TaskRegistry` without adding a scheduler, executor, VM
  format, or task interface.
- Next action: retain `Optional<VmSnapshot>` on registry rows, store child
  snapshots at fork registration and full snapshots at suspension/host wait,
  implement owner-or-wizard `taskStack`, register the exact manifest contract,
  add registry/catalog coverage, then run all unchanged task-stack suites and
  resume the WAIF file.

## `task_stack` locally green; no-argument suspension semantics exposed

- Added `task_stack(INT [, ANY [, ANY]])` as a TASK-owned irrevocable registry
  read and wired the production runtime's existing `TaskRegistry` handler into
  the catalog. The reachable-name inventory includes the exact new name.
- `TaskRegistry` now retains the existing `VmSnapshot` for registered forks,
  timed/host suspension, and restored tasks. It renders current-to-root frame
  rows with receiver, verb name, programmer, verb location, player, optional
  source line, and optional locals map; it enforces task-programmer or wizard
  access and rejects missing/current tasks with `E_INVARG`.
- Focused registry/catalog tests and the rebuilt distribution pass. The
  unchanged combined signature/call-shape/task-management/ANON/WAIF run passed
  81 rows (157 declared skips), including the formerly failing WAIF
  `task_stack` row, before the next WAIF queue row.
- An initial live-suspension update incorrectly inserted the foreground parent
  into `queued_tasks`; corrected it so live snapshots update only tasks already
  registered as background forks, while explicit restored-task insertion stays
  separate. A local isolation assertion prevents reintroducing that shared
  state contamination.
- The next row then reports only the delayed `:c` fork; the zero-delay child
  reaches `:a`, calls `suspend()` with no argument, immediately resumes, and is
  removed before observation. Pinned Toast `bf_suspend` passes a null delay to
  `enqueue_suspended_task`, which assigns `INTNUM_MAX`; `suspend(0)` passes a
  real zero delay and merely yields. Banteng currently initializes missing
  arguments to numeric zero and aliases these two contracts.
- Current blocker: represent an indefinite suspended wake distinctly from a
  timed zero delay through `BuiltinCatalog.Result`, `VmState`/`VmSnapshot`, the
  registry, scheduler resume/kill path, and durable export without inventing a
  timer shortcut or a second task owner.
- Next action: inspect the existing `resume`/timed-work/durable-task ownership,
  add focused scheduler tests for `suspend()` remaining queued and
  `suspend(0)` yielding, implement the smallest explicit indefinite wake shape,
  run task/codec gates, then rerun the unchanged task-stack/WAIF cluster.

## Indefinite suspension retained; Toast queue ordering pinned

- No-argument `suspend()` now remains registered at Toast's `INTNUM_MAX`
  scheduled-time boundary, has no timer thread, is killable, and is distinct
  from the ordinary `suspend(0)` yield. Focused scheduler, registry, and queued
  task codec tests pass after rebuilding the distribution.
- The unchanged WAIF row then reports both expected tasks with correct metadata,
  but in task-ID order: `:a` before `:c`. Toast expects `:c` before `:a`.
- Pinned `enqueue_waiting` and `bf_queued_tasks` in WSL Toast. Waiting tasks are
  inserted by ascending `start_tv`; equal start times retain FIFO insertion
  order. External waits are enumerated after waiting tasks. Indefinite suspend
  uses `INTNUM_MAX`, so it sorts after the finite 100-second fork.
- The precise defect is `TaskRegistry.tasks` being a `TreeMap` whose iteration
  order leaks task IDs into `queued_tasks()`. The registry has the correct
  scheduled values already; only its observable ordering is wrong.
- Current action: sort visible waiting rows by scheduled start then stable queue
  insertion sequence, keep external host waits after them, and rerun focused
  Java plus the unchanged task-stack/WAIF conformance cluster.

## Toast queue order and WAIF introspection green; live WAIF finalization exposed

- `TaskRegistry` now orders observable waiting rows by scheduled start and then
  stable insertion sequence, with external host waits after waiting tasks. A
  direct regression makes task-ID order disagree with wake-time order, and the
  live WAIF fork regression requires `:c` before indefinite `:a`; both pass.
- The unchanged combined conformance selection confirms the formerly failing
  `queued_tasks_returns_valid_waifs_for_programmers` row passes. It next exposed
  `properties(waif)`: Toast accepts WAIF as object-flavored, then rejects its
  invalid database identity with `E_INVARG`.
- Added the same narrow Toast validation boundary to `properties` and
  `property_info`; `is_clear_property` already returned `E_INVARG`. Focused
  coverage preserves scalar `properties(0)` as `E_TYPE`. All three unchanged
  WAIF introspection rows now pass.
- `language/waif.yaml` now passes 36 of 38 rows before
  `waif_lost_reference_calls_recycle`: the class `:recycle` hook is not run at
  the task-completion observation boundary (`recycle_called` remains zero).
- Banteng already detects unreachable WAIF components, persists them in
  `pendingFinalization`, has a `WAIF_FINALIZATION_RETURN` continuation, and
  removes the whole component after its hook. The missing link is that ordinary
  task publication calls `finalizePendingAnonymousObjects`, which preserves
  pending WAIF roots; only server startup drains them through
  `continueStartupAfterPendingWaifFinalization`.
- Current blocker: execute already-queued WAIF finalizers as ordinary spawned
  publication steps without accidentally entering the startup continuation or
  recollecting a finalizer's own `this` value. The following row additionally
  requires exactly-once removal even when `:recycle` stashes `this` in a class
  property.
- Next action: pin the scheduler continuation/collection exclusion, add a
  runtime regression for ordinary completion plus self-stashing exactly once,
  route pending WAIF roots through a non-startup finalization transition, then
  rerun the unchanged 38-row WAIF suite.

## Live WAIF finalization green; checkpoint ordering regression at 78 percent

- Ordinary publication now gives pending WAIF roots their own non-startup
  finalization continuation plus a durable in-progress marker. The marker is
  recovered to its raw root on boot, and completion removes the entire WAIF
  component plus raw/marked pending entries.
- Added an end-to-end scheduler regression: a lost WAIF runs `:recycle`, the
  hook stashes `this`, the body is still removed, and clearing the stored
  identity does not invoke the hook again. It passes. The unchanged
  `language/waif.yaml` suite is fully green at 38/38.
- Full stock then reached 78 percent with 8,287 passed and 1,766 declared skips
  before `fixture_provenance/pending_waif_nested_topology.yaml`. The recipe
  checkpoint writes 2,555 bytes and `0 values pending finalization`; canonical
  Toast is 2,593 bytes with one parent WAIF root containing its child.
- This is a regression in the first live-finalizer connection, not a fixture
  defect. The recipe drops parent/child locals and calls `shutdown()` in the
  same startup task. Toast checkpoints the newly discovered pending component
  before running finalizers; Banteng currently queues and drains it during
  completion publication before the checkpoint effect is published.
- The existing production persistence gates confirm the split: live WAIF and
  pending-recycle-startup tests pass, while
  `nestedWaifRecipeProducesTheOneCanonicalPendingRoot` fails byte length
  2,555 versus 2,593. The conformance file and fixture remain unchanged.
- A first guard checking for an already-staged `CHECKPOINT` effect was
  insufficient. `authorizePendingBuiltin` returns the VM to `RUNNING`, applies
  the checkpoint request, and `publishVmState` can collect before or while the
  request is drained; the precise intra-attempt order still needs pinning.
- Current blocker: preserve Toast's synchronous shutdown/checkpoint boundary
  without delaying ordinary lost-reference WAIF hooks or changing startup
  finalization.
- Next action: inspect the two publication calls around irrevocable builtin
  authorization, retain checkpoint intent on `VmState` until completion (or
  otherwise make it visible before collection), prove the canonical nested
  fixture and live exactly-once regression together, then rerun provenance and
  resume full stock.

## WAIF checkpoint ordering green; ANON task-authority boundary admitted

- A temporary one-test trace showed the exact shutdown ordering: the VM reaches
  `RETURNED` with one checkpoint request still staged on `VmState`, while the
  attempt effect list is still empty. The trace was removed.
- `publishVmState` now drains checkpoint intent before reachability collection
  and treats either newly drained intent or an existing checkpoint effect as a
  checkpoint boundary. Newly unreachable WAIFs remain raw pending roots for
  that checkpoint instead of being finalized first.
- The production nested-WAIF byte-match, pending-recycle startup, and live
  self-stashing exactly-once tests pass together. The unchanged nested/recycle
  provenance, startup lifecycle, and WAIF suite pass 41/41.
- Running the remaining language/object/server/VM suffix next admits
  `language/anonymous_task_authority.yaml`. Its first active row creates an
  ANON stored in a permanent class property, then a later command invokes an
  inherited verb. Banteng raises `E_PROPNF`; Toast expects the live ANON to be
  preserved for its owner and wizard before later redaction.
- The error is likely inside the invalid ANON receiver (`this.observer`) rather
  than the permanent `class.anon` lookup. Added a focused two-command scheduler
  regression to distinguish body validity and inherited property access.
- The regression's initial setup currently fails with `E_PERM` because it
  changes task permissions to `#3` before attempting to add a diagnostic root
  property on `#0`; this is a test-construction mistake, not production
  evidence. The unchanged conformance failure remains the authority.
- Current blocker: correct the focused setup without weakening permissions,
  then determine why the world-root traversal does not retain the ANON body
  stored in a permanent property across task publication.
- Next action: keep the diagnostic root creation under wizard permissions,
  switch to `#3` only for the conformance-equivalent object/ANON setup and
  observation, run the focused red test, inspect pending/reachable identity
  state, then make the smallest collector or value-view repair and rerun the
  unchanged anonymous task-authority suite.

## ANON task failure localized to intrinsic owner mutation

- Corrected the focused regression to the fixture's real identities (Wizard
  `#3`, Programmer `#4`) and then to the harness's actual initial player via
  `switch_player(player, #4)`. A permanent property keeps the ANON body live
  across commands, inherited `this.observer` works, and the exact owner-side
  nested `callers()` result is `{12, 1, "*anonymous*"}`.
- Rerunning the unchanged authority file alone remains deterministic, but the
  traceback expectation reveals the failed step was misidentified: owner and
  wizard `callers()` observations pass. The later wizard step expecting scalar
  `1` fails on `class.anon.owner = player` with `E_PROPNF`.
- `MooVm.getProperty`/`setProperty` route ANON through ordinary inherited
  properties. `WorldTxn.writeObjectProperty(AnonymousObjectValue, ...)`
  special-cases only intrinsic `name`; it has no intrinsic `owner` branch, so
  owner assignment falls through to inherited-property lookup and returns
  false/`E_PROPNF`.
- Toast and the checked-in ANON authority require wizard owner reassignment so
  subsequent task-frame views can distinguish owner visibility from redacted
  other-viewer visibility. This is an intrinsic ANON object mutation, not a
  new property definition and not collector behavior.
- Current blocker: implement ANON `owner` read/write with Toast permission and
  value-validation order while keeping structural property restrictions and
  WAIF immutable-owner behavior unchanged.
- Next action: pin Toast's ANON owner opcode path, add focused VM assertions for
  read, wizard write, non-wizard denial, and invalid owner value/error, extend
  `WorldTxn` with tracked immutable-body replacement, then rerun the unchanged
  anonymous task-authority file.

## ANON owner mutation green locally; task-view redaction implemented

- Pinned Toast `OP_PUT_PROP`: ANON `owner` requires an OBJ RHS (`E_TYPE`
  first), then wizard permission (`E_PERM` otherwise), and accepts the object
  identity without a validity check. ANON `programmer`/`wizard` writes are
  `E_PERM` for non-wizards and `E_INVARG` for wizards.
- Added intrinsic ANON owner read/write in `MooVm` and tracked immutable-body
  replacement in `WorldTxn`. Focused VM coverage passes for wizard mutation,
  persisted readback, non-wizard denial, wrong RHS type, and flag-write error
  precedence. WAIF owner behavior remains untouched.
- The unchanged anonymous task authority then advances to its intended final
  assertion: after Wizard changes ANON ownership, the former owner sees
  `{12, 1, "*anonymous*"}` but Toast expects the same type/literal with
  `valid == 0`. Owner and wizard views pass.
- Pinned Toast `anonymizing_var_ref` and its callers. `callers`, `task_stack`,
  and `queued_tasks` project only activation `this` and verb location. Wizard
  or current ANON owner receives the live value; every other viewer receives an
  invalid null-style ANON. Variables maps are deliberately not projected.
- Implemented boundary projection with a fresh ANON identity absent from the
  world, preserving type 12 and `*anonymous*` while making `valid` false.
- The first combined local run exposed a test-visible implementation typo:
  `TaskInfo.row(boolean, world, programmer)` shadows the record's task
  `programmer` component, so queued rows now report the viewer (`#1`) in field
  5 instead of the task programmer (`#2`). This is not a Toast ambiguity.
- Current blocker: remove that parameter shadowing, rerun registry/VM/live
  focused tests, then validate all three ANON task projection surfaces in the
  unchanged authority suite.
- Next action: rename the row parameter to `viewerProgrammer`, preserve the
  task's programmer field verbatim, rebuild, and rerun the authority fail-fast.

## ANON task authority green; traceback projection corrected

- Removed the `TaskInfo.row` parameter shadowing so queued task rows retain the
  task programmer while using the separate viewer identity only for ANON
  projection. Focused VM, scheduler, and registry coverage passes.
- The unchanged `language/anonymous_task_authority.yaml` first two rows then
  pass for `callers`, suspended `task_stack`, and `queued_tasks`, including
  owner, wizard, and other-viewer projections.
- The remaining failure was caught-error traceback construction: Banteng kept
  the live ANON receiver for its owner, while Toast expects an invalid typed
  `*anonymous*` placeholder for every viewer, including owners and wizards.
- Toast's `raise_error()` builds exception stack lists with programmer
  `NOTHING`; its source explicitly distinguishes this from viewer-sensitive
  task inspection. Both traceback `this` and verb location are therefore
  always anonymized.
- `MooVm.tracebackFrame` now replaces ANON receiver and verb-location values
  with invalid identities without mutating the frame or world. A focused VM
  regression passes under WSL Java 25.
- The unchanged ANON task-authority suite is green 4/4. The broader
  `language/anonymous.yaml` remains profile-gated at 1 pass and 56 declared
  skips; no capability declarations were changed.
- Next action: rerun the full stock profile fail-fast to identify the next
  admitted contract gap after ANON task authority.

## Full stock reaches 98%; MAP restart reinsertion green

- The rebuilt full stock profile crossed the repaired ANON authority boundary
  and reached 98% before failing at
  `server/map_dump_persistence.yaml::ordering_spelling_bytes_and_non_total_topology_round_trip`:
  10,193 passed and 2,436 declared skips preceded the failure.
- Total mixed-key order, case-replacement spelling, high-byte strings, and all
  persisted mappings were intact. Only the expected BOOL/WAIF/ANON non-total
  key-topology transformation was absent; traversal and literal state remained
  identical to the pre-dump baseline.
- Toast writes MAP pairs through `mapforeach()` comparator traversal and reads
  them by repeated ordinary `mapinsert()`. Banteng's writer already traversed
  `MapValue.entries()`, but the reader collected pairs in a `LinkedHashMap` and
  constructed a `MapValue` in one shot, bypassing normal reinsertion.
- The v17 tag-10 reader now starts with an empty `MapValue` and applies
  `with(key, value)` for each decoded pair, retaining duplicate-key validation.
  The pre-existing focused `reinsertsNonTotalMapKeysAcrossV17Read` regression
  and unchanged managed MAP persistence suite both pass (2/2 conformance).

## Next post-MAP blocker: panic startup task raises E_INVARG

- Running the ordered server/VM paths after MAP fail-fast reaches
  `startup_panic_live_waif_anon_roots.yaml` next. The server does not exit
  within the required five seconds; the failure reproduces when that file runs
  alone, so it is not contamination from preceding suites.
- An isolated direct launch logs `ORACLE LIVE ROOT HOLDER SUSPENDED`, followed
  by `#0:server_started (this == #0), line 11: Invalid argument` and an end of
  traceback. The delayed panic fork therefore never reaches the expected
  `shutdown("ORACLE PANIC WITH LIVE ROOTS", 1)` termination.
- The fixture's `server_started` creates live WAIF/ANON task roots, forks an
  immediate holder that suspends for 60 seconds, forks a one-second panic task,
  and finally calls `suspend(0)` in the startup task. Exact source-line mapping
  and suspend-zero handling still need to be distinguished.
- A diagnostic PowerShell interpolation mistake placed copied diagnostic files
  at explicit WSL-root paths `/PanicLiveWaifAnonRoots.db` and `/server.log`;
  both are regular files created by this run and must be removed safely before
  further diagnosis.
- Current blocker: determine which `server_started` operation raises E_INVARG
  and why that error prevents the already-forked delayed shutdown task from
  running.
- Next action: validate and remove only the two diagnostic root files, inspect
  suspend/fork scheduling and source-line behavior, add an exact startup
  regression, then rerun the unchanged panic-live-root suite.

## Panic blocker pinned to shutdown second-argument semantics

- Validated both accidental WSL-root diagnostic artifacts as exact regular
  files with the expected sizes, removed only `/PanicLiveWaifAnonRoots.db` and
  `/server.log`, and verified their absence.
- The failure is not `suspend(0)`. Banteng's `shutdown` builtin explicitly
  returns `E_INVARG` when its optional second argument is truthy; that is the
  fixture's `shutdown("ORACLE PANIC WITH LIVE ROOTS", 1)` line.
- Pinned Toast `bf_shutdown`: a non-wizard gets `E_PERM`; a truthy second
  argument calls `panic_moo(message)`. `panic_moo` logs `PANIC: <message>`,
  shuts down networking, writes a `DUMP_PANIC` database at the configured dump
  name plus `.PANIC`, and terminates via `abort()`/SIGABRT. A false or omitted
  second argument remains the ordinary clean-shutdown path.
- The conformance runner accepts only a direct POSIX SIGABRT status for its
  portable `termination: abort` expectation; an ordinary nonzero Java exit is
  insufficient.
- Panic persistence must also avoid ordinary reachability promotion. The
  fixture expects zero pending-finalization roots while one suspended task
  durably owns the live WAIF and ANON. Banteng currently runs unreachable-value
  queueing even at a checkpoint boundary, so the panic request must suppress
  that collection rather than manufacture pending roots.
- Current blocker: carry a distinct panic request through the VM publication
  boundary, write `<checkpoint>.PANIC` from the committed world plus durable
  tasks, and invoke a production SIGABRT only after the write completes.
- Next action: extend the value-only checkpoint request/effect model with panic
  mode and message, add a recordable listener panic capability for tests plus
  a lazy native `abort()` implementation in `MooServer`, add focused builtin
  and runtime persistence tests, rebuild, and rerun the unchanged startup panic
  suite.

## Panic semantics implemented; JVM core generation exceeds exit deadline

- Added a distinct value-only panic checkpoint request carrying its message.
  Truthy `shutdown(message, flag)` now selects panic; false/omitted remains the
  clean checkpoint-and-shutdown path. Panic bypasses checkpoint lifecycle MOO
  hooks as Toast does.
- Runtime publication suppresses ordinary unreachable-value promotion for a
  panic boundary, writes the committed world plus durable task inventory to
  `<checkpoint>.PANIC`, then calls the listener's panic capability. The normal
  checkpoint path is unchanged.
- `MooServer` implements panic with a lazily resolved libc `abort()` so unit
  test class loading remains portable. Recording listener implementations were
  extended without weakening the production interface.
- Focused builtin selection and runtime publication tests pass. The runtime
  test observes the panic file before the abort capability is invoked and
  reloads it with zero pending-finalization roots.
- A direct managed launch now logs both `ORACLE LIVE ROOT HOLDER SUSPENDED` and
  `PANIC: ORACLE PANIC WITH LIVE ROOTS`, emits a 3,151-byte
  `PanicLiveWaifAnonRoots.db.new.PANIC`, and terminates with SIGABRT. The
  unchanged conformance row still times out at five seconds.
- Timing separates the stages: port bind is about 0.5 seconds after launch,
  live-holder suspension follows about 0.3 seconds later, the panic fork wakes
  exactly one second later, and the panic target file appears about 23 ms after
  the log. Process exit follows roughly 5.2 seconds later while Linux reports
  `core dumped` for the JVM.
- Current blocker: native `abort()` has the correct process status but HotSpot's
  large core generation pushes observable exit past the suite's five-second
  deadline; scheduling and persistence are no longer implicated.
- Next action: disable core-file generation immediately before the intentional
  panic abort (while retaining default SIGABRT termination), verify direct
  exit latency and return status, rerun focused tests and the unchanged panic
  suite, then continue the post-MAP server/VM tail.

## Panic envelope green; suspended WAIF local topology differs

- Replacing atomic checkpoint promotion with a dedicated Toast-style panic
  writer removed the directory-sync mismatch. Panic now opens the final
  `<checkpoint>.PANIC` target directly, writes and file-fsyncs it, and performs
  no temporary rename or directory fsync, matching Toast `DUMP_PANIC`.
- `strace` proved the file fsync takes about 3.5 ms and SIGABRT is delivered
  immediately. The remaining delayed reap was kernel core handling for the JVM;
  production panic now makes the process non-dumpable with
  `prctl(PR_SET_DUMPABLE, 0)`, resets SIGABRT to its default disposition, and
  raises it. The unchanged runner now recognizes abort within five seconds.
- The panic conformance row advances through exit, log, filename, zero pending
  roots, and one suspended-task count. Its only remaining failure is the WAIF
  encoding inside the suspended activation's `state` local.
- Banteng writes activation locals in retained map order: `waif` first creates
  WAIF save index 0, then `state` contains `r 0` plus anonymous `#10`. The
  parser therefore sees no inline class/owner in `state`, though lookup identity
  itself is preserved.
- A direct pinned-Toast panic dump gives the exact authority. Its activation
  environment has the standard predefined slots, then `class`, `state`, and
  `holder`; the temporary `ANON` and `WAIF` predefined slots are `TYPE_NONE`.
  Consequently `state` contains `c 0`, class `#9`, owner `#3`, and anonymous
  `#10`, exactly as the conformance assertion expects.
- Current blocker: Banteng snapshots retain temporary WAIF/ANON values after
  their final consumption in `state = {WAIF, ANON}`, whereas Toast's VM clears
  those moved-from slots before the suspension boundary.
- Next action: inspect Banteng suspended activation export and Toast bytecode
  local-move/liveness behavior, implement the narrow VM-state clearing rule at
  the correct execution boundary rather than reordering persistence output,
  add a focused snapshot/dump regression, and rerun the unchanged panic suite.

## Panic suite green; next startup WAIF boundary repaired pending validation

- Pinned Toast's panic output directly. The fork activation clears the
  overwritten predefined `WAIF` and `ANON` slots to `TYPE_NONE`; its retained
  `state` local therefore owns the inline WAIF creation record (`c 0`, class
  `#9`, owner `#3`) and anonymous `#10`.
- Added conservative moved-from handling only for overwritten predefined
  `waif`/`anon` slots on their final straight-line read. Any later read,
  index/range use, `eval`, fork, jump, handler, loop, or scatter keeps the slot.
  The focused suspended-local snapshot test passes.
- The unchanged `startup_panic_live_waif_anon_roots.yaml` is now green 2/2:
  timely SIGABRT, exact log, direct panic file, zero pending roots, one durable
  suspended task, and inline WAIF/ANON roots all pass.
- Continuing the post-panic tail exposed
  `startup_pending_waif_nested_topology.yaml`: Banteng emitted zero pending
  roots where the raw nested component must remain one top-level root.
- This was not caused by moved-local handling. Startup's pending-WAIF loop
  explicitly called `finishWaifFinalization()` when a root had no executable
  inherited `:recycle`, deleting that raw component before `server_started`.
  The adjacent lifecycle contract distinguishes raw roots from roots with
  executable recycle hooks.
- Startup now skips no-hook pending WAIF roots while still selecting and
  executing the first hook-bearing root. Production rebuild succeeds.
- Current blocker: validate that raw nested pending roots survive the clean
  shutdown checkpoint without regressing hook-driven pending recycle or the
  newly green panic path.
- Next action: run the unchanged panic, nested-pending, and pending-recycle
  startup files together; then continue the remaining startup-repair,
  stress/persistence, and VM tail fail-fast.

## Startup repair through Anon6 green; Anon7 is a readiness/timing boundary

- The unchanged panic, raw nested-pending-WAIF, and hook-driven pending recycle
  cases are green together. Startup no longer deletes a raw pending WAIF root
  merely because it has no executable inherited `:recycle` hook.
- Clean checkpoint publication no longer treats a completed checkpointing VM
  snapshot as a durable root. It queues unreachable anonymous values from the
  committed world and durable task inventory instead; `startup_repair_anon1`
  and the preceding startup cases are green together.
- The unchanged Anon2 through Anon6 startup-repair cases also pass.
- `startup_repair_anon7` first exposed one persistence-reader mismatch: its
  source fixture has an extra trailing blank line after the v17 program
  section. The decoder now accepts trailing empty lines while still rejecting
  any trailing nonempty data; the focused round-trip regression passes.
- With that fix, a direct launch produces `Anon7.db.new`. The unchanged suite
  can still inspect before the atomically promoted file becomes visible: it
  begins its 750 ms allowance as soon as a TCP connect succeeds, while Banteng
  binds the listening socket before startup lifecycle work completes.
- Current blocker: quantify the port-visible to checkpoint-visible interval and
  determine whether the defect is premature readiness, avoidable startup
  scheduling latency, or both. The conformance timeout and fixture remain
  unchanged.
- Next action: measure process start, port visibility, checkpoint visibility,
  and exit; verify the produced Anon7 task/root topology; then repair the
  product boundary supported by that evidence and rerun the unchanged row.

## Anon7 isolated to cross-task GC roots; repair implemented pending WSL gate

- Direct timing is `465 ms` from process start to port visibility and another
  `367 ms` from port visibility to checkpoint visibility. This run is within
  the suite's 750 ms post-port allowance, so premature readiness is not the
  primary failing contract.
- The produced checkpoint contains both expected suspended tasks and both
  `cross-vm recycle called` payloads, but reports `1 values pending
  finalization` instead of zero.
- The precise defect is that anonymous/WAIF reachability was calculated from
  the currently publishing VM plus permanent world properties only. It omitted
  snapshots belonging to every other durable task in the scheduler registry.
  Thus the parent task's `run_gc()` misclassified the anonymous cycle held by
  the two indefinitely suspended child VMs as unreachable.
- The in-progress repair exposes registry snapshots excluding the currently
  publishing task and supplies them as GC roots at every publication boundary.
  Excluding the current registry row preserves the Anon1 rule: a terminal
  checkpointing task's stale pre-execution snapshot must not become durable;
  the current live snapshot is supplied explicitly only while appropriate.
- Java compilation succeeds. The attempted Windows scheduler test class is not
  a semantic result: its runtime tests fail during `BuiltinCatalog` static
  initialization because Windows cannot open Linux-only `libcrypt.so.1`.
- Current blocker: run the focused scheduler tests under the authoritative WSL
  Java environment, rebuild `installDist`, and rerun unchanged Anon1 plus
  Anon7. If those pass, continue the stock server/VM tail.

## Cross-task GC green; stock server tail now requires v5 startup repair

- The new registry-root unit test passes under WSL. The broader scheduler class
  has only the pre-existing host-cancellation timing stress failure; all other
  rows pass.
- The unchanged Anon1 and Anon7 files pass together (3/3). This proves the
  collector now retains values owned by other suspended VMs without reviving a
  terminal checkpointing VM's stale locals.
- The unchanged server directory advanced to
  `startup_repair_broken1.yaml`: Banteng rejects its format-v5 header before
  startup. Formats v4 and v17 alone are insufficient for the stock suite.
- Toast source confirms v5 is `DBV_NextGen`: object topology fields are typed
  persisted values (`location`, `contents`, `parents`, `children`) rather than
  v4's linked scalar representation, but it predates v17 `last_move`, ANON,
  WAIF, and modern task preambles.
- All five stock `Broken*.db` fixtures are v5 and deliberately corrupt those
  typed topology fields. Pinned Toast reports three repair phases: invalid
  type/reference filtering, parent/location cycle removal, and reciprocal
  top-down/bottom-up consistency repair. Exact log evidence for Broken1 through
  Broken5 has been captured in WSL temporary diagnostics.
- Current blocker: implement the narrow v5 legacy reader plus real topology
  normalization and Toast-compatible repair diagnostics; reuse Banteng's
  existing world/property model rather than adding an ORM or unrelated storage
  layer.
- Next action: add focused v5 reader tests covering typed fields and repair,
  wire format-v5 dispatch, rebuild, and run all five unchanged broken-startup
  suites before resuming the server tail.

## v5 repair green; repeated server runs expose unstable early readiness

- Added format-v5 dispatch and a dedicated legacy reader. It reads the
  pre-`last_move` next-generation object layout, preserves v5 value/property
  records needed by the fixtures, performs real invalid-reference/type,
  parent/location-cycle, and reciprocal-topology repair, then constructs a
  `WorldTxn` that passes Banteng's topology validation.
- The focused reader test loads all five stock `Broken*.db` fixtures as valid
  worlds and checks representative repaired relations. All five unchanged
  broken-startup conformance suites pass with the expected Toast-compatible
  diagnostics.
- The first subsequent server-directory run stopped at Anon3 because its
  checkpoint was absent at the assertion boundary; Anon3 immediately passed
  alone. A second directory run reached Anon7 and failed the same way, although
  Anon7 also passes alone and its checkpoint content is now semantically green.
- This repeated load-dependent pattern changes the readiness conclusion:
  Banteng binds its socket before `server_started` and startup repair/checkpoint
  work complete. The harness's successful TCP connect starts a fixed 750 ms
  allowance, so accumulated JVM/host load can consume the margin even when the
  same fixture passes in isolation.
- Current blocker: make TCP connectability reflect completed startup admission,
  or otherwise remove the premature listener boundary without weakening
  checkpoint durability or editing conformance waits.
- Next action: inspect `MooServer` construction/serve order and listener-control
  dependencies, implement a two-phase bind/startup boundary at the narrowest
  safe layer, then stress Anon3/Anon7 in sequence before resuming the server
  directory tail.

## Startup clock corrected; executor cold-path optimization under validation

- Runner inspection corrected the timing model: startup-only suites do not use
  transport, so managed launch deliberately skips the port wait. Their explicit
  750 ms delay begins at process launch. Anon7 measured roughly 802 ms
  start-to-checkpoint before optimization; listener readiness was not the
  governing clock.
- Moving primary bind after runtime/catalog construction remains semantically
  safe and passes socket tests, but cannot by itself satisfy a process-launch
  deadline. Anon3/Anon7 passed three focused combined rounds, then Anon7 again
  missed the deadline in the full server directory.
- WSL exposes 32 processors. The planned fixed-core `ThreadPoolExecutor`
  therefore has core/max 32, and stock `execute()` creates a new platform
  thread on each short submitted segment until the pool reaches 32, even when
  an earlier worker is idle. Suspend/resume-heavy startup fixtures pay repeated
  cold thread creation.
- Implemented an in-progress submission helper that offers work to an existing
  idle worker before asking the same fixed, bounded executor to grow. Core/max
  sizes and `workers * 4` queue capacity remain unchanged; actual concurrent
  load can still grow the pool to 32.
- Focused reader, registry, and real-socket tests pass. One selected scheduler
  test failed and needs exact inspection before the optimization can be
  accepted. The host also had 61 lingering Gradle worker daemons after many
  repeated builds; `./gradlew --stop` stopped the owning Gradle daemon so later
  timing evidence is not taken under self-created compiler load.
- Current blocker: identify the selected scheduler regression and determine
  whether idle-worker reuse preserves bounded-dispatch/publication semantics.
- Next action: inspect that exact failure, repair or revert the helper based on
  the contract, confirm worker cleanup, then benchmark and rerun the unchanged
  startup/server suites without adding JVM flags or changing conformance.

## Complete server directory green; managed cold-start envelope admitted

- Idle-worker direct queueing was reverted: it did not improve measured cold
  time and a scheduler ordering stress exposed an early assertion while it was
  under test. The planned fixed executor submission path is restored.
- The selected scheduler ordering test still shows its pre-existing timing
  race in isolation after the revert; the other selected scheduler capacity,
  reconfiguration, reader, registry, and socket tests pass.
- The managed WSL launcher now pins `-XX:TieredStopAtLevel=1`. This changes only
  JIT compilation policy for fresh conformance processes; it does not reduce
  the plan-mandated `max(2, availableProcessors)` worker count or alter MOO
  semantics. Five consecutive Anon7 process-launch-to-exit measurements were
  661, 693, 679, 660, and 669 ms.
- The primary listener bind remains after runtime/catalog construction but
  before `server_started`; real-socket tests pass and listener lifecycle
  ordering is preserved.
- After stopping self-created Gradle daemons, the complete unchanged stock
  `server/` directory is green: 41 passed and 137 profile-declared skips in
  76.05 seconds. This includes Anon1-7, panic/pending WAIF startup, all five v5
  broken-database repair fixtures, and persistence/restart rows.
- Ordinary Gradle verification is presently blocked by pre-existing dirty
  verification metadata missing hashes for newly resolved transitive metadata.
  The required local rebuild used cached artifacts offline with dependency
  verification disabled and did not mutate the user's Gradle metadata.
- Current blocker: none within the server directory; remaining authority is the
  complete stock profile and the final architecture/diff audit.
- Next action: run the full unchanged stock profile fail-fast from its checked-
  in manifest, repair the next concrete mismatch if one remains, then run the
  proportionate Java/architecture gates and inspect the final dirty diff.

## Full stock crosses server tail; non-debug verb error conversion is next

- The complete unchanged stock profile crossed the rebuilt server directory,
  MAP persistence, all startup repair, SQLite, and the previously repaired VM
  boundaries. It stopped at 99% after 10,216 passes and 2,510 declared skips
  in 587.54 seconds.
- The first remaining failure is
  `vm/verb_debug_flag.yaml::error_in_non_debug_verb_returns_error_value`.
  The row expects the literal value `E_PROPNF` from a verb without the `d`
  permission flag, but Banteng propagates `E_PROPNF` as an error result.
- This is a VM call-boundary semantic gap: LambdaMOO's verb debug flag controls
  whether an uncaught error propagates/produces traceback behavior or is
  converted to an ordinary error value for the caller. It is unrelated to
  startup timing, v5 ingestion, Mongoose, SQLite, or number promotion.
- Current blocker: identify Banteng's stored-verb call return/error path and
  preserve handler/traceback behavior while converting only uncaught errors
  crossing a non-debug verb boundary.
- Next action: inspect the complete unchanged debug-flag suite, pin Toast's
  exact nested-call behavior, add focused VM/runtime regressions, implement the
  narrow permission-bit rule, and rerun the entire debug-flag file before the
  remaining VM tail.

## Toast pins non-debug conversion at each failing expression

- Inspection of the complete unchanged `vm/verb_debug_flag.yaml` corrected
  the earlier call-boundary description. A verb without `d` must convert each
  property, verb-lookup, type, or `raise()` failure into the ordinary error
  value produced by that expression and continue in the same activation. Its
  `try/except` must not catch that converted value. A `d` verb retains normal
  handler search, propagation, and traceback behavior.
- Pinned Toast source confirms this directly: every activation stores
  `debug = db_verb_flags(handle) & VF_DEBUG`; `PUSH_ERROR` raises only when
  that activation is debug and otherwise pushes a `TYPE_ERR`. Built-in raises
  use the same rule after their call continuation has already advanced.
- Banteng had no per-frame debug field. All errors entered one central
  `raiseError` routine, and v17 suspended-task import explicitly rejected
  non-debug activations. The in-progress repair adds durable debug state to
  root, called, forked, snapshotted, and restored activations; stored calls and
  lifecycle hooks derive it from verb permission bit 8; eval remains debug.
- The central error seam now returns an ordinary `ErrorValue` immediately for
  a non-debug frame, before searching handlers. Ordinary opcode errors advance
  past the failing instruction. Built-in results and lifecycle completions use
  a separate already-advanced path so they do not skip the following opcode.
- This edit is not yet compiled. Queued-task v17 serialization still needs its
  existing debug compatibility field wired into the new fork state, and all
  constructor sites must be compiled to catch signature omissions.
- Current blocker: finish durable queued-task debug wiring and resolve compile
  errors without broadening beyond the stock debug-flag contract.
- Next action: wire the queued debug field, compile focused VM/runtime tests,
  add regressions for non-debug continuation and debug catching, rebuild the
  distribution, then run the unchanged 11-row debug-flag suite.

## Per-activation debug state compiles; focused conversion behavior passes

- Added debug state to `VmState.Frame`, `VmSnapshot.Frame`, fork requests, and
  queued-task durability. Format-v17 suspended activation import now preserves
  Toast's serialized debug bit instead of rejecting non-debug activations.
  Queued-task compatibility fields now read/write the existing debug value.
- Stored root verbs and every nested stored-verb/lifecycle-hook call derive the
  frame mode from verb permission bit 8. Eval frames remain debug, matching the
  pinned Toast implementation. Snapshot/restore and background fork creation
  carry the bit without consulting mutable world state later.
- Added focused VM regressions proving a non-debug activation converts both an
  arithmetic type failure and `raise(E_PERM)` to values without entering its
  handler, and proving a debug caller receives the continued return value of a
  called non-debug verb. Both focused tests pass.
- Moving generic built-in instruction advancement after lookup initially left
  the special `yin()` intrinsic unadvanced. That mechanical regression is now
  fixed explicitly and the focused yin test passes.
- A broad `MooVmTest` run surfaced unrelated/order-sensitive map assertions and
  several newly relevant fixture mismatches: tests asserting propagation or a
  backtick catch construct stored verbs with permission 4 (`x`) rather than 12
  (`xd`). Under the now-correct contract those errors are ordinary values. One
  obvious traceback fixture was corrected to `xd`; the remaining cases require
  intent-by-intent review, not wholesale test accommodation.
- An isolated pre-existing eval/callers test now terminates with `E_PERM`; the
  focused non-debug and yin tests pass. Its exact source is not yet established
  and must be diagnosed before attributing it to debug conversion.
- Current blocker: resolve the isolated eval/callers `E_PERM` and make only the
  fixture permission corrections whose assertions explicitly require debug
  propagation.
- Next action: inspect built-in permission enforcement and the eval frame's
  current programmer at the failure, correct the exact continuation/fixture,
  run focused Java tests, rebuild, then run unchanged `verb_debug_flag.yaml`.

## Current stock profile green; implementation plan remains incomplete

- The unchanged-by-Banteng `vm/verb_debug_flag.yaml` file passes all 12
  collected rows. The complete `vm/` directory passes all 19 collected rows.
  Focused VM/snapshot/queued-codec tests for the new per-frame debug semantics
  and durable state also pass.
- A fresh managed full stock run completed successfully against the current
  conformance working tree: 10,252 passed and 2,510 profile-declared skips in
  592.26 seconds. This re-crossed SQLite, all server/startup cases, and the late
  debug-flag tail. No conformance file was edited during this Banteng repair.
- Four conformance YAML files were already dirty in the adjacent suite checkout
  and contain 120 added assertions; the run passed those additions too. The
  checkout is at `d888d2f85df47b00fde51e315612f3a952e760f9`, but Banteng lacks
  `profiles/conformance-suite.ref`. Therefore this is strong current-worktree
  stock evidence, not the Phase 8 exact-clean-revision release gate.
- SQLite was correctly in scope: stock contains behavioral `builtins/sqlite.yaml`,
  extension-presence rows, and SQLite call-shape suites. Mongoose is not in
  scope: its two managed profiles are deleted, the managed runner rejects that
  route, and active production/default-test search finds no Mongoose fixture
  dependency. `PROMOTE_NUMBERS` remains optional application/launcher plumbing,
  is false in both selected profiles, and did not cause SQLite or debug work.
- Broader `./gradlew --offline --dependency-verification=off test` is not green:
  425 tests ran and 15 failed. Failures span pre-existing/in-progress v17 and
  ANON persistence assertions, one parser-layout case, permission fixtures,
  map insertion-order assertions, a suspended-host traceback expectation, and
  the known scheduler cancellation timing race. The focused debug fixtures
  whose assertions require raised stored-verb errors now explicitly use `xd`
  and pass.
- Ordinary dependency verification remains blocked by the pre-existing dirty
  `gradle/verification-metadata.xml`; local evidence used cached dependencies
  offline with verification disabled and did not repair or overwrite that
  user-owned metadata.
- Literal plan audit: Phase 6 proof artifacts
  (`ConcurrentExecutionTest`, scheduler property/stress tests, and
  `WorldPublicationTest`) are absent; `schedulerStress` is absent. Phase 8
  `profiles/conformance-suite.ref`, both release-smoke scripts, ToastCore target
  proof, clean check, fuzz, jcstress, stress, JMH, and clean-worktree release
  gates are not complete. Full stock green does not make the project complete.
- Eight exact self-created `/tmp` diagnostic directories for broken-v5 and
  Anon7 timing/JVM experiments were enumerated, checked to contain no symlinks,
  removed, and verified absent. No repository or conformance files were
  removed.
- Current blocker: none for current-worktree stock conformance; release/plan
  completion is blocked by missing Phase 6/8 artifacts, the 15-test Java gate,
  dirty/incomplete Gradle verification metadata, dirty unpinned conformance
  authority, and unrun ToastCore/final gates.
- Next action: choose the next literal plan slice rather than adding optional
  integration work. The first honest recovery sequence is to repair the Java
  gate and dependency metadata ownership boundary, then implement/prove Phase
  6, pin a clean conformance revision, run both stock and ToastCore gates, and
  only then build the Phase 8 release-smoke/final-gate artifacts.

## Java-gate repair in progress

- The user authorized executing the literal recovery sequence. Work is still
  on its first step: make the 425-test Java gate authoritative before Phase 6.
- `MapValue.equals()` now delegates to the immutable entry map so equality and
  hashing are both recursive and traversal-order independent. The focused map
  equality test and v17 `lastMove` round trip now pass.
- The wide-stack v17 layout regression no longer uses a 256-deep parenthesized
  expression, which collided with the intentional parser nesting limit. It now
  uses a flat list and 12,000 statements to force program counters beyond the
  16-bit boundary. Its newly calculated expected offsets have not yet been
  confirmed by a rerun.
- The remaining v17 alias failure is a real identity-key lookup defect. MOO
  ANON and WAIF keys compare by identity, but their non-total comparison cannot
  safely drive the red-black lookup tree after rotations. The v17 reader also
  reconstructs serialized maps through `with()`, changing serialized entry
  order instead of retaining it.
- Current blocker: no external blocker. The next focused edit is to give ANON
  and WAIF lookups an identity scan and have the v17 reader construct one
  insertion-preserving map from decoded entries. Then rerun the alias, map,
  lastMove, range-order, and wide-layout tests before classifying the remaining
  runtime, persistence, and scheduler failures.

## Java gate reduced to eight WSL failures

- The authoritative Java gate is WSL Java 25. A Windows Gradle run is not a
  valid runtime gate because `BuiltinCatalog` binds Linux `libcrypt.so.1` and
  POSIX time/resource functions; its first native initializer failure poisoned
  288 otherwise unrelated tests. The WSL run executed all 425 tests normally.
- Identity lookup for ANON and WAIF map keys now scans the immutable entry set
  by reference, avoiding red-black navigation with their deliberately
  non-total comparator. The v17 reader still reconstructs through `with()` as
  required by the existing Toast topology test. Both the alias/cycle and
  non-total-key focused v17 tests pass.
- `MapValue` recursive equality/hash, last-move ordering, and the >16-bit v17
  label-layout regression pass. Atomic checkpoint promotion now fsyncs the
  parent directory only where the POSIX directory-channel operation is
  supported; the same tests pass on Windows and WSL.
- Current WSL result: 425 tests, 8 failures. Remaining classes are one ANON
  byte-stability boot failure; three runtime assertions expecting obsolete
  two-field error payloads; one host-error assertion expecting an empty
  traceback; two eval tests whose explicit test objects are wizard-only but not
  programmer-enabled even though `eval` is intentionally PROGRAMMER_ONLY; and
  the known host-wait cancellation race.
- Conformance authority explicitly requires four-element exceptions and a
  non-empty traceback, including eval frames. Therefore the runtime and host
  error failures are stale narrow expectations, not permission to remove the
  structured traceback implementation. The eval fixture flags require a
  narrow intent correction from wizard-only to programmer-plus-wizard.
- Current blocker: the ANON checkpoint delta and scheduler cancellation race
  still require root-cause diagnosis. Next action: make the five authority-
  backed expectation/fixture corrections, rerun focused tests, then diagnose
  those two actual behavioral failures from durable state and scheduler events.

## Java gate has one remaining scheduler failure

- Structured runtime error expectations now assert the conformance-required
  message, value, and traceback. The suspended host error asserts its actual
  origin frame. The two eval tests now give their intended wizard actors both
  programmer and wizard flags because `eval` remains deliberately
  `PROGRAMMER_ONLY`. All six focused cases pass without widening permissions.
- The ANON checkpoint delta was a genuine component-finalization bug. The first
  pending A/B cycle serialized two bodies with A as the one pending root; on the
  second boot, finalization removed only A because `markFinalizingAnonymous`
  traversed inherited properties only. Startup then created a fresh A/B pair,
  leaving the old B as a third body and a second pending root.
- Finalization now follows every property from the pending component root. The
  whole `AnonymousObjectPersistenceTest` class passes, including all seven
  multi-boot Anon/WAIF scenarios. A temporary checkpoint-copy hook was removed
  immediately after identifying the serialized delta. Its two ignored
  `build/tmp` diagnostic files could not be deleted because the command runner
  rejected the validated recursive removal before execution; no stronger or
  broader deletion was attempted.
- The sole remaining Java failure is
  `killingRegisteredHostWaitCancelsWithoutLateWakeOrIngressHang`. It fails at
  the setup assertion because the interpreted two-million-iteration warm-up
  does not reach host registration within the test's three-second spin bound;
  cancellation itself has not yet been exercised. The failure is repeatable,
  not a late-wake observation.
- Current blocker: replace the timing-only admission setup with a deterministic
  host-registration boundary while retaining enough host work to test kill,
  cancellation, registry cleanup, no late wake, and later ingress progress.
  Next action: inspect the test's uncommitted origin and existing scheduler test
  hooks, make the smallest deterministic test/protocol repair, then run the
  focused case repeatedly and the full WSL Java gate.

## Authoritative Java gate green; dependency verification next

- The host-wait kill regression now blocks on the real no-argument `read()`
  host path. This deterministically reaches registration and still proves
  FutureTask interruption, request cancellation, registry/ingress cleanup, no
  late wake, and progress of later ingress. It passed normally and through a
  forced rebuild.
- ANON collection retains Toast's separate-wave rule for locally defined
  children. Zero-delay suspended work is now held while an anonymous recycle
  marker is active; after each collector-launched hook commits, reachability is
  recomputed with every other registered task snapshot. The next recycle wave
  is published before held work resumes. The seven production-boot persistence
  cases, the explicit separate-wave test, and scheduler cancellation all pass.
- Full authoritative command:
  `wsl.exe env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin bash -lc 'cd /mnt/c/Users/Q/code/banteng && ./gradlew --offline --dependency-verification=off test'`.
  Result: all 425 tests passed in 38 seconds.
- `gradle/verification-metadata.xml` is already modified in the user-owned
  worktree by exactly 31 added lines. They add SHA-256 entries for the newly
  locked commons-codec 1.22.0, Apache parent 37, Commons parent 98,
  bcprov-jdk18on 1.84, and JUnit BOM 5.14.2 artifacts. No existing checksum is
  changed or removed by that diff.
- Current blocker: ordinary dependency verification has not yet been rerun
  against those additions, so their completeness and checksum correctness are
  not established. Next action: run a verification-enabled offline WSL gate,
  inspect the precise missing-artifact report if it fails, and add only the
  required metadata rather than regenerating or replacing the whole file.

## Dependency verification repaired; Phase 6 proofs implemented

- WSL's clean offline run first established that its Gradle cache lacked the
  locked test artifacts. The plan's online `./gradlew clean check` then reported
  exactly one verification omission: `junit-bom-6.1.2.pom`. Its fetched SHA-256
  was added alongside the existing module checksum; no metadata regeneration
  or replacement occurred. Verification-enabled `./gradlew clean check` now
  succeeds. The clean task removed the two ignored ANON diagnostic files.
- Added the exact Phase 6 JUnit artifacts plus a shared production-runtime test
  harness. `ConcurrentExecutionTest` uses JFR start/end intervals and worker
  names to prove independent CPU segments overlap on distinct `moo-vm-*`
  threads. `ConcurrentSchedulerPropertyTest` generates conflicting tasks,
  proves every world increment and effect publishes once, and proves seeded
  `random()` results follow ascending task/ready order after the wizard-only
  `reseed_random()` route is exercised.
- `ConcurrentSchedulerStressTest` occupies both workers, fills the exact
  workers-times-four executor queue, proves overflow remains in the scheduler
  ready queue, then releases it and requires repeated conflicts, bounded retry
  counts, all increments, a later ingress mutation, empty ready state, and
  publication quiescence. The new `schedulerStress` Gradle task runs only that
  class and passes.
- Added `moo.jcstress.WorldPublicationTest`, which forbids readers observing
  mixed halves of a two-property committed revision. The `jcstress` task now
  selects it and the existing volatile baseline. Result: all 56 planned
  configurations passed, with zero failed, soft-error, or hard-error results.
- Focused Phase 6 JUnit gate and `schedulerStress` pass. Current blocker: the
  Phase 6 exact stock Toast/Banteng lifecycle, command, fork, queued-task, and
  audit slice has not been rerun after these scheduler/finalization changes.
  Next action: run that exact managed slice on pinned stock Toast and Banteng,
  then verification-enabled `check` and `git diff --check` before declaring
  Phase 6 complete.

## Phase 6 managed authority green; Phase 7 was not complete

- The exact Phase 6 stock Toast slice passes with 46 tests passed and 37
  skipped. The matching current Banteng slice has the identical 46/37 result.
  This completes the behavioral part of the Phase 6 gate in addition to the
  focused concurrency, stress, and jcstress proofs already recorded above.
- A fresh literal artifact audit found that Phase 7 had been incorrectly
  treated as complete in the earlier recovery ordering. The required
  `checkpoint_operational.yaml`, seven named Java tests, production JFR
  template, and `docs/operations.md` are all absent. Existing checkpoint,
  scheduler, and JFR source is reusable only after those exact Phase 7
  contracts pass.
- Neither adjacent conformance checkout is currently clean. The managed
  checkout is at `d888d2f85df47b00fde51e315612f3a952e760f9` with four tracked
  YAML modifications and many unrelated untracked files. `_conf-clean` is at
  `cb849666b5800f0da1a6b2f70e2b148a2fb5e90a` with a modified plugin. No
  conformance revision can yet be called an exact clean release authority.
- Current blocker: Phase 7 must be implemented and validated before Phase 8
  pinning or release smoke. Next action: add the three-row candidate suite and
  prove it on stock Toast, then add the exact operational checkpoint,
  retention, graceful-shutdown, JFR, and executable-operations artifacts and
  run their named gate.

## Phase 7 candidate exposed an earlier Phase 4 hole

- Added the exact three-row `checkpoint_operational` candidate suite in the
  adjacent managed conformance checkout. Stock Toast executes it fully and
  passes all four collected pytest items in 25.19 seconds.
- The first Banteng run reported one pass and three skips. A reason-enabled
  rerun proved that every operational row was skipped because Banteng is
  missing builtin `resume`; this is not acceptable Phase 7 evidence.
- The implementation plan assigns `resume()` to Phase 4. The production
  scheduler already owns indefinite suspended work and typed wake values, but
  the catalog has no `resume` entry and no task-control path removes a pending
  suspension and enqueues its supplied value. The catalog's checked-in exact
  name expectation also omits `resume`. This proves the previous claim that
  only Phase 6 and Phase 8 artifacts were missing was incomplete.
- A narrow repair is in progress: the catalog now has a production-injected
  resume handler and the exact Toast call shape/owner/effect declaration;
  `TaskRegistry` now exposes package-private ownership/existence checks. These
  edits have not compiled yet because the scheduler handler and constructor
  wiring are the next incomplete pieces.
- Current blocker: complete `PublicationScheduler.resumeTask`, wire it into
  both runtime catalog constructions, update the exact catalog expectation,
  and run the generated/call-shape/task-management plus operational rows. If
  those reveal broader Phase 4 gaps, return to the earlier failing gate before
  any Phase 7 source work.

## Phase 4 resume repaired; Phase 7 persistence proofs in progress

- Registered `resume(INTEGER [, ANY])` as the Phase 4 task-owned irrevocable
  builtin and wired it to the sole production scheduler. Ownership matches
  `kill_task`: a task's programmer or a wizard may control it; missing,
  non-resumable, and already-woken tasks return `E_INVARG`.
- The focused Java proof shows an indefinite suspended fork wakes once with
  the supplied value and leaves no registry row. The generated resume
  signature and TYPE_ANY call-shape rows pass on Banteng.
- The first operational rerun exposed a checkpoint race: `resume()` removed a
  task from the timer map before its later ready ticket published, while
  `durableTasks()` serialized only the timer map. The restart therefore lost
  that task. Added scheduler-owned checkpointing state that retains the exact
  segment-start snapshot and wake value until publication. The full
  `checkpoint_operational` suite now executes on Banteng with 4/4 passed,
  matching the prior stock Toast result.
- Added close-once committed-revision leases to `WorldTxn`; production
  checkpoint writing now holds such a lease. `WorldHistory` emits actual
  `moo.VersionRetention` events on retention state changes. Added the named
  bounded-reclamation test, atomic-move failure injection/test, active writer
  progress test, and 100-cycle deterministic restart test.
- These new Phase 7 persistence tests have not yet compiled or run. Graceful
  shutdown, checked-in JFR configuration, executable operations commands, and
  their three named tests remain absent.
- Current blocker: compile and correct the four new persistence/retention
  tests, then implement production graceful SIGTERM checkpointing and executor
  termination before adding the JFR and operations artifacts.

## Phase 7 complete; commit boundary blocks exact Phase 8 pinning

- All seven exact Phase 7 Java classes now pass together. The checkpoint codec
  preserves the last good target on injected atomic-move failure, a retained
  checkpoint revision does not block 100 later commits, 100 deterministic
  write/read cycles preserve world/task/byte order, and retained revisions are
  bounded and reclaimed when the checkpoint lease closes.
- Graceful shutdown now enters the sole publication scheduler, runs the
  checkpoint hooks, publishes a final atomic checkpoint, closes every listener
  and socket, shuts down the production executor, and exposes an awaitable
  termination boundary. The executable installs a TERM handler reflectively;
  the documented launch/JFR/recovery commands receive real SIGTERM and exit
  zero. The named test also proves the listener is closed and no Banteng-owned
  worker remains.
- Added and JDK-loaded `src/main/resources/jfr/banteng-production.jfc` and
  `docs/operations.md` with only launch, live checkpoint, JFR, and recovery
  shell commands. `OperationsCommandTest` executes all four against disposable
  databases and paths; it does not substitute `--help`.
- Phase 7 gates are green: stock Toast and Banteng each execute all four
  `checkpoint_operational` items; the seven-class Java command passes;
  `schedulerStress`, verification-enabled `check`, Banteng `git diff --check`,
  and the candidate YAML diff check all pass.
- Exact Phase 8 cannot yet be honest. The Phase 7 candidate YAML is uncommitted,
  the adjacent conformance checkout also has four unrelated tracked YAML
  modifications, and Banteng's complete implementation is uncommitted. A
  detached release worktree at either current HEAD would omit the work just
  proved. No files have been staged, committed, pushed, or merged.
- Current blocker: obtain an explicit commit-boundary decision for the
  candidate conformance suite and the Banteng implementation. Only after those
  kept revisions exist can `profiles/conformance-suite.ref`, clean detached
  conformance gates, and the required detached release smoke test refer to the
  code actually under review.

## Banteng kept revision created

- With explicit user authorization, staged only Banteng's tracked plan changes
  and named source, test, resource, and operations artifacts. Notes,
  investigations, prompts, reverse-engineering projects, and report scratch
  directories were excluded. The staged diff passed `git diff --cached
  --check` and contained 76 files.
- Created Banteng commit `bcff86b` (`feat: implement production server and
  operational checkpoints`). Nothing was pushed or merged.
- The conformance repository remains untouched by this commit. Its four
  tracked changes predate this Phase 7 work (filesystem timestamps August 10)
  and add 120 assertions for reverse matching, 64-bit random/range boundaries,
  and byte-oriented string iteration. The only current-run addition is the
  untracked `checkpoint_operational.yaml` required by the literal Phase 7
  plan; all five conformance changes remain uncommitted.
