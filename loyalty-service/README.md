# Loyalty-service

Purpose
- Vert.x HTTP service that calculates loyalty points.
- Part of the loyalty-assessment multi-module Maven project.

Where to look
- Parent README with repo-level commands and CI: ../README.md

Quick start (dev)
1. Build module and copy runtime dependencies:
```sh
mvn -DskipTests package dependency:copy-dependencies -DoutputDirectory=target/dependency
```

2. Run from repo root locally (Vert.x Launcher):
```sh
java -Dhttp.port=8086 \
  -cp "loyalty-service/target/classes:target/dependency/*" \
  io.vertx.core.Launcher run com.kleadingsolutions.loyalty.MainVerticle
```

Health & example API
- Health: GET http://localhost:8888/health
- Quote endpoint (POST): POST http://localhost:8888/v1/points/quote
  Example payload:
  {"fareAmount":123.45,"currency":"USD","cabinClass":"ECONOMY","customerTier":"SILVER","promoCode":"SUMMER25"}

Tests
- Unit tests:
```sh
mvn  test
```

Docker
- Build from repo root:
```sh
DOCKER_BUILDKIT=1 docker build -f loyalty-service/Dockerfile -t loyalty-service:local .
```
- Run:
```sh
docker run --rm -p 8888:8888 --name loyalty-service-local loyalty-service:local
```

Configuration
- HTTP_PORT (env) / -Dhttp.port JVM property — default 8888
- OTEL_EXPORTER_OTLP_ENDPOINT — OTel collector endpoint if using tracing
- OTEL_SERVICE_NAME — service name for tracing

Troubleshooting
- ClassNotFoundException for io.vertx.core.Launcher: ensure dependency jars are on classpath (use dependency:copy-dependencies).
- Docker COPY of /build/.../target/dependency not found: ensure Dockerfile runs mvn package dependency:copy-dependencies from module dir so the path exists.
- Netty macOS DNS warning: usually benign; add io.netty:netty-resolver-dns-native-macos only if you require native DNS on macOS.

See parent README for repository-level info and CI:
../README.md

<!-- Requirements mapping for reviewers -->
## Requirements mapping
This section maps the assessment requirements to the files and tests in this module to make review quick and deterministic.

- Endpoint & API contract
  - `openapi.yml` (resources/openapi.yml) — request/response schema and examples
  - Route registration: `com.kleadingsolutions.loyalty.MainVerticle` (registers POST `/v1/points/quote`)

- Business logic
  - Calculation and caps: `com.kleadingsolutions.loyalty.service.PointsCalculatorService`
  - Request DTO/Response DTO: `com.kleadingsolutions.loyalty.dto.QuoteRequest`, `QuoteResponse`, `Promo`

- Clients / Resilience
  - FX client interface: `com.kleadingsolutions.loyalty.client.FxClient`
  - Promo client interface: `com.kleadingsolutions.loyalty.client.PromoClient`
  - Vert.x implementations (timeouts/retries): `com.kleadingsolutions.loyalty.client.impl.VertxFxClient`, `VertxPromoClient`
  - Deterministic stubs for tests: `com.kleadingsolutions.loyalty.client.impl.StubFxClient`, `StubPromoClient`

- Request handling, validation & error paths
  - `com.kleadingsolutions.loyalty.api.QuoteHandler` — validation, orchestration, error handling, metrics.

- Tests & stubbing
  - Unit tests for handler and clients: `src/test/java/com/kleadingsolutions/loyalty/api/QuoteHandlerTest.java`, `.../client/*Test.java`
  - Integration tests (random port + WireMock): `loyalty-integration/src/test/java/com/kleadingsolutions/loyalty/integration/*IT.java`

- Observability
  - Micrometer metrics and `/metrics` endpoint in `MainVerticle` (Prometheus registry wiring in tests)

Contact
- keamp84@gmail.com — for questions or clarifications.
