window.BENCHMARK_DATA = {
  "lastUpdate": 1786781294471,
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
      }
    ]
  }
}