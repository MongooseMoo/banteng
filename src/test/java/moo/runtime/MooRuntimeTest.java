package moo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import moo.persistence.LambdaMooV4Reader;
import moo.value.MooValue.ObjectValue;
import moo.world.WorldObject;
import moo.world.WorldTxn;
import moo.world.WorldVerb;
import org.junit.jupiter.api.Test;

final class MooRuntimeTest {
  private static final Path FIXTURE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");
  private static final String CONNECTION_PREFIX = "-=!-^-!=-";
  private static final String CONNECTION_SUFFIX = "-=!-v-!=-";

  @Test
  void executesTheFirstManagedRowThroughStoredVerbsAndOneWorldTxn() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);

    assertEquals(List.of(), runtime.openConnection(-2));
    runtime.closeConnection(-2);

    long connectionId = -47;
    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    WorldObject wizard = world.object(8).orElseThrow();
    assertEquals(8, wizard.owner());
    assertEquals(7, wizard.flags());
    assertEquals(2, wizard.location());
    assertEquals(List.of(3L, 4L, 8L), world.object(2).orElseThrow().contents());
    assertEquals(List.of(3L, 4L, 8L), world.players());

    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    executeSetup(runtime, connectionId, "object", "#1");
    executeSetup(runtime, connectionId, "anonymous", "#5");
    executeSetup(runtime, connectionId, "anon", "#5");
    executeSetup(runtime, connectionId, "sysobj", "#0");
    executeSetup(runtime, connectionId, "nothing", "#-1");

    assertEquals(new ObjectValue(5), world.property(0, "anon").orElseThrow().value());
    assertEquals(new ObjectValue(0), world.property(0, "sysobj").orElseThrow().value());
    assertEquals(5, world.property(0, "anon").orElseThrow().permissions());
    assertEquals(5, world.property(0, "sysobj").orElseThrow().permissions());
    assertEquals(8, world.object(0).orElseThrow().properties().size());

    assertEquals(
        List.of(
            CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 2}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return 1 + 1;"));
  }

  @Test
  void tokenizesBackslashEscapesForAStoredPlayerCommandVerb() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; add_verb(player, {player, "xd", "audit_words"}, {"any", "none", "none"});
            set_verb_code(player, "audit_words", {
              "notify(player, \\"LEN:\\" + tostr(length(args)));",
              "notify(player, \\"ARG1:\\" + args[1]);",
              "notify(player, \\"ARG2:\\" + args[2]);"
            });
            return 1;
            """);
    long player = world.connectionPlayer(connectionId).orElseThrow();
    assertTrue(
        world.verb(player, "audit_words").isPresent(),
        () -> setupOutput + " player=" + player + " object=" + world.object(player));
    assertTrue(
        !world.verb(player, "audit_words").orElseThrow().programSource().isEmpty(),
        setupOutput::toString);

    try {
      assertEquals(
          List.of("LEN:2", "ARG1:foo bar", "ARG2:baz"),
          runtime.executeLine(connectionId, "audit_words foo\\ bar baz"));
    } finally {
      runtime.executeLine(connectionId, "; return delete_verb(player, \"audit_words\");");
    }
  }

  @Test
  void tokenizesMidwordQuotesForAStoredPlayerCommandVerb() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; add_verb(player, {player, "xd", "audit_words"}, {"any", "none", "none"});
            set_verb_code(player, "audit_words", {
              "notify(player, \\"LEN:\\" + tostr(length(args)));",
              "notify(player, \\"ARG1:\\" + args[1]);",
              "notify(player, \\"ARG2:\\" + args[2]);"
            });
            return 1;
            """);
    long player = world.connectionPlayer(connectionId).orElseThrow();
    assertTrue(world.verb(player, "audit_words").isPresent(), setupOutput::toString);

    try {
      assertEquals(
          List.of("LEN:2", "ARG1:abc def", "ARG2:zz"),
          runtime.executeLine(connectionId, "audit_words ab\"c d\"ef zz"));
    } finally {
      runtime.executeLine(connectionId, "; return delete_verb(player, \"audit_words\");");
    }
  }

  @Test
  void usesTheLeftmostPrepositionForAStoredPlayerCommandVerb() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; add_verb(player, {player, "xd", "auditprep"}, {"any", "any", "any"});
            set_verb_code(player, "auditprep", {
              "notify(player, \\"DOBJSTR:\\" + dobjstr);",
              "notify(player, \\"PREPSTR:\\" + prepstr);",
              "notify(player, \\"IOBJSTR:\\" + iobjstr);"
            });
            return 1;
            """);
    assertTrue(
        world.verb(world.connectionPlayer(connectionId).orElseThrow(), "auditprep").isPresent(),
        setupOutput::toString);

    try {
      assertEquals(
          List.of("DOBJSTR:book", "PREPSTR:out of", "IOBJSTR:bag in front of chair"),
          runtime.executeLine(connectionId, "auditprep book out of bag in front of chair"));
    } finally {
      runtime.executeLine(connectionId, "; return delete_verb(player, \"auditprep\");");
    }
  }

  @Test
  void keepsNegativeObjectLiteralsAsFailedMatchesForStoredPlayerCommands() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; add_verb(player, {player, "xd", "auditgrab"}, {"any", "none", "none"});
            set_verb_code(player, "auditgrab", {
              "notify(player, \\"DOBJ:\\" + toliteral(dobj));"
            });
            return 1;
            """);
    assertTrue(
        world.verb(world.connectionPlayer(connectionId).orElseThrow(), "auditgrab").isPresent(),
        setupOutput::toString);

    try {
      assertEquals(List.of("DOBJ:#-3"), runtime.executeLine(connectionId, "auditgrab #-1"));
      assertEquals(List.of("DOBJ:#-3"), runtime.executeLine(connectionId, "auditgrab #-2"));
      assertEquals(List.of("DOBJ:#-3"), runtime.executeLine(connectionId, "auditgrab #-3"));
    } finally {
      runtime.executeLine(connectionId, "; return delete_verb(player, \"auditgrab\");");
    }
  }

  @Test
  void poolsExactObjectNamesAndAliasesForStoredPlayerCommands() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = world.connectionPlayer(connectionId).orElseThrow();
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; first = create($nothing);
            first.name = "auditexact";
            add_property(first, "aliases", {}, {player, "rw"});
            move(first, player);
            second = create($nothing);
            second.name = "other audit exact";
            add_property(second, "aliases", {"auditexact"}, {player, "rw"});
            move(second, player);
            add_verb(player, {player, "xd", "auditlook"}, {"any", "none", "none"});
            set_verb_code(player, "auditlook", {
              "notify(player, \\"DOBJ:\\" + toliteral(dobj));"
            });
            return {first, second};
            """);
    List<Long> inventory = world.object(player).orElseThrow().contents();
    assertEquals(2, inventory.size(), setupOutput::toString);
    long first = inventory.get(0);
    long second = inventory.get(1);
    assertEquals("auditexact", world.object(first).orElseThrow().name());
    assertEquals("other audit exact", world.object(second).orElseThrow().name());
    assertEquals("{}", world.readObjectProperty(first, "aliases").orElseThrow().toLiteral());
    assertEquals(
        "{\"auditexact\"}", world.readObjectProperty(second, "aliases").orElseThrow().toLiteral());
    assertEquals(player, world.object(first).orElseThrow().location());
    assertEquals(player, world.object(second).orElseThrow().location());
    assertTrue(world.verb(player, "auditlook").isPresent(), setupOutput::toString);

    try {
      assertEquals(List.of("DOBJ:#-2"), runtime.executeLine(connectionId, "auditlook auditexact"));
    } finally {
      runtime.executeLine(
          connectionId,
          "; delete_verb(player, \"auditlook\"); recycle(#"
              + first
              + "); recycle(#"
              + second
              + "); return 1;");
    }
  }

  @Test
  void poolsPartialObjectNamesAndAliasesForStoredPlayerCommands() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = world.connectionPlayer(connectionId).orElseThrow();
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; first = create($nothing);
            first.name = "auditpartialone";
            add_property(first, "aliases", {}, {player, "rw"});
            move(first, player);
            second = create($nothing);
            second.name = "other audit partial";
            add_property(second, "aliases", {"auditpartialtwo"}, {player, "rw"});
            move(second, player);
            add_verb(player, {player, "xd", "auditlook"}, {"any", "none", "none"});
            set_verb_code(player, "auditlook", {
              "notify(player, \\"DOBJ:\\" + toliteral(dobj));"
            });
            return {first, second};
            """);
    List<Long> inventory = world.object(player).orElseThrow().contents();
    assertEquals(2, inventory.size(), setupOutput::toString);
    long first = inventory.get(0);
    long second = inventory.get(1);
    assertEquals("auditpartialone", world.object(first).orElseThrow().name());
    assertEquals("other audit partial", world.object(second).orElseThrow().name());
    assertEquals("{}", world.readObjectProperty(first, "aliases").orElseThrow().toLiteral());
    assertEquals(
        "{\"auditpartialtwo\"}",
        world.readObjectProperty(second, "aliases").orElseThrow().toLiteral());
    assertEquals(player, world.object(first).orElseThrow().location());
    assertEquals(player, world.object(second).orElseThrow().location());
    assertTrue(world.verb(player, "auditlook").isPresent(), setupOutput::toString);

    try {
      assertEquals(
          List.of("DOBJ:#-2"), runtime.executeLine(connectionId, "auditlook auditpartial"));
    } finally {
      runtime.executeLine(
          connectionId,
          "; delete_verb(player, \"auditlook\"); recycle(#"
              + first
              + "); recycle(#"
              + second
              + "); return 1;");
    }
  }

  @Test
  void matchesTheCurrentPlayerThroughRoomContentsForStoredPlayerCommands() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = world.connectionPlayer(connectionId).orElseThrow();
    String oldName = world.object(player).orElseThrow().name();
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; old_name = player.name;
            player.name = "auditplayerunique";
            add_property(player, "audit_old_name", old_name, {player, "rw"});
            add_verb(player, {player, "xd", "auditlook"}, {"any", "none", "none"});
            set_verb_code(player, "auditlook", {
              "notify(player, \\"ISPLAYER:\\" + tostr(dobj == player));"
            });
            return 1;
            """);
    assertEquals("auditplayerunique", world.object(player).orElseThrow().name());
    assertEquals(
        "\"" + oldName + "\"",
        world.readObjectProperty(player, "audit_old_name").orElseThrow().toLiteral());
    long location = world.object(player).orElseThrow().location();
    assertTrue(world.object(location).orElseThrow().contents().contains(player));
    assertTrue(world.verb(player, "auditlook").isPresent(), setupOutput::toString);

    try {
      assertEquals(
          List.of("ISPLAYER:1"), runtime.executeLine(connectionId, "auditlook auditplayerunique"));
    } finally {
      runtime.executeLine(
          connectionId,
          "; player.name = player.audit_old_name; "
              + "delete_property(player, \"audit_old_name\"); "
              + "delete_verb(player, \"auditlook\"); return 1;");
    }
  }

  @Test
  void passesTheFullWordListToTruthyDoCommandBeforeNormalDispatch() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; try
              add_verb(#0, {player, "rxd", "do_command"}, {"this", "none", "this"});
            except (E_INVARG)
            endtry
            set_verb_code(#0, "do_command", {
              "if (args[1] == \\";\\")",
              "  return 0;",
              "endif",
              "notify(player, \\"DO_COMMAND:\\" + tostr(length(args)) + \\":\\" + args[2]);",
              "return 1;"
            });
              add_verb(player, {player, "xd", "audit_words"}, {"any", "none", "none"});
              set_verb_code(player, "audit_words", {"notify(player, \\"NORMAL_DISPATCH\\");"});
              return 1;
            """);
    assertTrue(world.verb(0, "do_command").isPresent(), setupOutput::toString);

    try {
      assertEquals(List.of(), runtime.executeLine(connectionId, "pReFiX command-prefix"));
      assertEquals(List.of(), runtime.executeLine(connectionId, "sUfFiX command-suffix"));
      assertEquals(
          List.of("command-prefix", "DO_COMMAND:2:foo bar", "command-suffix"),
          runtime.executeLine(connectionId, "audit_words foo\\ bar"));
    } finally {
      runtime.executeLine(
          connectionId,
          "; delete_verb(#0, \"do_command\"); delete_verb(player, \"audit_words\"); return 1;");
    }
  }

  @Test
  void runsRoomHuhAfterStoredCommandArgspecMismatch() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = world.connectionPlayer(connectionId).orElseThrow();
    long room = world.object(player).orElseThrow().location();
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; room = player.location;
            add_verb(player, {player, "xd", "auditmismatch"}, {"none", "none", "none"});
            set_verb_code(player, "auditmismatch", {
              "notify(player, \\"BAD_MATCH\\");"
            });
            add_verb(room, {player, "xd", "huh"}, {"none", "none", "none"});
            set_verb_code(room, "huh", {
              "notify(player, \\"HUH:\\" + verb);",
              "notify(player, \\"ARGSTR:\\" + argstr);"
            });
            return room;
            """);
    assertEquals(List.of(CONNECTION_PREFIX, "{1, #" + room + "}", CONNECTION_SUFFIX), setupOutput);
    assertEquals(room, world.object(player).orElseThrow().location());
    WorldVerb commandVerb = world.verb(player, "auditmismatch").orElseThrow();
    assertEquals(12, commandVerb.permissions());
    assertEquals(-1, commandVerb.preposition());
    assertTrue(!commandVerb.programSource().isEmpty());
    WorldVerb huhVerb = world.verb(room, "huh").orElseThrow();
    assertEquals(12, huhVerb.permissions());
    assertEquals(-1, huhVerb.preposition());
    assertTrue(!huhVerb.programSource().isEmpty());

    try {
      assertEquals(
          List.of("HUH:auditmismatch", "ARGSTR:object"),
          runtime.executeLine(connectionId, "auditmismatch object"));
    } finally {
      runtime.executeLine(
          connectionId,
          "; delete_verb(player, \"auditmismatch\"); delete_verb(#"
              + room
              + ", \"huh\"); return 1;");
    }
  }

  @Test
  void reparsesTheLeadingQuoteShortcutAsSay() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = world.connectionPlayer(connectionId).orElseThrow();
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; box = create($nothing);
            box.name = "auditbox";
            move(box, player);
            add_verb(player, {player, "xd", "say"}, {"any", "in", "any"});
            set_verb_code(player, "say", {
              "notify(player, \\"DOBJSTR:\\" + dobjstr);",
              "notify(player, \\"PREPSTR:\\" + prepstr);",
              "notify(player, \\"IOBJSTR:\\" + iobjstr);"
            });
            return box;
            """);
    List<Long> inventory = world.object(player).orElseThrow().contents();
    assertEquals(1, inventory.size(), setupOutput::toString);
    long box = inventory.getFirst();
    assertEquals(List.of(CONNECTION_PREFIX, "{1, #" + box + "}", CONNECTION_SUFFIX), setupOutput);
    assertEquals("auditbox", world.object(box).orElseThrow().name());
    assertEquals(player, world.object(box).orElseThrow().location());
    WorldVerb say = world.verb(player, "say").orElseThrow();
    assertEquals(92, say.permissions());
    assertEquals(3, say.preposition());
    assertTrue(!say.programSource().isEmpty());

    try {
      assertEquals(
          List.of("DOBJSTR:widget", "PREPSTR:in", "IOBJSTR:auditbox"),
          runtime.executeLine(connectionId, "\"widget in auditbox"));
    } finally {
      runtime.executeLine(
          connectionId, "; delete_verb(player, \"say\"); recycle(#" + box + "); return 1;");
    }
  }

  @Test
  void reparsesTheLeadingColonShortcutAsEmote() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = world.connectionPlayer(connectionId).orElseThrow();
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; add_verb(player, {player, "xd", "emote"}, {"any", "at", "any"});
            set_verb_code(player, "emote", {
              "notify(player, \\"DOBJSTR:\\" + dobjstr);",
              "notify(player, \\"PREPSTR:\\" + prepstr);",
              "notify(player, \\"IOBJSTR:\\" + iobjstr);",
              "notify(player, \\"IOBJISPLAYER:\\" + tostr(iobj == player));"
            });
            return 1;
            """);
    assertEquals(List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX), setupOutput);
    WorldVerb emote = world.verb(player, "emote").orElseThrow();
    assertEquals(92, emote.permissions());
    assertEquals(1, emote.preposition());
    assertTrue(!emote.programSource().isEmpty());

    try {
      assertEquals(
          List.of("DOBJSTR:wave", "PREPSTR:at", "IOBJSTR:me", "IOBJISPLAYER:1"),
          runtime.executeLine(connectionId, ":wave at me"));
    } finally {
      runtime.executeLine(connectionId, "; return delete_verb(player, \"emote\");");
    }
  }

  @Test
  void reparsesTheLeadingSemicolonShortcutAsEval() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = world.connectionPlayer(connectionId).orElseThrow();
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; box = create($nothing);
            box.name = "auditevalbox";
            move(box, player);
            add_verb(player, {player, "xd", "eval"}, {"any", "in", "any"});
            set_verb_code(player, "eval", {
              "notify(player, \\"DOBJSTR:\\" + dobjstr);",
              "notify(player, \\"PREPSTR:\\" + prepstr);",
              "notify(player, \\"IOBJSTR:\\" + iobjstr);"
            });
            return box;
            """);
    List<Long> inventory = world.object(player).orElseThrow().contents();
    assertEquals(1, inventory.size(), setupOutput::toString);
    long box = inventory.getFirst();
    assertEquals(List.of(CONNECTION_PREFIX, "{1, #" + box + "}", CONNECTION_SUFFIX), setupOutput);
    assertEquals("auditevalbox", world.object(box).orElseThrow().name());
    assertEquals(player, world.object(box).orElseThrow().location());
    WorldVerb eval = world.verb(player, "eval").orElseThrow();
    assertEquals(92, eval.permissions());
    assertEquals(3, eval.preposition());
    assertTrue(!eval.programSource().isEmpty());

    try {
      assertEquals(
          List.of("DOBJSTR:widget", "PREPSTR:in", "IOBJSTR:auditevalbox"),
          runtime.executeLine(connectionId, ";widget in auditevalbox"));
    } finally {
      runtime.executeLine(
          connectionId, "; delete_verb(player, \"eval\"); recycle(#" + box + "); return 1;");
    }
  }

  @Test
  void installsSubmittedVerbCodeThroughDotProgramIntrinsic() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = world.connectionPlayer(connectionId).orElseThrow();
    long object = world.objectCount();
    assertTrue(world.object(object).isEmpty());
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; obj = create($nothing);
            obj.name = "audit program target";
            add_verb(obj, {player, "rxd", "auditprog"}, {"this", "none", "none"});
            return obj;
            """);
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, #" + object + "}", CONNECTION_SUFFIX), setupOutput);
    WorldObject target = world.object(object).orElseThrow();
    assertEquals("audit program target", target.name());
    assertEquals(-1, target.parent());
    assertEquals(player, target.owner());
    assertEquals(1, target.verbs().size());
    WorldVerb auditprog = target.verbs().getFirst();
    assertEquals("auditprog", auditprog.names());
    assertEquals(player, auditprog.owner());
    assertEquals(45, auditprog.permissions());
    assertEquals(-1, auditprog.preposition());
    assertEquals("", auditprog.programSource());

    try {
      runtime.executeLine(connectionId, ".program #" + object + ":auditprog");
      assertEquals("", world.verb(object, 0).orElseThrow().programSource());
      runtime.executeLine(connectionId, "return 4242;");
      assertEquals("", world.verb(object, 0).orElseThrow().programSource());
      runtime.executeLine(connectionId, ".");

      assertEquals(
          List.of(CONNECTION_PREFIX, "{1, 4242}", CONNECTION_SUFFIX),
          runtime.executeLine(connectionId, "; return #" + object + ":auditprog();"));
      assertEquals("return 4242;\n", world.verb(object, 0).orElseThrow().programSource());
    } finally {
      runtime.executeLine(connectionId, "; recycle(#" + object + "); return 1;");
    }
  }

  @Test
  void evalRuntimeErrorUnwindsIntoPersistedCallerExceptAndFinally() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{2, {E_TYPE}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return 1.0 + 1;"));
  }

  @Test
  void executesEqualityCollectionsThroughStoredEvalRuntime() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {1, 0, {1, 0}}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; return {\"10\" == \"1\" + \"0\", [] == {}, {\"A\" == \"a\", \"À\" == \"à\"}};"));
  }

  @Test
  void executesTheFrozenQueuedTasksSurfaceThroughStoredEvalRuntime() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {{{\"function_info\", 0, 1, {2}}, "
                + "{\"queued_tasks\", 0, 2, {0, 0}}}, "
                + "{\"function_info\", 0, 1, {2}}, "
                + "{\"queued_tasks\", 0, 2, {0, 0}}, {}, {}, {}, 0}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; return {function_info(), function_info(\"function_info\"), "
                + "function_info(\"queued_tasks\"), queued_tasks(), queued_tasks(1), "
                + "queued_tasks(0, 0), queued_tasks(0, 1)};"));
  }

  @Test
  void executesSqliteExistsInRuntimeThroughStoredEvalRuntime() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    assertEquals(
        List.of(
            CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; try sqlite_open(1,2,3,4,5,6,7,8,9); return 1; except (E_VERBNF) return 0; except (ANY) return 1; endtry;"));
  }

  @Test
  void executesSqliteManagedSurfaceThroughStoredEvalRuntime() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {1, 1, 1, 1, 1, 1, 1}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; h = sqlite_open(\":memory:\"); info = sqlite_info(h); "
                + "return {h > 0, h in sqlite_handles(), info[\"path\"] == \":memory:\", "
                + "info[\"parse_types\"] == 1, info[\"parse_objects\"] == 1, "
                + "info[\"sanitize_strings\"] == 0, info[\"locks\"] == 0};"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {1, 1, 1}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; sqlite_query(1, \"CREATE TABLE t(id INTEGER PRIMARY KEY, n INTEGER, "
                + "x REAL, obj TEXT, label TEXT)\"); "
                + "inserted = sqlite_execute(1, \"INSERT INTO t(n, x, obj, label) "
                + "VALUES (?, ?, ?, ?)\", {42, 3.5, #0, \"alpha\"}); "
                + "return {typeof(inserted) == LIST, length(inserted) == 0, "
                + "sqlite_last_insert_row_id(1) == 1};"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {{42, 3.5, #0, \"alpha\"}}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; return sqlite_execute(1, \"SELECT ?, ?, ?, ?\", {42, 3.5, #0, \"alpha\"});"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {{{\"first\", 42}, {\"label\", \"alpha\"}}}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId, "; return sqlite_query(1, \"SELECT n AS first, label FROM t\", 1);"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {1, 1, 1, 1, 1}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; name_limit = sqlite_limit(1, \"LIMIT_COLUMN\", -1); "
                + "number_limit = sqlite_limit(1, 2, -1); "
                + "previous = sqlite_limit(1, 2, name_limit - 1); "
                + "current = sqlite_limit(1, \"LIMIT_COLUMN\", -1); "
                + "restored = sqlite_limit(1, \"LIMIT_COLUMN\", name_limit); "
                + "final = sqlite_limit(1, 2, -1); "
                + "return {name_limit == number_limit, previous == name_limit, "
                + "current == name_limit - 1, restored == name_limit - 1, "
                + "final == name_limit};"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {1, 1, 1, 1, 1, 1, 1}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; query_returned = sqlite_query(999999, \"SELECT 1\") == E_INVARG; "
                + "execute_returned = sqlite_execute(999999, \"SELECT 1\", {}) == E_INVARG; "
                + "close_raised = `sqlite_close(999999) ! E_INVARG => 1'; "
                + "info_raised = `sqlite_info(999999) ! E_INVARG => 1'; "
                + "limit_raised = `sqlite_limit(1, \"LIMIT_NOT_REAL\", -1) "
                + "! E_INVARG => 1'; "
                + "call_raised = `call_function(\"sqlite_info\", 2) ! E_INVARG => 1'; "
                + "interrupt_raised = `sqlite_interrupt(999999) ! E_INVARG => 1'; "
                + "return {query_returned, execute_returned, close_raised, info_raised, "
                + "limit_raised, call_raised, interrupt_raised};"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {0, 0}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; result = sqlite_close(1); return {result, length(sqlite_handles())};"));
  }

  @Test
  void interruptsActiveSqliteQueryAfterExactDelayedForkAndSuspend() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    List<String> output =
        runtime.executeLine(
            connectionId,
            """
            ; h = sqlite_open(":memory:");
            fork (1)
              suspend(1);
              sqlite_interrupt(h);
            endfork
            result = sqlite_query(h, "WITH RECURSIVE cnt(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM cnt WHERE x < 1000) SELECT count(*) FROM cnt AS a, cnt AS b, cnt AS c, cnt AS d;");
            sqlite_close(h);
            return result;
            """);

    assertTrue(
        output.stream().anyMatch(line -> line.toLowerCase(Locale.ROOT).contains("interrupt")),
        output::toString);
  }

  private static void executeSetup(
      MooRuntime runtime, long connectionId, String name, String value) {
    String source =
        "; try add_property(#0, \""
            + name
            + "\", "
            + value
            + ", {#0, \"rc\"}); except (ANY) return 0; endtry";
    assertEquals(
        List.of(
            CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 0}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, source));
  }
}
