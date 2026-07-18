package moo.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.Socket;
import java.nio.channels.Channel;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import moo.vm.VmSnapshot;
import org.junit.jupiter.api.Test;

final class DurableTaskStateArchitectureTest {
  private static final List<Class<?>> FORBIDDEN_TYPES =
      List.of(Thread.class, Future.class, CompletionStage.class, Socket.class, Channel.class, Clock.class);

  @Test
  void durableTaskSnapshotContainsNoLiveRuntimeState() {
    inspect(VmSnapshot.class, new HashSet<>(), VmSnapshot.class.getName());
  }

  private static void inspect(Type type, Set<Type> inspected, String path) {
    if (!inspected.add(type)) {
      return;
    }
    if (type instanceof ParameterizedType parameterizedType) {
      inspect(parameterizedType.getRawType(), inspected, path);
      for (Type argument : parameterizedType.getActualTypeArguments()) {
        inspect(argument, inspected, path);
      }
      return;
    }
    if (type instanceof GenericArrayType genericArrayType) {
      inspect(genericArrayType.getGenericComponentType(), inspected, path);
      return;
    }
    if (type instanceof WildcardType wildcardType) {
      for (Type bound : wildcardType.getUpperBounds()) {
        inspect(bound, inspected, path);
      }
      for (Type bound : wildcardType.getLowerBounds()) {
        inspect(bound, inspected, path);
      }
      return;
    }
    if (type instanceof TypeVariable<?> typeVariable) {
      for (Type bound : typeVariable.getBounds()) {
        inspect(bound, inspected, path);
      }
      return;
    }
    if (!(type instanceof Class<?> typeClass)) {
      return;
    }

    for (Class<?> forbiddenType : FORBIDDEN_TYPES) {
      assertFalse(
          forbiddenType.isAssignableFrom(typeClass),
          () -> path + " reaches forbidden durable state type " + typeClass.getName());
    }
    if (typeClass.isArray()) {
      inspect(typeClass.getComponentType(), inspected, path + "[]");
      return;
    }
    if (!typeClass.getPackageName().startsWith("moo.")) {
      return;
    }

    for (Field field : typeClass.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers())) {
        inspect(field.getGenericType(), inspected, path + "." + field.getName());
      }
    }
    if (typeClass.isSealed()) {
      for (Class<?> permittedSubclass : typeClass.getPermittedSubclasses()) {
        inspect(permittedSubclass, inspected, path + "<" + permittedSubclass.getSimpleName() + ">");
      }
    }
  }
}
