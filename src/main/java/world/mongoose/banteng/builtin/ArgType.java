package world.mongoose.banteng.builtin;

import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue.WaifValue;

/** Closed argument kinds used by builtin call-shape validation. */
public enum ArgType {
  ANY,
  INTEGER,
  FLOAT,
  NUMBER,
  STRING,
  LIST,
  MAP,
  OBJECT,
  WAIF,
  ERROR;

  /** Returns whether this contract kind accepts one concrete MOO value. */
  public boolean accepts(MooValue value) {
    return switch (this) {
      case ANY -> true;
      case INTEGER -> value instanceof IntegerValue;
      case FLOAT -> value instanceof FloatValue;
      case NUMBER -> value instanceof IntegerValue || value instanceof FloatValue;
      case STRING -> value instanceof StringValue;
      case LIST -> value instanceof ListValue;
      case MAP -> value instanceof MapValue;
      case OBJECT -> value instanceof ObjectValue;
      case WAIF -> value instanceof WaifValue;
      case ERROR -> value instanceof ErrorValue;
    };
  }
}
