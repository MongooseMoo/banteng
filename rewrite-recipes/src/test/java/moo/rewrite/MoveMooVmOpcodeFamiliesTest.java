package moo.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

final class MoveMooVmOpcodeFamiliesTest {
  @Test
  void promotesRealInstanceArithmeticBodiesAndTheirDependencyClosure() {
    Map<String, String> rewritten = rewrite(completeMooVmSource());

    assertEquals(
        List.of(
            "ArithmeticOps.java",
            "ErrorOps.java",
            "IndexOps.java",
            "ListOps.java",
            "LoopOps.java",
            "MooVm.java",
            "PropertyOps.java"),
        rewritten.keySet().stream().sorted().toList());
    String mooVm = rewritten.get("MooVm.java");
    String arithmetic = rewritten.get("ArithmeticOps.java");
    assertFalse(mooVm.contains("void arithmetic("));
    assertTrue(
        mooVm.contains(
            "ArithmeticOps.arithmetic(instruction, frame, state, world, valueSemantics)"));
    assertTrue(
        mooVm.contains(
            "ArithmeticOps.comparison(instruction, frame, state, world, valueSemantics)"));
    assertTrue(mooVm.contains("ListOps.buildList(frame, 1)"));
    assertTrue(mooVm.contains("ErrorOps.raiseError(state, ErrorValue.E_TYPE, world)"));

    assertTrue(arithmetic.contains("static void arithmetic("));
    assertTrue(arithmetic.contains("ValueSemantics valueSemantics"));
    assertTrue(arithmetic.contains("valueSemantics.promoteNumbers()"));
    assertTrue(arithmetic.contains("static long integerPower("));
    assertTrue(arithmetic.contains("ErrorOps.raiseError(state, ErrorValue.E_TYPE, world)"));
    assertTrue(arithmetic.contains("static boolean mooEquals("));
    assertTrue(
        arithmetic.contains(
            "ArithmeticOps.mooEquals(frame.left, frame.right, valueSemantics)"));
    assertTrue(rewritten.get("LoopOps.java").contains("record CollectionElement("));
  }

  @Test
  void failsClosedForAnIncompleteDependencyClosureOrUnexpectedOverload() {
    String complete = completeMooVmSource();
    String missingDependency =
        complete.replace("private static long integerPower(", "private static long omittedPower(");
    String unexpectedOverload =
        complete.replace(
            "private static void raiseError(VmState state, ErrorValue error, WorldTxn world) {}",
            "private static void raiseError(VmState state, ErrorValue error, WorldTxn world) {}\n"
                + "  private static void raiseError(VmState state, ErrorValue error) {}");

    assertTrue(rewrite(missingDependency).isEmpty());
    assertTrue(rewrite(unexpectedOverload).isEmpty());
  }

  @Test
  void leavesNonMooVmSourcesAndExistingFamilyFilesUntouched() {
    String unrelated = "package sample; final class MooVm { private static void index() {} }";
    String candidate =
        "package moo.vm; final class MooVm { private static void buildList() {} }";
    String collision = "package moo.vm; final class ListOps {}";
    InMemoryExecutionContext context =
        new InMemoryExecutionContext(
            failure -> {
              throw new AssertionError(failure);
            });
    List<SourceFile> sources =
        JavaParser.fromJavaVersion()
            .build()
            .parse(context, unrelated, candidate, collision)
            .toList();

    List<Result> results =
        new MoveMooVmOpcodeFamilies()
            .run(new InMemoryLargeSourceSet(sources), context)
            .getChangeset()
            .getAllResults();

    assertTrue(results.isEmpty());
  }

  @Test
  void declarativeRecipeActivatesTheExecutableMove() throws Exception {
    String yaml = Files.readString(Path.of("..", "rewrite.yml"));
    assertTrue(yaml.contains("name: moo.vm.DecomposeMooVmOpcodeFamilies"));
    assertTrue(yaml.contains("- moo.rewrite.MoveMooVmOpcodeFamilies"));
  }

  private static Map<String, String> rewrite(String before) {
    InMemoryExecutionContext context =
        new InMemoryExecutionContext(
            failure -> {
              throw new AssertionError(failure);
            });
    List<SourceFile> sources = JavaParser.fromJavaVersion().build().parse(context, before).toList();
    List<Result> results =
        new MoveMooVmOpcodeFamilies()
            .run(new InMemoryLargeSourceSet(sources), context)
            .getChangeset()
            .getAllResults();
    return results.stream()
        .filter(result -> result.getAfter() != null)
        .map(Result::getAfter)
        .collect(
            Collectors.toMap(
                source -> source.getSourcePath().getFileName().toString(),
                SourceFile::printAll));
  }

  private static String completeMooVmSource() {
    return """
        package moo.vm;

        final class MooVm {
          private final ValueSemantics valueSemantics;

          MooVm(ValueSemantics valueSemantics) {
            this.valueSemantics = valueSemantics;
          }

          private void executeInstruction(
              Instruction instruction, Frame frame, VmState state, WorldTxn world) {
            buildList(frame, 1);
            arithmetic(instruction, frame, state, world);
            comparison(instruction, frame, state, world);
            equality(instruction, frame);
            raiseError(state, ErrorValue.E_TYPE, world);
          }

          private static void buildList(Frame frame, int count) {}
          private static void appendList(Frame frame, VmState state, WorldTxn world) {}
          private static void extendList(Frame frame, VmState state, WorldTxn world) {}
          private static void pushCheckedList(
              Frame frame, VmState state, WorldTxn world, ListValue value) {}
          private static void buildMap(Frame frame, VmState state, WorldTxn world, int count) {}
          private static MapValue mapFrom(List keys, List values) { return null; }

          private static void loadLocal(Frame frame, String name, VmState state, WorldTxn world) {}
          private static boolean containsAnonymousOrWaifReference(MooValue value) { return false; }
          private static boolean isFinalStraightLineLocalRead(Frame frame, String name) {
            return false;
          }
          private static void getProperty(Frame frame, VmState state, WorldTxn world) {}
          private static void setProperty(Frame frame, VmState state, WorldTxn world) {}
          private static String normalize(String name) { return name; }

          private static void index(
              Frame frame, VmState state, WorldTxn world, int parentDepth) {}
          private static void boundaryIndex(
              Frame frame, VmState state, WorldTxn world, boolean last) {}
          private static void range(Frame frame, VmState state, WorldTxn world) {}
          private static void setIndexedLocal(
              Frame frame, VmState state, WorldTxn world, String owner, int parentDepth) {}
          private static void dispatchWaifIndexHandler(
              WaifValue waif, String name, ListValue arguments, Frame frame,
              VmState state, WorldTxn world) {}
          private static MooValue replaceIndex(
              MooValue collection, MooValue key, MooValue value, VmState state, WorldTxn world) {
            return null;
          }
          private static void setIndexedProperty(Frame frame, VmState state, WorldTxn world) {}
          private static void setRangeLocal(
              Frame frame, VmState state, WorldTxn world, String owner, int parentDepth) {}

          private void membership(Frame frame, VmState state, WorldTxn world) {
            mooEquals(frame.left, frame.right);
          }
          private static void unaryNegate(Frame frame, VmState state, WorldTxn world) {}
          private static void bitwiseComplement(Frame frame, VmState state, WorldTxn world) {}
          private static void bitwise(
              Instruction instruction, Frame frame, VmState state, WorldTxn world) {}
          private void arithmetic(
              Instruction instruction, Frame frame, VmState state, WorldTxn world) {
            if (valueSemantics.promoteNumbers()) {
              frame.left = promoteInteger(frame.left);
            }
            frame.number = integerPower(2, 3);
            raiseError(state, ErrorValue.E_TYPE, world);
          }
          private static long integerPower(long base, long exponent) { return base + exponent; }
          private void equality(Instruction instruction, Frame frame) {
            frame.result = mooEquals(frame.left, frame.right);
          }
          private boolean mooEquals(MooValue left, MooValue right) {
            return valueSemantics.promoteNumbers()
                ? numericDouble(left) == numericDouble(right)
                : left.equals(right);
          }
          private void comparison(
              Instruction instruction, Frame frame, VmState state, WorldTxn world) {
            double left = numericDouble(frame.left);
            double right = numericDouble(frame.right);
            frame.result =
                switch (instruction.opcode()) {
                  case LESS_THAN -> left < right;
                  case LESS_THAN_OR_EQUAL -> left <= right;
                  case GREATER_THAN -> left > right;
                  case GREATER_THAN_OR_EQUAL -> left >= right;
                };
          }
          private static boolean isMixedIntegerFloat(MooValue left, MooValue right) {
            return false;
          }
          private static MooValue promoteInteger(MooValue value) { return value; }
          private static double numericDouble(MooValue value) { return 0.0; }

          private static void fork(
              Instruction instruction, Frame frame, VmState state, WorldTxn world) {}
          private static void conditionalJump(Instruction instruction, Frame frame, boolean truth) {}
          private static void leaveHandler(Frame frame) {}
          private static void endFinally(VmState state, WorldTxn world) {}
          private static void iterate(
              Instruction instruction, Frame frame, VmState state, WorldTxn world) {}
          private static CollectionElement nextCollectionElement(CollectionCursor cursor) {
            return null;
          }
          private static void iterateRange(
              Instruction instruction, Frame frame, VmState state, WorldTxn world) {}
          private static long scalar(MooValue value) { return 0; }
          private static MooValue scalarValue(RangeKind kind, long value) { return null; }
          private record CollectionElement(MooValue value, MooValue indexOrKey) {}
          private static void scatter(
              Instruction instruction, Frame frame, VmState state, WorldTxn world) {}
          private static void routeReturn(VmState state, MooValue value, WorldTxn world) {}
          private static int target(Instruction instruction) { return 0; }

          private static void raiseError(VmState state, ErrorValue error, WorldTxn world) {}
          private static boolean propagateWorldFailure(
              WorldResult result, VmState state, WorldTxn world) { return false; }
          private static void raiseError(
              VmState state, ErrorValue error, WorldTxn world, boolean advanceInstruction) {}
          private static ListValue exceptionTuple(ErrorValue error, ListValue details) {
            return null;
          }
          private static void raiseError(
              VmState state, ErrorValue error, ListValue details, WorldTxn world) {}
          private static void raiseError(
              VmState state, ErrorValue error, ListValue details, WorldTxn world,
              boolean advanceInstruction) {}
          private static ListValue completeErrorDetails(
              VmState state, ErrorValue error, ListValue details) { return null; }
          private static ListValue traceback(VmState state, ErrorValue error) { return null; }
          private static ListValue tracebackFrame(Frame frame, boolean origin) { return null; }
          private static MooValue tracebackReference(MooValue value) { return value; }
          private static boolean catches(ActiveHandler handler, ErrorValue error) { return false; }
        }
        """;
  }
}
