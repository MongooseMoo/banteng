package moo.persistence;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/** Flight Recorder definition for one durable database checkpoint. */
@Name("moo.Checkpoint")
@Label("Database Checkpoint")
@Category({"Banteng", "Persistence"})
@Description("Writing one committed world revision and its task snapshots")
@StackTrace(false)
public final class CheckpointEvent extends Event {
  @Label("Committed Revision")
  public long revision;

  @Label("Object Count")
  public long objectCount;

  @Label("Task Count")
  public long taskCount;

  @Label("Bytes Written")
  public long bytesWritten;

  @Label("Successful")
  public boolean success;
}
