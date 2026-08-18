package moo.persistence;

import static moo.persistence.DbScanner.malformed;
import static moo.persistence.DbScanner.readCount;
import static moo.persistence.DbScanner.readDouble;
import static moo.persistence.DbScanner.readInt;
import static moo.persistence.DbScanner.readLong;
import static moo.persistence.DbScanner.requiredLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;

/** Shared decoder for value tags common to v5 and v17 databases. */
final class ValueTagDecoder {
  @FunctionalInterface
  interface NestedValueDecoder {
    MooValue read(BufferedReader input) throws IOException;
  }

  @FunctionalInterface
  interface IndexedNestedValueDecoder {
    MooValue read(BufferedReader input, int index) throws IOException;
  }

  private ValueTagDecoder() {}

  static MooValue readV5(BufferedReader input) throws IOException {
    return readV5(input, readInt(input, "value tag"));
  }

  static MooValue readV5(BufferedReader input, int tag) throws IOException {
    return readCommon(input, tag, (NestedValueDecoder) ValueTagDecoder::readV5)
        .orElseThrow(() -> malformed("unsupported v5 value tag " + tag));
  }

  static Optional<MooValue> readCommon(
      BufferedReader input, int tag, NestedValueDecoder nestedValueDecoder) throws IOException {
    return readCommon(input, tag, (nestedInput, index) -> nestedValueDecoder.read(nestedInput));
  }

  static Optional<MooValue> readCommon(
      BufferedReader input, int tag, IndexedNestedValueDecoder nestedValueDecoder)
      throws IOException {
    return switch (tag) {
      case 0 -> Optional.of(new IntegerValue(readLong(input, "integer value")));
      case 1 -> Optional.of(new ObjectValue(readLong(input, "object value")));
      case 2 -> Optional.of(StringValue.of(requiredLine(input, "string value")));
      case 3 -> {
        long code = readLong(input, "error value");
        yield Optional.of(
            ErrorValue.fromCode(code & 0xffff_ffffL)
                .orElseThrow(() -> malformed("unsupported error value " + code)));
      }
      case 4 -> {
        int count = readCount(input, "list count");
        List<MooValue> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
          values.add(nestedValueDecoder.read(input, index));
        }
        yield Optional.of(new ListValue(values));
      }
      case 9 -> Optional.of(new FloatValue(readDouble(input, "float value")));
      default -> Optional.empty();
    };
  }
}
