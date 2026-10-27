package world.mongoose.banteng.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import world.mongoose.banteng.value.MooValue;

/** One complete runtime activation retained for a suspended VM. */
public record SuspendedActivation(
    int languageVersion,
    String programSource,
    Map<String, Optional<MooValue>> locals,
    List<SuspendedStackSlot> operandStack,
    MooValue receiver,
    MooValue verbLocation,
    boolean threadMode,
    long taskPlayer,
    long programmer,
    boolean debug,
    String verb,
    String verbNames,
    Optional<MooValue> temporary,
    long programCounter,
    long builtinFunctionCounter,
    long errorCounter,
    Optional<BuiltinContinuation> builtinContinuation) {
  /** Builtin state is data owned by the existing frame return routing. */
  public sealed interface BuiltinContinuation permits EvalContinuation, CreateContinuation,
      RecycleContinuation, MoveContinuation, CallFunctionContinuation {
    String name();
  }

  public record EvalContinuation() implements BuiltinContinuation {
    @Override public String name() { return "eval"; }
  }

  public record CreateContinuation(boolean recreate, long object) implements BuiltinContinuation {
    @Override public String name() { return recreate ? "recreate" : "create"; }
  }

  public record RecycleContinuation(long object) implements BuiltinContinuation {
    @Override public String name() { return "recycle"; }
  }

  public record MoveContinuation(long object, long destination, long position) implements BuiltinContinuation {
    @Override public String name() { return "move"; }
  }

  public record CallFunctionContinuation(BuiltinContinuation function) implements BuiltinContinuation {
    @Override public String name() { return "call_function"; }
  }

  public SuspendedActivation(int languageVersion, String programSource,
      Map<String, Optional<MooValue>> locals, List<SuspendedStackSlot> operandStack,
      MooValue receiver, MooValue verbLocation, boolean threadMode, long taskPlayer,
      long programmer, boolean debug, String verb, String verbNames,
      Optional<MooValue> temporary, long programCounter, long builtinFunctionCounter,
      long errorCounter) {
    this(languageVersion, programSource, locals, operandStack, receiver, verbLocation,
        threadMode, taskPlayer, programmer, debug, verb, verbNames, temporary,
        programCounter, builtinFunctionCounter, errorCounter, Optional.empty());
  }
  /** Takes immutable copies of activation-owned state. */
  public SuspendedActivation {
    Objects.requireNonNull(programSource, "programSource");
    Objects.requireNonNull(locals, "locals");
    Objects.requireNonNull(operandStack, "operandStack");
    Objects.requireNonNull(receiver, "receiver");
    Objects.requireNonNull(verbLocation, "verbLocation");
    Objects.requireNonNull(verb, "verb");
    Objects.requireNonNull(verbNames, "verbNames");
    Objects.requireNonNull(temporary, "temporary");
    Objects.requireNonNull(builtinContinuation, "builtinContinuation");
    locals = Collections.unmodifiableMap(new LinkedHashMap<>(locals));
    operandStack = List.copyOf(operandStack);
  }
}
