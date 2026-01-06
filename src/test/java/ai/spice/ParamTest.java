/*
Copyright 2024-2026 The Spice.ai OSS Authors

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

import junit.framework.TestCase;

/**
 * Comprehensive unit tests for the Param class.
 * Tests all factory methods, edge cases, and type validation.
 */
public class ParamTest extends TestCase {

    // ==================== Constructor Tests ====================

    public void testConstructorWithValue() {
        Param param = new Param("test");
        assertEquals("test", param.getValue());
        assertNull("Type should be null for inferred", param.getType());
        assertFalse("Should not have explicit type", param.hasExplicitType());
    }

    public void testConstructorWithValueAndType() {
        ArrowType type = ArrowType.Utf8.INSTANCE;
        Param param = new Param("test", type);
        assertEquals("test", param.getValue());
        assertEquals(type, param.getType());
        assertTrue("Should have explicit type", param.hasExplicitType());
    }

    public void testConstructorWithNullValue() {
        Param param = new Param(null);
        assertNull(param.getValue());
        assertNull(param.getType());
    }

    public void testConstructorWithNullValueAndType() {
        Param param = new Param(null, ArrowType.Null.INSTANCE);
        assertNull(param.getValue());
        assertEquals(ArrowType.Null.INSTANCE, param.getType());
    }

    // ==================== Integer Type Tests ====================

    public void testInt8() {
        Param param = Param.int8((byte) 127);
        assertEquals((byte) 127, param.getValue());
        assertTrue(param.hasExplicitType());
        assertTrue(param.getType() instanceof ArrowType.Int);
        ArrowType.Int intType = (ArrowType.Int) param.getType();
        assertEquals(8, intType.getBitWidth());
        assertTrue(intType.getIsSigned());
    }

    public void testInt8MinMax() {
        Param min = Param.int8(Byte.MIN_VALUE);
        assertEquals(Byte.MIN_VALUE, min.getValue());
        
        Param max = Param.int8(Byte.MAX_VALUE);
        assertEquals(Byte.MAX_VALUE, max.getValue());
    }

    public void testInt16() {
        Param param = Param.int16((short) 32767);
        assertEquals((short) 32767, param.getValue());
        assertTrue(param.hasExplicitType());
        ArrowType.Int intType = (ArrowType.Int) param.getType();
        assertEquals(16, intType.getBitWidth());
        assertTrue(intType.getIsSigned());
    }

    public void testInt16MinMax() {
        Param min = Param.int16(Short.MIN_VALUE);
        assertEquals(Short.MIN_VALUE, min.getValue());
        
        Param max = Param.int16(Short.MAX_VALUE);
        assertEquals(Short.MAX_VALUE, max.getValue());
    }

    public void testInt32() {
        Param param = Param.int32(123456);
        assertEquals(123456, param.getValue());
        assertTrue(param.hasExplicitType());
        ArrowType.Int intType = (ArrowType.Int) param.getType();
        assertEquals(32, intType.getBitWidth());
        assertTrue(intType.getIsSigned());
    }

    public void testInt32MinMax() {
        Param min = Param.int32(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, min.getValue());
        
        Param max = Param.int32(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, max.getValue());
    }

    public void testInt64() {
        Param param = Param.int64(9876543210L);
        assertEquals(9876543210L, param.getValue());
        assertTrue(param.hasExplicitType());
        ArrowType.Int intType = (ArrowType.Int) param.getType();
        assertEquals(64, intType.getBitWidth());
        assertTrue(intType.getIsSigned());
    }

    public void testInt64MinMax() {
        Param min = Param.int64(Long.MIN_VALUE);
        assertEquals(Long.MIN_VALUE, min.getValue());
        
        Param max = Param.int64(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, max.getValue());
    }

    // ==================== Unsigned Integer Tests ====================

    public void testUint8() {
        Param param = Param.uint8((short) 255);
        assertEquals((short) 255, param.getValue());
        assertTrue(param.hasExplicitType());
        ArrowType.Int intType = (ArrowType.Int) param.getType();
        assertEquals(8, intType.getBitWidth());
        assertFalse(intType.getIsSigned());
    }

    public void testUint16() {
        Param param = Param.uint16(65535);
        assertEquals(65535, param.getValue());
        assertTrue(param.hasExplicitType());
        ArrowType.Int intType = (ArrowType.Int) param.getType();
        assertEquals(16, intType.getBitWidth());
        assertFalse(intType.getIsSigned());
    }

    public void testUint32() {
        Param param = Param.uint32(4294967295L);
        assertEquals(4294967295L, param.getValue());
        assertTrue(param.hasExplicitType());
        ArrowType.Int intType = (ArrowType.Int) param.getType();
        assertEquals(32, intType.getBitWidth());
        assertFalse(intType.getIsSigned());
    }

    public void testUint64() {
        Param param = Param.uint64(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, param.getValue());
        assertTrue(param.hasExplicitType());
        ArrowType.Int intType = (ArrowType.Int) param.getType();
        assertEquals(64, intType.getBitWidth());
        assertFalse(intType.getIsSigned());
    }

    // ==================== Floating Point Tests ====================

    public void testFloat16() {
        Param param = Param.float16((short) 16384);
        assertEquals((short) 16384, param.getValue());
        assertTrue(param.hasExplicitType());
        ArrowType.FloatingPoint fpType = (ArrowType.FloatingPoint) param.getType();
        assertEquals(FloatingPointPrecision.HALF, fpType.getPrecision());
    }

    public void testFloat32() {
        Param param = Param.float32(3.14f);
        assertEquals(3.14f, param.getValue());
        assertTrue(param.hasExplicitType());
        ArrowType.FloatingPoint fpType = (ArrowType.FloatingPoint) param.getType();
        assertEquals(FloatingPointPrecision.SINGLE, fpType.getPrecision());
    }

    public void testFloat32SpecialValues() {
        Param posInf = Param.float32(Float.POSITIVE_INFINITY);
        assertEquals(Float.POSITIVE_INFINITY, posInf.getValue());
        
        Param negInf = Param.float32(Float.NEGATIVE_INFINITY);
        assertEquals(Float.NEGATIVE_INFINITY, negInf.getValue());
        
        Param nan = Param.float32(Float.NaN);
        assertTrue(Float.isNaN((Float) nan.getValue()));
        
        Param min = Param.float32(Float.MIN_VALUE);
        assertEquals(Float.MIN_VALUE, min.getValue());
        
        Param max = Param.float32(Float.MAX_VALUE);
        assertEquals(Float.MAX_VALUE, max.getValue());
    }

    public void testFloat64() {
        Param param = Param.float64(3.141592653589793);
        assertEquals(3.141592653589793, param.getValue());
        assertTrue(param.hasExplicitType());
        ArrowType.FloatingPoint fpType = (ArrowType.FloatingPoint) param.getType();
        assertEquals(FloatingPointPrecision.DOUBLE, fpType.getPrecision());
    }

    public void testFloat64SpecialValues() {
        Param posInf = Param.float64(Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, posInf.getValue());
        
        Param negInf = Param.float64(Double.NEGATIVE_INFINITY);
        assertEquals(Double.NEGATIVE_INFINITY, negInf.getValue());
        
        Param nan = Param.float64(Double.NaN);
        assertTrue(Double.isNaN((Double) nan.getValue()));
        
        Param min = Param.float64(Double.MIN_VALUE);
        assertEquals(Double.MIN_VALUE, min.getValue());
        
        Param max = Param.float64(Double.MAX_VALUE);
        assertEquals(Double.MAX_VALUE, max.getValue());
    }

    // ==================== String and Binary Tests ====================

    public void testString() {
        Param param = Param.string("hello world");
        assertEquals("hello world", param.getValue());
        assertTrue(param.hasExplicitType());
        assertEquals(ArrowType.Utf8.INSTANCE, param.getType());
    }

    public void testStringEmpty() {
        Param param = Param.string("");
        assertEquals("", param.getValue());
        assertEquals(ArrowType.Utf8.INSTANCE, param.getType());
    }

    public void testStringNull() {
        Param param = Param.string(null);
        assertNull(param.getValue());
        assertEquals(ArrowType.Utf8.INSTANCE, param.getType());
    }

    public void testStringUnicode() {
        String unicode = "Hello 世界 🌍 привет";
        Param param = Param.string(unicode);
        assertEquals(unicode, param.getValue());
    }

    public void testStringLong() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("a");
        }
        Param param = Param.string(sb.toString());
        assertEquals(sb.toString(), param.getValue());
    }

    public void testLargeString() {
        Param param = Param.largeString("large value");
        assertEquals("large value", param.getValue());
        assertTrue(param.hasExplicitType());
        assertEquals(ArrowType.LargeUtf8.INSTANCE, param.getType());
    }

    public void testBinary() {
        byte[] data = new byte[] { 0x01, 0x02, 0x03, 0x04 };
        Param param = Param.binary(data);
        assertSame(data, param.getValue());
        assertTrue(param.hasExplicitType());
        assertEquals(ArrowType.Binary.INSTANCE, param.getType());
    }

    public void testBinaryEmpty() {
        byte[] data = new byte[0];
        Param param = Param.binary(data);
        assertSame(data, param.getValue());
    }

    public void testBinaryNull() {
        Param param = Param.binary(null);
        assertNull(param.getValue());
        assertEquals(ArrowType.Binary.INSTANCE, param.getType());
    }

    public void testLargeBinary() {
        byte[] data = new byte[] { 0x01, 0x02, 0x03 };
        Param param = Param.largeBinary(data);
        assertSame(data, param.getValue());
        assertTrue(param.hasExplicitType());
        assertEquals(ArrowType.LargeBinary.INSTANCE, param.getType());
    }

    public void testFixedSizeBinary() {
        byte[] data = new byte[] { 0x01, 0x02, 0x03, 0x04 };
        Param param = Param.fixedSizeBinary(data, 4);
        assertSame(data, param.getValue());
        assertTrue(param.hasExplicitType());
        assertTrue(param.getType() instanceof ArrowType.FixedSizeBinary);
        assertEquals(4, ((ArrowType.FixedSizeBinary) param.getType()).getByteWidth());
    }

    // ==================== Boolean Tests ====================

    public void testBoolTrue() {
        Param param = Param.bool(true);
        assertEquals(true, param.getValue());
        assertTrue(param.hasExplicitType());
        assertEquals(ArrowType.Bool.INSTANCE, param.getType());
    }

    public void testBoolFalse() {
        Param param = Param.bool(false);
        assertEquals(false, param.getValue());
        assertTrue(param.hasExplicitType());
        assertEquals(ArrowType.Bool.INSTANCE, param.getType());
    }

    // ==================== Date Tests ====================

    public void testDate32() {
        LocalDate date = LocalDate.of(2024, 6, 15);
        Param param = Param.date32(date);
        assertEquals(date, param.getValue());
        assertTrue(param.hasExplicitType());
        assertTrue(param.getType() instanceof ArrowType.Date);
        assertEquals(DateUnit.DAY, ((ArrowType.Date) param.getType()).getUnit());
    }

    public void testDate32Epoch() {
        LocalDate epoch = LocalDate.of(1970, 1, 1);
        Param param = Param.date32(epoch);
        assertEquals(epoch, param.getValue());
    }

    public void testDate32Future() {
        LocalDate future = LocalDate.of(2100, 12, 31);
        Param param = Param.date32(future);
        assertEquals(future, param.getValue());
    }

    public void testDate32Past() {
        LocalDate past = LocalDate.of(1900, 1, 1);
        Param param = Param.date32(past);
        assertEquals(past, param.getValue());
    }

    public void testDate64() {
        LocalDate date = LocalDate.of(2024, 6, 15);
        Param param = Param.date64(date);
        assertEquals(date, param.getValue());
        assertTrue(param.hasExplicitType());
        assertTrue(param.getType() instanceof ArrowType.Date);
        assertEquals(DateUnit.MILLISECOND, ((ArrowType.Date) param.getType()).getUnit());
    }

    // ==================== Time Tests ====================

    public void testTime32Second() {
        LocalTime time = LocalTime.of(14, 30, 45);
        Param param = Param.time32(time, TimeUnit.SECOND);
        assertEquals(time, param.getValue());
        assertTrue(param.hasExplicitType());
        assertTrue(param.getType() instanceof ArrowType.Time);
        ArrowType.Time timeType = (ArrowType.Time) param.getType();
        assertEquals(TimeUnit.SECOND, timeType.getUnit());
        assertEquals(32, timeType.getBitWidth());
    }

    public void testTime32Millisecond() {
        LocalTime time = LocalTime.of(14, 30, 45, 123000000);
        Param param = Param.time32(time, TimeUnit.MILLISECOND);
        assertEquals(time, param.getValue());
        ArrowType.Time timeType = (ArrowType.Time) param.getType();
        assertEquals(TimeUnit.MILLISECOND, timeType.getUnit());
    }

    public void testTime32InvalidUnit() {
        try {
            Param.time32(LocalTime.now(), TimeUnit.MICROSECOND);
            fail("Should throw for invalid time32 unit");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("SECOND") || e.getMessage().contains("MILLISECOND"));
        }
    }

    public void testTime64Microsecond() {
        LocalTime time = LocalTime.of(14, 30, 45, 123456000);
        Param param = Param.time64(time, TimeUnit.MICROSECOND);
        assertEquals(time, param.getValue());
        assertTrue(param.hasExplicitType());
        ArrowType.Time timeType = (ArrowType.Time) param.getType();
        assertEquals(TimeUnit.MICROSECOND, timeType.getUnit());
        assertEquals(64, timeType.getBitWidth());
    }

    public void testTime64Nanosecond() {
        LocalTime time = LocalTime.of(14, 30, 45, 123456789);
        Param param = Param.time64(time, TimeUnit.NANOSECOND);
        assertEquals(time, param.getValue());
        ArrowType.Time timeType = (ArrowType.Time) param.getType();
        assertEquals(TimeUnit.NANOSECOND, timeType.getUnit());
    }

    public void testTime64InvalidUnit() {
        try {
            Param.time64(LocalTime.now(), TimeUnit.SECOND);
            fail("Should throw for invalid time64 unit");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("MICROSECOND") || e.getMessage().contains("NANOSECOND"));
        }
    }

    public void testTimeMidnight() {
        LocalTime midnight = LocalTime.MIDNIGHT;
        Param param = Param.time64(midnight, TimeUnit.MICROSECOND);
        assertEquals(midnight, param.getValue());
    }

    public void testTimeMaxValue() {
        LocalTime max = LocalTime.of(23, 59, 59, 999999999);
        Param param = Param.time64(max, TimeUnit.NANOSECOND);
        assertEquals(max, param.getValue());
    }

    // ==================== Timestamp Tests ====================

    public void testTimestampWithTimezone() {
        LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
        Param param = Param.timestamp(dt, TimeUnit.MICROSECOND, "America/New_York");
        assertEquals(dt, param.getValue());
        assertTrue(param.hasExplicitType());
        ArrowType.Timestamp tsType = (ArrowType.Timestamp) param.getType();
        assertEquals(TimeUnit.MICROSECOND, tsType.getUnit());
        assertEquals("America/New_York", tsType.getTimezone());
    }

    public void testTimestampUTC() {
        LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
        Param param = Param.timestamp(dt, TimeUnit.MICROSECOND);
        assertEquals(dt, param.getValue());
        ArrowType.Timestamp tsType = (ArrowType.Timestamp) param.getType();
        assertEquals("UTC", tsType.getTimezone());
    }

    public void testTimestampAllUnits() {
        LocalDateTime dt = LocalDateTime.now();
        
        Param second = Param.timestamp(dt, TimeUnit.SECOND, "UTC");
        assertEquals(TimeUnit.SECOND, ((ArrowType.Timestamp) second.getType()).getUnit());
        
        Param milli = Param.timestamp(dt, TimeUnit.MILLISECOND, "UTC");
        assertEquals(TimeUnit.MILLISECOND, ((ArrowType.Timestamp) milli.getType()).getUnit());
        
        Param micro = Param.timestamp(dt, TimeUnit.MICROSECOND, "UTC");
        assertEquals(TimeUnit.MICROSECOND, ((ArrowType.Timestamp) micro.getType()).getUnit());
        
        Param nano = Param.timestamp(dt, TimeUnit.NANOSECOND, "UTC");
        assertEquals(TimeUnit.NANOSECOND, ((ArrowType.Timestamp) nano.getType()).getUnit());
    }

    // ==================== Duration Tests ====================

    public void testDuration() {
        Duration dur = Duration.ofHours(2).plusMinutes(30);
        Param param = Param.duration(dur, TimeUnit.MICROSECOND);
        assertEquals(dur, param.getValue());
        assertTrue(param.hasExplicitType());
        assertTrue(param.getType() instanceof ArrowType.Duration);
        assertEquals(TimeUnit.MICROSECOND, ((ArrowType.Duration) param.getType()).getUnit());
    }

    public void testDurationZero() {
        Duration dur = Duration.ZERO;
        Param param = Param.duration(dur, TimeUnit.SECOND);
        assertEquals(dur, param.getValue());
    }

    public void testDurationNegative() {
        Duration dur = Duration.ofHours(-5);
        Param param = Param.duration(dur, TimeUnit.MILLISECOND);
        assertEquals(dur, param.getValue());
    }

    // ==================== Decimal Tests ====================

    public void testDecimal128() {
        BigDecimal value = new BigDecimal("12345.67890");
        Param param = Param.decimal128(value, 10, 5);
        assertEquals(value, param.getValue());
        assertTrue(param.hasExplicitType());
        assertTrue(param.getType() instanceof ArrowType.Decimal);
        ArrowType.Decimal decType = (ArrowType.Decimal) param.getType();
        assertEquals(10, decType.getPrecision());
        assertEquals(5, decType.getScale());
        assertEquals(128, decType.getBitWidth());
    }

    public void testDecimal128MinPrecision() {
        BigDecimal value = new BigDecimal("1");
        Param param = Param.decimal128(value, 1, 0);
        assertEquals(1, ((ArrowType.Decimal) param.getType()).getPrecision());
    }

    public void testDecimal128MaxPrecision() {
        BigDecimal value = new BigDecimal("1");
        Param param = Param.decimal128(value, 38, 0);
        assertEquals(38, ((ArrowType.Decimal) param.getType()).getPrecision());
    }

    public void testDecimal128PrecisionTooLow() {
        try {
            Param.decimal128(new BigDecimal("1"), 0, 0);
            fail("Should throw for precision < 1");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("precision"));
        }
    }

    public void testDecimal128PrecisionTooHigh() {
        try {
            Param.decimal128(new BigDecimal("1"), 39, 0);
            fail("Should throw for precision > 38");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("precision"));
        }
    }

    public void testDecimal256() {
        BigDecimal value = new BigDecimal("999999999999999999999999999999999999999.999999999");
        Param param = Param.decimal256(value, 50, 9);
        assertEquals(value, param.getValue());
        assertTrue(param.hasExplicitType());
        ArrowType.Decimal decType = (ArrowType.Decimal) param.getType();
        assertEquals(50, decType.getPrecision());
        assertEquals(9, decType.getScale());
        assertEquals(256, decType.getBitWidth());
    }

    public void testDecimal256MaxPrecision() {
        BigDecimal value = new BigDecimal("1");
        Param param = Param.decimal256(value, 76, 0);
        assertEquals(76, ((ArrowType.Decimal) param.getType()).getPrecision());
    }

    public void testDecimal256PrecisionTooHigh() {
        try {
            Param.decimal256(new BigDecimal("1"), 77, 0);
            fail("Should throw for precision > 76");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("precision"));
        }
    }

    // ==================== Null Type Tests ====================

    public void testNullValue() {
        Param param = Param.nullValue();
        assertNull(param.getValue());
        assertTrue(param.hasExplicitType());
        assertEquals(ArrowType.Null.INSTANCE, param.getType());
    }

    // ==================== Generic Factory Tests ====================

    public void testOfWithValue() {
        Param param = Param.of("test");
        assertEquals("test", param.getValue());
        assertNull(param.getType());
        assertFalse(param.hasExplicitType());
    }

    public void testOfWithValueAndType() {
        ArrowType type = ArrowType.Utf8.INSTANCE;
        Param param = Param.of("test", type);
        assertEquals("test", param.getValue());
        assertEquals(type, param.getType());
        assertTrue(param.hasExplicitType());
    }

    public void testOfWithNull() {
        Param param = Param.of(null);
        assertNull(param.getValue());
        assertFalse(param.hasExplicitType());
    }

    // ==================== toString Tests ====================

    public void testToStringWithType() {
        Param param = Param.int32(42);
        String str = param.toString();
        assertTrue("Should contain 'Param'", str.contains("Param"));
        assertTrue("Should contain value", str.contains("42"));
        assertTrue("Should contain type", str.contains("Int"));
    }

    public void testToStringWithInferredType() {
        Param param = Param.of(42);
        String str = param.toString();
        assertTrue("Should contain 'inferred'", str.contains("inferred"));
    }

    public void testToStringWithNullValue() {
        Param param = Param.nullValue();
        String str = param.toString();
        assertTrue("Should contain 'null'", str.contains("null"));
    }

    // ==================== Type Caching Tests ====================

    public void testInt32TypeCaching() {
        // Same type instance should be reused
        Param p1 = Param.int32(1);
        Param p2 = Param.int32(2);
        assertSame("Type should be cached", p1.getType(), p2.getType());
    }

    public void testFloat64TypeCaching() {
        Param p1 = Param.float64(1.0);
        Param p2 = Param.float64(2.0);
        assertSame("Type should be cached", p1.getType(), p2.getType());
    }

    public void testDate32TypeCaching() {
        Param p1 = Param.date32(LocalDate.now());
        Param p2 = Param.date32(LocalDate.now().plusDays(1));
        assertSame("Type should be cached", p1.getType(), p2.getType());
    }

    public void testStringTypeIsShared() {
        Param p1 = Param.string("a");
        Param p2 = Param.string("b");
        assertSame("Utf8 type should be singleton", p1.getType(), p2.getType());
    }

    public void testBoolTypeIsShared() {
        Param p1 = Param.bool(true);
        Param p2 = Param.bool(false);
        assertSame("Bool type should be singleton", p1.getType(), p2.getType());
    }

    // ==================== Edge Cases ====================

    public void testVeryLargeInt64() {
        Param param = Param.int64(9223372036854775807L); // Long.MAX_VALUE
        assertEquals(9223372036854775807L, param.getValue());
    }

    public void testVerySmallInt64() {
        Param param = Param.int64(-9223372036854775808L); // Long.MIN_VALUE
        assertEquals(-9223372036854775808L, param.getValue());
    }

    public void testDecimalWithNegativeScale() {
        // Negative scale is valid (shifts decimal point right)
        BigDecimal value = new BigDecimal("123000");
        Param param = Param.decimal128(value, 6, -3);
        ArrowType.Decimal decType = (ArrowType.Decimal) param.getType();
        assertEquals(-3, decType.getScale());
    }

    public void testManyTimezones() {
        LocalDateTime dt = LocalDateTime.now();
        String[] timezones = { "UTC", "America/New_York", "Europe/London", 
                               "Asia/Tokyo", "Australia/Sydney", "Pacific/Auckland" };
        
        for (String tz : timezones) {
            Param param = Param.timestamp(dt, TimeUnit.MICROSECOND, tz);
            assertEquals(tz, ((ArrowType.Timestamp) param.getType()).getTimezone());
        }
    }
}
