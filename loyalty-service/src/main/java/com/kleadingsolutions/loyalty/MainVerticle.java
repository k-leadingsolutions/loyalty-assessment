package com.kleadingsolutions.loyalty;

import com.kleadingsolutions.loyalty.api.QuoteHandler;
import com.kleadingsolutions.loyalty.client.FxClient;
import com.kleadingsolutions.loyalty.client.PromoClient;
import com.kleadingsolutions.loyalty.client.impl.StubFxClient;
import com.kleadingsolutions.loyalty.client.impl.StubPromoClient;
import com.kleadingsolutions.loyalty.client.impl.VertxFxClient;
import com.kleadingsolutions.loyalty.client.impl.VertxPromoClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * MainVerticle boots the HTTP server. Also exposes Prometheus metrics and simple health endpoints.
 */
public class MainVerticle extends AbstractVerticle {

    private FxClient fxClient;
    private PromoClient promoClient;

    private final PrometheusMeterRegistry meterRegistry;

    public MainVerticle() {
        // resolve in start() to use the verticle's vertx instance
        this.fxClient = null;
        this.promoClient = null;
        this.meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }


    public MainVerticle(FxClient fxClient, PromoClient promoClient, MeterRegistry registry) {
        this.fxClient = fxClient;
        this.promoClient = promoClient;
        if (registry instanceof PrometheusMeterRegistry) {
            this.meterRegistry = (PrometheusMeterRegistry) registry;
        } else {
            this.meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        }
    }

    public PrometheusMeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        if (this.fxClient == null) {
            String fxBase = System.getProperty("fx.base.url", "").trim();
            this.fxClient = !fxBase.isEmpty()
                    ? new VertxFxClient(this.vertx, fxBase)
                    : new StubFxClient();
        }
        if (this.promoClient == null) {
            String promoBase = System.getProperty("promo.base.url", "").trim();
            this.promoClient = !promoBase.isEmpty()
                    ? new VertxPromoClient(this.vertx, promoBase)
                    : new StubPromoClient();
        }

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        router.get("/metrics").handler(ctx -> {
            ctx.response()
                    .putHeader("content-type", "text/plain; version=0.0.4; charset=utf-8")
                    .end(meterRegistry.scrape());
        });

        router.get("/health").handler(ctx -> ctx.response().end("OK"));
        router.get("/ready").handler(ctx -> ctx.response().end("OK"));

        router.post("/v1/points/quote").handler(new QuoteHandler(fxClient, promoClient, meterRegistry));

        int configuredPort = Integer.parseInt(System.getProperty("http.port", "8888"));
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(configuredPort, ar -> {
                    if (ar.succeeded()) {
                        int actual = ar.result().actualPort();
                        System.setProperty("http.actual.port", String.valueOf(actual));
                        System.out.println("HTTP server started on port " + actual);
                        startPromise.complete();
                    } else {
                        startPromise.fail(ar.cause());
                    }
                });
    }
}