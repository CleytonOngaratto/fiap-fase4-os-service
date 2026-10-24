package br.com.fiap.postech.carworkshop.saga.domain.message;

public record CancelDiagnosisPayload(String reason) implements SagaPayload {
}
