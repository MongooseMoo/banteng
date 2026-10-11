# Phase 2 persistent walking skeleton builtin dependency audit

## Current findings

- `docs/reports/banteng-implementation-plan.md` names Phase 2 as the first production vertical slice.
- The exact row is `src/moo_conformance/_tests/server/persistent_walking_skeleton.yaml` with suite `persistent_walking_skeleton`.
- The row declares only `dump_database`, but that declaration is not evidence of builtins called transitively by database-resident login and evaluation verbs.
- Phase 2 explicitly requires the complete production path for Wizard login, command evaluation, `dump_database()`, checkpoint, restart, reconnect, and evaluation.
- The current `BuiltinCatalog` manifest has 12 entries: `length`, `create`, `set_player_flag`, `move`, `switch_player`, `set_task_perms`, `notify`, `toliteral`, `eval`, `typeof`, `function_info`, and `dump_database`.
- The Banteng stock profile points to `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_db/Test.db` (WSL path `/mnt/c/Users/Q/code/moo-conformance-tests/src/moo_conformance/_db/Test.db`) with checksum `1a3f23ebb549e02ccf5341668425118fcdc935b977096add87bc2a8ef29d408e`.
- `LambdaMooV4Reader` loads every persisted verb source into `WorldVerb`; `MooRuntime` then compiles and runs the stored listener `do_login_command`, optional connection hooks, listener `do_command`, and selected command verb through the production VM/catalog path.
- The conformance runner switches the test to `permission: wizard`; `SocketTransport` maps that to `Wizard` and sends the default `connect Wizard`. After `restart_server`, the runner reconnects as the same current user, so the same login path runs a second time.
- In this exact `Test.db`, object `#0` owns `do_login_command` and object `#2` owns `eval`; there is no stored `do_command` or connected-hook verb in the fixture.
- The taken `connect Wizard` branch of `#0:0` invokes `length`, `create`, `set_player_flag`, `move`, and `switch_player`.
- Every row `run` command dispatches through `#2:0`, which invokes `set_task_perms`, `notify`, `toliteral`, and `eval`. The dynamic source `return dump_database();` additionally invokes `dump_database`; the two arithmetic sources invoke no further builtins.
- Therefore the exact transitive production set for this row is 10 names: `length`, `create`, `set_player_flag`, `move`, `switch_player`, `set_task_perms`, `notify`, `toliteral`, `eval`, and `dump_database`.

## Observations

- This is a read-only audit of the plan, `BuiltinCatalog.java`, and bundled `Test.db` loader/runtime paths.
- No production file has been edited and no broad suite has been run.

## State

- Exact row, harness login/restart behavior, Java loader/runtime control flow, and database program dependencies are confirmed. Final manifest sufficiency comparison is in progress.

## Blocker

- None currently.

## Next action

- Compare the 10-name transitive set one-to-one against `BuiltinCatalog.buildManifest()`, check whether the two extra manifest names are merely unused by this row, and report any exact missing name.
