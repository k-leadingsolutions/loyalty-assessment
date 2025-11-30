package com.kleadingsolutions.loyalty.client.impl;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VertxPromoClientMalformedResponseTest {

  private WireMockServer wireMock;
  private Vertx vertx;

  @BeforeAll
  void setup() {
    wireMock = new WireMockServer(0);
    wireMock.start();
    vertx = Vertx.vertx();
  }

  @AfterAll
  void teardown() throws Exception {
    if (wireMock != null) wireMock.stop();
    if (vertx != null) vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @Test
  public void malformedJson_throws() {
    wireMock.stubFor(get(urlPathEqualTo("/promo/SUMMER25"))
      .willReturn(aResponse()
        .withHeader("Content-Type","application/json")
        .withBody("this-is-not-json")
        .withStatus(200)));

    String baseUrl = "http://localhost:" + wireMock.port();
    VertxPromoClient client = new VertxPromoClient(vertx, baseUrl);

    assertThrows(Exception.class, () -> client.getPromo("SUMMER25").get(3, TimeUnit.SECONDS));
  }
}