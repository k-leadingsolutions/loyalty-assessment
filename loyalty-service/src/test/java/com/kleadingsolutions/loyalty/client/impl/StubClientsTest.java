package com.kleadingsolutions.loyalty.client.impl;

import com.kleadingsolutions.loyalty.dto.Promo;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class StubClientsTest {

  @Test
  public void stubFxClient_returnsExpectedRates() throws Exception {
    StubFxClient fx = new StubFxClient();
    assertThat(fx.getEffectiveRate("USD").get(1, TimeUnit.SECONDS)).isEqualTo(3.67d);
    assertThat(fx.getEffectiveRate("EUR").get(1, TimeUnit.SECONDS)).isEqualTo(4.0d);
    assertThat(fx.getEffectiveRate("UNKNOWN").get(1, TimeUnit.SECONDS)).isEqualTo(1.0d);
  }

  @Test
  public void stubPromoClient_handlesNullAndKnownCodes() throws Exception {
    StubPromoClient promo = new StubPromoClient();
    Promo p1 = promo.getPromo(null).get(1, TimeUnit.SECONDS);
    assertThat(p1).isNull();

    Promo p2 = promo.getPromo("SUMMER25").get(1, TimeUnit.SECONDS);
    assertThat(p2).isNotNull();
    assertThat(p2.getPercent()).isEqualTo(0.25d);
    assertThat(p2.getExpiresInDays()).isEqualTo(1);

    Promo p3 = promo.getPromo("OTHER").get(1, TimeUnit.SECONDS);
    assertThat(p3).isNotNull();
    assertThat(p3.getPercent()).isEqualTo(0.10d);
  }
}