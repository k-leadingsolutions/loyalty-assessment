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
import static org.assertj.core.api.AssertionsForClassTypes.within;

/*
 Integration test that uses WireMock to stub external FX and Promo services
 and tests the /v1/points/quote endpoint end-to-end.
*/
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class WireMockQuoteEndpointIT {

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
  public void e2e_with_wiremock_stubs() throws Exception {
    wireMock.stubFor(get(urlPathEqualTo("/fx/rate"))
            .withQueryParam("currency", equalTo("USD"))
            .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{ \"rate\": 3.67 }")
            ));

    wireMock.stubFor(get(urlPathMatching("/promo/.*"))
            .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{ \"code\":\"SUMMER25\", \"percent\":0.25, \"expiresInDays\":1 }")
            ));

    String baseUrl = "http://localhost:" + wireMock.port();


    FxClient fxClient = new VertxFxClient(vertx, baseUrl);
    PromoClient promoClient = new VertxPromoClient(vertx, baseUrl);

    System.setProperty("http.port", "0");
    CompletableFuture<String> deployed = new CompletableFuture<>();
    PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    MainVerticle verticle = new MainVerticle(fxClient, promoClient, registry);
    vertx.deployVerticle(verticle, ar -> {
      if (ar.succeeded()) deployed.complete(ar.result());
      else deployed.completeExceptionally(ar.cause());
    });
    // wait for deployment
    deployed.get(5, TimeUnit.SECONDS);

    String portStr;
    int tries = 0;
    while ((portStr = System.getProperty("http.actual.port")) == null && tries++ < 100) {
      Thread.sleep(50);
    }
    assertThat(portStr).isNotNull();
    int port = Integer.parseInt(portStr);


    QuoteRequest req = new QuoteRequest();
    req.setFareAmount(1234.50);
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
    assertThat(json.has("basePoints")).isTrue();
    assertThat(json.has("promoBonus")).isTrue();
    assertThat(json.get("effectiveFxRate").asDouble()).isEqualTo(3.67d, within(0.0001));

   // calculate expected values
    long expectedBase = (long)Math.floor(1234.50 * 3.67);
    long expectedTier = (long)Math.floor(expectedBase * 0.15);
    long expectedPromo = (long)Math.floor(expectedBase * 0.25);
    long expectedTotal = expectedBase + expectedTier + expectedPromo;
    if (expectedTotal > 50_000L) expectedTotal = 50_000L;

    assertThat(json.get("basePoints").asLong()).isEqualTo(expectedBase);
    assertThat(json.get("tierBonus").asLong()).isEqualTo(expectedTier);
    assertThat(json.get("promoBonus").asLong()).isEqualTo(expectedPromo);
    assertThat(json.get("totalPoints").asLong()).isEqualTo(expectedTotal);
  }
}