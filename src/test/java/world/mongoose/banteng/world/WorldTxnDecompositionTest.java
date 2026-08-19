package world.mongoose.banteng.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class WorldTxnDecompositionTest {
  @Test
  void anonymousPropertyHolderBranchDoesNotBindAnUnreadPatternVariable() throws Exception {
    String source =
        Files.readString(
                Path.of(
                    "src",
                    "main",
                    "java",
                    "world",
                    "mongoose",
                    "banteng",
                    "world",
                    "WorldTxn.java"))
            .replace("\r\n", "\n");

    assertFalse(source.contains("case WorldAnonymousObject _ ->"), source);
    assertTrue(source.contains("default ->\n          new WorldAnonymousObject("), source);
  }

  @Test
  void worldTransactionOwnsNeitherConnectionStateNorDeadMethods() {
    Set<String> fields =
        Arrays.stream(WorldTxn.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());
    Set<String> methods =
        Arrays.stream(WorldTxn.class.getDeclaredMethods())
            .map(Method::getName)
            .collect(Collectors.toSet());

    assertTrue(
        java.util.Collections.disjoint(
            fields, Set.of("connections", "connectionInfo", "intrinsicCommands")),
        fields.toString());
    assertTrue(
        java.util.Collections.disjoint(
            methods,
            Set.of(
                "openConnection",
                "closeConnection",
                "connectionPlayer",
                "connectedPlayers",
                "connectionInfo",
                "connectionId",
                "intrinsicCommands",
                "setIntrinsicCommands",
                "switchConnectionPlayer",
                "restoreIntrinsicCommands",
                "baseRevision",
                "changeParent")),
        methods.toString());
  }

  @Test
  void propertyLayoutAlgorithmsHaveOneStaticOwner() {
    Set<String> engineMethods =
        Arrays.stream(PropertyLayoutEngine.class.getDeclaredMethods())
            .filter(method -> Modifier.isStatic(method.getModifiers()))
            .map(Method::getName)
            .collect(Collectors.toSet());
    Set<String> worldMethods =
        Arrays.stream(WorldTxn.class.getDeclaredMethods())
            .map(Method::getName)
            .collect(Collectors.toSet());
    Set<String> expected =
        Set.of(
            "validateNewParents",
            "ancestryFromParents",
            "inheritedProperties",
            "rebuildPropertyLayouts",
            "oldPropertySlots",
            "descendantsOf",
            "directParentProperty",
            "usesAffectedAncestor",
            "rebuiltAnonymousProperties");

    assertTrue(engineMethods.containsAll(expected), engineMethods.toString());
    assertTrue(java.util.Collections.disjoint(worldMethods, expected), worldMethods.toString());
  }

  @Test
  void permanentAndAnonymousObjectsShareTheSealedPropertyHolderContract() {
    assertTrue(PropertyHolder.class.isSealed());
    assertEquals(
        Set.of(WorldObject.class, WorldAnonymousObject.class),
        Set.of(PropertyHolder.class.getPermittedSubclasses()));
    assertTrue(PropertyHolder.class.isAssignableFrom(WorldObject.class));
    assertTrue(PropertyHolder.class.isAssignableFrom(WorldAnonymousObject.class));

    List<String> holderMethods =
        Arrays.stream(PropertyHolder.class.getDeclaredMethods()).map(Method::getName).toList();
    assertTrue(
        holderMethods.containsAll(
            List.of("name", "flags", "owner", "parents", "verbs", "properties")));
  }

  @Test
  void committedWorldHasOnlyConvenienceAndCompleteConstructors() {
    long publicConstructors =
        Arrays.stream(WorldTxn.class.getDeclaredConstructors())
            .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
            .count();

    assertTrue(publicConstructors <= 2, "public constructors: " + publicConstructors);
  }

  @Test
  void propertyHolderPreventsMirroredPrivateImplementations() {
    Set<String> privateMethods =
        Arrays.stream(WorldTxn.class.getDeclaredMethods())
            .filter(method -> Modifier.isPrivate(method.getModifiers()))
            .map(Method::getName)
            .collect(Collectors.toSet());

    assertFalse(privateMethods.contains("findProperty"));
    assertTrue(
        privateMethods.containsAll(
            Set.of(
                "replacePropertyHolder",
                "propertyHolder",
                "verbFor",
                "verbLocationFor",
                "propertyFor",
                "readPropertyFor",
                "writePropertyFor",
                "writePropertyForProgrammer",
                "changeParentsFor",
                "addPropertyFor",
                "addVerbFor",
                "setVerbCodeFor")),
        privateMethods.toString());
  }
}
