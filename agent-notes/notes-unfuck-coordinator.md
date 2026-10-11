# Unfuck-the-codebase coordinator notes

**Date:** 2026-08-16
**Goal:** Q asked to start cleaning up banteng (built conformance-test-first, so structure is accretive). Plan: audit → open GitHub issues (architectural, DRY, cleanup).

## Observed so far
- Repo: MongooseMoo/banteng, issues enabled, **zero existing issues** (clean slate).
- ~35k lines Java, 62 main-source files under src/main/java/moo/.
- God classes: BuiltinCatalog 7370, MooRuntime 3617, MooVm 2634, WorldTxn 2494, PublicationScheduler 2328, ToastV17ProgramLayout 2229, VmState 1210.
- ~180 notes-*.md scratch files untracked in repo root (hygiene issue candidate).

## In flight
Four parallel scout agents spawned (audit-godclass, audit-dry, audit-deadcode, audit-arch). Each writes its own notes file:
- notes-unfuck-godclass-audit.md
- notes-unfuck-dry-audit.md
- notes-unfuck-deadcode-audit.md
- notes-unfuck-arch-audit.md

## Audits complete
All four reports landed (notes-unfuck-{godclass,dry,deadcode,arch}-audit.md). Arch verdict: package layering is clean (no cycles); rot is intra-layer. Star finds: 22-Optional Result record (BuiltinCatalog:6864), waif/anon variable-name hack (MooVm:570), triplicated persistence scanners, wizard-flag `& 4` in 13 places, fabricated verb_cache_stats.

## Q's corrections (IMPORTANT, saved to memory too)
1. **Precompilation principle:** never write "either X or Y" in an issue — make the decision precisely.
2. **Goal is completion:** wire things up fully; match ~/src/toaststunt and ~/code/barn.
3. **promote-numbers is wanted:** it's the mongoose branch of toaststunt — `mongoosemoo/promote-to-float` (commits 734f140→5458af9, PROMOTE_NUMBERS in options.h, numbers.cc/utils.cc). Full semantics: binary-op promotion, comparisons, builtin coercion.

## Issues filed so far
- #1 waif/anon variable-name finalization bug
- #2 in-band finalization sentinel strings + magic 12
- #3 ObjectFlags consolidation (wizard `& 4`)
- #4 .program diagnostics (match toaststunt)
- #5 real verb cache + verb_cache_stats
- #6 ServerLog + --log-level/--log-file (toaststunt oklog/errlog)
- #7 promote-numbers full mongoose semantics + phase-vocabulary purge

## Tooling installed (Q's request: CLI-driven refactoring, never cut-and-paste)
- OpenRewrite wired into build.gradle.kts: plugin org.openrewrite.rewrite 7.39.0 + rewrite-recipe-bom 3.37.0 (+static-analysis, migrate-java modules). 2,300 recipes discovered; verified against Java 25 sources (FinalizeLocalVariables dry run produced a 16k-line patch).
- Gotchas solved: dependency verification (regenerated gradle/verification-metadata.xml via --write-verification-metadata sha256), lockfile (--write-locks), and warning.mode=fail (plugin trips deprecated Project.getProperties → run rewrite tasks with --warning-mode summary).
- Wrapper: scripts/rewrite.sh [--apply] <recipe...>. Modified tracked files pending commit: build.gradle.kts, gradle.lockfile, gradle/verification-metadata.xml, scripts/rewrite.sh (new).
- MISTAKE LOG: I ran `git checkout -- .` mid-flow and reverted my own build edits; re-applied. Don't use broad checkout again.
- Heredoc batch for issues 8-11 hit a bash quoting error; switched to writing bodies as scratchpad files (issue08-11.md) + gh --body-file.

## Issues #8-#15 filed
#8 sealed BuiltinResult, #9 BuiltinCall+BuiltinHosts, #10 buildManifest per-family split, #11 DurableTaskCodec + runtime↔scheduler cycle, #12 ToastV17ProgramLayout split + ArchUnit carve-out deletion, #13 MooVm opcode-family split, #14 WorldTxn decomposition (ConnectionRegistry/PropertyLayoutEngine/PropertyHolder), #15 MooRuntime decomposition (FinalizationCollector/LoginFlow/ConnectionIo/executeLineNow). All name the tooling mechanism (OpenRewrite recipe / scripts/rewrite.sh / hand-design where semantic).

## DONE — all 23 issues filed (#1-#23)
#16 persistence DbScanner DRY, #17 StringValue latin1, #18 error boundaries (WorldResult), #19 DbCodec + composition root, #20 @GuardedBy threading contract, #21 repo hygiene, #22 VmState split, #23 small-DRY batch.

Suggested execution order (dependencies): #8 → #9 → #10 (builtin protocol chain); #1/#2/#3/#4/#5 bugs anytime; #11-#15 independent decompositions; #16/#17 cheap mechanical wins first if warming up on OpenRewrite; #21 hygiene immediately.

**Uncommitted work:** build.gradle.kts (OpenRewrite plugin 7.39.0 + recipe BOM), gradle.lockfile, gradle/verification-metadata.xml, scripts/rewrite.sh (new). Q hasn't asked for a commit — left in tree.

Old plan line: sealed BuiltinResult, buildManifest split per family, BuiltinCall record + BuiltinHosts, DurableTaskCodec out of scheduler, ToastV17ProgramLayout split (+ ArchitectureTest carve-out removal), MooVm opcode-family split, WorldTxn (ConnectionRegistry + PropertyLayoutEngine), MooRuntime (FinalizationCollector/login/executeLineNow), persistence DRY (DbScanner/tag decoder/shared records), latin1 helpers on StringValue, error-boundary hygiene (IAE channel, catch Throwable, boolean returns), persistence interface + composition root, scheduler threading contract docs, repo hygiene (gitignore notes-*, pyghidra dir, stray moo/ classfiles, dead methods).
