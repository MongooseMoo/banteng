# First server v4 database authority

## Scope

This record freezes the minimum LambdaMOO Format Version 4 reader and immutable
world needed to load the exact conformance `Test.db`. The fixture SHA-256 is
`1a3f23ebb549e02ccf5341668425118fcdc935b977096add87bc2a8ef29d408e`.
It does not authorize v17, a writer, task records, active connections, anonymous
batches, or a generic persistence abstraction.

## Normative Barn specification

`../barn/spec/database.md:32-52` requires v4 input. Its detailed top-level and
object descriptions at `:54-179` are v17-only; the normative spec does not
describe the v4 object layout. That absence is resolved here from the exact
fixture and verified Toast source rather than inferred from the v17 layout.

## Current Barn implementation reference

The current path is `server.(*Server).LoadDatabase` -> `format.LoadDatabase` ->
`parseDatabase` -> `parseV4` -> `readObjectV4` / `readValue` /
`readVerbCode` -> startup repair -> `Database.NewStoreFromDatabase`.

- `../barn/server/server.go:74-84`
- `../barn/db/format/reader.go:37-53,75-130`
- `../barn/db/format/reader_v4.go:12-313`
- `../barn/db/format/reader_object.go:333-378`
- `../barn/db/format/reader_value.go:11-91`
- `../barn/db/format/startup_repair.go:10-210`
- `../barn/db/store/builder.go:23-148`
- `../barn/db/store/object.go:18-225`

Barn agrees on section order, scalar fields, verb metadata, property slots,
program attachment, and the final topology of this fixture. Banteng must not
copy Barn's observable differences: substring header matching, trimmed object
names, arbitrary slot IDs, discarded persisted link order, startup repair in
place of v4 validation, unparsed program source, or omission of the players
list.

## Verified Toast authority

Source identity is `/root/src/toaststunt` at
`aecc51e9449c6e7c95272f0f044b5ba38948459e`.

- `src/db_file.cc:60-77,222-292`: raw v4 object fields and object reader.
- `src/db_file.cc:447-593,750-819`: validation and upgrade of persisted link
  chains.
- `src/db_file.cc:838-881,916-1024`: exact top-level v4 section order and
  program attachment.
- `src/db_io.cc:163-221`: tagged persisted values.
- `src/db_verbs.cc:195-213,702-720,836-857`: packed permissions, verb lookup,
  and stored program installation.
- `src/include/db.h:487-500`: argument and preposition codes.
- `src/tasks.cc:2008-2111` and `src/server.cc:1821-1863`: task and optional
  old-format connection tails.

## Exact fixture map

| Lines | Meaning |
| --- | --- |
| 1 | exact v4 header |
| 2-5 | 8 object slots, 5 programs, dummy 0, 2 players |
| 6-7 | players `#3`, `#4` |
| 8-169 | eight sequential live objects |
| 170-240 | five stored programs |
| 241-243 | zero clocks, queued tasks, and suspended tasks |
| EOF | no active-connections section, valid for this old format |

Each object stores name, an old handles line, flags, owner, location, first
contents, next contents, parent, first child, sibling, verbs, property names,
and property slots. All eight handles lines are empty but must be consumed.

| ID | Name | flags | owner | location | contents | next | parent | child | sibling | verbs | properties |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `#0` | System Object | 16 | 3 | -1 | -1 | -1 | 1 | -1 | 2 | 3 | 6 |
| `#1` | Root Class | 144 | 3 | -1 | -1 | -1 | -1 | 0 | -1 | 0 | 0 |
| `#2` | The First Room | 0 | 3 | -1 | 3 | -1 | 1 | -1 | 3 | 1 | 0 |
| `#3` | Wizard | 7 | 3 | 2 | -1 | 4 | 1 | -1 | 4 | 0 | 0 |
| `#4` | Programmer | 3 | 4 | 2 | -1 | -1 | 1 | -1 | 5 | 0 | 0 |
| `#5` | Anonymous Class | 256 | 3 | -1 | -1 | -1 | 1 | -1 | 6 | 0 | 0 |
| `#6` | Server Options | 0 | 3 | -1 | -1 | -1 | 1 | -1 | 7 | 0 | 0 |
| `#7` | Waif Class | 128 | 3 | -1 | -1 | -1 | 1 | -1 | -1 | 1 | 0 |

The derived immutable topology is `#1.children = [#0,#2,#3,#4,#5,#6,#7]`
and `#2.contents = [#3,#4]`. Objects `#0` and `#2` through `#7` have parent
`#1`; `#3` and `#4` have location `#2`; every other location is `#-1`.

The six `#0` properties are `nothing=#-1`, `system=#0`, `object=#1`,
`anonymous=#5`, `server_options=#6`, and `waif=#7`. These are the only tagged
values in the fixture; every value is tag 1 followed by a signed object ID.

The five stored programs are `#0:0 do_login_command`,
`#0:1 handle_uncaught_error`, `#0:2 handle_task_timeout`, `#2:0 eval`, and
`#7:0 new`. Program headers use zero-based verb indices. The reader must reject
a missing object/index and preserve the source bytes through the terminating
dot line.

## Frozen implementation contract

1. A concrete final v4 reader streams ISO-8859-1 and requires the exact header.
2. It reads the four header counts, retains players `[3,4]`, and requires each
   live object in slot `N` to be `#N`.
3. It preserves names and source bytes without trimming and consumes every
   handles line.
4. It retains raw relation IDs only while reading, follows persisted sibling and
   next chains, and validates reciprocal parent/location relations.
5. It supports only the exercised persisted value form, object-reference tag 1;
   every other tag is rejected as outside this slice.
6. It zips local property names to slots and attaches each program to an existing
   verb after validating the zero-based index.
7. It requires zero clocks, queued tasks, and suspended tasks, then accepts EOF
   as the absent connections section.
8. It returns immutable world, object, verb, and property records. A concrete
   `WorldTxn` is the only runtime-visible path to those records; there is no
   store interface, adapter, facade, or mutable-world escape hatch.

The durable broader proof remains `basic/arithmetic.yaml:6`,
`arithmetic::addition`. This report did not substitute a manual server run for
that managed gate.
