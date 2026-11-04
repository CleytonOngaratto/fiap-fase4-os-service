package br.com.fiap.postech.carworkshop.bdd.component;

import br.com.fiap.postech.carworkshop.bdd.SagaScenario;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@Unremovable
@ApplicationScoped
public class ComponentHooks {

    @Inject
    SagaBench bench;

    @Before
    public void beginScenario(Scenario scenario) {
        SagaScenario.begin(new ComponentSagaDriver(bench, "EVIDENCIA-BDD-" + scenario.getLine()));
    }

    @After
    public void assertInvariants(Scenario scenario) {
        if (!scenario.isFailed()) {
            ((ComponentSagaDriver) SagaScenario.driver()).assertScenarioInvariants();
        }
    }
}
