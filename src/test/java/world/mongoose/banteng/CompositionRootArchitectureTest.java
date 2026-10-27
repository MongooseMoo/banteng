package world.mongoose.banteng;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The command-line app owns construction; runtime state is independent of database format. */
final class CompositionRootArchitectureTest {
  @Test
  void runtimeAndServerDoNotUseCodecNestedTypes() {
    noClasses().that().resideInAnyPackage(
            "world.mongoose.banteng.runtime..", "world.mongoose.banteng.server..")
        .should().dependOnClassesThat()
        .haveNameMatching("world\\.mongoose\\.banteng\\.persistence\\.LambdaMooV17Codec\\$.*")
        .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("world.mongoose.banteng"));
  }

  @Test
  void onlyAppConstructsTopLevelCollaborators() {
    Set<String> collaborators = Set.of(
        "world.mongoose.banteng.vm.MooVm",
        "world.mongoose.banteng.persistence.LambdaMooV17Codec",
        "world.mongoose.banteng.persistence.LambdaMooV4Reader",
        "world.mongoose.banteng.persistence.LambdaMooV5Reader",
        "world.mongoose.banteng.builtin.BuiltinCatalog",
        "world.mongoose.banteng.runtime.PublicationScheduler",
        "world.mongoose.banteng.runtime.TaskRegistry",
        "world.mongoose.banteng.runtime.MooRuntime",
        "world.mongoose.banteng.server.ConnectionRegistry",
        "world.mongoose.banteng.server.MooServer");
    noClasses().that().resideOutsideOfPackage("world.mongoose.banteng.app..")
        .should(new ArchCondition<JavaClass>("construct top-level collaborators") {
          @Override
          public void check(JavaClass owner, ConditionEvents events) {
            owner.getConstructorCallsFromSelf().stream()
                .filter(call -> collaborators.contains(call.getTargetOwner().getName()))
                .filter(call -> !call.getOriginOwner().equals(call.getTargetOwner()))
                .filter(call -> call.getTarget().resolveMember()
                    .map(constructor -> constructor.getModifiers().contains(JavaModifier.PUBLIC))
                    .orElse(false))
                .forEach(call -> events.add(SimpleConditionEvent.satisfied(call,
                    call.getDescription())));
          }
        })
        .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("world.mongoose.banteng"));
  }
}
