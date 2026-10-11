# Phase 2 check-failure audit

## Current findings

- Read-only audit requested; no source, test, or plan edits are authorized.
- `docs/reports/banteng-implementation-plan.md` Phase 2 is the controlling boundary.
- Current `build/test-results/test` XML reports exactly 98 failed methods:
  - `moo.runtime.MooRuntimeTest`: 54
  - `moo.server.MooServerTest`: 12
  - `moo.vm.MooVmTest`: 32
- All other current unit-test XML suites report zero failures.
- Phase 2 explicitly owns the first persistent walking skeleton, its production executor/scheduler/VM/transaction/checkpoint path, `dump_database()`, login/evaluation/restart, and the named Phase 2 architecture/persistence/transaction tests.
- The plan explicitly defers named fork-variable binding and real task/fork behavior to Phase 4, and expands server/task semantics and builtin coverage in later phases.

## Final classification

### A - retain and fix for Phase 2 (2)

- `moo.runtime.MooRuntimeTest.executesTheFirstManagedRowThroughStoredVerbsAndOneWorldTxn()`
- `moo.server.MooServerTest.servesTheFirstManagedRowOverRealSockets()`

Phase 2 lines 280-286 require the row's production executor, real `WorldTxn`,
socket ingress, Wizard login, command evaluation, `dump_database()`, checkpoint,
restart, reconnect, and evaluation path. Lines 304-305 require replacing every
placeholder/direct-world path exercised by the row. These two tests are the
current local tests explicitly identifying that first managed-row path and must
be corrected to the current row instead of allowing later tests to determine
Phase 2 production design.

Only two current source/XML method names contain `FirstManagedRow`; there is no
third current method with that spelling.

### B - Phase 3+ or removed-surface tests (96)

- `moo.runtime.MooRuntimeTest`: 53
- `moo.server.MooServerTest`: 11
- `moo.vm.MooVmTest`: 32

The remaining runtime/server failures cover Phase 3 world/builtin behavior or
Phase 4 server, connection, command, task, fork, queue, timing, and Telnet
semantics. The remaining VM failures cover Phase 3 language/world expansion,
Phase 4 task/queue behavior, Phase 5 builtin completion, or direct/frozen
surfaces that Phase 2 explicitly replaces. They must not drive Phase 2
production changes.

## State

- The requested classification is complete.
- No production, test, or plan files were changed.

## Blocker

- None. The only discrepancy is the request's reference to three
  `FirstManagedRow` methods when current source and XML contain two.

## Next action

- Report the completed read-only audit to the parent agent.
