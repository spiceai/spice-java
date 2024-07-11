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

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightClient.Builder;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.Location;
import org.apache.arrow.flight.Ticket;
import org.apache.arrow.flight.auth2.BasicAuthCredentialWriter;
import org.apache.arrow.flight.auth2.ClientBearerHeaderHandler;
import org.apache.arrow.flight.auth2.ClientIncomingAuthHeaderMiddleware;
import org.apache.arrow.flight.grpc.CredentialCallOption;

import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.flight.sql.FlightSqlClient;

/**
 * Client to execute SQL queries against Spice.ai Cloud and Spice.ai OSS
 */
public class SpiceClient {

    private String appId;
    private String apiKey;
    private URI flightAddress;
    private FlightSqlClient flightClient;
    private CredentialCallOption authCallOptions = null;

    /**
     * Returns a new instance of SpiceClientBuilder
     *
     * @return A new SpiceClientBuilder instance
     * @throws URISyntaxException if there is an error in constructing the URI
     */
    public static SpiceClientBuilder builder() throws URISyntaxException {
        return new SpiceClientBuilder();
    }

    /**
     * Constructs a new SpiceClient instance with the specified parameters
    * @param appId        the application ID used to identify the client application
    * @param apiKey       the API key used for authentication with Spice.ai services
    * @param flightAddress the URI of the flight address for connecting to Spice.ai services
    */
    public SpiceClient(String appId, String apiKey, URI flightAddress) {
        this.appId = appId;
        this.apiKey = apiKey;

        // Arrow Flight requires URI to be grpc protocol, convert http/https for
        // convinience
        if (flightAddress.getScheme().equals("https")) {
            this.flightAddress = URI.create("grpc+tls://" + flightAddress.getHost() + ":" + flightAddress.getPort());
        } else if (flightAddress.getScheme().equals("http")) {
            this.flightAddress = URI.create("grpc+tcp://" + flightAddress.getHost() + ":" + flightAddress.getPort());
        } else {
            this.flightAddress = flightAddress;
        }

        Builder builder = FlightClient.builder(new RootAllocator(Long.MAX_VALUE), new Location(this.flightAddress));

        if (apiKey == null) {
            this.flightClient = new FlightSqlClient(builder.build());
            return;

        }

        final ClientIncomingAuthHeaderMiddleware.Factory factory = new ClientIncomingAuthHeaderMiddleware.Factory(
                new ClientBearerHeaderHandler());

        final FlightClient client = builder.intercept(factory).build();
        client.handshake(new CredentialCallOption(new BasicAuthCredentialWriter(this.appId, this.apiKey)));
        this.authCallOptions = factory.getCredentialCallOption();
        this.flightClient = new FlightSqlClient(client);
    }

    /**
     * Executes a sql query
     *
     * @param sql the SQL query to execute
     * @return a FlightStream with the query results
     */
    public FlightStream query(String sql) {
        if (sql == null || sql.isEmpty()) {
            throw new IllegalArgumentException("No SQL provided");
        }

        FlightInfo flightInfo = this.flightClient.execute(sql, authCallOptions);
        Ticket ticket = flightInfo.getEndpoints().get(0).getTicket();
        return this.flightClient.getStream(ticket, authCallOptions);
    }
}
