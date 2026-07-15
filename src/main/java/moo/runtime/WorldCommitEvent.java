package moo.runtime;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/** Flight Recorder definition for ticket-ordered world publication. */
@Name("moo.WorldCommit")
@Label("World Commit")
@Category({"Banteng", "Runtime"})
@Description("Ticket-ordered publication of one committed world revision")
@StackTrace(false)
public final class WorldCommitEvent extends Event {
  @Label("Publication Ticket")
  public long ticket;

  @Label("Committed Revision")
  public long revision;

  @Label("Write Count")
  public int writeCount;

  @Label("Effect Count")
  public int effectCount;
}
