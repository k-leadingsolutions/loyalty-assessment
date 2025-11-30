package com.kleadingsolutions.loyalty.client;

import com.kleadingsolutions.loyalty.client.impl.VertxPromoClient;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class VertxPromoClientConnectionFailureTest {

  private static Vertx vertx;

  @BeforeAll
  static void setup() {
    vertx = Vertx.vertx();
  }

  @AfterAll
  static void teardown() throws Exception {
    if (vertx != null) vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @Test
  public void connectionRefused_throws() {
    // Port 9 is typically closed/reserved; using it to provoke a connection error
    String baseUrl = "http://localhost:9";
    VertxPromoClient client = new VertxPromoClient(vertx, baseUrl);

    assertThrows(Exception.class, () -> client.getPromo("SUMMER25").get(3, TimeUnit.SECONDS));
  }
}