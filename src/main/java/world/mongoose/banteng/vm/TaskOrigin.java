package world.mongoose.banteng.vm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;

/** Immutable initial task environment and dispatch metadata. */
public record TaskOrigin(Map<String, MooValue> locals, long programmer, MooValue verbLocation,
    String calledVerb, String fullVerbName, boolean debug) {
  public TaskOrigin {
    Map<String, MooValue> normalized = new LinkedHashMap<>();
    locals.forEach((name, value) -> normalized.put(name.toLowerCase(Locale.ROOT), value));
    locals = Collections.unmodifiableMap(normalized);
    Objects.requireNonNull(calledVerb, "calledVerb");
    Objects.requireNonNull(fullVerbName, "fullVerbName");
  }

  public static MooValue inferVerbLocation(Map<String, MooValue> locals) {
    Map<String, MooValue> normalized = new LinkedHashMap<>();
    locals.forEach((name, value) -> normalized.put(name.toLowerCase(Locale.ROOT), value));
    return normalized.get("this") instanceof ObjectValue object ? object : new ObjectValue(-1);
  }

  public static String calledVerb(Map<String, MooValue> locals) {
    return locals.get("verb") instanceof StringValue verb ? verb.text() : "";
  }
}
