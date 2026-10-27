# Issue 89: reduced builtin contract

## Goal
Implement issue 89's five-field BuiltinSpec, one invoke(BuiltinSpec, BuiltinCall), canonical BuiltinCall constructor, and Toast registration argument contracts before family extraction.

## State
Branch campaign/89-builtin-contract starts at campaign/92-native-libraries 67ee49a. Coordinator opened PR104 against that dependency for isolated review; PR100 must merge before104 targets master. This note records implementation and validation evidence; the PR and central issue-campaign note own live CI/merge state.

## Decisions and why
Smallest change: remove unused cost/owner/effect metadata, retain a boolean irrevocable, construct VM calls through one helper preserving captured pending context and live callbacks, migrate callers, audit shapes against Toast register_function, and remove only duplicate checks proven by those shapes. Estimated 500-900 changed lines; no registrar/family extraction and no conformance changes.

## Surprises
Toast functions.cc:254-268 validates only minargs positions for maxargs=-1; min/max and eval tails must be ANY centrally. verbs.cc:609-614 checks programmer permission before validating every eval source string. The internal string check now follows that order.

Audited 219 literal registrations by comparing required count, maximum, and every position type against source register_function/register_function_with_read_write calls in Toast's src/*.cc. Separately checked the six generated string/binary/value hash and HMAC registrations against crypto.cc:718-724. All match. Shape corrections: length (list.cc:1765), create (objects.cc:1351-1353), min/max (numbers.cc:935-936), and eval (verbs.cc:678). TYPE_ANY object/verb checks stay in implementations. The only duplicate implementation arity rejection found was functionInfo; it and its duplicate string check were removed. The count-check audit covered the entire builtin package plus MooRuntime and TaskRegistry host handlers: other count checks select optional values/defaults or iterate variadic arguments; no duplicate arity rejections were found there.

Float registrations remain FLOAT per numbers.cc:949-969. Existing --promote-numbers behavior (app/Banteng.java:50-56; old catalog floatMath) is preserved by a small invocation predicate for the exact existing twenty math functions. It accepts only ArgType.NUMBER (integer/float), adds no conversions, and leaves floatstr, round, trunc, and frandom unchanged. Existing promotion tests now expect the declared FLOAT signature while retaining runtime integer assertions.

VM uses one construction helper. Pending programmer/task-local/tick/seconds/caller values remain captured; task ID, thread mode, and output projection stay live as before. call_function now forwards the projection callback rather than silently defaulting it. A nested buffered-output test covers that context propagation.

The first targeted test command used unqualified test and incorrectly selected subprojects; it also exposed a removed LongBinaryOperator import still needed by bufferedOutputLength. Restored that import and corrected the command to root :test. Full CI is not a local gate.

## Next step
Root-only actively edited BuiltinCatalogTest passed through WSL Java 25: `JAVA_HOME=/opt/java/25 ./gradlew --no-daemon :test --tests world.mongoose.banteng.builtin.BuiltinCatalogTest` (`BUILD SUCCESSFUL in 7m 56s`). Main and test compilation passed in that run. Parent owns push/PR/review/CI and dependency merge. Full CI and runtime pending-call path verification were not run locally. Conformance suite is unchanged.

## Draft PR
Title: Reduce builtin contracts and pass invocation context directly

Builtin registrations now retain only name, call shapes, permission, irrevocable, and implementation. The catalog accepts one BuiltinCall, with VM construction shared between normal execution and authorization of a pending request. Captured pending values and live callbacks retain their prior ownership.

Argument declarations match Toast registrations, including ANY declarations for length/create and unchecked variadic tails. Eval checks all source strings after programmer permission. The existing optional numeric promotion remains available with FLOAT declarations, and nested call_function retains the output projection callback.

Validation: targeted root :test --tests world.mongoose.banteng.builtin.BuiltinCatalogTest passed; full PR CI not run locally. No conformance fixture changes. Depends on PR 100.
