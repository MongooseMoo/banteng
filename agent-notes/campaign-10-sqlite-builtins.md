# Goal
Extract issue 10's SQLite family using the existing stateful-service mover.

## State
Published as PR128 with completed review. Prepared the normal merge of latest committed campaign/callback-command-locals, preserving its current callback, WAIF and continuation repairs. Catalog and rewrite.yml merged mechanically without conflicts. SqliteBuiltins, FileIoBuiltins, ConfinedFileRoot, MoveFileIoBuiltins and its focused fixture are unchanged from the reviewed SQLite head. Both catalog constructors still create one ConfinedFileRoot shared by File I/O and SQLite, and sqlite.register remains in its existing manifest position. Incoming runtime/VM/persistence/bytecode/app, BuiltinHosts/BuiltinResult and runtime test files are unchanged. No new SQLite implementation/mover delta; no local CI or repeat review was run.

SQLite service and nine registrations were moved using inspected OpenRewrite dry-run output applied through apply_patch. Branch includes the File I/O prerequisite's unused-argument repair.

## Decisions and why
Move the existing service intact into family.SqliteBuiltins and place its nine registrations in its instance register method. Preserve the shared ConfinedFileRoot, all connection/lock/cancellation code, WIZARD_ONLY permission, four irrevocable registrations, and query/execute threadMode. Extend MoveFileIoBuiltins with a Sqlite selection rather than introduce another mover. About 407 implementation lines relocate, 72 catalog lines leave, and 25 scaffold/45 incremental recipe lines plus focused coverage are expected.

## Surprises
The first normal migration stopped on the prerequisite FileIoBuiltins handles argument: [UnusedVariable] The parameter 'arguments' is never read. Incorporated the prerequisite repair before continuing, and removed the equivalent unused SQLite handles parameter in the mover.

The configurable recipe constructor initially accepted null from OpenRewrite's reflection loader. Bytecode inspection of RecipeIntrospectionUtils.construct showed it maps Java parameter names before falling back to Jackson after construction failure; this subproject does not retain those parameter names. Require the configured family non-null, as the existing configurable mover's required collections already do, and preserve configuration through the existing clone pattern. Focused coverage now loads SQLite from YAML and clones the child mover. Intermediate fixture failures exposed the constructor issue; the final focused run passed.

Normal migration command: WSL env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractSqliteBuiltins. It completed successfully with all ordinary compilation prerequisites, produced only the service relocation and catalog extraction, and retained existing Gradle plugin classpath-lock/configuration-cache diagnostics. No task exclusions or check weakening.

## Next step
Parent owns publication and required PR CI after runtime lands. Reuse the completed SQLite review because the implementation/mover/shared-root owners are unchanged; this ancestry integration does not require another review. Production compilation/conformance remains on the PR. The original scoped MoveFileIoBuiltinsTest completed successfully after the recipe fixes; a read-only comparison confirmed the moved implementation matches the original after only the declared constructor, method visibility and unused handles argument changes. No publication performed by this worker.

## Draft PR body
Moves the existing SQLite owner into family.SqliteBuiltins and places its nine registrations in that owner, replacing BuiltinCatalog's SQLite manifest helpers. Preserves registration order, wizard permissions, selective irrevocable flags, query/execute thread mode and all existing connection state, locking and cancellation bodies. The shared ConfinedFileRoot remains the existing owner.

Extends the existing File I/O mover with SQLite configuration and YAML activation/clone coverage. Removes the now-private handles method's unused argument.

Validation: scoped MoveFileIoBuiltinsTest and the normal ExtractSqliteBuiltins migration runner. PR CI and independent review remain for the parent; depends on the File I/O extraction.
