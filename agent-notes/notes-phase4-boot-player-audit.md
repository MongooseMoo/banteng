# Phase 4 boot-player path audit

## Observed

- The exact row is `audit_boot_player_messages` in `../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:1429-1508`.
- Setup creates and logs in a player, then the required action invokes `boot_player(#0.audit_boot_player)` at line 1469 and expects `*** Disconnected ***` on the target connection at lines 1474-1479.
- At committed HEAD `b40e05c`, `boot_player` was absent from the catalog, while the Result/VM/runtime/server boot-effect plumbing already existed.
- A concurrent uncommitted parent-agent change now adds the catalog entry, implementation, and focused catalog test in `BuiltinCatalog.java` and `BuiltinCatalogTest.java`; this audit has not edited those files.
- Existing runtime ownership is `MooRuntime.applyBootPlayerTargets`, `finishBootPlayer`, publication of `RuntimeEffect.BOOT`, and `MooServer.bootConnection`.
- The primary listener has `printMessages=true`; the fixture contains no `boot_msg` string/property name, so current runtime falls back to the exact expected default.

## State and blocker

- The committed first mismatch is the missing catalog producer for the existing boot target effect.
- There is no remaining row-setup builtin dependency after the recently committed verb metadata builtins.
- Current tests have no socket-level boot regression; the focused catalog test alone does not prove the required message reaches the connection and EOF follows.

## Next action

- Finish the read-only review of the concurrent slice and report to the parent that the minimal kept slice needs the existing catalog producer plus one exact `MooServerTest` covering login, wizard `boot_player`, the exact disconnect line, and socket EOF. No new helper, interface, adapter, runtime, or server production change is needed.
