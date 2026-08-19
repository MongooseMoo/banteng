package world.mongoose.banteng.value;

/** Immutable runtime configuration for value operations. */
public record ValueSemantics(boolean promoteNumbers) {
  /** Stock Toast-compatible value behavior. */
  public static final ValueSemantics STANDARD = new ValueSemantics(false);
}
