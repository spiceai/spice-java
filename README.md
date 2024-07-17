# Java SDK for Spice.ai

For full documentation visit [docs.spice.ai](https://docs.spice.ai/sdks/java-sdk).

## Installation

### Maven

Add the following dependency in your Maven project:

```xml
<dependency>
  <groupId>ai.spice</groupId>
  <artifactId>spiceai</artifactId>
  <version>0.1.0</version>
  <scope>compile</scope>
</dependency>
```

### Gradle

```groovy
implementation 'ai.spice:spiceai:0.1.0'
```

### Manual installation

jars are available from a public [maven](https://mvnrepository.com/artifact/spiceai/spiceai) repository. To build own .jar, execute command below from repository root:

```shell
mvn package -Dmaven.test.skip=true
```

## Supported Java Versions

This library supports the following Java implementations:

- OpenJDK 17
- OpenJDK 21
- OracleJDK 17
- OracleJDK 21
- OracleJDK 22

## Usage

### With locally running [Spice runtime](https://github.com/spiceai/spiceai)

Requires local Spice OSS running: [follow the quickstart]( https://github.com/spiceai/spiceai?tab=readme-ov-file#%EF%B8%8F-quickstart-local-machine)

```java
import org.apache.arrow.flight.FlightStream;
import ai.spice.SpiceClient;

public class Example {

    public static void main(String[] args) {
        SpiceClient client = SpiceClient.builder()
            .build();

        FlightStream res = client.query("SELECT * FROM taxi_trips;");

        while (res.next()) {
            System.out.println(res.getRoot().contentToTSVString());
        }
    }
}

```

### With [Spice.ai Cloud Platform](https://spice.ai)

Create [free Spice.ai account](https://spice.ai/login) to obtain API_KEY

```java
import org.apache.arrow.flight.FlightStream;
import ai.spice.SpiceClient;

public class Example {
    final static String API_KEY = "api-key";

    public static void main(String[] args) {
        SpiceClient client = SpiceClient.builder()
            .withApiKey(API_KEY)
            .withSpiceCloud()
            .build();

        FlightStream res = client.query("SELECT * FROM eth.recent_blocks LIMIT 10;");

        while (res.next()) {
            System.out.println(res.getRoot().contentToTSVString());
        }
    }
}
```

### Connection retry

The `SpiceClient` implements connection retry mechanism (3 attempts by default).
The number of attempts can be configured with `withMaxRetries`:

```java
SpiceClient client = SpiceClient.builder()
    .withMaxRetries(5) // Setting to 0 will disable retries
    .build();

```

Retries are performed for connection and system internal errors. It is the SDK user's responsibility to properly
handle other errors, for example RESOURCE_EXHAUSTED (HTTP 429).

## 🤝 Connect with us

Use [issues](https://github.com/spiceai/spice-java/issues),  [hey@spice.ai](mailto:hey@spice.ai) or [Discord](https://discord.gg/kZnTfneP5u) to send us feedback, suggestion or if you need help installing or using the library.
