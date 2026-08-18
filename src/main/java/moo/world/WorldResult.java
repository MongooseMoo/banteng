package moo.world;

import java.util.Objects;
import moo.value.MooValue.ErrorValue;

/** One successful world mutation value or its exact MOO failure reason. */
public sealed interface WorldResult<T> permits WorldResult.Ok, WorldResult.Failed {
  /** A successfully applied mutation. */
  record Ok<T>(T value) implements WorldResult<T> {
    public Ok {
      Objects.requireNonNull(value, "value");
    }
  }

  /** A rejected mutation with the reason callers must propagate. */
  record Failed<T>(MooError reason) implements WorldResult<T> {
    public Failed {
      Objects.requireNonNull(reason, "reason");
    }
  }

  /** Creates one successful mutation result. */
  static <T> WorldResult<T> ok(T value) {
    return new Ok<>(value);
  }

  /** Creates one failed mutation result. */
  static <T> WorldResult<T> failed(ErrorValue reason) {
    return new Failed<>(new MooError(reason));
  }

  /** Returns whether the mutation succeeded. */
  default boolean isOk() {
    return this instanceof Ok<?>;
  }
}
