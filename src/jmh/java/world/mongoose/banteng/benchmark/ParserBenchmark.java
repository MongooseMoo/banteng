package world.mongoose.banteng.benchmark;

import world.mongoose.banteng.syntax.Ast;
import world.mongoose.banteng.syntax.MooParser;
import org.openjdk.jmh.annotations.Benchmark;

/** Forked benchmark for the production parser entry point. */
public class ParserBenchmark {
  @Benchmark
  public Ast.Program parse() {
    return MooParser.parse("return 1;");
  }
}
