# Java SDK for Spice.ai

For full documentation visit [Spice.ai OSS Docs](https://docs.spiceai.org/sdks/java).

## Installation

### Maven

Add the following dependency to your Maven project:

```xml
<dependency>
  <groupId>ai.spice</groupId>
  <artifactId>spiceai</artifactId>
  <version>0.7.0</version>
  <scope>compile</scope>
</dependency>
```

### Gradle

Add the following dependency to your Gradle project:

```groovy
implementation 'ai.spice:spiceai:0.7.0'
```

### Manual installation

Pre-built jars are available from a public [maven](https://mvnrepository.com/artifact/ai.spice/spiceai) repository. To build a .jar, execute the command below from the repository root:

```shell
mvn package -Dmaven.test.skip=true
```

## Supported Java Versions

This library supports the following Java implementations:

| Distribution              | Versions                       |
| ------------------------- | ------------------------------ |
| OpenJDK (Microsoft Build) | 11, 17, 21 (LTS)               |
| OpenJDK (Eclipse Temurin) | 21 (LTS), 23, 24               |
| Oracle JDK                | 17, 21 (LTS), 23, 24, 25 (LTS) |

## Usage

### With locally running [Spice.ai OSS](https://github.com/spiceai/spiceai)

Follow the [quickstart guide](https://github.com/spiceai/spiceai?tab=readme-ov-file#%EF%B8%8F-quickstart-local-machine) to install and run Spice locally:

```java
import org.apache.arrow.flight.FlightStream;
import ai.spice.SpiceClient;

public class Example {

    public static void main(String[] args) {
        try (SpiceClient client = SpiceClient.builder()
                .build()) {

            FlightStream stream = client.query("SELECT * FROM taxi_trips LIMIT 10;");

            while (stream.next()) {
                try (VectorSchemaRoot batches = stream.getRoot()) {
                    System.out.println(batches.contentToTSVString());
                }
            }
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
        }
    }
}

```

### With [Spice.ai Cloud](https://spice.ai)

Create a [free Spice.ai account](https://spice.ai/login) to obtain an API_KEY

```java
import org.apache.arrow.flight.FlightStream;
import ai.spice.SpiceClient;

public class Example {
    final static String API_KEY = "api-key";

    public static void main(String[] args) {
        try (SpiceClient client = SpiceClient.builder()
                .withApiKey(API_KEY)
                .withSpiceCloud()
                .build()) {

            FlightStream stream = client.query("SELECT * FROM eth.recent_blocks LIMIT 10;");

            while (stream.next()) {
                try (VectorSchemaRoot batches = stream.getRoot()) {
                    System.out.println(batches.contentToTSVString());
                }
            }
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
        }
    }
}
```

### Connection retry

The `SpiceClient` implements a connection retry mechanism with 3 attempts by default.
The number of attempts can be configured with `withMaxRetries`:

```java
SpiceClient client = SpiceClient.builder()
    .withMaxRetries(5) // Setting to 0 will disable retries
    .build();

```

Retries are performed for connection and system internal errors. It is the SDK user's responsibility to properly
handle other errors, for example RESOURCE_EXHAUSTED (HTTP 429). Retries use exponential backoff with jitter
(~250ms, 500ms, 1s, ... capped at 10s). If the server reports an expired authentication token
(UNAUTHENTICATED), the client automatically re-handshakes and retries.

### Performance Tuning

```java
SpiceClient client = SpiceClient.builder()
    // Number of gRPC connections; queries are distributed round-robin.
    // Increase for highly concurrent workloads with large result streams.
    .withChannelCount(4)
    // Deadline for query planning and statement preparation RPCs
    // (result streaming is not limited by this timeout).
    .withQueryTimeout(Duration.ofSeconds(30))
    // Max idle prepared statements reused by queryWithParams (default 64, 0 disables).
    .withPreparedStatementCacheSize(128)
    .build();
```

### Connection Pooling and HikariCP

`SpiceClient` is thread-safe and multiplexes concurrent queries over shared HTTP/2 connections — use **one client instance per application** and share it across threads. It does not need an external connection pool; for high concurrency, size the built-in pool with `withChannelCount(n)`.

Applications that want JDBC semantics (ORMs, existing [HikariCP](https://github.com/brettwooldridge/HikariCP) infrastructure) can connect to Spice through the [Arrow Flight SQL JDBC driver](https://arrow.apache.org/docs/java/flight_sql_jdbc_driver.html) and pool those connections with HikariCP — this combination is exercised in this repo's test suite:

```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:arrow-flight-sql://localhost:50051/?useEncryption=false");
// For Spice Cloud: jdbc:arrow-flight-sql://flight.spiceai.io:443/?useEncryption=true
// with setUsername(appId) and setPassword(apiKey) — the same credentials the SDK uses.
config.setMaximumPoolSize(4);
// The Flight SQL JDBC driver does not implement Connection.isValid():
config.setConnectionTestQuery("SELECT 1");

try (HikariDataSource pool = new HikariDataSource(config);
        Connection conn = pool.getConnection();
        PreparedStatement ps = conn.prepareStatement("SELECT * FROM taxi_trips WHERE total_amount > ?")) {
    ps.setDouble(1, 10.0);
    try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) { /* ... */ }
    }
}
```

Requires `org.apache.arrow:flight-sql-jdbc-driver` (or `flight-sql-jdbc-core`) and `com.zaxxer:HikariCP` on your classpath. Keep the pool small — each JDBC connection opens its own Flight channel. Prefer the native `SpiceClient` where possible: it streams Arrow data without JDBC row conversion and is significantly faster for analytical results.

### Parameterized Queries (Recommended)

The SDK supports parameterized queries using Arrow Flight SQL prepared statements, which is the recommended approach for queries with user input to prevent SQL injection. Prepared statements are cached and reused across repeated executions of the same SQL, and run on the same tuned connection as regular queries (DNS re-resolution, keep-alive, mTLS):

```java
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowReader;
import ai.spice.SpiceClient;
import ai.spice.Param;

public class Example {
    public static void main(String[] args) {
        try (SpiceClient client = SpiceClient.builder().build()) {

            // Query with automatic type inference
            ArrowReader reader = client.queryWithParams(
                "SELECT * FROM taxi_trips WHERE trip_distance > $1 LIMIT 10",
                5.0);  // Double is inferred as Float64

            while (reader.loadNextBatch()) {
                VectorSchemaRoot root = reader.getVectorSchemaRoot();
                System.out.println(root.contentToTSVString());
            }
            reader.close();

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
```

#### Multiple Parameters

Use positional placeholders ($1, $2, etc.) for multiple parameters:

```java
ArrowReader reader = client.queryWithParams(
    "SELECT * FROM taxi_trips WHERE trip_distance > $1 AND fare_amount > $2 LIMIT 10",
    5.0, 20.0);
```

#### Explicit Type Control

For precise control over Arrow types, use the `Param` factory methods:

```java
import ai.spice.Param;

// Explicit type specification
ArrowReader reader = client.queryWithParams(
    "SELECT * FROM orders WHERE order_id = $1 AND amount >= $2",
    Param.int64(12345),
    Param.decimal128(new BigDecimal("99.99"), 10, 2));
```

**Available typed parameter constructors:**

- **Integers**: `int8`, `int16`, `int32`, `int64`, `uint8`, `uint16`, `uint32`, `uint64`
- **Floating point**: `float16`, `float32`, `float64`
- **Strings**: `string`, `largeString`
- **Binary**: `binary`, `largeBinary`, `fixedSizeBinary`
- **Boolean**: `bool`
- **Date/Time**: `date32`, `date64`, `time32`, `time64`, `timestamp`, `duration`
- **Decimals**: `decimal128`, `decimal256`
- **Null**: `nullValue`

Or use the generic constructors:

- `Param.of(value)` - Creates a parameter with automatic type inference
- `Param.of(value, arrowType)` - Creates a parameter with explicit Arrow type

**Supported parameter types with automatic type inference:**

- Integers: `int`, `byte`, `short`, `long`
- Floating point: `float`, `double`
- String: `String`
- Boolean: `boolean`
- Binary: `byte[]`
- Temporal: `LocalDate`, `LocalTime`, `LocalDateTime`, `Duration`
- Decimal: `BigDecimal`
- Null: `null`

### Memory Configuration

The `SpiceClient` uses an Arrow `RootAllocator` for managing off-heap memory. By default, it uses all available memory. You can configure the memory limit using megabytes:

```java
SpiceClient client = SpiceClient.builder()
    .withArrowMemoryLimitMB(1024) // 1GB limit
    .build();
```

### Long-lived Clients and Transport Resilience

The `SpiceClient` is designed for long-lived reuse. The underlying gRPC channel uses `dns:///` resolution, which periodically re-resolves hostnames so clients automatically recover from load-balancer IP rotation (e.g. AWS NLB). HTTP/2 keep-alive is enabled by default (30s interval, 10s timeout) to detect dead connections quickly.

For the rare case where the transport becomes permanently stuck (e.g. TLS handshake to a wrong backend, persistent `UNAVAILABLE` after retries), use `reset()` to discard the bad connection and immediately establish a fresh one:

```java
SpiceClient client = SpiceClient.builder()
    .withApiKey(API_KEY)
    .withSpiceCloud()
    .build();

// Long-lived usage with transport recovery.
// isTransportFailure() is application-defined; check for
// io.grpc.StatusRuntimeException with Status.UNAVAILABLE,
// SSLHandshakeException, or similar transport-level errors.
try {
    try (FlightStream stream = client.query(sql)) {
        // process results...
    }
} catch (ExecutionException e) {
    if (isTransportFailure(e.getCause())) {
        client.reset();                     // discard bad transport, reconnect immediately
        try (FlightStream stream = client.query(sql)) {
            // process results with fresh connection...
        }
    } else {
        throw e;
    }
}
```

**DNS cache TTL:** The gRPC `DnsNameResolver` respects the JVM's DNS cache TTL. For more aggressive DNS refresh (recommended for cloud-deployed clients), set the JVM property:

```bash
-Dnetworkaddress.cache.ttl=30
```

### Iterating Through Results

For more control over query results, you can iterate through rows and access individual field values:

```java
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Field;

try (SpiceClient client = SpiceClient.builder().build()) {
    FlightStream stream = client.query("SELECT * FROM taxi_trips LIMIT 10;");

    while (stream.next()) {
        try (VectorSchemaRoot root = stream.getRoot()) {
            int rowCount = root.getRowCount();

            // Print column names and types
            for (Field field : root.getSchema().getFields()) {
                System.out.printf("Column: %s, Type: %s%n", field.getName(), field.getType());
            }

            // Iterate through rows generically
            for (int row = 0; row < rowCount; row++) {
                for (FieldVector vector : root.getFieldVectors()) {
                    String columnName = vector.getName();
                    Object value = vector.isNull(row) ? null : vector.getObject(row);
                    System.out.printf("%s = %s%n", columnName, value);
                }
            }

            // Access specific columns with type safety
            FieldVector fareVector = root.getVector("fare_amount");
            if (fareVector instanceof Float8Vector) {
                Float8Vector fareVec = (Float8Vector) fareVector;
                for (int row = 0; row < rowCount; row++) {
                    if (!fareVec.isNull(row)) {
                        double fare = fareVec.get(row);
                        System.out.printf("Fare: $%.2f%n", fare);
                    }
                }
            }

            // Access string columns
            FieldVector vendorVector = root.getVector("vendor_id");
            if (vendorVector instanceof VarCharVector) {
                VarCharVector strVec = (VarCharVector) vendorVector;
                for (int row = 0; row < rowCount; row++) {
                    if (!strVec.isNull(row)) {
                        String vendorId = new String(strVec.get(row), java.nio.charset.StandardCharsets.UTF_8);
                        System.out.printf("Vendor: %s%n", vendorId);
                    }
                }
            }
        }
    }
}
```

See [ExampleIteratingResults.java](/src/main/java/ai/spice/example/ExampleIteratingResults.java) for a comprehensive example.

### Spice.ai Runtime commands

#### Accelerated dataset refresh

Use `refresh` method to perform [Accelerated Dataset](https://docs.spiceai.org/components/data-accelerators) refresh. See full [dataset refresh example](/src/main/java/ai/spice/example/ExampleDatasetRefreshSpiceOSS.java).

```java
SpiceClient client = SpiceClient.builder()
    ..
    .build();

client.refresh("taxi_trips")

```

#### Health, readiness, and status

Use `isHealthy()` and `isReady()` to probe the runtime, and `runtimeStatus()` for
per-component detail. See the full [health and status example](/src/main/java/ai/spice/example/ExampleHealthAndStatus.java).

```java
SpiceClient client = SpiceClient.builder()
    ..
    .build();

// Liveness — is the runtime up? Unauthenticated.
if (!client.isHealthy()) {
    return;
}

// Readiness — the runtime becomes ready once its datasets have loaded, so a
// runtime can be healthy but not yet queryable.
while (!client.isReady()) {
    Thread.sleep(1000);
}
```

Both return `false` rather than throwing when the runtime is unreachable, so they can be
polled directly in a loop.

`runtimeStatus()` returns one `ConnectionDetails` per runtime connection — `http`,
`flight`, `metrics`, and `opentelemetry` — naming which component is not ready and where
it is bound. That makes it strictly more informative than the boolean `isReady()`:

```java
for (ConnectionDetails connection : client.runtimeStatus()) {
    System.out.printf("%s %s %s%n",
        connection.getName(),        // "flight"
        connection.getEndpoint(),    // "127.0.0.1:50051"
        connection.getStatus());     // ComponentStatus.READY
}
```

`getStatus()` returns a `ComponentStatus` — `INITIALIZING`, `READY`, `DISABLED`, `ERROR`,
`REFRESHING`, `SHUTTING_DOWN`, or `NOT_LOADED`. A status a newer runtime introduces maps to
`UNKNOWN` rather than failing; `getRawStatus()` returns it verbatim.

#### Search

Use `search()` to find documents similar to a piece of text via the runtime's
`/v1/search` endpoint. This runs against datasets with an embedding column and a
loaded embedding model — see the
[search and retrieval docs](https://docs.spice.ai/features/search-and-retrieval) for
how to configure them. Supplying `withKeywords(...)` adds a lexical pass, which the
runtime blends with the vector scores into a hybrid ranking.

```java
SpiceClient client = SpiceClient.builder()
    ..
    .build();

SearchResponse response = client.search(new SearchRequest("food safety violations")
    .withDatasets(Arrays.asList("restaurant_inspections"))
    .withLimit(5));

for (SearchMatch match : response.getResults()) {
    System.out.printf("%s (score=%.3f)%n", match.getDataset(), match.getScore());
}
```

### Logging

The SDK uses SLF4J for logging, allowing you to plug in your preferred logging implementation (Logback, Log4j2, java.util.logging, etc.).

**Adding a logging implementation (Maven):**

```xml
<!-- Using Logback -->
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.18</version>
</dependency>

<!-- Or using SLF4J Simple (console output) -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>2.0.17</version>
</dependency>
```

**Log levels used:**

- `DEBUG` - Client initialization, query execution, connection lifecycle
- `WARN` - Recoverable errors during resource cleanup
- `ERROR` - Query failures, connection errors

To enable debug logging with `slf4j-simple`, set the system property:

```bash
-Dorg.slf4j.simpleLogger.defaultLogLevel=debug
```

## 🤝 Connect with us

Use [issues](https://github.com/spiceai/spice-java/issues), [hey@spice.ai](mailto:hey@spice.ai) or [Slack](https://spiceai.org/slack) to send us feedback, suggestions, or if you need help installing or using the library.
