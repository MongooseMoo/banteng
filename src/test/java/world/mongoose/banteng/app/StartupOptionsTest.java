package world.mongoose.banteng.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.StringValue;

final class StartupOptionsTest {
  @Test
  void keepsLastValuesFirstKindOrderAndCleanUnevaluatedLines(@TempDir Path directory) throws Exception {
    Path file = directory.resolve("startup file.moo");
    Files.writeString(file, "first\r\n\r\nraw\u00ff\t\u0001\u007f~\r\n", StringValue.charset());
    ListValue fileArgs = new ListValue(List.of(StringValue.of("first"), StringValue.of(""), StringValue.of("raw~")));
    ListValue lineArgs = new ListValue(List.of(StringValue.of("one; $literal {braces}~")));
    for (boolean fileFirst : List.of(false, true)) {
      Banteng application = new Banteng();
      CommandLine cli = new CommandLine(application);
      String first = fileFirst ? "--start-script" : "--start-line";
      String firstValue = fileFirst ? directory.resolve("unused file").toString() : "old line";
      String second = fileFirst ? "--start-line" : "--start-script";
      String secondValue = fileFirst ? "old line" : directory.resolve("unused file").toString();
      cli.parseArgs(first, firstValue, second, secondValue,
          "--start-line", "one;\t $literal\u00ff {braces}\r\n\u0001\u007f~", "--start-script", file.toString());
      assertEquals(fileFirst ? List.of(fileArgs, lineArgs) : List.of(lineArgs, fileArgs), application.startupScripts());
    }
  }
}
