# Banteng namespace residual audit

Issue #33 moves Banteng-owned Java identity to `world.mongoose.banteng` while preserving upstream
MOO and Toast identities and exact historical evidence. The repository contract audits each
remaining lowercase `moo` line and applies the classifications below. Old dotted, escaped-dotted,
and path-shaped Java namespace spellings are rejected unless the line is a named migration input or
an upstream Toast path.

## Migration inputs and proof

- `rewrite.yml` retains `oldPackageName: moo` because
  `world.mongoose.banteng.ChangePackage` names the source package it migrates.
- `RetargetSingleSegmentPackageReferences` retains the exact `OLD_PACKAGE = "moo"` migration input
  needed to complete references that stock `ChangePackage` misses for a single-segment root.
- `ChangePackageTest` retains exact `moo.*` package, import, and fully-qualified type inputs to prove
  a one-cycle recursive rewrite followed by a fixed point. Its `private Holder moo;` and
  `return moo.example;` lines are the negative proof that an ordinary variable named `moo` and its
  instance access remain unchanged.
- The CI namespace step temporarily creates one `moo.namespace_rewrite_proof` source in each of
  `errorprone-checks` and `rewrite-recipes`. The single root OpenRewrite task must report both moved
  paths before CI removes the probes and requires a clean repository-wide fixed point.
- `NamespaceOwnershipTest` contains old-namespace matcher literals so CI can reject an unclassified
  reintroduction. This audit file is excluded from scanning because it documents those literals.

## Upstream conformance and runtime identities

The following retain the external repository/package/CLI names `moo-conformance-tests`,
`moo_conformance`, conformance flags beginning `--moo-`, and related environment names:

- `.github/workflows/ci.yml`;
- `docs/reports/banteng-implementation-plan.md`;
- `profiles/banteng/stock.json` and `profiles/banteng/toastcore.json`;
- `profiles/toast/stock-wsl-testdb.json` and `profiles/toast/stock-wsl-toastcore.json`;
- `scripts/run_managed_wsl.sh` and `scripts/test_managed_runners_wsl.sh`;
- Java tests that address the external conformance checkout or Python package.

Markdown `moo` code fences identify the MOO language. The implementation plan's `moo_interp` name
is an external reference implementation. Runtime and test code retain the observable
`moo-vm-`, `moo-host-wake-`, `moo-timer-wake-`, and `moo-connect-timeout-` thread-name prefixes as
MOO runtime terminology, not Java package or artifact identity.

## Upstream Toast identity

Toast's executable remains `moo`, and its CMake target remains `CMakeFiles/moo.dir`. Those spellings
remain in the two Toast profiles, `scripts/test_verify_toast_profile_wsl.sh`, and line-classified
historical authority evidence. They are not Banteng launchers, artifacts, or packages.

## Historical evidence

The residual contract recognizes the named historical reports only when an individual line carries
an old Java source/package path, an external conformance identity, a Toast CMake target, or a
backticked upstream `moo` executable. Other lowercase `moo` lines in those files still fail the
audit. The named reports are:

- `docs/reports/arithmetic-float-eval-authority.md`;
- `docs/reports/background-tick-budget-authority.md`;
- `docs/reports/connection-lifecycle-authority.md`;
- `docs/reports/foreground-tick-budget-authority.md`;
- `docs/reports/java-ots-library-review.md`;
- `docs/reports/java25-static-analysis-decision.md`;
- `docs/reports/jazzer-java25-junit6-proof.md`;
- `docs/reports/jcstress-java25-proof.md`;
- `docs/reports/jetcheck-acceptance-spike.md`;
- `docs/reports/jmh-java25-proof.md`;
- `docs/reports/jvm-moo-architecture-research.md`;
- `docs/reports/object-movement-authority.md`;
- `docs/reports/object-parent-authority.md`;
- `docs/reports/phase1-java-skeleton-proof.md`;
- `docs/reports/splice-computed-access-authority.md`;
- `docs/reports/telnet-transport-authority.md`.

The controlling implementation plan is not historical. Its package table, boundary prose,
selectors, commands, and package-derived resource paths use `world.mongoose.banteng`; only explicit
external MOO and Toast identities remain.
