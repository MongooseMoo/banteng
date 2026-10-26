# Latest MOO conformance

## Goal

Make Banteng pass the latest tests on MongooseMoo/moo-conformance-tests main.
Q authorized implementation and a PR on 2026-10-25 with "Make it so!".
Necessary redesign/cleanup is in scope; weakening tests or skipping failures is not.

## State

- Isolated branch `fix/latest-conformance` starts at Banteng master `2ccad0597d7a6889e1607174cd2e6513a2032542`.
- Existing master CI run 32452595044 passed against suite `6528b8cf6d72c065c722469b070fea16d626e602` in August; it is not current evidence.
- Suite main observed as `8dbe801819c41529122cfb1f98f68e9b709db383` at task start and is actively changing.
- No implementation changes yet. Publish this note to obtain the existing PR CI baseline.

## Decisions and why

Smallest initial change: this task note only, roughly 30 lines. The existing CI
already checks out suite main, runs admission/conformance with strict markers and
unexpected-skip failure, and uploads results. No new runner or validation gate is
needed. Implementation scope and line estimate will follow actual failure evidence.
Use PR CI, not a local rerun of CI commands. Preserve unrelated worktrees/files.
No merge or release has been requested explicitly; deliver the reviewed passing PR
and resolve any remaining publication decision with Q.

## Surprises

The primary checkout was behind remote master and contains an unrelated untracked
investigation. It remains untouched.

## Next step

Open the PR, inspect the suite checkout SHA and conformance failures, and trace
each failure into current tests and Banteng source before changing behavior.

## Implementation: verb-name whitespace

PR #82 baseline CI is run 40271391375. While it runs, source confirms a mismatch:
`verb_setter_error_precedence.yaml:set_verb_info_tab_only_names` requires a tab to
remain a callable name; Toast `src/verbs.cc:116` strips only spaces, whereas
Banteng `BuiltinCatalog` uses `stripLeading` in both setters and `WorldTxn`
uses the default whitespace tokenizer. Smallest complete fix: strip spaces in
both metadata entry points and tokenize aliases on spaces only, about 12 lines.
Existing upstream cases supply regression coverage; no new guard is needed.

The runner's `plugin.py:405` requires a `{log}` placeholder to run named-log
signal cases. Add the existing Banteng `--log-file {log}` option to the existing
managed suite command (one line), enabling those tests without a new CI step.

## Baseline and next repair group

CI 40271391375 / conformance job 121404769359 tested suite 8dbe801 and reported
112 failures, 10867 passes, 2012 skips. SIGUSR1 terminated the process and the
managed lifecycle check stopped the run; tests after that point are unverified.
All other CI jobs passed. Full raw failure details remain in GitHub job logs.

Smallest next changes, about 35 lines across existing value/builtin classes:
restore full-width integer/object map comparisons (Toast utils.cc:412), signed
zero formatting, JSON 64-bit integers and first duplicate key retention
(json.cc:250, handle_end_map reverse insertion), C-string truncation in chr
(list.cc:1600), SHA salt validation (crypto.cc:parse_prefix), hex tofloat input,
and exp underflow errors. Existing conformance cases demonstrate each mismatch.
Do not alter the failed-dump test or manufacture a failure: its blocked temporary
paths name Toast/Barn, whereas Banteng uses a PID-qualified temporary file. This
requires separate analysis of the portable test contract.

Activation thread mode: VmState inherits the caller's mode into eval/verb frames;
Toast execute.cc initializes each activation to DEFAULT_THREAD_MODE. Reset new
frames to true while retaining snapshot restoration (three lines). Correct
chparent/chparents metadata argument types in their existing CallShapes (two
lines); the new function_info cases require OBJECT and LIST respectively.
