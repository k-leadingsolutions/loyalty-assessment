package com.kleadingsolutions.loyalty.client;

import com.kleadingsolutions.loyalty.client.impl.VertxPromoClient;
import com.kleadingsolutions.loyalty.dto.Promo;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VertxPromoClientTest {

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
  public void getPromo_success() throws Exception {
    wireMock.stubFor(get(urlPathEqualTo("/promo/SUMMER25"))
      .willReturn(aResponse()
        .withHeader("Content-Type","application/json")
        .withBody("{ \"code\":\"SUMMER25\", \"percent\":0.25, \"expiresInDays\":1 }")
        .withStatus(200)));

    String baseUrl = "http://localhost:" + wireMock.port();
    VertxPromoClient client = new VertxPromoClient(vertx, baseUrl);

    Promo p = client.getPromo("SUMMER25").get(3, TimeUnit.SECONDS);
    assertThat(p).isNotNull();
    assertThat(p.getCode()).isEqualTo("SUMMER25");
    assertThat(p.getPercent()).isEqualTo(0.25d);
    assertThat(p.getExpiresInDays()).isEqualTo(1);
  }

  @Test
  public void getPromo_timeoutHandledAsFailure() {
    wireMock.stubFor(get(urlPathMatching("/promo/.*"))
      .willReturn(aResponse()
        .withFixedDelay(2000) // longer than client timeout
        .withHeader("Content-Type","application/json")
        .withBody("{ \"code\":\"SUMMER25\", \"percent\":0.25, \"expiresInDays\":1 }")
      ));

    String baseUrl = "http://localhost:" + wireMock.port();
    VertxPromoClient client = new VertxPromoClient(vertx, baseUrl);

    Assertions.assertThrows(Exception.class, () -> client.getPromo("SUMMER25").get(3, TimeUnit.SECONDS));
  }
}