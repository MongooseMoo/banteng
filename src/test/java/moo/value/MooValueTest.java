package moo.value;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.BooleanValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.value.MooValue.WaifValue;
import org.junit.jupiter.api.Test;

final class MooValueTest {
  @Test
  void familyIsClosedOverExactlyTheTenAuthorizedValues() {
    assertTrue(MooValue.class.isSealed());
    assertEquals(
        Set.of(
            IntegerValue.class,
            BooleanValue.class,
            FloatValue.class,
            StringValue.class,
            ObjectValue.class,
            AnonymousObjectValue.class,
            WaifValue.class,
            ErrorValue.class,
            ListValue.class,
            MapValue.class),
        Set.of(MooValue.class.getPermittedSubclasses()));
    assertEquals(
        List.of(0, 1, 2, 3, 4, 9, 10, 12, 13, 14),
        List.of(MooValue.Type.values()).stream().map(MooValue.Type::code).toList());
  }

  @Test
  void anonymousObjectsUseReferenceIdentityAndTypeTwelve() {
    AnonymousObjectValue first = new AnonymousObjectValue();
    AnonymousObjectValue second = new AnonymousObjectValue();

    assertEquals(MooValue.Type.ANONYMOUS, first.type());
    assertFalse(first.isTruthy());
    assertEquals("*anonymous*", first.toLiteral());
    assertEquals(first, first);
    assertNotEquals(first, second);

    MapValue map = new MapValue(Map.of(first, new IntegerValue(1), second, new IntegerValue(2)));
    assertEquals(new IntegerValue(1), map.get(first).orElseThrow());
    assertEquals(new IntegerValue(2), map.get(second).orElseThrow());
    assertEquals(2, map.size());
  }

  @Test
  void booleansPreserveDistinctTypeTruthLiteralEqualityAndHashing() {
    assertEquals(MooValue.Type.BOOLEAN, BooleanValue.TRUE.type());
    assertTrue(BooleanValue.TRUE.isTruthy());
    assertFalse(BooleanValue.FALSE.isTruthy());
    assertEquals("true", BooleanValue.TRUE.toLiteral());
    assertEquals("false", BooleanValue.FALSE.toLiteral());
    assertEquals(BooleanValue.TRUE, BooleanValue.of(true));
    assertEquals(BooleanValue.FALSE, BooleanValue.of(false));
    assertNotEquals(BooleanValue.TRUE, BooleanValue.FALSE);
  }

  @Test
  void floatsPreserveNumericIdentityTruthHashingAndToastLiteralForm() {
    FloatValue positiveZero = new FloatValue(0.0);
    FloatValue negativeZero = new FloatValue(-0.0);
    FloatValue one = new FloatValue(1.0);
    FloatValue adjacent = new FloatValue(Math.nextUp(1.0));

    assertEquals(MooValue.Type.FLOAT, one.type());
    assertFalse(positiveZero.isTruthy());
    assertFalse(negativeZero.isTruthy());
    assertTrue(one.isTruthy());
    assertEquals(positiveZero, negativeZero);
    assertEquals(positiveZero.hashCode(), negativeZero.hashCode());
    assertNotEquals(one, adjacent);
    assertNotEquals(one.hashCode(), adjacent.hashCode());
    assertEquals("0.0", negativeZero.toLiteral());
    assertEquals("11.0", new FloatValue(11.0).toLiteral());
    assertEquals("1.4142135623731", new FloatValue(Math.sqrt(2.0)).toLiteral());
    assertEquals("0.333333333333333", new FloatValue(1.0 / 3.0).toLiteral());
    assertEquals("3.33333333333333", new FloatValue(10.0 / 3.0).toLiteral());
    assertEquals("0.3", new FloatValue(0.1 + 0.2).toLiteral());
    assertEquals("2.71828182845905", new FloatValue(Math.E).toLiteral());
    assertEquals("123456789012345.0", new FloatValue(123456789012345.0).toLiteral());
    assertEquals("1.23456789012346e+15", new FloatValue(1234567890123456.0).toLiteral());
    assertEquals("10000000000.0", new FloatValue(1.0e10).toLiteral());
    assertEquals("4.94065645841247e-324", new FloatValue(Double.MIN_VALUE).toLiteral());
  }

  @Test
  void integersPreserveValueTruthLiteralEqualityAndHashing() {
    IntegerValue zero = new IntegerValue(0);
    IntegerValue seventeen = new IntegerValue(17);

    assertEquals(MooValue.Type.INTEGER, seventeen.type());
    assertFalse(zero.isTruthy());
    assertTrue(new IntegerValue(-1).isTruthy());
    assertEquals("17", seventeen.toLiteral());
    assertEquals(seventeen, new IntegerValue(17));
    assertEquals(seventeen.hashCode(), new IntegerValue(17).hashCode());
    assertNotEquals(seventeen, new IntegerValue(18));
  }

  @Test
  void stringsOwnTheirLatinOneBytes() {
    byte[] source = {(byte) 0xE9, 'A'};
    StringValue value = new StringValue(source);
    source[0] = 0;

    assertArrayEquals(new byte[] {(byte) 0xE9, 'A'}, value.bytes());

    byte[] exposed = value.bytes();
    exposed[1] = 0;
    assertArrayEquals(new byte[] {(byte) 0xE9, 'A'}, value.bytes());
  }

  @Test
  void stringsFoldValidUtf8ButOnlyAsciiWithinInvalidByteArrays() {
    StringValue upper = new StringValue("Wizard".getBytes(StandardCharsets.ISO_8859_1));
    StringValue lower = new StringValue("wIZARD".getBytes(StandardCharsets.ISO_8859_1));
    StringValue upperUtf8 = new StringValue("À".getBytes(StandardCharsets.UTF_8));
    StringValue lowerUtf8 = new StringValue("à".getBytes(StandardCharsets.UTF_8));
    StringValue invalidUpper = new StringValue(new byte[] {(byte) 0xC0, 'A'});
    StringValue invalidLower = new StringValue(new byte[] {(byte) 0xC0, 'a'});

    assertEquals(upper, lower);
    assertEquals(upper.hashCode(), lower.hashCode());
    assertEquals(upperUtf8, lowerUtf8);
    assertEquals(upperUtf8.hashCode(), lowerUtf8.hashCode());
    assertEquals(invalidUpper, invalidLower);
    assertEquals(invalidUpper.hashCode(), invalidLower.hashCode());
    assertNotEquals(
        new StringValue(new byte[] {(byte) 0xC0}), new StringValue(new byte[] {(byte) 0xE0}));
  }

  @Test
  void stringsPreserveTruthAndMooLiteralForm() {
    assertEquals(MooValue.Type.STRING, new StringValue(new byte[0]).type());
    assertFalse(new StringValue(new byte[0]).isTruthy());
    assertTrue(new StringValue(new byte[] {'x'}).isTruthy());
    assertEquals(
        "\"hello\"", new StringValue("hello".getBytes(StandardCharsets.ISO_8859_1)).toLiteral());
    assertEquals("\"\\\"\\\\é\"", new StringValue(new byte[] {'"', '\\', (byte) 0xE9}).toLiteral());
  }

  @Test
  void objectReferencesPreserveIdentityTruthAndLiteralForm() {
    ObjectValue system = new ObjectValue(0);

    assertEquals(MooValue.Type.OBJECT, system.type());
    assertFalse(system.isTruthy());
    assertEquals("#0", system.toLiteral());
    assertEquals(system, new ObjectValue(0));
    assertEquals(system.hashCode(), new ObjectValue(0).hashCode());
    assertNotEquals(system, new ObjectValue(1));
  }

  @Test
  void errorsExposeTheSupportedToastCodesAndAreFalse() {
    assertEquals(
        List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18),
        List.of(ErrorValue.values()).stream().map(ErrorValue::code).toList());
    for (ErrorValue error : ErrorValue.values()) {
      assertEquals(error, ErrorValue.fromCode(error.code()).orElseThrow());
      assertEquals(error.name(), error.toLiteral());
      assertFalse(error.isTruthy());
      assertEquals(MooValue.Type.ERROR, error.type());
    }
    assertTrue(ErrorValue.fromCode(-1).isEmpty());
    assertTrue(ErrorValue.fromCode(19).isEmpty());
    assertEquals("E_TYPE", ErrorValue.E_TYPE.toLiteral());
  }

  @Test
  void listsOwnAnImmutableSnapshotAndUseOneBasedAccess() {
    List<MooValue> source = new ArrayList<>(List.of(new IntegerValue(1), new IntegerValue(2)));
    ListValue value = new ListValue(source);
    source.set(0, new IntegerValue(99));

    assertEquals(new IntegerValue(1), value.get(1).orElseThrow());
    assertEquals(new IntegerValue(2), value.get(2).orElseThrow());
    assertTrue(value.get(0).isEmpty());
    assertTrue(value.get(3).isEmpty());
    assertThrows(
        UnsupportedOperationException.class, () -> value.elements().add(new IntegerValue(3)));
  }

  @Test
  void listsUseStructuralEqualityTruthLiteralFormAndConsistentHashing() {
    ListValue empty = new ListValue(List.of());
    ListValue value = new ListValue(List.of(new IntegerValue(1), new IntegerValue(2)));
    ListValue equal = new ListValue(List.of(new IntegerValue(1), new IntegerValue(2)));

    assertEquals(MooValue.Type.LIST, value.type());
    assertFalse(empty.isTruthy());
    assertTrue(value.isTruthy());
    assertEquals("{}", empty.toLiteral());
    assertEquals("{1, 2}", value.toLiteral());
    assertEquals(value, equal);
    assertEquals(value.hashCode(), equal.hashCode());
    assertNotEquals(value, new ListValue(List.of(new IntegerValue(2), new IntegerValue(1))));
  }

  @Test
  void mapsOwnInsertionOrderButUseOrderIndependentRecursiveEqualityAndHashing() {
    LinkedHashMap<MooValue, MooValue> firstEntries = new LinkedHashMap<>();
    firstEntries.put(
        new StringValue("Key".getBytes(StandardCharsets.ISO_8859_1)),
        new ListValue(List.of(new IntegerValue(1))));
    firstEntries.put(new FloatValue(0.0), new IntegerValue(2));
    MapValue first = new MapValue(firstEntries);

    LinkedHashMap<MooValue, MooValue> reversedEntries = new LinkedHashMap<>();
    reversedEntries.put(new FloatValue(-0.0), new IntegerValue(2));
    reversedEntries.put(
        new StringValue("key".getBytes(StandardCharsets.ISO_8859_1)),
        new ListValue(List.of(new IntegerValue(1))));
    MapValue reversed = new MapValue(reversedEntries);

    assertEquals(MooValue.Type.MAP, first.type());
    assertTrue(first.isTruthy());
    assertFalse(new MapValue(Map.of()).isTruthy());
    assertEquals(first, reversed);
    assertEquals(first.hashCode(), reversed.hashCode());
    assertEquals(List.copyOf(firstEntries.keySet()), List.copyOf(first.entries().keySet()));
    assertThrows(
        UnsupportedOperationException.class,
        () -> first.entries().put(new IntegerValue(3), new IntegerValue(4)));
  }

  @Test
  void mapsReplaceEqualScalarKeyObjectsInPlaceAndRejectCollectionKeys() {
    StringValue original = new StringValue("Key".getBytes(StandardCharsets.ISO_8859_1));
    LinkedHashMap<MooValue, MooValue> initialEntries = new LinkedHashMap<>();
    initialEntries.put(original, new IntegerValue(1));
    initialEntries.put(new IntegerValue(9), new IntegerValue(9));
    MapValue initial = new MapValue(initialEntries);
    MapValue replaced =
        initial.with(
            new StringValue("kEY".getBytes(StandardCharsets.ISO_8859_1)), new IntegerValue(2));

    assertEquals(2, initial.size());
    assertEquals(2, replaced.size());
    assertEquals(new IntegerValue(1), initial.get(original).orElseThrow());
    assertEquals(new IntegerValue(2), replaced.get(original).orElseThrow());
    assertArrayEquals(
        "Key".getBytes(StandardCharsets.ISO_8859_1),
        ((StringValue) List.copyOf(initial.entries().keySet()).getFirst()).bytes());
    assertArrayEquals(
        "kEY".getBytes(StandardCharsets.ISO_8859_1),
        ((StringValue) List.copyOf(replaced.entries().keySet()).getFirst()).bytes());
    assertEquals(new IntegerValue(9), List.copyOf(replaced.entries().keySet()).get(1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new MapValue(Map.of(new ListValue(List.of()), new IntegerValue(1))));
    assertThrows(
        IllegalArgumentException.class,
        () -> initial.with(new MapValue(Map.of()), new IntegerValue(1)));
  }
}
