package com.kleadingsolutions.loyalty.client.impl;

import com.kleadingsolutions.loyalty.client.PromoClient;
import com.kleadingsolutions.loyalty.dto.Promo;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.CompletableFuture;

/**
 * Simple WebClient-based Promo client.
 * Expected endpoint:
 * GET {baseUrl}/promo/{code} -> { "code":"SUMMER25", "percent":0.25, "expiresInDays":1 }
 */
public class VertxPromoClient implements PromoClient {

  private final WebClient webClient;
  private final String baseUrl;

  public VertxPromoClient(Vertx vertx, String baseUrl) {
    this.webClient = WebClient.create(vertx);
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length()-1) : baseUrl;
  }

  @Override
  public CompletableFuture<Promo> getPromo(String promoCode) {
    CompletableFuture<Promo> fut = new CompletableFuture<>();
    if (promoCode == null || promoCode.isBlank()) {
      fut.complete(null);
      return fut;
    }
    webClient.getAbs(baseUrl + "/promo/" + promoCode)
      .timeout(1000)
      .send(ar -> {
        if (ar.succeeded()) {
          HttpResponse<io.vertx.core.buffer.Buffer> resp = ar.result();
          try {
            JsonObject obj = resp.bodyAsJsonObject();
            Promo p = new Promo();
            p.setCode(obj.getString("code"));
            p.setPercent(obj.getDouble("percent", 0.0));
            p.setExpiresInDays(obj.getInteger("expiresInDays", 0));
            fut.complete(p);
          } catch (Exception e) {
            fut.completeExceptionally(e);
          }
        } else {
          fut.completeExceptionally(ar.cause());
        }
      });
    return fut;
  }
}