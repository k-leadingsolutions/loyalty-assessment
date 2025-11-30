package com.kleadingsolutions.loyalty.api;

import com.kleadingsolutions.loyalty.client.FxClient;
import com.kleadingsolutions.loyalty.client.PromoClient;
import com.kleadingsolutions.loyalty.dto.Promo;
import com.kleadingsolutions.loyalty.dto.QuoteRequest;
import com.kleadingsolutions.loyalty.dto.QuoteResponse;
import com.kleadingsolutions.loyalty.service.PointsCalculatorService;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.mockito.Mockito.*;

/**
 * Tests targeting try/catch branches inside QuoteHandler:
    * - promoFuture.handle() throwing exception
 * - serialization failure inside inner block
 */
class QuoteHandlerTryCatchTests {

  @Mock RoutingContext ctx;
  @Mock HttpServerResponse response;
  @Mock FxClient fxClient;
  @Mock PromoClient promoClient;
  @Mock RequestBody requestBody;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    doReturn(response).when(ctx).response();
    doReturn(response).when(response).putHeader(anyString(), anyString());
    doReturn(response).when(response).setStatusCode(anyInt());
    doAnswer(inv -> null).when(response).end(anyString());
  }

  @AfterEach
  void tearDown() {
    Mockito.framework().clearInlineMocks();
    System.clearProperty("allowed.currencies");
  }

  private void stubBody(String json) {
    doReturn(requestBody).when(ctx).body();
    when(requestBody.asString()).thenReturn(json);
    doReturn(json).when(ctx).getBodyAsString();
  }

  @Test
  void promoFuture_handleMethodThrows_isCaught_by_outer_try_and_returns500() throws Exception {
    // FX succeeds
    when(fxClient.getEffectiveRate("USD")).thenReturn(CompletableFuture.completedFuture(2.0));

    CompletableFuture<Promo> badPromoFuture = new CompletableFuture<>() {
      @Override
      public <U> CompletableFuture<U> handle(BiFunction<? super Promo, Throwable, ? extends U> fn) {
        throw new RuntimeException("handle boom");
      }
    };
    when(promoClient.getPromo(anyString())).thenReturn(badPromoFuture);

    String reqJson = "{\"fareAmount\":50.0,\"currency\":\"USD\",\"cabinClass\":\"ECONOMY\",\"customerTier\":\"NONE\"}";
    stubBody(reqJson);

    QuoteHandler handler = new QuoteHandler(fxClient, promoClient);
    handler.handle(ctx);

    verify(response, timeout(1000)).setStatusCode(500);
    verify(response, timeout(1000)).end(anyString());
  }

  @Test
  void serializationFailure_in_inner_block_resultsIn500() throws Exception {
    when(fxClient.getEffectiveRate("USD")).thenReturn(CompletableFuture.completedFuture(2.0));
    when(promoClient.getPromo(anyString())).thenReturn(CompletableFuture.completedFuture(null));

    String reqJson = "{\"fareAmount\":50.0,\"currency\":\"USD\",\"cabinClass\":\"ECONOMY\",\"customerTier\":\"NONE\"}";
    stubBody(reqJson);

    Supplier<PointsCalculatorService> supplierWithBadResponse = () -> new PointsCalculatorService() {
      @Override
      public QuoteResponse calculate(QuoteRequest req, double fxRate, Promo promo) {
        return new QuoteResponse() {
          @Override
          public long getBasePoints() {
            throw new RuntimeException("getter boom");
          }
        };
      }
    };

    QuoteHandler handler = new QuoteHandler(fxClient, promoClient, supplierWithBadResponse);
    handler.handle(ctx);

    verify(response, timeout(1000)).setStatusCode(500);
    verify(response, timeout(1000)).end(anyString());
  }

  @Test
  void nullFxClient_throws_and_is_handled_by_outermost_catch_returning400() throws Exception {
    FxClient nullFx = null;
    PromoClient somePromo = mock(PromoClient.class);
    when(somePromo.getPromo(anyString())).thenReturn(CompletableFuture.completedFuture(null));

    String reqJson = "{\"fareAmount\":10.0,\"currency\":\"USD\",\"cabinClass\":\"ECONOMY\",\"customerTier\":\"NONE\"}";
    stubBody(reqJson);

    QuoteHandler handler = new QuoteHandler(nullFx, somePromo);
    handler.handle(ctx);

    verify(response, timeout(1000)).setStatusCode(400);
    verify(response, timeout(1000)).end(anyString());
  }
}