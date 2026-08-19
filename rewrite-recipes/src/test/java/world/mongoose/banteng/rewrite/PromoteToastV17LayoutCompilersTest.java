package world.mongoose.banteng.rewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

final class PromoteToastV17LayoutCompilersTest implements RewriteTest {
  private static final String FACADE_PATH =
      "src/main/java/world/mongoose/banteng/persistence/ToastV17ProgramLayout.java";
  private static final String COMPILER_PATH =
      "src/main/java/world/mongoose/banteng/bytecode/LayoutCompiler.java";
  private static final String BUILDER_PATH =
      "src/main/java/world/mongoose/banteng/bytecode/VectorBuilder.java";

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new PromoteToastV17LayoutCompilers()).cycles(2);
  }

  @Test
  void promotesTheRealCompilerDependencyClosureAndRemovesEveryMovedMember() {
    rewriteRun(
        spec -> spec.expectedCyclesThatMakeChanges(1),
        java(
            """
            package world.mongoose.banteng.persistence;

            public final class ToastV17ProgramLayout {
              int facadeSentinel() {
                return 17;
              }

              private static int handlerControl() {
                return 1;
              }

              private static int iterateControl() {
                return 2;
              }

              private static int exitControl() {
                return 3;
              }

              private static int uniqueControl() {
                return 4;
              }

              private static boolean isBodyDescendant(int owner, int descendant, int component) {
                return owner + component < descendant;
              }

              private record IndexedControl(int value) {}

              private enum UnitKind {
                BYTE
              }

              private record PendingCall(int value) {}

              private static final class LabelReference {
                private int target = -1;
              }

              private record PendingToastClause(int value) {}

              private record PendingToastHandlerGroup(int value) {}

              private record PendingToastFinally(int value) {}

              private record PendingToastExitTarget(int value) {}

              private record ActiveLoopTarget(int value) {}

              private sealed interface PendingStructuralEntry
                  permits PendingCatchGroup,
                      PendingProtectedFinally,
                      PendingFinallyContinuation,
                      PendingCollectionLoop,
                      PendingRangeLoop {}

              private record PendingCatchGroup(int value) implements PendingStructuralEntry {}

              private record PendingProtectedFinally(int value) implements PendingStructuralEntry {}

              private record PendingFinallyContinuation(int value)
                  implements PendingStructuralEntry {}

              private record PendingCollectionLoop(int value) implements PendingStructuralEntry {}

              private record PendingRangeLoop(int value) implements PendingStructuralEntry {}

              private record VectorLayout(int value) {}

              private record ProgramLayout(int value) {}

              private static final class GlobalState {
                private int value;
              }

              private record LiteralKey(int value) {}

              private static final class VectorBuilder {
                // unique vector body sentinel
                private final PendingCall call = new PendingCall(23);

                int vectorSentinel() {
                  return referenceWidth(call.value())
                      + (isBodyDescendant(1, 3, 1) ? UnitKind.BYTE.ordinal() : 0);
                }
              }

              private static int referenceWidth(int maximum) {
                return maximum < 256 ? 1 : 2;
              }

              private static final class LayoutCompiler {
                // unique compiler body sentinel
                int compilerSentinel() {
                  return handlerControl()
                      + iterateControl()
                      + exitControl()
                      + uniqueControl()
                      + new IndexedControl(5).value();
                }
              }
            }
            """,
            """
            package world.mongoose.banteng.persistence;

            public final class ToastV17ProgramLayout {
              int facadeSentinel() {
                return 17;
              }

              private static boolean isBodyDescendant(int owner, int descendant, int component) {
                return owner + component < descendant;
              }

              private record IndexedControl(int value) {}
            }
            """,
            source -> source.path(FACADE_PATH)),
        java(
            null,
            """
            package world.mongoose.banteng.bytecode;

            public final class LayoutCompiler {
              enum UnitKind {
                BYTE
              }

              record PendingCall(int value) {}

              static final class LabelReference {
                int target = -1;
              }

              record PendingToastClause(int value) {}

              record PendingToastHandlerGroup(int value) {}

              record PendingToastFinally(int value) {}

              record PendingToastExitTarget(int value) {}

              record ActiveLoopTarget(int value) {}

              sealed interface PendingStructuralEntry
                  permits PendingCatchGroup,
                      PendingProtectedFinally,
                      PendingFinallyContinuation,
                      PendingCollectionLoop,
                      PendingRangeLoop {}

              record PendingCatchGroup(int value) implements PendingStructuralEntry {}

              record PendingProtectedFinally(int value) implements PendingStructuralEntry {}

              record PendingFinallyContinuation(int value)
                  implements PendingStructuralEntry {}

              record PendingCollectionLoop(int value) implements PendingStructuralEntry {}

              record PendingRangeLoop(int value) implements PendingStructuralEntry {}

              record VectorLayout(int value) {}

              record ProgramLayout(int value) {}

              static final class GlobalState {
                int value;
              }

              record LiteralKey(int value) {}

              private record IndexedControl(int value) {}

              private static int handlerControl() {
                return 1;
              }

              private static int iterateControl() {
                return 2;
              }

              private static int exitControl() {
                return 3;
              }

              private static int uniqueControl() {
                return 4;
              }

              static boolean isBodyDescendant(int owner, int descendant, int component) {
                return owner + component < descendant;
              }

              static int referenceWidth(int maximum) {
                return maximum < 256 ? 1 : 2;
              }

              // unique compiler body sentinel
              int compilerSentinel() {
                return handlerControl()
                    + iterateControl()
                    + exitControl()
                    + uniqueControl()
                    + new IndexedControl(5).value();
              }
            }
            """,
            source -> source.path(COMPILER_PATH)),
        java(
            null,
            """
            package world.mongoose.banteng.bytecode;

            import static world.mongoose.banteng.bytecode.LayoutCompiler.isBodyDescendant;
            import static world.mongoose.banteng.bytecode.LayoutCompiler.referenceWidth;

            import world.mongoose.banteng.bytecode.LayoutCompiler.ActiveLoopTarget;
            import world.mongoose.banteng.bytecode.LayoutCompiler.GlobalState;
            import world.mongoose.banteng.bytecode.LayoutCompiler.LabelReference;
            import world.mongoose.banteng.bytecode.LayoutCompiler.LiteralKey;
            import world.mongoose.banteng.bytecode.LayoutCompiler.PendingCall;
            import world.mongoose.banteng.bytecode.LayoutCompiler.PendingCatchGroup;
            import world.mongoose.banteng.bytecode.LayoutCompiler.PendingProtectedFinally;
            import world.mongoose.banteng.bytecode.LayoutCompiler.PendingStructuralEntry;
            import world.mongoose.banteng.bytecode.LayoutCompiler.PendingToastClause;
            import world.mongoose.banteng.bytecode.LayoutCompiler.PendingToastExitTarget;
            import world.mongoose.banteng.bytecode.LayoutCompiler.PendingToastFinally;
            import world.mongoose.banteng.bytecode.LayoutCompiler.PendingToastHandlerGroup;
            import world.mongoose.banteng.bytecode.LayoutCompiler.UnitKind;
            import world.mongoose.banteng.bytecode.LayoutCompiler.VectorLayout;

            final class VectorBuilder {
              // unique vector body sentinel
              private final PendingCall call = new PendingCall(23);

              int vectorSentinel() {
                return referenceWidth(call.value())
                    + (isBodyDescendant(1, 3, 1) ? UnitKind.BYTE.ordinal() : 0);
              }
            }
            """,
            source -> source.path(BUILDER_PATH)));
  }

  @Test
  void refusesAPartialNestedPair() {
    rewriteRun(
        java(
            """
            package world.mongoose.banteng.persistence;

            public final class ToastV17ProgramLayout {
              private static final class LayoutCompiler {
                int compilerSentinel() {
                  return 17;
                }
              }
            }
            """,
            source -> source.path(FACADE_PATH)));
  }

  @Test
  void refusesAnExistingTargetCollision() {
    rewriteRun(
        java(
            """
            package world.mongoose.banteng.persistence;

            public final class ToastV17ProgramLayout {
              private static int handlerControl() { return 1; }
              private static int iterateControl() { return 2; }
              private static int exitControl() { return 3; }
              private static int uniqueControl() { return 4; }
              private static boolean isBodyDescendant(int a, int b, int c) { return true; }
              private static int referenceWidth(int maximum) { return 1; }
              private enum UnitKind { BYTE }
              private record PendingCall() {}
              private static final class LabelReference {}
              private record PendingToastClause() {}
              private record PendingToastHandlerGroup() {}
              private record PendingToastFinally() {}
              private record PendingToastExitTarget() {}
              private record ActiveLoopTarget() {}
              private interface PendingStructuralEntry {}
              private record PendingCatchGroup() {}
              private record PendingProtectedFinally() {}
              private record PendingFinallyContinuation() {}
              private record PendingCollectionLoop() {}
              private record PendingRangeLoop() {}
              private record VectorLayout() {}
              private record ProgramLayout() {}
              private static final class GlobalState {}
              private record LiteralKey() {}
              private record IndexedControl() {}
              private static final class LayoutCompiler {}

              private static final class VectorBuilder {}
            }
            """,
            source -> source.path(FACADE_PATH)),
        java(
            """
            package world.mongoose.banteng.bytecode;

            public final class LayoutCompiler {}
            """,
            source -> source.path(COMPILER_PATH)));
  }

  @Test
  void refusesAnIncompleteCompilerDependencyClosure() {
    rewriteRun(
        java(
            """
            package world.mongoose.banteng.persistence;

            public final class ToastV17ProgramLayout {
              private enum UnitKind {
                BYTE
              }

              private static final class LayoutCompiler {}

              private static final class VectorBuilder {}
            }
            """,
            source -> source.path(FACADE_PATH)));
  }
}
