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
import java.util.concurrent.ExecutionException;

import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.vector.ipc.ArrowReader;

import junit.framework.TestCase;

/**
 * TLS and mutual-TLS integration tests: a real TLS handshake against an
 * in-process Flight SQL server using runtime-generated certificates —
 * covering custom-CA trust, client-certificate authentication, and the
 * corresponding rejection paths. This exercises the SDK's file-based TLS
 * configuration end-to-end (previously only builder validation was tested).
 */
public class MtlsTest extends TestCase {

    private static TestCerts certs;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (certs == null) {
            certs = TestCerts.generate();
        }
    }

    private static SpiceClientBuilder clientFor(TestFlightSqlServer server) throws Exception {
        return SpiceClient.builder()
                .withFlightAddress(new URI("grpc+tls://localhost:" + server.getPort()))
                .withMaxRetries(0);
    }

    private static void assertQueriesWork(SpiceClient client, TestFlightSqlServer server) throws Exception {
        try (FlightStream stream = client.query("SELECT * FROM test")) {
            assertEquals(server.expectedTotalRows(), LocalFlightServerTest.countRows(stream));
        }
        // Parameterized queries run on the same TLS channel (prepared
        // statements inherit the transport configuration).
        try (ArrowReader reader = client.queryWithParams("SELECT * FROM test WHERE id > $1", 1L)) {
            assertEquals(server.expectedTotalRows(), LocalFlightServerTest.countRows(reader));
        }
    }

    /** Server TLS with a custom CA: the client trusts it via withTlsRootCertFile. */
    public void testTlsWithCustomRootCa() throws Exception {
        try (TestFlightSqlServer server = new TestFlightSqlServer(certs, false);
                SpiceClient client = clientFor(server)
                        .withTlsRootCertFile(certs.caCert.toString())
                        .build()) {
            assertQueriesWork(client, server);
        }
    }

    /** Full mutual TLS: server verifies the client certificate. */
    public void testMutualTls() throws Exception {
        try (TestFlightSqlServer server = new TestFlightSqlServer(certs, true);
                SpiceClient client = clientFor(server)
                        .withTlsRootCertFile(certs.caCert.toString())
                        .withTlsClientCertFile(certs.clientCert.toString())
                        .withTlsClientKeyFile(certs.clientKey.toString())
                        .build()) {
            assertQueriesWork(client, server);
        }
    }

    /** An mTLS server rejects clients that present no certificate. */
    public void testMissingClientCertificateRejected() throws Exception {
        try (TestFlightSqlServer server = new TestFlightSqlServer(certs, true);
                SpiceClient client = clientFor(server)
                        .withTlsRootCertFile(certs.caCert.toString())
                        .build()) {
            try {
                client.query("SELECT 1");
                fail("Expected the TLS handshake to be rejected without a client certificate");
            } catch (ExecutionException expected) {
                // Clean, classifiable transport failure — not a hang or an NPE.
            }
        }
    }

    /** A client that trusts a different CA must reject the server. */
    public void testUntrustedServerCaRejected() throws Exception {
        try (TestFlightSqlServer server = new TestFlightSqlServer(certs, false);
                SpiceClient client = clientFor(server)
                        .withTlsRootCertFile(certs.otherCaCert.toString())
                        .build()) {
            try {
                client.query("SELECT 1");
                fail("Expected certificate verification to fail against an untrusted CA");
            } catch (ExecutionException expected) {
                // expected
            }
        }
    }
}
