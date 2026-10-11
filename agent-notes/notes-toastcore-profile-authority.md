# ToastCore profile authority audit

## Scope

- Read-only Phase 0 audit delegated by the parent agent.
- Inspect existing ToastCore reports, plans, and scripts in `C:\Users\Q\code\barn`.
- Inspect the managed harness and profile schema in `C:\Users\Q\code\moo-conformance-tests`.
- Determine the checked-in profile manifest path/fields and the managed WSL command/login mechanism for `C:\Users\Q\code\barn\toastcore.db`.
- Determine whether the stock Toast binary identified by the `aecc51e...` digest is appropriate.

## Current state

- User selected ToastCore as in-scope for the first release.
- No repository files have been inspected or changed in this delegated audit yet.
- No destructive commands or Banteng execution are authorized.

## Findings and observations

- Barn requires the managed conformance command and forbids manual server launch for ordinary conformance work (`barn/AGENTS.md:5-10`).
- Barn's convergence plan selects stock WSL Toast `/root/src/toaststunt/build-release/moo` at commit `aecc51e9449c6e7c95272f0f044b5ba38948459e` for ordinary behavior, and reserves the separate Mongoose build for `PROMOTE_NUMBERS` behavior (`barn/plans/barn-toast-mongoose-convergence-workstreams.md:28-47`).
- The checked-in Toast profile convention is `barn/profiles/toast/<profile-name>.json`; the two current examples are `stock-wsl-testdb.json` and `mongoose-wsl-mongoose.json`.
- The complete Barn manifest struct has: `profile_id`, `implementation`, `implementation_ref`, `binary_path`, `build_system`, `runtime_os`, `arch_bits`, `database_fixture`, `database_checksum`, `config_file`, `config_checksum`, `features`, `support_status`, and `unsupported_reason` (`barn/profile/manifest.go:16-31`).
- The conformance gate specifically requires matching `database_fixture`, `database_checksum`, and `runtime_os`; accepted `support_status` values are `supported` and `diagnostic`; and both manifests require boolean `option.OUTBOUND_NETWORK` and `option.PROMOTE_NUMBERS` features (`moo-conformance-tests/src/moo_conformance/profile_gate.py:14-16,32-50`).
- The existing WSL managed wrapper contract is `--server-command "wsl -e bash <this> {db} {port}"`; it converts the Windows disposable DB path with `wslpath`, defaults `TOAST_MOO` to the stock WSL binary, and execs Toast as input DB, `{db}.new`, and port (`barn/scripts/run_toast_wsl.sh:2-12,15-29`).
- `barn/toastcore.db` currently has no corresponding checked-in profile manifest; only stock Test.db and Mongoose manifests are present.
- The selected tracked fixture is `C:\Users\Q\code\barn\toastcore.db`, size `2,083,333`, SHA-256 `da1c3ea32e57857855be94999efba930eaf8d1f5f3cbd04c16165ce7f54fd483`.
- It is intentionally a different identity from WSL `/root/src/toastcore/toastcore.db` and Barn `db/format/testdata/toastcore.db`, which are both size `2,104,432` and SHA-256 `8013b703c61a9894866f836f2b934eada7118cdf0b3cd56181e4bf9205b2f557`. The selected Barn-root fixture therefore must be hashed and manifested as itself, not substituted with the WSL core.
- An exact reusable managed PowerShell invocation shape already exists in Banteng authority reports, for example `docs/reports/toast-oracle-identity-2026-07-14.md:59-67`. For ToastCore, the literal substitutions are `--server-db C:/Users/Q/code/barn/toastcore.db` and both profile flags pointing to the new ToastCore manifest; the server command itself remains unchanged.
- ToastCore needs no credential environment hook in the evidence inspected. The managed harness maps its session user to case-sensitive `Wizard` and defaults to raw `connect Wizard` (`moo-conformance-tests/src/moo_conformance/transport.py:219-225,348-356`). `--moo-login-script-env` is only needed when the selected fixture requires a non-default multi-command login (`plugin.py:124-154`).
- The stock binary is the correct selected engine for this ordinary, non-`PROMOTE_NUMBERS` ToastCore profile: Barn's plan assigns `aecc51e...` to ordinary behavior and reserves `72e3c7...` for Mongoose promotion behavior (`barn/plans/barn-toast-mongoose-convergence-workstreams.md:35-47`). Live source HEAD and options checksum match the stock manifest.

## Blocker

- No checked-in authority explicitly dictates the new filename/profile ID. Applying the two existing profile conventions yields `barn/profiles/toast/stock-wsl-toastcore.json` and `profile_id: toast-stock-wsl-toastcore`; this is a convention-derived required addition, not an already-existing manifest.
- No exact ToastCore-specific managed invocation is checked into Barn or `moo-conformance-tests`; the existing exact invocation is stock-Test.db-specific and must be mechanically retargeted to the selected ToastCore DB and its new manifest.

## Next action

- Parent agent should add the new checked-in ToastCore profile manifest, then run the existing managed WSL command shape with the selected DB, the same manifest supplied as both oracle and target, and default `connect Wizard` login. No helper or adapter is needed.
