package world.mongoose.banteng.builtin.family;
import java.util.Optional;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinRegistrar;
import world.mongoose.banteng.builtin.BuiltinPermissionRule;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;
import world.mongoose.banteng.builtin.ConfinedFileRoot;


import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import org.jspecify.annotations.Nullable;

/** Catalog-scoped owner of Toast-compatible transient file handles. */
public final class FileIoBuiltins {
  private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
  private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);
  private static final Set<ArgType> STRING = Set.of(ArgType.STRING);
  public void register(List<BuiltinSpec> entries) {
    BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
    registrar.builtin("file_handles", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> handles());
    registrar.builtin("file_open", List.of(new CallShape(List.of(STRING, STRING), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> open(call.arguments()));
    registrar.builtin("file_close", List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> close(call.arguments()));
    registrar.builtin("file_name", List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> name(call.arguments()));
    registrar.builtin("file_openmode", List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> openMode(call.arguments()));
    registrar.builtin("file_readline", List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> readLine(call.arguments()));
    registrar.builtin("file_readlines", List.of(new CallShape(List.of(INTEGER, INTEGER, INTEGER), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> readLines(call.arguments()));
    registrar.builtin("file_writeline", List.of(new CallShape(List.of(INTEGER, STRING), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> writeLine(call.arguments()));
    registrar.builtin("file_grep", List.of(new CallShape(List.of(INTEGER, STRING), List.of(INTEGER), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> grep(call.arguments()));
    registrar.builtin("file_read", List.of(new CallShape(List.of(INTEGER, INTEGER), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> read(call.arguments()));
    registrar.builtin("file_write", List.of(new CallShape(List.of(INTEGER, STRING), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> write(call.arguments()));
    registrar.builtin("file_flush", List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> flush(call.arguments()));
    registrar.builtin("file_seek", List.of(new CallShape(List.of(INTEGER, INTEGER, STRING), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> seek(call.arguments()));
    registrar.builtin("file_tell", List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> tell(call.arguments()));
    registrar.builtin("file_eof", List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> eof(call.arguments()));
    registrar.builtin("file_count_lines", List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> countLines(call.arguments()));
    registrar.builtin("file_list", List.of(new CallShape(List.of(STRING), List.of(ANY), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> list(call.arguments()));
    registrar.builtin("file_mkdir", List.of(new CallShape(List.of(STRING), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> mkdir(call.arguments()));
    registrar.builtin("file_rmdir", List.of(new CallShape(List.of(STRING), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> rmdir(call.arguments()));
    registrar.builtin("file_remove", List.of(new CallShape(List.of(STRING), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> remove(call.arguments()));
    registrar.builtin("file_rename", List.of(new CallShape(List.of(STRING, STRING), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> rename(call.arguments()));
    registrar.builtin("file_chmod", List.of(new CallShape(List.of(STRING, STRING), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> chmod(call.arguments()));
    registrar.builtin("file_size", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> size(call.arguments()));
    registrar.builtin("file_mode", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> mode(call.arguments()));
    registrar.builtin("file_type", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> type(call.arguments()));
    registrar.builtin("file_last_access", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> lastAccess(call.arguments()));
    registrar.builtin("file_last_modify", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> lastModify(call.arguments()));
    registrar.builtin("file_last_change", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> lastChange(call.arguments()));
    registrar.builtin("file_stat", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> stat(call.arguments()));
  }

  private static final int MAX_HANDLES = 256;
  private final Map<Long, Handle> handles = new LinkedHashMap<>();
  private final ConfinedFileRoot root;
  private long nextHandle = 1;

  public FileIoBuiltins(ConfinedFileRoot root) {
    this.root = root;
  }

  private synchronized BuiltinResult open(List<MooValue> arguments) {
    String name = ((StringValue) arguments.get(0)).text();
    Mode mode = Mode.parse(((StringValue) arguments.get(1)).text());
    if (mode == null) {
      return invalidArgument();
    }
    Path path = resolve(name);
    if (path == null) {
      return invalidArgument();
    }
    if (handles.size() >= MAX_HANDLES) {
      return BuiltinResult.error(ErrorValue.E_QUOTA);
    }
    try {
      RandomAccessFile file = new RandomAccessFile(path.toFile(), mode.readOnly ? "r" : "rw");
      if (mode.truncate) {
        file.setLength(0);
      }
      if (mode.append) {
        file.seek(file.length());
      }
      long id = allocateHandle();
      handles.put(id, new Handle(name, path, mode, file));
      return BuiltinResult.value(new IntegerValue(id));
    } catch (IOException failure) {
      return fileError();
    }
  }

  private synchronized BuiltinResult close(List<MooValue> arguments) {
    long id = integer(arguments.get(0));
    Handle handle = handles.remove(id);
    if (handle == null) {
      return invalidArgument();
    }
    try {
      handle.file.close();
      if (handles.isEmpty()) {
        nextHandle = 1;
      }
      return BuiltinResult.value(new IntegerValue(0));
    } catch (IOException failure) {
      return fileError();
    }
  }

  private synchronized BuiltinResult name(List<MooValue> arguments) {
    Handle handle = handle(arguments.get(0));
    return handle == null ? invalidArgument() : string(handle.name);
  }

  private synchronized BuiltinResult openMode(List<MooValue> arguments) {
    Handle handle = handle(arguments.get(0));
    return handle == null ? invalidArgument() : string(handle.mode.source);
  }

  private synchronized BuiltinResult readLine(List<MooValue> arguments) {
    Handle handle = readable(arguments.get(0));
    if (handle == null) {
      return invalidArgument();
    }
    try {
      byte[] line = readRawLine(handle);
      return BuiltinResult.value(StringValue.of(handle.mode.binary ? encodeBinary(line) : clean(line)));
    } catch (EOFException failure) {
      return fileError();
    } catch (IOException failure) {
      return fileError();
    }
  }

  private synchronized BuiltinResult readLines(List<MooValue> arguments) {
    long begin = integer(arguments.get(1));
    long end = integer(arguments.get(2));
    if (begin < 1 || begin > end) {
      return invalidArgument();
    }
    Handle handle = readable(arguments.get(0));
    if (handle == null) {
      return invalidArgument();
    }
    try {
      handle.file.seek(0);
      handle.eof = false;
      for (long line = 1; line < begin; line++) {
        readRawLine(handle);
      }
      long beginPosition = handle.file.getFilePointer();
      List<MooValue> lines = new ArrayList<>();
      for (long line = begin; line <= end; line++) {
        try {
          byte[] raw = readRawLine(handle);
          lines.add(StringValue.of(handle.mode.binary ? encodeBinary(raw) : clean(raw)));
        } catch (EOFException exhausted) {
          break;
        }
      }
      handle.file.seek(beginPosition);
      handle.eof = false;
      return BuiltinResult.value(new ListValue(lines));
    } catch (IOException failure) {
      return fileError();
    }
  }

  private synchronized BuiltinResult writeLine(List<MooValue> arguments) {
    Handle handle = writable(arguments.get(0));
    if (handle == null) {
      return invalidArgument();
    }
    byte[] raw = outputBytes(handle, (StringValue) arguments.get(1));
    if (raw == null) {
      return invalidArgument();
    }
    try {
      prepareWrite(handle);
      handle.file.write(raw);
      handle.file.write('\n');
      flushIfRequested(handle);
      handle.eof = false;
      return BuiltinResult.value(new IntegerValue(0));
    } catch (IOException failure) {
      return fileError();
    }
  }

  private synchronized BuiltinResult read(List<MooValue> arguments) {
    Handle handle = readable(arguments.get(0));
    long requested = integer(arguments.get(1));
    if (handle == null || requested < 0 || requested > Integer.MAX_VALUE) {
      return invalidArgument();
    }
    if (requested == 0) {
      return string("");
    }
    try {
      byte[] raw = new byte[(int) requested];
      int count = handle.file.read(raw);
      if (count < 0) {
        handle.eof = true;
        return fileError();
      }
      if (count != raw.length) {
        raw = java.util.Arrays.copyOf(raw, count);
        handle.eof = true;
      }
      return BuiltinResult.value(StringValue.of(handle.mode.binary ? encodeBinary(raw) : clean(raw)));
    } catch (IOException failure) {
      return fileError();
    }
  }

  private synchronized BuiltinResult write(List<MooValue> arguments) {
    Handle handle = writable(arguments.get(0));
    if (handle == null) {
      return invalidArgument();
    }
    byte[] raw = outputBytes(handle, (StringValue) arguments.get(1));
    if (raw == null) {
      return invalidArgument();
    }
    try {
      prepareWrite(handle);
      handle.file.write(raw);
      flushIfRequested(handle);
      handle.eof = false;
      return BuiltinResult.value(new IntegerValue(raw.length));
    } catch (IOException failure) {
      return fileError();
    }
  }

  private synchronized BuiltinResult flush(List<MooValue> arguments) {
    Handle handle = handle(arguments.get(0));
    if (handle == null) {
      return invalidArgument();
    }
    try {
      handle.file.getFD().sync();
      return BuiltinResult.value(new IntegerValue(0));
    } catch (IOException failure) {
      return fileError();
    }
  }

  private synchronized BuiltinResult seek(List<MooValue> arguments) {
    Handle handle = handle(arguments.get(0));
    if (handle == null) {
      return invalidArgument();
    }
    long offset = integer(arguments.get(1));
    String whence = ((StringValue) arguments.get(2)).text().toUpperCase(Locale.ROOT);
    try {
      long target =
          switch (whence) {
            case "SEEK_SET" -> offset;
            case "SEEK_CUR" -> Math.addExact(handle.file.getFilePointer(), offset);
            case "SEEK_END" -> Math.addExact(handle.file.length(), offset);
            default -> Long.MIN_VALUE;
          };
      if (target < 0) {
        return invalidArgument();
      }
      handle.file.seek(target);
      handle.eof = false;
      return BuiltinResult.value(new IntegerValue(0));
    } catch (ArithmeticException | IOException failure) {
      return fileError();
    }
  }

  private synchronized BuiltinResult tell(List<MooValue> arguments) {
    Handle handle = handle(arguments.get(0));
    if (handle == null) {
      return invalidArgument();
    }
    try {
      return BuiltinResult.value(new IntegerValue(handle.file.getFilePointer()));
    } catch (IOException failure) {
      return fileError();
    }
  }

  private synchronized BuiltinResult eof(List<MooValue> arguments) {
    Handle handle = handle(arguments.get(0));
    return handle == null ? invalidArgument() : BuiltinResult.value(new IntegerValue(handle.eof ? 1 : 0));
  }

  private synchronized BuiltinResult countLines(List<MooValue> arguments) {
    Handle handle = readable(arguments.get(0));
    if (handle == null) {
      return invalidArgument();
    }
    try {
      handle.file.seek(0);
      handle.eof = false;
      long count = 0;
      while (true) {
        try {
          readRawLine(handle);
          count++;
        } catch (EOFException exhausted) {
          break;
        }
      }
      return BuiltinResult.value(new IntegerValue(count));
    } catch (IOException failure) {
      return fileError();
    }
  }

  private synchronized BuiltinResult grep(List<MooValue> arguments) {
    Handle handle = readable(arguments.get(0));
    if (handle == null) {
      return invalidArgument();
    }
    byte[] needle = ((StringValue) arguments.get(1)).bytes();
    boolean all = arguments.size() == 3 && arguments.get(2).isTruthy();
    List<MooValue> matches = new ArrayList<>();
    try {
      handle.file.seek(0);
      handle.eof = false;
      long lineNumber = 0;
      while (true) {
        byte[] raw;
        try {
          raw = readRawLine(handle);
        } catch (EOFException exhausted) {
          break;
        }
        lineNumber++;
        if (contains(raw, needle)) {
          MooValue value =
              StringValue.of(handle.mode.binary ? encodeBinary(raw) : clean(raw));
          matches.add(
              new ListValue(List.of(value, new IntegerValue(lineNumber))));
          if (!all) {
            break;
          }
        }
      }
      return BuiltinResult.value(new ListValue(matches));
    } catch (IOException failure) {
      return fileError();
    }
  }

  private synchronized BuiltinResult handles() {
    return BuiltinResult.value(
        new ListValue(
            handles.keySet().stream()
                .sorted()
                .map(IntegerValue::new)
                .map(MooValue.class::cast)
                .toList()));
  }

  private synchronized BuiltinResult list(List<MooValue> arguments) {
    Path path = resolve(((StringValue) arguments.get(0)).text());
    if (path == null) {
      return invalidArgument();
    }
    boolean detailed = arguments.size() == 2 && arguments.get(1).isTruthy();
    List<Path> paths = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
      for (Path entry : stream) {
        paths.add(entry);
      }
      paths.sort(Comparator.comparing(entry -> entry.getFileName().toString()));
      List<MooValue> values = new ArrayList<>();
      for (Path entry : paths) {
        String filename = entry.getFileName().toString();
        if (filename.equals(".") || filename.equals("..")) {
          continue;
        }
        if (detailed) {
          FileInfo info = stat(entry);
          values.add(
              new ListValue(
                  List.of(
                      StringValue.of(filename),
                      StringValue.of(info.type),
                      StringValue.of(info.mode),
                      new IntegerValue(info.size))));
        } else {
          values.add(StringValue.of(filename));
        }
      }
      return BuiltinResult.value(new ListValue(values));
    } catch (IOException failure) {
      return fileError();
    }
  }

  private synchronized BuiltinResult mkdir(List<MooValue> arguments) {
    Path path = resolve(((StringValue) arguments.get(0)).text());
    if (path == null) {
      return invalidArgument();
    }
    try {
      Files.createDirectory(path);
      return BuiltinResult.value(new IntegerValue(0));
    } catch (IOException failure) {
      return fileError();
    }
  }

  private synchronized BuiltinResult rmdir(List<MooValue> arguments) {
    return removePath(arguments, true);
  }

  private synchronized BuiltinResult remove(List<MooValue> arguments) {
    return removePath(arguments, false);
  }

  private synchronized BuiltinResult rename(List<MooValue> arguments) {
    Path from = resolve(((StringValue) arguments.get(0)).text());
    if (from == null) {
      return fileError();
    }
    Path to = resolve(((StringValue) arguments.get(1)).text());
    if (to == null) {
      return invalidArgument();
    }
    try {
      Files.move(from, to);
      return BuiltinResult.value(new IntegerValue(0));
    } catch (IOException failure) {
      return fileError();
    }
  }

  private synchronized BuiltinResult chmod(List<MooValue> arguments) {
    String mode = ((StringValue) arguments.get(1)).text();
    if (!mode.matches("[0-7]{3}")) {
      return invalidArgument();
    }
    Path path = resolve(((StringValue) arguments.get(0)).text());
    if (path == null) {
      return invalidArgument();
    }
    try {
      Files.setPosixFilePermissions(path, permissions(Integer.parseInt(mode, 8)));
      return BuiltinResult.value(new IntegerValue(0));
    } catch (IOException | UnsupportedOperationException failure) {
      return fileError();
    }
  }

  private synchronized BuiltinResult size(List<MooValue> arguments) {
    FileInfo info = fileInfo(arguments.get(0));
    return info == null ? fileSpecError(arguments.get(0)) : BuiltinResult.value(new IntegerValue(info.size));
  }

  private synchronized BuiltinResult mode(List<MooValue> arguments) {
    FileInfo info = fileInfo(arguments.get(0));
    return info == null ? fileSpecError(arguments.get(0)) : string(info.mode);
  }

  private synchronized BuiltinResult type(List<MooValue> arguments) {
    FileInfo info = fileInfo(arguments.get(0));
    return info == null ? fileSpecError(arguments.get(0)) : string(info.type);
  }

  private synchronized BuiltinResult lastAccess(List<MooValue> arguments) {
    FileInfo info = fileInfo(arguments.get(0));
    return info == null
        ? fileSpecError(arguments.get(0))
        : BuiltinResult.value(new IntegerValue(info.access));
  }

  private synchronized BuiltinResult lastModify(List<MooValue> arguments) {
    FileInfo info = fileInfo(arguments.get(0));
    return info == null
        ? fileSpecError(arguments.get(0))
        : BuiltinResult.value(new IntegerValue(info.modify));
  }

  private synchronized BuiltinResult lastChange(List<MooValue> arguments) {
    FileInfo info = fileInfo(arguments.get(0));
    return info == null
        ? fileSpecError(arguments.get(0))
        : BuiltinResult.value(new IntegerValue(info.change));
  }

  private synchronized BuiltinResult stat(List<MooValue> arguments) {
    FileInfo info = fileInfo(arguments.get(0));
    if (info == null) {
      return fileSpecError(arguments.get(0));
    }
    return BuiltinResult.value(
        new ListValue(
            List.of(
                new IntegerValue(info.size),
                StringValue.of(info.type),
                StringValue.of(info.mode),
                StringValue.of(""),
                StringValue.of(""),
                new IntegerValue(info.access),
                new IntegerValue(info.modify),
                new IntegerValue(info.change))));
  }

  private BuiltinResult removePath(List<MooValue> arguments, boolean directory) {
    Path path = resolve(((StringValue) arguments.get(0)).text());
    if (path == null) {
      return invalidArgument();
    }
    try {
      if (directory && !Files.isDirectory(path)) {
        return fileError();
      }
      Files.delete(path);
      return BuiltinResult.value(new IntegerValue(0));
    } catch (IOException failure) {
      return fileError();
    }
  }

  private @Nullable FileInfo fileInfo(MooValue value) {
    Path path;
    if (value instanceof StringValue string) {
      path = resolve(string.text());
      if (path == null) {
        return null;
      }
    } else {
      Handle handle = handle(value);
      if (handle == null) {
        return null;
      }
      path = handle.path;
    }
    try {
      return stat(path);
    } catch (IOException failure) {
      return null;
    }
  }

  private static FileInfo stat(Path path) throws IOException {
    BasicFileAttributes attributes =
        Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    String type =
        attributes.isRegularFile()
            ? "reg"
            : attributes.isDirectory() ? "dir" : "unknown";
    String mode = modeString(Files.getPosixFilePermissions(path));
    long change = seconds(attributes.lastModifiedTime());
    try {
      Object value = Files.getAttribute(path, "unix:ctime", LinkOption.NOFOLLOW_LINKS);
      if (value instanceof FileTime time) {
        change = seconds(time);
      }
    } catch (UnsupportedOperationException ignored) {
      // The Linux authority supplies ctime. Other filesystems use mtime as a safe fallback.
    }
    return new FileInfo(
        attributes.size(),
        type,
        mode,
        seconds(attributes.lastAccessTime()),
        seconds(attributes.lastModifiedTime()),
        change);
  }

  private BuiltinResult fileSpecError(MooValue value) {
    if (value instanceof StringValue string && resolve(string.text()) == null) {
      return invalidArgument();
    }
    if (!(value instanceof StringValue) && handle(value) == null) {
      return invalidArgument();
    }
    return fileError();
  }

  private @Nullable Path resolve(String name) {
    return root.resolve(name);
  }

  private long allocateHandle() {
    while (handles.containsKey(nextHandle)) {
      nextHandle++;
    }
    return nextHandle++;
  }

  private @Nullable Handle handle(MooValue value) {
    return value instanceof IntegerValue integer ? handles.get(integer.value()) : null;
  }

  private @Nullable Handle readable(MooValue value) {
    Handle handle = handle(value);
    return handle != null && handle.mode.read ? handle : null;
  }

  private @Nullable Handle writable(MooValue value) {
    Handle handle = handle(value);
    return handle != null && handle.mode.write ? handle : null;
  }

  private static byte[] readRawLine(Handle handle) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    while (true) {
      int value = handle.file.read();
      if (value < 0) {
        handle.eof = true;
        if (output.size() == 0) {
          throw new EOFException();
        }
        break;
      }
      if (value == '\n') {
        break;
      }
      output.write(value);
    }
    byte[] result = output.toByteArray();
    if (result.length != 0 && result[result.length - 1] == '\r') {
      result = java.util.Arrays.copyOf(result, result.length - 1);
    }
    return result;
  }

  private static byte @Nullable [] outputBytes(Handle handle, StringValue value) {
    return handle.mode.binary ? decodeBinary(value.bytes()) : value.bytes();
  }

  private static void prepareWrite(Handle handle) throws IOException {
    if (handle.mode.append) {
      handle.file.seek(handle.file.length());
    }
  }

  private static void flushIfRequested(Handle handle) throws IOException {
    if (handle.mode.flush) {
      handle.file.getFD().sync();
    }
  }

  private static byte[] clean(byte[] raw) {
    ByteArrayOutputStream output = new ByteArrayOutputStream(raw.length);
    for (byte current : raw) {
      int value = Byte.toUnsignedInt(current);
      if ((value >= 0x21 && value <= 0x7e) || value == ' ') {
        output.write(value);
      }
    }
    return output.toByteArray();
  }

  private static byte[] encodeBinary(byte[] raw) {
    ByteArrayOutputStream output = new ByteArrayOutputStream(raw.length);
    for (byte current : raw) {
      int value = Byte.toUnsignedInt(current);
      if (value != '~' && ((value >= 0x21 && value <= 0x7e) || value == ' ')) {
        output.write(value);
      } else {
        output.write('~');
        output.write(Character.toUpperCase(Character.forDigit(value >>> 4, 16)));
        output.write(Character.toUpperCase(Character.forDigit(value & 0x0f, 16)));
      }
    }
    return output.toByteArray();
  }

  private static byte @Nullable [] decodeBinary(byte[] encoded) {
    ByteArrayOutputStream output = new ByteArrayOutputStream(encoded.length);
    for (int index = 0; index < encoded.length; index++) {
      int value = Byte.toUnsignedInt(encoded[index]);
      if (value != '~') {
        output.write(value);
        continue;
      }
      if (index + 2 >= encoded.length) {
        return null;
      }
      int high = Character.digit((char) Byte.toUnsignedInt(encoded[++index]), 16);
      int low = Character.digit((char) Byte.toUnsignedInt(encoded[++index]), 16);
      if (high < 0 || low < 0) {
        return null;
      }
      output.write(high << 4 | low);
    }
    return output.toByteArray();
  }

  private static boolean contains(byte[] haystack, byte[] needle) {
    if (needle.length == 0) {
      return true;
    }
    for (int start = 0; start <= haystack.length - needle.length; start++) {
      boolean found = true;
      for (int offset = 0; offset < needle.length; offset++) {
        if (haystack[start + offset] != needle[offset]) {
          found = false;
          break;
        }
      }
      if (found) {
        return true;
      }
    }
    return false;
  }

  private static Set<PosixFilePermission> permissions(int mode) {
    Set<PosixFilePermission> result = EnumSet.noneOf(PosixFilePermission.class);
    addPermissions(result, mode >> 6 & 7, PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    addPermissions(result, mode >> 3 & 7, PosixFilePermission.GROUP_READ,
        PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE);
    addPermissions(result, mode & 7, PosixFilePermission.OTHERS_READ,
        PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE);
    return result;
  }

  private static void addPermissions(
      Set<PosixFilePermission> target,
      int bits,
      PosixFilePermission read,
      PosixFilePermission write,
      PosixFilePermission execute) {
    if ((bits & 4) != 0) target.add(read);
    if ((bits & 2) != 0) target.add(write);
    if ((bits & 1) != 0) target.add(execute);
  }

  private static String modeString(Set<PosixFilePermission> permissions) {
    int mode = 0;
    if (permissions.contains(PosixFilePermission.OWNER_READ)) mode |= 0400;
    if (permissions.contains(PosixFilePermission.OWNER_WRITE)) mode |= 0200;
    if (permissions.contains(PosixFilePermission.OWNER_EXECUTE)) mode |= 0100;
    if (permissions.contains(PosixFilePermission.GROUP_READ)) mode |= 0040;
    if (permissions.contains(PosixFilePermission.GROUP_WRITE)) mode |= 0020;
    if (permissions.contains(PosixFilePermission.GROUP_EXECUTE)) mode |= 0010;
    if (permissions.contains(PosixFilePermission.OTHERS_READ)) mode |= 0004;
    if (permissions.contains(PosixFilePermission.OTHERS_WRITE)) mode |= 0002;
    if (permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) mode |= 0001;
    return String.format(Locale.ROOT, "%03o", mode);
  }

  private static long seconds(FileTime time) {
    return time.toMillis() / 1_000;
  }

  private static long integer(MooValue value) {
    return ((IntegerValue) value).value();
  }

  private static BuiltinResult string(String value) {
    return BuiltinResult.value(StringValue.of(value));
  }

  private static BuiltinResult invalidArgument() {
    return BuiltinResult.error(ErrorValue.E_INVARG);
  }

  private static BuiltinResult fileError() {
    return BuiltinResult.error(ErrorValue.E_FILE);
  }

  private record FileInfo(long size, String type, String mode, long access, long modify, long change) {}

  private static final class Handle {
    private boolean eof;
    private final RandomAccessFile file;
    private final Mode mode;
    private final String name;
    private final Path path;

    private Handle(String name, Path path, Mode mode, RandomAccessFile file) {
      this.name = name;
      this.path = path;
      this.mode = mode;
      this.file = file;
    }
  }

  private record Mode(
      String source,
      boolean read,
      boolean write,
      boolean binary,
      boolean flush,
      boolean append,
      boolean truncate,
      boolean readOnly) {
    private static @Nullable Mode parse(String source) {
      if (source.length() != 4) {
        return null;
      }
      char operation = source.charAt(0);
      char plus = source.charAt(1);
      char type = source.charAt(2);
      char flushing = source.charAt(3);
      if ((operation != 'r' && operation != 'w' && operation != 'a')
          || (plus != '+' && plus != '-')
          || (type != 't' && type != 'b')
          || (flushing != 'f' && flushing != 'n')) {
        return null;
      }
      boolean read = operation == 'r' || plus == '+';
      boolean write = operation != 'r' || plus == '+';
      return new Mode(
          source,
          read,
          write,
          type == 'b',
          flushing == 'f',
          operation == 'a',
          operation == 'w',
          read && !write);
    }
  }
}
