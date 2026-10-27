# Server builtin family

## Goal
Extract 15 server/diagnostics registrations in four manifest groups and their existing 16-method closure and SERVER_VERSION constant into ServerBuiltins. Preserve catalog manifest/spec/protected-functions state and existing logging ownership.

## State
Implemented scoped mover/YAML/fixture and applied reviewed migration output with apply_patch. ServerBuiltins has 319 lines, 15 registrations in four original groups, the 16-method closure and unchanged SERVER_VERSION constant. Catalog retains manifest/spec/protected-function ownership.

Latest campaign/callback-command-locals integrated normally without conflicts. ServerBuiltins, the existing family mover, its focused fixture and rewrite.yml are byte-for-byte unchanged by this integration. Current catalog retains all four Server registration calls, including the existing catalog/logging ownership arguments.

Validation: focused MoveBuiltinFamilyTest passed on Java25 with --no-daemon. Normal ExtractServerBuiltins migration completed in a fresh single-use Gradle process with all prerequisites enabled and source frozen through compilation. All sources parsed; BUILD SUCCESSFUL. Read-only comparison confirmed 16 implementation bodies match original after whitespace normalization and planned catalog/log qualification; all 15 registrations preserve order; manifest/spec/reloadProtectedFunctions bodies remain unchanged; BuiltinCall and ValueLimits imports are present. git diff --check passed. No broad local CI or post-migration compile.

## Decisions and why
Pass the existing catalog only for functionInfo/load_server_options; pass catalog hosts.serverLog() for logging. Keep db_disk_size using invocation hosts as before and valueBytes in ValueLimits. No new diagnostics, state or service.

## Surprises
Parent source inspection found the mover mistook enum switch labels INTEGER/NUMBER for shape constant references, emitting unused fields. Corrected the existing identifier collector to exclude case labels, removed the two generated fields, and included the real switch shape in the existing fixture.

Initial patch inspection found the moved dumpDatabase signature needed an explicit BuiltinCall import because it leaves that type's package. Added the required import to the existing recipe skeleton and included the real dumpDatabase method/reference in the authored fixture before applying output. Focused fixture and corrected normal migration passed. OpenRewrite emitted existing Gradle exclusive-lock/classpath and configuration-cache compatibility warnings but parsed all sources and completed successfully.

## Next step
Parent owns publication of this local PR136 integration and current hosted checks. No local CI rerun or repeat review of unchanged Server extraction code was performed. Existing review/validation evidence remains reusable; this integration has no family or recipe delta.

## Draft PR body

Move the 15 server and diagnostics registrations, implementation closure and server-version constant into ServerBuiltins while preserving their four manifest positions. Retain the existing catalog for function_info/options reload and its original ServerLog for logging, with db_disk_size continuing to use invocation hosts and value sizing remaining in ValueLimits.

Original validation: focused MoveBuiltinFamilyTest; normal ExtractServerBuiltins migration dry-run with fresh Java25 Gradle process; read-only method/order/owner/import comparisons. Latest integration: clean normal merge, zero Server family/mover/fixture/YAML delta and git diff --check. No repeated CI or unchanged-code review; parent owns publication and hosted checks.

## Login and layout repair ancestry
Merged the latest coordinator runtime login-output and pinned-layout-boundary repairs normally, without conflicts or family source adaptations. Family implementations, services, dependencies, extraction mover, authored fixtures and YAML are unchanged. No local tests, CI, migration or repeated review ran for this mechanical integration. Parent owns publication, hosted checks and merge.

## Independent Claude review of the Server-specific range
Claude reviewed the fixed two-endpoint diff from the PR138 runtime head to the PR136 head (six paths) manually, reusing the completed PR138 runtime review rather than repeating it. No code findings. One note inaccuracy: State says ServerBuiltins has 321 lines; the published file has 319. Ledger returned to the coordinator; parent runs the gate after the runtime base lands.

## Queue prerequisite integration
Normally merged the published Noise tip and its reviewed prerequisite families. Mover resolution retains Server constant/method selection, serverParameters and logging rewrites alongside all incoming generation branches and shared imports; YAML retains every complete recipe document. Reused fixture/catalog resolutions retain Server's four registration positions and canonical hosts.serverLog dependency, matching final assembly. ServerBuiltins and incoming family owners remain byte-identical. Preserved the pre-existing Claude note edits in this substantive merge. No local tests, repeated review or publication; parent owns hosted validation and merge.

Normally merged the newly published Noise tip carrying the actual merged master ancestry. No conflicts occurred; incoming source changes are only the already-reviewed startup-input fix and focused test, alongside runtime and prerequisite ancestry notes. ServerBuiltins, all other builtin owners, catalog and extraction files remain unchanged. No CI, review or publication ran here.
