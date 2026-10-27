package world.mongoose.banteng.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import world.mongoose.banteng.server.ConnectionRegistry;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.ArgSpec;
import world.mongoose.banteng.world.PropertyPerms;
import world.mongoose.banteng.world.VerbPerms;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;

final class StartupScriptCallbacksTest {
  private static final String OBSERVE =
      "#0.events = {@#0.events, {verb, args, argstr, dobj, dobjstr, prepstr, iobj, iobjstr, caller_perms()}};";

  @Test
  void rawScriptsPrecedeServerStartedAfterReturnErrorOrActualSuspension() {
    for (String tail : List.of("return 93;", "raise(E_INVARG);", "suspend(60);")) {
      WorldTxn world = world(List.of(verb("do_start_script", 12, "random_bytes(1); " + OBSERVE + tail),
          verb("server_started", 12, OBSERVE)));
      try (MooRuntime runtime = runtime(world)) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
          runtime.startScript(new ListValue(List.of(StringValue.of("#0.executed = 1;"))));
          runtime.startServer();
        });
        try (WorldTxn read = world.begin()) {
          assertEquals("{{\"do_start_script\", {\"#0.executed = 1;\"}, \"\", #-1, \"\", \"\", #-1, \"\", #-1}, "
            + "{\"server_started\", {}, \"\", #-1, \"\", \"\", #-1, \"\", #-1}}",
              read.readObjectProperty(0, "events").orElseThrow().toLiteral(), tail);
          assertEquals(new IntegerValue(0), read.readObjectProperty(0, "executed").orElseThrow());
        }
      }
    }
  }

  @Test
  void absentAndNonexecutableHooksDoNotEvaluateScriptArguments() {
    for (List<WorldVerb> hooks : List.of(List.<WorldVerb>of(),
        List.of(verb("do_start_script", 8, "#0.executed = 2;")))) {
      WorldTxn world = world(hooks);
      try (MooRuntime runtime = runtime(world)) {
        runtime.startScript(new ListValue(List.of(StringValue.of("#0.executed = 1;"))));
        try (WorldTxn read = world.begin()) {
          assertEquals(new IntegerValue(0), read.readObjectProperty(0, "executed").orElseThrow());
        }
      }
    }
  }

  private static WorldVerb verb(String name, int permissions, String source) {
    return new WorldVerb(name, 0, new VerbPerms(permissions), ArgSpec.NONE, -1, ArgSpec.NONE, source);
  }

  private static WorldTxn world(List<WorldVerb> verbs) {
    return new WorldTxn(List.of(), List.of(new WorldObject(0, "system", 4, 0, -1, List.of(), List.of(), List.of(), verbs,
        List.of(new WorldProperty("events", new ListValue(List.of()), 0, new PropertyPerms(3), false, true),
            new WorldProperty("executed", new IntegerValue(0), 0, new PropertyPerms(3), false, true)))));
  }

  private static MooRuntime runtime(WorldTxn world) {
    return SchedulerTestHarness.runtime(world, new ConnectionRegistry(), 2, System::nanoTime,
        MooRuntime::resolveCanonicalConnectionName, Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of());
  }
}
