package world.mongoose.banteng.builtin;

import java.util.Objects;

/** Value-only request to publish one checkpoint after the current transaction commits. */
public record CheckpointRequest(boolean shutdown, boolean panic, String message) {
  public CheckpointRequest {
    Objects.requireNonNull(message, "message");
    if (panic && shutdown) {
      throw new IllegalArgumentException("panic and clean shutdown are mutually exclusive");
    }
  }

  /** Creates an ordinary or clean-shutdown checkpoint request. */
  public CheckpointRequest(boolean shutdown) {
    this(shutdown, false, "");
  }

  /** Creates the ordinary non-shutdown checkpoint request used by dump_database(). */
  public CheckpointRequest() {
    this(false);
  }

  /** Creates the unclean panic-dump request used by shutdown(message, true). */
  public static CheckpointRequest panic(String message) {
    return new CheckpointRequest(false, true, message);
  }
}
