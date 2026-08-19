package world.mongoose.banteng.rewrite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

final class DecomposeWorldTxnTest {
  private static final List<String> MOVED_METHODS =
      List.of(
          "ancestryFromParents",
          "collectAncestry",
          "inheritedProperties",
          "rebuildPropertyLayouts",
          "rebuildPropertyLayout",
          "oldPropertySlots",
          "descendantsOf",
          "directParentProperty",
          "usesAffectedAncestor",
          "rebuiltAnonymousProperties");

  @Test
  void generatesTheEngineFromTheExactCompleteBaseClosureAndMigratesItsCaller() {
    Run run = run(baseWorld(), baseCaller());
    Map<Path, String> rewritten = run.rewritten();
    String world = sourceEndingIn(rewritten, "WorldTxn.java");
    String engine = sourceEndingIn(rewritten, "PropertyLayoutEngine.java");
    String caller = sourceEndingIn(rewritten, "MooRuntimeTestFixture.java");

    assertTrue(engine.contains("final class PropertyLayoutEngine"), engine);
    assertTrue(engine.contains("private PropertyLayoutEngine()"), engine);
    for (String engineImport :
        List.of(
            "java.util.List",
            "java.util.Map",
            "java.util.Optional",
            "java.util.Set")) {
      assertTrue(engine.contains("import " + engineImport + ";"), engine);
    }
    assertFalse(world.contains("baseRevision("), world);
    assertFalse(world.contains("void changeParent(long objectId, long newParentId)"), world);
    assertFalse(world.contains("restoreIntrinsicCommands("), world);
    assertTrue(
        world.contains(
            "void collectAncestry(long id, Set<Long> visited, List<Long> result)"),
        world);
    assertFalse(world.contains("private static void collectAncestry("), world);
    for (String movedMethod : MOVED_METHODS) {
      assertTrue(engine.contains(movedMethod + "("), movedMethod + " missing from:\n" + engine);
    }
    assertTrue(engine.contains("record PropertyDefinition(long objectId, String name)"), engine);
    assertFalse(world.contains("record PropertyDefinition"), world);
    for (String sentinel :
        List.of(
            "ancestry-sentinel",
            "collect-sentinel",
            "inherited-sentinel",
            "rebuild-layouts-sentinel",
            "rebuild-layout-sentinel",
            "old-slots-sentinel",
            "descendants-sentinel",
            "direct-parent-sentinel",
            "affected-ancestor-sentinel",
            "anonymous-layout-sentinel")) {
      assertTrue(engine.contains(sentinel), sentinel + " missing from:\n" + engine);
      assertFalse(world.contains(sentinel), sentinel + " remains in:\n" + world);
    }
    assertTrue(caller.contains("transaction.changeParents(player, List.of(definingObject))"), caller);
    assertFalse(caller.contains("changeParent("), caller);
    for (String retainedCall :
        List.of(
            "ancestryFromParents",
            "inheritedProperties",
            "rebuildPropertyLayouts",
            "descendantsOf",
            "usesAffectedAncestor",
            "rebuiltAnonymousProperties")) {
      assertTrue(
          world.contains("PropertyLayoutEngine." + retainedCall + "("),
          retainedCall + " caller was not qualified in:\n" + world);
    }

    List<SourceFile> rewrittenSources =
        run.results().stream().map(result -> Objects.requireNonNull(result.getAfter())).toList();
    Run fixedPoint = run(rewrittenSources);
    assertTrue(fixedPoint.results().isEmpty(), fixedPoint.results().toString());
  }

  @Test
  void failsClosedWhenOneClosureMemberIsMissing() {
    String incomplete =
        baseWorld().replace(
            "private static boolean usesAffectedAncestor(",
            "private static boolean unrelatedAffectedAncestor(");

    assertTrue(run(incomplete, baseCaller()).results().isEmpty());
  }

  @Test
  void failsClosedWhenTheExactBaseCallerIsMissing() {
    assertTrue(run(baseWorld()).results().isEmpty());
  }

  @Test
  void failsClosedWhenPropertyDefinitionIsMissing() {
    String incomplete =
        baseWorld()
            .replace("  private record PropertyDefinition(long objectId, String name) {}\n\n", "")
            .replace(
                "final class WorldObject {}",
                "record PropertyDefinition(long objectId, String name) {}\n"
                    + "final class WorldObject {}");

    assertTrue(run(incomplete, baseCaller()).results().isEmpty());
  }

  @Test
  void failsClosedOnAnExtraStaticOverloadOfAMovedName() {
    String extra =
        baseWorld().replace(
            "  private record PropertyDefinition",
            "  private static void descendantsOf(String unexpected) {}\n\n"
                + "  private record PropertyDefinition");

    assertTrue(run(extra, baseCaller()).results().isEmpty());
  }

  @Test
  void failsClosedOnAnExtraDeadMethodOverload() {
    String extra =
        baseWorld().replace(
            "  public void changeParents",
            "  public void changeParent(String object, String parent) {}\n"
                + "  public void changeParents");

    assertTrue(run(extra, baseCaller()).results().isEmpty());
  }

  @Test
  void failsClosedWhenTheTargetOwnerAlreadyExists() {
    String collision =
        """
        package world.mongoose.banteng.world;

        final class PropertyLayoutEngine {}
        """;

    assertTrue(run(baseWorld(), baseCaller(), collision).results().isEmpty());
  }

  private static Run run(String... sources) {
    InMemoryExecutionContext context =
        new InMemoryExecutionContext(
            failure -> {
              throw new AssertionError(failure);
            });
    List<SourceFile> parsed =
        JavaParser.fromJavaVersion().build().parse(context, sources).toList();
    return run(parsed, context);
  }

  private static Run run(List<SourceFile> sources) {
    InMemoryExecutionContext context =
        new InMemoryExecutionContext(
            failure -> {
              throw new AssertionError(failure);
            });
    return run(sources, context);
  }

  private static Run run(List<SourceFile> sources, InMemoryExecutionContext context) {
    List<Result> results =
        new DecomposeWorldTxn()
            .run(new InMemoryLargeSourceSet(sources), context)
            .getChangeset()
            .getAllResults();
    return new Run(results);
  }

  private static String sourceEndingIn(Map<Path, String> sources, String fileName) {
    return sources.entrySet().stream()
        .filter(entry -> entry.getKey().endsWith(fileName))
        .findFirst()
        .orElseThrow()
        .getValue();
  }

  private static String baseCaller() {
    return """
        package world.mongoose.banteng.runtime;

        import world.mongoose.banteng.world.WorldTxn;

        final class MooRuntimeTestFixture {
          void configure(WorldTxn transaction, long player, long definingObject) {
            transaction.changeParent(player, definingObject);
          }
        }
        """;
  }

  private static String baseWorld() {
    return """
        package world.mongoose.banteng.world;

        import java.util.List;
        import java.util.Map;
        import java.util.Optional;
        import java.util.Set;

        public final class WorldTxn {
          public WorldTxn() {}

          public long baseRevision() { return 0; }
          public void changeParent(long objectId, long newParentId) {}
          public void changeParents(long objectId, List<Long> newParentIds) {}
          public void restoreIntrinsicCommands(long connectionId) {}

          void collectAncestry(long id, Set<Long> visited, List<Long> result) {}

          void useLayoutAlgorithms(Map<Long, WorldObject> objects) {
            ancestryFromParents(List.of(), objects);
            inheritedProperties(List.of(), 0, objects);
            rebuildPropertyLayouts(objects, objects, Set.of());
            descendantsOf(Set.of(), objects);
            usesAffectedAncestor(List.of(), objects, Set.of());
            rebuiltAnonymousProperties(null, List.of(), objects, null, List.of(), objects);
          }

          private static List<Long> ancestryFromParents(
              List<Long> parents, Map<Long, WorldObject> objects) {
            throw new IllegalStateException("ancestry-sentinel");
          }

          private static void collectAncestry(
              long objectId,
              Map<Long, WorldObject> objects,
              Set<Long> visiting,
              Set<Long> visited,
              List<Long> result) {
            throw new IllegalStateException("collect-sentinel");
          }

          private static List<WorldProperty> inheritedProperties(
              List<Long> parents, long owner, Map<Long, WorldObject> objects) {
            throw new IllegalStateException("inherited-sentinel");
          }

          private static Map<Long, WorldObject> rebuildPropertyLayouts(
              Map<Long, WorldObject> oldSource,
              Map<Long, WorldObject> newSource,
              Set<Long> roots) {
            throw new IllegalStateException("rebuild-layouts-sentinel");
          }

          private static void rebuildPropertyLayout(
              long objectId,
              Map<Long, WorldObject> oldSource,
              Map<Long, WorldObject> newSource,
              Map<Long, WorldObject> rebuilt,
              Set<Long> affected,
              Set<Long> complete) {
            throw new IllegalStateException("rebuild-layout-sentinel");
          }

          private static Map<PropertyDefinition, WorldProperty> oldPropertySlots(
              WorldObject object, Map<Long, WorldObject> source) {
            throw new IllegalStateException("old-slots-sentinel");
          }

          private static Set<Long> descendantsOf(
              Set<Long> roots, Map<Long, WorldObject> objects) {
            throw new IllegalStateException("descendants-sentinel");
          }

          private record PropertyDefinition(long objectId, String name) {}

          private static Optional<WorldProperty> directParentProperty(
              List<Long> parents, String name, Map<Long, WorldObject> objects) {
            throw new IllegalStateException("direct-parent-sentinel");
          }

          private static boolean usesAffectedAncestor(
              List<Long> parents, Map<Long, WorldObject> objects, Set<Long> affectedObjects) {
            throw new IllegalStateException("affected-ancestor-sentinel");
          }

          private static List<WorldProperty> rebuiltAnonymousProperties(
              WorldAnonymousObject oldObject,
              List<Long> oldParents,
              Map<Long, WorldObject> oldObjects,
              WorldAnonymousObject object,
              List<Long> parents,
              Map<Long, WorldObject> objects) {
            throw new IllegalStateException("anonymous-layout-sentinel");
          }
        }

        final class WorldObject {}
        final class WorldProperty {}
        final class WorldAnonymousObject {}
        """;
  }

  private record Run(List<Result> results) {
    Map<Path, String> rewritten() {
      return results.stream()
          .filter(result -> result.getAfter() != null)
          .collect(
              Collectors.toMap(
                  result -> result.getAfter().getSourcePath(),
                  result -> result.getAfter().printAll()));
    }
  }
}
