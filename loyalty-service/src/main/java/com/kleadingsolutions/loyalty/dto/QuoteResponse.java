package com.kleadingsolutions.loyalty.dto;

import java.util.List;

public class QuoteResponse {
  private long basePoints;
  private long tierBonus;
  private long promoBonus;
  private long totalPoints;
  private double effectiveFxRate;
  private List<String> warnings;

  public long getBasePoints() { return basePoints; }
  public void setBasePoints(long basePoints) { this.basePoints = basePoints; }

  public long getTierBonus() { return tierBonus; }
  public void setTierBonus(long tierBonus) { this.tierBonus = tierBonus; }

  public long getPromoBonus() { return promoBonus; }
  public void setPromoBonus(long promoBonus) { this.promoBonus = promoBonus; }

  public long getTotalPoints() { return totalPoints; }
  public void setTotalPoints(long totalPoints) { this.totalPoints = totalPoints; }

  public double getEffectiveFxRate() { return effectiveFxRate; }
  public void setEffectiveFxRate(double effectiveFxRate) { this.effectiveFxRate = effectiveFxRate; }

  public List<String> getWarnings() { return warnings; }
  public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}