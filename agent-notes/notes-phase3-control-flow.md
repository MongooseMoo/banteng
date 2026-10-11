# Phase 3 control-flow handoff

- Prior completed source slice: `4c7f55f feat: add phase 3 list builtins`.
- `language/error_authority.yaml`: Toast 4/4 and Banteng 4/4; no source change was required.
- Active exact family: `src/moo_conformance/_tests/language/control_flow.yaml`.
- Toast managed result: 26/26 passed.
- Banteng managed baseline: 0/26 passed. Named `while` failed parsing; unnamed interrupts later threw through the connection and caused subsequent broken pipes.
- Active tracked slice: named and unnamed `while` interrupt semantics through `Ast`, parser, unparser, compiler, and VM.
- Focused TDD authority: the two new complete-pipeline tests first failed solely because `Ast.While.loopVariable()` did not exist, then passed under WSL with `JAVA_HOME=/opt/java/25` after the production changes.
- The Phase 3 plan was reread after the focused pass. It still requires control flow as a managed-socket vertical slice and keeps the exact `language/control_flow.yaml` path in the Phase 3 conformance list.
- Managed result after the while slice: 21/26 passed, up from 0/26. All five named/unnamed while rows pass; the existing nested-for, ternary, and three anonymous-fork delay rows also pass.
- Remaining exact failures: the three `for ... in [from..to]` rows fail because the parser requires a parenthesized iterable; `fork_with_id_variable` fails because named fork parsing/binding is absent; `fork_zero_delay` fails because `queued_tasks()` is absent.
- `JAVA_HOME=/opt/java/25 ./gradlew check` passes and `git diff --check` is clean.
- Commit `115aac4 feat: add phase 3 while control flow` closes the measured while slice. The tracked worktree is clean after the commit; notes remain untracked.
- The next exact failures are the three bracket-range rows. Current grammar supports parenthesized collection `for` loops only; `[` begins a map literal elsewhere, while `[start..end]` is special MOO `for` syntax.
- Current `ITERATE` loop state stores materialized primary/secondary `ListValue` sequences and is already serialized in `VmSnapshot.LoopState`. No tracked range edit has started.
- Range TDD authority: `iteratesIntegerRangesThroughTheCompleteControlFlowPipeline` covers ascending `[1..5]`, reversed `[5..1]`, and single `[3..3]`; it failed at compile time solely because `Ast.For.rangeEnd()` did not exist.
- The existing collection iterator binds a second loop variable to a 1-based index when there is no secondary value sequence. The range iterator must preserve that behavior.
- Active tracked range slice now spans the direct owners: `Ast`, parser, unparser, compiler, bytecode, VM, `VmState`, `VmSnapshot`, and `MooVmTest`.
- `ITERATE_RANGE` evaluates the two endpoints once, keeps them in the existing serialized loop cursor, marks the cursor as range-backed, and advances via a resumable long offset. It does not materialize a list or introduce a second scheduler/state hierarchy.
- The focused complete-pipeline range test passes under WSL with `JAVA_HOME=/opt/java/25`. The Phase 3 plan was reread after that pass and still requires managed-socket evidence for this slice.
- Managed result after the range slice: 24/26 passed, up from 21/26. All three bracket-range rows now pass with no regression. Full `./gradlew check` passes and `git diff --check` is clean.
- Commit `496d424 feat: add phase 3 for ranges` closes the range slice.
- The only remaining `control_flow.yaml` failures are `fork_with_id_variable` and `fork_zero_delay`. The controlling Phase 3 text explicitly says task IDs, queueing, and named fork-variable binding remain Phase 4 runtime behavior, so these two rows are recorded for the Phase 4 revisit and are not authority to pull Phase 4 work forward.
- Next action: verify the post-commit tracked ledger, then run Toast and Banteng for the next ordered Phase 3 family, `src/moo_conformance/_tests/language/index_and_range.yaml`, without changing the two deferred task-runtime rows.
- This note is a handoff artifact only and must remain untracked and unstaged.

## Read-only fork boundary audit (2026-07-20)

- Phase 3 explicitly owns anonymous fork parsing plus the serializable fork-request VM outcome; task IDs, queueing, and named fork-variable binding are explicitly Phase 4.
- Current parser/AST/compiler implement only anonymous `fork (delay)` and lower it to a child vector. Current VM accepts integer/float delays, raises `E_TYPE` for non-numeric delays and `E_INVARG` for negative delays, and captures the child program, delay, locals, programmer, and verb location in the snapshot fork outcome.
- Existing managed evidence records `fork_float_delay` and `fork_non_numeric_delay_error` passing. The only current row failures are `fork_with_id_variable` and `fork_zero_delay`.
- Toast confirms named syntax assigns a generated task ID during enqueue, and `queued_tasks()` enumerates actual task queues. Both failing assertions therefore require the exact Phase 4 behaviors named by the plan.
- The Phase 3 exact whole-file `control_flow.yaml` gate is inconsistent with its own boundary because it includes those two Phase 4 rows. The smallest Phase 3 boundary stops at anonymous parse/compile and snapshot-safe VM fork request, including numeric validation; it does not add named binding or observable task queue state.
- No source, test, conformance, fixture, or launcher change is authorized by this read-only audit. Next action is to report the boundary and gate mismatch to the parent agent.
