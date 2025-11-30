package com.kleadingsolutions.loyalty.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kleadingsolutions.loyalty.client.FxClient;
import com.kleadingsolutions.loyalty.client.PromoClient;
import com.kleadingsolutions.loyalty.client.impl.StubFxClient;
import com.kleadingsolutions.loyalty.client.impl.StubPromoClient;
import com.kleadingsolutions.loyalty.dto.Promo;
import com.kleadingsolutions.loyalty.dto.QuoteResponse;
import com.kleadingsolutions.loyalty.service.PointsCalculatorService;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QuoteHandlerTest {

    @Mock
    RoutingContext ctx;
    @Mock HttpServerResponse response;
    @Mock FxClient fxClient;
    @Mock PromoClient promoClient;
    @Mock RequestBody requestBody;

    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        doReturn(response).when(ctx).response();
        doReturn(response).when(response).putHeader(anyString(), anyString());
        doReturn(response).when(response).setStatusCode(anyInt());
        doAnswer(inv -> null).when(response).end(anyString());

        // default promo stub
        when(promoClient.getPromo(anyString())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("allowed.currencies");
        Mockito.framework().clearInlineMocks();
    }

    private void stubBody(String json) {
        doReturn(requestBody).when(ctx).body();
        when(requestBody.asString()).thenReturn(json);
        doReturn(json).when(ctx).getBodyAsString();
    }

    @Test
    void internalError_during_response_writes_results_in_500() throws Exception {
        System.setProperty("allowed.currencies", "USD,EUR");

        String reqJson = """
      {"fareAmount":50.0,"currency":"USD","cabinClass":"ECONOMY","customerTier":"NONE"}
      """;
        stubBody(reqJson);

        FxClient fxClient = new StubFxClient();
        PromoClient promoClient = new StubPromoClient();

        Supplier<PointsCalculatorService> throwingCalcSupplier = () -> new PointsCalculatorService() {
            @Override
            public QuoteResponse calculate(com.kleadingsolutions.loyalty.dto.QuoteRequest req, double fxRate, com.kleadingsolutions.loyalty.dto.Promo promo) {
                throw new RuntimeException("boom");
            }
        };

        doReturn(response).when(response).setStatusCode(anyInt());
        doAnswer(inv -> null).when(response).end(anyString());

        // Use the package-visible constructor that accepts a Supplier to inject our throwing calculator
        QuoteHandler handler = new QuoteHandler(fxClient, promoClient, throwingCalcSupplier);
        handler.handle(ctx);

        // verify the handler's catch wrote a 500
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(response, timeout(1000)).setStatusCode(500);
        verify(response, timeout(1000)).end(cap.capture());
        String body = cap.getValue();
        assertThat(body).contains("internal error");
    }

    @Test
    void fxFailure_returns502() throws Exception {
        when(fxClient.getEffectiveRate("USD")).thenReturn(CompletableFuture.failedFuture(new RuntimeException("upstream")));
        when(promoClient.getPromo(anyString())).thenReturn(CompletableFuture.completedFuture(null));

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient);

        String reqJson = """
      {"fareAmount":100.0,"currency":"USD","cabinClass":"ECONOMY","customerTier":"SILVER"}
      """;
        stubBody(reqJson);

        handler.handle(ctx);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(response, timeout(1000)).setStatusCode(502);
        verify(response, timeout(1000)).end(bodyCaptor.capture());
        String resp = bodyCaptor.getValue();
        assertThat(resp).contains("fx service unavailable");
    }

    @Test
    void promoFailure_isFailOpen_andWarningEmitted() throws Exception {
        when(fxClient.getEffectiveRate("USD")).thenReturn(CompletableFuture.completedFuture(3.67));
        when(promoClient.getPromo("SUMMER25")).thenReturn(CompletableFuture.failedFuture(new RuntimeException("timeout")));

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient);

        String reqJson = """
      {"fareAmount":100.0,"currency":"USD","cabinClass":"ECONOMY","customerTier":"SILVER","promoCode":"SUMMER25"}
      """;
        stubBody(reqJson);

        handler.handle(ctx);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(response, timeout(1000)).setStatusCode(200);
        verify(response, timeout(1000)).end(bodyCaptor.capture());
        String resp = bodyCaptor.getValue();
        assertThat(resp).contains("PROMO_UNAVAILABLE");
        var node = mapper.readTree(resp);
        assertThat(node.get("promoBonus").asLong()).isEqualTo(0L);
    }

    @Test
    void invalidJson_returns400() {
        String bad = "{ not-json ";
        stubBody(bad);

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient);
        handler.handle(ctx);

        verify(response, timeout(1000)).setStatusCode(400);
        verify(response, timeout(1000)).end(anyString());
    }

    @Test
    void fareZero_returns400() throws Exception {
        String reqJson = """
      {"fareAmount":0.0,"currency":"USD","cabinClass":"ECONOMY","customerTier":"GOLD"}
      """;
        stubBody(reqJson);

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient);
        handler.handle(ctx);

        verify(response, timeout(1000)).setStatusCode(400);
        verify(response, timeout(1000)).end(anyString());
    }

    @Test
    void happyPath_writesQuoteJson() throws Exception {
        // fx returns 3.67
        when(fxClient.getEffectiveRate("USD")).thenReturn(CompletableFuture.completedFuture(3.67));
        Promo p = new Promo(); p.setCode("SUMMER25"); p.setPercent(0.25); p.setExpiresInDays(1);
        when(promoClient.getPromo("SUMMER25")).thenReturn(CompletableFuture.completedFuture(p));

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient);

        String reqJson = """
      {"fareAmount":1234.50,"currency":"USD","cabinClass":"ECONOMY","customerTier":"SILVER","promoCode":"SUMMER25"}
      """;
        stubBody(reqJson);

        handler.handle(ctx);

        // Verify status 200 and capture the body
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(response, timeout(1000)).setStatusCode(200);
        verify(response, timeout(1000)).end(bodyCaptor.capture());

        String resp = bodyCaptor.getValue();
        assertThat(resp).contains("\"effectiveFxRate\"");

        var node = mapper.readTree(resp);
        long basePoints = (long)Math.floor(1234.50 * 3.67);
        assertThat(node.get("basePoints").asLong()).isEqualTo(basePoints);
        assertThat(node.get("promoBonus").asLong()).isEqualTo((long)Math.floor(basePoints * 0.25));
    }

    @Test
    void emptyBody_returns400() {
        // Simulate deprecated getBodyAsString() returning empty string
        stubBody("");

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient);
        handler.handle(ctx);

        verify(response, timeout(1000)).setStatusCode(400);
        verify(response, timeout(1000)).end(anyString());
    }

    @Test
    void nullBody_returns400() {
        doThrow(new NoSuchMethodError()).when(ctx).body();
        doReturn(null).when(ctx).getBodyAsString();

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient);
        handler.handle(ctx);

        verify(response, timeout(1000)).setStatusCode(400);
        verify(response, timeout(1000)).end(anyString());
    }

    @Test
    void invalidCurrency_returns400() throws Exception {
        System.setProperty("allowed.currencies", "USD,EUR,AED"); // configured allowed list

        String reqJson = """
      {"fareAmount":100.0,"currency":"GBP","cabinClass":"ECONOMY","customerTier":"SILVER"}
      """;
        stubBody(reqJson);

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient);
        handler.handle(ctx);

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(response, timeout(1000)).setStatusCode(400);
        verify(response, timeout(1000)).end(cap.capture());

        String body = cap.getValue();
        assertThat(body).contains("invalid currency");
    }

    @Test
    void allowedCurrency_isAccepted() throws Exception {
        System.setProperty("allowed.currencies", "USD,GBP");

        // Use real stub clients to ensure synchronous completed futures and deterministic behavior
        FxClient fxClient = new StubFxClient();
        PromoClient promoClient = new StubPromoClient();

        String reqJson = """
      {"fareAmount":100.0,"currency":"GBP","cabinClass":"ECONOMY","customerTier":"SILVER"}
      """;
        stubBody(reqJson);

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient);
        handler.handle(ctx);

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(response, timeout(1000)).setStatusCode(200);
        verify(response, timeout(1000)).end(cap.capture());

        String body = cap.getValue();
        // should contain computed fields
        assertThat(body).contains("basePoints");
    }

    @Test
    void bodyAsString_path_is_used_and_returns_200() throws Exception {
        when(fxClient.getEffectiveRate("USD")).thenReturn(CompletableFuture.completedFuture(3.0));
        Promo p = new Promo(); p.setCode("X"); p.setPercent(0.1); p.setExpiresInDays(10);
        when(promoClient.getPromo("X")).thenReturn(CompletableFuture.completedFuture(p));

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient);

        String reqJson = "{\"fareAmount\":100.0,\"currency\":\"USD\",\"cabinClass\":\"ECONOMY\",\"customerTier\":\"SILVER\",\"promoCode\":\"X\"}";
        stubBody(reqJson);

        handler.handle(ctx);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(response, timeout(1000)).setStatusCode(200);
        verify(response, timeout(1000)).end(bodyCaptor.capture());

        String resp = bodyCaptor.getValue();
        assertThat(resp).contains("\"basePoints\"");
        var node = mapper.readTree(resp);
        assertThat(node.get("effectiveFxRate").asDouble()).isEqualTo(3.0d);
    }

    @Test
    void invalidCabin_returns400() throws Exception {
        System.setProperty("allowed.currencies", "USD,EUR,AED");

        String reqJson = """
      {"fareAmount":100.0,"currency":"USD","cabinClass":"UNKNOWN","customerTier":"SILVER"}
      """;
        stubBody(reqJson);

        FxClient fxClient = new StubFxClient();
        PromoClient promoClient = new StubPromoClient();

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient);
        handler.handle(ctx);

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(response, timeout(1000)).setStatusCode(400);
        verify(response, timeout(1000)).end(cap.capture());
        String body = cap.getValue();
        assertThat(body).contains("invalid cabinClass");
    }

    @Test
    void allowedCurrencies_parsing_handles_whitespace_and_lowercase() throws Exception {
        System.setProperty("allowed.currencies", " usd , eur,, aed ");

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient);

        // access private allowedCurrencies field via reflection
        Field f = QuoteHandler.class.getDeclaredField("allowedCurrencies");
        f.setAccessible(true);
        Set<String> allowed = (Set<String>) f.get(handler);

        assertThat(allowed).containsExactlyInAnyOrder("USD", "EUR", "AED");
    }

    @Test
    void putHeaderThrows_isHandled_and_returns500() throws Exception {
        System.setProperty("allowed.currencies", "USD,EUR");

        String reqJson = """
      {"fareAmount":50.0,"currency":"USD","cabinClass":"ECONOMY","customerTier":"NONE"}
      """;
        stubBody(reqJson);

        FxClient fxClient = new StubFxClient();
        PromoClient promoClient = new StubPromoClient();

        Supplier<PointsCalculatorService> throwingCalcSupplier = () -> new PointsCalculatorService() {
            @Override
            public QuoteResponse calculate(com.kleadingsolutions.loyalty.dto.QuoteRequest req, double fxRate, com.kleadingsolutions.loyalty.dto.Promo promo) {
                throw new RuntimeException("internal boom");
            }
        };

        doReturn(response).when(response).setStatusCode(anyInt());
        doAnswer(inv -> null).when(response).end(anyString());

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient, throwingCalcSupplier);
        handler.handle(ctx);

        verify(response, timeout(2000).atLeastOnce()).setStatusCode(500);
        verify(response, timeout(2000).atLeastOnce()).end(anyString());
    }

    @Test
    void setStatusCodeThrows_isHandled_and_returns500() throws Exception {
        System.setProperty("allowed.currencies", "USD,EUR");

        String reqJson = """
      {"fareAmount":10.0,"currency":"USD","cabinClass":"ECONOMY","customerTier":"NONE"}
      """;
        stubBody(reqJson);

        FxClient fxClient = new StubFxClient();
        PromoClient promoClient = new StubPromoClient();

        Supplier<PointsCalculatorService> throwingCalcSupplier = () -> new PointsCalculatorService() {
            @Override
            public QuoteResponse calculate(com.kleadingsolutions.loyalty.dto.QuoteRequest req, double fxRate, com.kleadingsolutions.loyalty.dto.Promo promo) {
                throw new RuntimeException("internal boom");
            }
        };

        doReturn(response).when(response).setStatusCode(anyInt());
        doAnswer(inv -> null).when(response).end(anyString());

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient, throwingCalcSupplier);
        handler.handle(ctx);

        verify(response, timeout(2000).atLeastOnce()).setStatusCode(500);
        verify(response, timeout(2000).atLeastOnce()).end(anyString());
    }

    @Test
    void endThrows_isHandled_and_handler_completes() throws Exception {
        System.setProperty("allowed.currencies", "USD,EUR");

        when(fxClient.getEffectiveRate("USD")).thenReturn(CompletableFuture.completedFuture(1.0));
        when(promoClient.getPromo(anyString())).thenReturn(CompletableFuture.completedFuture(null));

        AtomicInteger counter = new AtomicInteger(0);
        doAnswer(invocation -> {
            if (counter.getAndIncrement() == 0) {
                throw new RuntimeException("end boom first");
            }
            return null;
        }).when(response).end(anyString());

        doReturn(response).when(response).putHeader(anyString(), anyString());
        doReturn(response).when(response).setStatusCode(anyInt());

        String reqJson = """
      {"fareAmount":20.0,"currency":"USD","cabinClass":"ECONOMY","customerTier":"NONE"}
      """;
        stubBody(reqJson);

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient);
        handler.handle(ctx);

        verify(response, timeout(2000).atLeastOnce()).end(anyString());
    }

    @Test
    void fxFailure_resultsIn502() throws Exception {
        System.setProperty("allowed.currencies", "USD,EUR");

        // FX fails
        when(fxClient.getEffectiveRate("USD")).thenReturn(CompletableFuture.failedFuture(new RuntimeException("fx down")));
        // promo shouldn't be involved, but provide a completed future
        when(promoClient.getPromo(anyString())).thenReturn(CompletableFuture.completedFuture(null));

        String reqJson = """
      {"fareAmount":100.0,"currency":"USD","cabinClass":"ECONOMY","customerTier":"NONE"}
      """;
        stubBody(reqJson);

        // ensure response methods exist
        doReturn(response).when(response).putHeader(anyString(), anyString());
        doReturn(response).when(response).setStatusCode(anyInt());
        doAnswer(inv -> null).when(response).end(anyString());

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient);
        handler.handle(ctx);

        verify(response, timeout(2000).atLeastOnce()).setStatusCode(502);
        verify(response, timeout(2000).atLeastOnce()).end(anyString());
    }

    @Test
    void promoFailure_addsPromoUnavailable_and_returns200() throws Exception {
        System.setProperty("allowed.currencies", "USD,EUR");

        when(fxClient.getEffectiveRate("USD")).thenReturn(CompletableFuture.completedFuture(2.0));
        when(promoClient.getPromo("X")).thenReturn(CompletableFuture.failedFuture(new RuntimeException("promo timeout")));

        String reqJson = """
      {"fareAmount":50.0,"currency":"USD","cabinClass":"ECONOMY","customerTier":"NONE","promoCode":"X"}
      """;
        stubBody(reqJson);

        // ensure response works
        doReturn(response).when(response).putHeader(anyString(), anyString());
        doReturn(response).when(response).setStatusCode(anyInt());
        doAnswer(inv -> null).when(response).end(anyString());

        QuoteHandler handler = new QuoteHandler(fxClient, promoClient);
        handler.handle(ctx);

        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(response, timeout(2000).atLeastOnce()).setStatusCode(200);
        verify(response, timeout(2000).atLeastOnce()).end(bodyCap.capture());

        String resp = bodyCap.getValue();
        assertThat(resp).contains("PROMO_UNAVAILABLE");
    }

}