# Testing

The SDK is validated by tiered test suites. Everything below runs in GitHub
Actions; every tier can also be run locally.

| Tier | What it proves | When it runs |
| ---- | -------------- | ------------ |
| Unit + in-process integration (~290 tests) | Both query paths, prepared-statement caching, retry/backoff/timeouts, auth re-handshake, reset races, multi-endpoint results, parameter types, TLS/mTLS handshakes, HikariCP/JDBC interop — against an in-process mock Flight SQL server with RPC counters and failure injection | Every PR/push, 14 jobs (3 OSes × 11 JDK/vendor combos) |
| Live-runtime integration | The same suites against a real `spiced` quickstart (taxi_trips): queries, parameterized queries, dataset refresh, health/readiness/status | Every PR/push (same jobs — the workflow starts the runtime first) |
| Chaos e2e (`ChaosE2ETest`) | Crash/restart recovery on one client (reconnect + prepared-statement re-prepare), queries during downtime recovering via retry backoff, clean failure when the runtime dies mid-stream, keep-alive detection of a frozen (SIGSTOP) runtime | Every PR/push (`e2e-chaos` job) |
| Performance (`PerfBenchmarkTest`) | Deterministic round-trip contracts (cached: 0 prepares per query; uncached: prepare+close per query), parameter-allocation bounds; latency percentiles tracked as a trend | Every PR/push (contracts); nightly trend on gh-pages with regression alerts |
| Soak (`SoakTest`) | 30 minutes of sustained mixed workload with periodic `reset()`: zero errors, zero Arrow buffer leaks (leak ⇒ `close()` throws), bounded thread growth, stable p99 across the run | Nightly |
| Quality gates | SpotBugs, Checkstyle, Maven Enforcer, OWASP dependency-check (CVSS ≥ 7 fails), japicmp API-compatibility vs the last release, CodeQL | Every PR/push |

## Running locally

```bash
# Unit + in-process integration (no runtime needed)
make test

# With a live runtime: start a quickstart first, then the same command.
# Tests gate themselves on availability — a running runtime activates the
# live-integration paths automatically.
spice init qs && cd qs && spice run   # add the taxi_trips quickstart dataset

# Chaos e2e (manages its own spiced processes; needs the spice CLI installed)
SPICE_E2E_CHAOS=1 mvn test -Dtest=ChaosE2ETest

# Soak (against a running quickstart runtime; duration in seconds)
SPICE_SOAK_SECONDS=120 mvn test -Dtest=SoakTest

# Benchmarks with JSON output for trend tooling
BENCH_JSON=/tmp/bench.json mvn test -Dtest=PerfBenchmarkTest

# API compatibility vs the last release
mvn package -DskipTests -Dgpg.skip japicmp:cmp
```

## Conventions

- Availability-gated tests probe once per class with a single retry and skip
  silently when no runtime is present — a missing runtime must never fail
  `make test` for a contributor.
- Tests that mutate shared runtime state (e.g. dataset refresh with a
  restricting `refresh_sql`) must restore that state in a `finally` block and
  wait for the async restore, so suites are rerun-safe against one runtime.
- Failure injection belongs in `TestFlightSqlServer` (per-RPC counters,
  injected statuses, handle invalidation, bearer expiry, TLS modes) so
  resilience behavior stays testable without external infrastructure.
