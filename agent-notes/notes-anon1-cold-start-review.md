# Notes: Anon1 cold-start review (read-only) — CONCLUDED

## Verified causal path (all quoted from source/measurements)
- Row clock starts at Popen return: server.py:127-138 (`wait_for_port=False` → no port wait), runner.py:355-356 (`time.sleep(step.wait/1000)`), assert at runner.py:598+.
- Launch: run_banteng_wsl.sh `exec /opt/java/25/bin/java -classpath build/install/banteng/lib/* moo.app.Banteng --checkpoint ${database}.new`.
- Banteng.java:57-91 call(): header probe open + codec re-open (+30ms), MooServer ctor (bind + MooRuntime + catalog, +83ms), serve() → startServer() → blocking scheduler.submit(startup).
- Anon1.db #0:0 (lines 98-115): 6× server_log → IRREVOCABLE (MooVm.java:1092 yields PENDING_BUILTIN) → segment aborts unauthorized (PublicationScheduler.java:197-199) → validate → re-enqueue authorized (330-342) → FULL re-run incl. recompile (startServerNow → startStored). Then suspend(0) → publishSuspension (403-434) routes delay=0 through startTimer (436-455): first Thread.ofVirtual() + sleep(0) → enqueueWake. Wake segment: create/add_verb/set_verb_code → finally shutdown() → CheckpointRequest(true) → RETURNED → queueUnreachableAnonymousObjects → pending=1 → CHECKPOINT writeAtomic (createTempFile → JCA/SecureRandom init, 0.48-0.56s window; create→rename 42ms; fsync 1.7ms) → Anon1.db.new → SHUTDOWN → close → exit ~629ms.
- Measured: file absent@500ms present@600ms (direct polling, restored launcher); checkpoint effect 0.524s; call() entry ≈ 629-278-exit ≈ ~250ms frozen prefix. Deficit ∈ (0,100]ms.
- Architecture doc lines 68-70 MANDATES the irrevocable rerun ("reruns only when its ticket owns the publication turn") → not removable. Lines 76-81 mandate sibling temp + force/close/atomic-move only, NOT randomized naming.

## Answer given
1. Causal path as above.
2. Largest removable cost: randomized temp-name JCA/SecureRandom init in createTempFile (~40ms of the 42ms window, latest block on path; constraint-preserving per prior green attempt + architecture doc). Individually insufficient (attempt reverted).
3. Smallest next test: ONE diagnostic run of the unchanged managed row against a build with the three validated removals composed (deterministic temp + JFR guard + zero-delay requeue), capturing file mtime + /proc start time. Green → composed slice viable; red → infeasibility proven (all removables gone, still red).
4. Recommendation: explicit infeasibility finding for any SINGLE-operation slice (five individually tested, each green on tests, each red on row); composed-removals diagnostic is the only remaining discriminator.
