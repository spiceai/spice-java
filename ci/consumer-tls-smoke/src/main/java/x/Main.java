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

package x;

import ai.spice.SpiceClient;

/**
 * Outside-consumer smoke: {@code withSpiceCloud().build()} with a dummy Cloud
 * key must reach flight.spiceai.io over TLS and fail authentication. A Netty
 * 4.1/4.2 mix fails earlier with {@code NoClassDefFoundError:
 * io.netty.util.LeakPresenceDetector}.
 */
public final class Main {

    public static void main(String[] args) {
        try (SpiceClient client = SpiceClient.builder()
                .withApiKey("dummy-app|dummy-key")
                .withSpiceCloud()
                .build()) {
            System.out.println("UNEXPECTED_SUCCESS: client built without an auth error");
            System.exit(1);
        } catch (Throwable t) {
            if (isNettyMix(t)) {
                System.err.println("NETTY_MIX_FAILURE");
                t.printStackTrace(System.err);
                System.exit(2);
            }
            if (isAuthFailure(t)) {
                System.out.println("AUTH_ERROR: " + summarize(t));
                System.exit(0);
            }
            System.err.println("UNEXPECTED_FAILURE");
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static boolean isNettyMix(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof NoClassDefFoundError
                    || cur instanceof ClassNotFoundException) {
                return true;
            }
            String message = cur.getMessage();
            if (message != null && message.contains("LeakPresenceDetector")) {
                return true;
            }
        }
        return false;
    }

    static boolean isAuthFailure(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            String blob = (cur.getClass().getName() + " " + cur.getMessage()).toLowerCase();
            if (blob.contains("unauthenticated")
                    || blob.contains("invalid api key")
                    || blob.contains("api key not found")) {
                return true;
            }
        }
        return false;
    }

    private static String summarize(Throwable t) {
        StringBuilder out = new StringBuilder();
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (out.length() > 0) {
                out.append(" <- ");
            }
            out.append(cur.getClass().getName());
            if (cur.getMessage() != null) {
                out.append(": ").append(cur.getMessage());
            }
        }
        return out.toString();
    }

    private Main() {
    }
}
