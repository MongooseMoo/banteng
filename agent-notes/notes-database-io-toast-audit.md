# Banteng database I/O audit against local ToastStunt

## Scope and authority

- Banteng checkout: `C:\Users\Q\code\banteng`, HEAD `de14645ccece48b20f18c4ee88e2e6be00fc15b8`.
- ToastStunt checkout explicitly requested by the user: `C:\Users\Q\src\toaststunt`, branch `json-v20-db`, HEAD `e8a353665a106244f5e01edb67239c90411ae584`.
- ToastStunt has pre-existing untracked local artifacts but its relevant source changes are committed on `mongoosemoo/json-v20-db`.
- Banteng has many pre-existing untracked notes/reports. Preserve them. This note is task-specific and must remain uncommitted.
- GitHub target is `MongooseMoo/banteng`; open issues currently run through #23. Do not publish issue text until Q has read the exact drafts.

## Confirmed findings

1. Toast JSON database v20 is unsupported by Banteng.
   - Toast selects a directory backend from `manifest.json` and implements read/write in `db_backend.cc`, `db_json_load.cc`, and `db_json_dump.cc`.
   - Banteng `Banteng.call()` opens `--database` as a text file, dispatches only headers 4/5/17, and `LambdaMooV17Codec` writes one text file.
   - Existing issue #19 is an architecture/refactor issue for `DbCodec`; it does not track the functional JSON-v20 interoperability requirement.

2. The v4 reader is a narrow bootstrap subset, not Toast-compatible.
   - It accepts only exact format 4, requires every slot to be live (no `#N recycled`), accepts only property value tag 1 (object), requires property slot count to equal local property-name count, requires a program for every verb, and requires zero clocks/queued/suspended tasks followed by EOF.
   - Toast v4 accepts recycled slots, all value types available in that version, inherited property slots, verbs without programs, queued/suspended tails, and active connections.
   - Toast canned `Suspended.db` is format 4 and is an appropriate regression fixture.

3. The v5 reader loses or rejects valid Toast state.
   - It maps only the first `min(local property names, total property slots)` slots and drops clear/inherited slots, although Toast stores local slots followed by flattened ancestor slots.
   - It requires zero clocks/queued/suspended tasks and EOF, so it cannot restore valid task/connection tails.
   - It defaults missing program entries to empty source, which preserves no distinction between absent and present-empty compiled programs.
   - Its topology repair accepts the Toast `Broken2.db` through `Broken5.db` fixtures after silently changing invalid types, cycles, and reciprocal-link inconsistencies. Toast's `dbpriv_validate_hierarchies()` returns failure for those cases; only missing object references are repairable.

4. The v17 queued-task reader/writer hardcodes wire values Toast does not.
   - Toast writes `first_lineno = find_line_number(ft.program, ft.f_index, 0)`; Banteng writes `1` and rejects any input not equal to `1`.
   - Toast writes both called verb (`a.verb`) and full matching verb name (`a.verbname`); Banteng rejects them unless identical and writes both from one local.
   - This rejects valid Toast checkpoints and loses matching-name state on Banteng checkpoints.

5. The v17 task codec cannot read or write suspended built-in continuations.
   - Toast `write_activ()` writes built-in name and builtin-specific state whenever `bi_func_pc != 0`; `read_activ()` restores it through registered read/write hooks.
   - Banteng throws `unsupported suspended builtin continuation` on both read and write when `builtinFunctionCounter != 0`.
   - Existing issue #7 broadly tracks every `unsupported X` persistence site. Treat this as already tracked and do not draft a duplicate issue; surface the concrete evidence under #7.

6. The v17 reader does not implement Toast hierarchy admission/repair.
   - Banteng validates inheritance parent/child reciprocity and cycles, but does not validate location cycles or location/contents reciprocity.
   - Toast `dbpriv_validate_hierarchies()` validates types, removes invalid references, rejects parent and location cycles, and rejects parent/child plus location/contents inconsistencies.
   - Result: Banteng can admit corrupt location/contents graphs Toast refuses, while rejecting some repairable invalid references Toast removes.

## Existing issues checked

- #7: broad unsupported persistence/compiler constructs and wording.
- #11: extract durable-task codec (refactor only).
- #16: deduplicate readers (refactor only).
- #19: persistence abstraction/composition root (refactor only).
- No open issue currently names JSON v20 support, v4/v5 functional completeness, queued-task first-line/full-verb fidelity, built-in continuation persistence, or v17 hierarchy admission/repair.

## Verification state

- `gradlew test --tests 'moo.persistence.*'` ran 50 tests and failed 8 `AnonymousObjectPersistenceTest` cases because JNR could not open `libcrypt.so.1`; this is an environment failure rather than codec evidence.
- The eight other persistence test classes passed in one focused Gradle invocation.
- Direct Java 25 probes loaded Toast `Anon1.db` through `Anon6.db`; Banteng rejected Toast v4 `Suspended.db` with `invalid suspended-tasks tail: 1 suspended tasks`.
- Direct Java 25 probes loaded Toast `Broken1.db` through `Broken5.db`; Banteng silently repaired/admitted Broken2-5 even though Toast's validator fails closed on invalid types, cycles, and reciprocal inconsistencies.
- Toast's committed `test/json_v20_canned_roundtrip.rb` completed with exit 0 under WSL against the checkout's local `build/moo`.
- Interrupted-task wire classification is not an additional gap: Toast's reader converts an interrupted record to an immediately runnable `TASK_SUSPENDED` with `E_INTRPT`, matching Banteng's semantic conversion.
- Atomic promotion appears aligned at the source level and is not a drafted finding.
- Q approved the exact five drafts. They were created and re-fetched from GitHub:
  - #24 JSON-v20 support (`enhancement`; closed at Q's request)
  - #25 v4 completeness (`enhancement`)
  - #26 v5 fidelity/admission (`bug`)
  - #27 queued-task wire fidelity (`bug`)
  - #28 v17 hierarchy admission/repair (`bug`)

## Next action

1. Keep this audit note untracked.
2. Await prioritization or implementation direction; no code changes were authorized by this audit task.
