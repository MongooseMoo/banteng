package moo.builtin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import org.jspecify.annotations.Nullable;
import org.sqlite.SQLiteConnection;
import org.sqlite.SQLiteLimits;

/** Catalog-scoped owner of Toast-compatible transient SQLite connections. */
final class SqliteService {
  private static final int MAX_HANDLES = 20;
  private static final int PARSE_TYPES = 2;
  private static final int PARSE_OBJECTS = 4;
  private static final int SANITIZE_STRINGS = 8;
  private static final Map<String, Integer> LIMIT_CATEGORIES = limitCategories();

  private final Map<Long, Database> databases = new LinkedHashMap<>();
  private final ConfinedFileRoot files;
  private long nextHandle = 1;

  SqliteService(ConfinedFileRoot files) {
    this.files = files;
  }

  synchronized BuiltinResult open(List<MooValue> arguments) {
    if (databases.size() >= MAX_HANDLES) {
      return BuiltinResult.error(ErrorValue.E_QUOTA);
    }
    String unresolved = text(arguments.get(0));
    int options =
        arguments.size() == 2 ? Math.toIntExact(integer(arguments.get(1))) : PARSE_TYPES | PARSE_OBJECTS;
    Path path = null;
    String key = unresolved;
    String url;
    if (unresolved.equals(":memory:") || unresolved.isEmpty()) {
      url = "jdbc:sqlite:" + unresolved;
    } else {
      path = files.resolve(unresolved);
      if (path == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      key = path.toString();
      for (Database database : databases.values()) {
        if (database.key.equals(key)) {
          return BuiltinResult.error(ErrorValue.E_INVARG);
        }
      }
      url = "jdbc:sqlite:" + path;
    }

    try {
      Connection opened = DriverManager.getConnection(url);
      SQLiteConnection connection = opened.unwrap(SQLiteConnection.class);
      long handle = nextHandle++;
      String displayPath =
          path == null
              ? unresolved
              : Path.of("files")
                  .resolve(unresolved.startsWith("/") ? unresolved.substring(1) : unresolved)
                  .normalize()
                  .toString()
                  .replace('\\', '/');
      databases.put(handle, new Database(key, displayPath, options, connection));
      return BuiltinResult.value(new IntegerValue(handle));
    } catch (SQLException failure) {
      return BuiltinResult.error(ErrorValue.E_NONE);
    }
  }

  synchronized BuiltinResult close(List<MooValue> arguments) {
    long handle = integer(arguments.get(0));
    Database database = databases.get(handle);
    if (database == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    if (database.locks.get() > 0) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    try {
      database.connection.close();
      databases.remove(handle);
      if (databases.isEmpty()) {
        nextHandle = 1;
      }
      return BuiltinResult.value(new IntegerValue(0));
    } catch (SQLException failure) {
      return BuiltinResult.error(ErrorValue.E_NONE);
    }
  }

  synchronized BuiltinResult handles(List<MooValue> arguments) {
    return BuiltinResult.value(
        new ListValue(
            databases.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .map(IntegerValue::new)
                .map(MooValue.class::cast)
                .toList()));
  }

  synchronized BuiltinResult info(List<MooValue> arguments) {
    Database database = databases.get(integer(arguments.get(0)));
    if (database == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    Map<MooValue, MooValue> values = new LinkedHashMap<>();
    values.put(string("path"), string(database.displayPath));
    values.put(string("parse_types"), truth((database.options & PARSE_TYPES) != 0));
    values.put(string("parse_objects"), truth((database.options & PARSE_OBJECTS) != 0));
    values.put(string("sanitize_strings"), truth((database.options & SANITIZE_STRINGS) != 0));
    values.put(string("locks"), new IntegerValue(database.locks.get()));
    return BuiltinResult.value(new MapValue(values));
  }

  synchronized BuiltinResult query(List<MooValue> arguments) {
    Database database = databases.get(integer(arguments.get(0)));
    if (database == null) {
      return BuiltinResult.value(ErrorValue.E_INVARG);
    }
    String sql = text(arguments.get(1));
    boolean headers = arguments.size() == 3 && arguments.get(2).isTruthy();
    database.locks.incrementAndGet();
    return BuiltinResult.hostWork(() -> run(database, () -> executeQuery(database, sql, headers)));
  }

  synchronized BuiltinResult execute(List<MooValue> arguments) {
    Database database = databases.get(integer(arguments.get(0)));
    if (database == null) {
      return BuiltinResult.value(ErrorValue.E_INVARG);
    }
    String sql = text(arguments.get(1));
    ListValue parameters = (ListValue) arguments.get(2);
    database.locks.incrementAndGet();
    return BuiltinResult.hostWork(() -> run(database, () -> executePrepared(database, sql, parameters)));
  }

  BuiltinResult lastInsertRowId(List<MooValue> arguments) {
    Database database = database(integer(arguments.get(0)));
    if (database == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    synchronized (database.executionLock) {
      try (Statement statement = database.connection.createStatement();
          ResultSet result = statement.executeQuery("SELECT last_insert_rowid()")) {
        return BuiltinResult.value(new IntegerValue(result.next() ? result.getLong(1) : 0));
      } catch (SQLException failure) {
        return stringResult(failure.getMessage());
      }
    }
  }

  BuiltinResult limit(List<MooValue> arguments) {
    Database database = database(integer(arguments.get(0)));
    if (database == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    int category = category(arguments.get(1));
    if (category < 0 || category >= 12) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    long requested = integer(arguments.get(2));
    if (requested > Integer.MAX_VALUE || requested < Integer.MIN_VALUE) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    try {
      int previous = database.connection.getDatabase().limit(category, (int) requested);
      return BuiltinResult.value(new IntegerValue(previous));
    } catch (SQLException failure) {
      return stringResult(failure.getMessage());
    }
  }

  BuiltinResult interrupt(List<MooValue> arguments) {
    Database database = database(integer(arguments.get(0)));
    if (database == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    @Nullable Statement active = database.activeStatement;
    if (active != null) {
      try {
        active.cancel();
      } catch (SQLException ignored) {
        // sqlite3_interrupt below is the authoritative connection-wide cancellation.
      }
    }
    try {
      database.connection.getDatabase().interrupt();
      return BuiltinResult.value(new IntegerValue(0));
    } catch (SQLException failure) {
      return stringResult(failure.getMessage());
    }
  }

  private BuiltinResult executeQuery(Database database, String sql, boolean headers) {
    synchronized (database.executionLock) {
      try (Statement statement = database.connection.createStatement()) {
        database.activeStatement = statement;
        boolean hasRows = statement.execute(sql);
        if (!hasRows) {
          return BuiltinResult.value(new ListValue(List.of()));
        }
        try (ResultSet result = statement.getResultSet()) {
          return BuiltinResult.value(rows(database, result, headers));
        }
      } catch (SQLException failure) {
        return stringResult(failure.getMessage());
      } finally {
        database.activeStatement = null;
      }
    }
  }

  private BuiltinResult executePrepared(Database database, String sql, ListValue parameters) {
    synchronized (database.executionLock) {
      try (PreparedStatement statement = database.connection.prepareStatement(sql)) {
        database.activeStatement = statement;
        bind(statement, parameters);
        boolean hasRows = statement.execute();
        if (!hasRows) {
          return BuiltinResult.value(new ListValue(List.of()));
        }
        try (ResultSet result = statement.getResultSet()) {
          return BuiltinResult.value(rows(database, result, false));
        }
      } catch (SQLException failure) {
        return stringResult(failure.getMessage());
      } finally {
        database.activeStatement = null;
      }
    }
  }

  private static BuiltinResult run(Database database, SqlWork work) {
    try {
      return work.run();
    } finally {
      database.locks.decrementAndGet();
    }
  }

  private static void bind(PreparedStatement statement, ListValue parameters) throws SQLException {
    int index = 1;
    for (MooValue parameter : parameters.elements()) {
      switch (parameter) {
        case StringValue string -> statement.setString(index, text(string));
        case IntegerValue integer -> statement.setLong(index, integer.value());
        case FloatValue floating -> statement.setDouble(index, floating.value());
        case ObjectValue object -> statement.setString(index, "#" + object.value());
        default -> statement.setObject(index, null);
      }
      index++;
    }
  }

  private static ListValue rows(Database database, ResultSet result, boolean headers)
      throws SQLException {
    List<MooValue> rows = new ArrayList<>();
    ResultSetMetaData metadata = result.getMetaData();
    int columns = metadata.getColumnCount();
    while (result.next()) {
      List<MooValue> row = new ArrayList<>();
      for (int column = 1; column <= columns; column++) {
        MooValue value = databaseValue(database.options, result.getString(column));
        if (headers) {
          row.add(
              new ListValue(
                  List.of(string(metadata.getColumnLabel(column)), value)));
        } else {
          row.add(value);
        }
      }
      rows.add(new ListValue(row));
    }
    return new ListValue(rows);
  }

  private static MooValue databaseValue(int options, @Nullable String raw) {
    String value = raw == null ? "NULL" : raw;
    if ((options & PARSE_TYPES) == 0) {
      return string(sanitize(options, value));
    }
    if ((options & PARSE_OBJECTS) != 0 && value.matches("#[+-]?[0-9]+")) {
      try {
        return new ObjectValue(Long.parseLong(value.substring(1)));
      } catch (NumberFormatException ignored) {
        // Preserve a numeric-looking value that exceeds Banteng's object range as a string.
      }
    }
    if (value.matches("[+-]?[0-9]+")) {
      try {
        return new IntegerValue(Long.parseLong(value));
      } catch (NumberFormatException ignored) {
        // SQLite may return arbitrary-precision text; fall through to float/string parsing.
      }
    }
    if (looksFloating(value)) {
      try {
        return new FloatValue(Double.parseDouble(value));
      } catch (NumberFormatException ignored) {
        // Not a Java-representable floating value.
      }
    }
    return string(sanitize(options, value));
  }

  private static boolean looksFloating(String value) {
    return value.matches("[+-]?(?:[0-9]+\\.[0-9]*|[0-9]*\\.[0-9]+|[0-9]+[eE][+-]?[0-9]+)(?:[eE][+-]?[0-9]+)?");
  }

  private static String sanitize(int options, String value) {
    return (options & SANITIZE_STRINGS) == 0 ? value : value.replace('\n', '\t');
  }

  private synchronized @Nullable Database database(long handle) {
    return databases.get(handle);
  }

  private static int category(MooValue value) {
    if (value instanceof IntegerValue integer) {
      long category = integer.value();
      return category < Integer.MIN_VALUE || category > Integer.MAX_VALUE ? -1 : (int) category;
    }
    if (value instanceof StringValue string) {
      return LIMIT_CATEGORIES.getOrDefault(text(string), -1);
    }
    return -1;
  }

  private static Map<String, Integer> limitCategories() {
    SQLiteLimits[] limits = SQLiteLimits.values();
    Map<String, Integer> values = new LinkedHashMap<>();
    for (int index = 0; index < Math.min(12, limits.length); index++) {
      values.put(limits[index].name().substring("SQLITE_".length()), limits[index].getId());
    }
    return Map.copyOf(values);
  }

  private static long integer(MooValue value) {
    return ((IntegerValue) value).value();
  }

  private static String text(MooValue value) {
    return text((StringValue) value);
  }

  private static String text(StringValue value) {
    return new String(value.bytes(), StandardCharsets.ISO_8859_1);
  }

  private static StringValue string(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }

  private static BuiltinResult stringResult(@Nullable String value) {
    return BuiltinResult.value(string(value == null ? "SQLite error" : value));
  }

  private static IntegerValue truth(boolean value) {
    return new IntegerValue(value ? 1 : 0);
  }

  @FunctionalInterface
  private interface SqlWork {
    BuiltinResult run();
  }

  private static final class Database {
    private volatile @Nullable Statement activeStatement;
    private final SQLiteConnection connection;
    private final String displayPath;
    private final Object executionLock = new Object();
    private final String key;
    private final AtomicInteger locks = new AtomicInteger();
    private final int options;

    private Database(
        String key, String displayPath, int options, SQLiteConnection connection) {
      this.key = key;
      this.displayPath = displayPath;
      this.options = options;
      this.connection = connection;
    }
  }
}
