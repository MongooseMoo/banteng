package moo.benchmark;

import moo.syntax.Ast;
import moo.syntax.MooParser;
import org.openjdk.jmh.annotations.Benchmark;

/** Forked benchmark for the production parser entry point. */
public class ParserBenchmark {
  @Benchmark
  public Ast.Program parse() {
    return MooParser.parse("return 1;");
  }
}
