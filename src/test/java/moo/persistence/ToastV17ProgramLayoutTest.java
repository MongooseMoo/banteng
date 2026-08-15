package moo.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.BytecodeProgram.AstPath;
import moo.bytecode.BytecodeProgram.HandlerSpec;
import moo.bytecode.BytecodeProgram.Instruction;
import moo.bytecode.BytecodeProgram.Opcode;
import moo.bytecode.MooCompiler;
import org.junit.jupiter.api.Test;

final class ToastV17ProgramLayoutTest {
  @Test
  void exposesMultiarmCatchSourceOrderDepthsAndBantengControls() {
    String source =
        """
        try
          suspend();
        except allErrors (ANY)
        except selected (E_TYPE, E_INVARG)
        endtry
        """;
    ToastV17ProgramLayout.StructuralStackShape shape = structuralShape(source, -1, 0);
    ToastV17ProgramLayout.CatchGroup group =
        assertInstanceOf(
            ToastV17ProgramLayout.CatchGroup.class, shape.entriesBaseToTop().getFirst());

    assertEquals(5, shape.postArgumentDepth());
    assertEquals(0, group.baseDepth());
    assertEquals(4, group.markerDepth().orElseThrow());
    assertEquals(ToastV17ProgramLayout.StructuralPhase.PROTECTED, group.phase());
    assertTrue(group.activeClauseIndex().isEmpty());
    assertEquals(2, group.clauses().size());
    assertEquals(
        new ToastV17ProgramLayout.ToastErrorSelector(true, List.of()),
        group.clauses().get(0).selector());
    assertEquals(
        new ToastV17ProgramLayout.ToastErrorSelector(false, List.of("E_TYPE", "E_INVARG")),
        group.clauses().get(1).selector());
    assertEquals(2, group.clauseControls().size());
    assertEquals(group.ownerPath(), group.ownerControl().astPath());
    assertEquals(
        group.clauses().stream().map(ToastV17ProgramLayout.ToastHandlerClause::astPath).toList(),
        group.clauseControls().stream()
            .map(ToastV17ProgramLayout.BantengHandlerControl::astPath)
            .toList());
  }

  @Test
  void distinguishesStatementClauseAndExpressionFallbackWithoutRawCatchMarkers() {
    String clauseSource =
        """
        try
          0;
        except problem (E_TYPE)
          suspend();
        endtry
        """;
    ToastV17ProgramLayout.StructuralStackShape clauseShape =
        structuralShape(clauseSource, -1, 0);
    ToastV17ProgramLayout.CatchGroup clauseGroup =
        assertInstanceOf(
            ToastV17ProgramLayout.CatchGroup.class,
            clauseShape.entriesBaseToTop().getFirst());

    assertEquals(0, clauseShape.postArgumentDepth());
    assertEquals(ToastV17ProgramLayout.StructuralPhase.EXCEPT_CLAUSE, clauseGroup.phase());
    assertEquals(0, clauseGroup.activeClauseIndex().orElseThrow());
    assertTrue(clauseGroup.markerDepth().isEmpty());

    String fallbackSource = "return `1 / 0 ! E_DIV => suspend()';\n";
    ToastV17ProgramLayout.StructuralStackShape fallbackShape =
        structuralShape(fallbackSource, -1, 0);
    ToastV17ProgramLayout.CatchGroup fallbackGroup =
        assertInstanceOf(
            ToastV17ProgramLayout.CatchGroup.class,
            fallbackShape.entriesBaseToTop().getFirst());

    assertEquals(0, fallbackShape.postArgumentDepth());
    assertEquals(
        ToastV17ProgramLayout.StructuralPhase.EXPRESSION_FALLBACK,
        fallbackGroup.phase());
    assertEquals(0, fallbackGroup.activeClauseIndex().orElseThrow());
    assertTrue(fallbackGroup.markerDepth().isEmpty());
    assertEquals(1, fallbackGroup.clauseControls().size());
  }

  @Test
  void distinguishesProtectedFinallyFromFinallyHandlerContinuation() {
    String source =
        """
        try
          suspend();
        finally
          suspend();
        endtry
        """;
    ToastV17ProgramLayout.StructuralStackShape protectedShape =
        structuralShape(source, -1, 0);
    ToastV17ProgramLayout.ProtectedFinally protectedFinally =
        assertInstanceOf(
            ToastV17ProgramLayout.ProtectedFinally.class,
            protectedShape.entriesBaseToTop().getFirst());
    ToastV17ProgramLayout.StructuralStackShape handlerShape =
        structuralShape(source, -1, 1);
    ToastV17ProgramLayout.FinallyContinuation continuation =
        assertInstanceOf(
            ToastV17ProgramLayout.FinallyContinuation.class,
            handlerShape.entriesBaseToTop().getFirst());

    assertEquals(1, protectedShape.postArgumentDepth());
    assertEquals(0, protectedFinally.baseDepth());
    assertEquals(0, protectedFinally.markerDepth());
    assertEquals(2, handlerShape.postArgumentDepth());
    assertEquals(0, continuation.reasonDepth());
    assertEquals(1, continuation.valueDepth());
    assertEquals(
        ToastV17ProgramLayout.StructuralPhase.FINALLY_HANDLER, continuation.phase());
    assertEquals(protectedFinally.ownerPath(), continuation.ownerPath());
    assertEquals(protectedFinally.ownerControl(), continuation.ownerControl());
    assertEquals(List.of(), continuation.exitTargets());
  }

  @Test
  void mapsAndInvertsBreakAndContinueFinExitTargets() {
    String source =
        """
        while outer (1)
          try
            if (args)
              break outer;
            else
              continue outer;
            endif
          finally
            suspend();
          endtry
        endwhile
        """;
    ToastV17ProgramLayout.FinallyContinuation continuation =
        assertInstanceOf(
            ToastV17ProgramLayout.FinallyContinuation.class,
            structuralShape(source, -1, 0).entriesBaseToTop().getFirst());

    assertEquals(2, continuation.exitTargets().size());
    ToastV17ProgramLayout.ToastExitTarget broken =
        continuation.exitTargets().stream()
            .filter(target -> target.bantengControl().action() == ToastV17ProgramLayout.ExitAction.BREAK)
            .findFirst()
            .orElseThrow();
    ToastV17ProgramLayout.ToastExitTarget continued =
        continuation.exitTargets().stream()
            .filter(target -> target.bantengControl().action() == ToastV17ProgramLayout.ExitAction.CONTINUE)
            .findFirst()
            .orElseThrow();
    assertEquals(0, broken.targetStackDepth());
    assertEquals(0, continued.targetStackDepth());
    assertEquals(0, broken.bantengOperandDepth());
    assertEquals(0, continued.bantengOperandDepth());
    assertTrue(broken.targetProgramCounter() != continued.targetProgramCounter());
    for (ToastV17ProgramLayout.ToastExitTarget target : continuation.exitTargets()) {
      assertEquals(
          target,
          continuation.resolveToastExitTarget(
              target.targetStackDepth(), target.targetProgramCounter()));
      assertEquals(
          target,
          continuation.resolveBantengExitTarget(
              target.bantengOperandDepth(),
              target.bantengControl().targetInstructionPointer()));
    }
  }

  @Test
  void mapsLabeledOuterAndInnerLoopExitsAcrossFinally() {
    String source =
        """
        for outer in ({1})
          for inner in ({2})
            try
              if (args)
                break outer;
              elseif (argstr)
                continue outer;
              else
                break inner;
              endif
            finally
              suspend();
            endtry
          endfor
        endfor
        """;
    ToastV17ProgramLayout.StructuralStackShape shape = structuralShape(source, -1, 0);
    ToastV17ProgramLayout.CollectionLoop outer =
        assertInstanceOf(
            ToastV17ProgramLayout.CollectionLoop.class, shape.entriesBaseToTop().get(0));
    ToastV17ProgramLayout.CollectionLoop inner =
        assertInstanceOf(
            ToastV17ProgramLayout.CollectionLoop.class, shape.entriesBaseToTop().get(1));
    ToastV17ProgramLayout.FinallyContinuation continuation =
        assertInstanceOf(
            ToastV17ProgramLayout.FinallyContinuation.class,
            shape.entriesBaseToTop().get(2));

    assertEquals(3, continuation.exitTargets().size());
    ToastV17ProgramLayout.ToastExitTarget breakOuter =
        findExit(continuation, ToastV17ProgramLayout.ExitAction.BREAK, outer.ownerPath());
    ToastV17ProgramLayout.ToastExitTarget continueOuter =
        findExit(continuation, ToastV17ProgramLayout.ExitAction.CONTINUE, outer.ownerPath());
    ToastV17ProgramLayout.ToastExitTarget breakInner =
        findExit(continuation, ToastV17ProgramLayout.ExitAction.BREAK, inner.ownerPath());
    assertEquals(0, breakOuter.targetStackDepth());
    assertEquals(2, continueOuter.targetStackDepth());
    assertEquals(2, breakInner.targetStackDepth());
    assertEquals(0, breakOuter.bantengOperandDepth());
    assertEquals(0, continueOuter.bantengOperandDepth());
    assertEquals(0, breakInner.bantengOperandDepth());
  }

  @Test
  void propagatesOneFinExitMappingThroughNestedFinallyAndSeparatesReturn() {
    String exitSource =
        """
        while outer (1)
          try
            try
              break outer;
            finally
              suspend();
            endtry
          finally
            suspend();
          endtry
        endwhile
        """;
    ToastV17ProgramLayout.FinallyContinuation inner =
        assertInstanceOf(
            ToastV17ProgramLayout.FinallyContinuation.class,
            structuralShape(exitSource, -1, 0).entriesBaseToTop().getLast());
    ToastV17ProgramLayout.FinallyContinuation outer =
        assertInstanceOf(
            ToastV17ProgramLayout.FinallyContinuation.class,
            structuralShape(exitSource, -1, 1).entriesBaseToTop().getLast());
    assertEquals(1, inner.exitTargets().size());
    assertEquals(inner.exitTargets(), outer.exitTargets());

    String returnSource =
        """
        try
          return 1;
        finally
          suspend();
        endtry
        """;
    ToastV17ProgramLayout.FinallyContinuation returned =
        assertInstanceOf(
            ToastV17ProgramLayout.FinallyContinuation.class,
            structuralShape(returnSource, -1, 0).entriesBaseToTop().getFirst());
    assertEquals(List.of(), returned.exitTargets());
  }

  @Test
  void resolvesFinExitInsideHierarchicalForkWithWidenedToastLabels() {
    String source =
        "fork (0)\n  fork (0)\n"
            + "    0;\n".repeat(130)
            + "    while loop (1)\n"
            + "      try\n        break loop;\n"
            + "      finally\n        suspend();\n      endtry\n"
            + "    endwhile\n  endfork\nendfork\n";
    ToastV17ProgramLayout layout = new ToastV17ProgramLayout();
    ToastV17ProgramLayout.CallBoundary boundary = layout.callBoundaries(source, 0).getFirst();
    ToastV17ProgramLayout.StructuralStackShape shape =
        layout.resolveStructuralStack(source, 0, new MooCompiler().compile(source), boundary);
    ToastV17ProgramLayout.FinallyContinuation continuation =
        assertInstanceOf(
            ToastV17ProgramLayout.FinallyContinuation.class,
            shape.entriesBaseToTop().getLast());
    ToastV17ProgramLayout.ToastExitTarget target = continuation.exitTargets().getFirst();

    assertTrue(target.targetProgramCounter() > 256);
    assertEquals(
        target,
        continuation.resolveToastExitTarget(
            target.targetStackDepth(), target.targetProgramCounter()));
    assertEquals(
        target,
        continuation.resolveBantengExitTarget(
            target.bantengOperandDepth(),
            target.bantengControl().targetInstructionPointer()));
  }

  @Test
  void ordersMixedNestedStructuresByExactBaseToTopInterleaving() {
    String source =
        """
        try
          for item in ({1})
            try
              suspend();
            except problem (E_TYPE)
            endtry
          endfor
        finally
          0;
        endtry
        """;
    ToastV17ProgramLayout.StructuralStackShape shape = structuralShape(source, -1, 0);

    assertEquals(6, shape.postArgumentDepth());
    assertEquals(3, shape.entriesBaseToTop().size());
    ToastV17ProgramLayout.ProtectedFinally outer =
        assertInstanceOf(
            ToastV17ProgramLayout.ProtectedFinally.class, shape.entriesBaseToTop().get(0));
    ToastV17ProgramLayout.CollectionLoop loop =
        assertInstanceOf(
            ToastV17ProgramLayout.CollectionLoop.class, shape.entriesBaseToTop().get(1));
    ToastV17ProgramLayout.CatchGroup inner =
        assertInstanceOf(
            ToastV17ProgramLayout.CatchGroup.class, shape.entriesBaseToTop().get(2));
    assertEquals(0, outer.baseDepth());
    assertEquals(1, loop.baseDepth());
    assertEquals(2, loop.iteratorDepth());
    assertEquals(3, inner.baseDepth());
    assertEquals(5, inner.markerDepth().orElseThrow());
  }

  @Test
  void resolvesNestedHandlerGroupsInToastRuntimeStackOrder() {
    String source =
        """
        try
          try
            suspend();
          except inner (E_TYPE)
          endtry
        except outer (ANY)
        endtry
        """;
    ToastV17ProgramLayout layout = new ToastV17ProgramLayout();
    ToastV17ProgramLayout.CallBoundary boundary = layout.callBoundaries(source, -1).getFirst();
    ToastV17ProgramLayout.ToastControlLabels controls =
        layout.resolveToastControls(source, -1, boundary.astPath());

    assertEquals(2, controls.handlersOuterToInner().size());
    ToastV17ProgramLayout.ToastHandlerGroup outer = controls.handlersOuterToInner().get(0);
    ToastV17ProgramLayout.ToastHandlerGroup inner = controls.handlersOuterToInner().get(1);
    assertTrue(outer.ownerPath().components().size() < inner.ownerPath().components().size());
    assertEquals(new ToastV17ProgramLayout.ToastErrorSelector(true, List.of()),
        outer.clauses().getFirst().selector());
    assertEquals(new ToastV17ProgramLayout.ToastErrorSelector(false, List.of("E_TYPE")),
        inner.clauses().getFirst().selector());
    assertTrue(inner.clauses().getFirst().handlerLabelProgramCounter()
        < outer.clauses().getFirst().handlerLabelProgramCounter());
    assertEquals(outer, layout.resolveToastHandlerGroup(source, -1, outer.ownerPath()));
    assertEquals(inner, layout.resolveToastHandlerGroup(source, -1, inner.ownerPath()));
    assertEquals(List.of(), controls.finallyLabelsOuterToInner());
  }

  @Test
  void exposesCollectionKindsVariableAritiesAndPostVerbCallDepth() {
    List<CollectionCase> cases =
        List.of(
            new CollectionCase("{1, 2}", "value", ToastV17ProgramLayout.CollectionKind.LIST),
            new CollectionCase("{1, 2}", "value, index", ToastV17ProgramLayout.CollectionKind.LIST),
            new CollectionCase("\"ab\"", "value", ToastV17ProgramLayout.CollectionKind.STRING),
            new CollectionCase("\"ab\"", "value, index", ToastV17ProgramLayout.CollectionKind.STRING),
            new CollectionCase("[1 -> 2]", "value", ToastV17ProgramLayout.CollectionKind.MAP),
            new CollectionCase("[1 -> 2]", "value, key", ToastV17ProgramLayout.CollectionKind.MAP));

    for (CollectionCase expected : cases) {
      String source =
          "for "
              + expected.variables()
              + " in ("
              + expected.expression()
              + ")\n  #0:probe(1);\nendfor\n";
      ToastV17ProgramLayout.StructuralStackShape shape = structuralShape(source, -1, 0);
      ToastV17ProgramLayout.CollectionLoop loop =
          assertInstanceOf(
              ToastV17ProgramLayout.CollectionLoop.class,
              shape.entriesBaseToTop().getFirst(),
              source);

      assertEquals(2, shape.postArgumentDepth(), source);
      assertEquals(0, loop.baseDepth(), source);
      assertEquals(1, loop.iteratorDepth(), source);
      assertEquals(List.of(expected.variables().split(", ")), loop.variableNames(), source);
      assertEquals(expected.kind(), loop.staticallyKnownKind().orElseThrow(), source);
      assertEquals(Opcode.ITERATE, loop.control().opcode(), source);
    }
  }

  @Test
  void exposesIntegerAndObjectRangeDepthsAndTwoNestedLoops() {
    for (RangeCase expected :
        List.of(
            new RangeCase("1", "3", ToastV17ProgramLayout.RangeKind.INTEGER),
            new RangeCase("#1", "#3", ToastV17ProgramLayout.RangeKind.OBJECT))) {
      String source =
          "for value in ["
              + expected.start()
              + ".."
              + expected.end()
              + "]\n  suspend();\nendfor\n";
      ToastV17ProgramLayout.StructuralStackShape shape = structuralShape(source, -1, 0);
      ToastV17ProgramLayout.RangeLoop loop =
          assertInstanceOf(
              ToastV17ProgramLayout.RangeLoop.class,
              shape.entriesBaseToTop().getFirst(),
              source);

      assertEquals(2, shape.postArgumentDepth(), source);
      assertEquals(0, loop.nextDepth(), source);
      assertEquals(1, loop.endDepth(), source);
      assertEquals(expected.kind(), loop.staticallyKnownKind().orElseThrow(), source);
      assertEquals(Opcode.ITERATE_RANGE, loop.control().opcode(), source);
    }

    String nested =
        """
        for outer in ({1})
          for inner in [#1..#3]
            suspend();
          endfor
        endfor
        """;
    ToastV17ProgramLayout.StructuralStackShape nestedShape = structuralShape(nested, -1, 0);
    assertEquals(4, nestedShape.postArgumentDepth());
    assertEquals(2, nestedShape.entriesBaseToTop().size());
    ToastV17ProgramLayout.CollectionLoop outer =
        assertInstanceOf(
            ToastV17ProgramLayout.CollectionLoop.class,
            nestedShape.entriesBaseToTop().get(0));
    ToastV17ProgramLayout.RangeLoop inner =
        assertInstanceOf(
            ToastV17ProgramLayout.RangeLoop.class,
            nestedShape.entriesBaseToTop().get(1));
    assertEquals(0, outer.baseDepth());
    assertEquals(1, outer.iteratorDepth());
    assertEquals(2, inner.nextDepth());
    assertEquals(3, inner.endDepth());
  }

  @Test
  void resolvesHandlerLabelsInToastFlatNestedForkVector() {
    String source =
        """
        fork (0)
          fork (0)
            try
              suspend();
            except problem (E_TYPE, E_INVARG)
            endtry
          endfork
        endfork
        """;
    ToastV17ProgramLayout layout = new ToastV17ProgramLayout();
    ToastV17ProgramLayout.CallBoundary boundary = layout.callBoundaries(source, 0).getFirst();
    ToastV17ProgramLayout.ToastControlLabels controls =
        layout.resolveToastControls(source, 0, boundary.astPath());

    assertEquals(1, controls.handlersOuterToInner().size());
    ToastV17ProgramLayout.ToastHandlerGroup group = controls.handlersOuterToInner().getFirst();
    assertEquals(List.of("E_TYPE", "E_INVARG"), group.clauses().getFirst().selector().errors());
    assertEquals(group, layout.resolveToastHandlerGroup(source, 0, group.ownerPath()));

    ToastV17ProgramLayout.StructuralStackShape shape =
        layout.resolveStructuralStack(source, 0, new MooCompiler().compile(source), boundary);
    ToastV17ProgramLayout.CatchGroup structuralGroup =
        assertInstanceOf(
            ToastV17ProgramLayout.CatchGroup.class, shape.entriesBaseToTop().getFirst());
    assertEquals(group.ownerPath(), structuralGroup.ownerPath());
    assertEquals(group.clauses(), structuralGroup.clauses());
    assertEquals(3, shape.postArgumentDepth());
  }

  @Test
  void handlerTargetsUseTheFinalToastLabelWidth() {
    ToastV17ProgramLayout layout = new ToastV17ProgramLayout();
    String oneByteLabels = handlerThresholdSource(120);
    String twoByteLabels = handlerThresholdSource(121);
    ToastV17ProgramLayout.CallBoundary oneByteCall =
        layout.callBoundaries(oneByteLabels, -1).getFirst();
    ToastV17ProgramLayout.CallBoundary twoByteCall =
        layout.callBoundaries(twoByteLabels, -1).getFirst();

    assertEquals(
        254,
        layout.resolveToastControls(oneByteLabels, -1, oneByteCall.astPath())
            .handlersOuterToInner().getFirst().clauses().getFirst()
            .handlerLabelProgramCounter());
    assertEquals(
        258,
        layout.resolveToastControls(twoByteLabels, -1, twoByteCall.astPath())
            .handlersOuterToInner().getFirst().clauses().getFirst()
            .handlerLabelProgramCounter());

    ToastV17ProgramLayout.CatchGroup oneByteShape =
        assertInstanceOf(
            ToastV17ProgramLayout.CatchGroup.class,
            structuralShape(oneByteLabels, -1, 0).entriesBaseToTop().getFirst());
    ToastV17ProgramLayout.CatchGroup twoByteShape =
        assertInstanceOf(
            ToastV17ProgramLayout.CatchGroup.class,
            structuralShape(twoByteLabels, -1, 0).entriesBaseToTop().getFirst());
    assertEquals(254, oneByteShape.clauses().getFirst().handlerLabelProgramCounter());
    assertEquals(258, twoByteShape.clauses().getFirst().handlerLabelProgramCounter());
    assertEquals(2, oneByteShape.markerDepth().orElseThrow());
    assertEquals(2, twoByteShape.markerDepth().orElseThrow());
  }

  @Test
  void exposesFinallyAsItsDistinctExactToastControl() {
    String source =
        """
        try
          suspend();
        finally
          0;
        endtry
        """;
    ToastV17ProgramLayout layout = new ToastV17ProgramLayout();
    ToastV17ProgramLayout.CallBoundary boundary = layout.callBoundaries(source, -1).getFirst();
    ToastV17ProgramLayout.ToastControlLabels controls =
        layout.resolveToastControls(source, -1, boundary.astPath());

    assertEquals(List.of(), controls.handlersOuterToInner());
    assertEquals(1, controls.finallyLabelsOuterToInner().size());
    ToastV17ProgramLayout.ToastFinallyLabel label = controls.finallyLabelsOuterToInner().getFirst();
    assertEquals(9, label.handlerLabelProgramCounter());
    assertEquals(label, layout.resolveToastFinallyLabel(source, -1, label.ownerPath()));
  }

  @Test
  void resolvesNestedTryInsideForAndRejectsAbsentOrAmbiguousControls() {
    String source =
        """
        for item in ({1, 2})
          try
            suspend();
          except caught (ANY)
          endtry
        endfor
        """;
    ToastV17ProgramLayout layout = new ToastV17ProgramLayout();
    ToastV17ProgramLayout.CallBoundary boundary = layout.callBoundaries(source, -1).getFirst();
    BytecodeProgram compiled = new MooCompiler().compile(source);

    ToastV17ProgramLayout.ContinuationSite site =
        layout.resolveContinuation(compiled, boundary, 2, true);

    assertEquals(2, site.handlers().size());
    assertTrue(site.handlers().getFirst().specification().structuredCatchBinding());
    assertEquals(false, site.handlers().getLast().specification().structuredCatchBinding());
    assertEquals(Opcode.ITERATE, site.iterate().orElseThrow().opcode());

    String unguardedSource = "suspend();\n";
    ToastV17ProgramLayout.CallBoundary unguardedBoundary =
        layout.callBoundaries(unguardedSource, -1).getFirst();
    BytecodeProgram unguarded = new MooCompiler().compile(unguardedSource);
    assertThrows(
        IllegalArgumentException.class,
        () -> layout.resolveContinuation(unguarded, unguardedBoundary, 1, false));

    AstPath ownerPath = new AstPath(List.of(0));
    AstPath callPath = ownerPath.child(0, 0, 0);
    HandlerSpec owner =
        new HandlerSpec(-1, java.util.Optional.empty(), false, List.of(), false, -1, 4);
    BytecodeProgram ambiguous =
        new BytecodeProgram(
            List.of(
                new Instruction(owner, ownerPath),
                new Instruction(owner, ownerPath),
                new Instruction(Opcode.CALL, "suspend", callPath),
                new Instruction(Opcode.RETURN)));
    ToastV17ProgramLayout.CallBoundary ambiguousBoundary =
        new ToastV17ProgramLayout.CallBoundary(
            -1, 0, 2, callPath, ToastV17ProgramLayout.CallKind.BUILTIN);
    assertThrows(
        IllegalArgumentException.class,
        () -> layout.resolveContinuation(ambiguous, ambiguousBoundary, 1, false));
  }

  @Test
  void resolvesToastFlatNestedForkVectorIntoBantengHierarchicalOwner() {
    String source = "fork (0)\nfork (0)\nsuspend();\nendfork\nendfork\n";
    ToastV17ProgramLayout layout = new ToastV17ProgramLayout();
    ToastV17ProgramLayout.CallBoundary boundary = layout.callBoundaries(source, 0).getFirst();
    BytecodeProgram compiled = new MooCompiler().compile(source);
    BytecodeProgram nestedOwner = compiled.forkVectors().getFirst().forkVectors().getFirst();

    ToastV17ProgramLayout.BantengCallSite call =
        layout.locateBantengCall(compiled, boundary.astPath());

    assertSame(nestedOwner, call.program());
    assertEquals(1, call.callInstructionIndex());
    assertEquals(2, call.resumeInstructionPointer());
  }

  @Test
  void stackOperandWideningDoesNotChangePinnedLabelWidthDecision() {
    StringBuilder source = new StringBuilder();
    source.append('{');
    for (int index = 0; index < 256; index++) {
      if (index != 0) {
        source.append(", ");
      }
      source.append('0');
    }
    source.append("};\nif (0)\nendif\n");
    for (int index = 0; index < 12_000; index++) {
      source.append("args[^];\n");
    }
    source.append("suspend();\n");

    ToastV17ProgramLayout.CallBoundary boundary =
        new ToastV17ProgramLayout().callBoundaries(source.toString(), -1).getFirst();

    assertEquals(72_525, boundary.errorProgramCounter());
    assertEquals(72_527, boundary.programCounter());
  }

  private static String handlerThresholdSource(int statements) {
    StringBuilder source = new StringBuilder("try\n");
    source.append("  0;\n".repeat(statements));
    source.append("  suspend();\nexcept (ANY)\nendtry\n");
    return source.toString();
  }

  private static ToastV17ProgramLayout.StructuralStackShape structuralShape(
      String source, int vector, int callIndex) {
    ToastV17ProgramLayout layout = new ToastV17ProgramLayout();
    ToastV17ProgramLayout.CallBoundary boundary =
        layout.callBoundaries(source, vector).get(callIndex);
    return layout.resolveStructuralStack(
        source, vector, new MooCompiler().compile(source), boundary);
  }

  private static ToastV17ProgramLayout.ToastExitTarget findExit(
      ToastV17ProgramLayout.FinallyContinuation continuation,
      ToastV17ProgramLayout.ExitAction action,
      AstPath targetLoopPath) {
    return continuation.exitTargets().stream()
        .filter(target -> target.bantengControl().action() == action)
        .filter(target -> target.bantengControl().targetLoopPath().equals(targetLoopPath))
        .findFirst()
        .orElseThrow();
  }

  private record CollectionCase(
      String expression, String variables, ToastV17ProgramLayout.CollectionKind kind) {}

  private record RangeCase(
      String start, String end, ToastV17ProgramLayout.RangeKind kind) {}
}
