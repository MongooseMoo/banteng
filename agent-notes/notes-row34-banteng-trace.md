# Row 34 Banteng command-dispatch trace

## Scope

- Read-only trace for `audit_command_dispatch_ignores_execute_flag`.
- No source edits or tests were run.

## Observed

- The conformance row creates `player:auditnoexec` with permissions `"d"`, then sends the command `auditnoexec` and expects `EXECUTED`.
- `MooRuntime.executeLine` performs normal player/room command discovery with `world.verb(player, words.getFirst())` and `world.verb(room, words.getFirst())` at `src/main/java/moo/runtime/MooRuntime.java:352-353`.
- `WorldTxn.verb(long, String)` matches names through the inheritance chain but returns a match only when `(verb.permissions() & 4) != 0` at `src/main/java/moo/world/WorldTxn.java:182-218`, specifically line 211.
- Therefore the `"d"` verb is excluded before command argument matching, leaving no selected command or room `huh`, which reaches `I couldn't understand that.` at `MooRuntime.java:631-638`.
- The named lookup is also used for direct VM verb calls and hooks; changing its global executable filter would affect more than command dispatch.

## Current state and next step

- The exact exclusion condition is identified.
- Named lookup callers were inventoried. `MooRuntime` uses it for `.program`, listener hooks, normal player/room command lookup, room `huh`, blank/login/OOB hooks. `MooVm` uses it for ordinary verb calls and `pass()`.
- `MooVmTest.verbFramesSkipNonExecutableMatchesPopulateLocalsAndScopeProgrammer` (`src/test/java/moo/vm/MooVmTest.java:987-1034`) proves direct VM lookup must keep skipping a nearer non-`x` verb and find an inherited executable verb.
- Consequently, deleting the executable check globally from `WorldTxn.verb(long, String)` is unsafe: it would break ordinary verb-call and `pass()` semantics, and could change hook lookup.
- The closest focused regression seam is `MooRuntimeTest`, beside `passesTheFullWordListToTruthyDoCommandBeforeNormalDispatch` and `runsRoomHuhAfterStoredCommandArgspecMismatch` (`src/test/java/moo/runtime/MooRuntimeTest.java:1340-1431`): set up a player verb with `"d"`, assert its persisted permissions through indexed lookup, then assert `executeLine(..., "auditnoexec")` emits `EXECUTED`.
- Remaining read-only work: choose the narrowest owner-preserving lookup shape and enumerate its inheritance/first-match risks.
- No blocker.
