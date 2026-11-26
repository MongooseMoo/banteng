# Latest MOO conformance

## Goal

Make the current Banteng checkout pass the latest MOO conformance tests under its existing managed CI profile.

## State

Banteng's most recent master CI passed the stock and promotion runs against the October 27 suite checkout. PR #140 is open with the workflow fixture checkout and VM repairs for map range reads and floating-point power. All four non-conformance jobs passed. The hosted current suite ran against the complete fixture checkout and reported 232 failures, 12,278 passes, and 1,826 skips. Promotion did not run after stock failed. All nine floating underflow cases passed. Seventeen of eighteen map-range cases passed. The log and XML evidence are in the GitHub run and its `conformance-results` artifact.

## Decisions and why

Use Banteng's managed stock Test.db run with admission and its separate promotion run. Keep the latest suite's declared expectations and strict skip handling. Preserve the existing working changes while tracing failures.

## Surprises

Suite main includes a pinned ToastCore startup fixture as a Git submodule. Banteng's conformance checkout in `.github/workflows/ci.yml:197` does not initialize submodules. The latest runner prepares the suite database before checking feature requirements (`test_conformance.py:423-427`). It raises when the named database file is missing (`runner.py:322-325`). The ToastCore lifecycle suite at `server/toastcore_interactive_lifecycle.yaml:3` therefore reaches that error with the existing Banteng checkout step.

Source inspection found that the working implementations already check the new JSON mode names, invalid `property_info` targets, invalid `create` owners, and depth-first callable verb and `pass()` lookup. These are findings from source inspection. Actual case results remain unverified.

Two further current cases have contradictions in the source. `IndexOps.range` handles inverted integer map bounds before lookup but reports E_RANGE for inverted bounds of other types when a key is absent. `ArithmeticOps.arithmetic` accepts a floating power of zero when a finite nonzero base underflows.

The current unknown-builtin startup fixture expects load-time diagnostics, source normalization to `call_function`, and a structured `E_INVARG`. The latest local batch composes the database verb compiler from the builtin catalog and server log, rewrites only absent builtins, and preserves normal dynamic compilation diagnostics. The structured error is already on the PR branch. The isolated distribution build passed after narrowing the compiler's source and metadata error handling.

The pinned Toast suite's 64-bit artifact confirms that all four queued-fork restart cases actually passed. The Toast writer passes a two-word `Var` to one integer conversion; the last field prints the definer's runtime type and is read as the debug flag. The previous Barn investigation captured the bytes and disassembly. Banteng writes the actual debug flag in the shared v17 activation header; write the definer type there and keep the existing `debug != 0` import semantics for older files.

The parser now lowers `obj.:name` to a computed string name with a colon prefix. Outside strings and comments, the lexer treats a high byte as end-of-input, and the unparser shares its ASCII identifier rules. The baseline found that the ToastCore fixture's `$string_utils` reference inside an index was treated as the last-index marker, and that the comparator treated `false` as greater than `true`. Both paths are corrected in the follow-up. Integer power of `1` or `-1` now preserves the result and sign for negative exponents.

An isolated Linux build was still reading source when the checkpoint patch was applied. Error Prone reported `NoSuchElementException` in `StringConcatToTextBlock` at an unchanged literal. The follow-up local build was stopped to allow the remaining edits. The next hosted build will verify a stable checkout.

## Next step

1. Compare the published follow-up's JUnit results against the baseline. The largest baseline groups were callback stack frames, scatter assignment, SQLite, and PCRE. The unknown-builtin load path still needs normalization and logging. Live non-debug fork/suspend cases failed separately from the checkpoint field behavior.
2. Fix verified Banteng differences at their source. Re-run the existing managed stock and promotion checks against the latest suite and read the resulting JUnit reports and admission evidence.

Q authorized publication of the pull request. Leave the pull request open for review.

The scatter follow-up is local while hosted CI validates the preceding changes. `MooCompiler` previously emitted all default expressions before `SCATTER`. `LoopOps` also let a rest target take elements reserved for optional targets. The compiler now emits default blocks after the instruction. The VM validates the source, binds supplied values, and jumps to the first missing default or the end. The Toast checkpoint layout already models defaults after binding with the source list on the stack.

The second hosted build passed. Its Java tests found five older assertions with different expectations for boolean ordering, Unicode loop labels, or the v17 debug field. The local test updates assert the verified Toast behavior and retain checks for loading older files and invalid input. The second managed run reported 219 failures, 12,424 passes, and 1,831 skips on the same suite revision. All 18 map-range cases and all 13 parser-syntax cases passed. ToastCore started and its five cases received the declared feature skip in the stock profile. Promotion did not run after stock failed.

The callback cases exposed two independent task paths. Live queued forks now snapshot a root frame so restore retains the captured debug flag. `task_stack` ignores queued forks until they suspend, though the snapshots remain available as collection roots. Uncaught traceback output now renders the builtin rows it already receives, and the row uses the builtin name at the caller's instruction, including `recreate` and `call_function`.

The published third batch also logs the v4 bad-label admission failure, recognizes a v5 label without using it as the record identity, and narrows `move`, `index`, and `rindex` arguments to signed 32 bits before validation. A clean Linux copy built the complete batch successfully. The third hosted run is in progress.

The next local delta corrects misplaced unescaped `$` handling in the MOO regex translator. The baseline's eight `match` and `rmatch` failures map directly to Toast's `Reol` compile check. The PCRE replacement translator now handles numeric references, `$&`, `$+`, literal dollar forms, and the 32-reference limit before matching. The third CI run's test compilation exposed two tests still calling `RawVerb.compile`; those tests now target the shared compiler and retain their original assertions.

The local delta also restores Toast's PCRE match-loop limit and cache eviction order, fixes integer division and modulus boundaries and subnormal `exp` results, narrows SQLite integer parameters and preserves its result-text rules, and recognizes error keywords regardless of case. The map authority cases show a remaining asymmetrical comparison for distinct waifs and anonymous objects, and the map builtins incorrectly reject anonymous keys even though the VM accepts them.

SQLite parses the text returned by `sqlite3_column_text` using Toast's `parse_number` and `parse_float`. The fourth batch preserves C-string termination, leading whitespace, decimal integer saturation and the 15 significant digits of REAL column text. A fresh isolated Linux copy passed `installDist compileTestJava` for the complete fourth batch, including the two corrected `RawVerb.compile` references. The third hosted conformance job was still running when this batch was prepared for publication.
