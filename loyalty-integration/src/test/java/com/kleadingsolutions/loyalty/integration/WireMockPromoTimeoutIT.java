package com.kleadingsolutions.loyalty.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.kleadingsolutions.loyalty.MainVerticle;
import com.kleadingsolutions.loyalty.client.FxClient;
import com.kleadingsolutions.loyalty.client.PromoClient;
import com.kleadingsolutions.loyalty.client.impl.VertxFxClient;
import com.kleadingsolutions.loyalty.client.impl.VertxPromoClient;
import com.kleadingsolutions.loyalty.dto.QuoteRequest;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/*
 Integration test that verifies the service handles a slow FX service (timeout) gracefully.
 Notes:
 - MainVerticle reads system properties: http.port. We set it here before
   constructing/deploying the MainVerticle so the verticle will actually call WireMock.
 - VertxPromoClient uses a per-request timeout of 1000 ms, so WireMock delay must be >1000 ms.
*/
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class WireMockPromoTimeoutIT {

  private Vertx vertx;
  private WebClient client;
  private WireMockServer wireMock;
  private ObjectMapper mapper = new ObjectMapper();

  @BeforeAll
  void beforeAll() {
    vertx = Vertx.vertx();
    client = WebClient.create(vertx);
    wireMock = new WireMockServer(0);
    wireMock.start();
  }

  @AfterAll
  void afterAll() throws Exception {
    if (wireMock != null) wireMock.stop();
    if (vertx != null) vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @BeforeEach
  void clearProps() {
    System.clearProperty("http.actual.port");
  }

  @Test
  public void promo_timeout_is_handled_as_fail_open_and_warning_emitted() throws Exception {
    wireMock.stubFor(get(urlPathEqualTo("/fx/rate"))
            .withQueryParam("currency", equalTo("USD"))
            .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{ \"rate\": 3.67 }")
            ));

    wireMock.stubFor(get(urlPathMatching("/promo/.*"))
            .willReturn(aResponse()
                    .withFixedDelay(2000) // 2s delay; VertxPromoClient timeout is 1s
                    .withHeader("Content-Type", "application/json")
                    .withBody("{ \"code\":\"SUMMER25\", \"percent\":0.25, \"expiresInDays\":1 }")
            ));

    String baseUrl = "http://localhost:" + wireMock.port();

    FxClient fxClient = new VertxFxClient(vertx, baseUrl);
    PromoClient promoClient = new VertxPromoClient(vertx, baseUrl);

    // deploy
    System.setProperty("http.port", "0");
    CompletableFuture<String> deployed = new CompletableFuture<>();
    PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    MainVerticle verticle = new MainVerticle(fxClient, promoClient, registry);
    vertx.deployVerticle(verticle, ar -> {
      if (ar.succeeded()) deployed.complete(ar.result());
      else deployed.completeExceptionally(ar.cause());
    });
    deployed.get(5, TimeUnit.SECONDS);

    String portStr;
    int tries = 0;
    while ((portStr = System.getProperty("http.actual.port")) == null && tries++ < 100) {
      Thread.sleep(50);
    }
    assertThat(portStr).isNotNull();
    int port = Integer.parseInt(portStr);


    QuoteRequest req = new QuoteRequest();
    req.setFareAmount(100.0);
    req.setCurrency("USD");
    req.setCustomerTier("SILVER");
    req.setCabinClass("ECONOMY");
    req.setPromoCode("SUMMER25");

    String body = mapper.writeValueAsString(req);

    var response = client.post(port, "localhost", "/v1/points/quote")
            .putHeader("content-type", "application/json")
            .sendBuffer(io.vertx.core.buffer.Buffer.buffer(body))
            .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);

    assertThat(response.statusCode()).isEqualTo(200);

    String respBody = response.bodyAsString();
    JsonNode json = mapper.readTree(respBody);


    assertThat(json.get("promoBonus").asLong()).isEqualTo(0L);

    boolean hasPromoUnavailable = false;
    if (json.has("warnings") && json.get("warnings").isArray()) {
      for (JsonNode n : json.get("warnings")) {
        if ("PROMO_UNAVAILABLE".equals(n.asText())) hasPromoUnavailable = true;
      }
    }
    assertThat(hasPromoUnavailable).isTrue();
  }
}