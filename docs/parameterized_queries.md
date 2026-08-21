# Parameterized Queries in spice-java

This document describes the parameterized query system in spice-java, including support for all Arrow types and explicit type annotation.

## Overview

Parameterized queries provide a safe and efficient way to execute SQL queries with dynamic values. They prevent SQL injection attacks and allow the database to optimize query execution.

The parameterized query system supports three modes of parameter usage:

1. **Type Inference**: Pass Java values directly, types are automatically inferred
2. **Explicit Types**: Use `Param` class with explicit Arrow type
3. **Helper Functions**: Use convenience factory methods for common types

## Basic Usage

### Simple Type Inference

```java
// Types are automatically inferred from Java values
ArrowReader reader = client.sqlWithParams(
    "SELECT * FROM table WHERE id = $1 AND name = $2",
    42,      // Inferred as Int32
    "test"   // Inferred as Utf8
);
```

### Explicit Type Annotation

```java
// Explicitly specify Arrow types for precise control
ArrowReader reader = client.sqlWithParams(
    "SELECT * FROM table WHERE id = $1 AND created = $2",
    Param.int32(42),
    Param.timestamp(LocalDateTime.now(), TimeUnit.MICROSECOND, "UTC")
);
```

## Supported Types

### Primitive Integer Types

| Java Type | Arrow Type | Helper Method    | Auto-Inferred |
| --------- | ---------- | ---------------- | ------------- |
| `byte`    | Int8       | `Param.int8(v)`  | ✅             |
| `short`   | Int16      | `Param.int16(v)` | ✅             |
| `int`     | Int32      | `Param.int32(v)` | ✅             |
| `long`    | Int64      | `Param.int64(v)` | ✅             |

### Unsigned Integer Types

| Java Type | Arrow Type | Helper Method     | Auto-Inferred |
| --------- | ---------- | ----------------- | ------------- |
| `short`   | Uint8      | `Param.uint8(v)`  | ❌             |
| `int`     | Uint16     | `Param.uint16(v)` | ❌             |
| `long`    | Uint32     | `Param.uint32(v)` | ❌             |
| `long`    | Uint64     | `Param.uint64(v)` | ❌             |

### Floating Point Types

| Java Type      | Arrow Type | Helper Method      | Auto-Inferred |
| -------------- | ---------- | ------------------ | ------------- |
| `short` (bits) | Float16    | `Param.float16(v)` | ❌             |
| `float`        | Float32    | `Param.float32(v)` | ✅             |
| `double`       | Float64    | `Param.float64(v)` | ✅             |

### String and Binary Types

| Java Type | Arrow Type      | Helper Method                     | Auto-Inferred |
| --------- | --------------- | --------------------------------- | ------------- |
| `String`  | Utf8            | `Param.string(v)`                 | ✅             |
| `String`  | LargeUtf8       | `Param.largeString(v)`            | ❌             |
| `byte[]`  | Binary          | `Param.binary(v)`                 | ✅             |
| `byte[]`  | LargeBinary     | `Param.largeBinary(v)`            | ❌             |
| `byte[]`  | FixedSizeBinary | `Param.fixedSizeBinary(v, width)` | ❌             |
| `boolean` | Bool            | `Param.bool(v)`                   | ✅             |

### Temporal Types

| Java Type       | Arrow Type | Helper Method                  | Auto-Inferred         |
| --------------- | ---------- | ------------------------------ | --------------------- |
| `LocalDate`     | Date32     | `Param.date32(v)`              | ✅                     |
| `LocalDate`     | Date64     | `Param.date64(v)`              | ❌                     |
| `LocalTime`     | Time32     | `Param.time32(v, unit)`        | ❌                     |
| `LocalTime`     | Time64     | `Param.time64(v, unit)`        | ✅ (microseconds)      |
| `LocalDateTime` | Timestamp  | `Param.timestamp(v, unit, tz)` | ✅ (microseconds, UTC) |
| `Duration`      | Duration   | `Param.duration(v, unit)`      | ✅ (microseconds)      |

### Decimal Types

| Java Type    | Arrow Type | Helper Method                           | Auto-Inferred |
| ------------ | ---------- | --------------------------------------- | ------------- |
| `BigDecimal` | Decimal128 | `Param.decimal128(v, precision, scale)` | ✅             |
| `BigDecimal` | Decimal256 | `Param.decimal256(v, precision, scale)` | ❌             |

### Special Types

| Java Type | Arrow Type | Helper Method       | Auto-Inferred |
| --------- | ---------- | ------------------- | ------------- |
| `null`    | Null       | `Param.nullValue()` | ✅             |

## API Methods

### sqlWithParams

Primary method for parameterized queries:

```java
public ArrowReader sqlWithParams(String sql, Object... params) throws ExecutionException
```

**Parameters:**

- `sql`: SQL query with positional placeholders (`$1`, `$2`, etc.)
- `params`: Variable number of parameters (Java values or Param instances)

**Returns:**

- `ArrowReader`: Arrow RecordReader for results

**Throws:**

- `ExecutionException`: If query execution fails

## Examples

### Example 1: Basic Query with Inferred Types

```java
import ai.spice.SpiceClient;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowReader;

public class Example {
    public static void main(String[] args) throws Exception {
        try (SpiceClient client = SpiceClient.builder().build()) {
            
            // Simple query with inferred types
            ArrowReader reader = client.sqlWithParams(
                "SELECT * FROM customers WHERE age > $1 AND country = $2 LIMIT $3",
                18,      // int -> Int32
                "USA",   // String -> Utf8
                100      // int -> Int32
            );
            
            while (reader.loadNextBatch()) {
                VectorSchemaRoot root = reader.getVectorSchemaRoot();
                System.out.println(root.contentToTSVString());
            }
            reader.close();
        }
    }
}
```

### Example 2: Explicit Types for Precision

```java
import ai.spice.Param;
import ai.spice.SpiceClient;

public class Example {
    public static void main(String[] args) throws Exception {
        try (SpiceClient client = SpiceClient.builder().build()) {
            
            // Use explicit types when precision matters
            ArrowReader reader = client.sqlWithParams(
                "SELECT * FROM orders WHERE order_id = $1 AND quantity = $2",
                Param.int32(12345),  // Explicitly Int32
                Param.int16((short) 10)  // Explicitly Int16
            );
            
            // Process results...
            reader.close();
        }
    }
}
```

### Example 3: Temporal Data

```java
import ai.spice.Param;
import ai.spice.SpiceClient;
import org.apache.arrow.vector.types.TimeUnit;
import java.time.LocalDateTime;

public class Example {
    public static void main(String[] args) throws Exception {
        try (SpiceClient client = SpiceClient.builder().build()) {
            
            // Query with timestamp
            LocalDateTime startTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
            
            ArrowReader reader = client.sqlWithParams(
                "SELECT * FROM events WHERE created_at > $1",
                Param.timestamp(startTime, TimeUnit.MICROSECOND, "UTC")
            );
            
            // Process results...
            reader.close();
        }
    }
}
```

### Example 4: Mixed Inferred and Explicit Types

```java
ArrowReader reader = client.sqlWithParams(
    "SELECT * FROM table WHERE id = $1 AND name = $2 AND created = $3 AND active = $4",
    42,                                        // Inferred as Int32
    Param.string("test"),                      // Explicit Utf8
    Param.date32(LocalDate.of(2024, 1, 15)),   // Explicit Date32
    true                                       // Inferred as Bool
);
```

### Example 5: Decimal Precision for Financial Data

```java
import java.math.BigDecimal;

// Working with high-precision decimal numbers
BigDecimal amount = new BigDecimal("12345.67");

ArrowReader reader = client.sqlWithParams(
    "SELECT * FROM transactions WHERE amount >= $1",
    Param.decimal128(amount, 10, 2)  // 10 precision, 2 scale
);
```

## Custom Type Annotation

For advanced use cases, you can create custom `Param` instances with explicit Arrow types:

```java
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.TimeUnit;

// Create a custom param with explicit type
Param customParam = Param.of(
    myValue,
    new ArrowType.Timestamp(TimeUnit.NANOSECOND, "America/New_York")
);

ArrowReader reader = client.sqlWithParams(
    "SELECT * FROM table WHERE ts = $1",
    customParam
);
```

## Type Inference Rules

When a plain Java value is passed to `sqlWithParams()`, the SDK applies these inference rules:

1. **Integers**: Based on the Java type (`byte` → Int8, `short` → Int16, `int` → Int32, `long` → Int64)
2. **Floating Point**: `float` → Float32, `double` → Float64
3. **Strings**: Always inferred as Utf8 (use `Param.largeString()` for LargeUtf8)
4. **Binary**: `byte[]` → Binary
5. **Boolean**: `boolean` → Bool
6. **Dates**: `LocalDate` → Date32
7. **Times**: `LocalTime` → Time64 (microseconds)
8. **Timestamps**: `LocalDateTime` → Timestamp (microseconds, UTC)
9. **Duration**: `Duration` → Duration (microseconds)
10. **Decimal**: `BigDecimal` → Decimal128 (precision and scale derived from value)
11. **Null**: `null` → Null

## Best Practices

1. **Use Type Inference for Simple Cases**: For common types like int, String, boolean
2. **Use Explicit Types for Precision**: When exact type matters (e.g., Int32 vs Int64)
3. **Use Temporal Helpers for Dates/Times**: Ensures correct units and timezones
4. **Use LargeString/LargeBinary**: For data > 2GB
5. **Use Decimals for Financial Data**: Precise decimal arithmetic
6. **Always Close Readers**: Use try-with-resources or explicit `close()`

## Error Handling

The system provides clear error messages for type mismatches:

```java
try {
    ArrowReader reader = client.sqlWithParams("SELECT $1", unsupportedType);
} catch (ExecutionException e) {
    // Error will indicate: "Unsupported parameter type: <type>"
    System.err.println("Query failed: " + e.getMessage());
}
```

## Performance Considerations

1. **Type Inference**: Minimal overhead, types are determined once per query
2. **Explicit Types**: No overhead, types are directly specified
3. **Arrow Conversion**: Zero-copy when possible, efficient serialization
4. **Large Data**: Use Large variants (LargeString, LargeBinary) for > 2GB data
5. **Statement Reuse**: Prepared statements are cached per SQL string and reused,
   removing the create/close round trips from repeated queries. Tune with
   `withPreparedStatementCacheSize(n)` (default 64; 0 disables caching).
6. **Connection Reuse**: Parameterized queries share the same gRPC channels as
   regular queries — one connection pool, one set of keep-alive/DNS/mTLS settings.
   Use `withChannelCount(n)` for highly concurrent workloads.

## Security Benefits

Parameterized queries protect against SQL injection:

```java
// ❌ Vulnerable to SQL injection
String userId = getUserInput(); // Could be: "1 OR 1=1"
String sql = "SELECT * FROM users WHERE id = " + userId;
FlightStream stream = client.sql(sql);

// ✅ Safe from SQL injection
ArrowReader reader = client.sqlWithParams(
    "SELECT * FROM users WHERE id = $1",
    userId
);
```

## Troubleshooting

### Type Mismatch Errors

If you get type mismatch errors, use explicit types:

```java
// If inference picks wrong type
ArrowReader reader = client.sqlWithParams("SELECT $1", 42);  // Might infer as Int32

// Use explicit type instead
ArrowReader reader = client.sqlWithParams("SELECT $1", Param.int64(42));
```

### Unsupported Type Error

If you encounter "unsupported parameter type", use `Param.of` with explicit Arrow type:

```java
Param param = Param.of(value, myCustomArrowType);
ArrowReader reader = client.sqlWithParams("SELECT $1", param);
```

