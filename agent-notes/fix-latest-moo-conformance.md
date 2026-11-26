# Latest MOO conformance

## Goal

Make the current Banteng checkout pass the latest MOO conformance tests under its existing managed CI profile.

## State

Banteng's most recent master CI passed the stock and promotion runs against the October 27 suite checkout. There is no current Banteng result for the latest suite in the GitHub runs inspected. This branch includes the workflow fixture checkout and VM repairs for map range reads and floating-point power. The Linux Java distribution built in the original working tree. GitHub CI will verify these changes in isolation from the original tree's existing edits. A fresh Linux suite checkout fetched the pinned ToastCore database, and the checksum matched the suite manifest.

## Decisions and why

Use Banteng's managed stock Test.db run with admission and its separate promotion run. Keep the latest suite's declared expectations and strict skip handling. Preserve the existing working changes while tracing failures.

## Surprises

Suite main includes a pinned ToastCore startup fixture as a Git submodule. Banteng's conformance checkout in `.github/workflows/ci.yml:197` does not initialize submodules. The latest runner prepares the suite database before checking feature requirements (`test_conformance.py:423-427`). It raises when the named database file is missing (`runner.py:322-325`). The ToastCore lifecycle suite at `server/toastcore_interactive_lifecycle.yaml:3` therefore reaches that error with the existing Banteng checkout step.

Source inspection found that the working implementations already check the new JSON mode names, invalid `property_info` targets, invalid `create` owners, and depth-first callable verb and `pass()` lookup. These are findings from source inspection. Actual case results remain unverified.

Two further current cases have contradictions in the source. `IndexOps.range` handles inverted integer map bounds before lookup but reports E_RANGE for inverted bounds of other types when a key is absent. `ArithmeticOps.arithmetic` accepts a floating power of zero when a finite nonzero base underflows.

The current unknown-builtin startup fixture also expects load-time diagnostics, source normalization to `call_function`, and the structured `E_INVARG` from that call. The database readers use `RawVerb.compile` with the original source, and `TaskBuiltins.callFunction` returns a bare error for an unknown name. This still needs a repair across the load and builtin paths. The pinned Toast checkpoint source reads and writes the fork activation's debug flag, while the suite expects a previously non-debug fork to run in debug mode after restart. Keep that case open until runtime evidence explains the difference.

## Next step

1. Run PR CI against the latest suite with the complete startup fixtures. Use the JUnit failures and admission evidence to identify each remaining difference.
2. Fix verified Banteng differences at their source. Re-run the existing managed stock and promotion checks against the latest suite and read the resulting JUnit reports and admission evidence.

Q authorized publication of the pull request. Leave the pull request open for review.
