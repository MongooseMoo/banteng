package moo.world;

import java.util.Objects;
import moo.value.MooValue.ErrorValue;

/** One world-layer mutation failure expressed as its exact MOO error value. */
public record MooError(ErrorValue value) {
  public MooError {
    Objects.requireNonNull(value, "value");
  }
}
