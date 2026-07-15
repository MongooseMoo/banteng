package moo.world;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/** Flight Recorder definition for committed-world version retention. */
@Name("moo.VersionRetention")
@Label("World Version Retention")
@Category({"Banteng", "World"})
@Description("Committed revisions retained for active segments and checkpoints")
@StackTrace(false)
public final class VersionRetentionEvent extends Event {
  @Label("Current Revision")
  public long currentRevision;

  @Label("Oldest Retained Revision")
  public long oldestRetainedRevision;

  @Label("Retained Revision Count")
  public long retainedRevisionCount;

  @Label("Active Snapshot Count")
  public long activeSnapshotCount;
}
