package world.mongoose.banteng.vm;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import world.mongoose.banteng.builtin.CheckpointRequest;
import world.mongoose.banteng.builtin.family.ConnectionBuiltins;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;

/** Ordered host effects staged by one task attempt. */
public final class EffectOutbox {
  final Queue<String> output = new ArrayDeque<>();
  final Queue<ConnectionBuiltins.ConnectionOptionRequest> connectionOptionRequests = new ArrayDeque<>();
  final Queue<Long> bootPlayerTargets = new ArrayDeque<>();
  final Queue<ConnectionBuiltins.ForcedInputRequest> forcedInputRequests = new ArrayDeque<>();
  final Queue<ConnectionBuiltins.NotificationRequest> notificationRequests = new ArrayDeque<>();
  final Queue<CheckpointRequest> checkpointRequests = new ArrayDeque<>();
  final Queue<AnonymousObjectValue> anonymousCollectionDeferrals = new ArrayDeque<>();
  /** Returns ordered output staged by this execution. */
  public List<String> output() {
    return List.copyOf(output);
  }

  /** Clears output already delivered when the foreground input task suspended. */
  public void clearOutput() {
    output.clear();
  }

  /** Stages an output line, including runtime output without an attached transport. */
  public void stageOutput(String line) {
    output.add(line);
  }

  void stageConnectionOptionRequest(ConnectionBuiltins.ConnectionOptionRequest request) {
    connectionOptionRequests.add(request);
  }

  /** Removes and returns connection-option requests in their task execution order. */
  public List<ConnectionBuiltins.ConnectionOptionRequest> drainConnectionOptionRequests() {
    return drain(connectionOptionRequests);
  }

  void stageBootPlayerTarget(long target) {
    bootPlayerTargets.add(target);
  }

  /** Removes and returns boot-player targets in their task execution order. */
  public List<Long> drainBootPlayerTargets() {
    return drain(bootPlayerTargets);
  }

  void stageForcedInputRequest(ConnectionBuiltins.ForcedInputRequest request) {
    forcedInputRequests.add(request);
  }

  /** Removes and returns forced-input requests in their task execution order. */
  public List<ConnectionBuiltins.ForcedInputRequest> drainForcedInputRequests() {
    return drain(forcedInputRequests);
  }

  void stageNotificationRequest(ConnectionBuiltins.NotificationRequest request) {
    notificationRequests.add(request);
  }

  /** Removes and returns connection notifications in their task execution order. */
  public List<ConnectionBuiltins.NotificationRequest> drainNotificationRequests() {
    return drain(notificationRequests);
  }

  /** Projects this attempt's staged notification sequence over live queued bytes. */
  public long stagedBufferedOutputLength(long connectionId, long liveQueuedBytes) {
    long queuedBytes = liveQueuedBytes;
    for (ConnectionBuiltins.NotificationRequest request : notificationRequests) {
      if (request.connectionId() != connectionId) {
        continue;
      }
      queuedBytes =
          Math.addExact(
              queuedBytes,
              request.line().length() + (request.noNewline() ? 0L : 2L));
    }
    return queuedBytes;
  }

  void stageCheckpointRequest(CheckpointRequest request) {
    checkpointRequests.add(request);
  }

  /** Removes and returns checkpoint requests in task execution order. */
  public List<CheckpointRequest> drainCheckpointRequests() {
    return drain(checkpointRequests);
  }

  void deferAnonymousCollection(List<AnonymousObjectValue> identities) {
    for (AnonymousObjectValue identity : identities) {
      if (!anonymousCollectionDeferrals.contains(identity)) {
        anonymousCollectionDeferrals.add(identity);
      }
    }
  }

  /** Returns anonymous identities excluded from collection for this task's publication. */
  public List<AnonymousObjectValue> anonymousCollectionDeferrals() {
    return List.copyOf(anonymousCollectionDeferrals);
  }


  private static <T> List<T> drain(Queue<T> queue) {
    List<T> values = List.copyOf(queue);
    queue.clear();
    return values;
  }
}
