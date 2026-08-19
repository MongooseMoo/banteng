package world.mongoose.banteng.rewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

final class ChangePackageTest implements RewriteTest {
  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipeFromResources("world.mongoose.banteng.ChangePackage")
        .cycles(2)
        .expectedCyclesThatMakeChanges(1);
  }

  @Test
  void movesRecursivePackagesAndReferences() {
    rewriteRun(
        java(
            """
            package moo.example;

            public final class Example {}
            """,
            """
            package world.mongoose.banteng.example;

            public final class Example {}
            """),
        java(
            """
            package moo.caller;

            import moo.example.Example;

            final class Caller {
              private Example example;
            }
            """,
            """
            package world.mongoose.banteng.caller;

            import world.mongoose.banteng.example.Example;

            final class Caller {
              private Example example;
            }
            """));
  }
}
