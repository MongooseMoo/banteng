# Toast v17 format audit

## Scope and state

- Read-only source audit requested by the parent agent; no production or test files changed.
- Phase 2 requires minimum v4 `Test.db` input, minimum v17 checkpoint input, deterministic v17 output, durable task state, and atomic checkpoint replacement (`docs/reports/banteng-implementation-plan.md:259-362`).
- Primary source is present at `C:/Users/Q/src/toaststunt`.

## Current findings

- `src/include/version.h:36-90` defines append-only on-disk versions; `DBV_Bool` is numeric format 17 and `current_db_version` is the last enumerator.
- `src/db_file.cc:56-57` defines the exact header as `** LambdaMOO Database, Format Version %u **\n`.
- v17 top-level read/write ordering is visible in `src/db_file.cc:846-1032` and `src/db_file.cc:1039-1115`: header; player count and player IDs; values pending finalization; task queue; formerly active connections; one or more object batches followed by zero; verb-program count and programs.
- v17 object syntax is owned by `ng_read_object` / `ng_write_object` at `src/db_file.cc:303-449`: object/recycled header, name, flags, owner, typed location/last_move/contents/parents/children values, verb definitions, property definitions, and property values.
- primitive and recursive value encoding is owned by `src/db_io.cc:76-221,307-420`; values begin with a decimal numeric type tag and newline, then type-specific newline-delimited payload. Programs are source text terminated by a line containing `.`.
- task queue syntax is owned by `src/tasks.cc:1903-2153`: `0 clocks`, queued fork tasks, suspended tasks, and interrupted tasks. VM/task-frame serialization continues through `src/eval_vm.cc:85-134` and `src/execute.cc:3815-4095`.
- `src/server.cc:696-779` writes/reads `<count> values pending finalization` followed by that many typed values.
- `src/server.cc:1843-1931` writes/reads `<count> active connections with listeners` followed by `<player> <listener>` pairs.
- `src/db_file.cc:1249-1275` writes a generation-suffixed temporary file, flushes it, calls `fsync()` on the file, closes it, then promotes it.
- Promotion at `src/db_file.cc:1174-1179` first removes the destination and only then calls `rename(temp, destination)`. This is not atomic replacement and leaves a missing-destination window; the parent must not describe Toast as implementing atomic checkpoint replacement.
- Bundled `test/Test.db` is v4, 2,035 bytes and 243 LF-only lines; it contains no CR, NUL, or high-bit bytes. `test/tests/Anon1.db` is a concrete v17 fixture whose first sections confirm the exact top-level labels and ordering.

## Blocker

- None. The requested source audit is complete. No suites were run.

## Next action

- Report the complete v17 grammar, task snapshot structure, byte behavior, and checkpoint-promotion caveat to the parent agent.

## Completed details

- Audited source revision `e8a353665a106244f5e01edb67239c90411ae584` on local branch `json-v20-db`; tracked source state was clean.
- v17 native-text data is line-oriented C `char` data with no charset decoder or transcoder. Under the pinned WSL/POSIX runtime, explicit newlines are LF bytes. A JVM implementation needs ISO-8859-1/Latin-1 for lossless byte-to-character mapping; it must not use UTF-8 decoding.
- Value tags are the append-only numeric enum from `src/include/structures.h:84-119`: int 0, object 1, string 2, error 3, list 4, clear 5, none 6, catch 7, finally 8, float 9, map 10, iterator 11, anonymous object 12, WAIF 13, boolean 14. The writer never emits iterator tag 11; it serializes the iterator's current key or clear.
- Permanent object records contain name, flags, owner, typed location, typed `last_move`, typed contents, typed parents, typed children, verb definitions, local property definitions, and all property value slots. Object batches end with a standalone zero; verb programs follow and each ends with a standalone dot line. There is no separate EOF marker.
- Durable queue serialization covers queued forked tasks, suspended VMs, and interrupted external/read tasks. It does not snapshot the currently executing task or live thread/socket objects. Fork and suspension start times are rounded to seconds; VM state consists of task-local value, activation stack metadata, source programs, runtime environments, runtime stacks, activation identity fields, temp register, PCs, and optional builtin continuation state.
