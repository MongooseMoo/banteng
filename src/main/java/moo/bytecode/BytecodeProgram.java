package moo.bytecode;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/** Immutable bytecode for the executable language slice. */
public record BytecodeProgram(List<Instruction> instructions, List<BytecodeProgram> forkVectors) {
  public BytecodeProgram {
    instructions = List.copyOf(instructions);
    forkVectors = List.copyOf(forkVectors);
  }

  /** Creates a program with no fork vectors. */
  public BytecodeProgram(List<Instruction> instructions) {
    this(instructions, List.of());
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
    POP,
    GET_PROPERTY,
    SET_PROPERTY,
    ENTER_INDEX,
    INDEX,
    RANGE,
    FIRST,
    LAST,
    SET_INDEX_LOCAL,
    SET_RANGE_LOCAL,
    CALL,
    CALL_VERB,
    NEGATE,
    NOT,
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    REMAINDER,
    POWER,
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
    SCATTER,
    RETURN
  }

  /** One validated instruction and its explicit operands. */
  public record Instruction(
      Opcode opcode, OptionalLong operand, Optional<String> text, Optional<HandlerSpec> handler) {
    public Instruction {
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
                SCATTER ->
                true;
            default -> false;
          };
      boolean handlerRequired = opcode == Opcode.ENTER_HANDLER;
      boolean optionalParent = opcode == Opcode.INDEX || opcode == Opcode.SET_RANGE_LOCAL;
      if ((!optionalParent && numberRequired != operand.isPresent())
          || (optionalParent && operand.isPresent() && operand.orElseThrow() != 1)
          || textRequired != text.isPresent()
          || handlerRequired != handler.isPresent()) {
        throw new IllegalArgumentException(opcode + " has invalid operands");
      }
    }

    /** Creates an instruction without operands. */
    public Instruction(Opcode opcode) {
      this(opcode, OptionalLong.empty(), Optional.empty(), Optional.empty());
    }

    /** Creates an instruction with one numeric operand. */
    public Instruction(Opcode opcode, long operand) {
      this(opcode, OptionalLong.of(operand), Optional.empty(), Optional.empty());
    }

    /** Creates an instruction with one text operand. */
    public Instruction(Opcode opcode, String text) {
      this(opcode, OptionalLong.empty(), Optional.of(text), Optional.empty());
    }

    /** Creates an instruction with numeric and text operands. */
    public Instruction(Opcode opcode, long operand, String text) {
      this(opcode, OptionalLong.of(operand), Optional.of(text), Optional.empty());
    }

    /** Creates an explicit handler entry instruction. */
    public Instruction(HandlerSpec handler) {
      this(Opcode.ENTER_HANDLER, OptionalLong.empty(), Optional.empty(), Optional.of(handler));
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
