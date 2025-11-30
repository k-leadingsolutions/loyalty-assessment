package com.kleadingsolutions.loyalty.contracts;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import com.kleadingsolutions.loyalty.client.PromoClient;
import com.kleadingsolutions.loyalty.client.impl.VertxPromoClient;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "PromoProvider", pactVersion = PactSpecVersion.V3)
public class PromoServicePactTest {

    private Vertx vertx;

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }

    @Pact(consumer = "loyalty-service", provider = "PromoProvider")
    public RequestResponsePact createPact(PactDslWithProvider builder) {
        String responseBody = """
                { "code": "SUMMER25", "percent": 0.25, "expiresInDays": 1 }
                """;

        return builder
                .uponReceiving("A request for promo SUMMER25")
                .path("/promo/SUMMER25")
                .method("GET")
                .willRespondWith()
                .status(200)
                .headers(java.util.Map.of("Content-Type", "application/json"))
                .body(responseBody)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "createPact")
    void consumerTest(MockServer mockServer) throws Exception {
        vertx = Vertx.vertx();
        try {
            String baseUrl = mockServer.getUrl();
            PromoClient client = new VertxPromoClient(vertx, baseUrl);

            var promo = client.getPromo("SUMMER25")
                    .get(3, TimeUnit.SECONDS);

            Assertions.assertNotNull(promo);
            Assertions.assertEquals("SUMMER25", promo.getCode());
            Assertions.assertEquals(0.25, promo.getPercent(), 0.0001);
            Assertions.assertEquals(1, promo.getExpiresInDays());
        } finally {
            vertx.close();
        }
    }
}