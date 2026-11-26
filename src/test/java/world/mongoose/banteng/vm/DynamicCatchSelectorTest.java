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
  void compilesRealMongooseHandleReply() {
    // #1596:3 from Q's mongoose.db.new, verb handle_reply.
    new MooCompiler().compile("""
        {session, id} = args;
        if (!$perm_utils:controls(caller_perms(), session.connection))
        raise(E_PERM);
        endif
        if (callback = $list_utils:assoc(session, this.callbacks))
        this.callbacks = setremove(this.callbacks, callback);
        {s, i, o, v} = callback;
        valid(o) && `o:(v)() ! 1';
        endif
        "timing stuff...";
        if (ptiming = $list_utils:iassoc(session, this.times))
        rtt = time() - this.times[ptiming][3];
        times = {@this.times[ptiming][2], rtt};
        times = times[2..$];
        this.times[ptiming][2] = times;
        endif
        "Last modified Tue Jul  4 01:47:29 2006 PDT by splint98 (#2300).";
        """);
  }

  @Test
  void evaluatesSelectorsAndMatchesValuesWithoutTreatingOneAsAny() {
    for (String source : List.of(
        "return `raise(1) ! 1 => 42';",
        "return `raise(\"Custom\") ! \"custom\" => 42';",
        "return ``1 / 0 ! 1 => 0' ! E_DIV => 42';",
        "codes = {E_TYPE, 1}; return `raise(1) ! @codes => 42';",
        "n = 0; try raise(2); except (n = n + 1) return 0; except (n = n + 1) return n + 40; endtry",
        "return {9, `raise(1) ! 1 => 42'}[2];")) {
      var program = new MooCompiler().compile(source);
      VmState state = state();
      new PureVmFixture().execute(program, state);
      assertEquals(VmState.Outcome.RETURNED, state.outcome(), source);
      assertEquals(new IntegerValue(42), state.returnValue().orElseThrow(), source);
    }
  }

  @Test
  void retainsEvaluatedSelectorAcrossSuspendedActivationRoundTrip() {
    String source = "code = 1; try suspend(0); code = 2; raise(1); except (code) return 42; endtry";
    var program = new MooCompiler().compile(source);
    VmState state = state();
    new PureVmFixture().execute(program, state);
    assertEquals(VmState.Outcome.SUSPENDED, state.outcome());
    var snapshot = state.snapshot();
    var durable = DurableTaskCodec.durableSuspension(12, 0, snapshot, new IntegerValue(0));
    var frame = DurableTaskCodec.importActivation(durable.activations().getFirst(), -1, true);
    assertEquals(snapshot.frames().getFirst().handlers(), frame.handlers());
    state = VmState.restore(snapshot);
    state.resume(new IntegerValue(0));
    new PureVmFixture().execute(program, state);
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(42), state.returnValue().orElseThrow());
  }

  private static VmState state() {
    return new VmState(new TaskOrigin(Map.of(), 0, new ObjectValue(-1), "", "", true),
        new TaskBudget(60_000, 5, 50));
  }

  @Test
  void canSuspendWhileEvaluatingALaterSelectorBeforeHandlersAreInstalled() {
    for (String first : List.of("E_TYPE", "99")) {
      String source = "try raise(1); except (" + first
          + ") return 0; except (suspend(0)) return 42; endtry";
      var program = new MooCompiler().compile(source);
      VmState state = state();
      new PureVmFixture().execute(program, state);
      assertEquals(VmState.Outcome.SUSPENDED, state.outcome());
      var snapshot = state.snapshot();
      var durable = DurableTaskCodec.durableSuspension(12, 0, snapshot, new IntegerValue(1));
      var frame = DurableTaskCodec.importActivation(durable.activations().getFirst(), -1, true);
      assertEquals(snapshot.frames().getFirst().operandStack(), frame.operandStack());
      assertEquals(snapshot.frames().getFirst().handlers(), frame.handlers());
      state = VmState.restore(snapshot);
      state.resume(new IntegerValue(1));
      new PureVmFixture().execute(program, state);
      assertEquals(VmState.Outcome.RETURNED, state.outcome());
      assertEquals(new IntegerValue(42), state.returnValue().orElseThrow());
    }
  }
}
