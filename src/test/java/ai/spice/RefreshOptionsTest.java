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

import junit.framework.TestCase;

/**
 * Unit tests for RefreshOptions class.
 */
public class RefreshOptionsTest extends TestCase {

    // ==================== Constructor Tests ====================

    public void testDefaultConstructor() {
        RefreshOptions options = new RefreshOptions();
        assertNull("refreshSql should be null by default", options.refreshSql);
        assertNull("refreshMode should be null by default", options.refreshMode);
        assertNull("refreshJitterMax should be null by default", options.refreshJitterMax);
    }

    // ==================== Fluent Builder Tests ====================

    public void testWithRefreshSql() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshSql("SELECT * FROM orders WHERE updated_at > NOW() - INTERVAL '1 hour'");
        assertEquals("SELECT * FROM orders WHERE updated_at > NOW() - INTERVAL '1 hour'", 
                options.refreshSql);
    }

    public void testWithRefreshMode() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshMode("full");
        assertEquals("full", options.refreshMode);
    }

    public void testWithRefreshModeAppend() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshMode("append");
        assertEquals("append", options.refreshMode);
    }

    public void testWithRefreshJitterMax() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshJitterMax("1m");
        assertEquals("1m", options.refreshJitterMax);
    }

    public void testWithRefreshJitterMaxSeconds() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshJitterMax("30s");
        assertEquals("30s", options.refreshJitterMax);
    }

    // ==================== Chained Builder Tests ====================

    public void testChainedBuilder() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshSql("SELECT * FROM table")
                .withRefreshMode("full")
                .withRefreshJitterMax("2m");
        
        assertEquals("SELECT * FROM table", options.refreshSql);
        assertEquals("full", options.refreshMode);
        assertEquals("2m", options.refreshJitterMax);
    }

    public void testChainedBuilderReturnsThis() {
        RefreshOptions options = new RefreshOptions();
        RefreshOptions result = options.withRefreshSql("test");
        assertSame("withRefreshSql should return same instance", options, result);
        
        result = options.withRefreshMode("full");
        assertSame("withRefreshMode should return same instance", options, result);
        
        result = options.withRefreshJitterMax("1m");
        assertSame("withRefreshJitterMax should return same instance", options, result);
    }

    // ==================== Override Tests ====================

    public void testOverrideRefreshSql() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshSql("original")
                .withRefreshSql("updated");
        assertEquals("updated", options.refreshSql);
    }

    public void testOverrideRefreshMode() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshMode("full")
                .withRefreshMode("append");
        assertEquals("append", options.refreshMode);
    }

    public void testOverrideRefreshJitterMax() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshJitterMax("1m")
                .withRefreshJitterMax("5m");
        assertEquals("5m", options.refreshJitterMax);
    }

    // ==================== Null Value Tests ====================

    public void testWithNullRefreshSql() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshSql("test")
                .withRefreshSql(null);
        assertNull(options.refreshSql);
    }

    public void testWithNullRefreshMode() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshMode("full")
                .withRefreshMode(null);
        assertNull(options.refreshMode);
    }

    public void testWithNullRefreshJitterMax() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshJitterMax("1m")
                .withRefreshJitterMax(null);
        assertNull(options.refreshJitterMax);
    }

    // ==================== Empty String Tests ====================

    public void testWithEmptyRefreshSql() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshSql("");
        assertEquals("", options.refreshSql);
    }

    public void testWithEmptyRefreshMode() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshMode("");
        assertEquals("", options.refreshMode);
    }

    public void testWithEmptyRefreshJitterMax() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshJitterMax("");
        assertEquals("", options.refreshJitterMax);
    }

    // ==================== Complex SQL Tests ====================

    public void testWithComplexRefreshSql() {
        String complexSql = "SELECT c.customer_id, c.name, o.order_id, o.total " +
                "FROM customers c " +
                "LEFT JOIN orders o ON c.customer_id = o.customer_id " +
                "WHERE o.created_at > NOW() - INTERVAL '1 day' " +
                "ORDER BY o.total DESC";
        
        RefreshOptions options = new RefreshOptions()
                .withRefreshSql(complexSql);
        assertEquals(complexSql, options.refreshSql);
    }

    public void testWithSqlContainingSpecialChars() {
        String sql = "SELECT * FROM table WHERE name = 'O''Brien' AND value > 100";
        RefreshOptions options = new RefreshOptions()
                .withRefreshSql(sql);
        assertEquals(sql, options.refreshSql);
    }

    public void testWithSqlContainingUnicode() {
        String sql = "SELECT * FROM table WHERE name = '日本語' OR city = 'Москва'";
        RefreshOptions options = new RefreshOptions()
                .withRefreshSql(sql);
        assertEquals(sql, options.refreshSql);
    }

    // ==================== Jitter Format Tests ====================

    public void testJitterMaxVariousFormats() {
        String[] validFormats = { "1s", "30s", "1m", "5m", "1h", "100ms", "2.5s" };
        
        for (String format : validFormats) {
            RefreshOptions options = new RefreshOptions()
                    .withRefreshJitterMax(format);
            assertEquals(format, options.refreshJitterMax);
        }
    }

    // ==================== Partial Configuration Tests ====================

    public void testWithOnlyRefreshSql() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshSql("SELECT * FROM table");
        
        assertEquals("SELECT * FROM table", options.refreshSql);
        assertNull(options.refreshMode);
        assertNull(options.refreshJitterMax);
    }

    public void testWithOnlyRefreshMode() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshMode("full");
        
        assertNull(options.refreshSql);
        assertEquals("full", options.refreshMode);
        assertNull(options.refreshJitterMax);
    }

    public void testWithOnlyRefreshJitterMax() {
        RefreshOptions options = new RefreshOptions()
                .withRefreshJitterMax("1m");
        
        assertNull(options.refreshSql);
        assertNull(options.refreshMode);
        assertEquals("1m", options.refreshJitterMax);
    }

    // ==================== Instance Independence Tests ====================

    public void testInstanceIndependence() {
        RefreshOptions options1 = new RefreshOptions()
                .withRefreshMode("full");
        
        RefreshOptions options2 = new RefreshOptions()
                .withRefreshMode("append");
        
        assertEquals("full", options1.refreshMode);
        assertEquals("append", options2.refreshMode);
    }

    public void testMultipleInstances() {
        RefreshOptions[] options = new RefreshOptions[10];
        for (int i = 0; i < 10; i++) {
            options[i] = new RefreshOptions()
                    .withRefreshSql("SELECT " + i)
                    .withRefreshMode("mode" + i)
                    .withRefreshJitterMax(i + "m");
        }
        
        for (int i = 0; i < 10; i++) {
            assertEquals("SELECT " + i, options[i].refreshSql);
            assertEquals("mode" + i, options[i].refreshMode);
            assertEquals(i + "m", options[i].refreshJitterMax);
        }
    }
}
