package br.com.fiap.postech.carworkshop.saga.domain.message;

import java.math.BigDecimal;

public record SagaItem(String type, Long catalogId, String description, int quantity, BigDecimal unitPrice) {

    public static final String SERVICE = "SERVICE";
    public static final String PART = "PART";

    public boolean isPart() {
        return PART.equals(type);
    }
}
