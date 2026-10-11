# Phase 4 scheduler scout

## Current findings

- The delegated task is read-only design work: identify the smallest existing-code change that makes `fork` a publication boundary; do not edit production code or tests.
- The current scheduler allocates task IDs and tickets in scheduler-owned state. Task IDs therefore must not be allocated during speculative VM execution because a publication conflict/retry would consume IDs.
- The current `FORKED` path records a pending child, calls `continueAfterFork()`, and continues the parent in the same segment. That violates the required publication boundary and prevents the published child ID from being supplied to a named fork before parent continuation.
- The child must be registered/queued atomically during successful publication, before the parent is made runnable again. The parent must retain its existing task ID; the child must receive the next deterministic task ID.
- Publication-ticket accounting must advance once for the original fork boundary, then assign fresh deterministic tickets to the child and resumed parent. Exact child-versus-parent ticket order still needs verification from the plan/tests.

## Observation and state

- Current HEAD includes `f325ab4 feat: add scheduler-owned task registry`; its exact registry lifecycle still needs inspection.
- The first attempted source read used an incorrect Maven-style path and changed nothing.
- No production or test files have been modified by this scout.

## Blocker

- None. The exact source root and current `VmState.continueAfterFork` contract still need to be located and read.

## Next action

- Locate `PublicationScheduler.java`, then inspect its fork/publication/task-registry paths together with `VmState`, `VmSnapshot`, and the scheduler tests. Report exact code locations and invariants to the parent agent without editing.

## Exact source observations

- `PublicationScheduler.executeSegment` at lines 200-214 constructs the child request, appends it to `pendingForks`, calls `state.continueAfterFork()`, and immediately continues the parent. This is the exact same-segment behavior to remove.
- `drainPublications` at lines 300-310 currently registers/enqueues all pending forks before `publishSegmentResultOutsideMonitor`; `enqueuePendingForks` at lines 398-425 assigns the child task ID only after the whole parent segment commits.
- `publishSegmentResultOutsideMonitor` at lines 323-332 rebuilds a boundary entry using the original ticket; `publishVmCompletionOutsideMonitor` accepts only `SUSPENDED`, and `publishSuspension` advances `nextPublicationTicket` once.
- `finishSuccess` and `finishFailure` also advance `nextPublicationTicket` once and remove the completing task ID from the registry.
- `VmState.continueAfterFork()` only clears the fork request and restores `RUNNING`; it accepts no assigned child ID.
- Parser/AST/compiler currently support only anonymous `fork (delay)`: `Ast.Fork` has delay/body only, `MooParser.parseFork` reads the parenthesized delay immediately, and `MooCompiler` emits only the child-vector index. Named fork binding is therefore a separate parser/compiler/VM requirement, not something the scheduler alone can supply.
- Existing `PublicationSchedulerTest` proves parent output independence and eventual child registry removal, but does not prove same-parent-instruction visibility, publication-boundary ticket order, or named ID binding.

## Refined invariants

- Child ID allocation occurs only after successful world/runtime publication, never during speculative `executeSegment`; retries must neither expose nor consume a task ID.
- A fork result must stop the parent at the existing `FORKED` VM outcome. Publication must register/queue the child before a fresh parent continuation entry can be dispatched.
- The parent keeps its current task ID. The child receives exactly one monotonically increasing scheduler task ID and exactly one registry entry, retained through ready/delayed/suspended states and removed only on child terminal completion.
- The current publication ticket advances exactly once for the committed fork boundary. Both child execution and resumed parent execution require fresh tickets; their exact relative order must follow the active Phase 4/Toast authority rather than be guessed.
- For named forks, the assigned child ID must be written into the parent VM state at publication time before clearing `FORKED`; current syntax and VM state carry no variable target, so parser/AST/compiler/snapshot state must first preserve that optional name.

## Remaining next action

- Read the untruncated active Phase 4 paragraph and the exact fork observation rows to determine child-versus-parent ticket order, then send the minimal change map to the parent agent.
