package moo;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import moo.builtin.ArchUnitThrowableCatchFixture;
import moo.persistence.ToastV17ProgramLayout;
import org.junit.jupiter.api.Test;

final class ArchitectureTest {
  private static final List<String> PRODUCTION_PACKAGES =
      List.of(
          "app",
          "builtin",
          "bytecode",
          "host",
          "logging",
          "persistence",
          "runtime",
          "server",
          "syntax",
          "value",
          "vm",
          "world");

  @Test
  void productionPackagesHaveNoCycles() {
    slices().matching("moo.(*)..").should().beFreeOfCycles().check(productionClasses());
  }

  @Test
  void productionPackagesRespectAllowedDependencies() {
    JavaClasses productionClasses = productionClasses();

    assertOnlyDependsOn(productionClasses, "value");
    assertOnlyDependsOn(productionClasses, "syntax");
    assertOnlyDependsOn(productionClasses, "bytecode", "syntax", "value");
    assertOnlyDependsOn(productionClasses, "host");
    assertOnlyDependsOn(productionClasses, "logging");
    assertOnlyDependsOn(productionClasses, "world", "bytecode", "value");
    assertOnlyDependsOn(productionClasses, "vm", "builtin", "bytecode", "value", "world");
    assertOnlyDependsOn(
        productionClasses, "builtin", "bytecode", "host", "logging", "syntax", "value", "world");
    assertOnlyDependsOn(
        productionClasses,
        "runtime",
        "builtin",
        "bytecode",
        "logging",
        "persistence",
        "value",
        "vm",
        "world");
    assertOnlyDependsOn(
        productionClasses,
        "persistence",
        "bytecode",
        "logging",
        "syntax",
        "value",
        "vm",
        "world");
    noClasses()
        .that()
        .resideInAPackage("moo.persistence..")
        .and()
        .doNotBelongToAnyOf(ToastV17ProgramLayout.class)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("moo.syntax..")
        .check(productionClasses);
    assertOnlyDependsOn(
        productionClasses,
        "server",
        "builtin",
        "logging",
        "persistence",
        "runtime",
        "value",
        "world");
    assertOnlyDependsOn(
        productionClasses,
        "app",
        "builtin",
        "bytecode",
        "logging",
        "persistence",
        "runtime",
        "server",
        "syntax",
        "value",
        "vm",
        "world");
  }

  @Test
  void testOnlyWorldAndPersistenceMethodsAreNotPublicApi() {
    noMethods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAnyPackage("moo.world..", "moo.persistence..")
        .and()
        .haveNameMatching("stageEffect|resolveToastFinallyLabel")
        .should()
        .bePublic()
        .check(productionClasses());
  }

  @Test
  void builtinsNeverCatchThrowable() {
    builtinThrowableCatchRule().check(productionClasses());
  }

  @Test
  void builtinThrowableCatchRuleRejectsFocusedFixture() {
    JavaClasses fixture =
        new ClassFileImporter().importClasses(ArchUnitThrowableCatchFixture.class);

    AssertionError violation =
        assertThrows(AssertionError.class, () -> builtinThrowableCatchRule().check(fixture));
    assertTrue(violation.getMessage().contains("ArchUnitThrowableCatchFixture"));
    assertTrue(violation.getMessage().contains("java.lang.Throwable"));
  }

  private static ArchRule builtinThrowableCatchRule() {
    return classes()
        .that()
        .resideInAPackage("moo.builtin..")
        .should(
            new ArchCondition<JavaClass>("not catch java.lang.Throwable") {
              @Override
              public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.getCodeUnits().stream()
                    .flatMap(codeUnit -> codeUnit.getTryCatchBlocks().stream())
                    .filter(
                        block ->
                            block.getCaughtThrowables().stream()
                                .anyMatch(caught -> caught.isEquivalentTo(Throwable.class)))
                    .forEach(
                        block ->
                            events.add(
                                SimpleConditionEvent.violated(
                                    block,
                                    block.getOwner().getFullName()
                                        + " catches java.lang.Throwable at "
                                        + block.getSourceCodeLocation())));
              }
            });
  }

  private static JavaClasses productionClasses() {
    return new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("moo");
  }

  private static void assertOnlyDependsOn(
      JavaClasses productionClasses, String owner, String... allowedDependencies) {
    Set<String> allowed = new HashSet<>(List.of(allowedDependencies));
    allowed.add(owner);
    String[] forbiddenPackages =
        PRODUCTION_PACKAGES.stream()
            .filter(candidate -> !allowed.contains(candidate))
            .map(candidate -> "moo." + candidate + "..")
            .toArray(String[]::new);

    noClasses()
        .that()
        .resideInAPackage("moo." + owner + "..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(forbiddenPackages)
        .check(productionClasses);
  }
}
