# #93: behavior and compiled architecture tests

## Goal
Implement issue #93's decisions without changing behavioral or conformance checks.

## State
Isolated branch `campaign/93-behavior-tests` from `origin/master` at `90ecb19`.
Initial implementation was committed as a87d584. Coordinator requested completion of the existing structural-rule migration before publication. No local CI suite was run, and nothing was published or pushed by this worker.

## Decisions and why
Smallest change is primarily deleting text assertions and retaining real contracts over compiled classes or resources. No workflow changes, no generic testing framework.

Exact changes and evidence from the starting tree:
- Delete DuplicationConsolidationTest, OpcodeFamilyArchitectureTest and RepositoryHygieneTest, explicitly named by issue #93.
- NamespaceOwnershipTest: source namespace/path scan (141-176) becomes one compiled class namespace rule in ArchitectureTest, importing classpath directories rather than filtering by the namespace being tested. Remove fixed-point workflow spelling assertions (211-256), residual doc/source whitelist (279-509), documentation namespace assertions (317-357), and exact ChangePackage residual proof (359-374). Keep build/launcher/resource contracts (178-208) and JFR event names (258-276); these read config and resources, never Java source. No count map exists in this head.
- ArchitectureTest: remove persistence's syntax allowance while retaining its explicit prohibition. Move System.err ownership from ServerLogRoutingTest:29-45 into a compiled field-access rule.
- ThreadingContractTest: move GuardedBy field assertions (33-67) into compiled annotation/private-field checks in ArchitectureTest; remove documentation-word checks (69-79). Keep compiler dependency and lockfile checks (15-30).
- SharedDbReaderInfrastructureTest: move reader dependency source spelling checks (140-153) into compiled class dependency rules. Keep all scanner/value decoding behavior checks.
- ObjectFlagsTest: remove numeric-mask source scanner and its own regex tests (17-40, 88-164). Preserve bit values, predicates and existing API assertions (42-85). Those source-token restrictions concern spelling rather than runtime behavior and cannot be faithfully expressed over compiled integer constants.
- WorldTxnDecompositionTest: remove the exact switch-branch source substring check (19-37); migrate all remaining ownership, sealed holder, method and constructor rules into ArchUnit conditions in ArchitectureTest and delete the empty class. Likewise migrate the remaining ServerLogRoutingTest constructor/field ownership rule and delete that class. SharedDbReaderInfrastructureTest's top-level record/scanner ownership and ObjectFlagsTest's obsolete API checks also move there; their record values and bit/predicate behavior remain.
- Restrict namespace imports to root/errorprone-checks/rewrite-recipes build/classes/java directories. The modules are declared in settings.gradle.kts:7-8. This avoids importing Gradle worker/dependency classpath directories while still inspecting compiled outputs independently of their declared namespace.
- ArchUnit v1.4.2 JavaClass source has no sealed/PermittedSubclasses model (https://raw.githubusercontent.com/TNG/ArchUnit/v1.4.2/archunit/src/main/java/com/tngtech/archunit/core/domain/JavaClass.java). Only that class attribute uses reflection inside its ArchUnit condition; field/method/constructor/subtype checks use the imported compiled domain.
- Preserve existing behavioral/conformance checks and workflow fixed-point step.

## Surprises
Current NamespaceOwnershipTest has no file-count map. Five other test classes read production Java source; the issue's universal no-source-reading decision reaches those methods too.

## Next step
Coordinator: inspect exact diff, obtain Claude review, publish PR, inspect hosted CI and normally merge if accepted.

## Draft PR text
Title: Replace source-shape tests with compiled architecture rules

Tests no longer read production Java source or pin the completed package move and past refactor shapes. Namespace ownership, stderr ownership, guarded fields and reader dependency rules now inspect compiled classes in ArchitectureTest. Build/resource identity contracts and behavioral tests remain, and persistence consistently forbids syntax dependencies.

Closes #93.

Validation: source-read search and diff inspection completed. Hosted CI pending; no local suite run under repository instructions.
