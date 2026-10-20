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

import java.net.URI;

import org.apache.arrow.flight.FlightRuntimeException;
import org.apache.arrow.flight.FlightStatusCode;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.ipc.ArrowReader;

import junit.framework.TestCase;

/**
 * API key auth against a server that, like a Spice OSS runtime secured with an
 * API key, accepts only {@code Authorization: Bearer <key>}.
 */
public class ApiKeyAuthTest extends TestCase {

    public void testPlainKeyIsSentAsBearerOnEveryCall() throws Exception {
        try (TestFlightSqlServer server = TestFlightSqlServer.withStaticApiKey("plain-key");
                SpiceClient client = SpiceClient.builder()
                        .withFlightAddress(server.flightUri())
                        .withApiKey("plain-key")
                        .build()) {
            try (FlightStream stream = client.sql("SELECT 1")) {
                assertEquals(server.expectedTotalRows(), LocalFlightServerTest.countRows(stream));
            }
            try (ArrowReader reader = client.sqlWithParams("SELECT * FROM t WHERE id=$1", 7L)) {
                assertEquals(server.expectedTotalRows(), LocalFlightServerTest.countRows(reader));
            }

            assertFalse("server saw calls", server.authorizationHeaders.isEmpty());
            for (String authorization : server.authorizationHeaders) {
                assertEquals("Bearer plain-key", authorization);
            }
            assertEquals("no Basic handshake", 0, server.basicAuthValidations.get());
        }
    }

    public void testPlainKeyViaConstructorWithoutAppId() throws Exception {
        try (TestFlightSqlServer server = TestFlightSqlServer.withStaticApiKey("plain-key");
                SpiceClient client = new SpiceClient(null, "plain-key", server.flightUri(),
                        new URI("http://localhost:8090"), 0, null, 256L)) {
            try (FlightStream stream = client.sql("SELECT 1")) {
                assertEquals(server.expectedTotalRows(), LocalFlightServerTest.countRows(stream));
            }
            assertTrue(client.usesBearerAuth());
        }
    }

    public void testWrongPlainKeyFailsWithoutRetrying() throws Exception {
        try (TestFlightSqlServer server = TestFlightSqlServer.withStaticApiKey("plain-key");
                SpiceClient client = SpiceClient.builder()
                        .withFlightAddress(server.flightUri())
                        .withApiKey("wrong-key")
                        .withMaxRetries(3)
                        .build()) {
            try (FlightStream stream = client.sql("SELECT 1")) {
                LocalFlightServerTest.countRows(stream);
                fail("Expected UNAUTHENTICATED");
            } catch (Exception e) {
                assertTrue("unexpected failure: " + e, causedByUnauthenticated(e));
            }
            assertEquals("a rejected Bearer key is not retried", 1, server.authorizationHeaders.size());
        }
    }

    /**
     * A revoked Bearer key fails a parameterized query on a cached prepared
     * statement without preparing a fresh statement with the rejected key.
     */
    public void testRevokedPlainKeyDoesNotRePrepareCachedStatement() throws Exception {
        try (TestFlightSqlServer server = TestFlightSqlServer.withStaticApiKey("plain-key");
                SpiceClient client = SpiceClient.builder()
                        .withFlightAddress(server.flightUri())
                        .withApiKey("plain-key")
                        .withMaxRetries(0)
                        .build()) {
            String sql = "SELECT * FROM t WHERE id=$1";
            try (ArrowReader reader = client.sqlWithParams(sql, 7L)) {
                assertEquals(server.expectedTotalRows(), LocalFlightServerTest.countRows(reader));
            }
            assertEquals("statement prepared once", 1, server.createPreparedStatementCalls.get());

            server.staticApiKeyRevoked = true;
            int callsBefore = server.authorizationHeaders.size();
            try (ArrowReader reader = client.sqlWithParams(sql, 8L)) {
                LocalFlightServerTest.countRows(reader);
                fail("Expected UNAUTHENTICATED");
            } catch (Exception e) {
                assertTrue("unexpected failure: " + e, causedByUnauthenticated(e));
            }
            int rejectedCalls = server.authorizationHeaders.size() - callsBefore;
            assertEquals("bind on the cached statement plus its close; no fresh prepare", 2, rejectedCalls);
        }
    }

    public void testCloudKeyStillUsesBasicHandshake() throws Exception {
        try (TestFlightSqlServer server = new TestFlightSqlServer("testapp", "testapp|secret");
                SpiceClient client = SpiceClient.builder()
                        .withFlightAddress(server.flightUri())
                        .withApiKey("testapp|secret")
                        .build()) {
            assertFalse(client.usesBearerAuth());
            assertEquals("constructor performs the handshake", 1, server.basicAuthValidations.get());
        }
    }

    private static boolean causedByUnauthenticated(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof FlightRuntimeException
                    && ((FlightRuntimeException) t).status().code() == FlightStatusCode.UNAUTHENTICATED) {
                return true;
            }
        }
        return false;
    }
}
