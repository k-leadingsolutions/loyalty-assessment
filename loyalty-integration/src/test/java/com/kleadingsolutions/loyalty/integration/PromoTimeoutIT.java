package com.kleadingsolutions.loyalty.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that verifies the service handles a slow promo service (timeout) gracefully.
 * Notes:
 * - MainVerticle reads system properties: promo.base.url and http.port. We set them here before
 *   constructing/deploying the MainVerticle so the verticle will actually call WireMock.
 * - VertxPromoClient uses a per-request timeout of 1000 ms, so WireMock delay must be >1000 ms.
 */
public class PromoTimeoutIT {

  private WireMockServer wireMockServer;
  private Vertx vertx;

  @AfterEach
  void tearDown() throws Exception {
    // clear any system properties we set so other tests remain deterministic
    System.clearProperty("promo.base.url");
    System.clearProperty("http.port");
    System.clearProperty("fx.base.url");

    if (vertx != null) {
      vertx.close().toCompletionStage().toCompletableFuture().get(3, TimeUnit.SECONDS);
    }
    if (wireMockServer != null && wireMockServer.isRunning()) {
      wireMockServer.stop();
    }
  }

  @Test
  void promoTimeoutShouldBeHandledGracefully() throws Exception {
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
    WireMock.configureFor(wireMockServer.port());

    // Stub promo endpoint: delay longer than VertxPromoClient's 1000ms timeout
    wireMockServer.stubFor(get(urlMatching("/promo/SUMMER25"))
            .willReturn(aResponse()
                    .withFixedDelay(2000) // 2s delay -> should trigger the client's 1s timeout
                    .withHeader("Content-Type", "application/json")
                    .withBody("{ \"code\": \"SUMMER25\", \"percent\": 0.25, \"expiresInDays\": 1 }")
            )
    );

    System.setProperty("promo.base.url", wireMockServer.baseUrl());
    System.setProperty("http.port", "0"); // ephemeral port


    // Deploy the MainVerticle AFTER setting the system properties so it picks them up
    vertx = Vertx.vertx();
    var deployFuture = vertx.deployVerticle(new com.kleadingsolutions.loyalty.MainVerticle()).toCompletionStage();
    deployFuture.toCompletableFuture().get(5, TimeUnit.SECONDS);

    // Determine actual HTTP port
    String actualPort = System.getProperty("http.actual.port");
    if (actualPort == null || actualPort.isEmpty()) {
      throw new IllegalStateException("Could not determine application HTTP port. Ensure MainVerticle sets 'http.actual.port' system property.");
    }
    int port = Integer.parseInt(actualPort);


    WebClient client = WebClient.create(vertx);

    JsonObject requestBody = new JsonObject()
            .put("fareAmount", 1234.50)
            .put("currency", "USD")
            .put("cabinClass", "ECONOMY")
            .put("customerTier", "SILVER")
            .put("promoCode", "SUMMER25");

    var response = client.post(port, "localhost", "/v1/points/quote")
            .putHeader("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(5).toMillis())
            .sendJsonObject(requestBody)
            .toCompletionStage()
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

    assertThat(response.statusCode()).isEqualTo(200);

    var body = response.bodyAsJsonObject();

    // Confirm WireMock received the request
    var serveEvents = wireMockServer.getAllServeEvents();
    assertThat(serveEvents).withFailMessage("No requests to WireMock. Did MainVerticle pick up promo.base.url?").isNotEmpty();

    // Promo timeout behaviour: Promo client should have timed out -> promoBonus should be 0
    int promoBonus = body.containsKey("promoBonus") ? body.getInteger("promoBonus") : 0;
    assertThat(promoBonus).isEqualTo(0);

    if (body.containsKey("warnings")) {
      var warnings = body.getJsonArray("warnings");
      boolean hasPromoWarning = warnings.stream()
              .map(Object::toString)
              .anyMatch(s -> s.toUpperCase().contains("PROMO"));
      assertThat(hasPromoWarning).isTrue();
    }

    assertThat(body.getInteger("basePoints")).isNotNull();
    assertThat(body.getInteger("totalPoints")).isNotNull();

    client.close();
  }
}