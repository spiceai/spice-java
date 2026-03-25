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
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.arrow.adbc.core.AdbcConnection;
import org.apache.arrow.adbc.core.AdbcDatabase;
import org.apache.arrow.adbc.core.AdbcDriver;
import org.apache.arrow.adbc.core.AdbcException;
import org.apache.arrow.adbc.core.AdbcStatement;
import org.apache.arrow.adbc.core.AdbcStatusCode;
import org.apache.arrow.adbc.driver.flightsql.FlightSqlDriver;
import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightClientMiddleware;
import org.apache.arrow.flight.FlightGrpcUtils;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.Location;
import org.apache.arrow.flight.Ticket;
import org.apache.arrow.flight.auth2.BasicAuthCredentialWriter;
import org.apache.arrow.flight.auth2.ClientBearerHeaderHandler;
import org.apache.arrow.flight.auth2.ClientIncomingAuthHeaderMiddleware;
import org.apache.arrow.flight.grpc.CredentialCallOption;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightRuntimeException;

import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.DateMilliVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.DurationVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.LargeVarBinaryVector;
import org.apache.arrow.vector.LargeVarCharVector;
import org.apache.arrow.vector.SmallIntVector;
import org.apache.arrow.vector.TimeMicroVector;
import org.apache.arrow.vector.TimeMilliVector;
import org.apache.arrow.vector.TimeNanoVector;
import org.apache.arrow.vector.TimeSecVector;
import org.apache.arrow.vector.TimeStampMicroTZVector;
import org.apache.arrow.vector.TimeStampMicroVector;
import org.apache.arrow.vector.TimeStampMilliTZVector;
import org.apache.arrow.vector.TimeStampMilliVector;
import org.apache.arrow.vector.TimeStampNanoTZVector;
import org.apache.arrow.vector.TimeStampNanoVector;
import org.apache.arrow.vector.TimeStampSecTZVector;
import org.apache.arrow.vector.TimeStampSecVector;
import org.apache.arrow.vector.TinyIntVector;
import org.apache.arrow.vector.UInt1Vector;
import org.apache.arrow.vector.UInt2Vector;
import org.apache.arrow.vector.UInt4Vector;
import org.apache.arrow.vector.UInt8Vector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowReader;
import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Strings;
import com.google.gson.Gson;

import org.apache.arrow.flight.sql.FlightSqlClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client to execute SQL queries against Spice.ai Cloud and Spice.ai OSS.
 * Supports both regular queries and parameterized queries using ADBC.
 */
public class SpiceClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SpiceClient.class);
    
    private static final long BYTES_PER_MB = 1024L * 1024L;
    
    // Cached Gson instance for JSON serialization (thread-safe)
    private static final Gson GSON = new Gson();
    
    // Cached HttpClient for refresh operations (thread-safe, connection pooling)
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    
    // Pre-computed parameter field names to avoid string concatenation in hot path
    private static final String[] PARAM_NAMES = new String[64];
    static {
        for (int i = 0; i < PARAM_NAMES.length; i++) {
            PARAM_NAMES[i] = "$" + (i + 1);
        }
    }

    private String appId;
    private String apiKey;
    private String userAgent;
    private URI flightAddress;
    private URI httpAddress;
    private int maxRetries;
    private FlightSqlClient flightClient;
    private CredentialCallOption authCallOptions = null;
    private BufferAllocator allocator;
    
    // Cached retryers (immutable, thread-safe)
    private Retryer<ArrowReader> adbcRetryer;
    private Retryer<FlightStream> flightRetryer;

    // ADBC resources for parameterized queries
    private AdbcDatabase adbcDatabase;
    private AdbcConnection adbcConnection;

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
     * @param flightAddress the URI of the flight address for connecting to
     *                      Spice.ai
     *                      services
     * @param httpAddress   the URI of the Spice.ai runtime HTTP address
     * 
     * @param maxRetries    the maximum number of connection retries for the
     *                      client
     * @param userAgent     the user agent string
     * @param memoryLimitMB the memory limit in megabytes for the Arrow
     *                      RootAllocator
     */
    public SpiceClient(String appId, String apiKey, URI flightAddress, URI httpAddress, int maxRetries,
            String userAgent, long memoryLimitMB) {
        this.appId = appId;
        this.apiKey = apiKey;
        this.maxRetries = maxRetries;
        this.httpAddress = httpAddress;
        this.userAgent = userAgent;

        // Arrow Flight requires URI to be grpc protocol, convert http/https for
        // convinience
        if (flightAddress.getScheme().equals("https")) {
            this.flightAddress = URI.create("grpc+tls://" + flightAddress.getHost() + ":" + flightAddress.getPort());
        } else if (flightAddress.getScheme().equals("http")) {
            this.flightAddress = URI.create("grpc+tcp://" + flightAddress.getHost() + ":" + flightAddress.getPort());
        } else {
            this.flightAddress = flightAddress;
        }

        // Convert megabytes to bytes for RootAllocator:
        // https://arrow.apache.org/java/main/reference/org.apache.arrow.memory.core/org/apache/arrow/memory/RootAllocator.html
        long memoryLimitBytes = (memoryLimitMB > Long.MAX_VALUE / BYTES_PER_MB)
                ? Long.MAX_VALUE
                : memoryLimitMB * BYTES_PER_MB;
        this.allocator = new RootAllocator(memoryLimitBytes);

        // Build the Flight client (channel + auth handshake)
        buildFlightClient();

        // Initialize cached retryers (immutable, built once)
        initRetryers();

        logger.debug("SpiceClient initialized - flightAddress={}, appId={}", this.flightAddress, this.appId);
    }
    
    /**
     * Builds or rebuilds the Flight client, including the gRPC channel and auth handshake.
     * This method is called during construction and after {@link #reset()}.
     *
     * <p>The gRPC channel is configured with:</p>
     * <ul>
     *   <li>{@code dns:///} target scheme for periodic DNS re-resolution behind load balancers</li>
     *   <li>HTTP/2 keep-alive (30s interval, 10s timeout) to detect dead connections quickly</li>
     * </ul>
     */
    private synchronized void buildFlightClient() {
        // Build a gRPC channel using forTarget() with the "dns:///" scheme so that
        // gRPC's DnsNameResolver periodically re-resolves the hostname. This is critical
        // for long-lived clients connecting to load-balanced endpoints (e.g. AWS ALBs)
        // where backend IPs can change. Arrow Flight's default FlightClient.Builder uses
        // NettyChannelBuilder.forAddress(SocketAddress), which resolves DNS exactly once
        // at construction time and never re-resolves, causing clients to get stuck on
        // stale IPs.
        boolean useTls = this.flightAddress.getScheme().equals("grpc+tls");
        String host = this.flightAddress.getHost();
        int port = this.flightAddress.getPort();
        if (port == -1) {
            port = useTls ? 443 : 80;
        }
        // Wrap IPv6 literals in brackets for a valid dns:/// target
        if (host != null && host.indexOf(':') >= 0 && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        String target = "dns:///" + host + ":" + port;

        NettyChannelBuilder channelBuilder = NettyChannelBuilder.forTarget(target);
        if (useTls) {
            try {
                channelBuilder.useTransportSecurity()
                        .sslContext(GrpcSslContexts.forClient().build());
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure TLS for Flight client", e);
            }
        } else {
            channelBuilder.usePlaintext();
        }
        channelBuilder
                // HTTP/2 keep-alive to detect dead/idle connections behind load balancers
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .maxInboundMessageSize(Integer.MAX_VALUE)
                .maxInboundMetadataSize(Integer.MAX_VALUE);
        ManagedChannel channel = channelBuilder.build();

        if (Strings.isNullOrEmpty(apiKey)) {
            FlightClient client = FlightGrpcUtils.createFlightClient(allocator, channel);
            this.flightClient = new FlightSqlClient(client);
            logger.debug("Flight client built (unauthenticated) - target={}", target);
            return;
        }

        // prepare additional headers to insert into Flight requests
        Map<String, String> headers = new HashMap<>();
        String uaString;
        if (Strings.isNullOrEmpty(userAgent)) {
            uaString = Config.getUserAgent();
        } else {
            // Prepend the user-supplied user agent string with the Spice.ai user agent
            uaString = userAgent + " " + Config.getUserAgent();
        }
        headers.put("User-Agent", uaString);

        final ClientIncomingAuthHeaderMiddleware.Factory authFactory = new ClientIncomingAuthHeaderMiddleware.Factory(
                new ClientBearerHeaderHandler());

        // Combine auth and custom header middleware into a single factory
        final HeaderAuthMiddlewareFactory combinedFactory = new HeaderAuthMiddlewareFactory(authFactory, headers);

        List<FlightClientMiddleware.Factory> middleware = new ArrayList<>();
        middleware.add(combinedFactory);

        final FlightClient client = FlightGrpcUtils.createFlightClient(allocator, channel, middleware);
        client.handshake(new CredentialCallOption(new BasicAuthCredentialWriter(this.appId, this.apiKey)));
        this.authCallOptions = authFactory.getCredentialCallOption();
        this.flightClient = new FlightSqlClient(client);

        logger.debug("Flight client built (authenticated) - target={}, appId={}", target, this.appId);
    }

    /**
     * Ensures the Flight client is connected, rebuilding it if necessary
     * (e.g. after a {@link #reset()} call).
     */
    private synchronized void ensureFlightClient() {
        if (this.flightClient == null) {
            buildFlightClient();
        }
    }

    /**
     * Resets the underlying gRPC transport by closing the current Flight client and ADBC connections,
     * then immediately establishes a fresh connection with a new DNS lookup and TLS handshake.
     * This ensures the next {@link #query(String)} or {@link #queryWithParams(String, Object...)}
     * call does not incur connection setup overhead.
     *
     * <p>Use this method to recover from unrecoverable transport failures such as:</p>
     * <ul>
     *   <li>SSLHandshakeException with mismatched certificates (e.g. load-balancer routing to wrong backend)</li>
     *   <li>Persistent UNAVAILABLE errors after exhausting retries</li>
     *   <li>Stale connections pinned to decommissioned backend IPs</li>
     * </ul>
     *
     * <p>Example usage for long-lived clients:</p>
     * <pre>{@code
     * try {
     *     return client.query(sql);
     * } catch (ExecutionException e) {
     *     if (isTransportFailure(e.getCause())) {
     *         client.reset();
     *         return client.query(sql); // retry with fresh connection
     *     }
     *     throw e;
     * }
     * }</pre>
     */
    public synchronized void reset() {
        logger.info("Resetting SpiceClient transport");

        // Close ADBC resources (they maintain a separate Flight connection)
        closeADBC();

        // Close Flight client (this also shuts down the underlying gRPC channel)
        if (this.flightClient != null) {
            try {
                this.flightClient.close();
            } catch (Exception e) {
                logger.warn("Error closing Flight client during reset: {}", e.getMessage());
            }
            this.flightClient = null;
        }
        this.authCallOptions = null;

        // Eagerly re-establish the connection so the next query has no setup overhead
        buildFlightClient();

        logger.info("SpiceClient transport reset and reconnected.");
    }

    /**
     * Initializes the cached retryer instances.
     * Called from constructor and must be called after maxRetries is set.
     */
    private void initRetryers() {
        this.adbcRetryer = RetryerBuilder.<ArrowReader>newBuilder()
                .retryIfException(throwable -> {
                    if (throwable instanceof AdbcException) {
                        String message = throwable.getMessage();
                        return message != null && (message.contains("UNAVAILABLE") ||
                                message.contains("UNKNOWN") ||
                                message.contains("DEADLINE_EXCEEDED") ||
                                message.contains("INTERNAL"));
                    }
                    return false;
                })
                .withWaitStrategy(WaitStrategies.fibonacciWait())
                .withStopStrategy(StopStrategies.stopAfterAttempt(this.maxRetries + 1))
                .build();
                
        this.flightRetryer = RetryerBuilder.<FlightStream>newBuilder()
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

        logger.debug("Executing query: {}", sql);
        try {
            FlightStream result = this.queryInternalWithRetry(sql);
            logger.debug("Query executed successfully");
            return result;
        } catch (RetryException e) {
            Throwable err = e.getLastFailedAttempt().getExceptionCause();
            logger.error("Query failed after {} attempts: {}", e.getNumberOfFailedAttempts(), err.getMessage());
            throw new ExecutionException("Failed to execute query due to error: " + err.toString(), err);
        }
    }

    /**
     * Executes a parameterized SQL query using ADBC.
     * This is the recommended method for queries with user input to prevent SQL
     * injection.
     * Parameters should use positional placeholders ($1, $2, etc.) in the SQL
     * query.
     *
     * <p>
     * Parameters can be:
     * </p>
     * <ul>
     * <li>Simple Java values (int, long, String, boolean, etc.) - type will be
     * inferred</li>
     * <li>Param instances with explicit type annotation using Param factory
     * methods</li>
     * </ul>
     *
     * <p>
     * Example usage:
     * </p>
     * 
     * <pre>
     * // With automatic type inference
     * ArrowReader reader = client.queryWithParams(
     *     "SELECT * FROM table WHERE id = $1 AND name = $2",
     *     123, "test");
     * 
     * // With explicit types
     * ArrowReader reader = client.queryWithParams(
     *     "SELECT * FROM table WHERE id = $1 AND amount = $2",
     *     Param.int32(123), Param.float64(99.99));
     * </pre>
     *
     * @param sql    the SQL query with positional parameter placeholders ($1, $2,
     *               etc.)
     * @param params the parameter values (can be plain values or Param instances)
     * @return an ArrowReader with the query results. The caller is responsible for
     *         closing the reader.
     * @throws ExecutionException if there is an error executing the query
     */
    public ArrowReader queryWithParams(String sql, Object... params) throws ExecutionException {
        if (Strings.isNullOrEmpty(sql)) {
            throw new IllegalArgumentException("No SQL query provided");
        }

        logger.debug("Executing parameterized query with {} parameters: {}", params != null ? params.length : 0, sql);
        try {
            initADBCIfNeeded();
            ArrowReader result = queryWithParamsInternal(sql, params);
            logger.debug("Parameterized query executed successfully");
            return result;
        } catch (AdbcException e) {
            logger.error("Parameterized query failed: {}", e.getMessage());
            throw new ExecutionException("Failed to execute parameterized query: " + e.getMessage(), e);
        } catch (RetryException e) {
            Throwable err = e.getLastFailedAttempt().getExceptionCause();
            logger.error("Parameterized query failed after {} attempts: {}", e.getNumberOfFailedAttempts(), err.getMessage());
            throw new ExecutionException("Failed to execute parameterized query due to error: " + err.toString(), err);
        }
    }

    /**
     * Initializes the ADBC connection if not already initialized.
     * This is called lazily on the first parameterized query.
     */
    private synchronized void initADBCIfNeeded() throws AdbcException {
        if (adbcDatabase != null && adbcConnection != null) {
            return;
        }

        logger.debug("Initializing ADBC connection");
        
        // Format the URI for ADBC FlightSQL driver
        String uri = this.flightAddress.toString();

        // Convert grpc+tls:// to grpc+tls:// format expected by ADBC
        // and grpc+tcp:// to grpc:// format
        if (uri.startsWith("grpc+tcp://")) {
            uri = "grpc://" + uri.substring("grpc+tcp://".length());
        }

        // Build driver options
        Map<String, Object> options = new HashMap<>();
        AdbcDriver.PARAM_URI.set(options, uri);

        // Add authentication if available
        if (!Strings.isNullOrEmpty(apiKey)) {
            AdbcDriver.PARAM_USERNAME.set(options, appId);
            AdbcDriver.PARAM_PASSWORD.set(options, apiKey);
        }

        // Add user agent header
        String uaString;
        if (Strings.isNullOrEmpty(userAgent)) {
            uaString = Config.getUserAgent();
        } else {
            uaString = userAgent + " " + Config.getUserAgent();
        }
        options.put("adbc.flight.sql.rpc.call_header.user-agent", uaString);

        // Create the driver and database
        FlightSqlDriver driver = new FlightSqlDriver(allocator);
        adbcDatabase = driver.open(options);
        adbcConnection = adbcDatabase.connect();
        
        logger.debug("ADBC connection established - uri={}", uri);
    }

    /**
     * Closes the ADBC resources.
     */
    private void closeADBC() {
        if (adbcConnection != null) {
            try {
                adbcConnection.close();
                logger.debug("ADBC connection closed");
            } catch (Exception e) {
                logger.warn("Error closing ADBC connection: {}", e.getMessage());
            }
            adbcConnection = null;
        }
        if (adbcDatabase != null) {
            try {
                adbcDatabase.close();
                logger.debug("ADBC database closed");
            } catch (Exception e) {
                logger.warn("Error closing ADBC database: {}", e.getMessage());
            }
            adbcDatabase = null;
        }
    }

    /**
     * Internal implementation of parameterized query execution.
     */
    private ArrowReader queryWithParamsInternal(String sql, Object... params)
            throws AdbcException, RetryException, ExecutionException {
        return adbcRetryer.call(() -> executeParameterizedQuery(sql, params));
    }

    /**
     * Executes a single parameterized query using ADBC prepare/bind/execute
     * pattern.
     */
    private ArrowReader executeParameterizedQuery(String sql, Object... params) throws AdbcException {
        AdbcStatement stmt = adbcConnection.createStatement();
        VectorSchemaRoot paramRoot = null;

        try {
            // Set the query
            stmt.setSqlQuery(sql);

            // Prepare the statement
            stmt.prepare();

            // Bind parameters if provided
            if (params != null && params.length > 0) {
                paramRoot = createParameterRoot(params);
                stmt.bind(paramRoot);
            }

            // Execute the query - at this point parameters have been serialized
            AdbcStatement.QueryResult result = stmt.executeQuery();
            ArrowReader reader = result.getReader();
            
            // Now we can safely close the parameter root since it has been sent to server
            if (paramRoot != null) {
                paramRoot.close();
            }
            
            return reader;
        } catch (AdbcException e) {
            // Clean up on error
            if (paramRoot != null) {
                try {
                    paramRoot.close();
                } catch (Exception closeEx) {
                    // Ignore close exception
                }
            }
            try {
                stmt.close();
            } catch (Exception closeEx) {
                // Ignore close exception
            }
            throw e;
        }
        // Note: We don't close the statement here because the reader needs it
        // The statement will be closed when the reader is closed
    }

    /**
     * Creates a VectorSchemaRoot containing the parameter values.
     * The caller is responsible for closing the returned root.
     */
    private VectorSchemaRoot createParameterRoot(Object... params) throws AdbcException {
        // Extract values and determine types
        final int numParams = params.length;
        Object[] values = new Object[numParams];
        ArrowType[] types = new ArrowType[numParams];

        for (int i = 0; i < numParams; i++) {
            Object param = params[i];

            if (param instanceof Param) {
                Param p = (Param) param;
                values[i] = p.getValue();
                if (p.hasExplicitType()) {
                    types[i] = p.getType();
                } else {
                    types[i] = inferArrowType(p.getValue());
                }
            } else {
                values[i] = param;
                types[i] = inferArrowType(param);
            }
        }

        // Build the schema for parameters with pre-sized list
        List<Field> fields = new ArrayList<>(numParams);
        for (int i = 0; i < numParams; i++) {
            // Use cached field names for common cases (up to 64 params)
            String fieldName = (i < PARAM_NAMES.length) ? PARAM_NAMES[i] : "$" + (i + 1);
            fields.add(new Field(fieldName, FieldType.nullable(types[i]), null));
        }
        Schema schema = new Schema(fields);

        // Create a VectorSchemaRoot and populate it
        VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator);
        root.allocateNew();

        // Append values to the vectors and set value count for each
        for (int i = 0; i < numParams; i++) {
            FieldVector vector = root.getVector(i);
            appendValueToVector(vector, 0, values[i], types[i]);
            vector.setValueCount(1);
        }

        root.setRowCount(1);
        
        logger.debug("Created parameter root: rowCount={}, schema={}", 
            root.getRowCount(), root.getSchema());

        return root;
    }

    // Cached ArrowTypes for type inference (immutable, thread-safe)
    private static final ArrowType INFER_INT8 = new ArrowType.Int(8, true);
    private static final ArrowType INFER_INT16 = new ArrowType.Int(16, true);
    private static final ArrowType INFER_INT32 = new ArrowType.Int(32, true);
    private static final ArrowType INFER_INT64 = new ArrowType.Int(64, true);
    private static final ArrowType INFER_FLOAT32 = new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE);
    private static final ArrowType INFER_FLOAT64 = new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE);
    private static final ArrowType INFER_DATE32 = new ArrowType.Date(DateUnit.DAY);
    private static final ArrowType INFER_TIME64_MICRO = new ArrowType.Time(TimeUnit.MICROSECOND, 64);
    private static final ArrowType INFER_TIMESTAMP_MICRO_UTC = new ArrowType.Timestamp(TimeUnit.MICROSECOND, "UTC");
    private static final ArrowType INFER_DURATION_MICRO = new ArrowType.Duration(TimeUnit.MICROSECOND);

    /**
     * Infers the Arrow type from a Java value.
     * Uses cached type instances for common types to minimize allocations.
     */
    private ArrowType inferArrowType(Object value) {
        if (value == null) {
            return ArrowType.Null.INSTANCE;
        }

        // Integer types - use cached instances
        if (value instanceof Byte) {
            return INFER_INT8;
        }
        if (value instanceof Short) {
            return INFER_INT16;
        }
        if (value instanceof Integer) {
            return INFER_INT32;
        }
        if (value instanceof Long) {
            return INFER_INT64;
        }

        // Floating point types - use cached instances
        if (value instanceof Float) {
            return INFER_FLOAT32;
        }
        if (value instanceof Double) {
            return INFER_FLOAT64;
        }

        // String and binary types - already use singleton INSTANCE
        if (value instanceof String) {
            return ArrowType.Utf8.INSTANCE;
        }
        if (value instanceof byte[]) {
            return ArrowType.Binary.INSTANCE;
        }

        // Boolean - already uses singleton INSTANCE
        if (value instanceof Boolean) {
            return ArrowType.Bool.INSTANCE;
        }

        // Temporal types - use cached instances
        if (value instanceof LocalDate) {
            return INFER_DATE32;
        }
        if (value instanceof LocalTime) {
            return INFER_TIME64_MICRO;
        }
        if (value instanceof LocalDateTime) {
            return INFER_TIMESTAMP_MICRO_UTC;
        }
        if (value instanceof Duration) {
            return INFER_DURATION_MICRO;
        }

        // Decimal - must create new instance due to precision/scale
        if (value instanceof BigDecimal) {
            BigDecimal bd = (BigDecimal) value;
            int precision = Math.max(bd.precision(), 1);
            int scale = Math.max(bd.scale(), 0);
            // Ensure precision is at least scale + 1
            precision = Math.max(precision, scale + 1);
            // Cap at Decimal128 max precision
            if (precision <= 38) {
                return new ArrowType.Decimal(precision, scale, 128);
            } else {
                return new ArrowType.Decimal(precision, scale, 256);
            }
        }

        throw new IllegalArgumentException(
                "Unsupported parameter type: " + value.getClass().getName() +
                        ". Use Param.of(value, type) for explicit type control.");
    }

    /**
     * Appends a value to an Arrow vector at the specified index.
     * Uses setSafe methods to properly handle validity buffer and auto-expansion.
     */
    @SuppressWarnings("deprecation")
    private void appendValueToVector(FieldVector vector, int index, Object value, ArrowType type)
            throws AdbcException {
        if (value == null) {
            vector.setNull(index);
            return;
        }

        try {
            // Integer vectors - use setSafe for proper validity buffer handling
            if (vector instanceof TinyIntVector) {
                ((TinyIntVector) vector).setSafe(index, ((Number) value).byteValue());
            } else if (vector instanceof SmallIntVector) {
                ((SmallIntVector) vector).setSafe(index, ((Number) value).shortValue());
            } else if (vector instanceof IntVector) {
                ((IntVector) vector).setSafe(index, ((Number) value).intValue());
            } else if (vector instanceof BigIntVector) {
                ((BigIntVector) vector).setSafe(index, ((Number) value).longValue());
            }
            // Unsigned integer vectors
            else if (vector instanceof UInt1Vector) {
                ((UInt1Vector) vector).setSafe(index, ((Number) value).byteValue());
            } else if (vector instanceof UInt2Vector) {
                // Convert char to int for UInt2Vector
                if (value instanceof Character) {
                    ((UInt2Vector) vector).setSafe(index, (int) ((Character) value).charValue());
                } else {
                    ((UInt2Vector) vector).setSafe(index, ((Number) value).intValue());
                }
            } else if (vector instanceof UInt4Vector) {
                ((UInt4Vector) vector).setSafe(index, ((Number) value).intValue());
            } else if (vector instanceof UInt8Vector) {
                ((UInt8Vector) vector).setSafe(index, ((Number) value).longValue());
            }
            // Floating point vectors
            else if (vector instanceof Float4Vector) {
                ((Float4Vector) vector).setSafe(index, ((Number) value).floatValue());
            } else if (vector instanceof Float8Vector) {
                ((Float8Vector) vector).setSafe(index, ((Number) value).doubleValue());
            }
            // String vectors
            else if (vector instanceof VarCharVector) {
                byte[] bytes = value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ((VarCharVector) vector).setSafe(index, bytes);
            } else if (vector instanceof LargeVarCharVector) {
                byte[] bytes = value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ((LargeVarCharVector) vector).setSafe(index, bytes);
            }
            // Binary vectors
            else if (vector instanceof VarBinaryVector) {
                ((VarBinaryVector) vector).setSafe(index, (byte[]) value);
            } else if (vector instanceof LargeVarBinaryVector) {
                ((LargeVarBinaryVector) vector).setSafe(index, (byte[]) value);
            }
            // Boolean vector
            else if (vector instanceof BitVector) {
                ((BitVector) vector).setSafe(index, ((Boolean) value) ? 1 : 0);
            }
            // Date vectors
            else if (vector instanceof DateDayVector) {
                LocalDate date = (LocalDate) value;
                int daysSinceEpoch = (int) date.toEpochDay();
                ((DateDayVector) vector).setSafe(index, daysSinceEpoch);
            } else if (vector instanceof DateMilliVector) {
                LocalDate date = (LocalDate) value;
                long millisSinceEpoch = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
                ((DateMilliVector) vector).setSafe(index, millisSinceEpoch);
            }
            // Time vectors
            else if (vector instanceof TimeSecVector) {
                LocalTime time = (LocalTime) value;
                ((TimeSecVector) vector).setSafe(index, time.toSecondOfDay());
            } else if (vector instanceof TimeMilliVector) {
                LocalTime time = (LocalTime) value;
                ((TimeMilliVector) vector).setSafe(index, (int) (time.toNanoOfDay() / 1_000_000));
            } else if (vector instanceof TimeMicroVector) {
                LocalTime time = (LocalTime) value;
                ((TimeMicroVector) vector).setSafe(index, time.toNanoOfDay() / 1_000);
            } else if (vector instanceof TimeNanoVector) {
                LocalTime time = (LocalTime) value;
                ((TimeNanoVector) vector).setSafe(index, time.toNanoOfDay());
            }
            // Timestamp vectors
            else if (vector instanceof TimeStampSecVector) {
                LocalDateTime dt = (LocalDateTime) value;
                ((TimeStampSecVector) vector).setSafe(index, dt.toEpochSecond(ZoneOffset.UTC));
            } else if (vector instanceof TimeStampSecTZVector) {
                LocalDateTime dt = (LocalDateTime) value;
                ((TimeStampSecTZVector) vector).setSafe(index, dt.toEpochSecond(ZoneOffset.UTC));
            } else if (vector instanceof TimeStampMilliVector) {
                LocalDateTime dt = (LocalDateTime) value;
                ((TimeStampMilliVector) vector).setSafe(index, dt.toInstant(ZoneOffset.UTC).toEpochMilli());
            } else if (vector instanceof TimeStampMilliTZVector) {
                LocalDateTime dt = (LocalDateTime) value;
                ((TimeStampMilliTZVector) vector).setSafe(index, dt.toInstant(ZoneOffset.UTC).toEpochMilli());
            } else if (vector instanceof TimeStampMicroVector) {
                LocalDateTime dt = (LocalDateTime) value;
                long epochMicro = dt.toEpochSecond(ZoneOffset.UTC) * 1_000_000 + dt.getNano() / 1_000;
                ((TimeStampMicroVector) vector).setSafe(index, epochMicro);
            } else if (vector instanceof TimeStampMicroTZVector) {
                LocalDateTime dt = (LocalDateTime) value;
                long epochMicro = dt.toEpochSecond(ZoneOffset.UTC) * 1_000_000 + dt.getNano() / 1_000;
                ((TimeStampMicroTZVector) vector).setSafe(index, epochMicro);
            } else if (vector instanceof TimeStampNanoVector) {
                LocalDateTime dt = (LocalDateTime) value;
                long epochNano = dt.toEpochSecond(ZoneOffset.UTC) * 1_000_000_000 + dt.getNano();
                ((TimeStampNanoVector) vector).setSafe(index, epochNano);
            } else if (vector instanceof TimeStampNanoTZVector) {
                LocalDateTime dt = (LocalDateTime) value;
                long epochNano = dt.toEpochSecond(ZoneOffset.UTC) * 1_000_000_000 + dt.getNano();
                ((TimeStampNanoTZVector) vector).setSafe(index, epochNano);
            }
            // Duration vector
            else if (vector instanceof DurationVector) {
                Duration duration = (Duration) value;
                ArrowType.Duration durationType = (ArrowType.Duration) type;
                long durationValue;
                switch (durationType.getUnit()) {
                    case SECOND:
                        durationValue = duration.getSeconds();
                        break;
                    case MILLISECOND:
                        durationValue = duration.toMillis();
                        break;
                    case MICROSECOND:
                        durationValue = duration.toNanos() / 1_000;
                        break;
                    case NANOSECOND:
                        durationValue = duration.toNanos();
                        break;
                    default:
                        durationValue = duration.toNanos() / 1_000; // Default to microseconds
                }
                ((DurationVector) vector).setSafe(index, durationValue);
            }
            // Decimal vector
            else if (vector instanceof DecimalVector) {
                DecimalVector decVector = (DecimalVector) vector;
                BigDecimal bd = (BigDecimal) value;
                decVector.setSafe(index, bd);
            } else {
                throw new AdbcException("Unsupported vector type: " + vector.getClass().getName(),
                        null, AdbcStatusCode.UNKNOWN, null, 0);
            }
        } catch (ClassCastException e) {
            throw new AdbcException(
                    "Cannot convert value of type " + value.getClass().getName() +
                            " to " + vector.getClass().getSimpleName(),
                    e, AdbcStatusCode.INVALID_ARGUMENT, null, 0);
        }
    }

    /**
     * Refreshes an accelerated dataset using the configured dataset acceleration
     * settings
     * 
     * @param dataset the name of the dataset to refresh
     * @throws ExecutionException if there is an error refreshing the dataset
     */
    public void refreshDataset(String dataset) throws ExecutionException {
        if (Strings.isNullOrEmpty(dataset)) {
            throw new IllegalArgumentException("No dataset name provided");
        }

        refreshDataset(dataset, null);
    }

    /**
     * Refreshes an accelerated dataset using the configured dataset acceleration
     * settings
     * 
     * @param dataset        the name of the dataset to refresh
     * @param refreshOptions the refresh options to use when refreshing the dataset
     * @throws ExecutionException if there is an error refreshing the dataset
     */
    public void refreshDataset(String dataset, RefreshOptions refreshOptions) throws ExecutionException {
        if (Strings.isNullOrEmpty(dataset)) {
            throw new IllegalArgumentException("No dataset name provided");
        }

        logger.debug("Refreshing dataset: {}", dataset);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(new URI(String.format("%s/v1/datasets/%s/acceleration/refresh", this.httpAddress, dataset)))
                    .header("Content-Type", "application/json")
                    .header("X-Spice-User-Agent", Config.getUserAgent());

            if (refreshOptions != null) {
                String json = GSON.toJson(refreshOptions);
                builder = builder.POST(HttpRequest.BodyPublishers.ofString(json));
            } else {
                builder = builder.POST(HttpRequest.BodyPublishers.ofString("{}"));
            }

            HttpRequest request = builder.build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201) {
                logger.error("Dataset refresh failed - dataset={}, statusCode={}, response={}", dataset, response.statusCode(), response.body());
                throw new ExecutionException(
                        String.format("Failed to trigger dataset refresh. Status Code: %d, Response: %s",
                                response.statusCode(),
                                response.body()),
                        null);
            }
            logger.debug("Dataset refresh triggered successfully: {}", dataset);
        } catch (ExecutionException e) {
            // no need to wrap ExecutionException
            throw e;
        } catch (ConnectException err) {
            logger.error("Cannot connect to Spice runtime at {}: {}", this.httpAddress, err.getMessage());
            throw new ExecutionException(
                    String.format("The Spice runtime is unavailable at %s. Is it running?", this.httpAddress), err);
        } catch (Exception err) {
            logger.error("Dataset refresh failed: {}", err.getMessage());
            throw new ExecutionException("Failed to trigger dataset refresh due to error: " + err.toString(), err);
        }
    }

    private FlightStream queryInternal(String sql) {
        ensureFlightClient();
        FlightInfo flightInfo = this.flightClient.execute(sql, authCallOptions);
        Ticket ticket = flightInfo.getEndpoints().get(0).getTicket();
        return this.flightClient.getStream(ticket, authCallOptions);
    }

    private FlightStream queryInternalWithRetry(String sql) throws ExecutionException, RetryException {
        return flightRetryer.call(() -> this.queryInternal(sql));
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
        logger.debug("Closing SpiceClient");
        List<Exception> exceptions = new ArrayList<>();

        // Close ADBC resources first
        try {
            closeADBC();
        } catch (Exception e) {
            logger.warn("Error during ADBC cleanup: {}", e.getMessage());
            exceptions.add(e);
        }

        // Close Flight client
        if (this.flightClient != null) {
            try {
                this.flightClient.close();
                logger.debug("Flight client closed");
            } catch (Exception e) {
                logger.warn("Error closing Flight client: {}", e.getMessage());
                exceptions.add(e);
            }
        }

        // Close allocator
        try {
            if (this.allocator != null) {
                this.allocator.close();
                logger.debug("Arrow allocator closed");
            }
        } catch (Exception e) {
            logger.warn("Error closing Arrow allocator: {}", e.getMessage());
            exceptions.add(e);
        }

        if (!exceptions.isEmpty()) {
            Exception first = exceptions.get(0);
            for (int i = 1; i < exceptions.size(); i++) {
                first.addSuppressed(exceptions.get(i));
            }
            logger.error("SpiceClient closed with {} error(s)", exceptions.size());
            throw first;
        }
        
        logger.debug("SpiceClient closed successfully");
    }
}
