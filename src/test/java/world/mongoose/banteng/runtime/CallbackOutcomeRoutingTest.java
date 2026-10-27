package world.mongoose.banteng.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import world.mongoose.banteng.server.ConnectionRegistry;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.world.ArgSpec;
import world.mongoose.banteng.world.PropertyPerms;
import world.mongoose.banteng.world.VerbPerms;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;

final class CallbackOutcomeRoutingTest {
  @Test
  void foregroundHandlerOutcomesPreserveOutputEnvelopeAndOriginalFallback() throws Exception {
    for (String tail : List.of("absent", "nonexecutable", "return 0;", "return 1;",
        "raise(E_PERM, \"handler-error\");", "suspend(60); return 0;",
        "suspend(0); raise(E_PERM, \"late-handler-error\");")) {
      try (SchedulerTestHarness harness = SchedulerTestHarness.open(2, -47)) {
        String setup = "try delete_verb(#0, \"handle_uncaught_error\"); except (E_VERBNF) endtry ";
        if (!tail.equals("absent")) {
          String permissions = tail.equals("nonexecutable") ? "rd" : "rxd";
          String body = tail.equals("nonexecutable") ? "return 1;" : tail;
          setup += "add_verb(#0, {player, \"" + permissions + "\", \"handle_uncaught_error\"}, {\"this\", \"none\", \"this\"}); "
              + "set_verb_code(#0, \"handle_uncaught_error\", {\"#0.scheduler_counter = #0.scheduler_counter + 1;\", "
              + quote(body) + "}); ";
        }
        setup += "add_verb(player, {player, \"rxd\", \"cb_fail\"}, {\"none\", \"none\", \"none\"}); "
            + "set_verb_code(player, \"cb_fail\", {\"raise(E_INVARG, \\\"original-error\\\", 37);\"}); return 1;";
        assertTrue(harness.runtime.executeLine(-47, "; " + setup).contains("{1, 1}"));
        harness.runtime.executeLine(-47, "OUTPUTPREFIX PRE");
        harness.runtime.executeLine(-47, "OUTPUTSUFFIX POST");
        boolean blocked = tail.startsWith("suspend");
        List<String> output = assertTimeoutPreemptively(Duration.ofSeconds(5),
            () -> blocked ? harness.runtime.receiveLine(-47, "cb_fail") : harness.runtime.executeLine(-47, "cb_fail"));
        if (blocked) {
          assertEquals(List.of(), output);
          assertTrue(harness.runtime.executeLine(-47, "; return 7;").contains("{1, 7}"));
          assertEquals(1, harness.counter());
          continue;
        }
        assertEquals("PRE", output.getFirst(), tail);
        assertEquals("POST", output.getLast(), tail);
        boolean fallback = tail.equals("absent") || tail.equals("nonexecutable")
            || tail.equals("return 0;") || tail.startsWith("raise");
        assertEquals(fallback, output.toString().contains("original-error"), tail);
        assertEquals(tail.startsWith("raise"), output.toString().contains("handler-error"), tail);
        if (tail.startsWith("raise")) {
          assertTrue(output.toString().indexOf("handler-error") < output.toString().indexOf("original-error"));
        }
        assertEquals(tail.equals("absent") || tail.equals("nonexecutable") ? 0 : 1, harness.counter(), tail);
      }
    }
  }

  @Test
  void checkpointStartedErrorOrBlockStillCompletesOneCheckpoint(@TempDir Path directory) {
    for (String tail : List.of("return 0;", "raise(E_INVARG);", "suspend(60);", "suspend(0);")) {
      Path checkpoint = directory.resolve("checkpoint-" + tail.hashCode() + ".db");
      WorldTxn world = new WorldTxn(List.of(), List.of(new WorldObject(0, "system", 4, 0, -1,
          List.of(), List.of(), List.of(), List.of(
              verb("do_start_script", "dump_database();"),
              verb("checkpoint_started", tail),
              verb("checkpoint_finished", "#0.finished = #0.finished + 1;")),
          List.of(new WorldProperty("finished", new IntegerValue(0), 0, new PropertyPerms(3), false, true)))));
      try (MooRuntime runtime = SchedulerTestHarness.runtime(world, new ConnectionRegistry(), 2, System::nanoTime,
          MooRuntime::resolveCanonicalConnectionName, Optional.empty(), Optional.empty(), Optional.of(checkpoint), List.of(), List.of())) {
        runtime.startScript(new ListValue(List.of()));
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
          while (true) {
            try (WorldTxn read = world.begin()) {
              if (read.readObjectProperty(0, "finished").orElseThrow().equals(new IntegerValue(1))) break;
            }
            Thread.sleep(10);
          }
        });
        assertTrue(Files.exists(checkpoint), tail);
        runtime.startServer();
        try (WorldTxn read = world.begin()) {
          assertEquals(new IntegerValue(1), read.readObjectProperty(0, "finished").orElseThrow());
        }
      }
    }
  }

  private static WorldVerb verb(String name, String source) {
    return new WorldVerb(name, 0, new VerbPerms(12), ArgSpec.NONE, -1, ArgSpec.NONE, source);
  }

  private static String quote(String text) {
    return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
