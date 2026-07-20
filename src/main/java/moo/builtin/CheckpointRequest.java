package moo.builtin;

/** Value-only request to publish one checkpoint after the current transaction commits. */
public record CheckpointRequest(boolean shutdown) {
  /** Creates the ordinary non-shutdown checkpoint request used by dump_database(). */
  public CheckpointRequest() {
    this(false);
  }
}
