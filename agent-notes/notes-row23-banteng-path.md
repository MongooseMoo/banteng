# Row 23 Banteng path trace

## Current state

- Assigned read-only subtask: inspect Banteng Java for `audit_user_created_hook_for_new_login_object`.
- Frozen authority supplied by parent: the exact row passes pinned Toast.
- Required trace: `MooRuntime.executeLogin`, returned-player acceptance, connection association, login messages, `user_connected`, `verbLocals`, object-ID high-water APIs and create/recycle behavior, plus existing tests.
- Required design constraints: use a safe maximum-object-ID snapshot rather than `objectCount` when holes exist; make `args[1]` active only for a newly created login object, after association and before any existing subsequent hook; no helpers, interfaces, adapters, or senders.
- No repository inspection has yet been performed for this subtask.
- No blocker is currently known.
- Next step: read the exact production and test paths named above, then report a minimal implementation and focused regression without editing tracked files or running managed servers.

## Findings after initial trace

- The conformance row installs `#0:user_created`, has `do_login_command` create a new object, mark it as a player, and return it. The row observes `{typeof(audit_created_seen), typeof(audit_created_user), equality}`. Current `[4, 1, 0]` means the created user is an object, but `audit_created_seen` remains its initial empty list; `user_created` was never called.
- `MooRuntime.executeLogin` executes `do_login_command`, accepts a returned object only if it is in `world.players()`, associates it at `world.switchConnectionPlayer`, then calls `user_connected` only for returned-player association. There is currently no `user_created` lookup or dispatch.
- `verbLocals` supplies `this`, `player`, `caller`, `verb`, `args`, and `argstr`. Existing lifecycle hooks use listener handler as `this`, authenticated player as `player`, `#-1` as `caller`, `{player}` as `args`, and empty `argstr`.
- `WorldTxn.objectCount()` returns the number of live objects, not a maximum ID. It is unsafe as a creation high-water marker when recycled objects leave holes.
- `WorldTxn.createObject` scans all live object IDs for the maximum and allocates `max + 1`. `recycleObject` removes the object and player entry. No public maximum-object-ID API currently exists; `World` is package-private and hides the map.
- Existing runtime tests cover returned-player acceptance and `user_connected`, but no new-player `user_created` case has yet been found.
- Current working diagnosis: snapshot the exact maximum live object ID before `do_login_command`; if the accepted returned player ID is greater than that snapshot, it was created by this login execution and should receive `user_created` after connection association and before the already-existing `user_connected` hook.
- No blocker is currently known. Remaining inspection: choose the smallest honest maximum-ID exposure, check exact hook ordering branches, and formulate the focused JUnit regression.
