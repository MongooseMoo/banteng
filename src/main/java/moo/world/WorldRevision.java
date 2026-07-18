package moo.world;

/** The package-owned identity of one committed revision. */
record WorldRevision(long value) {
  WorldRevision {
    if (value < 0) {
      throw new IllegalArgumentException("revision must not be negative");
    }
  }
}
