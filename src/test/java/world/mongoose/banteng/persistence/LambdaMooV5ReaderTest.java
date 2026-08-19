package world.mongoose.banteng.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldTxn;
import org.junit.jupiter.api.Test;

final class LambdaMooV5ReaderTest {
  private static final Path STARTUP_DATABASES =
      Path.of(
          "..", "moo-conformance-tests", "src", "moo_conformance", "_db", "startup");

  @Test
  void repairsEveryStockV5TopologyFixtureIntoAValidatedWorld() throws IOException {
    LambdaMooV5Reader reader = new LambdaMooV5Reader();
    for (int fixture = 1; fixture <= 5; fixture++) {
      WorldTxn world = reader.read(STARTUP_DATABASES.resolve("Broken" + fixture + ".db"));
      try (WorldTxn snapshot = world.begin()) {
        assertEquals(fixture == 2 ? 5 : 4, snapshot.objectCount());
      }
    }

    WorldTxn broken1 = reader.read(STARTUP_DATABASES.resolve("Broken1.db"));
    try (WorldTxn snapshot = broken1.begin()) {
      WorldObject system = snapshot.object(0).orElseThrow();
      assertEquals(-1, system.location());
      assertEquals(List.of(1L), system.parents());
      assertEquals(List.of(), system.contents());
      assertEquals(List.of(), system.children());
    }

    WorldTxn broken5 = reader.read(STARTUP_DATABASES.resolve("Broken5.db"));
    try (WorldTxn snapshot = broken5.begin()) {
      assertEquals(List.of(), snapshot.object(1).orElseThrow().children());
      assertEquals(List.of(), snapshot.object(2).orElseThrow().contents());
    }
  }
}
