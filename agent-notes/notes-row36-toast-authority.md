# Row 36 Toast authority subagent

- Scope: read-only trace for `audit_waif_callers_preserve_this_and_verb_location` in pinned Toast source `/root/src/toaststunt`.
- Observed: source identity is exactly `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Observed: WAIF construction is in `src/waif.cc` (`new_waif`, `bf_new_waif`); WAIF verb dispatch branches are in `src/execute.cc`; `bf_callers` is at `src/execute.cc:3733`; WAIF verb names use the prefix declared in `src/include/waif.h`.
- Tried: read the RE skill, verified source identity, and searched exact WAIF/callers symbols.
- Worked: the exact construction, dispatch, and frame-reporting symbols are located.
- Did not work: the first `rg` pattern was malformed by quoting and returned no source evidence; a corrected narrower pattern succeeded.
- Blocker: none.
- Next step: read `new_waif`/`bf_new_waif`, the `OP_CALL_VERB` WAIF branch, activation construction, and `bf_callers`, then report exact tuple semantics to the parent.
