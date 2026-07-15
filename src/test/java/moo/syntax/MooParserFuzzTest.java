package moo.syntax;

import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.charset.StandardCharsets;

final class MooParserFuzzTest {
  @FuzzTest(maxDuration = "5s")
  void parsesArbitraryLatin1(byte[] input) {
    try {
      MooParser.parse(new String(input, StandardCharsets.ISO_8859_1));
    } catch (MooParser.ParseException expected) {
      // Malformed source is a normal parser result; every other failure is a Jazzer finding.
    }
  }
}
