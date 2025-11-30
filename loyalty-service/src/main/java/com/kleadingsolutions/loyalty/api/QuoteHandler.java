package com.kleadingsolutions.loyalty.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.kleadingsolutions.loyalty.client.FxClient;
import com.kleadingsolutions.loyalty.client.PromoClient;
import com.kleadingsolutions.loyalty.dto.Promo;
import com.kleadingsolutions.loyalty.dto.QuoteRequest;
import com.kleadingsolutions.loyalty.dto.QuoteResponse;
import com.kleadingsolutions.loyalty.service.PointsCalculatorService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import io.vertx.ext.web.RoutingContext;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * QuoteHandler with optional metrics instrumentation (Micrometer).
 * Handles /v1/points/quote requests.
 * Validates input, calls FX and Promo clients asynchronously,
 * calculates points using PointsCalculatorService, and returns JSON response.
 */
public class QuoteHandler implements io.vertx.core.Handler<RoutingContext> {

    private static final Logger LOGGER = Logger.getLogger(QuoteHandler.class.getName());

    private final ObjectMapper mapper = new ObjectMapper();
    private final FxClient fxClient;
    private final PromoClient promoClient;
    private final Set<String> allowedCurrencies;
    private static final Set<String> DEFAULT_ALLOWED = Set.of("USD", "EUR", "AED");

    private static final Set<String> VALID_CABINS = Set.of("ECONOMY", "PREMIUM_ECONOMY", "BUSINESS", "FIRST");

    private final Supplier<PointsCalculatorService> calcSupplier;

    private final MeterRegistry meterRegistry;
    private final Counter requestCounter;
    private final Timer requestTimer;

    public QuoteHandler(FxClient fxClient, PromoClient promoClient) {
        this(fxClient, promoClient, PointsCalculatorService::new, null);
    }

    public QuoteHandler(FxClient fxClient, PromoClient promoClient, MeterRegistry meterRegistry) {
        this(fxClient, promoClient, PointsCalculatorService::new, meterRegistry);
    }

    QuoteHandler(FxClient fxClient, PromoClient promoClient, Supplier<PointsCalculatorService> calcSupplier) {
        this(fxClient, promoClient, calcSupplier, null);
    }

    QuoteHandler(FxClient fxClient, PromoClient promoClient, Supplier<PointsCalculatorService> calcSupplier, MeterRegistry meterRegistry) {
        this.fxClient = fxClient;
        this.promoClient = promoClient;
        this.calcSupplier = Objects.requireNonNull(calcSupplier, "calcSupplier");
        this.allowedCurrencies = loadAllowedCurrencies();

        this.meterRegistry = meterRegistry;
        if (this.meterRegistry != null) {
            this.requestCounter = this.meterRegistry.counter("loyalty_quotes_requests_total");
            this.requestTimer = this.meterRegistry.timer("loyalty_quotes_request_duration_seconds");
        } else {
            this.requestCounter = null;
            this.requestTimer = null;
        }
    }

    private Set<String> loadAllowedCurrencies() {
        String csv = System.getProperty("allowed.currencies", "").trim();
        if (csv.isEmpty()) {
            return DEFAULT_ALLOWED;
        }
        //else parse or call external config service in future
        String[] parts = csv.split(",");
        Set<String> s = new HashSet<>();
        for (String p : parts) {
            String t = p.trim().toUpperCase();
            if (!t.isEmpty()) s.add(t);
        }
        return s.isEmpty() ? DEFAULT_ALLOWED : Collections.unmodifiableSet(s);
    }

    private static String getBodyString(RoutingContext ctx) {
        try {
            return ctx.body().asString();
        } catch (NoSuchMethodError | AbstractMethodError e) {
            //backward compatibility fallback
            return getBodyStringDeprecated(ctx);
        }
    }

    @SuppressWarnings("deprecation")
    private static String getBodyStringDeprecated(RoutingContext ctx) {
        return ctx.getBodyAsString();
    }

    private boolean isValidCurrency(String currency) {
        return currency != null && allowedCurrencies.contains(currency.toUpperCase());
    }

    private boolean isValidCabin(String cabin) {
        return cabin != null && VALID_CABINS.contains(cabin.toUpperCase());
    }

    /**
     * Ensure we always run response sends on the Vert.x context to avoid cross-thread issues.
     */
    private void safeSendResponseOnContext(RoutingContext ctx, int status, String body) {
        try {
            // ensure running on vertx event loop
            ctx.vertx().runOnContext(v -> safeSendResponse(ctx, status, body));
        } catch (Exception e) {
            // fallback to direct call if ctx.vertx() throws for some reason
            safeSendResponse(ctx, status, body);
        }
    }

    private void safeSendResponse(RoutingContext ctx, int status, String body) {
        try {
            final io.vertx.core.http.HttpServerResponse resp = ctx.response();

            try {
                resp.putHeader("content-type", "application/json");
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "putHeader failed: " + e.getMessage(), e);
            }
            try {
                resp.setStatusCode(status);
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "setStatusCode failed: " + e.getMessage(), e);
            }
            try {
                resp.end(body);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to end response: " + e.getMessage(), e);
            }
        } catch (Exception outer) {
            LOGGER.log(Level.SEVERE, "Unexpected error while sending response: " + outer.getMessage(), outer);
        }
    }

    @Override
    public void handle(RoutingContext ctx) {
        if (requestCounter != null) {
            try { requestCounter.increment(); } catch (Exception ignored) {}
        }
        Sample sample = null;
        if (requestTimer != null) {
            try { sample = Timer.start(meterRegistry); } catch (Exception ignored) {}
        }

        String bodyStr = getBodyString(ctx);
        try {
            if (bodyStr == null || bodyStr.isEmpty()) {
                safeSendResponseOnContext(ctx, 400, "{\"error\":\"invalid request\"}");
                recordTimer(sample);
                return;
            }

            final QuoteRequest req;
            try {
                req = mapper.readValue(bodyStr, QuoteRequest.class);
            } catch (JsonProcessingException ex) {
                LOGGER.log(Level.FINE, "Invalid JSON payload: " + ex.getMessage());
                safeSendResponseOnContext(ctx, 400, "{\"error\":\"invalid request\"}");
                recordTimer(sample);
                return;
            }

            // validate required fields - fareAmount, currency, cabinClass
            // to be more robust, we could use a validation framework / move to DTO annotations / separate validator class
            if (req.getFareAmount() <= 0) {
                safeSendResponseOnContext(ctx, 400, "{\"error\":\"fareAmount must be > 0\"}");
                recordTimer(sample);
                return;
            }
            if (!isValidCurrency(req.getCurrency())) {
                safeSendResponseOnContext(ctx, 400, "{\"error\":\"invalid currency\"}");
                recordTimer(sample);
                return;
            }
            if (!isValidCabin(req.getCabinClass())) {
                safeSendResponseOnContext(ctx, 400, "{\"error\":\"invalid cabinClass\"}");
                recordTimer(sample);
                return;
            }

            CompletableFuture<Double> fxFuture = fxClient.getEffectiveRate(req.getCurrency());
            CompletableFuture<Promo> promoFuture = promoClient.getPromo(req.getPromoCode());

            if (fxFuture == null) {
                safeSendResponseOnContext(ctx, 502, "{\"error\":\"fx service unavailable\"}");
                recordTimer(sample);
                return;
            }
            if (promoFuture == null) {
                promoFuture = CompletableFuture.failedFuture(new RuntimeException("promo client returned null"));
            }

            Sample finalSample = sample;
            CompletableFuture<Promo> finalPromoFuture = promoFuture;
            fxFuture.whenComplete((fxRate, fxEx) -> {
                try {
                    if (fxEx != null) {
                        safeSendResponseOnContext(ctx, 502, "{\"error\":\"fx service unavailable\"}");
                        recordTimer(finalSample);
                        return;
                    }

                    finalPromoFuture.handle((promo, promoEx) -> {
                        try {
                            List<String> externalWarnings = new ArrayList<>();
                            if (promoEx != null) {
                                externalWarnings.add("PROMO_UNAVAILABLE");
                                promo = null;
                            }

                            PointsCalculatorService calc = calcSupplier.get();
                            QuoteResponse resp = calc.calculate(req, fxRate, promo);

                            if (!externalWarnings.isEmpty()) {
                                if (resp.getWarnings() == null) {
                                    resp.setWarnings(externalWarnings);
                                } else {
                                    resp.getWarnings().addAll(externalWarnings);
                                }
                            }

                            String json;
                            try {
                                json = mapper.writeValueAsString(resp);
                            } catch (Exception serEx) {
                                LOGGER.log(Level.WARNING, "Failed to serialize response: " + serEx.getMessage(), serEx);
                                safeSendResponseOnContext(ctx, 500, "{\"error\":\"internal error\"}");
                                recordTimer(finalSample);
                                return null;
                            }

                            safeSendResponseOnContext(ctx, 200, json);
                            recordTimer(finalSample);
                        } catch (Exception e) {
                            LOGGER.log(Level.SEVERE, "Error while handling promo/fx result: " + e.getMessage(), e);
                            safeSendResponseOnContext(ctx, 500, "{\"error\":\"internal error\"}");
                            recordTimer(finalSample);
                        }
                        return null;
                    });
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Unhandled exception in FX completion handler: " + e.getMessage(), e);
                    safeSendResponseOnContext(ctx, 500, "{\"error\":\"internal error\"}");
                    recordTimer(finalSample);
                }
            });

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Invalid request processing error: " + e.getMessage(), e);
            safeSendResponseOnContext(ctx, 400, "{\"error\":\"invalid request\"}");
            recordTimer(sample);
        }
    }

    private void recordTimer(Sample sample) {
        if (sample != null && requestTimer != null) {
            try {
                sample.stop(requestTimer);
            } catch (Exception ignored) {}
        }
    }
}