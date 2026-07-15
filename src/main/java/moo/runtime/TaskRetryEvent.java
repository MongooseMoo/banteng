package moo.runtime;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/** Flight Recorder definition for a speculative task-segment retry. */
@Name("moo.TaskRetry")
@Label("MOO Task Retry")
@Category({"Banteng", "Runtime"})
@Description("Retry of a conflicted speculative MOO task segment")
@StackTrace(false)
public final class TaskRetryEvent extends Event {
  @Label("Task ID")
  public long taskId;

  @Label("Publication Ticket")
  public long ticket;

  @Label("Attempt")
  public int attempt;

  @Label("Retry Cause")
  public String cause = "";
}
