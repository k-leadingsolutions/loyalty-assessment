package com.kleadingsolutions.loyalty.contracts;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import com.kleadingsolutions.loyalty.client.FxClient;
import com.kleadingsolutions.loyalty.client.impl.VertxFxClient;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "FxProvider", pactVersion = PactSpecVersion.V3)
public class FxServicePactTest {

    private Vertx vertx;

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }

    @Pact(consumer = "loyalty-service", provider = "FxProvider")
    public RequestResponsePact createPact(PactDslWithProvider builder) {
        return builder
                .given("FX rate for USD exists")
                .uponReceiving("A request for USD rate")
                .path("/fx/rate")
                .method("GET")
                .query("currency=USD")
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body("{ \"rate\": 3.67 }")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "createPact")
    void consumerTest(MockServer mockServer) throws Exception {
        vertx = Vertx.vertx();
        try {
            String baseUrl = mockServer.getUrl();
            FxClient client = new VertxFxClient(vertx, baseUrl);

            double rate = client.getEffectiveRate("USD")
                    .get(3, TimeUnit.SECONDS);

            // assert the client parsed the provider response correctly
            Assertions.assertEquals(3.67, rate, 0.0001);
        } finally {
            vertx.close();
        }
    }
}