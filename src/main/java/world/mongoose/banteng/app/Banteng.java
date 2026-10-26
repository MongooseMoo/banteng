package world.mongoose.banteng.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import world.mongoose.banteng.logging.ServerLog;
import world.mongoose.banteng.persistence.LambdaMooV17Codec;
import world.mongoose.banteng.persistence.LambdaMooV17Codec.Checkpoint;
import world.mongoose.banteng.persistence.LambdaMooV4Reader;
import world.mongoose.banteng.persistence.LambdaMooV5Reader;
import world.mongoose.banteng.server.MooServer;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.ValueSemantics;
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
  private @Nullable Path checkpoint;

  @Option(names = "--listen-address", defaultValue = "127.0.0.1", description = "Listener address")
  private String listenAddress = "127.0.0.1";

  @Option(names = "--port", defaultValue = "7777", description = "Listener port")
  private int port = 7777;

  @Option(names = "--log-level", defaultValue = "INFO", description = "System.Logger level")
  private System.Logger.Level logLevel = System.Logger.Level.INFO;

  @Option(names = "--log-file", paramLabel = "PATH", description = "Append server log to a file")
  private @Nullable Path logFile;

  @Option(
      names = "--promote-numbers",
      arity = "1",
      defaultValue = "false",
      description = "Promote integers for mixed numeric operations and float math")
  private boolean promoteNumbers;

  @Spec private @Nullable CommandSpec commandSpec;

  /** Loads the configured database and blocks while serving connections. */
  @Override
  @SuppressWarnings("try")
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

    @Nullable Path checkpointPath = checkpoint;
    if (checkpointPath == null) {
      throw new CommandLine.ParameterException(spec.commandLine(), "--checkpoint is required");
    }

    try (ServerLog serverLog = ServerLog.open(logLevel, Optional.ofNullable(logFile))) {
      Checkpoint loaded;
      try (BufferedReader input =
          Files.newBufferedReader(databasePath, StringValue.charset())) {
        String header = input.readLine();
        loaded =
            switch (Objects.requireNonNullElse(header, "")) {
              case "** LambdaMOO Database, Format Version 4 **" ->
                  new Checkpoint(
                      new LambdaMooV4Reader().read(databasePath), List.of(), List.of());
              case "** LambdaMOO Database, Format Version 5 **" ->
                  new Checkpoint(
                      new LambdaMooV5Reader(serverLog).read(databasePath), List.of(), List.of());
              case "** LambdaMOO Database, Format Version 17 **" ->
                  new LambdaMooV17Codec().read(databasePath);
              default -> throw new IOException("unsupported database header: " + header);
            };
      }
      try (MooServer server =
              new MooServer(
                  listenAddress,
                  port,
                  loaded.world(),
                  databasePath,
                  checkpointPath,
                  loaded.tasks(),
                  loaded.activeConnections(),
                  serverLog,
                  valueSemantics())) {
        try (ProcessSignals signals = new ProcessSignals(server::handleSignal)) {
          server.serve();
        }
      }
    }
    return CommandLine.ExitCode.OK;
  }

  /** Process entry point. */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new Banteng()).execute(args);
    System.exit(exitCode);
  }

  ValueSemantics valueSemantics() {
    return new ValueSemantics(promoteNumbers);
  }

}
