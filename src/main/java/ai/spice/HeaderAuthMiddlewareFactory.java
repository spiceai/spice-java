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
        return new FlightClientMiddleware() {
            @Override
            public void onBeforeSendingHeaders(CallHeaders callHeaders) {
                authFactory.onCallStarted(callInfo).onBeforeSendingHeaders(callHeaders);
                headers.forEach(callHeaders::insert);
            }

            @Override
            public void onHeadersReceived(CallHeaders callHeaders) {
                authFactory.onCallStarted(callInfo).onHeadersReceived(callHeaders);
            }

            @Override
            public void onCallCompleted(CallStatus callStatus) {
                authFactory.onCallStarted(callInfo).onCallCompleted(callStatus);
            }
        };
    }
}