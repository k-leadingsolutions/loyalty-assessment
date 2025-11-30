package com.kleadingsolutions.loyalty.client;

import java.util.concurrent.CompletableFuture;

public interface FxClient {
  /**
   * Returns effective FX rate to convert fare to points (points per unit of currency).
   * Example: if fare in USD and 1 USD -> 3.67 points, returns 3.67
   */
  CompletableFuture<Double> getEffectiveRate(String currency);
}