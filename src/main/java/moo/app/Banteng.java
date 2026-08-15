package moo.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import moo.persistence.LambdaMooV17Codec;
import moo.persistence.LambdaMooV17Codec.Checkpoint;
import moo.persistence.LambdaMooV4Reader;
import moo.persistence.LambdaMooV5Reader;
import moo.server.MooServer;
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

    @Nullable Path checkpointPath = checkpoint;
    if (checkpointPath == null) {
      throw new CommandLine.ParameterException(spec.commandLine(), "--checkpoint is required");
    }

    Checkpoint loaded;
    try (BufferedReader input =
        Files.newBufferedReader(databasePath, StandardCharsets.ISO_8859_1)) {
      String header = input.readLine();
      loaded =
          switch (Objects.requireNonNullElse(header, "")) {
            case "** LambdaMOO Database, Format Version 4 **" ->
                new Checkpoint(
                    new LambdaMooV4Reader().read(databasePath), List.of(), List.of());
            case "** LambdaMOO Database, Format Version 5 **" ->
                new Checkpoint(
                    new LambdaMooV5Reader().read(databasePath), List.of(), List.of());
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
            loaded.activeConnections());
        SignalRegistration signalRegistration =
            SignalRegistration.install("TERM", server::gracefulShutdown)) {
      signalRegistration.keepAlive();
      server.serve();
    }
    return CommandLine.ExitCode.OK;
  }

  /** Process entry point. */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new Banteng()).execute(args);
    System.exit(exitCode);
  }

  private static final class SignalRegistration implements AutoCloseable {
    private final Method handle;
    private final Object signal;
    private final Object previous;
    @SuppressWarnings("unused")
    private final Object installed;

    private SignalRegistration(Method handle, Object signal, Object previous, Object installed) {
      this.handle = handle;
      this.signal = signal;
      this.previous = previous;
      this.installed = installed;
    }

    static SignalRegistration install(String name, Runnable action) throws ReflectiveOperationException {
      Class<?> signalType = Class.forName("sun.misc.Signal");
      Class<?> handlerType = Class.forName("sun.misc.SignalHandler");
      Object signal = signalType.getConstructor(String.class).newInstance(name);
      Object handler =
          Proxy.newProxyInstance(
              ClassLoader.getSystemClassLoader(),
              new Class<?>[] {handlerType},
              (proxy, method, arguments) -> {
                if (method.getName().equals("handle")) {
                  Thread.ofPlatform().name("banteng-sigterm").start(action);
                }
                return null;
              });
      Method handle = signalType.getMethod("handle", signalType, handlerType);
      Object previous = handle.invoke(null, signal, handler);
      return new SignalRegistration(handle, signal, previous, handler);
    }

    @Override
    public void close() throws ReflectiveOperationException {
      handle.invoke(null, signal, previous);
    }

    void keepAlive() {
      // Referenced by the serving scope so javac verifies the registration remains live.
    }
  }
}
