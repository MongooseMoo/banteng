package moo.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import moo.value.MooValue.ObjectValue;
import moo.world.WorldObject;
import moo.world.WorldProperty;
import moo.world.WorldTxn;
import moo.world.WorldVerb;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LambdaMooV4ReaderTest {
  private static final Path TEST_DATABASE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");

  private static final String LOGIN_SOURCE =
      """
      if ((args && (length(args) == 1)) && (args[1] == "shutdown"))
      reset_max_object();
      while (max_object() > #7)
      ticks_left() < 2000 || seconds_left() < 2 && suspend(0);
      `recycle(max_object()) ! ANY';
      reset_max_object();
      endwhile
      boot_player(player);
      shutdown();
      endif
      if ((args && (length(args) > 1)) && (args[1] == "connect"))
      if (args[2] == "wizard")
      o = create($nothing);
      o.owner = o;
      set_player_flag(o, 1);
      o.programmer = 1;
      o.wizard = 1;
      move(o, #2);
      switch_player(player, o);
      return;
      elseif (args[2] == "programmer")
      o = create($nothing);
      o.owner = o;
      set_player_flag(o, 1);
      o.programmer = 1;
      move(o, #2);
      switch_player(player, o);
      return;
      elseif (args[2] == "player")
      o = create($nothing);
      o.owner = o;
      set_player_flag(o, 1);
      move(o, #2);
      switch_player(player, o);
      return;
      endif
      endif
      """;

  private static final String UNCAUGHT_SOURCE =
      """
      callers() && raise(E_PERM, "Server task");
      {code, message, value, traceback, formatted} = args;
      for line in (formatted)
      server_log(line);
      endfor
      """;

  private static final String TIMEOUT_SOURCE =
      """
      callers() && raise(E_PERM, "Server task");
      {resource, traceback, formatted} = args;
      for line in (formatted)
      server_log(line);
      endfor
      """;

  private static final String EVAL_SOURCE =
      """
      set_task_perms(player);
      try
      try
      notify(player, "-=!-^-!=-");
      notify(player, toliteral(eval(argstr)));
      except e (ANY)
      notify(player, toliteral({2, e}));
      endtry
      finally
      notify(player, "-=!-v-!=-");
      endtry
      """;

  private static final String NEW_SOURCE =
      """
      set_task_perms(caller_perms());
      player = caller_perms();
      return new_waif();
      """;

  @Test
  void readsTheCompleteAuthoritativeFixtureThroughWorldTxn() throws IOException {
    WorldTxn root = new LambdaMooV4Reader().read(TEST_DATABASE);

    try (WorldTxn world = root.begin()) {
      assertEquals(List.of(3L, 4L), world.players());
      assertEquals(8, world.objectCount());

      List<WorldObject> expected =
          List.of(
              new WorldObject(
                  0,
                  "System Object",
                  16,
                  3,
                  -1,
                  1,
                  List.of(),
                  List.of(),
                  List.of(
                      new WorldVerb("do_login_command", 3, 173, -1, LOGIN_SOURCE),
                      new WorldVerb("handle_uncaught_error", 3, 172, -1, UNCAUGHT_SOURCE),
                      new WorldVerb("handle_task_timeout", 3, 172, -1, TIMEOUT_SOURCE)),
                  List.of(
                      property("nothing", -1, 4),
                      property("system", 0, 4),
                      property("object", 1, 4),
                      property("anonymous", 5, 3),
                      property("server_options", 6, 3),
                      property("waif", 7, 3))),
              new WorldObject(
                  1,
                  "Root Class",
                  144,
                  3,
                  -1,
                  -1,
                  List.of(),
                  List.of(0L, 2L, 3L, 4L, 5L, 6L, 7L),
                  List.of(),
                  List.of()),
              new WorldObject(
                  2,
                  "The First Room",
                  0,
                  3,
                  -1,
                  1,
                  List.of(3L, 4L),
                  List.of(),
                  List.of(new WorldVerb("eval", 3, 88, -2, EVAL_SOURCE)),
                  List.of()),
              object(3, "Wizard", 7, 3, 2, 1),
              object(4, "Programmer", 3, 4, 2, 1),
              object(5, "Anonymous Class", 256, 3, -1, 1),
              object(6, "Server Options", 0, 3, -1, 1),
              new WorldObject(
                  7,
                  "Waif Class",
                  128,
                  3,
                  -1,
                  1,
                  List.of(),
                  List.of(),
                  List.of(new WorldVerb("new", 3, 173, -1, NEW_SOURCE)),
                  List.of()));

      for (WorldObject expectedObject : expected) {
        assertEquals(expectedObject, world.object(expectedObject.id()).orElseThrow());
      }

      assertEquals(expected.get(2).verbs().getFirst(), world.verb(2, 0).orElseThrow());
      assertEquals(
          expected.getFirst().properties().getFirst(), world.property(0, "nothing").orElseThrow());
      assertTrue(world.object(8).isEmpty());
      assertTrue(world.verb(2, 1).isEmpty());
      assertTrue(world.verb(99, 0).isEmpty());
      assertTrue(world.property(0, "missing").isEmpty());
    }
  }

  @Test
  void rejectsAnInvalidHeader(@TempDir Path temporaryDirectory) throws IOException {
    assertMalformed(
        temporaryDirectory,
        fixture()
            .replace(
                "** LambdaMOO Database, Format Version 4 **",
                "** LambdaMOO Database, Format Version 17 **"),
        "invalid v4 header");
  }

  @Test
  void rejectsAnInvalidObjectSlot(@TempDir Path temporaryDirectory) throws IOException {
    assertMalformed(
        temporaryDirectory,
        fixture().replace("#7\nWaif Class\n", "#8\nWaif Class\n"),
        "invalid object slot 7");
  }

  @Test
  void rejectsAnInvalidPropertySlotCount(@TempDir Path temporaryDirectory) throws IOException {
    assertMalformed(
        temporaryDirectory,
        fixture().replace("waif\n6\n1\n-1\n", "waif\n5\n1\n-1\n"),
        "6 property names but 5 slots");
  }

  @Test
  void rejectsAnUnsupportedValueTag(@TempDir Path temporaryDirectory) throws IOException {
    assertMalformed(
        temporaryDirectory,
        fixture().replace("waif\n6\n1\n-1\n", "waif\n6\n2\n-1\n"),
        "unsupported persisted value tag 2");
  }

  @Test
  void rejectsAnInvalidProgramIndex(@TempDir Path temporaryDirectory) throws IOException {
    assertMalformed(
        temporaryDirectory,
        fixture().replace("#7:0\nset_task_perms", "#7:1\nset_task_perms"),
        "program references missing verb #7:1");
  }

  @Test
  void rejectsInvalidReciprocalTopology(@TempDir Path temporaryDirectory) throws IOException {
    assertMalformed(
        temporaryDirectory,
        fixture().replace("#3\nWizard\n\n7\n3\n2\n-1\n4\n", "#3\nWizard\n\n7\n3\n1\n-1\n4\n"),
        "content #3 does not name location #2");
  }

  @Test
  void rejectsUnsupportedTails(@TempDir Path temporaryDirectory) throws IOException {
    assertMalformed(
        temporaryDirectory, fixture() + "0 active connections\n", "unsupported v4 tail");
  }

  private static WorldProperty property(String name, long value, long owner) {
    return new WorldProperty(name, new ObjectValue(value), owner, 1);
  }

  private static WorldObject object(
      long id, String name, int flags, long owner, long location, long parent) {
    return new WorldObject(
        id, name, flags, owner, location, parent, List.of(), List.of(), List.of(), List.of());
  }

  private static String fixture() throws IOException {
    return Files.readString(TEST_DATABASE, StandardCharsets.ISO_8859_1);
  }

  private static void assertMalformed(Path temporaryDirectory, String contents, String message)
      throws IOException {
    Path database = temporaryDirectory.resolve("invalid.db");
    Files.writeString(database, contents, StandardCharsets.ISO_8859_1);
    IOException error =
        assertThrows(IOException.class, () -> new LambdaMooV4Reader().read(database));
    String actualMessage = Objects.requireNonNull(error.getMessage());
    assertTrue(actualMessage.contains(message), actualMessage);
  }
}
