# Phase 4 queued_tasks authority audit

- Task: read-only derivation of Toast `queued_tasks()` shape, timing, permissions, filtering, and minimum first-slice fields from pinned source and exact conformance rows.
- Pinned authority: `/root/src/toaststunt` commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`; Banteng's checked Toast manifest names the same commit, binary, checksum, and stock fixture.
- Signature: zero to two integer arguments. More than two raises `E_ARGS`; a non-integer supplied argument raises `E_TYPE`. One truthy argument includes variables. Two arguments make the second truthy value select count mode; because source tests `nargs == 1`, two-argument calls do not include variables.
- Default return is a list. Count mode returns an integer equal to the number of rows the same viewer could see.
- Default forked/suspended row length is 10; include-variables mode length is 11. Fields are ordered: task id, start/status, obsolete clock id, obsolete clock ticks, programmer/player, verb location, verb name, line number, `this`, byte size, optional runtime-variable map.
- Delayed fork field 2 is integer wall-clock epoch seconds derived from `gettimeofday(now) + delay`, rounded up only when microseconds exceed 500000. Reading tasks use `-1`; external enumerators use a status string in this slot.
- Wizard sees all queues. A nonwizard sees only reading tasks for its player identity and background/waiting/external tasks whose current verb programmer equals the viewer. Count and list apply the same filter.
- Anonymous field 6/9 values are preserved only for a wizard or the anonymous body's owner; otherwise they become an invalid anonymous reference. Tasks owned by another programmer are omitted before row construction.
- Exact audit row `audit_queued_task_reported_budget_shape` creates a delayed fork and requires fields 1, 2, 3 to be integers and field 2 to be later than `time()`.
- Exact anonymous authority row requires owner and wizard to see an anonymous `this` in field 9 and requires a different programmer to see zero matching rows after ownership changes.
- Blocker: none. Next action: distinguish the minimum first Phase 4 vertical slice from the complete Toast row and report it to the parent with source/row line references.
