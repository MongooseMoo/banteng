package world.mongoose.banteng.builtin;

import world.mongoose.banteng.world.ObjectFlags;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldTxn;

/** Runtime permission predicate attached directly to one builtin manifest entry. */
@FunctionalInterface
public interface BuiltinPermissionRule {
  BuiltinPermissionRule ANY = (world, programmer) -> true;

  BuiltinPermissionRule PROGRAMMER_ONLY =
      (world, programmer) -> {
        WorldObject actor = world.object(programmer).orElse(null);
        return actor != null && ObjectFlags.isProgrammer(actor.flags());
      };

  BuiltinPermissionRule WIZARD_ONLY =
      (world, programmer) -> {
        WorldObject actor = world.object(programmer).orElse(null);
        return actor != null && ObjectFlags.isWizard(actor.flags());
      };

  /** Returns whether the current programmer may invoke the builtin. */
  boolean allows(WorldTxn world, long programmer);
}
