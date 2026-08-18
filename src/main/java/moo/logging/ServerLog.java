package moo.logging;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Timestamped server diagnostics with one configurable severity threshold and destination. */
public final class ServerLog implements AutoCloseable {
  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("MMM dd HH:mm:ss", Locale.ENGLISH);

  private final Writer output;
  private final System.Logger.Level threshold;
  private final Clock clock;
  private final boolean closesOutput;

  ServerLog(Writer output, System.Logger.Level threshold, Clock clock) {
    this(output, threshold, clock, false);
  }

  private ServerLog(
      Writer output, System.Logger.Level threshold, Clock clock, boolean closesOutput) {
    this.output = Objects.requireNonNull(output, "output");
    this.threshold = Objects.requireNonNull(threshold, "threshold");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.closesOutput = closesOutput;
  }

  /** Creates a log that writes through the process's current standard error stream. */
  public static ServerLog stderr(System.Logger.Level threshold) {
    Writer stderr =
        new Writer() {
          @Override
          public void write(char[] characters, int offset, int length) {
            System.err.print(new String(characters, offset, length));
          }

          @Override
          public void flush() {
            System.err.flush();
          }

          @Override
          public void close() {
            // The process owns standard error.
          }
        };
    return new ServerLog(stderr, threshold, Clock.system(ZoneId.systemDefault()));
  }

  /** Opens an append-only file log, or uses standard error when no file is configured. */
  public static ServerLog open(System.Logger.Level threshold, Optional<Path> file)
      throws IOException {
    Objects.requireNonNull(file, "file");
    if (file.isEmpty()) {
      return stderr(threshold);
    }
    Writer output =
        Files.newBufferedWriter(
            file.orElseThrow(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
    return new ServerLog(output, threshold, Clock.system(ZoneId.systemDefault()), true);
  }

  /** Writes one normal server message when INFO is enabled. */
  public void info(String message) {
    write(System.Logger.Level.INFO, message);
  }

  /** Writes one error server message when ERROR is enabled. */
  public void error(String message) {
    write(System.Logger.Level.ERROR, message);
  }

  private synchronized void write(System.Logger.Level level, String message) {
    Objects.requireNonNull(message, "message");
    if (threshold == System.Logger.Level.OFF
        || level.getSeverity() < threshold.getSeverity()) {
      return;
    }
    try {
      output.write(TIMESTAMP.format(ZonedDateTime.now(clock)));
      output.write(": ");
      output.write(message);
      output.write('\n');
      output.flush();
    } catch (IOException failure) {
      throw new UncheckedIOException("failed to write server log", failure);
    }
  }

  @Override
  public synchronized void close() throws IOException {
    if (closesOutput) {
      output.close();
    } else {
      output.flush();
    }
  }
}
