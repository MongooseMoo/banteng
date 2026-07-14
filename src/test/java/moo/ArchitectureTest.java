package moo;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

final class ArchitectureTest {
  @Test
  void productionPackagesHaveNoCycles() {
    slices()
        .matching("moo.(*)..")
        .should()
        .beFreeOfCycles()
        .check(new ClassFileImporter().importPackages("moo"));
  }
}
