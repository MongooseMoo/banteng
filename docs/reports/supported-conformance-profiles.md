# Supported conformance profiles

## Decision

The first Banteng release includes all three currently identified conformance
targets:

1. stock WSL Toast with the bundled `Test.db`;
2. Mongoose WSL Toast with `PROMOTE_NUMBERS` enabled;
3. ToastCore as a real-core profile.

The user explicitly selected all three on 2026-07-15. This is the closed
selected-profile set used by every later reference to a "selected profile" in
`docs/reports/banteng-implementation-plan.md`. A target not listed here is out
of the first release until the user explicitly changes this record's scope.

Banteng owns the operational profile manifests and managed WSL launcher under
`profiles/toast/` and `scripts/`. Barn remains a semantic reference, but its
live worktree is not an executable dependency of Banteng's conformance gates.
The stock and Mongoose artifacts were snapshotted from Barn `master` commit
`41b904c34b1aee2e6d118f564985feec046055e3` before this ownership transfer.

## Selected targets

| Target | Status | Oracle/profile authority | Fixture identity |
| --- | --- | --- | --- |
| Stock Test.db | In | `profiles/toast/stock-wsl-testdb.json` | `../moo-conformance-tests/src/moo_conformance/_db/Test.db`, SHA-256 `1a3f23ebb549e02ccf5341668425118fcdc935b977096add87bc2a8ef29d408e` |
| Mongoose PROMOTE_NUMBERS | In | `profiles/toast/mongoose-wsl-mongoose.json` | manifest fixture `mongoose`, SHA-256 `33201970097d3d2d2bfc0d5f875f087d587601bf8255ef31ef19b416d65ac925` |
| ToastCore | In | `profiles/toast/stock-wsl-toastcore.json` | `/root/src/toastcore/toastcore.db` at upstream commit `1887eacd591d97fdc55d258a76e2167899b1951d`, v17, SHA-256 `8013b703c61a9894866f836f2b934eada7118cdf0b3cd56181e4bf9205b2f557` |

## Stock Test.db profile

The primary release gate remains the stock profile required by the plan.

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

## Mongoose PROMOTE_NUMBERS profile

Mongoose numeric-promotion behavior is release-required, not an optional
comparison.

- Profile ID: `toast-mongoose-wsl-mongoose`
- Manifest: `profiles/toast/mongoose-wsl-mongoose.json`
- Toast source: `72e3c7f96ce7a41fdeba793aef8818dc4408072e`
- WSL binary: `/root/src/toaststunt-mongoose/build-release/moo`
- Runtime: Debian WSL, 64-bit
- `OUTBOUND_NETWORK`: enabled
- `PROMOTE_NUMBERS`: enabled
- Fixture checksum:
  `33201970097d3d2bfc0d5f875f087d587601bf8255ef31ef19b416d65ac925`

Stock behavior does not substitute for this profile. Every numeric surface
whose result changes under `PROMOTE_NUMBERS` requires the Mongoose oracle and
managed target gate in addition to the stock gate.

## ToastCore profile

ToastCore is a selected real-core release target.

The canonical fixture is `/root/src/toastcore/toastcore.db` from the clean
upstream checkout of `https://github.com/lisdude/toastcore.git` at commit
`1887eacd591d97fdc55d258a76e2167899b1951d`. Its first line identifies
LambdaMOO database format version 17 and its SHA-256 is
`8013b703c61a9894866f836f2b934eada7118cdf0b3cd56181e4bf9205b2f557`.
The upstream `README.md` specifies `connect wizard` for the primary fresh-core
login.

The dedicated manifest is `profiles/toast/stock-wsl-toastcore.json`. Before
any ToastCore behavioral or pass/fail claim, the exact oracle source,
executable, configuration, feature flags, fixture checksum,
disposable-fixture command, README-mandated login mechanism, and managed target
command must also be proven and frozen in its identity record.

## Required gates

The selected set means Banteng is not release-conformant until all of the
following pass:

1. the full managed stock-Toast profile against the bundled disposable
   `Test.db`;
2. the full managed Mongoose profile against its freshly identified disposable
   fixture with `PROMOTE_NUMBERS` enabled;
3. the full checked-in ToastCore profile against a disposable copy of the
   pinned v17 fixture;
4. Banteng v17 output and checkpoint/restart proof for every selected fixture;
5. every broader Java, property, fuzz, concurrency, stress, persistence, and
   benchmark gate named by the implementation plan.

A passing focused row, selected family, local JUnit test, manual core boot, or
one selected profile never substitutes for another selected target or for the
full release gates.

## Immediate Phase 0 consequence

The next profile artifact is the managed-oracle identity record proving the
ToastCore manifest against the actual upstream fixture. No semantic
implementation or Phase 8 conformance work is authorized by this selection
record.
