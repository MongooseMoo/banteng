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
    long errorCounter) {
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
    locals = Collections.unmodifiableMap(new LinkedHashMap<>(locals));
    operandStack = List.copyOf(operandStack);
  }
}
