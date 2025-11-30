package com.kleadingsolutions.loyalty.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DtoTests {

  @Test
  void promo_gettersAndSetters_work() {
    Promo p = new Promo();
    p.setCode("SUMMER25");
    p.setPercent(0.25);
    p.setExpiresInDays(3);

    assertThat(p.getCode()).isEqualTo("SUMMER25");
    assertThat(p.getPercent()).isEqualTo(0.25d);
    assertThat(p.getExpiresInDays()).isEqualTo(3);
  }

  @Test
  void quoteRequest_gettersAndSetters_work() {
    QuoteRequest r = new QuoteRequest();
    r.setFareAmount(1234.5);
    r.setCurrency("USD");
    r.setCabinClass("ECONOMY");
    r.setCustomerTier("GOLD");
    r.setPromoCode("MEGA");

    assertThat(r.getFareAmount()).isEqualTo(1234.5d);
    assertThat(r.getCurrency()).isEqualTo("USD");
    assertThat(r.getCabinClass()).isEqualTo("ECONOMY");
    assertThat(r.getCustomerTier()).isEqualTo("GOLD");
    assertThat(r.getPromoCode()).isEqualTo("MEGA");
  }

  @Test
  void quoteResponse_gettersAndSetters_work() {
    QuoteResponse resp = new QuoteResponse();
    resp.setBasePoints(1000L);
    resp.setTierBonus(150L);
    resp.setPromoBonus(250L);
    resp.setTotalPoints(1400L);
    resp.setEffectiveFxRate(3.5);
    resp.setWarnings(List.of("PROMO_EXPIRES_SOON"));

    assertThat(resp.getBasePoints()).isEqualTo(1000L);
    assertThat(resp.getTierBonus()).isEqualTo(150L);
    assertThat(resp.getPromoBonus()).isEqualTo(250L);
    assertThat(resp.getTotalPoints()).isEqualTo(1400L);
    assertThat(resp.getEffectiveFxRate()).isEqualTo(3.5d);
    assertThat(resp.getWarnings()).containsExactly("PROMO_EXPIRES_SOON");
  }
}