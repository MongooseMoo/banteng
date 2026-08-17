package moo.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class VerbCacheTest {
  @Test
  void callableLookupCachesPositiveAndNegativeResultsWithRealHistogram() {
    WorldTxn root = world();

    assertEquals(
        new WorldTxn.VerbCacheStats(0, 0, 0, 0, zeroHistogram()),
        root.verbCacheStats());

    try (WorldTxn transaction = root.begin()) {
      assertTrue(transaction.verb(2, "LOOK").isPresent());
      assertTrue(transaction.verb(2, "look").isPresent());
      assertFalse(transaction.verb(2, "missing").isPresent());
      assertFalse(transaction.verb(2, "MISSING").isPresent());
    }

    assertEquals(
        new WorldTxn.VerbCacheStats(1, 1, 2, 0, histogram(7_505, 2)),
        root.verbCacheStats());
  }

  @Test
  void chainedBucketsReportTheirActualDepth() {
    WorldTxn root = world();
    try (WorldTxn transaction = root.begin()) {
      assertFalse(transaction.verb(2, "verb18").isPresent());
      assertFalse(transaction.verb(2, "verb20").isPresent());
    }

    assertEquals(
        new WorldTxn.VerbCacheStats(0, 0, 2, 0, histogram(7_506, 0, 1)),
        root.verbCacheStats());
  }

  @Test
  void mutationBeforeLazyAllocationDoesNotInventAGeneration() {
    WorldTxn root = world();
    try (WorldTxn transaction = root.begin()) {
      assertTrue(transaction.setVerbArgs(1, 0, 1, -1, 0));
      assertTrue(transaction.commit().isCommitted());
    }

    assertEquals(
        new WorldTxn.VerbCacheStats(0, 0, 0, 0, zeroHistogram()),
        root.verbCacheStats());
  }

  @Test
  void publishedLookupMutationClearsEntriesAndAdvancesGeneration() {
    WorldTxn root = world();
    try (WorldTxn transaction = root.begin()) {
      assertTrue(transaction.verb(2, "look").isPresent());
    }

    try (WorldTxn transaction = root.begin()) {
      assertTrue(transaction.setVerbInfo(1, 0, "examine", 1, 4));
      assertTrue(transaction.commit().isCommitted());
    }

    assertEquals(
        new WorldTxn.VerbCacheStats(0, 0, 1, 1, histogram(7_507, 0)),
        root.verbCacheStats());
    try (WorldTxn transaction = root.begin()) {
      assertFalse(transaction.verb(2, "look").isPresent());
      assertTrue(transaction.verb(2, "examine").isPresent());
    }
    assertEquals(
        new WorldTxn.VerbCacheStats(0, 0, 3, 1, histogram(7_505, 2)),
        root.verbCacheStats());
  }

  @Test
  void codeOnlyPublicationKeepsCallableCacheAndStaleTransactionsCannotRepopulateIt() {
    WorldTxn root = world();
    WorldTxn stale = root.begin();
    try (WorldTxn transaction = root.begin()) {
      assertTrue(transaction.verb(2, "look").isPresent());
      assertTrue(transaction.setVerbCode(1, 0, "return 2;"));
      assertTrue(transaction.commit().isCommitted());
    }

    try (WorldTxn transaction = root.begin()) {
      assertTrue(transaction.verb(2, "look").isPresent());
    }
    assertEquals(
        new WorldTxn.VerbCacheStats(1, 0, 1, 0, histogram(7_506, 1)),
        root.verbCacheStats());

    try (WorldTxn transaction = root.begin()) {
      assertTrue(transaction.setVerbArgs(1, 0, 1, -1, 0));
      assertTrue(transaction.commit().isCommitted());
    }
    assertTrue(stale.verb(2, "look").isPresent());
    stale.close();
    assertEquals(
        new WorldTxn.VerbCacheStats(1, 0, 1, 1, histogram(7_507, 0)),
        root.verbCacheStats());
  }

  private static WorldTxn world() {
    WorldVerb look = new WorldVerb("look", 1, 4, -1, "return 1;");
    WorldObject parent =
        new WorldObject(
            1,
            "parent",
            0,
            1,
            -1,
            -1,
            List.of(),
            List.of(2L),
            List.of(look),
            List.of());
    WorldObject child =
        new WorldObject(
            2,
            "child",
            0,
            1,
            -1,
            1,
            List.of(),
            List.of(),
            List.of(),
            List.of());
    return new WorldTxn(List.of(), List.of(parent, child));
  }

  private static List<Integer> zeroHistogram() {
    return histogram(0, 0);
  }

  private static List<Integer> histogram(int empty, int single) {
    return histogram(empty, single, 0);
  }

  private static List<Integer> histogram(int empty, int single, int doubleDepth) {
    return List.of(
        empty, single, doubleDepth, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  }
}
