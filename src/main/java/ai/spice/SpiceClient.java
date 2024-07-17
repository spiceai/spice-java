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
import java.util.concurrent.ExecutionException;

import org.apache.arrow.flight.CallStatus;
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
import org.apache.arrow.flight.FlightRuntimeException;
import org.apache.arrow.memory.RootAllocator;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Strings;

import org.apache.arrow.flight.sql.FlightSqlClient;

/**
 * Client to execute SQL queries against Spice.ai Cloud and Spice.ai OSS
 */
public class SpiceClient implements AutoCloseable {

    private String appId;
    private String apiKey;
    private URI flightAddress;
    private int maxRetries;
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
     * 
     * @param appId         the application ID used to identify the client
     *                      application
     * @param apiKey        the API key used for authentication with Spice.ai
     *                      services
     * @param flightAddress the URI of the flight address for connecting to Spice.ai
     *                      services
     * @param maxRetries    the maximum number of connection retries for the client
     */
    public SpiceClient(String appId, String apiKey, URI flightAddress, int maxRetries) {
        this.appId = appId;
        this.apiKey = apiKey;
        this.maxRetries = maxRetries;

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

        if (Strings.isNullOrEmpty(apiKey)) {
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
     * @throws ExecutionException if there is an error executing the query
     */
    public FlightStream query(String sql) throws ExecutionException {
        if (Strings.isNullOrEmpty(sql)) {
            throw new IllegalArgumentException("No SQL query provided");
        }

        try {
            return this.queryInternalWithRetry(sql);
        } catch (RetryException e) {
            Throwable err = e.getLastFailedAttempt().getExceptionCause();
            throw new ExecutionException("Failed to execute query due to error: " + err.getMessage(), err);
        }
    }

    private FlightStream queryInternal(String sql) {
        FlightInfo flightInfo = this.flightClient.execute(sql, authCallOptions);
        Ticket ticket = flightInfo.getEndpoints().get(0).getTicket();
        return this.flightClient.getStream(ticket, authCallOptions);
    }

    private FlightStream queryInternalWithRetry(String sql) throws ExecutionException, RetryException {
        Retryer<FlightStream> retryer = RetryerBuilder.<FlightStream>newBuilder()
                .retryIfException(throwable -> {
                    if (throwable instanceof FlightRuntimeException) {
                        FlightRuntimeException flightException = (FlightRuntimeException) throwable;
                        CallStatus status = flightException.status();
                        return shouldRetry(status);
                    }
                    return false;
                })
                .withWaitStrategy(WaitStrategies.fibonacciWait())
                .withStopStrategy(StopStrategies.stopAfterAttempt(this.maxRetries + 1))
                .build();

        return retryer.call(() -> this.queryInternal(sql));
    }

    private boolean shouldRetry(CallStatus status) {
        switch (status.code()) {
            case UNAVAILABLE:
            case UNKNOWN:
            case TIMED_OUT:
            case INTERNAL:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void close() throws Exception {
        this.flightClient.close();
    }
}
