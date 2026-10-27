package world.mongoose.banteng.vm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import world.mongoose.banteng.builtin.BuiltinCall;
import world.mongoose.banteng.builtin.BuiltinHosts;
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinCatalog.ConnectionOptionRequest;
import world.mongoose.banteng.builtin.BuiltinCatalog.ForcedInputRequest;
import world.mongoose.banteng.builtin.BuiltinCatalog.NotificationRequest;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CheckpointRequest;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.BytecodeProgram.Instruction;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue.WaifValue;
import world.mongoose.banteng.value.ValueSemantics;
import world.mongoose.banteng.vm.VmState.Frame;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldAnonymousObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldTxn.ResolvedVerb;
import world.mongoose.banteng.world.WorldVerb;

/** Iterative executor for the authorized explicit bytecode state. */
public final class MooVm {
  /** Existing differences in verb locals and activation continuation. */
  public enum VerbCallKind {
    NORMAL, PASS, PROTECTED, HOOK_ACCEPT, HOOK_EXIT, HOOK_ENTER,
    HOOK_RECYCLE, HOOK_ANONYMOUS_RECYCLE, HOOK_INITIALIZE, ROOT
  }

  /** Explicit command fields supplied only by command dispatch. */
  public record CommandArguments(
      long dobj, String dobjstr, String prepstr, long iobj, String iobjstr) {}

  /** Immutable inputs to a root verb; execution limits remain on its task state. */
  public record RootVerbCall(
      MooValue receiver, long player, long caller, String verb, ListValue arguments,
      String argstr, Optional<CommandArguments> command) {
    public RootVerbCall(long receiver, long player, long caller, String verb,
        ListValue arguments, String argstr) {
      this(new ObjectValue(receiver), player, caller, verb, arguments, argstr, Optional.empty());
    }
  }

  /** Builds every stored verb's locals and enters it through the single frame push. */
  public static boolean callVerb(
      VmState state, MooValue receiver, ResolvedVerb resolution, ListValue arguments,
      String name, VerbCallKind kind, Optional<MooValue> lifecycleTarget,
      Optional<BuiltinResult.Move> move, Optional<RootVerbCall> root) {
    Frame caller = kind == VerbCallKind.ROOT ? null : state.currentFrame();
    Map<String, MooValue> locals = kind == VerbCallKind.PASS
        ? new LinkedHashMap<>(Objects.requireNonNull(caller).namedLocals()) : new LinkedHashMap<>();
    if (kind == VerbCallKind.ROOT) {
      RootVerbCall context = root.orElseThrow();
      locals.put("player", new ObjectValue(context.player()));
      locals.put("caller", new ObjectValue(context.caller()));
      locals.put("argstr", StringValue.of(context.argstr()));
      context.command().ifPresent(command -> {
        locals.put("dobj", new ObjectValue(command.dobj()));
        locals.put("dobjstr", StringValue.of(command.dobjstr()));
        locals.put("prepstr", StringValue.of(command.prepstr()));
        locals.put("iobj", new ObjectValue(command.iobj()));
        locals.put("iobjstr", StringValue.of(command.iobjstr()));
      });
    } else if (kind == VerbCallKind.PASS) {
      locals.put("caller", receiver);
    } else {
      Frame parent = Objects.requireNonNull(caller);
      locals.put("player", parent.localOrDefault(5, new ObjectValue(-1)));
      boolean recycle = kind == VerbCallKind.HOOK_RECYCLE
          || kind == VerbCallKind.HOOK_ANONYMOUS_RECYCLE;
      locals.put("caller", recycle
          ? parent.localOrDefault(6, new ObjectValue(-1)) : parent.receiver);
      locals.put("argstr", kind == VerbCallKind.NORMAL
          ? parent.localOrDefault(10, StringValue.of("")) : StringValue.of(""));
      if (kind == VerbCallKind.NORMAL) {
        for (String field : List.of("dobj", "iobj")) {
          locals.put(field, parent.localOrDefault(verbFieldSlot(field), new ObjectValue(-1)));
        }
        for (String field : List.of("dobjstr", "prepstr", "iobjstr")) {
          locals.put(field, parent.localOrDefault(verbFieldSlot(field), StringValue.of("")));
        }
      }
    }
    if (kind != VerbCallKind.PASS) {
      locals.put("this", receiver);
      locals.put("verb", StringValue.of(name.startsWith(":") ? name.substring(1) : name));
    }
    locals.put("args", arguments);
    VmState.ReturnMode mode = switch (kind) {
      case ROOT -> VmState.ReturnMode.ROOT;
      case HOOK_ACCEPT -> VmState.ReturnMode.MOVE_ACCEPT;
      case HOOK_EXIT -> VmState.ReturnMode.MOVE_EXIT;
      case HOOK_ENTER -> VmState.ReturnMode.MOVE_ENTER;
      default -> VmState.ReturnMode.VERB;
    };
    WorldVerb verb = resolution.verb();
    return state.pushFrame(verb.program(), locals, new VmState.FrameOrigin(
        mode, verb.owner(), receiver, resolution.location(), name, verb.names(),
        kind == VerbCallKind.HOOK_INITIALIZE ? lifecycleTarget : Optional.empty(),
        kind == VerbCallKind.HOOK_RECYCLE
            ? OptionalLong.of(((ObjectValue) lifecycleTarget.orElseThrow()).value()) : OptionalLong.empty(),
        move.isPresent() ? OptionalLong.of(move.orElseThrow().object()) : OptionalLong.empty(),
        move.isPresent() ? OptionalLong.of(move.orElseThrow().destination()) : OptionalLong.empty(),
        move.isPresent() ? OptionalLong.of(move.orElseThrow().position()) : OptionalLong.empty(),
        kind == VerbCallKind.HOOK_ANONYMOUS_RECYCLE
            ? Optional.of((AnonymousObjectValue) receiver) : Optional.empty(), verb.perms().debug()));
  }

  private static final Set<BytecodeProgram.Opcode> COUNTED_OPCODES =
      Set.of(
          BytecodeProgram.Opcode.LIST_EXTEND,
          BytecodeProgram.Opcode.STORE_LOCAL,
          BytecodeProgram.Opcode.GET_PROPERTY,
          BytecodeProgram.Opcode.SET_PROPERTY,
          BytecodeProgram.Opcode.INDEX,
          BytecodeProgram.Opcode.RANGE,
          BytecodeProgram.Opcode.SET_INDEX_LOCAL,
          BytecodeProgram.Opcode.SET_INDEX_PROPERTY,
          BytecodeProgram.Opcode.SET_RANGE_LOCAL,
          BytecodeProgram.Opcode.SET_RANGE_PROPERTY,
          BytecodeProgram.Opcode.CALL,
          BytecodeProgram.Opcode.CALL_VERB,
          BytecodeProgram.Opcode.NEGATE,
          BytecodeProgram.Opcode.NOT,
          BytecodeProgram.Opcode.COMPLEMENT,
          BytecodeProgram.Opcode.ADD,
          BytecodeProgram.Opcode.SUBTRACT,
          BytecodeProgram.Opcode.MULTIPLY,
          BytecodeProgram.Opcode.DIVIDE,
          BytecodeProgram.Opcode.REMAINDER,
          BytecodeProgram.Opcode.POWER,
          BytecodeProgram.Opcode.BITOR,
          BytecodeProgram.Opcode.BITAND,
          BytecodeProgram.Opcode.BITXOR,
          BytecodeProgram.Opcode.BITSHL,
          BytecodeProgram.Opcode.BITSHR,
          BytecodeProgram.Opcode.EQUAL,
          BytecodeProgram.Opcode.NOT_EQUAL,
          BytecodeProgram.Opcode.LESS_THAN,
          BytecodeProgram.Opcode.LESS_THAN_OR_EQUAL,
          BytecodeProgram.Opcode.GREATER_THAN,
          BytecodeProgram.Opcode.GREATER_THAN_OR_EQUAL,
          BytecodeProgram.Opcode.IN,
          BytecodeProgram.Opcode.FORK,
          BytecodeProgram.Opcode.JUMP_IF_FALSE,
          BytecodeProgram.Opcode.JUMP_IF_TRUE,
          BytecodeProgram.Opcode.ENTER_HANDLER,
          BytecodeProgram.Opcode.ITERATE,
          BytecodeProgram.Opcode.ITERATE_RANGE,
          BytecodeProgram.Opcode.SCATTER);

  private static int verbFieldSlot(String field) {
    return switch (field) {
      case "dobj" -> 11; case "dobjstr" -> 12; case "prepstr" -> 13;
      case "iobj" -> 14; case "iobjstr" -> 15;
      default -> throw new IllegalArgumentException(field);
    };
  }

  private final ValueSemantics valueSemantics;

  /** Creates a VM with stock value semantics. */
  public MooVm() {
    this(ValueSemantics.STANDARD);
  }

  /** Creates a VM with the selected value semantics. */
  public MooVm(ValueSemantics valueSemantics) {
    this.valueSemantics = Objects.requireNonNull(valueSemantics, "valueSemantics");
  }

  /** Executes with the one concrete world transaction, builtin catalog, and scheduler task ID. */
  public void execute(
      BytecodeProgram program,
      VmState state,
      WorldTxn world,
      BuiltinCatalog builtins,
      BuiltinHosts hosts,
      long taskId) {
    state.budget().beginSegment();
    state.ensureRoot(program);
    while (state.outcome() == VmState.Outcome.RUNNING) {
      Frame frame = state.currentFrame();
      if (frame.instructionPointer >= frame.program.instructions().size()) {
        LoopOps.routeReturn(state, new IntegerValue(0), world);
        continue;
      }
      executeInstruction(
          frame.program.instructions().get(frame.instructionPointer),
          state,
          world,
          builtins,
          hosts,
          taskId);
    }
  }

  private void executeInstruction(
      Instruction instruction,
      VmState state,
      WorldTxn world,
      BuiltinCatalog builtins,
      BuiltinHosts hosts,
      long taskId) {
    Frame frame = state.currentFrame();
    BytecodeProgram.Opcode opcode = instruction.opcode();
    if ((opcode == BytecodeProgram.Opcode.CALL || (state.budget().remainingTicks() & 1023) == 0)
        && state.budget().secondsExhausted()) {
      state.abortSecondsExhaustion();
      return;
    }
    boolean extended = switch (opcode) {
      case POWER, BITOR, BITAND, BITXOR, BITSHL, BITSHR, COMPLEMENT, SCATTER, ITERATE -> true;
      case ENTER_HANDLER -> instruction.handler().orElseThrow().catchTarget() < 0;
      case JUMP -> instruction.astPath().isPresent();
      default -> false;
    };
    for (int tick = 0; tick < instructionTicks(opcode, instruction, frame); tick++) {
      state.budget().decrementRemainingTicks();
      if (!extended && state.budget().remainingTicks() <= 0) {
        state.abortTickExhaustion();
        return;
      }
    }
    switch (opcode) {
      case PUSH_INTEGER -> {
        frame.operandStack.push(new IntegerValue(instruction.operand().orElseThrow()));
        frame.instructionPointer++;
      }
      case PUSH_FLOAT -> {
        frame.operandStack.push(
            new FloatValue(Double.longBitsToDouble(instruction.operand().orElseThrow())));
        frame.instructionPointer++;
      }
      case PUSH_STRING -> {
        frame.operandStack.push(StringValue.of(instruction.text().orElseThrow()));
        frame.instructionPointer++;
      }
      case PUSH_OBJECT -> {
        frame.operandStack.push(new ObjectValue(instruction.operand().orElseThrow()));
        frame.instructionPointer++;
      }
      case PUSH_ERROR -> {
        frame.operandStack.push(ErrorValue.valueOf(instruction.text().orElseThrow()));
        frame.instructionPointer++;
      }
      case BUILD_LIST -> ListOps.buildList(frame, Math.toIntExact(instruction.operand().orElseThrow()));
      case LIST_APPEND -> ListOps.appendList(frame, state, world);
      case LIST_EXTEND -> ListOps.extendList(frame, state, world);
      case BUILD_MAP -> ListOps.buildMap(frame, state, world, Math.toIntExact(instruction.operand().orElseThrow()));
      case LOAD_LOCAL, PUSH_CLEAR -> PropertyOps.loadLocal(frame,
          Math.toIntExact(instruction.operand().orElseThrow()), opcode == BytecodeProgram.Opcode.PUSH_CLEAR, state, world);
      case STORE_LOCAL -> {
        frame.locals[Math.toIntExact(instruction.operand().orElseThrow())] = frame.operandStack.pop();
        frame.instructionPointer++;
      }
      case DUP -> {
        frame.operandStack.push(frame.operandStack.getFirst());
        frame.instructionPointer++;
      }
      case DUP_PAIR -> {
        MooValue first = frame.operandStack.removeFirst();
        MooValue second = frame.operandStack.getFirst();
        frame.operandStack.addFirst(first);
        frame.operandStack.push(second);
        frame.operandStack.push(first);
        frame.instructionPointer++;
      }
      case POP -> {
        frame.operandStack.pop();
        frame.instructionPointer++;
      }
      case GET_PROPERTY -> PropertyOps.getProperty(frame, state, world);
      case SET_PROPERTY -> PropertyOps.setProperty(frame, state, world);
      case ENTER_INDEX -> {
        frame.indexCollections.push(new VmState.IndexContext(
            frame.operandStack.getFirst(), Optional.empty(), frame.operandStack.size()));
        frame.instructionPointer++;
      }
      case INDEX -> IndexOps.index(frame, state, world, Math.toIntExact(instruction.operand().orElse(0)));
      case RANGE -> IndexOps.range(frame, state, world);
      case FIRST -> IndexOps.boundaryIndex(frame, state, world, false);
      case LAST -> IndexOps.boundaryIndex(frame, state, world, true);
      case SET_INDEX_LOCAL -> IndexOps.setIndexedLocal(frame, state, world, Math.toIntExact(instruction.operand().orElseThrow()),
              instruction.localAux());
      case SET_INDEX_PROPERTY -> IndexOps.setIndexedProperty(frame, state, world,
          Math.toIntExact(instruction.operand().orElse(0)));
      case SET_RANGE_LOCAL -> IndexOps.setRange(frame, state, world, java.util.OptionalInt.of(Math.toIntExact(instruction.operand().orElseThrow())),
              instruction.localAux());
      case SET_RANGE_PROPERTY -> IndexOps.setRange(frame, state, world, java.util.OptionalInt.empty(),
          Math.toIntExact(instruction.operand().orElse(0)));
      case CALL -> invokeBuiltin(instruction, frame, state, world, builtins, hosts, taskId);
      case CALL_VERB -> {
        MooValue argumentsValue = frame.operandStack.pop();
        MooValue nameValue = frame.operandStack.pop();
        MooValue receiverValue = frame.operandStack.pop();
        if (!(argumentsValue instanceof ListValue arguments)
            || !(nameValue instanceof StringValue name)) {
          ErrorOps.raise(state, ErrorValue.E_TYPE, world);
          return;
        }
        String verbName = name.text();
        ResolvedVerb resolution;
        String lookupName;
        if (receiverValue instanceof ObjectValue receiver) {
          lookupName = verbName;
          resolution = world.resolveVerb(receiver.value(), lookupName, true).orElse(null);
        } else if (receiverValue instanceof AnonymousObjectValue anonymous) {
          lookupName = verbName;
          resolution = world.resolveVerb(anonymous, lookupName, true).orElse(null);
        } else if (receiverValue instanceof WaifValue waif) {
          lookupName = verbName.startsWith(":") ? verbName : ":" + verbName;
          resolution = world.resolveVerb(waif.classObject().value(), lookupName, true).orElse(null);
        } else {
          Optional<String> prototypeProperty = primitivePrototypeProperty(receiverValue);
          MooValue prototypeValue =
              prototypeProperty
                  .flatMap(property -> world.readObjectProperty(0, property))
                  .orElse(null);
          if (!(prototypeValue instanceof ObjectValue prototype)) {
            ErrorOps.raise(state, ErrorValue.E_TYPE, world);
            return;
          }
          lookupName = verbName;
          resolution = world.resolveVerb(prototype.value(), lookupName, true).orElse(null);
        }
        if (resolution == null) {
          ErrorOps.raise(state, ErrorValue.E_VERBNF, world);
          return;
        }
        frame.instructionPointer++;
        if (!callVerb(state, receiverValue, resolution, arguments, lookupName,
            VerbCallKind.NORMAL, Optional.empty(), Optional.empty(), Optional.empty())) {
          ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
        }
      }
      case NEGATE -> ArithmeticOps.unaryNegate(frame, state, world);
      case NOT -> ArithmeticOps.logicalNot(frame);
      case COMPLEMENT -> ArithmeticOps.bitwiseComplement(frame, state, world);
      case ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER, POWER ->
          ArithmeticOps.arithmetic(instruction.opcode(), frame, state, world, valueSemantics);
      case BITOR, BITAND, BITXOR, BITSHL, BITSHR ->
          ArithmeticOps.bitwise(instruction.opcode(), frame, state, world);
      case EQUAL, NOT_EQUAL -> ArithmeticOps.equality(instruction.opcode(), frame, valueSemantics);
      case LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL ->
          ArithmeticOps.comparison(instruction.opcode(), frame, state, world, valueSemantics);
      case IN -> ArithmeticOps.membership(frame, state, world, valueSemantics);
      case FORK -> LoopOps.fork(instruction, frame, state, world);
      case JUMP -> frame.instructionPointer = LoopOps.target(instruction);
      case JUMP_IF_FALSE -> LoopOps.conditionalJump(instruction, frame, false);
      case JUMP_IF_TRUE -> LoopOps.conditionalJump(instruction, frame, true);
      case ENTER_HANDLER -> {
        frame.handlers.push(new VmState.ActiveHandler(
            instruction.handler().orElseThrow(), frame.operandStack.size()));
        frame.instructionPointer++;
      }
      case LEAVE_HANDLER -> LoopOps.leaveHandler(frame);
      case END_FINALLY -> LoopOps.endFinally(state, world);
      case ITERATE -> LoopOps.iterate(instruction, frame, state, world);
      case ITERATE_RANGE -> LoopOps.iterateRange(instruction, frame, state, world);
      case LEAVE_LOOP -> {
        frame.loops.remove(Math.toIntExact(instruction.operand().orElseThrow()));
        frame.instructionPointer++;
      }
      case SCATTER -> LoopOps.scatter(instruction, frame, state, world);
      case RETURN -> {
        MooValue value = frame.operandStack.pop();
        frame.instructionPointer++;
        LoopOps.routeReturn(state, value, world);
      }
    }
  }

  private static int instructionTicks(BytecodeProgram.Opcode opcode, Instruction instruction, Frame frame) {
    return switch (opcode) {
      case LIST_APPEND, LIST_EXTEND -> Math.toIntExact(instruction.operand().orElse(1));
      case LOAD_LOCAL, PUSH_CLEAR -> instruction.localAux();
      case STORE_LOCAL -> frame.instructionPointer > 0
          && frame.program.instructions().get(frame.instructionPointer - 1).opcode()
              == BytecodeProgram.Opcode.FORK ? 0 : 1;
      case JUMP -> instruction.astPath().isPresent() ? 1 : 0;
      case ENTER_HANDLER -> {
        var handler = instruction.handler().orElseThrow();
        yield handler.catchTarget() < 0 || !handler.catchesAny() ? 1 : 0;
      }
      case SET_INDEX_LOCAL, SET_INDEX_PROPERTY -> 2;
      default -> COUNTED_OPCODES.contains(opcode) ? 1 : 0;
    };
  }

  private static void invokeBuiltin(
      Instruction instruction,
      Frame frame,
      VmState state,
      WorldTxn world,
      BuiltinCatalog builtins,
      BuiltinHosts hosts,
      long taskId) {
    MooValue argumentValue = frame.operandStack.pop();
    if (!(argumentValue instanceof ListValue arguments)) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    String name = instruction.text().orElseThrow();
    BuiltinSpec spec = builtins.spec(name).orElse(null);
    if (spec == null) {
      ErrorOps.raise(state, ErrorValue.E_VERBNF, world);
      return;
    }
    frame.instructionPointer++;
    if (spec.irrevocable()) {
      state.yieldBuiltin(
          new VmSnapshot.PendingBuiltin(
              spec.name(),
              arguments.elements(),
              state.programmer(),
              state.taskLocal(),
              state.budget().remainingTicks(),
              state.budget().remainingSeconds(),
              frame.receiver,
              state.callerProgrammer(),
              state.callers()));
      return;
    }
    BuiltinResult result =
        builtins.invoke(spec, builtinCall(state, world, hosts, taskId, arguments.elements(),
            frame.receiver, null));
    applyBuiltinResult(result, frame, state, world);
  }

  private static Optional<String> primitivePrototypeProperty(MooValue value) {
    if (value instanceof IntegerValue) {
      return Optional.of("int_proto");
    }
    if (value instanceof FloatValue) {
      return Optional.of("float_proto");
    }
    if (value instanceof StringValue) {
      return Optional.of("str_proto");
    }
    if (value instanceof ErrorValue) {
      return Optional.of("err_proto");
    }
    if (value instanceof ListValue) {
      return Optional.of("list_proto");
    }
    if (value instanceof MapValue) {
      return Optional.of("map_proto");
    }
    return Optional.empty();
  }

  /** Authorizes and applies the exact builtin request for its scheduler task ID. */
  public void authorizePendingBuiltin(
      VmState state, WorldTxn world, BuiltinCatalog builtins, BuiltinHosts hosts, long taskId) {
    VmSnapshot.PendingBuiltin request = state.authorizePendingBuiltin();
    BuiltinSpec spec = builtins.spec(request.name()).orElseThrow();
    BuiltinResult result =
        builtins.invoke(spec, builtinCall(state, world, hosts, taskId, request.arguments(),
            request.receiver(), request));
    applyBuiltinResult(result, state.currentFrame(), state, world);
  }

  private static BuiltinCall builtinCall(
      VmState state, WorldTxn world, BuiltinHosts hosts, long taskId, List<MooValue> arguments,
      MooValue receiver, VmSnapshot.@Nullable PendingBuiltin pending) {
    return new BuiltinCall(
        arguments, world,
        pending == null ? state.programmer() : pending.programmer(),
        pending == null ? state.taskLocal() : pending.taskLocal(), taskId,
        pending == null ? state.budget().remainingTicks() : pending.remainingTicks(),
        pending == null ? state.budget().remainingSeconds() : pending.remainingSeconds(), receiver,
        pending == null ? state.callerProgrammer() : pending.callerProgrammer(), state::callers,
        state.threadMode(), state.effects()::stagedBufferedOutputLength, hosts);
  }

  static void applyBuiltinResult(
      BuiltinResult result, Frame frame, VmState state, WorldTxn world) {
    switch (result) {
      case BuiltinResult.SecondsAbort _ -> state.abortSecondsExhaustion();
      case BuiltinResult.KillAbort() -> state.abortKilled();
      case BuiltinResult.ErrorResult error ->
          ErrorOps.raise(state, error.error(), world, false);
      case BuiltinResult.RaisedError raised ->
          ErrorOps.raise(state, raised.error(), raised.details(), world, false);
      case BuiltinResult.Value value -> frame.operandStack.push(value.value());
      case BuiltinResult.Pass call -> applyPass(call, frame, state, world);
      case BuiltinResult.ProtectedCall call -> applyProtectedCall(call, state, world);
      case BuiltinResult.TaskLocal local -> {
        state.setTaskLocal(local.value());
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.Initialize initialize ->
          applyInitialize(initialize, frame, state, world);
      case BuiltinResult.Checkpoint _ -> {
        state.effects().stageCheckpointRequest(new CheckpointRequest(false));
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.Shutdown _ -> {
        state.effects().stageCheckpointRequest(new CheckpointRequest(true));
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.Panic panic -> {
        state.effects().stageCheckpointRequest(CheckpointRequest.panic(panic.message()));
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.Suspend suspend ->
          state.suspend(OptionalDouble.of(suspend.seconds()), Optional.empty());
      case BuiltinResult.HostWork hostWork ->
          state.suspend(OptionalDouble.empty(), Optional.of(hostWork.work()));
      case BuiltinResult.ThreadMode threadMode -> {
        state.setThreadMode(threadMode.enabled());
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.DynamicEval dynamicEval ->
          applyDynamicEval(dynamicEval.program(), state, world);
      case BuiltinResult.Output output -> {
        state.effects().stageOutput(output.line());
        frame.operandStack.push(new IntegerValue(1));
      }
      case BuiltinResult.Notify notify -> {
        state.effects().stageNotificationRequest(
            new NotificationRequest(
                notify.connectionId(), notify.line(), notify.noFlush(), notify.noNewline()));
        frame.operandStack.push(new IntegerValue(1));
      }
      case BuiltinResult.SwitchPlayer switchPlayer -> {
        state.switchPlayer(switchPlayer.player());
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.Programmer programmer -> {
        state.setProgrammer(programmer.programmer());
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.Move move -> MoveOps.start(move, state, world);
      case BuiltinResult.Recycle recycle ->
          applyRecycle(recycle.object(), state, world);
      case BuiltinResult.RecycleAnonymous recycle ->
          applyAnonymousRecycle(recycle.object(), frame, state, world);
      case BuiltinResult.BootPlayer bootPlayer -> {
        state.effects().stageBootPlayerTarget(bootPlayer.target());
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.SetConnectionOption request -> {
        state.effects().stageConnectionOptionRequest(
            new ConnectionOptionRequest(request.target(), request.option(), request.value()));
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.ForceInput request -> {
        state.effects().stageForcedInputRequest(new ForcedInputRequest(request.target(), request.input()));
        frame.operandStack.push(new IntegerValue(0));
      }
    }
  }

  private static void applyPass(
      BuiltinResult.Pass call, Frame frame, VmState state, WorldTxn world) {
    ListValue arguments = call.arguments();
    MooValue thisValue = frame.locals[6];
    MooValue verbValue = frame.locals[8];
    if (thisValue == null
        || (!(thisValue instanceof ObjectValue)
            && !(thisValue instanceof AnonymousObjectValue)
            && primitivePrototypeProperty(thisValue).isEmpty())
        || !(verbValue instanceof StringValue verbNameValue)) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world, false);
      return;
    }

    String verbName = verbNameValue.text();
    MooValue receiver = thisValue;
    List<Long> directParents;
    if (frame.verbLocation instanceof ObjectValue currentLocation) {
      WorldObject location = world.object(currentLocation.value()).orElse(null);
      if (location == null) {
        ErrorOps.raise(state, ErrorValue.E_INVIND, world, false);
        return;
      }
      directParents = location.parents();
    } else if (frame.verbLocation instanceof AnonymousObjectValue currentLocation) {
      WorldAnonymousObject location = world.anonymousObject(currentLocation).orElse(null);
      if (location == null) {
        ErrorOps.raise(state, ErrorValue.E_INVIND, world, false);
        return;
      }
      directParents = location.parents();
    } else {
      ErrorOps.raise(state, ErrorValue.E_INVIND, world, false);
      return;
    }
    if (directParents.isEmpty()) {
      ErrorOps.raise(state, ErrorValue.E_INVIND, world, false);
      return;
    }
    ResolvedVerb target = null;
    for (long directParent : directParents) {
      target = world.resolveVerb(directParent, verbName, true).orElse(null);
      if (target != null) break;
    }
    if (target == null) {
      ErrorOps.raise(state, ErrorValue.E_VERBNF, world, false);
      return;
    }
    if (!callVerb(state, receiver, target, arguments, verbName, VerbCallKind.PASS,
        Optional.empty(), Optional.empty(), Optional.empty())) {
      ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
    }
  }

  private static void applyProtectedCall(
      BuiltinResult.ProtectedCall call, VmState state, WorldTxn world) {
    ResolvedVerb wrapper = world.resolveVerb(0, call.verb(), true).orElseThrow();
    if (!callVerb(state, new ObjectValue(0), wrapper, call.arguments(), call.verb(),
        VerbCallKind.PROTECTED, Optional.empty(), Optional.empty(), Optional.empty())) {
      ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
    }
  }

  private static void applyDynamicEval(
      BytecodeProgram program, VmState state, WorldTxn world) {
    Frame caller = state.currentFrame();
    Map<String, MooValue> locals = new LinkedHashMap<>();
    locals.put("player", caller.localOrDefault(5, new ObjectValue(-1)));
    locals.put("caller", caller.receiver);
    for (String field : List.of("this", "dobj", "iobj")) {
      locals.put(field, new ObjectValue(-1));
    }
    for (String field : List.of("dobjstr", "iobjstr", "argstr", "prepstr", "verb")) {
      locals.put(field, StringValue.of(""));
    }
    locals.put("args", new ListValue(List.of()));
    if (!state.pushFrame(program, locals, new VmState.FrameOrigin(
        VmState.ReturnMode.EVAL, caller.programmer, new ObjectValue(-1), new ObjectValue(-1),
        "", "Input to EVAL", Optional.empty(), OptionalLong.empty(), OptionalLong.empty(),
        OptionalLong.empty(), OptionalLong.empty(), Optional.empty(), true))) {
      ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
    }
  }

  private static void applyRecycle(
      long recycleTarget, VmState state, WorldTxn world) {
    if (!callRecycleHook(recycleTarget, recycleTarget, "recycle", new ListValue(List.of()), state, world)) {
      continueRecycle(recycleTarget, state, world);
    }
  }

  static void continueRecycle(long target, VmState state, WorldTxn world) {
    while (true) {
      WorldObject object = world.object(target).orElse(null);
      if (object == null) {
        ErrorOps.raise(state, ErrorValue.E_INVARG, world, false);
        return;
      }
      long moving;
      if (!object.contents().isEmpty()) moving = object.contents().getFirst();
      else if (object.location() != -1) moving = target;
      else break;
      long oldLocation = world.object(moving).orElseThrow().location();
      if (!ErrorOps.propagateWorldFailure(world.move(moving, -1, 0), state, world)) return;
      if (callRecycleHook(target, oldLocation, "exitfunc",
          new ListValue(List.of(new ObjectValue(moving))), state, world)) {
        return;
      }
    }
    if (ErrorOps.propagateWorldFailure(world.recycleObject(target), state, world)) {
      state.currentFrame().operandStack.push(new IntegerValue(0));
    }
  }

  private static boolean callRecycleHook(
      long recycleTarget, long receiver, String name, ListValue arguments,
      VmState state, WorldTxn world) {
    ResolvedVerb hook = world.resolveVerb(receiver, name, true).orElse(null);
    if (hook == null) return false;
    if (!callVerb(state, new ObjectValue(receiver), hook, arguments, name,
        VerbCallKind.HOOK_RECYCLE, Optional.of(new ObjectValue(recycleTarget)),
        Optional.empty(), Optional.empty())) {
      continueRecycle(recycleTarget, state, world);
    }
    return true;
  }

  private static void applyAnonymousRecycle(
      AnonymousObjectValue recycleTarget, Frame frame, VmState state, WorldTxn world) {
    WorldAnonymousObject recycleBody = world.anonymousObject(recycleTarget).orElse(null);
    if (recycleBody == null) {
      ErrorOps.raise(state, ErrorValue.E_INVARG, world);
      return;
    }
    ResolvedVerb hook = world.resolveVerb(recycleTarget, "recycle", true).orElse(null);
    if (hook == null) {
      if (!ErrorOps.propagateWorldFailure(world.removeAnonymousObject(recycleTarget), state, world)) {
        return;
      }
      frame.operandStack.push(new IntegerValue(0));
      return;
    }
    List<AnonymousObjectValue> collectionDeferrals = new ArrayList<>();
    for (WorldProperty property : recycleBody.properties()) {
      if (property.defined()) {
        collectAnonymousReferences(property.value(), collectionDeferrals);
      }
    }
    state.effects().deferAnonymousCollection(collectionDeferrals);
    if (!ErrorOps.propagateWorldFailure(world.removeAnonymousObject(recycleTarget), state, world)) {
      return;
    }
    if (!callVerb(state, recycleTarget, hook, new ListValue(List.of()), "recycle",
        VerbCallKind.HOOK_ANONYMOUS_RECYCLE, Optional.empty(), Optional.empty(), Optional.empty())) {
      ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
    }
  }

  private static void applyInitialize(
      BuiltinResult.Initialize request, Frame frame, VmState state, WorldTxn world) {
    MooValue created = request.created();
    if (!(created instanceof ObjectValue) && !(created instanceof AnonymousObjectValue)) {
      ErrorOps.raise(state, ErrorValue.E_INVARG, world);
      return;
    }
    ResolvedVerb initialize = world.resolveVerb(created, "initialize", true).orElse(null);
    if (initialize == null) {
      frame.operandStack.push(created);
      return;
    }
    if (!callVerb(state, created, initialize, request.arguments(), "initialize",
        VerbCallKind.HOOK_INITIALIZE, Optional.of(created), Optional.empty(), Optional.empty())) {
      ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
    }
  }
  /** Resumes a suspended builtin by routing its MOO error through this activation's handlers. */
  public void resumeWithError(VmState state, BuiltinResult completion, WorldTxn world) {
    state.resumeError();
    switch (completion) {
      case BuiltinResult.RaisedError raised ->
          ErrorOps.raise(state, raised.error(), raised.details(), world, false);
      case BuiltinResult.ErrorResult error ->
          ErrorOps.raise(state, error.error(), world, false);
      default -> throw new IllegalArgumentException("completion is not a MOO error");
    }
  }

  private static void collectAnonymousReferences(
      MooValue value, List<AnonymousObjectValue> references) {
    if (value instanceof AnonymousObjectValue anonymous) {
      if (!references.contains(anonymous)) {
        references.add(anonymous);
      }
      return;
    }
    if (value instanceof ListValue list) {
      for (MooValue element : list.elements()) {
        collectAnonymousReferences(element, references);
      }
      return;
    }
    if (value instanceof MapValue map) {
      for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
        collectAnonymousReferences(entry.getKey(), references);
        collectAnonymousReferences(entry.getValue(), references);
      }
    }
  }

}
