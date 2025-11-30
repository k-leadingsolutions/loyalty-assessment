package com.kleadingsolutions.loyalty.client;

import com.kleadingsolutions.loyalty.dto.Promo;

import java.util.concurrent.CompletableFuture;

public interface PromoClient {
  CompletableFuture<Promo> getPromo(String promoCode);
}