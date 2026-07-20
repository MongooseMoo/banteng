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

## Current Best Theory

The row interval includes substantial pre-composition-root launch and class-loading time, and startup execution lazily loads compiler/VM classes near the deadline. The evidence does not yet prove which source initialization is unnecessary or whether any semantics-preserving source removal is sufficient.

## Open Questions

- At what exact point does the managed runner start Banteng relative to its 500 ms step timer?
- Which production method first triggers the late compiler/VM class loads, and is that work required for the fixture's startup verb?
- How much time elapses from checkpoint-effect entry to atomic file visibility?

## Next Action

Read the exact managed-runner timer/process-launch path and the production startup/checkpoint call chain. Select a diagnostic only if it separates the surviving theories without changing the row, fixture, launcher, JVM command, or production behavior.
