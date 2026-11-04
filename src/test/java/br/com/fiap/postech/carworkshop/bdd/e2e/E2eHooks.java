package br.com.fiap.postech.carworkshop.bdd.e2e;

import br.com.fiap.postech.carworkshop.bdd.SagaScenario;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;

public class E2eHooks {

    @Before
    public void beginScenario(Scenario scenario) {
        String run = System.getenv().getOrDefault("GITHUB_RUN_ID", "local");
        SagaScenario.begin(new EksSagaDriver("EVIDENCIA-B7-" + run + "-" + scenario.getLine()));
    }

    @After
    public void bringEverythingBack() {
        ((EksSagaDriver) SagaScenario.driver()).bringEverythingBack();
    }
}
