# Issue 90: shared persistence grammar

## Goal
Extract one activation header writer/reader, one program-section reader, shared raw property slots and named v17 wire constants without weakening caller validation.

## State
Branch campaign/90-shared-persistence starts at f90c342 (issue11). Prior read-only source packet established current Java and Toast producer/reader behavior. Parent owns publication; no push authorized.
Merged origin/master 324cc9c. Shared helpers and authored SharedPersistenceTest are implemented. First authored-test invocation passed production/jcstress/JMH compilation but test compilation rejected a nullable error.getMessage().contains assertion; replaced with direct expected-message equality without suppressions. Direct numeric tag lookup replaces the temporary values() scan. Removed forwarding methods made redundant by the shared program reader. Final authored-test invocation passed with source frozen: BUILD SUCCESSFUL in 1m 39s. git diff --check passed.

## Decisions and why
Smallest change: private activation header helpers in LambdaMooV17Codec reuse its private value/context owners. Both queued/suspended writers use anonymous numeric recv=-1, canonical numeric vloc=existing encoded ID and unchanged typed values; preserve other nonobject behavior. Header reader parses once and returns raw compatibility numbers so queued and suspended callers retain their current distinct checks. No VM/DTO numeric recv field and no claim about native anonymous varargs output. Approximately 250–400 production lines relocated/extracted plus mechanical wire-tag replacements and focused tests.

Both verb loops call existing writeProgramSource. ProgramSectionReader uses DbScanner/ProgramSlot and standard LongToIntFunction lookup; RawPropertySlot is extracted only from identical V5/V17 records. V4 direct properties remain unchanged. V17Format uses explicit persisted values0–14 and activation markers, independent of internal complex flags. Existing validation is retained.

## Surprises
Native numeric recv for primitive calls is not universally reconstructible from typed receiver/vloc; this bounded change preserves current nonobject behavior. Modern Toast uses typed vloc despite its numeric varargs writer defect; own canonical anonymous numeric vloc is the existing encoded ID. Queued anonymous numeric-vloc equality remains, so acceptance of unobserved native anonymous dumps is not claimed.

## Next step
Parent handles integration/review/publication after its current PR queue drains. No push or publication performed. Full CI and restart suites were not run locally.

## Draft PR
Title: Share persistence activation and program grammars

Extract the common queued/suspended activation header grammar while retaining each caller's existing validation. Both writers retain typed references and use the same anonymous legacy receiver and encoded location fields. Share program-section parsing across V4/V5/V17, property slots across V5/V17, and name persisted V17 tags and markers.

Validation: authored `:test --tests world.mongoose.banteng.persistence.SharedPersistenceTest` passed under Debian/JDK25. It covers canonical anonymous headers and distinct caller guards, malformed program sections, unrepresentable verb source, and byte-identical rewriting of the checked-in native object activation fixture. Anonymous cases are synthetic; this does not establish acceptance of unobserved native anonymous dumps. Native primitive receiver metadata remains outside this bounded extraction.
