package world.mongoose.banteng.world;

import java.util.Objects;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.MooCompiler;

/** An immutable persisted verb and its exact program source. */
public record WorldVerb(
    String names, long owner, VerbPerms perms, ArgSpec dobj, int preposition, ArgSpec iobj,
    String programSource, BytecodeProgram program) {
  /** Compiles code at a source-entry boundary, retaining its exact persisted spelling. */
  public WorldVerb(
      String names, long owner, VerbPerms perms, ArgSpec dobj, int preposition, ArgSpec iobj,
      String programSource) {
    this(names, owner, perms, dobj, preposition, iobj, programSource,
        new MooCompiler().compile(programSource));
  }

  /** Creates a verb with its exact persisted metadata and source. */
  public WorldVerb {
    Objects.requireNonNull(names, "names");
    Objects.requireNonNull(perms, "perms");
    Objects.requireNonNull(dobj, "dobj");
    Objects.requireNonNull(iobj, "iobj");
    Objects.requireNonNull(programSource, "programSource");
    Objects.requireNonNull(program, "program");
  }
}
