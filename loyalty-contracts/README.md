# Loyalty-contracts

Purpose
- This module contains Pact consumer tests for the external services the loyalty-service calls:
  - FxProvider -> /fx/rate?currency=USD
  - PromoProvider -> /promo/{code}
- Tests exercise the Vertx-based clients (VertxFxClient / VertxPromoClient) against a Pact mock provider and generate Pact files describing the expected interactions.

What the tests do
- Start a Pact mock provider that responds with pre-defined responses.
- Run the Vertx-based client (VertxFxClient / VertxPromoClient) against the mock server.
- Verify the client correctly interprets the response.
- Generate pact files under `target/pacts/` for publication/verification by providers.

Run the tests
- From the module root:
   ```sh
  mvn -DskipTests=false test
  ```

- From the module root (recommended):
```sh
./mvnw -DskipTests=false test
```

- From the repository root (multi-module invocation):
```sh
./mvnw -pl loyalty-contracts test
```

Generated pacts
- After successful test execution, Pact files are produced at:
```
loyalty-contracts/target/pacts/
```
- Each pact file is typically named like `<consumer>-<provider>.json`.

Future improvements:
- Add more detailed interactions and edge cases for the providers.
- Integrate with a Pact Broker for automated verification by provider teams.
- Expand to include additional external services as needed.

Contact
- keamp84@gmail.com — for questions or clarifications.
