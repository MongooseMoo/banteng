package world.mongoose.banteng.world;

/** Toast property flags, including inherited owner reassignment. */
public record PropertyPerms(int bits) {
  public boolean readable() { return (bits & 1) != 0; }
  public boolean writable() { return (bits & 2) != 0; }
  public boolean chownable() { return (bits & 4) != 0; }
}

