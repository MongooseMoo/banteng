package world.mongoose.banteng.runtime;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinHosts;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.vm.MooVm;
import world.mongoose.banteng.vm.VmSnapshot;
import world.mongoose.banteng.vm.VmState;
import world.mongoose.banteng.world.WorldSnapshot;
import world.mongoose.banteng.world.WorldTxn;

/** Runtime callbacks used by the deterministic publication scheduler. */
public interface SchedulerRuntime {
  OptionalLong connectionPlayer(long connectionId);
  boolean loginTimeoutIsCurrent(long connectionId, long generation);
  OptionalLong inputTaskPlayer(long connectionId, String line);
  MooRuntime.AttemptContext openAttempt(WorldTxn transaction, long taskId);
  BuiltinHosts builtinHosts(MooRuntime.AttemptContext context);
  MooRuntime.RuntimeStep execute(MooRuntime.AttemptContext context,
      MooRuntime.RuntimeContinuation continuation, Optional<VmSnapshot> completedVm);
  void collectAfterAnonymousFinalization(MooRuntime.AttemptContext context,
      AnonymousObjectValue target, List<VmSnapshot> otherTaskRoots,
      boolean startNextFinalizationWave);
  VmState startBackgroundTask(MooRuntime.AttemptContext context, VmSnapshot initial);
  MooVm vm();
  BuiltinCatalog builtins();
  void publishVmState(MooRuntime.AttemptContext context,
      VmState task, long taskPlayer, boolean collectAfterCompletion,
      List<VmSnapshot> otherTaskRoots);
  MooRuntime.RuntimeStep startUncaughtError(MooRuntime.AttemptContext context, VmState state);
  Optional<List<String>> suspendedInputOutput(MooRuntime.AttemptContext context,
      MooRuntime.RuntimeContinuation continuation, VmState state);
  void writeConnectionOutput(long connectionId, List<String> output);
  void writeDetachedOutput(long player, List<String> output);
  boolean sessionsAreCurrent(MooRuntime.AttemptContext context);
  void publishAttempt(MooRuntime.AttemptContext context, WorldSnapshot committedWorld);
  List<MooRuntime.RuntimeStep> takeSpawnedSteps(MooRuntime.AttemptContext context);
}
