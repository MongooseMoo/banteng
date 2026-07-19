package moo.builtin;

import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.value.MooValue.WaifValue;

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
