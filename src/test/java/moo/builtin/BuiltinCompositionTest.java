package moo.builtin;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import moo.builtin.BuiltinCatalog.ListenerControl;
import moo.value.MooValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.ValueSemantics;
import moo.world.WorldTxn;
import org.junit.jupiter.api.Test;

final class BuiltinCompositionTest {
  @Test
  void builtinCallOwnsTheCompleteInvocationContext() {
    WorldTxn world = new WorldTxn(List.of(), List.of());
    List<MooValue> arguments = List.of(new IntegerValue(1));
    MooValue taskLocal = new IntegerValue(2);
    MooValue receiver = new IntegerValue(3);
    ListValue callers = new ListValue(List.of(new IntegerValue(4)));

    BuiltinCall call =
        new BuiltinCall(arguments, world, 5, taskLocal, 6, 7, 8, receiver, 9, callers, true);

    assertAll(
        () -> assertEquals(arguments, call.arguments()),
        () -> assertSame(world, call.world()),
        () -> assertEquals(5, call.programmer()),
        () -> assertSame(taskLocal, call.taskLocal()),
        () -> assertEquals(6, call.taskId()),
        () -> assertEquals(7, call.remainingTicks()),
        () -> assertEquals(8, call.remainingSeconds()),
        () -> assertSame(receiver, call.receiver()),
        () -> assertEquals(9, call.callerProgrammer()),
        () -> assertSame(callers, call.callers()),
        () -> assertTrue(call.threadMode()));
  }

  @Test
  void handlerHasOneRecordBasedInvocationMethod() {
    Method[] methods = BuiltinHandler.class.getDeclaredMethods();

    assertEquals(1, methods.length);
    assertEquals("invoke", methods[0].getName());
    assertEquals(List.of(BuiltinCall.class), List.of(methods[0].getParameterTypes()));

    AtomicReference<BuiltinCall> observed = new AtomicReference<>();
    BuiltinHandler handler =
        call -> {
          observed.set(call);
          return BuiltinResult.value(new IntegerValue(call.threadMode() ? 1 : 0));
        };
    BuiltinCall call = call(true);

    assertEquals(BuiltinResult.value(new IntegerValue(1)), handler.invoke(call));
    assertSame(call, observed.get());
  }

  @Test
  void hostsBuilderOwnsAllCatalogDependenciesAndDefaultsEveryHandler() {
    BuiltinHosts hosts = BuiltinHosts.builder().build();

    assertEquals(ValueSemantics.STANDARD, hosts.valueSemantics());
    assertAll(
        () -> assertNotNull(hosts.queuedTasks()),
        () -> assertNotNull(hosts.killTask()),
        () -> assertNotNull(hosts.read()),
        () -> assertNotNull(hosts.threadPool()),
        () -> assertNotNull(hosts.threads()),
        () -> assertNotNull(hosts.connectionOptions()),
        () -> assertNotNull(hosts.dbDiskSize()),
        () -> assertNotNull(hosts.flushInput()),
        () -> assertNotNull(hosts.outputDelimiters()),
        () -> assertNotNull(hosts.queueInfo()),
        () -> assertNotNull(hosts.taskStack()),
        () -> assertNotNull(hosts.resumeTask()),
        () -> assertNotNull(hosts.serverLog()),
        () -> assertNotNull(hosts.connections()));

    BuiltinHandler queuedTasks = call -> BuiltinResult.value(new IntegerValue(17));
    ValueSemantics semantics = new ValueSemantics(true);
    BuiltinHosts configured =
        BuiltinHosts.builder().valueSemantics(semantics).queuedTasks(queuedTasks).build();
    assertSame(semantics, configured.valueSemantics());
    assertSame(queuedTasks, configured.queuedTasks());
  }

  @Test
  void catalogExposesExactlyTheTwoCompositionConstructors() {
    Set<List<Class<?>>> signatures =
        Arrays.stream(BuiltinCatalog.class.getDeclaredConstructors())
            .map(Constructor::getParameterTypes)
            .map(List::of)
            .collect(Collectors.toSet());

    assertEquals(
        Set.of(List.of(BuiltinHosts.class), List.of(ListenerControl.class, BuiltinHosts.class)),
        signatures);
  }

  private static BuiltinCall call(boolean threadMode) {
    return new BuiltinCall(
        List.of(),
        new WorldTxn(List.of(), List.of()),
        0,
        new IntegerValue(0),
        0,
        0,
        0,
        new IntegerValue(0),
        0,
        new ListValue(List.of()),
        threadMode);
  }
}
