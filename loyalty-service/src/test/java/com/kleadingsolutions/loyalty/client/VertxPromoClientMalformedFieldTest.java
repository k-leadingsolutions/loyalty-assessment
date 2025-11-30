package com.kleadingsolutions.loyalty.client;

import com.kleadingsolutions.loyalty.client.impl.VertxPromoClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ensure the VertxPromoClient try/catch branch is exercised when promo JSON has wrong types.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VertxPromoClientMalformedFieldTest {

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
  public void jsonWithNonNumericPercent_throws() {
    // Return a JSON where "percent" is an object (not a number) to trigger parsing/type error
    wireMock.stubFor(get(urlPathEqualTo("/promo/SUMMER25"))
      .willReturn(aResponse()
        .withHeader("Content-Type","application/json")
        .withBody("{ \"code\": \"SUMMER25\", \"percent\": { \"bad\": \"value\" }, \"expiresInDays\": 1 }")
        .withStatus(200)));

    String baseUrl = "http://localhost:" + wireMock.port();
    VertxPromoClient client = new VertxPromoClient(vertx, baseUrl);

    assertThrows(Exception.class, () -> client.getPromo("SUMMER25").get(3, TimeUnit.SECONDS));
  }

  @Test
  public void nullOrBlankPromoCode_returnsNullImmediately() throws Exception {
    String baseUrl = "http://localhost:" + wireMock.port();
    VertxPromoClient client = new VertxPromoClient(vertx, baseUrl);

    // null -> immediate completed future with null
    Assertions.assertNull(client.getPromo(null).get(1, TimeUnit.SECONDS));
    // blank -> immediate completed future with null
    Assertions.assertNull(client.getPromo("").get(1, TimeUnit.SECONDS));
  }
}