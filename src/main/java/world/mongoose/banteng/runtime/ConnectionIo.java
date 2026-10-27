package world.mongoose.banteng.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import world.mongoose.banteng.builtin.BuiltinCall;
import world.mongoose.banteng.builtin.BuiltinCatalog.ConnectionOption;
import world.mongoose.banteng.builtin.BuiltinCatalog.ConnectionOptionRequest;
import world.mongoose.banteng.builtin.BuiltinCatalog.ForcedInputRequest;
import world.mongoose.banteng.builtin.BuiltinCatalog.NotificationRequest;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.BooleanValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.vm.MooVm.RootVerbCall;
import world.mongoose.banteng.world.WorldTxn.ResolvedVerb;
import world.mongoose.banteng.vm.VmState;
import world.mongoose.banteng.runtime.MooRuntime.AttemptContext;
import world.mongoose.banteng.runtime.MooRuntime.ConnectionState;
import world.mongoose.banteng.runtime.MooRuntime.RuntimeStep;
import world.mongoose.banteng.runtime.MooRuntime.RuntimeContinuation;
import world.mongoose.banteng.runtime.MooRuntime.RuntimeTransition;
import world.mongoose.banteng.runtime.MooRuntime.RuntimeEffect;
import world.mongoose.banteng.runtime.MooRuntime.ReadRegistration;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import world.mongoose.banteng.builtin.BuiltinCatalog.ListenerControl;

/** Connection input/options, retaining publication's single runtime monitor for pending reads. */
final class ConnectionIo {
  private final MooRuntime runtime;
  private final PublicationScheduler scheduler;
  private final Optional<ListenerControl> listenerControl;
  @GuardedBy("runtime")
  private final Map<Long, CompletableFuture<BuiltinResult>> pendingReads = new LinkedHashMap<>();

  ConnectionIo(MooRuntime runtime, PublicationScheduler scheduler, Optional<ListenerControl> listenerControl) {
    this.runtime = runtime;
    this.scheduler = scheduler;
    this.listenerControl = listenerControl;
  }

  BuiltinResult read(AttemptContext context, BuiltinCall call) {
    if (call.arguments().isEmpty() && !scheduler.isLastInputTask(call.taskId())) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    long target;
    if (call.arguments().isEmpty()) {
      OptionalLong inputPlayer = scheduler.lastInputPlayer(call.taskId());
      if (inputPlayer.isEmpty()) {
        return BuiltinResult.error(ErrorValue.E_PERM);
      }
      target = inputPlayer.orElseThrow();
    } else {
      target = ((ObjectValue) call.arguments().getFirst()).value();
    }
    long connectionId = target;
    ConnectionState connection = context.sessions.get(connectionId);
    if (connection == null) {
      for (Map.Entry<Long, ConnectionState> entry : context.sessions.entrySet()) {
        if (context.connections.connectionPlayer(entry.getKey()).orElse(Long.MIN_VALUE)
            == target) {
          connectionId = entry.getKey();
          connection = entry.getValue();
          break;
        }
      }
    }
    if (connection == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    if (!connection.pendingInput.isEmpty()) {
      return BuiltinResult.value(StringValue.of(connection.pendingInput.removeFirst()));
    }
    if (call.arguments().size() == 2 && call.arguments().get(1).isTruthy()) {
      return BuiltinResult.value(new IntegerValue(0));
    }
    CompletableFuture<BuiltinResult> completion = new CompletableFuture<>();
    context.readRegistrations.add(new ReadRegistration(connectionId, completion));
    long registeredConnectionId = connectionId;
    return BuiltinResult.hostWork(
        () -> awaitRead(registeredConnectionId, completion));
  }

  BuiltinResult awaitRead(
      long connectionId, CompletableFuture<BuiltinResult> completion) throws Exception {
    try {
      return completion.get();
    } finally {
      synchronized (runtime) {
        pendingReads.remove(connectionId, completion);
      }
    }
  }

  boolean hasPendingRead(long connectionId) {
    synchronized (runtime) {
      return pendingReads.containsKey(connectionId);
    }
  }

  boolean deliverPendingRead(long connectionId, String line) {
    synchronized (runtime) {
      CompletableFuture<BuiltinResult> completion = pendingReads.remove(connectionId);
      if (completion == null) {
        return false;
      }
      completion.complete(BuiltinResult.value(StringValue.of(line)));
      return true;
    }
  }

  void applyConnectionOptionRequests(AttemptContext context, VmState task) {
    for (ConnectionOptionRequest request : task.drainConnectionOptionRequests()) {
      applyConnectionOption(context, request.target(), request.option(), request.value());
    }
  }

  BuiltinResult applyConnectionOption(AttemptContext context,
      long target, ConnectionOption option, MooValue value) {
    long connectionId = target;
    ConnectionState connection = context.sessions.get(target);
    if (connection == null) {
      for (Map.Entry<Long, ConnectionState> entry : context.sessions.entrySet()) {
        if (context.connections.connectionPlayer(entry.getKey()).orElse(Long.MIN_VALUE) == target) {
          connectionId = entry.getKey();
          connection = entry.getValue();
          break;
        }
      }
    }
    if (connection == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    if (option == ConnectionOption.HOLD_INPUT) {
      boolean release = connection.holdInput && !value.isTruthy();
      connection.holdInput = value.isTruthy();
      if (release) {
        List<String> pendingInput = List.copyOf(connection.pendingInput);
        connection.pendingInput.clear();
        for (String line : pendingInput) {
          context.effects.add(RuntimeEffect.input(connectionId, line));
        }
      }
    } else if (option == ConnectionOption.DISABLE_OOB) {
      connection.disableOob = value.isTruthy();
    } else if (option == ConnectionOption.BINARY) {
      connection.binary = value.isTruthy();
      long binaryConnectionId = connectionId;
      if (listenerControl.isPresent()) {
        context.effects.add(RuntimeEffect.binary(binaryConnectionId, connection.binary));
      }
    } else if (option == ConnectionOption.KEEP_ALIVE) {
      connection.keepAlive = connection.keepAlive.with(value);
      context.effects.add(RuntimeEffect.keepAlive(connectionId, connection.keepAlive));
    } else if (option == ConnectionOption.INTRINSIC_COMMANDS) {
      context.connections.setIntrinsicCommands(connectionId, (ListValue) value);
    } else if (value instanceof StringValue command && command.length() > 0) {
      connection.flushCommand = Optional.of(command.text());
    } else {
      connection.flushCommand = Optional.empty();
    }
    return BuiltinResult.value(new IntegerValue(0));
  }

  BuiltinResult connectionOptions(AttemptContext context,
      List<MooValue> arguments, WorldTxn world, long programmer) {
    long target = ((ObjectValue) arguments.getFirst()).value();
    OptionalLong connectionId = context.connections.connectionId(target);
    if (connectionId.isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    ConnectionState connection = context.sessions.get(connectionId.orElseThrow());
    if (connection == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    boolean wizard =
        world.isWizard(programmer);
    if (target != programmer && !wizard) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    if (arguments.size() == 2) {
      String option =
          ((StringValue) arguments.get(1)).text()
              .toLowerCase(java.util.Locale.ROOT);
      MooValue value =
          switch (option) {
            case "binary" -> new IntegerValue(connection.binary ? 1 : 0);
            case "flush-command" -> StringValue.of(connection.flushCommand.orElse(""));
            case "hold-input" -> new IntegerValue(connection.holdInput ? 1 : 0);
            case "disable-oob" -> new IntegerValue(connection.disableOob ? 1 : 0);
            case "keep-alive" -> connection.keepAlive.toValue();
            case "intrinsic-commands" ->
                context.connections
                    .intrinsicCommands(target)
                    .orElseThrow();
            default -> null;
          };
      return value == null
          ? BuiltinResult.error(ErrorValue.E_INVARG)
          : BuiltinResult.value(value);
    }
    List<MooValue> options = new ArrayList<>();
    options.add(connectionOptionPair("binary", new IntegerValue(connection.binary ? 1 : 0)));
    options.add(
        connectionOptionPair(
            "flush-command", StringValue.of(connection.flushCommand.orElse(""))));
    options.add(connectionOptionPair("hold-input", new IntegerValue(connection.holdInput ? 1 : 0)));
    options.add(connectionOptionPair("disable-oob", new IntegerValue(connection.disableOob ? 1 : 0)));
    options.add(connectionOptionPair("keep-alive", connection.keepAlive.toValue()));
    options.add(
        connectionOptionPair(
            "intrinsic-commands",
            context.connections
                .intrinsicCommands(target)
                .orElseThrow()));
    return BuiltinResult.value(new ListValue(options));
  }

  BuiltinResult connectionSeconds(AttemptContext context, BuiltinCall call, boolean connected) {
    long target = ((ObjectValue) call.arguments().getFirst()).value();
    OptionalLong connectionId = context.connections.connectionId(target);
    if (connectionId.isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    ConnectionState connection = context.sessions.get(connectionId.orElseThrow());
    if (connection == null || (connected && connection.connectedNanos.isEmpty())) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    long since =
        connected ? connection.connectedNanos.orElseThrow() : connection.lastActivityNanos;
    return BuiltinResult.value(new IntegerValue(runtime.elapsedSeconds(since)));
  }

  BuiltinResult outputDelimiters(AttemptContext context,
      List<MooValue> arguments, WorldTxn world, long programmer) {
    long target = ((ObjectValue) arguments.getFirst()).value();
    boolean wizard =
        world.isWizard(programmer);
    if (target != programmer && !wizard) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    OptionalLong connectionId = context.connections.connectionId(target);
    if (connectionId.isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    ConnectionState connection = context.sessions.get(connectionId.orElseThrow());
    if (connection == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    return BuiltinResult.value(
        new ListValue(
            List.of(
                StringValue.of(connection.prefix.orElse("")), StringValue.of(connection.suffix.orElse("")))));
  }

  BuiltinResult queueInfo(AttemptContext context,
      List<MooValue> arguments, WorldTxn world, long programmer, TaskRegistry taskRegistry) {
    if (arguments.isEmpty()) {
      Set<Long> players = new LinkedHashSet<>();
      for (Map.Entry<Long, ConnectionState> entry : context.sessions.entrySet()) {
        players.add(context.connections.connectionPlayer(entry.getKey()).orElse(entry.getKey()));
      }
      players.addAll(taskRegistry.queuePlayers());
      return BuiltinResult.value(
          new ListValue(players.stream().map(ObjectValue::new).map(MooValue.class::cast).toList()));
    }

    long target = ((ObjectValue) arguments.getFirst()).value();
    long backgroundTasks = taskRegistry.backgroundTaskCount(target);
    boolean wizard =
        world.isWizard(programmer);
    if (!wizard) {
      return BuiltinResult.value(new IntegerValue(backgroundTasks));
    }

    Map.Entry<Long, ConnectionState> selected = null;
    for (Map.Entry<Long, ConnectionState> entry : context.sessions.entrySet()) {
      if (entry.getKey() == target
          || context.connections.connectionPlayer(entry.getKey()).orElse(Long.MIN_VALUE)
              == target) {
        selected = entry;
        break;
      }
    }
    if (selected == null && backgroundTasks == 0) {
      return BuiltinResult.value(new IntegerValue(0));
    }

    ConnectionState connection = selected == null ? null : selected.getValue();
    long player =
        selected == null
            ? target
            : context.connections.connectionPlayer(selected.getKey()).orElse(target);
    long inputLength =
        connection == null
            ? 0
            : connection.pendingInput.stream().mapToLong(String::length).sum();
    Map<MooValue, MooValue> values = new LinkedHashMap<>();
    values.put(StringValue.of("player"), new ObjectValue(player));
    values.put(
        StringValue.of("handler"),
        new ObjectValue(connection == null ? -1 : connection.listenerHandler));
    values.put(StringValue.of("connected"), BooleanValue.of(connection != null));
    values.put(StringValue.of("total_input_length"), new IntegerValue(inputLength));
    values.put(StringValue.of("last_input_task_id"), new IntegerValue(scheduler.lastInputTask(player)));
    values.put(
        StringValue.of("input_suspended"),
        BooleanValue.of(connection != null && connection.holdInput));
    values.put(StringValue.of("usage"), new IntegerValue(0));
    values.put(StringValue.of("num_bg_tasks"), new IntegerValue(backgroundTasks));
    // Toast's queue_info() exposes hold_input from its zero-valued usage field.
    values.put(StringValue.of("hold_input"), BooleanValue.FALSE);
    values.put(
        StringValue.of("disable_oob"),
        BooleanValue.of(connection != null && connection.disableOob));
    values.put(StringValue.of("reading"), BooleanValue.FALSE);
    values.put(StringValue.of("parsing"), BooleanValue.FALSE);
    values.put(StringValue.of("reading_task_id"), new IntegerValue(0));
    return BuiltinResult.value(new MapValue(values));
  }

  BuiltinResult flushInput(AttemptContext context,
      List<MooValue> arguments, WorldTxn world, long programmer) {
    long target = ((ObjectValue) arguments.getFirst()).value();
    boolean wizard =
        world.isWizard(programmer);
    if (target != programmer && !wizard) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }

    Map.Entry<Long, ConnectionState> selected = null;
    for (Map.Entry<Long, ConnectionState> entry : context.sessions.entrySet()) {
      if (entry.getKey() == target
          || context.connections.connectionPlayer(entry.getKey()).orElse(Long.MIN_VALUE)
              == target) {
        selected = entry;
        break;
      }
    }
    if (selected == null) {
      return BuiltinResult.value(new IntegerValue(0));
    }

    ConnectionState connection = selected.getValue();
    List<String> pending = List.copyOf(connection.pendingInput);
    connection.pendingInput.clear();
    if (arguments.size() == 2 && arguments.get(1).isTruthy()) {
      List<String> output = new ArrayList<>();
      if (pending.isEmpty()) {
        output.add(">> No pending input to flush...");
      } else {
        output.add(">> Flushing the following pending input:");
        pending.forEach(line -> output.add(">>     " + line));
        output.add(">> (Done flushing)");
      }
      context.effects.add(RuntimeEffect.write(selected.getKey(), output));
    }
    return BuiltinResult.value(new IntegerValue(0));
  }

  static ListValue connectionOptionPair(String name, MooValue value) {
    return new ListValue(List.of(StringValue.of(name), value));
  }

  void applyForcedInputRequests(AttemptContext context, VmState task) {
    for (ForcedInputRequest request : task.drainForcedInputRequests()) {
      long connectionId = request.target();
      if (!context.sessions.containsKey(connectionId)) {
        for (Map.Entry<Long, ConnectionState> entry : context.sessions.entrySet()) {
          if (context.connections.connectionPlayer(entry.getKey()).orElse(Long.MIN_VALUE)
              == request.target()) {
            connectionId = entry.getKey();
            break;
          }
        }
      }
      if (!context.sessions.containsKey(connectionId)) {
        continue;
      }
      context.effects.add(RuntimeEffect.input(connectionId, request.line()));
    }
  }

  void applyNotificationRequests(AttemptContext context, VmState task, long taskPlayer) {
    for (NotificationRequest request : task.drainNotificationRequests()) {
      long notificationPlayer =
          context.connections
              .connectionPlayer(request.connectionId())
              .orElse(request.connectionId());
      boolean ordinaryCurrentPlayerOutput =
          notificationPlayer == taskPlayer && !request.noFlush() && !request.noNewline();
      if (ordinaryCurrentPlayerOutput || listenerControl.isEmpty()) {
        task.stageRuntimeOutput(request.line());
      } else {
        context.effects
            .add(
                RuntimeEffect.notify(
                    request.connectionId(),
                    request.line(),
                    request.noFlush(),
                    request.noNewline()));
      }
    }
  }

  void applyBootPlayerTargets(AttemptContext context, VmState task, long taskPlayer) {
    for (long target : task.drainBootPlayerTargets()) {
      long connectionId = target;
      ConnectionState connection = context.sessions.get(connectionId);
      if (connection == null) {
        for (Map.Entry<Long, ConnectionState> entry : context.sessions.entrySet()) {
          if (context.connections.connectionPlayer(entry.getKey()).orElse(Long.MIN_VALUE)
              == target) {
            connectionId = entry.getKey();
            connection = entry.getValue();
            break;
          }
        }
      }
      if (connection == null) {
        continue;
      }

      long disconnectedPlayer = context.connections.connectionPlayer(connectionId).orElse(target);
      if (disconnectedPlayer == taskPlayer) {
        long outputConnectionId = connectionId;
        if (listenerControl.isPresent()) {
          context.effects.add(RuntimeEffect.write(outputConnectionId, task.output()));
        }
      }
      long listenerHandler = connection.listenerHandler;
      List<String> lines = new ArrayList<>();
      if (connection.printMessages) {
        lines.addAll(runtime.serverMessage(context, listenerHandler, "boot_msg", "*** Disconnected ***"));
      }
      context.sessions.remove(connectionId);
      context.connections.closeConnection(connectionId);
      Optional<ResolvedVerb> disconnected = context.world.resolveVerb(listenerHandler, "user_disconnected", true);
      if (disconnected.isPresent()) {
        context.spawnedSteps
            .add(
                runtime.startStored(context,
                    disconnected.orElseThrow(),
                    new RootVerbCall(
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
        finishBootPlayer(context, connectionId, lines);
      }
    }
  }

  RuntimeStep finishBootPlayer(AttemptContext context, long connectionId, List<String> lines) {
    if (listenerControl.isPresent()) {
      context.effects.add(RuntimeEffect.boot(connectionId, lines));
    }
    return RuntimeStep.returned(List.of());
  }

  void closeRecycledPlayerConnections(AttemptContext context) {
    for (Map.Entry<Long, ConnectionState> entry : new ArrayList<>(context.sessions.entrySet())) {
      long connectionId = entry.getKey();
      long player = context.connections.connectionPlayer(connectionId).orElse(Long.MIN_VALUE);
      if (player < 0 || context.world.object(player).isPresent()) {
        continue;
      }

      ConnectionState connection = entry.getValue();
      context.sessions.remove(connectionId);
      context.connections.closeConnection(connectionId);
      List<String> lines = new ArrayList<>();
      if (connection.printMessages) {
        lines.addAll(
            runtime.serverMessage(context, connection.listenerHandler, "recycle_msg", "*** Recycled ***"));
      }
      if (listenerControl.isPresent()) {
        context.effects.add(RuntimeEffect.boot(connectionId, lines));
      }
    }
  }

  void registerReads(List<ReadRegistration> readRegistrations, Set<Long> publishedConnections) {
    synchronized (runtime) {
      for (ReadRegistration registration : readRegistrations) {
        if (!publishedConnections.contains(registration.connectionId())) {
          registration.completion().complete(BuiltinResult.error(ErrorValue.E_INVARG));
        } else if (pendingReads.putIfAbsent(
                registration.connectionId(), registration.completion())
            != null) {
          registration.completion().complete(BuiltinResult.error(ErrorValue.E_INVARG));
        }
      }
    }
  }
}
