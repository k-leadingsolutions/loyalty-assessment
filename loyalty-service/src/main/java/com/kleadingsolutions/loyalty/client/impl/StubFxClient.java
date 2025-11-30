package com.kleadingsolutions.loyalty.client.impl;

import com.kleadingsolutions.loyalty.client.FxClient;

import java.util.concurrent.CompletableFuture;

/**
 * Very small stub FX client for local runs/tests.
 * Real implementation would call an external FX service asynchronously.
 */
public class StubFxClient implements FxClient {
  @Override
  public CompletableFuture<Double> getEffectiveRate(String currency) {
    // simple fixed mapping for skeleton
    double rate = switch (currency == null ? "" : currency.toUpperCase()) {
      case "USD" -> 3.67;
      case "EUR" -> 4.00;
      default -> 1.0;
    };
    return CompletableFuture.completedFuture(rate);
  }
}