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

import java.math.BigDecimal;
import java.time.LocalDate;

import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;

import junit.framework.TestCase;

/**
 * Tests for parameter root construction: allocation sizing and type mapping.
 */
public class ParamRootTest extends TestCase {

    /**
     * The parameter root always holds exactly one row; its vectors must be
     * sized for that, not for Arrow's default ~3970-value capacity (which
     * previously allocated ~48KB per string parameter per query).
     */
    public void testParameterRootIsRightSizedForOneRow() throws Exception {
        try (SpiceClient client = SpiceClient.builder().build()) {
            try (VectorSchemaRoot root = client.createParameterRoot(
                    42, 42L, "hello", 3.5, true, LocalDate.of(2026, 7, 30), new BigDecimal("9.99"))) {
                assertEquals(1, root.getRowCount());
                for (FieldVector vector : root.getFieldVectors()) {
                    assertTrue(
                            "vector " + vector.getField() + " should be sized for ~1 value, capacity was "
                                    + vector.getValueCapacity(),
                            vector.getValueCapacity() < 512);
                }
            }
        }
    }

    public void testParameterRootValues() throws Exception {
        try (SpiceClient client = SpiceClient.builder().build()) {
            try (VectorSchemaRoot root = client.createParameterRoot(7, "abc")) {
                assertEquals(1, root.getRowCount());
                assertEquals("$1", root.getSchema().getFields().get(0).getName());
                assertEquals("$2", root.getSchema().getFields().get(1).getName());
                assertEquals(7, root.getVector(0).getObject(0));
                assertEquals("abc", root.getVector(1).getObject(0).toString());
            }
        }
    }

    public void testUnsupportedTypeThrowsIllegalArgument() throws Exception {
        try (SpiceClient client = SpiceClient.builder().build()) {
            try {
                client.createParameterRoot(new Object());
                fail("Expected IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("Unsupported parameter type"));
            }
        }
    }
}
