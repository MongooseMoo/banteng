# Issue 27 final audit notes

- Candidate: `ddab89c76194bb6d822610549be781176794826e`
- Base: `d50652d1ff4b643a759812e4cd37e4a5ca85b1f9`
- Audit worktree: `C:\Users\Q\code\banteng-agent-issue-27`
- State: read-only design and scope audit nearly complete; no tests or builds run.
- Governing observations: issue #27 requires durable retention of queued-task `first_lineno`, called verb, and full matching verb across v17 read/write and restored fork activation, without reconstructing them from locals. The plan assigns durable task ownership to `moo.runtime`, v17 encoding to `moo.persistence`, and behavioral authority to pinned WSL Toast.
- Current implementation observations: the candidate adds explicit metadata to queued durable records, VM root/frame/fork snapshots, v17 read/write, task-registry rows, and restored queued programs. It replaces the prior equality/local-reconstruction checks in the codec and restores source lines by applying the persisted first-line offset to compiled instructions and fork vectors.
- Findings so far: none. Production queue creation/restoration uses the explicit metadata overloads; the locals-derived overloads are compatibility/test surfaces and are not on the issue #27 production path. Toast writes the queued header line from `find_line_number(ft.program, ft.f_index, 0)`, writes `activation.verb` and `activation.verbname` separately, restores the header into `program->first_lineno`, uses `verbname` for `queued_tasks()` rows, and uses `verb` for task-stack rows. The candidate mirrors those splits.
- Source-line review: new forks retain `fork.program().sourceLine(0)`; restored fork-only source is recompiled and shifted by the persisted first-line offset, recursively including nested fork vectors. The compiler always emits implicit return instructions for an empty body, so the restored-program non-empty guard does not reject an otherwise parsable empty fork body.
- Fixture review: the v17 fixture contains header `0 4 ...`, called verb `audit_t`, full matching name `audit_task_aliases audit_t*`, and a source verb whose fork-body instruction is line 4. The codec test asserts those values and exact input/output bytes; the scheduler test asserts full-name/source-line external fields and called-verb/source-line task-stack fields after restoration.
- Blocker: none currently.
- Next action: perform the final scope/constructor inventory check and report the audit result with file/line evidence.
