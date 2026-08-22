/*
Copyright 2024 The Spice.ai OSS Authors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package ai.spice;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;

/**
 * Represents a query parameter with an optional explicit Arrow type.
 * If type is null, the type will be inferred from the value.
 * 
 * <p>
 * Use the static factory methods to create parameters with explicit types,
 * or pass simple Java values directly to sqlWithParams for automatic type
 * inference.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * // With type inference
 * client.sqlWithParams("SELECT * FROM table WHERE id = $1", 123);
 *
 * // With explicit type
 * client.sqlWithParams("SELECT * FROM table WHERE id = $1", Param.int32(123));
 * </pre>
 */
public class Param {

    // ========== Cached Arrow Types for Performance ==========
    // These are immutable and safe to share across threads
    
    private static final ArrowType INT8 = new ArrowType.Int(8, true);
    private static final ArrowType INT16 = new ArrowType.Int(16, true);
    private static final ArrowType INT32 = new ArrowType.Int(32, true);
    private static final ArrowType INT64 = new ArrowType.Int(64, true);
    private static final ArrowType UINT8 = new ArrowType.Int(8, false);
    private static final ArrowType UINT16 = new ArrowType.Int(16, false);
    private static final ArrowType UINT32 = new ArrowType.Int(32, false);
    private static final ArrowType UINT64 = new ArrowType.Int(64, false);
    
    private static final ArrowType FLOAT16 = new ArrowType.FloatingPoint(FloatingPointPrecision.HALF);
    private static final ArrowType FLOAT32 = new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE);
    private static final ArrowType FLOAT64 = new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE);
    
    private static final ArrowType DATE32 = new ArrowType.Date(DateUnit.DAY);
    private static final ArrowType DATE64 = new ArrowType.Date(DateUnit.MILLISECOND);
    
    private static final ArrowType TIME64_MICRO = new ArrowType.Time(TimeUnit.MICROSECOND, 64);
    private static final ArrowType TIME64_NANO = new ArrowType.Time(TimeUnit.NANOSECOND, 64);
    private static final ArrowType TIME32_SEC = new ArrowType.Time(TimeUnit.SECOND, 32);
    private static final ArrowType TIME32_MILLI = new ArrowType.Time(TimeUnit.MILLISECOND, 32);
    
    private static final ArrowType DURATION_MICRO = new ArrowType.Duration(TimeUnit.MICROSECOND);
    
    private static final ArrowType TIMESTAMP_MICRO_UTC = new ArrowType.Timestamp(TimeUnit.MICROSECOND, "UTC");

    private final Object value;
    private final ArrowType type;

    /**
     * Creates a new parameter with inferred type.
     *
     * @param value The parameter value
     */
    public Param(Object value) {
        this.value = value;
        this.type = null;
    }

    /**
     * Creates a new parameter with explicit Arrow type.
     *
     * @param value The parameter value
     * @param type  The explicit Arrow type
     */
    public Param(Object value, ArrowType type) {
        this.value = value;
        this.type = type;
    }

    /**
     * Gets the parameter value.
     *
     * @return The parameter value
     */
    public Object getValue() {
        return value;
    }

    /**
     * Gets the explicit Arrow type, or null if type should be inferred.
     *
     * @return The Arrow type or null
     */
    public ArrowType getType() {
        return type;
    }

    /**
     * Returns true if this parameter has an explicit type specified.
     *
     * @return true if type is explicit
     */
    public boolean hasExplicitType() {
        return type != null;
    }

    // ========== Integer Types ==========

    /**
     * Creates an int8 (byte) parameter.
     *
     * @param value The byte value
     * @return A new Param with Int8 type
     */
    public static Param int8(byte value) {
        return new Param(value, INT8);
    }

    /**
     * Creates an int16 (short) parameter.
     *
     * @param value The short value
     * @return A new Param with Int16 type
     */
    public static Param int16(short value) {
        return new Param(value, INT16);
    }

    /**
     * Creates an int32 parameter.
     *
     * @param value The int value
     * @return A new Param with Int32 type
     */
    public static Param int32(int value) {
        return new Param(value, INT32);
    }

    /**
     * Creates an int64 parameter.
     *
     * @param value The long value
     * @return A new Param with Int64 type
     */
    public static Param int64(long value) {
        return new Param(value, INT64);
    }

    /**
     * Creates a uint8 parameter.
     *
     * @param value The value (0-255)
     * @return A new Param with Uint8 type
     */
    public static Param uint8(short value) {
        return new Param(value, UINT8);
    }

    /**
     * Creates a uint16 parameter.
     *
     * @param value The value (0-65535)
     * @return A new Param with Uint16 type
     */
    public static Param uint16(int value) {
        return new Param(value, UINT16);
    }

    /**
     * Creates a uint32 parameter.
     *
     * @param value The value (0-4294967295)
     * @return A new Param with Uint32 type
     */
    public static Param uint32(long value) {
        return new Param(value, UINT32);
    }

    /**
     * Creates a uint64 parameter.
     *
     * @param value The value
     * @return A new Param with Uint64 type
     */
    public static Param uint64(long value) {
        return new Param(value, UINT64);
    }

    // ========== Floating Point Types ==========

    /**
     * Creates a float16 parameter.
     *
     * @param value The value (stored as short bits)
     * @return A new Param with Float16 type
     */
    public static Param float16(short value) {
        return new Param(value, FLOAT16);
    }

    /**
     * Creates a float32 parameter.
     *
     * @param value The float value
     * @return A new Param with Float32 type
     */
    public static Param float32(float value) {
        return new Param(value, FLOAT32);
    }

    /**
     * Creates a float64 (double) parameter.
     *
     * @param value The double value
     * @return A new Param with Float64 type
     */
    public static Param float64(double value) {
        return new Param(value, FLOAT64);
    }

    // ========== String and Binary Types ==========

    /**
     * Creates a string parameter.
     *
     * @param value The string value
     * @return A new Param with Utf8 type
     */
    public static Param string(String value) {
        return new Param(value, ArrowType.Utf8.INSTANCE);
    }

    /**
     * Creates a large string parameter (for strings &gt; 2GB).
     *
     * @param value The string value
     * @return A new Param with LargeUtf8 type
     */
    public static Param largeString(String value) {
        return new Param(value, ArrowType.LargeUtf8.INSTANCE);
    }

    /**
     * Creates a binary parameter.
     *
     * @param value The byte array value
     * @return A new Param with Binary type
     */
    public static Param binary(byte[] value) {
        return new Param(value, ArrowType.Binary.INSTANCE);
    }

    /**
     * Creates a large binary parameter (for data &gt; 2GB).
     *
     * @param value The byte array value
     * @return A new Param with LargeBinary type
     */
    public static Param largeBinary(byte[] value) {
        return new Param(value, ArrowType.LargeBinary.INSTANCE);
    }

    /**
     * Creates a fixed size binary parameter.
     *
     * @param value     The byte array value
     * @param byteWidth The fixed byte width
     * @return A new Param with FixedSizeBinary type
     */
    public static Param fixedSizeBinary(byte[] value, int byteWidth) {
        return new Param(value, new ArrowType.FixedSizeBinary(byteWidth));
    }

    // ========== Boolean Type ==========

    /**
     * Creates a boolean parameter.
     *
     * @param value The boolean value
     * @return A new Param with Bool type
     */
    public static Param bool(boolean value) {
        return new Param(value, ArrowType.Bool.INSTANCE);
    }

    // ========== Temporal Types ==========

    /**
     * Creates a date32 parameter (days since Unix epoch).
     *
     * @param value The LocalDate value
     * @return A new Param with Date32 type
     */
    public static Param date32(LocalDate value) {
        return new Param(value, DATE32);
    }

    /**
     * Creates a date64 parameter (milliseconds since Unix epoch).
     *
     * @param value The LocalDate value
     * @return A new Param with Date64 type
     */
    public static Param date64(LocalDate value) {
        return new Param(value, DATE64);
    }

    /**
     * Creates a time32 parameter with specified unit.
     *
     * @param value The LocalTime value
     * @param unit  The time unit (SECOND or MILLISECOND)
     * @return A new Param with Time32 type
     */
    public static Param time32(LocalTime value, TimeUnit unit) {
        if (unit == TimeUnit.SECOND) {
            return new Param(value, TIME32_SEC);
        } else if (unit == TimeUnit.MILLISECOND) {
            return new Param(value, TIME32_MILLI);
        }
        throw new IllegalArgumentException("Time32 only supports SECOND or MILLISECOND units");
    }

    /**
     * Creates a time64 parameter with specified unit.
     *
     * @param value The LocalTime value
     * @param unit  The time unit (MICROSECOND or NANOSECOND)
     * @return A new Param with Time64 type
     */
    public static Param time64(LocalTime value, TimeUnit unit) {
        if (unit == TimeUnit.MICROSECOND) {
            return new Param(value, TIME64_MICRO);
        } else if (unit == TimeUnit.NANOSECOND) {
            return new Param(value, TIME64_NANO);
        }
        throw new IllegalArgumentException("Time64 only supports MICROSECOND or NANOSECOND units");
    }

    /**
     * Creates a timestamp parameter with specified unit and timezone.
     *
     * @param value    The LocalDateTime value
     * @param unit     The time unit
     * @param timezone The timezone string (e.g., "UTC", "America/New_York")
     * @return A new Param with Timestamp type
     */
    public static Param timestamp(LocalDateTime value, TimeUnit unit, String timezone) {
        // Fast path for common case: microseconds with UTC
        if (unit == TimeUnit.MICROSECOND && "UTC".equals(timezone)) {
            return new Param(value, TIMESTAMP_MICRO_UTC);
        }
        return new Param(value, new ArrowType.Timestamp(unit, timezone));
    }

    /**
     * Creates a timestamp parameter with specified unit and UTC timezone.
     *
     * @param value The LocalDateTime value
     * @param unit  The time unit
     * @return A new Param with Timestamp type and UTC timezone
     */
    public static Param timestamp(LocalDateTime value, TimeUnit unit) {
        return timestamp(value, unit, "UTC");
    }

    /**
     * Creates a duration parameter with specified unit.
     *
     * @param value The Duration value
     * @param unit  The time unit
     * @return A new Param with Duration type
     */
    public static Param duration(Duration value, TimeUnit unit) {
        // Fast path for common case: microseconds
        if (unit == TimeUnit.MICROSECOND) {
            return new Param(value, DURATION_MICRO);
        }
        return new Param(value, new ArrowType.Duration(unit));
    }

    // ========== Decimal Types ==========

    /**
     * Creates a decimal128 parameter with specified precision and scale.
     *
     * @param value     The BigDecimal value
     * @param precision The precision (1-38)
     * @param scale     The scale
     * @return A new Param with Decimal128 type
     */
    public static Param decimal128(BigDecimal value, int precision, int scale) {
        if (precision < 1 || precision > 38) {
            throw new IllegalArgumentException("Decimal128 precision must be between 1 and 38");
        }
        return new Param(value, new ArrowType.Decimal(precision, scale, 128));
    }

    /**
     * Creates a decimal256 parameter with specified precision and scale.
     *
     * @param value     The BigDecimal value
     * @param precision The precision (1-76)
     * @param scale     The scale
     * @return A new Param with Decimal256 type
     */
    public static Param decimal256(BigDecimal value, int precision, int scale) {
        if (precision < 1 || precision > 76) {
            throw new IllegalArgumentException("Decimal256 precision must be between 1 and 76");
        }
        return new Param(value, new ArrowType.Decimal(precision, scale, 256));
    }

    // ========== Null Type ==========

    /**
     * Creates a null parameter.
     *
     * @return A new Param with Null type
     */
    public static Param nullValue() {
        return new Param(null, ArrowType.Null.INSTANCE);
    }

    // ========== Generic Factory Methods ==========

    /**
     * Creates a new parameter with inferred type.
     *
     * @param value The parameter value
     * @return A new Param with inferred type
     */
    public static Param of(Object value) {
        return new Param(value);
    }

    /**
     * Creates a new parameter with explicit Arrow type.
     *
     * @param value The parameter value
     * @param type  The explicit Arrow type
     * @return A new Param with the specified type
     */
    public static Param of(Object value, ArrowType type) {
        return new Param(value, type);
    }

    @Override
    public String toString() {
        return "Param{value=" + value + ", type=" + (type != null ? type : "inferred") + "}";
    }
}
