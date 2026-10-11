# Row 37 Barn move authority handoff

## Current assignment

- Read-only authority investigation for Banteng conformance row `audit_move_wizard_calls_accept_but_overrides_false`.
- The exact scope is `C:\Users\Q\code\barn`: normative `move()`/destination `accept`/wizard-override permissions and errors, then the current Barn implementation path from builtin entry through the move/accept semantic owners.
- Required report: exact files, symbols, line ranges, observable contract relevant to the row, agreements, disagreements, ambiguities, and whether the row can change an implementation decision.
- Explicit prohibitions: no Barn edits, tests, staging, commits, or Java design.

## Findings and observations

- Barn root `AGENTS.md` requires uncertain conformance behavior to be verified against the managed WSL Toast oracle before Barn-side implementation; this delegated task is read-only and does not authorize an oracle run.
- The current normative prose is incomplete for the row:
  - `spec/builtins/objects.md:316-340` says `move(what, where)`, lists physical relocation followed by `exitfunc`/`enterfunc`, and names only E_PERM/E_RECMOVE/E_INVIND. It says nothing about destination `accept`, E_NACC, a missing `accept` verb, or wizard override.
  - `spec/objects.md:347-370` repeats the same physical move/hook ordering and only E_RECMOVE/E_PERM, again without `accept` or wizard semantics.
  - `spec/errors.md:378-389` describes E_NACC as generic inaccessible data and does not list destination refusal; this conflicts with `types/errorcode.go:103-104`, whose runtime message is `Move refused by destination`.
  - `builtins/function_signatures_generated.go:125` accepts 2-3 arguments (`OBJ, OBJ, INT`), while the prose signature documents only two. The third argument is irrelevant to the named row but proves the prose is stale/incomplete.
- Current implementation path:
  - `builtins/registry.go:154` registers `move` to `builtinMove`; `builtins/registry.go:399-435` dispatches it after protected-builtin redirection and signature validation.
  - `builtins/objects_movement.go:11-69` owns move policy. After type/arity/validity/recmove checks, lines 52-60 call destination `accept(what)` whenever destination is not `$nothing`. A non-E_VERBNF exception propagates. E_VERBNF is ignored. A false normal return raises E_NACC only when `ctx.IsWizard` is false; a wizard still calls `accept` but overrides its false result. Lines 63-64 call store mutation only after that decision. Lines 67-69 show exitfunc/enterfunc are not implemented and return integer 0.
  - `builtins/registry.go:494-500` forwards `CallVerb` through the configured callback or returns E_VERBNF when none exists.
  - `scheduler/scheduler.go:69-78` configures that callback to call `Scheduler.CallVerb` with the task's player (falling back to programmer).
  - `scheduler/call_verb.go:21-162` is the current accept-call execution owner: lookup via `Store.FindVerb`, E_VERBNF on missing verb, compile, execute in a fresh synchronous task/VM, and return the result. The accept verb runs with programmer/wizard status derived from the accept verb owner, while the original `builtinMove` wizard override checks its own calling context.
  - `db/store/store_relationships.go:25-52` owns physical relocation under one store lock: remove from old contents, update location, insert into new contents, return E_NONE.
- `builtins/objects_movement_test.go:36-105` covers only invalid-object/destination E_INVARG cases. There is no durable unit coverage here for accept invocation, false result, missing accept, exceptions, or wizard override.
- Historical, non-normative `notes/conformance-hunt-2026-06-22.md:370-383` records a managed Toast observation that a non-wizard move to a valid destination with no `accept` verb returns E_NACC; the current Barn implementation instead ignores E_VERBNF and moves. This does not invalidate the named wizard/false row, but it is a separate known disagreement.
- No tests, Barn edits, staging, or commits have been performed.

## Current blocker

- The Barn prose cannot itself establish the exact row contract because it omits destination `accept` and wizard override. The named row can still change a Banteng decision by distinguishing `call accept, then override false for wizard` from `skip accept for wizard`, but Toast remains the behavioral authority for that distinction.

## Next step

- Read the narrow VM builtin-entry range only if needed to finish the call trace, then report the exact contract and spec/implementation disagreement to the parent agent. Do not design Java or mutate Barn.
