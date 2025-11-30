package com.kleadingsolutions.loyalty.client.impl;

import com.kleadingsolutions.loyalty.client.FxClient;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.CompletableFuture;

/**
 * WebClient-based FX client with simple retry behavior.
 * Expected endpoint: GET {baseUrl}/fx/rate?currency=USD  -> { "rate": 3.67 }
 * Configurable via system properties:
 * - fx.retries (default 2)
 * - fx.retry.delay.ms (default 200)
 */
public class VertxFxClient implements FxClient {

  private final WebClient webClient;
  private final String baseUrl;
  private final Vertx vertx;

  public VertxFxClient(Vertx vertx, String baseUrl) {
    this.webClient = WebClient.create(vertx);
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length()-1) : baseUrl;
    this.vertx = vertx;
  }

  @Override
  public CompletableFuture<Double> getEffectiveRate(String currency) {
    int retries = Integer.parseInt(System.getProperty("fx.retries", "2"));
    long retryDelay = Long.parseLong(System.getProperty("fx.retry.delay.ms", "200"));
    CompletableFuture<Double> fut = new CompletableFuture<>();
    attemptGet(currency, retries, retryDelay, fut);
    return fut;
  }

  private void attemptGet(String currency, int attemptsLeft, long retryDelayMs, CompletableFuture<Double> fut) {
    webClient.getAbs(baseUrl + "/fx/rate")
            .addQueryParam("currency", currency == null ? "" : currency)
            .timeout(1000)
            .send(ar -> {
              if (ar.succeeded()) {
                HttpResponse<io.vertx.core.buffer.Buffer> resp = ar.result();
                try {
                  JsonObject obj = resp.bodyAsJsonObject();
                  double rate = obj.getDouble("rate", 3.67);
                  fut.complete(rate);
                } catch (Exception e) {
                  if (attemptsLeft > 0) {
                    vertx.setTimer(retryDelayMs, id -> attemptGet(currency, attemptsLeft - 1, retryDelayMs, fut));
                  } else {
                    fut.completeExceptionally(e);
                  }
                }
              } else {
                if (attemptsLeft > 0) {
                  vertx.setTimer(retryDelayMs, id -> attemptGet(currency, attemptsLeft - 1, retryDelayMs, fut));
                } else {
                  fut.completeExceptionally(ar.cause());
                }
              }
            });
  }
}