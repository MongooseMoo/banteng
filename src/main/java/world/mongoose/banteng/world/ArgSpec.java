package world.mongoose.banteng.world;

/** Toast db_arg_spec with explicit values independent of enum declaration order. */
public enum ArgSpec {
  NONE(0), ANY(1), THIS(2);

  private final int value;

  ArgSpec(int value) {
    this.value = value;
  }

  public int value() {
    return value;
  }

  public static ArgSpec fromValue(int value) {
    return switch (value) {
      case 0 -> NONE;
      case 1 -> ANY;
      case 2 -> THIS;
      default -> throw new IllegalArgumentException("invalid argument specification: " + value);
    };
  }
}
