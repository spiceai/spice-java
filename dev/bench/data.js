window.BENCHMARK_DATA = {
  "lastUpdate": 1787559549555,
  "repoUrl": "https://github.com/spiceai/spice-java",
  "entries": {
    "spice-java in-process benchmarks": [
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1785574244662,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2670,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2923,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 1118,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1785660690522,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2720,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 3082,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 1122,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1785748355652,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 1984,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2194,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 804,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1785834079918,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2551,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2769,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 1080,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1785920415226,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2503,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 1977,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 1056,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1786006818036,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2745,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2720,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 1040,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1786091281549,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2792,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2956,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 1071,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1786176964729,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2721,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2922,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 1093,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1786263403328,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2606,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2788,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 1112,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1786351005292,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2650,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2818,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 1079,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1786436681132,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2287,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2104,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 793,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1786523581393,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2744,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2681,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 1163,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1786610083424,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 1860,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2062,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 878,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1786696312161,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2728,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2660,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 1137,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1786781293610,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2742,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 3033,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 1167,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1786867732079,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2657,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2918,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 1124,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1786954623538,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2700,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2775,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 1087,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1787040744889,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2090,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2177,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 809,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Luke Kim",
            "username": "lukekim",
            "email": "80174+lukekim@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a52c89a778196b0e97576fdbfa3c3ad34f0044d0",
          "message": "docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening (#51)\n\n* docs: v0.7.0 release preparation — release notes, upgrade guide, QA hardening\n\n- Release notes now cover the full v0.6.0..v0.7.0 delta: mTLS client\n  certificates (#46) and health/readiness/status checks (#48) were never\n  released and join the perf overhaul (#50); dependency table corrected\n  against what v0.6.0 actually shipped (ADBC 0.22.0)\n- New upgrade guide: docs/upgrade_guides/v0.6.0-to-v0.7.0.md (behavior\n  changes, dependency-tree impacts, new capabilities)\n- README installation snippets bumped to 0.7.0\n- Integration-test hardening found during live-runtime QA:\n  - testRefreshWithOptionsSpiceOSS was rerun-unsafe: its refresh_sql LIMIT\n    persists in the accelerated table across runs and refreshes are async.\n    Now self-heals the precondition, polls instead of fixed sleep, and\n    restores the dataset afterward\n  - availability probes use one retry instead of zero (resilient gating,\n    still fast when no runtime is present)\n\nQA: 284 tests green twice consecutively against a live spiced quickstart\n(taxi_trips, 2.9M rows) plus mock-only runs; jar/sources/javadoc build clean.\n\n* fix: address Copilot review — test resource management and comment accuracy\n\n- FlightQueryTest: all four tests now close SpiceClient (and FlightStream)\n  via try-with-resources\n- testRefreshWithOptionsSpiceOSS: shrink/verify runs in try/finally with a\n  guaranteed restore that waits for the async refresh to land; restore\n  failures are logged, never masking the primary test failure\n- TpchIntegrationTest: probe comment updated to match withMaxRetries(1)\n\nValidated against a live spiced quickstart: FlightQueryTest green twice\nconsecutively with the dataset restored to full row count after each run;\nfull suite 284/284 + SpotBugs clean.",
          "timestamp": "2026-08-01T03:56:31Z",
          "url": "https://github.com/spiceai/spice-java/commit/a52c89a778196b0e97576fdbfa3c3ad34f0044d0"
        },
        "date": 1787127194705,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2625,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2980,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 1121,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Viktor Yershov",
            "username": "krinart",
            "email": "krinart@gmail.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "dcd719d0bc09c282dfb661fc22b0e2fb1b303ded",
          "message": "feat: add `Nsql` and `NsqlGenerateSql` for the runtime's `/v1/nsql` endpoint (#55)\n\n* feat: add Nsql and NsqlGenerateSql for the runtime's /v1/nsql endpoint\n\nText-to-SQL was reachable from other SDKs but not from Java. nsql() runs the\ngenerated query and returns the rows alongside the SQL; nsqlGenerateSql()\nstops after generation so the query can be inspected or run separately.\n\n* fix: give the authenticated NsqlTest case a real Flight endpoint\n\nwithApiKey() makes SpiceClient's constructor perform a real Flight\nhandshake, but the API-key test only stood up the HTTP mock server,\nnot a Flight server. The handshake against a dead default Flight\naddress failed unpredictably by platform (reliable on Windows CI,\nintermittent on Linux/macOS). Start a TestFlightSqlServer with\nmatching credentials for that case.\n\n* fix: address review feedback on nsql()\n\n- Reject a null/empty-body decoded response instead of letting nsql()\n  return null in violation of its contract.\n- Box sampleDataEnabled so an unset value is omitted from the request\n  body instead of always sending \"sample_data_enabled\":false.\n- Defensively copy NsqlRequest.datasets on the way in and return an\n  unmodifiable view on the way out.",
          "timestamp": "2026-08-19T22:07:54Z",
          "url": "https://github.com/spiceai/spice-java/commit/dcd719d0bc09c282dfb661fc22b0e2fb1b303ded"
        },
        "date": 1787213696132,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 1959,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2066,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 766,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Viktor Yershov",
            "username": "krinart",
            "email": "viktor@spice.ai"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "dd5a37e9475e0438b3b5aece609799de5f620c6b",
          "message": "release: prepare v0.8.0 (#57)\n\n* release: prepare v0.8.0\n\nBumps pom.xml/Version.java to 0.8.0 and adds release notes covering the\nfour additive feature PRs (#53-#56): search, Nsql/NsqlGenerateSql,\nactive-query management, and async queries.\n\njapicmp.oldVersion stays at 0.6.0 rather than bumping to 0.7.0: v0.7.0\nwas tagged and GitHub-released but its Maven Central publish never\ncompleted, so it isn't a resolvable dependency. See the pom.xml comment\nfor detail.\n\n* docs: reflect that #54 (search) and #55 (nsql) have merged\n\n* docs: scope v0.8.0 to search + nsql only\n\n#53 (active queries) and #56 (async queries) will not merge for this\nrelease; remove them from the release notes' What's New and reflect\nthat in Release status instead of listing them as still-pending\ndependencies.\n\n* Update release notes for v0.8.0\n\nRemoved release status section and notes on features not included in v0.8.0.\n\n* Update release notes for v0.8.0\n\nRemoved testing section from release notes for v0.8.0.\n\n* Update release notes for v0.8.0\n\nRemoved highlights section from release notes for v0.8.0.\n\n* Change japicmp.oldVersion from 0.6.0 to 0.7.0\n\nUpdated the old version for japicmp to 0.7.0.",
          "timestamp": "2026-08-20T16:56:05Z",
          "url": "https://github.com/spiceai/spice-java/commit/dd5a37e9475e0438b3b5aece609799de5f620c6b"
        },
        "date": 1787300154697,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2856,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2923,
            "unit": "us"
          },
          {
            "name": "query() p50",
            "value": 1072,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Viktor Yershov",
            "username": "krinart",
            "email": "viktor@spice.ai"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "215eae25492d23f995a793d0aa5d24868bd32280",
          "message": "feat!: rename query/queryWithParams to sql/sqlWithParams; query becomes async (#58)\n\n* docs: add active query management and async queries to v0.8.0 notes\n\n#53 (listActiveQueries/cancelActiveQuery) has merged to trunk since\nv0.8.0's release notes were first written, and #56 (queryAsync/\nqueryAsyncWithParams) is expected to merge before release. Both are\npurely additive, so the compatibility statement is unchanged.\n\n* Update release notes for v0.8.0\n\nAdded documentation for asynchronous query execution and active query management features.\n\n* docs: address review comments on v0.8.0 release notes\n\n- Move the AsyncQuery-handle paragraph (status/waitForCompletion/cancel)\n  back under Async Queries; it was left stranded under Active Query\n  Management by a later reordering of the two sections.\n- Reword \"Public API unchanged\" to \"Additive, backward-compatible\n  change\" -- new public methods were added, so the API did change,\n  just without breaking anything existing.\n\n* feat!: rename query/queryWithParams to sql/sqlWithParams; queryAsync becomes query\n\nMatches the breaking-rename pattern already shipped in the dotnet, js,\nand python SDKs: query()/queryWithParams() now submit SQL for\nasynchronous execution and return an AsyncQuery handle, and the\nprevious synchronous, streaming behavior moves to new sql()/\nsqlWithParams() methods.\n\n- SpiceClient.query(String) -> SpiceClient.sql(String)\n- SpiceClient.queryWithParams(String, Object...) -> SpiceClient.sqlWithParams(String, Object...)\n- SpiceClient.queryAsync(String) -> SpiceClient.query(String)\n- SpiceClient.queryAsyncWithParams(String, Object...) -> SpiceClient.queryWithParams(String, Object...)\n\nUpdates every call site and cross-reference in src/main, src/test,\nREADME.md, and docs/parameterized_queries.md. Historical release notes\n(v0.5.0.md, v0.6.0.md) are left untouched since they accurately\ndocument what those versions actually shipped at the time.\n\nFull test suite passes unchanged in behavior -- this is a pure rename,\nno logic changes.\n\n* docs: document query/queryWithParams -> sql/sqlWithParams as breaking\n\nReplaces the \"Additive, backward-compatible change\" compatibility\nstatement, which the rename in the previous commit made false, with an\nactual Breaking Changes section describing it: query()/queryWithParams()\nnow submit for asynchronous execution and return an AsyncQuery handle;\nthe previous synchronous, streaming behavior moved to sql()/\nsqlWithParams(). Also fixes two now-stale sync-path cross-references\n(Nsql and Async Queries sections) that still said query()/\nqueryWithParams() where they meant the new sql()/sqlWithParams().\n\n* build: excuse query/queryWithParams from the japicmp compatibility gate\n\nThe gate correctly caught the intentional breaking change: query()'s\nreturn type changed from FlightStream to AsyncQuery, and\nqueryWithParams()'s from ArrowReader to AsyncQuery, since both now\nsubmit for asynchronous execution instead of streaming results\ndirectly. Documented, scoped exclusions for exactly these two methods\nkeep the gate meaningful for catching any other, unintended breaking\nchange in this or a future release.\n\nVerified locally with the exact CI command (mvn checkstyle:check\njapicmp:cmp) -- BUILD SUCCESS, and the generated report confirms\nSpiceClient is otherwise fully binary- and source-compatible with the\npublished 0.7.0.",
          "timestamp": "2026-08-22T00:29:10Z",
          "url": "https://github.com/spiceai/spice-java/commit/215eae25492d23f995a793d0aa5d24868bd32280"
        },
        "date": 1787386134641,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2008,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2100,
            "unit": "us"
          },
          {
            "name": "sql() p50",
            "value": 845,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Viktor Yershov",
            "username": "krinart",
            "email": "viktor@spice.ai"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "215eae25492d23f995a793d0aa5d24868bd32280",
          "message": "feat!: rename query/queryWithParams to sql/sqlWithParams; query becomes async (#58)\n\n* docs: add active query management and async queries to v0.8.0 notes\n\n#53 (listActiveQueries/cancelActiveQuery) has merged to trunk since\nv0.8.0's release notes were first written, and #56 (queryAsync/\nqueryAsyncWithParams) is expected to merge before release. Both are\npurely additive, so the compatibility statement is unchanged.\n\n* Update release notes for v0.8.0\n\nAdded documentation for asynchronous query execution and active query management features.\n\n* docs: address review comments on v0.8.0 release notes\n\n- Move the AsyncQuery-handle paragraph (status/waitForCompletion/cancel)\n  back under Async Queries; it was left stranded under Active Query\n  Management by a later reordering of the two sections.\n- Reword \"Public API unchanged\" to \"Additive, backward-compatible\n  change\" -- new public methods were added, so the API did change,\n  just without breaking anything existing.\n\n* feat!: rename query/queryWithParams to sql/sqlWithParams; queryAsync becomes query\n\nMatches the breaking-rename pattern already shipped in the dotnet, js,\nand python SDKs: query()/queryWithParams() now submit SQL for\nasynchronous execution and return an AsyncQuery handle, and the\nprevious synchronous, streaming behavior moves to new sql()/\nsqlWithParams() methods.\n\n- SpiceClient.query(String) -> SpiceClient.sql(String)\n- SpiceClient.queryWithParams(String, Object...) -> SpiceClient.sqlWithParams(String, Object...)\n- SpiceClient.queryAsync(String) -> SpiceClient.query(String)\n- SpiceClient.queryAsyncWithParams(String, Object...) -> SpiceClient.queryWithParams(String, Object...)\n\nUpdates every call site and cross-reference in src/main, src/test,\nREADME.md, and docs/parameterized_queries.md. Historical release notes\n(v0.5.0.md, v0.6.0.md) are left untouched since they accurately\ndocument what those versions actually shipped at the time.\n\nFull test suite passes unchanged in behavior -- this is a pure rename,\nno logic changes.\n\n* docs: document query/queryWithParams -> sql/sqlWithParams as breaking\n\nReplaces the \"Additive, backward-compatible change\" compatibility\nstatement, which the rename in the previous commit made false, with an\nactual Breaking Changes section describing it: query()/queryWithParams()\nnow submit for asynchronous execution and return an AsyncQuery handle;\nthe previous synchronous, streaming behavior moved to sql()/\nsqlWithParams(). Also fixes two now-stale sync-path cross-references\n(Nsql and Async Queries sections) that still said query()/\nqueryWithParams() where they meant the new sql()/sqlWithParams().\n\n* build: excuse query/queryWithParams from the japicmp compatibility gate\n\nThe gate correctly caught the intentional breaking change: query()'s\nreturn type changed from FlightStream to AsyncQuery, and\nqueryWithParams()'s from ArrowReader to AsyncQuery, since both now\nsubmit for asynchronous execution instead of streaming results\ndirectly. Documented, scoped exclusions for exactly these two methods\nkeep the gate meaningful for catching any other, unintended breaking\nchange in this or a future release.\n\nVerified locally with the exact CI command (mvn checkstyle:check\njapicmp:cmp) -- BUILD SUCCESS, and the generated report confirms\nSpiceClient is otherwise fully binary- and source-compatible with the\npublished 0.7.0.",
          "timestamp": "2026-08-22T00:29:10Z",
          "url": "https://github.com/spiceai/spice-java/commit/215eae25492d23f995a793d0aa5d24868bd32280"
        },
        "date": 1787472579946,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2334,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 2603,
            "unit": "us"
          },
          {
            "name": "sql() p50",
            "value": 927,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Viktor Yershov",
            "username": "krinart",
            "email": "viktor@spice.ai"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "215eae25492d23f995a793d0aa5d24868bd32280",
          "message": "feat!: rename query/queryWithParams to sql/sqlWithParams; query becomes async (#58)\n\n* docs: add active query management and async queries to v0.8.0 notes\n\n#53 (listActiveQueries/cancelActiveQuery) has merged to trunk since\nv0.8.0's release notes were first written, and #56 (queryAsync/\nqueryAsyncWithParams) is expected to merge before release. Both are\npurely additive, so the compatibility statement is unchanged.\n\n* Update release notes for v0.8.0\n\nAdded documentation for asynchronous query execution and active query management features.\n\n* docs: address review comments on v0.8.0 release notes\n\n- Move the AsyncQuery-handle paragraph (status/waitForCompletion/cancel)\n  back under Async Queries; it was left stranded under Active Query\n  Management by a later reordering of the two sections.\n- Reword \"Public API unchanged\" to \"Additive, backward-compatible\n  change\" -- new public methods were added, so the API did change,\n  just without breaking anything existing.\n\n* feat!: rename query/queryWithParams to sql/sqlWithParams; queryAsync becomes query\n\nMatches the breaking-rename pattern already shipped in the dotnet, js,\nand python SDKs: query()/queryWithParams() now submit SQL for\nasynchronous execution and return an AsyncQuery handle, and the\nprevious synchronous, streaming behavior moves to new sql()/\nsqlWithParams() methods.\n\n- SpiceClient.query(String) -> SpiceClient.sql(String)\n- SpiceClient.queryWithParams(String, Object...) -> SpiceClient.sqlWithParams(String, Object...)\n- SpiceClient.queryAsync(String) -> SpiceClient.query(String)\n- SpiceClient.queryAsyncWithParams(String, Object...) -> SpiceClient.queryWithParams(String, Object...)\n\nUpdates every call site and cross-reference in src/main, src/test,\nREADME.md, and docs/parameterized_queries.md. Historical release notes\n(v0.5.0.md, v0.6.0.md) are left untouched since they accurately\ndocument what those versions actually shipped at the time.\n\nFull test suite passes unchanged in behavior -- this is a pure rename,\nno logic changes.\n\n* docs: document query/queryWithParams -> sql/sqlWithParams as breaking\n\nReplaces the \"Additive, backward-compatible change\" compatibility\nstatement, which the rename in the previous commit made false, with an\nactual Breaking Changes section describing it: query()/queryWithParams()\nnow submit for asynchronous execution and return an AsyncQuery handle;\nthe previous synchronous, streaming behavior moved to sql()/\nsqlWithParams(). Also fixes two now-stale sync-path cross-references\n(Nsql and Async Queries sections) that still said query()/\nqueryWithParams() where they meant the new sql()/sqlWithParams().\n\n* build: excuse query/queryWithParams from the japicmp compatibility gate\n\nThe gate correctly caught the intentional breaking change: query()'s\nreturn type changed from FlightStream to AsyncQuery, and\nqueryWithParams()'s from ArrowReader to AsyncQuery, since both now\nsubmit for asynchronous execution instead of streaming results\ndirectly. Documented, scoped exclusions for exactly these two methods\nkeep the gate meaningful for catching any other, unintended breaking\nchange in this or a future release.\n\nVerified locally with the exact CI command (mvn checkstyle:check\njapicmp:cmp) -- BUILD SUCCESS, and the generated report confirms\nSpiceClient is otherwise fully binary- and source-compatible with the\npublished 0.7.0.",
          "timestamp": "2026-08-22T00:29:10Z",
          "url": "https://github.com/spiceai/spice-java/commit/215eae25492d23f995a793d0aa5d24868bd32280"
        },
        "date": 1787559548744,
        "tool": "customSmallerIsBetter",
        "benches": [
          {
            "name": "queryWithParams cached p50",
            "value": 2273,
            "unit": "us"
          },
          {
            "name": "queryWithParams uncached p50",
            "value": 1998,
            "unit": "us"
          },
          {
            "name": "sql() p50",
            "value": 744,
            "unit": "us"
          },
          {
            "name": "param-root bytes per 100 binds",
            "value": 3490,
            "unit": "bytes"
          }
        ]
      }
    ]
  }
}