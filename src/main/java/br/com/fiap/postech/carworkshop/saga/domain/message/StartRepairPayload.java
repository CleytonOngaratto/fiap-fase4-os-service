package br.com.fiap.postech.carworkshop.saga.domain.message;

import java.util.List;

public record StartRepairPayload(List<SagaItem> items) implements SagaPayload {
}
