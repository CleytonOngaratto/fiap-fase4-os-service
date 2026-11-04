package br.com.fiap.postech.carworkshop.bdd.e2e;

import java.time.Duration;

final class E2eEnvironment {

    private E2eEnvironment() {
    }

    static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variavel de ambiente obrigatoria ausente: " + name);
        }
        return value;
    }

    static Duration seconds(String name) {
        return Duration.ofSeconds(Long.parseLong(required(name)));
    }
}
