package moo.app;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

final class BantengExecutableSmokeTest {
  @Test
  void installedExecutableReportsVersionAndShutsDownWithoutDatabase()
      throws IOException, InterruptedException {
    String executable =
        requireNonNull(
            System.getProperty("banteng.executable"),
            "Gradle must supply the installed executable");
    Process process = new ProcessBuilder(executable, "--version").redirectErrorStream(true).start();

    try {
      assertTrue(process.waitFor(10, SECONDS), "installed executable did not shut down");
      String output = new String(process.getInputStream().readAllBytes(), UTF_8).strip();
      assertEquals("banteng 0.1.0-SNAPSHOT", output);
      assertEquals(0, process.exitValue());
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }
}
