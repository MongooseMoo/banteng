package world.mongoose.banteng.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import world.mongoose.banteng.logging.ServerLog;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldTxn;

final class LambdaMooV5ReaderTest {
  private static final Path STARTUP_DATABASES =
      Path.of(
          "..", "moo-conformance-tests", "src", "moo_conformance", "_db", "startup");

  @Test
  void repairsMissingReferencesButRejectsInvalidHierarchies() throws IOException {
    LambdaMooV5Reader reader = new LambdaMooV5Reader(ServerLog.stderr(System.Logger.Level.INFO), new LambdaMooV17Codec());
    for (int fixture = 2; fixture <= 5; fixture++) {
      Path database = STARTUP_DATABASES.resolve("Broken" + fixture + ".db");
      assertThrows(IOException.class, () -> reader.read(database), database.toString());
    }

    WorldTxn broken1 = reader.read(STARTUP_DATABASES.resolve("Broken1.db")).world();
    try (WorldTxn snapshot = broken1.begin()) {
      assertEquals(4, snapshot.objectCount());
      WorldObject system = snapshot.object(0).orElseThrow();
      assertEquals(-1, system.location());
      assertEquals(List.of(1L), system.parents());
      assertEquals(List.of(), system.contents());
      assertEquals(List.of(), system.children());
    }

  }
}
