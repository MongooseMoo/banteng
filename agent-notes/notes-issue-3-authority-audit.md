# Issue #3 object-flags authority audit

## State

- Read-only authority audit requested; no implementation, build, test, commit, push, or publication.
- Audited GitHub issue #3 and current remote base `origin/master` at
  `4880f025b2e396548bce7cbeb93a670a46b4f2e6`.
- The primary checkout is stale at `b93e01485f371b4bf4391d66e8c9483a4f5cc4d5`
  and contains many pre-existing untracked notes; do not use it as the implementation base.

## Authority

- Banteng identity record: `docs/reports/toast-oracle-identity-2026-07-14.md`.
- Pinned profile: `profiles/toast/stock-wsl-testdb.json`.
- Both identify ToastStunt source commit
  `aecc51e9449c6e7c95272f0f044b5ba38948459e` at
  `/root/src/toaststunt` in Debian WSL.
- Live checkout matched that exact commit during this audit.
- Toast `src/include/db.h:253-269` declares the permanent enum in bit order:
  `USER=1`, `PROGRAMMER=2`, `WIZARD=4`, obsolete bit `8`, `READ=16`,
  `WRITE=32`, obsolete bit `64`, `FERTILE=128`, `ANONYMOUS=256`,
  `INVALID=512`, `RECYCLED=1024`.
- Toast `src/db_objects.cc:1269-1292` defines `db_object_allows` as wizard OR owner
  OR requested object flag, while `is_wizard`, `is_programmer`, and `is_user`
  require a valid permanent object and then test the corresponding flag.
- Barn `db/store/object.go:304-339` mirrors all non-obsolete named bits and has
  generic `Has`, `Set`, `Clear`, plus `ObjectAllows`.

## Current findings

- `moo.world.ObjectFlags` is the narrow domain location requested: flags are world
  object metadata used by world, VM, runtime, and builtins. Do not put property or
  verb permission masks in this class.
- Issue #3 understates the duplicated surface. On current `origin/master`, magic
  object-flag checks also exist in `BuiltinPermissionRule`, `MooRuntime`,
  `TaskRegistry`, and `MooVm`, not only `BuiltinCatalog` and `WorldTxn`.
- `WorldTxn` also mutates write and fertile via bare `32` and `128`, and projects
  read/write/fertile via bare `16`/`32`/`128`.
- The complete named flag family should include user/player, programmer, wizard,
  read, write, fertile, anonymous, invalid, and recycled. Obsolete bits 3 and 6
  should remain reserved, not be repurposed.
- Predicate names must not blur raw bit membership with Toast authority:
  `isReadable(flags)`/`isWritable(flags)`/`isFertile(flags)` are raw flag tests;
  owner/wizard bypass belongs in a separate `allows(owner, programmer, wizard,
  requiredFlag)` rule if centralized. `isWizard`/`isProgrammer` over a mere int
  cannot reproduce Toast's `valid(oid)` requirement; call sites already enforce
  missing-object false and should preserve that behavior.

## Next action

- Exact `origin/master` inventory found 32 bare wizard-bit checks:
  `BuiltinCatalog` 13, `BuiltinPermissionRule` 1, `MooRuntime` 5,
  `TaskRegistry` 5, and `MooVm` 8. It also found three bare programmer checks,
  three bare read checks, five bare write checks, plus bare write mutation and
  fertile projection/mutation.
- The worker's red test commit `a5cf8dc` correctly pins every Toast enum bit,
  including reserved and lifecycle bits. Its static regex only covers
  `1,2,4,16,32,128,256`, and its duplicate-constant regex only covers
  `PLAYER,PROGRAMMER,WIZARD,FERTILE,ANONYMOUS`. If the single-home invariant is
  meant to be future-proof, the patterns should also cover reserved `8/64`,
  lifecycle `512/1024`, and every corresponding legacy constant stem.
- Current issue worktree has an uncommitted `ObjectFlags.java` and modified
  `WorldTxn.java`; this audit did not inspect or alter those in-progress files.
- Send the completed authority report to the parent. No blocker.
