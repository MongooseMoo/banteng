package moo.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import moo.world.WorldTxn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AtomicCheckpointFailureTest {
  @Test
  void unsupportedAtomicMovePreservesLastGoodDatabaseAndDeletesTemporaryFile(
      @TempDir Path directory) throws IOException {
    Path checkpoint = directory.resolve("world.db");
    LambdaMooV17Codec production = new LambdaMooV17Codec();
    production.writeAtomic(
        checkpoint, new WorldTxn(List.of(), List.of()).snapshot(), List.of(), List.of());
    byte[] lastGood = Files.readAllBytes(checkpoint);
    LambdaMooV17Codec failing =
        new LambdaMooV17Codec(
            (source, target) -> {
              throw new AtomicMoveNotSupportedException(
                  source.toString(), target.toString(), "injected proof");
            });

    assertThrows(
        AtomicMoveNotSupportedException.class,
        () ->
            failing.writeAtomic(
                checkpoint,
                new WorldTxn(List.of(), List.of()).snapshot(),
                List.of(),
                List.of()));

    assertArrayEquals(lastGood, Files.readAllBytes(checkpoint));
    try (var entries = Files.list(directory)) {
      assertEquals(List.of(checkpoint), entries.toList());
    }
  }
}
