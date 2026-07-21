package moo.runtime;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import moo.builtin.BuiltinCatalog;
import moo.value.MooValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.world.WorldObject;
import moo.world.WorldTxn;

/** The scheduler-owned live registry used by task-management builtins. */
final class TaskRegistry {
  private static final long BACKGROUND_TICKS = 30_000;

  private final Map<Long, TaskInfo> tasks = new TreeMap<>();

  synchronized void registerFork(
      long taskId,
      long scheduledStart,
      long programmer,
      ObjectValue verbLocation,
      Map<String, MooValue> variables) {
    if (tasks.putIfAbsent(
            taskId,
            new TaskInfo(
                taskId,
                scheduledStart,
                programmer,
                verbLocation,
                stringVariable(variables, "verb"),
                0,
                variables.getOrDefault("this", verbLocation),
                0,
                variables))
        != null) {
      throw new IllegalStateException("duplicate live task " + taskId);
    }
  }

  synchronized void remove(long taskId) {
    tasks.remove(taskId);
  }

  synchronized int size() {
    return tasks.size();
  }

  BuiltinCatalog.Result queuedTasks(
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers) {
    boolean includeVariables =
        arguments.size() == 1 && ((IntegerValue) arguments.getFirst()).isTruthy();
    boolean returnCount =
        arguments.size() == 2 && ((IntegerValue) arguments.get(1)).isTruthy();
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && (actor.flags() & 4) != 0;
    List<TaskInfo> visible = visibleTo(programmer, wizard);
    if (returnCount) {
      return BuiltinCatalog.Result.value(new IntegerValue(visible.size()));
    }
    return BuiltinCatalog.Result.value(
        new ListValue(visible.stream().map(task -> task.row(includeVariables)).toList()));
  }

  private synchronized List<TaskInfo> visibleTo(long programmer, boolean wizard) {
    return tasks.values().stream()
        .filter(task -> wizard || task.programmer() == programmer)
        .toList();
  }

  private static StringValue stringVariable(Map<String, MooValue> variables, String name) {
    MooValue value = variables.get(name);
    return value instanceof StringValue string ? string : string("");
  }

  private static StringValue string(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }

  private record TaskInfo(
      long taskId,
      long scheduledStart,
      long programmer,
      ObjectValue verbLocation,
      StringValue verbName,
      long sourceLine,
      MooValue receiver,
      long bytes,
      Map<String, MooValue> variables) {
    TaskInfo {
      variables = Map.copyOf(variables);
    }

    ListValue row(boolean includeVariables) {
      List<MooValue> fields = new ArrayList<>(includeVariables ? 11 : 10);
      fields.add(new IntegerValue(taskId));
      fields.add(new IntegerValue(scheduledStart));
      fields.add(new IntegerValue(0));
      fields.add(new IntegerValue(BACKGROUND_TICKS));
      fields.add(new ObjectValue(programmer));
      fields.add(verbLocation);
      fields.add(verbName);
      fields.add(new IntegerValue(sourceLine));
      fields.add(receiver);
      fields.add(new IntegerValue(bytes));
      if (includeVariables) {
        Map<MooValue, MooValue> runtimeVariables = new LinkedHashMap<>();
        variables.forEach((name, value) -> runtimeVariables.put(string(name), value));
        fields.add(new MapValue(runtimeVariables));
      }
      return new ListValue(fields);
    }
  }
}
