# Scheduler design check

- Scope: read-only inspection of `MooRuntime`, `MooVm`/`VmState` snapshots, `WorldTxn`, plan lines 43-74, and `MooRuntimeTest`.
- Current finding: `MooRuntime.executeStored` owns an inline runnable/timed/host-task loop and runs `MooVm.execute` directly against the root `WorldTxn`; fork children are constructed from `ForkRequest` locals/programmer/verb location and run through the same loop.
- Current state: plan lines 43-74 require one deterministic ready queue, publication tickets, bounded platform-thread VM execution, `WorldTxn` validation, and ticket-ordered publication with segment snapshot retry.
- Blocker: none in the source inspection; a higher-priority checkpoint required this note despite the delegated task's explicit no-notes constraint.
- Next action: inspect the exact snapshot/transaction contracts and runtime-test behavior, then report the smallest concrete scheduler/task-state design without source edits.

## Builtin manifest slice

- Scope: replace builtin name/effect switches with the plan's concrete manifest types, add the exact `dump_database` entry, and add a value-only deferred checkpoint request result.
- Current finding: `BuiltinCatalog` has 59 reachable switch cases and a separate effect switch; there were no focused `moo.builtin` tests before this slice.
- Current state: all 60 manifest entries are now present, including the exact `dump_database` contract; both independent switches still need replacement and deferred checkpoint result wiring/tests remain incomplete.
- Blocker: none in the builtin code; this note update is required by a higher-priority checkpoint despite the delegated no-notes constraint.
- Next action: route catalog invocation through explicit `BuiltinSpec` entries, add `dump_database`, then run focused builtin tests.
