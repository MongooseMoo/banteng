package world.mongoose.banteng.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import world.mongoose.banteng.builtin.BuiltinCall;
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinHosts;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.BooleanValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.ValueSemantics;
import world.mongoose.banteng.world.WorldTxn;

final class ValueSemanticsTest {
  @Test
  void mooEqualityUsesToastCaseAndBooleanRulesWithoutChangingJavaRepresentationEquality() {
    ValueSemantics standard = ValueSemantics.STANDARD;
    ListValue integers = new ListValue(List.of(new IntegerValue(1), text("KEY")));
    ListValue booleans = new ListValue(List.of(BooleanValue.TRUE, text("key")));
    assertFalse(integers.equals(booleans));
    assertTrue(standard.equal(integers, booleans, false));
    assertFalse(standard.equal(integers, booleans, true));
    assertFalse(standard.equal(new IntegerValue(1), new FloatValue(1), false));
    assertTrue(new ValueSemantics(true).equal(new IntegerValue(1), new FloatValue(1), true));

    StringValue upper = StringValue.of("À".getBytes(StandardCharsets.UTF_8));
    StringValue lower = StringValue.of("à".getBytes(StandardCharsets.UTF_8));
    assertEquals(upper, lower);
    assertFalse(standard.equal(upper, lower, false));
    assertTrue(standard.compare(text("A"), BooleanValue.TRUE, false) > 0);
    assertEquals(1, standard.compare(new FloatValue(Double.NaN), new FloatValue(1), false));
    assertFalse(standard.equal(new FloatValue(Double.NaN), new FloatValue(Double.NaN), false));
    FloatValue nan = new FloatValue(Double.NaN);
    ListValue shared = new ListValue(List.of(nan));
    assertTrue(standard.equal(shared, shared, false));
    assertFalse(standard.equal(shared, new ListValue(List.of(nan)), false));
  }

  @Test
  void mapKeysKeepTheirFullLongRangeAndMooEqualityUsesTreeOrder() {
    LinkedHashMap<MooValue, MooValue> entries = new LinkedHashMap<>();
    entries.put(new IntegerValue(4_294_967_296L), text("large"));
    entries.put(new IntegerValue(0), text("zero"));
    MapValue map = new MapValue(entries);
    MapValue reversed = new MapValue(Map.of(new IntegerValue(0), text("zero"),
        new IntegerValue(4_294_967_296L), text("large")));
    assertEquals(2, map.size());
    assertEquals(text("zero"), map.get(new IntegerValue(0)).orElseThrow());
    assertEquals(text("large"), map.get(new IntegerValue(4_294_967_296L)).orElseThrow());
    assertEquals(List.of(new IntegerValue(0), new IntegerValue(4_294_967_296L)),
        map.orderedEntries().stream().map(Map.Entry::getKey).toList());
    assertTrue(ValueSemantics.STANDARD.equal(map, reversed, true));
    assertTrue(ValueSemantics.STANDARD.compare(new IntegerValue(Long.MIN_VALUE),
        new IntegerValue(Long.MAX_VALUE), false) < 0);
  }

  @Test
  void mapLiteralEqualityRequiresBothLookupTreesToBeExhausted() {
    assertEquals(
        new ListValue(List.of(new IntegerValue(0), new IntegerValue(0), new IntegerValue(0),
            new IntegerValue(0), new IntegerValue(1), new IntegerValue(1))),
        execute("""
            a = ["\u00c3\u00a9" -> 1, "\u00c3\u0089" -> 2];
            b = ["\u00c3\u0089" -> 2];
            c = ["\u00c3\u0089" -> 2, "\u00c3\u00a9" -> 1];
            return {a == b, b == a, equal(a, b), equal(b, a), equal(a, c), a == c};
            """, Map.of()));
  }

  @Test
  void mapRangesCopyOrderedKeyValuePairsWithoutJavaKeyCollisions() {
    String setup = "k1 = chr(195, 169); k2 = chr(195, 137); m = [k1 -> 1, k2 -> 2]; ";
    assertEquals(
        new ListValue(List.of(new IntegerValue(2), new IntegerValue(2),
            new IntegerValue(1), new IntegerValue(2))),
        execute(setup + "r = m[k2..k1]; return {m[k2], m[k2..k2][k2], r[k1], r[k2]};", Map.of()));
    assertEquals(
        new ListValue(List.of(new IntegerValue(2), new IntegerValue(1))),
        execute(setup + "a = m; b = m; a[k1..k1] = []; b[k2..k2] = []; return {a[k2], b[k1]};",
            Map.of()));
    assertEquals(
        new ListValue(List.of(new IntegerValue(1), new IntegerValue(2), new IntegerValue(1),
            new IntegerValue(2), new IntegerValue(1), new IntegerValue(2))),
        execute(setup + """
            a = [0 -> 0, 1 -> 1, 2 -> 2]; a[1..1] = m;
            b = m; b[k1..k2] = [];
            c = [0 -> 0, 1 -> 1]; c[1..0] = m;
            return {a[k1], a[k2], b[k1], b[k2], c[k1], c[k2]};
            """, Map.of()));
    assertEquals(
        new ListValue(List.of(new IntegerValue(10), new IntegerValue(3))),
        execute("m = [1 -> 1, 2 -> 2, 3 -> 3]; m[2..2] = [1 -> 10, 3 -> 30]; return {m[1], m[3]};",
            Map.of()));
    assertEquals(new IntegerValue(1),
        execute(setup + "k3 = chr(196); m[k3] = 3; m[k3..k3] = [k2 -> 20]; return m[k1];",
            Map.of()));
    assertEquals(new IntegerValue(2),
        execute("m = [1 -> 1, 2 -> 2, 3 -> 3]; m[3..1] = [2 -> 20]; return m[2];", Map.of()));
    ListValue keys = (ListValue) execute("""
        literal = ["Key" -> 1, "kEY" -> 2];
        updated = ["First" -> 1]; updated["fIRST"] = 2;
        return {mapkeys(literal)[1], mapkeys(updated)[1]};
        """, Map.of());
    assertEquals(List.of("kEY", "fIRST"),
        keys.elements().stream().map(key -> ((StringValue) key).text()).toList());
  }

  @Test
  void anonymousMapKeysUseExistingTreeTopologyAndReferenceIdentity() {
    AnonymousObjectValue first = new AnonymousObjectValue();
    AnonymousObjectValue second = new AnonymousObjectValue();
    AnonymousObjectValue third = new AnonymousObjectValue();
    MapValue map = new MapValue(Map.of()).with(first, new IntegerValue(1))
        .with(second, new IntegerValue(2)).with(third, new IntegerValue(3));
    assertEquals(1, ValueSemantics.STANDARD.compare(first, second, false));
    assertEquals(1, ValueSemantics.STANDARD.compare(second, first, false));
    assertEquals(0, ValueSemantics.STANDARD.compare(first, first, false));
    assertFalse(ValueSemantics.STANDARD.equal(first, second, false));
    assertSame(third, map.orderedEntries().getFirst().getKey());
    assertEquals(new IntegerValue(1), map.get(first).orElseThrow());
    assertEquals(new ListValue(List.of(third, second, first)), builtin("mapkeys", List.of(map), false));
    assertEquals(new ListValue(List.of(new IntegerValue(3), new IntegerValue(2), new IntegerValue(1))),
        builtin("mapvalues", List.of(map), false));
  }

  @Test
  void setBuiltinsShareBooleanIntegerAndConfiguredNumericMembership() {
    ListValue list = new ListValue(List.of(new IntegerValue(1)));
    assertSame(list, builtin("setadd", List.of(list, BooleanValue.TRUE), false));
    assertEquals(new ListValue(List.of()), builtin("setremove", List.of(list, BooleanValue.TRUE), false));
    ListValue numbers = new ListValue(List.of(new IntegerValue(1), BooleanValue.TRUE, new FloatValue(1)));
    assertEquals(new ListValue(List.of(new IntegerValue(1), new IntegerValue(2))),
        builtin("all_members", List.of(new IntegerValue(1), numbers), false));
    assertEquals(new ListValue(List.of(new IntegerValue(1), new IntegerValue(2), new IntegerValue(3))),
        builtin("all_members", List.of(new IntegerValue(1), numbers), true));
    assertSame(list, builtin("setadd", List.of(list, new FloatValue(1)), true));
    assertEquals(new IntegerValue(0), builtin("equal", List.of(text("A"), text("a")), false));
  }

  @Test
  void vmEqualityMembershipMapRangesAndLoopsUseTheSharedRules() {
    assertEquals(new ListValue(List.of(new IntegerValue(1), new IntegerValue(1))),
        execute("return {true == 1, true in {1}};", Map.of()));
    LinkedHashMap<MooValue, MooValue> entries = new LinkedHashMap<>();
    entries.put(text("b"), new IntegerValue(2));
    entries.put(text("a"), new IntegerValue(1));
    MapValue map = new MapValue(entries);
    assertEquals(new ListValue(List.of(new IntegerValue(1), new IntegerValue(2))),
        execute("r = {}; for v in (m) r = {@r, v}; endfor return r;", Map.of("m", map)));
    assertEquals(new IntegerValue(1), execute("return 1 in m;", Map.of("m", map)));
    assertEquals(new IntegerValue(2), execute("return m[\"A\"..\"B\"][\"b\"];", Map.of("m", map)));
  }

  @Test
  void builtinSortKeepsNaturalAndBooleanPoliciesOutsideScalarComparison() {
    ListValue strings = new ListValue(List.of(text("b10"), text("b2")));
    assertEquals(strings, builtin("sort", List.of(strings), false));
    assertEquals(new ListValue(List.of(text("b2"), text("b10"))),
        builtin("sort", List.of(strings, new ListValue(List.of()), new IntegerValue(1)), false));
    ListValue booleans = new ListValue(List.of(BooleanValue.TRUE, BooleanValue.FALSE));
    assertEquals(booleans, builtin("sort", List.of(booleans), false));
  }

  private static MooValue builtin(String name, List<MooValue> arguments, boolean promote) {
    BuiltinCatalog catalog = new BuiltinCatalog(
        BuiltinHosts.builder().valueSemantics(new ValueSemantics(promote)).build());
    try (WorldTxn world = new WorldTxn(List.of(), List.of()).begin()) {
      BuiltinCall call = new BuiltinCall(arguments, world, 0, new IntegerValue(0), 1, 60_000, 5,
          new ObjectValue(0), 0, _lineNumbers -> new ListValue(List.of()), false,
          (_connection, bytes) -> bytes);
      BuiltinResult result = catalog.invoke(catalog.spec(name).orElseThrow(), call);
      return ((BuiltinResult.Value) result).value();
    }
  }

  private static MooValue execute(String source, Map<String, MooValue> locals) {
    VmState state = new VmState(locals, 0, new ObjectValue(0));
    new PureVmFixture().execute(new MooCompiler().compile(source), state);
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    return state.returnValue().orElseThrow();
  }

  private static StringValue text(String text) {
    return StringValue.of(text);
  }
}
