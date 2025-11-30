package com.kleadingsolutions.loyalty.client;

import com.kleadingsolutions.loyalty.client.impl.VertxFxClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ensure the VertxFxClient try/catch branch is exercised when the provider returns
 * a JSON structure that causes a type error while extracting the double value.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VertxFxClientMalformedFieldTest {

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
  public void jsonWithNonNumericRate_throws() {
    // Return a JSON where "rate" is an object instead of a number to provoke a ClassCastException
    wireMock.stubFor(get(urlPathEqualTo("/fx/rate"))
      .withQueryParam("currency", equalTo("USD"))
      .willReturn(aResponse()
        .withHeader("Content-Type","application/json")
        .withBody("{ \"rate\": { \"not\": \"a number\" } }")
        .withStatus(200)));

    String baseUrl = "http://localhost:" + wireMock.port();
    VertxFxClient client = new VertxFxClient(vertx, baseUrl);

    assertThrows(Exception.class, () -> client.getEffectiveRate("USD").get(3, TimeUnit.SECONDS));
  }

  @Test
  public void nullCurrency_isHandled() throws Exception {
    // Stub accepts any call and returns numeric rate; calling with null currency should proceed (query param empty)
    wireMock.stubFor(get(urlPathEqualTo("/fx/rate"))
      .willReturn(aResponse()
        .withHeader("Content-Type","application/json")
        .withBody("{ \"rate\": 2.5 }")
        .withStatus(200)));

    String baseUrl = "http://localhost:" + wireMock.port();
    VertxFxClient client = new VertxFxClient(vertx, baseUrl);

    // Should not throw; returns the numeric rate
    double rate = client.getEffectiveRate(null).get(3, TimeUnit.SECONDS);
    Assertions.assertEquals(2.5d, rate, 1e-6);
  }
}