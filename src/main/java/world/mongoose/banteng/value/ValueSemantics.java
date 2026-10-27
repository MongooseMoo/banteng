package world.mongoose.banteng.value;

import world.mongoose.banteng.value.MooValue.BooleanValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;

/** Immutable runtime configuration for value operations. */
public record ValueSemantics(boolean promoteNumbers) {
  /** Stock Toast-compatible value behavior. */
  public static final ValueSemantics STANDARD = new ValueSemantics(false);

  /** Toast equality(), including recursive collections and the configured numeric extension. */
  // Toast listequal (list.cc:359) and mapequal (map.cc:766) test collection identity first.
  @SuppressWarnings("ReferenceEquality")
  public boolean equal(MooValue left, MooValue right, boolean caseMatters) {
    if (left.type() != right.type()) {
      if (left instanceof BooleanValue bool && right instanceof IntegerValue integer) {
        return integer.value() == (bool.value() ? 1 : 0);
      }
      if (right instanceof BooleanValue bool && left instanceof IntegerValue integer) {
        return integer.value() == (bool.value() ? 1 : 0);
      }
      return promoteNumbers && mixedNumbers(left, right) && number(left) == number(right);
    }
    return switch (left) {
      case IntegerValue integer -> integer.value() == ((IntegerValue) right).value();
      case ObjectValue object -> object.value() == ((ObjectValue) right).value();
      case ErrorValue error -> error == right;
      case BooleanValue bool -> bool == right;
      case FloatValue floating -> floating.value() == ((FloatValue) right).value();
      case StringValue string -> string.length() == ((StringValue) right).length()
          && compareStrings(string, (StringValue) right, caseMatters) == 0;
      case ListValue list -> {
        if (list == right) yield true;
        ListValue other = (ListValue) right;
        boolean same = list.size() == other.size();
        for (int index = 0; same && index < list.size(); index++) {
          same = equal(list.elements().get(index), other.elements().get(index), caseMatters);
        }
        yield same;
      }
      case MapValue map -> {
        if (map == right) yield true;
        MapValue other = (MapValue) right;
        var leftEntries = map.orderedEntries().iterator();
        var rightEntries = other.orderedEntries().iterator();
        boolean same = true;
        while (same && leftEntries.hasNext() && rightEntries.hasNext()) {
          var leftEntry = leftEntries.next();
          var rightEntry = rightEntries.next();
          same = equal(leftEntry.getKey(), rightEntry.getKey(), caseMatters)
              && equal(leftEntry.getValue(), rightEntry.getValue(), caseMatters);
        }
        yield same && !leftEntries.hasNext() && !rightEntries.hasNext();
      }
      case MooValue.AnonymousObjectValue anonymous -> anonymous == right;
      case MooValue.WaifValue waif -> waif == right;
    };
  }

  /** Toast compare(); identity types deliberately do not form a Java Comparator order. */
  public int compare(MooValue left, MooValue right, boolean caseMatters) {
    if (promoteNumbers && mixedNumbers(left, right)) {
      return compareNumbers(number(left), number(right));
    }
    if (left.type() != right.type()) {
      return runtimeType(left.type()) - runtimeType(right.type());
    }
    return switch (left) {
      case IntegerValue integer -> Long.compare(integer.value(), ((IntegerValue) right).value());
      case ObjectValue object -> Long.compare(object.value(), ((ObjectValue) right).value());
      case ErrorValue error -> error.code() - ((ErrorValue) right).code();
      case StringValue string -> compareStrings(string, (StringValue) right, caseMatters);
      case FloatValue floating -> compareNumbers(floating.value(), ((FloatValue) right).value());
      case BooleanValue bool -> bool == right ? 0 : 1;
      case MooValue.AnonymousObjectValue anonymous -> anonymous == right ? 0 : 1;
      case MooValue.WaifValue waif -> waif == right ? 0 : 1;
      case ListValue ignored -> throw new IllegalArgumentException("list comparison");
      case MapValue ignored -> throw new IllegalArgumentException("map comparison");
    };
  }

  private static int compareNumbers(double left, double right) {
    return left == right ? 0 : (left - right < 0.0 ? -1 : 1);
  }

  private static boolean mixedNumbers(MooValue left, MooValue right) {
    return (left instanceof IntegerValue && right instanceof FloatValue)
        || (left instanceof FloatValue && right instanceof IntegerValue);
  }

  private static double number(MooValue value) {
    return value instanceof IntegerValue integer ? integer.value() : ((FloatValue) value).value();
  }

  private static int runtimeType(MooValue.Type type) {
    return switch (type) {
      case STRING, LIST, MAP, ANONYMOUS, WAIF -> type.code() | 0x80;
      default -> type.code();
    };
  }

  private static int compareStrings(StringValue left, StringValue right, boolean caseMatters) {
    for (int index = 0; ; index++) {
      int lhs = index < left.length() ? left.unsignedByteAt(index) : 0;
      int rhs = index < right.length() ? right.unsignedByteAt(index) : 0;
      if (!caseMatters) {
        if (lhs >= 'A' && lhs <= 'Z') lhs += 'a' - 'A';
        if (rhs >= 'A' && rhs <= 'Z') rhs += 'a' - 'A';
      }
      if (lhs != rhs || lhs == 0) return lhs - rhs;
    }
  }
}
