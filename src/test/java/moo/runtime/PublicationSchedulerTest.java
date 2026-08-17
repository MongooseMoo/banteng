package moo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import moo.builtin.BuiltinCatalog.ListenerControl;
import moo.builtin.BuiltinCatalog.ListenerDescription;
import moo.builtin.BuiltinResult;
import moo.bytecode.MooCompiler;
import moo.persistence.LambdaMooV17Codec;
import moo.persistence.LambdaMooV17Codec.ActiveConnection;
import moo.persistence.LambdaMooV17Codec.SuspendedActivation;
import moo.persistence.LambdaMooV17Codec.SuspendedStackSlot;
import moo.persistence.LambdaMooV17Codec.SuspendedTask;
import moo.persistence.LambdaMooV4Reader;
import moo.persistence.ToastV17ProgramLayout;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.world.WorldObject;
import moo.world.WorldProperty;
import moo.world.WorldTxn;
import moo.vm.VmSnapshot;
import moo.world.WorldVerb;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PublicationSchedulerTest {
  private static final Path FIXTURE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");
  private static final long CONNECTION_ID = -47;
  private static final long SECOND_CONNECTION_ID = -48;
  private static final String CONNECTION_PREFIX = "-=!-^-!=-";
  private static final String CONNECTION_SUFFIX = "-=!-v-!=-";

  @Test
  void activatesRestoredValueWakeExactlyOnceWithoutReplayingTheSavedCall() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      harness.resetCounter();
      TaskRegistry registry = field(harness.scheduler, "taskRegistry", TaskRegistry.class);
      String source = "value = suspend(); #0.scheduler_counter = value; return 0;\n";
      SuspendedTask task = suspendedTask(900_000_001L, source, new IntegerValue(73), List.of());

      harness.scheduler.restoreTasks(List.of(task));
      Thread.sleep(100);
      assertEquals(0, harness.counter());
      assertEquals(1, registry.size());
      assertEquals(List.of(task), harness.scheduler.durableTasks());

      harness.scheduler.activateRestoredTasks();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while ((harness.counter() != 73 || registry.size() != 0)
          && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      assertEquals(73, harness.counter());
      assertEquals(0, registry.size());
      assertEquals(List.of(), harness.scheduler.durableTasks());
      assertThrows(IllegalStateException.class, harness.scheduler::activateRestoredTasks);
    }
  }

  @Test
  void wakesInterruptedRestoredTaskWithEintrptThroughItsExactHandlers() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      harness.resetCounter();
      TaskRegistry registry = field(harness.scheduler, "taskRegistry", TaskRegistry.class);
      String source =
          """
          try
            suspend();
          except (E_INTRPT)
            #0.scheduler_counter = 88;
          endtry
          return 0;
          """;
      List<SuspendedStackSlot> catchStack =
          structuralStack(source, 0, Map.of());
      SuspendedTask task =
          suspendedTask(900_000_002L, source, new IntegerValue(0), catchStack);
      task =
          new SuspendedTask(
              task.taskId(),
              task.scheduledEpochSecond(),
              task.resumeValue(),
              task.taskLocal(),
              task.rootActivationVector(),
              task.functionId(),
              task.maxStackDepth(),
              Optional.of("interrupted reading task"),
              task.activations());

      harness.scheduler.restoreTasks(List.of(task));
      harness.scheduler.activateRestoredTasks();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while ((harness.counter() != 88 || registry.size() != 0)
          && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      assertEquals(88, harness.counter());
      assertEquals(0, registry.size());
      assertEquals(List.of(), harness.scheduler.durableTasks());
    }
  }

  @Test
  void startsRestoredTimersOnlyAfterDisconnectCallbacksAndServerStarted() {
    WorldObject system =
        new WorldObject(
            0,
            "System",
            4,
            0,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(
                new WorldVerb(
                    "user_disconnected",
                    0,
                    4,
                    -1,
                    "this.order = {@this.order, args[1]}; return 0;"),
                new WorldVerb(
                    "server_started",
                    0,
                    4,
                    -1,
                    "this.order = {@this.order, 999}; return 0;")),
            List.of(
                new WorldProperty(
                    "order", new ListValue(List.of()), 0, 3, false, true)));
    WorldTxn world = new WorldTxn(List.of(), List.of(system));
    String taskSource = "suspend(); #0.order = {@#0.order, 777}; return 0;\n";
    SuspendedTask task =
        suspendedTask(900_000_003L, taskSource, new IntegerValue(0), List.of());
    MooRuntime runtime =
        new MooRuntime(
            world,
            new RecordingListener(),
            Path.of("unused-startup-order.db"),
            List.of(task),
            List.of(new ActiveConnection(41, 0), new ActiveConnection(42, 0)));
    try {
      runtime.startServer();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      ListValue order = readOrder(world);
      while (order.size() != 4 && System.nanoTime() < deadline) {
        Thread.onSpinWait();
        order = readOrder(world);
      }
      assertEquals(
          List.of(
              new ObjectValue(41),
              new ObjectValue(42),
              new IntegerValue(999),
              new IntegerValue(777)),
          order.elements());
    } finally {
      scheduler(runtime).close();
    }
  }

  @Test
  void checkpointsAndRestartsFutureNativeSuspensionExactlyOnce() throws Exception {
    Harness harness = Harness.open(1, new RecordingListener());
    try {
      harness.resetCounter();
      harness.line(
          "; fork (0) value = suspend(2); "
              + "#0.scheduler_counter = #0.scheduler_counter + 1; endfork return 1;");
      SuspendedTask checkpointed = null;
      long captureDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while (checkpointed == null && System.nanoTime() < captureDeadline) {
        checkpointed =
            harness.scheduler.durableTasks().stream()
                .filter(SuspendedTask.class::isInstance)
                .map(SuspendedTask.class::cast)
                .findFirst()
                .orElse(null);
        Thread.onSpinWait();
      }
      SuspendedTask durable = Objects.requireNonNull(checkpointed);
      assertEquals(new IntegerValue(0), durable.resumeValue());
      assertEquals(1, durable.activations().size());
      assertEquals(0, harness.counter());
      harness.close();

      MooRuntime restarted =
          new MooRuntime(
              harness.root,
              new RecordingListener(),
              Path.of("unused-native-restart.db"),
              List.of(durable),
              List.of());
      try {
        restarted.startServer();
        long completionDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (harness.counter() != 1 && System.nanoTime() < completionDeadline) {
          Thread.onSpinWait();
        }
        assertEquals(1, harness.counter());
        Thread.sleep(250);
        assertEquals(1, harness.counter());
        assertEquals(0, field(scheduler(restarted), "taskRegistry", TaskRegistry.class).size());
        assertEquals(List.of(), scheduler(restarted).durableTasks());
      } finally {
        scheduler(restarted).close();
      }
    } finally {
      harness.close();
    }
  }

  @Test
  void resumeWakesOneIndefiniteSuspensionWithTheSuppliedValue() throws Exception {
    try (Harness harness = Harness.open(2, new RecordingListener())) {
      harness.resetCounter();
      TaskRegistry registry = field(harness.scheduler, "taskRegistry", TaskRegistry.class);

      List<String> output =
          harness.line(
              "; fork id (0) value = suspend(); "
                  + "#0.scheduler_counter = value; endfork "
                  + "suspend(0); result = resume(id, 77); return {id, result};");

      assertTrue(
          output.stream().anyMatch(line -> line.matches("\\{1, \\{[0-9]+, 0}}")),
          output.toString());
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while ((harness.counter() != 77 || registry.size() != 0)
          && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      assertEquals(77, harness.counter());
      assertEquals(0, registry.size());
    }
  }

  @Test
  void writesPanicDatabaseBeforeInvokingAbortCapability(@TempDir Path temporaryDirectory)
      throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    Path checkpoint = temporaryDirectory.resolve("panic.db.new");
    RecordingListener listener = new RecordingListener();
    listener.expectedPanicDump = Optional.of(Path.of(checkpoint.toString() + ".PANIC"));
    MooRuntime runtime = new MooRuntime(world, listener, checkpoint);
    try {
      runtime.openConnection(CONNECTION_ID, 0, true, new MapValue(Map.of()));
      runtime.executeLine(CONNECTION_ID, "connect Wizard");
      runtime.executeLine(CONNECTION_ID, "; return shutdown(\"focused panic\", 1);");

      assertTrue(listener.panicCalled);
      assertTrue(listener.panicDumpPresentAtCall);
      assertTrue(Files.isRegularFile(listener.expectedPanicDump.orElseThrow()));
      assertEquals(
          0,
          new LambdaMooV17Codec()
              .read(listener.expectedPanicDump.orElseThrow())
              .world()
              .snapshot()
              .pendingFinalization()
              .size());
    } finally {
      scheduler(runtime).close();
    }
  }

  @Test
  void roundTripsProtectedNestedCatchFinallyLoopAndCatchHandlerPhases() {
    String protectedSource =
        """
        try
          for item in ({1, 2})
            try
              suspend();
            except selected (E_TYPE, E_INVARG)
            except allErrors (ANY)
            endtry
          endfor
        finally
          0;
        endtry
        """;
    ToastV17ProgramLayout.StructuralStackShape protectedShape =
        structuralShape(protectedSource, 0);
    ToastV17ProgramLayout.CollectionLoop loop =
        protectedShape.entriesBaseToTop().stream()
            .filter(ToastV17ProgramLayout.CollectionLoop.class::isInstance)
            .map(ToastV17ProgramLayout.CollectionLoop.class::cast)
            .findFirst()
            .orElseThrow();
    ListValue loopBase =
        new ListValue(List.of(new IntegerValue(1), new IntegerValue(2)));
    assertActivationStackRoundTrips(
        protectedSource,
        0,
        structuralStack(
            protectedSource,
            0,
            Map.of(
                loop.baseDepth(), valueSlot(loopBase),
                loop.iteratorDepth(), valueSlot(new IntegerValue(2)))));

    String clauseSource =
        """
        try
          0;
        except problem (E_TYPE)
          suspend();
        endtry
        """;
    assertActivationStackRoundTrips(clauseSource, 0, List.of());

    String fallbackSource = "return `1 / 0 ! E_DIV => suspend()';\n";
    assertActivationStackRoundTrips(fallbackSource, 0, List.of());
  }

  @Test
  void roundTripsEverySupportedFinallyReasonAndRejectsFinAbort() {
    String source =
        """
        try
          return 9;
        finally
          suspend();
        endtry
        """;
    ToastV17ProgramLayout.FinallyContinuation continuation =
        assertInstanceOf(
            ToastV17ProgramLayout.FinallyContinuation.class,
            structuralShape(source, 0).entriesBaseToTop().getFirst());
    ListValue exception =
        new ListValue(
            List.of(
                ErrorValue.E_TYPE,
                string("wrong type"),
                new IntegerValue(17),
                new ListValue(List.of())));
    for (Map.Entry<Long, MooValue> expected :
        Map.<Long, MooValue>of(
                0L, new IntegerValue(0),
                1L, exception,
                2L, new IntegerValue(0),
                3L, string("returned"))
            .entrySet()) {
      assertActivationStackRoundTrips(
          source,
          0,
          structuralStack(
              source,
              0,
              Map.of(
                  continuation.reasonDepth(), valueSlot(new IntegerValue(expected.getKey())),
                  continuation.valueDepth(), valueSlot(expected.getValue()))));
    }

    String exitSource =
        """
        while outer (1)
          try
            break outer;
          finally
            suspend();
          endtry
        endwhile
        """;
    ToastV17ProgramLayout.FinallyContinuation exitContinuation =
        assertInstanceOf(
            ToastV17ProgramLayout.FinallyContinuation.class,
            structuralShape(exitSource, 0).entriesBaseToTop().getFirst());
    ToastV17ProgramLayout.ToastExitTarget target = exitContinuation.exitTargets().getFirst();
    ListValue rawExit =
        new ListValue(
            List.of(
                new IntegerValue(target.targetStackDepth()),
                new IntegerValue(target.targetProgramCounter())));
    assertActivationStackRoundTrips(
        exitSource,
        0,
        structuralStack(
            exitSource,
            0,
            Map.of(
                exitContinuation.reasonDepth(), valueSlot(new IntegerValue(5)),
                exitContinuation.valueDepth(), valueSlot(rawExit))));

    List<SuspendedStackSlot> aborted =
        structuralStack(
            source,
            0,
            Map.of(
                continuation.reasonDepth(), valueSlot(new IntegerValue(4)),
                continuation.valueDepth(), valueSlot(new IntegerValue(0))));
    assertThrows(
        IllegalArgumentException.class,
        () -> importActivation(suspendedActivation(source, 0, aborted)));
  }

  @Test
  void roundTripsEveryCollectionAndRangeCursorIncludingNestedLoops() {
    ListValue list =
        new ListValue(List.of(new IntegerValue(1), new IntegerValue(2)));
    assertLoopStackRoundTrips(
        "for value in ({1, 2})\n  suspend();\nendfor\n",
        list,
        valueSlot(new IntegerValue(3)));

    StringValue string = string("ab");
    assertLoopStackRoundTrips(
        "for value, index in (\"ab\")\n  suspend();\nendfor\n",
        string,
        valueSlot(new IntegerValue(3)));

    MapValue map =
        new MapValue(Map.of(new IntegerValue(1), string("one")));
    String mapSource = "for value, key in ([1 -> \"one\"])\n  suspend();\nendfor\n";
    assertLoopStackRoundTrips(mapSource, map, valueSlot(new IntegerValue(1)));
    assertLoopStackRoundTrips(
        mapSource,
        map,
        new SuspendedStackSlot(Optional.empty(), 6, 0));

    String integerRange = "for value in [1..2]\n  suspend();\nendfor\n";
    ToastV17ProgramLayout.RangeLoop integerShape =
        assertInstanceOf(
            ToastV17ProgramLayout.RangeLoop.class,
            structuralShape(integerRange, 0).entriesBaseToTop().getFirst());
    assertActivationStackRoundTrips(
        integerRange,
        0,
        structuralStack(
            integerRange,
            0,
            Map.of(
                integerShape.nextDepth(), valueSlot(new IntegerValue(Long.MAX_VALUE)),
                integerShape.endDepth(), valueSlot(new IntegerValue(Long.MAX_VALUE)))));

    String objectRange = "for value in [#1..#2]\n  suspend();\nendfor\n";
    ToastV17ProgramLayout.RangeLoop objectShape =
        assertInstanceOf(
            ToastV17ProgramLayout.RangeLoop.class,
            structuralShape(objectRange, 0).entriesBaseToTop().getFirst());
    assertActivationStackRoundTrips(
        objectRange,
        0,
        structuralStack(
            objectRange,
            0,
            Map.of(
                objectShape.nextDepth(), valueSlot(new ObjectValue(Long.MAX_VALUE)),
                objectShape.endDepth(), valueSlot(new ObjectValue(Long.MAX_VALUE)))));

    String nested =
        """
        for outer in ({1})
          for inner in [#1..#3]
            suspend();
          endfor
        endfor
        """;
    ToastV17ProgramLayout.StructuralStackShape nestedShape = structuralShape(nested, 0);
    ToastV17ProgramLayout.CollectionLoop outer =
        assertInstanceOf(
            ToastV17ProgramLayout.CollectionLoop.class,
            nestedShape.entriesBaseToTop().get(0));
    ToastV17ProgramLayout.RangeLoop inner =
        assertInstanceOf(
            ToastV17ProgramLayout.RangeLoop.class,
            nestedShape.entriesBaseToTop().get(1));
    assertActivationStackRoundTrips(
        nested,
        0,
        structuralStack(
            nested,
            0,
            Map.of(
                outer.baseDepth(), valueSlot(new ListValue(List.of(new IntegerValue(1)))),
                outer.iteratorDepth(), valueSlot(new IntegerValue(2)),
                inner.nextDepth(), valueSlot(new ObjectValue(2)),
                inner.endDepth(), valueSlot(new ObjectValue(3)))));
  }

  @Test
  void assignsMonotonicallyIncreasingTicketsInReadyOrder() throws Exception {
    try (Harness harness = Harness.open(2, new RecordingListener())) {
      long first = harness.scheduler.nextTicket();

      harness.line("; return 1;");
      long second = harness.scheduler.nextTicket();
      harness.line("; return 2;");
      long third = harness.scheduler.nextTicket();
      harness.line("; return 3;");
      long after = harness.scheduler.nextTicket();

      assertEquals(first + 1, second);
      assertEquals(second + 1, third);
      assertEquals(third + 1, after);
      assertEquals(after, harness.scheduler.nextPublicationTicket());
    }
  }

  @Test
  void usesFixedWorkersAndWorkersTimesFourBoundedQueue() throws Exception {
    WorldTxn root = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(root);
    try (PublicationScheduler scheduler = new PublicationScheduler(root, runtime, 3)) {
      ThreadPoolExecutor executor = field(scheduler, "executor", ThreadPoolExecutor.class);

      assertEquals(3, scheduler.workers());
      assertEquals(3, executor.getCorePoolSize());
      assertEquals(3, executor.getMaximumPoolSize());
      assertInstanceOf(ArrayBlockingQueue.class, executor.getQueue());
      assertEquals(12, scheduler.queueCapacity());
      assertEquals(12, executor.getQueue().remainingCapacity());
      assertEquals(
          1,
          List.of(PublicationScheduler.class.getDeclaredFields()).stream()
              .filter(field -> field.getType() == ThreadPoolExecutor.class)
              .count());
    } finally {
      scheduler(runtime).close();
    }
  }

  @Test
  void threadPoolReconfiguresTheExistingExecutorAndZeroRejectsHostWork() throws Exception {
    try (Harness harness = Harness.open(3, new RecordingListener())) {
      List<String> result =
          harness.line(
              "; thread_pool(\"INIT\", \"MAIN\", 0); "
                  + "try all_members(\"a\", {\"A\"}); "
                  + "except (E_QUOTA) "
                  + "thread_pool(\"INIT\", \"MAIN\", 2); return 1; "
                  + "endtry");
      ThreadPoolExecutor executor =
          field(harness.scheduler, "executor", ThreadPoolExecutor.class);

      assertTrue(result.contains("{1, 1}"), result.toString());
      assertEquals(2, harness.scheduler.workers());
      assertEquals(2, executor.getCorePoolSize());
      assertEquals(2, executor.getMaximumPoolSize());
      assertEquals(
          executor.getQueue().size() + executor.getQueue().remainingCapacity(),
          harness.scheduler.queueCapacity());
      assertEquals(
          1,
          List.of(PublicationScheduler.class.getDeclaredFields()).stream()
              .filter(field -> field.getType() == ThreadPoolExecutor.class)
              .count());
    }
  }

  @Test
  void runsAllMembersThroughHostAndSynchronousModesWithoutLeakingMode() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      List<String> first =
          harness.line(
              "; threaded = all_members(\"a\", {\"A\", \"b\", \"a\"}); "
                  + "set_thread_mode(0); "
                  + "synchronous = all_members(\"a\", {\"A\", \"b\", \"a\"}); "
                  + "return {threaded, synchronous, set_thread_mode()};");
      List<String> second = harness.line("; return set_thread_mode();");

      assertTrue(first.contains("{1, {{1, 3}, {1, 3}, 0}}"), first.toString());
      assertTrue(second.contains("{1, 1}"), second.toString());
    }
  }

  @Test
  void routesSuspendedSortTypeErrorThroughCapturedWaifHandler() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      List<String> caught =
          harness.line(
              "; class = create($waif); first = class:new(); second = class:new(); "
                  + "try ignored = sort({first, second}); sorting = E_NONE; "
                  + "except error (ANY) sorting = error[1]; endtry "
                  + "recycle(class); return sorting;");
      List<String> comparisons =
          harness.line(
              "; class = create($waif); first = class:new(); second = class:new(); "
                  + "result = {first < second, first <= second, first > second, "
                  + "first >= second}; recycle(class); return result;");
      List<String> result =
          harness.line(
              "; class = create($waif); first = class:new(); second = class:new(); "
                  + "try ignored = sort({first, second}); sorting = E_NONE; "
                  + "except error (ANY) sorting = error[1]; endtry "
                  + "result = {first < second, first <= second, first > second, "
                  + "first >= second, sorting}; recycle(class); return result;");

      assertTrue(caught.contains("{1, E_TYPE}"), caught.toString());
      assertTrue(comparisons.contains("{1, {0, 1, 0, 1}}"), comparisons.toString());
      assertTrue(result.contains("{1, {0, 1, 0, 1, E_TYPE}}"), result.toString());
    }
  }

  @Test
  void waifPropertyAssignmentRejectsDirectAndCollectionSelfReferences() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      List<String> result =
          harness.line(
              "; class = create($waif); add_property(class, \":value\", 0, {player, \"\"}); "
                  + "waif = class:new(); "
                  + "try waif.value = waif; direct = E_NONE; "
                  + "except error (ANY) direct = error[1]; endtry "
                  + "try waif.value = {waif}; list_recursion = E_NONE; "
                  + "except error (ANY) list_recursion = error[1]; endtry "
                  + "try waif.value = [\"self\" -> waif]; map_recursion = E_NONE; "
                  + "except error (ANY) map_recursion = error[1]; endtry "
                  + "result = {direct, list_recursion, map_recursion}; "
                  + "recycle(class); return result;");

      assertTrue(
          result.contains("{1, {E_RECMOVE, E_RECMOVE, E_RECMOVE}}"), result.toString());
    }
  }

  @Test
  void wizardOwnedWaifClassDispatchesIndexHandlers() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      List<String> result =
          harness.line(
              "; class = create($waif); "
                  + "add_property(class, \":last_key\", \"\", {player, \"\"}); "
                  + "add_property(class, \":last_value\", 0, {player, \"\"}); "
                  + "add_verb(class, {player, \"xd\", \":_index\"}, "
                  + "{\"this\", \"none\", \"this\"}); "
                  + "set_verb_code(class, \":_index\", "
                  + "{\"return {args[1], this.last_key, this.last_value, "
                  + "typeof(this) == WAIF};\"}); "
                  + "add_verb(class, {player, \"xd\", \":_set_index\"}, "
                  + "{\"this\", \"none\", \"this\"}); "
                  + "set_verb_code(class, \":_set_index\", "
                  + "{\"this.last_key = args[1];\", \"this.last_value = args[2];\", "
                  + "\"return this;\"}); "
                  + "waif = class:new(); "
                  + "try waif[\"answer\"] = 42; write_result = E_NONE; "
                  + "except error (ANY) write_result = error[1]; endtry "
                  + "try read_result = waif[\"answer\"]; "
                  + "except error (ANY) read_result = error[1]; endtry "
                  + "result = {waif.class == class, class.owner == player, "
                  + "class.owner.wizard, write_result, read_result}; "
                  + "recycle(class); return result;");

      assertTrue(
          result.contains("{1, {1, 1, 1, E_NONE, {\"answer\", \"answer\", 42, 1}}}"),
          result.toString());
    }
  }

  @Test
  void queuedTasksRetainsDelayedAndZeroDelayWaifForksAtObservationBoundary() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      List<String> result =
          harness.line(
              "; class = create($waif); "
                  + "add_verb(class, {player, \"xd\", \":a\"}, {\"this\", \"none\", \"this\"}); "
                  + "set_verb_code(class, \":a\", {\"suspend();\"}); "
                  + "add_verb(class, {player, \"xd\", \":b\"}, {\"this\", \"none\", \"this\"}); "
                  + "set_verb_code(class, \":b\", {\"return this:a();\"}); "
                  + "add_verb(class, {player, \"xd\", \":c\"}, {\"this\", \"none\", \"this\"}); "
                  + "set_verb_code(class, \":c\", {"
                  + "\"for delay in ({0, 100})\", "
                  + "\"  fork (delay)\", "
                  + "\"    c = this:b();\", "
                  + "\"  endfork\", "
                  + "\"endfor\", "
                  + "\"suspend(0);\", "
                  + "\"q = queued_tasks();\", "
                  + "\"result = {length(q), q};\", "
                  + "\"for row in (q) kill_task(row[1]); endfor\", "
                  + "\"return result;\"}); "
                  + "waif = class:new(); result = waif:c(); recycle(class); return result;");

      assertTrue(result.toString().contains("{2, "), result.toString());
      assertTrue(result.toString().contains("\":c\""), result.toString());
      assertTrue(result.toString().contains("\":a\""), result.toString());
      assertTrue(
          result.toString().indexOf("\":c\"") < result.toString().indexOf("\":a\""),
          result.toString());
    }
  }

  @Test
  void unreachableWaifRunsRecycleOnceEvenWhenHookStashesItself() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      assertTrue(
          harness
              .line(
                  "; class = create($waif); "
                      + "add_property(class, \"reference\", 0, {player, \"\"}); "
                      + "add_property(class, \"recycle_called\", 0, {player, \"\"}); "
                      + "add_verb(class, {player, \"xd\", \":recycle\"}, "
                      + "{\"this\", \"none\", \"this\"}); "
                      + "set_verb_code(class, \":recycle\", {"
                      + "\"this.class.recycle_called = this.class.recycle_called + 1;\", "
                      + "\"this.class.reference = this;\"}); "
                      + "add_property(#0, \"scheduler_waif_class\", class, {player, \"rw\"}); "
                      + "return 0;")
              .contains("{1, 0}"));

      assertTrue(
          harness.line("; w = #0.scheduler_waif_class:new(); return 0;").contains("{1, 0}"));
      assertTrue(
          harness
              .line("; return #0.scheduler_waif_class.recycle_called;")
              .contains("{1, 1}"));
      assertTrue(
          harness.line("; #0.scheduler_waif_class.reference = 0; return 0;").contains("{1, 0}"));
      assertTrue(harness.line("; return 0;").contains("{1, 0}"));
      assertTrue(
          harness
              .line("; return #0.scheduler_waif_class.recycle_called;")
              .contains("{1, 1}"));

      assertTrue(
          harness
              .line(
                  "; class = #0.scheduler_waif_class; "
                      + "delete_property(#0, \"scheduler_waif_class\"); "
                      + "recycle(class); return 0;")
              .contains("{1, 0}"));
    }
  }

  @Test
  void permanentPropertyKeepsAnonymousValueReachableAcrossCommands() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      List<String> setup =
          harness.line(
                  "; add_property(#0, \"scheduler_anon_class\", 0, {player, \"rw\"}); "
                      + "switch_player(player, #4); set_task_perms(#4); player = #4; "
                      + "class = create($object); observer = create($nothing); "
                      + "add_property(class, \"anon\", 0, {player, \"r\"}); "
                      + "add_property(class, \"observer\", observer, {player, \"r\"}); "
                      + "add_verb(class, {player, \"xd\", \"entry_owner\"}, "
                      + "{\"this\", \"none\", \"this\"}); "
                      + "set_verb_code(class, \"entry_owner\", "
                      + "{\"return this.observer:probe_owner();\"}); "
                      + "add_verb(observer, {player, \"xd\", \"probe_owner\"}, "
                      + "{\"this\", \"none\", \"this\"}); "
                      + "set_verb_code(observer, \"probe_owner\", {"
                      + "\"frames = callers();\", "
                      + "\"value = frames[1][1];\", "
                      + "\"return {typeof(value), valid(value), toliteral(value)};\"}); "
                      + "class.anon = create(class, 1); "
                      + "#0.scheduler_anon_class = class; "
                      + "return 0;");
      assertTrue(setup.contains("{1, 0}"), setup.toString());

      List<String> observed =
          harness.line(
              "; class = #0.scheduler_anon_class; "
                  + "return {valid(class.anon), class.anon:entry_owner()};");
      assertTrue(
          observed.toString().contains("{1, {1, {12, 1, \"*anonymous*\"}}}"),
          observed.toString());

    }
  }

  @Test
  void forkedTaskCanEnterAndCompleteHostSuspension() throws Exception {
    try (Harness harness = Harness.open(2, new RecordingListener())) {
      TaskRegistry registry = field(harness.scheduler, "taskRegistry", TaskRegistry.class);

      List<String> parent =
          harness.line(
              "; #0.scheduler_counter = 0; "
                  + "fork (0) "
                  + "#0.scheduler_counter = length(all_members(\"a\", {\"a\", \"b\", \"A\"})); "
                  + "endfork "
                  + "return 1;");

      assertTrue(parent.contains("{1, 1}"), parent.toString());
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while ((harness.counter() != 2 || registry.size() != 0)
          && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      assertEquals(2, harness.counter());
      assertEquals(0, registry.size());
      assertTrue(harness.line("; return 7;").contains("{1, 7}"));
    }
  }

  @Test
  void mapsRejectedHostSubmissionToCatchableQuotaError() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      try (WorldTxn transaction = harness.root.begin()) {
        ObjectValue serverOptions =
            (ObjectValue) transaction.readObjectProperty(0, "server_options").orElseThrow();
        boolean configured =
            transaction.property(serverOptions.value(), "fg_ticks").isPresent()
                ? transaction.writeObjectProperty(
                    serverOptions.value(), "fg_ticks", new IntegerValue(20_000_000))
                : transaction.addProperty(
                    serverOptions.value(), "fg_ticks", new IntegerValue(20_000_000), 0, 3);
        assertTrue(configured);
        assertTrue(transaction.commit().isCommitted());
      }
      ThreadPoolExecutor executor =
          field(harness.scheduler, "executor", ThreadPoolExecutor.class);
      CountDownLatch release = new CountDownLatch(1);
      long ticket = harness.scheduler.nextTicket();
      CompletableFuture<List<String>> result =
          harness.lineAsync(
              "; i = 0; while (i < 2000000) i = i + 1; endwhile "
                  + "try return all_members(1, {1}); "
                  + "except (E_QUOTA) return \"quota\"; endtry");
      while (harness.scheduler.nextTicket() == ticket || executor.getActiveCount() == 0) {
        Thread.onSpinWait();
      }
      for (int queued = 0; queued < 4; queued++) {
        executor.execute(() -> awaitRelease(release));
      }

      try {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (readySize(harness.scheduler) == 0 && System.nanoTime() < deadline) {
          Thread.onSpinWait();
        }
        assertTrue(readySize(harness.scheduler) > 0);
      } finally {
        release.countDown();
      }
      while (executor.getActiveCount() != 0 || !executor.getQueue().isEmpty()) {
        Thread.onSpinWait();
      }
      harness.line("; return 1;");
      assertTrue(result.get(3, TimeUnit.SECONDS).contains("{1, \"quota\"}"));
    }
  }

  @Test
  void killingRegisteredHostWaitCancelsWithoutLateWakeOrIngressHang() throws Exception {
    try (Harness harness = Harness.open(2, new RecordingListener())) {
      TaskRegistry registry = field(harness.scheduler, "taskRegistry", TaskRegistry.class);
      CompletableFuture<List<String>> waiting =
          harness.lineAsync(
              "; #0.scheduler_counter = task_id(); "
                  + "return read();");
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while ((harness.counter() == 0 || registry.size() == 0)
          && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      long waitingTaskId = harness.counter();
      assertTrue(waitingTaskId > 0);
      assertEquals(1, registry.size());

      List<String> killed =
          harness.runtime.executeLine(
              SECOND_CONNECTION_ID,
              "; return kill_task(" + waitingTaskId + ");");

      assertTrue(killed.contains("{1, 0}"), killed.toString());
      try {
        waiting.get(3, TimeUnit.SECONDS);
        fail("killed host wait must not complete normally");
      } catch (ExecutionException failure) {
        assertInstanceOf(CancellationException.class, failure.getCause());
      }
      assertEquals(0, registry.size());
      assertFalse(
          field(harness.scheduler, "ingress", Map.class).containsKey(waitingTaskId));

      List<String> later =
          harness.runtime.executeLine(
              SECOND_CONNECTION_ID,
              "; #0.scheduler_counter = 77; return #0.scheduler_counter;");
      assertTrue(later.contains("{1, 77}"), later.toString());
      assertEquals(77, harness.counter());
      long stableTicket = harness.scheduler.nextTicket();
      deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      ThreadPoolExecutor executor =
          field(harness.scheduler, "executor", ThreadPoolExecutor.class);
      while ((executor.getActiveCount() != 0 || !executor.getQueue().isEmpty())
          && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      assertEquals(stableTicket, harness.scheduler.nextTicket());
      assertEquals(0, readySize(harness.scheduler));
    }
  }

  @Test
  void keepsOverflowInReadyQueueUntilExecutorCapacityReturns() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      ThreadPoolExecutor executor =
          field(harness.scheduler, "executor", ThreadPoolExecutor.class);
      executor.execute(
          () -> {
            entered.countDown();
            awaitRelease(release);
          });
      entered.await();

      long firstOverflowTicket = harness.scheduler.nextTicket();
      List<CompletableFuture<List<String>>> overflow = new ArrayList<>();
      for (int index = 0; index < 5; index++) {
        overflow.add(harness.lineAsync("; return " + index + ";"));
      }
      while (harness.scheduler.nextTicket() != firstOverflowTicket + 5) {
        Thread.onSpinWait();
      }

      try {
        assertTrue(readySize(harness.scheduler) > 0);
      } finally {
        release.countDown();
      }
      overflow.forEach(CompletableFuture::join);
      assertEquals(0, readySize(harness.scheduler));
    }
  }

  @Test
  void publishesReverseCompletionsInTicketOrder() throws Exception {
    RecordingListener listener = new RecordingListener();
    try (Harness harness = Harness.open(2, listener)) {
      try (ConflictScenario scenario = startConflictScenario(harness)) {
        assertEquals(scenario.earlierTicket, harness.scheduler.nextPublicationTicket());

        scenario.finish();
        assertEquals(
            List.of(scenario.laterTicket, scenario.earlierTicket),
            scenario.initialCompletionTickets);
        assertTrue(scenario.laterTicket > scenario.earlierTicket);
        assertEquals(2, harness.counter());
        assertEquals(harness.scheduler.nextTicket(), harness.scheduler.nextPublicationTicket());
      }
    }
  }

  @Test
  void validatesOnlyWhenTicketOwnsPublicationTurn() throws Exception {
    RecordingListener listener = new RecordingListener();
    try (Harness harness = Harness.open(2, listener)) {
      try (ConflictScenario scenario = startConflictScenario(harness)) {
        scenario.finish();
        assertEquals(scenario.laterTicket, scenario.initialCompletionTickets.getFirst());
        assertEquals(2, harness.counter());
      }
    }
  }

  @Test
  void restoresAndRetriesConflictUnderSameTicket() throws Exception {
    RecordingListener listener = new RecordingListener();
    try (Harness harness = Harness.open(2, listener)) {
      try (ConflictScenario scenario = startConflictScenario(harness)) {
        scenario.finish();

        assertEquals(scenario.laterTicket, scenario.conflictTicket);
        assertEquals(List.of(scenario.laterTicket), scenario.conflictTickets);
        assertEquals(2, Collections.frequency(scenario.segmentTickets, scenario.laterTicket));
        assertEquals(2, harness.counter());
      }
    }
  }

  @Test
  void discardsEffectsFromConflictedAttempt() throws Exception {
    RecordingListener listener = new RecordingListener();
    try (Harness harness = Harness.open(2, listener)) {
      try (ConflictScenario scenario = startConflictScenario(harness)) {
        List<String> publishedOutput = scenario.finish();

        assertEquals(1, Collections.frequency(publishedOutput, "conflicted-effect"));
        assertEquals(2, harness.counter());
      }
    }
  }

  @Test
  void rollsBackTickAbortedMutationAndCompletesWithFramedOutput() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      try (WorldTxn transaction = harness.root.begin()) {
        ObjectValue serverOptions =
            (ObjectValue) transaction.readObjectProperty(0, "server_options").orElseThrow();
        boolean configured =
            transaction.property(serverOptions.value(), "fg_ticks").isPresent()
                ? transaction.writeObjectProperty(
                    serverOptions.value(), "fg_ticks", new IntegerValue(100))
                : transaction.addProperty(
                    serverOptions.value(), "fg_ticks", new IntegerValue(100), 0, 3);
        assertTrue(configured);
        assertTrue(transaction.commit().isCommitted());
      }
      harness.line("PREFIX " + CONNECTION_PREFIX);
      harness.line("SUFFIX " + CONNECTION_SUFFIX);

      List<String> output =
          harness.line(
              "; #0.scheduler_counter = 99; "
                  + "try i = 0; while (1) i = i + 1; endwhile "
                  + "except (ANY) return \"caught\"; endtry return \"completed\";");

      assertEquals(
          List.of(
              CONNECTION_PREFIX,
              CONNECTION_PREFIX,
              "Task ran out of ticks",
              CONNECTION_SUFFIX),
          output);
      assertEquals(0, harness.counter());
    }
  }

  @Test
  void invokesTaskTimeoutHandlerAfterBackgroundTickAbort() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      try (WorldTxn transaction = harness.root.begin()) {
        ObjectValue serverOptions =
            (ObjectValue) transaction.readObjectProperty(0, "server_options").orElseThrow();
        boolean configured =
            transaction.property(serverOptions.value(), "bg_ticks").isPresent()
                ? transaction.writeObjectProperty(
                    serverOptions.value(), "bg_ticks", new IntegerValue(100))
                : transaction.addProperty(
                    serverOptions.value(), "bg_ticks", new IntegerValue(100), 0, 3);
        assertTrue(configured);
        WorldObject system = transaction.object(0).orElseThrow();
        int handlerIndex =
            IntStream.range(0, system.verbs().size())
                .filter(index -> system.verbs().get(index).names().equals("handle_task_timeout"))
                .findFirst()
                .orElseThrow();
        assertTrue(
            transaction.setVerbCode(
                0,
                handlerIndex,
                "#0.scheduler_counter = length(args) == 3 && args[1] == \"ticks\" "
                    + "&& typeof(args[2]) == LIST && length(args[2]) > 0 "
                    + "&& typeof(args[3]) == LIST && length(args[3]) > 0; return 1;"));
        assertTrue(transaction.commit().isCommitted());
      }

      harness.line("; fork (0) while (1) endwhile endfork return 1;");
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while (harness.counter() == 0 && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }

      assertEquals(1, harness.counter());
    }
  }

  @Test
  void yinRefreshesTheBudgetForAnOtherwiseOverBudgetLoop() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      List<String> output =
          harness.line(
              "; count = 0; for i in [1..300000] count = count + 1; yin(); endfor return count;");

      assertEquals(
          List.of(CONNECTION_PREFIX, "{1, 300000}", CONNECTION_SUFFIX), output);
    }
  }

  @Test
  void doCommandIsServerInitiatedEvenThoughPlayerRemainsTheConnectedPlayer() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      harness.line(
          "; add_property(#0, \"scheduler_do_command_frame\", {}, {#0, \"rw\"}); "
              + "add_verb(#0, {#0, \"rxd\", \"do_command\"}, {\"this\", \"none\", \"this\"}); "
              + "set_verb_code(#0, \"do_command\", "
              + "{\"if (args[1] != \\\";\\\")\", "
              + "\"#0.scheduler_do_command_frame = {player, caller};\", "
              + "\"endif\", \"return 0;\"}); return 1;");

      harness.line("frameprobe");

      try (WorldTxn transaction = harness.root.begin()) {
        assertEquals(
            new ListValue(List.of(new ObjectValue(8), new ObjectValue(-1))),
            transaction.readObjectProperty(0, "scheduler_do_command_frame").orElseThrow());
      }
    }
  }

  @Test
  void resumesSuspendedForegroundTaskWithBackgroundLimits() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      harness.line(
          """
          ; try
              add_property($server_options, "fg_ticks", 50000, {player, "r"});
            except (E_INVARG)
              $server_options.fg_ticks = 50000;
            endtry
            try
              add_property($server_options, "bg_ticks", 9000, {player, "r"});
            except (E_INVARG)
              $server_options.bg_ticks = 9000;
            endtry
            try
              add_property($server_options, "bg_seconds", 7, {player, "r"});
            except (E_INVARG)
              $server_options.bg_seconds = 7;
            endtry
            load_server_options();
            return 1;
          """);

      List<String> output =
          harness.line(
              """
              ; before_ticks = ticks_left();
                suspend(0);
                after_ticks = ticks_left();
                after_seconds = seconds_left();
                return {
                  before_ticks <= 50000 && before_ticks > 49000,
                  after_ticks <= 9000 && after_ticks > 8500,
                  after_seconds <= 7 && after_seconds > 4
                };
              """);

      assertEquals(
          List.of(CONNECTION_PREFIX, "{1, {1, 1, 1}}", CONNECTION_SUFFIX), output);
    }
  }

  @Test
  void releasesEveryChildRevisionAfterCommitConflictAndFailure() throws Exception {
    RecordingListener listener = new RecordingListener();
    try (Harness harness = Harness.open(2, listener)) {
      harness.line("; return 1;");
      try (ConflictScenario scenario = startConflictScenario(harness)) {
        scenario.finish();
      }
      try {
        harness.runtime.executeLine(-999, "; return 1;");
        fail("unknown connection request must fail");
      } catch (IllegalArgumentException expected) {
        assertEquals("unknown connection #-999", expected.getMessage());
      }

      assertEquals(1, retainedRevisionCount(harness.root));
    }
  }

  @Test
  void hasNoSerializedFallbackMode() throws Exception {
    assertFalse(Modifier.isSynchronized(MooRuntime.class.getMethod("openConnection", long.class).getModifiers()));
    assertFalse(
        Modifier.isSynchronized(
            MooRuntime.class.getMethod("executeLine", long.class, String.class).getModifiers()));
    assertFalse(
        Modifier.isSynchronized(MooRuntime.class.getMethod("closeConnection", long.class).getModifiers()));
    assertEquals(
        1,
        List.of(MooRuntime.class.getDeclaredFields()).stream()
            .filter(field -> field.getType() == PublicationScheduler.class)
            .count());
    assertTrue(
        List.of(MooRuntime.class.getDeclaredFields()).stream()
            .noneMatch(field -> field.getName().toLowerCase(Locale.ROOT).contains("serial")));
  }

  @Test
  void completesForkParentBeforeChildAndNeverPublishesChildOutputToParent() throws Exception {
    try (Harness harness = Harness.open(2, new RecordingListener())) {
      TaskRegistry registry = field(harness.scheduler, "taskRegistry", TaskRegistry.class);

      CompletableFuture<List<String>> parent =
          harness.lineAsync(
              "; fork task_id (5) suspend(5); return 99; endfork "
                  + "tasks = queued_tasks(); "
                  + "return length(tasks) > 0 "
                  + "&& tasks[length(tasks)][1] == task_id;");
      List<String> output = parent.get(3, TimeUnit.SECONDS);

      assertTrue(output.stream().noneMatch(line -> line.contains("99")), output.toString());
      assertTrue(output.contains("{1, 1}"), output.toString());
      assertEquals(1, registry.size());
    }
  }

  @Test
  void removesForkFromRegistryAfterChildTerminalCompletion() throws Exception {
    try (Harness harness = Harness.open(2, new RecordingListener())) {
      TaskRegistry registry = field(harness.scheduler, "taskRegistry", TaskRegistry.class);

      harness
          .lineAsync("; fork (0) suspend(0.1); return 99; endfork return 1;")
          .get(3, TimeUnit.SECONDS);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while (registry.size() != 0 && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      assertEquals(0, registry.size());
    }
  }

  @Test
  void killedReadyForkNeverPublishesItsWorldMutation() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      TaskRegistry registry = field(harness.scheduler, "taskRegistry", TaskRegistry.class);

      List<String> output =
          harness.line(
              "; fork task_id (0) #0.scheduler_counter = 99; endfork "
                  + "return kill_task(task_id);");

      assertTrue(output.contains("{1, 0}"), output.toString());
      assertEquals(0, harness.counter());
      assertEquals(0, registry.size());
    }
  }

  private static ConflictScenario startConflictScenario(Harness harness) throws IOException {
    try (WorldTxn transaction = harness.root.begin()) {
      ObjectValue serverOptions =
          (ObjectValue) transaction.readObjectProperty(0, "server_options").orElseThrow();
      boolean configured =
          transaction.property(serverOptions.value(), "fg_ticks").isPresent()
              ? transaction.writeObjectProperty(
                  serverOptions.value(), "fg_ticks", new IntegerValue(20_000_000))
              : transaction.addProperty(
                  serverOptions.value(), "fg_ticks", new IntegerValue(20_000_000), 0, 3);
      assertTrue(configured);
      assertTrue(transaction.commit().isCommitted());
    }
    harness.resetCounter();
    Recording events = new Recording();
    events.enable(TaskSegmentEvent.class).withoutThreshold();
    events.enable(WorldConflictEvent.class).withoutThreshold();
    events.start();
    Path eventFile = Files.createTempFile("banteng-publication-", ".jfr");

    long earlierTicket = harness.scheduler.nextTicket();
    CompletableFuture<List<String>> earlierRoot =
        harness.lineAsync(
            CONNECTION_ID,
            "; value = #0.scheduler_counter; "
                + "i = 0; while (i < 2000000) i = i + 1; value = value + 0; endwhile "
                + "#0.scheduler_counter = value + 1; "
                + "return 1;");
    while (harness.scheduler.nextTicket() == earlierTicket) {
      Thread.onSpinWait();
    }
    long laterTicket = harness.scheduler.nextTicket();
    CompletableFuture<List<String>> laterRoot =
        harness.lineAsync(
            SECOND_CONNECTION_ID,
            "; #0.scheduler_counter = #0.scheduler_counter + 1; "
                + "notify(player, \"conflicted-effect\"); "
                + "return 1;");
    while (harness.scheduler.nextTicket() == laterTicket) {
      Thread.onSpinWait();
    }
    return new ConflictScenario(
        earlierTicket,
        laterTicket,
        earlierRoot,
        laterRoot,
        events,
        eventFile);
  }

  private static void awaitRelease(CountDownLatch release) {
    try {
      release.await();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("test executor blocker interrupted", interrupted);
    }
  }

  private static PublicationScheduler scheduler(MooRuntime runtime) {
    return field(runtime, "scheduler", PublicationScheduler.class);
  }

  private static int readySize(PublicationScheduler scheduler) {
    synchronized (scheduler) {
      return field(scheduler, "ready", Queue.class).size();
    }
  }

  private static int retainedRevisionCount(WorldTxn root) {
    try {
      var method = WorldTxn.class.getDeclaredMethod("retainedRevisionCount");
      method.setAccessible(true);
      return (int) method.invoke(root);
    } catch (ReflectiveOperationException error) {
      throw new LinkageError(error.getMessage(), error);
    }
  }

  private static void assertLoopStackRoundTrips(
      String source, MooValue base, SuspendedStackSlot iterator) {
    ToastV17ProgramLayout.CollectionLoop shape =
        assertInstanceOf(
            ToastV17ProgramLayout.CollectionLoop.class,
            structuralShape(source, 0).entriesBaseToTop().getFirst());
    assertActivationStackRoundTrips(
        source,
        0,
        structuralStack(
            source,
            0,
            Map.of(shape.baseDepth(), valueSlot(base), shape.iteratorDepth(), iterator)));
  }

  private static void assertActivationStackRoundTrips(
      String source, int callIndex, List<SuspendedStackSlot> expected) {
    SuspendedActivation imported = suspendedActivation(source, callIndex, expected);
    VmSnapshot.Frame frame = importActivation(imported);
    SuspendedActivation exported = exportActivation(frame);
    assertEquals(expected, exported.operandStack(), source);
  }

  private static VmSnapshot.Frame importActivation(SuspendedActivation activation) {
    try {
      Method method =
          PublicationScheduler.class.getDeclaredMethod(
              "importActivation",
              SuspendedActivation.class,
              int.class,
              boolean.class,
              ToastV17ProgramLayout.class);
      method.setAccessible(true);
      return (VmSnapshot.Frame)
          method.invoke(null, activation, -1, true, new ToastV17ProgramLayout());
    } catch (InvocationTargetException failure) {
      throw rethrowInvocation(failure);
    } catch (ReflectiveOperationException failure) {
      throw new LinkageError(failure.getMessage(), failure);
    }
  }

  private static SuspendedActivation exportActivation(VmSnapshot.Frame frame) {
    try {
      Method method =
          PublicationScheduler.class.getDeclaredMethod(
              "exportActivation", VmSnapshot.Frame.class, long.class);
      method.setAccessible(true);
      return (SuspendedActivation) method.invoke(null, frame, 0L);
    } catch (InvocationTargetException failure) {
      throw rethrowInvocation(failure);
    } catch (ReflectiveOperationException failure) {
      throw new LinkageError(failure.getMessage(), failure);
    }
  }

  private static RuntimeException rethrowInvocation(InvocationTargetException failure) {
    Throwable cause = failure.getCause();
    if (cause instanceof RuntimeException runtime) {
      return runtime;
    }
    throw new LinkageError(cause == null ? failure.getMessage() : cause.getMessage(), failure);
  }

  private static ToastV17ProgramLayout.StructuralStackShape structuralShape(
      String source, int callIndex) {
    ToastV17ProgramLayout layout = new ToastV17ProgramLayout();
    ToastV17ProgramLayout.CallBoundary boundary =
        layout.callBoundaries(source, -1).get(callIndex);
    return layout.resolveStructuralStack(
        source, -1, new MooCompiler().compile(source), boundary);
  }

  private static List<SuspendedStackSlot> structuralStack(
      String source,
      int callIndex,
      Map<Integer, SuspendedStackSlot> supplied) {
    ToastV17ProgramLayout.StructuralStackShape shape = structuralShape(source, callIndex);
    SuspendedStackSlot[] baseToTop = new SuspendedStackSlot[shape.postArgumentDepth()];
    for (ToastV17ProgramLayout.StructuralStackEntry entry : shape.entriesBaseToTop()) {
      switch (entry) {
        case ToastV17ProgramLayout.CatchGroup group
            when group.phase() == ToastV17ProgramLayout.StructuralPhase.PROTECTED -> {
          for (int index = 0; index < group.clauses().size(); index++) {
            ToastV17ProgramLayout.ToastHandlerClause clause = group.clauses().get(index);
            baseToTop[group.baseDepth() + index * 2] =
                valueSlot(
                    clause.selector().catchesAny()
                        ? new IntegerValue(0)
                        : new ListValue(
                            clause.selector().errors().stream()
                                .map(ErrorValue::valueOf)
                                .toList()));
            baseToTop[group.baseDepth() + index * 2 + 1] =
                valueSlot(new IntegerValue(clause.handlerLabelProgramCounter()));
          }
          baseToTop[group.markerDepth().orElseThrow()] =
              new SuspendedStackSlot(Optional.empty(), 7, group.clauses().size());
        }
        case ToastV17ProgramLayout.ProtectedFinally protectedFinally ->
            baseToTop[protectedFinally.markerDepth()] =
                new SuspendedStackSlot(
                    Optional.empty(), 8, protectedFinally.handlerLabelProgramCounter());
        default -> {
          // The caller supplies typed finally and loop values for the selected case.
        }
      }
    }
    supplied.forEach(
        (depth, slot) -> {
          if (baseToTop[depth] != null) {
            throw new IllegalArgumentException("test stack slot overlaps structural control");
          }
          baseToTop[depth] = slot;
        });
    for (SuspendedStackSlot slot : baseToTop) {
      Objects.requireNonNull(slot, "missing test stack slot");
    }
    List<SuspendedStackSlot> topToBase = new ArrayList<>(List.of(baseToTop));
    Collections.reverse(topToBase);
    return List.copyOf(topToBase);
  }

  private static SuspendedStackSlot valueSlot(MooValue value) {
    return new SuspendedStackSlot(Optional.of(value), -1, 0);
  }

  private static SuspendedTask suspendedTask(
      long taskId,
      String source,
      MooValue resumeValue,
      List<SuspendedStackSlot> operandStack) {
    SuspendedActivation activation = suspendedActivation(source, 0, operandStack);
    return new SuspendedTask(
        taskId,
        -1,
        resumeValue,
        new MapValue(Map.of()),
        -1,
        0,
        50,
        Optional.empty(),
        List.of(activation));
  }

  private static SuspendedActivation suspendedActivation(
      String source, int callIndex, List<SuspendedStackSlot> operandStack) {
    ToastV17ProgramLayout.CallBoundary boundary =
        new ToastV17ProgramLayout().callBoundaries(source, -1).get(callIndex);
    Map<String, Optional<MooValue>> locals =
        Map.of(
            "this", Optional.of(new ObjectValue(0)),
            "player", Optional.of(new ObjectValue(0)),
            "caller", Optional.of(new ObjectValue(0)),
            "verb", Optional.of(string("restored_test")),
            "args", Optional.of(new moo.value.MooValue.ListValue(List.of())));
    return new SuspendedActivation(
        17,
        source,
        locals,
        operandStack,
        new ObjectValue(0),
        new ObjectValue(0),
        true,
        0,
        0,
        true,
        "restored_test",
        "restored_test",
        Optional.empty(),
        boundary.programCounter(),
        0,
        boundary.errorProgramCounter());
  }

  private static ListValue readOrder(WorldTxn world) {
    try (WorldTxn transaction = world.begin()) {
      return (ListValue) transaction.readObjectProperty(0, "order").orElseThrow();
    }
  }

  private static StringValue string(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }

  private static <T> T field(Object owner, String name, Class<T> type) {
    try {
      Field field = owner.getClass().getDeclaredField(name);
      field.setAccessible(true);
      return type.cast(field.get(owner));
    } catch (ReflectiveOperationException error) {
      throw new LinkageError(error.getMessage(), error);
    }
  }

  private static final class ConflictScenario implements AutoCloseable {
    private final long earlierTicket;
    private final long laterTicket;
    private final CompletableFuture<List<String>> earlierRoot;
    private final CompletableFuture<List<String>> laterRoot;
    private final Recording events;
    private final Path eventFile;
    private List<Long> initialCompletionTickets = List.of();
    private List<Long> segmentTickets = List.of();
    private List<Long> conflictTickets = List.of();
    private long conflictTicket = -1;
    private boolean eventsRead;

    private ConflictScenario(
        long earlierTicket,
        long laterTicket,
        CompletableFuture<List<String>> earlierRoot,
        CompletableFuture<List<String>> laterRoot,
        Recording events,
        Path eventFile) {
      this.earlierTicket = earlierTicket;
      this.laterTicket = laterTicket;
      this.earlierRoot = earlierRoot;
      this.laterRoot = laterRoot;
      this.events = events;
      this.eventFile = eventFile;
    }

    List<String> finish() throws IOException {
      earlierRoot.join();
      List<String> output = laterRoot.join();
      readEvents();
      return output;
    }

    private void readEvents() throws IOException {
      if (eventsRead) {
        return;
      }
      events.stop();
      events.dump(eventFile);
      List<RecordedEvent> recorded = RecordingFile.readAllEvents(eventFile);
      List<RecordedEvent> segments =
          recorded.stream()
              .filter(event -> event.getEventType().getName().equals("moo.TaskSegment"))
              .sorted(Comparator.comparing(RecordedEvent::getEndTime))
              .toList();
      segmentTickets = segments.stream().map(event -> event.getLong("ticket")).toList();
      initialCompletionTickets =
          segmentTickets.stream()
              .filter(ticket -> ticket == earlierTicket || ticket == laterTicket)
              .distinct()
              .toList();
      conflictTickets =
          recorded.stream()
              .filter(event -> event.getEventType().getName().equals("moo.WorldConflict"))
              .sorted(Comparator.comparing(RecordedEvent::getEndTime))
              .map(event -> event.getLong("ticket"))
              .toList();
      if (conflictTickets.isEmpty()) {
        throw new AssertionError(
            "no world conflict; segment tickets=" + segmentTickets);
      }
      conflictTicket = conflictTickets.getFirst();
      eventsRead = true;
      events.close();
      Files.delete(eventFile);
    }

    @Override
    public void close() throws IOException {
      events.close();
      Files.deleteIfExists(eventFile);
    }
  }

  private static final class Harness implements AutoCloseable {
    private final WorldTxn root;
    private final MooRuntime runtime;
    private final PublicationScheduler scheduler;

    static Harness open(int workers, ListenerControl listener) throws IOException {
      WorldTxn root = new LambdaMooV4Reader().read(FIXTURE);
      MooRuntime runtime = new MooRuntime(root, listener, workers);
      PublicationScheduler scheduler = scheduler(runtime);
      Harness harness = new Harness(root, runtime, scheduler);
      runtime.openConnection(CONNECTION_ID, 0, true, new MapValue(Map.of()));
      runtime.executeLine(CONNECTION_ID, "connect Wizard");
      runtime.openConnection(SECOND_CONNECTION_ID, 0, true, new MapValue(Map.of()));
      runtime.executeLine(SECOND_CONNECTION_ID, "connect Wizard");
      harness.resetCounter();
      return harness;
    }

    private Harness(WorldTxn root, MooRuntime runtime, PublicationScheduler scheduler) {
      this.root = root;
      this.runtime = runtime;
      this.scheduler = scheduler;
    }

    List<String> line(String source) {
      return runtime.executeLine(CONNECTION_ID, source);
    }

    CompletableFuture<List<String>> lineAsync(String source) {
      return lineAsync(CONNECTION_ID, source);
    }

    CompletableFuture<List<String>> lineAsync(long connectionId, String source) {
      return CompletableFuture.supplyAsync(() -> runtime.executeLine(connectionId, source));
    }

    void resetCounter() {
      try (WorldTxn transaction = root.begin()) {
        boolean written =
            transaction.property(0, "scheduler_counter").isPresent()
                ? transaction.writeObjectProperty(
                    0, "scheduler_counter", new IntegerValue(0))
                : transaction.addProperty(
                    0, "scheduler_counter", new IntegerValue(0), 0, 3);
        assertTrue(written);
        assertTrue(transaction.commit().isCommitted());
      }
    }

    long counter() {
      try (WorldTxn transaction = root.begin()) {
        WorldProperty property = transaction.property(0, "scheduler_counter").orElseThrow();
        return ((IntegerValue) property.value()).value();
      }
    }

    @Override
    public void close() {
      scheduler.close();
    }
  }

  private static final class RecordingListener implements ListenerControl {
    private Optional<Path> expectedPanicDump = Optional.empty();
    private boolean panicCalled;
    private boolean panicDumpPresentAtCall;

    @Override
    public int listen(
        long handler,
        int port,
        boolean ipv6,
        boolean printMessages,
        String interfaceAddress) {
      return 77;
    }

    @Override
    public List<ListenerDescription> listeners() {
      return List.of();
    }

    @Override
    public boolean unlisten(int port, boolean ipv6) {
      return true;
    }

    @Override
    public long openNetworkConnection(String host, int port, boolean ipv6, long listenerHandler) {
      return -77;
    }

    @Override
    public void writeConnection(long connectionId, List<String> output) {}

    @Override
    public void bootConnection(long connectionId, List<String> output) {}

    @Override
    public void setConnectionBinary(long connectionId, boolean binary) {}

    @Override
    public long bufferedOutputLength(long connectionId) {
      return 0;
    }

    @Override
    public void shutdown() {}

    @Override
    public void panic() {
      panicCalled = true;
      panicDumpPresentAtCall =
          expectedPanicDump.filter(Files::isRegularFile).isPresent();
    }
  }
}
