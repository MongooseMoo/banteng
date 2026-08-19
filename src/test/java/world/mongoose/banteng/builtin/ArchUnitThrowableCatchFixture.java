package world.mongoose.banteng.builtin;

/** Bytecode fixture proving that the architecture rule observes caught exception types. */
@SuppressWarnings("NarrowIllegalArgumentCatch")
public final class ArchUnitThrowableCatchFixture {
  private ArchUnitThrowableCatchFixture() {}

  public static void catchThrowable() {
    try {
      operation();
    } catch (Throwable ignored) {
      // Intentionally empty: this class exists only to prove the architecture rule rejects it.
    }
  }

  private static void operation() {}
}
