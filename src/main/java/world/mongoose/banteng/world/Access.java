package world.mongoose.banteng.world;

/** Shared permission grants for owned verb and property metadata. */
public final class Access {
  private Access() {}

  private static boolean allows(WorldTxn world, long actor, long owner, boolean flag) {
    return actor == owner || world.isWizard(actor) || flag;
  }

  public static boolean canRead(WorldTxn world, long actor, long owner, VerbPerms perms) {
    return allows(world, actor, owner, perms.readable());
  }

  public static boolean canRead(WorldTxn world, long actor, long owner, PropertyPerms perms) {
    return allows(world, actor, owner, perms.readable());
  }

  public static boolean canWrite(WorldTxn world, long actor, long owner, VerbPerms perms) {
    return allows(world, actor, owner, perms.writable());
  }

  public static boolean canWrite(WorldTxn world, long actor, long owner, PropertyPerms perms) {
    return allows(world, actor, owner, perms.writable());
  }

  public static boolean canExecute(WorldTxn world, long actor, long owner, VerbPerms perms) {
    return allows(world, actor, owner, perms.executable());
  }
}

