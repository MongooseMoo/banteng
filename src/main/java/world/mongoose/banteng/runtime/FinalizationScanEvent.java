package world.mongoose.banteng.runtime;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/** Flight Recorder measurement of one existing finalization reachability pass. */
@Name("world.mongoose.banteng.FinalizationScan")
@Label("Finalization Reachability Scan")
@Category({"Banteng", "Runtime"})
@Description("Reachability pass duration and the counts already collected by that pass")
@StackTrace(false)
public final class FinalizationScanEvent extends Event {
  public long taskId;
  public int traversedValues;
  public int reachableAnonymous;
  public int reachableWaifs;
  public int worldAnonymous;
  public int worldWaifs;
}
