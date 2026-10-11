# PR #38 test-fixture repair

- Worktree: `C:\Users\Q\code\banteng-agent-issue-4`
- Branch: `agent/issue-4-program-feedback`
- Assigned HEAD: `eed315589c1e645489053d55c82d1ef59212ee99`
- Scope: test-only repair of five invalid fixtures reported by CI run `32078296689`.
- Constraints: preserve production code and prior commits; use `apply_patch`; do not run local builds or tests; do not push.
- Current findings: all five initial fixture defects were repaired; follow-up CI run `32079216644` exposed two name-based verb lookups that require executable permission. The `inherited_program` and `program_reordered` target fixtures now use permissions `6`; the anchor remains `2`.
- State: follow-up committed locally as `54e2653` with message `codex: make programmed fixtures executable`, on top of `849e3e8`.
- Blocker: none.
- Next action: parent agent may inspect the exact diff and decide whether to push; no local builds/tests were run and nothing was pushed by this worker.
