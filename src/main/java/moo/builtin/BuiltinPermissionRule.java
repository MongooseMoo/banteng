package moo.builtin;

import moo.world.ObjectFlags;
import moo.world.WorldObject;
import moo.world.WorldTxn;

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
