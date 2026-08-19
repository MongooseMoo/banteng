package world.mongoose.banteng.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import org.junit.jupiter.api.Test;

final class WorldResultTest {
  @Test
  void representsSuccessAndTypedMooFailureWithoutBooleans() {
    WorldResult.Ok<String> ok = new WorldResult.Ok<>("written");
    WorldResult.Failed<String> failed =
        new WorldResult.Failed<>(new MooError(ErrorValue.E_PERM));

    assertEquals("written", ok.value());
    assertEquals(
        ErrorValue.E_PERM,
        assertInstanceOf(WorldResult.Failed.class, failed).reason().value());
  }

  @Test
  void everyPublicBooleanWorldMutationUsesWorldResult() {
    List<String> publicBooleanMethods =
        Arrays.stream(WorldTxn.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .filter(method -> method.getReturnType().equals(boolean.class))
            .map(method -> method.getName())
            .distinct()
            .toList();

    assertEquals(List.of("valueRefersToWaif"), publicBooleanMethods);
  }
}
