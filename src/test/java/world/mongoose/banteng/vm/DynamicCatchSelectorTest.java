package world.mongoose.banteng.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.persistence.DurableTaskCodec;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;

final class DynamicCatchSelectorTest {
  @Test
  void retainsEvaluatedSelectorAcrossSuspendedActivationRoundTrip() {
    assertResumesWithSameSelectors(
        "code = 1; try suspend(0); code = 2; raise(1); except (code) return 42; endtry");
  }

  @Test
  void retainsEarlierSelectorsWhenASelectorSuspendsBeforeHandlersAreInstalled() {
    for (String first : List.of("E_TYPE", "99")) {
      assertResumesWithSameSelectors("try raise(1); except (" + first
          + ") return 0; except (suspend(0)) return 42; endtry");
    }
  }

  @Test
  void retainsEnclosingOperandsWhenACatchHandlerSuspends() {
    for (String source : List.of(
        "return {1 + `1/0 ! ANY => 5 + suspend()', {`1/0 ! E_DIV => \"d\"'}};",
        "try 1/0; except err (E_DIV, \"unused\") return {err[1], suspend()}; endtry")) {
      var program = new MooCompiler().compile(source);
      VmState state = new VmState(new TaskOrigin(Map.of(), 0, new ObjectValue(-1), "", "", true),
          new TaskBudget(60_000, 5, 50));
      var vm = new PureVmFixture();
      vm.execute(program, state);
      assertEquals(VmState.Outcome.SUSPENDED, state.outcome(), source);
      var snapshot = state.snapshot();
      var durable = DurableTaskCodec.durableSuspension(12, 0, snapshot, new IntegerValue(10));
      var before = snapshot.frames().getFirst();
      var imported = DurableTaskCodec.importActivation(durable.activations().getFirst(), -1, true);
      assertEquals(before.operandStack(), imported.operandStack(), source);
      assertEquals(before.handlers(), imported.handlers(), source);
    }
  }

  private static void assertResumesWithSameSelectors(String source) {
    var program = new MooCompiler().compile(source);
    VmState state = new VmState(new TaskOrigin(Map.of(), 0, new ObjectValue(-1), "", "", true),
        new TaskBudget(60_000, 5, 50));
    var vm = new PureVmFixture();
    vm.execute(program, state);
    assertEquals(VmState.Outcome.SUSPENDED, state.outcome(), source);
    var snapshot = state.snapshot();
    var durable = DurableTaskCodec.durableSuspension(12, 0, snapshot, new IntegerValue(1));
    var before = snapshot.frames().getFirst();
    var imported = DurableTaskCodec.importActivation(durable.activations().getFirst(), -1, true);
    assertEquals(before.operandStack(), imported.operandStack(), source);
    assertEquals(before.handlers(), imported.handlers(), source);
    state = VmState.restore(snapshot);
    state.resume(new IntegerValue(1));
    vm.execute(program, state);
    assertEquals(VmState.Outcome.RETURNED, state.outcome(), source);
    assertEquals(new IntegerValue(42), state.returnValue().orElseThrow(), source);
  }
}
