# Java SDK for Spice.ai

For full documentation visit [Spice.ai OSS Docs](https://docs.spiceai.org/sdks/java).

## Installation

### Maven

Add the following dependency to your Maven project:

```xml
<dependency>
  <groupId>ai.spice</groupId>
  <artifactId>spiceai</artifactId>
  <version>0.5.0</version>
  <scope>compile</scope>
</dependency>
```

### Gradle

Add the following dependency to your Gradle project:

```groovy
implementation 'ai.spice:spiceai:0.5.0'
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
handle other errors, for example RESOURCE_EXHAUSTED (HTTP 429).

### Parameterized Queries (Recommended)

The SDK supports parameterized queries using ADBC (Arrow Database Connectivity), which is the recommended approach for queries with user input to prevent SQL injection:

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

### Spice.ai Runtime commands

#### Accelerated dataset refresh

Use `refresh` method to perform [Accelerated Dataset](https://docs.spiceai.org/components/data-accelerators) refresh. See full [dataset refresh example](/src/main/java/ai/spice/example/ExampleDatasetRefreshSpiceOSS.java).

```java
SpiceClient client = SpiceClient.builder()
    ..
    .build();

client.refresh("taxi_trips")

```

## 🤝 Connect with us

Use [issues](https://github.com/spiceai/spice-java/issues), [hey@spice.ai](mailto:hey@spice.ai) or [Slack](https://spiceai.org/slack) to send us feedback, suggestions, or if you need help installing or using the library.
