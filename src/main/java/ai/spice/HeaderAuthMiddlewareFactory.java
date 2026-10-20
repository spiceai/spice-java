package ai.spice;

import java.util.Map;

import org.apache.arrow.flight.CallHeaders;
import org.apache.arrow.flight.CallInfo;
import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.FlightClientMiddleware;
import org.apache.arrow.flight.FlightClientMiddleware.Factory;
import org.apache.arrow.flight.auth2.ClientIncomingAuthHeaderMiddleware;

public class HeaderAuthMiddlewareFactory implements Factory {
    private final ClientIncomingAuthHeaderMiddleware.Factory authFactory;
    private final Map<String, String> headers;

    public HeaderAuthMiddlewareFactory(ClientIncomingAuthHeaderMiddleware.Factory authFactory,
            Map<String, String> headers) {
        this.authFactory = authFactory;
        this.headers = headers;
    }

    @Override
    public FlightClientMiddleware onCallStarted(CallInfo callInfo) {
        // Create the auth middleware once per RPC, not once per callback. A null
        // authFactory sends only the static headers (e.g. a Bearer API key).
        final FlightClientMiddleware authMiddleware = authFactory == null ? null : authFactory.onCallStarted(callInfo);
        return new FlightClientMiddleware() {
            @Override
            public void onBeforeSendingHeaders(CallHeaders callHeaders) {
                if (authMiddleware != null) {
                    authMiddleware.onBeforeSendingHeaders(callHeaders);
                }
                headers.forEach(callHeaders::insert);
            }

            @Override
            public void onHeadersReceived(CallHeaders callHeaders) {
                if (authMiddleware != null) {
                    authMiddleware.onHeadersReceived(callHeaders);
                }
            }

            @Override
            public void onCallCompleted(CallStatus callStatus) {
                if (authMiddleware != null) {
                    authMiddleware.onCallCompleted(callStatus);
                }
            }
        };
    }
}