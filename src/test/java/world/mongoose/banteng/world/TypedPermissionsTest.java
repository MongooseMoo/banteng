package world.mongoose.banteng.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import world.mongoose.banteng.persistence.LambdaMooV17Codec;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.StringValue;

final class TypedPermissionsTest {
  @Test
  void toastFlagsGrantAccessToOwnerWizardOrFlaggedStranger() {
    WorldTxn root = new WorldTxn(List.of(), List.of(
        object(0, ObjectFlags.FLAG_WIZARD, List.of(), List.of()),
        object(1, 0, List.of(), List.of()), object(2, 0, List.of(), List.of())));
    try (WorldTxn world = root.begin()) {
      for (int bits = 0; bits < 16; bits++) {
        VerbPerms verb = new VerbPerms(bits);
        assertEquals((bits & 1) != 0, verb.readable());
        assertEquals((bits & 2) != 0, verb.writable());
        assertEquals((bits & 4) != 0, verb.executable());
        assertEquals((bits & 8) != 0, verb.debug());
        assertTrue(Access.canRead(world, 0, 1, verb));
        assertTrue(Access.canWrite(world, 1, 1, verb));
        assertTrue(Access.canExecute(world, 0, 1, verb));
        assertEquals(verb.readable(), Access.canRead(world, 2, 1, verb));
        assertEquals(verb.writable(), Access.canWrite(world, 2, 1, verb));
        assertEquals(verb.executable(), Access.canExecute(world, 2, 1, verb));
      }
      for (int bits = 0; bits < 8; bits++) {
        PropertyPerms property = new PropertyPerms(bits);
        assertEquals((bits & 4) != 0, property.chownable());
        assertTrue(Access.canRead(world, 1, 1, property));
        assertTrue(Access.canWrite(world, 0, 1, property));
        assertEquals((bits & 1) != 0, Access.canRead(world, 2, 1, property));
        assertEquals((bits & 2) != 0, Access.canWrite(world, 2, 1, property));
      }
      assertFalse(world.isWizard(99));
    }
  }

  @Test
  void metadataChangesPreserveIndependentArgumentsAndExecutableLookup() {
    WorldVerb verb = new WorldVerb("look", 0, new VerbPerms(8), ArgSpec.THIS,
        -1, ArgSpec.ANY, "return 1;");
    WorldTxn root = new WorldTxn(List.of(), List.of(object(0,
        ObjectFlags.FLAG_WIZARD, List.of(verb), List.of())));
    try (WorldTxn world = root.begin()) {
      assertFalse(world.verb(0, "look").isPresent());
      assertTrue(world.setVerbInfo(0, 0, "look", 0, new VerbPerms(12)).isOk());
      WorldVerb changed = world.verb(0, "look").orElseThrow();
      assertEquals(ArgSpec.THIS, changed.dobj());
      assertEquals(ArgSpec.ANY, changed.iobj());
      assertTrue(changed.perms().debug());
      assertTrue(world.setVerbArgs(0, 0, ArgSpec.NONE, -2, ArgSpec.THIS).isOk());
      changed = world.verb(0, "look").orElseThrow();
      assertEquals(new VerbPerms(12), changed.perms());
      assertEquals(ArgSpec.NONE, changed.dobj());
      assertEquals(ArgSpec.THIS, changed.iobj());
      assertEquals("return 1;", changed.programSource());
    }
  }

  @Test
  void v17PersistsEveryArgumentPairForPermanentAndAnonymousVerbs(@TempDir Path directory)
      throws IOException {
    List<WorldVerb> verbs = new ArrayList<>();
    for (ArgSpec direct : ArgSpec.values()) {
      for (ArgSpec indirect : ArgSpec.values()) {
        verbs.add(new WorldVerb(direct + "_" + indirect, 0, new VerbPerms(13),
            direct, -2, indirect, ""));
      }
    }
    AnonymousObjectValue identity = new AnonymousObjectValue();
    WorldProperty reference = new WorldProperty("anonymous", identity, 0,
        new PropertyPerms(7), false, true);
    WorldProperty value = new WorldProperty("value", new IntegerValue(7), 0,
        new PropertyPerms(5), false, true);
    WorldAnonymousObject anonymous = new WorldAnonymousObject("anon", 0, 0,
        List.of(), verbs, List.of(value));
    WorldTxn world = new WorldTxn(List.of(), List.of(object(0, 0, verbs, List.of(reference))),
        Map.of(identity, anonymous), Map.of(), List.of(), 0);
    Path database = directory.resolve("permissions.db");
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    codec.writeAtomic(database, world.snapshot(), List.of(), List.of());
    String persisted = Files.readString(database, StringValue.charset());
    // THIS/ANY with read, execute and debug: 13 + 2*16 + 1*64 = 109.
    assertTrue(persisted.contains("THIS_ANY\n0\n109\n-2\n"));
    WorldSnapshot restored = codec.read(database).world().snapshot();
    WorldObject restoredRoot = Objects.requireNonNull(restored.objects().get(0L));
    assertEquals(verbs, restoredRoot.verbs());
    WorldAnonymousObject restoredAnonymous = restored.anonymousObjects().values().iterator().next();
    assertEquals(verbs, restoredAnonymous.verbs());
    assertEquals(List.of(value), restoredAnonymous.properties());
    assertEquals(new PropertyPerms(7), restoredRoot.properties().getFirst().perms());
  }

  private static WorldObject object(long id, int flags, List<WorldVerb> verbs,
      List<WorldProperty> properties) {
    return new WorldObject(id, "object" + id, flags, id, -1, List.of(), List.of(),
        List.of(), verbs, properties);
  }
}
