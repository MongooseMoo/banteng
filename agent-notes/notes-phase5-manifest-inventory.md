# Phase 5 manifest inventory

## Current task

- Read-only inventory of Phase 5 in `docs/reports/banteng-implementation-plan.md`.
- Compare Banteng's production `BuiltinCatalog` manifest with the exact pinned stock and mongoose authority manifests/sources.
- Report canonical stock and mongoose name coverage, missing and extra names, authority paths, evidence commands, and the literal first implementation slice implied by the plan.

## Current state

- Phase 5 is at `docs/reports/banteng-implementation-plan.md:626-666`.
- Both exact Toast manifests pass `scripts/verify_toast_profile_wsl.sh` in WSL.
- Stock authority: `profiles/toast/stock-wsl-testdb.json`, source `/root/src/toaststunt` at `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Mongoose authority: `profiles/toast/mongoose-wsl-mongoose.json`, source `/root/src/toaststunt-mongoose` at `72e3c7f96ce7a41fdeba793aef8818dc4408072e`.
- The Phase 5 extractor and both required checked-in builtin-name fixture files do not exist yet.
- Toast registration is centralized by `src/functions.cc::bi_function_registries`, whose listed registration functions call `register_function` or `register_function_with_read_write` in the pinned source tree.
- The current production manifest contains these canonical names (sorted): `abs`, `add_property`, `add_verb`, `all_members`, `boot_player`, `caller_perms`, `chr`, `clear_property`, `connected_players`, `connection_info`, `connection_name`, `create`, `decode_binary`, `delete_property`, `delete_verb`, `disassemble`, `dump_database`, `encode_binary`, `equal`, `eval`, `explode`, `force_input`, `function_info`, `index`, `is_clear_property`, `is_player`, `kill_task`, `length`, `listappend`, `listdelete`, `listen`, `listinsert`, `listset`, `load_server_options`, `mapkeys`, `max`, `min`, `move`, `new_waif`, `notify`, `parent`, `properties`, `property_info`, `queued_tasks`, `raise`, `random`, `read`, `recycle`, `reseed_random`, `reverse`, `rindex`, `run_gc`, `seconds_left`, `server_log`, `set_connection_option`, `set_player_flag`, `set_task_perms`, `set_thread_mode`, `set_verb_args`, `set_verb_code`, `set_verb_info`, `setadd`, `setremove`, `shutdown`, `strcmp`, `strsub`, `suspend`, `switch_player`, `task_id`, `task_perms`, `thread_pool`, `threads`, `ticks_left`, `time`, `tofloat`, `toint`, `toliteral`, `toobj`, `tostr`, `typeof`, `valid`, `value_bytes`, `verb_args`, `verb_code`, `verb_info`, `yin`.
- The current production manifest count is 86.
- Preprocessing every pinned stock registration translation unit with its actual generated `config.h` and include paths, then extracting literal `register_function` and `register_function_with_read_write` calls, yields 239 unique stock canonical names. This excludes disabled conditional registrations such as `background_test`.
- The same profile-conditioned extraction against the pinned Mongoose source printed the same sorted name sequence; an exact set comparison and count are still pending.
- Current repo HEAD is `ca9dfbd fix: pin generated Toast configuration`. Both profiles now pin `generated_config_file` and `generated_config_checksum`, and both exact verification commands still pass.
- Exact comparison proves stock and Mongoose each expose 239 canonical names and their sorted sets are byte-for-byte identical.
- Banteng's 86 production names are a strict subset of both pinned sets: 153 names are missing and zero names are extra.
- The central registration authority in both sources is `src/functions.cc:45-87`; it invokes every module registrar. `background_test` is conditionally registered in `src/background.cc:326-360` but is absent from both preprocessed sets because neither pinned configuration defines `BACKGROUND_TEST`.
- This note is a required checkpoint artifact and must remain uncommitted.

## Blockers

- No inventory blocker.

## Next action

- Return the exact missing/extra inventory, grouped source authority paths, preprocessing evidence, and the plan-order conclusion to the parent agent. No production or test files were edited.
