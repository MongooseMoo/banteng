package world.mongoose.banteng.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ObjectFlagsTest {
  @Test
  void permanentBitsMatchTheToastObjectFlagLayout() {
    assertEquals(1, ObjectFlags.FLAG_USER);
    assertEquals(1 << 1, ObjectFlags.FLAG_PROGRAMMER);
    assertEquals(1 << 2, ObjectFlags.FLAG_WIZARD);
    assertEquals(1 << 3, ObjectFlags.FLAG_OBSOLETE_1);
    assertEquals(1 << 4, ObjectFlags.FLAG_READ);
    assertEquals(1 << 5, ObjectFlags.FLAG_WRITE);
    assertEquals(1 << 6, ObjectFlags.FLAG_OBSOLETE_2);
    assertEquals(1 << 7, ObjectFlags.FLAG_FERTILE);
    assertEquals(1 << 8, ObjectFlags.FLAG_ANONYMOUS);
    assertEquals(1 << 9, ObjectFlags.FLAG_INVALID);
    assertEquals(1 << 10, ObjectFlags.FLAG_RECYCLED);
  }

  @Test
  void predicatesTestTheirNamedBits() {
    int allFlags =
        ObjectFlags.FLAG_PROGRAMMER
            | ObjectFlags.FLAG_WIZARD
            | ObjectFlags.FLAG_READ
            | ObjectFlags.FLAG_WRITE
            | ObjectFlags.FLAG_FERTILE;

    assertTrue(ObjectFlags.isProgrammer(allFlags));
    assertTrue(ObjectFlags.isWizard(allFlags));
    assertTrue(ObjectFlags.isReadable(allFlags));
    assertTrue(ObjectFlags.isWritable(allFlags));
    assertTrue(ObjectFlags.isFertile(allFlags));
    assertFalse(ObjectFlags.isProgrammer(allFlags & ~ObjectFlags.FLAG_PROGRAMMER));
    assertFalse(ObjectFlags.isWizard(allFlags & ~ObjectFlags.FLAG_WIZARD));
    assertFalse(ObjectFlags.isReadable(allFlags & ~ObjectFlags.FLAG_READ));
    assertFalse(ObjectFlags.isWritable(allFlags & ~ObjectFlags.FLAG_WRITE));
    assertFalse(ObjectFlags.isFertile(allFlags & ~ObjectFlags.FLAG_FERTILE));
  }
}
