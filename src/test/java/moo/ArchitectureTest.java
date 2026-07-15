package moo;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ArchitectureTest {
  private static final List<String> PRODUCTION_PACKAGES =
      List.of(
          "app",
          "builtin",
          "bytecode",
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
    assertOnlyDependsOn(productionClasses, "world", "bytecode", "value");
    assertOnlyDependsOn(productionClasses, "vm", "builtin", "bytecode", "value", "world");
    assertOnlyDependsOn(productionClasses, "builtin", "bytecode", "syntax", "value", "world");
    assertOnlyDependsOn(
        productionClasses, "runtime", "builtin", "bytecode", "value", "vm", "world");
    assertOnlyDependsOn(productionClasses, "persistence", "bytecode", "value", "vm", "world");
    assertOnlyDependsOn(productionClasses, "server", "builtin", "runtime", "value", "world");
    assertOnlyDependsOn(
        productionClasses,
        "app",
        "builtin",
        "bytecode",
        "persistence",
        "runtime",
        "server",
        "syntax",
        "value",
        "vm",
        "world");
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
