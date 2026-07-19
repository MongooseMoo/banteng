package moo.builtin;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Complete production contract and implementation for one canonical builtin. */
public record BuiltinSpec(
    String name,
    List<CallShape> callShapes,
    BuiltinPermissionRule permission,
    BuiltinCostRule tickCost,
    EffectClass effect,
    BuiltinOwner owner,
    BuiltinHandler implementation) {
  public BuiltinSpec {
    name = Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
    if (name.isBlank()) {
      throw new IllegalArgumentException("blank builtin name");
    }
    callShapes = List.copyOf(callShapes);
    if (callShapes.isEmpty()) {
      throw new IllegalArgumentException("builtin has no call shape: " + name);
    }
    Objects.requireNonNull(permission, "permission");
    Objects.requireNonNull(tickCost, "tickCost");
    Objects.requireNonNull(effect, "effect");
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(implementation, "implementation");
  }
}
