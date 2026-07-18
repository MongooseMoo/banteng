package moo.world;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class WorldAccessArchitectureTest {
  @Test
  void committedWorldAndRevisionImplementationsStayInsideWorldPackage() {
    JavaClasses productionClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("moo");

    noClasses()
        .that()
        .resideOutsideOfPackage("moo.world..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("moo.world.World")
        .check(productionClasses);
    noClasses()
        .that()
        .resideOutsideOfPackage("moo.world..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("moo.world.WorldRevision")
        .check(productionClasses);
    noClasses()
        .that()
        .resideOutsideOfPackage("moo.world..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("moo.world.WorldHistory")
        .check(productionClasses);

    assertFalse(Modifier.isPublic(World.class.getModifiers()));
    assertFalse(Modifier.isPublic(WorldRevision.class.getModifiers()));
    assertFalse(Modifier.isPublic(WorldHistory.class.getModifiers()));
  }

  @Test
  void publicSnapshotIsAClosedImmutableValue() {
    WorldObject object =
        new WorldObject(0, "object", 0, 0, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldSnapshot snapshot = new WorldSnapshot(7, List.of(0L), Map.of(0L, object));

    assertTrue(Modifier.isPublic(WorldSnapshot.class.getModifiers()));
    assertTrue(Modifier.isFinal(WorldSnapshot.class.getModifiers()));
    assertTrue(WorldSnapshot.class.isRecord());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.players().add(1L));
    assertThrows(UnsupportedOperationException.class, () -> snapshot.objects().put(1L, object));
  }
}
