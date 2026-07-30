/*
Copyright 2024-2026 The Spice.ai OSS Authors

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

package ai.spice.example;

import java.net.URI;
import java.util.List;

import ai.spice.ConnectionDetails;
import ai.spice.SpiceClient;

/**
 * Example of checking runtime health, readiness, and per-component status.
 * _JAVA_OPTIONS="--add-opens=java.base/java.nio=ALL-UNNAMED" mvn exec:java
 * -Dexec.mainClass="ai.spice.example.ExampleHealthAndStatus"
 *
 * Requires local Spice OSS running. Follow the quickstart
 * https://github.com/spiceai/spiceai?tab=readme-ov-file#%EF%B8%8F-quickstart-local-machine.
 */
public class ExampleHealthAndStatus {

    /** How long to wait for the runtime to finish loading its datasets. */
    private static final long READY_TIMEOUT_MS = 120_000;

    /** How long to wait between readiness polls. */
    private static final long POLL_INTERVAL_MS = 1_000;

    public static void main(String[] args) {
        try (SpiceClient client = SpiceClient.builder()
                .withFlightAddress(URI.create("grpc://localhost:50051"))
                .withHttpAddress(URI.create("http://localhost:8090"))
                .build()) {

            // Liveness: is the runtime process up at all?
            if (!client.isHealthy()) {
                System.out.println("Spice runtime is not healthy. Is it running?");
                return;
            }
            System.out.println("Spice runtime is healthy");

            // Readiness: the runtime becomes ready once its datasets have loaded,
            // so poll rather than assuming healthy means queryable.
            long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
            while (!client.isReady()) {
                if (System.currentTimeMillis() > deadline) {
                    System.out.println("Timed out waiting for the runtime to become ready");
                    return;
                }
                Thread.sleep(POLL_INTERVAL_MS);
            }
            System.out.println("Spice runtime is ready");

            // Per-component detail: which connection is not ready, and where is it bound?
            List<ConnectionDetails> status = client.runtimeStatus();
            System.out.println("Runtime status:");
            for (ConnectionDetails connection : status) {
                System.out.printf("  %-14s %-24s %s%n",
                        connection.getName(),
                        connection.getEndpoint(),
                        connection.getStatus());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Interrupted while waiting for the runtime");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
