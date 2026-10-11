# Phase 3 v17 anonymous-object codec investigation

## State

- Task is a read-only comparison of Toast’s authoritative v17 anonymous-object and WAIF serialization/deserialization behavior against Banteng’s `LambdaMooV17Codec`.
- No source files have been changed.

## Findings and observations

- Toast v17 anonymous references are ordinary serialized value tag 12 followed by a temporary dump object id. `db_write_anonymous()` assigns the next object slot the first time a live anonymous object is encountered, stores the object in the global object table, and writes that id; subsequent references write the same id (`/root/src/toaststunt/src/db_io.cc:347-395`, `/root/src/toaststunt/src/db_objects.cc:373-421`). The reader creates or reuses the anonymous object placeholder at that id before later object batches populate it (`db_objects.cc:373-401`).
- Toast begins dumping the current permanent range, then repeats object batches while serializing those objects discovers additional anonymous objects; only after the last batch writes a zero terminator does it count and write programs over the full expanded id range (`/root/src/toaststunt/src/db_file.cc:1061-1100`). Reading mirrors this: permanent batch first, then zero or more anonymous batches, then programs (`db_file.cc:906-1004`).
- Toast WAIF values use tag 13 and a dump-global identity table. First encounter writes `c <index>`, class id, owner id, declared WAIF-property count, sparse `(property-index, value)` entries, `-1`, and `.`. Later encounters write `r <index>` and `.` (`/root/src/toaststunt/src/waif.cc:850-927`). Loading installs a created WAIF in the identity table before recursively reading its values, so recursive/shared references resolve; `r` returns that exact instance (`waif.cc:938-1045`). After all objects and programs load, Toast resolves class property definitions and invalidates self-referential WAIFs (`waif.cc:1049-1085`).
- Banteng currently writes only primitive, list, and map values; every other value reaches an unsupported-value exception (`src/main/java/moo/persistence/LambdaMooV17Codec.java:243-271`). Its reader accepts only tags 0, 1, 2, 3, 4, 9, 10, and 14 (`LambdaMooV17Codec.java:460-497`). It imports neither `AnonymousObjectValue` nor `WaifValue`, and has no dump/load identity contexts.
- Banteng currently freezes its object list and maximum id before writing the first and only nonzero object batch, writes zero immediately afterward, and then emits programs only from that original list (`LambdaMooV17Codec.java:165-197`). It therefore cannot discover anonymous objects transitively from property/map/list values or serialize their object bodies/programs in the required later batches.
- All six committed conformance fixtures `Anon1.db` through `Anon6.db` have the exact v17 header. The plan correctly calls them v17 at lines 392-395, but Phase 3 line 373 grammatically asks for v4 input for WAIFs and anonymous objects too. That line should distinguish legacy v4 permanent-world import from v17 WAIF/anonymous-object input and output; no v4 anonymous-object format or fixture exists.
- The map persistence row itself requires both identity schemes: the state list contains two WAIF instances and two anonymous objects before maps that use those same instances as keys (`map_dump_persistence.yaml:24-73`), then after restart indexes each map with the restored standalone instances and asserts key order/topology (`map_dump_persistence.yaml:86-127`). Therefore value decoding must intern WAIFs by WAIF save index and anonymous objects by temporary dump object id across the entire checkpoint, including nested map keys.
- Banteng’s `WaifValue` has only identity plus class and owner (`MooValue.java:346-385`), so it can represent distinct/backreferenced zero-property WAIFs for the map row but cannot encode the sparse per-instance WAIF property state required by `waif_dump_persistence.yaml:18-41`. The model must gain mutable-or-persistently-replaceable WAIF property slots before the codec can satisfy that later row.
- Banteng has no anonymous-object value type at all: the sealed value family omits tag 12 (`MooValue.java:18-48`), and `WorldObject` is only a permanent long-id record (`WorldObject.java:7-25`). Exact v17 anonymous support therefore cannot be a codec-only switch case; it first requires an identity-bearing anonymous value/object-body model distinct from `ObjectValue` and from the permanent `WorldSnapshot.objects()` id map.
- Toast pending finalization is a typed-value root section, not just a label. It writes anonymous objects/WAIFs through the same dump identity contexts (`server.cc:689-724`), reads them before object bodies as placeholders (`server.cc:726-751`), and queues them after loading (`server.cc:754-781`). Banteng currently rejects any nonzero count at `LambdaMooV17Codec.java:114` and always writes zero at line 158, so Anon1-Anon6 require the checkpoint model/runtime to carry and process this value list, not merely relax the parser.
- Toast’s anonymous-load invariant is explicit: references in pending values, tasks, and permanent objects create all anonymous placeholders before their later object bodies are read (`db_file.cc:294-324`). The temporary dump ids are serialization handles only; anonymous object bodies remain pointer-identity objects, and the permanent object id range is restored after use (`db_objects.cc:373-439`).
- Toast MAP persistence traverses entries in its red-black-tree comparator order for both `mapkeys()` and `dbio_write_var()` (`map.cc:803-823`, `map.cc:1065-1079`, `db_io.cc:379-382`). Same-type BOOL, WAIF, and ANON comparisons return zero only for identical values and positive for either direction otherwise (`utils.cc:430-435`), so their order is intentionally non-total/insertion-topology dependent. Banteng serializes raw `LinkedHashMap` order (`LambdaMooV17Codec.java:263-269`) and reconstructs the same raw order (`483-493`), while its `mapkeys` only performs a stable sort over insertion order for BOOL/WAIF and has no ANON case (`BuiltinCatalog.java:811-860`). The map row therefore requires the value/map representation to preserve Toast traversal topology across write/reinsert, not an arbitrary key sort inside the codec.

## Blocker

- No investigation blocker. Implementation is structurally blocked until the Phase 3 value/world model contains anonymous-object identity/body state, WAIF property state, and checkpoint pending-finalization roots; adding tag cases to the current codec alone would create false support.

## Required change mapping

1. **Correct the plan’s format scope.** Replace Phase 3 line 373’s combined `v4/v17 ... WAIFs, and anonymous objects` requirement with an explicit split: streaming v4 input remains the legacy permanent-object bootstrap/import surface; complete objects/properties/verbs/WAIFs/anonymous-object persistence is v17 input and deterministic v17 output. Keep lines 392-395’s explicit six-v17-fixture authority.
2. **Add the missing runtime value state before changing the codec.** Add identity-bearing tag-12 anonymous values, including the invalid `-1` sentinel and a body containing the same persisted object fields/verbs/properties as a v17 object. Keep that identity outside the permanent numeric object namespace. Extend `WaifValue` with its sparse instance property slots. Extend `Checkpoint`/runtime state with ordered values pending finalization.
3. **Use one checkpoint-wide write context.** It must use identity, not Java `equals`, to map anonymous instances to temporary dump object ids and WAIF instances to save indices. Initialize it before pending-finalization values are written and retain it through all object bodies and programs. Tag 12 writes `-1`, an existing temporary id, or allocates the next id. Tag 13 writes Toast’s exact `c`/`r` record, class, owner, sparse property indexes/values, `-1`, and `.`.
4. **Emit object batches to a fixed point.** The first batch is exactly the permanent slot range snapshot. Writing pending values and any batch may discover anonymous objects. Snapshot the new upper id after each batch and emit another contiguous batch until no ids were added, then emit `0`. Only then count and emit programs, in ascending temporary object id and stored verb order, so anonymous-object programs are included.
5. **Use one checkpoint-wide read context.** Read the pending-finalization count and values before object batches. Tag 12 must intern placeholders by temporary id immediately; tag 13 `c` must install its instance before recursively reading property values, and `r` must return that exact instance. Read the first object batch as permanent slots, later batches into already-interned anonymous placeholders, reject an anonymous body without a prior reference, and attach post-batch programs to either permanent or anonymous bodies by the same temporary id. After complete load, resolve WAIF property definitions against classes, remove serialization-only anonymous ids, and hand pending roots to runtime finalization.
6. **Do not normalize MAPs in the codec.** Persist entries in the runtime map’s Toast-equivalent traversal order and rebuild by normal insertion. Identity interning must happen before a WAIF/ANON is used as a key. The present `LinkedHashMap` plus ad-hoc `mapkeys` sort cannot represent Toast’s non-total same-type key topology, so the map value implementation must own that behavior; the codec must merely traverse/reinsert it exactly.
7. **Prove the dependency stack in order.** Add direct v17 round trips for invalid tag-12 `-1`, repeated and forward anonymous references, an anonymous object discovered from another anonymous object (multiple batches), anonymous verbs/program headers, WAIF creation/backreferences and sparse properties, and pending-finalization roots. Then run the exact managed map row, WAIF persistence row, and each committed v17 Anon1-Anon6 row through the production runtime.

## Requirement review

- **v17 anonymous serialization/deserialization:** answered by findings on Toast tag 12, temporary ids, placeholders, and fixed-point batches, plus required changes 2-5.
- **WAIF serialization/deserialization:** answered by the Toast `c`/`r` identity table and sparse properties, plus required changes 2, 3, and 5.
- **identity/reference remapping:** answered by checkpoint-wide identity contexts and removal of serialization-only anonymous ids in required changes 3 and 5.
- **object batch/program ordering:** answered by Toast `db_file.cc:1061-1100` and required change 4.
- **Phase 3 map row:** answered by the exact row’s standalone/key alias graph and required changes 3, 5, and 6.
- **Anon1-Anon6:** answered by their verified v17 headers, pending-finalization value section, and required changes 1, 2, 5, and 7.

## Next action

- Send this exact mapping to the parent agent. No source edit is authorized in this subtask.
## 2026-07-19 bounded anonymous-object model inspection

- Assignment: read the current Banteng value, world/transaction/history/snapshot, VM dispatch/index/equality/map ordering, and create/recycle builtin code without changing source or the implementation plan.
- Current finding: inspection has not started yet; this entry satisfies the mandatory checkpoint before any further action.
- State: read-only architecture audit in progress. No source, test, or plan files have been changed.
- Blocker: none currently. The controlling correction is that v4 anonymous-object databases do not exist; any proposed model and plan dependency must use v17 persistence.
- Next action: inspect the exact current owners and report the smallest v17-capable anonymous-object model, exact file/line evidence, and required tests to the parent agent.

## 2026-07-19 model-inspection checkpoint

- `MooValue` has no anonymous-object variant. `ObjectValue` is a numeric ordinary-object reference; `WaifValue` already demonstrates identity equality by intentionally inheriting `Object.equals`.
- `MapValue` already accepts every scalar other than LIST/MAP and uses each key's `equals`/`hashCode`; an identity-based anonymous value therefore becomes an identity map key without changing map storage or lookup.
- `World`, `WorldSnapshot`, and `WorldHistory` currently own only `Map<Long, WorldObject> objects`; transaction conflict tracking is likewise keyed only by ordinary object number. Anonymous objects need committed/working storage and transactional identity/write tracking, but no new interface/helper/adapter is indicated.
- `WorldTxn` ordinary verb/property lookup, reads, writes, creation, and recycling are all concrete methods over `WorldObject`; the smallest route is overloads/branches on the new anonymous value/record while continuing to reuse `WorldProperty`, `WorldVerb`, and inherited ordinary-parent lookup.
- VM call/property dispatch currently accepts `ObjectValue` and special-cases `WaifValue`. Equality already delegates to value equality except BOOL/INT compatibility. Adding one identity-valued anonymous case to call/property dispatch is sufficient; indexing should remain `E_TYPE` because anonymous objects are not collections.
- Builtin `create` currently has only a one-OBJECT call shape and always calls ordinary `createObject`; supporting `create($anonymous, 1)` requires the literal two-argument anonymous signature and direct anonymous allocation in `WorldTxn`. `recycle` already accepts ANY but rejects non-`ObjectValue`; it needs the anonymous identity branch and same lifecycle-hook path.
- Persistence authority is v17 only: anonymous references use value tag 12 plus dump-assigned ids, anonymous bodies are emitted in iterative object batches, and references are rebound to placeholders on v17 read. No v4 anonymous database or v4 anonymous path should appear in the plan or tests.
- State: read-only inspection continues; no tracked file has been changed.
- Blocker: none.
- Next action: inspect exact world/history validation and v17 codec boundaries, then send the parent a line-cited minimal model and required test list.

## 2026-07-19 source-read checkpoint

- Observation: WSL is available and resolves the working directory, but two bounded commands against `/root/src/toaststunt/src/objects.cc` returned no output. No source or environment state was changed.
- Existing pinned-source findings in this note remain sufficient to constrain v17 identity and batching; the missing terminal output is not being treated as contrary evidence.
- State: read-only audit remains in progress; no tracked modifications.
- Blocker: exact fresh Toast line display is temporarily unavailable through the attempted command form.
- Next action: use one minimal WSL existence/read command; if it remains blank, rely only on already captured pinned-source evidence and current Banteng line-numbered code for the parent report.

## 2026-07-19 transaction and codec checkpoint

- A direct `wsl -e sed` read works. Pinned Toast `bf_create` confirms the exact call grammar `(OBJ|LIST parent(s) [, OBJ owner] [, INT anonymous] [, LIST args])`; `create($anonymous, 1)` means one ordinary parent plus the anonymous flag, not a v4 fixture or an anonymous parent.
- Toast allocates an ordinary temporary object body, marks the returned value `TYPE_ANON`, removes the temporary ordinary id from the permanent high-water sequence, and invokes inherited `initialize` with the ANON as `this`.
- Banteng `WorldHistory` validates/conflicts only `World.objects`, and topology validation requires reciprocal ordinary parent/children links. Anonymous objects must be excluded from ordinary children reciprocity and require their own conflict key space/table.
- Banteng v17 writer freezes one ordinary-object list, writes one batch then zero, and writes programs only for that fixed list. The exact v17 anonymous model must let the codec discover reachable anonymous identities while values are serialized, emit later object batches, and include their programs.
- Banteng v17 reader already loops over multiple object batches but treats every body as permanent and restores one ordinary map. Tag 12 placeholders plus a final split into permanent and anonymous bodies are required for graph identity and forward/self references.
- State: audit evidence is sufficient for the requested design; no tracked modification was made.
- Blocker: none.
- Next action: collect stable current line ranges, produce the minimal no-helper/interface/adapter design and required test list, and send it to the parent agent.

## 2026-07-19 final inspection checkpoint

- `BuiltinCatalog.mapKeys` is an exhaustive switch over the sealed value family. ANON must be ranked after MAP and before BOOL/WAIF according to type code and, for two ANON keys, must return Toast's non-total result: zero only for the same identity and positive for any distinct pair. No change to `MapValue` insertion-preserving storage is needed.
- Exact current ownership points are now collected: `MooValue.java:18-49,324-383,528-620`; `World.java:10-18`; `WorldSnapshot.java:9-14`; `WorldHistory.java:15-85,116-180`; `WorldTxn.java:24-44,245-383,459-607,1037-1068`; `MooVm.java:240-307,498,525-646,1436-1452`; `BuiltinCatalog.java:312-318,388-394,811-861,1105-1114,1586-1601`; `LambdaMooV17Codec.java:54-182,243-270,364-497`.
- State: requested read-only audit is complete; only this required uncommitted notes artifact was updated.
- Blocker: none.
- Next action: send the parent the minimal data model, exact code touchpoints, lifecycle/transaction/persistence rules, and required tests.

## 2026-07-19 recycle-continuation addendum

- Existing recycle continuation state is numeric-only: `BuiltinCatalog.Result.recycleTarget`, `VmState.Frame.recycleTarget`, and `VmSnapshot.FrameSnapshot.recycleTarget` are `OptionalLong`, and `MooVm` converts them directly to `ObjectValue` and calls numeric world methods.
- The exact minimal change is to widen that existing recycle target field to an optional `MooValue` and branch on `ObjectValue` versus the new anonymous identity. Adding a second anonymous recycle result, adapter, or parallel hook dispatcher would duplicate the lifecycle and violate the requested ownership boundary.
- State: read-only audit complete; no tracked change.
- Blocker: none.
- Next action: report to parent now.
## 2026-07-20 Anon1 exact-row start

- Corrected plan authority is explicit: v4 anonymous-object databases do not exist; all `Anon1.db` through `Anon6.db` work is ToastStunt v17 input/output.
- Exact Toast managed row `startup_repair_anon1.yaml` passes 1/1.
- Exact Banteng managed row fails because `Anon1.db.new` is absent after 500 ms.
- Corrected diagnosis: the Anon1 input header contains `0 values pending finalization`; Banteng is not rejecting a nonzero input pending-root count.
- Exact fixture program: object `#0` verb `server_started` logs, `suspend(0)`, creates an anonymous object via `create(#-1, 1)`, adds/sets a `recycle` verb, and calls `shutdown()` in `finally`. Toast writes the created garbage as pending anonymous object `#4`, yielding `Anon1.db.new` with `1 values pending finalization`.
- Current Banteng gap: no production startup path invokes `server_started`; production builtin search also finds no `server_log`, `suspend`, or `shutdown` handler. Therefore the fixture-defined mutation and checkpoint never occur.
- What worked: exact fixture/YAML and current production path inspection established temporal causality; the missing file follows the absent startup execution, not v17 input rejection.
- Blocker: none yet; exact Toast wire/lifecycle details for pending roots are still being audited read-only.
- Next action: finish the Toast source audit, then add the smallest complete Anon1 red test through startup VM execution and v17 pending-root output before production changes.
## 2026-07-20 Toast pending-root wire and ordering

- Toast authority: `/root/src/toaststunt/src/server.cc` `write_values_pending_finalization` (around 697-724) writes the total count followed by each pending anonymous tagged value, then each destroyed WAIF whose recycle verb has not run.
- `read_values_pending_finalization` (around 735-751) reads that count and the corresponding tagged values into `pending_list`.
- `main_loop` (around 755 onward) first queues non-null anonymous roots and not-yet-queued WAIF roots from `pending_list`, frees the list, then runs `#0:server_started`.
- In each main-loop turn, checkpoint handling precedes `recycle_anonymous_objects()` and `recycle_waifs()` (around 851-852). This ordering is required to preserve a pending root in a shutdown checkpoint rather than recycle it first.
- Anon1 input itself contains zero pending roots; its startup verb creates the one root that its output checkpoint must contain.
- Next action remains: complete source audit for shutdown/checkpoint timing and later Anon fixtures, then establish the production startup red test.
## 2026-07-20 Anon1 canned-database lifecycle authority

- Exact Anon1 lines 1-10: v17 header, one user (`#3`), `0 values pending finalization`, empty clocks/queued/suspended/interrupted/connections, then permanent object count `4`.
- `server_started` (fixture lines about 97-115) creates one anonymous object, installs a logging `recycle` verb, and shuts down.
- Toast `test_canned_dbs.rb` (about 40-57) requires first-boot log not to contain `recycle called`; the first output diff adds `1 values pending finalization`, tagged type `12`, and anonymous object id `4`.
- On boot two, the log must contain `recycle called`; its second output database is byte-identical to the first output.
- `dbio_write_var` writes masked type tag then payload; `_TYPE_ANON` is tag `12`. `db_write_anonymous` assigns/stabilizes a temporary database object id and writes it. `db_read_anonymous` creates or returns the placeholder for that id, with `-1` representing invalid/null.
- This proves the durable world model must retain ordered pending-finalization value roots, not infer them solely from anonymous object bodies at write time.
- Next action: create the Anon1 production lifecycle red test with startup execution, first output pending root/body assertions, second boot recycle observation, and byte identity.
## 2026-07-20 Toast shutdown and boot-two timing

- `server.cc` `bf_shutdown` clean path (about 2587-2613) only sets `shutdown_triggered`.
- After `main_loop` exits, Toast shuts down background/network work and calls `db_shutdown` (about 2351-2362). `db_file.cc` `db_shutdown` (about 1296-1305) calls `dump_database(DUMP_SHUTDOWN)`.
- `db_initialize` binds command-line input and output database names (about 1217-1237), so the canned harness `./moo original backup 9899` writes the final database to `backup`.
- `dump_database` writes a temporary `output.#N#`, calls `write_db_file`, flushes, fsyncs, closes, removes the prior output, and renames the temporary file to the output (about 1117-1203).
- `run_server_task` starts `server_started`; Anon1 `suspend(0)` queues its VM continuation. Each main-loop iteration recycles pending anonymous objects/WAIFs before `run_ready_tasks`.
- Therefore boot two first recycles the loaded pending root, then resumes `server_started`, creates replacement garbage, sets shutdown, and the final dump preserves that new pending root. Temporary id reuse makes the second output byte-identical.
- Next action: run the new `AnonymousObjectPersistenceTest` to capture the current production red boundary.
## 2026-07-20 Anon1 red-test progression

- Added the plan-named `AnonymousObjectPersistenceTest` for the real committed v17 `Anon1.db`. It requires fixture-driven shutdown, first output `1 / 12 / 4`, restored pending-root/body identity, and byte-identical second output.
- First red gate was compile-time: `WorldSnapshot` had no `pendingFinalization` state.
- Implemented in the active uncommitted slice: immutable ordered pending-finalization values are threaded through `World`, `WorldSnapshot`, `WorldHistory`, and `WorldTxn`; v17 read/write now decodes/encodes the count and tagged values using the same placeholder/identity context as later anonymous bodies.
- Second red gate occurs while reading the real input, before runtime startup: `java.io.IOException: missing program #2:0`.
- Exact meaning: Toast v17 permits verb definition slots without a program record. Banteng currently requires one program record per verb and writes one for every verb, including empty source.
- Required codec correction for this family: missing program record restores as an unprogrammed/empty-source verb; deterministic output omits empty-source program records from the count and program section.
- No runtime startup or builtin changes have started. Current tracked slice is uncommitted as required.
- Blocker: none.
- Next action: correct the existing v17 program section, rerun the exact Anon1 test, and use its next failure as the startup-runtime red boundary.

## 2026-07-20 Anon1 runtime red boundary after restart

- The plan now explicitly limits v4 to legacy permanent-object bootstrap input and requires v17 for every anonymous-object fixture and checkpoint; commit `6b2ef02` records that correction.
- The active tracked Anon1 slice remains uncommitted and contains ordered pending-finalization state in the world model, v17 pending-value read/write support, legal omitted program records for empty-source verbs, and the real-fixture `AnonymousObjectPersistenceTest`.
- The exact targeted test now loads `Anon1.db` successfully and fails only because the fixture does not shut down: Banteng does not yet invoke `#0:server_started` and lacks the fixture's required startup lifecycle.
- Toast authority establishes the required ordering: boot startup runs `server_started`; `suspend(0)` creates a scheduler boundary; loaded pending roots are recycled before the continuation resumes; shutdown requests final checkpoint output after the task's committed changes.
- Current production inspection confirms `BuiltinCatalog` registers `add_verb`, `set_verb_code`, and `create`, while no `server_log`, `suspend`, or `shutdown` handler is present. Anonymous receiver support in the existing verb mutation paths still must be checked before editing.
- Blocker: none.
- Next action: inspect the existing builtin handlers and runtime effect/startup owners narrowly, then implement the smallest production Anon1 lifecycle through those existing owners and rerun the exact `AnonymousObjectPersistenceTest`.

## 2026-07-20 Anon1 runtime ownership inspection

- `MooServer.serve()` currently enters the accept loop without submitting any startup task. `MooRuntime` and its nested `RuntimeRequest`/`Operation` own every scheduler ingress, so startup belongs there rather than in a separate runner.
- `PublicationScheduler` already executes suspended continuations through the production transaction/commit path. A zero-delay result resumes with integer zero, and deferred runtime effects publish only after the world transaction commits.
- `BuiltinCatalog.Result` already has `delaySeconds` and checkpoint-request fields. `MooVm.applyBuiltinResult` already turns either into the existing suspension or staged-checkpoint machinery. No new sender/helper/interface is needed for `suspend` or shutdown checkpointing.
- `BuiltinCatalog.ListenerControl` is the existing server-host boundary. `MooServer` implements it, so shutdown can extend that existing boundary and close the production listener after checkpoint publication.
- `add_verb` and `set_verb_code` currently reject anonymous receivers and call permanent-object-only `WorldTxn` mutation methods. `create(#-1, 1)` already allocates the anonymous identity/body required by Anon1.
- The exact Anon1 program invokes `server_log`, `suspend(0)`, anonymous `create`, `add_verb`, `set_verb_code`, and `shutdown` in that order. The first input contains zero pending roots.
- Blocker: none.
- Next action: patch the existing world mutation owner for anonymous verbs, then add the three exact builtin contracts and startup/shutdown effects through the existing catalog/runtime/server owners.

## 2026-07-20 Anon1 world mutation and builtin authority

- The active slice now extends the existing `WorldTxn` verb lookup, add, and source-replacement operations to anonymous identities and adds exact anonymous-body removal. The existing name matching logic is shared by permanent and anonymous lookup, including abbreviation wildcards and executable filtering.
- Pending-finalization reads/writes now use an explicit transaction scan predicate so a concurrent pending-root change invalidates the attempt rather than silently overwriting current state.
- Toast source confirms exact manifest arities/types: `server_log` requires one string and accepts one optional value; `suspend` accepts zero or one numeric argument; `shutdown` accepts zero through two arguments with first optional string and second optional value.
- Toast `server_log` and `shutdown` are wizard-only. Anon1 runs `#0:server_started` as the verb's owner/programmer, which is wizard in the fixture.
- Existing `BuiltinCatalog.Result` has no factory for timed suspension yet, but its `delaySeconds` field and VM application path are already complete. Shutdown can distinguish its checkpoint request from `dump_database` by adding a shutdown flag to the existing request record.
- Blocker: none.
- Next action: add the exact three manifest entries and result factories, then thread shutdown through the existing deferred checkpoint/runtime listener effect.

## 2026-07-20 Anon1 startup and builtin implementation state

- The active slice now registers exact `server_log`, `suspend`, and `shutdown` call shapes and permission/effect owners in the existing builtin manifest. `server_log` is irrevocable so retries cannot duplicate external log lines; `suspend` uses the existing VM delay field; clean shutdown emits a flagged existing checkpoint request.
- `CheckpointRequest` now distinguishes an ordinary `dump_database` request from shutdown while retaining its no-argument constructor for existing callers.
- `add_verb` and `set_verb_code` now accept either permanent or anonymous receivers and use the corresponding existing `WorldTxn` mutation owner with the same permission checks.
- `MooRuntime.startServer()` and `Operation.STARTUP` now submit `#0:server_started` through the production scheduler with standard root locals. `MooServer.serve()` has not yet called it, and shutdown publication has not yet been threaded into the listener boundary.
- Anonymous and WAIF values have Java-reference identity. Any reachability walk therefore must preserve those exact keys and use visited identity instances while traversing list/map keys and values plus property bodies.
- Blocker: none.
- Next action: implement pending-root finalization and unreachable-anonymous discovery inside the existing runtime/world transaction, then publish checkpoint followed by shutdown and call startup from `MooServer.serve()`.

## 2026-07-20 Anon1 first implementation run

- The existing runtime now removes loaded anonymous pending roots at the suspension boundary and, at terminal task boundaries, computes anonymous reachability from permanent properties through nested lists, maps, anonymous bodies, and referenced WAIF bodies before queuing unreachable identities in stable body order.
- Shutdown effects are emitted after the flagged checkpoint effect and use the existing `ListenerControl` owner. `MooServer.serve()` now submits startup before accepting sockets, and `MooServer.shutdown()` closes the server only after checkpoint publication.
- The first exact Anon1 test run stopped at Java compilation. The anonymous-receiver declaration patch had matched `disassemble` instead of `setVerbCode`, leaving invalid references in both methods.
- The accidental `disassemble` changes have been removed, and the receiver/body declarations now live in `setVerbCode`, where they belong. This remains the same uncommitted source slice; no experiment was kept or abandoned separately.
- Blocker: none.
- Next action: rerun the exact Anon1 production test and use the first runtime assertion or exception as the next decision boundary.

## 2026-07-20 Anon1 managed-row failure

- The corrected exact two-boot Java production test passed, so the plan was reread before running the managed Banteng Anon1 row.
- The managed row still failed because `Anon1.db.new` was absent at its 500 ms assertion.
- A direct run of the exact installed launcher against the same fixture proved the checkpoint is written successfully, but process completion takes about 0.686 seconds. The server log also contains `{E_VERBNF, ""}` from the fixture's caught anonymous verb setup error.
- The diagnostic used one fixed WSL temp directory and removed its database, checkpoint, log, and directory afterward. An earlier timing attempt never launched Banteng because `/usr/bin/time` is unavailable; its fixed temp directory was also removed before the successful Bash built-in timing run.
- The existing test only proved pending identity/body persistence and therefore allowed an anonymous body with no recycle verb. It has now been strengthened to require exactly one `recycle` verb with the exact persisted `server_log("recycle called");` source.
- Blocker: none.
- Next action: rerun the strengthened test to locate whether `add_verb` or `set_verb_code` fails, repair that exact production path, then remeasure startup and remove the 500 ms managed-row miss without changing the conformance row.

## 2026-07-20 Anon1 verb lifecycle corrected

- The strengthened test first proved the anonymous body had zero verbs. The exact missing dependency was `task_perms()`: argument evaluation raised `E_VERBNF` before `add_verb` could run.
- Added the exact pure VM builtin `task_perms`, returning the active programmer object, and included it plus the three startup builtins in the canonical manifest-name test.
- The next test run proved the anonymous body had one `recycle` verb and executable source. Its only failure was an incorrect test expectation that omitted the v17 program's terminating newline; the assertion now matches the exact restored source.
- The strengthened two-boot Anon1 production test now passes, including pending-root identity/body, exact recycle verb/source, and byte-identical second checkpoint. Phase 3 was reread immediately afterward as required.
- Remaining active-family failure: the exact managed row's 500 ms file assertion. The last direct installed-launcher measurement was about 0.686 seconds and included six Java `System.Logger` startup lines before compilation/checkpointing.
- Blocker: none.
- Next action: remove avoidable logging startup latency while preserving irrevocable server-log behavior, rebuild the installed distribution, and rerun the exact managed Anon1 row.

## 2026-07-20 Anon1 managed startup timing after logger removal

- `server_log` now writes its irrevocable line directly to server stderr, and checkpoint publication likewise writes directly, avoiding both `System.Logger` backend initializations while preserving the managed server log.
- The strengthened two-boot test remained green, the installed distribution rebuilt, Phase 3 was reread, and the exact managed Anon1 row still failed its 500 ms file assertion.
- A new direct cold launch measured about 0.848 seconds even without logging backend initialization. The checkpoint and exact fixture log were correct, and the diagnostic directory was removed afterward.
- Therefore the earlier diagnosis that logger initialization governed the 500 ms miss was wrong. Cold JVM/application startup plus database/runtime work remains the active timing surface.
- The conformance row, its 500 ms wait, and the sibling conformance repository remain unchanged.
- Blocker: none.
- Next action: measure startup-oriented JVM flags with the exact installed classpath and fixture; keep only a launcher change that makes the unchanged managed row pass while preserving the production behavior.

## 2026-07-20 Anon1 cold-start bottleneck measurement

- The JVM-flag launcher experiment was rejected and removed before further work: limiting processors, compiler tier, GC, and heap to satisfy the harness delay changed production server semantics and immediately broke the managed setup connection. The tracked launcher is restored.
- Java `-Xlog:startuptime` measured VM creation at about 46 ms, while total cold process time was about 544 ms in that run. JVM creation is not the governing cost.
- Temporary composition-root timing then measured from entry to `Banteng.call()`: v17 database load about 30 ms, `MooServer` constructed by about 83 ms, and `serve()` returned after startup/checkpoint at about 278 ms. Total process time in the same run was about 629 ms.
- Therefore roughly 350 ms occurs before/after `call()`, dominated by CLI/class loading and process teardown rather than v17 parsing, runtime scheduling, or checkpoint writing. The server's actual application work is already within the row's 500 ms delay once `call()` begins.
- The temporary diagnostic database/checkpoint/log/directory were removed. Temporary `Banteng` timing prints are still present only long enough to record this checkpoint and must be removed before any kept code.
- Blocker: none.
- Next action: remove the temporary timing prints, inspect the plan's exact CLI requirements, and replace only the proven cold CLI startup bottleneck if the plan permits it.

## 2026-07-20 Picocli cold-model correction

- The temporary `Banteng.call()` timing prints were removed before the next experiment.
- The controlling plan explicitly assigns `moo.app` ownership to Picocli options and the concrete composition root, so replacing Picocli or adding a launcher-side parser is not authorized.
- Temporary `main` timing measured annotated Picocli command-model construction at about 179 ms and command execution return at about 472 ms from `main`; total cold process time was about 604 ms. Those timing prints and their diagnostic directory were removed/replaced in the current edit.
- Picocli 4.7.7 official documentation confirms annotation discovery is reflective at runtime. Its compile-time codegen generates native-image metadata, but the plan requires `/opt/java/25/bin/java`, so native-image is not the applicable path.
- The exact local Picocli 4.7.7 API provides `CommandSpec.wrapWithoutInspection`, programmatic `OptionSpec` builders, and typed setters. `Banteng` now has one programmatic Picocli model containing the same command name, help/version surface, descriptions, six options, defaults, arity, and custom validation; annotation-based duplicate CLI metadata has been removed.
- This programmatic model is not yet compiled or tested. Existing CLI tests still instantiate the removed annotated model and must be moved to `Banteng.commandLine()` if compilation succeeds.
- Blocker: none.
- Next action: compile the programmatic model, correct only exact API/type errors, update the existing CLI tests to the sole model, and measure the unchanged production launch before rerunning the managed row.

## 2026-07-20 Programmatic Picocli experiment rejected

- The programmatic Picocli model compiled after replacing generic `ISetter` lambdas with direct bindings and supplying primitive initial values. Its CLI unit tests, installed executable smoke test, and strengthened Anon1 lifecycle test all passed together.
- After the required Phase 3 reread, the unchanged managed Anon1 row still failed at the same 500 ms file assertion.
- A direct cold launch of the programmatic model measured about 0.580 seconds. It did not establish a reliable reduction sufficient for the named gate, and the generated checkpoint/log were otherwise correct. The diagnostic directory was removed.
- This CLI rewrite was explicitly intended to reduce the active gate metric and produced no kept gate improvement. It is a rejected experiment within the still-uncommitted Anon1 source slice, not a completed kept slice.
- The `Banteng.java` programmatic CLI changes and corresponding `BantengTest.java` changes must now be fully restored to their pre-experiment tracked state before any new diagnosis.
- Blocker: none.
- Next action: restore those two exact CLI files, rebuild the original annotated distribution, then determine the checkpoint creation time directly rather than using process completion as a proxy.

## 2026-07-20 Restored baseline and unrecoverable C1 diagnostic

- `Banteng.java`, `BantengTest.java`, and `scripts/run_banteng_wsl.sh` have no tracked diff, confirming that both the rejected programmatic-Picocli experiment and rejected launcher flags were fully restored.
- The active tracked worktree remains the single uncommitted Anon1 v17 lifecycle slice; notes and `pyghidra_mcp_projects/` remain untracked and must not be staged.
- A temporary class-load trace showed startup-verb compiler/AST/VM class loading around 0.463-0.512 seconds under logging overhead, checkpoint-effect handling around 0.524 seconds, and v17 checkpoint codec loading through about 0.543 seconds. The trace and its temporary directory were removed after inspection.
- A subsequent direct diagnostic using only `-XX:TieredStopAtLevel=1` was meant to sample checkpoint existence at 400 and 500 ms, but its tool output was lost across context compaction. Its fixed temporary directory `/tmp/banteng-anon1-diag-20260720-12` is absent, so no result can be recovered or claimed; the diagnostic will not be repeated as a substitute for evidence.
- The exact managed Anon1 row remains the active failing authority: its unchanged 500 ms assertion does not yet see `Anon1.db.new`.
- Blocker: none.
- Next action: inspect the active startup/compiler/class-loading path and choose only a source change that removes causally proven startup work while preserving the exact Picocli launcher, JVM command, fixture, row timing, and production semantics.

## 2026-07-20 Managed-runner timing boundary

- The structured investigation record is committed separately as `7e05e11 docs: record Anon1 startup investigation`; no active Anon1 source/test file was staged in that commit.
- The read-only Anon1 subagent could not initialize the protocol role required by its hook, so it inspected no current file, made no change, and supplied no recommendation. Local evidence remains authoritative.
- `scripts/run_managed_wsl.sh` passes the exact launcher template to the sibling conformance runner without an intermediate readiness delay.
- In the current sibling runner, `ManagedServer.start()` copies the database, opens the log, and calls `subprocess.Popen`. It waits for the socket only when `wait_for_port` is true; otherwise it returns immediately after process creation.
- Therefore a startup-repair suite using `wait_for_port=false` will begin its YAML steps while the Java launcher is still in pre-main and application startup. This directly supports the process-launch-overhead theory, but the suite-to-mode selection has not yet been inspected and remains unproven.
- Blocker: none.
- Next action: locate the exact fixture/collection code that starts `startup_repair_anon1.yaml`, prove whether it passes `wait_for_port=false`, then trace the production startup/checkpoint interval before deciding on any source change.

## 2026-07-20 Startup-repair launch proven; diagnostic fixture correction needed

- `startup_repair_anon1.yaml` has no transport-bearing step. `YamlTestRunner._ensure_suite_server_db()` therefore computes `needs_transport=false` and calls `ManagedServer.restart(db_path=desired_db, wait_for_port=false)`.
- The managed restart launches Banteng with `subprocess.Popen` and returns without a readiness wait. The row's 500 ms sleep therefore begins immediately after process creation and includes Bash launcher, JVM, Picocli, database load, server construction, scheduler/compiler/VM work, and checkpoint publication.
- The plan requires the scheduler's default `max(2, availableProcessors())` platform-worker configuration, so worker-count reduction is not a valid source change.
- The first focused class-initialization diagnostic did not start because Windows-to-WSL quoting stripped Bash variables. The second invocation likewise did not enter Bash. A temporary script removed that host ambiguity, but then stopped before Java because `profiles/banteng/stock.json.fixture_path` names the default `Test.db` file, not the startup fixture directory.
- The managed command separately supplies `--server-db-dir src/moo_conformance/_db/startup`; that directory is the exact source from which the runner resolves `Anon1.db`. The diagnostic script must use that exact committed fixture path.
- `/tmp/banteng-anon1-jfrinit-20260720-13` currently exists but contains no copied database, log, checkpoint, or Java result because the script failed at `cp`. It must be removed before retrying the corrected diagnostic.
- Blocker: none.
- Next action: remove that empty fixed temporary directory, correct only the diagnostic script's fixture path to the managed runner's exact startup directory, run the first actual class-initialization diagnostic, then remove the temporary script.

## 2026-07-20 Unrequested JFR initialization isolated

- The corrected focused diagnostic ran the exact installed Java distribution against a disposable copy of the managed `Anon1.db` fixture and produced the checkpoint. Its temporary database, checkpoint, logs, and fixed directory were removed by the script, and the temporary diagnostic script was then deleted from the worktree.
- Class-initialization timestamps placed `TaskSegmentEvent` at 0.361 seconds and `MooParser` at 0.442 seconds, exposing an approximately 81 ms gap before startup-verb parsing. `CheckpointEvent` initialized at 0.521 seconds, already beyond the unchanged row's 500 ms assertion.
- Java 25's exact `jdk.jfr.FlightRecorder` API exposes `isInitialized()`. The current production command does not request a recording, so unconditional custom-event construction was doing material operational instrumentation initialization outside Phase 3's requested launch behavior.
- `PublicationScheduler.executeAttempt()` now constructs/begins/commits `TaskSegmentEvent` only when `FlightRecorder.isInitialized()` is true. Scheduler, transaction, VM, and publication behavior are otherwise unchanged.
- `LambdaMooV17Codec.writeAtomic()` now applies the same guard to `CheckpointEvent`; write, force, atomic move, directory force, cleanup, and emitted event fields remain unchanged when JFR is active.
- Existing `PublicationSchedulerTest` starts a real `Recording`, enables `TaskSegmentEvent`, and exercises the production scheduler, so it covers the guarded scheduler emission path. `JfrEventDefinitionsTest` currently constructs `CheckpointEvent` directly rather than invoking the production codec, so production checkpoint-event preservation is not yet directly covered.
- Blocker: none.
- Next action: add the smallest production-codec recording assertion to the existing JFR event test, run the affected JFR/scheduler/Anon1 tests together, rebuild the installed distribution, then run the unchanged managed Anon1 row. Keep the source change only if both semantic coverage and the named timing gate pass.

## 2026-07-20 JFR guard source attempt rejected

- Guarding `TaskSegmentEvent` and `CheckpointEvent` construction behind `FlightRecorder.isInitialized()` compiled and passed `JfrEventDefinitionsTest`, `PublicationSchedulerTest`, `V17RoundTripTest`, and the strengthened `AnonymousObjectPersistenceTest` together. The production checkpoint-event path was exercised under an active recording during that test run.
- After the required Phase 3 reread, the exact unchanged managed Banteng `startup_repair_anon1.yaml` row still failed because `Anon1.db.new` was absent at 500 ms.
- This was one completed source attempt explicitly intended to reduce the active exact gate. It produced no kept gate improvement, so it has been rejected and fully reversed rather than retained as nearby useful work.
- `PublicationScheduler.java` and `JfrEventDefinitionsTest.java` are back to their tracked state. `LambdaMooV17Codec.java` retains only its pre-existing Anon1 v17 changes; the JFR import, guard, and conditionals are gone.
- The class-initialization measurement remains valid evidence that JFR initialization consumes startup time, but removing that cost alone is insufficient to satisfy the row and therefore is not the fix.
- Blocker: none.
- Next action: update the tracked investigation with this rejected result, then distinguish whether the remaining miss is late atomic checkpoint visibility or another pre-checkpoint startup/class-initialization cost. Do not retry JFR guarding or alter the row, fixture, launcher, JVM command, worker count, or Picocli model.

## 2026-07-20 Atomic checkpoint latency ruled out

- The restored-source installed distribution was rebuilt after the rejected JFR guard so the next measurement could not accidentally execute stale experiment classes.
- A disposable `strace` run captured the exact production checkpoint syscalls. Trace overhead delayed the absolute startup timeline, but checkpoint temporary-file creation through atomic rename took about 42 ms total; the data-file `fsync` took about 1.7 ms and rename about 0.08 ms.
- Atomic force/rename is therefore not the dominant cause of the 500 ms miss. The late surface remains before checkpoint entry: process/Picocli/class initialization, database load, server/runtime construction, startup-verb compilation, VM execution, and publication.
- The diagnostic used the exact committed managed `Anon1.db` fixture, current installed classpath, and `/opt/java/25/bin/java`; its database, checkpoint, log, trace files, fixed temp directory, and temporary script were all removed.
- This was diagnostic evidence only, not a source attempt and not a second no-improvement slice.
- Blocker: none.
- Next action: inspect the restored class-initialization sequence immediately before checkpoint entry and the exact `server_started` source/bytecode path. Prove one redundant production operation before attempting another source change; a second source attempt that does not reduce the exact row would trigger the two-slice stop rule.

## 2026-07-20 Deterministic sibling temp attempt awaiting valid gate

- The restored late class-initialization window proved that `Files.createTempFile` initializes `TempFileHelper`, `SecureRandom`, the JCA provider stack, SUN/native PRNG classes, and POSIX temp-permission machinery immediately before checkpoint output.
- The second convergence source attempt replaces only randomized temp naming with deterministic sibling `<target>.tmp` plus the existing channel's `CREATE`; v17 serialization, data force, atomic replace, directory force, failure cleanup, and JFR event behavior are unchanged.
- `V17RoundTripTest` and the strengthened two-boot `AnonymousObjectPersistenceTest` passed together, and the installed distribution was rebuilt from this source. Phase 3 was reread immediately afterward.
- The subsequent exact managed command did not reach the Anon1 test step. Session setup connected to the default managed server, then raised `BrokenPipeError` while sending `SUFFIX -=!-v-!=-`. This neither passes nor fails the Anon1 500 ms assertion and therefore does not adjudicate the second source attempt.
- The failing setup named managed temp directory `/tmp/moo_conformance_0x9tj602` in the prior failed row, but this run's traceback did not print its current managed temp path. The currently live/recent temp directory must be identified without a broad scan and its server log inspected if preserved.
- Blocker: none.
- Next action: inspect the bounded current `/tmp/moo_conformance_*` candidates and server logs to determine why the default server closed the socket. If the setup failure is unrelated and disposable state is clean, rerun the same exact managed command as the first valid gate attempt; do not alter source meanwhile.

## 2026-07-20 Two-slice exact-convergence stop

- The first managed run after deterministic temp naming failed during unrelated default-server session setup with `BrokenPipeError` and never executed the Anon1 row, so it was correctly treated as inconclusive rather than as a source result.
- The runner cleaned that current temp directory; only older stale managed directories remained, and they were not substituted as evidence. With no source or harness change, the exact managed command was rerun to obtain the required valid gate result.
- The valid run reached `startup_repair_anon1::pending_finalization_is_written_to_output_db` and still failed because `Anon1.db.new` was absent at 500 ms.
- Deterministic sibling temp naming was therefore the second consecutive completed source attempt with no kept reduction on the same exact target. It has been fully reversed: the codec again uses `Files.createTempFile`, and the earlier JFR guard remains absent. `PublicationScheduler.java` and `JfrEventDefinitionsTest.java` are clean; `LambdaMooV17Codec.java` retains only the pre-existing Anon1 v17 slice.
- The tracked investigation now records both rejected attempts and the mandatory stop. No row, fixture, launcher, JVM flag, Picocli model, worker count, or sibling conformance file was changed.
- Blocker: the exact-convergence rule requires stopping before widening after two consecutive no-improvement source slices on Anon1. The active Phase 3 plan remains incomplete at the unchanged managed Anon1 row.
- Next action: commit only the updated investigation record, verify the active source worktree still contains no rejected experiment, then report the exact stop condition and request user direction. Do not continue implementation or widen diagnosis without that direction.

## 2026-07-20 Two-slice rule corrected; Anon1 resumed

- The global exact-convergence wording was corrected outside this repository from `stop before widening` to `do not widen`. The rule forbids switching away from Anon1 after two no-improvement slices; it does not authorize stopping the plan or requiring new user permission.
- `investigations/anon1-managed-startup.md` has been corrected locally to remove the false authorization requirement and to name continued same-target investigation as the next action. That tracked record is modified but not yet committed.
- The active source worktree is unchanged from the pre-investigation Anon1 v17 lifecycle slice. Both rejected startup experiments remain absent.
- Current same-target hypothesis: the fixture's exact `suspend(0)` boundary may unnecessarily initialize Banteng's virtual-thread timer subsystem when an immediate ready-queue reschedule would preserve the observable boundary.
- Fresh Toast source confirms `bf_suspend` returns a suspend package and interpreter handling routes it through `suspend_task()` to `OUTCOME_BLOCKED`. The excerpt has not yet established how zero seconds is queued; no source conclusion or edit has been made.
- Blocker: none.
- Next action: read the exact Toast `bf_suspend`, `suspend_task`, and queued-task scheduling implementation for zero seconds, then compare it with Banteng's `publishSuspension()` implementation. Modify source only if the zero-delay virtual timer is proven unnecessary.

## 2026-07-20 Zero-delay suspension aligned with Toast

- Fresh Toast source proves `bf_suspend` creates a blocked VM and `enqueue_suspended_task` inserts a zero-delay task into ordered `waiting_tasks` with start time `now`. The next `run_ready_tasks()` pass moves it to the runnable background queue; Toast does not create a host timer thread for zero.
- Banteng previously sent every timed suspension, including zero, through `startTimer()`, initializing and starting a virtual thread before requeueing the VM.
- `PublicationScheduler.publishSuspension()` now increments the publication boundary exactly as before, then directly calls the existing `enqueueWake(..., 0)` path for `delaySeconds == 0.0`. Positive delays still use `startTimer()` and non-timed suspensions still use the host-wake path.
- No helper, alternate scheduler, launcher change, JVM flag, worker-count change, fixture change, or conformance change was introduced.
- `PublicationSchedulerTest` and the strengthened two-boot `AnonymousObjectPersistenceTest` pass together, and the installed distribution was rebuilt from this source.
- Blocker: none.
- Next action: reread Phase 3, then run the exact unchanged managed Banteng `startup_repair_anon1.yaml` row. Keep the zero-delay change only if it produces a measured reduction on the active target; otherwise fully revert that change and remain on Anon1.

## 2026-07-20 Zero-delay attempt rejected; L3 same-target review active

- After the required Phase 3 reread, the exact unchanged managed Anon1 row still failed because `Anon1.db.new` was absent at 500 ms.
- The direct zero-delay requeue was explicitly intended to reduce the active metric, produced no kept reduction, and was fully reverted. `src/main/java/moo/runtime/PublicationScheduler.java` is clean again.
- A new suspicion that `FlightRecorder.isInitialized()` itself performed platform-recorder initialization was checked against Java 25 bytecode and was wrong: that method only reads the static `initialized` boolean. The prior guarded-JFR source attempt remains a valid rejected result and must not be retried unchanged.
- The structured investigation has exceeded three failed source attempts, so its required L3 escalation is active without widening beyond Anon1.
- The gauntlet skill's typed `scout/coder/analyst/verifier` roles are unavailable in this collaboration API. A fresh read-only Codex scout and a read-only high-effort Claude architectural review are running as the supported fallback. Neither is authorized to edit source.
- `prompts/anon1-cold-start-review.md` is a temporary untracked external-review prompt. Its result must be evaluated against current source, recorded in the existing investigation, and the prompt removed before the next source slice.
- Blocker: none.
- Next action: wait for both read-only reviewers, compare their concrete causal evidence, then run the required adversarial Claude follow-up against the strongest recommendation. Do not edit source until the review gate identifies one decision-changing test or source owner.

## 2026-07-20 L3 review complete; corrected composed attempt authorized

- The Codex startup scout found no remaining single source change with enough measured latency to close the observed Anon1 gap.
- The high-effort source review confirmed that the stored startup segment must rerun after the irrevocable `server_log` effect; bypassing that retry would violate the production transaction boundary. It identified an untested cumulative hypothesis composed of the three individually insufficient source attempts: JFR guards, deterministic checkpoint-temp creation, and direct zero-delay requeue.
- The required adversarial follow-up returned `TEST`, but rejected a shared `<target>.tmp` because concurrent processes could corrupt or delete each other's work, a predictable file could introduce a symlink hazard, and default POSIX creation could widen database permissions.
- The corrected checkpoint-temp design is a PID-scoped sibling opened with `CREATE_NEW`, with owner-only POSIX permissions when supported. It retains the existing data force, close, atomic replace, directory force, and failure cleanup.
- The JFR guards preserve dynamic recording because `FlightRecorder.isInitialized()` becomes true before recording begins and the custom events register on their first post-activation construction.
- The direct `suspend(0)` path must call the existing synchronized `enqueueWake` method, preserving shutdown checks, ticket ordering, publication, and finalization behavior.
- `investigations/anon1-managed-startup.md` now records the reviews, the rejected shared-temp design, and the exact corrected composed attempt.
- Blocker: none.
- Next action: commit only the updated tracked investigation record, remove the temporary review prompts, then apply the corrected composed source attempt and run its named test authorities before the unchanged managed Anon1 row.

## 2026-07-20 Composed attempt ready for source editing

- The global exact-convergence rule now says `continue the same target`; this replaces the ambiguous `do not widen` wording with an affirmative continuation requirement.
- The corrected L3 decision is committed as `53efbd0` (`docs: authorize corrected Anon1 composed attempt`). Only `investigations/anon1-managed-startup.md` was staged in that commit.
- The active Anon1 implementation remains uncommitted in its pre-existing tracked files. No rejected startup experiment is currently present in `PublicationScheduler.java`, and the codec still uses randomized `Files.createTempFile`.
- Current source design: nullable JFR events guarded by `FlightRecorder.isInitialized()`; PID-scoped checkpoint sibling opened atomically with `CREATE_NEW` and owner-only POSIX attributes when supported; zero-delay suspension through the existing synchronized `enqueueWake`; positive delays retain `startTimer()`.
- `JfrEventDefinitionsTest` currently constructs `CheckpointEvent` manually. It must exercise `LambdaMooV17Codec.writeAtomic()` under the active recording so the guarded production emission path remains authoritative.
- Blocker: none.
- Next action: patch `PublicationScheduler.java`, `LambdaMooV17Codec.java`, and `JfrEventDefinitionsTest.java` exactly as reviewed, then run the four named affected test classes together.

## 2026-07-20 Corrected composed attempt passes Anon1

- `PublicationScheduler` now avoids custom `TaskSegmentEvent` construction until JFR is initialized and routes `suspend(0)` through the existing synchronized `enqueueWake`; positive delays still use `startTimer()`.
- `LambdaMooV17Codec` now avoids custom `CheckpointEvent` construction until JFR is initialized and uses a PID-scoped sibling temporary file opened with `CREATE_NEW`; POSIX filesystems receive `rw-------` at atomic creation.
- `JfrEventDefinitionsTest` now invokes the production codec while a real recording is active instead of manually constructing `CheckpointEvent`.
- `JfrEventDefinitionsTest`, `PublicationSchedulerTest`, `V17RoundTripTest`, and `AnonymousObjectPersistenceTest` passed together under WSL Java 25 in 39 seconds.
- Phase 3 was reread. The unchanged managed Banteng `startup_repair_anon1.yaml` row passed: `1 passed in 5.90s`. This is the first measured reduction on the exact active target, so the composed source attempt is kept pending its full slice gates and commit.
- The exact Phase 3 named Gradle test command then became silent after `Calculating task graph` and remained active for more than ten minutes. The unified process backend cannot send an interrupt to that invocation.
- A read-only WSL process check shows wrapper PID 2513 still sleeping, Gradle daemon PID 340, and two compiler worker daemons. The wrapper has negligible CPU after more than twelve minutes, so the exact gate is hung rather than still progressing normally.
- Blocker: the kept slice cannot be committed until the hung exact gate is terminated cleanly and rerun to completion.
- Next action: terminate only wrapper PID 2513, allow Gradle to cancel its owned build, then rerun the same exact named gate with a timeout derived from the 39-second affected-test baseline. Do not change source or substitute a different gate.

## 2026-07-20 Anon1 kept slice fully gated

- The attempted targeted termination found wrapper PID 2513 already gone. Polling the original process returned its real result: the exact Phase 3 named test gate passed in 12 minutes 11 seconds and stored the configuration cache. No process was killed and no gate was substituted.
- After the required Phase 3 reread, the exact `JAVA_HOME=/opt/java/25 ./gradlew check` gate passed in 29 seconds with the configuration cache reused.
- After the next required Phase 3 reread, exact `git diff --check` passed with no output.
- The unchanged managed Banteng Anon1 row and every source-slice gate are now green. The corrected composed startup changes are a measured kept improvement.
- The current commit scope must include the complete active Anon1 production slice and its tests, including the new `AnonymousObjectPersistenceTest`; it must exclude all untracked notes, prompts, and unrelated artifacts.
- Blocker: none.
- Next action: inspect the complete source diff, stage only the active Anon1 source/test paths, verify the staged set, and commit the kept slice before starting Anon2.

## 2026-07-20 Anon2 row correction authorized

- The complete Anon1 source slice was committed as `3485294` with message `feat: pass Anon1 startup repair`; the Banteng tracked worktree is clean afterward.
- The unchanged Banteng Anon2 managed row failed because the output file was absent at 500 ms.
- The unchanged Toast stock oracle produced `Anon2.db.new`, but it contained `1 values pending finalization` while the conformance row expected `0 values pending finalization`. This proves the row expectation itself is invalid independently of Banteng behavior.
- Work stopped under the issue-invalidity rule until the user explicitly authorized: `fix the row`.
- The sibling `moo-conformance-tests` repository is on `main`, ahead of origin by 29, with no tracked modifications and many pre-existing untracked artifacts. None of those artifacts are in scope.
- Blocker: none.
- Next action: change only `startup_repair_anon2.yaml` from expected count `0` to `1`, run that exact row against Toast stock, and commit only that YAML file in the conformance repository before resuming Banteng Anon2.

## 2026-07-20 Anon2 row fixed; production test is next

- The conformance row now expects Toast's verified `1 values pending finalization` and passes Toast stock in 5.22 seconds.
- Only `startup_repair_anon2.yaml` was committed in the sibling conformance repository as `6f72ad6` with message `fix: match Anon2 pending finalization oracle`.
- The corrected unchanged row also passes Banteng stock in 5.72 seconds. No Banteng source change was needed for the managed assertion.
- The plan still requires `AnonymousObjectPersistenceTest` to run Anon2 for exactly two production boots, reload both checkpoints, prove aliases/cycles, and prove the second checkpoint is byte-identical to the first.
- The exact fixture begins with zero pending roots. `server_started` creates anonymous A and B, gives each a `next` property and recycle verb, links A to B and B to A, and shuts down. One pending root must serialize the reachable two-object cycle; the next boot recycles that pending cycle and creates the same replacement cycle.
- The next test can prove both reloaded worlds directly by locating A and B from their bodies, asserting the single pending root is A, asserting the two `next` values preserve A/B identity with `assertSame`, asserting exact recycle verbs, and comparing first/second checkpoint bytes.
- Blocker: none.
- Next action: verify Banteng tracked state, add only the Anon2 two-boot method to `AnonymousObjectPersistenceTest`, run that test class, reread Phase 3, and keep/commit the slice only if the corrected managed row and full gates remain green.

## 2026-07-20 Anon2 production test exposes anonymous-property gap

- Banteng tracked state was clean before the new test edit. The only tracked change now is the Anon2 method in `AnonymousObjectPersistenceTest`.
- The method runs exactly two production boots, reloads both checkpoints, checks byte identity, and expects the A/B anonymous cycle, single pending root, `next` identity edges, and exact recycle verbs on both boots.
- The targeted test fails at the first structural assertion: only one anonymous body exists instead of A and B.
- The test's captured production stderr shows both boots reach `create(#-1, 1)` and then log `{E_TYPE, ""}` before checkpoint. The single surviving body is blank, owner 3, with no properties or verbs.
- The fixture operation immediately after the successful anonymous `create` is `add_property(a, "next", ...)`. `BuiltinCatalog.addProperty` currently requires an `ObjectValue`, so it returns `E_TYPE` for the anonymous receiver. Serialization is not yet the cause of this failure.
- Existing `WorldTxn.addProperty(long, ...)` owns permanent property creation and inheritance propagation; existing anonymous verb overloads mutate the same `WorldAnonymousObject` body through `replaceAnonymousObject`. No anonymous property overload exists yet.
- After `add_property` is corrected, the same fixture also exercises anonymous `a.name = "A"`, `b.name = "B"`, `a.next = b`, and `b.next = a`; those production property access paths must be checked rather than assuming the builtin alone completes Anon2.
- Blocker: none.
- Next action: inspect the exact builtin property permission logic plus VM/runtime property read/write dispatch, then add anonymous overloads only in the existing `WorldTxn` and dispatch owners required by Anon2. Rerun the same failing test after each causal correction.

## 2026-07-20 Anon2 anonymous property owner in progress

- The existing VM property opcodes dispatch WAIFs separately and otherwise require `ObjectValue`; anonymous receivers currently raise `E_TYPE` on both reads and writes.
- `WorldTxn` now has concrete anonymous overloads in the existing property owner: local/inherited property lookup, `name` and ordinary property reads, `name` and ordinary property writes, and local `addProperty` mutation through the existing `replaceAnonymousObject` transaction path.
- No interface, adapter, sender, alternate world representation, or helper was added. The overloads preserve anonymous identity and transaction write tracking.
- The builtin and VM dispatch sites have not yet been changed, so the integration test remains expected to fail at `add_property` until those exact owners are patched.
- Blocker: none.
- Next action: extend `BuiltinCatalog.addProperty` permission/dispatch handling to the existing anonymous body, then extend `MooVm` GET/SET property dispatch to the new concrete overloads. Run the focused Anon2 test immediately afterward.

## 2026-07-20 Anon2 cycle behavior corrected; test snapshot fix pending

- `BuiltinCatalog.addProperty` now accepts permanent or anonymous receivers using the same owner/wizard/write permission calculation and dispatches to the concrete `WorldTxn` overload.
- `MooVm` GET/SET property opcodes now dispatch anonymous receivers through the same property permission checks and concrete transaction owner used by permanent objects and WAIFs.
- The fixture then completes without `{E_TYPE}` and creates A/B with names, `next` properties, and recycle verbs.
- The next focused failure proved Banteng queued two pending roots for one A↔B component. `MooRuntime.queueUnreachableAnonymousObjects` now marks the graph reached from each selected pending root so one root covers the cycle; restart finalization now traverses that root and removes the whole anonymous component.
- With those changes, first and second checkpoint bytes are identical, each reloaded structure has one pending root and two anonymous bodies, and both identity edges pass.
- The remaining verb-source assertion exposed a test-state mistake: `runUntilFixtureShutdown(firstCheckpoint, second)` mutates the `WorldTxn` stored in `firstCheckpoint`, so inspecting `firstCheckpoint.world()` after boot two observes the new in-memory source rather than the first reloaded checkpoint. The test must retain `firstWorld` immediately after the first read and compare that immutable snapshot with the separately reloaded second snapshot.
- Blocker: none.
- Next action: capture the first reloaded `WorldSnapshot` before boot two, restore the persisted trailing-newline expectations for both reloaded checkpoints, rerun the focused test, then run the whole anonymous persistence class.

## 2026-07-20 Anon2 semantics green; managed timing remains

- The test now retains the first reloaded `WorldSnapshot` before passing its mutable `WorldTxn` into boot two. Persisted trailing-newline expectations are asserted against both separately reloaded snapshots.
- The focused two-boot Anon2 test passes in 11 seconds. After the required Phase 3 reread, the full `AnonymousObjectPersistenceTest` class passes in 6 seconds.
- After the next required Phase 3 reread, the corrected managed Banteng Anon2 row failed because `Anon2.db.new` was absent at the unchanged 500 ms assertion. The row now executes the complete A/B construction rather than aborting early, so the prior pre-slice pass did not exercise the required semantics.
- The server's first `server_log` remains an irrevocable boundary. The scheduler correctly validates and reruns the segment with irrevocable authorization; all later log calls execute in that authorized attempt. This protected transaction behavior cannot be bypassed.
- `MooRuntime.startStored` compiles the same `server_started` source once in the initial attempt and again in the authorized rerun. A source-keyed compiled-program cache is the strongest local semantics-preserving candidate because it removes duplicate pure work without removing the rerun or moving the transaction boundary.
- A read-only Codex subagent is independently ranking the current Anon2 cold path. No additional source edit will be made until its result is compared with the compile-cache hypothesis.
- Blocker: none.
- Next action: wait for the scout, then either test the smallest evidence-backed same-target optimization against the focused Java test and unchanged managed row or continue investigation if no source owner is justified.

## 2026-07-20 Anon2 compile-cache attempt kept

- The read-only scout independently ranked duplicate compilation of the full 28-line `server_started` body across the required irrevocable retry as the largest removable Anon2 operation. It ranked `set_verb_code` validation, anonymous body replacement, reachability, and v17 writing as required or smaller.
- `MooRuntime.startStored` now uses a thread-safe exact-source `BytecodeProgram` cache. A cache miss creates its own `MooCompiler`, avoiding concurrent access to the compiler's mutable lowering state; source edits naturally use a different key.
- Interactive `.program` validation still constructs a fresh compiler and does not populate the execution cache before the world source is changed.
- The scheduler still validates, authorizes, and reruns the complete transaction after the first irrevocable `server_log`; only the second pure compilation becomes a lookup.
- `AnonymousObjectPersistenceTest` and `PublicationSchedulerTest` pass together in 32 seconds. After the required Phase 3 reread, the unchanged corrected managed Anon2 row passes in 5.74 seconds.
- This is a measured kept improvement on the exact active target. No row, fixture, harness, launcher, JVM flag, Picocli model, worker count, or transaction boundary changed.
- Blocker: none.
- Next action: run the exact Phase 3 named test gate, reread Phase 3, run exact `gradlew check`, reread Phase 3, run `git diff --check`, inspect/stage only the Anon2 source/test paths, and commit before starting Anon3.

## 2026-07-20 Anon2 committed

- The exact Phase 3 named test gate passed in 7 seconds, the full exact `gradlew check` gate passed in 29 seconds, and `git diff --check` passed. Phase 3 was reread after each substantial passing gate as required.
- The audited Anon2 diff contained exactly five paths: `BuiltinCatalog`, `MooRuntime`, `MooVm`, `WorldTxn`, and `AnonymousObjectPersistenceTest`.
- Those five paths were staged, the staged whitespace check passed, and the slice was committed as `a71d9ad` with message `feat: preserve Anon2 anonymous cycles`.
- The Banteng tracked worktree is clean after commit; only pre-existing untracked notes, prompts, and tool artifacts remain.
- The sibling conformance correction remains committed as `6f72ad6`.
- Blocker: none.
- Next action: remain in Phase 3 and move to Anon3. Read the exact Anon3 row and fixture, verify the row against Toast stock, run Banteng as the baseline, then add the plan-required two-boot production persistence assertions before changing production source.

## 2026-07-20 Anon3 row fixed; production test pending

### Focused production-test result

- Added the planned `anon3LeavesNoAnonymousGarbageAcrossTwoProductionBoots` test in `AnonymousObjectPersistenceTest`; it performs exactly two production boots, compares both output checkpoints byte-for-byte with `Anon3.db`, and asserts both anonymous-object tables and pending-finalization lists are empty after reload.
- The focused WSL Gradle run failed at the first byte-identity assertion: input length 1083 bytes, produced checkpoint length 1180 bytes.
- Current tracked state is only the intended Anon3 test modification. Existing `notes-*.md`, `prompts/`, and `pyghidra_mcp_projects/` paths remain untracked and untouched.
- Existing runtime behavior finalizes already-pending anonymous objects on `SUSPENDED`, but discovers newly unreachable anonymous objects only on `RETURNED` or `ERRORED`. `queueUnreachableAnonymousObjects()` scans permanent world properties and pending roots, not suspended VM snapshot values.
- The precise blocker under investigation is the correct Toast-backed suspended-task root set and collection timing: the anonymous object must remain live at the first suspend while held in VM locals, then be recycled and absent from the checkpoint after those locals are cleared at the second suspend.
- Next action: inspect the existing `VmSnapshot` value-bearing fields and ToastCore's collection path, then make the smallest production correction in the current Anon3 slice and rerun the focused test.

### Suspended-root findings

- `PublicationScheduler.executeSegment()` calls `runtime.publishVmState(state, taskPlayer)` immediately after VM execution and before it snapshots and returns a suspended boundary. The existing `MooRuntime.publishVmState` boundary therefore owns the live `VmState` needed for anonymous reachability.
- `VmSnapshot` exposes value roots in `initialLocals`, every frame's locals and operand stack, index collections/keys, loop value lists, frame receivers, optional return/error/finally values, fork locals, pending-builtin arguments/receiver/task local/callers, and the task-local value.
- The exact `~/src/toastcore` checkout is clean but data-only: it contains `README.md`, `changelog.txt`, and `toastcore.db`, not engine source or the Anon3 fixture. No alternate Toast checkout was substituted.
- Anon1's fixture deliberately leaves the anonymous object in live task locals when it calls `shutdown()`, and its corrected conformance row requires one pending root in the checkpoint. Anon3 clears both `l` and `o`, then suspends before shutdown; its corrected row and plan require no pending root and a byte-identical checkpoint.
- Banteng currently has no separate recycle-verb dispatch in `MooRuntime`; existing anonymous lifecycle code only queues unreachable identities on terminal return/error and removes already-pending graphs on suspend.
- Next action: define reachability from the current suspended snapshot using the existing recursive marker, queue only identities not reachable from permanent or VM roots, finalize that newly unreachable graph at the Anon3 suspend boundary, and verify Anon1/Anon2 remain unchanged.

### Anon3 production correction verified

- Refined the Anon3 test assertion order so empty pending/body state is checked before byte identity. The focused baseline then failed at the first pending-finalization emptiness assertion, confirming that the 97-byte expansion was one queued anonymous graph rather than a serializer-only difference.
- Changed only `MooRuntime`'s existing anonymous lifecycle owner: on suspension it finalizes old pending state, scans every value-bearing durable field in the current `VmSnapshot` as a live root, queues unreachable anonymous graphs, and finalizes those newly unreachable graphs before checkpoint effects are drained. Terminal return/error behavior still queues without task roots, preserving shutdown-held locals as Anon1 requires.
- Focused `anon3LeavesNoAnonymousGarbageAcrossTwoProductionBoots` passed in 33 seconds. Full `AnonymousObjectPersistenceTest` passed in 7 seconds, preserving Anon1 and Anon2.
- The corrected managed Banteng `startup_repair_anon3.yaml` row passed 1/1 in 6.03 seconds through the WSL login environment. A preceding direct `wsl.exe --exec` attempt did not run the suite because it omitted the login-shell `uv` path; no product result was inferred from it.
- Active tracked diff is the intended `MooRuntime.java` and `AnonymousObjectPersistenceTest.java` Anon3 slice. No known functional blocker remains.
- Next action from the reread Phase 3 plan: run the exact five-class Phase 3 Java gate, reread the plan after it passes, run exact `gradlew check`, reread again, run `git diff --check`, audit/stage only the two Anon3 paths, and commit the kept slice before starting Anon4.

### Anon3 committed

- Exact five-class Phase 3 Java gate passed in 7 seconds.
- Exact `JAVA_HOME=/opt/java/25 ./gradlew check` passed in 31 seconds.
- `git diff --check` and staged `git diff --cached --check` passed.
- Audited and staged only `src/main/java/moo/runtime/MooRuntime.java` and `src/test/java/moo/persistence/AnonymousObjectPersistenceTest.java`.
- Committed the kept Anon3 slice as `1c349d6 feat: collect Anon3 suspension garbage` (82 insertions, 1 deletion across the two intended files).
- Post-commit branch state is `master...origin/master [ahead 178]` with no tracked modifications. Existing notes and tool artifacts remain untracked and untouched.
- Next unchecked plan target is Anon4: inspect its exact committed v17 fixture and conformance row, validate the row against Toast stock, then add the required three-production-boot test before changing production code.

### Anon4 fixture correction in progress

- Inspected the committed `Anon4.db` and `startup_repair_anon4.yaml`. The fixture has three startup branches: create a two-anonymous-object chain and recycle the second; clear the permanent `$one` root and run GC; then delete the property and run GC. The plan requires exactly three production boots and the final checkpoint byte-identical to input.
- The original Toast-stock row failed because `$one is valid` was outside the runner's post-start log window. The user authorized removing both invalid startup-log assertions while retaining the durable checkpoint assertion.
- With the log assertions removed, Toast stock proved the remaining `Anon4.db.new` assertion was also too early at the original 500 ms. The user then explicitly authorized fixing the fixtures to be valid.
- The same corrected row still found no checkpoint at 1000 ms or 2000 ms. Those are revisions inside the same uncommitted fixture-change attempt, not separate slices.
- Current sibling tracked diff is only `src/moo_conformance/_tests/server/startup_repair_anon4.yaml`: both invalid log assertions removed and its existing wait raised to 4000 ms. No sibling artifacts were touched.
- Next action: validate the 4000 ms row against Toast stock. If the checkpoint exists, audit and commit the single row; if not, use the result to determine whether the fixture never reaches shutdown rather than inventing a longer wait outside its existing five-second test timeout.

### Anon4 four-second result and diagnostic correction

- Toast stock still produced no `Anon4.db.new` after the row's wait was raised to 4000 ms. This disproves a credible wait-only repair inside the row's existing timing contract; the row remains one uncommitted fixture-change attempt.
- The checked-in stock profile names `/root/src/toaststunt` at exact commit `aecc51e9449c6e7c95272f0f044b5ba38948459e` and binary checksum authority. That exact checkout is available; its tracked state is clean with pre-existing untracked build/debug artifacts.
- A retained diagnostic intended for a unique `/tmp` directory was malformed because PowerShell stripped Bash variables in the command payload. Toast therefore loaded `/Anon4.db`, not the intended temporary path. The malformed run is not being treated as fixture authority.
- The malformed run did show only initial fixture logs before its 15-second timeout, followed by signal-driven dumping; this is a clue, not proof, that the first `suspend(0)` may not be resumed in that launch shape.
- The malformed run created exactly `/Anon4.db` (2040 bytes) and `/Anon4.db.new` (2091 bytes). Both files were removed immediately; absence verification is still pending. No repository path was deleted or modified by that cleanup.
- Current sibling row remains: two invalid `assert_log` steps removed, wait temporarily 4000 ms, durable file assertion unchanged.
- Next action: verify the two accidental WSL-root files are absent, inspect `YamlTestRunner.run_suite_setup` to understand the exact managed canned-DB start/resume behavior, then correct the Anon4 fixture itself rather than extending an invalid wait.

### Anon4 port-probe hypothesis disproved

- Verified `/Anon4.db` and `/Anon4.db.new` from the malformed diagnostic were absent.
- The runner restarts transport-free canned-DB suites with `wait_for_port=false`; this prompted a hypothesis that Anon4's first `suspend(0)` needed the socket event generated by `_wait_for_port`.
- Ran the unchanged committed Anon4 fixture through the exact pinned Toast launcher in `/tmp/banteng-anon4-probe-20260720` on confirmed-free port 48888, then opened and closed one TCP connection.
- Toast accepted and closed the connection but still emitted none of the post-suspend `$one` lines and did not write `Anon4.db.new` after another five seconds. The port-probe hypothesis is disproved.
- Terminated exact Toast PID 18989 normally. Toast then wrote a signal-driven dump containing the still-suspended startup state; that dump is diagnostic-only and is not fixture authority.
- The current evidence is now causal: the committed `Anon4.db` reaches its first `suspend(0)` but the pinned Toast engine never resumes that task in this runtime/profile, so no wait value can make the YAML's shutdown-checkpoint assertion valid.
- Current sibling tracked change is still only the Anon4 YAML row (log assertions removed, temporary 4000 ms wait). The retained `/tmp/banteng-anon4-probe-20260720` diagnostic directory contains only copied/diagnostic artifacts and must be removed after exact path verification.
- Next action: inspect the pinned Toast `enqueue_suspended_task` and main task loop plus the fixture provenance to identify whether the committed canned DB encodes an invalid `suspend(0)` boundary for this engine. Then repair the fixture and its SHA record directly, validate Toast stock, and only then commit the row/fixture correction.

### Anon4 fixture incompatibility proven and repaired

- The signal dump after the retained run had `0 suspended tasks` and a newly added `#0.one` property referencing anonymous `#4`; therefore `suspend(0)` did resume. Execution aborted after `add_property(#0, "one", create(...))` and before the nested `two` property or name assignments.
- Exact pinned Toast source proves why: `bf_add_prop` is registered with `TYPE_ANY` but rejects the receiver unless `obj.is_obj()`, which excludes anonymous-object values. The canned statement `add_property($one, "two", ...)` is incompatible with this pinned engine and can never reach fixture `shutdown()`.
- `Anon4.db` itself is checksum-correct and was imported in `e3b6d0e test: own startup database fixtures`; this was an upstream-artifact incompatibility, not local corruption or a timing problem.
- Patched only the Anon4 program: define temporary property `two` on permanent parent `#1`, create `$one` as an anonymous child of `#1`, assign `$one.two` to the second anonymous object, and delete `#1.two` in the third-boot cleanup branch. This preserves the anonymous chain while allowing the final third checkpoint to restore the original permanent world.
- The sibling change attempt now includes the authorized YAML assertion removal/wait correction plus the repaired `Anon4.db`. Its SHA record has not yet been updated, and no validation has yet run on the repaired fixture.
- Next action: restore the YAML wait from the diagnostic 4000 ms to 500 ms, compute and update only Anon4's checksum entry, run the corrected row against Toast stock, then perform an exact three-boot Toast chain and final-byte-identity validation before committing the three fixture paths.

### Repaired Anon4 Toast validation

- Restored `startup_repair_anon4.yaml` to its original 500 ms wait; only the two invalid post-start log assertions remain removed.
- Repaired Anon4 SHA-256 is `d80b429959e2f1cbc173ce18eecd99bde8055af03d6a106b5ad102ad6843db0e`; updated only its line in `startup-fixtures.sha256`.
- Corrected Anon4 row passed Toast stock 1/1 in 5.18 seconds at the original 500 ms wait.
- Reread the Phase 3 plan. Its exact lifecycle invariant remains three Anon4 production boots with the third checkpoint byte-identical to the input.
- Ran the exact pinned Toast launcher sequentially in `/tmp/banteng-anon4-threeboot-20260720`, each checkpoint copied as the next input: boot one exited 0 in 3.37 seconds, boot two exited 0 in 3.22 seconds, and boot three exited 0 in 3.37 seconds.
- Final byte comparison between repaired committed input and `boot3.db.new` is pending. No claim of three-boot invariant success has been made yet.
- Current sibling tracked slice is exactly three paths: repaired `Anon4.db`, its SHA line, and the YAML row with invalid log assertions removed. Diagnostic `/tmp` directories remain outside repositories pending verified cleanup.
- Next action: compare `boot3.db.new` byte-for-byte with the repaired fixture, inspect all three checkpoints' anonymous/pending summaries, then audit/stage/commit only the three sibling paths if exact validation passes.

### Repaired Anon4 exact invariant staged

- Exact `cmp` between repaired `Anon4.db` and the third Toast checkpoint returned 0. Both SHA-256 values are `d80b429959e2f1cbc173ce18eecd99bde8055af03d6a106b5ad102ad6843db0e`.
- Checkpoint lifecycle headers were: boot one `0 values pending finalization` (2177 bytes), boot two `1 values pending finalization` (2181 bytes), boot three `0 values pending finalization` (2075 bytes). Boot three exactly restores the repaired 2075-byte input.
- A malformed summary loop caused by PowerShell stripping Bash `$f` variables was discarded; each literal checkpoint path was then read separately and produced the values above.
- Verified both exact `/tmp/banteng-anon4-probe-20260720` and `/tmp/banteng-anon4-threeboot-20260720` paths with `readlink -f`, removed only those diagnostic trees, and verified both absent.
- `sha256sum -c startup-fixtures.sha256` passes for all 11 committed startup fixtures. Sibling `git diff --check` passes.
- Forced textual diff audit of `Anon4.db` shows only the intended parent-property chain repair and third-boot deletion. YAML diff only removes the two invalid log assertions; checksum diff only changes Anon4's line.
- Staged exactly three sibling paths: `Anon4.db`, `startup-fixtures.sha256`, and `startup_repair_anon4.yaml`. Pre-existing untracked sibling artifacts remain untouched.
- Next action: verify staged diff/check, commit this corrected Anon4 fixture slice, confirm sibling tracked-clean state, then return to Banteng and add the plan-required exact three-production-boot Anon4 test before changing Banteng production code.

### Anon4 fixture committed; Banteng test compile correction pending

- Staged check passed and committed the three-path sibling fixture correction as `18e25fb fix: make Anon4 fixture executable`. Sibling is now `main...origin/main [ahead 32]` with no tracked changes.
- Banteng remained `master...origin/master [ahead 178]` with no tracked changes before the Anon4 test edit. Removed only the newly generated untracked `C:\Users\Q\code\banteng\server.log`, proven by timestamp/content to belong to the malformed PID 18922 diagnostic.
- Added `anon4PreservesThenCollectsAnAnonymousChainAcrossThreeProductionBoots` to the existing `AnonymousObjectPersistenceTest`. It asserts first-boot anonymous chain state, second-boot sole pending-root identity and body, third-boot removal of temporary permanent properties/anonymous state, and final byte identity with the repaired input.
- The first focused Gradle run did not execute production. `compileTestJava` failed only because NullAway treats five `Map.get()` results as nullable: permanent objects #0/#1 and anonymous bodies retrieved from maps.
- This is a test-compilation defect inside the same uncommitted Anon4 slice, not a product result or no-improvement slice. No production source has been changed for Anon4.
- Next action: use `Objects.requireNonNull` at those five exact lookups, rerun the focused method, and treat the resulting runtime assertion as the actual Banteng baseline.

### Anon4 Banteng runtime attempt in progress

- Added `Objects.requireNonNull` only at the five NullAway-rejected map lookups. The focused test then compiled and established the real baseline: first production boot did not reach shutdown within five seconds.
- Captured stderr showed the fixture reached both validity logs but Banteng printed `$one is invalid` and `$one.two is invalid`. Existing `valid()` accepted only permanent `ObjectValue`; existing `recycle()` also rejected anonymous values with `E_TYPE`, aborting the fixture before shutdown.
- Pinned Toast source confirms `valid(anonymous)` tests body validity and `recycle(anonymous)` checks control, runs an optional recycle verb, then destroys the anonymous body. The active fixture's recycled child has no recycle verb.
- Patched only existing `BuiltinCatalog.valid` and `BuiltinCatalog.recycle`: anonymous validity uses `world.anonymousObject`, and controlled anonymous recycle removes the body directly and returns zero. Permanent-object behavior remains on the existing staged recycle path; non-object values still return `E_TYPE`.
- The next focused run compiled but again timed out at the shared shutdown helper after 57 seconds. The exact boot call site and captured fixture output are not yet inspected, so no causal claim is recorded.
- Current Banteng tracked slice is exactly `BuiltinCatalog.java` plus the new Anon4 method in `AnonymousObjectPersistenceTest.java`. No other production path is changed.
- Next action: read the focused XML stack and stderr to identify which of the three boots stopped and the last successful fixture operation, then continue this same Anon4 slice.

- The original Anon3 row failed Toast stock because `assert_log` reads only log content after the row offset, while the requested `recycle called` message belongs to managed startup outside that observable window.
- The user explicitly authorized `fix it`. Only the invalid `assert_log` step was removed; the unchanged 500 ms wait and checkpoint assertion remain.
- The corrected row passes Toast stock in 5.22 seconds and was committed alone in the conformance repository as `a160590` with message `fix: observe Anon3 checkpoint state`.
- The corrected Banteng row currently fails because `Anon3.db.new` is absent at the 500 ms assertion.
- The Anon3 input is v17 with zero pending values and no anonymous bodies. Its `server_started` creates one anonymous object held in local list `l`, adds a recycle verb, then clears both locals, suspends again, and shuts down. Both required checkpoints must be byte-identical to the original input, proving the unreachable body is finalized before checkpoint and leaves no evidence.
- Blocker: none.
- Next action: add exactly the two-boot Anon3 method to `AnonymousObjectPersistenceTest`, assert each separately reloaded checkpoint has no pending or anonymous bodies, and compare both output byte arrays to the committed input before diagnosing the managed timing miss.

### Anon4 fixture and Banteng slice validated

- The committed conformance fixture is now executable on pinned Toast: its inherited `two` property is defined on permanent parent #1, the first boot creates anonymous A and B, the second boot drops the permanent root and requests collection, and the third boot removes both temporary permanent properties. The repaired input SHA-256 is `d80b429959e2f1cbc173ce18eecd99bde8055af03d6a106b5ad102ad6843db0e`.
- Toast stock passed the corrected managed Anon4 row. Three manual Toast boots produced pending-finalization counts 0, 1, and 0, and the third checkpoint was byte-identical to the repaired input. The sibling conformance change is committed as `18e25fb fix: make Anon4 fixture executable`.
- Banteng boot one originally aborted because `valid()` and `recycle()` accepted only permanent object references. The active production slice extends those existing methods to anonymous values without changing the permanent-object path.
- Banteng boot two then aborted at missing `run_gc()`. Pinned Toast defines it as zero-argument, wizard-only, sets a deferred collector flag, and returns no value. Banteng already performs the corresponding reachability collection at VM publication boundaries, so the active slice adds only that exact builtin manifest entry with zero result.
- The focused three-boot Anon4 production test now passes. The complete `moo.persistence.AnonymousObjectPersistenceTest` class also passes in WSL on Java 25.
- Current tracked Banteng slice remains exactly `src/main/java/moo/builtin/BuiltinCatalog.java` and `src/test/java/moo/persistence/AnonymousObjectPersistenceTest.java`; notes remain untracked and must not be staged.
- Blocker: none.
- Next action: reread Phase 3 as required after the passing substantial targeted run, then execute the corrected Banteng Anon4 managed row and the exact named Phase 3 gates before committing this kept source slice.

### Anon4 gates complete, pending commit

- Corrected Banteng managed Anon4 row passed 1/1 in 5.89 seconds through the production server/checkpoint path.
- The exact Phase 3 named Java test command passed.
- The first exact `JAVA_HOME=/opt/java/25 ./gradlew check` run found one integration omission: `BuiltinCatalogTest.REACHABLE_NAMES` still expected 53 entries after `run_gc` made 54 reachable. Added only `run_gc` to that existing contract set.
- The rerun of the exact `check` command passed all 216 tests in one minute. The exact `git diff --check` gate also passed.
- Final audited tracked slice is exactly three files: `BuiltinCatalog.java`, `BuiltinCatalogTest.java`, and `AnonymousObjectPersistenceTest.java`. All notes and other untracked artifacts remain untouched.
- Blocker: none.
- Next action: stage exactly those three tracked files, verify the staged diff, commit the kept Anon4 slice, then reread Phase 3 and begin Anon5 as the next incomplete anonymous fixture.

### Anon4 committed; Anon5 fixture repair in progress

- Staged exactly the three audited Anon4 Banteng paths and committed them as `fbe27aa feat: pass Anon4 anonymous chain lifecycle`. Banteng is now ahead 179 with no tracked changes before beginning Anon5.
- The committed Anon5 managed row passed Toast stock, but a direct pinned-Toast boot exposed that the fixture itself was invalid: Toast logged `E_TYPE` at `server_started` line 12 while executing `add_property($one, "two", ...)`. The row passed only because it asserted a startup banner and generic zero-pending output, not the intended graph.
- Repaired only `Anon5.db`: define `two` and `foo` on permanent parent #1, create both anonymous values with parent #1, and assign their inherited property values. This preserves the intended permanent root -> anonymous A -> map -> anonymous B -> self-reference topology without calling `add_property` on anonymous receivers.
- Updated the Anon5 fixture SHA-256 to `53385a37a8175871bf51414529ae3d905ce43eefa5f52c3b70678c47fd1d7b71`. `sha256sum -c startup-fixtures.sha256` passes for every startup fixture.
- Current sibling tracked slice is exactly `Anon5.db` and `startup-fixtures.sha256`; no Banteng tracked edits exist.
- Blocker: none.
- Next action: run the repaired row on pinned Toast, then run three isolated Toast boots and inspect each checkpoint for the exact anonymous graph, zero pending roots, and second/third byte identity before committing the sibling fixture slice.

### Anon5 topology correction before Banteng implementation

- The first repaired fixture passed its row and three Toast boots with identical checkpoints and zero pending roots, and was committed as sibling `e38c350`. Raw checkpoint inspection then showed the outer `create(..., 0)` result is a permanent object and the inner `create(..., 1)` result is the sole anonymous object.
- Pinned Toast `bf_create` confirms an integer second argument is the anonymous flag; zero creates a permanent object and one creates an anonymous object. The original `E_TYPE` therefore came from adding `foo` to the inner anonymous object, not adding `two` to the permanent outer object.
- The first repair was executable but unnecessarily reparented the permanent outer object and made `two` inherited. Corrected the same fixture slice to preserve the original outer object: create it with parent #-1, add its `two` property directly, define only `foo` on permanent parent #1, create the inner anonymous object with parent #1, and assign its inherited self-reference.
- The corrected fixture hash is `bb268ce347ae9be1f2d68bed58d086c5a0139481e8c17ce7dfe2156f205792bf`; the checksum registry still contains the prior repair hash and must be updated next.
- Current sibling state is one commit plus an uncommitted correction in the same Anon5 fixture target. No Banteng tracked files are changed.
- Blocker: none.
- Next action: update the Anon5 checksum entry, rerun all fixture hashes, the Toast managed row, and three isolated Toast boots; verify the graph and byte identity, then amend `e38c350` before beginning Banteng code.

### Minimal Anon5 fixture repair verified

- Updated the registry to corrected fixture SHA-256 `bb268ce347ae9be1f2d68bed58d086c5a0139481e8c17ce7dfe2156f205792bf`; all startup hashes pass.
- The corrected fixture passes the Toast stock managed row 1/1 in 5.21 seconds.
- Three fresh direct boots on pinned Toast completed without any caught error. Each checkpoint has 0 pending roots, 5 permanent objects, and 1 anonymous object. Boot 1, boot 2, and boot 3 are byte-identical with SHA-256 `bcaaaadc4737e2b04411946c83db80dacfd28f16622d71cf5203210c660a6b58`, satisfying the required second/third identity.
- The repaired graph preserves the original outer permanent object created with parent #-1. `#0.one` points to it; its direct `two` property maps `"foo"` to the one anonymous object; that anonymous object inherits `foo` from permanent parent #1 and its override points to itself. The anonymous body has no verbs and remains reachable, so it is never pending finalization.
- The two corrected sibling paths are staged; all unrelated untracked files remain unstaged. Commit `e38c350` still contains the superseded first repair and must be amended with this staged correction.
- Blocker: none.
- Next action: verify the staged two-path diff, amend `e38c350` without changing its message, confirm sibling tracked cleanliness, then add the exact three-boot Anon5 production test in Banteng.

### Anon5 production test passes; managed log assertion invalid

- Amended the sibling fixture commit to `2bf3298 fix: make Anon5 fixture executable`; sibling tracked state was clean afterward.
- Added only the plan-required three-boot Anon5 method to `AnonymousObjectPersistenceTest`. It checks each checkpoint has one anonymous body and no pending roots; `#0.one` references the permanent outer object; its `two` map references the anonymous body; the anonymous inherited `foo` value is the same identity; body metadata and empty verbs persist; and checkpoint two equals checkpoint three byte-for-byte.
- The focused production test passed in WSL on Java 25 without any production-source change. Banteng already implements this corrected lifecycle.
- The Banteng managed row failed only at its `assert_log`: after the fixed wait, the harness log offset captured the first banner line but not the required second banner line. This assertion is timing-dependent and does not validate the checkpoint topology.
- Removed only the invalid two-line `assert_log` step from `startup_repair_anon5.yaml`; the 500 ms wait and `Anon5.db.new` zero-pending assertion remain.
- Current tracked state: Banteng has only `AnonymousObjectPersistenceTest.java` modified; sibling has only `startup_repair_anon5.yaml` modified. Neither is staged.
- Blocker: none.
- Next action: rerun the corrected row on Toast stock and Banteng stock, then commit the sibling row correction before running the exact Phase 3 Java gates for the Banteng Anon5 test slice.

### Anon5 managed timing repair validated

- Removing the invalid log assertion left the real managed failure visible: at 500 ms Banteng had not yet published `Anon5.db.new`.
- A direct exact `run_banteng_wsl.sh` invocation measured 0.50 seconds from process launch through checkpoint and clean fixture shutdown, proving the 500 ms wait sat exactly on the completion boundary.
- Changed only the existing row wait from 500 ms to 750 ms, providing 250 ms modest slack over the observed baseline. No retry, new gate, or alternate assertion was added.
- The corrected row passes Toast stock 1/1 in 5.55 seconds and Banteng stock 1/1 in 6.00 seconds.
- The single YAML row repair is staged in the sibling repo and passes staged diff validation. Banteng still has only the Anon5 production test modified and unstaged.
- Blocker: none.
- Next action: commit the staged sibling row repair, then reread Phase 3 and run the exact named Java test gate, full `check`, and `git diff --check` for the Banteng Anon5 test slice.

### Anon5 complete and committed

- Committed the corrected sibling row as `4e84ef2 fix: wait for Anon5 checkpoint`.
- The exact Phase 3 named Java gate passed, the full `JAVA_HOME=/opt/java/25 ./gradlew check` passed, and `git diff --check` passed.
- Audited and staged only `AnonymousObjectPersistenceTest.java`, then committed the Banteng test slice as `fc8914c test: cover Anon5 recursive lifecycle`.
- Anon5 required no Banteng production-source change: the corrected three-boot fixture already passed the production scheduler, VM, `WorldTxn`, and checkpoint path. The durable missing artifact was the explicit plan-required lifecycle test.
- Blocker: none.
- Next action: verify both repositories have no tracked changes, reread Phase 3, and begin the final single-boot Anon6 family by validating its committed fixture and managed row on pinned Toast before changing Banteng.

### Anon6 implementation gates pass

- Both repositories were tracked-clean before Anon6. The existing Anon6 fixture intentionally contains one invalid anonymous value pending finalization; its Toast stock managed row passed without fixture changes.
- Added exactly the plan-required one-boot Anon6 method to `AnonymousObjectPersistenceTest`. It starts the production server from the committed input, waits for fixture shutdown/checkpoint, reloads the output, and proves 0 pending roots, 0 anonymous bodies, and all 4 permanent objects retained.
- The focused Anon6 production test passed. The existing Banteng stock managed row also passed 1/1 without a row or production-source change.
- The exact Phase 3 named Java gate passed, and the full exact `JAVA_HOME=/opt/java/25 ./gradlew check` passed.
- Current Banteng tracked slice is only the unstaged Anon6 addition in `AnonymousObjectPersistenceTest.java`. Sibling conformance has no tracked changes.
- Blocker: none.
- Next action: reread Phase 3 after the passing full check, run `git diff --check`, audit/stage/commit only the Anon6 test file, then execute the plan's full exact managed path list on Toast stock and Banteng stock now that all six anonymous lifecycle tests exist.

### Phase 3 exact managed gate baseline

- `git diff --check` passed; the audited one-file Anon6 test slice was committed as `e5f60b8 test: cover Anon6 invalid pending cleanup`.
- The exact full Phase 3 `toast stock` path list passed with 548 passed and 4 skipped in 44.62 seconds.
- The identical full `banteng stock` path list failed with 72 failed, 476 passed, and 4 skipped in 66.13 seconds.
- The first causal family is `control_flow` fork behavior: named fork syntax is rejected by the parser; float-delay fork times out; nonnumeric delay unexpectedly succeeds; zero-delay fork fails. Phase 3 requires fork parsing and a serializable fork-request VM outcome, while task IDs/queueing/named binding are explicitly Phase 4, so the exact expected boundary must be checked against the actual row definitions before implementation.
- Three `error_authority` failures and most `index_and_range` caret/dollar/range failures followed the fork timeout and may be transport response contamination; later looping/scatter/splice/try families passed. They are not yet independent baselines.
- Anon4 and Anon6 failed only because their checkpoint files were absent at their fixed row waits during the loaded full run; each row had passed individually. Anon5's measured 750 ms row continued to pass.
- Both repositories have no tracked changes after the committed slices. Notes remain untracked.
- Blocker: none.
- Next action: remain on the first `control_flow` target, inspect the exact fork rows plus parser/VM tests and Toast behavior, run the control-flow suite alone on Banteng to establish independent failures, and implement only the Phase 3 parser/snapshot boundary justified by that evidence.
