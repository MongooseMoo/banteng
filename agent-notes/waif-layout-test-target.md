# WAIF layout test target

## Goal
Preserve the pinned label-width test's actual suspend builtin target after index operations became explicit possible call boundaries.

## State
Isolated branch from current callback-command-locals. The existing test method now selects CallKind.BUILTIN before its first boundary. Source generation and pinned72525/72527 assertions remain unchanged. Exact-method validation passed with source frozen. No production changes, new guards or publication.

## Decisions and why
ToastV17ProgramLayoutTest:615-631 generates12000 args[^] reads before suspend(). Selecting the first unfiltered boundary now returns an implicit INDEX at528, explaining the hosted assertion failure. Filter the existing query to its original explicit builtin target; do not alter the offset assertions or layout behavior.

## Surprises
This selector was missed in the earlier staged WAIF expectation migration. Repair the existing selection directly.

## Next step
Parent owns publication and hosted CI. No remaining implementation action.

## Validation
Explicit WSL JDK25 with JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin: ./gradlew --no-daemon :test --tests world.mongoose.banteng.persistence.ToastV17ProgramLayoutTest.stackOperandWideningDoesNotChangePinnedLabelWidthDecision passed. No broader local tests.
