# Native library isolation (#92)

## Goal
Split native ownership into lazy Crypt and Libc, move native layouts and proc reads to Libc, and report host os.name. Smallest change is approximately 350-550 lines including moved code and regression coverage.

## State
Read NativeCalls completely and every caller. Current ctime returns E_INVARG for null localtime/empty strftime (BuiltinCatalog:3532-3540); ftime returns E_FLOAT for clock status (3561-3562); crypt returns *0 on null native result (4588-4589), mapped to E_INVARG for recognized bcrypt (4480-4481). invoke (2617-2670) forwards implementation results without a failure catch. usage catches NativeCallException and zeroes resource fields (2942-2943). Issue line references describe older source.

## Decisions and why
Keep these existing native failure values for lookup failures. Crypt owns conversion and copying under its synchronization because crypt returns shared storage. BuiltinHosts supplies a Crypt instance, allowing a test to use a genuinely missing library name without process-global flags. Libc owns all native buffers and /proc parsing. No substitute native implementations.

## Surprises
The current ctime/ftime/crypt callers do not catch NativeCallException, unlike the issue description. Their explicit native result failure branches determine the mapping.
Coordinator found the moved crypt decoder initially retained a host-to-value import. ArchitectureTest:54 permits no such edge; MooValue:199,218-219 uses ISO_8859_1. Crypt now names that JDK charset directly, preserving bytes without the dependency.

## Next step
Coordinator reviews the committed candidate with Claude and publishes it for CI. Focused root regression is running; worker will report its result separately.

## Implementation
Crypt resolves its configured library on its first hash call and caches lookup failure. BuiltinHosts defaults to the unlinked shared system Crypt; tests can compose a missing library instance without affecting other catalogs. Libc's nested library holder captures its own lookup failure, independent of Crypt. All native struct access and proc reads now live in Libc. Existing native result branches are preserved, and missing-library failures join those same branches.

## Validation state
The authored missing-library regression invokes the real foreign library lookup and expects its IllegalArgumentException cause, then calls crypt twice through the builtin path and verifies ctime, ftime and usage continue working. The first Gradle invocation used `test` instead of `:test`, inadvertently selecting filtered subproject tests as well; their filter mismatch is not product failure. Compilation reported NarrowIllegalArgumentCatch on the moved memoryUsage catch; narrowed it to the single host call. Focused root regression execution remains pending.

## PR draft
Title: Isolate lazy native library failures and move host layouts out of builtins

Missing libcrypt now affects crypt alone; lookup failures are cached per library and translated through existing native failure results. Libc owns time buffers, rusage layout and proc parsing, and server_version reports the host's os.name. Regression coverage selects an actually missing crypt library while exercising ctime, ftime and usage through the catalog.

Closes #92.

At this implementation commit, the worker had not published; coordinator owns publication and the campaign note tracks live PR state.
