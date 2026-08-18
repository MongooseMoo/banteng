package moo;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import moo.persistence.ToastV17ProgramLayout;
import org.junit.jupiter.api.Test;

final class ArchitectureTest {
  private static final List<String> PRODUCTION_PACKAGES =
      List.of(
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
    assertOnlyDependsOn(productionClasses, "logging");
    assertOnlyDependsOn(productionClasses, "world", "bytecode", "value");
    assertOnlyDependsOn(productionClasses, "vm", "builtin", "bytecode", "value", "world");
    assertOnlyDependsOn(
        productionClasses, "builtin", "bytecode", "logging", "syntax", "value", "world");
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
  void builtinsNeverCatchOrDependDirectlyOnThrowable() {
    noClasses()
        .that()
        .resideInAPackage("moo.builtin..")
        .should()
        .dependOnClassesThat()
        .areEquivalentTo(Throwable.class)
        .check(productionClasses());
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
