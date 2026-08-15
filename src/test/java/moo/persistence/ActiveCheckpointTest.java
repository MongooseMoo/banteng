package moo.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import moo.world.WorldTxn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ActiveCheckpointTest {
  @Test
  void activeCheckpointRetainsItsCommittedRevisionWithoutBlockingLaterSegments(
      @TempDir Path directory) throws Exception {
    WorldTxn root = new WorldTxn(List.of(), List.of());
    CountDownLatch promotionEntered = new CountDownLatch(1);
    CountDownLatch releasePromotion = new CountDownLatch(1);
    LambdaMooV17Codec codec =
        new LambdaMooV17Codec(
            (source, target) -> {
              promotionEntered.countDown();
              await(releasePromotion);
              Files.move(
                  source,
                  target,
                  java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                  java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            });
    Path checkpoint = directory.resolve("active.db");
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread writer =
        Thread.ofPlatform()
            .start(
                () -> {
                  try (WorldTxn.RetainedSnapshot retained = root.retainSnapshot()) {
                    codec.writeAtomic(checkpoint, retained.snapshot(), List.of(), List.of());
                  } catch (Throwable caught) {
                    failure.set(caught);
                  }
                });

    assertTrue(promotionEntered.await(3, TimeUnit.SECONDS));
    for (int revision = 1; revision <= 100; revision++) {
      try (WorldTxn segment = root.begin()) {
        assertTrue(segment.commit().isCommitted());
      }
    }
    assertTrue(writer.isAlive());
    releasePromotion.countDown();
    writer.join(TimeUnit.SECONDS.toMillis(3));

    assertFalse(writer.isAlive());
    assertEquals(null, failure.get());
    assertEquals(0, codec.read(checkpoint).world().snapshot().revision());
  }

  private static void await(CountDownLatch release) throws IOException {
    try {
      release.await();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("checkpoint promotion interrupted", interrupted);
    }
  }
}
