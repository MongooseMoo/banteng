package world.mongoose.banteng.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinHosts;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldTxn;

final class NestedPropertyAssignmentTest {
  private record Update(String initial, String path, String value, String updated) {}

  @Test
  void collectionFixtureWritesPreserveSavedValuesOtherHoldersAndInheritedSlots() {
    // Real producers: semantic_property_collection_updates.yaml in conformance revision 73c1ae9.
    List<Update> updates = List.of(
        new Update("{{1,2},{3,4}}", "[1][2]", "9", "{{1,9},{3,4}}"),
        new Update("{{1,2},{3,4}}", "[1][1..2]", "{9}", "{{9},{3,4}}"),
        new Update("[\"a\" -> [\"b\" -> 1]]", "[\"a\"][\"b\"]", "9",
            "[\"a\" -> [\"b\" -> 9]]"),
        new Update("[\"a\" -> {1,2}]", "[\"a\"][2]", "9", "[\"a\" -> {1,9}]"),
        new Update("{[\"a\" -> 1]}", "[1][\"a\"]", "9", "{[\"a\" -> 9]}"),
        new Update("{\"ab\"}", "[1][2]", "\"X\"", "{\"aX\"}"),
        new Update("[\"a\" -> \"abc\"]", "[\"a\"][2..2]", "\"XY\"",
            "[\"a\" -> \"aXYc\"]"),
        new Update("{[\"a\" -> {1,2}]}", "[1][\"a\"][2]", "9",
            "{[\"a\" -> {1,9}]}"),
        new Update("{{0},{1,2,3}}", "[$][$]", "9", "{{0},{1,2,9}}"),
        new Update("{{0},\"abc\"}", "[$][^..$]", "\"XY\"", "{{0},\"XY\"}"),
        new Update("{[\"a\" -> {1,2}]}", "[1][\"a\"][1..2]", "{9}",
            "{[\"a\" -> {9}]}"));
    for (Update update : updates) {
      for (int mode = 0; mode < 3; mode++) {
        String setup = mode == 2 ? "c.x = p.x;" : "";
        String target = mode == 0 ? "p" : "c";
        String parentValue = mode == 0 ? update.updated() : update.initial();
        String source = """
            p = create(#-1);
            add_property(p, "x", %s, {player, "rw"});
            c = create(p); sibling = create(p);
            %s
            t = %s; saved = t.x; before_clear = is_clear_property(t, "x");
            outcome = `t.x%s = %s ! ANY';
            return {before_clear, equal(outcome, %s), equal(t.x, %s),
                    equal(p.x, %s), equal(sibling.x, %s), equal(saved, %s),
                    is_clear_property(t, "x")};
            """.formatted(update.initial(), setup, target, update.path(), update.value(),
                update.value(), update.updated(), parentValue, parentValue, update.initial());
        assertEquals(integers(mode == 1 ? 1 : 0, 1, 1, 1, 1, 1, 0), run(source),
            mode + ": " + update.path());
      }
    }
  }

  @Test
  void failedUpdatesPreserveClearInheritedSlotsAndSubsequentAssignments() {
    List<Update> updates = List.of(
        new Update("{1}", "[1][1]", "9", "E_TYPE"),
        new Update("[\"a\" -> {1}]", "[\"b\"][1]", "9", "E_RANGE"),
        new Update("{{1}}", "[1][2]", "9", "E_RANGE"),
        new Update("{{1}}", "[1][1..1]", "\"x\"", "E_TYPE"),
        new Update("\"abc\"", "[2]", "\"XY\"", "E_INVARG"),
        new Update("{\"abc\"}", "[1][2]", "\"\"", "E_INVARG"));
    for (Update update : updates) {
      String source = """
          p = create(#-1); add_property(p, "x", %s, {player, "rw"});
          c = create(p); saved = c.x;
          outcome = `c.x%s = %s ! ANY';
          preserved = {outcome == %s, is_clear_property(c, "x"),
                       equal(c.x, saved), equal(p.x, saved)};
          c.x = {{1,2}}; c.x[1][2] = 9;
          return {@preserved, equal(c.x, {{1,9}})};
          """.formatted(update.initial(), update.path(), update.value(), update.updated());
      assertEquals(integers(1, 1, 1, 1, 1), run(source), update.path());
    }
  }

  @Test
  void evaluatesPropertyReceiverNameIndicesAndValueOnceInOrder() {
    String source = """
        this.x = {{1,2}}; order = 0;
        result = ((order = order * 10 + 1) ? this | this).
                 ((order = order * 10 + 2) ? "x" | "x")
                 [(order = order * 10 + 3) ? 1 | 1]
                 [(order = order * 10 + 4) ? 2 | 2] =
                 ((order = order * 10 + 5) ? 9 | 9);
        return {order, result, equal(this.x, {{1,9}})};
        """;
    assertEquals(integers(12345, 9, 1), run("add_property(this, \"x\", {}, {player, \"rw\"});" + source));
  }

  @Test
  void propertyWritePermissionIsCheckedAfterConstructingNestedReplacement() {
    WorldTxn root = world();
    try (WorldTxn transaction = root.begin()) {
      execute("add_property(this, \"x\", {{1,2}}, {player, \"r\"});", 1, transaction);
      assertTrue(transaction.commit().isCommitted());
    }
    try (WorldTxn transaction = root.begin()) {
      assertEquals(integers(1, 1), execute("""
          error = `this.x[1][2] = 9 ! ANY';
          return {error == E_PERM, equal(this.x, {{1,2}})};
          """, 2, transaction));
    }
  }

  private static MooValue run(String source) {
    try (WorldTxn transaction = world().begin()) {
      return execute(source, 1, transaction);
    }
  }

  private static MooValue execute(String source, long permissions, WorldTxn transaction) {
    BytecodeProgram program = new MooCompiler().compileResult(source).program().orElseThrow();
    VmState state = new VmState(new TaskOrigin(Map.of("player", new ObjectValue(permissions), "this", new ObjectValue(1)), permissions, TaskOrigin.inferVerbLocation(Map.of("player", new ObjectValue(permissions), "this", new ObjectValue(1))),
        TaskOrigin.calledVerb(Map.of("player", new ObjectValue(permissions), "this", new ObjectValue(1))), TaskOrigin.calledVerb(Map.of("player", new ObjectValue(permissions), "this", new ObjectValue(1))), true), new TaskBudget(60_000, 5, 50));
    BuiltinCatalog builtins = new BuiltinCatalog(BuiltinHosts.builder().build());
    MooVm vm = new MooVm();
    vm.execute(program, state, transaction, builtins, builtins.hosts(), 0);
    while (state.outcome() == VmState.Outcome.PENDING_BUILTIN) {
      vm.authorizePendingBuiltin(state, transaction, builtins, 0);
      vm.execute(program, state, transaction, builtins, builtins.hosts(), 0);
    }
    assertEquals(VmState.Outcome.RETURNED, state.outcome(), source);
    assertTrue(state.uncaughtError().isEmpty(), source);
    return state.returnValue().orElse(new IntegerValue(0));
  }

  private static WorldTxn world() {
    return new WorldTxn(List.of(1L), List.of(
        new WorldObject(1, "wizard", 4, 1, -1, -1, List.of(), List.of(), List.of(), List.of()),
        new WorldObject(2, "reader", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of())));
  }

  private static ListValue integers(int... values) {
    return new ListValue(java.util.Arrays.stream(values)
        .mapToObj(value -> (MooValue) new IntegerValue(value)).toList());
  }
}
