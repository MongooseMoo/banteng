# Server builtin family

## Goal
Extract 15 server/diagnostics registrations in four manifest groups and their existing 16-method closure and SERVER_VERSION constant into ServerBuiltins. Preserve catalog manifest/spec/protected-functions state and existing logging ownership.

## State
Implemented scoped mover/YAML/fixture and applied reviewed migration output with apply_patch. ServerBuiltins has 321 lines, 15 registrations in four original groups, the 16-method closure and unchanged SERVER_VERSION constant. Catalog retains manifest/spec/protected-function ownership.

Validation: focused MoveBuiltinFamilyTest passed on Java25 with --no-daemon. Normal ExtractServerBuiltins migration completed in a fresh single-use Gradle process with all prerequisites enabled and source frozen through compilation. All sources parsed; BUILD SUCCESSFUL. Read-only comparison confirmed 16 implementation bodies match original after whitespace normalization and planned catalog/log qualification; all 15 registrations preserve order; manifest/spec/reloadProtectedFunctions bodies remain unchanged; BuiltinCall and ValueLimits imports are present. git diff --check passed. No broad local CI or post-migration compile.

## Decisions and why
Pass the existing catalog only for functionInfo/load_server_options; pass catalog hosts.serverLog() for logging. Keep db_disk_size using invocation hosts as before and valueBytes in ValueLimits. No new diagnostics, state or service.

## Surprises
Parent source inspection found the mover mistook enum switch labels INTEGER/NUMBER for shape constant references, emitting unused fields. Corrected the existing identifier collector to exclude case labels, removed the two generated fields, and included the real switch shape in the existing fixture.

Initial patch inspection found the moved dumpDatabase signature needed an explicit BuiltinCall import because it leaves that type's package. Added the required import to the existing recipe skeleton and included the real dumpDatabase method/reference in the authored fixture before applying output. Focused fixture and corrected normal migration passed. OpenRewrite emitted existing Gradle exclusive-lock/classpath and configuration-cache compatibility warnings but parsed all sources and completed successfully.

## Next step
Parent verifies source and integrates sibling mover/YAML changes, obtains required Claude review and publishes for PR CI. Local only; no publication performed.

## Draft PR body

Move the 15 server and diagnostics registrations, implementation closure and server-version constant into ServerBuiltins while preserving their four manifest positions. Retain the existing catalog for function_info/options reload and its original ServerLog for logging, with db_disk_size continuing to use invocation hosts and value sizing remaining in ValueLimits.

Validation: focused MoveBuiltinFamilyTest; normal ExtractServerBuiltins migration dry-run with fresh Java25 Gradle process; read-only method/order/owner/import comparisons; git diff --check. Required Claude review and PR CI remain pending.
