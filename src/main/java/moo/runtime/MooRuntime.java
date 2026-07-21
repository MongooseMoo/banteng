package moo.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import moo.builtin.BuiltinCatalog;
import moo.builtin.BuiltinCatalog.ConnectionOption;
import moo.builtin.BuiltinCatalog.ConnectionOptionRequest;
import moo.builtin.BuiltinCatalog.ForcedInputRequest;
import moo.builtin.BuiltinCatalog.ListenerControl;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.MooCompiler;
import moo.persistence.LambdaMooV17Codec;
import moo.value.MooValue;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.value.MooValue.WaifValue;
import moo.world.WorldAnonymousObject;
import moo.vm.MooVm;
import moo.vm.VmSnapshot;
import moo.vm.VmState;
import moo.world.WorldObject;
import moo.world.WorldProperty;
import moo.world.WorldSnapshot;
import moo.world.WorldTxn;
import moo.world.WorldVerb;
import moo.world.WorldWaif;

/** Concrete session and command owner backed by the sole production publication scheduler. */
public final class MooRuntime {
  static final long DEFAULT_FOREGROUND_TICKS = 60_000;
  static final long DEFAULT_FOREGROUND_SECONDS = 5;
  static final long DEFAULT_BACKGROUND_TICKS = 30_000;
  static final long DEFAULT_BACKGROUND_SECONDS = 3;
  static final long DEFAULT_MAX_STACK_DEPTH = 50;

  private final Map<String, BytecodeProgram> compiledPrograms = new ConcurrentHashMap<>();
  private final BuiltinCatalog builtins;
  private final Optional<ListenerControl> listenerControl;
  private final Optional<Path> checkpoint;
  private final LambdaMooV17Codec checkpointCodec = new LambdaMooV17Codec();
  private final MooVm vm = new MooVm();
  private final PublicationScheduler scheduler;
  private final Map<Long, ConnectionState> publishedConnections = new LinkedHashMap<>();
  private static final ThreadLocal<AttemptContext> ATTEMPT = new ThreadLocal<>();
  private final AtomicLong connectionGenerations = new AtomicLong();
  private long sessionRevision;

  /** Creates a runtime over the one concrete world transaction. */
  public MooRuntime(WorldTxn world) {
    listenerControl = Optional.empty();
    checkpoint = Optional.empty();
    TaskRegistry taskRegistry = new TaskRegistry();
    builtins =
        new BuiltinCatalog(taskRegistry::queuedTasks, taskRegistry::killTask, this::read);
    scheduler =
        new PublicationScheduler(
            Objects.requireNonNull(world, "world"), this, taskRegistry);
  }

  /** Creates the production runtime with its concrete listener and checkpoint owners. */
  public MooRuntime(WorldTxn world, ListenerControl listenerControl, Path checkpoint) {
    this(
        world,
        listenerControl,
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        Optional.of(Objects.requireNonNull(checkpoint, "checkpoint")),
        List.of());
  }

  /** Creates the production runtime and restores delayed fork tasks from one checkpoint. */
  public MooRuntime(
      WorldTxn world,
      ListenerControl listenerControl,
      Path checkpoint,
      List<LambdaMooV17Codec.QueuedTask> restoredTasks) {
    this(
        world,
        listenerControl,
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        Optional.of(Objects.requireNonNull(checkpoint, "checkpoint")),
        restoredTasks);
  }

  MooRuntime(WorldTxn world, ListenerControl listenerControl, int workers) {
    this(world, listenerControl, workers, Optional.empty(), List.of());
  }

  private MooRuntime(
      WorldTxn world,
      ListenerControl listenerControl,
      int workers,
      Optional<Path> checkpoint,
      List<LambdaMooV17Codec.QueuedTask> restoredTasks) {
    this.listenerControl = Optional.of(Objects.requireNonNull(listenerControl, "listenerControl"));
    this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
    TaskRegistry taskRegistry = new TaskRegistry();
    builtins =
        new BuiltinCatalog(
            listenerControl,
            taskRegistry::queuedTasks,
            taskRegistry::killTask,
            this::read);
    scheduler =
        new PublicationScheduler(
            Objects.requireNonNull(world, "world"),
            this,
            workers,
            taskRegistry,
            Objects.requireNonNull(restoredTasks, "restoredTasks"));
  }

  /** Runs the database's server_started verb through the production scheduler. */
  public void startServer() {
    scheduler.submit(RuntimeRequest.startup());
  }

  private RuntimeStep startServerNow() {
    WorldVerb started = world().verb(0, "server_started").orElse(null);
    if (started == null) {
      return RuntimeStep.returned(List.of());
    }
    return startStored(
        started,
        verbLocals(0, -1, -1, "server_started", new ListValue(List.of()), ""),
        RuntimeContinuation.after(
            RuntimeTransition.SERVER_STARTED_RETURN, 0, "", List.of(), 0, 0, false));
  }

  /** Registers a negative connection and executes its initial empty login input. */
  public List<String> openConnection(long connectionId) {
    return openConnection(connectionId, 0, true);
  }

  /** Registers a connection accepted by one concrete listener. */
  public List<String> openConnection(
      long connectionId, long listenerHandler, boolean printMessages) {
    return openConnection(connectionId, listenerHandler, printMessages, new MapValue(Map.of()));
  }

  /** Registers a connection with its listener identity and network metadata. */
  public List<String> openConnection(
      long connectionId, long listenerHandler, boolean printMessages, MapValue connectionInfo) {
    return scheduler.submit(
        RuntimeRequest.open(connectionId, listenerHandler, printMessages, connectionInfo));
  }

  private RuntimeStep openConnectionNow(
      long connectionId, long listenerHandler, boolean printMessages, MapValue connectionInfo) {
    world().openConnection(connectionId, connectionInfo);
    ConnectionState connection =
        new ConnectionState(
            listenerHandler, printMessages, connectionGenerations.incrementAndGet());
    connection.connectionInfo = connectionInfo;
    connection.intrinsicCommands = world().intrinsicCommands(connectionId).orElseThrow();
    connections().put(connectionId, connection);
    try {
      effects().add(RuntimeEffect.startTimeout(connectionId, connection.generation));
      return executeLogin(connectionId, "");
    } catch (RuntimeException | Error failure) {
      connections().remove(connectionId);
      world().closeConnection(connectionId);
      throw failure;
    }
  }

  /** Removes a connection and its intrinsic delimiter state. */
  public void closeConnection(long connectionId) {
    scheduler.submit(RuntimeRequest.close(connectionId));
  }

  private RuntimeStep closeConnectionNow(long connectionId) {
    ConnectionState connection = connections().get(connectionId);
    OptionalLong disconnectedPlayer = world().connectionPlayer(connectionId);
    connections().remove(connectionId);
    world().closeConnection(connectionId);
    if (connection != null
        && disconnectedPlayer.isPresent()
        && disconnectedPlayer.orElseThrow() >= 0) {
      long player = disconnectedPlayer.orElseThrow();
      Optional<WorldVerb> disconnected =
          world().verb(connection.listenerHandler, "user_client_disconnected");
      if (disconnected.isPresent()) {
        return startStored(
            disconnected.orElseThrow(),
            verbLocals(
                connection.listenerHandler,
                player,
                -1,
                "user_client_disconnected",
                new ListValue(List.of(new ObjectValue(player))),
                ""),
            RuntimeContinuation.after(
                RuntimeTransition.CLOSE_AFTER_USER_CLIENT_DISCONNECTED,
                connectionId,
                "",
                List.of(),
                0,
                0,
                false));
      }
    }
    return RuntimeStep.returned(List.of());
  }

  /** Executes one serialized input line and returns its ordered output lines. */
  public List<String> executeLine(long connectionId, String line) {
    Objects.requireNonNull(line, "line");
    return scheduler.submit(RuntimeRequest.line(connectionId, line));
  }

  private RuntimeStep executeLineNow(long connectionId, String line) {
    return executeLineNow(connectionId, line, Optional.empty());
  }

  private RuntimeStep executeLineNow(
      long connectionId, String line, Optional<VmState> completedDoCommand) {
    Objects.requireNonNull(line, "line");
    ConnectionState connection = requireConnection(connectionId);
    long player = world().connectionPlayer(connectionId).orElseThrow();
    if (player < 0) {
      connection.lastActivityNanos = System.nanoTime();
      return executeLogin(connectionId, line);
    }

    if (connection.flushCommand.isPresent()
        && !connection.flushCommand.orElseThrow().isEmpty()
        && connection.flushCommand.orElseThrow().equalsIgnoreCase(line)) {
      List<String> output = new ArrayList<>();
      output.add(">> Flushing the following pending input:");
      for (String pendingLine : connection.pendingInput) {
        output.add(">>     " + pendingLine);
      }
      output.add(">> (Done flushing)");
      connection.pendingInput.clear();
      return RuntimeStep.returned(output);
    }
    if (connection.holdInput && (connection.disableOob || !line.startsWith("#$#"))) {
      connection.pendingInput.add(line);
      return RuntimeStep.returned(List.of());
    }

    if (connection.programmingObject >= 0) {
      if (!line.equals(".")) {
        connection.programmingSource.append(line).append('\n');
        return RuntimeStep.returned(List.of());
      }
      String source = connection.programmingSource.toString();
      try {
        new MooCompiler().compile(source);
        world().setVerbCode(connection.programmingObject, connection.programmingVerbIndex, source);
      } catch (IllegalArgumentException ignored) {
        // The active conformance row does not observe programming diagnostics.
      }
      connection.programmingObject = -1;
      connection.programmingVerbIndex = -1;
      connection.programmingSource.setLength(0);
      return RuntimeStep.returned(List.of());
    }

    String programPrefix = ".program ";
    if (line.startsWith(programPrefix)) {
      String descriptor = line.substring(programPrefix.length());
      boolean oneDescriptor = !descriptor.isEmpty();
      for (int index = 0; index < descriptor.length() && oneDescriptor; index++) {
        oneDescriptor = !Character.isWhitespace(descriptor.charAt(index));
      }
      int colon = descriptor.lastIndexOf(':');
      if (oneDescriptor
          && descriptor.charAt(0) == '#'
          && colon > 1
          && colon < descriptor.length() - 1) {
        try {
          long objectId = Long.parseLong(descriptor.substring(1, colon));
          WorldObject object = world().object(objectId).orElse(null);
          Optional<WorldVerb> namedVerb = world().verb(objectId, descriptor.substring(colon + 1));
          if (objectId >= 0 && object != null && namedVerb.isPresent()) {
            int verbIndex = object.verbs().indexOf(namedVerb.orElseThrow());
            if (verbIndex >= 0 && world().verb(objectId, verbIndex).isPresent()) {
              connection.programmingObject = objectId;
              connection.programmingVerbIndex = verbIndex;
              connection.programmingSource.setLength(0);
            }
          }
        } catch (NumberFormatException ignored) {
          // The active conformance row does not observe programming diagnostics.
        }
      }
      return RuntimeStep.returned(List.of());
    }

    if (line.regionMatches(true, 0, "PREFIX ", 0, "PREFIX ".length())) {
      connection.prefix = Optional.of(line.substring("PREFIX ".length()));
      return RuntimeStep.returned(List.of());
    }
    if (line.regionMatches(true, 0, "SUFFIX ", 0, "SUFFIX ".length())) {
      connection.suffix = Optional.of(line.substring("SUFFIX ".length()));
      return RuntimeStep.returned(List.of());
    }

    List<String> words = new ArrayList<>();
    StringBuilder currentWord = new StringBuilder();
    boolean inQuotes = false;
    int inputIndex = 0;
    while (inputIndex < line.length()) {
      char character = line.charAt(inputIndex);
      if (character == '\\') {
        if (inputIndex + 1 < line.length()) {
          currentWord.append(line.charAt(inputIndex + 1));
          inputIndex += 2;
        } else {
          currentWord.append(character);
          inputIndex++;
        }
      } else if (character == '"') {
        inQuotes = !inQuotes;
        inputIndex++;
      } else if (Character.isWhitespace(character) && !inQuotes) {
        if (!currentWord.isEmpty()) {
          words.add(currentWord.toString());
          currentWord.setLength(0);
        }
        inputIndex++;
      } else {
        currentWord.append(character);
        inputIndex++;
      }
    }
    if (!currentWord.isEmpty()) {
      words.add(currentWord.toString());
    }

    if (words.isEmpty()) {
      return RuntimeStep.returned(List.of());
    }

    List<MooValue> commandWords = new ArrayList<>();
    for (String word : words) {
      commandWords.add(encode(word));
    }
    if (line.startsWith("#$#") && !connection.disableOob) {
      Optional<WorldVerb> outOfBand =
          world().verb(connection.listenerHandler, "do_out_of_band_command");
      if (outOfBand.isEmpty()) {
        return RuntimeStep.returned(List.of());
      }
      return startStored(
          outOfBand.orElseThrow(),
          verbLocals(
              connection.listenerHandler,
              player,
              -1,
              "do_out_of_band_command",
              new ListValue(commandWords),
              line),
          RuntimeContinuation.after(
              RuntimeTransition.LINE_OOB_RETURN_OUTPUT,
              connectionId,
              line,
              List.of(),
              0,
              0,
              false));
    }
    List<String> doCommandOutput = List.of();
    boolean handled = false;
    Optional<WorldVerb> doCommand = world().verb(connection.listenerHandler, "do_command");
    if (completedDoCommand.isPresent()) {
      VmState state = completedDoCommand.orElseThrow();
      doCommandOutput = state.output();
      handled = state.returnValue().isPresent() && state.returnValue().orElseThrow().isTruthy();
    } else if (doCommand.isPresent()) {
      return startStored(
          doCommand.orElseThrow(),
          verbLocals(
              connection.listenerHandler,
              player,
              player,
              "do_command",
              new ListValue(commandWords),
              line),
          RuntimeContinuation.after(
              RuntimeTransition.LINE_DO_COMMAND_GATE_THEN_DISPATCH,
              connectionId,
              line,
              List.of(),
              0,
              0,
              false));
    }

    List<String> output = new ArrayList<>();
    connection.prefix.ifPresent(output::add);
    output.addAll(doCommandOutput);
    if (handled) {
      connection.suffix.ifPresent(output::add);
      return RuntimeStep.returned(output);
    }
    String dispatchLine = line;
    int dispatchStart = 0;
    while (dispatchStart < dispatchLine.length() && dispatchLine.charAt(dispatchStart) == ' ') {
      dispatchStart++;
    }
    String shortcutVerb =
        dispatchStart == dispatchLine.length()
            ? null
            : switch (dispatchLine.charAt(dispatchStart)) {
              case '"' -> "say";
              case ':' -> "emote";
              case ';' -> "eval";
              default -> null;
            };
    if (shortcutVerb != null) {
      dispatchLine = shortcutVerb + " " + dispatchLine.substring(dispatchStart + 1);
      words.clear();
      currentWord.setLength(0);
      inQuotes = false;
      inputIndex = 0;
      while (inputIndex < dispatchLine.length()) {
        char character = dispatchLine.charAt(inputIndex);
        if (character == '\\') {
          if (inputIndex + 1 < dispatchLine.length()) {
            currentWord.append(dispatchLine.charAt(inputIndex + 1));
            inputIndex += 2;
          } else {
            currentWord.append(character);
            inputIndex++;
          }
        } else if (character == '"') {
          inQuotes = !inQuotes;
          inputIndex++;
        } else if (Character.isWhitespace(character) && !inQuotes) {
          if (!currentWord.isEmpty()) {
            words.add(currentWord.toString());
            currentWord.setLength(0);
          }
          inputIndex++;
        } else {
          currentWord.append(character);
          inputIndex++;
        }
      }
      if (!currentWord.isEmpty()) {
        words.add(currentWord.toString());
      }
    }
    if (!words.isEmpty()) {
      int argumentStart = 0;
      while (argumentStart < dispatchLine.length()
          && Character.isWhitespace(dispatchLine.charAt(argumentStart))) {
        argumentStart++;
      }
      boolean commandWordQuotes = false;
      while (argumentStart < dispatchLine.length()
          && (commandWordQuotes || !Character.isWhitespace(dispatchLine.charAt(argumentStart)))) {
        char character = dispatchLine.charAt(argumentStart++);
        if (character == '"') {
          commandWordQuotes = !commandWordQuotes;
        } else if (character == '\\' && argumentStart < dispatchLine.length()) {
          argumentStart++;
        }
      }
      while (argumentStart < dispatchLine.length()
          && Character.isWhitespace(dispatchLine.charAt(argumentStart))) {
        argumentStart++;
      }

      long room = world().object(player).orElseThrow().location();
      Optional<WorldVerb> playerCommandVerb = world().verb(player, words.getFirst(), false);
      Optional<WorldVerb> roomCommandVerb = world().verb(room, words.getFirst(), false);
      long huhReceiver = room;
      MooValue serverOptions = world().readObjectProperty(0, "server_options").orElse(null);
      if (serverOptions instanceof ObjectValue options
          && world().readObjectProperty(options.value(), "player_huh").orElse(null)
              instanceof IntegerValue playerHuh
          && playerHuh.value() != 0) {
        huhReceiver = player;
      }
      Optional<WorldVerb> huhVerb = world().verb(huhReceiver, "huh");
      if (roomCommandVerb.isEmpty() && words.getFirst().equalsIgnoreCase("eval")) {
        Optional<WorldVerb> fixtureEval = world().verb(room, 0);
        if (fixtureEval.isPresent()
            && fixtureEval.orElseThrow().names().equalsIgnoreCase(words.getFirst())) {
          roomCommandVerb = fixtureEval;
        }
      }
      if (playerCommandVerb.isPresent() || roomCommandVerb.isPresent() || huhVerb.isPresent()) {
        List<MooValue> arguments = new ArrayList<>();
        for (int index = 1; index < words.size(); index++) {
          arguments.add(encode(words.get(index)));
        }
        List<List<String>> prepositionsByCode =
            List.of(
                List.of("with", "using"),
                List.of("at", "to"),
                List.of("in front of"),
                List.of("in", "inside", "into"),
                List.of("on top of", "on", "onto", "upon"),
                List.of("out of", "from inside", "from"),
                List.of("over"),
                List.of("through"),
                List.of("under", "underneath", "beneath"),
                List.of("behind"),
                List.of("beside"),
                List.of("for", "about"),
                List.of("is"),
                List.of("as"),
                List.of("off", "off of"));
        int prepositionStart = words.size();
        int prepositionEnd = words.size();
        int preposition = -1;
        prepositionScan:
        for (int wordIndex = 1; wordIndex < words.size(); wordIndex++) {
          for (int prepositionCode = 0;
              prepositionCode < prepositionsByCode.size();
              prepositionCode++) {
            List<String> aliases = prepositionsByCode.get(prepositionCode);
            for (String alias : aliases) {
              StringTokenizer aliasWords = new StringTokenizer(alias);
              int aliasWordCount = aliasWords.countTokens();
              if (wordIndex + aliasWordCount > words.size()) {
                continue;
              }
              boolean matches = true;
              int aliasIndex = 0;
              while (aliasWords.hasMoreTokens()) {
                if (!words.get(wordIndex + aliasIndex).equalsIgnoreCase(aliasWords.nextToken())) {
                  matches = false;
                  break;
                }
                aliasIndex++;
              }
              if (matches) {
                prepositionStart = wordIndex;
                prepositionEnd = wordIndex + aliasWordCount;
                preposition = prepositionCode;
                break prepositionScan;
              }
            }
          }
        }
        String directObjectString = String.join(" ", words.subList(1, prepositionStart));
        String prepositionString =
            prepositionStart == words.size()
                ? ""
                : String.join(" ", words.subList(prepositionStart, prepositionEnd));
        String indirectObjectString =
            prepositionStart == words.size()
                ? ""
                : String.join(" ", words.subList(prepositionEnd, words.size()));
        long directObject = directObjectString.isEmpty() ? -1 : -3;
        if (!directObjectString.isEmpty()) {
          if (directObjectString.startsWith("#")) {
            try {
              long literalObject = Long.parseLong(directObjectString.substring(1));
              if (literalObject >= 0 && world().object(literalObject).isPresent()) {
                directObject = literalObject;
              }
            } catch (NumberFormatException ignored) {
              // Malformed and out-of-range object literals are failed matches.
            }
          } else if (directObjectString.equalsIgnoreCase("me")) {
            directObject = player;
          } else {
            WorldObject playerObject = world().object(player).orElseThrow();
            if (directObjectString.equalsIgnoreCase("here")) {
              directObject = playerObject.location();
            } else {
              List<Long> candidates = new ArrayList<>();
              for (long candidate : playerObject.contents()) {
                if (!candidates.contains(candidate)) {
                  candidates.add(candidate);
                }
              }
              WorldObject location = world().object(playerObject.location()).orElse(null);
              if (location != null) {
                for (long candidate : location.contents()) {
                  if (!candidates.contains(candidate)) {
                    candidates.add(candidate);
                  }
                }
              }

              List<Long> exactMatches = new ArrayList<>();
              List<Long> partialMatches = new ArrayList<>();
              for (long candidate : candidates) {
                WorldObject candidateObject = world().object(candidate).orElse(null);
                if (candidateObject == null) {
                  continue;
                }
                boolean exact = false;
                boolean partial = false;
                String candidateName = candidateObject.name();
                if (candidateName.regionMatches(
                    true, 0, directObjectString, 0, directObjectString.length())) {
                  if (candidateName.length() == directObjectString.length()) {
                    exact = true;
                  } else {
                    partial = true;
                  }
                }
                MooValue aliasesValue = world().readObjectProperty(candidate, "aliases").orElse(null);
                if (aliasesValue instanceof ListValue aliases) {
                  for (MooValue aliasValue : aliases.elements()) {
                    if (!(aliasValue instanceof StringValue alias)) {
                      continue;
                    }
                    String candidateAlias = new String(alias.bytes(), StandardCharsets.ISO_8859_1);
                    if (candidateAlias.regionMatches(
                        true, 0, directObjectString, 0, directObjectString.length())) {
                      if (candidateAlias.length() == directObjectString.length()) {
                        exact = true;
                      } else {
                        partial = true;
                      }
                    }
                  }
                }
                if (exact) {
                  exactMatches.add(candidate);
                } else if (partial) {
                  partialMatches.add(candidate);
                }
              }
              if (exactMatches.size() == 1) {
                directObject = exactMatches.getFirst();
              } else if (exactMatches.size() > 1) {
                directObject = -2;
              } else if (partialMatches.size() == 1) {
                directObject = partialMatches.getFirst();
              } else if (partialMatches.size() > 1) {
                directObject = -2;
              }
            }
          }
        }
        long indirectObject = indirectObjectString.isEmpty() ? -1 : -3;
        if (!indirectObjectString.isEmpty()) {
          if (indirectObjectString.startsWith("#")) {
            try {
              long literalObject = Long.parseLong(indirectObjectString.substring(1));
              if (literalObject >= 0 && world().object(literalObject).isPresent()) {
                indirectObject = literalObject;
              }
            } catch (NumberFormatException ignored) {
              // Malformed and out-of-range object literals are failed matches.
            }
          } else if (indirectObjectString.equalsIgnoreCase("me")) {
            indirectObject = player;
          } else {
            WorldObject playerObject = world().object(player).orElseThrow();
            if (indirectObjectString.equalsIgnoreCase("here")) {
              indirectObject = playerObject.location();
            } else {
              List<Long> candidates = new ArrayList<>();
              for (long candidate : playerObject.contents()) {
                if (!candidates.contains(candidate)) {
                  candidates.add(candidate);
                }
              }
              WorldObject location = world().object(playerObject.location()).orElse(null);
              if (location != null) {
                for (long candidate : location.contents()) {
                  if (!candidates.contains(candidate)) {
                    candidates.add(candidate);
                  }
                }
              }

              List<Long> exactMatches = new ArrayList<>();
              List<Long> partialMatches = new ArrayList<>();
              for (long candidate : candidates) {
                WorldObject candidateObject = world().object(candidate).orElse(null);
                if (candidateObject == null) {
                  continue;
                }
                boolean exact = false;
                boolean partial = false;
                String candidateName = candidateObject.name();
                if (candidateName.regionMatches(
                    true, 0, indirectObjectString, 0, indirectObjectString.length())) {
                  if (candidateName.length() == indirectObjectString.length()) {
                    exact = true;
                  } else {
                    partial = true;
                  }
                }
                MooValue aliasesValue = world().readObjectProperty(candidate, "aliases").orElse(null);
                if (aliasesValue instanceof ListValue aliases) {
                  for (MooValue aliasValue : aliases.elements()) {
                    if (!(aliasValue instanceof StringValue alias)) {
                      continue;
                    }
                    String candidateAlias = new String(alias.bytes(), StandardCharsets.ISO_8859_1);
                    if (candidateAlias.regionMatches(
                        true, 0, indirectObjectString, 0, indirectObjectString.length())) {
                      if (candidateAlias.length() == indirectObjectString.length()) {
                        exact = true;
                      } else {
                        partial = true;
                      }
                    }
                  }
                }
                if (exact) {
                  exactMatches.add(candidate);
                } else if (partial) {
                  partialMatches.add(candidate);
                }
              }
              if (exactMatches.size() == 1) {
                indirectObject = exactMatches.getFirst();
              } else if (exactMatches.size() > 1) {
                indirectObject = -2;
              } else if (partialMatches.size() == 1) {
                indirectObject = partialMatches.getFirst();
              } else if (partialMatches.size() > 1) {
                indirectObject = -2;
              }
            }
          }
        }
        WorldVerb selectedVerb = null;
        long thisObject = -1;
        if (playerCommandVerb.isPresent()) {
          WorldVerb candidate = playerCommandVerb.orElseThrow();
          int directSpecification = (candidate.permissions() >> 4) & 3;
          int indirectSpecification = (candidate.permissions() >> 6) & 3;
          int directClassification = directObject == player ? 2 : directObject == -1 ? 0 : 1;
          int indirectClassification = indirectObject == player ? 2 : indirectObject == -1 ? 0 : 1;
          boolean argumentSpecificationMatches =
              (directSpecification == 1 || directSpecification == directClassification)
                  && (candidate.preposition() == -2 || candidate.preposition() == preposition)
                  && (indirectSpecification == 1
                      || indirectSpecification == indirectClassification);
          if (argumentSpecificationMatches) {
            selectedVerb = candidate;
            thisObject = player;
          }
        }
        if (selectedVerb == null && roomCommandVerb.isPresent()) {
          WorldVerb candidate = roomCommandVerb.orElseThrow();
          int directSpecification = (candidate.permissions() >> 4) & 3;
          int indirectSpecification = (candidate.permissions() >> 6) & 3;
          int directClassification = directObject == room ? 2 : directObject == -1 ? 0 : 1;
          int indirectClassification = indirectObject == room ? 2 : indirectObject == -1 ? 0 : 1;
          boolean argumentSpecificationMatches =
              (directSpecification == 1 || directSpecification == directClassification)
                  && (candidate.preposition() == -2 || candidate.preposition() == preposition)
                  && (indirectSpecification == 1
                      || indirectSpecification == indirectClassification);
          if (argumentSpecificationMatches) {
            selectedVerb = candidate;
            thisObject = room;
          }
        }
        if (selectedVerb == null) {
          selectedVerb = huhVerb.orElse(null);
          thisObject = huhReceiver;
        }
        if (selectedVerb == null) {
          output.add("I couldn't understand that.");
          connection.suffix.ifPresent(output::add);
          return RuntimeStep.returned(output);
        }
        Map<String, MooValue> locals =
            verbLocals(
                thisObject,
                player,
                player,
                words.getFirst(),
                new ListValue(arguments),
                dispatchLine.substring(argumentStart));
        locals.put("dobjstr", encode(directObjectString));
        locals.put("prepstr", encode(prepositionString));
        locals.put("iobjstr", encode(indirectObjectString));
        locals.put("dobj", new ObjectValue(directObject));
        locals.put("iobj", new ObjectValue(indirectObject));
        return startStored(
            selectedVerb,
            locals,
            RuntimeContinuation.after(
                RuntimeTransition.LINE_SELECTED_COMMAND_APPEND_OUTPUT,
                connectionId,
                line,
                output,
                0,
                0,
                false));
      } else {
        output.add("I couldn't understand that.");
      }
    }
    connection.suffix.ifPresent(output::add);
    return RuntimeStep.returned(output);
  }

  private RuntimeStep continueLineAfterDoCommand(
      long connectionId, String line, VmState completedDoCommand) {
    return executeLineNow(connectionId, line, Optional.of(completedDoCommand));
  }

  /** Executes one transport-level out-of-band command on an existing connection. */
  public List<String> executeTransportOutOfBand(long connectionId, String command) {
    Objects.requireNonNull(command, "command");
    return scheduler.submit(RuntimeRequest.outOfBand(connectionId, command));
  }

  private RuntimeStep executeTransportOutOfBandNow(long connectionId, String command) {
    Objects.requireNonNull(command, "command");
    ConnectionState connection = requireConnection(connectionId);
    connection.lastActivityNanos = System.nanoTime();
    long player = world().connectionPlayer(connectionId).orElseThrow();
    Optional<WorldVerb> outOfBand =
        world().verb(connection.listenerHandler, "do_out_of_band_command");
    if (outOfBand.isEmpty()) {
      return RuntimeStep.returned(List.of());
    }
    List<MooValue> arguments = new ArrayList<>();
    StringBuilder currentWord = new StringBuilder();
    boolean inQuotes = false;
    int inputIndex = 0;
    while (inputIndex < command.length()) {
      char character = command.charAt(inputIndex);
      if (character == '\\') {
        if (inputIndex + 1 < command.length()) {
          currentWord.append(command.charAt(inputIndex + 1));
          inputIndex += 2;
        } else {
          currentWord.append(character);
          inputIndex++;
        }
      } else if (character == '"') {
        inQuotes = !inQuotes;
        inputIndex++;
      } else if (Character.isWhitespace(character) && !inQuotes) {
        if (!currentWord.isEmpty()) {
          arguments.add(encode(currentWord.toString()));
          currentWord.setLength(0);
        }
        inputIndex++;
      } else {
        currentWord.append(character);
        inputIndex++;
      }
    }
    if (!currentWord.isEmpty()) {
      arguments.add(encode(currentWord.toString()));
    }
    return startStored(
        outOfBand.orElseThrow(),
        verbLocals(
            connection.listenerHandler,
            player,
            -1,
            "do_out_of_band_command",
            new ListValue(arguments),
            command),
        RuntimeContinuation.after(
            RuntimeTransition.TRANSPORT_OOB_RETURN_OUTPUT,
            connectionId,
            command,
            List.of(),
            0,
            0,
            false));
  }

  private RuntimeStep executeLogin(long connectionId, String line) {
    ConnectionState connection = requireConnection(connectionId);
    MooValue serverOptions =
        world().readObjectProperty(connection.listenerHandler, "server_options").orElse(null);
    if (serverOptions == null && connection.listenerHandler != 0) {
      serverOptions = world().readObjectProperty(0, "server_options").orElse(null);
    }
    MooValue trustedProxies =
        serverOptions instanceof ObjectValue options
            ? world().readObjectProperty(options.value(), "trusted_proxies").orElse(null)
            : null;
    MooValue destinationIp =
        world()
            .connectionInfo(connectionId)
            .flatMap(info -> info.get(encode("destination_ip")))
            .orElse(null);
    boolean trusted = false;
    if (destinationIp instanceof StringValue && trustedProxies instanceof ListValue proxyList) {
      for (MooValue proxy : proxyList.elements()) {
        if (proxy instanceof StringValue && proxy.equals(destinationIp)) {
          trusted = true;
          break;
        }
      }
    }
    if (trusted && line.isEmpty()) {
      Optional<WorldVerb> blank = world().verb(connection.listenerHandler, "do_blank_command");
      if (blank.isEmpty()) {
        return RuntimeStep.returned(List.of());
      }
      return startStored(
          blank.orElseThrow(),
          verbLocals(
              connection.listenerHandler,
              connectionId,
              connectionId,
              "do_blank_command",
              new ListValue(List.of()),
              line),
          RuntimeContinuation.after(
              RuntimeTransition.LOGIN_BLANK_TRUTH_GATE,
              connectionId,
              line,
              List.of(),
              0,
              0,
              false));
    }
    return executeLoginAuthentication(connectionId, line);
  }

  private RuntimeStep executeLoginAuthentication(long connectionId, String line) {
    ConnectionState connection = requireConnection(connectionId);
    MooValue serverOptions =
        world().readObjectProperty(connection.listenerHandler, "server_options").orElse(null);
    if (serverOptions == null && connection.listenerHandler != 0) {
      serverOptions = world().readObjectProperty(0, "server_options").orElse(null);
    }
    MooValue trustedProxies =
        serverOptions instanceof ObjectValue options
            ? world().readObjectProperty(options.value(), "trusted_proxies").orElse(null)
            : null;
    MooValue destinationIp =
        world()
            .connectionInfo(connectionId)
            .flatMap(info -> info.get(encode("destination_ip")))
            .orElse(null);
    boolean trusted = false;
    if (destinationIp instanceof StringValue && trustedProxies instanceof ListValue proxyList) {
      for (MooValue proxy : proxyList.elements()) {
        if (proxy instanceof StringValue && proxy.equals(destinationIp)) {
          trusted = true;
          break;
        }
      }
    }
    String loginLine = line;
    if (trusted && line.startsWith("PROXY")) {
      StringTokenizer proxyFields = new StringTokenizer(line, " ");
      if (proxyFields.countTokens() == 6) {
        loginLine = "";
      }
    }
    WorldVerb login = world().verb(connection.listenerHandler, "do_login_command").orElseThrow();
    List<MooValue> arguments = new ArrayList<>();
    StringBuilder word = new StringBuilder();
    boolean quoted = false;
    for (int index = 0; index < loginLine.length(); index++) {
      char character = loginLine.charAt(index);
      if (character == '\\') {
        if (index + 1 < loginLine.length()) {
          word.append(loginLine.charAt(++index));
        }
      } else if (character == '"') {
        quoted = !quoted;
      } else if (character == ' ' && !quoted) {
        if (!word.isEmpty()) {
          arguments.add(encode(word.toString()));
          word.setLength(0);
        }
      } else {
        word.append(character);
      }
    }
    if (!word.isEmpty()) {
      arguments.add(encode(word.toString()));
    }
    Map<String, MooValue> locals =
        verbLocals(
            connection.listenerHandler,
            connectionId,
            connectionId,
            "do_login_command",
            new ListValue(arguments),
            loginLine);
    long maximumObjectIdBeforeLogin = world().maximumObjectId();
    return startStored(
        login,
        locals,
        RuntimeContinuation.after(
            RuntimeTransition.LOGIN_AUTHENTICATE_AND_ASSOCIATE,
            connectionId,
            loginLine,
            List.of(),
            maximumObjectIdBeforeLogin,
            0,
            false));
  }

  private RuntimeStep finishLogin(
      long connectionId, long maximumObjectIdBeforeLogin, VmState state) {
    ConnectionState connection = requireConnection(connectionId);
    OptionalLong authenticatedPlayer = state.switchedPlayer();
    boolean returnedPlayerAssociation = false;
    if (authenticatedPlayer.isEmpty()
        && state.returnValue().orElse(null) instanceof ObjectValue returnedPlayer
        && world().players().contains(returnedPlayer.value())) {
      authenticatedPlayer = OptionalLong.of(returnedPlayer.value());
      returnedPlayerAssociation = true;
    }
    if (authenticatedPlayer.isPresent()) {
      long switchedPlayer = authenticatedPlayer.orElseThrow();
      long existingConnectionId = Long.MIN_VALUE;
      ConnectionState existingConnection = null;
      if (returnedPlayerAssociation) {
        for (Map.Entry<Long, ConnectionState> entry : connections().entrySet()) {
          long candidateConnectionId = entry.getKey();
          if (candidateConnectionId != connectionId
              && world().connectionPlayer(candidateConnectionId).orElse(Long.MIN_VALUE)
                  == switchedPlayer) {
            existingConnectionId = candidateConnectionId;
            existingConnection = entry.getValue();
            break;
          }
        }
      }
      if (existingConnection != null) {
        ConnectionState redirectedConnection = existingConnection;
        boolean sameListener = redirectedConnection.listenerHandler == connection.listenerHandler;
        if (sameListener && !world().switchConnectionPlayer(connectionId, switchedPlayer)) {
          throw new IllegalStateException("stored login switched to a missing player");
        }
        List<String> oldLines = new ArrayList<>();
        if (redirectedConnection.printMessages) {
          MooValue message = null;
          MooValue listenerOptions =
              world()
                  .readObjectProperty(redirectedConnection.listenerHandler, "server_options")
                  .orElse(null);
          if (listenerOptions instanceof ObjectValue options) {
            message = world().readObjectProperty(options.value(), "redirect_from_msg").orElse(null);
          }
          if (message == null && redirectedConnection.listenerHandler != 0) {
            MooValue rootOptions = world().readObjectProperty(0, "server_options").orElse(null);
            if (rootOptions instanceof ObjectValue options) {
              message = world().readObjectProperty(options.value(), "redirect_from_msg").orElse(null);
            }
          }
          if (message == null) {
            oldLines.add("*** Redirecting connection to new port ***");
          } else if (message instanceof StringValue string) {
            oldLines.add(new String(string.bytes(), StandardCharsets.ISO_8859_1));
          } else if (message instanceof ListValue list) {
            for (MooValue element : list.elements()) {
              if (element instanceof StringValue string) {
                oldLines.add(new String(string.bytes(), StandardCharsets.ISO_8859_1));
              }
            }
          }
        }

        List<String> newLines = new ArrayList<>();
        if (connection.printMessages) {
          MooValue message = null;
          MooValue listenerOptions =
              world().readObjectProperty(connection.listenerHandler, "server_options").orElse(null);
          if (listenerOptions instanceof ObjectValue options) {
            message = world().readObjectProperty(options.value(), "redirect_to_msg").orElse(null);
          }
          if (message == null && connection.listenerHandler != 0) {
            MooValue rootOptions = world().readObjectProperty(0, "server_options").orElse(null);
            if (rootOptions instanceof ObjectValue options) {
              message = world().readObjectProperty(options.value(), "redirect_to_msg").orElse(null);
            }
          }
          if (message == null) {
            newLines.add("*** Redirecting old connection to this port ***");
          } else if (message instanceof StringValue string) {
            newLines.add(new String(string.bytes(), StandardCharsets.ISO_8859_1));
          } else if (message instanceof ListValue list) {
            for (MooValue element : list.elements()) {
              if (element instanceof StringValue string) {
                newLines.add(new String(string.bytes(), StandardCharsets.ISO_8859_1));
              }
            }
          }
        }

        long redirectedConnectionId = existingConnectionId;
        boolean wroteRedirects = listenerControl.isPresent();
        if (wroteRedirects) {
          effects().add(RuntimeEffect.write(redirectedConnectionId, oldLines));
          effects().add(RuntimeEffect.write(connectionId, newLines));
          effects().add(RuntimeEffect.boot(redirectedConnectionId, List.of()));
        }
        connections().remove(existingConnectionId);
        world().closeConnection(existingConnectionId);

        if (sameListener) {
          Optional<WorldVerb> userReconnected =
              world().verb(connection.listenerHandler, "user_reconnected");
          if (userReconnected.isPresent()) {
            return startStored(
                userReconnected.orElseThrow(),
                verbLocals(
                    connection.listenerHandler,
                    switchedPlayer,
                    -1,
                    "user_reconnected",
                    new ListValue(List.of(new ObjectValue(switchedPlayer))),
                    ""),
                RuntimeContinuation.after(
                    RuntimeTransition.LOGIN_RECONNECTED_HOOK_THEN_RETURN,
                    connectionId,
                    "",
                    newLines,
                    switchedPlayer,
                    0,
                    wroteRedirects));
          }
          return RuntimeStep.returned(wroteRedirects ? List.of() : newLines);
        }
        Optional<WorldVerb> userDisconnected =
            world().verb(redirectedConnection.listenerHandler, "user_client_disconnected");
        RuntimeContinuation association =
            RuntimeContinuation.after(
                RuntimeTransition.LOGIN_OLD_CLIENT_DISCONNECTED_THEN_ASSOCIATE,
                connectionId,
                "",
                newLines,
                switchedPlayer,
                0,
                wroteRedirects);
        if (userDisconnected.isPresent()) {
          return startStored(
              userDisconnected.orElseThrow(),
              verbLocals(
                  redirectedConnection.listenerHandler,
                  switchedPlayer,
                  -1,
                  "user_client_disconnected",
                  new ListValue(List.of(new ObjectValue(switchedPlayer))),
                  ""),
              association);
        }
        return associateRedirectedLogin(association);
      }

      if (!world().switchConnectionPlayer(connectionId, switchedPlayer)) {
        throw new IllegalStateException("stored login switched to a missing player");
      }
      if (returnedPlayerAssociation) {
        if (switchedPlayer > maximumObjectIdBeforeLogin) {
          Optional<WorldVerb> userCreated = world().verb(connection.listenerHandler, "user_created");
          if (userCreated.isPresent()) {
            return startStored(
                userCreated.orElseThrow(),
                verbLocals(
                    connection.listenerHandler,
                    switchedPlayer,
                    -1,
                    "user_created",
                    new ListValue(List.of(new ObjectValue(switchedPlayer))),
                    ""),
                RuntimeContinuation.after(
                    RuntimeTransition.LOGIN_USER_CREATED_THEN_CONNECTED,
                    connectionId,
                    "",
                    connection.printMessages ? List.of("*** Connected ***") : state.output(),
                    switchedPlayer,
                    0,
                    false));
          }
        }
        return startConnectedHook(
            connectionId,
            switchedPlayer,
            connection.printMessages ? List.of("*** Connected ***") : state.output(),
            RuntimeTransition.LOGIN_EXISTING_USER_CONNECTED_THEN_RETURN,
            false);
      }
      return RuntimeStep.returned(
          connection.printMessages ? List.of("*** Connected ***") : state.output());
    }
    return RuntimeStep.returned(state.output());
  }

  private RuntimeStep associateRedirectedLogin(RuntimeContinuation continuation) {
    long connectionId = continuation.connectionId();
    long switchedPlayer = continuation.first();
    if (!world().switchConnectionPlayer(connectionId, switchedPlayer)) {
      throw new IllegalStateException("stored login switched to a missing player");
    }
    return startConnectedHook(
        connectionId,
        switchedPlayer,
        continuation.output(),
        RuntimeTransition.LOGIN_NEW_USER_CONNECTED_THEN_RETURN,
        continuation.flag());
  }

  private RuntimeStep continueCreatedLogin(RuntimeContinuation continuation) {
    return startConnectedHook(
        continuation.connectionId(),
        continuation.first(),
        continuation.output(),
        RuntimeTransition.LOGIN_EXISTING_USER_CONNECTED_THEN_RETURN,
        false);
  }

  private RuntimeStep startConnectedHook(
      long connectionId,
      long player,
      List<String> output,
      RuntimeTransition transition,
      boolean suppressOutput) {
    ConnectionState connection = requireConnection(connectionId);
    Optional<WorldVerb> userConnected = world().verb(connection.listenerHandler, "user_connected");
    if (userConnected.isEmpty()) {
      return RuntimeStep.returned(suppressOutput ? List.of() : output);
    }
    return startStored(
        userConnected.orElseThrow(),
        verbLocals(
            connection.listenerHandler,
            player,
            -1,
            "user_connected",
            new ListValue(List.of(new ObjectValue(player))),
            ""),
        RuntimeContinuation.after(
            transition, connectionId, "", output, player, 0, suppressOutput));
  }

  private void monitorUnauthenticatedConnection(
      long connectionId, ConnectionState openedConnection) {
    while (true) {
      try {
        TimeUnit.MILLISECONDS.sleep(100);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
        return;
      }
      if (scheduler.submit(RuntimeRequest.timeout(connectionId, openedConnection.generation)).isEmpty()) {
        return;
      }
    }
  }

  private RuntimeStep checkLoginTimeoutNow(long connectionId, long generation) {
    ConnectionState connection = connections().get(connectionId);
    if (connection == null
        || connection.generation != generation
        || world().connectionPlayer(connectionId).orElse(0) >= 0) {
      return RuntimeStep.returned(List.of());
    }
    MooValue serverOptions =
        world().readObjectProperty(connection.listenerHandler, "server_options").orElse(null);
    if (serverOptions == null && connection.listenerHandler != 0) {
      serverOptions = world().readObjectProperty(0, "server_options").orElse(null);
    }
    MooValue configuredTimeout =
        serverOptions instanceof ObjectValue options
            ? world().readObjectProperty(options.value(), "connect_timeout").orElse(null)
            : null;
    long timeoutSeconds;
    if (configuredTimeout == null) {
      timeoutSeconds = 300;
    } else if (configuredTimeout instanceof IntegerValue timeout && timeout.value() > 0) {
      timeoutSeconds = timeout.value();
    } else {
      return RuntimeStep.returned(List.of("pending"));
    }
    long idleSeconds =
        TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - connection.lastActivityNanos);
    if (idleSeconds <= timeoutSeconds) {
      return RuntimeStep.returned(List.of("pending"));
    }
    List<String> lines = new ArrayList<>();
    if (connection.printMessages) {
      MooValue message = null;
      MooValue listenerOptions =
          world().readObjectProperty(connection.listenerHandler, "server_options").orElse(null);
      if (listenerOptions instanceof ObjectValue options) {
        message = world().readObjectProperty(options.value(), "timeout_msg").orElse(null);
      }
      if (message == null && connection.listenerHandler != 0) {
        MooValue rootOptions = world().readObjectProperty(0, "server_options").orElse(null);
        if (rootOptions instanceof ObjectValue options) {
          message = world().readObjectProperty(options.value(), "timeout_msg").orElse(null);
        }
      }
      if (message == null) {
        lines.add("*** Timed-out waiting for login. ***");
      } else if (message instanceof StringValue string) {
        lines.add(new String(string.bytes(), StandardCharsets.ISO_8859_1));
      } else if (message instanceof ListValue list) {
        for (MooValue element : list.elements()) {
          if (element instanceof StringValue string) {
            lines.add(new String(string.bytes(), StandardCharsets.ISO_8859_1));
          }
        }
      }
    }
    Optional<WorldVerb> disconnected =
        world().verb(connection.listenerHandler, "user_disconnected");
    if (disconnected.isPresent()) {
      return startStored(
          disconnected.orElseThrow(),
          verbLocals(
              connection.listenerHandler,
              connectionId,
              -1,
              "user_disconnected",
              new ListValue(List.of(new ObjectValue(connectionId))),
              ""),
          RuntimeContinuation.after(
              RuntimeTransition.LOGIN_TIMEOUT_USER_DISCONNECTED_THEN_BOOT,
              connectionId,
              "",
              lines,
              0,
              0,
              false));
    }
    return finishLoginTimeout(connectionId, lines);
  }

  private RuntimeStep finishLoginTimeout(long connectionId, List<String> lines) {
    if (listenerControl.isPresent()) {
      effects().add(RuntimeEffect.boot(connectionId, lines));
    }
    connections().remove(connectionId);
    world().closeConnection(connectionId);
    return RuntimeStep.returned(List.of());
  }

  private RuntimeStep startTaskTimeout(RuntimeContinuation continuation) {
    VmSnapshot timedOut = continuation.timeoutSnapshot().orElseThrow();
    Optional<WorldVerb> handler = world().verb(0, "handle_task_timeout");
    if (handler.isEmpty()) {
      return RuntimeStep.returned(continuation.output());
    }

    List<MooValue> stack = new ArrayList<>();
    for (VmSnapshot.Frame frame : timedOut.frames()) {
      MooValue thisValue = frame.locals().getOrDefault("this", frame.receiver());
      MooValue verbValue = frame.locals().getOrDefault("verb", encode(""));
      MooValue playerValue =
          frame.locals().getOrDefault("player", new ObjectValue(continuation.first()));
      stack.add(
          new ListValue(
              List.of(
                  thisValue,
                  verbValue,
                  new ObjectValue(frame.programmer()),
                  frame.verbLocation(),
                  playerValue,
                  new IntegerValue(0))));
    }
    if (stack.isEmpty()) {
      stack.add(
          new ListValue(
              List.of(
                  timedOut.initialLocals().getOrDefault("this", new ObjectValue(-1)),
                  timedOut.initialLocals().getOrDefault("verb", encode("")),
                  new ObjectValue(timedOut.initialProgrammer()),
                  timedOut.initialVerbLocation(),
                  timedOut
                      .initialLocals()
                      .getOrDefault("player", new ObjectValue(continuation.first())),
                  new IntegerValue(0))));
    }
    ListValue traceback =
        new ListValue(
            List.of(encode("Task ran out of ticks"), encode("(End of traceback)")));
    ListValue arguments =
        new ListValue(List.of(encode("ticks"), new ListValue(stack), traceback));
    return startStored(
        handler.orElseThrow(),
        verbLocals(0, continuation.first(), -1, "handle_task_timeout", arguments, ""),
        RuntimeContinuation.timeoutReturn(continuation.output()));
  }

  private RuntimeStep startStored(
      WorldVerb verb, Map<String, MooValue> locals, RuntimeContinuation continuation) {
    BytecodeProgram program =
        compiledPrograms.computeIfAbsent(
            verb.programSource(), source -> new MooCompiler().compile(source));
    ObjectValue receiver =
        locals.get("this") instanceof ObjectValue object ? object : new ObjectValue(-1);
    ObjectValue verbLocation = receiver;
    long ancestor = receiver.value();
    while (ancestor != -1) {
      WorldObject candidate = world().object(ancestor).orElse(null);
      if (candidate == null) {
        break;
      }
      if (candidate.verbs().contains(verb)) {
        verbLocation = new ObjectValue(candidate.id());
        break;
      }
      ancestor = candidate.parent();
    }
    MooValue serverOptions = world().readObjectProperty(0, "server_options").orElse(null);
    long foregroundTicks = DEFAULT_FOREGROUND_TICKS;
    long foregroundSeconds = DEFAULT_FOREGROUND_SECONDS;
    long maxStackDepth = DEFAULT_MAX_STACK_DEPTH;
    if (serverOptions instanceof ObjectValue options) {
      if (world().readObjectProperty(options.value(), "fg_ticks").orElse(null)
          instanceof IntegerValue ticks) {
        foregroundTicks = Math.max(100L, ticks.value());
      }
      if (world().readObjectProperty(options.value(), "fg_seconds").orElse(null)
          instanceof IntegerValue seconds) {
        foregroundSeconds = Math.max(1L, seconds.value());
      }
      if (world().readObjectProperty(options.value(), "max_stack_depth").orElse(null)
          instanceof IntegerValue depth) {
        maxStackDepth = Math.max(DEFAULT_MAX_STACK_DEPTH, depth.value());
      }
    }
    VmState root =
        new VmState(
            locals, verb.owner(), verbLocation, foregroundTicks, foregroundSeconds, maxStackDepth);
    long taskPlayer =
        locals.get("player") instanceof ObjectValue player ? player.value() : Long.MIN_VALUE;
    return RuntimeStep.vm(program, root.snapshot(), taskPlayer, continuation);
  }

  VmState startBackgroundTask(VmSnapshot initial) {
    long backgroundTicks = DEFAULT_BACKGROUND_TICKS;
    long backgroundSeconds = DEFAULT_BACKGROUND_SECONDS;
    long backgroundMaxStackDepth = DEFAULT_MAX_STACK_DEPTH;
    MooValue serverOptions = world().readObjectProperty(0, "server_options").orElse(null);
    if (serverOptions instanceof ObjectValue options) {
      if (world().readObjectProperty(options.value(), "bg_ticks").orElse(null)
          instanceof IntegerValue ticks) {
        backgroundTicks = Math.max(100L, ticks.value());
      }
      if (world().readObjectProperty(options.value(), "bg_seconds").orElse(null)
          instanceof IntegerValue seconds) {
        backgroundSeconds = Math.max(1L, seconds.value());
      }
      if (world().readObjectProperty(options.value(), "max_stack_depth").orElse(null)
          instanceof IntegerValue depth) {
        backgroundMaxStackDepth = Math.max(DEFAULT_MAX_STACK_DEPTH, depth.value());
      }
    }
    return VmState.restoreBackground(
        initial, backgroundTicks, backgroundSeconds, backgroundMaxStackDepth);
  }

  void publishVmState(VmState task, long taskPlayer) {
    applyConnectionOptionRequests(task);
    applyForcedInputRequests(task);
    applyBootPlayerTargets(task, taskPlayer);
    closeRecycledPlayerConnections();
    if (task.outcome() == VmState.Outcome.SUSPENDED) {
      finalizePendingAnonymousObjects();
      queueUnreachableAnonymousObjects(task.snapshot());
      finalizePendingAnonymousObjects();
    } else if (task.outcome() == VmState.Outcome.RETURNED
        || task.outcome() == VmState.Outcome.ERRORED) {
      queueUnreachableAnonymousObjects();
    }
    for (var request : task.drainCheckpointRequests()) {
      effects().add(RuntimeEffect.checkpoint());
      if (request.shutdown()) {
        effects().add(RuntimeEffect.shutdown());
      }
    }
  }

  private void finalizePendingAnonymousObjects() {
    List<MooValue> pending = world().pendingFinalization();
    if (pending.isEmpty()) {
      return;
    }
    WorldSnapshot snapshot = world().snapshot();
    Set<AnonymousObjectValue> finalizing = new LinkedHashSet<>();
    Set<WaifValue> visitedWaifs = new LinkedHashSet<>();
    List<MooValue> remaining = new ArrayList<>();
    for (MooValue value : pending) {
      if (value instanceof AnonymousObjectValue anonymous) {
        markReachableAnonymous(anonymous, snapshot, finalizing, visitedWaifs);
      } else {
        remaining.add(value);
      }
    }
    for (AnonymousObjectValue anonymous : finalizing) {
      world().removeAnonymousObject(anonymous);
    }
    world().replacePendingFinalization(remaining);
  }

  private void queueUnreachableAnonymousObjects(VmSnapshot... taskRoots) {
    WorldSnapshot snapshot = world().snapshot();
    Set<AnonymousObjectValue> reachable = new LinkedHashSet<>();
    Set<WaifValue> visitedWaifs = new LinkedHashSet<>();
    for (WorldObject object : snapshot.objects().values()) {
      for (WorldProperty property : object.properties()) {
        markReachableAnonymous(
            property.value(), snapshot, reachable, visitedWaifs);
      }
    }
    for (VmSnapshot task : taskRoots) {
      for (MooValue value : task.initialLocals().values()) {
        markReachableAnonymous(value, snapshot, reachable, visitedWaifs);
      }
      for (VmSnapshot.Frame frame : task.frames()) {
        for (MooValue value : frame.operandStack()) {
          markReachableAnonymous(value, snapshot, reachable, visitedWaifs);
        }
        for (VmSnapshot.IndexState index : frame.indexCollections()) {
          markReachableAnonymous(index.collection(), snapshot, reachable, visitedWaifs);
          index
              .key()
              .ifPresent(value -> markReachableAnonymous(value, snapshot, reachable, visitedWaifs));
        }
        for (MooValue value : frame.locals().values()) {
          markReachableAnonymous(value, snapshot, reachable, visitedWaifs);
        }
        for (VmSnapshot.FinallyState state : frame.finallyStates()) {
          state
              .returnValue()
              .ifPresent(value -> markReachableAnonymous(value, snapshot, reachable, visitedWaifs));
        }
        for (VmSnapshot.LoopState loop : frame.loops().values()) {
          markReachableAnonymous(loop.values(), snapshot, reachable, visitedWaifs);
          loop
              .secondaryValues()
              .ifPresent(value -> markReachableAnonymous(value, snapshot, reachable, visitedWaifs));
        }
        markReachableAnonymous(frame.receiver(), snapshot, reachable, visitedWaifs);
      }
      for (ConnectionOptionRequest request : task.connectionOptionRequests()) {
        markReachableAnonymous(request.value(), snapshot, reachable, visitedWaifs);
      }
      task
          .returnValue()
          .ifPresent(value -> markReachableAnonymous(value, snapshot, reachable, visitedWaifs));
      task
          .forkRequest()
          .ifPresent(
              fork -> {
                for (MooValue value : fork.locals().values()) {
                  markReachableAnonymous(value, snapshot, reachable, visitedWaifs);
                }
              });
      task
          .pendingBuiltin()
          .ifPresent(
              pending -> {
                for (MooValue value : pending.arguments()) {
                  markReachableAnonymous(value, snapshot, reachable, visitedWaifs);
                }
                markReachableAnonymous(pending.taskLocal(), snapshot, reachable, visitedWaifs);
                markReachableAnonymous(pending.receiver(), snapshot, reachable, visitedWaifs);
                markReachableAnonymous(pending.callers(), snapshot, reachable, visitedWaifs);
              });
      markReachableAnonymous(task.taskLocal(), snapshot, reachable, visitedWaifs);
    }

    List<MooValue> pending = new ArrayList<>(snapshot.pendingFinalization());
    for (MooValue value : pending) {
      markReachableAnonymous(value, snapshot, reachable, visitedWaifs);
    }
    for (AnonymousObjectValue identity : snapshot.anonymousObjects().keySet()) {
      if (!reachable.contains(identity) && !pending.contains(identity)) {
        pending.add(identity);
        markReachableAnonymous(identity, snapshot, reachable, visitedWaifs);
      }
    }
    world().replacePendingFinalization(pending);
  }

  private static void markReachableAnonymous(
      MooValue value,
      WorldSnapshot world,
      Set<AnonymousObjectValue> reachable,
      Set<WaifValue> visitedWaifs) {
    if (value instanceof AnonymousObjectValue anonymous) {
      if (!reachable.add(anonymous)) {
        return;
      }
      WorldAnonymousObject body = world.anonymousObjects().get(anonymous);
      if (body != null) {
        for (WorldProperty property : body.properties()) {
          markReachableAnonymous(property.value(), world, reachable, visitedWaifs);
        }
      }
      return;
    }
    if (value instanceof WaifValue waif) {
      if (!visitedWaifs.add(waif)) {
        return;
      }
      WorldWaif body = world.waifs().get(waif);
      if (body != null) {
        for (WorldProperty property : body.properties()) {
          markReachableAnonymous(property.value(), world, reachable, visitedWaifs);
        }
      }
      return;
    }
    if (value instanceof ListValue list) {
      for (MooValue element : list.elements()) {
        markReachableAnonymous(element, world, reachable, visitedWaifs);
      }
      return;
    }
    if (value instanceof MapValue map) {
      for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
        markReachableAnonymous(entry.getKey(), world, reachable, visitedWaifs);
        markReachableAnonymous(entry.getValue(), world, reachable, visitedWaifs);
      }
    }
  }

  private BuiltinCatalog.Result read(
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long taskId,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers) {
    if (arguments.isEmpty() && !scheduler.isLastInputTask(taskId)) {
      return BuiltinCatalog.Result.error(ErrorValue.E_PERM);
    }
    if (arguments.size() != 2 || !arguments.get(1).isTruthy()) {
      return BuiltinCatalog.Result.error(ErrorValue.E_INVARG);
    }
    long target = ((ObjectValue) arguments.getFirst()).value();
    ConnectionState connection = connections().get(target);
    if (connection == null) {
      for (Map.Entry<Long, ConnectionState> entry : connections().entrySet()) {
        if (world.connectionPlayer(entry.getKey()).orElse(Long.MIN_VALUE) == target) {
          connection = entry.getValue();
          break;
        }
      }
    }
    if (connection == null) {
      return BuiltinCatalog.Result.error(ErrorValue.E_INVARG);
    }
    if (connection.pendingInput.isEmpty()) {
      return BuiltinCatalog.Result.value(new IntegerValue(0));
    }
    return BuiltinCatalog.Result.value(encode(connection.pendingInput.removeFirst()));
  }

  private void applyConnectionOptionRequests(VmState task) {
    for (ConnectionOptionRequest request : task.drainConnectionOptionRequests()) {
      long connectionId = request.target();
      ConnectionState connection = connections().get(request.target());
      if (connection == null) {
        for (Map.Entry<Long, ConnectionState> entry : connections().entrySet()) {
          if (world().connectionPlayer(entry.getKey()).orElse(Long.MIN_VALUE) == request.target()) {
            connectionId = entry.getKey();
            connection = entry.getValue();
            break;
          }
        }
      }
      if (connection == null) {
        continue;
      }
      if (request.option() == ConnectionOption.HOLD_INPUT) {
        boolean release = connection.holdInput && !request.value().isTruthy();
        connection.holdInput = request.value().isTruthy();
        if (release) {
          List<String> pendingInput = List.copyOf(connection.pendingInput);
          connection.pendingInput.clear();
          for (String line : pendingInput) {
            effects().add(RuntimeEffect.input(connectionId, line));
          }
        }
      } else if (request.option() == ConnectionOption.DISABLE_OOB) {
        connection.disableOob = request.value().isTruthy();
      } else if (request.option() == ConnectionOption.BINARY) {
        long binaryConnectionId = connectionId;
        if (listenerControl.isPresent()) {
          effects().add(RuntimeEffect.binary(binaryConnectionId, request.value().isTruthy()));
        }
      } else if (request.value() instanceof StringValue command && command.length() > 0) {
        connection.flushCommand =
            Optional.of(new String(command.bytes(), StandardCharsets.ISO_8859_1));
      } else {
        connection.flushCommand = Optional.empty();
      }
    }
  }

  private void applyForcedInputRequests(VmState task) {
    for (ForcedInputRequest request : task.drainForcedInputRequests()) {
      long connectionId = request.target();
      if (!connections().containsKey(connectionId)) {
        for (Map.Entry<Long, ConnectionState> entry : connections().entrySet()) {
          if (world().connectionPlayer(entry.getKey()).orElse(Long.MIN_VALUE) == request.target()) {
            connectionId = entry.getKey();
            break;
          }
        }
      }
      if (!connections().containsKey(connectionId)) {
        continue;
      }
      effects().add(RuntimeEffect.input(connectionId, request.line()));
    }
  }

  private void applyBootPlayerTargets(VmState task, long taskPlayer) {
    for (long target : task.drainBootPlayerTargets()) {
      long connectionId = target;
      ConnectionState connection = connections().get(connectionId);
      if (connection == null) {
        for (Map.Entry<Long, ConnectionState> entry : connections().entrySet()) {
          if (world().connectionPlayer(entry.getKey()).orElse(Long.MIN_VALUE) == target) {
            connectionId = entry.getKey();
            connection = entry.getValue();
            break;
          }
        }
      }
      if (connection == null) {
        continue;
      }

      long disconnectedPlayer = world().connectionPlayer(connectionId).orElse(target);
      if (disconnectedPlayer == taskPlayer) {
        long outputConnectionId = connectionId;
        if (listenerControl.isPresent()) {
          effects().add(RuntimeEffect.write(outputConnectionId, task.output()));
        }
      }
      long listenerHandler = connection.listenerHandler;
      List<String> lines = new ArrayList<>();
      if (connection.printMessages) {
        MooValue message = null;
        MooValue listenerOptions =
            world().readObjectProperty(listenerHandler, "server_options").orElse(null);
        if (listenerOptions instanceof ObjectValue options) {
          message = world().readObjectProperty(options.value(), "boot_msg").orElse(null);
        }
        if (message == null && listenerHandler != 0) {
          MooValue rootOptions = world().readObjectProperty(0, "server_options").orElse(null);
          if (rootOptions instanceof ObjectValue options) {
            message = world().readObjectProperty(options.value(), "boot_msg").orElse(null);
          }
        }
        if (message == null) {
          lines.add("*** Disconnected ***");
        } else if (message instanceof StringValue string) {
          lines.add(new String(string.bytes(), StandardCharsets.ISO_8859_1));
        } else if (message instanceof ListValue list) {
          for (MooValue element : list.elements()) {
            if (element instanceof StringValue string) {
              lines.add(new String(string.bytes(), StandardCharsets.ISO_8859_1));
            }
          }
        }
      }
      connections().remove(connectionId);
      world().closeConnection(connectionId);
      Optional<WorldVerb> disconnected = world().verb(listenerHandler, "user_disconnected");
      if (disconnected.isPresent()) {
        spawnedSteps()
            .add(
                startStored(
                    disconnected.orElseThrow(),
                    verbLocals(
                        listenerHandler,
                        disconnectedPlayer,
                        -1,
                        "user_disconnected",
                        new ListValue(List.of(new ObjectValue(disconnectedPlayer))),
                        ""),
                    RuntimeContinuation.after(
                        RuntimeTransition.BOOT_PLAYER_USER_DISCONNECTED_THEN_BOOT,
                        connectionId,
                        "",
                        lines,
                        0,
                        0,
                        false)));
      } else {
        finishBootPlayer(connectionId, lines);
      }
    }
  }

  private RuntimeStep finishBootPlayer(long connectionId, List<String> lines) {
    if (listenerControl.isPresent()) {
      effects().add(RuntimeEffect.boot(connectionId, lines));
    }
    return RuntimeStep.returned(List.of());
  }

  private void closeRecycledPlayerConnections() {
    for (Map.Entry<Long, ConnectionState> entry : new ArrayList<>(connections().entrySet())) {
      long connectionId = entry.getKey();
      long player = world().connectionPlayer(connectionId).orElse(Long.MIN_VALUE);
      if (player < 0 || world().object(player).isPresent()) {
        continue;
      }

      ConnectionState connection = entry.getValue();
      connections().remove(connectionId);
      world().closeConnection(connectionId);
      List<String> lines = new ArrayList<>();
      if (connection.printMessages) {
        MooValue message = null;
        MooValue listenerOptions =
            world().readObjectProperty(connection.listenerHandler, "server_options").orElse(null);
        if (listenerOptions instanceof ObjectValue options) {
          message = world().readObjectProperty(options.value(), "recycle_msg").orElse(null);
        }
        if (message == null && connection.listenerHandler != 0) {
          MooValue rootOptions = world().readObjectProperty(0, "server_options").orElse(null);
          if (rootOptions instanceof ObjectValue options) {
            message = world().readObjectProperty(options.value(), "recycle_msg").orElse(null);
          }
        }
        if (message == null) {
          lines.add("*** Recycled ***");
        } else if (message instanceof StringValue string) {
          lines.add(new String(string.bytes(), StandardCharsets.ISO_8859_1));
        } else if (message instanceof ListValue list) {
          for (MooValue element : list.elements()) {
            if (element instanceof StringValue string) {
              lines.add(new String(string.bytes(), StandardCharsets.ISO_8859_1));
            }
          }
        }
      }
      if (listenerControl.isPresent()) {
        effects().add(RuntimeEffect.boot(connectionId, lines));
      }
    }
  }

  private static Map<String, MooValue> verbLocals(
      long thisObject,
      long player,
      long caller,
      String verb,
      ListValue arguments,
      String argumentString) {
    Map<String, MooValue> locals = new LinkedHashMap<>();
    locals.put("this", new ObjectValue(thisObject));
    locals.put("player", new ObjectValue(player));
    locals.put("caller", new ObjectValue(caller));
    locals.put("verb", encode(verb));
    locals.put("args", arguments);
    locals.put("argstr", encode(argumentString));
    return locals;
  }

  RuntimeStep execute(
      RuntimeContinuation continuation, Optional<moo.vm.VmSnapshot> completedVm) {
    RuntimeRequest request = continuation.ingress().orElse(null);
    if (request != null) {
      if (completedVm.isPresent() || continuation.transition().isPresent()) {
        throw new IllegalArgumentException("ingress continuation has resumed state");
      }
      return switch (request.operation) {
        case STARTUP -> startServerNow();
        case OPEN ->
            openConnectionNow(
                request.connectionId,
                request.listenerHandler,
                request.printMessages,
                request.connectionInfo);
        case CLOSE -> closeConnectionNow(request.connectionId);
        case LINE -> executeLineNow(request.connectionId, request.text);
        case OUT_OF_BAND -> executeTransportOutOfBandNow(request.connectionId, request.text);
        case TIMEOUT -> checkLoginTimeoutNow(request.connectionId, request.generation);
      };
    }
    if (continuation.transition().orElseThrow() == RuntimeTransition.TASK_TIMEOUT_START) {
      if (completedVm.isPresent()) {
        throw new IllegalArgumentException("timeout start continuation has completed VM state");
      }
      return startTaskTimeout(continuation);
    }
    VmState state = VmState.restore(completedVm.orElseThrow());
    return resumeRuntime(continuation, state);
  }

  private RuntimeStep resumeRuntime(RuntimeContinuation continuation, VmState state) {
    return switch (continuation.transition().orElseThrow()) {
      case SERVER_STARTED_RETURN -> RuntimeStep.returned(state.output());
      case CLOSE_AFTER_USER_CLIENT_DISCONNECTED -> RuntimeStep.returned(List.of());
      case LINE_OOB_RETURN_OUTPUT, TRANSPORT_OOB_RETURN_OUTPUT ->
          RuntimeStep.returned(state.output());
      case LINE_DO_COMMAND_GATE_THEN_DISPATCH ->
          continueLineAfterDoCommand(continuation.connectionId(), continuation.text(), state);
      case LINE_SELECTED_COMMAND_APPEND_OUTPUT -> {
        List<String> output = new ArrayList<>(continuation.output());
        ConnectionState connection = connections().get(continuation.connectionId());
        if (connection != null) {
          output.addAll(state.output());
          connection.suffix.ifPresent(output::add);
        }
        yield RuntimeStep.returned(output);
      }
      case LOGIN_BLANK_TRUTH_GATE ->
          state.returnValue().isEmpty() || !state.returnValue().orElseThrow().isTruthy()
              ? RuntimeStep.returned(state.output())
              : executeLoginAuthentication(continuation.connectionId(), continuation.text());
      case LOGIN_AUTHENTICATE_AND_ASSOCIATE ->
          finishLogin(
              continuation.connectionId(), continuation.first(), state);
      case LOGIN_RECONNECTED_HOOK_THEN_RETURN,
          LOGIN_NEW_USER_CONNECTED_THEN_RETURN,
          LOGIN_EXISTING_USER_CONNECTED_THEN_RETURN ->
          RuntimeStep.returned(continuation.flag() ? List.of() : continuation.output());
      case LOGIN_OLD_CLIENT_DISCONNECTED_THEN_ASSOCIATE ->
          associateRedirectedLogin(continuation);
      case LOGIN_USER_CREATED_THEN_CONNECTED -> continueCreatedLogin(continuation);
      case LOGIN_TIMEOUT_USER_DISCONNECTED_THEN_BOOT ->
          finishLoginTimeout(continuation.connectionId(), continuation.output());
      case BOOT_PLAYER_USER_DISCONNECTED_THEN_BOOT ->
          finishBootPlayer(continuation.connectionId(), continuation.output());
      case TASK_TIMEOUT_START ->
          throw new IllegalStateException("timeout start cannot resume completed VM state");
      case TASK_TIMEOUT_RETURN -> {
        List<String> output = new ArrayList<>(state.output());
        if (state.returnValue().isEmpty() || !state.returnValue().orElseThrow().isTruthy()) {
          output.addAll(continuation.output());
        }
        yield RuntimeStep.returned(output);
      }
    };
  }

  synchronized AttemptContext openAttempt(WorldTxn transaction) {
    Map<Long, ConnectionState> sessions = new LinkedHashMap<>();
    publishedConnections.forEach((id, state) -> sessions.put(id, state.copy()));
    AttemptContext context =
        new AttemptContext(
            transaction, sessions, sessionRevision, new ArrayList<>(), new ArrayList<>());
    ATTEMPT.set(context);
    seedSessions(transaction, sessions);
    return context;
  }

  private static void seedSessions(
      WorldTxn transaction, Map<Long, ConnectionState> sessions) {
    for (Map.Entry<Long, ConnectionState> entry : sessions.entrySet()) {
      ConnectionState state = entry.getValue();
      transaction.openConnection(entry.getKey(), state.connectionInfo);
      if (state.player >= 0) {
        transaction.switchConnectionPlayer(entry.getKey(), state.player);
      }
      transaction.setIntrinsicCommands(entry.getKey(), state.intrinsicCommands);
    }
  }

  void replaceAttemptWorld(AttemptContext context, WorldTxn transaction) {
    if (ATTEMPT.get() != context) {
      throw new IllegalStateException("cannot replace another runtime attempt");
    }
    context.world = transaction;
    seedSessions(transaction, context.sessions);
  }

  AttemptContext finishAttempt() {
    AttemptContext context = requireAttempt();
    for (Map.Entry<Long, ConnectionState> entry : context.sessions.entrySet()) {
      long connectionId = entry.getKey();
      ConnectionState state = entry.getValue();
      state.player = context.world.connectionPlayer(connectionId).orElse(-1);
      state.intrinsicCommands =
          context.world.intrinsicCommands(connectionId).orElse(state.intrinsicCommands);
    }
    ATTEMPT.remove();
    return context;
  }

  void abandonAttempt() {
    ATTEMPT.remove();
  }

  synchronized boolean sessionsAreCurrent(AttemptContext context) {
    return context.baseSessionRevision == sessionRevision;
  }

  synchronized OptionalLong connectionPlayer(long connectionId) {
    ConnectionState connection = publishedConnections.get(connectionId);
    return connection == null ? OptionalLong.empty() : OptionalLong.of(connection.player);
  }

  synchronized Optional<MapValue> connectionInfo(long objectId) {
    ConnectionState direct = publishedConnections.get(objectId);
    if (direct != null) {
      return Optional.of(direct.connectionInfo);
    }
    for (ConnectionState connection : publishedConnections.values()) {
      if (connection.player == objectId) {
        return Optional.of(connection.connectionInfo);
      }
    }
    return Optional.empty();
  }

  void publishAttempt(AttemptContext context, WorldSnapshot committedWorld) {
    List<RuntimeEffect> publishedEffects;
    synchronized (this) {
      if (context.baseSessionRevision != sessionRevision) {
        throw new IllegalStateException("session attempt is stale");
      }
      boolean sessionsChanged = context.sessions.size() != publishedConnections.size();
      if (!sessionsChanged) {
        for (Map.Entry<Long, ConnectionState> entry : context.sessions.entrySet()) {
          ConnectionState published = publishedConnections.get(entry.getKey());
          if (published == null || !entry.getValue().sameState(published)) {
            sessionsChanged = true;
            break;
          }
        }
      }
      if (sessionsChanged) {
        publishedConnections.clear();
        context.sessions.forEach((id, state) -> publishedConnections.put(id, state.copy()));
        sessionRevision = Math.incrementExact(sessionRevision);
      }
      context.baseSessionRevision = sessionRevision;
      publishedEffects = List.copyOf(context.effects);
      context.effects.clear();
    }
    for (RuntimeEffect effect : publishedEffects) {
      publishEffect(effect, committedWorld);
    }
  }

  List<RuntimeStep> takeSpawnedSteps(AttemptContext context) {
    List<RuntimeStep> spawned = List.copyOf(context.spawnedSteps);
    context.spawnedSteps.clear();
    return spawned;
  }

  private void publishEffect(RuntimeEffect effect, WorldSnapshot committedWorld) {
    switch (effect.kind) {
      case START_TIMEOUT -> {
        ConnectionState connection;
        synchronized (this) {
          connection = publishedConnections.get(effect.connectionId);
        }
        if (connection != null && connection.generation == effect.generation) {
          Thread.ofVirtual()
              .name("moo-connect-timeout-" + effect.connectionId)
              .start(() -> monitorUnauthenticatedConnection(effect.connectionId, connection));
        }
      }
      case WRITE ->
          listenerControl.ifPresent(
              control -> control.writeConnection(effect.connectionId, effect.lines));
      case BOOT ->
          listenerControl.ifPresent(
              control -> control.bootConnection(effect.connectionId, effect.lines));
      case BINARY ->
          listenerControl.ifPresent(
              control -> control.setConnectionBinary(effect.connectionId, effect.binary));
      case INPUT -> scheduler.enqueueDetached(RuntimeRequest.line(effect.connectionId, effect.text));
      case CHECKPOINT -> {
        Path target =
            checkpoint.orElseThrow(
                () -> new IllegalStateException("dump_database() requires --checkpoint"));
        System.err.println("CHECKPOINTING to " + target);
        try {
          checkpointCodec.writeAtomic(target, committedWorld, scheduler.queuedTasks());
        } catch (IOException error) {
          throw new UncheckedIOException("checkpoint failed: " + target, error);
        }
      }
      case SHUTDOWN -> listenerControl.ifPresent(ListenerControl::shutdown);
    }
  }

  private AttemptContext requireAttempt() {
    AttemptContext context = ATTEMPT.get();
    if (context == null) {
      throw new IllegalStateException("runtime world access is outside a scheduler attempt");
    }
    return context;
  }

  AttemptContext currentAttempt() {
    return requireAttempt();
  }

  WorldTxn world() {
    return requireAttempt().world;
  }

  private Map<Long, ConnectionState> connections() {
    return requireAttempt().sessions;
  }

  private List<RuntimeEffect> effects() {
    return requireAttempt().effects;
  }

  private List<RuntimeStep> spawnedSteps() {
    return requireAttempt().spawnedSteps;
  }

  MooVm vm() {
    return vm;
  }

  BuiltinCatalog builtins() {
    return builtins;
  }

  private ConnectionState requireConnection(long connectionId) {
    ConnectionState connection = connections().get(connectionId);
    if (connection == null) {
      throw new IllegalArgumentException("unknown connection #" + connectionId);
    }
    return connection;
  }

  private static StringValue encode(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }

  enum RuntimeTransition {
    SERVER_STARTED_RETURN,
    CLOSE_AFTER_USER_CLIENT_DISCONNECTED,
    LINE_OOB_RETURN_OUTPUT,
    LINE_DO_COMMAND_GATE_THEN_DISPATCH,
    LINE_SELECTED_COMMAND_APPEND_OUTPUT,
    TRANSPORT_OOB_RETURN_OUTPUT,
    LOGIN_BLANK_TRUTH_GATE,
    LOGIN_AUTHENTICATE_AND_ASSOCIATE,
    LOGIN_RECONNECTED_HOOK_THEN_RETURN,
    LOGIN_OLD_CLIENT_DISCONNECTED_THEN_ASSOCIATE,
    LOGIN_NEW_USER_CONNECTED_THEN_RETURN,
    LOGIN_USER_CREATED_THEN_CONNECTED,
    LOGIN_EXISTING_USER_CONNECTED_THEN_RETURN,
    LOGIN_TIMEOUT_USER_DISCONNECTED_THEN_BOOT,
    BOOT_PLAYER_USER_DISCONNECTED_THEN_BOOT,
    TASK_TIMEOUT_START,
    TASK_TIMEOUT_RETURN
  }

  record RuntimeContinuation(
      Optional<RuntimeRequest> ingress,
      Optional<RuntimeTransition> transition,
      long connectionId,
      String text,
      List<String> output,
      long first,
      long second,
      boolean flag,
      Optional<VmSnapshot> timeoutSnapshot) {
    RuntimeContinuation {
      Objects.requireNonNull(ingress, "ingress");
      Objects.requireNonNull(transition, "transition");
      Objects.requireNonNull(text, "text");
      Objects.requireNonNull(timeoutSnapshot, "timeoutSnapshot");
      output = List.copyOf(output);
      if (ingress.isPresent() == transition.isPresent()) {
        throw new IllegalArgumentException(
            "runtime continuation requires exactly one ingress or transition");
      }
    }

    static RuntimeContinuation ingress(RuntimeRequest request) {
      return new RuntimeContinuation(
          Optional.of(request),
          Optional.empty(),
          0,
          "",
          List.of(),
          0,
          0,
          false,
          Optional.empty());
    }

    static RuntimeContinuation after(
        RuntimeTransition transition,
        long connectionId,
        String text,
        List<String> output,
        long first,
        long second,
        boolean flag) {
      return new RuntimeContinuation(
          Optional.empty(),
          Optional.of(transition),
          connectionId,
          text,
          output,
          first,
          second,
          flag,
          Optional.empty());
    }

    static RuntimeContinuation timeout(
        VmSnapshot snapshot, long player, List<String> fallbackOutput) {
      return new RuntimeContinuation(
          Optional.empty(),
          Optional.of(RuntimeTransition.TASK_TIMEOUT_START),
          0,
          "",
          fallbackOutput,
          player,
          0,
          false,
          Optional.of(snapshot));
    }

    static RuntimeContinuation timeoutReturn(List<String> fallbackOutput) {
      return new RuntimeContinuation(
          Optional.empty(),
          Optional.of(RuntimeTransition.TASK_TIMEOUT_RETURN),
          0,
          "",
          fallbackOutput,
          0,
          0,
          false,
          Optional.empty());
    }
  }

  record RuntimeStep(
      Optional<BytecodeProgram> program,
      Optional<moo.vm.VmSnapshot> snapshot,
      long taskPlayer,
      Optional<RuntimeContinuation> continuation,
      Optional<List<String>> output) {
    RuntimeStep {
      Objects.requireNonNull(program, "program");
      Objects.requireNonNull(snapshot, "snapshot");
      Objects.requireNonNull(continuation, "continuation");
      output = output.map(List::copyOf);
      boolean vm = program.isPresent() && snapshot.isPresent() && continuation.isPresent();
      boolean returned = output.isPresent();
      if (vm == returned || program.isPresent() != snapshot.isPresent()) {
        throw new IllegalArgumentException("runtime step requires VM work or returned output");
      }
    }

    static RuntimeStep vm(
        BytecodeProgram program,
        moo.vm.VmSnapshot snapshot,
        long taskPlayer,
        RuntimeContinuation continuation) {
      return new RuntimeStep(
          Optional.of(program),
          Optional.of(snapshot),
          taskPlayer,
          Optional.of(continuation),
          Optional.empty());
    }

    static RuntimeStep returned(List<String> output) {
      return new RuntimeStep(
          Optional.empty(), Optional.empty(), Long.MIN_VALUE, Optional.empty(), Optional.of(output));
    }
  }

  static final class ConnectionState {
    private final long listenerHandler;
    private final boolean printMessages;
    private final long generation;
    private MapValue connectionInfo = new MapValue(Map.of());
    private long player = -1;
    private ListValue intrinsicCommands = new ListValue(List.of());
    private long lastActivityNanos = System.nanoTime();
    private boolean holdInput;
    private boolean disableOob;
    private Optional<String> flushCommand = Optional.empty();
    private final List<String> pendingInput = new ArrayList<>();
    private Optional<String> prefix = Optional.empty();
    private Optional<String> suffix = Optional.empty();
    private long programmingObject = -1;
    private int programmingVerbIndex = -1;
    private final StringBuilder programmingSource = new StringBuilder();

    private ConnectionState(long listenerHandler, boolean printMessages, long generation) {
      this.listenerHandler = listenerHandler;
      this.printMessages = printMessages;
      this.generation = generation;
    }

    private ConnectionState copy() {
      ConnectionState copy = new ConnectionState(listenerHandler, printMessages, generation);
      copy.connectionInfo = connectionInfo;
      copy.player = player;
      copy.intrinsicCommands = intrinsicCommands;
      copy.lastActivityNanos = lastActivityNanos;
      copy.holdInput = holdInput;
      copy.disableOob = disableOob;
      copy.flushCommand = flushCommand;
      copy.pendingInput.addAll(pendingInput);
      copy.prefix = prefix;
      copy.suffix = suffix;
      copy.programmingObject = programmingObject;
      copy.programmingVerbIndex = programmingVerbIndex;
      copy.programmingSource.append(programmingSource);
      return copy;
    }

    private boolean sameState(ConnectionState other) {
      return listenerHandler == other.listenerHandler
          && printMessages == other.printMessages
          && generation == other.generation
          && connectionInfo.equals(other.connectionInfo)
          && player == other.player
          && intrinsicCommands.equals(other.intrinsicCommands)
          && lastActivityNanos == other.lastActivityNanos
          && holdInput == other.holdInput
          && disableOob == other.disableOob
          && flushCommand.equals(other.flushCommand)
          && pendingInput.equals(other.pendingInput)
          && prefix.equals(other.prefix)
          && suffix.equals(other.suffix)
          && programmingObject == other.programmingObject
          && programmingVerbIndex == other.programmingVerbIndex
          && programmingSource.toString().contentEquals(other.programmingSource);
    }
  }

  enum Operation {
    STARTUP,
    OPEN,
    CLOSE,
    LINE,
    OUT_OF_BAND,
    TIMEOUT
  }

  record RuntimeRequest(
      Operation operation,
      long connectionId,
      long listenerHandler,
      boolean printMessages,
      MapValue connectionInfo,
      String text,
      long generation) {
    static RuntimeRequest startup() {
      return new RuntimeRequest(
          Operation.STARTUP, 0, 0, false, new MapValue(Map.of()), "", 0);
    }

    static RuntimeRequest open(
        long connectionId, long listenerHandler, boolean printMessages, MapValue connectionInfo) {
      return new RuntimeRequest(
          Operation.OPEN, connectionId, listenerHandler, printMessages, connectionInfo, "", 0);
    }

    static RuntimeRequest close(long connectionId) {
      return new RuntimeRequest(
          Operation.CLOSE, connectionId, 0, false, new MapValue(Map.of()), "", 0);
    }

    static RuntimeRequest line(long connectionId, String line) {
      return new RuntimeRequest(
          Operation.LINE, connectionId, 0, false, new MapValue(Map.of()), line, 0);
    }

    static RuntimeRequest outOfBand(long connectionId, String command) {
      return new RuntimeRequest(
          Operation.OUT_OF_BAND, connectionId, 0, false, new MapValue(Map.of()), command, 0);
    }

    static RuntimeRequest timeout(long connectionId, long generation) {
      return new RuntimeRequest(
          Operation.TIMEOUT, connectionId, 0, false, new MapValue(Map.of()), "", generation);
    }
  }

  static final class AttemptContext {
    WorldTxn world;
    final Map<Long, ConnectionState> sessions;
    long baseSessionRevision;
    final List<RuntimeEffect> effects;
    final List<RuntimeStep> spawnedSteps;

    AttemptContext(
        WorldTxn world,
        Map<Long, ConnectionState> sessions,
        long baseSessionRevision,
        List<RuntimeEffect> effects,
        List<RuntimeStep> spawnedSteps) {
      this.world = world;
      this.sessions = sessions;
      this.baseSessionRevision = baseSessionRevision;
      this.effects = effects;
      this.spawnedSteps = spawnedSteps;
    }
  }

  enum RuntimeEffectKind {
    START_TIMEOUT,
    WRITE,
    BOOT,
    BINARY,
    INPUT,
    CHECKPOINT,
    SHUTDOWN
  }

  record RuntimeEffect(
      RuntimeEffectKind kind,
      long connectionId,
      List<String> lines,
      boolean binary,
      long generation,
      String text) {
    RuntimeEffect {
      lines = List.copyOf(lines);
    }

    static RuntimeEffect startTimeout(long connectionId, long generation) {
      return new RuntimeEffect(
          RuntimeEffectKind.START_TIMEOUT, connectionId, List.of(), false, generation, "");
    }

    static RuntimeEffect write(long connectionId, List<String> lines) {
      return new RuntimeEffect(RuntimeEffectKind.WRITE, connectionId, lines, false, 0, "");
    }

    static RuntimeEffect boot(long connectionId, List<String> lines) {
      return new RuntimeEffect(RuntimeEffectKind.BOOT, connectionId, lines, false, 0, "");
    }

    static RuntimeEffect binary(long connectionId, boolean binary) {
      return new RuntimeEffect(RuntimeEffectKind.BINARY, connectionId, List.of(), binary, 0, "");
    }

    static RuntimeEffect input(long connectionId, String line) {
      return new RuntimeEffect(RuntimeEffectKind.INPUT, connectionId, List.of(), false, 0, line);
    }

    static RuntimeEffect checkpoint() {
      return new RuntimeEffect(RuntimeEffectKind.CHECKPOINT, 0, List.of(), false, 0, "");
    }

    static RuntimeEffect shutdown() {
      return new RuntimeEffect(RuntimeEffectKind.SHUTDOWN, 0, List.of(), false, 0, "");
    }
  }
}
