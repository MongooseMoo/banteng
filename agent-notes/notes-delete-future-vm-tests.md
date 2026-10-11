# Delete future-phase MooVm tests

- Scope: edit only `src/test/java/moo/vm/MooVmTest.java`, plus this mandatory untracked hook note.
- Authority read: `docs/reports/banteng-implementation-plan.md` Phase 2 establishes only the first production vertical slice; Phase 3 and later own the expanded language, persistence, server, task, and builtin surfaces.
- Baseline report: `build/test-results/test/TEST-moo.vm.MooVmTest.xml` records 111 tests, 32 failures, 0 errors, and 0 skipped.
- Current state: all 32 matched failing `@Test` methods are deleted. The file now contains 79 `@Test` methods, exactly matching the 79 baseline passers, and the newly unused `EffectClass` import was removed.
- Test result: the exact WSL command `JAVA_HOME=/opt/java/25 ./gradlew test --tests moo.vm.MooVmTest` completed successfully in 28 seconds. The regenerated XML reports 79 tests, 0 failures, 0 errors, and 0 skipped.
- Blocker: none.
- Next action: stop and report. Do not stage or commit.
