package com.kleadingsolutions.loyalty.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.kleadingsolutions.loyalty.client.impl.VertxFxClient;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VertxFxClientTest {

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
  public void connectionRefused_throws() {
    // Port 9 is typically closed/reserved; using it to provoke a connection error
    String baseUrl = "http://localhost:9";
    VertxFxClient client = new VertxFxClient(vertx, baseUrl);

    assertThrows(Exception.class, () -> client.getEffectiveRate("USD").get(3, TimeUnit.SECONDS));
  }

  @Test
  public void getEffectiveRate_success() throws Exception {
    wireMock.stubFor(get(urlPathEqualTo("/fx/rate"))
      .withQueryParam("currency", equalTo("USD"))
      .willReturn(aResponse()
        .withHeader("Content-Type","application/json")
        .withBody("{ \"rate\": 3.67 }")
        .withStatus(200)));

    String baseUrl = "http://localhost:" + wireMock.port();
    VertxFxClient client = new VertxFxClient(vertx, baseUrl);

    double rate = client.getEffectiveRate("USD").get(3, TimeUnit.SECONDS);
    assertThat(rate).isEqualTo(3.67d, within(0.0001));
  }

  @Test
  public void getEffectiveRate_timeoutFails() {
    wireMock.stubFor(get(urlPathEqualTo("/fx/rate"))
      .withQueryParam("currency", equalTo("USD"))
      .willReturn(aResponse()
        .withFixedDelay(2000) // slower than client timeout (1s)
        .withHeader("Content-Type","application/json")
        .withBody("{ \"rate\": 3.67 }")
      ));

    String baseUrl = "http://localhost:" + wireMock.port();
    VertxFxClient client = new VertxFxClient(vertx, baseUrl);

    assertThrows(Exception.class, () -> client.getEffectiveRate("USD").get(3, TimeUnit.SECONDS));
  }
}