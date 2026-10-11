# Yin Phase 4 Banteng trace

## Current assignment

- Read-only trace for `audit_yin_yields_when_thresholds_crossed`.
- Determine why Banteng currently returns `E_VERBNF` and identify the smallest implementation and test surface.
- Do not edit source, change Git state, or run Gradle.

## Current findings and observations

- Exact fixture: `../moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml:776-804`.
- The fixture sets foreground ticks to 60000, creates a ready zero-delay fork, then calls `yin(0, 59999, 4)` and expects `main-before`, `fork`, `main-after`.
- No `yin` or `Yin` path exists in Banteng Java source or tests.
- `MooVm.invokeBuiltin()` advances the CALL instruction, looks up the builtin by name, and raises `E_VERBNF` when the spec is absent. `BuiltinCatalog` has task budget builtins and `suspend`, but no `yin` manifest entry.
- The catalog handler contract already receives current remaining ticks and seconds.
- Existing `Result.suspend(0)` makes the VM `SUSPENDED`. `PublicationScheduler.publishSuspension()` commits that segment, advances the publication ticket, and requeues the continuation at a later ticket.
- For this exact row, the prior `fork (0)` child already has the earlier ready ticket, so the existing zero-delay suspension path orders the child before the resumed parent without a new scheduler primitive.
- The direct ToastCore database help documents `yin([INT time, INT minimum ticks, INT minimum seconds]) => INT` and describes suspension when ticks or seconds run low. The exact server implementation search remains to be located in the pinned ToastStunt source.

## State and blocker

- State: failure cause and existing Banteng yield mechanism traced; exact argument/default/threshold semantics are the only remaining check.
- Blocker: none.

## Next action

- Locate the pinned ToastStunt registration/implementation or its generated name, then report the smallest source and regression-test surface to the parent.
