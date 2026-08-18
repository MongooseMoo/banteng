package moo.errorprone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.errorprone.BaseErrorProneJavaCompiler;
import com.google.errorprone.scanner.ScannerSupplier;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import org.junit.jupiter.api.Test;

final class NarrowIllegalArgumentCatchTest {
  @Test
  void acceptsOnePreciselyGuardedOperation() {
    Compilation compilation =
        compile(
            """
            class Test {
              int parse(String value) {
                try {
                  return Integer.parseInt(value);
                } catch (IllegalArgumentException invalid) {
                  return 0;
                }
              }
            }
            """);

    assertEquals(Boolean.TRUE, compilation.succeeded());
  }

  @Test
  void rejectsBroadTryBlocksThatCanHideInternalIllegalArguments() {
    Compilation compilation =
        compile(
            """
            class Test {
              void parseAndApply(String value) {
                try {
                  Integer.parseInt(value);
                  apply();
                } catch (IllegalArgumentException invalid) {
                  return;
                }
              }
              void apply() {}
            }
            """);

    assertEquals(Boolean.FALSE, compilation.succeeded());
    assertTrue(
        compilation.diagnostics().stream()
            .map(diagnostic -> diagnostic.getMessage(Locale.ROOT))
            .anyMatch(message -> message.contains("exactly one operation")));
  }

  @Test
  void rejectsIllegalArgumentExceptionInsideUnionCatch() {
    Compilation compilation =
        compile(
            """
            class Test {
              void parseAndApply(String value) {
                try {
                  Integer.parseInt(value);
                  apply();
                } catch (IllegalArgumentException | ArithmeticException invalid) {
                  return;
                }
              }
              void apply() {}
            }
            """);

    assertEquals(Boolean.FALSE, compilation.succeeded());
    assertTrue(
        compilation.diagnostics().stream()
            .map(diagnostic -> diagnostic.getMessage(Locale.ROOT))
            .anyMatch(message -> message.contains("exactly one operation")));
  }

  private static Compilation compile(String source) {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    JavaFileObject compilationUnit =
        new SimpleJavaFileObject(URI.create("string:///Test.java"), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
          }
        };
    BaseErrorProneJavaCompiler compiler =
        new BaseErrorProneJavaCompiler(
            ScannerSupplier.fromBugCheckerClasses(NarrowIllegalArgumentCatch.class));
    Boolean succeeded =
        compiler
            .getTask(
                null,
                null,
                diagnostics,
                List.of("-proc:none"),
                null,
                List.of(compilationUnit))
            .call();
    return new Compilation(succeeded, List.copyOf(diagnostics.getDiagnostics()));
  }

  private record Compilation(
      Boolean succeeded, List<Diagnostic<? extends JavaFileObject>> diagnostics) {}
}
