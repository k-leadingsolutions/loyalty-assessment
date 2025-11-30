package com.kleadingsolutions.loyalty.dto;

public class Promo {
  private String code;
  private double percent; // e.g. 0.25 for 25%
  private int expiresInDays; // days until expiry

  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }

  public double getPercent() { return percent; }
  public void setPercent(double percent) { this.percent = percent; }

  public int getExpiresInDays() { return expiresInDays; }
  public void setExpiresInDays(int expiresInDays) { this.expiresInDays = expiresInDays; }
}