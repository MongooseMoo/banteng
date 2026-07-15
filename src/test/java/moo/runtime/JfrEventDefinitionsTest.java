package moo.runtime;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import moo.persistence.CheckpointEvent;
import moo.world.VersionRetentionEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JfrEventDefinitionsTest {
  @Test
  void recordsNamedRuntimeWorldAndCheckpointEvents(@TempDir Path temporaryDirectory)
      throws IOException {
    Path recordingPath = temporaryDirectory.resolve("banteng-events.jfr");

    try (Recording recording = new Recording()) {
      recording.enable(TaskSegmentEvent.class).withoutThreshold();
      recording.enable(WorldCommitEvent.class).withoutThreshold();
      recording.enable(WorldConflictEvent.class).withoutThreshold();
      recording.enable(TaskRetryEvent.class).withoutThreshold();
      recording.enable(TaskFallbackEvent.class).withoutThreshold();
      recording.enable(VersionRetentionEvent.class).withoutThreshold();
      recording.enable(CheckpointEvent.class).withoutThreshold();
      recording.start();

      TaskSegmentEvent segment = new TaskSegmentEvent();
      segment.taskId = 17;
      segment.ticket = 23;
      segment.queueDelayNanos = 29;
      segment.publicationWaitNanos = 31;
      segment.begin();
      segment.commit();

      WorldCommitEvent commit = new WorldCommitEvent();
      commit.ticket = 23;
      commit.revision = 37;
      commit.writeCount = 2;
      commit.effectCount = 3;
      commit.commit();

      WorldConflictEvent conflict = new WorldConflictEvent();
      conflict.taskId = 17;
      conflict.ticket = 23;
      conflict.cause = "PRNG_RECORD";
      conflict.commit();

      TaskRetryEvent retry = new TaskRetryEvent();
      retry.taskId = 17;
      retry.ticket = 23;
      retry.attempt = 2;
      retry.cause = "PRNG_RECORD";
      retry.commit();

      TaskFallbackEvent fallback = new TaskFallbackEvent();
      fallback.taskId = 17;
      fallback.ticket = 23;
      fallback.reason = "PUBLICATION_SENSITIVE_WALL_CLOCK";
      fallback.commit();

      VersionRetentionEvent retention = new VersionRetentionEvent();
      retention.currentRevision = 37;
      retention.oldestRetainedRevision = 31;
      retention.retainedRevisionCount = 7;
      retention.activeSnapshotCount = 2;
      retention.commit();

      CheckpointEvent checkpoint = new CheckpointEvent();
      checkpoint.revision = 37;
      checkpoint.objectCount = 41;
      checkpoint.taskCount = 5;
      checkpoint.bytesWritten = 43;
      checkpoint.success = true;
      checkpoint.begin();
      checkpoint.commit();

      recording.stop();
      recording.dump(recordingPath);
    }

    List<RecordedEvent> events = RecordingFile.readAllEvents(recordingPath);
    Map<String, RecordedEvent> eventsByName = new HashMap<>();
    for (RecordedEvent event : events) {
      eventsByName.put(event.getEventType().getName(), event);
    }

    assertEquals(
        Set.of(
            "moo.TaskSegment",
            "moo.WorldCommit",
            "moo.WorldConflict",
            "moo.TaskRetry",
            "moo.TaskFallback",
            "moo.VersionRetention",
            "moo.Checkpoint"),
        eventsByName.keySet());

    RecordedEvent recordedSegment = requireNonNull(eventsByName.get("moo.TaskSegment"));
    assertEquals(17, recordedSegment.getLong("taskId"));
    assertEquals(23, recordedSegment.getLong("ticket"));
    assertEquals(29, recordedSegment.getDuration("queueDelay").toNanos());
    assertEquals(31, recordedSegment.getDuration("publicationWait").toNanos());
    assertFalse(recordedSegment.getDuration().isNegative());

    RecordedEvent recordedConflict = requireNonNull(eventsByName.get("moo.WorldConflict"));
    assertEquals("PRNG_RECORD", recordedConflict.getString("cause"));

    RecordedEvent recordedFallback = requireNonNull(eventsByName.get("moo.TaskFallback"));
    assertEquals("PUBLICATION_SENSITIVE_WALL_CLOCK", recordedFallback.getString("reason"));

    RecordedEvent recordedRetention = requireNonNull(eventsByName.get("moo.VersionRetention"));
    assertEquals(7, recordedRetention.getLong("retainedRevisionCount"));
    assertEquals(2, recordedRetention.getLong("activeSnapshotCount"));

    RecordedEvent recordedCheckpoint = requireNonNull(eventsByName.get("moo.Checkpoint"));
    assertTrue(recordedCheckpoint.getBoolean("success"));
    assertTrue(recordedCheckpoint.getDuration().compareTo(Duration.ZERO) >= 0);
  }
}
