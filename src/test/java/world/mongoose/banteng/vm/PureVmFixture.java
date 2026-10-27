package world.mongoose.banteng.vm;

import java.util.List;
import java.util.Objects;
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinHosts;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.value.ValueSemantics;
import world.mongoose.banteng.world.WorldTxn;

/** Pure VM execution without publishing world state or authorizing irrevocable builtins. */
final class PureVmFixture {
  private final ValueSemantics valueSemantics;

  PureVmFixture() {
    this(ValueSemantics.STANDARD);
  }

  PureVmFixture(ValueSemantics valueSemantics) {
    this.valueSemantics = Objects.requireNonNull(valueSemantics, "valueSemantics");
  }

  void execute(BytecodeProgram program, VmState state) {
    WorldTxn root = new WorldTxn(List.of(), List.of());
    try (WorldTxn transaction = root.begin()) {
      new MooVm(valueSemantics).execute(program, state, transaction,
          new BuiltinCatalog(BuiltinHosts.builder().valueSemantics(valueSemantics).build()), 0L);
      if (state.outcome() == VmState.Outcome.PENDING_BUILTIN) {
        throw new IllegalStateException("pure VM execution reached an irrevocable builtin");
      }
    }
  }
}
