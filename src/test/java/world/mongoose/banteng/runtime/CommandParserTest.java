package world.mongoose.banteng.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.Prepositions;
import world.mongoose.banteng.world.PropertyPerms;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldTxn;

final class CommandParserTest {
  @Test
  void tokenizesLikeToastParseIntoWords() {
    // Expected values observed by compiling the original parse_cmd.cc:34-79 function.
    assertEquals(List.of("foo\tbar", "baz"), CommandParser.words("foo\tbar baz"));
    assertEquals(List.of("foo"), CommandParser.words("foo\\"));
    assertEquals(List.of(""), CommandParser.words("\"\""));
    assertEquals(List.of("abcd efgh", "ij"), CommandParser.words("ab\"cd ef\"gh ij"));
    assertEquals(List.of("", "tail"), CommandParser.words("  \"\" tail  "));
    assertEquals(List.of("a b", "c\"d"), CommandParser.words("a\\ b c\\\"d"));
    assertEquals(List.of(), CommandParser.words("   "));
  }

  @Test
  void preservesShortcutSpacingAndOriginalGateWords() {
    assertEquals(List.of(":", "waves"), CommandParser.words(":  waves"));
    CommandParser.ParsedCommand parsed = CommandParser.parse("  :  waves");
    assertEquals("emote", parsed.verb());
    assertEquals("  waves", parsed.argstr());
    assertEquals(List.of("emote", "waves"), parsed.words());
    assertEquals(" hi", CommandParser.parse("\" hi").argstr());
    assertEquals("return 1;", CommandParser.parse(";return 1;").argstr());
  }

  @Test
  void parsesQuotedVerbsAndOrderedPrepositions() {
    CommandParser.ParsedCommand parsed = CommandParser.parse("  pu\"t\"  red box in front of me");
    assertEquals("put", parsed.verb());
    assertEquals("red box in front of me", parsed.argstr());
    assertEquals("red box", parsed.dobjstr());
    assertEquals("in front of", parsed.prepstr());
    assertEquals("me", parsed.iobjstr());
    assertEquals(2, parsed.preposition());
    assertEquals("off", CommandParser.parse("take hat off of me").prepstr());
    assertEquals("of me", CommandParser.parse("take hat off of me").iobjstr());
    assertEquals("x\ty", CommandParser.parse("verb x\ty").dobjstr());
    assertEquals("", CommandParser.parse("   ").verb());
  }

  @Test
  void sharesPersistentPrepositionCodesWithVerbArguments() {
    for (int code = 0; code < Prepositions.ALIASES.size(); code++) {
      assertEquals(code, Prepositions.parse(Prepositions.format(code)));
      for (String alias : Prepositions.ALIASES.get(code)) {
        assertEquals(code, Prepositions.parse(alias));
        assertEquals(code, CommandParser.parse("put thing " + alias + " me").preposition());
      }
    }
    assertEquals(-1, Prepositions.parse("none"));
    assertEquals(-2, Prepositions.parse("any"));
    assertEquals(14, Prepositions.parse("#14"));
    assertEquals(Integer.MIN_VALUE, Prepositions.parse("15"));
  }

  @Test
  void matchesObjectIdentityAcrossContentsAndAliases() {
    WorldObject room = object(0, "room", -1, List.of(1L, 3L), List.of());
    WorldObject player = object(1, "player", 0, List.of(2L), List.of());
    WorldProperty aliases = new WorldProperty("aliases",
        new ListValue(List.of(StringValue.of("box"), StringValue.of("box"))), 1, new PropertyPerms(0), false, true);
    WorldObject box = object(2, "box", 1, List.of(), List.of(aliases));
    WorldObject other = object(3, "boxcar", 0, List.of(), List.of());
    WorldTxn owner = new WorldTxn(List.of(1L), List.of(room, player, box, other));
    try (WorldTxn world = owner.begin()) {
      assertEquals(2, CommandParser.matchObject(1, "box", world));
      assertEquals(-2, CommandParser.matchObject(1, "bo", world));
      assertEquals(3, CommandParser.matchObject(1, "boxc", world));
      assertEquals(1, CommandParser.matchObject(1, "me", world));
      assertEquals(0, CommandParser.matchObject(1, "here", world));
      assertEquals(0, CommandParser.matchObject(1, "#", world));
      assertEquals(2, CommandParser.matchObject(-999, "# +2", world));
      assertEquals(-3, CommandParser.matchObject(1, "#2 ", world));
      assertEquals(-3, CommandParser.matchObject(1, "# ", world));
      assertEquals(-3, CommandParser.matchObject(-999, "me", world));
      assertEquals(-1, CommandParser.matchObject(-999, "", world));
    }
  }

  @Test
  void duplicateExactMatchesAreAmbiguous() {
    WorldObject room = object(0, "room", -1, List.of(1L, 2L, 3L), List.of());
    WorldObject player = object(1, "player", 0, List.of(), List.of());
    WorldTxn owner = new WorldTxn(List.of(1L), List.of(room, player,
        object(2, "box", 0, List.of(), List.of()),
        object(3, "box", 0, List.of(), List.of())));
    try (WorldTxn world = owner.begin()) {
      assertEquals(-2, CommandParser.matchObject(1, "box", world));
      assertEquals(-3, CommandParser.matchObject(1, "absent", world));
    }
  }

  @Test
  void matchesLatin1NamesWithoutFoldingTheirHighBytes() {
    String upper = StringValue.of(new byte[] {(byte) 0xC9, 'C', 'L', 'A', 'I', 'R'}).text();
    String lower = StringValue.of(new byte[] {(byte) 0xE9, 'c', 'l', 'a', 'i', 'r'}).text();
    WorldObject room = object(0, "room", -1, List.of(1L, 2L, 3L), List.of());
    WorldObject player = object(1, "player", 0, List.of(), List.of());
    WorldTxn owner = new WorldTxn(List.of(1L), List.of(room, player,
        object(2, upper, 0, List.of(), List.of()),
        object(3, lower, 0, List.of(), List.of())));
    try (WorldTxn world = owner.begin()) {
      assertEquals(2, CommandParser.matchObject(1,
          StringValue.of(new byte[] {(byte) 0xC9, 'c', 'l', 'a', 'i', 'r'}).text(), world));
      assertEquals(3, CommandParser.matchObject(1,
          StringValue.of(new byte[] {(byte) 0xE9, 'C', 'L', 'A', 'I', 'R'}).text(), world));
      assertEquals(2, CommandParser.matchObject(1,
          StringValue.of(new byte[] {(byte) 0xC9, 'c', 'l'}).text(), world));
      assertEquals(3, CommandParser.matchObject(1,
          StringValue.of(new byte[] {(byte) 0xE9, 'C', 'L'}).text(), world));
    }
  }

  @Test
  void aliasesUseAsciiBytePrefixesAndKeepDuplicateMatchesOnOneIdentity() {
    StringValue alias = StringValue.of(new byte[] {(byte) 0xC9, 'B', 'O', 'X'});
    WorldProperty aliases = new WorldProperty("aliases",
        new ListValue(List.of(alias, alias)), 1, new PropertyPerms(0), false, true);
    WorldObject room = object(0, "room", -1, List.of(1L, 2L), List.of());
    WorldObject player = object(1, "player", 0, List.of(), List.of());
    WorldTxn owner = new WorldTxn(List.of(1L), List.of(room, player,
        object(2, "target", 0, List.of(), List.of(aliases))));
    try (WorldTxn world = owner.begin()) {
      assertEquals(2, CommandParser.matchObject(1,
          StringValue.of(new byte[] {(byte) 0xC9, 'b', 'o', 'x'}).text(), world));
      assertEquals(2, CommandParser.matchObject(1,
          StringValue.of(new byte[] {(byte) 0xC9, 'b'}).text(), world));
      assertEquals(-3, CommandParser.matchObject(1,
          StringValue.of(new byte[] {(byte) 0xE9, 'B', 'O', 'X'}).text(), world));
      assertEquals(-3, CommandParser.matchObject(1,
          StringValue.of(new byte[] {(byte) 0xE9, 'B'}).text(), world));
    }
  }

  private static WorldObject object(long id, String name, long location,
      List<Long> contents, List<WorldProperty> properties) {
    return new WorldObject(id, name, 0, 1, location, -1, contents, List.of(), List.of(), properties);
  }
}
