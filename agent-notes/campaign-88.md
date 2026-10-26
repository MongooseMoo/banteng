# Issue 88: command parser

## Goal
Implement issue 88 using Toast's tokenizer and object matcher and one world preposition table.

## State
Implementation complete; focused CommandParserTest passed. This note records the worker handoff and source evidence. The coordinator owns PR publication and live CI/merge state in the central issue-campaign note; the worker has no publication authority.

## Decisions and why
Smallest change: pure CommandParser and ParsedCommand, canonical Prepositions, replace duplicated loops and matchers, move existing execution phases unchanged within MooRuntime to keep executeLineNow below 150 lines. Approximately 250–330 parser/table lines plus focused tests and runtime moves. Preserve original do_command words, shortcut argstr spacing, and .program's system-property lookup.

## Surprises
Toast `match_object` uses strtol: `#` can match object zero, leading ASCII whitespace and signs are accepted, trailing characters are rejected. The port preserves these semantics. `.program` retains its separate `$property` lookup before using the shared matcher.

## Source and validation
- Toast source read: `C:/Users/Q/src/toaststunt/src/parse_cmd.cc:34-79,122-238`, `match.cc:51-134`, `db_verbs.cc:50-193`.
- Runtime producers read: command gate and dispatch, transport OOB, login, `.program`; BuiltinCatalog verb_args formatting and set_verb_args parsing.
- Compiled the original Toast `parse_into_words` function extracted directly from parse_cmd.cc using g++ with allocator stubs. Observed: `foo<TAB>bar baz` -> two words (`foo<TAB>bar`, `baz`); trailing backslash disappears; `""` yields one empty word; `ab"cd ef"gh ij` -> (`abcd efgh`, `ij`). These observations back the required focused tests.
- Authored `CommandParserTest` covers tokenizer cases, shortcuts and raw argstr spacing, quoted verbs, ordered prepositions, persisted preposition aliases, object names and aliases, duplicate identity, exact/partial ambiguity, invalid actors and literals.
- `executeLineNow` occupies lines 769–856 (88 lines). MooRuntime has no command character scan. Existing execution phases moved within MooRuntime; no new execution owner.
- First test command mistakenly used unqualified `test`, selecting subproject test tasks as well; `:errorprone-checks:test` failed with `No tests found for given includes: [world.mongoose.banteng.runtime.CommandParserTest](--tests filter)`.
- I edited runtime imports/nullable annotation while that compilation was active. Analyzer output then had displaced NotJavadoc warnings and `MooRuntime.java:81: error: An unhandled exception was thrown by the Error Prone static analysis plugin`, `BugPattern: StringConcatToTextBlock`, `NoSuchElementException at Iterables.getLast / StringConcatToTextBlock.matchLiteral:101`. Full first log: WSL `/root/.gradle/daemon/9.6.1/daemon-2857303.out.log`. Concurrent source changes are the identified explanation for displaced positions; corrected run uses stable source. No check was disabled.
- Corrected authored-test invocation: WSL Java25 `./gradlew --no-daemon :test --tests world.mongoose.banteng.runtime.CommandParserTest`. Stable production compilation passed with all analyzers enabled; the first source-position exception did not recur. Matcher test setup had two defects: a committed WorldTxn owner was passed despite object:170-173 requiring an active transaction, and one object was listed in two containers despite WorldHistory.validateTopology:328 requiring reciprocal locations. Changed tests to owner.begin() and a reciprocal world; duplicate alias identity remains covered. Corrected authored class passed (`BUILD SUCCESSFUL in 21s`). Conformance unchanged and unrun locally; PR CI remains required evidence.

## Draft PR
Title: Share Toast command parsing and object matching

Command parsing previously split tabs and discarded empty quoted arguments in several separate runtime paths. Share Toast's tokenizer and object matcher between commands, login, OOB and programming lookup, and share persistent preposition codes with verb_args/set_verb_args. Preserve original do_command input and shortcut argstr spacing. Move existing programming/output-control and selected-command phases into private MooRuntime methods so executeLineNow stays below 150 lines.

Validation: focused CommandParserTest; four required tokenizer expectations observed from the original Toast C++ function. Full conformance remains for PR CI. Closes #88.

## Next step
Implementation committed on campaign/88-command-parser. Coordinator follows PR CI/conformance and independent review through normal merge. Consult the PR and central issue-campaign note for current delivery state; local focused validation is recorded above.
