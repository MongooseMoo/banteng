package moo.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class OpcodeFamilyArchitectureTest {
  private static final Path VM_SOURCE = Path.of("src", "main", "java", "moo", "vm");
  private static final List<String> FAMILIES =
      List.of("ListOps", "PropertyOps", "IndexOps", "ArithmeticOps", "LoopOps", "ErrorOps");

  @Test
  void opcodeFamiliesHaveDedicatedStaticOwners() throws IOException {
    for (String family : FAMILIES) {
      String source = source(family);
      assertTrue(source.contains("final class " + family), family);
      assertTrue(source.contains("private " + family + "()"), family);
    }
  }

  @Test
  void rawOpcodeIsReadAndDispatchedExactlyOnce() throws IOException {
    String mooVm = source("MooVm");
    String allVmSources = String.join("\n", FAMILIES.stream().map(this::sourceUnchecked).toList());

    assertEquals(1, occurrences(mooVm, "instruction.opcode()"));
    assertEquals(0, occurrences(allVmSources, "instruction.opcode()"));
    assertTrue(mooVm.contains("BytecodeProgram.Opcode opcode = instruction.opcode();"));
    assertTrue(mooVm.contains("switch (opcode)"));
  }

  @Test
  void opcodeFamiliesSwitchExhaustivelyOnNarrowOperationTypes() throws IOException {
    for (String family : FAMILIES.subList(0, FAMILIES.size() - 1)) {
      String source = source(family);
      assertTrue(source.contains("enum Operation"), family);
      assertTrue(source.contains("return switch (operation)"), family);
      assertFalse(source.contains("default ->"), family);
      assertFalse(source.contains("execute(\n      BytecodeProgram.Opcode"), family);
    }
  }

  @Test
  void movedConcernsAreAbsentFromMooVm() throws IOException {
    String mooVm = source("MooVm");
    for (String method :
        List.of(
            "buildList(",
            "loadLocal(",
            "index(",
            "arithmetic(",
            "comparison(",
            "iterate(",
            "raiseError(",
            "traceback(")) {
      assertFalse(mooVm.contains("private static void " + method), method);
      assertFalse(mooVm.contains("private void " + method), method);
      assertFalse(mooVm.contains("private static ListValue " + method), method);
    }
  }

  @Test
  void comparisonsShareOneThreeWayResultMapping() throws IOException {
    String arithmetic = source("ArithmeticOps");

    assertTrue(arithmetic.contains("private static boolean comparisonResult("));
    assertEquals(1, occurrences(arithmetic, "comparisonResult(operation, comparison)"));
    assertEquals(1, occurrences(arithmetic, "left.compareIgnoringCase(right)"));
  }

  private String sourceUnchecked(String name) {
    try {
      return source(name);
    } catch (IOException failure) {
      throw new AssertionError(failure);
    }
  }

  private static String source(String name) throws IOException {
    return Files.readString(VM_SOURCE.resolve(name + ".java"));
  }

  private static int occurrences(String source, String text) {
    int count = 0;
    int offset = 0;
    while ((offset = source.indexOf(text, offset)) >= 0) {
      count++;
      offset += text.length();
    }
    return count;
  }
}
