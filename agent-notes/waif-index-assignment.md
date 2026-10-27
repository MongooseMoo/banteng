# WAIF indexed assignment

## Goal
Implement the approved staged index replacement using existing activation temporary, parent index contexts and ordinary callback returns. Preserve original RHS expressions, local/property owners, suspension, GC roots and actual v17 roundtrip.

## State
Staged repair and authored focused class pass, including actual v17 writer/reader resume, distinct saved names versus mutated local verb, exact native stage ticks and anonymous RHS retention. No publication. Creating the substantive local commit before merging the latest callback integration branch and its switchedPlayer removal.

## Decisions and why
Toast code_gen882-917 saves RHS, runs each INDEXSET, stores the final owner and restores RHS. Reuse existing SuspendedActivation.temporary instead of a Java-only assignment continuation. Add exact index read/PUSH_REF/set call boundaries to existing layout ownership. Preserve saved-player and builtin continuation metadata.

Native include/opcode.h:28-35/54-88/129-130 makes INDEXSET and owner stores cost1, PUSH_REF/temp/RANGESET cost0. Concrete staged instructions use those costs, rather than old fused costs. Existing disassembly assertions have14 mechanical staged sequence/offset migrations; two fused parent-depth assertions now assert the explicit parent instruction. Existing structural fixture helpers filter their original explicit builtin/verb call target while the new focused class covers implicit index boundaries. Existing Frame/signature consumers are directly retargeted, with no compatibility facade.

## Surprises
The original230-320 total-changed-line estimate undercounted removal-heavy replacement: duplicated compiler lowering is about90 lines and old fused IndexOps paths about90 lines. Parent authorized the same design at roughly350-420 changed production lines, without added scope or generic machinery.

Parent source verification found adjacent durable identity loss: exportActivation used mutable local verb for both saved names. Toast execute.cc:3833-3834 owns a.verb/a.verbname separately. Export now uses existing frame.calledVerb/fullVerbName directly; the authored roundtrip mutates local verb and uses full alias strings to prove names stay separate.

## Next step
Merge latest campaign/callback-command-locals, preserve temporary/player/continuation fields and remove the obsolete switchedPlayer argument from the authored snapshot helper. Validate any actual API adaptation with only the authored test filter; parent owns review, broad CI and publication.

## Validation
Successful frozen-source WSL JDK25 run: JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin ./gradlew --no-daemon :test --tests world.mongoose.banteng.runtime.WaifIndexedAssignmentTest. Earlier selector lambda shadowing and package-private test accessor compilation mistakes were corrected directly, preserving the existing checks. No broad local CI.

## Draft PR body
WAIF indexed assignment now stores each setter's returned replacement container back through local/property and nested parent owners while retaining the original RHS as the expression result. Each stage uses ordinary callback return routing and survives suspension, GC and the existing v17 temporary/structural-stack representation. Saved called/full activation names remain independent from mutable local verb through the roundtrip.

Validated with the authored WaifIndexedAssignmentTest filter under JDK25, including immediate/resumed/scalar/property/mixed/range cases, real v17 writer/reader resume, anonymous RHS retention and native tick costs. Existing disassembly and explicit-call fixture expectations were migrated without removing semantic assertions. Parent owns full PR CI and publication.
