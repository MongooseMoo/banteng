# File I/O builtin family extraction

## Goal
Move FileIoService into family.FileIoBuiltins with its 29 registrations, retaining behavior and the shared ConfinedFileRoot instance used by SQLite.

## State
Implementation complete in an isolated worktree from Time. Scoped OpenRewrite dry-run output applied through apply_patch. No publication performed; root owns review, PR CI and normal merge.

## Decisions and why
Smallest change: relocate the existing service unchanged, make its constructor public and handlers private, move registrations into its instance register method through BuiltinRegistrar. Preserve shapes, order, wizard permission and irrevocable flags. Expose the existing shared root class, constructor and resolve method across packages. About 790 implementation lines relocate, 43 catalog lines leave, and a scoped migration recipe handles the existing service path. No new runtime abstraction.

## Surprises
The initial recipe reparsed compilation units with fresh identities and its multiline text deletion retained the catalog manifest helper. No initial migration output was applied. Corrected the recipe to retain compilation-unit identity and remove helper methods through the AST. The focused recipe test passes with both corrections.

The runner completed successfully while printing existing Gradle/OpenRewrite classpath-resolution and configuration-cache warnings. Those warnings do not establish runtime verification.

## Verification
Ran only the authored MoveFileIoBuiltinsTest and the migration dry runner, using explicit WSL JDK 25. A read-only comparison of the old service body against the new owner body confirmed exact preservation after constructor and method visibility changes. A separate comparison confirmed all 29 registration names, shapes, order and handler targets, with every generated registration wizard-only and irrevocable. git diff --check passed. No broad local CI or conformance run performed.

## Draft PR body
Move the existing stateful File I/O owner into family.FileIoBuiltins, with its 29 registrations owned by its instance register method. BuiltinCatalog delegates at the existing manifest position and retains the same ConfinedFileRoot instance shared with SQLite. File handlers remain synchronized with unchanged bodies and become private; only the shared root's existing class, constructor and resolver gain public visibility.

The scoped OpenRewrite recipe performs the extraction and has focused coverage for registration shape, flags, order, shared root and file identity. Validation: authored recipe test and migration dry runner; direct comparison of preserved bodies and registrations. PR CI and conformance remain for the parent workflow.

## Next step
Root reviews the local family commit and draft body, integrates other family work, obtains required Claude review and PR CI, then follows the authorized publication and merge workflow. SQLite can reuse or extend the scoped existing-service recipe path. This worker must not push or publish.
