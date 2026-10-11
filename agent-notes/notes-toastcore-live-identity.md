# ToastCore live identity subtask

- Scope: read-only Phase 0 verification of Debian WSL stock Toast source and binary identity at `/root/src/toaststunt`, plus determination of whether `C:\Users\Q\code\barn\toastcore.db` has an already authorized managed/disposable boot path.
- Current findings:
  - Barn `AGENTS.md` requires documented managed conformance commands, forbids manual Toast launches without explicit approval, and requires disposable copies for any manual repro.
  - `profiles/toast/stock-wsl-testdb.json` pins `/root/src/toaststunt/build-release/moo` at commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`, but it authorizes the bundled `Test.db` profile, not `toastcore.db`.
  - `scripts/run_toast_wsl.sh` is the managed foreground wrapper; it accepts a Windows database path and port and writes to `{db}.new`, but the documented usage is through the conformance harness.
  - `reports/toast-disassemble-oracle.md` records prior use of `/root/src/toastcore/toastcore.db` and refers to a repository disposable `cmd/toast_oracle` flow, without giving the exact command in that report.
  - Live Debian WSL source identity: `/root/src/toaststunt` is on `master` at `aecc51e9449c6e7c95272f0f044b5ba38948459e`; tracked files are clean, with existing untracked files including `build-release/`.
  - Live binary identity: `/root/src/toaststunt/build-release/moo` is an ELF64 x86-64 PIE, SHA-256 `72fb1cf96cb303647a8ee72808e7c1ff62a491ecf44f547e6757e71ba2402bde`, BuildID `459b718e8fe6ba7e404a7af61c3a177ac7296fc9`, size 988264 bytes, and reports `ToastStunt version 2.7.3_5`; its `--version` exit is 1 and is not used as the managed identity gate.
  - Live options identity: `/root/src/toaststunt/src/include/options.h` SHA-256 is `a88a8c6c37b66ca65a08a318988361827f131421edeff25e5b4af83fb3fa8036`, matching the stock profile manifest.
  - `cmd/toast_oracle` copies its selected database to an OS temporary file and runs emergency wizard mode, but the active plan explicitly says it is expression-only and cannot replace `moo_client.exe` for login, connection, or room-render behavior.
  - The tracked, clean Barn fixture `C:\Users\Q\code\barn\toastcore.db` has SHA-256 `da1c3ea32e57857855be94999efba930eaf8d1f5f3cbd04c16165ce7f54fd483` and size 2083333 bytes. It is not identical to `/root/src/toastcore/toastcore.db`, whose SHA-256 is `8013b703c61a9894866f836f2b934eada7118cdf0b3cd56181e4bf9205b2f557` and size is 2104432 bytes.
- State: source/binary/config and both candidate fixture identities were verified read-only. No Toast server was started, no boot/login was attempted, and PID 19 was not touched.
- Constraints: do not edit Banteng source or tracked fixtures; do not stop unrelated PID 19; run a boot/login check only if Barn already records the exact managed command and authority.
- Blocker: Barn does not record an exact authorized managed ToastCore boot/login command. It also lacks a ToastCore profile manifest selecting one of the two differing fixture identities and lacks an exact `moo_client.exe` login invocation/credential authority for this database.
- Next action: stop. Resume a live boot/login only after the user or controlling plan supplies the selected ToastCore fixture identity, supported profile manifest, and exact managed client/login command.
