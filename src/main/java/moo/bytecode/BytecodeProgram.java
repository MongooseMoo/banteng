package moo.bytecode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Immutable bytecode for the executable language slice. */
public record BytecodeProgram(
    List<Instruction> instructions, List<BytecodeProgram> forkVectors, String source) {
  public BytecodeProgram {
    instructions =
        instructions.stream()
            .map(
                instruction ->
                    instruction.sourceLine() == 0
                        ? new Instruction(
                            instruction.opcode(),
                            instruction.operand(),
                            instruction.text(),
                            instruction.handler(),
                            1,
                            instruction.astPath())
                        : instruction)
            .toList();
    forkVectors = List.copyOf(forkVectors);
    Objects.requireNonNull(source, "source");
  }

  /** Creates a manually built program with explicit fork vectors and no source syntax. */
  public BytecodeProgram(List<Instruction> instructions, List<BytecodeProgram> forkVectors) {
    this(instructions, forkVectors, "");
  }

  /** Creates a manually built program with no fork vectors or source syntax. */
  public BytecodeProgram(List<Instruction> instructions) {
    this(instructions, List.of(), "");
  }

  /** Returns the exact one-based source line retained for one instruction. */
  public int sourceLine(int instructionIndex) {
    return instructions.get(instructionIndex).sourceLine();
  }

  /** Returns a stable, line-oriented representation of this program. */
  public String disassemble() {
    StringBuilder text = new StringBuilder();
    for (int index = 0; index < instructions.size(); index++) {
      if (index != 0) {
        text.append('\n');
      }
      Instruction instruction = instructions.get(index);
      text.append(index).append(' ').append(instruction.opcode());
      instruction.operand().ifPresent(operand -> text.append(' ').append(operand));
      instruction.text().ifPresent(operand -> text.append(' ').append(operand));
      instruction.handler().ifPresent(operand -> text.append(' ').append(operand.disassemble()));
    }
    for (int index = 0; index < forkVectors.size(); index++) {
      if (!text.isEmpty()) {
        text.append('\n');
      }
      text.append("fork ").append(index).append(":\n  ");
      text.append(forkVectors.get(index).disassemble().replace("\n", "\n  "));
    }
    return text.toString();
  }

  /** Opcodes implemented by the first executable source slice. */
  public enum Opcode {
    PUSH_INTEGER,
    PUSH_FLOAT,
    PUSH_STRING,
    PUSH_OBJECT,
    PUSH_ERROR,
    BUILD_LIST,
    LIST_APPEND,
    LIST_EXTEND,
    BUILD_MAP,
    LOAD_LOCAL,
    STORE_LOCAL,
    DUP,
    DUP_PAIR,
    POP,
    GET_PROPERTY,
    SET_PROPERTY,
    ENTER_INDEX,
    INDEX,
    RANGE,
    FIRST,
    LAST,
    SET_INDEX_LOCAL,
    SET_INDEX_PROPERTY,
    SET_RANGE_LOCAL,
    CALL,
    CALL_VERB,
    NEGATE,
    NOT,
    COMPLEMENT,
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    REMAINDER,
    POWER,
    BITOR,
    BITAND,
    BITXOR,
    BITSHL,
    BITSHR,
    EQUAL,
    NOT_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    IN,
    FORK,
    JUMP,
    JUMP_IF_FALSE,
    JUMP_IF_TRUE,
    ENTER_HANDLER,
    LEAVE_HANDLER,
    END_FINALLY,
    ITERATE,
    ITERATE_RANGE,
    LEAVE_LOOP,
    SCATTER,
    RETURN
  }

  /** Stable structural path from a parsed program root to one AST node. */
  public record AstPath(List<Integer> components) {
    public AstPath {
      components = List.copyOf(components);
      if (components.isEmpty() || components.stream().anyMatch(component -> component < 0)) {
        throw new IllegalArgumentException("AST path requires nonnegative components");
      }
    }

    /** Returns the path to one structural child of this node. */
    public AstPath child(int... childComponents) {
      List<Integer> nested = new java.util.ArrayList<>(components);
      for (int component : childComponents) {
        if (component < 0) {
          throw new IllegalArgumentException("AST path requires nonnegative components");
        }
        nested.add(component);
      }
      return new AstPath(nested);
    }
  }

  /** One validated instruction and its explicit operands. */
  public record Instruction(
      Opcode opcode,
      OptionalLong operand,
      Optional<String> text,
      Optional<HandlerSpec> handler,
      int sourceLine,
      Optional<AstPath> astPath) {
    public Instruction {
      Objects.requireNonNull(astPath, "astPath");
      if (sourceLine < 0) {
        throw new IllegalArgumentException("source line must not be negative");
      }
      boolean numberRequired =
          switch (opcode) {
            case PUSH_INTEGER,
                PUSH_FLOAT,
                PUSH_OBJECT,
                BUILD_LIST,
                BUILD_MAP,
                JUMP,
                JUMP_IF_FALSE,
                JUMP_IF_TRUE,
                FORK,
                ITERATE,
                ITERATE_RANGE,
                LEAVE_LOOP,
                SCATTER ->
                true;
            default -> false;
          };
      boolean textRequired =
          switch (opcode) {
            case PUSH_STRING,
                PUSH_ERROR,
                LOAD_LOCAL,
                STORE_LOCAL,
                SET_INDEX_LOCAL,
                SET_RANGE_LOCAL,
                CALL,
                ITERATE,
                ITERATE_RANGE,
                SCATTER ->
                true;
            default -> false;
          };
      boolean handlerRequired = opcode == Opcode.ENTER_HANDLER;
      boolean optionalParent =
          opcode == Opcode.INDEX
              || opcode == Opcode.SET_INDEX_LOCAL
              || opcode == Opcode.SET_RANGE_LOCAL;
      if ((!optionalParent && numberRequired != operand.isPresent())
          || ((opcode == Opcode.INDEX || opcode == Opcode.SET_RANGE_LOCAL)
              && operand.isPresent()
              && operand.orElseThrow() != 1)
          || (opcode == Opcode.SET_INDEX_LOCAL
              && operand.isPresent()
              && operand.orElseThrow() < 1)
          || textRequired != text.isPresent()
          || handlerRequired != handler.isPresent()) {
        throw new IllegalArgumentException(opcode + " has invalid operands");
      }
      boolean pathAllowed =
          opcode == Opcode.CALL
              || opcode == Opcode.CALL_VERB
              || opcode == Opcode.JUMP
              || opcode == Opcode.ENTER_HANDLER
              || opcode == Opcode.ITERATE
              || opcode == Opcode.ITERATE_RANGE;
      if (astPath.isPresent() && !pathAllowed) {
        throw new IllegalArgumentException(opcode + " cannot carry an AST path");
      }
    }

    @Override
    public boolean equals(Object other) {
      return this == other
          || (other instanceof Instruction that
              && opcode == that.opcode
              && operand.equals(that.operand)
              && text.equals(that.text)
              && handler.equals(that.handler));
    }

    @Override
    public int hashCode() {
      return Objects.hash(opcode, operand, text, handler);
    }

    /** Creates an instruction with explicit operands before source location is assigned. */
    public Instruction(
        Opcode opcode, OptionalLong operand, Optional<String> text, Optional<HandlerSpec> handler) {
      this(opcode, operand, text, handler, 0, Optional.empty());
    }

    /** Creates an instruction with explicit operands and a retained source line. */
    public Instruction(
        Opcode opcode,
        OptionalLong operand,
        Optional<String> text,
        Optional<HandlerSpec> handler,
        int sourceLine) {
      this(opcode, operand, text, handler, sourceLine, Optional.empty());
    }

    /** Creates an instruction without operands. */
    public Instruction(Opcode opcode) {
      this(opcode, OptionalLong.empty(), Optional.empty(), Optional.empty(), 0);
    }

    /** Creates an instruction with one numeric operand. */
    public Instruction(Opcode opcode, long operand) {
      this(opcode, OptionalLong.of(operand), Optional.empty(), Optional.empty(), 0);
    }

    /** Creates a numeric control instruction with its stable AST path. */
    public Instruction(Opcode opcode, long operand, AstPath astPath) {
      this(
          opcode,
          OptionalLong.of(operand),
          Optional.empty(),
          Optional.empty(),
          0,
          Optional.of(astPath));
    }

    /** Creates an instruction with one text operand. */
    public Instruction(Opcode opcode, String text) {
      this(opcode, OptionalLong.empty(), Optional.of(text), Optional.empty(), 0);
    }

    /** Creates a call instruction with one text operand and its stable AST path. */
    public Instruction(Opcode opcode, String text, AstPath astPath) {
      this(
          opcode,
          OptionalLong.empty(),
          Optional.of(text),
          Optional.empty(),
          0,
          Optional.of(astPath));
    }

    /** Creates an operand-free call instruction with its stable AST path. */
    public Instruction(Opcode opcode, AstPath astPath) {
      this(
          opcode,
          OptionalLong.empty(),
          Optional.empty(),
          Optional.empty(),
          0,
          Optional.of(astPath));
    }

    /** Creates a numeric-and-text control instruction with its stable AST path. */
    public Instruction(Opcode opcode, long operand, String text, AstPath astPath) {
      this(
          opcode,
          OptionalLong.of(operand),
          Optional.of(text),
          Optional.empty(),
          0,
          Optional.of(astPath));
    }

    /** Creates an instruction with numeric and text operands. */
    public Instruction(Opcode opcode, long operand, String text) {
      this(opcode, OptionalLong.of(operand), Optional.of(text), Optional.empty(), 0);
    }

    /** Creates an explicit handler entry instruction. */
    public Instruction(HandlerSpec handler) {
      this(Opcode.ENTER_HANDLER, OptionalLong.empty(), Optional.empty(), Optional.of(handler), 0);
    }

    /** Creates an explicit handler entry instruction with its stable AST path. */
    public Instruction(HandlerSpec handler, AstPath astPath) {
      this(
          Opcode.ENTER_HANDLER,
          OptionalLong.empty(),
          Optional.empty(),
          Optional.of(handler),
          0,
          Optional.of(astPath));
    }
  }

  /** Static targets and error selection for one compiled try statement. */
  public record HandlerSpec(
      int catchTarget,
      Optional<String> catchVariable,
      boolean catchesAny,
      List<String> caughtErrors,
      boolean structuredCatchBinding,
      int finallyTarget,
      int endTarget) {
    public HandlerSpec {
      catchVariable = catchVariable.map(String::toLowerCase);
      caughtErrors = List.copyOf(caughtErrors);
      if (catchTarget < -1 || finallyTarget < -1 || endTarget < 0) {
        throw new IllegalArgumentException("invalid handler target");
      }
      if ((catchTarget == -1)
          != (catchVariable.isEmpty() && caughtErrors.isEmpty() && !catchesAny)) {
        throw new IllegalArgumentException("invalid catch metadata");
      }
      if (catchTarget == -1 && structuredCatchBinding) {
        throw new IllegalArgumentException("catch binding requires a catch target");
      }
    }

    String disassemble() {
      String caught = catchesAny ? "ANY" : String.join(",", caughtErrors);
      return "catch="
          + catchTarget
          + ":"
          + catchVariable.orElse("-")
          + ":"
          + caught
          + ",binding="
          + (catchTarget < 0 ? "NONE" : structuredCatchBinding ? "STRUCTURED" : "ERROR")
          + ",finally="
          + finallyTarget
          + ",end="
          + endTarget;
    }
  }
}
