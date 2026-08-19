package world.mongoose.banteng.syntax;

import com.code_intelligence.jazzer.junit.FuzzTest;
import world.mongoose.banteng.value.MooValue.StringValue;

final class MooParserFuzzTest {
  @FuzzTest(maxDuration = "5s")
  void parsesArbitraryLatin1(byte[] input) {
    try {
      MooParser.parse(StringValue.of(input).text());
    } catch (MooParser.ParseException expected) {
      // Malformed source is a normal parser result; every other failure is a Jazzer finding.
    }
  }
}
