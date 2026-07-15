package moo.runtime;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/** Flight Recorder definition for an exclusive publication-lane rerun. */
@Name("moo.TaskFallback")
@Label("MOO Task Fallback")
@Category({"Banteng", "Runtime"})
@Description("Rerun caused by an irrevocable or publication-sensitive operation")
@StackTrace(false)
public final class TaskFallbackEvent extends Event {
  @Label("Task ID")
  public long taskId;

  @Label("Publication Ticket")
  public long ticket;

  @Label("Fallback Reason")
  public String reason = "";
}
