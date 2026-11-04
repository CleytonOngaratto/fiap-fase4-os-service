package br.com.fiap.postech.carworkshop.bdd.e2e;

import br.com.fiap.postech.carworkshop.bdd.SagaScenario;
import io.cucumber.java.Before;

public class E2eHooks {

    @Before
    public void beginScenario() {
        SagaScenario.begin(EksSagaDriver.create());
    }
}
