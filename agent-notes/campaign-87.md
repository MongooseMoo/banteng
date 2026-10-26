# Issue 87: one MOO equality and comparison

## Goal
Centralize MOO equality and ordering in ValueSemantics, following Toast utils.cc:408-492 plus the existing numeric-promotion extension. Preserve Java representation equality/hashCode and constructor behavior.

## State
Own isolated branch campaign/87-value-semantics starts at campaign/89-builtin-contract aabe141, with current master integrated at 8d8ade7. Implementation and focused tests are committed locally; no publication by this agent. Other agents own permission changes in separate worktrees.

## Decisions and why
Smallest approved change: about 250 added / 300 deleted lines plus focused tests. Add equality/comparison to the existing ValueSemantics record; expose in-order traversal of the existing map lookup tree; delete duplicate semantic comparisons and migrate actual equality/membership/key-order callers. No Java sort with Toast's asymmetric identity comparison, no new storage or comparator framework. Keep natural sorting and relational/NaN boundary policies, and keep all Java equals/hashCode methods unchanged.

## Surprises
Long integer/object key comparison is already fixed. Toast's runtime type rank includes TYPE_COMPLEX_FLAG and differs from persisted Type.code ordering. Its map equality and map-value membership traverse tree order. Java representation methods deliberately retain existing behavior and are not used for MOO-level equality.

## Next step
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
