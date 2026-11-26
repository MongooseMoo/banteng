package world.mongoose.banteng.builtin.family;
import java.util.Optional;
import java.util.Set;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinRegistrar;
import world.mongoose.banteng.builtin.BuiltinPermissionRule;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;
import world.mongoose.banteng.builtin.ConfinedFileRoot;


import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import org.jspecify.annotations.Nullable;
import org.sqlite.Function;
import org.sqlite.SQLiteConnection;
import org.sqlite.SQLiteLimits;
import org.sqlite.SQLiteException;

/** Catalog-scoped owner of Toast-compatible transient SQLite connections. */
public final class SqliteBuiltins {
  private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
  private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);
  private static final Set<ArgType> STRING = Set.of(ArgType.STRING);
  public void register(List<BuiltinSpec> entries) {
    BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
    registrar.builtin("sqlite_open", List.of(new CallShape(List.of(STRING), List.of(INTEGER), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> open(call.arguments()));
    registrar.builtin("sqlite_close", List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> close(call.arguments()));
    registrar.builtin("sqlite_handles", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> handles());
    registrar.builtin("sqlite_info", List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> info(call.arguments()));
    registrar.builtin("sqlite_query", List.of(new CallShape(List.of(INTEGER, STRING), List.of(ANY), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> query(call.arguments(), call.threadMode()));
    registrar.builtin("sqlite_execute", List.of(new CallShape(
    List.of(INTEGER, STRING, Set.of(ArgType.LIST)),
    List.of(),
    Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> execute(call.arguments(), call.threadMode()));
    registrar.builtin("sqlite_last_insert_row_id", List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> lastInsertRowId(call.arguments()));
    registrar.builtin("sqlite_limit", List.of(new CallShape(List.of(INTEGER, ANY, INTEGER), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> limit(call.arguments()));
    registrar.builtin("sqlite_interrupt", List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> interrupt(call.arguments()));
  }

  private static final int MAX_HANDLES = 20;
  private static final int PARSE_TYPES = 2;
  private static final int PARSE_OBJECTS = 4;
  private static final int SANITIZE_STRINGS = 8;
  private static final Map<String, Integer> LIMIT_CATEGORIES = limitCategories();
  private static final Pattern DECIMAL_INTEGER = Pattern.compile("[+-]?[0-9]+");
  private static final Pattern DECIMAL_REAL = Pattern.compile(
      "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:e[+-]?[0-9]+)?");
  private static final Pattern HEX_REAL = Pattern.compile(
      "[+-]?0x(?:[0-9a-f]+(?:\\.[0-9a-f]*)?|\\.[0-9a-f]+)(?:p[+-]?[0-9]+)?");

  private final LinkedHashMap<Long, Database> databases = new LinkedHashMap<>();
  private final ConfinedFileRoot files;
  private long nextHandle = 1;

  public SqliteBuiltins(ConfinedFileRoot files) {
    this.files = files;
  }

  private synchronized BuiltinResult open(List<MooValue> arguments) {
    if (databases.size() >= MAX_HANDLES) {
      return BuiltinResult.error(ErrorValue.E_QUOTA);
    }
    String unresolved = ((StringValue) arguments.get(0)).text();
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
      for (Map.Entry<Long, Database> entry : databases.entrySet()) {
        if (entry.getValue().key.equals(key)) {
          return BuiltinResult.raised(ErrorValue.E_INVARG,
              StringValue.of("Database already open with handle: " + entry.getKey()),
              new IntegerValue(entry.getKey()));
        }
      }
      url = "jdbc:sqlite:" + path;
    }

    try {
      SQLiteConnection connection = openConnection(url);
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

  private static SQLiteConnection openConnection(String url) throws SQLException {
    Connection opened = DriverManager.getConnection(url);
    try {
      SQLiteConnection connection = opened.unwrap(SQLiteConnection.class);
      Function.create(connection, "regexp", new Function() {
        @Override
        protected void xFunc() throws SQLException {
          String pattern = value_text(0);
          String subject = value_text(1);
          if (pattern == null) {
            error("SQLite REGEXP called with invalid pattern.");
          } else if (subject == null) {
            error("SQLite REGEXP called with invalid string.");
          } else {
            try {
              result(PcreService.containsMatch(subject, pattern) ? 1 : 0);
            } catch (PatternSyntaxException failure) {
              error(failure.getDescription());
            }
          }
        }
      }, 2, Function.FLAG_DETERMINISTIC);
      return connection;
    } catch (SQLException failure) {
      try {
        opened.close();
      } catch (SQLException closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
  }

  private synchronized BuiltinResult close(List<MooValue> arguments) {
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

  private synchronized BuiltinResult handles() {
    return BuiltinResult.value(
        new ListValue(
            databases.sequencedKeySet().reversed().stream()
                .map(IntegerValue::new)
                .map(MooValue.class::cast)
                .toList()));
  }

  private synchronized BuiltinResult info(List<MooValue> arguments) {
    Database database = databases.get(integer(arguments.get(0)));
    if (database == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    Map<MooValue, MooValue> values = new LinkedHashMap<>();
    values.put(StringValue.of("path"), StringValue.of(database.displayPath));
    values.put(StringValue.of("parse_types"), truth((database.options & PARSE_TYPES) != 0));
    values.put(StringValue.of("parse_objects"), truth((database.options & PARSE_OBJECTS) != 0));
    values.put(StringValue.of("sanitize_strings"), truth((database.options & SANITIZE_STRINGS) != 0));
    values.put(StringValue.of("locks"), new IntegerValue(database.locks.get()));
    return BuiltinResult.value(new MapValue(values));
  }

  private synchronized BuiltinResult query(List<MooValue> arguments, boolean threaded) {
    Database database = databases.get(integer(arguments.get(0)));
    if (database == null) {
      return BuiltinResult.value(ErrorValue.E_INVARG);
    }
    String sql = ((StringValue) arguments.get(1)).text();
    boolean headers = arguments.size() == 3 && arguments.get(2).isTruthy();
    database.locks.incrementAndGet();
    return threaded
        ? BuiltinResult.hostWork(() -> run(database, () -> executeQuery(database, sql, headers)))
        : run(database, () -> executeQuery(database, sql, headers));
  }

  private synchronized BuiltinResult execute(List<MooValue> arguments, boolean threaded) {
    Database database = databases.get(integer(arguments.get(0)));
    if (database == null) {
      return BuiltinResult.value(ErrorValue.E_INVARG);
    }
    String sql = ((StringValue) arguments.get(1)).text();
    ListValue parameters = (ListValue) arguments.get(2);
    database.locks.incrementAndGet();
    return threaded
        ? BuiltinResult.hostWork(() -> run(database, () -> executePrepared(database, sql, parameters)))
        : run(database, () -> executePrepared(database, sql, parameters));
  }

  private BuiltinResult lastInsertRowId(List<MooValue> arguments) {
    Database database = database(integer(arguments.get(0)));
    if (database == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    synchronized (database.executionLock) {
      try (Statement statement = database.connection.createStatement();
          ResultSet result = statement.executeQuery("SELECT last_insert_rowid()")) {
        return BuiltinResult.value(new IntegerValue(result.next() ? result.getLong(1) : 0));
      } catch (SQLException failure) {
        return errorResult(failure);
      }
    }
  }

  private BuiltinResult limit(List<MooValue> arguments) {
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
      return errorResult(failure);
    }
  }

  private BuiltinResult interrupt(List<MooValue> arguments) {
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
      return errorResult(failure);
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
        return errorResult(failure);
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
        return errorResult(failure);
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
    int available = statement.getParameterMetaData().getParameterCount();
    int index = 1;
    for (MooValue parameter : parameters.elements()) {
      if (index > available) break;
      switch (parameter) {
        case StringValue string -> statement.setString(index, string.text());
        case IntegerValue integer -> statement.setInt(index, (int) integer.value());
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
        Object typed = result.getObject(column);
        String raw = typed instanceof Double real && Double.isFinite(real)
            ? new FloatValue(real).toLiteral() : result.getString(column);
        MooValue value = databaseValue(database.options, raw);
        if (headers) {
          row.add(
              new ListValue(
                  List.of(StringValue.of(metadata.getColumnLabel(column)), value)));
        } else {
          row.add(value);
        }
      }
      rows.add(new ListValue(row));
    }
    return new ListValue(rows);
  }

  private static MooValue databaseValue(int options, @Nullable String raw) {
    if (raw == null) {
      return StringValue.of((options & PARSE_TYPES) == 0 ? "" : "NULL");
    }
    int terminator = raw.indexOf('\0');
    String value = terminator < 0 ? raw : raw.substring(0, terminator);
    if ((options & PARSE_TYPES) == 0) {
      return StringValue.of(sanitize(options, value));
    }
    if ((options & PARSE_OBJECTS) != 0 && value.startsWith("#")) {
      OptionalLong object = parseDecimalInteger(numericText(value.substring(1)));
      if (object.isPresent()) return new ObjectValue(object.orElseThrow());
    }
    String number = numericText(value).toLowerCase(Locale.ROOT);
    OptionalLong integer = parseDecimalInteger(number);
    if (integer.isPresent()) return new IntegerValue(integer.orElseThrow());
    if (number.equals("inf") || number.equals("+inf")
        || number.equals("infinity") || number.equals("+infinity")) {
      return new FloatValue(Double.POSITIVE_INFINITY);
    }
    if (number.equals("-inf") || number.equals("-infinity")) {
      return new FloatValue(Double.NEGATIVE_INFINITY);
    }
    if (DECIMAL_REAL.matcher(number).matches()) {
      return new FloatValue(Double.parseDouble(number));
    }
    if (HEX_REAL.matcher(number).matches()) {
      return new FloatValue(Double.parseDouble(number.indexOf('p') >= 0 ? number : number + "p0"));
    }
    return StringValue.of(sanitize(options, value));
  }

  private static OptionalLong parseDecimalInteger(String value) {
    if (!DECIMAL_INTEGER.matcher(value).matches()) return OptionalLong.empty();
    try {
      return OptionalLong.of(Long.parseLong(value));
    } catch (NumberFormatException overflow) {
      return OptionalLong.of(value.charAt(0) == '-' ? Long.MIN_VALUE : Long.MAX_VALUE);
    }
  }

  private static String numericText(String value) {
    int start = 0;
    while (start < value.length()) {
      char current = value.charAt(start);
      if (current != ' ' && (current < '\t' || current > '\r')) break;
      start++;
    }
    int end = value.length();
    while (end > start && value.charAt(end - 1) == ' ') end--;
    return value.substring(start, end);
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
      return LIMIT_CATEGORIES.getOrDefault(string.text(), -1);
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

  private static BuiltinResult errorResult(SQLException failure) {
    String value = failure.getMessage();
    if (failure instanceof SQLiteException sqlite && value != null) {
      String prefix = sqlite.getResultCode() + " (";
      if (value.startsWith(prefix) && value.endsWith(")")) {
        value = value.substring(prefix.length(), value.length() - 1);
      }
    }
    return BuiltinResult.value(StringValue.of(value == null ? "SQLite error" : value));
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
