# Issue 87: one MOO equality and comparison

## Goal
Centralize MOO equality and ordering in ValueSemantics, following Toast utils.cc:408-492 plus the existing numeric-promotion extension. Preserve Java representation equality/hashCode and constructor behavior.

## State
PR #108 targets campaign/89-builtin-contract at 09d8fd5. The coordinator integrated PR #109 at eb344dd to preserve its pure-VM fixture move; ValueSemanticsTest now uses PureVmFixture. The prior bc48eaa review confirmed the map repair and identified an unused LoopOps import and stale State text, both corrected here. Current-head review and PR CI remain required. Coordinator owns publication and merges; other agents own separate worktrees.

## Decisions and why
Smallest approved change: about 250 added / 300 deleted lines plus focused tests. Add equality/comparison to the existing ValueSemantics record; expose in-order traversal of the existing map lookup tree; delete duplicate semantic comparisons and migrate actual equality/membership/key-order callers. No Java sort with Toast's asymmetric identity comparison, no new storage or comparator framework. Keep natural sorting and relational/NaN boundary policies, and keep all Java equals/hashCode methods unchanged.

## Surprises
Long integer/object key comparison is already fixed. Toast's runtime type rank includes TYPE_COMPLEX_FLAG and differs from persisted Type.code ordering. Its map equality and map-value membership traverse tree order. Java representation methods deliberately retain existing behavior and are not used for MOO-level equality.

## Next step
PR #108 review repair: integrated campaign/89-builtin-contract 09d8fd5 as merge 90a906a before editing. The map equality branch incorrectly checked Java representation size before traversing the separate lookup tree, and advanced the right iterator without checking exhaustion. `StringValue.equals` folds valid UTF-8 Unicode while map insertion uses byte/ASCII ordering, so literal keys with bytes C3 A9 and C3 89 can produce one representation entry and two lookup-tree entries. Smallest repair: compare paired iterators until either is exhausted, then require both exhausted and every compared entry equal, matching Toast map.cc:774-788. Add only a focused real-literal regression to existing ValueSemanticsTest; preserve identity suppression, map representation and conformance. Parent owns publication/CI.

Repair validation: `./gradlew --no-daemon --max-workers=2 :test --tests world.mongoose.banteng.vm.ValueSemanticsTest.mapLiteralEqualityRequiresBothLookupTreesToBeExhausted` through WSL Debian/root Java25 completed `BUILD SUCCESSFUL in 5m 48s`. The authored method builds the real map literals, verifies inequality in both operand orders through `==` and case-sensitive `equal()`, and equality of the same two-entry trees built in reverse insertion order. Java source remained unchanged throughout compilation/execution. No local CI pipeline or conformance run. Parent owns final PR checks and publication.

Q explicitly answered "Allow the narrow suppression" for ReferenceEquality on ValueSemantics.equal. Applied only that method annotation with a source comment identifying Toast list.cc:359 and map.cc:766, restored the direct collection identity shortcuts, and deleted the attempted sameReference helpers. The prior three runs stopped at compilation; no tests executed in those runs. The focused run passed on committed Java source 2d280e4: `JAVA_HOME=/opt/java/25 ./gradlew --no-daemon :test --tests world.mongoose.banteng.vm.ValueSemanticsTest`, BUILD SUCCESSFUL in 6m 48s. Java source stayed unchanged throughout compilation/execution. Parent owns publication and full PR CI; those remain unverified locally.

## Source and implementation
ValueSemantics now owns Toast equality and compare (utils.cc:408-492). Its type ordering accounts for the complex flag (include/structures.h:84,100-118). String comparison uses C-string byte/ASCII rules; equality retains the memo-length comparison enabled by include/options.h:343. Collection equality follows list.cc:359-373 and map.cc:766-790, including same-collection identity shortcuts.

Deleted ArithmeticOps.mooEquals, Catalog.exactlyEqual, setRemoveValue's private equality, MapValue.compareKeys, the catalog map-key Java sort, dead StringValue comparison methods, and the unused ArithmeticOps numeric helper. Java equals/hashCode and MapValue constructors remain unchanged. Standard comparison supplies map lookup, insertion/replacement, deletion, range endpoints, restored loop keys, and v17 duplicate-key validation. Configured semantics supplies MOO equality and set membership, including all_members.

MapValue.orderedEntries walks its existing RB tree, with no new storage. Map keys/values, map equality, map-value membership, ranges, boundaries, and loops use that traversal. Parent explicitly included LoopOps after inspecting its cursor consumers. Toast starts/advances map loops through new_iter/mapseek/iterget/iternext (execute.cc:2676-2697), with new_iter selecting rbtfirst (map.cc:946-956). Existing cursor key representation/storage is unchanged.

Natural string sort remains its own documented option (list.cc:947-1010). Boolean sort equivalence and NaN policies remain at the sorting/relational callers; ordinary scalar ordering delegates to ValueSemantics. Integrated current origin/master without conflicts after the failed authored run exited. Master includes issue 93's compiled architecture graph; the obsolete source-shape test was removed by that merge, and no source-shape assertion is restored.

## Draft PR
Title: Share MOO equality and comparison across values and callers

Set addition, removal, VM equality/membership, and equal() now use the same Toast equality rules, including boolean/integer equivalence and configured numeric promotion. All map-key comparisons delegate to ValueSemantics; map ordering follows the existing lookup tree instead of Java sorting with an asymmetric identity comparator. Java representation equality/hashing and caller-specific natural-sort/relational policies remain intact.

Prepublication repair: compareStrings initially called StringValue.bytes() twice, allocating two full defensive array copies per comparison. It now reads through package-private StringValue.unsignedByteAt(int), retaining comparison ownership in ValueSemantics and keeping the backing array private. No cache, storage, or comparison policy changed. Parent explicitly requested no repeated local CI; the focused test passed on the prior source commit 2d280e4, and final PR CI must verify this repair.

Validation: focused authored ValueSemanticsTest passed through WSL Java 25 before the byte-access repair; it was not rerun afterward. Full CI belongs to the PR. Conformance suite unchanged. Depends on the builtin-contract branch.

Integration: merged opcode-dispatch head 7327523 (which includes typed permissions and runtime-status integration) into e1868dd. Resolved ArithmeticOps by keeping central ValueSemantics equality and #108 ordering/membership while using package-visible Opcode helpers; the removed private equality implementation and operation enums stay deleted. Resolved IndexOps by retaining its shared-semantics map-key search and exposing boundaryIndex for direct VM dispatch. Paired map iteration, narrow ReferenceEquality suppression, typed permission access, Pass/callers rendering and PureVmFixture remain. No local CI, push or publication; parent owns final review and PR CI.
