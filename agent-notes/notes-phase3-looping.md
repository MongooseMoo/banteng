# Phase 3 looping family handoff

## Current state

- The preceding `index_and_range` vertical slice is committed as `a250ccb feat: add phase 3 verb code builtins`.
- The tracked worktree was clean before beginning `language/looping.yaml`; all `notes-*.md` files and `pyghidra_mcp_projects/` remain untracked.
- Exact Toast managed command: `scripts/run_managed_wsl.sh toast stock src/moo_conformance/_tests/language/looping.yaml`.
- Toast result: 29 passed in 5.35 seconds.
- Exact Banteng managed command: `scripts/run_managed_wsl.sh banteng stock src/moo_conformance/_tests/language/looping.yaml`.
- Banteng result: 23 passed, 6 failed in 6.47 seconds.

## Findings

- Every execution row passes, including string, map, and two-variable loops, break/continue semantics, invalid syntax/error cases, and empty-map no-leak rows.
- The six failures are exactly the rows named `decompile_for_loop_*`; each returns `E_VERBNF`.
- Those rows create a verb, call the now-present `add_verb` and `set_verb_code`, then call `verb_code(o, "test")` and compare the returned source-line list.
- `verb_code` is absent from Banteng's builtin manifest and implementation. The failure is therefore a missing Phase 3 row dependency, not a loop-execution defect and not a missing `decompile` builtin.
- Banteng already stores source text on `WorldVerb.programSource()` and exposes local/non-executable verb lookup through `WorldTxn.verb(..., false)`; `WorldTxn.setVerbCode` is the existing mutation owner.
- One read-only Codex subagent is inspecting the local Toast primary source for the exact `verb_code` call shape, validation/error ordering, local/inherited behavior, permissions, and output semantics. It has no edit authority.

## Blocker and next action

- No external blocker is known. The exact Toast contract must be read before defining the focused test or production handler.
- Next action: combine the Toast primary-source result with Banteng's existing world ownership, add a focused red `BuiltinCatalogTest` for `verb_code`, then add only the direct manifest handler required by the six active rows. Keep the slice only if the managed family improves from 23/29 to 29/29; otherwise restore it before another source slice.
- This note must remain untracked and unstaged.

## Verb-code slice state

- Toast primary source `src/verbs.cc:469-501,672-673` defines `verb_code(object, descriptor [, fully-paren [, indent]])` with arity 2..4 and all `TYPE_ANY` positions. Validation is object type, descriptor, valid object, local defined verb, then read permission. Success is a list of canonical source lines.
- `find_described_verb` is local-only. Read permission is verb owner, read bit, or wizard; no programmer flag is required.
- Banteng already owns canonical source rendering in `MooParser` plus `MooUnparser`. The production slice adds a direct `verb_code` manifest handler using those owners and no new helper/interface/world method.
- Focused red was exact: 17 tests ran with two failures, the manifest count and missing `verb_code` spec. After production implementation, `BuiltinCatalogTest` passes 17/17.
- The first managed looping rerun did not improve: 23 passed, 6 failed. Every failure remains the same undifferentiated `E_VERBNF`.
- The checked-in generated managed `verb_code` suite passes 3/3, proving the new handler is installed with the exact arity. Therefore `verb_code` itself is not established as the remaining `E_VERBNF` source.
- Each failing row next calls `recycle(o)` before returning its successful `verb_code` result. `recycle` has not yet been proven present or absent.
- Next action: run the checked-in generated `recycle` signature suite. If it proves `recycle` absent, treat it as the next same-row Phase 3 handler dependency; if it passes, continue tracing the exact row in call order. Do not commit the tracked `verb_code` slice until the managed family improves.

## Recycle dependency correction

- The checked-in generated managed `recycle` suite did not produce a builtin result. Banteng accepted the port probe but closed the socket during the harness login, so all four rows errored at fixture setup with `BrokenPipeError`.
- The runner deletes the current managed temp directory and `server.log` unconditionally during teardown. A later tail command selected a stale unrelated Toast log and is explicitly not evidence for this failure.
- Direct current-source inspection establishes that the builtin manifest has no `recycle` entry. The prior statement that no world owner existed was wrong: `WorldTxn.recycleObject` already performs the real immutable graph mutation, including detaching location/contents, reparenting children, removing the player entry, deleting the object, and recording writes.
- `BuiltinCatalog.Result` already contains `recycleTarget`, and `MooVm` already owns the complete recycle control flow: resolve/call inherited `:recycle`, persist the recycle target in the child frame/snapshot, call `WorldTxn.recycleObject` after normal hook return, and push zero.
- Toast `objects.cc:788-947,1347-1349` registers `recycle` as one `TYPE_ANY` argument. It validates object type, validity, and owner-or-wizard control, invokes `:recycle`, then performs graph destruction and returns zero. The active rows recycle newly created empty objects with no hook.
- A focused test now expects an `ANY` one-argument manifest entry, transaction-write/world ownership, owner authorization through the existing `recycleTarget` VM outcome, and exact `E_TYPE`, `E_INVARG`, and `E_PERM` errors. Production `recycle` has not yet been added.
- Next action: run the focused catalog test. If red is exactly missing manifest/spec, add the direct handler and the minimal `Result.recycle` factory required to populate the already-existing VM outcome; then rerun the focused test and managed looping family.

## Looping family keep evidence

- The recycle focused red was exact: 18 tests ran with two failures, the manifest count and absent `recycle` spec.
- Production now registers one-argument `recycle(ANY)` as a transaction-write world builtin, validates type/validity/owner-or-wizard control, and returns the existing durable `recycleTarget` VM outcome through a minimal `Result.recycle` constructor.
- The focused `BuiltinCatalogTest` passes 18/18 after implementation.
- Exact managed command: `scripts/run_managed_wsl.sh banteng stock src/moo_conformance/_tests/language/looping.yaml`.
- Managed result: 29 passed in 6.39 seconds, matching Toast's 29 passed. The kept reduction is 6 failures to zero.
- The plan was reread after both the focused pass and managed family pass. `verb_code` and `recycle` are both exact handlers invoked by the same Phase 3 looping rows, so they are one managed-socket vertical slice through the scheduler, VM, `WorldTxn`, and recycle-hook outcome.
- Exact plan gate `JAVA_HOME=/opt/java/25 ./gradlew check` completed `BUILD SUCCESSFUL` in 30 seconds. The separate `git diff --check` gate is clean.
- Next action: inspect, stage, and commit only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`, leaving this note and all other untracked files unstaged. Then reread Phase 3 and advance to the next exact path, `language/scatter.yaml`.
