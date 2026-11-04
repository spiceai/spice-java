/*
Copyright 2026 The Spice.ai OSS Authors

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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.apache.arrow.vector.TimeStampVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;

import junit.framework.TestCase;

/**
 * Timestamp parameter encoding: range checks, timezone handling, java.time
 * inference and allocator hygiene on failure.
 */
public class TimestampParamTest extends TestCase {

    private static final LocalDateTime LDT_0300 = LocalDateTime.of(2024, 1, 15, 3, 0);
    private static final long LDT_0300_UTC_MICROS = 1_705_287_600_000_000L; // 2024-01-15T03:00Z

    private static long encode(SpiceClient client, Object param) {
        try (VectorSchemaRoot root = client.createParameterRoot(param)) {
            return ((TimeStampVector) root.getVector(0)).get(0);
        }
    }

    private static ArrowType.Timestamp type(SpiceClient client, Object param) {
        try (VectorSchemaRoot root = client.createParameterRoot(param)) {
            return (ArrowType.Timestamp) root.getVector(0).getField().getType();
        }
    }

    // ---- 3a: range ----

    public void testNanosecondFarFutureIsRejectedNotWrapped() throws Exception {
        try (SpiceClient client = SpiceClient.builder().build()) {
            try {
                encode(client, Param.timestamp(LocalDateTime.of(9999, 12, 31, 0, 0), TimeUnit.NANOSECOND));
                fail("expected out-of-range error");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("out of range for unit NANOSECOND"));
            }
        }
        // allocator closed cleanly above: no leaked param buffers
    }

    public void testNanosecondRangeEdges() throws Exception {
        try (SpiceClient client = SpiceClient.builder().build()) {
            Instant max = Instant.ofEpochSecond(0, Long.MAX_VALUE); // 2262-04-11T23:47:16.854775807Z
            Instant min = Instant.ofEpochSecond(0, Long.MIN_VALUE); // 1677-09-21T00:12:43.145224192Z
            assertEquals(Long.MAX_VALUE, encode(client, Param.timestampTz(max, TimeUnit.NANOSECOND)));
            assertEquals(Long.MIN_VALUE, encode(client, Param.timestampTz(min, TimeUnit.NANOSECOND)));
            assertEquals(Long.MAX_VALUE, encode(client,
                    Param.timestamp(LocalDateTime.ofInstant(max, ZoneOffset.UTC), TimeUnit.NANOSECOND, null)));
            for (Instant out : new Instant[] { max.plusNanos(1), min.minusNanos(1) }) {
                try {
                    encode(client, Param.timestampTz(out, TimeUnit.NANOSECOND));
                    fail("expected out-of-range error for " + out);
                } catch (IllegalArgumentException expected) {
                    // ok
                }
            }
        }
    }

    public void testFarFutureFitsInMicroAndMilli() throws Exception {
        LocalDateTime sentinel = LocalDateTime.of(9999, 12, 31, 0, 0);
        long secs = sentinel.toEpochSecond(ZoneOffset.UTC);
        try (SpiceClient client = SpiceClient.builder().build()) {
            assertEquals(secs * 1_000_000L, encode(client, Param.timestamp(sentinel, TimeUnit.MICROSECOND)));
            assertEquals(secs * 1_000L, encode(client, Param.timestamp(sentinel, TimeUnit.MILLISECOND)));
            assertEquals(secs, encode(client, Param.timestamp(sentinel, TimeUnit.SECOND)));
        }
    }

    public void testPreEpochSubSecond() throws Exception {
        try (SpiceClient client = SpiceClient.builder().build()) {
            Instant i = Instant.parse("1969-12-31T23:59:59.999999Z");
            assertEquals(-1L, encode(client, Param.timestampTz(i, TimeUnit.MICROSECOND)));
            assertEquals(-1L, encode(client, Param.timestampTz(i, TimeUnit.MILLISECOND)));
            assertEquals(-1L, encode(client, Param.timestampTz(i, TimeUnit.SECOND)));
        }
    }

    // ---- 3b: timezone on encode ----

    public void testLocalDateTimeInterpretedInVectorZone() throws Exception {
        try (SpiceClient client = SpiceClient.builder().build()) {
            assertEquals(LDT_0300_UTC_MICROS,
                    encode(client, Param.timestamp(LDT_0300, TimeUnit.MICROSECOND, "UTC")));
            // 03:00 in Los Angeles (PST, -08:00) is 11:00Z
            assertEquals(LDT_0300_UTC_MICROS + 8 * 3_600_000_000L,
                    encode(client, Param.timestamp(LDT_0300, TimeUnit.MICROSECOND, "America/Los_Angeles")));
            // 03:00 at +05:30 is 21:30Z the previous day
            assertEquals(LDT_0300_UTC_MICROS - 5L * 3_600_000_000L - 30L * 60_000_000L,
                    encode(client, Param.timestamp(LDT_0300, TimeUnit.MICROSECOND, "+05:30")));
            // naive type: wall clock stored as-is
            assertEquals(LDT_0300_UTC_MICROS,
                    encode(client, Param.timestamp(LDT_0300, TimeUnit.MICROSECOND, null)));
            // all units honour the zone
            assertEquals((LDT_0300_UTC_MICROS + 8 * 3_600_000_000L) * 1_000L,
                    encode(client, Param.timestamp(LDT_0300, TimeUnit.NANOSECOND, "America/Los_Angeles")));
            assertEquals((LDT_0300_UTC_MICROS + 8 * 3_600_000_000L) / 1_000_000L,
                    encode(client, Param.timestamp(LDT_0300, TimeUnit.SECOND, "America/Los_Angeles")));
        }
    }

    public void testEmptyTimezoneIsNaive() throws Exception {
        try (SpiceClient client = SpiceClient.builder().build()) {
            assertEquals(LDT_0300_UTC_MICROS, encode(client, Param.timestamp(LDT_0300, TimeUnit.MICROSECOND, "")));
            assertEquals(LDT_0300_UTC_MICROS, encode(client,
                    Param.of(LDT_0300, new ArrowType.Timestamp(TimeUnit.MICROSECOND, ""))));
        }
    }

    public void testNullTimestampStillCompilesAndBindsNull() throws Exception {
        try (SpiceClient client = SpiceClient.builder().build()) {
            try (VectorSchemaRoot root = client.createParameterRoot(Param.timestamp(null, TimeUnit.MICROSECOND))) {
                assertTrue(root.getVector(0).isNull(0));
            }
            for (Param p : new Param[] { Param.timestampTz((Instant) null, TimeUnit.MICROSECOND),
                    Param.timestampTz((OffsetDateTime) null, TimeUnit.MICROSECOND),
                    Param.timestampTz((ZonedDateTime) null, TimeUnit.MICROSECOND) }) {
                try (VectorSchemaRoot root = client.createParameterRoot(p)) {
                    assertTrue(root.getVector(0).isNull(0));
                }
            }
        }
    }

    public void testFixedZonesUseArrowOffsetForm() {
        assertEquals("+05:30", Param.arrowTimezone(ZoneId.of("UTC+05:30")));
        assertEquals("+00:00", Param.arrowTimezone(ZoneOffset.UTC));
        assertEquals("+00:00", Param.arrowTimezone(ZoneOffset.ofHoursMinutesSeconds(5, 30, 15)));
        assertEquals("-08:00", Param.arrowTimezone(ZoneId.of("GMT-08:00")));
        assertEquals("America/Los_Angeles", Param.arrowTimezone(ZoneId.of("America/Los_Angeles")));
    }

    public void testInvalidTimezoneRejected() {
        try {
            Param.timestamp(LDT_0300, TimeUnit.MICROSECOND, "Not/AZone");
            fail("expected invalid timezone error");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Not/AZone"));
        }
    }

    // ---- 3c: java.time types ----

    public void testInstantInferredAsUtcMicros() throws Exception {
        Instant i = Instant.parse("2024-01-15T11:00:00Z");
        try (SpiceClient client = SpiceClient.builder().build()) {
            assertEquals("UTC", type(client, i).getTimezone());
            assertEquals(TimeUnit.MICROSECOND, type(client, i).getUnit());
            assertEquals(LDT_0300_UTC_MICROS + 8 * 3_600_000_000L, encode(client, i));
            assertEquals(LDT_0300_UTC_MICROS + 8 * 3_600_000_000L, encode(client, Param.of(i)));
            // the workaround the old error message suggested
            assertEquals(LDT_0300_UTC_MICROS + 8 * 3_600_000_000L,
                    encode(client, Param.of(i, new ArrowType.Timestamp(TimeUnit.MICROSECOND, "UTC"))));
        }
    }

    public void testOffsetAndZonedDateTime() throws Exception {
        OffsetDateTime odt = OffsetDateTime.of(LDT_0300, ZoneOffset.ofHoursMinutes(5, 30));
        ZonedDateTime zdt = LDT_0300.atZone(ZoneId.of("America/Los_Angeles"));
        OffsetDateTime utc = OffsetDateTime.of(LDT_0300, ZoneOffset.UTC);
        try (SpiceClient client = SpiceClient.builder().build()) {
            assertEquals("+05:30", type(client, odt).getTimezone());
            assertEquals("America/Los_Angeles", type(client, zdt).getTimezone());
            assertEquals("+00:00", type(client, utc).getTimezone());
            assertEquals(odt.toInstant().toEpochMilli() * 1_000L, encode(client, odt));
            assertEquals(zdt.toInstant().toEpochMilli() * 1_000L, encode(client, zdt));
            assertEquals(LDT_0300_UTC_MICROS, encode(client, utc));
            assertEquals(zdt.toInstant().toEpochMilli(), encode(client, Param.timestampTz(zdt, TimeUnit.MILLISECOND)));
            assertEquals("+05:30", type(client, Param.timestampTz(odt, TimeUnit.NANOSECOND)).getTimezone());
            // an instant-typed value into a UTC vector keeps the instant
            assertEquals(zdt.toInstant().toEpochMilli() * 1_000L, encode(client,
                    Param.of(zdt, new ArrowType.Timestamp(TimeUnit.MICROSECOND, "UTC"))));
        }
    }

    public void testSqlTimestampIsNaiveWallClock() throws Exception {
        java.sql.Timestamp ts = java.sql.Timestamp.valueOf(LDT_0300);
        try (SpiceClient client = SpiceClient.builder().build()) {
            assertNull(type(client, ts).getTimezone());
            assertEquals(LDT_0300_UTC_MICROS, encode(client, ts));
            // into a tz-aware vector it is the instant it denotes
            assertEquals(ts.toInstant().toEpochMilli() * 1_000L, encode(client,
                    Param.of(ts, new ArrowType.Timestamp(TimeUnit.MICROSECOND, "UTC"))));
        }
    }

    // ---- 3d: no leak when encoding fails ----

    public void testFailedBindDoesNotLeakOnClose() throws Exception {
        SpiceClient client = SpiceClient.builder().build();
        try {
            client.createParameterRoot(42, "abc",
                    Param.of(new Object(), new ArrowType.Timestamp(TimeUnit.MICROSECOND, "UTC")));
            fail("expected failure");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        client.close(); // threw "Memory was leaked by query" before the fix
    }
}
