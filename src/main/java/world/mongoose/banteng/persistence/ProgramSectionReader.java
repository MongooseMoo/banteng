package world.mongoose.banteng.persistence;

import static world.mongoose.banteng.persistence.DbScanner.malformed;
import static world.mongoose.banteng.persistence.DbScanner.parseCount;
import static world.mongoose.banteng.persistence.DbScanner.parseLong;
import static world.mongoose.banteng.persistence.DbScanner.requiredLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongToIntFunction;

/** Shared program headers, dot-terminated bodies and duplicate-slot validation. */
final class ProgramSectionReader {
  private ProgramSectionReader() {}

  static Map<ProgramSlot, String> read(
      BufferedReader input, int count, LongToIntFunction verbCount) throws IOException {
    Map<ProgramSlot, String> programs = new LinkedHashMap<>();
    for (int index = 0; index < count; index++) {
      String header = requiredLine(input, "program header");
      int colon = header.indexOf(':');
      if (!header.startsWith("#") || colon <= 1 || colon != header.lastIndexOf(':')) {
        throw malformed("invalid program header: " + header);
      }
      long object = parseLong(header.substring(1, colon), "program object");
      int verb = parseCount(header.substring(colon + 1), "program verb index");
      int available = verbCount.applyAsInt(object);
      if (available < 0) {
        throw malformed("program references missing object #" + object);
      }
      if (verb >= available) {
        throw malformed("program references missing verb #" + object + ":" + verb);
      }
      ProgramSlot slot = new ProgramSlot(object, verb);
      if (programs.putIfAbsent(slot, readSource(input, "program source")) != null) {
        throw malformed("duplicate program #" + object + ":" + verb);
      }
    }
    return programs;
  }

  static String readSource(BufferedReader input, String field) throws IOException {
    StringBuilder source = new StringBuilder();
    while (true) {
      String line = requiredLine(input, field);
      if (line.equals(".")) {
        return source.toString();
      }
      source.append(line).append('\n');
    }
  }
}
