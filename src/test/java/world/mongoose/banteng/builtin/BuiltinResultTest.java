package world.mongoose.banteng.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import org.junit.jupiter.api.Test;

final class BuiltinResultTest {
  @Test
  void resultHierarchyIsClosedAndEachEffectCarriesOnlyItsOwnState() {
    assertTrue(BuiltinResult.class.isSealed());
    assertEquals(
        Set.of(
            "BootPlayer",
            "Checkpoint",
            "DynamicEval",
            "ErrorResult",
            "ForceInput",
            "HostWork",
            "Initialize",
            "Move",
            "Notify",
            "Output",
            "Panic",
            "Programmer",
            "RaisedError",
            "Recycle",
            "RecycleAnonymous",
            "SecondsAbort",
            "SetConnectionOption",
            "Shutdown",
            "Suspend",
            "SwitchPlayer",
            "ThreadMode",
            "Value"),
        Arrays.stream(BuiltinResult.class.getPermittedSubclasses())
            .map(Class::getSimpleName)
            .collect(Collectors.toUnmodifiableSet()));

    assertEquals(
        Map.ofEntries(
            Map.entry("BootPlayer", List.of("target")),
            Map.entry("Checkpoint", List.of()),
            Map.entry("DynamicEval", List.of("source")),
            Map.entry("ErrorResult", List.of("error")),
            Map.entry("ForceInput", List.of("target", "input")),
            Map.entry("HostWork", List.of("work")),
            Map.entry("Initialize", List.of("created", "arguments")),
            Map.entry("Move", List.of("object", "destination", "position")),
            Map.entry(
                "Notify", List.of("connectionId", "line", "noFlush", "noNewline")),
            Map.entry("Output", List.of("line")),
            Map.entry("Panic", List.of("message")),
            Map.entry("Programmer", List.of("programmer")),
            Map.entry("RaisedError", List.of("error", "details")),
            Map.entry("Recycle", List.of("object")),
            Map.entry("RecycleAnonymous", List.of("object")),
            Map.entry("SecondsAbort", List.of()),
            Map.entry("SetConnectionOption", List.of("target", "option", "value")),
            Map.entry("Shutdown", List.of()),
            Map.entry("Suspend", List.of("seconds")),
            Map.entry("SwitchPlayer", List.of("player")),
            Map.entry("ThreadMode", List.of("enabled")),
            Map.entry("Value", List.of("value"))),
        Arrays.stream(BuiltinResult.class.getPermittedSubclasses())
            .collect(
                Collectors.toUnmodifiableMap(
                    Class::getSimpleName,
                    type ->
                        Arrays.stream(type.getRecordComponents())
                            .map(RecordComponent::getName)
                            .toList())));
  }

  @Test
  void valueFactoryAndMoveRecordReturnTheirConcreteCases() {
    MooValue value = new IntegerValue(42);

    BuiltinResult.Value returned = (BuiltinResult.Value) BuiltinResult.value(value);
    BuiltinResult.Move move = new BuiltinResult.Move(3, 7, 2);

    assertEquals(value, returned.value());
    assertEquals(3, move.object());
    assertEquals(7, move.destination());
    assertEquals(2, move.position());
  }
}
