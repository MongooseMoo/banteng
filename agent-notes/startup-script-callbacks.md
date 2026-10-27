# Goal
Support Toast-compatible --start-line/--start-script callback invocation before server_started.

# State
Implemented in an isolated tree based on the command-locals repair. CLI passes each selected raw argument list to the runtime hook before server.serve starts server_started. No publication by this agent.

# Decisions and why
Smallest change: retain the last CLI line/file values and their first relative kind order, send their raw argument lists through a START_SCRIPT runtime request into existing startStored, and release startup's caller at its first actual suspension. Reuse existing runtime return and scheduler input-output publication routes. About 90–110 production lines including request argument propagation. No evaluator, service or worker. Preserve fresh callback fields from the prerequisite, blank file lines and StringValue charset. Irrevocable authorization remains a separate scheduler path and must not release startup.
Source: Toast server.cc:1274-1333/2019-2034/2318-2329; current Banteng app CLI, MooRuntime request/startStored owners and PublicationScheduler suspension boundary. Real startup_script_callback_context fixture requires raw strings, fresh frames, absent/nonexecutable handling and continuation after return/error/suspend.

# Surprises
I initially enabled Picocli overwriting globally for duplicate startup options. Parent identified that it broadened other option behavior; removed it after compilation finished. Read the local Picocli 4.7.7 Interpreter array-binding and MethodBinding bytecode, then used array-valued arity-one setters on only the two startup options. The authored CLI fixture proves both last-value selection and original relative kind order without global parser changes.
The first runtime test read WorldTxn outside an active transaction and failed with "begin a transaction before reading or writing world state". Its assertions now read through world.begin(); no production behavior changed for that fixture mistake.

# Validation
`wsl env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin ./gradlew --no-daemon :test --tests world.mongoose.banteng.app.StartupOptionsTest --tests world.mongoose.banteng.runtime.StartupScriptCallbacksTest` passed after source freeze.
After strengthening last-file-value coverage, ran only `:test --tests world.mongoose.banteng.app.StartupOptionsTest` with the same JDK25 environment; passed.
Runtime coverage uses real scheduler requests and checks raw/no-eval arguments, fresh callback fields, absent/nonexecutable hook behavior, return/error continuation, and first actual 60-second suspension releasing startup. An irrevocable random_bytes call before event capture verifies that authorization yields do not release startup prematurely. CLI coverage preserves blank lines, ISO byte/CR content, duplicate last values and both orderings.
`git diff --check` passed. No broad local CI/conformance; parent owns required PR CI.

# Draft PR body
Adds --start-line and --start-script as raw arguments to #0:do_start_script before server_started. Keeps the last value of each startup option and Toast's relative file/line order, preserving blank file lines and MOO string encoding. Missing or nonexecutable hooks leave those arguments unevaluated.

The existing runtime request/continuation and scheduler publication paths handle the callback. Return/error continues startup; its first actual suspension releases startup while the task remains scheduled. Irrevocable authorization keeps its existing behavior. Adds focused CLI/runtime coverage; authored-file tests passed, with required CI/conformance pending publication.

# Next step
Parent to verify this scoped diff and prerequisite ancestry, obtain review, publish and read required CI/conformance. No release or deployment performed here.
