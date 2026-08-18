package moo.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.IntegerValue;
import org.junit.jupiter.api.Test;

final class WorldTxnMultipleInheritanceTest {
  @Test
  void diamondAncestryUsesDepthFirstParentOrderWithFirstVisitDeduplication() {
    try (WorldTxn world = diamond().begin()) {
      assertEquals(List.of(4L, 2L, 1L, 3L), world.ancestry(4));
      assertEquals(List.of(4L), world.object(2).orElseThrow().children());
      assertEquals(List.of(4L), world.object(3).orElseThrow().children());
    }
  }

  @Test
  void creationBuildsOneInheritedSlotPerUniqueAncestorDefinitionInCanonicalOrder() {
    WorldTxn root = diamond();
    try (WorldTxn transaction = root.begin()) {
      WorldObject created = transaction.createObject(List.of(2L, 3L), 1);

      assertEquals(List.of(2L, 3L), created.parents());
      assertEquals(List.of("left", "root", "right"), names(created.properties()));
      assertTrue(created.properties().stream().allMatch(WorldProperty::clear));
      assertEquals(List.of(4L, created.id()), transaction.object(2).orElseThrow().children());
      assertEquals(List.of(4L, created.id()), transaction.object(3).orElseThrow().children());
    }
  }

  @Test
  void parentMutationRejectsDuplicatesCyclesAndConflictingAncestorDefinitionsAtomically() {
    WorldTxn root = diamond();
    try (WorldTxn transaction = root.begin()) {
      assertFalse(transaction.changeParents(4, List.of(2L, 2L)).isOk());
      assertFalse(transaction.changeParents(1, List.of(4L)).isOk());
      assertEquals(List.of(2L, 3L), transaction.object(4).orElseThrow().parents());
    }

    WorldProperty duplicate = property("left", 99, true);
    WorldObject conflicting =
        new WorldObject(
            5,
            "conflicting",
            0,
            1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(duplicate));
    WorldTxn conflictRoot =
        new WorldTxn(
            List.of(),
            List.of(
                object(1, "root", List.of(), List.of(2L, 3L), List.of(property("root", 1, true))),
                object(
                    2,
                    "left",
                    List.of(1L),
                    List.of(4L),
                    List.of(property("left", 2, true), property("root", 1, false))),
                object(
                    3,
                    "right",
                    List.of(1L),
                    List.of(4L),
                    List.of(property("right", 3, true), property("root", 1, false))),
                object(
                    4,
                    "child",
                    List.of(2L, 3L),
                    List.of(),
                    List.of(
                        property("left", 2, false),
                        property("root", 1, false),
                        property("right", 3, false))),
                conflicting));
    try (WorldTxn transaction = conflictRoot.begin()) {
      assertFalse(transaction.changeParents(4, List.of(2L, 5L)).isOk());
      assertEquals(List.of(2L, 3L), transaction.object(4).orElseThrow().parents());
    }
  }

  @Test
  void recycleSplicesEveryParentIntoEveryChildAndKeepsReciprocalOrder() {
    WorldTxn root = diamond();
    try (WorldTxn transaction = root.begin()) {
      AnonymousObjectValue anonymous = transaction.createAnonymousObject(List.of(2L, 3L), 1);
      assertTrue(transaction.recycleObject(2).isOk());

      assertEquals(List.of(1L, 3L), transaction.object(4).orElseThrow().parents());
      assertEquals(
          List.of(1L, 3L), transaction.anonymousObject(anonymous).orElseThrow().parents());
      assertEquals(List.of(4L, 3L), transaction.object(1).orElseThrow().children());
      assertEquals(List.of(4L), transaction.object(3).orElseThrow().children());
      assertTrue(transaction.object(2).isEmpty());
    }
  }

  @Test
  void reparentPreservesOverridesOnlyForTheSameDefiningAncestor() {
    WorldObject first =
        object(1, "first", List.of(), List.of(3L), List.of(property("shared", 10, true)));
    WorldObject second =
        object(2, "second", List.of(), List.of(), List.of(property("shared", 20, true)));
    WorldProperty override =
        new WorldProperty("shared", new IntegerValue(99), 1, 0, false, false);
    WorldObject child = object(3, "child", List.of(1L), List.of(), List.of(override));
    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(first, second, child)).begin()) {
      assertTrue(transaction.changeParents(3, List.of(2L)).isOk());

      WorldProperty replacement = transaction.object(3).orElseThrow().properties().getFirst();
      assertEquals(new IntegerValue(20), replacement.value());
      assertTrue(replacement.clear());
    }
  }

  @Test
  void permanentMutationRebuildsAffectedAnonymousInheritorsButNotUnrelatedObjects() {
    WorldTxn root = diamond();
    try (WorldTxn transaction = root.begin()) {
      AnonymousObjectValue anonymous = transaction.createAnonymousObject(List.of(4L), 1);
      WorldObject unrelated = transaction.object(1).orElseThrow();

      assertTrue(transaction.changeParents(4, List.of(2L)).isOk());

      assertEquals(
          List.of("left", "root"),
          names(transaction.anonymousObject(anonymous).orElseThrow().properties()));
      assertSame(unrelated, transaction.object(1).orElseThrow());
    }
  }

  @Test
  void recycleDoesNotDuplicateAChildAlreadyOwnedByAReplacementParent() {
    WorldObject root = object(1, "root", List.of(), List.of(2L, 3L), List.of());
    WorldObject target = object(2, "target", List.of(1L), List.of(3L), List.of());
    WorldObject child = object(3, "child", List.of(2L, 1L), List.of(), List.of());
    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(root, target, child)).begin()) {
      assertTrue(transaction.recycleObject(2).isOk());

      assertEquals(List.of(1L), transaction.object(3).orElseThrow().parents());
      assertEquals(List.of(3L), transaction.object(1).orElseThrow().children());
    }
  }

  @Test
  void recycleRebuildsAnonymousObjectsBelowAnIndirectPermanentDescendant() {
    WorldObject root =
        object(1, "root", List.of(), List.of(2L), List.of(property("root", 1, true)));
    WorldObject target =
        object(
            2,
            "target",
            List.of(1L),
            List.of(3L),
            List.of(property("target", 2, true), property("root", 1, false)));
    WorldObject child =
        object(
            3,
            "child",
            List.of(2L),
            List.of(),
            List.of(
                property("child", 3, true),
                property("target", 2, false),
                property("root", 1, false)));
    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(root, target, child)).begin()) {
      AnonymousObjectValue anonymous = transaction.createAnonymousObject(List.of(3L), 1);
      assertEquals(
          List.of("child", "target", "root"),
          names(transaction.anonymousObject(anonymous).orElseThrow().properties()));

      assertTrue(transaction.recycleObject(2).isOk());

      assertEquals(List.of(1L), transaction.object(3).orElseThrow().parents());
      assertEquals(List.of(3L), transaction.anonymousObject(anonymous).orElseThrow().parents());
      assertEquals(
          List.of("child", "root"),
          names(transaction.anonymousObject(anonymous).orElseThrow().properties()));
    }
  }

  @Test
  void largeDagValidationAndLeafReparentReplaceOnlyTheAffectedRecords() {
    int objectCount = 800;
    Map<Long, List<Long>> children = new LinkedHashMap<>();
    for (long id = 0; id < objectCount; id++) {
      children.put(id, new ArrayList<>());
    }
    for (long id = 0; id < objectCount; id++) {
      List<Long> parents = id < 2 ? List.of() : List.of(id - 1, id - 2);
      for (long parent : parents) {
        Objects.requireNonNull(children.get(parent)).add(id);
      }
    }
    List<WorldObject> objects = new ArrayList<>();
    for (long id = 0; id < objectCount; id++) {
      List<Long> parents = id < 2 ? List.of() : List.of(id - 1, id - 2);
      objects.add(
          object(
              id,
              "node " + id,
              parents,
              Objects.requireNonNull(children.get(id)),
              List.of()));
    }
    try (WorldTxn transaction = new WorldTxn(List.of(), objects).begin()) {
      Map<Long, WorldObject> before = transaction.snapshot().objects();

      assertTrue(transaction.changeParents(799, List.of(796L, 795L)).isOk());

      Map<Long, WorldObject> after = transaction.snapshot().objects();
      for (long id : List.of(795L, 796L, 797L, 798L, 799L)) {
        assertNotSame(before.get(id), after.get(id));
      }
      assertSame(before.get(400L), after.get(400L));
    }
  }

  private static WorldTxn diamond() {
    return new WorldTxn(
        List.of(),
        List.of(
            object(1, "root", List.of(), List.of(2L, 3L), List.of(property("root", 1, true))),
            object(
                2,
                "left",
                List.of(1L),
                List.of(4L),
                List.of(property("left", 2, true), property("root", 1, false))),
            object(
                3,
                "right",
                List.of(1L),
                List.of(4L),
                List.of(property("right", 3, true), property("root", 1, false))),
            object(
                4,
                "child",
                List.of(2L, 3L),
                List.of(),
                List.of(
                    property("left", 2, false),
                    property("root", 1, false),
                    property("right", 3, false)))));
  }

  private static WorldObject object(
      long id,
      String name,
      List<Long> parents,
      List<Long> children,
      List<WorldProperty> properties) {
    return new WorldObject(
        id, name, 0, 1, -1, parents, List.of(), children, List.of(), properties);
  }

  private static WorldProperty property(String name, long value, boolean defined) {
    return new WorldProperty(name, new IntegerValue(value), 1, 0, !defined, defined);
  }

  private static List<String> names(List<WorldProperty> properties) {
    return properties.stream().map(WorldProperty::name).toList();
  }
}
