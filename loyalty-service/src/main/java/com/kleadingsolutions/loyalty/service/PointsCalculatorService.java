package com.kleadingsolutions.loyalty.service;

import com.kleadingsolutions.loyalty.dto.Promo;
import com.kleadingsolutions.loyalty.dto.QuoteRequest;
import com.kleadingsolutions.loyalty.dto.QuoteResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PointsCalculatorService {

  private static final long CAP = 50_000L;
  private static final Map<String, Double> TIER_MULT = Map.of(
          "NONE", 0.0,
          "SILVER", 0.15,
          "GOLD", 0.30,
          "PLATINUM", 0.50
  );

  public QuoteResponse calculate(QuoteRequest req, double effectiveFxRate, Promo promo) {
    long basePoints = (long)Math.floor(req.getFareAmount() * effectiveFxRate);

    String tierKey = (req.getCustomerTier() == null) ? "NONE" : req.getCustomerTier().toUpperCase();
    double tierMult = TIER_MULT.getOrDefault(tierKey, 0.0);
    long tierBonus = (long)Math.floor(basePoints * tierMult);

    long promoBonus = 0;
    List<String> warnings = new ArrayList<>();
    if (promo != null && promo.getPercent() > 0) {
      promoBonus = (long)Math.floor(basePoints * promo.getPercent());
      if (promo.getExpiresInDays() <= 2) {
        warnings.add("PROMO_EXPIRES_SOON");
      }
    }

    long total = basePoints + tierBonus + promoBonus;
    if (total > CAP) {
      total = CAP;
    }

    QuoteResponse resp = new QuoteResponse();
    resp.setBasePoints(basePoints);
    resp.setTierBonus(tierBonus);
    resp.setPromoBonus(promoBonus);
    resp.setTotalPoints(total);
    resp.setEffectiveFxRate(effectiveFxRate);
    resp.setWarnings(warnings);
    return resp;
  }
}