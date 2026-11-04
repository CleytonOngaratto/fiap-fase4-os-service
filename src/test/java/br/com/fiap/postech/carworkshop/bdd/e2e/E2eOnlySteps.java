package br.com.fiap.postech.carworkshop.bdd.e2e;

import br.com.fiap.postech.carworkshop.bdd.SagaScenario;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;

public class E2eOnlySteps {

    private static EksSagaDriver driver() {
        return (EksSagaDriver) SagaScenario.driver();
    }

    @Dado("o OS está fora do ar")
    public void theOsIsDown() {
        driver().takeOsDown();
    }

    @Quando("o mecânico tenta concluir o diagnóstico")
    public void theMechanicTriesToCompleteTheDiagnosis() {
        driver().tryToCompleteDiagnosis();
    }

    @Entao("o mecânico recebe {int}")
    public void theMechanicReceives(int status) {
        driver().assertMechanicAnswer(status);
    }

    @Quando("o OS volta")
    public void theOsComesBack() {
        driver().bringOsBack();
    }
}
