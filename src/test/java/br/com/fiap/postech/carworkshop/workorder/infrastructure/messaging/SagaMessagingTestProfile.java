package br.com.fiap.postech.carworkshop.workorder.infrastructure.messaging;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

// Postgres de verdade em vez do H2 do perfil de teste: so assim a V5.0.0 e aplicada pelo Flyway antes da
// sessao no laboratorio. No %test o Flyway esta desligado e o schema vem do create-drop do Hibernate.
public class SagaMessagingTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.datasource.db-kind", "postgresql",
                "quarkus.hibernate-orm.dialect", "org.hibernate.dialect.PostgreSQLDialect",
                "quarkus.hibernate-orm.schema-management.strategy", "none",
                "quarkus.hibernate-orm.sql-load-script", "no-file",
                "quarkus.flyway.migrate-at-start", "true");
    }
}
