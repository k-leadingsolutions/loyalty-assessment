package com.kleadingsolutions.loyalty.service;

import com.kleadingsolutions.loyalty.dto.Promo;
import com.kleadingsolutions.loyalty.dto.QuoteRequest;
import com.kleadingsolutions.loyalty.dto.QuoteResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PointsCalculatorServiceTest {
  PointsCalculatorService calc = new PointsCalculatorService();
  @Test
  public void calculates_base_tier_and_promo() {
    PointsCalculatorService calc = new PointsCalculatorService();
    QuoteRequest req = new QuoteRequest();
    req.setFareAmount(100.0);
    req.setCustomerTier("SILVER");

    Promo promo = new Promo();
    promo.setPercent(0.25);
    promo.setExpiresInDays(1);

    QuoteResponse r = calc.calculate(req, 3.0, promo);
    // basePoints = floor(100 * 3) = 300
    assertThat(r.getBasePoints()).isEqualTo(300);
    // tier bonus = floor(300 * 0.15) = 45
    assertThat(r.getTierBonus()).isEqualTo(45);
    // promo bonus = floor(300 * 0.25) = 75
    assertThat(r.getPromoBonus()).isEqualTo(75);
    // total = 420
    assertThat(r.getTotalPoints()).isEqualTo(420);
    assertThat(r.getWarnings()).contains("PROMO_EXPIRES_SOON");
  }

  @Test
  public void caps_total_at_50000() {
    PointsCalculatorService calc = new PointsCalculatorService();
    QuoteRequest req = new QuoteRequest();
    req.setFareAmount(1_000_000.0);
    req.setCustomerTier("PLATINUM");

    Promo promo = new Promo();
    promo.setPercent(1.0); // huge
    promo.setExpiresInDays(10);

    QuoteResponse r = calc.calculate(req, 10.0, promo);
    assertThat(r.getTotalPoints()).isEqualTo(50_000L);
  }

  @Test
  public void noPromo_and_NONE_tier() {
    QuoteRequest req = new QuoteRequest();
    req.setFareAmount(50.0);
    req.setCustomerTier("NONE");

    QuoteResponse r = calc.calculate(req, 2.0, null);
    assertThat(r.getTierBonus()).isEqualTo(0L);
    assertThat(r.getPromoBonus()).isEqualTo(0L);
  }

  @Test
  public void gold_and_platinum_tiers_and_near_expiry_promo() {
    QuoteRequest req = new QuoteRequest();
    req.setFareAmount(200.0);

    // GOLD tier
    req.setCustomerTier("GOLD");
    Promo promo = new Promo();
    promo.setPercent(0.10);
    promo.setExpiresInDays(2); // boundary should trigger PROMO_EXPIRES_SOON

    QuoteResponse r = calc.calculate(req, 3.0, promo);
    long base = (long)Math.floor(200.0 * 3.0);
    assertThat(r.getTierBonus()).isEqualTo((long)Math.floor(base * 0.30));
    assertThat(r.getWarnings()).contains("PROMO_EXPIRES_SOON");

    // PLATINUM tier with big percent to test cap
    req.setCustomerTier("PLATINUM");
    promo.setPercent(1.0);
    r = calc.calculate(req, 100.0, promo); // large FX to push totals high
    assertThat(r.getTotalPoints()).isEqualTo(50_000L); // capped
  }
}