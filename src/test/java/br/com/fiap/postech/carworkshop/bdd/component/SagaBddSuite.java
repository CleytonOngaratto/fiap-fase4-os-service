package br.com.fiap.postech.carworkshop.bdd.component;

import br.com.fiap.postech.carworkshop.saga.infrastructure.messaging.SagaMessagingResource;
import br.com.fiap.postech.carworkshop.saga.infrastructure.messaging.SagaMessagingTestProfile;
import io.quarkiverse.cucumber.CucumberOptions;
import io.quarkiverse.cucumber.CucumberQuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.TestProfile;

@TestProfile(SagaMessagingTestProfile.class)
@QuarkusTestResource(value = SagaMessagingResource.class, restrictToAnnotatedClass = true)
@CucumberOptions(
        features = "classpath:features/saga",
        glue = {"br.com.fiap.postech.carworkshop.bdd.steps", "br.com.fiap.postech.carworkshop.bdd.component"},
        tags = "not @e2e-only",
        plugin = "html:target/cucumber/component.html")
public class SagaBddSuite extends CucumberQuarkusTest {
}
