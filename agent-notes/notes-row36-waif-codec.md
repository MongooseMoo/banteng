# Row 36 WAIF codec checkpoint

- Scope remains Barn v17 WAIF serialization/deserialization only; no tests, Git, plan, Ward, or production edits have been performed.
- `db/format/reader.go:25-35` keeps load-only `savedWaifs []waifLoadData`; each entry holds one `types.Value` WAIF plus sparse raw property values keyed by serialized property index.
- `db/format/reader.go:94-130` recognizes `Format Version 17` and dispatches to `parseV17`.
- `db/format/reader_v17.go:46-85` reads numbered objects and anonymous objects, then resolves ordinary property names followed by WAIF property names after all object definitions are available.
- `db/format/reader_value.go:123-206` decodes type code 13. `r N` reads a terminator and returns the exact previously saved `types.Value`; invalid reference indices error. `c N` reads class, owner, a property-definition length, registers the new WAIF before recursive property values (enabling self/back references), reads sparse property-index/value pairs until a negative index, reads a terminator, and stores the raw indexed values for later name resolution.
- Current observations needing writer/spec/test comparison: the creation marker's serialized index is not parsed or checked; the property-definition length is read but discarded by the main reader; marker terminator content is read but not validated; duplicate sparse indices overwrite in the temporary map; out-of-range property indices are later ignored.
- `db/format/reader_helpers.go:234-271` resolves indexed properties after object property-name resolution. It finds the class object, filters its final property order to colon-prefixed names, strips the colon for in-memory WAIF storage, and sets the property. Missing classes and out-of-range indices are silently skipped. `savedWaifs` is then cleared.
- `db/format/reader_value.go:292-335` has a separate skip-only type-13 path; unlike the main decoder, it uses the serialized property-definition length to skip additional values from index 32 onward. This requires comparison with the writer and v17 format authority before classifying it.

Current blocker: none after the parent authorized this exact forced-checkpoint file.

## Second checkpoint

- `db/format/writer.go:12-47` defines v17 type code 13 and allocates both `waifIndex` and `nextWaifID`, but repository search shows `waifIndex` is never read or written after construction.
- `db/format/writer.go:168-242,244-272` routes tagged and raw `TYPE_WAIF` values to `writeWaif`.
- `db/format/writer.go:339-398` currently increments `nextWaifID` and emits `c N` for every encounter. Despite its comment promising later `r N` references, it never detects an already-seen identity and never emits `r`. Therefore repeated aliases deserialize as distinct WAIF identities, and a self/back-reference re-enters `writeWaif` recursively instead of closing through a reference.
- The writer serializes class and owner, derives the allowed property-definition list from `snapshot.PropertyNames[classID]` filtered to colon-prefixed entries, strips the colon for lookup against in-memory WAIF properties, writes only properties present in that name-to-index map, then writes `-1` and `.`. A missing/recycled/invalid class produces a zero-length propdef list and silently drops all WAIF properties.
- `types/waif.go:8-31` represents a WAIF as a `Value` whose pointer refers to one mutable `waifRep {class, owner, properties}`. Every `NewWaif` allocates a distinct identity.
- `types/waif.go:38-47,52-96` defines equality and dedup identity by `waifRep` pointer, exposes class/owner, mutates properties in place through aliases, exposes the stable pointer through `WaifIdentity`, and enumerates property names from a Go map (nondeterministic order, though serialized property indices carry names indirectly).
- The closest direct type tests are `types/value_struct_test.go:243-261` (distinct/stable identity and alias-visible mutation) and `types/review_test.go:28-84` (reference mutation and identity equality). They do not exercise the database codec.
- No dedicated WAIF reader/writer test was found under `db/format/*_test.go`. Generic coverage consists of v17 fixture loading (`reader_test.go:13-44`, with no WAIF assertions), external Mongoose fixture loading if present (`reader_test.go:239-261`), ordinary-property round trips (`reader_test.go:320-378`, `dump_persistence_test.go:11-79,81-174`), and checkpoint loadability (`checkpoint_test.go:9-28`). None asserts WAIF identity/backrefs, class, owner, or WAIF properties after round trip.

Current blocker: none. Remaining bounded work is normative spec matching, codec-related permission/error tracing, and exact gap classification.

## Third checkpoint

- The controlling normative codec section is `spec/database.md:3-30` (fresh Toast source/managed behavior authoritative; `src/waif.cc` owns WAIF records), `:32-52` (WAIF introduced at DB version 14; Barn reads v4/v17 and writes v17), `:82-109` (tag 13 and exact creation/reference shapes), `:211-230` (current Barn IO/checkpoint status and required MOO-visible round-trip preservation), and `:232-238` (focused tests do not replace managed Toast/full functional round trips).
- `spec/database.md:106-109` explicitly requires first creation as `c <index>` with class, owner, indexed non-clear properties, `-1`, `.`, and later aliases as `r <index>`, `.`. The reader mostly implements both record shapes; the writer contradicts the later-reference requirement because it always emits creation records.
- `spec/types.md:9-23,223-237` identifies type code 13 and gives only a high-level lightweight/prototype/property API description. Its `Custom MOOWaif struct` Go-mapping text does not match the current `types.Value` tagged-union plus private `waifRep` implementation.
- `spec/objects.md:428-455` normatively says WAIFs are prototype-based, GC'd, use `.:` properties, and inherit default property values/verbs from their class. It does not specify durable record identity, owner encoding, indexed property layout, or reader error policy; those live in `spec/database.md`.
- `spec/objects.md:458-473` begins a general object permission model, but no codec permission check is specified. The codec reads/writes WAIF owner as data and returns Go `error` values for malformed IO/records; it does not raise MOO `E_PERM` or consult task permissions.

Current blocker: none. Remaining work: exact malformed-record/error inventory, snapshot property-order owner tracing, and a final cross-check for hidden dedicated codec tests/spec claims.
