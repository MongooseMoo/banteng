package moo.builtin;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import moo.value.MooValue;

/** One builtin overload with required, optional, and variadic position contracts. */
public record CallShape(
    List<Set<ArgType>> required,
    List<Set<ArgType>> optional,
    Optional<Set<ArgType>> variadic) {
  public CallShape {
    required = immutablePositions(required);
    optional = immutablePositions(optional);
    variadic = variadic.map(CallShape::immutableTypes);
  }

  /** Returns whether this overload accepts the supplied argument count. */
  public boolean acceptsArity(int argumentCount) {
    if (argumentCount < required.size()) {
      return false;
    }
    return variadic.isPresent() || argumentCount <= required.size() + optional.size();
  }

  /** Returns whether this overload accepts every supplied argument kind. */
  public boolean accepts(List<MooValue> arguments) {
    if (!acceptsArity(arguments.size())) {
      return false;
    }
    for (int index = 0; index < arguments.size(); index++) {
      Set<ArgType> allowed;
      if (index < required.size()) {
        allowed = required.get(index);
      } else if (index < required.size() + optional.size()) {
        allowed = optional.get(index - required.size());
      } else {
        allowed = variadic.orElseThrow();
      }
      MooValue argument = arguments.get(index);
      if (allowed.stream().noneMatch(type -> type.accepts(argument))) {
        return false;
      }
    }
    return true;
  }

  private static List<Set<ArgType>> immutablePositions(List<Set<ArgType>> positions) {
    return positions.stream().map(CallShape::immutableTypes).toList();
  }

  private static Set<ArgType> immutableTypes(Set<ArgType> types) {
    if (types.isEmpty()) {
      throw new IllegalArgumentException("builtin argument position has no allowed types");
    }
    return Collections.unmodifiableSet(new LinkedHashSet<>(types));
  }
}
