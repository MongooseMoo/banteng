# Row 36 WAIF primitive contract trace

- Scope: read-only extension of row 36 to establish the complete Barn WAIF value contract needed before a Java value type.
- Representation: `types.Value` carries `TYPE_WAIF` plus a GC-traced pointer to `waifRep`; the payload owns immutable class/owner fields and a mutable property map.
- Identity/copy/equality: every `NewWaif` allocates a distinct payload; copied values alias the same payload; equality and `WaifIdentity` use payload identity, not structural state.
- Mutation: `SetProperty` mutates the shared payload in place. Recursive self-containment through WAIF/list/map values is rejected with `E_RECMOVE` by identity.
- Dispatch/frames: WAIF verb lookup uses the class and colon-prefixed method name while preserving the concrete WAIF as `this`; row-36 callers behavior follows from that split.
- Type/literal: externally visible type code is 13 and `typeof` returns 13; `String`/`toliteral` render `<waif #<class>>`; `tostr` currently falls through to an empty string; numeric/object conversions raise `E_TYPE`.
- Truth/order/hash gaps: current `Truthy` makes WAIF false, contradicting normative `spec/operators.md` and `spec/types.md` "everything else" truthiness. Relational ordering and sort keys reject WAIF with `E_TYPE`. `value_hash` hashes only the literal, so distinct same-class WAIFs collide.
- Map-key gap: normative map specs say WAIF keys are valid by reference, but `types.IsValidMapKey` excludes WAIF, so VM map construction/update and map builtins reject them. The internal fallback key hash would also use the non-identity literal.
- Properties/errors: `.owner` and `.class` are special reads; a recycled class makes `.class` return `#-1`; ordinary reads check the instance map then class `:<name>` property. Writes to owner/class/wizard/programmer raise `E_PERM`; self-containment raises `E_RECMOVE`; arbitrary writes mutate the instance without class-property existence or permission checks. Missing properties/classes raise `E_PROPNF`.
- Lifecycle gap: `RegisterWaif`/WAIF stats registry exists but `new_waif` has no caller, so the registry appears unpopulated; deferred finalization separately roots persistent/task WAIFs and invokes executable `:recycle`.
- Tests found: focused tests pin identity, aliasing mutation, and recursive-containment identity, but none pin truthiness, map keys, `tostr`, property permissions, or callers frames.
- Codec: a read-only subagent is tracing v17 creation/reference serialization and deserialization; awaiting its report.
- Current blocker: none beyond the pending codec trace.
- Next step: integrate exact v17 codec findings and return the complete marked contract to the parent.

## Spec-correction audit checkpoint

- Named authority commit verified: `e790d04dd0c32899ad9dcc8d60e2e4285de9a0e0`, `test: freeze Toast WAIF semantics`.
- The commit adds `language/waif_authority.yaml` and `server/waif_dump_persistence.yaml`, totaling 291 lines.
- No WAIF assertions from those files have been interpreted yet.
- No Barn spec files or tracked files have been changed.
- Current blocker: none; the mandatory checkpoint required this note before reading the committed authority rows.
- Next step: read the two committed YAML files exactly, enumerate their Toast-proven behaviors, and compare only the listed Barn WAIF spec passages.

## Barn normative correction implementation checkpoint

- Edited only `C:/Users/Q/code/barn/spec/types.md` and `C:/Users/Q/code/barn/spec/operators.md`.
- Added only Toast-proven WAIF truthiness, reference equality/aliasing, construction/intrinsics, string/literal forms, recursive-containment errors, relational behavior, and indexed-handler dispatch semantics.
- Indexed assignment now states that `:_set_index` receives key/value and its return value replaces the indexed base; returning `this` preserves a local WAIF.
- Both files cite committed `waif_authority.yaml` by relative path.
- `git diff --check -- spec/types.md spec/operators.md` passed with no output.
- Diff summary: 2 files changed, 56 insertions, 7 deletions (`spec/operators.md`: 36 lines; `spec/types.md`: 27 lines).
- No other Barn file, implementation file, plan, Git index, or Git history was changed.
- Current blocker: none. The requested handoff is ready; no commit was requested.
