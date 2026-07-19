package moo.builtin;

import moo.world.WorldObject;
import moo.world.WorldTxn;

/** Runtime permission predicate attached directly to one builtin manifest entry. */
@FunctionalInterface
public interface BuiltinPermissionRule {
  BuiltinPermissionRule ANY = (world, programmer) -> true;

  BuiltinPermissionRule WIZARD_ONLY =
      (world, programmer) -> {
        WorldObject actor = world.object(programmer).orElse(null);
        return actor != null && (actor.flags() & 4) != 0;
      };

  /** Returns whether the current programmer may invoke the builtin. */
  boolean allows(WorldTxn world, long programmer);
}
