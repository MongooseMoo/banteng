package moo.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

final class ConsolidateUnaryFloatHandlersTest {
  @Test
  void replacesAllEighteenUniformManifestHandlers() {
    String before =
        source(
            """
            (a, w, p, t, id, rt, rs, r, cp, c) -> acos(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> acosh(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> asin(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> asinh(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> atanh(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> cbrt(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> ceil(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> cosine(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> cosh(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> exp(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> floor(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> log(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> log10(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> sine(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> sinh(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> sqrt(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> tangent(a),
            (a, w, p, t, id, rt, rs, r, cp, c) -> tanh(a)
            """,
            8);
    String expected =
        source(
            """
            unaryFloatBuiltin(BuiltinCatalog::acos),
            unaryFloatBuiltin(BuiltinCatalog::acosh),
            unaryFloatBuiltin(BuiltinCatalog::asin),
            unaryFloatBuiltin(BuiltinCatalog::asinh),
            unaryFloatBuiltin(BuiltinCatalog::atanh),
            unaryFloatBuiltin(Math::cbrt),
            unaryFloatBuiltin(Math::ceil),
            unaryFloatBuiltin(BuiltinCatalog::cosine),
            unaryFloatBuiltin(Math::cosh),
            unaryFloatBuiltin(Math::exp),
            unaryFloatBuiltin(Math::floor),
            unaryFloatBuiltin(BuiltinCatalog::log),
            unaryFloatBuiltin(BuiltinCatalog::log10),
            unaryFloatBuiltin(BuiltinCatalog::sine),
            unaryFloatBuiltin(Math::sinh),
            unaryFloatBuiltin(BuiltinCatalog::sqrt),
            unaryFloatBuiltin(BuiltinCatalog::tangent),
            unaryFloatBuiltin(Math::tanh)
            """,
            12);
    InMemoryExecutionContext context =
        new InMemoryExecutionContext(
            failure -> {
              throw new AssertionError(failure);
            });
    List<SourceFile> sources = JavaParser.fromJavaVersion().build().parse(context, before).toList();
    List<Result> results =
        new ConsolidateUnaryFloatHandlers()
            .run(new InMemoryLargeSourceSet(sources), context)
            .getChangeset()
            .getAllResults();

    assertEquals(18, ConsolidateUnaryFloatHandlers.operatorCount());
    assertEquals(1, results.size());
    assertEquals(expected, results.getFirst().getAfter().printAll());
  }

  private static String source(String handlers, int indentation) {
    return """
        package moo.builtin;

        import java.util.List;
        import java.util.function.DoubleUnaryOperator;

        class BuiltinCatalog {
          record MooValue() {}
          record BuiltinResult() {}

          @FunctionalInterface
          interface BuiltinHandler {
            BuiltinResult invoke(
                List<MooValue> a,
                Object w,
                Object p,
                Object t,
                Object id,
                Object rt,
                Object rs,
                Object r,
                Object cp,
                Object c);
          }

          private List<BuiltinHandler> buildManifest() {
            return List.of(
        """
        + handlers.indent(indentation)
        + """
            );
          }

          private static BuiltinHandler unaryFloatBuiltin(DoubleUnaryOperator operator) {
            return null;
          }

          private static BuiltinResult acos(List<MooValue> arguments) { return null; }
          private static BuiltinResult acosh(List<MooValue> arguments) { return null; }
          private static BuiltinResult asin(List<MooValue> arguments) { return null; }
          private static BuiltinResult asinh(List<MooValue> arguments) { return null; }
          private static BuiltinResult atanh(List<MooValue> arguments) { return null; }
          private static BuiltinResult cbrt(List<MooValue> arguments) { return null; }
          private static BuiltinResult ceil(List<MooValue> arguments) { return null; }
          private static BuiltinResult cosine(List<MooValue> arguments) { return null; }
          private static BuiltinResult cosh(List<MooValue> arguments) { return null; }
          private static BuiltinResult exp(List<MooValue> arguments) { return null; }
          private static BuiltinResult floor(List<MooValue> arguments) { return null; }
          private static BuiltinResult log(List<MooValue> arguments) { return null; }
          private static BuiltinResult log10(List<MooValue> arguments) { return null; }
          private static BuiltinResult sine(List<MooValue> arguments) { return null; }
          private static BuiltinResult sinh(List<MooValue> arguments) { return null; }
          private static BuiltinResult sqrt(List<MooValue> arguments) { return null; }
          private static BuiltinResult tangent(List<MooValue> arguments) { return null; }
          private static BuiltinResult tanh(List<MooValue> arguments) { return null; }

          private static double acos(double value) { return value; }
          private static double acosh(double value) { return value; }
          private static double asin(double value) { return value; }
          private static double asinh(double value) { return value; }
          private static double atanh(double value) { return value; }
          private static double cosine(double value) { return value; }
          private static double log(double value) { return value; }
          private static double log10(double value) { return value; }
          private static double sine(double value) { return value; }
          private static double sqrt(double value) { return value; }
          private static double tangent(double value) { return value; }
        }
        """;
  }
}
