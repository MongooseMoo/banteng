# WSL ToastCore oracle identity verification

## Scope

This Phase 0 record proves the selected upstream ToastCore fixture through
Banteng's managed WSL Toast workflow. It freezes the source, executable,
configuration, profile, database, login, disposable-fixture command, and one
Wizard-scoped managed identity row. It does not claim full ToastCore semantic
conformance.

## Banteng-owned operational authority

- ToastCore profile: `profiles/toast/stock-wsl-toastcore.json`
- Managed WSL launcher: `scripts/run_toast_wsl.sh`
- Generic conformance harness: `../moo-conformance-tests`
- Upstream core checkout: `https://github.com/lisdude/toastcore.git`

Barn remains a semantic reference but is not an operational dependency of this
workflow.

## Verification context

- Timestamp: `2026-07-15T16:05:46.7078474-06:00`
- Banteng HEAD before this record:
  `a880aace84e142e5bf765ab08497d27856bc3dfd`
- `moo-conformance-tests` HEAD:
  `fcc853a962940bbafe444c836ef85d3cc6a77185`
- `moo-conformance-tests` tracked state: clean
- WSL distribution/user: `Debian`, `root`

## Toast executable identity

- Source checkout: `/root/src/toaststunt`
- Source HEAD: `aecc51e9449c6e7c95272f0f044b5ba38948459e`
- Source tracked state: clean
- Executable: `/root/src/toaststunt/build-release/moo`
- Executable SHA-256:
  `72fb1cf96cb303647a8ee72808e7c1ff62a491ecf44f547e6757e71ba2402bde`
- Configuration: `/root/src/toaststunt/src/include/options.h`
- Configuration SHA-256:
  `a88a8c6c37b66ca65a08a318988361827f131421edeff25e5b4af83fb3fa8036`
- Profile features: `OUTBOUND_NETWORK=true`, `PROMOTE_NUMBERS=false`,
  `runtime.arch_bits=64`

Untracked files in the Toast source checkout do not replace or alter the pinned
tracked source identity, executable checksum, or configuration checksum.

## ToastCore fixture identity

- Checkout: `/root/src/toastcore`
- Origin: `https://github.com/lisdude/toastcore.git`
- Local HEAD: `1887eacd591d97fdc55d258a76e2167899b1951d`
- Remote HEAD observed with `git ls-remote`:
  `1887eacd591d97fdc55d258a76e2167899b1951d`
- Tracked state: clean
- Fixture: `/root/src/toastcore/toastcore.db`
- Windows harness path:
  `\\wsl.localhost\Debian\root\src\toastcore\toastcore.db`
- Size: `2104432` bytes
- SHA-256:
  `8013b703c61a9894866f836f2b934eada7118cdf0b3cd56181e4bf9205b2f557`
- Database format: LambdaMOO v17
- Login authority: upstream `README.md`, which specifies `connect wizard` for
  the primary wizard character of a fresh ToastCore.

The older `../barn/toastcore.db` is a different database and is not an
authority or fixture for this profile.

## Managed command

Run from the Banteng repository after obtaining the current Debian WSL address:

```powershell
$wslIp = (wsl -d Debian -u root -e hostname -I).Trim()
uv run --isolated --project ..\moo-conformance-tests moo-conformance `
  --moo-host $wslIp `
  --server-command "wsl -d Debian -u root -e env TOAST_MOO=/root/src/toaststunt/build-release/moo bash /mnt/c/Users/Q/code/banteng/scripts/run_toast_wsl.sh {db} {port}" `
  --server-db "\\wsl.localhost\Debian\root\src\toastcore\toastcore.db" `
  --oracle-profile-manifest C:/Users/Q/code/banteng/profiles/toast/stock-wsl-toastcore.json `
  --target-profile-manifest C:/Users/Q/code/banteng/profiles/toast/stock-wsl-toastcore.json `
  --moo-skip-standard-properties `
  -k connection_info_returns_map
```

The harness copies the upstream fixture to its temporary managed directory;
Toast reads and writes only that disposable copy. The selected row has
`permission: wizard`, so the harness retains its session-start
`connect Wizard` identity instead of attempting a different player login.

## Result

The exact command exited `0` on 2026-07-15:

```text
collected 11519 items / 11518 deselected / 1 selected
test_conformance.py .                                                    [100%]
1 passed, 11518 deselected in 3.42s
```

This proves managed boot, profile compatibility, README-mandated Wizard login,
evaluation, and cleanup for the pinned upstream fixture. Full profile
conformance, Banteng target execution, v17 output, checkpoint, and restart
remain separate required gates.
