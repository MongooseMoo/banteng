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
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinCatalog.ConnectionOptionRequest;
import world.mongoose.banteng.builtin.BuiltinCatalog.ForcedInputRequest;
import world.mongoose.banteng.builtin.BuiltinCatalog.NotificationRequest;
import world.mongoose.banteng.builtin.BuiltinHosts;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CheckpointRequest;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.BytecodeProgram.Instruction;
import world.mongoose.banteng.bytecode.MooCompiler;
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
import world.mongoose.banteng.world.WorldVerb;

/** Iterative executor for the authorized explicit bytecode state. */
public final class MooVm {
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

  private final ValueSemantics valueSemantics;

  /** Creates a VM with stock value semantics. */
  public MooVm() {
    this(ValueSemantics.STANDARD);
  }

  /** Creates a VM with the selected value semantics. */
  public MooVm(ValueSemantics valueSemantics) {
    this.valueSemantics = Objects.requireNonNull(valueSemantics, "valueSemantics");
  }

  /** Executes a pure program for package-level VM tests without publishing world state or effects. */
  void execute(BytecodeProgram program, VmState state) {
    WorldTxn root = new WorldTxn(List.of(), List.of());
    try (WorldTxn transaction = root.begin()) {
      execute(
          program,
          state,
          transaction,
          new BuiltinCatalog(
              BuiltinHosts.builder().valueSemantics(valueSemantics).build()),
          0L);
      if (state.outcome() == VmState.Outcome.PENDING_BUILTIN) {
        throw new IllegalStateException("pure VM execution reached an irrevocable builtin");
      }
    }
  }

  /** Executes with the one concrete world transaction, builtin catalog, and scheduler task ID. */
  public void execute(
      BytecodeProgram program,
      VmState state,
      WorldTxn world,
      BuiltinCatalog builtins,
      long taskId) {
    state.beginSegment();
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
          taskId);
    }
  }

  private void executeInstruction(
      Instruction instruction,
      VmState state,
      WorldTxn world,
      BuiltinCatalog builtins,
      long taskId) {
    Frame frame = state.currentFrame();
    BytecodeProgram.Opcode opcode = instruction.opcode();
    if ((opcode == BytecodeProgram.Opcode.CALL || (state.remainingTicks() & 1023) == 0)
        && state.secondsExhausted()) {
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
      state.decrementRemainingTicks();
      if (!extended && state.remainingTicks() <= 0) {
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
      case LOAD_LOCAL -> PropertyOps.loadLocal(frame, instruction.text().orElseThrow(), state, world);
      case STORE_LOCAL -> {
        frame.locals.put(PropertyOps.normalize(instruction.text().orElseThrow()), frame.operandStack.pop());
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
      case SET_INDEX_LOCAL -> IndexOps.setIndexedLocal(frame, state, world, instruction.text().orElseThrow(),
              Math.toIntExact(instruction.operand().orElse(0)));
      case SET_INDEX_PROPERTY -> IndexOps.setIndexedProperty(frame, state, world);
      case SET_RANGE_LOCAL -> IndexOps.setRange(frame, state, world, instruction.text(),
              Math.toIntExact(instruction.operand().orElse(0)));
      case SET_RANGE_PROPERTY -> IndexOps.setRange(frame, state, world, Optional.empty(), 0);
      case CALL -> invokeBuiltin(instruction, frame, state, world, builtins, taskId);
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
        WorldVerb verb;
        MooValue definingLocation;
        String lookupName;
        if (receiverValue instanceof ObjectValue receiver) {
          lookupName = verbName;
          verb = world.verb(receiver.value(), lookupName).orElse(null);
          OptionalLong location = world.verbLocation(receiver.value(), lookupName, true);
          definingLocation = location.isPresent() ? new ObjectValue(location.orElseThrow()) : null;
        } else if (receiverValue instanceof AnonymousObjectValue anonymous) {
          lookupName = verbName;
          verb = world.verb(anonymous, lookupName, true).orElse(null);
          definingLocation = world.verbLocation(anonymous, lookupName, true).orElse(null);
        } else if (receiverValue instanceof WaifValue waif) {
          lookupName = verbName.startsWith(":") ? verbName : ":" + verbName;
          verb = world.verb(waif.classObject().value(), lookupName).orElse(null);
          OptionalLong location =
              world.verbLocation(waif.classObject().value(), lookupName, true);
          definingLocation = location.isPresent() ? new ObjectValue(location.orElseThrow()) : null;
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
          verb = world.verb(prototype.value(), lookupName).orElse(null);
          OptionalLong location = world.verbLocation(prototype.value(), lookupName, true);
          definingLocation = location.isPresent() ? new ObjectValue(location.orElseThrow()) : null;
        }
        if (verb == null || definingLocation == null) {
          ErrorOps.raise(state, ErrorValue.E_VERBNF, world);
          return;
        }
        BytecodeProgram verbProgram;
        try {
          verbProgram = new MooCompiler().compile(verb.programSource());
        } catch (IllegalArgumentException error) {
          ErrorOps.raise(state, ErrorValue.E_INVARG, world);
          return;
        }
        frame.instructionPointer++;
        Map<String, MooValue> locals = new LinkedHashMap<>();
        locals.put("this", receiverValue);
        locals.put("player", frame.locals.getOrDefault("player", new ObjectValue(-1)));
        locals.put("caller", frame.receiver);
        locals.put("verb", StringValue.of(lookupName));
        locals.put("args", arguments);
        locals.put("argstr", frame.locals.getOrDefault("argstr", StringValue.of("")));
        locals.put("dobj", frame.locals.getOrDefault("dobj", new ObjectValue(-1)));
        locals.put("dobjstr", frame.locals.getOrDefault("dobjstr", StringValue.of("")));
        locals.put("prepstr", frame.locals.getOrDefault("prepstr", StringValue.of("")));
        locals.put("iobj", frame.locals.getOrDefault("iobj", new ObjectValue(-1)));
        locals.put("iobjstr", frame.locals.getOrDefault("iobjstr", StringValue.of("")));
        if (!state.pushVerbFrame(
            verbProgram,
            locals,
            verb.owner(),
            receiverValue,
            definingLocation,
            OptionalLong.empty(),
            OptionalLong.empty(),
            OptionalLong.empty(),
            lookupName,
            verb.names(),
            verb.perms().debug())) {
          ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
          return;
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
      case LOAD_LOCAL -> Math.toIntExact(instruction.operand().orElse(0));
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
              state.remainingTicks(),
              state.remainingSeconds(),
              frame.receiver,
              state.callerProgrammer(),
              state.callers()));
      return;
    }
    BuiltinResult result =
        builtins.invoke(spec, builtinCall(state, world, taskId, arguments.elements(),
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
      VmState state, WorldTxn world, BuiltinCatalog builtins, long taskId) {
    VmSnapshot.PendingBuiltin request = state.authorizePendingBuiltin();
    BuiltinSpec spec = builtins.spec(request.name()).orElseThrow();
    BuiltinResult result =
        builtins.invoke(spec, builtinCall(state, world, taskId, request.arguments(),
            request.receiver(), request));
    applyBuiltinResult(result, state.currentFrame(), state, world);
  }

  private static BuiltinCall builtinCall(
      VmState state, WorldTxn world, long taskId, List<MooValue> arguments,
      MooValue receiver, VmSnapshot.@Nullable PendingBuiltin pending) {
    return new BuiltinCall(
        arguments, world,
        pending == null ? state.programmer() : pending.programmer(),
        pending == null ? state.taskLocal() : pending.taskLocal(), taskId,
        pending == null ? state.remainingTicks() : pending.remainingTicks(),
        pending == null ? state.remainingSeconds() : pending.remainingSeconds(), receiver,
        pending == null ? state.callerProgrammer() : pending.callerProgrammer(), state::callers,
        state.threadMode(), state::stagedBufferedOutputLength);
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
      case BuiltinResult.ProtectedCall call -> applyProtectedCall(call, frame, state, world);
      case BuiltinResult.TaskLocal local -> {
        state.setTaskLocal(local.value());
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.Initialize initialize ->
          applyInitialize(initialize, frame, state, world);
      case BuiltinResult.Checkpoint _ -> {
        state.stageCheckpointRequest(new CheckpointRequest(false));
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.Shutdown _ -> {
        state.stageCheckpointRequest(new CheckpointRequest(true));
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.Panic panic -> {
        state.stageCheckpointRequest(CheckpointRequest.panic(panic.message()));
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
        state.stageOutput(output.line());
        frame.operandStack.push(new IntegerValue(1));
      }
      case BuiltinResult.Notify notify -> {
        state.stageNotificationRequest(
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
        state.stageBootPlayerTarget(bootPlayer.target());
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.SetConnectionOption request -> {
        state.stageConnectionOptionRequest(
            new ConnectionOptionRequest(request.target(), request.option(), request.value()));
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.ForceInput request -> {
        state.stageForcedInputRequest(new ForcedInputRequest(request.target(), request.input()));
        frame.operandStack.push(new IntegerValue(0));
      }
    }
  }

  private static void applyPass(
      BuiltinResult.Pass call, Frame frame, VmState state, WorldTxn world) {
    ListValue arguments = call.arguments();
    MooValue thisValue = frame.locals.get("this");
    MooValue verbValue = frame.locals.get("verb");
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
    WorldVerb target = null;
    long targetLocation = -1;
    for (long directParent : directParents) {
      OptionalLong location = world.verbLocation(directParent, verbName, true);
      if (location.isPresent()) {
        target = world.verb(directParent, verbName, true).orElseThrow();
        targetLocation = location.orElseThrow();
        break;
      }
    }
    if (target == null) {
      ErrorOps.raise(state, ErrorValue.E_VERBNF, world, false);
      return;
    }
    BytecodeProgram targetProgram;
    try {
      targetProgram = new MooCompiler().compile(target.programSource());
    } catch (IllegalArgumentException error) {
      ErrorOps.raise(state, ErrorValue.E_INVARG, world, false);
      return;
    }

    Map<String, MooValue> locals = new LinkedHashMap<>(frame.locals);
    locals.put("caller", receiver);
    locals.put("args", arguments);
    if (!state.pushVerbFrame(
        targetProgram,
        locals,
        target.owner(),
        receiver,
        new ObjectValue(targetLocation),
        OptionalLong.empty(),
        OptionalLong.empty(),
        OptionalLong.empty(),
        verbName,
        target.names(),
        target.perms().debug())) {
      ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
      return;
    }
  }

  private static void applyProtectedCall(
      BuiltinResult.ProtectedCall call, Frame frame, VmState state, WorldTxn world) {
    WorldVerb wrapper = world.verb(0, call.verb(), true).orElseThrow();
    BytecodeProgram program;
    try {
      program = new MooCompiler().compile(wrapper.programSource());
    } catch (IllegalArgumentException failure) {
      ErrorOps.raise(state, ErrorValue.E_INVARG, world);
      return;
    }
    ObjectValue system = new ObjectValue(0);
    Map<String, MooValue> locals = new LinkedHashMap<>();
    locals.put("this", system);
    locals.put("player", frame.locals.getOrDefault("player", new ObjectValue(-1)));
    locals.put("caller", frame.receiver);
    locals.put("verb", StringValue.of(call.verb()));
    locals.put("args", call.arguments());
    locals.put("argstr", StringValue.of(""));
    if (!state.pushVerbFrame(program, locals, wrapper.owner(), system,
        new ObjectValue(world.verbLocation(0, call.verb(), true).orElseThrow()),
        OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(),
        call.verb(), wrapper.names(), wrapper.perms().debug())) {
      ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
    }
  }

  private static void applyDynamicEval(
      BytecodeProgram program, VmState state, WorldTxn world) {
    if (!state.pushEvalFrame(program)) {
      ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
    }
  }

  private static void applyRecycle(
      long recycleTarget, VmState state, WorldTxn world) {
    WorldVerb hook = world.verb(recycleTarget, "recycle").orElse(null);
    if (hook == null) {
      continueRecycle(recycleTarget, state, world);
      return;
    }
    callRecycleHook(recycleTarget, recycleTarget, "recycle", new ListValue(List.of()), state, world);
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
      if (world.verb(oldLocation, "exitfunc").isPresent()) {
        callRecycleHook(target, oldLocation, "exitfunc",
            new ListValue(List.of(new ObjectValue(moving))), state, world);
        return;
      }
    }
    if (ErrorOps.propagateWorldFailure(world.recycleObject(target), state, world)) {
      state.currentFrame().operandStack.push(new IntegerValue(0));
    }
  }

  private static void callRecycleHook(
      long recycleTarget, long receiver, String name, ListValue arguments,
      VmState state, WorldTxn world) {
    Frame frame = state.currentFrame();
    WorldVerb hook = world.verb(receiver, name).orElseThrow();
    BytecodeProgram hookProgram;
    try {
      hookProgram = new MooCompiler().compile(hook.programSource());
    } catch (IllegalArgumentException error) {
      ErrorOps.raise(state, ErrorValue.E_INVARG, world);
      return;
    }
    Map<String, MooValue> locals = new LinkedHashMap<>();
    ObjectValue target = new ObjectValue(receiver);
    locals.put("this", target);
    locals.put("player", frame.locals.getOrDefault("player", new ObjectValue(-1)));
    locals.put("caller", frame.locals.getOrDefault("this", new ObjectValue(-1)));
    locals.put("verb", StringValue.of(name));
    locals.put("args", arguments);
    locals.put("argstr", StringValue.of(""));
    OptionalLong definingLocation = world.verbLocation(receiver, name, true);
    if (!state.pushVerbFrame(
        hookProgram,
        locals,
        hook.owner(),
        target,
        new ObjectValue(definingLocation.orElse(receiver)),
        OptionalLong.of(recycleTarget),
        OptionalLong.empty(),
        OptionalLong.empty(),
        name,
        hook.names(),
        hook.perms().debug())) {
      continueRecycle(recycleTarget, state, world);
    }
  }

  private static void applyAnonymousRecycle(
      AnonymousObjectValue recycleTarget, Frame frame, VmState state, WorldTxn world) {
    WorldAnonymousObject recycleBody = world.anonymousObject(recycleTarget).orElse(null);
    if (recycleBody == null) {
      ErrorOps.raise(state, ErrorValue.E_INVARG, world);
      return;
    }
    WorldVerb hook = world.verb(recycleTarget, "recycle", true).orElse(null);
    if (hook == null) {
      if (!ErrorOps.propagateWorldFailure(world.removeAnonymousObject(recycleTarget), state, world)) {
        return;
      }
      frame.operandStack.push(new IntegerValue(0));
      return;
    }
    BytecodeProgram hookProgram;
    try {
      hookProgram = new MooCompiler().compile(hook.programSource());
    } catch (IllegalArgumentException error) {
      ErrorOps.raise(state, ErrorValue.E_INVARG, world);
      return;
    }
    Map<String, MooValue> locals = new LinkedHashMap<>();
    locals.put("this", recycleTarget);
    locals.put("player", frame.locals.getOrDefault("player", new ObjectValue(-1)));
    locals.put("caller", frame.locals.getOrDefault("this", new ObjectValue(-1)));
    locals.put("verb", StringValue.of("recycle"));
    locals.put("args", new ListValue(List.of()));
    locals.put("argstr", StringValue.of(""));
    MooValue definingLocation =
        world.verbLocation(recycleTarget, "recycle", true).orElse(recycleTarget);
    List<AnonymousObjectValue> collectionDeferrals = new ArrayList<>();
    for (WorldProperty property : recycleBody.properties()) {
      if (property.defined()) {
        collectAnonymousReferences(property.value(), collectionDeferrals);
      }
    }
    state.deferAnonymousCollection(collectionDeferrals);
    if (!ErrorOps.propagateWorldFailure(world.removeAnonymousObject(recycleTarget), state, world)) {
      return;
    }
    if (!state.pushAnonymousRecycleFrame(
        hookProgram,
        locals,
        hook.owner(),
        recycleTarget,
        definingLocation,
        "recycle",
        hook.names(),
        hook.perms().debug())) {
      ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
    }
  }

  private static void applyInitialize(
      BuiltinResult.Initialize request, Frame frame, VmState state, WorldTxn world) {
    MooValue created = request.created();
    WorldVerb initialize;
    MooValue definingLocation;
    if (created instanceof ObjectValue object) {
      initialize = world.verb(object.value(), "initialize").orElse(null);
      OptionalLong location = world.verbLocation(object.value(), "initialize", true);
      definingLocation = new ObjectValue(location.orElse(object.value()));
    } else if (created instanceof AnonymousObjectValue anonymous) {
      initialize = world.verb(anonymous, "initialize", true).orElse(null);
      definingLocation = world.verbLocation(anonymous, "initialize", true).orElse(anonymous);
    } else {
      ErrorOps.raise(state, ErrorValue.E_INVARG, world);
      return;
    }
    if (initialize == null) {
      frame.operandStack.push(created);
      return;
    }
    BytecodeProgram initializeProgram;
    try {
      initializeProgram = new MooCompiler().compile(initialize.programSource());
    } catch (IllegalArgumentException error) {
      ErrorOps.raise(state, ErrorValue.E_INVARG, world);
      return;
    }
    Map<String, MooValue> locals = new LinkedHashMap<>();
    locals.put("this", created);
    locals.put("player", frame.locals.getOrDefault("player", new ObjectValue(-1)));
    locals.put("caller", frame.receiver);
    locals.put("verb", StringValue.of("initialize"));
    locals.put("args", request.arguments());
    locals.put("argstr", StringValue.of(""));
    if (!state.pushCreateInitializeFrame(
        initializeProgram,
        locals,
        initialize.owner(),
        created,
        definingLocation,
        created,
        "initialize",
        initialize.names(),
        initialize.perms().debug())) {
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
