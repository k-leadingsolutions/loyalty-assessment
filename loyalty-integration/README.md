# Loyalty-integration

Purpose
- Component / integration tests for the loyalty-service using WireMock + Vert.x test harness.
- These tests exercise the service end-to-end against mocked external dependencies (FX, Promo) and validate timing, error handling, and integration flows.

Where to look
- Parent README with repo-level commands and CI: ../README.md
- CI workflow for integration tests: .github/workflows/ci.yml

Requirements
- Java 17 (JDK)
- Maven (or use the included `./mvnw` wrapper)
- No Docker is required to run the tests locally (WireMock runs embedded by the test harness), but Docker/Compose can be used for full-stack runs.

Quick start — run the integration tests

- From the module root (ensure the service artifact is available first)
  If you run inside the module directory, either build/install the service beforehand:

```sh
# from repo root (build + install service)
mvn -pl loyalty-service install

# then, from loyalty-integration directory:
cd loyalty-integration
mvn -Dit.test=QuoteEndpointIT verify
```

Notes about test lifecycle
- Integration tests run in the Maven `verify` phase and typically use the Failsafe plugin.
- Tests start lightweight WireMock stubs for external HTTP dependencies and then start the Vert.x-based service (or client) under test.
- Many tests bind servers to ephemeral ports (use `-Dhttp.port=0` for the service) and publish the actual bound port to test logs or a system property so the test harness can discover it.

Common invocation patterns for faster iteration
- Run a single integration test class (from repo root, builds required modules):
```sh
mvn -am -pl loyalty-integration -Dit.test=ValidationIT#fare_must_be_greater_than_zero_returns_400 verify
```

- Run single integration test (from repo root, builds required modules):
```sh
mvn -am -pl loyalty-integration -Dit.test=QuoteEndpointIT verify
```

Configuration & environment
- Typical system properties used by tests:
  - `-Dhttp.port=0` — bind to an ephemeral HTTP port
  - `-Dwiremock.port=<port>` — override WireMock port if needed
  - `-Dhttp.actual.port` — some tests read this system property to discover the bound port (check test logs)
- If you need deterministic ports for debugging, set explicit ports and ensure no other process uses them.

Future improvements
- Add more integration tests for edge cases, error handling, and performance scenarios.
- Enhance test reporting and logging for easier debugging of failures.
- Dockerize the integration test environment for consistent CI runs.
- OpenAPI validation of responses against the spec.

Contact
- keamp84@gmail.com — for questions or clarifications.
