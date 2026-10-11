# PR #57 read-only adversarial audit checkpoint

- Task: audit PR #57 / issue #14 at exact head `55aa340dd4b336a55f62c49f4a7d47e440d28814`, exact/current base `5ef47c4382868fed9ad2dc5ca3192777ec0e8903`; no source edits, builds, tests, reruns, push, or merge.
- Worktree `C:\Users\Q\code\banteng-agent-issue-14` is clean and matches remote head. Base is an ancestor; branch is 22 commits ahead/0 behind; diff-check is clean.
- PR is open/non-draft, body contains literal `Closes #14`, 25 changed files, no current reviews/comments. Exact-head CI run `32214295129` is still in progress; workflow static analysis and dependency review have succeeded.
- Issue requires deletion of `baseRevision`, singular `changeParent`, and `restoreIntrinsicCommands`; server-owned ConnectionRegistry outside WorldTxn/MVCC; PropertyLayoutEngine; sealed PropertyHolder convergence; executable dead-method/static-move recipe.
- Dead API search currently returns zero declarations/callers in main/test. WorldTxn decomposition test forbids all connection fields/methods and dead methods.
- `moo.server.MooServer` owns one final `ConnectionRegistry` field and injects it into all `MooRuntime` construction. `ConnectionRegistry` owns player identity, connection info, and intrinsic commands. Runtime retains only the injected `ConnectionRegistryAccess` and per-attempt copies; its `ConnectionState` contains transport/session control fields, not player/info/intrinsic identity mirrors. World package has no connection symbols.
- Dependency direction avoids a server/runtime cycle: consumer port `moo.builtin.ConnectionRegistryAccess`; server implements it; runtime and BuiltinHosts depend on the port; builtin and world packages import neither server nor runtime. `MooRuntime.builtinHosts()` supplies `.connections(this::connectionRegistry)` through issue #9 BuiltinHosts.
- Connection identity is outside WorldTxn MVCC but participates in runtime attempt copying/publication under `sessionRevision`, preserving retry atomicity without world read/write sets. Need adjudicate whether this matches the literal no-runtime-mirror criterion; current evidence says yes because copies are the same authoritative registry abstraction, not parallel identity maps.
- `PropertyHolder` is sealed to WorldObject/WorldAnonymousObject and exposes six shared fields. Public type-specific overloads delegate to shared private `*For(Object...)` implementations for property lookup/read/write/auth, parent change, add property/verb, and verb-code replacement. PropertyLayoutEngine owns nine static graph/layout methods; WorldTxn delegates.
- Potential residual to inspect: some permanent-only operations such as deleteProperty/clearProperty/deleteVerb/setVerbInfo remain type-specific, which may be legitimate because no anonymous sibling exists. Verify all actual mirrored pairs converge and tests cover them.
- Remaining work: inspect full recipe/test for overload-safe capture, exact closure/caller retarget/deletion/fixed-point and non-vacuous failure modes; review every commit for TDD boundaries/scope; verify package/identity architecture tests; wait for exact-head terminal CI and inspect CodeQL/reviews/comments; return strict MERGE/NO-MERGE with raw evidence.
## Replacement-head re-audit checkpoint (2026-08-18)

- Exact requested head: `b92a7171d344943c36c91aae72a2b2827ab8714d`.
- Current state: read-only adversarial re-audit started; no source/build/test/rerun/push/merge action taken.
- Required confirmations: absent-target input-derived generation; overload-safe exact complete closure with fail-closed/idempotence; exact `changeParent` caller migration; CodeQL alert 106 after new analysis.
- Also pending: production ownership, TDD ordering, diff scope, ancestry, closing phrase, reviews/comments, and exact-head CI.
- Prior blockers remain unverified at this replacement head until raw repository and GitHub evidence is collected.
- Next action: inspect exact head/worktree and live PR/issue state, then issue strict MERGE or NO-MERGE.
