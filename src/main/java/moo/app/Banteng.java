package moo.app;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import moo.persistence.LambdaMooV4Reader;
import moo.server.MooServer;
import moo.world.WorldTxn;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Banteng command-line entry point and concrete server composition root. */
@Command(
    name = "banteng",
    mixinStandardHelpOptions = true,
    version = "banteng 0.1.0-SNAPSHOT",
    description = "Java MOO server")
public final class Banteng implements Callable<Integer> {
  @Option(names = "--database", paramLabel = "PATH", description = "Input MOO database")
  private @Nullable Path database;

  @Option(names = "--checkpoint", paramLabel = "PATH", description = "Checkpoint output path")
  @SuppressWarnings(
      "UnusedVariable") // Accepted now; checkpoint writing is deferred from this slice.
  private @Nullable Path checkpoint;

  @Option(names = "--listen-address", defaultValue = "127.0.0.1", description = "Listener address")
  private String listenAddress = "127.0.0.1";

  @Option(names = "--port", defaultValue = "7777", description = "Listener port")
  private int port = 7777;

  @Option(names = "--log-level", defaultValue = "INFO", description = "System.Logger level")
  @SuppressWarnings(
      "UnusedVariable") // Retained CLI surface; server logging is not part of this slice.
  private System.Logger.Level logLevel = System.Logger.Level.INFO;

  @Option(
      names = "--promote-numbers",
      arity = "1",
      defaultValue = "false",
      description = "Enable mixed integer/float numeric equality")
  @SuppressWarnings("UnusedVariable") // Profile-selected configuration for the Phase 3 value slice.
  private boolean promoteNumbers;

  @Spec private @Nullable CommandSpec commandSpec;

  /** Loads the configured database and blocks while serving connections. */
  @Override
  public Integer call() throws Exception {
    CommandSpec spec = Objects.requireNonNull(commandSpec, "picocli command spec");
    if (port < 1 || port > 65_535) {
      throw new CommandLine.ParameterException(
          spec.commandLine(), "--port must be between 1 and 65535");
    }

    @Nullable Path databasePath = database;
    if (databasePath == null) {
      throw new CommandLine.ParameterException(spec.commandLine(), "--database is required");
    }

    WorldTxn world = new LambdaMooV4Reader().read(databasePath);
    try (MooServer server = new MooServer(listenAddress, port, world)) {
      server.serve();
    }
    return CommandLine.ExitCode.OK;
  }

  /** Process entry point. */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new Banteng()).execute(args);
    System.exit(exitCode);
  }
}
