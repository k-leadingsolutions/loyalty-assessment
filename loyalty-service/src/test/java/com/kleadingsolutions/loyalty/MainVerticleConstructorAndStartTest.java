package com.kleadingsolutions.loyalty;

import com.kleadingsolutions.loyalty.client.FxClient;
import com.kleadingsolutions.loyalty.client.PromoClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class MainVerticleConstructorAndStartTest {

  @Mock
  MeterRegistry meterRegistryMock;

  @AfterEach
  void clearProps() {
    System.clearProperty("fx.base.url");
    System.clearProperty("promo.base.url");
    System.clearProperty("http.port");
    System.clearProperty("http.actual.port");
  }

  /**
   * Deploys the verticle and then inspects the fx/promo client fields.
   * Deploying ensures fields are populated whether initialization happens
   * in the constructor or in start().
   */
  @Test
  public void defaultConstructor_usesStubClients_when_no_props() throws Exception {
    // ensure no props
    System.clearProperty("fx.base.url");
    System.clearProperty("promo.base.url");

    Vertx vertx = Vertx.vertx();
    try {
      MainVerticle verticle = new MainVerticle();

      // deploy and wait for startup (ephemeral port)
      System.setProperty("http.port", "0");
      CompletableFuture<String> deployed = new CompletableFuture<>();
      vertx.deployVerticle(verticle, ar -> {
        if (ar.succeeded()) deployed.complete(ar.result());
        else deployed.completeExceptionally(ar.cause());
      });
      deployed.get(5, TimeUnit.SECONDS);

      // reflectively inspect fields AFTER deployment to be robust to init timing
      Field fxField = MainVerticle.class.getDeclaredField("fxClient");
      Field promoField = MainVerticle.class.getDeclaredField("promoClient");
      fxField.setAccessible(true);
      promoField.setAccessible(true);

      Object fx = fxField.get(verticle);
      Object promo = promoField.get(verticle);

      assertThat(fx).isInstanceOf(FxClient.class);
      assertThat(promo).isInstanceOf(PromoClient.class);

      // class names for stubs contain "Stub"
      assertThat(fx.getClass().getSimpleName().toLowerCase()).contains("stub");
      assertThat(promo.getClass().getSimpleName().toLowerCase()).contains("stub");
    } finally {
      vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
  }

  @Test
  public void defaultConstructor_createsVertxClients_when_props_present() throws Exception {
    // set properties so the constructor/start will create Vertx clients
    System.setProperty("fx.base.url", "http://example.invalid");
    System.setProperty("promo.base.url", "http://example.invalid");

    Vertx vertx = Vertx.vertx();
    try {
      MainVerticle verticle = new MainVerticle();

      // deploy and wait
      System.setProperty("http.port", "0");
      CompletableFuture<String> deployed = new CompletableFuture<>();
      vertx.deployVerticle(verticle, ar -> {
        if (ar.succeeded()) deployed.complete(ar.result());
        else deployed.completeExceptionally(ar.cause());
      });
      deployed.get(5, TimeUnit.SECONDS);

      // inspect fields after deployment (works whether clients created in ctor or in start())
      Field fxField = MainVerticle.class.getDeclaredField("fxClient");
      Field promoField = MainVerticle.class.getDeclaredField("promoClient");
      fxField.setAccessible(true);
      promoField.setAccessible(true);

      Object fx = fxField.get(verticle);
      Object promo = promoField.get(verticle);

      assertThat(fx).isInstanceOf(FxClient.class);
      assertThat(promo).isInstanceOf(PromoClient.class);
      // concrete types created by constructor/start should include "Vertx" in the name when system props are present
      assertThat(fx.getClass().getSimpleName()).containsIgnoringCase("Vertx");
      assertThat(promo.getClass().getSimpleName()).containsIgnoringCase("Vertx");
    } finally {
      vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void whenPrometheusRegistryProvided_thenItIsUsedDirectly() {
    // Arrange
    FxClient fxClient = mock(FxClient.class);
    PromoClient promoClient = mock(PromoClient.class);
    PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    // Act
    MainVerticle verticle = new MainVerticle(fxClient, promoClient, prometheus);

    // Assert
    assertThat(verticle.getMeterRegistry()).isSameAs(prometheus)
            .withFailMessage("Constructor should use the provided PrometheusMeterRegistry instance");
  }

  @Test
  void whenNonPrometheusRegistryProvided_thenPrometheusRegistryIsCreated() {
    // Arrange
    FxClient fxClient = mock(FxClient.class);
    PromoClient promoClient = mock(PromoClient.class);
    MeterRegistry other = new SimpleMeterRegistry();

    // Act
    MainVerticle verticle = new MainVerticle(fxClient, promoClient, other);

    // Assert
    assertThat(verticle.getMeterRegistry())
            .isNotNull()
            .isInstanceOf(PrometheusMeterRegistry.class)
            .isNotSameAs(other)
            .withFailMessage("Constructor should create/wrap a PrometheusMeterRegistry when a non-Prometheus MeterRegistry is provided");
  }

  @Test
  void whenNullRegistryProvided_thenPrometheusRegistryIsCreated() {
    // Arrange
    FxClient fxClient = mock(FxClient.class);
    PromoClient promoClient = mock(PromoClient.class);

    // Act
    MainVerticle verticle = new MainVerticle(fxClient, promoClient, null);

    // Assert
    assertThat(verticle.getMeterRegistry())
            .isNotNull()
            .isInstanceOf(PrometheusMeterRegistry.class)
            .withFailMessage("Constructor should create a PrometheusMeterRegistry when a null registry is provided");
  }
}