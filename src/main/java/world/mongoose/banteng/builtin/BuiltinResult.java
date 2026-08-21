package world.mongoose.banteng.builtin;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import world.mongoose.banteng.builtin.BuiltinCatalog.ConnectionOption;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.StringValue;

/** One explicit builtin value, MOO error, dynamic call, or staged effect. */
public sealed interface BuiltinResult {
  /** Completes a builtin with one MOO value. */
  record Value(MooValue value) implements BuiltinResult {
    public Value {
      Objects.requireNonNull(value, "value");
    }
  }

  /** Invokes initialize after creating an object or anonymous object. */
  record Initialize(MooValue created, ListValue arguments) implements BuiltinResult {
    public Initialize {
      Objects.requireNonNull(created, "created");
      Objects.requireNonNull(arguments, "arguments");
    }
  }

  /** Requests a non-terminating checkpoint. */
  record Checkpoint() implements BuiltinResult {}

  /** Requests a checkpoint followed by orderly shutdown. */
  record Shutdown() implements BuiltinResult {}

  /** Requests a panic checkpoint followed by process abort. */
  record Panic(String message) implements BuiltinResult {
    public Panic {
      Objects.requireNonNull(message, "message");
    }
  }

  /** Suspends the current task for a delay. */
  record Suspend(double seconds) implements BuiltinResult {}

  /** Raises one MOO error without details. */
  record ErrorResult(ErrorValue error) implements BuiltinResult {
    public ErrorResult {
      Objects.requireNonNull(error, "error");
    }
  }

  /** Suspends until bounded host work completes. */
  record HostWork(Callable<BuiltinResult> work) implements BuiltinResult {
    public HostWork {
      Objects.requireNonNull(work, "work");
    }
  }

  /** Changes the current task's thread mode and returns zero. */
  record ThreadMode(boolean enabled) implements BuiltinResult {}

  /** Aborts remaining-seconds exhaustion without completing the builtin. */
  record SecondsAbort() implements BuiltinResult {}

  /** Raises one MOO error with structured details. */
  record RaisedError(ErrorValue error, ListValue details) implements BuiltinResult {
    public RaisedError {
      Objects.requireNonNull(error, "error");
      Objects.requireNonNull(details, "details");
    }
  }

  /** Compiles and evaluates source in a new frame. */
  record DynamicEval(String source) implements BuiltinResult {
    public DynamicEval {
      Objects.requireNonNull(source, "source");
    }
  }

  /** Stages one output line and returns one. */
  record Output(String line) implements BuiltinResult {
    public Output {
      Objects.requireNonNull(line, "line");
    }
  }

  /** Stages one connection-targeted notification and returns one. */
  record Notify(long connectionId, String line, boolean noFlush, boolean noNewline)
      implements BuiltinResult {
    public Notify {
      Objects.requireNonNull(line, "line");
    }
  }

  /** Switches the current player and returns zero. */
  record SwitchPlayer(long player) implements BuiltinResult {}

  /** Changes the current programmer and returns zero. */
  record Programmer(long programmer) implements BuiltinResult {}

  /** Moves one object to one destination and position as one atomic effect. */
  record Move(long object, long destination, long position) implements BuiltinResult {}

  /** Recycles one ordinary object. */
  record Recycle(long object) implements BuiltinResult {}

  /** Recycles one anonymous object. */
  record RecycleAnonymous(AnonymousObjectValue object) implements BuiltinResult {
    public RecycleAnonymous {
      Objects.requireNonNull(object, "object");
    }
  }

  /** Disconnects one player and returns zero. */
  record BootPlayer(long target) implements BuiltinResult {}

  /** Applies one connection option and returns zero. */
  record SetConnectionOption(long target, ConnectionOption option, MooValue value)
      implements BuiltinResult {
    public SetConnectionOption {
      Objects.requireNonNull(option, "option");
      Objects.requireNonNull(value, "value");
    }
  }

  /** Forces one input line and returns zero. */
  record ForceInput(long target, String input) implements BuiltinResult {
    public ForceInput {
      Objects.requireNonNull(input, "input");
    }
  }

  /** Creates a value completion. */
  static BuiltinResult value(MooValue value) {
    return new Value(value);
  }

  /** Creates an error completion. */
  static BuiltinResult error(ErrorValue error) {
    return new ErrorResult(error);
  }

  /** Creates a bounded host-work suspension. */
  static BuiltinResult hostWork(Callable<BuiltinResult> work) {
    return new HostWork(work);
  }

  /** Creates an error completion with structured raise details. */
  static BuiltinResult raised(ErrorValue error, StringValue message, MooValue value) {
    return new RaisedError(
        error, new ListValue(List.of(message, value, new ListValue(List.of()))));
  }

}
