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
 * or pass simple Java values directly to queryWithParams for automatic type
 * inference.
 * </p>
 * 
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * // With type inference
 * client.queryWithParams("SELECT * FROM table WHERE id = $1", 123);
 * 
 * // With explicit type
 * client.queryWithParams("SELECT * FROM table WHERE id = $1", Param.int32(123));
 * </pre>
 */
public class Param {

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
        return new Param(value, new ArrowType.Int(8, true));
    }

    /**
     * Creates an int16 (short) parameter.
     *
     * @param value The short value
     * @return A new Param with Int16 type
     */
    public static Param int16(short value) {
        return new Param(value, new ArrowType.Int(16, true));
    }

    /**
     * Creates an int32 parameter.
     *
     * @param value The int value
     * @return A new Param with Int32 type
     */
    public static Param int32(int value) {
        return new Param(value, new ArrowType.Int(32, true));
    }

    /**
     * Creates an int64 parameter.
     *
     * @param value The long value
     * @return A new Param with Int64 type
     */
    public static Param int64(long value) {
        return new Param(value, new ArrowType.Int(64, true));
    }

    /**
     * Creates a uint8 parameter.
     *
     * @param value The value (0-255)
     * @return A new Param with Uint8 type
     */
    public static Param uint8(short value) {
        return new Param(value, new ArrowType.Int(8, false));
    }

    /**
     * Creates a uint16 parameter.
     *
     * @param value The value (0-65535)
     * @return A new Param with Uint16 type
     */
    public static Param uint16(int value) {
        return new Param(value, new ArrowType.Int(16, false));
    }

    /**
     * Creates a uint32 parameter.
     *
     * @param value The value (0-4294967295)
     * @return A new Param with Uint32 type
     */
    public static Param uint32(long value) {
        return new Param(value, new ArrowType.Int(32, false));
    }

    /**
     * Creates a uint64 parameter.
     *
     * @param value The value
     * @return A new Param with Uint64 type
     */
    public static Param uint64(long value) {
        return new Param(value, new ArrowType.Int(64, false));
    }

    // ========== Floating Point Types ==========

    /**
     * Creates a float16 parameter.
     *
     * @param value The value (stored as short bits)
     * @return A new Param with Float16 type
     */
    public static Param float16(short value) {
        return new Param(value, new ArrowType.FloatingPoint(FloatingPointPrecision.HALF));
    }

    /**
     * Creates a float32 parameter.
     *
     * @param value The float value
     * @return A new Param with Float32 type
     */
    public static Param float32(float value) {
        return new Param(value, new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE));
    }

    /**
     * Creates a float64 (double) parameter.
     *
     * @param value The double value
     * @return A new Param with Float64 type
     */
    public static Param float64(double value) {
        return new Param(value, new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE));
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
        return new Param(value, new ArrowType.Date(DateUnit.DAY));
    }

    /**
     * Creates a date64 parameter (milliseconds since Unix epoch).
     *
     * @param value The LocalDate value
     * @return A new Param with Date64 type
     */
    public static Param date64(LocalDate value) {
        return new Param(value, new ArrowType.Date(DateUnit.MILLISECOND));
    }

    /**
     * Creates a time32 parameter with specified unit.
     *
     * @param value The LocalTime value
     * @param unit  The time unit (SECOND or MILLISECOND)
     * @return A new Param with Time32 type
     */
    public static Param time32(LocalTime value, TimeUnit unit) {
        if (unit != TimeUnit.SECOND && unit != TimeUnit.MILLISECOND) {
            throw new IllegalArgumentException("Time32 only supports SECOND or MILLISECOND units");
        }
        return new Param(value, new ArrowType.Time(unit, 32));
    }

    /**
     * Creates a time64 parameter with specified unit.
     *
     * @param value The LocalTime value
     * @param unit  The time unit (MICROSECOND or NANOSECOND)
     * @return A new Param with Time64 type
     */
    public static Param time64(LocalTime value, TimeUnit unit) {
        if (unit != TimeUnit.MICROSECOND && unit != TimeUnit.NANOSECOND) {
            throw new IllegalArgumentException("Time64 only supports MICROSECOND or NANOSECOND units");
        }
        return new Param(value, new ArrowType.Time(unit, 64));
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
