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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Manages a real spiced process for lifecycle-level tests: launch on fixed
 * localhost ports with a caller-provided spicepod, wait for health, and
 * crash (SIGKILL), freeze (SIGSTOP)/thaw (SIGCONT), restart on the same
 * address (mirroring a crashed server replaced behind a stable VIP), or
 * destroy. The process fixture counterpart to {@link TestFlightSqlServer}.
 */
final class SpicedProcess {

    /** A spicepod with no datasets: fast startup, no external dependencies. */
    static final String DATASET_FREE_SPICEPOD = "version: v1\nkind: Spicepod\nname: chaos\n";

    private static final HttpClient HEALTH_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .build();

    final Path workspace;
    final int httpPort;
    final int flightPort;
    final int metricsPort;
    private Process process;

    private SpicedProcess(Path workspace, int httpPort, int flightPort, int metricsPort) {
        this.workspace = workspace;
        this.httpPort = httpPort;
        this.flightPort = flightPort;
        this.metricsPort = metricsPort;
    }

    /** The spiced binary from SPICED_BIN or ~/.spice/bin, or null if absent. */
    static String findBinary() {
        String env = System.getenv("SPICED_BIN");
        if (env != null && Files.isExecutable(Path.of(env))) {
            return env;
        }
        Path home = Path.of(System.getProperty("user.home"), ".spice", "bin", "spiced");
        return Files.isExecutable(home) ? home.toString() : null;
    }

    static SpicedProcess start(String spicepodYaml) throws Exception {
        // Ephemeral ports are released before spiced binds them, so a rare
        // port steal is possible; retry with fresh ports.
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            Path workspace = Files.createTempDirectory("spice-chaos");
            Files.writeString(workspace.resolve("spicepod.yaml"), spicepodYaml, StandardCharsets.UTF_8);
            SpicedProcess spiced = new SpicedProcess(workspace, freePort(), freePort(), freePort());
            try {
                spiced.launch();
                return spiced;
            } catch (Exception e) {
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    /**
     * Starts a fresh process on the same ports and workspace — clients must
     * be able to reconnect to the same address. No port retry is possible here.
     */
    SpicedProcess restart() throws Exception {
        SpicedProcess fresh = new SpicedProcess(workspace, httpPort, flightPort, metricsPort);
        fresh.launch();
        return fresh;
    }

    private void launch() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                findBinary(),
                "--http", "127.0.0.1:" + httpPort,
                "--flight", "127.0.0.1:" + flightPort,
                "--metrics", "127.0.0.1:" + metricsPort);
        builder.directory(workspace.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(workspace.resolve("spiced.log").toFile());
        process = builder.start();
        try {
            waitUntilHealthy();
        } catch (Exception startupFailure) {
            // Never orphan a half-started process the caller has no handle to.
            try {
                destroy();
            } catch (Exception cleanup) {
                startupFailure.addSuppressed(cleanup);
            }
            throw startupFailure;
        }
    }

    private void waitUntilHealthy() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://127.0.0.1:" + httpPort + "/health"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("spiced exited during startup; see "
                        + workspace.resolve("spiced.log"));
            }
            try {
                HttpResponse<String> response = HEALTH_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException | InterruptedException retry) {
                // Not up yet.
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("spiced did not become healthy within 60s");
    }

    /** SIGKILL — an abrupt crash, no graceful shutdown. */
    void kill() throws Exception {
        process.destroyForcibly();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "spiced pid " + process.pid() + " did not exit within 10s of SIGKILL");
        }
    }

    /** SIGSTOP — the process is alive but completely unresponsive. */
    void freeze() throws Exception {
        signal("STOP");
    }

    /** SIGCONT — resume a frozen process. */
    void thaw() throws Exception {
        signal("CONT");
    }

    private void signal(String name) throws Exception {
        Process kill = new ProcessBuilder("kill", "-" + name, Long.toString(process.pid())).start();
        if (!kill.waitFor(5, TimeUnit.SECONDS) || kill.exitValue() != 0) {
            throw new IllegalStateException("kill -" + name + " failed for pid " + process.pid());
        }
    }

    void destroy() throws Exception {
        if (process != null && process.isAlive()) {
            // A frozen process ignores SIGKILL delivery ordering with SIGSTOP
            // pending on some platforms; thaw first, best-effort.
            try {
                signal("CONT");
            } catch (Exception ignored) {
                // Already dead or never frozen.
            }
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
}
