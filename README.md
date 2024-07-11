# Java SDK for Spice.ai

For full documentation visit [docs.spice.ai](https://docs.spice.ai/sdks/java-sdk).

## Installation

### Maven

Add the following dependency in your Maven project:

```xml
<dependency>
  <groupId>spiceai</groupId>
  <artifactId>spiceai</artifactId>
  <version>0.1.0</version>
  <scope>compile</scope>
</dependency>
```

### Gradle

Gradle build support is in progress

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

```java
import org.apache.arrow.flight.FlightStream;
import spiceai.SpiceClient;

public class Example {

    public static void main(String[] args) {
        SpiceClient client = SpiceClient.builder()
            .withApiKey(API_KEY)
            .withSpiceCloud()
            .build();

        FlightStream res = client.query("SELECT * FROM taxi_trips;");

        while (res.next()) {
            System.out.println(res.getRoot().contentToTSVString());
        }
    }
}

```

### With <https://spice.ai> Cloud

```java
import org.apache.arrow.flight.FlightStream;
import spiceai.SpiceClient;

public class Example {

    // Create free Spice.ai account to obtain API_KEY: https://spice.ai/login
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

## 🤝 Connect with us

Use [issues](https://github.com/spiceai/spice-java/issues),  [hey@spice.ai](mailto:hey@spice.ai) or [Discord](https://discord.gg/kZnTfneP5u) to send us feedback, suggestion or if you need help installing or using the library.