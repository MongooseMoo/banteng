package world.mongoose.banteng.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import world.mongoose.banteng.value.MooValue;

/** Ordered queued-task runtime slots, including Toast's uninitialized variables. */
public record QueuedEnvironment(Map<String, Optional<MooValue>> slots) {
  /** Takes an immutable ordered copy of the saved runtime environment. */
  public QueuedEnvironment {
    Objects.requireNonNull(slots, "slots");
    slots.forEach(
        (name, value) -> {
          Objects.requireNonNull(name, "queued-task variable name");
          Objects.requireNonNull(value, "queued-task variable slot");
        });
    slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
  }

  static QueuedEnvironment assigned(Map<String, MooValue> locals) {
    Objects.requireNonNull(locals, "initialLocals");
    Map<String, Optional<MooValue>> slots = new LinkedHashMap<>();
    locals.forEach((name, value) -> slots.put(name, Optional.of(value)));
    return new QueuedEnvironment(slots);
  }

  Map<String, MooValue> initializedLocals() {
    Map<String, MooValue> initialized = new LinkedHashMap<>();
    slots.forEach((name, value) -> value.ifPresent(local -> initialized.put(name, local)));
    return Collections.unmodifiableMap(initialized);
  }
}
