# OBJ Toast audit checkpoint

- Scope: read-only audit of OBJ value-family behavior in pinned Toast source `/root/src/toaststunt` at `aecc51e9449c6e7c95272f0f044b5ba38948459e` and existing managed `moo-conformance` rows.
- Boundaries: do not inspect Barn; do not edit tracked files; do not use skills.
- Current repository state: `master` is ahead of `origin/master` by 48 commits and has many pre-existing untracked notes/artifacts. No tracked changes were made by this audit.
- Current observation: Banteng contains Toast profiles, oracle identity reports, `scripts/run_toast_wsl.sh`, and value-authority reports; the current repo file-name scan did not itself reveal the managed conformance row source.
- Verified pin: `/root/src/toaststunt` is exactly `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Existing managed coverage found: OBJ `typeof`, basic `tostr`/`toliteral`, `toint`/`tofloat`/`toobj` conversions, false truthiness, ordinary equality and cross-type inequality, object map keys/values and lookup, and OBJ-as-index type errors for list/string bases. Existing dump/restart suites cover properties, integers, floats, strings, booleans, and waifs, but no dedicated OBJ value persistence suite was found.
- Toast owners found: `include/structures.h` for range/type/construction; `parser.y` for `#` literals; `numbers.cc` plus `objects.cc` for conversions; `utils.cc` plus `map.cc` for truth/equality/order/map identity/copy; `execute.cc` for reference/update errors; `list.cc` for `tostr`/`toliteral`; `db_io.cc` plus `db_file.cc` for persistence.
- Observed gaps: no managed OBJ extreme-literal/overflow row, no distinct unequal/order/extreme map-identity row, no OBJ-as-base read/write error row, and no OBJ dump/restart boundary row.
- Blocker: none.
- Next action: report the deduplicated owners and smallest missing managed-oracle set to the parent agent; no further writes.
