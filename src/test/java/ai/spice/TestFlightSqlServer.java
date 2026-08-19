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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.arrow.flight.Action;
import org.apache.arrow.flight.CallHeaders;
import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.FlightDescriptor;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightServer;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.Location;
import org.apache.arrow.flight.PutResult;
import org.apache.arrow.flight.Result;
import org.apache.arrow.flight.Ticket;
import org.apache.arrow.flight.auth2.BasicCallHeaderAuthenticator;
import org.apache.arrow.flight.auth2.CallHeaderAuthenticator;
import org.apache.arrow.flight.auth2.GeneratedBearerTokenAuthenticator;
import org.apache.arrow.flight.sql.NoOpFlightSqlProducer;
import org.apache.arrow.flight.sql.impl.FlightSql;
import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;

/**
 * In-process Flight SQL server for tests: serves deterministic data, counts
 * every RPC, and supports failure injection (transient errors, prepared
 * statement invalidation, artificial delays, bearer-token expiry).
 */
final class TestFlightSqlServer implements AutoCloseable {

    static final Schema RESULT_SCHEMA = new Schema(Arrays.asList(
            Field.nullable("id", new ArrowType.Int(64, true)),
            Field.nullable("name", ArrowType.Utf8.INSTANCE)));

    // ==================== RPC counters ====================
    final AtomicInteger createPreparedStatementCalls = new AtomicInteger();
    final AtomicInteger closePreparedStatementCalls = new AtomicInteger();
    final AtomicInteger getFlightInfoCalls = new AtomicInteger();
    final AtomicInteger doPutParameterCalls = new AtomicInteger();
    final AtomicInteger doGetCalls = new AtomicInteger();
    final AtomicInteger basicAuthValidations = new AtomicInteger();

    // ==================== failure injection ====================
    private final AtomicInteger failNextGetFlightInfo = new AtomicInteger();
    private volatile CallStatus injectedFailureStatus = CallStatus.UNAVAILABLE;
    private final AtomicBoolean rejectNextBearer = new AtomicBoolean();
    volatile long getFlightInfoDelayMs = 0;

    // ==================== result shape ====================
    volatile int endpointCount = 1;
    volatile int batchesPerEndpoint = 1;
    volatile int rowsPerBatch = 10;

    /** Parameter values (row 0 of each vector) captured from the last DoPut. */
    volatile List<Object> lastBoundParameters;

    private final Set<String> validHandles = ConcurrentHashMap.newKeySet();
    private final AtomicLong handleCounter = new AtomicLong();

    // ==================== async queries ====================
    private static final Gson ASYNC_GSON = new Gson();
    private final ConcurrentHashMap<String, AsyncQueryState> asyncQueries = new ConcurrentHashMap<>();
    private final AtomicLong asyncQueryCounter = new AtomicLong();
    final AtomicInteger submitAsyncQueryCalls = new AtomicInteger();
    final AtomicInteger getAsyncQueryResultCalls = new AtomicInteger();

    private final BufferAllocator allocator;
    private final FlightServer server;

    /** Starts an unauthenticated server on an ephemeral port. */
    TestFlightSqlServer() throws Exception {
        this(null, null, null, false);
    }

    /**
     * Starts a server on an ephemeral port. When expectedUser/expectedPassword
     * are non-null, the server requires a basic-auth handshake and issues
     * bearer tokens for subsequent calls (mirroring Spice's auth flow).
     */
    TestFlightSqlServer(String expectedUser, String expectedPassword) throws Exception {
        this(expectedUser, expectedPassword, null, false);
    }

    /**
     * Starts a TLS server using the fixture's server certificate. With
     * requireClientCert, clients must present a certificate signed by the
     * fixture CA (mutual TLS).
     */
    TestFlightSqlServer(TestCerts certs, boolean requireClientCert) throws Exception {
        this(null, null, certs, requireClientCert);
    }

    private TestFlightSqlServer(String expectedUser, String expectedPassword,
            TestCerts certs, boolean requireClientCert) throws Exception {
        this.allocator = new RootAllocator(Long.MAX_VALUE);
        // TLS tests bind and dial 127.0.0.1 explicitly: "localhost" can resolve
        // to ::1 first, and a connection-refused there would let negative TLS
        // tests pass without ever reaching the handshake under test.
        Location location = certs != null
                ? Location.forGrpcTls("127.0.0.1", 0)
                : Location.forGrpcInsecure("localhost", 0);
        FlightServer.Builder builder = FlightServer.builder(allocator, location, new Producer());
        if (certs != null) {
            builder.useTls(pemStream(certs.serverCert), pemStream(certs.serverKey));
            if (requireClientCert) {
                builder.useMTlsClientVerification(pemStream(certs.caCert));
            }
        }
        if (expectedUser != null) {
            CallHeaderAuthenticator inner = new GeneratedBearerTokenAuthenticator(
                    new BasicCallHeaderAuthenticator((username, password) -> {
                        basicAuthValidations.incrementAndGet();
                        if (!expectedUser.equals(username) || !expectedPassword.equals(password)) {
                            throw CallStatus.UNAUTHENTICATED.withDescription("invalid credentials")
                                    .toRuntimeException();
                        }
                        return () -> username;
                    }));
            builder.headerAuthenticator(headers -> {
                String authorization = headers.get("authorization");
                if (authorization != null && authorization.startsWith("Bearer ")
                        && rejectNextBearer.compareAndSet(true, false)) {
                    throw CallStatus.UNAUTHENTICATED.withDescription("token expired").toRuntimeException();
                }
                return inner.authenticate(headers);
            });
        }
        this.server = builder.build();
        this.server.start();
    }

    int getPort() {
        return server.getPort();
    }

    URI flightUri() throws Exception {
        return new URI("grpc://localhost:" + getPort());
    }

    /** The next N GetFlightInfo calls (plain or prepared) fail with the given status. */
    void failNextGetFlightInfo(int count, CallStatus status) {
        this.injectedFailureStatus = status;
        this.failNextGetFlightInfo.set(count);
    }

    /** Simulates a server restart: all existing prepared statement handles become unknown. */
    void invalidatePreparedStatements() {
        validHandles.clear();
    }

    /** The next call presenting a bearer token is rejected with UNAUTHENTICATED. */
    void rejectNextBearerToken() {
        rejectNextBearer.set(true);
    }

    /**
     * Configures the status sequence {@code GetAsyncQueryStatus} returns for
     * queryId: the submit response reports {@code sequence.get(0)}, and each
     * subsequent status poll advances one entry, holding the last entry once
     * exhausted.
     */
    void setAsyncQueryStatusSequence(String queryId, List<QueryStatus> sequence) {
        stateFor(queryId).statusSequence = new ArrayList<>(sequence);
    }

    /** Configures the error reported once queryId's status reaches FAILED. */
    void setAsyncQueryError(String queryId, String errorCode, String message) {
        AsyncQueryState state = stateFor(queryId);
        state.errorCode = errorCode;
        state.errorMessage = message;
    }

    /**
     * Configures the result chunks (pre-serialized Arrow IPC streams, one per
     * chunk index) {@code GetAsyncQueryResult} returns for queryId, and the
     * total_row_count the SUCCEEDED status response reports.
     */
    void setAsyncQueryChunks(String queryId, List<byte[]> chunks, long totalRowCount) {
        AsyncQueryState state = stateFor(queryId);
        state.chunks = new ArrayList<>(chunks);
        state.totalRowCount = totalRowCount;
    }

    private AsyncQueryState stateFor(String queryId) {
        return asyncQueries.computeIfAbsent(queryId, id -> new AsyncQueryState());
    }

    /** Mutable server-side state for one async query, configured by tests via the setters above. */
    private static final class AsyncQueryState {
        volatile List<QueryStatus> statusSequence = new ArrayList<>(Collections.singletonList(QueryStatus.SUCCEEDED));
        final AtomicInteger statusPollIndex = new AtomicInteger(-1);
        volatile String errorCode;
        volatile String errorMessage;
        volatile List<byte[]> chunks = Collections.emptyList();
        volatile long totalRowCount;
    }

    /**
     * PEM file as an in-memory stream: the FlightServer builder defers reading
     * its TLS streams until build(), so file streams would be closed by then.
     */
    private static InputStream pemStream(Path pem) throws IOException {
        return new ByteArrayInputStream(Files.readAllBytes(pem));
    }

    long expectedTotalRows() {
        return (long) endpointCount * batchesPerEndpoint * rowsPerBatch;
    }

    @Override
    public void close() throws Exception {
        server.shutdown();
        server.awaitTermination();
        allocator.close();
    }

    private void maybeInjectGetFlightInfoFailure() {
        if (getFlightInfoDelayMs > 0) {
            try {
                Thread.sleep(getFlightInfoDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        int remaining = failNextGetFlightInfo.get();
        while (remaining > 0) {
            if (failNextGetFlightInfo.compareAndSet(remaining, remaining - 1)) {
                throw injectedFailureStatus.withDescription("injected failure").toRuntimeException();
            }
            remaining = failNextGetFlightInfo.get();
        }
    }

    private List<FlightEndpoint> buildEndpoints(java.util.function.Function<Integer, Ticket> ticketForEndpoint) {
        List<FlightEndpoint> endpoints = new ArrayList<>(endpointCount);
        for (int i = 0; i < endpointCount; i++) {
            endpoints.add(new FlightEndpoint(ticketForEndpoint.apply(i)));
        }
        return endpoints;
    }

    private void serveData(int endpointIndex, org.apache.arrow.flight.FlightProducer.ServerStreamListener listener) {
        doGetCalls.incrementAndGet();
        try (BufferAllocator streamAllocator = allocator.newChildAllocator("doget", 0, Long.MAX_VALUE);
                VectorSchemaRoot root = VectorSchemaRoot.create(RESULT_SCHEMA, streamAllocator)) {
            listener.start(root);
            BigIntVector idVector = (BigIntVector) root.getVector("id");
            VarCharVector nameVector = (VarCharVector) root.getVector("name");
            for (int batch = 0; batch < batchesPerEndpoint; batch++) {
                root.allocateNew();
                for (int row = 0; row < rowsPerBatch; row++) {
                    long id = endpointIndex * 1_000_000L + batch * 1_000L + row;
                    idVector.setSafe(row, id);
                    nameVector.setSafe(row, ("row-" + id).getBytes(StandardCharsets.UTF_8));
                }
                root.setRowCount(rowsPerBatch);
                listener.putNext();
            }
            listener.completed();
        }
    }

    private final class Producer extends NoOpFlightSqlProducer {

        @Override
        public void createPreparedStatement(FlightSql.ActionCreatePreparedStatementRequest request,
                CallContext context, StreamListener<Result> listener) {
            createPreparedStatementCalls.incrementAndGet();
            String handle = "ps-" + handleCounter.incrementAndGet();
            validHandles.add(handle);
            FlightSql.ActionCreatePreparedStatementResult result = FlightSql.ActionCreatePreparedStatementResult
                    .newBuilder()
                    .setPreparedStatementHandle(ByteString.copyFromUtf8(handle))
                    // The dataset schema tells clients this statement is a query.
                    // JDBC (Avatica) clients treat an empty dataset schema as an
                    // UPDATE and would route execution to DoPut(...Update).
                    .setDatasetSchema(ByteString.copyFrom(RESULT_SCHEMA.serializeAsMessage()))
                    .setParameterSchema(ByteString.copyFrom(parameterSchemaFor(request.getQuery())
                            .serializeAsMessage()))
                    .build();
            listener.onNext(new Result(Any.pack(result).toByteArray()));
            listener.onCompleted();
        }

        @Override
        public void closePreparedStatement(FlightSql.ActionClosePreparedStatementRequest request,
                CallContext context, StreamListener<Result> listener) {
            closePreparedStatementCalls.incrementAndGet();
            validHandles.remove(request.getPreparedStatementHandle().toStringUtf8());
            listener.onCompleted();
        }

        @Override
        public Runnable acceptPutPreparedStatementQuery(FlightSql.CommandPreparedStatementQuery command,
                CallContext context, FlightStream flightStream, StreamListener<PutResult> ackStream) {
            return () -> {
                doPutParameterCalls.incrementAndGet();
                String handle = command.getPreparedStatementHandle().toStringUtf8();
                if (!validHandles.contains(handle)) {
                    ackStream.onError(CallStatus.NOT_FOUND
                            .withDescription("unknown prepared statement handle").toRuntimeException());
                    return;
                }
                List<Object> captured = new ArrayList<>();
                while (flightStream.next()) {
                    VectorSchemaRoot root = flightStream.getRoot();
                    if (root.getRowCount() > 0) {
                        captured.clear();
                        for (FieldVector vector : root.getFieldVectors()) {
                            captured.add(vector.getObject(0));
                        }
                    }
                }
                lastBoundParameters = captured;
                FlightSql.DoPutPreparedStatementResult metadata = FlightSql.DoPutPreparedStatementResult.newBuilder()
                        .setPreparedStatementHandle(command.getPreparedStatementHandle())
                        .build();
                byte[] bytes = metadata.toByteArray();
                try (ArrowBuf buffer = allocator.buffer(bytes.length)) {
                    buffer.writeBytes(bytes);
                    ackStream.onNext(PutResult.metadata(buffer));
                }
                ackStream.onCompleted();
            };
        }

        @Override
        public FlightInfo getFlightInfoPreparedStatement(FlightSql.CommandPreparedStatementQuery command,
                CallContext context, FlightDescriptor descriptor) {
            getFlightInfoCalls.incrementAndGet();
            maybeInjectGetFlightInfoFailure();
            String handle = command.getPreparedStatementHandle().toStringUtf8();
            if (!validHandles.contains(handle)) {
                throw CallStatus.NOT_FOUND.withDescription("unknown prepared statement handle").toRuntimeException();
            }
            List<FlightEndpoint> endpoints = buildEndpoints(i -> new Ticket(
                    Any.pack(FlightSql.CommandPreparedStatementQuery.newBuilder()
                            .setPreparedStatementHandle(ByteString.copyFromUtf8(handle + "#" + i))
                            .build()).toByteArray()));
            return new FlightInfo(RESULT_SCHEMA, descriptor, endpoints, -1, -1);
        }

        @Override
        public void getStreamPreparedStatement(FlightSql.CommandPreparedStatementQuery command,
                CallContext context, ServerStreamListener listener) {
            serveData(parseEndpointIndex(command.getPreparedStatementHandle().toStringUtf8()), listener);
        }

        @Override
        public FlightInfo getFlightInfoStatement(FlightSql.CommandStatementQuery command,
                CallContext context, FlightDescriptor descriptor) {
            getFlightInfoCalls.incrementAndGet();
            maybeInjectGetFlightInfoFailure();
            List<FlightEndpoint> endpoints = buildEndpoints(i -> new Ticket(
                    Any.pack(FlightSql.TicketStatementQuery.newBuilder()
                            .setStatementHandle(ByteString.copyFromUtf8("q#" + i))
                            .build()).toByteArray()));
            return new FlightInfo(RESULT_SCHEMA, descriptor, endpoints, -1, -1);
        }

        @Override
        public void getStreamStatement(FlightSql.TicketStatementQuery ticket,
                CallContext context, ServerStreamListener listener) {
            serveData(parseEndpointIndex(ticket.getStatementHandle().toStringUtf8()), listener);
        }

        @Override
        public void doAction(CallContext context, Action action, StreamListener<Result> listener) {
            switch (action.getType()) {
                case "SubmitAsyncQuery":
                    handleSubmitAsyncQuery(action, listener);
                    return;
                case "GetAsyncQueryStatus":
                    handleGetAsyncQueryStatus(action, listener);
                    return;
                case "GetAsyncQueryResult":
                    handleGetAsyncQueryResult(action, listener);
                    return;
                case "CancelAsyncQuery":
                    handleCancelAsyncQuery(action, listener);
                    return;
                default:
                    // Preserves NoOpFlightSqlProducer's dispatch of the built-in
                    // FlightSql actions (CreatePreparedStatement, etc.) to the
                    // methods this Producer overrides above.
                    super.doAction(context, action, listener);
            }
        }

        private void handleSubmitAsyncQuery(Action action, StreamListener<Result> listener) {
            submitAsyncQueryCalls.incrementAndGet();
            String queryId = "aq-" + asyncQueryCounter.incrementAndGet();
            AsyncQueryState state = stateFor(queryId);
            JsonObject response = new JsonObject();
            response.addProperty("query_id", queryId);
            response.addProperty("status", state.statusSequence.get(0).getWireValue());
            respondJson(listener, response);
        }

        private void handleGetAsyncQueryStatus(Action action, StreamListener<Result> listener) {
            String queryId = parseJson(action).get("query_id").getAsString();
            AsyncQueryState state = asyncQueries.get(queryId);
            if (state == null) {
                listener.onError(CallStatus.NOT_FOUND.withDescription("unknown async query: " + queryId)
                        .toRuntimeException());
                return;
            }
            List<QueryStatus> sequence = state.statusSequence;
            int idx = Math.max(0, Math.min(state.statusPollIndex.incrementAndGet(), sequence.size() - 1));
            QueryStatus status = sequence.get(idx);

            JsonObject response = new JsonObject();
            response.addProperty("query_id", queryId);
            response.addProperty("status", status.getWireValue());
            if (status == QueryStatus.FAILED && state.errorMessage != null) {
                JsonObject error = new JsonObject();
                if (state.errorCode != null) {
                    error.addProperty("error_code", state.errorCode);
                }
                error.addProperty("message", state.errorMessage);
                response.add("error", error);
            }
            if (status == QueryStatus.SUCCEEDED) {
                JsonObject result = new JsonObject();
                result.addProperty("total_row_count", state.totalRowCount);
                result.addProperty("total_chunk_count", state.chunks.size());
                response.add("result", result);
            }
            respondJson(listener, response);
        }

        private void handleGetAsyncQueryResult(Action action, StreamListener<Result> listener) {
            getAsyncQueryResultCalls.incrementAndGet();
            JsonObject request = parseJson(action);
            String queryId = request.get("query_id").getAsString();
            int chunkIndex = request.get("chunk_index").getAsInt();
            AsyncQueryState state = asyncQueries.get(queryId);
            if (state == null || chunkIndex < 0 || chunkIndex >= state.chunks.size()) {
                listener.onError(CallStatus.NOT_FOUND
                        .withDescription("no result chunk " + chunkIndex + " for async query " + queryId)
                        .toRuntimeException());
                return;
            }
            listener.onNext(new Result(state.chunks.get(chunkIndex)));
            listener.onCompleted();
        }

        private void handleCancelAsyncQuery(Action action, StreamListener<Result> listener) {
            String queryId = parseJson(action).get("query_id").getAsString();
            AsyncQueryState state = asyncQueries.get(queryId);
            if (state == null) {
                listener.onError(CallStatus.NOT_FOUND.withDescription("unknown async query: " + queryId)
                        .toRuntimeException());
                return;
            }
            state.statusSequence = Collections.singletonList(QueryStatus.CANCELLED);
            state.statusPollIndex.set(0);

            JsonObject response = new JsonObject();
            response.addProperty("query_id", queryId);
            response.addProperty("cancelled", true);
            response.addProperty("status", QueryStatus.CANCELLED.getWireValue());
            respondJson(listener, response);
        }

        private JsonObject parseJson(Action action) {
            return JsonParser.parseString(new String(action.getBody(), StandardCharsets.UTF_8)).getAsJsonObject();
        }

        private void respondJson(StreamListener<Result> listener, JsonObject body) {
            listener.onNext(new Result(ASYNC_GSON.toJson(body).getBytes(StandardCharsets.UTF_8)));
            listener.onCompleted();
        }
    }

    /**
     * Advertises one nullable BIGINT parameter per JDBC-style '?' placeholder.
     * JDBC clients build their bind roots from this schema; the native SDK uses
     * $N placeholders (no '?') and constructs its own parameter roots, so it
     * receives an empty schema here — matching real servers' optionality.
     */
    private static Schema parameterSchemaFor(String query) {
        List<Field> fields = new ArrayList<>();
        for (int i = 0; i < query.length(); i++) {
            if (query.charAt(i) == '?') {
                fields.add(Field.nullable("$" + (fields.size() + 1), new ArrowType.Int(64, true)));
            }
        }
        return new Schema(fields);
    }

    /**
     * Extracts the endpoint index from a "handle#index" ticket, converting any
     * malformed input into a protocol-appropriate INVALID_ARGUMENT instead of
     * an uncaught NumberFormatException.
     */
    private static int parseEndpointIndex(String handle) {
        int separator = handle.lastIndexOf('#');
        if (separator < 0 || separator == handle.length() - 1) {
            throw CallStatus.INVALID_ARGUMENT
                    .withDescription("malformed ticket handle: " + handle).toRuntimeException();
        }
        try {
            return Integer.parseInt(handle.substring(separator + 1));
        } catch (NumberFormatException e) {
            throw CallStatus.INVALID_ARGUMENT
                    .withDescription("malformed ticket handle: " + handle).toRuntimeException();
        }
    }
}
