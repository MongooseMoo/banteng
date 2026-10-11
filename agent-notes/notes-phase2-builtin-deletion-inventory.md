# Phase 2 BuiltinCatalog deletion inventory

Current state:
- Read-only inventory requested for `src/main/java/moo/builtin/BuiltinCatalog.java` against the Phase 2 builtin contract in `docs/reports/banteng-implementation-plan.md`.
- The manifest is authoritative at exactly 12 reachable Phase 2 entries: `length`, `create`, `set_player_flag`, `move`, `switch_player`, `set_task_perms`, `notify`, `toliteral`, `eval`, `typeof`, `function_info`, and `dump_database`.

Observations:
- The deletion-first cleanup protocol has been read.
- The plan and source slice have not yet been inspected in this turn.

Blocker:
- None.

Next action:
- Read the named Phase 2 contract and the complete current `BuiltinCatalog.java`, then report transitive keep/delete dispositions without editing source.
