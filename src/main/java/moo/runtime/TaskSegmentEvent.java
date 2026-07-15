package moo.runtime;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;

/** Flight Recorder definition for one explicit MOO VM segment execution. */
@Name("moo.TaskSegment")
@Label("MOO Task Segment")
@Category({"Banteng", "Runtime"})
@Description("Execution of one explicit MOO VM segment; event duration is execution time")
@StackTrace(false)
public final class TaskSegmentEvent extends Event {
  @Label("Task ID")
  public long taskId;

  @Label("Publication Ticket")
  public long ticket;

  @Name("queueDelay")
  @Label("Queue Delay")
  @Timespan
  public long queueDelayNanos;

  @Name("publicationWait")
  @Label("Publication Wait")
  @Timespan
  public long publicationWaitNanos;
}
