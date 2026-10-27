package world.mongoose.banteng.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import world.mongoose.banteng.persistence.LambdaMooV17Codec.QueuedTask;
import world.mongoose.banteng.persistence.LambdaMooV17Codec.SuspendedActivation;
import world.mongoose.banteng.persistence.LambdaMooV17Codec.SuspendedTask;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.ArgSpec;
import world.mongoose.banteng.world.VerbPerms;
import world.mongoose.banteng.world.WorldAnonymousObject;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldSnapshot;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;

final class SharedPersistenceTest {
  @Test
  void anonymousHeadersShareCanonicalFieldsAndRetainCallerValidation(@TempDir Path directory)
      throws IOException {
    AnonymousObjectValue identity = new AnonymousObjectValue();
    WorldAnonymousObject body = new WorldAnonymousObject("anon", 0, 0, List.of(),
        List.of(), List.of());
    WorldSnapshot world = new WorldTxn(List.of(), List.of(root(List.of())),
        Map.of(identity, body), Map.of(), List.of(), 0).snapshot();
    QueuedTask queued = new QueuedTask(7, 12, 1, "tick", "tick", "return 0;\n",
        Map.of("this", identity), 0, identity, 0, true);
    SuspendedActivation activation = new SuspendedActivation(17, "suspend();\n",
        Map.of("this", Optional.of(identity)), List.of(), identity, identity, true,
        0, 0, true, "tick", "tick", Optional.empty(), 20, 0, 18);
    SuspendedTask suspended = new SuspendedTask(8, 13, new IntegerValue(0),
        new MapValue(Map.of()), -1, 0, 50, Optional.empty(), List.of(activation));
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    Path database = directory.resolve("anonymous.db");
    codec.writeAtomic(database, world, List.of(queued, suspended), List.of());
    String text = Files.readString(database, StringValue.charset());
    String legacy = "-1 -7 -8 0 -9 0 1 -10 1";
    assertEquals(2, text.lines().filter(legacy::equals).count());
    assertTrue(text.contains("-111\n12\n1\n12\n1\n1\n" + legacy));
    LambdaMooV17Codec.Checkpoint restored = codec.read(database);
    QueuedTask restoredQueued = assertInstanceOf(QueuedTask.class, restored.tasks().getFirst());
    SuspendedTask restoredSuspended = assertInstanceOf(SuspendedTask.class, restored.tasks().get(1));
    assertSame(restoredQueued.verbLocation(), restoredQueued.initialLocals().get("this"));
    assertSame(restoredQueued.verbLocation(), restoredSuspended.activations().getFirst().receiver());
    assertSame(restoredQueued.verbLocation(), restoredSuspended.activations().getFirst().verbLocation());

    // Queued anonymous numeric-vloc equality remains stricter than suspended's Object-only check.
    Path changed = directory.resolve("changed.db");
    Files.writeString(changed, text.replace(legacy, "-1 -7 -8 0 -9 0 -1 -10 1"),
        StringValue.charset());
    IOException error = assertThrows(IOException.class, () -> codec.read(changed));
    assertEquals("queued-task verb-location encodings disagree", error.getMessage());
    int last = text.lastIndexOf(legacy);
    Files.writeString(changed, text.substring(0, last) + "-1 -7 -8 0 -9 0 -1 -10 1"
        + text.substring(last + legacy.length()), StringValue.charset());
    assertEquals(2, codec.read(changed).tasks().size());
  }

  @Test
  void bothVerbProgramWritersRejectUnrepresentableSource(@TempDir Path directory) {
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    for (String source : List.of("return 1;\r\n", "return 1;\n.\n")) {
      WorldVerb verb =
          new WorldVerb("test", 0, new VerbPerms(4), ArgSpec.NONE, -1, ArgSpec.NONE, source);
      WorldSnapshot permanent = new WorldTxn(List.of(), List.of(root(List.of(verb)))).snapshot();
      assertThrows(IOException.class, () -> codec.writeAtomic(directory.resolve("permanent.db"),
          permanent, List.of(), List.of()));
      AnonymousObjectValue identity = new AnonymousObjectValue();
      WorldSnapshot anonymous = new WorldTxn(List.of(), List.of(root(List.of())),
          Map.of(identity, new WorldAnonymousObject("anon", 0, 0, List.of(), List.of(verb), List.of())),
          Map.of(), List.of(identity), 0).snapshot();
      assertThrows(IOException.class, () -> codec.writeAtomic(directory.resolve("anonymous.db"),
          anonymous, List.of(), List.of()));
    }
  }

  @Test
  void sharedProgramReaderRejectsBadHeadersSlotsDuplicatesAndMissingTerminators() throws IOException {
    assertEquals(Map.of(new ProgramSlot(7, 0), "return 1;\n"),
        ProgramSectionReader.read(reader("#7:0\nreturn 1;\n.\n"), 1, id -> id == 7 ? 1 : -1));
    for (String source : List.of("7:0\n.\n", "#7:0:1\n.\n", "#8:0\n.\n",
        "#7:1\n.\n", "#7:-1\n.\n", "#7:0\nreturn 1;\n")) {
      assertThrows(IOException.class,
          () -> ProgramSectionReader.read(reader(source), 1, id -> id == 7 ? 1 : -1), source);
    }
    assertThrows(IOException.class, () -> ProgramSectionReader.read(
        reader("#7:0\n.\n#7:0\n.\n"), 2, id -> 1));
  }

  @Test
  void pinnedNativeObjectActivationStillWritesIdenticalBytes(@TempDir Path directory)
      throws IOException {
    byte[] fixture;
    try (var input = Objects.requireNonNull(getClass().getResourceAsStream(
        "/world/mongoose/banteng/persistence/queued-task-source-and-dispatch.db"))) {
      fixture = input.readAllBytes();
    }
    Path original = directory.resolve("native.db");
    Path copy = directory.resolve("copy.db");
    Files.write(original, fixture);
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    LambdaMooV17Codec.Checkpoint restored = codec.read(original);
    codec.writeAtomic(copy, restored.world().snapshot(), restored.tasks(), restored.activeConnections());
    assertArrayEquals(fixture, Files.readAllBytes(copy));
  }

  private static BufferedReader reader(String source) {
    return new BufferedReader(new StringReader(source));
  }

  private static WorldObject root(List<WorldVerb> verbs) {
    return new WorldObject(0, "root", 0, 0, -1, List.of(), List.of(), List.of(), verbs, List.of());
  }
}
