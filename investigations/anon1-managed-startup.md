# Investigation: Anon1 managed startup

## Facts (verified)

- The unchanged `startup_repair_anon1.yaml` row checks for `Anon1.db.new` after a 500 ms wait, and the managed Banteng run does not find the file.
- The strengthened two-boot `AnonymousObjectPersistenceTest` passes through the production runtime and proves the v17 pending root, anonymous body identity, exact `recycle` verb source, and byte-identical second checkpoint.
- A direct polling run with the restored annotated launcher did not see the checkpoint at 400 or 500 ms and did see it at 600 ms.
- Timing inside `Banteng.call()` measured roughly 30 ms through v17 load, 83 ms through `MooServer` construction, and 278 ms through startup execution and checkpoint completion. Total process time in that diagnostic was roughly 629 ms.
- A class-load trace, with its own logging overhead, placed startup compiler/AST/VM loading around 0.463-0.512 seconds, checkpoint-effect handling around 0.524 seconds, and v17 checkpoint-codec loading through about 0.543 seconds.
- Replacing the annotated Picocli model with a programmatic model did not make the unchanged managed row pass and was fully restored.
- A bundle of JVM startup flags changed server behavior and broke managed setup; it was rejected and the launcher was fully restored.
- A later diagnostic using only `-XX:TieredStopAtLevel=1` lost its tool output. Its temporary directory is absent, so it supplies no usable evidence and will not be repeated as though it had succeeded.
- The exact startup-repair suite has no transport steps. The managed runner therefore calls `Popen` with `wait_for_port=false` and begins the YAML wait immediately after process creation.
- A focused class-initialization run placed `TaskSegmentEvent` initialization at 0.361 seconds and parser initialization at 0.442 seconds, an approximately 81 ms gap before startup-verb compilation. `CheckpointEvent` initialized at 0.521 seconds, after the row deadline.
- A syscall trace of restored source showed checkpoint temporary-file creation through atomic rename taking about 42 ms. The data-file `fsync` itself took about 1.7 ms and rename about 0.08 ms. Trace overhead delayed checkpoint entry substantially, but the individual persistence operations were not slow.
- The restored late-init window showed `Files.createTempFile` initializing `TempFileHelper`, `SecureRandom`, the JCA provider list, the SUN provider, native PRNG classes, and POSIX temp-permission machinery immediately before checkpoint file creation. This work is caused by randomized naming, not v17 serialization or atomic durability.
- Toast `bf_suspend` always returns a suspend package; `enqueue_suspended_task` records a zero delay as start time `now` in the ordered `waiting_tasks` queue, and the next `run_ready_tasks()` pass moves it to the runnable background queue. Toast does not create a host timer thread for zero delay.
- Banteng currently sends every numeric suspension delay, including zero, through `startTimer()`, which initializes and starts a virtual thread before requeueing the suspended VM.
- Directly requeueing `suspend(0)` through the existing wake path passed the scheduler and Anon1 tests, but the unchanged managed row still missed the checkpoint at 500 ms; that source attempt was fully restored.
- A fresh read-only startup review found no remaining single source change with enough measured latency to close the observed gap.
- A high-effort source review found that startup correctly reruns the stored segment after the irrevocable `server_log` effect, so bypassing that rerun would violate the production transaction boundary.
- The same review identified one untested cumulative hypothesis: the individually insufficient JFR guard, deterministic checkpoint temp, and direct zero-delay requeue remove distinct startup costs and may together close the measured gap.
- Adversarial review rejected a shared `<target>.tmp`: concurrent processes could interleave writes or delete each other's temporary file, a predictable name could follow a symlink, and default POSIX creation could widen database permissions.
- The adversarial verdict was `TEST` only with a PID-scoped temporary file, `CREATE_NEW`, owner-only POSIX permissions, and zero-delay requeue through the existing synchronized `enqueueWake` path.

## Theories (plausible)

1. The managed harness starts its 500 ms wait before Java reaches the composition root, so process/Picocli/class-loading overhead consumes most of the row interval. This predicts a large gap between process creation and `Banteng.call()` entry even when checkpoint work itself is fast.
2. Lazy compiler/VM class initialization during `#0:server_started` is the largest removable production source cost. This predicts that source-level removal of unnecessary startup compilation work, without changing semantics, moves checkpoint creation materially earlier.
3. Atomic checkpoint output (`force`, close, move) is the late governing cost. This predicts that the file becomes visible substantially after the checkpoint effect begins even when startup execution is already complete.

## Tests Run

| Test | Hypothesis | Result | Rules Out | Supports |
|------|------------|--------|-----------|----------|
| Direct checkpoint polling with restored launcher | 500 ms miss is only process-exit latency | File absent at 500 ms and present at 600 ms | Process teardown as the sole cause | 1, 2, or 3 |
| Composition-root timing | v17/runtime/checkpoint work itself exceeds 500 ms | `call()` completed in about 278 ms | Database/runtime work alone exceeding the row interval | 1 |
| Programmatic Picocli experiment | Annotation model construction is the removable governing cost | Managed row still failed; experiment restored | Picocli annotation inspection as a sufficient explanation | 1 or 2 |
| JVM flag bundle | Generic JVM startup tuning is a valid production fix | Managed setup broke; experiment restored | That flag bundle as an acceptable fix | None |
| Class-load trace | Late startup work occurs before checkpoint output | Compiler/AST/VM classes loaded immediately before checkpoint handling | A pure checkpoint-I/O explanation | 2 |
| Focused class-initialization timestamps | Custom JFR event initialization is material startup work | `TaskSegmentEvent` preceded parser initialization by about 81 ms; `CheckpointEvent` initialized at 0.521 s | Compiler/parser work as the sole late cost | 2 |
| Guard custom events until JFR is initialized | Removing unrequested JFR initialization is sufficient | Affected JFR, scheduler, v17, and Anon1 tests passed, but the unchanged managed row still missed the file at 500 ms; source attempt fully reversed | JFR initialization as a sufficient cause | 1, 2, or 3 beyond that cost |
| Timestamp checkpoint syscalls | Atomic force or rename governs the miss | Temporary creation through rename took about 42 ms; `fsync` 1.7 ms and rename 0.08 ms | Slow force/rename as the primary cause | 1 or 2 |
| Inspect restored 0.48-0.56 s class-init window | A removable pre-checkpoint operation remains | Randomized temp creation initialized the security-provider and PRNG stack immediately before file creation | No identifiable redundant work remains | 2 |
| Deterministic sibling temp source attempt | Removing randomized temp initialization is sufficient | V17 and Anon1 tests passed, but the valid unchanged managed row still missed the file at 500 ms; source attempt fully reversed | Randomized temp initialization as a sufficient cause | 1 or 2 beyond that cost |
| Compare zero-delay suspension owners | Banteng performs unnecessary host-timer work | Toast inserts start-time-now work into its waiting queue; Banteng starts a virtual timer even for zero | A host timer as required Toast behavior | 2 |
| Direct zero-delay requeue source attempt | Removing the host timer is sufficient | Scheduler and Anon1 tests passed, but the unchanged managed row still missed the file at 500 ms; source attempt fully reversed | Zero-delay timer initialization as a sufficient cause | Distinct cumulative startup costs remain |
| Read-only source and adversarial reviews | The three individually safe removals can be tested together unchanged | JFR guard and synchronized zero-delay requeue survived review; shared temp name failed review and was corrected to PID-scoped `CREATE_NEW` with owner-only POSIX permissions | Shared deterministic temp as production-safe | One corrected composed attempt |

## Current Best Theory

The row interval includes the entire Java launch. The JFR event initialization, randomized checkpoint-temp initialization, and zero-delay host-timer initialization are distinct removable costs. Each was insufficient alone and remains reverted. A single composed attempt can test their cumulative effect without changing the row, harness, fixture, launcher, JVM flags, Picocli model, worker count, transaction boundary, or atomic checkpoint contract.

## Open Questions

- Does the corrected composed attempt produce the checkpoint within the unchanged managed Anon1 row's 500 ms interval?
- Does a PID-scoped `CREATE_NEW` temporary file with owner-only POSIX permissions preserve byte-stable output, atomic replacement, cleanup, and JFR observability across the affected test authorities?

## Next Action

Remain on Anon1 and continue the same target. Apply one composed source attempt: guard `TaskSegmentEvent` and `CheckpointEvent` construction until JFR is initialized; create the checkpoint temporary file as a PID-scoped sibling with `CREATE_NEW` and owner-only permissions on POSIX filesystems; and route `suspend(0)` directly through `enqueueWake` while retaining `startTimer()` for positive delays and the host-wake path for non-timed suspensions. Run the affected JFR, scheduler, v17, and Anon1 tests together, reread Phase 3, and run the unchanged managed row. Keep and commit only if that exact row shows a measured reduction; otherwise fully restore the composed attempt. Do not alter the row, harness, timing, fixture, launcher, JVM flags, Picocli model, worker count, or production transaction boundary.
