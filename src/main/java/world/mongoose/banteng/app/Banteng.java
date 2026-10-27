package world.mongoose.banteng.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.Callable;
import world.mongoose.banteng.logging.ServerLog;
import world.mongoose.banteng.persistence.LambdaMooV17Codec;
import world.mongoose.banteng.persistence.DbCodec;
import world.mongoose.banteng.world.Checkpoint;
import world.mongoose.banteng.runtime.MooRuntime;
import world.mongoose.banteng.runtime.PublicationScheduler;
import world.mongoose.banteng.runtime.TaskRegistry;
import world.mongoose.banteng.server.ConnectionRegistry;
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinHosts;
import world.mongoose.banteng.vm.MooVm;
import world.mongoose.banteng.persistence.LambdaMooV4Reader;
import world.mongoose.banteng.persistence.LambdaMooV5Reader;
import world.mongoose.banteng.server.MooServer;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ListValue;
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

  private @Nullable String startLine;
  private @Nullable Path startScript;
  private boolean scriptFileFirst;

  @Option(names = "--start-line", arity = "1", paramLabel = "LINE", description = "Pass a line to #0:do_start_script")
  void startLine(String[] lines) {
    if (startScript == null) scriptFileFirst = false;
    startLine = lines[lines.length - 1];
  }

  @Option(names = "--start-script", arity = "1", paramLabel = "PATH", description = "Pass file lines to #0:do_start_script")
  void startScript(Path[] paths) {
    if (startLine == null) scriptFileFirst = true;
    startScript = paths[paths.length - 1];
  }

  @Spec private @Nullable CommandSpec commandSpec;

  private @Nullable MooRuntime runtime;

  private MooRuntime runtime() {
    return Objects.requireNonNull(runtime, "runtime has been assembled");
  }

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

    try (ServerLog serverLog = ServerLog.open(logLevel, Optional.ofNullable(logFile))) {
      DbCodec outputCodec = new LambdaMooV17Codec();
      DbCodec inputCodec;
      try (BufferedReader input =
          Files.newBufferedReader(databasePath, StringValue.charset())) {
        String header = input.readLine();
        inputCodec =
            switch (Objects.requireNonNullElse(header, "")) {
              case "** LambdaMOO Database, Format Version 4 **" ->
                  new LambdaMooV4Reader(outputCodec);
              case "** LambdaMOO Database, Format Version 5 **" ->
                  new LambdaMooV5Reader(serverLog, outputCodec);
              case "** LambdaMOO Database, Format Version 17 **" ->
                  outputCodec;
              default -> throw new IOException("unsupported database header: " + header);
            };
      }
      Checkpoint loaded = inputCodec.read(databasePath);
      ConnectionRegistry connections = new ConnectionRegistry();
      TaskRegistry tasks = new TaskRegistry();
      ValueSemantics semantics = valueSemantics();
      MooVm vm = new MooVm(semantics);
      try (ProcessSignals signals = new ProcessSignals();
          MooServer server =
              new MooServer(listenAddress, port, this::runtime);
          PublicationScheduler scheduler = new PublicationScheduler(
              loaded.world(), this::runtime,
              Math.max(2, Runtime.getRuntime().availableProcessors()), tasks)) {
        BuiltinCatalog builtins = new BuiltinCatalog(server,
            BuiltinHosts.builder().valueSemantics(semantics).serverLog(serverLog).build());
        runtime = new MooRuntime(loaded.world(), semantics, connections, System::nanoTime,
            MooRuntime::resolveCanonicalConnectionName, Optional.of(server),
            Optional.of(databasePath), Optional.of(checkpointPath), loaded.activeConnections(),
            serverLog, vm, tasks, scheduler, builtins, outputCodec);
        scheduler.restoreTasks(loaded.tasks());
        for (ListValue script : startupScripts()) runtime().startScript(script);
        server.serve(() -> signals.dispatch(server::handleSignal));
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

  List<ListValue> startupScripts() throws IOException {
    List<ListValue> scripts = new ArrayList<>();
    String line = startLine;
    if (line != null) scripts.add(new ListValue(List.of(StringValue.of(cleanStartupLine(line)))));
    Path path = startScript;
    if (path != null) {
      String content = Files.readString(path, StringValue.charset());
      List<String> lines = content.isEmpty() ? List.of() : Arrays.asList(content.split("\n", -1));
      if (content.endsWith("\n")) lines = lines.subList(0, lines.size() - 1);
      ListValue file = new ListValue(lines.stream().map(Banteng::cleanStartupLine).map(StringValue::of).map(MooValue.class::cast).toList());
      scripts.add(scriptFileFirst ? 0 : scripts.size(), file);
    }
    return scripts;
  }

  private static String cleanStartupLine(String line) {
    StringBuilder cleaned = new StringBuilder(line.length());
    for (int index = 0; index < line.length(); index++) {
      char character = line.charAt(index);
      if (character >= ' ' && character <= '~') cleaned.append(character);
    }
    return cleaned.toString();
  }

}
