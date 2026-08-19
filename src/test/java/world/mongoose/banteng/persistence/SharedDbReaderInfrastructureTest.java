package world.mongoose.banteng.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.BooleanValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import org.junit.jupiter.api.Test;

final class SharedDbReaderInfrastructureTest {
  private static final List<Class<?>> READERS =
      List.of(LambdaMooV4Reader.class, LambdaMooV5Reader.class, LambdaMooV17Codec.class);

  @Test
  void scannerOwnsTheSharedLineAndNumberContract() throws IOException {
    BufferedReader input = input("2\n-3\n4.5\nexpected\n");

    assertEquals(2, DbScanner.readCount(input, "count"));
    assertEquals(-3, DbScanner.readLong(input, "long"));
    assertEquals(4.5, DbScanner.readDouble(input, "double"));
    DbScanner.requireExact(input, "expected", "marker");
    assertEquals(7, DbScanner.parseCount("7", "parsed count"));
    assertEquals(-8, DbScanner.parseInt("-8", "parsed int"));
    assertEquals(-9, DbScanner.parseLong("-9", "parsed long"));
  }

  @Test
  void scannerPreservesMalformedDiagnostics() {
    IOException negative =
        assertThrows(IOException.class, () -> DbScanner.parseCount("-1", "object count"));
    assertEquals("object count must not be negative", negative.getMessage());

    IOException invalid =
        assertThrows(IOException.class, () -> DbScanner.parseInt("no", "object count"));
    assertEquals("invalid object count: no", invalid.getMessage());
    assertTrue(invalid.getCause() instanceof NumberFormatException);

    IOException eof =
        assertThrows(
            IOException.class, () -> DbScanner.requiredLine(input(""), "program header"));
    assertEquals("unexpected end of file while reading program header", eof.getMessage());
  }

  @Test
  void v5ValueModeOwnsTheSharedSubsetAndDiagnostics() throws IOException {
    MooValue decoded = ValueTagDecoder.readV5(input("4\n2\n0\n7\n1\n8\n"));

    assertEquals(new ListValue(List.of(new IntegerValue(7), new ObjectValue(8))), decoded);
    IOException unsupported =
        assertThrows(IOException.class, () -> ValueTagDecoder.readV5(input("10\n0\n")));
    assertEquals("unsupported v5 value tag 10", unsupported.getMessage());
  }

  @Test
  void sharedContainerRecursionReentersTheV17ExtensionDecoder() throws IOException {
    AnonymousObjectValue anonymous = new AnonymousObjectValue();
    AtomicLong decodedObjectId = new AtomicLong(-1);
    ValueTagDecoder.NestedValueDecoder nested =
        input -> readTestV17Value(input, anonymous, decodedObjectId);

    MooValue decoded =
        ValueTagDecoder.readCommon(input("1\n10\n1\n2\nanon\n12\n17\n"), 4, nested)
            .orElseThrow();

    MapValue map = new MapValue(Map.of(string("anon"), anonymous));
    assertEquals(new ListValue(List.of(map)), decoded);
    assertEquals(17, decodedObjectId.get());
  }

  @Test
  void programAndVerbRecordsAreSingleTopLevelTypes() {
    ProgramSlot slot = new ProgramSlot(12, 3);
    RawVerb verb = new RawVerb("look examine", 4, 5, 6);

    assertNull(ProgramSlot.class.getEnclosingClass());
    assertNull(RawVerb.class.getEnclosingClass());
    assertEquals(12, slot.objectId());
    assertEquals(3, slot.verbIndex());
    assertEquals("look examine", verb.names());
    assertEquals(4, verb.owner());
    assertEquals(5, verb.permissions());
    assertEquals(6, verb.preposition());
    for (Class<?> reader : READERS) {
      Set<String> nestedNames =
          Arrays.stream(reader.getDeclaredClasses())
              .map(Class::getSimpleName)
              .collect(java.util.stream.Collectors.toSet());
      assertFalse(nestedNames.contains("ProgramSlot"), reader.getName());
      assertFalse(nestedNames.contains("RawVerb"), reader.getName());
    }
  }

  @Test
  void readersDoNotRetainScannerPrimitives() {
    Set<String> scannerMethods =
        Set.of(
            "readCount",
            "parseCount",
            "readInt",
            "parseInt",
            "readParsedInt",
            "readLong",
            "parseLong",
            "readDouble",
            "requireExact",
            "requiredLine",
            "malformed");

    for (Class<?> reader : READERS) {
      Set<String> declared =
          Arrays.stream(reader.getDeclaredMethods())
              .map(java.lang.reflect.Method::getName)
              .collect(java.util.stream.Collectors.toSet());
      assertTrue(java.util.Collections.disjoint(scannerMethods, declared), reader.getName());
    }
  }

  @Test
  void readersConsumeOnlyTheirAuthorizedSharedValueSurface() throws IOException {
    String v4 = source("LambdaMooV4Reader.java");
    String v5 = source("LambdaMooV5Reader.java");
    String v17 = source("LambdaMooV17Codec.java");

    assertTrue(v4.contains("DbScanner"));
    assertFalse(v4.contains("ValueTagDecoder"));
    assertTrue(v5.contains("DbScanner"));
    assertTrue(v5.contains("ValueTagDecoder"));
    assertTrue(v17.contains("DbScanner"));
    assertTrue(v17.contains("ValueTagDecoder"));
  }

  private static MooValue readTestV17Value(
      BufferedReader input, AnonymousObjectValue anonymous, AtomicLong decodedObjectId)
      throws IOException {
    int tag = DbScanner.readInt(input, "value tag");
    ValueTagDecoder.NestedValueDecoder nested =
        nestedInput -> readTestV17Value(nestedInput, anonymous, decodedObjectId);
    Optional<MooValue> common = ValueTagDecoder.readCommon(input, tag, nested);
    if (common.isPresent()) {
      return common.orElseThrow();
    }
    return switch (tag) {
      case 10 -> {
        int count = DbScanner.readCount(input, "map count");
        MapValue map = new MapValue(Map.of());
        for (int index = 0; index < count; index++) {
          map = map.with(nested.read(input), nested.read(input));
        }
        yield map;
      }
      case 12 -> {
        decodedObjectId.set(DbScanner.readLong(input, "anonymous object reference"));
        yield anonymous;
      }
      case 14 -> BooleanValue.of(DbScanner.readLong(input, "boolean value") != 0);
      default -> throw DbScanner.malformed("unsupported test v17 value tag " + tag);
    };
  }

  private static String source(String fileName) throws IOException {
    return Files.readString(
        Path.of("src", "main", "java", "moo", "persistence", fileName),
        StandardCharsets.UTF_8);
  }

  private static BufferedReader input(String contents) {
    return new BufferedReader(new StringReader(contents));
  }

  private static StringValue string(String value) {
    return StringValue.of(value);
  }
}
