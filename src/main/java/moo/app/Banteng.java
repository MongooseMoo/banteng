package moo.app;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Banteng command-line entry point. MOO semantics are intentionally not implemented here. */
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

  @Spec private @Nullable CommandSpec commandSpec;

  /** Runs the non-semantic bootstrap command. */
  @Override
  public Integer call() {
    CommandSpec spec = Objects.requireNonNull(commandSpec, "picocli command spec");
    if (port < 1 || port > 65_535) {
      throw new CommandLine.ParameterException(
          spec.commandLine(), "--port must be between 1 and 65535");
    }

    @Nullable Path checkpointPath = checkpoint != null ? checkpoint : database;
    spec.commandLine()
        .getOut()
        .printf(
            "banteng bootstrap: database=%s checkpoint=%s listen=%s:%d log=%s%n",
            database, checkpointPath, listenAddress, port, logLevel);
    return CommandLine.ExitCode.OK;
  }

  /** Process entry point. */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new Banteng()).execute(args);
    System.exit(exitCode);
  }
}
