# Barn OBJ authority audit checkpoint

- Scope: read-only audit of Barn commit `5a89ba0250a654bf4ae9383aecd4e6f2b0b363a1`; Barn working tree not inspected or modified.
- Current findings: OBJ is an `int64`/`ObjID` scalar; parser and DB parsing use signed 64-bit parsing; `NewObj` accepts the full signed range without validity checks; equality is tag-and-ID exact; map identity is typed ID; OBJ truth is always false in `Value.Truthy`; string/literal form is `#<signed decimal>`.
- Spec disagreements found: `spec/types.md` says everything except listed empty/zero values is true, but implementation makes all OBJ false; `spec/operators.md` excludes OBJ ordering while VM compares OBJ IDs; `spec/database.md` says OBJ values are `#N` while format implementation needs confirmation; `spec/types.md` says broad hashable map keys while implementation limits map keys to scalar families.
- State: parser, value representation, conversions, equality, map ordering/identity, truth, literal formatting, VM indexing/error behavior, and v17 writer/read-helper encoding audited.
- Serialization detail: `Writer.writeValue` emits type code 1 then bare signed decimal via `writeObjID`; `readObjID` accepts either `#N` or bare `N` and parses signed 64-bit. Writer always emits format v17; reader dispatches formats 4, 5, and 17. Database I/O uses raw Go strings/bytes with newline and has no latin-1 transcoder despite `spec/database.md` requiring latin-1.
- VM detail: ordinary list/string indexing requires INT and gives E_TYPE/E_RANGE; OBJ works as a map key through typed ID identity. Separately, `executeListRange` accepts OBJ/ANON endpoints as integer IDs, a likely unrelated semantic anomaly.
- Blocker: none.
- Next action: return the concise audit to the parent agent.
