# Java SDK for Spice.ai

For full documentation visit [Spice.ai OSS Docs](https://docs.spiceai.org/sdks/java).

## Installation

### Maven

Add the following dependency to your Maven project:

```xml
<dependency>
  <groupId>ai.spice</groupId>
  <artifactId>spiceai</artifactId>
  <version>0.3.0</version>
  <scope>compile</scope>
</dependency>
```

### Gradle

Add the following dependency to your Gradle project:

```groovy
implementation 'ai.spice:spiceai:0.3.0'
```

### Manual installation

Pre-built jars are available from a public [maven](https://mvnrepository.com/artifact/ai.spice/spiceai) repository. To build a .jar, execute the command below from the repository root:

```shell
mvn package -Dmaven.test.skip=true
```

## Supported Java Versions

This library supports the following Java implementations:

- OpenJDK 11
- OpenJDK 17
- OpenJDK 21
- OracleJDK 11
- OracleJDK 17
- OracleJDK 21
- OracleJDK 22

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

Use [issues](https://github.com/spiceai/spice-java/issues),  [hey@spice.ai](mailto:hey@spice.ai) or [Discord](https://discord.gg/kZnTfneP5u) to send us feedback, suggestion or if you need help installing or using the library.
