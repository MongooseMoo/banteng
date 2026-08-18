package moo.world;

/** Canonical Toast-compatible bit layout and queries for permanent object flags. */
public final class ObjectFlags {
  public static final int FLAG_USER = 1;
  public static final int FLAG_PROGRAMMER = 1 << 1;
  public static final int FLAG_WIZARD = 1 << 2;
  public static final int FLAG_OBSOLETE_1 = 1 << 3;
  public static final int FLAG_READ = 1 << 4;
  public static final int FLAG_WRITE = 1 << 5;
  public static final int FLAG_OBSOLETE_2 = 1 << 6;
  public static final int FLAG_FERTILE = 1 << 7;
  public static final int FLAG_ANONYMOUS = 1 << 8;
  public static final int FLAG_INVALID = 1 << 9;
  public static final int FLAG_RECYCLED = 1 << 10;

  private ObjectFlags() {}

  /** Returns whether the programmer bit is present. */
  public static boolean isProgrammer(int flags) {
    return (flags & FLAG_PROGRAMMER) != 0;
  }

  /** Returns whether the wizard bit is present. */
  public static boolean isWizard(int flags) {
    return (flags & FLAG_WIZARD) != 0;
  }

  /** Returns whether the public-read bit is present. */
  public static boolean isReadable(int flags) {
    return (flags & FLAG_READ) != 0;
  }

  /** Returns whether the public-write bit is present. */
  public static boolean isWritable(int flags) {
    return (flags & FLAG_WRITE) != 0;
  }

  /** Returns whether the fertile bit is present. */
  public static boolean isFertile(int flags) {
    return (flags & FLAG_FERTILE) != 0;
  }
}
