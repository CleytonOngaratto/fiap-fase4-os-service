package br.com.fiap.postech.carworkshop.bdd;

public final class SagaScenario {

    private static SagaDriver current;

    private SagaScenario() {
    }

    public static void begin(SagaDriver driver) {
        current = driver;
    }

    public static SagaDriver driver() {
        return current;
    }
}
