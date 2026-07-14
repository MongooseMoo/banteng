# WSL Toast oracle identity verification

> Historical record. The Barn-local report named below no longer exists in the
> current checkout. Current authority is
> `../barn/plans/barn-toast-mongoose-convergence-100-line.md`,
> `../barn/profiles/toast/stock-wsl-testdb.json`,
> `../barn/scripts/run_toast_wsl.sh`, and the exact managed command in
> `../barn/plans/barn-toast-mongoose-convergence-workstreams.md`. Current
> verification is recorded in `toast-oracle-identity-2026-07-14.md`.

## Scope

This is the Phase 0 identity record required by
`docs/reports/banteng-implementation-plan.md`. It verifies the two WSL source
trees and executables named by `../barn/reports/toast-oracle-wsl.md`. No
behavioral conformance run was performed, so this record is not evidence for a
MOO semantic claim and has no fixture, profile, connection, or pass/fail result.

## Context

- Timestamp: `2026-07-13T22:42:39.7790321-06:00`
- Working directory: `C:\Users\Q\code\banteng`
- Barn branch: `master`
- Barn HEAD: `190c922a62996b18640f45847b7f30963a867c4a`
- Barn tracked state from `git status --short --untracked-files=no`:
  ` M AGENTS.md`

The tracked Barn change pre-existed this verification and was not modified.

## Stock Toast

- Binary: `/root/src/toaststunt/build-release/moo`
- Required source SHA: `aecc51e9449c6e7c95272f0f044b5ba38948459e`
- Observed source SHA: `aecc51e9449c6e7c95272f0f044b5ba38948459e`
- Executable check: exit status `0`
- Version: `ToastStunt version 2.7.3_5`

Commands:

```powershell
wsl -d Debian -u root -e git -C /root/src/toaststunt rev-parse HEAD
wsl -d Debian -u root -e test -x /root/src/toaststunt/build-release/moo
wsl -d Debian -u root -e /root/src/toaststunt/build-release/moo --version
```

Result: the observed source SHA exactly matches the required pin and the named
binary is executable.

## Mongoose Toast with `PROMOTE_NUMBERS`

- Binary: `/root/src/toaststunt-mongoose/build-release/moo`
- Required source SHA: `72e3c7f96ce7a41fdeba793aef8818dc4408072e`
- Observed source SHA: `72e3c7f96ce7a41fdeba793aef8818dc4408072e`
- Executable check: exit status `0`
- Version: `ToastStunt version 2.7.3_5`

Commands:

```powershell
wsl -d Debian -u root -e git -C /root/src/toaststunt-mongoose rev-parse HEAD
wsl -d Debian -u root -e test -x /root/src/toaststunt-mongoose/build-release/moo
wsl -d Debian -u root -e /root/src/toaststunt-mongoose/build-release/moo --version
```

Result: the observed source SHA exactly matches the required pin and the named
binary is executable.
