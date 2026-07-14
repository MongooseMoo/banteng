# WSL Toast oracle identity verification

## Scope

This is Banteng's current Phase 0 identity record. It verifies the stock WSL
Toast source, executable, option surface, database fixture, profile manifest,
wrapper, and managed command authority. It does not make a MOO semantic claim;
every semantic slice still requires its own Barn-spec, Barn-code, Toast-code,
and managed-oracle evidence record.

## Authority

- Barn convergence authority:
  `../barn/plans/barn-toast-mongoose-convergence-100-line.md`
- Stock profile manifest:
  `../barn/profiles/toast/stock-wsl-testdb.json`
- Managed WSL wrapper: `../barn/scripts/run_toast_wsl.sh`
- Exact Windows-to-WSL command:
  `../barn/plans/barn-toast-mongoose-convergence-workstreams.md`
- Generic behavioral truth owner: `../moo-conformance-tests`

The older `../barn/reports/toast-oracle-wsl.md` path is stale and absent. It is
not an authority for Banteng.

## Verification context

- Timestamp: `2026-07-14T00:41:59.6473921-06:00`
- Banteng directory: `C:\Users\Q\code\banteng`
- Barn HEAD: `2273765e0832f8fe8acd6bbd4b64250052d50b5b`
- Barn tracked state: clean
- `moo-conformance-tests` HEAD:
  `17330158adc809f380d5468e6930bcdbc72f3aa6`
- `moo-conformance-tests` tracked state: clean

## Stock Toast identity

- WSL distribution/user: `Debian`, `root`
- Binary: `/root/src/toaststunt/build-release/moo`
- Required source SHA: `aecc51e9449c6e7c95272f0f044b5ba38948459e`
- Observed source SHA: `aecc51e9449c6e7c95272f0f044b5ba38948459e`
- Executable check: exit `0`
- Required options checksum:
  `a88a8c6c37b66ca65a08a318988361827f131421edeff25e5b4af83fb3fa8036`
- Observed options checksum:
  `a88a8c6c37b66ca65a08a318988361827f131421edeff25e5b4af83fb3fa8036`
- Required `Test.db` checksum:
  `1a3f23ebb549e02ccf5341668425118fcdc935b977096add87bc2a8ef29d408e`
- Observed `Test.db` checksum:
  `1a3f23ebb549e02ccf5341668425118fcdc935b977096add87bc2a8ef29d408e`

The binary prints `ToastStunt version 2.7.3_5` for `--version` but exits `1`.
The current managed workflow does not use that exit status as an identity or
conformance gate.

## Managed command

Run from `C:\Users\Q\code\barn`, replacing only the focused `-k` selector:

```powershell
$wslIp = (wsl -d Debian -u root -e hostname -I).Trim()
uv run --project ..\moo-conformance-tests moo-conformance `
  --moo-host $wslIp `
  --server-command "wsl -d Debian -u root -e env TOAST_MOO=/root/src/toaststunt/build-release/moo bash /mnt/c/Users/Q/code/barn/scripts/run_toast_wsl.sh {db} {port}" `
  --server-db C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_db/Test.db `
  --oracle-profile-manifest C:/Users/Q/code/barn/profiles/toast/stock-wsl-testdb.json `
  --target-profile-manifest C:/Users/Q/code/barn/profiles/toast/stock-wsl-testdb.json `
  -k <focused-selector>
```

Keep a semantic row only when this exact managed command exits zero with the
named row passed. Run against the disposable fixture managed by the harness;
never run Toast directly against the tracked fixture.
