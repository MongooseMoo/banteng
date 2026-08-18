package moo.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ServerLogTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-18T19:20:21Z"), ZoneOffset.UTC);

  @Test
  void timestampsMessagesWithoutChangingTheirPayloads() {
    StringWriter output = new StringWriter();
    ServerLog log = new ServerLog(output, System.Logger.Level.INFO, CLOCK);

    log.info("CHECKPOINTING on banteng.db");
    log.error("PANIC: unable to checkpoint");

    assertEquals(
        "Aug 18 19:20:21: CHECKPOINTING on banteng.db\n"
            + "Aug 18 19:20:21: PANIC: unable to checkpoint\n",
        output.toString());
  }

  @Test
  void filtersMessagesBelowTheConfiguredLevel() {
    StringWriter output = new StringWriter();
    ServerLog log = new ServerLog(output, System.Logger.Level.ERROR, CLOCK);

    log.info("VALIDATE: Phase 1: Check for invalid objects ...");
    log.error("PANIC: unable to checkpoint");

    assertEquals("Aug 18 19:20:21: PANIC: unable to checkpoint\n", output.toString());
  }

  @Test
  void fileDestinationAppendsInsteadOfReplacing(@TempDir Path directory) throws Exception {
    Path file = directory.resolve("banteng.log");
    Files.writeString(file, "existing line\n");

    try (ServerLog log = ServerLog.open(System.Logger.Level.INFO, Optional.of(file))) {
      log.info("> hello from the world");
    }

    List<String> lines = Files.readAllLines(file);
    assertEquals("existing line", lines.get(0));
    assertEquals(
        "> hello from the world",
        lines.get(1).replaceFirst("^[A-Z][a-z]{2} \\d{2} \\d{2}:\\d{2}:\\d{2}: ", ""));
  }
}
