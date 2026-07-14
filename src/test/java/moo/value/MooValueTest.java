package moo.value;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import org.junit.jupiter.api.Test;

final class MooValueTest {
  @Test
  void familyIsClosedOverExactlyTheFiveAuthorizedValues() {
    assertTrue(MooValue.class.isSealed());
    assertEquals(
        Set.of(
            IntegerValue.class,
            StringValue.class,
            ObjectValue.class,
            ErrorValue.class,
            ListValue.class),
        Set.of(MooValue.class.getPermittedSubclasses()));
    assertEquals(
        List.of(0, 1, 2, 3, 4),
        List.of(MooValue.Type.values()).stream().map(MooValue.Type::code).toList());
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
  void stringsUseAsciiCaseInsensitiveEqualityAndConsistentHashing() {
    StringValue upper = new StringValue("Wizard".getBytes(StandardCharsets.ISO_8859_1));
    StringValue lower = new StringValue("wIZARD".getBytes(StandardCharsets.ISO_8859_1));

    assertEquals(upper, lower);
    assertEquals(upper.hashCode(), lower.hashCode());
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
  void errorsExposeTheCanonicalV4CodesAndAreFalse() {
    assertEquals(
        List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17),
        List.of(ErrorValue.values()).stream().map(ErrorValue::code).toList());
    for (ErrorValue error : ErrorValue.values()) {
      assertEquals(error, ErrorValue.fromCode(error.code()).orElseThrow());
      assertEquals(error.name(), error.toLiteral());
      assertFalse(error.isTruthy());
      assertEquals(MooValue.Type.ERROR, error.type());
    }
    assertTrue(ErrorValue.fromCode(-1).isEmpty());
    assertTrue(ErrorValue.fromCode(18).isEmpty());
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
}
