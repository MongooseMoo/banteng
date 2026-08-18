package moo.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

final class UseBuiltinCallTest {
  @Test
  void rewritesHandlerParametersToBuiltinCallAccessors() {
    String protocol =
        """
        package moo.builtin;

        public interface BuiltinHandler {
          Object invoke(Object arguments, Object world, long programmer, Object taskLocal,
              long taskId, long remainingTicks, long remainingSeconds, Object receiver,
              long callerProgrammer, Object callers);
        }
        """;
    String before =
        """
        package example;

        import moo.builtin.BuiltinHandler;

        class HandlerSites {
          BuiltinHandler used =
              (arguments, world, programmer, taskLocal, taskId, remainingTicks, remainingSeconds,
                      receiver, callerProgrammer, callers) ->
                  consume(arguments, world, programmer, taskLocal, taskId, remainingTicks,
                      remainingSeconds, receiver, callerProgrammer, callers);

          BuiltinHandler ignored =
              (a, w, p, t, id, rt, rs, r, cp, c) -> consume(a);

          private static Object consume(Object... values) { return values; }
        }
        """;
    InMemoryExecutionContext context =
        new InMemoryExecutionContext(
            failure -> {
              StringWriter trace = new StringWriter();
              failure.printStackTrace(new PrintWriter(trace));
              throw new AssertionError(trace.toString(), failure);
            });
    List<SourceFile> sources =
        JavaParser.fromJavaVersion().build().parse(context, protocol, before).toList();
    List<Result> results =
        new UseBuiltinCall()
            .run(new InMemoryLargeSourceSet(sources), context)
            .getChangeset()
            .getAllResults();

    assertEquals(1, results.size());
    String rewritten = results.getFirst().getAfter().printAll();
    assertTrue(rewritten.contains("BuiltinHandler used ="), rewritten);
    assertTrue(rewritten.contains("call ->"), rewritten);
    assertTrue(rewritten.contains("call.arguments()"), rewritten);
    assertTrue(rewritten.contains("call.world()"), rewritten);
    assertFalse(rewritten.contains("call.threadMode()"), rewritten);
    assertTrue(rewritten.contains("consume(call.arguments())"), rewritten);
    assertFalse(rewritten.contains("callerProgrammer, callers) ->"), rewritten);
    assertFalse(rewritten.contains("(a, w, p, t, id, rt, rs, r, cp, c)"), rewritten);
  }
}
