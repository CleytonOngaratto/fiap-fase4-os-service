package br.com.fiap.postech.carworkshop.bdd.e2e;

import io.cucumber.core.cli.Main;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SagaE2eSuite {

    @Test
    void runsTheFeaturesThroughTheGateway() {
        byte status = Main.run(new String[]{
                "--glue", "br.com.fiap.postech.carworkshop.bdd.steps",
                "--glue", "br.com.fiap.postech.carworkshop.bdd.e2e",
                "--plugin", "pretty",
                "--plugin", "html:target/cucumber/e2e.html",
                "--tags", "(" + System.getenv().getOrDefault("E2E_TAGS", "@e2e") + ") and @e2e",
                "classpath:features/saga"}, Thread.currentThread().getContextClassLoader());

        assertEquals(0, status);
    }
}
