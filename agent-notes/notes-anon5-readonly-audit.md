# Anon5 read-only audit

- Task: inspect only the committed Anon5 fixture/row and pinned `/root/src/toaststunt` to establish the exact three-boot lifecycle and Toast validity.
- Fixture evidence: `Anon5.db` begins with zero pending-finalization values. Its `server_started` verb logs the scenario, suspends, creates `$one` and an anonymous self-referential object on first boot, drops temporary local aliases on later boots, calls `run_gc()`, suspends again, and then shuts down.
- Row evidence: `startup_repair_anon5.yaml` waits 500 ms, checks the identifying log line, and asserts only `0 values pending finalization` in `Anon5.db.new`.
- Source state: pinned Toast checkout is `/root/src/toaststunt` at `aecc51e9449c6e7c95272f0f044b5ba38948459e`; it has pre-existing untracked files that this audit will not touch.
- Current observation: relevant source owners are `src/garbage.cc` for cycle discovery and `src/server.cc` for pending-finalization queueing/recycling/database serialization.
- Blocker: none. No exact Toast run has yet been performed.
- Next action: trace `run_gc`, root marking, anonymous queueing/recycling, and database write order line by line; then run only the exact committed Anon5 row if current source evidence confirms the command.

## Source trace and existing live authority

- `suspend(0)` packages the complete VM and activation stack, queues it at start time `now`, and returns `OUTCOME_BLOCKED` (`execute.cc:221-238`, `execute.cc:3519-3539`, `tasks.cc:1295-1318`). `run_ready_tasks()` moves due waiting tasks to the runnable queue (`tasks.cc:1628-1646`).
- The server loop runs `gc_collect()` before anonymous recycling and before `run_ready_tasks()` (`server.cc:807-810`, `server.cc:851-856`). Therefore the first suspension yields before graph construction; the second suspension yields after `run_gc()` so the requested collection occurs before shutdown resumes.
- Variable assignment frees the old VM-local value before storing the replacement (`execute.cc:2986-3026`). Anonymous decrements with a remaining reference call `gc_possible_root()` (`utils.cc:184-212`), which buffers a purple candidate once (`garbage.cc:145-159`).
- The collector does not enumerate task stacks as a separate root set. It starts from buffered decrement candidates, temporarily deducts their collection-child references, and restores a candidate black when its adjusted refcount remains positive (`garbage.cc:229-307`). VM/task values remain authoritative through ordinary reference counts.
- White anonymous cycles are restored and queued for finalization (`garbage.cc:328-364`). Queueing takes a reference (`server.cc:569-615`); recycling marks the body recycled, invokes `recycle`, destroys the body, and frees the queued reference (`server.cc:617-657`). Checkpoints serialize pending queue entries because recycle may resurrect them (`server.cc:689-724`).
- The pinned launcher manifest names the exact clean-source commit `aecc51e...`, binary `/root/src/toaststunt/build-release/moo`, and matching checksum.
- Existing current task notes record a direct pinned-Toast boot of the committed fixture: Toast logged `E_TYPE` at the second `add_property` call and never constructed the intended graph. The committed YAML nevertheless passed because it asserts only the startup banner and generic zero-pending output.
- This makes the committed row executable but semantically invalid as proof of the intended Anon5 lifecycle. No new Toast run has been performed in this audit because the delegated instruction forbids creating any file.
- Next action: verify the failing call against `bf_add_property`, report the exact invalid assertion/scope gap, and distinguish the intended lifecycle from the committed fixture's actual lifecycle.

## Committed fixture verdict

- The failing statement is source line 12 of `#0:server_started`: `add_property($one.two["foo"], "foo", ...)`. Its receiver is the anonymous value created by `create(#-1, 1)`.
- Toast `Var::is_obj()` is true only for `TYPE_OBJ` (`structures.h:221-224`). `bf_add_prop` returns `E_TYPE` when its receiver is not `is_obj()` (`property.cc:206-235`). Therefore the committed fixture cannot create the anonymous self-property it describes.
- Actual boot 1 state after the caught error: permanent #4 is reachable as `#0.one`; #4 has local `two` equal to a map whose `"foo"` value is one anonymous body; that body has no local properties, no verbs, and no self-cycle. Pending-finalization count is zero.
- Actual boots 2 and 3: locals `a` and `b` briefly alias that same body, then both are replaced by zero. Those decrements make the body a GC candidate, but the permanent `#0 -> #4 -> map -> anon` reference leaves positive adjusted refcount, so collection restores it black and never queues/recycles/removes it. Persistent state and zero pending roots remain unchanged.
- Consequently checkpoints 1, 2, and 3 are all expected to be byte-identical to each other; the plan names only the required weaker relation that checkpoints 2 and 3 are byte-identical. The original input differs because it lacks #4 and the anonymous body.
- The literal YAML assertions are individually true on the failed partial lifecycle: the banner is logged before the fault and the checkpoint has zero pending values. The invalidity is that these assertions do not prove the described anonymous self-cycle and do not reject the caught `E_TYPE`.
- No recycle verb is invoked and no anonymous body is removed on any boot. The body has no verb to invoke in the first place.
- No run was started by this scout; the current repo-local task record supplies the existing pinned-Toast pass/failure evidence.
- Next action: report this exact committed-state audit to the parent, including the distinction between executable row and invalid lifecycle authority.
