package com.kleadingsolutions.loyalty.api;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.vertx.ext.web.RequestBody;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class QuoteHandlerMetricsTest {

  @Mock RoutingContext ctx;
  @Mock HttpServerResponse response;
  @Mock RequestBody requestBody;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
    doReturn(response).when(ctx).response();
    doReturn(response).when(response).putHeader(anyString(), anyString());
    doReturn(response).when(response).setStatusCode(anyInt());
    doAnswer(inv -> null).when(response).end(anyString());
  }

  @AfterEach
  void teardown() {
    Mockito.framework().clearInlineMocks();
    System.clearProperty("allowed.currencies");
  }

  private void stubBody(String json) {
    doReturn(requestBody).when(ctx).body();
    when(requestBody.asString()).thenReturn(json);
    doReturn(json).when(ctx).getBodyAsString();
  }

  @Test
  void metricsCounter_increments_on_request() throws Exception {

    System.setProperty("allowed.currencies", "USD,EUR");
    PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);


    var fxClient = mock(com.kleadingsolutions.loyalty.client.FxClient.class);
    var promoClient = mock(com.kleadingsolutions.loyalty.client.PromoClient.class);
    when(fxClient.getEffectiveRate("USD")).thenReturn(CompletableFuture.completedFuture(3.0));
    when(promoClient.getPromo(anyString())).thenReturn(CompletableFuture.completedFuture(null));

    QuoteHandler handler = new QuoteHandler(fxClient, promoClient, registry);

    String reqJson = "{\"fareAmount\":100.0,\"currency\":\"USD\",\"cabinClass\":\"ECONOMY\",\"customerTier\":\"NONE\"}";
    stubBody(reqJson);

    // call handler
    handler.handle(ctx);

    // assert: counter incremented
    double count = registry.get("loyalty_quotes_requests_total").counter().count();
    assertThat(count).isEqualTo(1.0);

    String scrape = registry.scrape();
    assertThat(scrape).contains("loyalty_quotes_requests_total");
  }
}