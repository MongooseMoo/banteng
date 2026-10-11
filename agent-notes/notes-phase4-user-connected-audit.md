# Phase 4 user-connected row audit

## Scope and state

- Read-only audit of `audit_user_connected_hook_on_first_login`; no production or test edits.
- Current branch is `master`, ahead of origin by 189 commits, with tracked worktree clean and pre-existing untracked notes/tooling only.
- Current HEAD is `18e4cca feat: add verb metadata mutation builtins`.

## Findings

- The row setup first calls missing `verb_info` at YAML line 359. `BuiltinCatalog` has no `verb_info` manifest entry, so invocation returns `E_VERBNF`; the row catches it and resets the entire saved login-verb record to absent/empty.
- `verb_args` is also absent at line 360. Adding only `verb_info` would still fall into the same `except (E_VERBNF)` on `verb_args` and erase the capture, so the smallest setup-capture correction contains both read builtins. `verb_code` already exists.
- `delete_verb` remains absent for cleanup lines 420 and 431, and `boot_player` remains absent at line 436; both cleanup uses are guarded by `except (ANY)`, so they do not cause the assertion failure but can prevent exact restoration/cleanup.
- The production login/hook path itself already has the row's required behavior: `MooServer` passes the accepted listener handler into `MooRuntime.openConnection`; `finishLogin` recognizes an existing returned player, associates the connection, and calls `startConnectedHook`; `startConnectedHook` resolves `user_connected` on the listener handler and supplies `this=handler`, `player=returned player`, `caller=#-1`, `args={player}`, and empty `argstr`.
- `WorldVerb` already stores names, owner, encoded permissions/direct/indirect specs, preposition, and source; `WorldTxn` already provides local verb lookup. No runtime interface or listener change is needed for the first setup mismatch.

## First causal mismatch

- The first current divergence in row execution order is the missing `verb_info` call. It is caught, so it does not abort the row, but together with missing `verb_args` it makes the setup capture/restoration path false and prevents exact fixture state management.
- No static mismatch was found in the existing `user_connected` association/hook local construction required by the assertion.

## Smallest owned change/test surface

- One verb-metadata read slice in `BuiltinCatalog`: add existing-world-backed `verb_info` and `verb_args` entries/handlers; no new helper/interface and no `MooRuntime`, `MooServer`, scheduler, or `WorldTxn` production change.
- Focused `BuiltinCatalogTest` cases for the exact metadata shapes, permissions, and errors, followed by the existing managed row as the end-to-end gate.
- `delete_verb`/`boot_player` are remaining cleanup dependencies, not part of the first capture mismatch.

## Blocker and next action

- No blocker. Report these exact owners and the first-causal slice to the parent agent.
