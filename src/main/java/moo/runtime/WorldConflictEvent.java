package moo.runtime;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/** Flight Recorder definition for a rejected speculative world transaction. */
@Name("moo.WorldConflict")
@Label("World Conflict")
@Category({"Banteng", "Runtime"})
@Description("Conflict that prevents publication of a speculative MOO task segment")
@StackTrace(false)
public final class WorldConflictEvent extends Event {
  @Label("Task ID")
  public long taskId;

  @Label("Publication Ticket")
  public long ticket;

  @Label("Conflict Cause")
  public String cause = "";
}
