package world.mongoose.banteng.host;

/** Host linkage or invocation failure translated at the native boundary. */
public final class NativeCallException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  NativeCallException(String operation, Throwable cause) {
    super(operation + " invocation failed", cause);
  }
}
