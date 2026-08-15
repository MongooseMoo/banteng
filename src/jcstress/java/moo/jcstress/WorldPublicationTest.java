package moo.jcstress;

import java.util.List;
import moo.value.MooValue.IntegerValue;
import moo.world.WorldObject;
import moo.world.WorldProperty;
import moo.world.WorldTxn;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

/** Readers must observe either side of one complete committed world revision. */
@JCStressTest
@Outcome(id = "0, 0", expect = Expect.ACCEPTABLE, desc = "Reader opened before publication.")
@Outcome(id = "1, 1", expect = Expect.ACCEPTABLE, desc = "Reader opened after publication.")
@Outcome(
    id = {"0, 1", "1, 0"},
    expect = Expect.FORBIDDEN,
    desc = "A reader must not observe a partially published world revision.")
@State
public class WorldPublicationTest {
  private final WorldTxn world =
      new WorldTxn(
          List.of(),
          List.of(
              new WorldObject(
                  0,
                  "publication",
                  0,
                  0,
                  -1,
                  List.of(),
                  List.of(),
                  List.of(),
                  List.of(),
                  List.of(
                      new WorldProperty("left", new IntegerValue(0), 0, 3, false, true),
                      new WorldProperty("right", new IntegerValue(0), 0, 3, false, true)))));

  @Actor
  public void publish() {
    try (WorldTxn transaction = world.begin()) {
      transaction.writeObjectProperty(0, "left", new IntegerValue(1));
      transaction.writeObjectProperty(0, "right", new IntegerValue(1));
      transaction.commit();
    }
  }

  @Actor
  public void observe(II_Result result) {
    try (WorldTxn transaction = world.begin()) {
      result.r1 =
          (int)
              ((IntegerValue) transaction.readObjectProperty(0, "left").orElseThrow()).value();
      result.r2 =
          (int)
              ((IntegerValue) transaction.readObjectProperty(0, "right").orElseThrow()).value();
    }
  }
}
