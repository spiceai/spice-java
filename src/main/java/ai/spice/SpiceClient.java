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

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.CallOption;
import org.apache.arrow.flight.CallOptions;
import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightClientMiddleware;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightGrpcUtils;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightRuntimeException;
import org.apache.arrow.flight.FlightStatusCode;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.Result;
import org.apache.arrow.flight.Ticket;
import org.apache.arrow.flight.auth2.BasicAuthCredentialWriter;
import org.apache.arrow.flight.auth2.ClientBearerHeaderHandler;
import org.apache.arrow.flight.auth2.ClientIncomingAuthHeaderMiddleware;
import org.apache.arrow.flight.grpc.CredentialCallOption;

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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.apache.arrow.flight.sql.FlightSqlClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client to execute SQL queries against Spice.ai Cloud and Spice.ai OSS.
 * Supports both regular queries and parameterized queries using Arrow Flight
 * SQL prepared statements.
 */
public class SpiceClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(SpiceClient.class);

    private static final long BYTES_PER_MB = 1024L * 1024L;

    // Cached Gson instance for JSON serialization (thread-safe)
    private static final Gson GSON = new Gson();

    // Cap for large dataset results and metadata (~2 GiB, max safe signed-int value)
    private static final int MAX_INBOUND_MESSAGE_SIZE = Integer.MAX_VALUE;
    private static final int MAX_INBOUND_METADATA_SIZE = Integer.MAX_VALUE;

    /** Default number of gRPC channels (HTTP/2 connections) per client. */
    static final int DEFAULT_CHANNEL_COUNT = 1;
    /** Default maximum number of idle prepared statements kept for reuse. */
    static final int DEFAULT_STATEMENT_CACHE_SIZE = 64;

    // Flight DoAction action types for async queries, served by the Spice
    // runtime when running in distributed/scheduler mode.
    private static final String ACTION_SUBMIT_ASYNC_QUERY = "SubmitAsyncQuery";
    private static final String ACTION_GET_ASYNC_QUERY_STATUS = "GetAsyncQueryStatus";
    private static final String ACTION_GET_ASYNC_QUERY_RESULT = "GetAsyncQueryResult";
    private static final String ACTION_CANCEL_ASYNC_QUERY = "CancelAsyncQuery";

    // Retry backoff: exponential (multiplier * 2^attempt) capped at a maximum,
    // plus a random jitter so a fleet of clients does not retry in lockstep.
    // First waits are ~250ms, 500ms, 1s, ... capped at 10s.
    private static final long RETRY_BACKOFF_MULTIPLIER_MS = 125;
    private static final long RETRY_BACKOFF_MAX_MS = 10_000;
    private static final long RETRY_JITTER_MAX_MS = 250;

    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    // HTTP/2 keep-alive tuning for dead/unresponsive-peer detection.
    // Package-visible so resilience tests derive their detection windows
    // from the real values instead of restating them.
    static final long KEEPALIVE_TIME_SECONDS = 30;
    static final long KEEPALIVE_TIMEOUT_SECONDS = 10;

    // Pre-computed parameter field names to avoid string concatenation in hot path
    private static final String[] PARAM_NAMES = new String[64];
    static {
        for (int i = 0; i < PARAM_NAMES.length; i++) {
            PARAM_NAMES[i] = "$" + (i + 1);
        }
    }

    private final String appId;
    private final String apiKey;
    private final String userAgent;
    private final URI flightAddress;
    private final URI httpAddress;
    private final int maxRetries;
    private final int channelCount;
    private final Duration queryTimeout;
    private final String tlsClientCertFile;
    private final String tlsClientKeyFile;
    private final String tlsRootCertFile;
    private final BufferAllocator allocator;
    private final PreparedStatementCache statementCache;
    private final AtomicInteger channelSelector = new AtomicInteger();

    /**
     * The active connection generation. Volatile so the query hot path can
     * snapshot it without locking; rebuilt under the client monitor by
     * {@link #reset()}, lazy rebuild, and UNAUTHENTICATED recovery.
     */
    private volatile FlightChannel[] channels;

    /**
     * Channels replaced by {@link #reset()} or an auth rebuild whose transport
     * has been gracefully shut down but whose Flight client (and buffer
     * allocator) cannot be closed yet because RPCs or result streams may still
     * be in flight on them. Swept once the transport reports terminated.
     * Guarded by the client monitor.
     */
    private final List<FlightChannel> retiredChannels = new ArrayList<>();
    private volatile boolean closed = false;

    // HttpClient for refresh operations, created lazily on first use so clients
    // that never call refreshDataset() don't pay for its selector thread.
    private volatile HttpClient httpClient;

    // Cached retryers (immutable, thread-safe)
    private Retryer<FlightStream> flightRetryer;
    private Retryer<ArrowReader> readerRetryer;

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
        this(appId, apiKey, flightAddress, httpAddress, maxRetries, userAgent, memoryLimitMB, null, null);
    }

    public SpiceClient(String appId, String apiKey, URI flightAddress, URI httpAddress, int maxRetries,
            String userAgent, long memoryLimitMB, String tlsClientCertFile, String tlsClientKeyFile) {
        this(appId, apiKey, flightAddress, httpAddress, maxRetries, userAgent, memoryLimitMB, tlsClientCertFile,
                tlsClientKeyFile, null);
    }

    public SpiceClient(String appId, String apiKey, URI flightAddress, URI httpAddress, int maxRetries,
            String userAgent, long memoryLimitMB, String tlsClientCertFile, String tlsClientKeyFile,
            String tlsRootCertFile) {
        this(appId, apiKey, flightAddress, httpAddress, maxRetries, userAgent, memoryLimitMB, tlsClientCertFile,
                tlsClientKeyFile, tlsRootCertFile, DEFAULT_CHANNEL_COUNT, null, DEFAULT_STATEMENT_CACHE_SIZE);
    }

    SpiceClient(String appId, String apiKey, URI flightAddress, URI httpAddress, int maxRetries,
            String userAgent, long memoryLimitMB, String tlsClientCertFile, String tlsClientKeyFile,
            String tlsRootCertFile, int channelCount, Duration queryTimeout, int statementCacheSize) {
        this.appId = appId;
        this.apiKey = apiKey;
        this.maxRetries = maxRetries;
        this.httpAddress = httpAddress;
        this.userAgent = userAgent;
        this.tlsClientCertFile = tlsClientCertFile;
        this.tlsClientKeyFile = tlsClientKeyFile;
        this.tlsRootCertFile = tlsRootCertFile;
        this.channelCount = channelCount;
        this.queryTimeout = queryTimeout;
        this.statementCache = new PreparedStatementCache(statementCacheSize);

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

        try {
            // Build the Flight channels (gRPC channel + auth handshake each)
            buildFlightChannels();

            // Initialize cached retryers (immutable, built once)
            initRetryers();
        } catch (RuntimeException | Error e) {
            try {
                this.allocator.close();
            } catch (Exception closeEx) {
                e.addSuppressed(closeEx);
            }
            throw e;
        }

        logger.debug("SpiceClient initialized - flightAddress={}, appId={}, channels={}",
                this.flightAddress, this.appId, this.channelCount);
    }

    /**
     * A single gRPC connection to the Flight endpoint together with its
     * per-connection auth token and pre-computed call options.
     */
    private static final class FlightChannel {
        /** The underlying transport, retained for graceful retirement. */
        final ManagedChannel grpcChannel;
        final FlightSqlClient client;
        /**
         * The same connection's raw Flight client, used for RPCs FlightSqlClient
         * does not expose — currently only {@code DoAction} for async queries.
         * Closing {@link #client} closes this too; do not close it separately.
         */
        final FlightClient rawClient;
        /** Options for control-plane RPCs (GetFlightInfo, prepare, DoPut): auth + optional timeout. */
        final CallOption[] callOptions;
        /** Options for DoGet streams: auth only — a deadline would kill long-running result streams. */
        final CallOption[] streamOptions;

        FlightChannel(ManagedChannel grpcChannel, FlightSqlClient client, FlightClient rawClient,
                CallOption[] callOptions, CallOption[] streamOptions) {
            this.grpcChannel = grpcChannel;
            this.client = client;
            this.rawClient = rawClient;
            this.callOptions = callOptions;
            this.streamOptions = streamOptions;
        }
    }

    /**
     * A server-side prepared statement bound to the channel that created it and
     * tagged with the connection generation it belongs to.
     */
    private static final class CachedStatement {
        final FlightSqlClient.PreparedStatement statement;
        final FlightChannel channel;
        final Object generation;

        CachedStatement(FlightSqlClient.PreparedStatement statement, FlightChannel channel, Object generation) {
            this.statement = statement;
            this.channel = channel;
            this.generation = generation;
        }
    }

    /**
     * Bounded cache of idle prepared statements keyed by SQL text. Reusing a
     * prepared statement saves the CreatePreparedStatement and
     * ClosePreparedStatement round trips on every repeated query.
     *
     * <p>All methods are cheap map operations under the cache monitor — no RPC
     * is ever performed while holding the lock. Statements from a previous
     * connection generation are rejected on {@link #give} and drained by
     * {@link #swapGeneration} when the transport is rebuilt.</p>
     */
    private static final class PreparedStatementCache {
        private final int maxIdleStatements;
        private final HashMap<String, ArrayDeque<CachedStatement>> idleBySql = new HashMap<>();
        private int idleCount;
        private Object generation;

        PreparedStatementCache(int maxIdleStatements) {
            this.maxIdleStatements = maxIdleStatements;
        }

        synchronized CachedStatement borrow(String sql) {
            ArrayDeque<CachedStatement> deque = idleBySql.get(sql);
            if (deque == null) {
                return null;
            }
            CachedStatement statement = deque.poll();
            if (statement != null) {
                idleCount--;
                if (deque.isEmpty()) {
                    idleBySql.remove(sql);
                }
            }
            return statement;
        }

        /**
         * Offers a statement back for reuse. Returns false when the statement
         * must be closed by the caller instead (cache full, disabled, or the
         * statement belongs to a previous connection generation).
         */
        synchronized boolean give(String sql, CachedStatement statement) {
            if (statement.generation != generation || idleCount >= maxIdleStatements) {
                return false;
            }
            idleBySql.computeIfAbsent(sql, k -> new ArrayDeque<>(2)).push(statement);
            idleCount++;
            return true;
        }

        /**
         * Starts a new connection generation, returning all drained statements
         * for the caller to close outside the lock.
         */
        synchronized List<CachedStatement> swapGeneration(Object newGeneration) {
            this.generation = newGeneration;
            if (idleCount == 0) {
                return Collections.emptyList();
            }
            List<CachedStatement> drained = new ArrayList<>(idleCount);
            for (ArrayDeque<CachedStatement> deque : idleBySql.values()) {
                drained.addAll(deque);
            }
            idleBySql.clear();
            idleCount = 0;
            return drained;
        }
    }

    /**
     * A view over a parameter root whose close() is a no-op.
     * {@link FlightSqlClient.PreparedStatement#clearParameters()} closes the
     * root it was given; passing this wrapper keeps ownership of the real root
     * (and its buffers) with the SDK so it can be closed exactly once.
     */
    private static final class NonOwningRoot extends VectorSchemaRoot {
        NonOwningRoot(VectorSchemaRoot delegate) {
            super(delegate.getSchema(), delegate.getFieldVectors(), delegate.getRowCount());
        }

        @Override
        public void close() {
            // The delegate owns the vectors; SpiceClient closes it.
        }
    }

    /**
     * Builds (or rebuilds) all Flight channels, including the gRPC channels and
     * auth handshakes. Called during construction, after {@link #reset()}, and
     * on UNAUTHENTICATED recovery.
     *
     * <p>Each gRPC channel is configured with:</p>
     * <ul>
     *   <li>{@code dns:///} target scheme for periodic DNS re-resolution behind load balancers</li>
     *   <li>HTTP/2 keep-alive (30s interval, 10s timeout) to detect dead connections quickly</li>
     * </ul>
     */
    private synchronized void buildFlightChannels() {
        FlightChannel[] newChannels = new FlightChannel[channelCount];
        try {
            for (int i = 0; i < channelCount; i++) {
                newChannels[i] = buildFlightChannel();
            }
        } catch (RuntimeException | Error e) {
            for (FlightChannel channel : newChannels) {
                if (channel != null) {
                    closeChannelQuietly(channel, e);
                }
            }
            throw e;
        }
        this.channels = newChannels;
        // Any cached prepared statements belong to the previous connections.
        closeStatementsQuietly(statementCache.swapGeneration(newChannels));
    }

    private FlightChannel buildFlightChannel() {
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
                var sslContextBuilder = GrpcSslContexts.forClient();
                if (this.tlsClientCertFile != null && this.tlsClientKeyFile != null) {
                    sslContextBuilder.keyManager(
                            new java.io.File(this.tlsClientCertFile),
                            new java.io.File(this.tlsClientKeyFile));
                }
                if (this.tlsRootCertFile != null) {
                    sslContextBuilder.trustManager(new java.io.File(this.tlsRootCertFile));
                }
                channelBuilder.useTransportSecurity()
                        .sslContext(sslContextBuilder.build());
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure TLS for Flight client", e);
            }
        } else {
            channelBuilder.usePlaintext();
        }
        channelBuilder
                // HTTP/2 keep-alive to detect dead/idle connections behind load balancers
                .keepAliveTime(KEEPALIVE_TIME_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                .keepAliveTimeout(KEEPALIVE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
                .maxInboundMetadataSize(MAX_INBOUND_METADATA_SIZE);
        ManagedChannel channel = channelBuilder.build();

        try {
            CredentialCallOption auth = null;
            FlightClient client;
            if (Strings.isNullOrEmpty(apiKey)) {
                client = FlightGrpcUtils.createFlightClient(allocator, channel);
                logger.debug("Flight channel built (unauthenticated) - target={}", target);
            } else {
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

                final ClientIncomingAuthHeaderMiddleware.Factory authFactory =
                        new ClientIncomingAuthHeaderMiddleware.Factory(new ClientBearerHeaderHandler());

                // Combine auth and custom header middleware into a single factory
                final HeaderAuthMiddlewareFactory combinedFactory = new HeaderAuthMiddlewareFactory(authFactory,
                        headers);

                List<FlightClientMiddleware.Factory> middleware = new ArrayList<>();
                middleware.add(combinedFactory);

                client = FlightGrpcUtils.createFlightClient(allocator, channel, middleware);
                client.handshake(new CredentialCallOption(new BasicAuthCredentialWriter(this.appId, this.apiKey)));
                auth = authFactory.getCredentialCallOption();
                logger.debug("Flight channel built (authenticated) - target={}, appId={}", target, this.appId);
            }

            List<CallOption> options = new ArrayList<>(2);
            if (auth != null) {
                options.add(auth);
            }
            CallOption[] streamOptions = options.toArray(new CallOption[0]);
            if (queryTimeout != null) {
                options.add(CallOptions.timeout(queryTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS));
            }
            CallOption[] callOptions = options.toArray(new CallOption[0]);

            return new FlightChannel(channel, new FlightSqlClient(client), client, callOptions, streamOptions);
        } catch (Exception e) {
            // Ensure the channel is shut down if client creation or handshake fails
            // to avoid leaking threads and file descriptors on repeated rebuild attempts.
            try {
                channel.shutdownNow();
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    /**
     * Returns the current channels, lazily rebuilding them if necessary
     * (e.g. after a failed rebuild). Lock-free on the hot path.
     */
    private FlightChannel[] currentChannels() {
        FlightChannel[] snapshot = this.channels;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("SpiceClient is closed");
            }
            if (this.channels == null) {
                buildFlightChannels();
            }
            return this.channels;
        }
    }

    private FlightChannel selectChannel(FlightChannel[] snapshot) {
        if (snapshot.length == 1) {
            return snapshot[0];
        }
        return snapshot[Math.floorMod(channelSelector.getAndIncrement(), snapshot.length)];
    }

    /**
     * Resets the underlying gRPC transport by closing the current Flight channels and
     * cached prepared statements, then immediately establishes fresh connections with
     * a new DNS lookup and TLS handshake.
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
        if (closed) {
            throw new IllegalStateException("Cannot reset a closed SpiceClient");
        }
        logger.info("Resetting SpiceClient transport");

        // Close cached prepared statements while their channels still accept
        // RPCs, then retire the channels (graceful shutdown; deferred close so
        // concurrent in-flight queries can finish on them).
        closeStatementsQuietly(statementCache.swapGeneration(null));
        retireChannelsLocked();
        sweepRetiredChannels(false);

        // Eagerly re-establish the connections so the next query has no setup overhead
        buildFlightChannels();

        logger.info("SpiceClient transport reset and reconnected.");
    }

    /**
     * Rebuilds the transport once when the server reports UNAUTHENTICATED —
     * typically an expired handshake bearer token on a long-lived client. The
     * generation check makes concurrent failures trigger a single rebuild, and
     * the retryer then re-attempts the query on the fresh connection.
     */
    private void maybeRebuildOnAuthError(FlightRuntimeException e, FlightChannel[] expected) {
        if (e.status().code() != FlightStatusCode.UNAUTHENTICATED || Strings.isNullOrEmpty(apiKey)) {
            return;
        }
        synchronized (this) {
            if (closed || this.channels != expected) {
                return;
            }
            logger.info("Received UNAUTHENTICATED from server; re-authenticating with a fresh handshake");
            closeStatementsQuietly(statementCache.swapGeneration(null));
            retireChannelsLocked();
            sweepRetiredChannels(false);
            try {
                buildFlightChannels();
            } catch (RuntimeException rebuildError) {
                // Leave channels null: the next query attempt rebuilds lazily.
                logger.warn("Re-authentication failed: {}", rebuildError.getMessage());
            }
        }
    }

    /**
     * Builds an SSLContext configured with the custom CA and/or client certificate
     * for the JDK HTTP client.
     */
    private javax.net.ssl.SSLContext buildSslContext() throws Exception {
        // Ensure BouncyCastle provider is registered for PEM private key parsing
        if (java.security.Security.getProvider("BC") == null) {
            java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }

        javax.net.ssl.KeyManager[] keyManagers = null;
        javax.net.ssl.TrustManager[] trustManagers = null;

        if (this.tlsClientCertFile != null && this.tlsClientKeyFile != null) {
            // Load the client certificate
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            java.security.cert.Certificate clientCert;
            try (java.io.FileInputStream fis = new java.io.FileInputStream(this.tlsClientCertFile)) {
                clientCert = cf.generateCertificate(fis);
            }

            // Parse the PEM private key using BouncyCastle
            java.security.PrivateKey privateKey;
            try (java.io.FileReader keyReader = new java.io.FileReader(this.tlsClientKeyFile,
                    java.nio.charset.StandardCharsets.UTF_8);
                    org.bouncycastle.openssl.PEMParser pemParser = new org.bouncycastle.openssl.PEMParser(keyReader)) {
                Object parsed = pemParser.readObject();
                org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter converter =
                        new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter().setProvider("BC");
                if (parsed instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
                    privateKey = converter.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) parsed);
                } else if (parsed instanceof org.bouncycastle.openssl.PEMKeyPair) {
                    privateKey = converter
                            .getPrivateKey(((org.bouncycastle.openssl.PEMKeyPair) parsed).getPrivateKeyInfo());
                } else {
                    throw new IllegalArgumentException("Unsupported PEM key format in " + this.tlsClientKeyFile);
                }
            }

            // Build a KeyStore with the client identity
            java.security.KeyStore keyStore = java.security.KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            keyStore.setKeyEntry("client", privateKey, new char[0],
                    new java.security.cert.Certificate[] { clientCert });
            javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance(
                    javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, new char[0]);
            keyManagers = kmf.getKeyManagers();
        }

        if (this.tlsRootCertFile != null) {
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            java.security.KeyStore trustStore = java.security.KeyStore
                    .getInstance(java.security.KeyStore.getDefaultType());
            trustStore.load(null, null);
            try (java.io.FileInputStream fis = new java.io.FileInputStream(this.tlsRootCertFile)) {
                int i = 0;
                for (java.security.cert.Certificate cert : cf.generateCertificates(fis)) {
                    trustStore.setCertificateEntry("custom-ca-" + i++, cert);
                }
            }
            javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(
                    javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            trustManagers = tmf.getTrustManagers();
        }

        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);
        return sslContext;
    }

    /**
     * Returns the HTTP client, creating it on first use.
     */
    private HttpClient httpClient() {
        HttpClient client = this.httpClient;
        if (client != null) {
            return client;
        }
        synchronized (this) {
            if (this.httpClient == null) {
                HttpClient.Builder httpBuilder = HttpClient.newBuilder()
                        .connectTimeout(HTTP_CONNECT_TIMEOUT);
                if (this.tlsRootCertFile != null
                        || (this.tlsClientCertFile != null && this.tlsClientKeyFile != null)) {
                    try {
                        httpBuilder.sslContext(buildSslContext());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to configure TLS for HTTP client", e);
                    }
                }
                this.httpClient = httpBuilder.build();
            }
            return this.httpClient;
        }
    }

    /**
     * Initializes the cached retryer instances.
     * Called from constructor and must be called after maxRetries is set.
     */
    private void initRetryers() {
        this.flightRetryer = buildRetryer();
        this.readerRetryer = buildRetryer();
    }

    private <T> Retryer<T> buildRetryer() {
        return RetryerBuilder.<T>newBuilder()
                .retryIfException(throwable -> throwable instanceof FlightRuntimeException
                        && shouldRetry(((FlightRuntimeException) throwable).status()))
                .withWaitStrategy(WaitStrategies.join(
                        WaitStrategies.exponentialWait(RETRY_BACKOFF_MULTIPLIER_MS, RETRY_BACKOFF_MAX_MS,
                                java.util.concurrent.TimeUnit.MILLISECONDS),
                        WaitStrategies.randomWait(1, java.util.concurrent.TimeUnit.MILLISECONDS,
                                RETRY_JITTER_MAX_MS, java.util.concurrent.TimeUnit.MILLISECONDS)))
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
        if (closed) {
            throw new IllegalStateException("Cannot query with a closed SpiceClient");
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
     * Executes a parameterized SQL query using Arrow Flight SQL prepared
     * statements.
     * This is the recommended method for queries with user input to prevent SQL
     * injection.
     * Parameters should use positional placeholders ($1, $2, etc.) in the SQL
     * query.
     *
     * <p>
     * Prepared statements are cached and reused for repeated executions of the
     * same SQL, saving the create/close round trips on every call.
     * </p>
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
        if (closed) {
            throw new IllegalStateException("Cannot query with a closed SpiceClient");
        }

        logger.debug("Executing parameterized query with {} parameters: {}", params != null ? params.length : 0, sql);
        try {
            ArrowReader result = readerRetryer.call(() -> executeParameterizedQuery(sql, params));
            logger.debug("Parameterized query executed successfully");
            return result;
        } catch (RetryException e) {
            Throwable err = e.getLastFailedAttempt().getExceptionCause();
            logger.error("Parameterized query failed after {} attempts: {}", e.getNumberOfFailedAttempts(),
                    err.getMessage());
            throw new ExecutionException("Failed to execute parameterized query due to error: " + err.toString(), err);
        }
    }

    /**
     * Executes a single attempt of a parameterized query: bind + execute on a
     * cached prepared statement when available, falling back to a freshly
     * prepared statement when the cached one fails (e.g. the server restarted
     * and no longer knows the handle).
     */
    private ArrowReader executeParameterizedQuery(String sql, Object[] params) throws IOException {
        final FlightChannel[] snapshot = currentChannels();
        try (VectorSchemaRoot paramRoot = (params != null && params.length > 0) ? createParameterRoot(params)
                : null) {
            CachedStatement statement = statementCache.borrow(sql);
            FlightInfo info = null;
            if (statement != null) {
                try {
                    info = bindAndExecute(statement, paramRoot);
                } catch (FlightRuntimeException e) {
                    logger.debug("Cached prepared statement failed ({}); preparing a fresh statement",
                            e.status().code());
                    closeStatementQuietly(statement);
                    statement = null;
                }
            }
            if (info == null) {
                FlightChannel channel = selectChannel(snapshot);
                try {
                    statement = new CachedStatement(channel.client.prepare(sql, channel.callOptions), channel,
                            snapshot);
                    info = bindAndExecute(statement, paramRoot);
                } catch (FlightRuntimeException e) {
                    if (statement != null) {
                        closeStatementQuietly(statement);
                    }
                    maybeRebuildOnAuthError(e, snapshot);
                    throw e;
                }
            }

            // The statement is healthy: return it to the cache before opening the
            // result stream so other threads can reuse it immediately.
            //
            // Safe because only bind+execute mutates statement state — and the
            // cache hands a statement to at most one thread at a time for that
            // phase. The result stream is served by the self-contained ticket
            // minted by execute(), not by live statement state: the previous
            // implementation closed the server-side statement outright at this
            // point (reader still open) and reads were unaffected, so a later
            // re-bind on the same handle cannot alter an already-issued ticket.
            if (!statementCache.give(sql, statement)) {
                closeStatementQuietly(statement);
            }

            try {
                return new FlightInfoReader(allocator, statement.channel.client, statement.channel.streamOptions,
                        info);
            } catch (FlightRuntimeException e) {
                maybeRebuildOnAuthError(e, snapshot);
                throw e;
            }
        }
    }

    /**
     * Binds the parameters (if any) and executes the prepared statement.
     * The parameter root is passed as a non-owning view: the statement's
     * clearParameters() closes only the view, never the caller's root.
     */
    private FlightInfo bindAndExecute(CachedStatement statement, VectorSchemaRoot paramRoot) {
        if (paramRoot != null) {
            statement.statement.setParameters(new NonOwningRoot(paramRoot));
        }
        try {
            return statement.statement.execute(statement.channel.callOptions);
        } finally {
            try {
                statement.statement.clearParameters();
            } catch (RuntimeException ignored) {
                // Best-effort: closing the non-owning view cannot fail meaningfully.
            }
        }
    }

    private void closeStatementQuietly(CachedStatement statement) {
        try {
            statement.statement.close();
        } catch (Exception e) {
            logger.debug("Error closing prepared statement: {}", e.getMessage());
        }
    }

    private void closeStatementsQuietly(List<CachedStatement> statements) {
        for (CachedStatement statement : statements) {
            closeStatementQuietly(statement);
        }
    }

    private void closeChannelQuietly(FlightChannel channel, Throwable pending) {
        try {
            channel.client.close();
        } catch (Exception e) {
            if (pending != null) {
                pending.addSuppressed(e);
            } else {
                logger.warn("Error closing Flight channel: {}", e.getMessage());
            }
        }
    }

    /**
     * Retires the current channels: the gRPC transports are shut down
     * gracefully — in-flight RPCs and open result streams complete, new RPCs
     * fail with a retryable UNAVAILABLE — but the Flight clients (and their
     * buffer allocators) stay open until {@link #sweepRetiredChannels} sees
     * the transport terminate. Closing them inline would tear the allocator
     * out from under concurrent queries mid-RPC.
     * Must be called while holding the client monitor.
     */
    private void retireChannelsLocked() {
        FlightChannel[] snapshot = this.channels;
        if (snapshot == null) {
            return;
        }
        for (FlightChannel channel : snapshot) {
            try {
                channel.grpcChannel.shutdown();
            } catch (RuntimeException e) {
                logger.warn("Error shutting down Flight transport: {}", e.getMessage());
            }
            retiredChannels.add(channel);
        }
        this.channels = null;
    }

    /**
     * Closes retired channels whose transport has fully terminated (no RPCs or
     * streams remain). With {@code force}, closes them regardless — used by
     * {@link #close()}, where interrupting anything still in flight is the
     * documented behavior.
     * Must be called while holding the client monitor.
     */
    private void sweepRetiredChannels(boolean force) {
        for (java.util.Iterator<FlightChannel> it = retiredChannels.iterator(); it.hasNext();) {
            FlightChannel channel = it.next();
            if (force || channel.grpcChannel.isTerminated()) {
                closeChannelQuietly(channel, null);
                it.remove();
            }
        }
    }

    /**
     * Creates a VectorSchemaRoot containing the parameter values.
     * The caller is responsible for closing the returned root.
     * Package-private for tests.
     */
    VectorSchemaRoot createParameterRoot(Object... params) {
        final int numParams = params.length;

        // Single pass: build schema fields directly (no intermediate arrays)
        List<Field> fields = new ArrayList<>(numParams);
        for (int i = 0; i < numParams; i++) {
            Object param = params[i];
            ArrowType type;
            if (param instanceof Param) {
                Param p = (Param) param;
                type = p.hasExplicitType() ? p.getType() : inferArrowType(p.getValue());
            } else {
                type = inferArrowType(param);
            }
            String fieldName = (i < PARAM_NAMES.length) ? PARAM_NAMES[i] : "$" + (i + 1);
            fields.add(new Field(fieldName, FieldType.nullable(type), null));
        }
        Schema schema = new Schema(fields);

        // Create a VectorSchemaRoot sized for a single row of parameters —
        // without setInitialCapacity(1), allocateNew() reserves Arrow's default
        // ~3970-slot buffers per vector for what is always a 1-row binding.
        VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator);
        for (FieldVector vector : root.getFieldVectors()) {
            vector.setInitialCapacity(1);
        }
        root.allocateNew();

        // Populate vectors — read value from original params to avoid intermediate arrays
        for (int i = 0; i < numParams; i++) {
            Object param = params[i];
            Object value = (param instanceof Param) ? ((Param) param).getValue() : param;
            FieldVector vector = root.getVector(i);
            appendValueToVector(vector, 0, value, vector.getField().getType());
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
    private void appendValueToVector(FieldVector vector, int index, Object value, ArrowType type) {
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
                throw new IllegalArgumentException("Unsupported vector type: " + vector.getClass().getName());
            }
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(
                    "Cannot convert value of type " + value.getClass().getName() +
                            " to " + vector.getClass().getSimpleName(),
                    e);
        }
    }

    /**
     * Checks whether the Spice runtime is healthy by calling {@code /health}.
     *
     * <p>
     * This is an unauthenticated liveness probe: it reports that the runtime is
     * up, not that it is finished loading. Use {@link #isReady()} before issuing
     * queries.
     *
     * <p>
     * Returns false rather than throwing when the runtime is unreachable, so it
     * can be polled directly in a loop.
     *
     * @return true if the runtime reports healthy
     */
    public boolean isHealthy() {
        return probe("/health", "ok", false);
    }

    /**
     * Checks whether the Spice runtime is ready to accept queries by calling
     * {@code /v1/ready}.
     *
     * <p>
     * The runtime becomes ready once its datasets have loaded, so this returns
     * false for a period after {@link #isHealthy()} first returns true. When an
     * API key is configured it is sent, which Spice.ai Cloud requires.
     *
     * <p>
     * Returns false rather than throwing when the runtime is unreachable, so it
     * can be polled directly in a loop.
     *
     * @return true if the runtime reports ready
     */
    public boolean isReady() {
        return probe("/v1/ready", "ready", true);
    }

    /**
     * Sends a GET to a runtime probe endpoint and reports whether it succeeded.
     *
     * @param path         the endpoint path
     * @param expectedBody the token the body must contain
     * @param authenticate whether to send the API key, when one is configured
     * @return true if the runtime returned 200 with the expected body
     */
    private boolean probe(String path, String expectedBody, boolean authenticate) {
        try {
            HttpRequest request = buildProbeRequest(this.httpAddress, path,
                    authenticate ? this.apiKey : null);

            HttpResponse<String> response = httpClient().send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.debug("Probe {} returned status {}", path, response.statusCode());
                return false;
            }

            // Compare the whole trimmed body, not a substring: "not ready"
            // contains "ready", so a substring test reports a loading runtime as
            // ready. These endpoints return a single token.
            String body = response.body();
            return body != null && body.trim().equalsIgnoreCase(expectedBody);
        } catch (InterruptedException err) {
            Thread.currentThread().interrupt();
            logger.debug("Probe {} interrupted", path);
            return false;
        } catch (Exception err) {
            logger.debug("Probe {} failed: {}", path, err.getMessage());
            return false;
        }
    }

    /**
     * Builds a GET request against a runtime endpoint.
     *
     * @param httpAddress the runtime's HTTP address
     * @param path        the endpoint path
     * @param apiKey      the API key to send, or null to send none
     * @return the request
     * @throws URISyntaxException if the resulting URI is malformed
     */
    static HttpRequest buildProbeRequest(URI httpAddress, String path, String apiKey)
            throws URISyntaxException {
        // Resolve rather than concatenate: a base address with a trailing slash
        // would otherwise produce "http://host:8090//v1/ready".
        URI uri = httpAddress.resolve(path.startsWith("/") ? path : "/" + path);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .header("X-Spice-User-Agent", Config.getUserAgent())
                .GET();

        if (!Strings.isNullOrEmpty(apiKey)) {
            builder = builder.header("X-API-Key", apiKey);
        }

        return builder.build();
    }

    /**
     * Returns the status of each runtime connection by calling
     * {@code /v1/status}.
     *
     * <p>
     * The runtime reports one {@link ConnectionDetails} per connection —
     * {@code http}, {@code flight}, {@code metrics}, and
     * {@code opentelemetry} — naming which component is not ready and where it is
     * bound. That makes it strictly more informative than the boolean
     * {@link #isReady()}.
     *
     * @return the status of each runtime connection
     * @throws ExecutionException if the runtime is unreachable or returns an
     *                            unexpected response
     */
    public List<ConnectionDetails> runtimeStatus() throws ExecutionException {
        logger.debug("Fetching runtime status");
        try {
            HttpRequest request = buildProbeRequest(this.httpAddress, "/v1/status", this.apiKey);

            HttpResponse<String> response = httpClient().send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Runtime status failed - statusCode={}, response={}", response.statusCode(),
                        response.body());
                throw new ExecutionException(
                        String.format("Failed to fetch runtime status. Status Code: %d, Response: %s",
                                response.statusCode(),
                                response.body()),
                        null);
            }

            return parseRuntimeStatus(response.body());
        } catch (ExecutionException e) {
            // no need to wrap ExecutionException
            throw e;
        } catch (ConnectException err) {
            logger.error("Cannot connect to Spice runtime at {}: {}", this.httpAddress, err.getMessage());
            throw new ExecutionException(
                    String.format("The Spice runtime is unavailable at %s. Is it running?", this.httpAddress), err);
        } catch (InterruptedException err) {
            Thread.currentThread().interrupt();
            throw new ExecutionException("Interrupted while fetching runtime status", err);
        } catch (Exception err) {
            logger.error("Runtime status failed: {}", err.getMessage());
            throw new ExecutionException("Failed to fetch runtime status due to error: " + err.toString(), err);
        }
    }

    /**
     * Parses the {@code /v1/status} response body.
     *
     * @param body the JSON array the runtime returned
     * @return the parsed connection details
     * @throws ExecutionException if the body is not a JSON array
     */
    static List<ConnectionDetails> parseRuntimeStatus(String body) throws ExecutionException {
        JsonElement root;
        try {
            root = JsonParser.parseString(body == null ? "" : body);
        } catch (JsonSyntaxException err) {
            throw new ExecutionException("The runtime returned a malformed status response", err);
        }

        if (root == null || !root.isJsonArray()) {
            throw new ExecutionException("The runtime returned an unexpected status response", null);
        }

        List<ConnectionDetails> details = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            details.add(new ConnectionDetails(
                    optionalString(object, "name"),
                    optionalString(object, "endpoint"),
                    optionalString(object, "status")));
        }
        return details;
    }

    /**
     * Reads a string member, tolerating absent or null members.
     *
     * @param object the object to read from
     * @param member the member name
     * @return the string value, or null when absent
     */
    private static String optionalString(JsonObject object, String member) {
        JsonElement element = object.get(member);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    /**
     * Finds documents similar to {@code request}'s text by calling the
     * runtime's {@code /v1/search} endpoint.
     *
     * <p>
     * This runs against datasets that have an embedding column and a loaded
     * embedding model. See
     * <a href="https://docs.spice.ai/features/search-and-retrieval">the
     * search and retrieval docs</a> for how to configure them.
     *
     * @param request the search request
     * @return the search response
     * @throws ExecutionException if there is an error performing the search
     */
    public SearchResponse search(SearchRequest request) throws ExecutionException {
        if (request == null) {
            throw new IllegalArgumentException("No search request provided");
        }
        if (Strings.isNullOrEmpty(request.getText())) {
            throw new IllegalArgumentException("SearchRequest.text is required and must be a non-empty string");
        }
        if (request.getLimit() != null && request.getLimit() < 1) {
            throw new IllegalArgumentException("SearchRequest.limit must be greater than 0, got " + request.getLimit());
        }

        logger.debug("Performing search: {}", request.getText());
        try {
            // Resolve rather than concatenate: a base address with a trailing slash
            // would otherwise produce "http://host:8090//v1/search".
            URI uri = this.httpAddress.resolve("/v1/search");
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(HTTP_REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-Spice-User-Agent", Config.getUserAgent());
            if (!Strings.isNullOrEmpty(this.apiKey)) {
                builder = builder.header("X-API-Key", this.apiKey);
            }
            builder = builder.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(request)));

            HttpRequest httpRequest = builder.build();
            HttpResponse<String> response = httpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Search failed - statusCode={}, response={}", response.statusCode(), response.body());
                // The runtime explains search failures in a plain-text body (e.g. "No data
                // sources provided"); surface it rather than only the status code.
                throw new ExecutionException(
                        String.format("Failed to perform search. Status Code: %d, Response: %s",
                                response.statusCode(), response.body()),
                        null);
            }

            try {
                return GSON.fromJson(response.body(), SearchResponse.class);
            } catch (JsonSyntaxException e) {
                throw new ExecutionException("The runtime returned a malformed search response", e);
            }
        } catch (ExecutionException e) {
            // no need to wrap ExecutionException
            throw e;
        } catch (ConnectException err) {
            logger.error("Cannot connect to Spice runtime at {}: {}", this.httpAddress, err.getMessage());
            throw new ExecutionException(
                    String.format("The Spice runtime is unavailable at %s. Is it running?", this.httpAddress), err);
        } catch (InterruptedException err) {
            Thread.currentThread().interrupt();
            throw new ExecutionException("Interrupted while performing search", err);
        } catch (Exception err) {
            logger.error("Search failed: {}", err.getMessage());
            throw new ExecutionException("Failed to perform search due to error: " + err.toString(), err);
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
                    .timeout(HTTP_REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-Spice-User-Agent", Config.getUserAgent());

            if (refreshOptions != null) {
                String json = GSON.toJson(refreshOptions);
                builder = builder.POST(HttpRequest.BodyPublishers.ofString(json));
            } else {
                builder = builder.POST(HttpRequest.BodyPublishers.ofString("{}"));
            }

            HttpRequest request = builder.build();
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201) {
                logger.error("Dataset refresh failed - dataset={}, statusCode={}, response={}", dataset,
                        response.statusCode(), response.body());
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

    /**
     * Submits {@code sql} to the Spice runtime for asynchronous execution and
     * returns a handle for polling status and retrieving results.
     *
     * <p>
     * Async queries require the runtime to be running in distributed/scheduler
     * mode ({@code spiced --role scheduler} with
     * {@code runtime.scheduler.state_location} configured); otherwise the
     * runtime reports an error indicating async queries are only available in
     * cluster mode.
     *
     * <p>
     * Use {@link #query(String)} for the normal synchronous, streaming query
     * path.
     *
     * @param sql the SQL query to submit
     * @return a handle to the submitted query
     * @throws ExecutionException if the query could not be submitted
     */
    public AsyncQuery queryAsync(String sql) throws ExecutionException {
        return submitAsyncQuery(sql, null);
    }

    /**
     * Submits a parameterized query for asynchronous execution. Parameters are
     * bound positionally ($1, $2, ...) and sent to the runtime as a JSON array,
     * so each parameter must be a value Gson can encode meaningfully as JSON
     * (numbers, strings, booleans, lists) — this bypasses the Arrow-typed
     * parameter binding {@link #queryWithParams(String, Object...)} uses, so
     * temporal and decimal types are not given special handling here.
     *
     * <p>
     * Use {@link #queryWithParams(String, Object...)} for the normal
     * synchronous, streaming parameterized query path.
     *
     * @param sql    the SQL query with positional parameter placeholders ($1, $2,
     *               etc.)
     * @param params the parameter values
     * @return a handle to the submitted query
     * @throws ExecutionException if the query could not be submitted
     */
    public AsyncQuery queryAsyncWithParams(String sql, Object... params) throws ExecutionException {
        return submitAsyncQuery(sql, (params != null && params.length > 0) ? params : null);
    }

    private AsyncQuery submitAsyncQuery(String sql, Object[] params) throws ExecutionException {
        if (Strings.isNullOrEmpty(sql)) {
            throw new IllegalArgumentException("No SQL query provided");
        }
        if (closed) {
            throw new IllegalStateException("Cannot query with a closed SpiceClient");
        }

        JsonObject request = new JsonObject();
        request.addProperty("sql", sql);
        if (params != null) {
            request.add("parameters", GSON.toJsonTree(params));
        }

        logger.debug("Submitting async query: {}", sql);
        byte[] body = doFlightAction(ACTION_SUBMIT_ASYNC_QUERY, request);
        JsonObject response = parseAsyncActionResponse(body, "submit async query response");
        String queryId = optionalString(response, "query_id");
        if (Strings.isNullOrEmpty(queryId)) {
            throw new ExecutionException("The runtime did not return a query_id for the submitted async query", null);
        }
        QueryStatus status = QueryStatus.fromWireValue(optionalString(response, "status"));
        logger.debug("Async query submitted: queryId={}, status={}", queryId, status);
        return new AsyncQuery(this, queryId, status);
    }

    /**
     * Fetches the current status (and, when terminal, error/result metadata) of
     * an async query. Package-private: called by {@link AsyncQuery}.
     */
    JsonObject asyncQueryStatus(String queryId) throws ExecutionException {
        return asyncQueryStatus(queryId, null);
    }

    /**
     * Fetches the current status of an async query, bounding the RPC to
     * {@code perCallTimeout} when given. Package-private: called by
     * {@link AsyncQuery#waitForCompletion(java.time.Duration)} so a stalled
     * poll cannot outlive the caller's remaining wait budget.
     */
    JsonObject asyncQueryStatus(String queryId, java.time.Duration perCallTimeout) throws ExecutionException {
        JsonObject request = new JsonObject();
        request.addProperty("query_id", queryId);
        byte[] body = doFlightAction(ACTION_GET_ASYNC_QUERY_STATUS, request, false, perCallTimeout);
        return parseAsyncActionResponse(body, "async query status response");
    }

    /**
     * Requests cancellation of an async query. Package-private: called by
     * {@link AsyncQuery}.
     */
    JsonObject asyncQueryCancel(String queryId) throws ExecutionException {
        JsonObject request = new JsonObject();
        request.addProperty("query_id", queryId);
        byte[] body = doFlightAction(ACTION_CANCEL_ASYNC_QUERY, request);
        return parseAsyncActionResponse(body, "cancel async query response");
    }

    /**
     * Fetches one chunk of an async query's results as a self-contained Arrow
     * IPC stream. Package-private: called by {@link AsyncQueryResultReader}.
     */
    byte[] asyncQueryResultChunk(String queryId, int chunkIndex) throws ExecutionException {
        JsonObject request = new JsonObject();
        request.addProperty("query_id", queryId);
        request.addProperty("chunk_index", chunkIndex);
        // Result downloads are not bounded by the planning/query timeout baked
        // into callOptions (see SpiceClientBuilder's withQueryTimeout docs) —
        // use the auth-only streamOptions, matching how the sync query path
        // downloads results.
        return doFlightAction(ACTION_GET_ASYNC_QUERY_RESULT, request, true);
    }

    /**
     * The buffer allocator backing this client's Flight channels.
     * Package-private: used by {@link AsyncQueryResultReader} to decode result
     * chunks with the same allocator as the rest of the client.
     */
    BufferAllocator allocator() {
        return this.allocator;
    }

    private static JsonObject parseAsyncActionResponse(byte[] body, String description) throws ExecutionException {
        try {
            JsonElement root = JsonParser.parseString(body == null ? "" : new String(body, java.nio.charset.StandardCharsets.UTF_8));
            if (root == null || !root.isJsonObject()) {
                throw new ExecutionException("The runtime returned an unexpected " + description, null);
            }
            return root.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new ExecutionException("The runtime returned a malformed " + description, e);
        }
    }

    /**
     * Performs a Flight {@code DoAction} with a JSON-encoded request body and
     * returns the concatenated {@code Result} bodies, wrapping any failure into
     * an {@link ExecutionException} (this file's convention for every public
     * method). On {@code UNAUTHENTICATED}, re-authenticates and retries the
     * action exactly once on the fresh channel — matching the automatic
     * re-handshake-and-retry behavior of the sync query paths — before giving
     * up. Does not retry on any other error (matching gospice's behavior, which
     * also does not retry {@code DoAction}); callers that need retries on
     * transient errors can call the {@code AsyncQuery} method again.
     */
    private byte[] doFlightAction(String actionType, JsonObject request) throws ExecutionException {
        return doFlightAction(actionType, request, false, null);
    }

    private byte[] doFlightAction(String actionType, JsonObject request, boolean useStreamOptions)
            throws ExecutionException {
        return doFlightAction(actionType, request, useStreamOptions, null);
    }

    private byte[] doFlightAction(String actionType, JsonObject request, boolean useStreamOptions,
            java.time.Duration perCallTimeout) throws ExecutionException {
        byte[] requestBody = GSON.toJson(request).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FlightChannel[] snapshot = currentChannels();
        try {
            return doFlightActionOnce(actionType, requestBody, snapshot, useStreamOptions, perCallTimeout);
        } catch (FlightRuntimeException e) {
            maybeRebuildOnAuthError(e, snapshot);
            if (e.status().code() == FlightStatusCode.UNAUTHENTICATED && !Strings.isNullOrEmpty(apiKey)) {
                try {
                    FlightChannel[] rebuilt = currentChannels();
                    return doFlightActionOnce(actionType, requestBody, rebuilt, useStreamOptions, perCallTimeout);
                } catch (RuntimeException retryError) {
                    throw wrapFlightFailure(actionType, retryError, perCallTimeout);
                }
            }
            throw wrapFlightFailure(actionType, e, perCallTimeout);
        } catch (RuntimeException e) {
            throw wrapFlightFailure(actionType, e, perCallTimeout);
        }
    }

    /**
     * Wraps a failed Flight action into an {@link ExecutionException}, reporting
     * it as a timeout (rather than a generic transport failure) when
     * {@code perCallTimeout} was set and the RPC itself was the thing that
     * exceeded it — otherwise a caller bounding a poll to its remaining wait
     * budget (see {@link AsyncQuery#waitForCompletion(java.time.Duration)}) sees
     * a misleading "Failed to perform" error instead of a timeout.
     */
    private ExecutionException wrapFlightFailure(String actionType, RuntimeException e,
            java.time.Duration perCallTimeout) {
        if (perCallTimeout != null && e instanceof FlightRuntimeException
                && ((FlightRuntimeException) e).status().code() == FlightStatusCode.TIMED_OUT) {
            return new ExecutionException("Timed out waiting for a response to " + actionType, e);
        }
        return new ExecutionException("Failed to perform " + actionType + " due to error: " + e.toString(), e);
    }

    private byte[] doFlightActionOnce(String actionType, byte[] requestBody, FlightChannel[] snapshot,
            boolean useStreamOptions, java.time.Duration perCallTimeout) {
        FlightChannel channel = selectChannel(snapshot);
        CallOption[] baseOptions = useStreamOptions ? channel.streamOptions : channel.callOptions;
        CallOption[] options = baseOptions;
        if (perCallTimeout != null) {
            // Appended last so it overrides any queryTimeout already baked into
            // callOptions: the caller's remaining wait budget is a tighter,
            // more specific bound than the general planning-timeout default.
            // Rounded up rather than truncated, and floored at 1ms: Duration#toMillis()
            // truncates toward zero, so a sub-millisecond positive remainder would
            // otherwise become a 0ms timeout, which gRPC treats as "expire immediately"
            // rather than "no deadline", turning a live poll into a spurious failure.
            long timeoutMillis = Math.max(1, (perCallTimeout.toNanos() + 999_999) / 1_000_000);
            options = java.util.Arrays.copyOf(baseOptions, baseOptions.length + 1);
            options[baseOptions.length] = CallOptions.timeout(timeoutMillis,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        Iterator<Result> results = channel.rawClient.doAction(new Action(actionType, requestBody), options);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (results.hasNext()) {
            byte[] resultBody = results.next().getBody();
            out.write(resultBody, 0, resultBody.length);
        }
        return out.toByteArray();
    }

    // Without this Accept header, /v1/nsql returns a bare array of rows and
    // drops the generated SQL.
    private static final String NSQL_JSON_MEDIA_TYPE = "application/vnd.spiceai.nsql.v1+json";
    // Asks the runtime to generate SQL without executing it.
    private static final String NSQL_SQL_MEDIA_TYPE = "application/sql";

    /**
     * Answers {@code request}'s query by having the runtime's configured LLM
     * generate SQL, then running it.
     *
     * <p>
     * The generated SQL is returned in {@link NsqlResponse#getSql()}. The
     * runtime executes it read-only and retries generation when the query
     * fails to run, so a returned error means generation or execution failed
     * repeatedly.
     *
     * <p>
     * Nsql requires an LLM model in the Spicepod. See
     * <a href="https://docs.spice.ai/features/text-to-sql">the Nsql
     * docs</a> for how to configure one.
     *
     * @param request the natural-language query
     * @return the generated SQL and the rows it produced
     * @throws ExecutionException if there is an error generating or running the query
     */
    public NsqlResponse nsql(NsqlRequest request) throws ExecutionException {
        byte[] body = doNsqlRequest(request, NSQL_JSON_MEDIA_TYPE);
        NsqlResponse response;
        try {
            response = GSON.fromJson(new String(body, StandardCharsets.UTF_8), NsqlResponse.class);
        } catch (JsonSyntaxException err) {
            throw new ExecutionException("The runtime returned a malformed nsql response", err);
        }
        // An empty body or the JSON literal "null" is valid JSON but not the
        // documented response shape; Gson returns null rather than throwing for
        // either, so check explicitly instead of letting callers hit an
        // unrelated NPE.
        if (response == null) {
            throw new ExecutionException("The runtime returned a malformed nsql response", null);
        }
        return response;
    }

    /**
     * Translates {@code request}'s query into SQL without running it.
     *
     * <p>
     * Use it to inspect or edit the query before running it, or to run it
     * through {@link #query(String)} or {@link #queryWithParams(String, Object...)}
     * so the results arrive as Arrow rather than decoded JSON.
     *
     * @param request the natural-language query
     * @return the generated SQL
     * @throws ExecutionException if there is an error generating the query
     */
    public String nsqlGenerateSql(NsqlRequest request) throws ExecutionException {
        byte[] body = doNsqlRequest(request, NSQL_SQL_MEDIA_TYPE);
        return new String(body, StandardCharsets.UTF_8).trim();
    }

    /**
     * Posts {@code request} to {@code /v1/nsql} asking for {@code accept},
     * and returns the response body when the runtime answered 200.
     */
    private byte[] doNsqlRequest(NsqlRequest request, String accept) throws ExecutionException {
        if (request == null) {
            throw new IllegalArgumentException("No NsqlRequest provided");
        }
        if (Strings.isNullOrEmpty(request.getQuery())) {
            throw new IllegalArgumentException(
                    "NsqlRequest.query is required and must be a non-empty natural language query");
        }

        logger.debug("Executing nsql request: {}", request.getQuery());
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(this.httpAddress.resolve("/v1/nsql"))
                    .timeout(HTTP_REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", accept)
                    .header("X-Spice-User-Agent", Config.getUserAgent())
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(request)));

            if (!Strings.isNullOrEmpty(this.apiKey)) {
                builder = builder.header("X-API-Key", this.apiKey);
            }

            HttpResponse<byte[]> response = httpClient().send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                String responseBody = new String(response.body(), StandardCharsets.UTF_8).trim();
                logger.error("Nsql request failed - statusCode={}, response={}", response.statusCode(), responseBody);
                throw new ExecutionException(
                        String.format("Failed to execute nsql request. Status Code: %d, Response: %s",
                                response.statusCode(), responseBody),
                        null);
            }
            logger.debug("Nsql request executed successfully");
            return response.body();
        } catch (ExecutionException e) {
            // no need to wrap ExecutionException
            throw e;
        } catch (ConnectException err) {
            logger.error("Cannot connect to Spice runtime at {}: {}", this.httpAddress, err.getMessage());
            throw new ExecutionException(
                    String.format("The Spice runtime is unavailable at %s. Is it running?", this.httpAddress), err);
        } catch (InterruptedException err) {
            Thread.currentThread().interrupt();
            throw new ExecutionException("Interrupted while executing nsql request", err);
        } catch (Exception err) {
            logger.error("Nsql request failed: {}", err.getMessage());
            throw new ExecutionException("Failed to execute nsql request due to error: " + err.toString(), err);
        }
    }

    private FlightStream queryInternal(String sql) {
        FlightChannel[] snapshot = currentChannels();
        FlightChannel channel = selectChannel(snapshot);
        try {
            FlightInfo flightInfo = channel.client.execute(sql, channel.callOptions);
            List<FlightEndpoint> endpoints = flightInfo.getEndpoints();
            if (endpoints.isEmpty()) {
                throw CallStatus.INTERNAL
                        .withDescription("Server returned a FlightInfo with no endpoints for the query")
                        .toRuntimeException();
            }
            if (endpoints.size() > 1) {
                logger.warn("Server returned {} endpoints; query() consumes only the first. "
                        + "Use queryWithParams() (ArrowReader) to consume all endpoints.", endpoints.size());
            }
            Ticket ticket = endpoints.get(0).getTicket();
            return channel.client.getStream(ticket, channel.streamOptions);
        } catch (FlightRuntimeException e) {
            maybeRebuildOnAuthError(e, snapshot);
            throw e;
        }
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
            case UNAUTHENTICATED:
                // Retried after maybeRebuildOnAuthError() re-handshakes; only
                // meaningful for authenticated clients.
                return !Strings.isNullOrEmpty(apiKey);
            default:
                return false;
        }
    }

    @Override
    public synchronized void close() throws Exception {
        if (closed) {
            return;
        }
        closed = true;
        logger.debug("Closing SpiceClient");
        List<Exception> exceptions = new ArrayList<>();

        // Close cached prepared statements first (best-effort RPCs that need the
        // channels to still be open).
        try {
            closeStatementsQuietly(statementCache.swapGeneration(null));
        } catch (Exception e) {
            logger.warn("Error during prepared statement cleanup: {}", e.getMessage());
            exceptions.add(e);
        }

        // Close Flight channels: current ones and any retired channels whose
        // in-flight work never finished.
        FlightChannel[] snapshot = this.channels;
        this.channels = null;
        List<FlightChannel> toClose = new ArrayList<>(retiredChannels);
        retiredChannels.clear();
        if (snapshot != null) {
            toClose.addAll(java.util.Arrays.asList(snapshot));
        }
        for (FlightChannel channel : toClose) {
            try {
                channel.client.close();
            } catch (Exception e) {
                logger.warn("Error closing Flight channel: {}", e.getMessage());
                exceptions.add(e);
            }
        }
        logger.debug("Flight channels closed");

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
