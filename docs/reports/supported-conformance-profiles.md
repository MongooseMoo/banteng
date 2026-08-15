# Supported conformance profiles

## Decision

The first Banteng release has two selected conformance targets:

1. stock WSL Toast with the bundled `Test.db`; and
2. ToastCore as a real-core profile.

Mongoose is not a conformance target, phase gate, required persistence fixture,
default test dependency, or release gate. `PROMOTE_NUMBERS` is a desired
optional Banteng feature, but the selected conformance suite does not require
it. Optional promotion or Mongoose integration work cannot affect conformance
or completion status.

Banteng owns the selected operational profile manifests and managed WSL
launcher under `profiles/toast/` and `scripts/`. Barn remains a semantic
reference, but its live worktree and fixtures are not executable dependencies
of Banteng's conformance gates.

## Selected targets

| Target | Status | Oracle/profile authority | Fixture identity |
| --- | --- | --- | --- |
| Stock Test.db | In | `profiles/toast/stock-wsl-testdb.json` | `../moo-conformance-tests/src/moo_conformance/_db/Test.db`, SHA-256 `1a3f23ebb549e02ccf5341668425118fcdc935b977096add87bc2a8ef29d408e` |
| ToastCore | In | `profiles/toast/stock-wsl-toastcore.json` | `/root/src/toastcore/toastcore.db` at upstream commit `1887eacd591d97fdc55d258a76e2167899b1951d`, v17, SHA-256 `8013b703c61a9894866f836f2b934eada7118cdf0b3cd56181e4bf9205b2f557` |

## Stock Test.db profile

The primary release gate is the stock profile required by the implementation
plan.

- Profile ID: `toast-stock-wsl-testdb`
- Manifest: `profiles/toast/stock-wsl-testdb.json`
- Toast source: `aecc51e9449c6e7c95272f0f044b5ba38948459e`
- WSL binary: `/root/src/toaststunt/build-release/moo`
- Runtime: Debian WSL, 64-bit
- `OUTBOUND_NETWORK`: enabled
- `PROMOTE_NUMBERS`: disabled
- Fixture: bundled disposable copy of `Test.db`

The managed wrapper is `scripts/run_toast_wsl.sh`; the identity procedure is
recorded in `docs/reports/toast-oracle-identity-2026-07-14.md`. A direct Toast
process, `moo --version`, a Windows executable, or a tracked fixture run does
not replace the managed WSL gate.

## ToastCore profile

ToastCore is the selected real-core release target. The canonical fixture is
`/root/src/toastcore/toastcore.db` from the clean upstream checkout at commit
`1887eacd591d97fdc55d258a76e2167899b1951d`. Its first line identifies
LambdaMOO database format version 17 and its SHA-256 is
`8013b703c61a9894866f836f2b934eada7118cdf0b3cd56181e4bf9205b2f557`.
The upstream `README.md` specifies `connect wizard` for the primary fresh-core
login.

The dedicated manifest is `profiles/toast/stock-wsl-toastcore.json`. Before a
ToastCore pass/fail claim, verify its exact oracle source, executable,
configuration, feature flags, fixture checksum, login mechanism, and managed
target command.

## Optional promotion and application integration

The application may expose `--promote-numbers` and may have focused optional
tests for it. That option does not create a selected conformance profile,
broaden the canonical builtin set, or gate any phase. Optional behavioral proof
must use an explicitly scoped command and a minimal disposable fixture.

Mongoose boot or application compatibility may be exercised as separately
authorized integration work. It must not be routed through the selected managed
conformance profiles or loaded by the default Gradle test suite.

## Required gates

Banteng is not release-conformant until all of the following pass:

1. the full managed stock profile against the bundled disposable `Test.db`;
2. the full checked-in ToastCore profile against a disposable copy of the
   pinned v17 fixture;
3. Banteng v17 output and checkpoint/restart proof required by the exact
   selected conformance rows; and
4. every broader Java, property, fuzz, concurrency, stress, persistence, and
   benchmark gate named by the implementation plan.

A focused row, local JUnit test, manual core boot, optional promotion run, or
Mongoose integration run never substitutes for either selected target or the
full release gates.
