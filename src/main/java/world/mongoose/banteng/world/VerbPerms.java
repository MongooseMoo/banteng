package world.mongoose.banteng.world;

/** Toast verb flags; argument specifications live separately on WorldVerb. */
public record VerbPerms(int bits) {
  public boolean readable() { return (bits & 1) != 0; }
  public boolean writable() { return (bits & 2) != 0; }
  public boolean executable() { return (bits & 4) != 0; }
  public boolean debug() { return (bits & 8) != 0; }
}

