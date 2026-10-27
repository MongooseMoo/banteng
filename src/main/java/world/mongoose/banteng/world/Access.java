package world.mongoose.banteng.world;

import java.util.Optional;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;

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

  public static Optional<ErrorValue> resolveOwnershipPreamble(
      MooValue receiver, long newOwner, WorldTxn world, long programmer) {
    long targetOwner;
    int targetFlags;
    if (receiver instanceof ObjectValue object) {
      WorldObject target = world.object(object.value()).orElse(null);
      if (target == null) {
        return Optional.of(ErrorValue.E_INVARG);
      }
      targetOwner = target.owner();
      targetFlags = target.flags();
    } else if (receiver instanceof AnonymousObjectValue anonymous) {
      WorldAnonymousObject target = world.anonymousObject(anonymous).orElse(null);
      if (target == null) {
        return Optional.of(ErrorValue.E_INVARG);
      }
      targetOwner = target.owner();
      targetFlags = target.flags();
    } else {
      return Optional.of(ErrorValue.E_TYPE);
    }
    boolean wizard = world.isWizard(programmer);
    boolean writable = targetOwner == programmer || wizard || ObjectFlags.isWritable(targetFlags);
    return writable && (newOwner == programmer || wizard)
        ? Optional.empty()
        : Optional.of(ErrorValue.E_PERM);
  }
}
