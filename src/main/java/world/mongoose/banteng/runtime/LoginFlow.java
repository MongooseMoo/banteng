package world.mongoose.banteng.runtime;

import world.mongoose.banteng.builtin.BuiltinCatalog.ListenerControl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.vm.MooVm.RootVerbCall;
import world.mongoose.banteng.world.WorldTxn.ResolvedVerb;
import world.mongoose.banteng.vm.VmState;
import world.mongoose.banteng.runtime.MooRuntime.AttemptContext;
import world.mongoose.banteng.runtime.MooRuntime.ConnectionState;
import world.mongoose.banteng.runtime.MooRuntime.RuntimeStep;
import world.mongoose.banteng.runtime.MooRuntime.RuntimeContinuation;
import world.mongoose.banteng.runtime.MooRuntime.RuntimeTransition;
import world.mongoose.banteng.runtime.MooRuntime.RuntimeEffect;
import world.mongoose.banteng.runtime.MooRuntime.RuntimeRequest;
import world.mongoose.banteng.runtime.MooRuntime.LoginTimeoutResult;

/** Login association and its committed unauthenticated timeout monitor. */
final class LoginFlow {
  private final MooRuntime runtime;
  private final PublicationScheduler scheduler;
  private final Optional<ListenerControl> listenerControl;

  LoginFlow(MooRuntime runtime, PublicationScheduler scheduler, Optional<ListenerControl> listenerControl) {
    this.runtime = runtime;
    this.scheduler = scheduler;
    this.listenerControl = listenerControl;
  }

  RuntimeStep executeLogin(AttemptContext context, long connectionId, String line) {
    ConnectionState connection = runtime.requireConnection(context, connectionId);
    MooValue serverOptions =
        context.world.readObjectProperty(connection.listenerHandler, "server_options").orElse(null);
    if (serverOptions == null && connection.listenerHandler != 0) {
      serverOptions = context.world.readObjectProperty(0, "server_options").orElse(null);
    }
    MooValue trustedProxies =
        serverOptions instanceof ObjectValue options
            ? context.world.readObjectProperty(options.value(), "trusted_proxies").orElse(null)
            : null;
    MooValue destinationIp =
        context.connections
            .connectionInfo(connectionId)
            .flatMap(info -> info.get(StringValue.of("destination_ip")))
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
      Optional<ResolvedVerb> blank = context.world.resolveVerb(connection.listenerHandler, "do_blank_command", true);
      if (blank.isEmpty()) {
        return RuntimeStep.returned(List.of());
      }
      return runtime.startStored(context,
          blank.orElseThrow(),
          new RootVerbCall(
              connection.listenerHandler,
            connectionId,
            -1,
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
    return executeLoginAuthentication(context, connectionId, line);
  }


  RuntimeStep executeLoginAuthentication(AttemptContext context, long connectionId, String line) {
    ConnectionState connection = runtime.requireConnection(context, connectionId);
    MooValue serverOptions =
        context.world.readObjectProperty(connection.listenerHandler, "server_options").orElse(null);
    if (serverOptions == null && connection.listenerHandler != 0) {
      serverOptions = context.world.readObjectProperty(0, "server_options").orElse(null);
    }
    MooValue trustedProxies =
        serverOptions instanceof ObjectValue options
            ? context.world.readObjectProperty(options.value(), "trusted_proxies").orElse(null)
            : null;
    MooValue destinationIp =
        context.connections
            .connectionInfo(connectionId)
            .flatMap(info -> info.get(StringValue.of("destination_ip")))
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
    ResolvedVerb login = context.world.resolveVerb(connection.listenerHandler, "do_login_command", true).orElseThrow();
    List<MooValue> arguments = new ArrayList<>();
    for (String word : CommandParser.words(loginLine)) {
      arguments.add(StringValue.of(word));
    }
    RootVerbCall locals =
        new RootVerbCall(
            connection.listenerHandler,
            connectionId,
            -1,
            "do_login_command",
            new ListValue(arguments),
            loginLine);
    long maximumObjectIdBeforeLogin = context.world.maximumObjectId();
    return runtime.startStored(context,
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


  RuntimeStep finishLogin(AttemptContext context,
      long connectionId, long maximumObjectIdBeforeLogin, VmState state) {
    ConnectionState connection = runtime.requireConnection(context, connectionId);
    OptionalLong authenticatedPlayer = state.switchedPlayer();
    boolean returnedPlayerAssociation = false;
    if (authenticatedPlayer.isEmpty()
        && state.returnValue().orElse(null) instanceof ObjectValue returnedPlayer
        && context.world.players().contains(returnedPlayer.value())) {
      authenticatedPlayer = OptionalLong.of(returnedPlayer.value());
      returnedPlayerAssociation = true;
    }
    if (authenticatedPlayer.isPresent()) {
      long switchedPlayer = authenticatedPlayer.orElseThrow();
      long existingConnectionId = Long.MIN_VALUE;
      ConnectionState existingConnection = null;
      if (returnedPlayerAssociation) {
        for (Map.Entry<Long, ConnectionState> entry : context.sessions.entrySet()) {
          long candidateConnectionId = entry.getKey();
          if (candidateConnectionId != connectionId
              && context.connections
                      .connectionPlayer(candidateConnectionId)
                      .orElse(Long.MIN_VALUE)
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
        if (sameListener
            && !runtime.switchConnectionPlayer(context, connectionId, switchedPlayer)) {
          throw new IllegalStateException("stored login switched to a missing player");
        }
        List<String> oldLines = new ArrayList<>();
        if (redirectedConnection.printMessages) {
          oldLines.addAll(
              runtime.serverMessage(context,
                  redirectedConnection.listenerHandler,
                  "redirect_from_msg",
                  "*** Redirecting connection to new port ***"));
        }

        List<String> newLines = new ArrayList<>();
        if (connection.printMessages) {
          newLines.addAll(
              runtime.serverMessage(context,
                  connection.listenerHandler,
                  "redirect_to_msg",
                  "*** Redirecting old connection to this port ***"));
        }

        long redirectedConnectionId = existingConnectionId;
        boolean wroteRedirects = listenerControl.isPresent();
        if (wroteRedirects) {
          context.effects.add(RuntimeEffect.write(redirectedConnectionId, oldLines));
          context.effects.add(RuntimeEffect.write(connectionId, newLines));
          context.effects.add(RuntimeEffect.boot(redirectedConnectionId, List.of()));
        }
        context.sessions.remove(existingConnectionId);
        context.connections.closeConnection(existingConnectionId);

        if (sameListener) {
          Optional<ResolvedVerb> userReconnected =
              context.world.resolveVerb(connection.listenerHandler, "user_reconnected", true);
          if (userReconnected.isPresent()) {
            return runtime.startStored(context,
                userReconnected.orElseThrow(),
                new RootVerbCall(
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
        Optional<ResolvedVerb> userDisconnected =
            context.world.resolveVerb(redirectedConnection.listenerHandler, "user_client_disconnected", true);
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
          return runtime.startStored(context,
              userDisconnected.orElseThrow(),
              new RootVerbCall(
                  redirectedConnection.listenerHandler,
                  switchedPlayer,
                  -1,
                  "user_client_disconnected",
                  new ListValue(List.of(new ObjectValue(switchedPlayer))),
                  ""),
              association);
        }
        return associateRedirectedLogin(context, association);
      }

      if (!runtime.switchConnectionPlayer(context, connectionId, switchedPlayer)) {
        throw new IllegalStateException("stored login switched to a missing player");
      }
      if (returnedPlayerAssociation) {
        if (switchedPlayer > maximumObjectIdBeforeLogin) {
          Optional<ResolvedVerb> userCreated = context.world.resolveVerb(connection.listenerHandler, "user_created", true);
          if (userCreated.isPresent()) {
            return runtime.startStored(context,
                userCreated.orElseThrow(),
                new RootVerbCall(
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
                    connection.printMessages ? List.of("*** Connected ***") : state.effects().output(),
                    switchedPlayer,
                    0,
                    false));
          }
        }
        return startConnectedHook(context,
            connectionId,
            switchedPlayer,
            connection.printMessages ? List.of("*** Connected ***") : state.effects().output(),
            RuntimeTransition.LOGIN_EXISTING_USER_CONNECTED_THEN_RETURN,
            false);
      }
      return RuntimeStep.returned(
          connection.printMessages ? List.of("*** Connected ***") : state.effects().output());
    }
    return RuntimeStep.returned(state.effects().output());
  }


  RuntimeStep associateRedirectedLogin(AttemptContext context, RuntimeContinuation continuation) {
    long connectionId = continuation.connectionId();
    long switchedPlayer = continuation.first();
    if (!runtime.switchConnectionPlayer(context, connectionId, switchedPlayer)) {
      throw new IllegalStateException("stored login switched to a missing player");
    }
    return startConnectedHook(context,
        connectionId,
        switchedPlayer,
        continuation.output(),
        RuntimeTransition.LOGIN_NEW_USER_CONNECTED_THEN_RETURN,
        continuation.flag());
  }


  RuntimeStep continueCreatedLogin(AttemptContext context, RuntimeContinuation continuation) {
    return RuntimeStep.returned(continuation.output());
  }


  RuntimeStep startConnectedHook(AttemptContext context,
      long connectionId,
      long player,
      List<String> output,
      RuntimeTransition transition,
      boolean suppressOutput) {
    ConnectionState connection = runtime.requireConnection(context, connectionId);
    Optional<ResolvedVerb> userConnected = context.world.resolveVerb(connection.listenerHandler, "user_connected", true);
    if (userConnected.isEmpty()) {
      return RuntimeStep.returned(suppressOutput ? List.of() : output);
    }
    return runtime.startStored(context,
        userConnected.orElseThrow(),
        new RootVerbCall(
            connection.listenerHandler,
            player,
            -1,
            "user_connected",
            new ListValue(List.of(new ObjectValue(player))),
            ""),
        RuntimeContinuation.after(
            transition, connectionId, "", output, player, 0, suppressOutput));
  }


  void monitorUnauthenticatedConnection(
      long connectionId, ConnectionState openedConnection) {
    while (true) {
      try {
        TimeUnit.MILLISECONDS.sleep(100);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
        return;
      }
      switch (scheduler.submitLoginTimeout(
          RuntimeRequest.timeout(connectionId, openedConnection.generation))) {
        case LoginTimeoutResult.KeepWatching ignored -> { }
        case LoginTimeoutResult.Done ignored -> { return; }
        case LoginTimeoutResult.TimedOut ignored -> { return; }
      }
    }
  }


  LoginTimeoutResult checkLoginTimeoutNow(AttemptContext context, long connectionId, long generation) {
    ConnectionState connection = context.sessions.get(connectionId);
    if (connection == null
        || connection.generation != generation
        || context.connections.connectionPlayer(connectionId).orElse(0) >= 0) {
      return new LoginTimeoutResult.Done();
    }
    MooValue serverOptions =
        context.world.readObjectProperty(connection.listenerHandler, "server_options").orElse(null);
    if (serverOptions == null && connection.listenerHandler != 0) {
      serverOptions = context.world.readObjectProperty(0, "server_options").orElse(null);
    }
    MooValue configuredTimeout =
        serverOptions instanceof ObjectValue options
            ? context.world.readObjectProperty(options.value(), "connect_timeout").orElse(null)
            : null;
    long timeoutSeconds;
    if (configuredTimeout == null) {
      timeoutSeconds = MooRuntime.DEFAULT_CONNECT_TIMEOUT_SECONDS;
    } else if (configuredTimeout instanceof IntegerValue timeout && timeout.value() > 0) {
      timeoutSeconds = timeout.value();
    } else {
      return new LoginTimeoutResult.KeepWatching();
    }
    long idleSeconds =
        runtime.elapsedSeconds(connection.lastActivityNanos);
    if (idleSeconds <= timeoutSeconds) {
      return new LoginTimeoutResult.KeepWatching();
    }
    List<String> lines = new ArrayList<>();
    if (connection.printMessages) {
      lines.addAll(
          runtime.serverMessage(context,
              connection.listenerHandler,
              "timeout_msg",
              "*** Timed-out waiting for login. ***"));
    }
    return new LoginTimeoutResult.TimedOut(lines);
  }


  RuntimeStep executeLoginTimeout(AttemptContext context, long connectionId, long generation) {
    LoginTimeoutResult result = checkLoginTimeoutNow(context, connectionId, generation);
    if (!(result instanceof LoginTimeoutResult.TimedOut timedOut)) {
      return RuntimeStep.loginTimeout(result, List.of());
    }
    List<String> lines = timedOut.lines();
    ConnectionState connection = context.sessions.get(connectionId);
    if (connection == null) {
      throw new IllegalStateException("timed-out connection disappeared within attempt");
    }
    Optional<ResolvedVerb> disconnected =
        context.world.resolveVerb(connection.listenerHandler, "user_disconnected", true);
    if (disconnected.isPresent()) {
      return runtime.startStored(context,
          disconnected.orElseThrow(),
          new RootVerbCall(
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
    return finishLoginTimeout(context, connectionId, lines);
  }


  RuntimeStep finishLoginTimeout(AttemptContext context, long connectionId, List<String> lines) {
    if (listenerControl.isPresent()) {
      context.effects.add(RuntimeEffect.boot(connectionId, lines));
    }
    context.sessions.remove(connectionId);
    context.connections.closeConnection(connectionId);
    return RuntimeStep.loginTimeout(new LoginTimeoutResult.TimedOut(lines), List.of());
  }

}
