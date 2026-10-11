# Row 31 pinned Toast authority trace

- Scope: read-only authority trace for `audit_property_flags_deny_nonowner_read_and_write`.
- Required evidence: exact durable conformance row and pinned `/root/src/toaststunt` source from expression property read/write through lookup, permissions, flags, and error ownership; include programmer/owner context, error precedence, and uncertainties.
- Restrictions: do not edit project sources; do not start or stop processes; do not propose Java design.
- Current state: no source inspection has started in this subtask.
- Current blocker: none.
- Next step: locate and read the exact conformance row, then trace the corresponding pinned Toast source paths.

## Findings after initial trace

- Durable row: `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:750-770`.
- Setup runs as wizard: `obj = create($nothing); obj.w = 0; add_property(obj, "audit_secret", 10, {#0, ""});`.
- The read `return {obj}.audit_secret;` and write `{obj}.audit_secret = 11;` run as `programmer` and each require `E_PERM`.
- The pinned manifest identifies ToastStunt revision `aecc51e9449c6e7c95272f0f044b5ba38948459e` and Test.db fixture.
- Harness transport maps `wizard` to player `Wizard` and `programmer` to player `Programmer`; Test.db identifies `Wizard` as `#3` and `Programmer` as `#4`.
- Toast `execute.cc` handles ordinary reads through `OP_GET_PROP` (`1855-1911`, with optimized `OP_PUSH_GET_PROP` at `1913-1957`) and writes through `OP_PUT_PROP` (`1959-2082`).
- Both paths validate operand types and object validity before property lookup. They call `db_find_property`; a missing property becomes `E_PROPNF`; only an existing ordinary property proceeds to `db_property_allows` with `PF_READ` or `PF_WRITE`; denial becomes `E_PERM`. Successful writes alone call `db_set_property_value`.
- `db_properties.cc:494-607` defines lookup across the object and ancestors. The handle points at the target object's property value slot while retaining the definer. `db_properties.cc:697-751` reads owner/perms from that property value slot and defines permission as `(flags & requested_flag) || programmer == property_owner || is_wizard(programmer)`.
- `property.cc:108-148` parses property info. The empty permission string produces flags `0`; `{#0, ""}` therefore assigns owner `#0` and neither read nor write permission. `property.cc:207-235` permits the wizard to add that property.
- Current state: source lookup and permission mechanics are proven; exact eval-task programmer propagation and final error-precedence summary remain to be completed.
- Current blocker: none.
- Next step: trace the harness eval task into `RUN_ACTIV.progr`, confirm `#4` is the permission principal, and report exact source paths/lines plus uncertainties to the parent agent.
