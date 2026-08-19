package world.mongoose.banteng.rewrite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;

final class RecipeCatalogTest {
  private static final List<String> RECIPES =
      List.of(
          "world.mongoose.banteng.vm.DecomposeMooVmOpcodeFamilies",
          "world.mongoose.banteng.builtin.ExtractBuiltinResult",
          "world.mongoose.banteng.builtin.UseBuiltinCall",
          "world.mongoose.banteng.value.UseStringValueFactories",
          "world.mongoose.banteng.persistence.ShareDbReaderInfrastructure",
          "world.mongoose.banteng.builtin.ConsolidateUnaryFloatHandlers",
          "world.mongoose.banteng.persistence.DecomposeToastV17ProgramLayout",
          "world.mongoose.banteng.world.DecomposeWorldTxn",
          "world.mongoose.banteng.ChangePackage");

  @Test
  void everyNamedRecipeResolvesFromThePackagedCatalog() {
    Environment environment = Environment.builder().scanRuntimeClasspath().build();

    for (String recipeName : RECIPES) {
      Recipe recipe =
          assertDoesNotThrow(() -> environment.activateRecipes(recipeName), recipeName);
      assertEquals(recipeName, recipe.getName());
    }
  }
}
