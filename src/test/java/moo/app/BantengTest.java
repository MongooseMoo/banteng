package moo.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class BantengTest {
  @Test
  void reportsVersionWithoutDatabase() {
    StringWriter output = new StringWriter();
    CommandLine commandLine = new CommandLine(new Banteng());
    commandLine.setOut(new PrintWriter(output));

    assertEquals(CommandLine.ExitCode.OK, commandLine.execute("--version"));
    assertEquals("banteng 0.1.0-SNAPSHOT", output.toString().strip());
  }

  @Test
  void rejectsInvalidPort() {
    StringWriter errors = new StringWriter();
    CommandLine commandLine = new CommandLine(new Banteng());
    commandLine.setErr(new PrintWriter(errors));

    assertEquals(CommandLine.ExitCode.USAGE, commandLine.execute("--port", "0"));
    assertTrue(errors.toString().contains("--port must be between 1 and 65535"));
  }

  @Test
  void requiresDatabaseForAnActualRun() {
    StringWriter errors = new StringWriter();
    CommandLine commandLine = new CommandLine(new Banteng());
    commandLine.setErr(new PrintWriter(errors));

    assertEquals(CommandLine.ExitCode.USAGE, commandLine.execute());
    assertTrue(errors.toString().contains("--database is required"));
  }
}
