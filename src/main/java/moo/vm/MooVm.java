package moo.vm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import moo.builtin.BuiltinCatalog;
import moo.builtin.BuiltinCatalog.ConnectionOptionRequest;
import moo.builtin.BuiltinCatalog.ForcedInputRequest;
import moo.builtin.BuiltinHosts;
import moo.builtin.BuiltinResult;
import moo.builtin.BuiltinSpec;
import moo.builtin.CheckpointRequest;
import moo.builtin.EffectClass;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.BytecodeProgram.Instruction;
import moo.bytecode.MooCompiler;
import moo.value.MooValue;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.value.MooValue.WaifValue;
import moo.value.ValueSemantics;
import moo.vm.VmState.Frame;
import moo.world.WorldObject;
import moo.world.WorldAnonymousObject;
import moo.world.WorldProperty;
import moo.world.WorldTxn;
import moo.world.WorldVerb;

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
          BytecodeProgram.Opcode.FIRST,
          BytecodeProgram.Opcode.LAST,
          BytecodeProgram.Opcode.SET_INDEX_LOCAL,
          BytecodeProgram.Opcode.SET_INDEX_PROPERTY,
          BytecodeProgram.Opcode.SET_RANGE_LOCAL,
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
    if (isCountedInstruction(opcode, frame)) {
      state.decrementRemainingTicks();
      if (state.remainingTicks() == 0) {
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
      case BUILD_LIST ->
          ListOps.execute(ListOps.Operation.BUILD_LIST, state, instruction, frame, world);
      case LIST_APPEND ->
          ListOps.execute(ListOps.Operation.LIST_APPEND, state, instruction, frame, world);
      case LIST_EXTEND ->
          ListOps.execute(ListOps.Operation.LIST_EXTEND, state, instruction, frame, world);
      case BUILD_MAP ->
          ListOps.execute(ListOps.Operation.BUILD_MAP, state, instruction, frame, world);
      case LOAD_LOCAL ->
          PropertyOps.execute(PropertyOps.Operation.LOAD_LOCAL, state, instruction, frame, world);
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
      case GET_PROPERTY ->
          PropertyOps.execute(PropertyOps.Operation.GET_PROPERTY, state, instruction, frame, world);
      case SET_PROPERTY ->
          PropertyOps.execute(PropertyOps.Operation.SET_PROPERTY, state, instruction, frame, world);
      case ENTER_INDEX ->
          IndexOps.execute(IndexOps.Operation.ENTER_INDEX, state, instruction, frame, world);
      case INDEX ->
          IndexOps.execute(IndexOps.Operation.INDEX, state, instruction, frame, world);
      case RANGE ->
          IndexOps.execute(IndexOps.Operation.RANGE, state, instruction, frame, world);
      case FIRST ->
          IndexOps.execute(IndexOps.Operation.FIRST, state, instruction, frame, world);
      case LAST ->
          IndexOps.execute(IndexOps.Operation.LAST, state, instruction, frame, world);
      case SET_INDEX_LOCAL ->
          IndexOps.execute(IndexOps.Operation.SET_INDEX_LOCAL, state, instruction, frame, world);
      case SET_INDEX_PROPERTY ->
          IndexOps.execute(IndexOps.Operation.SET_INDEX_PROPERTY, state, instruction, frame, world);
      case SET_RANGE_LOCAL ->
          IndexOps.execute(IndexOps.Operation.SET_RANGE_LOCAL, state, instruction, frame, world);
      case CALL -> {
        String callName = instruction.text().orElseThrow();
        if (!callName.equalsIgnoreCase("pass")) {
          invokeBuiltin(instruction, frame, state, world, builtins, taskId);
        } else {
          MooValue argumentValue = frame.operandStack.pop();
          MooValue thisValue = frame.locals.get("this");
          MooValue verbValue = frame.locals.get("verb");
          if (!(argumentValue instanceof ListValue arguments)
              || thisValue == null
              || (!(thisValue instanceof ObjectValue)
                  && !(thisValue instanceof AnonymousObjectValue)
                  && primitivePrototypeProperty(thisValue).isEmpty())
              || !(verbValue instanceof StringValue verbNameValue)) {
            ErrorOps.raise(state, ErrorValue.E_TYPE, world);
            return;
          }

          String verbName = verbNameValue.text();
          MooValue receiver = thisValue;
          List<Long> directParents;
          if (frame.verbLocation instanceof ObjectValue currentLocation) {
            WorldObject location = world.object(currentLocation.value()).orElse(null);
            if (location == null) {
              ErrorOps.raise(state, ErrorValue.E_INVIND, world);
              return;
            }
            directParents = location.parents();
          } else if (frame.verbLocation instanceof AnonymousObjectValue currentLocation) {
            WorldAnonymousObject location = world.anonymousObject(currentLocation).orElse(null);
            if (location == null) {
              ErrorOps.raise(state, ErrorValue.E_INVIND, world);
              return;
            }
            directParents = location.parents();
          } else {
            ErrorOps.raise(state, ErrorValue.E_INVIND, world);
            return;
          }
          if (directParents.isEmpty()) {
            ErrorOps.raise(state, ErrorValue.E_INVIND, world);
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
            ErrorOps.raise(state, ErrorValue.E_VERBNF, world);
            return;
          }
          BytecodeProgram targetProgram;
          try {
            targetProgram = new MooCompiler().compile(target.programSource());
          } catch (IllegalArgumentException error) {
            ErrorOps.raise(state, ErrorValue.E_INVARG, world);
            return;
          }

          frame.instructionPointer++;
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
              (target.permissions() & 8) != 0)) {
            ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
            return;
          }
        }
      }
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
            (verb.permissions() & 8) != 0)) {
          ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
          return;
        }
      }
      case NEGATE ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.NEGATE, state, instruction, frame, world, valueSemantics);
      case NOT ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.NOT, state, instruction, frame, world, valueSemantics);
      case COMPLEMENT ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.COMPLEMENT, state, instruction, frame, world, valueSemantics);
      case ADD ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.ADD, state, instruction, frame, world, valueSemantics);
      case SUBTRACT ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.SUBTRACT, state, instruction, frame, world, valueSemantics);
      case MULTIPLY ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.MULTIPLY, state, instruction, frame, world, valueSemantics);
      case DIVIDE ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.DIVIDE, state, instruction, frame, world, valueSemantics);
      case REMAINDER ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.REMAINDER, state, instruction, frame, world, valueSemantics);
      case POWER ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.POWER, state, instruction, frame, world, valueSemantics);
      case BITOR ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.BITOR, state, instruction, frame, world, valueSemantics);
      case BITAND ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.BITAND, state, instruction, frame, world, valueSemantics);
      case BITXOR ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.BITXOR, state, instruction, frame, world, valueSemantics);
      case BITSHL ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.BITSHL, state, instruction, frame, world, valueSemantics);
      case BITSHR ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.BITSHR, state, instruction, frame, world, valueSemantics);
      case EQUAL ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.EQUAL, state, instruction, frame, world, valueSemantics);
      case NOT_EQUAL ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.NOT_EQUAL, state, instruction, frame, world, valueSemantics);
      case LESS_THAN ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.LESS_THAN, state, instruction, frame, world, valueSemantics);
      case LESS_THAN_OR_EQUAL ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.LESS_THAN_OR_EQUAL,
              state,
              instruction,
              frame,
              world,
              valueSemantics);
      case GREATER_THAN ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.GREATER_THAN,
              state,
              instruction,
              frame,
              world,
              valueSemantics);
      case GREATER_THAN_OR_EQUAL ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.GREATER_THAN_OR_EQUAL,
              state,
              instruction,
              frame,
              world,
              valueSemantics);
      case IN ->
          ArithmeticOps.execute(
              ArithmeticOps.Operation.IN, state, instruction, frame, world, valueSemantics);
      case FORK ->
          LoopOps.execute(LoopOps.Operation.FORK, state, instruction, frame, world);
      case JUMP ->
          LoopOps.execute(LoopOps.Operation.JUMP, state, instruction, frame, world);
      case JUMP_IF_FALSE ->
          LoopOps.execute(LoopOps.Operation.JUMP_IF_FALSE, state, instruction, frame, world);
      case JUMP_IF_TRUE ->
          LoopOps.execute(LoopOps.Operation.JUMP_IF_TRUE, state, instruction, frame, world);
      case ENTER_HANDLER ->
          LoopOps.execute(LoopOps.Operation.ENTER_HANDLER, state, instruction, frame, world);
      case LEAVE_HANDLER ->
          LoopOps.execute(LoopOps.Operation.LEAVE_HANDLER, state, instruction, frame, world);
      case END_FINALLY ->
          LoopOps.execute(LoopOps.Operation.END_FINALLY, state, instruction, frame, world);
      case ITERATE ->
          LoopOps.execute(LoopOps.Operation.ITERATE, state, instruction, frame, world);
      case ITERATE_RANGE ->
          LoopOps.execute(LoopOps.Operation.ITERATE_RANGE, state, instruction, frame, world);
      case LEAVE_LOOP ->
          LoopOps.execute(LoopOps.Operation.LEAVE_LOOP, state, instruction, frame, world);
      case SCATTER ->
          LoopOps.execute(LoopOps.Operation.SCATTER, state, instruction, frame, world);
      case RETURN ->
          LoopOps.execute(LoopOps.Operation.RETURN, state, instruction, frame, world);
    }
  }

  private static boolean isCountedInstruction(
      BytecodeProgram.Opcode opcode, Frame frame) {
    if (opcode != BytecodeProgram.Opcode.LIST_APPEND) {
      return COUNTED_OPCODES.contains(opcode);
    }
    var operands = frame.operandStack.iterator();
    if (operands.hasNext()) {
      operands.next();
    }
    return operands.hasNext()
        && operands.next() instanceof ListValue list
        && list.elements().isEmpty();
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
    if (name.equalsIgnoreCase("yin") && arguments.elements().isEmpty()) {
      frame.instructionPointer++;
      if (state.remainingTicks() < 2_000 || state.remainingSeconds() < 2) {
        state.suspend(java.util.OptionalDouble.of(0), Optional.empty());
      } else {
        frame.operandStack.push(new IntegerValue(0));
      }
      return;
    }
    BuiltinSpec spec = builtins.spec(name).orElse(null);
    if (spec == null) {
      ErrorOps.raise(state, ErrorValue.E_VERBNF, world);
      return;
    }
    frame.instructionPointer++;
    if (spec.effect() == EffectClass.IRREVOCABLE) {
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
    ListValue callerFrames =
        name.equals("callers")
                && !arguments.elements().isEmpty()
                && arguments.elements().getFirst().isTruthy()
            ? state.callers(true)
            : state.callers();
    BuiltinResult result =
        builtins.invoke(
            spec,
            arguments.elements(),
            world,
            state.programmer(),
            state.taskLocal(),
            taskId,
            state.remainingTicks(),
            state.remainingSeconds(),
            frame.receiver,
            state.callerProgrammer(),
            callerFrames,
            state.threadMode());
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
        builtins.invoke(
            spec,
            request.arguments(),
            world,
            request.programmer(),
            request.taskLocal(),
            taskId,
            request.remainingTicks(),
            request.remainingSeconds(),
            request.receiver(),
            request.callerProgrammer(),
            request.callers(),
            state.threadMode());
    applyBuiltinResult(result, state.currentFrame(), state, world);
  }

  static void applyBuiltinResult(
      BuiltinResult result, Frame frame, VmState state, WorldTxn world) {
    switch (result) {
      case BuiltinResult.SecondsAbort _ -> state.abortSecondsExhaustion();
      case BuiltinResult.ErrorResult error ->
          ErrorOps.raise(state, error.error(), world, false);
      case BuiltinResult.RaisedError raised ->
          ErrorOps.raise(state, raised.error(), raised.details(), world, false);
      case BuiltinResult.Value value -> frame.operandStack.push(value.value());
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
          applyDynamicEval(dynamicEval.source(), frame, state, world);
      case BuiltinResult.Output output -> {
        state.stageOutput(output.line());
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
      case BuiltinResult.Move move -> applyMove(move, frame, state, world);
      case BuiltinResult.Recycle recycle ->
          applyRecycle(recycle.object(), frame, state, world);
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

  private static void applyDynamicEval(
      String source, Frame frame, VmState state, WorldTxn world) {
    final BytecodeProgram dynamicProgram;
    try {
      dynamicProgram = new MooCompiler().compile(source);
    } catch (IllegalArgumentException error) {
      String diagnostic = error.getMessage();
      if (diagnostic == null) {
        diagnostic = error.getClass().getSimpleName();
      }
      frame.operandStack.push(
          new ListValue(
              List.of(
                  new IntegerValue(0),
                  new ListValue(List.of(StringValue.of("Parse error: " + diagnostic))))));
      return;
    }
    if (!state.pushEvalFrame(dynamicProgram)) {
      ErrorOps.raise(state, ErrorValue.E_MAXREC, world);
    }
  }

  private static void applyMove(
      BuiltinResult.Move move, Frame frame, VmState state, WorldTxn world) {
    WorldVerb hook = world.verb(move.destination(), "accept").orElse(null);
    if (hook == null) {
      if (!ErrorOps.propagateWorldFailure(
          world.move(move.object(), move.destination(), move.position()), state, world)) {
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
    ObjectValue destination = new ObjectValue(move.destination());
    locals.put("this", destination);
    locals.put("player", frame.locals.getOrDefault("player", new ObjectValue(-1)));
    locals.put("caller", frame.receiver);
    locals.put("verb", StringValue.of("accept"));
    locals.put("args", new ListValue(List.of(new ObjectValue(move.object()))));
    locals.put("argstr", StringValue.of(""));
    if (!state.pushVerbFrame(
        hookProgram,
        locals,
        hook.owner(),
        destination,
        destination,
        OptionalLong.empty(),
        OptionalLong.of(move.object()),
        OptionalLong.of(move.destination()),
        OptionalLong.of(move.position()),
        "accept",
        hook.names(),
        (hook.permissions() & 8) != 0)) {
      ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
    }
  }

  private static void applyRecycle(
      long recycleTarget, Frame frame, VmState state, WorldTxn world) {
    WorldVerb hook = world.verb(recycleTarget, "recycle").orElse(null);
    if (hook == null) {
      if (!ErrorOps.propagateWorldFailure(world.recycleObject(recycleTarget), state, world)) {
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
    ObjectValue target = new ObjectValue(recycleTarget);
    locals.put("this", target);
    locals.put("player", frame.locals.getOrDefault("player", new ObjectValue(-1)));
    locals.put("caller", frame.locals.getOrDefault("this", new ObjectValue(-1)));
    locals.put("verb", StringValue.of("recycle"));
    locals.put("args", new ListValue(List.of()));
    locals.put("argstr", StringValue.of(""));
    OptionalLong definingLocation = world.verbLocation(recycleTarget, "recycle", true);
    if (!state.pushVerbFrame(
        hookProgram,
        locals,
        hook.owner(),
        target,
        new ObjectValue(definingLocation.orElse(recycleTarget)),
        OptionalLong.of(recycleTarget),
        OptionalLong.empty(),
        OptionalLong.empty(),
        "recycle",
        hook.names(),
        (hook.permissions() & 8) != 0)) {
      ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
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
        (hook.permissions() & 8) != 0)) {
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
        (initialize.permissions() & 8) != 0)) {
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
