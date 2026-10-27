package world.mongoose.banteng.persistence;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import world.mongoose.banteng.world.ActiveConnection;
import world.mongoose.banteng.world.Checkpoint;
import world.mongoose.banteng.world.DurableTask;
import world.mongoose.banteng.world.WorldSnapshot;

/** Database input and canonical checkpoint output operations. */
public interface DbCodec {
  Checkpoint read(Path database) throws IOException;

  void write(BufferedWriter output, WorldSnapshot world,
      List<? extends DurableTask> tasks, List<ActiveConnection> activeConnections)
      throws IOException;

  void writeAtomic(Path checkpoint, WorldSnapshot world,
      List<? extends DurableTask> tasks, List<ActiveConnection> activeConnections)
      throws IOException;

  void writePanic(Path checkpoint, WorldSnapshot world,
      List<? extends DurableTask> tasks, List<ActiveConnection> activeConnections)
      throws IOException;
}
