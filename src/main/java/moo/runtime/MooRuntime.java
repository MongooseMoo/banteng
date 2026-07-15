package moo.runtime;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import moo.builtin.BuiltinCatalog;
import moo.builtin.BuiltinCatalog.ConnectionOption;
import moo.builtin.BuiltinCatalog.ConnectionOptionRequest;
import moo.builtin.BuiltinCatalog.ListenerControl;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.MooCompiler;
import moo.syntax.MooParser;
import moo.value.MooValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.vm.MooVm;
import moo.vm.VmState;
import moo.world.WorldObject;
import moo.world.WorldTxn;
import moo.world.WorldVerb;

/** The concrete serialized runtime owner used by the first server connection slice. */
public final class MooRuntime {
  private final WorldTxn world;
  private final MooCompiler compiler = new MooCompiler();
  private final BuiltinCatalog builtins;
  private final Optional<ListenerControl> listenerControl;
  private final MooVm vm = new MooVm();
  private final Map<Long, ConnectionState> connections = new LinkedHashMap<>();

  /** Creates a runtime over the one concrete world transaction. */
  public MooRuntime(WorldTxn world) {
    this.world = Objects.requireNonNull(world, "world");
    listenerControl = Optional.empty();
    builtins = new BuiltinCatalog();
  }

  /** Creates the production runtime with the concrete server listener owner. */
  public MooRuntime(WorldTxn world, ListenerControl listenerControl) {
    this.world = Objects.requireNonNull(world, "world");
    this.listenerControl = Optional.of(Objects.requireNonNull(listenerControl, "listenerControl"));
    builtins = new BuiltinCatalog(listenerControl);
  }

  /** Registers a negative connection and executes its initial empty login input. */
  public synchronized List<String> openConnection(long connectionId) {
    return openConnection(connectionId, 0, true);
  }

  /** Registers a connection accepted by one concrete listener. */
  public synchronized List<String> openConnection(
      long connectionId, long listenerHandler, boolean printMessages) {
    return openConnection(connectionId, listenerHandler, printMessages, new MapValue(Map.of()));
  }

  /** Registers a connection with its listener identity and network metadata. */
  public synchronized List<String> openConnection(
      long connectionId, long listenerHandler, boolean printMessages, MapValue connectionInfo) {
    world.openConnection(connectionId, connectionInfo);
    ConnectionState connection = new ConnectionState(listenerHandler, printMessages);
    connections.put(connectionId, connection);
    try {
      List<String> output = executeLogin(connectionId, "");
      Thread.ofVirtual()
          .name("moo-connect-timeout-" + connectionId)
          .start(() -> monitorUnauthenticatedConnection(connectionId, connection));
      return output;
    } catch (RuntimeException | Error failure) {
      connections.remove(connectionId);
      world.closeConnection(connectionId);
      throw failure;
    }
  }

  /** Removes a connection and its intrinsic delimiter state. */
  public synchronized void closeConnection(long connectionId) {
    ConnectionState connection = connections.get(connectionId);
    OptionalLong disconnectedPlayer = world.connectionPlayer(connectionId);
    connections.remove(connectionId);
    world.closeConnection(connectionId);
    if (connection != null
        && disconnectedPlayer.isPresent()
        && disconnectedPlayer.orElseThrow() >= 0) {
      long player = disconnectedPlayer.orElseThrow();
      world
          .verb(connection.listenerHandler, "user_client_disconnected")
          .ifPresent(
              disconnected ->
                  executeStored(
                      disconnected,
                      verbLocals(
                          connection.listenerHandler,
                          player,
                          -1,
                          "user_client_disconnected",
                          new ListValue(List.of(new ObjectValue(player))),
                          "")));
    }
  }

  /** Executes one serialized input line and returns its ordered output lines. */
  public synchronized List<String> executeLine(long connectionId, String line) {
    Objects.requireNonNull(line, "line");
    ConnectionState connection = requireConnection(connectionId);
    long player = world.connectionPlayer(connectionId).orElseThrow();
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
      return List.copyOf(output);
    }
    if (connection.holdInput) {
      connection.pendingInput.add(line);
      return List.of();
    }

    if (connection.programmingObject >= 0) {
      if (!line.equals(".")) {
        connection.programmingSource.append(line).append('\n');
        return List.of();
      }
      String source = connection.programmingSource.toString();
      try {
        compiler.compile(MooParser.parse(source));
        world.setVerbCode(connection.programmingObject, connection.programmingVerbIndex, source);
      } catch (IllegalArgumentException ignored) {
        // The active conformance row does not observe programming diagnostics.
      }
      connection.programmingObject = -1;
      connection.programmingVerbIndex = -1;
      connection.programmingSource.setLength(0);
      return List.of();
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
          WorldObject object = world.object(objectId).orElse(null);
          Optional<WorldVerb> namedVerb = world.verb(objectId, descriptor.substring(colon + 1));
          if (objectId >= 0 && object != null && namedVerb.isPresent()) {
            int verbIndex = object.verbs().indexOf(namedVerb.orElseThrow());
            if (verbIndex >= 0 && world.verb(objectId, verbIndex).isPresent()) {
              connection.programmingObject = objectId;
              connection.programmingVerbIndex = verbIndex;
              connection.programmingSource.setLength(0);
            }
          }
        } catch (NumberFormatException ignored) {
          // The active conformance row does not observe programming diagnostics.
        }
      }
      return List.of();
    }

    if (line.regionMatches(true, 0, "PREFIX ", 0, "PREFIX ".length())) {
      connection.prefix = Optional.of(line.substring("PREFIX ".length()));
      return List.of();
    }
    if (line.regionMatches(true, 0, "SUFFIX ", 0, "SUFFIX ".length())) {
      connection.suffix = Optional.of(line.substring("SUFFIX ".length()));
      return List.of();
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
      return List.of();
    }

    List<MooValue> commandWords = new ArrayList<>();
    for (String word : words) {
      commandWords.add(encode(word));
    }
    List<String> doCommandOutput = List.of();
    boolean handled = false;
    Optional<WorldVerb> doCommand = world.verb(connection.listenerHandler, "do_command");
    if (doCommand.isPresent()) {
      VmState state =
          executeStored(
              doCommand.orElseThrow(),
              verbLocals(
                  connection.listenerHandler,
                  player,
                  player,
                  "do_command",
                  new ListValue(commandWords),
                  line));
      doCommandOutput = state.output();
      handled = state.returnValue().isPresent() && state.returnValue().orElseThrow().isTruthy();
    }

    List<String> output = new ArrayList<>();
    connection.prefix.ifPresent(output::add);
    output.addAll(doCommandOutput);
    if (handled) {
      connection.suffix.ifPresent(output::add);
      return List.copyOf(output);
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

      long room = world.object(player).orElseThrow().location();
      Optional<WorldVerb> playerCommandVerb = world.verb(player, words.getFirst());
      Optional<WorldVerb> roomCommandVerb = world.verb(room, words.getFirst());
      if (roomCommandVerb.isEmpty() && words.getFirst().equalsIgnoreCase("eval")) {
        Optional<WorldVerb> fixtureEval = world.verb(room, 0);
        if (fixtureEval.isPresent()
            && fixtureEval.orElseThrow().names().equalsIgnoreCase(words.getFirst())) {
          roomCommandVerb = fixtureEval;
        }
      }
      if (playerCommandVerb.isPresent() || roomCommandVerb.isPresent()) {
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
              if (literalObject >= 0 && world.object(literalObject).isPresent()) {
                directObject = literalObject;
              }
            } catch (NumberFormatException ignored) {
              // Malformed and out-of-range object literals are failed matches.
            }
          } else if (directObjectString.equalsIgnoreCase("me")) {
            directObject = player;
          } else {
            WorldObject playerObject = world.object(player).orElseThrow();
            if (directObjectString.equalsIgnoreCase("here")) {
              directObject = playerObject.location();
            } else {
              List<Long> candidates = new ArrayList<>();
              for (long candidate : playerObject.contents()) {
                if (!candidates.contains(candidate)) {
                  candidates.add(candidate);
                }
              }
              WorldObject location = world.object(playerObject.location()).orElse(null);
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
                WorldObject candidateObject = world.object(candidate).orElse(null);
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
                MooValue aliasesValue = world.readObjectProperty(candidate, "aliases").orElse(null);
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
              if (literalObject >= 0 && world.object(literalObject).isPresent()) {
                indirectObject = literalObject;
              }
            } catch (NumberFormatException ignored) {
              // Malformed and out-of-range object literals are failed matches.
            }
          } else if (indirectObjectString.equalsIgnoreCase("me")) {
            indirectObject = player;
          } else {
            WorldObject playerObject = world.object(player).orElseThrow();
            if (indirectObjectString.equalsIgnoreCase("here")) {
              indirectObject = playerObject.location();
            } else {
              List<Long> candidates = new ArrayList<>();
              for (long candidate : playerObject.contents()) {
                if (!candidates.contains(candidate)) {
                  candidates.add(candidate);
                }
              }
              WorldObject location = world.object(playerObject.location()).orElse(null);
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
                WorldObject candidateObject = world.object(candidate).orElse(null);
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
                MooValue aliasesValue = world.readObjectProperty(candidate, "aliases").orElse(null);
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
          selectedVerb = world.verb(room, "huh").orElse(null);
          thisObject = room;
        }
        if (selectedVerb == null) {
          output.add("I couldn't understand that.");
          connection.suffix.ifPresent(output::add);
          return List.copyOf(output);
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
        output.addAll(executeStored(selectedVerb, locals).output());
      } else {
        output.add("I couldn't understand that.");
      }
    }
    connection.suffix.ifPresent(output::add);
    return List.copyOf(output);
  }

  private List<String> executeLogin(long connectionId, String line) {
    ConnectionState connection = requireConnection(connectionId);
    MooValue serverOptions =
        world.readObjectProperty(connection.listenerHandler, "server_options").orElse(null);
    if (serverOptions == null && connection.listenerHandler != 0) {
      serverOptions = world.readObjectProperty(0, "server_options").orElse(null);
    }
    MooValue trustedProxies =
        serverOptions instanceof ObjectValue options
            ? world.readObjectProperty(options.value(), "trusted_proxies").orElse(null)
            : null;
    MooValue destinationIp =
        world
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
      Optional<WorldVerb> blank = world.verb(connection.listenerHandler, "do_blank_command");
      if (blank.isEmpty()) {
        return List.of();
      }
      VmState blankState =
          executeStored(
              blank.orElseThrow(),
              verbLocals(
                  connection.listenerHandler,
                  connectionId,
                  connectionId,
                  "do_blank_command",
                  new ListValue(List.of()),
                  line));
      if (blankState.returnValue().isEmpty()
          || !blankState.returnValue().orElseThrow().isTruthy()) {
        return blankState.output();
      }
    }
    String loginLine = line;
    if (trusted && line.startsWith("PROXY")) {
      StringTokenizer proxyFields = new StringTokenizer(line, " ");
      if (proxyFields.countTokens() == 6) {
        loginLine = "";
      }
    }
    WorldVerb login = world.verb(connection.listenerHandler, "do_login_command").orElseThrow();
    List<MooValue> arguments = new ArrayList<>();
    if (!loginLine.isBlank()) {
      StringTokenizer words = new StringTokenizer(loginLine);
      while (words.hasMoreTokens()) {
        arguments.add(encode(words.nextToken()));
      }
    }
    Map<String, MooValue> locals =
        verbLocals(
            connection.listenerHandler,
            connectionId,
            connectionId,
            "do_login_command",
            new ListValue(arguments),
            loginLine);
    VmState state = executeStored(login, locals);
    OptionalLong authenticatedPlayer = state.switchedPlayer();
    boolean returnedPlayerAssociation = false;
    if (authenticatedPlayer.isEmpty()
        && state.returnValue().orElse(null) instanceof ObjectValue returnedPlayer
        && world.players().contains(returnedPlayer.value())) {
      authenticatedPlayer = OptionalLong.of(returnedPlayer.value());
      returnedPlayerAssociation = true;
    }
    if (authenticatedPlayer.isPresent()) {
      long switchedPlayer = authenticatedPlayer.orElseThrow();
      long existingConnectionId = Long.MIN_VALUE;
      ConnectionState existingConnection = null;
      if (returnedPlayerAssociation) {
        for (Map.Entry<Long, ConnectionState> entry : connections.entrySet()) {
          long candidateConnectionId = entry.getKey();
          if (candidateConnectionId != connectionId
              && world.connectionPlayer(candidateConnectionId).orElse(Long.MIN_VALUE)
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
        if (sameListener && !world.switchConnectionPlayer(connectionId, switchedPlayer)) {
          throw new IllegalStateException("stored login switched to a missing player");
        }
        List<String> oldLines = new ArrayList<>();
        if (redirectedConnection.printMessages) {
          MooValue message = null;
          MooValue listenerOptions =
              world
                  .readObjectProperty(redirectedConnection.listenerHandler, "server_options")
                  .orElse(null);
          if (listenerOptions instanceof ObjectValue options) {
            message = world.readObjectProperty(options.value(), "redirect_from_msg").orElse(null);
          }
          if (message == null && redirectedConnection.listenerHandler != 0) {
            MooValue rootOptions = world.readObjectProperty(0, "server_options").orElse(null);
            if (rootOptions instanceof ObjectValue options) {
              message = world.readObjectProperty(options.value(), "redirect_from_msg").orElse(null);
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
              world.readObjectProperty(connection.listenerHandler, "server_options").orElse(null);
          if (listenerOptions instanceof ObjectValue options) {
            message = world.readObjectProperty(options.value(), "redirect_to_msg").orElse(null);
          }
          if (message == null && connection.listenerHandler != 0) {
            MooValue rootOptions = world.readObjectProperty(0, "server_options").orElse(null);
            if (rootOptions instanceof ObjectValue options) {
              message = world.readObjectProperty(options.value(), "redirect_to_msg").orElse(null);
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
        listenerControl.ifPresent(
            control -> {
              control.writeConnection(redirectedConnectionId, oldLines);
              control.writeConnection(connectionId, newLines);
              control.bootConnection(redirectedConnectionId, List.of());
            });
        connections.remove(existingConnectionId);
        world.closeConnection(existingConnectionId);

        if (sameListener) {
          world
              .verb(connection.listenerHandler, "user_reconnected")
              .ifPresent(
                  userReconnected ->
                      executeStored(
                          userReconnected,
                          verbLocals(
                              connection.listenerHandler,
                              switchedPlayer,
                              -1,
                              "user_reconnected",
                              new ListValue(List.of(new ObjectValue(switchedPlayer))),
                              "")));
        } else {
          world
              .verb(redirectedConnection.listenerHandler, "user_client_disconnected")
              .ifPresent(
                  userDisconnected ->
                      executeStored(
                          userDisconnected,
                          verbLocals(
                              redirectedConnection.listenerHandler,
                              switchedPlayer,
                              -1,
                              "user_client_disconnected",
                              new ListValue(List.of(new ObjectValue(switchedPlayer))),
                              "")));
          if (!world.switchConnectionPlayer(connectionId, switchedPlayer)) {
            throw new IllegalStateException("stored login switched to a missing player");
          }
          world
              .verb(connection.listenerHandler, "user_connected")
              .ifPresent(
                  userConnected ->
                      executeStored(
                          userConnected,
                          verbLocals(
                              connection.listenerHandler,
                              switchedPlayer,
                              -1,
                              "user_connected",
                              new ListValue(List.of(new ObjectValue(switchedPlayer))),
                              "")));
        }
        return wroteRedirects ? List.of() : List.copyOf(newLines);
      }

      if (!world.switchConnectionPlayer(connectionId, switchedPlayer)) {
        throw new IllegalStateException("stored login switched to a missing player");
      }
      if (returnedPlayerAssociation) {
        world
            .verb(connection.listenerHandler, "user_connected")
            .ifPresent(
                userConnected ->
                    executeStored(
                        userConnected,
                        verbLocals(
                            connection.listenerHandler,
                            switchedPlayer,
                            -1,
                            "user_connected",
                            new ListValue(List.of(new ObjectValue(switchedPlayer))),
                            "")));
      }
      return connection.printMessages ? List.of("*** Connected ***") : state.output();
    }
    return state.output();
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
      synchronized (this) {
        ConnectionState connection = connections.get(connectionId);
        if (connection != openedConnection || world.connectionPlayer(connectionId).orElse(0) >= 0) {
          return;
        }

        MooValue serverOptions =
            world.readObjectProperty(connection.listenerHandler, "server_options").orElse(null);
        if (serverOptions == null && connection.listenerHandler != 0) {
          serverOptions = world.readObjectProperty(0, "server_options").orElse(null);
        }
        MooValue configuredTimeout =
            serverOptions instanceof ObjectValue options
                ? world.readObjectProperty(options.value(), "connect_timeout").orElse(null)
                : null;
        long timeoutSeconds;
        if (configuredTimeout == null) {
          timeoutSeconds = 300;
        } else if (configuredTimeout instanceof IntegerValue timeout && timeout.value() > 0) {
          timeoutSeconds = timeout.value();
        } else {
          continue;
        }

        long idleSeconds =
            TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - connection.lastActivityNanos);
        if (idleSeconds <= timeoutSeconds) {
          continue;
        }

        world
            .verb(connection.listenerHandler, "user_disconnected")
            .ifPresent(
                disconnected ->
                    executeStored(
                        disconnected,
                        verbLocals(
                            connection.listenerHandler,
                            connectionId,
                            -1,
                            "user_disconnected",
                            new ListValue(List.of(new ObjectValue(connectionId))),
                            "")));
        List<String> lines = new ArrayList<>();
        if (connection.printMessages) {
          MooValue message = null;
          MooValue listenerOptions =
              world.readObjectProperty(connection.listenerHandler, "server_options").orElse(null);
          if (listenerOptions instanceof ObjectValue options) {
            message = world.readObjectProperty(options.value(), "timeout_msg").orElse(null);
          }
          if (message == null && connection.listenerHandler != 0) {
            MooValue rootOptions = world.readObjectProperty(0, "server_options").orElse(null);
            if (rootOptions instanceof ObjectValue options) {
              message = world.readObjectProperty(options.value(), "timeout_msg").orElse(null);
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
        listenerControl.ifPresent(control -> control.bootConnection(connectionId, lines));
        connections.remove(connectionId);
        world.closeConnection(connectionId);
        return;
      }
    }
  }

  private VmState executeStored(WorldVerb verb, Map<String, MooValue> locals) {
    BytecodeProgram program = compiler.compile(MooParser.parse(verb.programSource()));
    VmState root = new VmState(locals, verb.owner());
    Map<VmState, BytecodeProgram> programs = new LinkedHashMap<>();
    Map<VmState, Long> timedTasks = new LinkedHashMap<>();
    Map<VmState, CompletableFuture<MooValue>> hostTasks = new LinkedHashMap<>();
    List<VmState> runnable = new ArrayList<>();
    programs.put(root, program);
    runnable.add(root);

    while (root.outcome() != VmState.Outcome.RETURNED
        && root.outcome() != VmState.Outcome.ERRORED) {
      while (!runnable.isEmpty()) {
        VmState task = runnable.removeFirst();
        BytecodeProgram taskProgram = programs.get(task);
        if (taskProgram == null) {
          throw new IllegalStateException("runnable task has no program");
        }
        vm.execute(taskProgram, task, world, builtins);
        applyConnectionOptionRequests(task);
        applyBootPlayerTargets(task);
        closeRecycledPlayerConnections();
        while (task.outcome() == VmState.Outcome.FORKED) {
          VmState.ForkRequest request = task.forkRequest().orElseThrow();
          VmState child = new VmState(request.locals(), request.programmer());
          programs.put(child, request.program());
          if (request.delaySeconds() == 0.0) {
            runnable.add(child);
          } else {
            long delayNanos = Math.max(0L, Math.round(request.delaySeconds() * 1_000_000_000.0));
            timedTasks.put(child, Math.addExact(System.nanoTime(), delayNanos));
          }
          task.continueAfterFork();
          vm.execute(taskProgram, task, world, builtins);
          applyConnectionOptionRequests(task);
          applyBootPlayerTargets(task);
          closeRecycledPlayerConnections();
        }
        if (task.outcome() == VmState.Outcome.SUSPENDED) {
          if (task.suspensionDelaySeconds().isPresent()) {
            long delayNanos =
                Math.max(
                    0L, Math.round(task.suspensionDelaySeconds().orElseThrow() * 1_000_000_000.0));
            timedTasks.put(task, Math.addExact(System.nanoTime(), delayNanos));
          } else {
            hostTasks.put(task, task.hostResult().orElseThrow());
          }
        } else if (task != root) {
          programs.remove(task);
        }
      }

      if (root.outcome() == VmState.Outcome.RETURNED || root.outcome() == VmState.Outcome.ERRORED) {
        break;
      }

      long now = System.nanoTime();
      List<VmState> timedReady = new ArrayList<>();
      for (Map.Entry<VmState, Long> entry : timedTasks.entrySet()) {
        if (entry.getValue() - now <= 0) {
          timedReady.add(entry.getKey());
        }
      }
      for (VmState task : timedReady) {
        timedTasks.remove(task);
        if (task.outcome() == VmState.Outcome.SUSPENDED) {
          task.resume(new moo.value.MooValue.IntegerValue(0));
        } else if (task.outcome() != VmState.Outcome.RUNNING) {
          throw new IllegalStateException("timed task is neither queued nor suspended");
        }
        runnable.add(task);
      }

      List<VmState> hostReady = new ArrayList<>();
      for (Map.Entry<VmState, CompletableFuture<MooValue>> entry : hostTasks.entrySet()) {
        if (entry.getValue().isDone()) {
          hostReady.add(entry.getKey());
        }
      }
      for (VmState task : hostReady) {
        CompletableFuture<MooValue> result = hostTasks.remove(task);
        if (result == null) {
          throw new IllegalStateException("completed host task has no result");
        }
        task.resume(result.join());
        runnable.add(task);
      }
      if (!runnable.isEmpty()) {
        continue;
      }

      long earliestWake = Long.MAX_VALUE;
      for (long wake : timedTasks.values()) {
        earliestWake = Math.min(earliestWake, wake);
      }
      long waitNanos =
          earliestWake == Long.MAX_VALUE
              ? Long.MAX_VALUE
              : Math.max(1L, earliestWake - System.nanoTime());
      try {
        if (!hostTasks.isEmpty()) {
          CompletableFuture<?>[] pending = hostTasks.values().toArray(CompletableFuture<?>[]::new);
          CompletableFuture<Object> nextHost = CompletableFuture.anyOf(pending);
          if (waitNanos == Long.MAX_VALUE) {
            nextHost.get();
          } else {
            nextHost.get(waitNanos, TimeUnit.NANOSECONDS);
          }
        } else if (waitNanos != Long.MAX_VALUE) {
          TimeUnit.NANOSECONDS.sleep(waitNanos);
        } else {
          throw new IllegalStateException("suspended runtime has no wake source");
        }
      } catch (TimeoutException ignored) {
        // The next timed task is now eligible and is selected at the top of the loop.
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("runtime interrupted while waiting for a MOO task", error);
      } catch (ExecutionException error) {
        throw new IllegalStateException("host operation completed exceptionally", error.getCause());
      }
    }
    return root;
  }

  private void applyConnectionOptionRequests(VmState task) {
    for (ConnectionOptionRequest request : task.drainConnectionOptionRequests()) {
      ConnectionState connection = connections.get(request.target());
      if (connection == null) {
        for (Map.Entry<Long, ConnectionState> entry : connections.entrySet()) {
          if (world.connectionPlayer(entry.getKey()).orElse(Long.MIN_VALUE) == request.target()) {
            connection = entry.getValue();
            break;
          }
        }
      }
      if (connection == null) {
        continue;
      }
      if (request.option() == ConnectionOption.HOLD_INPUT) {
        connection.holdInput = request.value().isTruthy();
      } else if (request.value() instanceof StringValue command && command.length() > 0) {
        connection.flushCommand =
            Optional.of(new String(command.bytes(), StandardCharsets.ISO_8859_1));
      } else {
        connection.flushCommand = Optional.empty();
      }
    }
  }

  private void applyBootPlayerTargets(VmState task) {
    for (long target : task.drainBootPlayerTargets()) {
      long connectionId = target;
      ConnectionState connection = connections.get(connectionId);
      if (connection == null) {
        for (Map.Entry<Long, ConnectionState> entry : connections.entrySet()) {
          if (world.connectionPlayer(entry.getKey()).orElse(Long.MIN_VALUE) == target) {
            connectionId = entry.getKey();
            connection = entry.getValue();
            break;
          }
        }
      }
      if (connection == null) {
        continue;
      }

      long disconnectedPlayer = world.connectionPlayer(connectionId).orElse(target);
      connections.remove(connectionId);
      world.closeConnection(connectionId);
      long listenerHandler = connection.listenerHandler;
      world
          .verb(listenerHandler, "user_disconnected")
          .ifPresent(
              disconnected ->
                  executeStored(
                      disconnected,
                      verbLocals(
                          listenerHandler,
                          disconnectedPlayer,
                          -1,
                          "user_disconnected",
                          new ListValue(List.of(new ObjectValue(disconnectedPlayer))),
                          "")));

      List<String> lines = new ArrayList<>();
      if (connection.printMessages) {
        MooValue message = null;
        MooValue listenerOptions =
            world.readObjectProperty(listenerHandler, "server_options").orElse(null);
        if (listenerOptions instanceof ObjectValue options) {
          message = world.readObjectProperty(options.value(), "boot_msg").orElse(null);
        }
        if (message == null && listenerHandler != 0) {
          MooValue rootOptions = world.readObjectProperty(0, "server_options").orElse(null);
          if (rootOptions instanceof ObjectValue options) {
            message = world.readObjectProperty(options.value(), "boot_msg").orElse(null);
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
      long bootedConnectionId = connectionId;
      listenerControl.ifPresent(control -> control.bootConnection(bootedConnectionId, lines));
    }
  }

  private void closeRecycledPlayerConnections() {
    for (Map.Entry<Long, ConnectionState> entry : new ArrayList<>(connections.entrySet())) {
      long connectionId = entry.getKey();
      long player = world.connectionPlayer(connectionId).orElse(Long.MIN_VALUE);
      if (player < 0 || world.object(player).isPresent()) {
        continue;
      }

      ConnectionState connection = entry.getValue();
      connections.remove(connectionId);
      world.closeConnection(connectionId);
      List<String> lines = new ArrayList<>();
      if (connection.printMessages) {
        MooValue message = null;
        MooValue listenerOptions =
            world.readObjectProperty(connection.listenerHandler, "server_options").orElse(null);
        if (listenerOptions instanceof ObjectValue options) {
          message = world.readObjectProperty(options.value(), "recycle_msg").orElse(null);
        }
        if (message == null && connection.listenerHandler != 0) {
          MooValue rootOptions = world.readObjectProperty(0, "server_options").orElse(null);
          if (rootOptions instanceof ObjectValue options) {
            message = world.readObjectProperty(options.value(), "recycle_msg").orElse(null);
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
      listenerControl.ifPresent(control -> control.bootConnection(connectionId, lines));
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

  private ConnectionState requireConnection(long connectionId) {
    ConnectionState connection = connections.get(connectionId);
    if (connection == null) {
      throw new IllegalArgumentException("unknown connection #" + connectionId);
    }
    return connection;
  }

  private static StringValue encode(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }

  private static final class ConnectionState {
    private final long listenerHandler;
    private final boolean printMessages;
    private long lastActivityNanos = System.nanoTime();
    private boolean holdInput;
    private Optional<String> flushCommand = Optional.empty();
    private final List<String> pendingInput = new ArrayList<>();
    private Optional<String> prefix = Optional.empty();
    private Optional<String> suffix = Optional.empty();
    private long programmingObject = -1;
    private int programmingVerbIndex = -1;
    private final StringBuilder programmingSource = new StringBuilder();

    private ConnectionState(long listenerHandler, boolean printMessages) {
      this.listenerHandler = listenerHandler;
      this.printMessages = printMessages;
    }
  }
}
