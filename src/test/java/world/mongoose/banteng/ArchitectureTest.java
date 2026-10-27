package world.mongoose.banteng;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.nio.file.Path;
import java.util.stream.Collectors;
import world.mongoose.banteng.builtin.ArchUnitThrowableCatchFixture;
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
  void compiledClassesUseTheOwnedNamespace() {
    classes()
        .should()
        .resideInAPackage("world.mongoose.banteng..")
        .check(
            new ClassFileImporter()
                .importPaths(
                    Path.of("build/classes/java/main"),
                    Path.of("build/classes/java/test"),
                    Path.of("build/classes/java/jmh"),
                    Path.of("build/classes/java/jcstress"),
                    Path.of("errorprone-checks/build/classes/java/main"),
                    Path.of("errorprone-checks/build/classes/java/test"),
                    Path.of("rewrite-recipes/build/classes/java/main"),
                    Path.of("rewrite-recipes/build/classes/java/test")));
  }

  @Test
  void serverDiagnosticOwnersAcceptTheSharedLog() {
    classes()
        .that()
        .haveNameMatching(".*\\.(LambdaMooV5Reader|BuiltinHosts|MooRuntime|MooServer)")
        .should(
            new ArchCondition<JavaClass>("accept the shared ServerLog") {
              @Override
              public void check(JavaClass owner, ConditionEvents events) {
                boolean acceptsLog =
                    owner.getSimpleName().equals("MooServer")
                        ? owner.getConstructors().stream()
                            .filter(constructor -> constructor.getModifiers().contains(JavaModifier.PUBLIC))
                            .flatMap(constructor -> constructor.getRawParameterTypes().stream())
                            .anyMatch(type -> type.getName().equals("world.mongoose.banteng.logging.ServerLog"))
                        : owner.getFields().stream()
                            .anyMatch(field -> field.getRawType().getName().equals("world.mongoose.banteng.logging.ServerLog"));
                events.add(new SimpleConditionEvent(owner, acceptsLog, owner.getName() + " accepts ServerLog"));
              }
            })
        .check(productionClasses());
  }

  @Test
  void worldTransactionKeepsItsExistingOwnershipBoundaries() {
    classes()
        .that()
        .haveFullyQualifiedName("world.mongoose.banteng.world.WorldTxn")
        .should(
            new ArchCondition<JavaClass>("keep transaction fields, methods and constructors in their existing owners") {
              @Override
              public void check(JavaClass owner, ConditionEvents events) {
                Set<String> fields = owner.getFields().stream().map(field -> field.getName()).collect(Collectors.toSet());
                Set<String> methods = owner.getMethods().stream().map(method -> method.getName()).collect(Collectors.toSet());
                Set<String> privateMethods = owner.getMethods().stream()
                    .filter(method -> method.getModifiers().contains(JavaModifier.PRIVATE))
                    .map(method -> method.getName()).collect(Collectors.toSet());
                Set<String> layoutMethods = Set.of(
                    "validateNewParents", "ancestryFromParents", "inheritedProperties",
                    "rebuildPropertyLayouts", "oldPropertySlots", "descendantsOf",
                    "directParentProperty", "usesAffectedAncestor", "rebuiltAnonymousProperties");
                boolean boundaries =
                    java.util.Collections.disjoint(fields, Set.of("connections", "connectionInfo", "intrinsicCommands"))
                        && java.util.Collections.disjoint(methods, Set.of(
                            "openConnection", "closeConnection", "connectionPlayer", "connectedPlayers",
                            "connectionInfo", "connectionId", "intrinsicCommands", "setIntrinsicCommands",
                            "switchConnectionPlayer", "restoreIntrinsicCommands", "baseRevision", "changeParent"))
                        && java.util.Collections.disjoint(methods, layoutMethods)
                        && !privateMethods.contains("findProperty")
                        && privateMethods.containsAll(Set.of(
                            "replacePropertyHolder", "propertyHolder", "verbFor", "verbLocationFor",
                            "propertyFor", "readPropertyFor", "writePropertyFor", "writePropertyForProgrammer",
                            "changeParentsFor", "addPropertyFor", "addVerbFor", "setVerbCodeFor"))
                        && owner.getConstructors().stream()
                            .filter(constructor -> constructor.getModifiers().contains(JavaModifier.PUBLIC)).count() <= 2;
                events.add(new SimpleConditionEvent(owner, boundaries, owner.getName() + " preserves transaction ownership"));
              }
            })
        .check(productionClasses());
  }

  @Test
  void propertyLayoutAlgorithmsHaveOneStaticOwner() {
    classes()
        .that()
        .haveFullyQualifiedName("world.mongoose.banteng.world.PropertyLayoutEngine")
        .should(
            new ArchCondition<JavaClass>("own the static property layout algorithms") {
              @Override
              public void check(JavaClass owner, ConditionEvents events) {
                Set<String> methods = owner.getMethods().stream()
                    .filter(method -> method.getModifiers().contains(JavaModifier.STATIC))
                    .map(method -> method.getName()).collect(Collectors.toSet());
                boolean ownsAlgorithms = methods.containsAll(Set.of(
                    "validateNewParents", "ancestryFromParents", "inheritedProperties",
                    "rebuildPropertyLayouts", "oldPropertySlots", "descendantsOf",
                    "directParentProperty", "usesAffectedAncestor", "rebuiltAnonymousProperties"));
                events.add(new SimpleConditionEvent(owner, ownsAlgorithms, owner.getName() + " owns property layout"));
              }
            })
        .check(productionClasses());
  }

  @Test
  void permanentAndAnonymousObjectsShareTheSealedPropertyHolderContract() {
    classes()
        .that()
        .haveFullyQualifiedName("world.mongoose.banteng.world.PropertyHolder")
        .should(
            new ArchCondition<JavaClass>("be the sealed property holder for permanent and anonymous objects") {
              @Override
              public void check(JavaClass owner, ConditionEvents events) {
                // ArchUnit 1.4.2 does not expose PermittedSubclasses; reflection reads that class attribute.
                Class<?> holder = owner.reflect();
                boolean sealed = holder.isSealed()
                    && java.util.Arrays.stream(holder.getPermittedSubclasses())
                        .map(Class::getName).collect(Collectors.toSet())
                        .equals(Set.of("world.mongoose.banteng.world.WorldObject", "world.mongoose.banteng.world.WorldAnonymousObject"));
                boolean members = owner.getMethods().stream().map(method -> method.getName())
                    .collect(Collectors.toSet()).containsAll(Set.of("name", "flags", "owner", "parents", "verbs", "properties"));
                boolean implementations = owner.getAllSubclasses().stream().map(JavaClass::getName)
                    .collect(Collectors.toSet()).containsAll(Set.of("world.mongoose.banteng.world.WorldObject", "world.mongoose.banteng.world.WorldAnonymousObject"));
                events.add(new SimpleConditionEvent(owner, sealed && members && implementations, owner.getName() + " preserves the sealed property contract"));
              }
            })
        .check(productionClasses());
  }

  @Test
  void objectFlagsKeepTheirCanonicalApi() {
    classes()
        .that()
        .haveFullyQualifiedName("world.mongoose.banteng.world.ObjectFlags")
        .should(
            new ArchCondition<JavaClass>("omit the obsolete flag APIs") {
              @Override
              public void check(JavaClass owner, ConditionEvents events) {
                boolean canonical = owner.tryGetField("FLAG_PLAYER").isEmpty()
                    && owner.tryGetMethod("isAnonymous", int.class).isEmpty();
                events.add(new SimpleConditionEvent(owner, canonical, owner.getName() + " has canonical flag APIs"));
              }
            })
        .check(productionClasses());
  }

  @Test
  void readersKeepScannerAndRecordOwnership() {
    JavaClasses productionClasses = productionClasses();
    classes()
        .that()
        .haveNameMatching(".*\\.(LambdaMooV4Reader|LambdaMooV5Reader|LambdaMooV17Codec)")
        .should(
            new ArchCondition<JavaClass>("leave primitives and record types to their shared owners") {
              @Override
              public void check(JavaClass owner, ConditionEvents events) {
                boolean sharedMethods = owner.getMethods().stream()
                    .noneMatch(method -> Set.of(
                        "readCount", "parseCount", "readInt", "parseInt", "readParsedInt",
                        "readLong", "parseLong", "readDouble", "requireExact", "requiredLine", "malformed")
                        .contains(method.getName()));
                boolean sharedRecords = productionClasses.stream()
                    .filter(type -> type.getEnclosingClass().filter(owner::equals).isPresent())
                    .noneMatch(type -> Set.of("ProgramSlot", "RawVerb").contains(type.getSimpleName()));
                events.add(new SimpleConditionEvent(owner, sharedMethods && sharedRecords, owner.getName() + " uses shared scanner and records"));
              }
            })
        .check(productionClasses);
    classes()
        .that()
        .haveNameMatching("world\\.mongoose\\.banteng\\.persistence\\.(ProgramSlot|RawVerb)")
        .should()
        .beTopLevelClasses()
        .check(productionClasses);
  }

  @Test
  void systemErrorIsOwnedOnlyByServerLog() {
    classes()
        .should(
            new ArchCondition<JavaClass>("leave System.err access to the ServerLog source owner") {
              @Override
              public void check(JavaClass owner, ConditionEvents events) {
                JavaClass sourceOwner = owner;
                while (sourceOwner.getEnclosingClass().isPresent()) {
                  sourceOwner = sourceOwner.getEnclosingClass().orElseThrow();
                }
                if (sourceOwner.getName().equals("world.mongoose.banteng.logging.ServerLog")) {
                  return;
                }
                owner.getFieldAccessesFromSelf().stream()
                    .filter(access -> access.getTarget().getOwner().isEquivalentTo(System.class))
                    .filter(access -> access.getTarget().getName().equals("err"))
                    .forEach(access -> events.add(SimpleConditionEvent.violated(access, access.getDescription())));
              }
            })
        .check(productionClasses());
  }

  @Test
  void readersConsumeOnlyTheirAuthorizedSharedValueSurface() {
    JavaClasses productionClasses = productionClasses();
    classes()
        .that()
        .haveNameMatching(".*\\.(LambdaMooV4Reader|LambdaMooV5Reader|LambdaMooV17Codec)")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("world.mongoose.banteng.persistence.DbScanner")
        .check(productionClasses);
    classes()
        .that()
        .haveNameMatching(".*\\.(LambdaMooV5Reader|LambdaMooV17Codec)")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("world.mongoose.banteng.persistence.ValueTagDecoder")
        .check(productionClasses);
    noClasses()
        .that()
        .haveFullyQualifiedName("world.mongoose.banteng.persistence.LambdaMooV4Reader")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("world.mongoose.banteng.persistence.ValueTagDecoder")
        .check(productionClasses);
  }

  @Test
  void monitorOwnedFieldsDeclareTheirGuard() {
    JavaClasses productionClasses = productionClasses();
    assertGuardedFields(
        productionClasses,
        "runtime.PublicationScheduler",
        "this",
        "ready",
        "completed",
        "ingress",
        "lastInputTasks",
        "timedWork",
        "checkpointingWork",
        "finalizationBlocked",
        "activeFinalizations",
        "nextTicket",
        "nextTaskId",
        "nextPublicationTicket",
        "publicationDraining",
        "restoredTasksActivated",
        "closed");
    assertGuardedFields(
        productionClasses,
        "runtime.TaskRegistry",
        "this",
        "tasks",
        "hostWork",
        "cancellationActions",
        "canceled",
        "nextHostHandle",
        "nextQueueSequence");
    assertGuardedFields(
        productionClasses,
        "world.WorldHistory",
        "this",
        "revisions", "activeTransactions", "verbCache", "current");
    assertGuardedFields(
        productionClasses,
        "runtime.MooRuntime",
        "this",
        "publishedConnections", "sessionRevision");
    assertGuardedFields(
        productionClasses,
        "runtime.ConnectionIo",
        "runtime",
        "pendingReads");
  }

  private static void assertGuardedFields(
      JavaClasses productionClasses, String owner, String expectedGuard, String... fieldNames) {
    JavaClass javaClass = productionClasses.get("world.mongoose.banteng." + owner);
    for (String fieldName : fieldNames) {
      var field = javaClass.getField(fieldName);
      assertTrue(field.getModifiers().contains(JavaModifier.PRIVATE), owner + "." + fieldName);
      var annotation =
          field
              .getAnnotationOfType("com.google.errorprone.annotations.concurrent.GuardedBy");
      assertTrue(annotation.get("value").filter(expectedGuard::equals).isPresent(), owner + "." + fieldName);
    }
  }

  @Test
  void productionPackagesHaveNoCycles() {
    slices().matching("world.mongoose.banteng.(*)..").should().beFreeOfCycles().check(productionClasses());
  }

  @Test
  void publicationSchedulerCallbacksDoNotDependOnConcreteRuntime() {
    noClasses()
        .that().haveFullyQualifiedName("world.mongoose.banteng.runtime.PublicationScheduler")
        .should().dependOnClassesThat()
        .haveFullyQualifiedName("world.mongoose.banteng.runtime.MooRuntime")
        .check(productionClasses());
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
        "value",
        "vm",
        "world");
    noClasses()
        .that()
        .resideInAPackage("world.mongoose.banteng.persistence..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("world.mongoose.banteng.syntax..")
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
        .resideInAnyPackage("world.mongoose.banteng.world..", "world.mongoose.banteng.persistence..")
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
    String message = Objects.requireNonNull(violation.getMessage());
    assertTrue(message.contains("ArchUnitThrowableCatchFixture"));
    assertTrue(message.contains("java.lang.Throwable"));
  }

  private static ArchRule builtinThrowableCatchRule() {
    return classes()
        .that()
        .resideInAPackage("world.mongoose.banteng.builtin..")
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
                    .filter(block -> !hasTryWithResourcesScaffolding(block.getOwner()))
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

  private static boolean hasTryWithResourcesScaffolding(JavaCodeUnit codeUnit) {
    // javac implements try-with-resources with synthetic Throwable handlers and addSuppressed.
    return codeUnit.getMethodCallsFromSelf().stream()
        .anyMatch(
            call ->
                call.getTarget().getOwner().isEquivalentTo(Throwable.class)
                    && call.getTarget().getName().equals("addSuppressed"));
  }

  private static JavaClasses productionClasses() {
    return new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("world.mongoose.banteng");
  }

  private static void assertOnlyDependsOn(
      JavaClasses productionClasses, String owner, String... allowedDependencies) {
    Set<String> allowed = new HashSet<>(List.of(allowedDependencies));
    allowed.add(owner);
    String[] forbiddenPackages =
        PRODUCTION_PACKAGES.stream()
            .filter(candidate -> !allowed.contains(candidate))
            .map(candidate -> "world.mongoose.banteng." + candidate + "..")
            .toArray(String[]::new);

    noClasses()
        .that()
        .resideInAPackage("world.mongoose.banteng." + owner + "..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(forbiddenPackages)
        .check(productionClasses);
  }
}
