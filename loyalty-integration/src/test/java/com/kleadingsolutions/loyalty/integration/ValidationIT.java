package com.kleadingsolutions.loyalty.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kleadingsolutions.loyalty.MainVerticle;
import com.kleadingsolutions.loyalty.client.FxClient;
import com.kleadingsolutions.loyalty.client.PromoClient;
import com.kleadingsolutions.loyalty.client.impl.StubFxClient;
import com.kleadingsolutions.loyalty.client.impl.StubPromoClient;
import com.kleadingsolutions.loyalty.dto.QuoteRequest;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/*
 Integration test that deploys the MainVerticle with stubbed clients
 and tests the validations (fareAmount) /v1/points/quote endpoint end-to-end.
*/
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ValidationIT {

  private Vertx vertx;
  private WebClient client;
  private ObjectMapper mapper = new ObjectMapper();

  @BeforeAll
  void beforeAll() {
    vertx = Vertx.vertx();
    client = WebClient.create(vertx);
  }

  @AfterAll
  void afterAll() throws Exception {
    vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @Test
  public void fare_must_be_greater_than_zero_returns_400() throws Exception {
    FxClient fxClient = new StubFxClient();
    PromoClient promoClient = new StubPromoClient();

    System.clearProperty("http.actual.port");
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
    req.setFareAmount(0.0); // invalid
    req.setCurrency("USD");
    req.setCustomerTier("GOLD");
    req.setCabinClass("ECONOMY");

    String body = mapper.writeValueAsString(req);

    var response = client.post(port, "localhost", "/v1/points/quote")
      .putHeader("content-type", "application/json")
      .sendBuffer(io.vertx.core.buffer.Buffer.buffer(body))
      .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);

    assertThat(response.statusCode()).isEqualTo(400);
    String resp = response.bodyAsString();
    assertThat(resp).contains("fareAmount must be");
  }
}