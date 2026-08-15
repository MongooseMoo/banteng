package moo.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CheckpointRetentionTest {
  @Test
  void checkpointLeaseRetainsOnlyItsRevisionAndTheCurrentRevision() {
    WorldTxn root = new WorldTxn(List.of(), List.of());
    WorldTxn.RetainedSnapshot retained = root.retainSnapshot();
    assertEquals(0, retained.snapshot().revision());

    for (int revision = 1; revision <= 100; revision++) {
      try (WorldTxn transaction = root.begin()) {
        assertTrue(transaction.commit().isCommitted());
      }
      assertEquals(List.of(0L, (long) revision), root.retainedRevisions());
      assertEquals(2, root.retainedRevisionCount());
    }

    retained.close();
    assertEquals(List.of(100L), root.retainedRevisions());
    assertEquals(1, root.retainedRevisionCount());
    retained.close();
    assertThrows(IllegalStateException.class, retained::snapshot);
  }
}
