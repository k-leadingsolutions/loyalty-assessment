package com.kleadingsolutions.loyalty.client.impl;

import com.kleadingsolutions.loyalty.client.PromoClient;
import com.kleadingsolutions.loyalty.dto.Promo;

import java.util.concurrent.CompletableFuture;

/**
 * Simple stub promo client. In real usage, call external promo service.
 */
public class StubPromoClient implements PromoClient {
  @Override
  public CompletableFuture<Promo> getPromo(String promoCode) {
    if (promoCode == null || promoCode.isBlank()) {
      return CompletableFuture.completedFuture(null);
    }
    Promo p = new Promo();
    p.setCode(promoCode);
    // example: SUMMER25 -> 25% off, expires soon
    if ("SUMMER25".equalsIgnoreCase(promoCode)) {
      p.setPercent(0.25);
      p.setExpiresInDays(1);
    } else {
      p.setPercent(0.10);
      p.setExpiresInDays(10);
    }
    return CompletableFuture.completedFuture(p);
  }
}