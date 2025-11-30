# Loyalty Assessment

-  ✨ Why This Project?
  This repository demonstrates a small, scalable, testable Vert.x-based loyalty service and a mobile login codebase.
  It includes unit tests, integration tests (WireMock + Vert.x), and Pact consumer tests for external dependencies. 
  The goal is to show testable architecture, separation of concerns and pragmatic decisions for a small service.

Quick summary
- Multi-module Maven project with service, integration, contract tests and a mobile module (Android + iOS).
  Project layout (multi-module)
- loyalty-assessment/ (parent)
    - .github/workflows/
        - ci.yml
        - mobile-ci.yml
    - loyalty-service/         -> application code + unit tests
        - README.md
    - loyalty-integration/     -> component tests (WireMock + Vert.x)
        - README.md
    - loyalty-contracts/       -> Pact consumer tests for external services
        - README.md
    - mobile/                  -> Android & iOS mobile modules
    - README.md
- Focus: clear module separation, scalable, deterministic tests, and simple code.
- Tech: Java 17, Vert.x 4, Maven, JUnit 5, WireMock, Pact JVM, Docker, GitHub Actions, Prometheus, OpenTelemetry, OpenAPI, Android (Kotlin + Jetpack Compose), iOS (Swift + SwiftUI).

---

## CI/CD Pipeline

[![CI](https://github.com/k-leadingsolutions/loyalty-assessment/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/k-leadingsolutions/loyalty-assessment/actions/workflows/ci.yml?query=branch%3Amain)

[![Mobile tests](https://github.com/k-leadingsolutions/loyalty-assessment/actions/workflows/mobile-ci.yml/badge.svg?branch=main)](https://github.com/k-leadingsolutions/loyalty-assessment/actions/workflows/mobile-ci.yml?query=branch%3Amain)

---

Build & test (local)
- Run all tests (unit + integration + contracts):
```sh
mvn -B -V -e clean verify
```

- Run unit tests only (service module):
```sh
mvn -am -pl loyalty-service test
```

- Run integration tests only:
```sh
mvn -am -pl loyalty-integration test
```

- Run contract tests only:
```sh
mvn  -pl loyalty-contracts test
```

Dev: run the service locally (class files + dependencies)
This is useful for quick iterative development without Docker.

1) Build module and copy runtime dependencies into target/dependency:
```sh
mvn -pl loyalty-service -am -DskipTests package dependency:copy-dependencies -DoutputDirectory=loyalty-service/target/dependency
```

2) Run with Vert.x Launcher on classpath:
```sh
java -Dhttp.port=8888 \
  -cp "loyalty-service/target/loyalty-service-0.1.0-SNAPSHOT.jar:loyalty-service/target/dependency/*" \
  io.vertx.core.Launcher run com.kleadingsolutions.loyalty.MainVerticle
```

Alternatively run from compiled classes (faster iteration):
```sh
mvn -pl loyalty-service -am -DskipTests package dependency:copy-dependencies -DoutputDirectory=target/dependency
java -Dhttp.port=8888 -cp "loyalty-service/target/classes:loyalty-service/target/dependency/*" \
  io.vertx.core.Launcher run com.kleadingsolutions.loyalty.MainVerticle
```

Docker: build & run the image
1. Build (run from repository root so Dockerfile paths resolve correctly):
```sh
DOCKER_BUILDKIT=1 docker build -f loyalty-service/Dockerfile -t loyalty-service:local .
```

2. Run (foreground):
```sh
docker run --rm -p 8888:8888 --name loyalty-service-local loyalty-service:local
```

- To inspect container logs:
```sh
docker logs -f loyalty-service-local
```
- To remove the running container:
```sh
docker rm -f loyalty-service-local
```
- To remove the built image:
```sh
docker rmi loyalty-service:local
```

Health check (from host)
```sh
curl http://localhost:8888/health
```
Micrometer Prometheus Metrics endpoint
```sh
curl http://localhost:8888/metrics
```

Prometheus UI
- Open Prometheus UI at http://localhost:9090/query 
- Open Prometheus targets at http://localhost:9090/api/v1/targets

Jaeger Tracing UI
- Open Jaeger UI at http://localhost:16686/search

Quote endpoint (POST)
```sh
curl -X POST http://localhost:8888/v1/points/quote \
  -H "Content-Type: application/json" \
  -d '{"fareAmount":123.45,"currency":"USD","cabinClass":"ECONOMY","customerTier":"SILVER","promoCode":"SUMMER25"}'
```

Example response
```json
{
  "basePoints": 453,
  "tierBonus": 67,
  "promoBonus": 113,
  "totalPoints": 633,
  "effectiveFxRate": 3.67,
  "warnings": ["PROMO_EXPIRES_SOON"]
}
```

API specification (Swagger / OpenAPI)
- The OpenAPI v3 specification is available at:
- [`loyalty-service/src/main/resources/openapi.yml`](https://github.com/k-leadingsolutions/loyalty-assessment/blob/main/loyalty-service/src/main/resources/openapi.yml)


Notes
- MainVerticle supports ephemeral port (set `-Dhttp.port=0`). It sets `http.actual.port` system property after successful bind so tests can discover the port.
- VertxFxClient and VertxPromoClient timeouts are 1s in the scaffold. Integration tests simulate longer delays using WireMock's fixedDelay to trigger timeouts.
- JaCoCo branch coverage check is configured in the parent pom. The verify phase will generate reports and fail if branch coverage < 80%.
- Integration tests in loyalty-integration module run in the verify phase via the Failsafe plugin.
- Contract tests in loyalty-contracts module run in the test phase via the Surefire plugin.
- Docker is not required to run tests; WireMock runs embedded in the test JVM.


CI notes
- Backend CI
- The CI pipeline is defined in `.github/workflows/ci.yml`.
- The workflow runs unit/integration/contract tests and publishes coverage reports (JaCoCo). The parent pom enforces branch coverage gates. 
Mobile CI
- The mobile CI workflow runs Android and iOS unit tests on GitHub Actions.
- Workflow file location: `.github/workflows/mobile-ci.yml`
- Android job installs Android command-line tools and runs `./gradlew test` in mobile/android.
- iOS job runs `swift test` in ios-app (macos runner).

Future improvements
- Add authentication/authorization to the loyalty-service endpoints.
- Split modules into separate microservices if they grow in complexity.
- Move hardcoded config (e.g. external service URLs) to environment variables or config server.
- Move hardcoded values (e.g. promo codes) to a database or refactor to use enums/constants.
- Add implementation for mobile network monitoring and offline handling.
- Add implementation for i8n/localization in mobile UIs.

Contact
- keamp84@gmail.com — for questions or clarifications.
