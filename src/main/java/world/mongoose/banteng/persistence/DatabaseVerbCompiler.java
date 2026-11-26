package world.mongoose.banteng.persistence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.world.ArgSpec;
import world.mongoose.banteng.world.VerbPerms;
import world.mongoose.banteng.world.WorldVerb;

/** Compiles the numbered and anonymous verb programs stored in a database. */
public record DatabaseVerbCompiler(Predicate<String> knownBuiltin, Consumer<String> log) {
  static final DatabaseVerbCompiler PRESERVE_SOURCE = new DatabaseVerbCompiler(name -> true, message -> {});

  public DatabaseVerbCompiler {
    Objects.requireNonNull(knownBuiltin, "knownBuiltin");
    Objects.requireNonNull(log, "log");
  }

  WorldVerb compile(long objectId, RawVerb verb, String source) throws IOException {
    List<MooCompiler.Diagnostic> warnings = new ArrayList<>();
    BytecodeProgram program;
    try {
      program = new MooCompiler().compileStored(source, knownBuiltin, warnings::add);
    } catch (IllegalArgumentException error) {
      throw new IOException("unparsable verb program: " + verb.names(), error);
    }
    if (!warnings.isEmpty()) {
      log.accept("PARSER: Warning in #" + objectId + ":" + verb.names() + ":");
      for (MooCompiler.Diagnostic warning : warnings) {
        log.accept(warning.display());
      }
      source = program.source() + "\n";
    }
    int permissions = verb.permissions();
    try {
      return new WorldVerb(verb.names(), verb.owner(), new VerbPerms(permissions & 15),
          ArgSpec.fromValue((permissions >> 4) & 3), verb.preposition(),
          ArgSpec.fromValue((permissions >> 6) & 3), source, program);
    } catch (IllegalArgumentException error) {
      throw new IOException("invalid verb metadata: " + verb.names(), error);
    }
  }
}
