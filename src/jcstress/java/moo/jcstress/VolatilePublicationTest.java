package moo.jcstress;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

/** Proves that the pinned jcstress harness can grade a Java Memory Model publication test. */
@JCStressTest
@Outcome(
    id = {"0, 0", "0, 42"},
    expect = Expect.ACCEPTABLE,
    desc = "The observer read the flag before publication.")
@Outcome(
    id = "1, 42",
    expect = Expect.ACCEPTABLE,
    desc = "The volatile flag published the preceding payload write.")
@Outcome(
    id = "1, 0",
    expect = Expect.FORBIDDEN,
    desc = "A volatile publication must make the preceding payload write visible.")
@State
public class VolatilePublicationTest {
  private int payload;
  private volatile int published;

  @Actor
  public void publish() {
    payload = 42;
    published = 1;
  }

  @Actor
  public void observe(II_Result result) {
    result.r1 = published;
    result.r2 = payload;
  }
}
